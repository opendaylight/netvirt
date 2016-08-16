/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class ExternalNetworkGroupInstaller {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalNetworkGroupInstaller.class);
    private static final long FIXED_DELAY_IN_MILLISECONDS = 4000;

    private final DataBroker broker;
    private final IMdsalApiManager mdsalManager;
    private final IElanService elanService;
    private final IdManagerService idManager;
    private final OdlInterfaceRpcService interfaceManager;

    public ExternalNetworkGroupInstaller(final DataBroker broker, final IMdsalApiManager mdsalManager,
            final IElanService elanService, final IdManagerService idManager,
            final OdlInterfaceRpcService interfaceManager) {
        this.broker = broker;
        this.mdsalManager = mdsalManager;
        this.elanService = elanService;
        this.idManager = idManager;
        this.interfaceManager = interfaceManager;
    }

    public void installExtNetGroupEntires(Subnetmap subnetMap) {
        if (subnetMap == null) {
            LOG.trace("Subnetmap is null");
            return;
        }

        Uuid networkId = subnetMap.getNetworkId();
        Uuid subnetId = subnetMap.getId();
        if (networkId == null) {
            LOG.trace("No external network associated subnet id {}", subnetId.getValue());
            return;
        }

        List<Uuid> routerIds = NatUtil.getRouterIdsfromNetworkId(broker, networkId);
        if (routerIds == null || routerIds.isEmpty()) {
            LOG.trace("No router found for network id", networkId.getValue());
            return;
        }

        String macAddress = NatUtil.getSubnetGwMac(broker, subnetId, routerIds.get(0).getValue());
        installExtNetGroupEntries(subnetMap, macAddress);
    }

    public void installExtNetGroupEntries(Uuid subnetId, String macAddress) {
        Subnetmap subnetMap = NatUtil.getSubnetMap(broker, subnetId);
        installExtNetGroupEntries(subnetMap, macAddress);
    }

    private void installExtNetGroupEntries(Subnetmap subnetMap, String macAddress) {
        if (subnetMap == null) {
            LOG.trace("Subnetmap is null");
            return;
        }

        String subnetName = subnetMap.getId().getValue();
        Uuid networkId = subnetMap.getNetworkId();
        if (networkId == null) {
            LOG.trace("No network associated subnet id {}", subnetName);
            return;
        }

        Collection<String> extInterfaces = elanService.getExternalElanInterfaces(networkId.getValue());
        if (extInterfaces == null || extInterfaces.isEmpty()) {
            LOG.trace("No external ELAN interfaces attached to subnet {}", subnetName);
            return;
        }

        long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(subnetName), idManager);

        for (String extInterface : extInterfaces) {
            GroupEntity groupEntity = buildExtNetGroupEntity(macAddress, subnetName, groupId, extInterface);
            if (groupEntity != null) {
                LOG.trace("Install ext-net Group: id {} gw mac address {} subnet id {}", groupId, macAddress,
                        subnetName);
                mdsalManager.syncInstallGroup(groupEntity, FIXED_DELAY_IN_MILLISECONDS);
            }
        }
    }

    private GroupEntity buildExtNetGroupEntity(String macAddress, String subnetName, long groupId,
            String extInterface) {
        BigInteger dpId = NatUtil.getDpnForInterface(interfaceManager, extInterface);
        if (BigInteger.ZERO.equals(dpId)) {
            LOG.warn("No DPN for interface {}. NAT ext-net flow will not be installed", extInterface);
            return null;
        }

        int pos = 0;
        List<ActionInfo> actionList = new ArrayList<>();
        if (!Strings.isNullOrEmpty(macAddress)) {
            actionList.add(new ActionInfo(ActionType.set_field_eth_dest, new String[] { macAddress }, pos++));
        } else {
            LOG.trace("GW mac has not been resolved for subnet {}", subnetName);
        }

        List<ActionInfo> egressActionList = NatUtil.getEgressActionsForInterface(interfaceManager, extInterface, null,
                pos);
        if (egressActionList == null || egressActionList.isEmpty()) {
            LOG.warn("No Egress actions found for interface {} subnet id {}", extInterface, subnetName);
        } else {
            actionList.addAll(egressActionList);
        }

        LOG.trace("Build group entry for subet {} groupId {} external interface {} on DPN {}", subnetName, groupId,
                extInterface, dpId);
        List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
        listBucketInfo.add(new BucketInfo(actionList));
        return MDSALUtil.buildGroupEntity(dpId, groupId, subnetName, GroupTypes.GroupIndirect, listBucketInfo);
    }

}

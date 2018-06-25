/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Strings;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetDestination;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalNetworkGroupInstaller {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalNetworkGroupInstaller.class);
    private final DataBroker broker;
    private final IMdsalApiManager mdsalManager;
    private final IElanService elanService;
    private final IdManagerService idManager;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final JobCoordinator coordinator;
    private final ItmRpcService itmRpcService;
    private final IInterfaceManager interfaceManager;
    private final NatServiceCounters natServiceCounters;

    @Inject
    public ExternalNetworkGroupInstaller(final DataBroker broker, final IMdsalApiManager mdsalManager,
                                         final IElanService elanService, final IdManagerService idManager,
                                         final OdlInterfaceRpcService odlInterfaceRpcService,
                                         final JobCoordinator coordinator, final ItmRpcService itmRpcService,
                                         final IInterfaceManager interfaceManager,
                                         NatServiceCounters natServiceCounters) {

        this.broker = broker;
        this.mdsalManager = mdsalManager;
        this.elanService = elanService;
        this.idManager = idManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.coordinator = coordinator;
        this.itmRpcService = itmRpcService;
        this.interfaceManager = interfaceManager;
        this.natServiceCounters = natServiceCounters;
    }

    public void installExtNetGroupEntries(Subnetmap subnetMap) {
        if (subnetMap == null) {
            LOG.error("installExtNetGroupEntries : Subnetmap is null");
            return;
        }

        if (NatUtil.isIPv6Subnet(subnetMap.getSubnetIp())) {
            LOG.debug("installExtNetGroupEntries : Subnet id {} is not an IPv4 subnet, hence skipping.",
                    subnetMap.getId());
            return;
        }

        Uuid networkId = subnetMap.getNetworkId();
        Uuid subnetId = subnetMap.getId();
        if (networkId == null) {
            LOG.error("installExtNetGroupEntries : No network associated subnet id {}", subnetId.getValue());
            return;
        }

        String macAddress = NatUtil.getSubnetGwMac(broker, subnetId, networkId.getValue());
        installExtNetGroupEntries(subnetMap, macAddress);
    }

    public void installExtNetGroupEntries(Uuid subnetId, String macAddress) {
        Subnetmap subnetMap = NatUtil.getSubnetMap(broker, subnetId);
        if (subnetMap == null) {
            LOG.error("installExtNetGroupEntries : Subnetmap is null");
            return;
        }

        if (NatUtil.isIPv6Subnet(subnetMap.getSubnetIp())) {
            LOG.debug("installExtNetGroupEntries : Subnet-id {} is not an IPv4 subnet, hence skipping.",
                    subnetMap.getId());
            return;
        }
        installExtNetGroupEntries(subnetMap, macAddress);
    }

    public void installExtNetGroupEntries(Uuid networkId, BigInteger dpnId) {
        if (networkId == null) {
            return;
        }

        List<Uuid> subnetIds = NatUtil.getSubnetIdsFromNetworkId(broker, networkId);
        if (subnetIds.isEmpty()) {
            LOG.error("installExtNetGroupEntries : No subnet ids associated network id {}", networkId.getValue());
            return;
        }

        for (Uuid subnetId : subnetIds) {
            String macAddress = NatUtil.getSubnetGwMac(broker, subnetId, networkId.getValue());
            installExtNetGroupEntry(networkId, subnetId, dpnId, macAddress);
        }
    }

    private void installExtNetGroupEntries(Subnetmap subnetMap, String macAddress) {

        String subnetName = subnetMap.getId().getValue();
        Uuid networkId = subnetMap.getNetworkId();
        if (networkId == null) {
            LOG.error("installExtNetGroupEntries : No network associated subnet id {}", subnetName);
            return;
        }

        Collection<String> extInterfaces = elanService.getExternalElanInterfaces(networkId.getValue());
        if (extInterfaces == null || extInterfaces.isEmpty()) {
            LOG.trace("installExtNetGroupEntries : No external ELAN interfaces attached to network:{},subnet {}",
                    networkId, subnetName);
            return;
        }

        long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(subnetName), idManager);

        LOG.info("installExtNetGroupEntries : Installing ext-net group {} entry for subnet {} with macAddress {} "
                + "(extInterfaces: {})", groupId, subnetName, macAddress, Arrays.toString(extInterfaces.toArray()));
        for (String extInterface : extInterfaces) {
            BigInteger dpId = NatUtil.getDpnForInterface(odlInterfaceRpcService, extInterface);
            if (BigInteger.ZERO.equals(dpId)) {
                LOG.info("installExtNetGroupEntries: No DPN for interface {}. NAT ext-net flow will not be installed "
                    + "for subnet {}", extInterface, subnetName);
                return;
            }
            installExtNetGroupEntry(groupId, subnetName, extInterface, macAddress, dpId);
        }
    }

    public void installExtNetGroupEntry(Uuid networkId, Uuid subnetId, BigInteger dpnId, String macAddress) {
        String subnetName = subnetId.getValue();
        String extInterface = elanService.getExternalElanInterface(networkId.getValue(), dpnId);
        if (extInterface == null) {
            LOG.warn("installExtNetGroupEntry : No external ELAN interface attached to network {} subnet {} DPN id {}",
                    networkId, subnetName, dpnId);
            //return;
        }

        long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(subnetName), idManager);
        LOG.info("installExtNetGroupEntry : Installing ext-net group {} entry for subnet {} with macAddress {} "
                + "(extInterface: {})", groupId, subnetName, macAddress, extInterface);
        installExtNetGroupEntry(groupId, subnetName, extInterface, macAddress, dpnId);
    }

    private void installExtNetGroupEntry(long groupId, String subnetName, String extInterface,
            String macAddress, BigInteger dpnId) {

        coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + subnetName + extInterface, () -> {
            GroupEntity groupEntity = buildExtNetGroupEntity(macAddress, subnetName, groupId, extInterface, dpnId);
            if (groupEntity != null) {
                mdsalManager.syncInstallGroup(groupEntity);
            }
            return Collections.emptyList();
        });
    }

    public void removeExtNetGroupEntries(Subnetmap subnetMap) {
        if (subnetMap == null) {
            return;
        }

        String subnetName = subnetMap.getId().getValue();
        Uuid networkId = subnetMap.getNetworkId();
        if (networkId == null) {
            LOG.error("removeExtNetGroupEntries : No external network associated subnet id {}", subnetName);
            return;
        }

        Collection<String> extInterfaces = elanService.getExternalElanInterfaces(networkId.getValue());
        if (extInterfaces == null || extInterfaces.isEmpty()) {
            LOG.debug("removeExtNetGroupEntries : No external ELAN interfaces attached to network {} subnet {}",
                    networkId, subnetName);
            return;
        }

        long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(subnetName), idManager);

        for (String extInterface : extInterfaces) {
            GroupEntity groupEntity = buildEmptyExtNetGroupEntity(subnetName, groupId, extInterface);
            if (groupEntity != null) {
                LOG.info("removeExtNetGroupEntries : Remove ext-net Group: id {}, subnet id {}", groupId, subnetName);
                natServiceCounters.removeExternalNetworkGroup();
                mdsalManager.syncRemoveGroup(groupEntity);
            }
        }
    }

    private GroupEntity buildExtNetGroupEntity(String macAddress, String subnetName,
                                               long groupId, String extInterface, BigInteger dpnId) {

        List<ActionInfo> actionList = new ArrayList<>();
        final int setFieldEthDestActionPos = 0;
        List<ActionInfo> egressActionList = new ArrayList<>();
        if (extInterface != null) {
            egressActionList = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmRpcService,
                    interfaceManager, extInterface, null, setFieldEthDestActionPos + 1, false);
        }
        if (Strings.isNullOrEmpty(macAddress) || egressActionList.isEmpty()) {
            if (Strings.isNullOrEmpty(macAddress)) {
                LOG.trace("buildExtNetGroupEntity : Building ext-net group {} entry with drop action since "
                        + "GW mac has not been resolved for subnet {} extInterface {}",
                        groupId, subnetName, extInterface);
            } else {
                LOG.warn("buildExtNetGroupEntity : Building ext-net group {} entry with drop action since "
                        + "no egress actions were found for subnet {} extInterface {}",
                        groupId, subnetName, extInterface);
            }
            actionList.add(new ActionDrop());
        } else {
            LOG.trace("Building ext-net group {} entry for subnet {} extInterface {} macAddress {}",
                      groupId, subnetName, extInterface, macAddress);
            actionList.add(new ActionSetFieldEthernetDestination(setFieldEthDestActionPos, new MacAddress(macAddress)));
            actionList.addAll(egressActionList);
        }

        List<BucketInfo> listBucketInfo = new ArrayList<>();
        listBucketInfo.add(new BucketInfo(actionList));
        return MDSALUtil.buildGroupEntity(dpnId, groupId, subnetName, GroupTypes.GroupAll, listBucketInfo);
    }

    private GroupEntity buildEmptyExtNetGroupEntity(String subnetName, long groupId, String extInterface) {
        BigInteger dpId = NatUtil.getDpnForInterface(odlInterfaceRpcService, extInterface);
        if (BigInteger.ZERO.equals(dpId)) {
            LOG.error("buildEmptyExtNetGroupEntity: No DPN for interface {}. NAT ext-net flow will not be installed "
                    + "for subnet {}", extInterface, subnetName);
            return null;
        }

        return MDSALUtil.buildGroupEntity(dpId, groupId, subnetName, GroupTypes.GroupAll, new ArrayList<>());
    }
}

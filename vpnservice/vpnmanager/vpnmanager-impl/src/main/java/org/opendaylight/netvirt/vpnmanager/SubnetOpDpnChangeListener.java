/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes.NetworkType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubnetOpDpnChangeListener extends AsyncDataTreeChangeListenerBase<SubnetToDpn, SubnetOpDpnChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetOpDpnChangeListener.class);

    private final DataBroker dataBroker;
    private final IElanService elanService;
    private final INeutronVpnManager neutronVpnManager;

    public SubnetOpDpnChangeListener(final DataBroker dataBroker, final IElanService elanService,
            final INeutronVpnManager neutronVpnManager) {
        this.dataBroker = dataBroker;
        this.elanService = elanService;
        this.neutronVpnManager = neutronVpnManager;
    }

    @Override
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<SubnetToDpn> getWildCardPath() {
        return InstanceIdentifier.create(SubnetOpData.class).child(SubnetOpDataEntry.class).child(SubnetToDpn.class);
    }

    @Override
    protected void remove(InstanceIdentifier<SubnetToDpn> dpnKey, SubnetToDpn oldSubneToDpn) {
        LOG.trace("remove called on old SubnetToDpn: {} ", oldSubneToDpn);

        Uuid subnetId = dpnKey.firstKeyOf(SubnetOpDataEntry.class).getSubnetId();
        Subnetmap subnetmap = VpnUtil.getSubnetmap(dataBroker, subnetId);
        BigInteger oldDpnId = oldSubneToDpn.getDpnId();
        if (subnetmap == null) {
            LOG.error("Cannot find Subnetmap ID: {}, will not remove external VpnInterface if needed for DPN {}",
                    subnetId, oldDpnId);
        }
        Uuid networkId = subnetmap.getNetworkId();
        Collection<String> subnetsNames = neutronVpnManager.getAllSubnetsNamesInNetwork(networkId);
        boolean isDpnPartOfTheNetwork =
                subnetsNames.stream()
                            .map(subnetName -> new Uuid(subnetName))
                            .filter(subnetUuid -> VpnUtil.getSubnetToDpn(dataBroker, subnetUuid, oldDpnId) != null)
                            .anyMatch(subnetUuid -> true);
        if (!isDpnPartOfTheNetwork) {
            addJobToRemoveExternelVpnInterface(oldDpnId, networkId);
        }

    }

    private void addJobToRemoveExternelVpnInterface(BigInteger oldDpnId, Uuid networkId) {
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(createJobCoordinatorKey(networkId, oldDpnId),
            () -> {
                neutronVpnManager.removeExternalVpnInterfaceForDpn(networkId, oldDpnId);
                return Collections.EMPTY_LIST;
            });
    }

    @Override
    protected void update(InstanceIdentifier<SubnetToDpn> key, SubnetToDpn oldSubnetToDpn,
            SubnetToDpn updatedSubnetToDpn) {
        LOG.trace("update called on old SubnetToDpn: {} and updated SubnetToDpn: {}", oldSubnetToDpn,
                updatedSubnetToDpn);
        // Nothing to do here for now.

    }

    @Override
    protected void add(InstanceIdentifier<SubnetToDpn> dpnKey, SubnetToDpn newSubnetToDpn) {
        LOG.trace("add called on new SubnetToDpn: {} ", newSubnetToDpn);

        Uuid subnetId = dpnKey.firstKeyOf(SubnetOpDataEntry.class).getSubnetId();
        Subnetmap subnetmap = VpnUtil.getSubnetmap(dataBroker, subnetId);
        BigInteger newDpnId = newSubnetToDpn.getDpnId();
        if (subnetmap == null) {
            LOG.error("Cannot find Subnetmap: {}, will not create external VpnInterface if needed for DPN {}", subnetId,
                    newDpnId);
        }
        if (isFlatOrVlanNetwork(subnetmap) && isRouterConnectedToSubnet(subnetmap)) {
            Uuid networkUuid = subnetmap.getNetworkId();
            String externalElanIntf = elanService.getExternalElanInterface(networkUuid.getValue(), newDpnId);
            VpnInterface vpnInterface = VpnUtil.getVpnInterface(dataBroker, externalElanIntf);
            if (vpnInterface == null) {
                Uuid vpnId = subnetmap.getVpnId();
                if (vpnId == null) {
                    LOG.error("Sunbetmap {} doesn't have VpnID, cannot create external VpnInterface {}",
                            subnetmap.getId(), externalElanIntf);
                }
                addJobToCreateExternalVpnInteface(networkUuid, vpnId, newDpnId);

            } else {
                LOG.debug("VpnInterface {} for network {} already exist, not creating additional one", newDpnId,
                        networkUuid);
            }
        }
    }

    private void addJobToCreateExternalVpnInteface(Uuid networkUuid, Uuid vpnId, BigInteger dpnId) {
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(createJobCoordinatorKey(networkUuid, dpnId),
            () -> {
                neutronVpnManager.createExternalVpnInterfaceForDpn(networkUuid, vpnId, dpnId);
                return Collections.EMPTY_LIST;
            });
    }

    private String createJobCoordinatorKey(Uuid networkUuid, BigInteger dpnId) {
        return "EXTERNAL-VPNINTERFACE-" + networkUuid.getValue() + "-" + dpnId;
    }

    private boolean isRouterConnectedToSubnet(Subnetmap subnetmap) {
        return subnetmap.getRouterInterfaceFixedIp() != null && subnetmap.getRouterIntfMacAddress() != null;
    }

    private boolean isFlatOrVlanNetwork(Subnetmap subnetmap) {
        return subnetmap.getNetworkType() == NetworkType.FLAT || subnetmap.getNetworkType() == NetworkType.VLAN;
    }

    @Override
    protected SubnetOpDpnChangeListener getDataTreeChangeListener() {
        return this;
    }


}

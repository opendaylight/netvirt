/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.UpgradeState;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalSubnetVpnInstanceListener extends AsyncDataTreeChangeListenerBase<VpnInstance,
    ExternalSubnetVpnInstanceListener> {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetVpnInstanceListener.class);
    private final DataBroker dataBroker;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;
    private final IElanService elanService;
    private final IVpnManager vpnManager;
    private final UpgradeState upgradeState;
    private final DataTreeEventCallbackRegistrar dataTreeEventCallbackRegistrar;

    @Inject
    public ExternalSubnetVpnInstanceListener(final DataBroker dataBroker,
                     final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
                     final IElanService elanService, final IVpnManager vpnManager,
                     final UpgradeState upgradeState, DataTreeEventCallbackRegistrar dataTreeEventCallbackRegistrar) {
        this.dataBroker = dataBroker;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
        this.elanService = elanService;
        this.vpnManager = vpnManager;
        this.upgradeState = upgradeState;
        this.dataTreeEventCallbackRegistrar = dataTreeEventCallbackRegistrar;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInstance> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstanceToVpnId.class).child(VpnInstance.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstance) {
        LOG.trace("remove : External Subnet VPN Instance remove mapping method - key:{}. value={}",
                vpnInstance.key(), vpnInstance);
        String possibleExtSubnetUuid = vpnInstance.getVpnInstanceName();
        Optional<Subnets> optionalSubnets = NatUtil.getOptionalExternalSubnets(dataBroker,
                new Uuid(possibleExtSubnetUuid));
        if (optionalSubnets.isPresent()) {
            addOrDelDefaultFibRouteToSNATFlow(vpnInstance, optionalSubnets.get(), NwConstants.DEL_FLOW);
            invokeSubnetDeletedFromVpn(possibleExtSubnetUuid);
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstanceOrig,
            VpnInstance vpnInstanceNew) {
        LOG.trace("update : External Subnet VPN Instance update mapping method - key:{} original:{} new:{}",
                vpnInstanceNew.key(), vpnInstanceOrig, vpnInstanceNew);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstance) {
        LOG.trace("add : External Subnet VPN Instance OP Data Entry add mapping method - key:{}. value={}",
                vpnInstance.key(), vpnInstance);
        String possibleExtSubnetUuid = vpnInstance.getVpnInstanceName();
        Optional<Subnets> optionalSubnets = NatUtil.getOptionalExternalSubnets(dataBroker,
                new Uuid(possibleExtSubnetUuid));
        if (optionalSubnets.isPresent()) {
            LOG.debug("add : VpnInstance {} for external subnet {}.", possibleExtSubnetUuid,
                    optionalSubnets.get());
            addOrDelDefaultFibRouteToSNATFlow(vpnInstance, optionalSubnets.get(), NwConstants.ADD_FLOW);
            invokeSubnetAddedToVpn(possibleExtSubnetUuid);
        }
    }

    private void invokeSubnetAddedToVpn(String externalSubnetId) {
        Uuid externalSubnetUuid = new Uuid(externalSubnetId);
        Subnetmap subnetMap = NatUtil.getSubnetMap(dataBroker, externalSubnetUuid);
        if (subnetMap == null) {
            LOG.error("invokeSubnetAddedToVpn : Cannot invoke onSubnetAddedToVpn for subnet-id {} in vpn-id {}"
                    + " due to this subnet missing in Subnetmap model", externalSubnetUuid, externalSubnetId);
            return;
        }
        ElanInstance elanInstance = elanService.getElanInstance(subnetMap.getNetworkId().getValue());
        vpnManager.onSubnetAddedToVpn(subnetMap, false, elanInstance.getElanTag());

    }

    private void invokeSubnetDeletedFromVpn(String externalSubnetId) {
        Uuid externalSubnetUuid = new Uuid(externalSubnetId);
        Subnetmap subnetMap = NatUtil.getSubnetMap(dataBroker, externalSubnetUuid);
        if (subnetMap == null) {
            LOG.error("invokeSubnetDeletedFromVpn : Cannot invoke invokeSubnetDeletedFromVpn for subnet-id {} in "
                    + "vpn-id {} due to this subnet missing in Subnetmap model", externalSubnetUuid, externalSubnetId);
            return;
        }
        vpnManager.onSubnetDeletedFromVpn(subnetMap, false);
    }

    private void addOrDelDefaultFibRouteToSNATFlow(VpnInstance vpnInstance, Subnets subnet, int flowAction) {
        String vpnInstanceName = vpnInstance.getVpnInstanceName();
        LOG.debug("addOrDelDefaultFibRouteToSNATFlow : VpnInstance {} for external subnet {}.",
                vpnInstanceName, subnet);
        Long vpnId = vpnInstance.getVpnId();

        if (upgradeState.isUpgradeInProgress()) {
            LOG.info("Upgrade in process, checking for existence of VpnInstanceOpDataEntry's vpn->dpn list");
            InstanceIdentifier<VpnInstanceOpDataEntry> vpnOpDataIid =
                    NatUtil.getVpnInstanceOpDataIdentifier(subnet.getExternalNetworkId().getValue());

            Optional<VpnInstanceOpDataEntry> networkVpnInstanceOp;
            try {
                networkVpnInstanceOp = SingleTransactionDataBroker.syncReadOptional(
                                            dataBroker, LogicalDatastoreType.OPERATIONAL, vpnOpDataIid);
            } catch (ReadFailedException e) {
                LOG.error("Exception while attempting to read VpnInstanceOpDataEntry", e);
                return;
            }

            List<VpnToDpnList> dpnListInVpn = null;
            if (networkVpnInstanceOp.isPresent()) {
                dpnListInVpn = networkVpnInstanceOp.get().getVpnToDpnList();
            }

            if (dpnListInVpn == null) {
                LOG.info("VpnInstanceOpDataEntry's vpn->dpn list not present, wait for it");
                dataTreeEventCallbackRegistrar.onAddOrUpdate(LogicalDatastoreType.OPERATIONAL, vpnOpDataIid,
                    (beforeOpData, afterOpData) -> {
                        LOG.info("VpnInstanceOpDataEntry added/updated {}", afterOpData);
                        if (afterOpData.getVpnToDpnList() == null) {
                            if (upgradeState.isUpgradeInProgress()) {
                                return DataTreeEventCallbackRegistrar.NextAction.CALL_AGAIN;
                            } else {
                                return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                            }
                        }
                        snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnet(subnet,
                                subnet.getExternalNetworkId().getValue(), flowAction, vpnId);
                        return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                    });
                return;
            }
            LOG.info("VpnInstanceOpDataEntry's vpn->dpn list present, continue with regular scheduled programming");

        }

        snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnet(subnet,
                subnet.getExternalNetworkId().getValue(), flowAction, vpnId);
    }

    @Override
    protected ExternalSubnetVpnInstanceListener getDataTreeChangeListener() {
        return ExternalSubnetVpnInstanceListener.this;
    }
}

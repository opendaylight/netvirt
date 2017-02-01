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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ExternalSubnetVpnInstanceOpDataListener extends AsyncDataTreeChangeListenerBase<VpnInstanceOpDataEntry,
    ExternalSubnetVpnInstanceOpDataListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetVpnInstanceOpDataListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;

    public ExternalSubnetVpnInstanceOpDataListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager) {
        super(VpnInstanceOpDataEntry.class, ExternalSubnetVpnInstanceOpDataListener.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
    }

    @Override
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInstanceOpDataEntry> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstanceOpDataEntry> key,
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry) {
        LOG.trace("NAT Service : External Subnet VPN Instance OP Data Entry remove mapping method - key:{}. value={}",
                key, vpnInstanceOpDataEntry);
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstanceOpDataEntry> key,
            VpnInstanceOpDataEntry vpnInstanceOpDataEntryOrig, VpnInstanceOpDataEntry vpnInstanceOpDataEntryNew) {
        LOG.trace("NAT Service : External Subnet VPN Instance OP Data Entry update mapping method - "
                + "key:{}. original={}, new={}", key, vpnInstanceOpDataEntryOrig, vpnInstanceOpDataEntryNew);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstanceOpDataEntry> key,
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry) {
        LOG.trace("NAT Service : External Subnet VPN Instance OP Data Entry add mapping method - key:{}. value={}",
                key, vpnInstanceOpDataEntry);
        String vrfId = vpnInstanceOpDataEntry.getVrfId();
        Uuid possibleExternalSubnetUuid = new Uuid(vrfId);
        InstanceIdentifier<Subnets> subnetsIdentifier = InstanceIdentifier.builder(ExternalSubnets.class)
                .child(Subnets.class, new SubnetsKey(possibleExternalSubnetUuid)).build();
        Optional<Subnets> optionalSubnets = NatUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                subnetsIdentifier);
        if (optionalSubnets.isPresent()) {
            LOG.debug("VpnInstanceOpDataEntry VRF ID {} has an external subnet {}.", vrfId, optionalSubnets.get());
            Subnets subnet = optionalSubnets.get();
            Long vpnId = vpnInstanceOpDataEntry.getVpnId();
            List<Uuid> routerIds = subnet.getRouterIds();
            if (routerIds != null) {
                for (Uuid routerId : routerIds) {
                    addDefaultFibRouteToSNAT(subnet, routerId.getValue(), vpnId);
                }
            }
        } else {
            LOG.trace("VpnInstanceOpDataEntry VRF ID {} is not of an external subnet.", vrfId);
        }
    }

    @Override
    protected ExternalSubnetVpnInstanceOpDataListener getDataTreeChangeListener() {
        return ExternalSubnetVpnInstanceOpDataListener.this;
    }

    private void addDefaultFibRouteToSNAT(Subnets subnet, String routerId, long vpnId) {
        String subnetId = subnet.getId().getValue();
        InstanceIdentifier<VpnInstanceOpDataEntry> routerVpnInstanceIdentifier =
                NatUtil.getVpnInstanceOpDataIdentifier(routerId);
        Optional<VpnInstanceOpDataEntry> routerVpnInstanceOp = NatUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, routerVpnInstanceIdentifier);
        if (routerVpnInstanceOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = routerVpnInstanceOp.get().getVpnToDpnList();
            if (dpnListInVpn != null) {
                for (VpnToDpnList dpn : dpnListInVpn) {
                    FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntity(dpn.getDpnId(), vpnId);
                    if (flowEntity == null) {
                        LOG.error("NAT Service : Flow entity received is NULL. Cannot install "
                                + "Default NAT flow for subnet {}", subnetId);
                        return;
                    }

                    LOG.debug("Installing flow {} for subnetId {}, vpnId {} on dpn {}",
                            flowEntity, subnetId, vpnId, dpn.getDpnId());
                    mdsalManager.installFlow(flowEntity);
                }
            } else {
                LOG.debug("Will not add/remove default NAT flow for subnet {} no dpn set for vpn instance {}",
                        subnetId, routerVpnInstanceOp.get());
            }
        } else {
            if (!routerVpnInstanceOp.isPresent()) {
                LOG.debug("Cannot create/remove default FIB route to SNAT flow for subnet  {} "
                        + "vpn-instance-op-data entry for router {} does not exist",
                        subnetId, routerId);
            }
        }
    }
}

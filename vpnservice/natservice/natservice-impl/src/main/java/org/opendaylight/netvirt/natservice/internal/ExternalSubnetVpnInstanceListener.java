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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalSubnetVpnInstanceListener extends AsyncDataTreeChangeListenerBase<VpnInstance,
    ExternalSubnetVpnInstanceListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetVpnInstanceListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;

    public ExternalSubnetVpnInstanceListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
            final IdManagerService idManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.idManager = idManager;
    }

    @Override
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
        LOG.trace("NAT Service : External Subnet VPN Instance remove mapping method - key:{}. value={}",
                key, vpnInstance);
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstanceOrig,
            VpnInstance vpnInstanceNew) {
        LOG.trace("NAT Service : External Subnet VPN Instance update mapping method - key:{}. original={}, new={}",
                key, vpnInstanceOrig, vpnInstanceNew);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstance) {
        LOG.trace("NAT Service : External Subnet VPN Instance OP Data Entry add mapping method - key:{}. value={}",
                key, vpnInstance);
        String vpnInstanceName = vpnInstance.getVpnInstanceName();
        Uuid possibleExternalSubnetUuid = new Uuid(vpnInstanceName);
        InstanceIdentifier<Subnets> subnetsIdentifier = InstanceIdentifier.builder(ExternalSubnets.class)
                .child(Subnets.class, new SubnetsKey(possibleExternalSubnetUuid)).build();
        Optional<Subnets> optionalSubnets = NatUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                subnetsIdentifier);
        if (optionalSubnets.isPresent()) {
            LOG.debug("VpnInstance {} if for external subnet {}.", vpnInstanceName, optionalSubnets.get());
            Subnets subnet = optionalSubnets.get();
            Long vpnId = vpnInstance.getVpnId();
            addDefaultFibRouteToSNAT(subnet, subnet.getExternalNetworkId().getValue(), vpnId);
        } else {
            LOG.trace("VpnInstance {} is not for an external subnet.", vpnInstanceName);
        }
    }

    @Override
    protected ExternalSubnetVpnInstanceListener getDataTreeChangeListener() {
        return ExternalSubnetVpnInstanceListener.this;
    }

    private void addDefaultFibRouteToSNAT(Subnets subnet, String networkId, long vpnId) {
        String subnetId = subnet.getId().getValue();
        InstanceIdentifier<VpnInstanceOpDataEntry> networkVpnInstanceIdentifier =
                NatUtil.getVpnInstanceOpDataIdentifier(networkId);
        Optional<VpnInstanceOpDataEntry> networkVpnInstanceOp = NatUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, networkVpnInstanceIdentifier);
        if (networkVpnInstanceOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = networkVpnInstanceOp.get().getVpnToDpnList();
            if (dpnListInVpn != null) {
                for (VpnToDpnList dpn : dpnListInVpn) {
                    FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntityForExternalSubnet(dpn.getDpnId(),
                            vpnId, subnetId, idManager);
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
                        subnetId, networkVpnInstanceOp.get());
            }
        } else {
            LOG.debug("Cannot create/remove default FIB route to SNAT flow for subnet  {} "
                    + "vpn-instance-op-data entry for network {} does not exist", subnetId, networkId);
        }
    }
}

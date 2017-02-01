/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalSubnetListener extends AsyncDataTreeChangeListenerBase<Subnets, ExternalSubnetListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;

    public ExternalSubnetListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
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
    protected InstanceIdentifier<Subnets> getWildCardPath() {
        return InstanceIdentifier.create(ExternalSubnets.class).child(Subnets.class);
    }

    @Override
    protected ExternalSubnetListener getDataTreeChangeListener() {
        return ExternalSubnetListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<Subnets> identifier, Subnets subnets) {
        LOG.trace("NAT Service : External Subnet remove mapping method - key:{}. value={}",identifier, subnets);
        Uuid externalNetworkId = subnets.getExternalNetworkId();
        addOrDelDefFibRouteToSNAT(subnets, externalNetworkId.getValue(), NwConstants.DEL_FLOW);
    }

    @Override
    protected void update(InstanceIdentifier<Subnets> identifier, Subnets subnetsOrig,
            Subnets subnetsNew) {
        LOG.trace("NAT Service : External Subnet update - key:{}. original={}, new={}",
                identifier, subnetsOrig, subnetsNew);
    }

    @Override
    protected void add(InstanceIdentifier<Subnets> identifier, Subnets subnets) {
        LOG.trace("NAT Service : External Subnet add mapping method - key:{}. value={}",identifier, subnets);
        Uuid externalNetworkId = subnets.getExternalNetworkId();
        addOrDelDefFibRouteToSNAT(subnets, externalNetworkId.getValue(), NwConstants.ADD_FLOW);
    }

    private void addOrDelDefFibRouteToSNAT(Subnets subnet, String networkId, int flowAction) {
        String subnetId = subnet.getId().getValue();
        InstanceIdentifier<VpnInstanceOpDataEntry> networkVpnInstanceIdentifier =
            NatUtil.getVpnInstanceOpDataIdentifier(networkId);
        Optional<VpnInstanceOpDataEntry> networkVpnInstanceOp = NatUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, networkVpnInstanceIdentifier);
        if (networkVpnInstanceOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = networkVpnInstanceOp.get().getVpnToDpnList();
            long vpnId = NatUtil.getVpnId(dataBroker, subnet.getVpnId().getValue());
            if (vpnId == NatConstants.INVALID_ID) {
                LOG.info("NAT Service : Invalid VPN ID for subnet {}, cannot handle default FIB route to SNAT flow.",
                        subnet);
                return;
            }

            if (dpnListInVpn != null) {
                for (VpnToDpnList dpn : dpnListInVpn) {
                    FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntityForExternalSubnet(dpn.getDpnId(),
                            vpnId, subnetId, idManager);
                    if (flowEntity == null) {
                        LOG.error("NAT Service : Flow entity received is NULL. Cannot install "
                                + "Default NAT flow for subnet {}", subnetId);
                        return;
                    }

                    if (flowAction == NwConstants.ADD_FLOW || flowAction == NwConstants.MOD_FLOW) {
                        LOG.debug("Installing flow {} for subnetId {}, vpnId {} on dpn {}",
                                flowEntity, subnetId, vpnId, dpn.getDpnId());
                        mdsalManager.installFlow(flowEntity);
                    } else if (shouldRemoveDefaultNatRouterFlowForSubnetOnDpn(subnet, networkId, dpn.getDpnId())) {
                        LOG.debug("Removing flow for subnetId {}, vpnId {} with dpn", subnetId, vpnId, dpn);
                        removeDefaultNATRouteInDPN(dpn.getDpnId(), vpnId, subnetId);
                    }
                }
            } else {
                LOG.debug("Will not add/remove default NAT flow for subnet {} no dpn set for vpn instance {}",
                    subnetId, networkVpnInstanceOp.get());
            }
        } else {
            LOG.debug("Cannot create/remove default FIB route to SNAT flow for subnet  {} "
                + "vpn-instance-op-data entry for network {} does not exist",
                subnetId, networkId);
        }
    }

    private boolean shouldRemoveDefaultNatRouterFlowForSubnetOnDpn(Subnets subnet,
        String removedRouterId, BigInteger dpnId) {
        Uuid subnetId = subnet.getId();
        List<Uuid> routerIdsForSubnet = subnet.getRouterIds();
        if (routerIdsForSubnet != null) {
            routerIdsForSubnet.remove(new Uuid(removedRouterId));
            for (Uuid routerId : routerIdsForSubnet) {
                if (NatUtil.isDpnBelongsToRouterVrf(dataBroker, routerId, dpnId)) {
                    return false;
                }
            }
        }

        LOG.debug("Should remove default router in FIB on dpn {} for subnetId {}", dpnId, subnetId);
        return true;
    }

    private void removeDefaultNATRouteInDPN(BigInteger dpnId, long vpnId, String subnetId) {
        FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntityForExternalSubnet(dpnId, vpnId, subnetId, idManager);
        if (flowEntity == null) {
            LOG.error("NAT Service : Flow entity received is NULL. Cannot proceed with removal of Default NAT flow");
            return;
        }
        mdsalManager.removeFlow(flowEntity);
    }
}

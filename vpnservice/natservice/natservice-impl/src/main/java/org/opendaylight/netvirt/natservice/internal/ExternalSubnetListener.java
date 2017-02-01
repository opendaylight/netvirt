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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
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

    public ExternalSubnetListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager) {
        super(Subnets.class, ExternalSubnetListener.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
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
        List<Uuid> routerIds = subnets.getRouterIds();
        if (routerIds != null) {
            for (Uuid routerId : routerIds) {
                addOrDelDefFibRouteToSNAT(subnets, routerId.getValue(), false);
            }
        } else {
            LOG.debug("No routers associated with external network {} for subnet {}", externalNetworkId, subnets);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Subnets> identifier, Subnets subnetsOrig,
            Subnets subnetsNew) {
        LOG.trace("NAT Service : External Subnet update - key:{}. original={}, new={}",
                identifier, subnetsOrig, subnetsNew);
        Uuid externalNetworkId = subnetsNew.getExternalNetworkId();
        List<Uuid> routerIds = subnetsNew.getRouterIds();
        if (routerIds != null) {
            for (Uuid routerId : routerIds) {
                addOrDelDefFibRouteToSNAT(subnetsNew, routerId.getValue(), true);
            }
        } else {
            LOG.debug("No routers associated with external network {} for subnet {}", externalNetworkId, subnetsNew);
        }

        List<Uuid> routerIdsOrig = subnetsOrig.getRouterIds();
        List<Uuid> routerIdsNew = subnetsNew.getRouterIds();
        Set<Uuid> removedRouterIds = new HashSet<>(routerIdsOrig);
        removedRouterIds.removeAll(routerIdsNew);
        if (removedRouterIds.size() != 0) {
            for (Uuid removedRouterId : removedRouterIds) {
                LOG.debug("Will remove default FIB router flows for subnetId {} on removal of routerId {}",
                        subnetsNew.getId(), removedRouterId.getValue());
                addOrDelDefFibRouteToSNAT(subnetsNew, removedRouterId.getValue(), false);
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<Subnets> identifier, Subnets subnets) {
        LOG.trace("NAT Service : External Subnet add mapping method - key:{}. value={}",identifier, subnets);
        Uuid externalNetworkId = subnets.getExternalNetworkId();
        List<Uuid> routerIds = subnets.getRouterIds();
        if (routerIds != null) {
            for (Uuid routerId : routerIds) {
                addOrDelDefFibRouteToSNAT(subnets, routerId.getValue(), true);
            }
        } else {
            LOG.debug("No routers associated with external network {} for subnet {}", externalNetworkId, subnets);
        }
    }

    private void addOrDelDefFibRouteToSNAT(Subnets subnet, String routerId, boolean shouldCreate) {
        String subnetId = subnet.getId().getValue();
        InstanceIdentifier<VpnInstanceOpDataEntry> routerVpnInstanceIdentifier =
                NatUtil.getVpnInstanceOpDataIdentifier(routerId);
        Optional<VpnInstanceOpDataEntry> routerVpnInstanceOp = NatUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, routerVpnInstanceIdentifier);
        InstanceIdentifier<VpnInstanceOpDataEntry> subnetIdentifier = NatUtil.getVpnInstanceOpDataIdentifier(subnetId);
        Optional<VpnInstanceOpDataEntry> subnetVpnInstanceOp = NatUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, subnetIdentifier);

        if (routerVpnInstanceOp.isPresent() && subnetVpnInstanceOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = routerVpnInstanceOp.get().getVpnToDpnList();
            long vpnId = NatUtil.readVpnId(dataBroker, subnetVpnInstanceOp.get().getVrfId());
            if (dpnListInVpn != null) {
                for (VpnToDpnList dpn : dpnListInVpn) {
                    FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntity(dpn.getDpnId(), vpnId);
                    if (flowEntity == null) {
                        LOG.error("NAT Service : Flow entity received is NULL. Cannot install "
                                + "Default NAT flow for subnet {}", subnetId);
                        return;
                    }

                    if (shouldCreate) {
                        LOG.debug("Installing flow {} for subnetId {}, vpnId {} on dpn {}",
                                flowEntity, subnetId, vpnId, dpn.getDpnId());
                        mdsalManager.installFlow(flowEntity);
                    } else {
                        if (shouldRemoveDefaultNatRouterFlowForSubnetOnDpn(subnet, routerId, dpn.getDpnId())) {
                            LOG.debug("Removing flow for subnetId {}, vpnId {} with dpn", subnetId, vpnId, dpn);
                            removeDefaultNATRouteInDPN(dpn.getDpnId(), vpnId);
                        }
                    }
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
            } else if (!subnetVpnInstanceOp.isPresent()) {
                LOG.debug("Cannot create/remove default FIB route to SNAT flow for subnet  {} "
                        + "vpn-instance-op-data entry for subnet does not exist",
                        subnetId);
            }
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

    private void removeDefaultNATRouteInDPN(BigInteger dpnId, long vpnId) {
        FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntity(dpnId, vpnId);
        if (flowEntity == null) {
            LOG.error("NAT Service : Flow entity received is NULL. Cannot proceed with removal of Default NAT flow");
            return;
        }
        mdsalManager.removeFlow(flowEntity);
    }
}

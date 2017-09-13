/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SNATDefaultRouteProgrammer {

    private static final Logger LOG = LoggerFactory.getLogger(SNATDefaultRouteProgrammer.class);
    private final IMdsalApiManager mdsalManager;
    private final DataBroker dataBroker;
    private final IdManagerService idManager;

    @Inject
    public SNATDefaultRouteProgrammer(final IMdsalApiManager mdsalManager, final DataBroker dataBroker,
            final IdManagerService idManager) {
        this.mdsalManager = mdsalManager;
        this.dataBroker = dataBroker;
        this.idManager = idManager;
    }

    private FlowEntity buildDefNATFlowEntity(BigInteger dpId, long vpnId) {
        InetAddress defaultIP = null;
        try {
            defaultIP = InetAddress.getByName("0.0.0.0");
        } catch (UnknownHostException e) {
            LOG.error("buildDefNATFlowEntity : Failed  to build FIB Table Flow for "
                + "Default Route to NAT table", e);
            return null;
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        //add match for default route "0.0.0.0/0"
//        matches.add(new MatchInfo(MatchFieldType.ipv4_dst, new long[] {
//                NatUtil.getIpAddress(defaultIP.getAddress()), 0 }));

        //add match for vrfid
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.L3_FIB_TABLE, defaultIP, vpnId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
            NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }

    private FlowEntity buildDefNATFlowEntity(BigInteger dpId, long bgpVpnId, long routerId) {
        InetAddress defaultIP = null;
        try {
            defaultIP = InetAddress.getByName("0.0.0.0");
        } catch (UnknownHostException e) {
            LOG.error("buildDefNATFlowEntity : Failed  to build FIB Table Flow for "
                + "Default Route to NAT table", e);
            return null;
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        //add match for default route "0.0.0.0/0"
//        matches.add(new MatchInfo(MatchFieldType.ipv4_dst, new long[] {
//                NatUtil.getIpAddress(defaultIP.getAddress()), 0 }));

        //add match for vrfid
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(bgpVpnId), MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.L3_FIB_TABLE, defaultIP, routerId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
            NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;


    }

    void installDefNATRouteInDPN(BigInteger dpnId, long vpnId, WriteTransaction writeFlowInvTx) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if (flowEntity == null) {
            LOG.error("installDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        NatServiceCounters.install_default_nat_flow.inc();
        mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);
    }

    void installDefNATRouteInDPN(BigInteger dpnId, long bgpVpnId, long routerId, WriteTransaction writeFlowInvTx) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, bgpVpnId, routerId);
        if (flowEntity == null) {
            LOG.error("installDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        NatServiceCounters.install_default_nat_flow.inc();
        mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);
    }

    void installDefNATRouteInDPN(BigInteger dpnId, long vpnId, String subnetId,
            IdManagerService idManager) {
        FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntityForExternalSubnet(dpnId, vpnId, subnetId, idManager);
        if (flowEntity == null) {
            LOG.error("installDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        NatServiceCounters.install_default_nat_flow.inc();
        mdsalManager.installFlow(flowEntity);
    }

    void removeDefNATRouteInDPN(BigInteger dpnId, long vpnId, WriteTransaction writeFlowInvTx) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if (flowEntity == null) {
            LOG.error("removeDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        NatServiceCounters.remove_default_nat_flow.inc();
        mdsalManager.removeFlowToTx(flowEntity, writeFlowInvTx);
    }

    void removeDefNATRouteInDPN(BigInteger dpnId, long bgpVpnId, long routerId, WriteTransaction writeFlowInvTx) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, bgpVpnId, routerId);
        if (flowEntity == null) {
            LOG.error("removeDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        NatServiceCounters.remove_default_nat_flow.inc();
        mdsalManager.removeFlowToTx(flowEntity, writeFlowInvTx);
    }

    Waiter2<VpnInstanceOpDataEntry> waiter = null;
    void addOrDelDefaultFibRouteToSNATForSubnet(Subnets subnet, String networkId, int flowAction, long vpnId) {
        String subnetId = subnet.getId().getValue();
        InstanceIdentifier<VpnInstanceOpDataEntry> networkVpnInstanceIdentifier =
            NatUtil.getVpnInstanceOpDataIdentifier(networkId);
        Optional<VpnInstanceOpDataEntry> networkVpnInstanceOp =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, networkVpnInstanceIdentifier);
        if (networkVpnInstanceOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = networkVpnInstanceOp.get().getVpnToDpnList();
            if (dpnListInVpn != null) {
                for (VpnToDpnList dpn : dpnListInVpn) {
                    FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntityForExternalSubnet(dpn.getDpnId(),
                            vpnId, subnetId, idManager);
                    if (flowAction == NwConstants.ADD_FLOW || flowAction == NwConstants.MOD_FLOW) {
                        LOG.info("addOrDelDefaultFibRouteToSNATForSubnet : Installing flow {} for subnetId {},"
                                + "vpnId {} on dpn {}", flowEntity, subnetId, vpnId, dpn.getDpnId());
                        mdsalManager.installFlow(flowEntity);
                    } else {
                        LOG.info("addOrDelDefaultFibRouteToSNATForSubnet : Removing flow for subnetId {},"
                                + "vpnId {} with dpn {}", subnetId, vpnId, dpn);
                        mdsalManager.removeFlow(flowEntity);
                    }
                }
            } else {
                LOG.debug("addOrDelDefaultFibRouteToSNATForSubnet : Will not add/remove default NAT flow for subnet {} "
                        + "no dpn set for vpn instance {}", subnetId, networkVpnInstanceOp.get());
                if (waiter == null) {
                    waiter = new Waiter2<VpnInstanceOpDataEntry>(dataBroker, LogicalDatastoreType.OPERATIONAL, networkVpnInstanceIdentifier,
                            (t, u) -> {
                                addOrDelDefaultFibRouteToSNATForSubnet(subnet, networkId, flowAction, vpnId);
                            });
                }
            }
        } else {
            LOG.debug("addOrDelDefaultFibRouteToSNATForSubnet : Cannot create/remove default FIB route to SNAT flow "
                    + "for subnet {} vpn-instance-op-data entry for network {} does not exist",
                subnetId, networkId);
        }
    }
}

class Waiter2<T extends DataObject> implements DataTreeChangeListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Waiter.class);

    private static Set instances = new HashSet();

    private BiConsumer<T, DataBroker> handler;
    private DataBroker dataBroker;
    private ListenerRegistration<Waiter2> registration;
    private InstanceIdentifier<T> iid;

    public Waiter2(DataBroker dataBroker, LogicalDatastoreType dataStore, InstanceIdentifier<T> iid, BiConsumer<T, DataBroker> handler) {
        this.handler = handler;
        this.dataBroker = dataBroker;
        this.iid = iid;
        final DataTreeIdentifier<T> treeId = new DataTreeIdentifier<>(dataStore, iid);
        LOG.info("Waiter {} waiting on {}", hashCode(), treeId);
        synchronized (instances) {
            this.registration = dataBroker.registerDataTreeChangeListener(treeId, this);
            instances.add(this);
        }
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<T>> collection) {
        for (DataTreeModification<T> change : collection) {
            final InstanceIdentifier<T> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<T> mod = change.getRootNode();

            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.info("Waiter {} got delete?!", hashCode());
                    break;
                case SUBTREE_MODIFIED:
                    LOG.info("Waiter {} got update?!", hashCode());
                    handler.accept(mod.getDataAfter(), this.dataBroker);
                    break;
                case WRITE:
                    LOG.info("Waiter {} got a write update?!", hashCode());
                    break;
                default:
                    // FIXME: May be not a good idea to throw.
                    throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
            }
        }

    }
}

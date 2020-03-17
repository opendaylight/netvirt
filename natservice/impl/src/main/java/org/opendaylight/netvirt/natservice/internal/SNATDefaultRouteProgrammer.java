/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
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
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.natservice.api.NatSwitchCache;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SNATDefaultRouteProgrammer {

    private static final Logger LOG = LoggerFactory.getLogger(SNATDefaultRouteProgrammer.class);
    private final IMdsalApiManager mdsalManager;
    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final ExternalNetworkGroupInstaller extNetGroupInstaller;
    private final NatServiceCounters natServiceCounters;
    private final JobCoordinator jobCoordinator;
    private final NatSwitchCache natSwitchCache;

    @Inject
    public SNATDefaultRouteProgrammer(final IMdsalApiManager mdsalManager, final DataBroker dataBroker,
            final IdManagerService idManager, final ExternalNetworkGroupInstaller extNetGroupInstaller,
            NatServiceCounters natServiceCounters, final JobCoordinator jobCoordinator,
            final NatSwitchCache natSwitchCache) {
        this.mdsalManager = mdsalManager;
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.extNetGroupInstaller = extNetGroupInstaller;
        this.natServiceCounters = natServiceCounters;
        this.jobCoordinator = jobCoordinator;
        this.natSwitchCache = natSwitchCache;
    }

    @Nullable
    private FlowEntity buildDefNATFlowEntity(Uint64 dpId, Uint32 vpnId) {
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
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.L3_FIB_TABLE, defaultIP, vpnId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
            NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }

    @Nullable
    private FlowEntity buildDefNATFlowEntity(Uint64 dpId, Uint32 bgpVpnId, Uint32 routerId) {
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
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(bgpVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.L3_FIB_TABLE, defaultIP, routerId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
            NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;


    }

    public void installDefNATRouteInDPN(Uint64 dpnId, Uint32 vpnId, TypedWriteTransaction<Configuration> confTx) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if (flowEntity == null) {
            LOG.error("installDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        natServiceCounters.installDefaultNatFlow();
        mdsalManager.addFlow(confTx, flowEntity);
    }

    public void installDefNATRouteInDPN(Uint64 dpnId, Uint32 bgpVpnId, Uint32 routerId,
                                        TypedWriteTransaction<Configuration> confTx) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, bgpVpnId, routerId);
        if (flowEntity == null) {
            LOG.error("installDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        natServiceCounters.installDefaultNatFlow();
        mdsalManager.addFlow(confTx, flowEntity);
    }

    public void installDefNATRouteInDPN(Uint64 dpnId, Uint32 vpnId, String subnetId) {
        FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntityForExternalSubnet(dpnId, vpnId, subnetId, idManager);
        if (flowEntity == null) {
            LOG.error("installDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        natServiceCounters.installDefaultNatFlow();
        mdsalManager.installFlow(flowEntity);
    }

    public void removeDefNATRouteInDPN(Uint64 dpnId, Uint32 vpnId, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if (flowEntity == null) {
            LOG.error("removeDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        natServiceCounters.removeDefaultNatFlow();
        mdsalManager.removeFlow(confTx, flowEntity);
    }

    public void removeDefNATRouteInDPN(Uint64 dpnId, Uint32 bgpVpnId, Uint32 routerId,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, bgpVpnId, routerId);
        if (flowEntity == null) {
            LOG.error("removeDefNATRouteInDPN : Flow entity received is NULL."
                    + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        natServiceCounters.removeDefaultNatFlow();
        mdsalManager.removeFlow(confTx, flowEntity);
    }

    public void addOrDelDefaultFibRouteToSNATForSubnet(Subnets subnet, String networkId, int flowAction, Uint32 vpnId) {
        String providerNet = NatUtil.getElanInstancePhysicalNetwok(networkId, dataBroker);
        Set<Uint64> dpnList = natSwitchCache.getSwitchesConnectedToExternal(providerNet);

        for (Uint64 dpn : dpnList) {
            addOrDelDefaultFibRouteToSNATForSubnetInDpn(subnet, networkId, flowAction, vpnId, dpn);
        }
    }

    public void addOrDelDefaultFibRouteToSNATForSubnetInDpn(Subnets subnet, String networkId, int flowAction,
                                                            Uint32 vpnId, Uint64 dpn) {
        String subnetId = subnet.getId().getValue();
        String macAddress = NatUtil.getSubnetGwMac(dataBroker, subnet.getId(), networkId);
        extNetGroupInstaller.installExtNetGroupEntry(new Uuid(networkId), subnet.getId(),
                dpn, macAddress);
        FlowEntity flowEntity = NatUtil.buildDefaultNATFlowEntityForExternalSubnet(dpn,
                vpnId, subnetId, idManager);
        if (flowAction == NwConstants.ADD_FLOW || flowAction == NwConstants.MOD_FLOW) {
            LOG.info("addOrDelDefaultFibRouteToSNATForSubnet : Installing flow {} for subnetId {},"
                    + "vpnId {} on dpn {}", flowEntity, subnetId, vpnId, dpn);
            jobCoordinator.enqueueJob(NatUtil.getDefaultFibRouteToSNATForSubnetJobKey(subnetId, dpn),
                () -> Collections.singletonList(mdsalManager.installFlow(flowEntity)));
        } else {
            LOG.info("addOrDelDefaultFibRouteToSNATForSubnet : Removing flow for subnetId {},"
                    + "vpnId {} with dpn {}", subnetId, vpnId, dpn);
            jobCoordinator.enqueueJob(NatUtil.getDefaultFibRouteToSNATForSubnetJobKey(subnetId, dpn),
                () -> Collections.singletonList(mdsalManager.removeFlow(flowEntity)));
        }
    }
}

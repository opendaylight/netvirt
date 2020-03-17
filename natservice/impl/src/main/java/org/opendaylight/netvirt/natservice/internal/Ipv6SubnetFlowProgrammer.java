/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.netvirt.natservice.internal.AbstractSnatService.LOAD_END;
import static org.opendaylight.netvirt.natservice.internal.AbstractSnatService.LOAD_START;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadMetadata;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6SubnetFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6SubnetFlowProgrammer.class);
    protected final DataBroker dataBroker;
    protected final IMdsalApiManager mdsalManager;

    @Inject
    public Ipv6SubnetFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
    }

    public void addSubnetSpecificFlows(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                       Uint32 routerId, Routers routers, Uint64 routerMetadata) {
        String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routers.getRouterName());
        for (ExternalIps externalIp : routers.getExternalIps()) {
            if (NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // Skip ipv4 subnets in the external network
                continue;
            }

            // Currently we only handle one external IPv6 address per router, others if present will be ignored.
            Uint32 extSubnetId  = NatUtil.getExternalSubnetVpnId(dataBroker, externalIp.getSubnetId());

            Uint64 extIpv6SubnetMetadata = MetaDataUtil.getVpnIdMetadata(extSubnetId.longValue());
            LOG.info("addSubnetSpecificFlows : flows on NAPTSwitch {} for routerId {}, routerName {},"
                    + " extIPv6Address {} Installing", dpnId, routerId, routers.getRouterName(),
                    externalIp.getIpAddress());

            // Program flows to handle ingress traffic coming over the tunnel port (i.e., from tableId 36 to 44)
            addIpv6InboundTerminatingServiceTblEntry(confTx, extSubnetId, extIpv6SubnetMetadata, dpnId, routerId);

            // Program flows in OUTBOUND_NAPT_TABLE(46) with action to send packets to NAPT_PFIB_TABLE(47)
            addIPv6FlowToUpdateSrcMacToRouterGwMac(confTx, extGwMacAddress, extSubnetId, dpnId, routerId,
                    routerMetadata);

            for (Uuid subnetId : routers.getSubnetIds()) {
                String tenantSubnetCidr = NatUtil.getSubnetIp(dataBroker, subnetId);
                if (!NatUtil.isIPv6Subnet(tenantSubnetCidr)) {
                    // Skip ipv4 subnets in the tenant network
                    continue;
                }

                LOG.info("addSubnetSpecificFlows : flows for NAPTSwitch {} for routerName {},"
                        + " tenantSubnetCidr {}, Installing",
                        dpnId, routers.getRouterName(), tenantSubnetCidr);

                // Program flows from NAPT_PFIB_TABLE(47) to FIB_TABLE(21) (egress direction)
                addIpv6NaptPfibOutboundFlow(confTx, tenantSubnetCidr, extIpv6SubnetMetadata, dpnId, routerId);
                // Program flows from FIB_TABLE(21) to INBOUND_NAPT_TABLE(44) (ingress direction)
                addIpv6NaptInboundFibEntry(confTx, extSubnetId, tenantSubnetCidr, extIpv6SubnetMetadata,
                        dpnId, routerId);
                // Program flows from INBOUND_NAPT_TABLE(44) to NAPT_PFIB_TABLE(47) (ingress direction)
                addIpv6NaptInboundNaptFlow(confTx, extSubnetId, tenantSubnetCidr, extIpv6SubnetMetadata,
                        dpnId, routerId, routerMetadata);
            }
        }
    }

    public void removeSubnetSpecificFlows(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                          Uint32 routerId, Routers routers)
            throws ExecutionException, InterruptedException {
        for (ExternalIps externalIp : routers.getExternalIps()) {
            if (NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // Skip ipv4 subnets in the external network
                continue;
            }
            // Currently we only handle one external IPv6 address per router, others if present will be ignored.
            LOG.info("removeSubnetSpecificFlows : flows on NAPTSwitch {} for routerId {}, routerName {},"
                    + " extIPv6Address {}, Removing",
                    dpnId, routerId, routers.getRouterName(), externalIp.getIpAddress());

            // Program flows to handle ingress traffic coming over the tunnel port (i.e., from tableId 36 to 44)
            removeIpv6InboundTerminatingServiceTblEntry(confTx, dpnId, routerId);

            // Program flows in OUTBOUND_NAPT_TABLE(46) with action to send packets to NAPT_PFIB_TABLE(47)
            removeIPv6FlowToUpdateSrcMacToRouterGwMac(confTx, dpnId, routerId);

            for (Uuid subnetId : routers.getSubnetIds()) {
                String tenantSubnetCidr = NatUtil.getSubnetIp(dataBroker, subnetId);
                if (!NatUtil.isIPv6Subnet(tenantSubnetCidr)) {
                    // Skip ipv4 subnets in the tenant network
                    continue;
                }

                LOG.info("removeSubnetSpecificFlows : flows for NAPTSwitch {} for routerName {},"
                        + " tenantSubnetCidr {}, Removing",
                        dpnId, routers.getRouterName(), tenantSubnetCidr);

                // Program flows from NAPT_PFIB_TABLE(47) to FIB_TABLE(21) (egress direction)
                removeIpv6NaptPfibOutboundFlow(confTx, tenantSubnetCidr, dpnId, routerId);
                // Program flows from FIB_TABLE(21) to INBOUND_NAPT_TABLE(44) (ingress direction)
                removeIpv6NaptInboundFibEntry(confTx, tenantSubnetCidr, dpnId, routerId);
                // Program flows from INBOUND_NAPT_TABLE(44) to NAPT_PFIB_TABLE(47) (ingress direction)
                removeIpv6NaptInboundNaptFlow(confTx, tenantSubnetCidr, dpnId, routerId);
            }
        }
    }

    private void addIpv6InboundTerminatingServiceTblEntry(TypedReadWriteTransaction<Configuration> confTx,
                                                          Uint32 extSubnetId, Uint64 extIpv6SubnetMetadata,Uint64 dpnId,
                                                          Uint32 routerId) {
        // Install the tunnel table entry in NAPT Switch for inbound traffic from a non NAPT Switch.
        LOG.debug("addIpv6InboundTerminatingServiceTblEntry : entry for Terminating Service Table for switch {},"
                + " routerId {}, Installing", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        if (extSubnetId == NatConstants.INVALID_ID) {
            LOG.error("addIpv6InboundTerminatingServiceTblEntry : external subnet id is invalid.");
            return;
        }
        matches.add(new MatchTunnelId(Uint64.valueOf(extSubnetId)));
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(extIpv6SubnetMetadata, LOAD_START, LOAD_END);
        actionsInfos.add(actionLoadMeta);
        actionsInfos.add(new ActionNxResubmit(NwConstants.INBOUND_NAPT_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        flowRef = flowRef + ".Inbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.SNAT_FIB_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    private void removeIpv6InboundTerminatingServiceTblEntry(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32 routerId) throws ExecutionException, InterruptedException {
        // Install the tunnel table entry in NAPT Switch for inbound traffic from a non NAPT Switch.
        LOG.debug("removeIpv6InboundTerminatingServiceTblEntry : entry for Terminating Service Table for switch {},"
                + " routerId {}, Removing", dpnId, routerId);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        flowRef = flowRef + ".Inbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef);
    }

    private void addIPv6FlowToUpdateSrcMacToRouterGwMac(TypedReadWriteTransaction<Configuration> confTx,
            String extGwMacAddress, Uint32 extSubnetId, Uint64 dpnId, Uint32 routerId, Uint64 routerMetadata) {
        LOG.debug("addIPv6FlowToUpdateSrcMacToRouterGwMac : called for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        listActionInfo.add(new ActionSetFieldEthernetSource(new MacAddress(extGwMacAddress)));
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                .getVpnIdMetadata(extSubnetId.longValue()), LOAD_START, LOAD_END);
        listActionInfo.add(actionLoadMeta);
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.NAPT_PFIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,
                NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo);
    }

    private void removeIPv6FlowToUpdateSrcMacToRouterGwMac(TypedReadWriteTransaction<Configuration> confTx,
             Uint64 dpnId, Uint32 routerId)
                    throws ExecutionException, InterruptedException {
        LOG.debug("removeIPv6FlowToUpdateSrcMacToRouterGwMac : called for switch {}, routerId {}", dpnId, routerId);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef);
    }

    private void addIpv6NaptPfibOutboundFlow(TypedReadWriteTransaction<Configuration> confTx,
            String tenantIpv6SubnetCidr, Uint64 extIpv6SubnetMetadata, Uint64 dpnId, Uint32 routerId) {
        LOG.debug("addIpv6NaptPfibOutboundFlow : called for NAPTSwitch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(extIpv6SubnetMetadata, MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(new MatchIpv6Source(tenantIpv6SubnetCidr));

        List<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructions = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(Uint64.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructions.add(new InstructionApplyActions(listActionInfo));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Outbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef,
                NatConstants.SNAT_TRK_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    private void removeIpv6NaptPfibOutboundFlow(TypedReadWriteTransaction<Configuration> confTx,
            String tenantIpv6SubnetCidr, Uint64 dpnId, Uint32 routerId)
                    throws ExecutionException, InterruptedException {
        LOG.debug("removeIpv6NaptPfibOutboundFlow : called for NAPTSwitch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Outbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef);
    }

    private void addIpv6NaptInboundFibEntry(TypedReadWriteTransaction<Configuration> confTx, Uint32 extSubnetId,
            String tenantIpv6SubnetCidr, Uint64 extIpv6SubnetMetadata, Uint64 dpnId, Uint32 routerId) {
        LOG.debug("addIpv6NaptInboundFibEntry : called for NAPTSwitch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        if (extSubnetId == NatConstants.INVALID_ID) {
            LOG.error("installIpv6NaptInboundFibEntry: external subnet id is invalid.");
            return;
        }
        matches.add(new MatchMetadata(extIpv6SubnetMetadata, MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(new MatchIpv6Destination(tenantIpv6SubnetCidr));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.INBOUND_NAPT_TABLE));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.L3_FIB_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Inbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.SNAT_FIB_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    private void removeIpv6NaptInboundFibEntry(TypedReadWriteTransaction<Configuration> confTx,
            String tenantIpv6SubnetCidr, Uint64 dpnId, Uint32 routerId)
                    throws ExecutionException, InterruptedException {
        LOG.debug("removeIpv6NaptInboundFibEntry : called for NAPTSwitch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.L3_FIB_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Inbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef);
    }

    private void addIpv6NaptInboundNaptFlow(TypedReadWriteTransaction<Configuration> confTx, Uint32 extSubnetId,
            String tenantIpv6SubnetCidr, Uint64 extIpv6SubnetMetadata, Uint64 dpnId, Uint32 routerId,
            Uint64 routerMetadata) {
        LOG.debug("addIpv6NaptInboundNaptFlow : called for switch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        if (extSubnetId == NatConstants.INVALID_ID) {
            LOG.error("installIpv6NaptInboundNaptFlow : external subnet id is invalid.");
            return;
        }
        matches.add(new MatchMetadata(extIpv6SubnetMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        matches.add(new MatchIpv6Destination(tenantIpv6SubnetCidr));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.NAPT_PFIB_TABLE));
        instructions.add(new InstructionWriteMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Inbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    private void removeIpv6NaptInboundNaptFlow(TypedReadWriteTransaction<Configuration> confTx,
            String tenantIpv6SubnetCidr,Uint64 dpnId, Uint32 routerId)
                    throws ExecutionException, InterruptedException {
        LOG.debug("removeIpv6NaptInboundNaptFlow : called for switch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Inbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef);
    }
}

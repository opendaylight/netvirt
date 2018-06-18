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

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6SubnetFlowProgrammer implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6SubnetFlowProgrammer.class);
    protected final DataBroker dataBroker;
    protected final IMdsalApiManager mdsalManager;
    protected final BigInteger dpnId;
    protected final Routers routers;
    protected final Long routerId;
    protected final BigInteger routerMetadata;
    protected final int addOrRemove;

    protected Ipv6SubnetFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                       final BigInteger dpnId, final Routers routers, final Long routerId,
                                       final BigInteger routerMetadata, final int addOrRemove) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.dpnId = dpnId;
        this.routers = routers;
        this.routerId = routerId;
        this.routerMetadata = routerMetadata;
        this.addOrRemove = addOrRemove;
    }

    @Override
    public String toString() {
        return "Ipv6SubnetFlowProgrammer [dpnId=" + dpnId + ", Routers=" + routers + ", routerMetadata="
                + routerMetadata + ", addOrRemove=" + addOrRemove + "]";
    }

    public List<ListenableFuture<Void>> programSubnetSpecificFlows() {
        String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routers.getRouterName());
        WriteTransaction writeFlowTx = dataBroker.newWriteOnlyTransaction();
        for (ExternalIps externalIp : routers.getExternalIps()) {
            if (NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // Skip ipv4 subnets in the external network
                continue;
            }

            // Currently we only handle one external IPv6 address per router, others if present will be ignored.
            long extSubnetId = NatConstants.INVALID_ID;
            if (addOrRemove == NwConstants.ADD_FLOW) {
                extSubnetId = NatUtil.getExternalSubnetVpnId(dataBroker, externalIp.getSubnetId());
            }

            BigInteger extIpv6SubnetMetadata = MetaDataUtil.getVpnIdMetadata(extSubnetId);
            LOG.info("programSubnetSpecificFlows : {} flows on NAPTSwitch {} for routerId {}, routerName {},"
                    + " extIPv6Address {}", (addOrRemove == NwConstants.ADD_FLOW) ? "Installing" : "Removing",
                    dpnId, routerId, routers.getRouterName(), externalIp.getIpAddress());

            // Program flows to handle ingress traffic coming over the tunnel port (i.e., from tableId 36 to 44)
            installIpv6InboundTerminatingServiceTblEntry(extSubnetId, extIpv6SubnetMetadata, writeFlowTx);

            // Program flows in OUTBOUND_NAPT_TABLE(46) with action to send packets to NAPT_PFIB_TABLE(47)
            installIPv6FlowToUpdateSrcMacToRouterGwMac(extGwMacAddress, extSubnetId, writeFlowTx);

            for (Uuid subnetId : routers.getSubnetIds()) {
                String tenantSubnetCidr = NatUtil.getSubnetIp(dataBroker, subnetId);
                if (!NatUtil.isIPv6Subnet(tenantSubnetCidr)) {
                    // Skip ipv4 subnets in the tenant network
                    continue;
                }

                LOG.info("programSubnetSpecificFlows : {} flows for NAPTSwitch {} for routerName {},"
                        + " tenantSubnetCidr {}", (addOrRemove == NwConstants.ADD_FLOW) ? "Installing" : "Removing",
                        dpnId, routers.getRouterName(), tenantSubnetCidr);

                // Program flows from NAPT_PFIB_TABLE(47) to FIB_TABLE(21) (egress direction)
                installIpv6NaptPfibOutboundFlow(tenantSubnetCidr, extIpv6SubnetMetadata, writeFlowTx);
                // Program flows from FIB_TABLE(21) to INBOUND_NAPT_TABLE(44) (ingress direction)
                installIpv6NaptInboundFibEntry(extSubnetId, tenantSubnetCidr, extIpv6SubnetMetadata, writeFlowTx);
                // Program flows from INBOUND_NAPT_TABLE(44) to NAPT_PFIB_TABLE(47) (ingress direction)
                installIpv6NaptInboundNaptFlow(extSubnetId, tenantSubnetCidr, extIpv6SubnetMetadata, writeFlowTx);
            }
            return Collections.singletonList(waitForTransactionToComplete(writeFlowTx));
        }
        return Collections.emptyList();
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        return programSubnetSpecificFlows();
    }

    protected void installIpv6InboundTerminatingServiceTblEntry(long extSubnetId,
                                                                BigInteger extIpv6SubnetMetadata,
                                                                WriteTransaction writeFlowTx) {
        // Install the tunnel table entry in NAPT Switch for inbound traffic from a non NAPT Switch.
        LOG.debug("installIpv6InboundTerminatingServiceTblEntry : {} entry for Terminating Service Table for switch {},"
                + " routerId {}, routerName {}", (addOrRemove == NwConstants.ADD_FLOW) ? "Installing" : "Removing",
                dpnId, routerId, routers.getRouterName());
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            if (extSubnetId == NatConstants.INVALID_ID) {
                LOG.error("installIpv6InboundTerminatingServiceTblEntry : external subnet id is invalid.");
                return;
            }
            matches.add(new MatchTunnelId(BigInteger.valueOf(extSubnetId)));
            ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(extIpv6SubnetMetadata, LOAD_START, LOAD_END);
            actionsInfos.add(actionLoadMeta);
        }
        actionsInfos.add(new ActionNxResubmit(NwConstants.INBOUND_NAPT_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        flowRef = flowRef + ".Inbound";
        NatUtil.syncFlow(mdsalManager, dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.SNAT_FIB_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, writeFlowTx, addOrRemove);
    }

    protected void installIPv6FlowToUpdateSrcMacToRouterGwMac(String extGwMacAddress,
                                                              long extSubnetId, WriteTransaction writeFlowTx) {
        LOG.debug("installIPv6FlowToUpdateSrcMacToRouterGwMac : called for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            listActionInfo.add(new ActionSetFieldEthernetSource(new MacAddress(extGwMacAddress)));
            ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                    .getVpnIdMetadata(extSubnetId), LOAD_START, LOAD_END);
            listActionInfo.add(actionLoadMeta);
        }
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.NAPT_PFIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.syncFlow(mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,
                NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, writeFlowTx, addOrRemove);
    }

    protected void installIpv6NaptPfibOutboundFlow(String tenantIpv6SubnetCidr,
                                                   BigInteger extIpv6SubnetMetadata, WriteTransaction writeFlowTx) {
        LOG.debug("installIpv6NaptPfibOutboundFlow : called for NAPTSwitch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(extIpv6SubnetMetadata, MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(new MatchIpv6Source(tenantIpv6SubnetCidr));

        List<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructions = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructions.add(new InstructionApplyActions(listActionInfo));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Outbound";
        NatUtil.syncFlow(mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef,
                NatConstants.SNAT_TRK_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions, writeFlowTx, addOrRemove);
    }

    protected void installIpv6NaptInboundFibEntry(long extSubnetId, String tenantIpv6SubnetCidr,
                                                  BigInteger extIpv6SubnetMetadata, WriteTransaction writeFlowTx) {
        LOG.debug("installIpv6NaptInboundFibEntry : called for NAPTSwitch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            if (extSubnetId == NatConstants.INVALID_ID) {
                LOG.error("installIpv6NaptInboundFibEntry: external subnet id is invalid.");
                return;
            }
            matches.add(new MatchMetadata(extIpv6SubnetMetadata, MetaDataUtil.METADATA_MASK_VRFID));
        }
        matches.add(new MatchIpv6Destination(tenantIpv6SubnetCidr));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.INBOUND_NAPT_TABLE));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.L3_FIB_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Inbound";
        NatUtil.syncFlow(mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.SNAT_FIB_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, writeFlowTx, addOrRemove);
    }

    protected void installIpv6NaptInboundNaptFlow(long extSubnetId, String tenantIpv6SubnetCidr,
                                                  BigInteger extIpv6SubnetMetadata, WriteTransaction writeFlowTx) {
        LOG.debug("installIpv6NaptInboundNaptFlow : called for switch {}, routerId {}, tenantIPv6Cidr {}",
                dpnId, routerId, tenantIpv6SubnetCidr);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            if (extSubnetId == NatConstants.INVALID_ID) {
                LOG.error("installIpv6NaptInboundNaptFlow : external subnet id is invalid.");
                return;
            }
            matches.add(new MatchMetadata(extIpv6SubnetMetadata, MetaDataUtil.METADATA_MASK_VRFID));
        }
        matches.add(new MatchIpv6Destination(tenantIpv6SubnetCidr));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.NAPT_PFIB_TABLE));
        instructions.add(new InstructionWriteMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId);
        flowRef += NatConstants.FLOWID_SEPARATOR + tenantIpv6SubnetCidr + ".Inbound";
        NatUtil.syncFlow(mdsalManager, dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions, writeFlowTx, addOrRemove);
    }

    public static CheckedFuture<Void, TransactionCommitFailedException> waitForTransactionToComplete(
            WriteTransaction tx) {
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Ipv6SubnetFlowProgrammer: Error writing to datastore {}", e);
        }
        return futures;
    }
}

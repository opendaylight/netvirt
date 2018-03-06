/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.MatchCriteria;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the implementation for egress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */
public class EgressAclServiceImpl extends AbstractAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(EgressAclServiceImpl.class);

    /**
     * Initialize the member variables.
     */
    public EgressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils, JobCoordinator jobCoordinator, AclInterfaceCache aclInterfaceCache) {
        // Service mode is w.rt. switch
        super(ServiceModeIngress.class, dataBroker, mdsalManager, aclDataUtil, aclServiceUtils,
                jobCoordinator, aclInterfaceCache);
    }

    /**
     * Bind service.
     *
     * @param aclInterface the acl interface
     */
    @Override
    public void bindService(AclInterface aclInterface) {
        String interfaceName = aclInterface.getInterfaceId();
        jobCoordinator.enqueueJob(interfaceName, () -> {
            int instructionKey = 0;
            List<Instruction> instructions = new ArrayList<>();
            instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(getAclAntiSpoofingTable(), ++instructionKey));
            short serviceIndex = ServiceIndex.getIndex(AclConstants.INGRESS_ACL_SERVICE_NAME,
                    AclConstants.INGRESS_ACL_SERVICE_INDEX);
            int flowPriority = AclConstants.INGRESS_ACL_SERVICE_INDEX;
            BoundServices serviceInfo =
                    AclServiceUtils.getBoundServices(String.format("%s.%s.%s", "acl", "ingressacl", interfaceName),
                            serviceIndex, flowPriority, AclConstants.COOKIE_ACL_BASE, instructions);
            InstanceIdentifier<BoundServices> path =
                    AclServiceUtils.buildServiceId(interfaceName, serviceIndex, serviceMode);

            return Collections.singletonList(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> tx.put(LogicalDatastoreType.CONFIGURATION,
                            path, serviceInfo, WriteTransaction.CREATE_MISSING_PARENTS)));
        });
    }

    /**
     * Unbind service.
     *
     * @param aclInterface the acl interface
     */
    @Override
    protected void unbindService(AclInterface aclInterface) {
        String interfaceName = aclInterface.getInterfaceId();
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                ServiceIndex.getIndex(NwConstants.ACL_SERVICE_NAME, NwConstants.ACL_SERVICE_INDEX), serviceMode);

        LOG.debug("UnBinding ACL service for interface {}", interfaceName);
        jobCoordinator.enqueueJob(interfaceName, () -> Collections.singletonList(txRunner
                .callWithNewWriteOnlyTransactionAndSubmit(tx -> tx.delete(LogicalDatastoreType.CONFIGURATION, path))));
    }

    @Override
    protected void programAntiSpoofingRules(AclInterface port, List<AllowedAddressPairs> allowedAddresses,
            Action action, int addOrRemove) {
        LOG.info("{} programAntiSpoofingRules for port {}, AAPs={}, action={}, addOrRemove={}", this.directionString,
                port.getInterfaceId(), allowedAddresses, action, addOrRemove);

        BigInteger dpid = port.getDpId();
        int lportTag = port.getLPortTag();
        if (action != Action.UPDATE) {
            egressAclDhcpDropServerTraffic(dpid, lportTag, addOrRemove);
            egressAclDhcpv6DropServerTraffic(dpid, lportTag, addOrRemove);
            egressAclIcmpv6DropRouterAdvts(dpid, lportTag, addOrRemove);
            egressAclIcmpv6AllowedList(dpid, lportTag, addOrRemove);
            programL2BroadcastAllowRule(port, addOrRemove);
        }
        List<AllowedAddressPairs> filteredAAPs = AclServiceUtils.excludeMulticastAAPs(allowedAddresses);

        egressAclDhcpAllowClientTraffic(dpid, filteredAAPs, lportTag, addOrRemove);
        egressAclDhcpv6AllowClientTraffic(dpid, filteredAAPs, lportTag, addOrRemove);
        programArpRule(dpid, filteredAAPs, lportTag, addOrRemove);
    }

    @Override
    protected void programRemoteAclTableFlow(BigInteger dpId, Integer aclTag, AllowedAddressPairs aap,
            int addOrRemove) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.addAll(AclServiceUtils.buildIpAndDstServiceMatch(aclTag, aap));

        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclCommitterTable());
        String flowNameAdded = "Acl_Filter_Egress_" + String.valueOf(aap.getIpAddress().getValue()) + "_" + aclTag;

        syncFlow(dpId, getAclRemoteAclTable(), flowNameAdded, AclConstants.ACL_DEFAULT_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
    }

    @Override
    protected void programGotoClassifierTableRules(BigInteger dpId, List<AllowedAddressPairs> aaps, int lportTag,
            int addOrRemove) {
        List<AllowedAddressPairs> filteredAAPs = AclServiceUtils.excludeMulticastAAPs(aaps);
        for (AllowedAddressPairs aap : filteredAAPs) {
            IpPrefixOrAddress attachIp = aap.getIpAddress();
            MacAddress mac = aap.getMacAddress();

            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
            matches.add(new MatchEthernetSource(mac));
            matches.addAll(AclServiceUtils.buildIpMatches(attachIp, MatchCriteria.MATCH_SOURCE));

            List<InstructionInfo> gotoInstructions = new ArrayList<>();
            gotoInstructions.add(new InstructionGotoTable(getAclConntrackClassifierTable()));

            String flowName = "Egress_Fixed_Goto_Classifier_" + dpId + "_" + lportTag + "_" + mac.getValue() + "_"
                    + String.valueOf(attachIp.getValue());
            syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, gotoInstructions, addOrRemove);
        }
    }

    /**
     * Anti-spoofing rule to block the Ipv4 DHCP server traffic from the port.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    protected void egressAclDhcpDropServerTraffic(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_SERVER_PORT_IPV4,
                AclConstants.DHCP_CLIENT_PORT_IPV4, lportTag, serviceMode);

        String flowName = "Egress_DHCP_Server_v4" + dpId + "_" + lportTag + "_Drop_";
        syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, Collections.emptyList(), addOrRemove);
    }

    /**
     * Anti-spoofing rule to block the Ipv6 DHCP server traffic from the port.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    protected void egressAclDhcpv6DropServerTraffic(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_SERVER_PORT_IPV6,
                AclConstants.DHCP_CLIENT_PORT_IPV6, lportTag, serviceMode);

        String flowName = "Egress_DHCP_Server_v6" + "_" + dpId + "_" + lportTag + "_Drop_";
        syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, Collections.emptyList(), addOrRemove);
    }

    /**
     * Anti-spoofing rule to block the Ipv6 Router Advts from the VM port.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    private void egressAclIcmpv6DropRouterAdvts(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches =
                AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_RA, 0, lportTag, serviceMode);

        String flowName = "Egress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_RA + "_Drop_";
        syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_IPV6_DROP_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, Collections.emptyList(), addOrRemove);
    }

    /**
     * Add rule to allow certain ICMPv6 traffic from VM ports.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    private void egressAclIcmpv6AllowedList(BigInteger dpId, int lportTag, int addOrRemove) {
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        for (Integer icmpv6Type: AclConstants.allowedIcmpv6NdList()) {
            List<MatchInfoBase> matches = AclServiceUtils.buildIcmpV6Matches(icmpv6Type, 0, lportTag, serviceMode);
            String flowName = "Egress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + icmpv6Type + "_Permit_";
            syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is allowed.
     *
     * @param dpId the dpid
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void egressAclDhcpAllowClientTraffic(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, int addOrRemove) {
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        for (AllowedAddressPairs aap : allowedAddresses) {
            if (!AclServiceUtils.isIPv4Address(aap)) {
                continue;
            }
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.addAll(AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_CLIENT_PORT_IPV4,
                AclConstants.DHCP_SERVER_PORT_IPV4, lportTag, serviceMode));
            matches.add(new MatchEthernetSource(aap.getMacAddress()));

            String flowName =
                    "Egress_DHCP_Client_v4" + dpId + "_" + lportTag + "_" + aap.getMacAddress().getValue() + "_Permit_";
            syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY,
                    "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void egressAclDhcpv6AllowClientTraffic(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, int addOrRemove) {
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        for (AllowedAddressPairs aap : allowedAddresses) {
            if (AclServiceUtils.isIPv4Address(aap)) {
                continue;
            }
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.addAll(AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_CLIENT_PORT_IPV6,
                AclConstants.DHCP_SERVER_PORT_IPV6, lportTag, serviceMode));
            matches.add(new MatchEthernetSource(aap.getMacAddress()));

            String flowName = "Egress_DHCP_Client_v6" + "_" + dpId + "_" + lportTag + "_"
                    + aap.getMacAddress().getValue() + "_Permit_";
            syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY,
                    "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Adds the rule to allow arp packets.
     *
     * @param dpId the dpId
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programArpRule(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses, int lportTag,
            int addOrRemove) {
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            if (!AclServiceUtils.isIPv4Address(allowedAddress)) {
                continue; // For IPv6 allowed addresses
            }

            IpPrefixOrAddress allowedAddressIp = allowedAddress.getIpAddress();
            MacAddress allowedAddressMac = allowedAddress.getMacAddress();
            List<MatchInfoBase> arpIpMatches = AclServiceUtils.buildArpIpMatches(allowedAddressIp);
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(MatchEthernetType.ARP);
            matches.add(new MatchArpSha(allowedAddressMac));
            matches.add(new MatchEthernetSource(allowedAddressMac));
            matches.addAll(arpIpMatches);
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));

            List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
            LOG.debug("{} ARP Rule on DPID {}, lportTag {}",
                    addOrRemove == NwConstants.DEL_FLOW ? "Deleting" : "Adding", dpId, lportTag);
            String flowName = "Egress_ARP_" + dpId + "_" + lportTag + "_" + allowedAddress.getMacAddress().getValue()
                    + String.valueOf(allowedAddressIp.getValue());
            syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_ARP_TRAFFIC_MATCH_PRIORITY, "ACL", 0,
                    0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Programs broadcast rules.
     *
     * @param port the Acl Interface port
     * @param addOrRemove whether to delete or add flow
     */
    @Override
    protected void programBroadcastRules(AclInterface port, int addOrRemove) {
        programL2BroadcastAllowRule(port, addOrRemove);
    }

    /**
     * Programs Non-IP broadcast rules.
     *
     * @param port the Acl Interface port
     * @param addOrRemove whether to delete or add flow
     */
    private void programL2BroadcastAllowRule(AclInterface port, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        int lportTag = port.getLPortTag();
        List<AllowedAddressPairs> allowedAddresses = port.getAllowedAddressPairs();
        Set<MacAddress> macs = allowedAddresses.stream().map(aap -> aap.getMacAddress()).collect(Collectors.toSet());
        for (MacAddress mac : macs) {
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(new MatchEthernetSource(mac));
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));

            List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

            String flowName = "Egress_L2Broadcast_" + dpId + "_" + lportTag + "_" + mac.getValue();
            syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_L2BROADCAST_TRAFFIC_MATCH_PRIORITY,
                    "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    @Override
    protected boolean isValidDirection(Class<? extends DirectionBase> direction) {
        return direction.equals(DirectionEgress.class);
    }

    @Override
    protected short getAclAntiSpoofingTable() {
        return NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE;
    }

    @Override
    protected short getAclConntrackClassifierTable() {
        return NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE;
    }

    @Override
    protected short getAclConntrackSenderTable() {
        return NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE;
    }

    @Override
    protected short getAclForExistingTrafficTable() {
        return NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE;
    }

    @Override
    protected short getAclFilterCumDispatcherTable() {
        return NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE;
    }

    @Override
    protected short getAclRuleBasedFilterTable() {
        return NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE;
    }

    @Override
    protected short getAclRemoteAclTable() {
        return NwConstants.INGRESS_REMOTE_ACL_TABLE;
    }

    @Override
    protected short getAclCommitterTable() {
        return NwConstants.INGRESS_ACL_COMMITTER_TABLE;
    }
}

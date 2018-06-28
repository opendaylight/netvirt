/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import static org.opendaylight.controller.md.sal.binding.api.WriteTransaction.CREATE_MISSING_PARENTS;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.MatchCriteria;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.port.subnet.SubnetInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the implementation for ingress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */
public class IngressAclServiceImpl extends AbstractAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(IngressAclServiceImpl.class);

    /**
     * Initialize the member variables.
     *
     * @param dataBroker the data broker instance.
     * @param mdsalManager the mdsal manager.
     * @param aclDataUtil the acl data util.
     * @param aclServiceUtils the acl service util.
     * @param jobCoordinator the job coordinator
     * @param aclInterfaceCache the acl interface cache
     */
    public IngressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils, JobCoordinator jobCoordinator, AclInterfaceCache aclInterfaceCache) {
        // Service mode is w.rt. switch
        super(ServiceModeEgress.class, dataBroker, mdsalManager, aclDataUtil, aclServiceUtils, jobCoordinator,
                aclInterfaceCache);
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
            instructions.add(
                    MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.EGRESS_ACL_DUMMY_TABLE, ++instructionKey));
            int flowPriority = NwConstants.EGRESS_ACL_SERVICE_INDEX;
            short serviceIndex =
                    ServiceIndex.getIndex(NwConstants.EGRESS_ACL_SERVICE_NAME, NwConstants.EGRESS_ACL_SERVICE_INDEX);
            BoundServices serviceInfo =
                    AclServiceUtils.getBoundServices(String.format("%s.%s.%s", "acl", "egressacl", interfaceName),
                            serviceIndex, flowPriority, AclConstants.COOKIE_ACL_BASE, instructions);
            InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                    ServiceIndex.getIndex(NwConstants.EGRESS_ACL_SERVICE_NAME, NwConstants.EGRESS_ACL_SERVICE_INDEX),
                    serviceMode);

            return Collections.singletonList(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> tx.put(
                            path, serviceInfo, CREATE_MISSING_PARENTS)));
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
                ServiceIndex.getIndex(NwConstants.EGRESS_ACL_SERVICE_NAME, NwConstants.EGRESS_ACL_SERVICE_INDEX),
                serviceMode);

        LOG.debug("UnBinding ACL service for interface {}", interfaceName);
        jobCoordinator.enqueueJob(interfaceName, () -> Collections.singletonList(txRunner
                .callWithNewWriteOnlyTransactionAndSubmit(tx -> tx.delete(LogicalDatastoreType.CONFIGURATION, path))));
    }

    @Override
    protected void programAntiSpoofingRules(AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, Action action, int addOrRemove) {
        LOG.info("{} programAntiSpoofingRules for port {}, AAPs={}, action={}, addOrRemove={}", this.directionString,
                port.getInterfaceId(), allowedAddresses, action, addOrRemove);

        BigInteger dpid = port.getDpId();
        int lportTag = port.getLPortTag();
        if (action == Action.ADD || action == Action.REMOVE) {
            programCommitterDropFlow(dpid, lportTag, addOrRemove);
            ingressAclDhcpAllowServerTraffic(dpid, lportTag, addOrRemove);
            ingressAclDhcpv6AllowServerTraffic(dpid, lportTag, addOrRemove);
            ingressAclIcmpv6AllowedTraffic(port, addOrRemove);
            programIcmpv6RARule(port, port.getSubnetInfo(), addOrRemove);

            programArpRule(dpid, lportTag, addOrRemove);
            programIpv4BroadcastRule(port, addOrRemove);
        }
    }

    private void programCommitterDropFlow(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        BigInteger metaData = MetaDataUtil.METADATA_MASK_ACL_DROP
                .and(AclConstants.METADATA_DROP_FLAG.shiftLeft(2));
        BigInteger metaDataMask = MetaDataUtil.METADATA_MASK_ACL_DROP
                .and(AclConstants.METADATA_DROP_FLAG.shiftLeft(2));
        matches.add(new NxMatchRegister(NxmNxReg6.class, MetaDataUtil.getLportTagForReg6(lportTag).longValue(),
                MetaDataUtil.getLportTagMaskForReg6()));
        matches.add(new MatchMetadata(metaData, metaDataMask));

        String flowName = "Ingress_" + dpId + "_" + lportTag + "_Drop";
        syncFlow(dpId, getAclCommitterTable(), flowName, AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY,
                "ACL", 0, 0, AclServiceUtils.getDropFlowCookie(lportTag), matches, instructions, addOrRemove);
    }

    @Override
    protected void programGotoClassifierTableRules(BigInteger dpId, List<AllowedAddressPairs> aaps, int lportTag,
            int addOrRemove) {
        for (AllowedAddressPairs aap : aaps) {
            IpPrefixOrAddress attachIp = aap.getIpAddress();
            MacAddress mac = aap.getMacAddress();

            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
            matches.add(new MatchEthernetDestination(mac));
            matches.addAll(AclServiceUtils.buildIpMatches(attachIp, MatchCriteria.MATCH_DESTINATION));

            List<InstructionInfo> gotoInstructions = new ArrayList<>();
            gotoInstructions.add(new InstructionGotoTable(getAclConntrackClassifierTable()));

            String flowName = "Ingress_Fixed_Goto_Classifier_" + dpId + "_" + lportTag + "_" + mac.getValue() + "_"
                    + String.valueOf(attachIp.getValue());
            syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, gotoInstructions, addOrRemove);
        }
    }

    @Override
    protected void programRemoteAclTableFlow(BigInteger dpId, Integer aclTag, AllowedAddressPairs aap,
            int addOrRemove) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.addAll(AclServiceUtils.buildIpAndSrcServiceMatch(aclTag, aap));

        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclCommitterTable());
        String flowNameAdded = "Acl_Filter_Ingress_" + String.valueOf(aap.getIpAddress().getValue()) + "_" + aclTag;

        syncFlow(dpId, getAclRemoteAclTable(), flowNameAdded, AclConstants.ACL_DEFAULT_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     */
    protected void ingressAclDhcpAllowServerTraffic(BigInteger dpId, int lportTag, int addOrRemove) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_SERVER_PORT_IPV4,
                AclConstants.DHCP_CLIENT_PORT_IPV4, lportTag, serviceMode);
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        String flowName = "Ingress_DHCP_Server_v4" + dpId + "_" + lportTag + "_Permit_";
        syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     */
    protected void ingressAclDhcpv6AllowServerTraffic(BigInteger dpId, int lportTag, int addOrRemove) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_SERVER_PORT_IPV6,
                AclConstants.DHCP_CLIENT_PORT_IPV6, lportTag, serviceMode);
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        String flowName = "Ingress_DHCP_Server_v6" + "_" + dpId + "_" + lportTag + "_Permit_";
        syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rules to ensure that certain ICMPv6 like MLD_QUERY (130), RS (134), NS (135), NA (136) are
     * allowed into the VM.
     *
     * @param port the port
     * @param addOrRemove is write or delete
     */
    private void ingressAclIcmpv6AllowedTraffic(AclInterface port, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        int lportTag = port.getLPortTag();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        // Allow ICMPv6 Multicast Listener Query packets.
        List<MatchInfoBase> matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_MLD_QUERY, 0,
                lportTag, serviceMode);

        final short tableId = getAclAntiSpoofingTable();
        String flowName =
                "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_MLD_QUERY + "_Permit_";
        syncFlow(dpId, tableId, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);

        // Allow ICMPv6 Neighbor Solicitation packets.
        matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_NS, 0, lportTag, serviceMode);

        flowName = "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_NS + "_Permit_";
        syncFlow(dpId, tableId, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);

        // Allow ICMPv6 Neighbor Advertisement packets.
        matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_NA, 0, lportTag, serviceMode);

        flowName = "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_NA + "_Permit_";
        syncFlow(dpId, tableId, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    @Override
    protected void programIcmpv6RARule(AclInterface port, List<SubnetInfo> subnets, int addOrRemove) {
        if (AclServiceUtils.isIpv6Subnet(subnets)) {
            /* Allow ICMPv6 Router Advertisement packets from external routers as well as internal routers
             * if subnet is configured with IPv6 version
             * Allow ICMPv6 Router Advertisement packets if originating from any LinkLocal Address.
             */
            List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
            List<MatchInfoBase> matches =
                    AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_RA, 0,
                            port.getLPortTag(), serviceMode);
            matches.addAll(AclServiceUtils.buildIpMatches(
                    new IpPrefixOrAddress(new IpPrefix(AclConstants.IPV6_LINK_LOCAL_PREFIX.toCharArray())),
                    AclServiceManager.MatchCriteria.MATCH_SOURCE));
            String flowName = "Ingress_ICMPv6" + "_" + port.getDpId() + "_" + port.getLPortTag() + "_"
                    + AclConstants.ICMPV6_TYPE_RA + "_LinkLocal_Permit_";
            syncFlow(port.getDpId(), getAclAntiSpoofingTable(), flowName,
                    AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, "ACL", 0,
                    0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Adds the rule to allow arp packets.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programArpRule(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.ARP);
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        LOG.debug("{} ARP Rule on DPID {}, lportTag {}", addOrRemove == NwConstants.DEL_FLOW ? "Deleting" : "Adding",
                dpId, lportTag);
        String flowName = "Ingress_ARP_" + dpId + "_" + lportTag;
        syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_ARP_TRAFFIC_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }


    /**
     * Programs broadcast rules.
     *
     * @param port the Acl Interface port
     * @param addOrRemove whether to delete or add flow
     */
    @Override
    protected void programBroadcastRules(AclInterface port, int addOrRemove) {
        programIpv4BroadcastRule(port, addOrRemove);
    }

    /**
     * Programs IPv4 broadcast rules.
     *
     * @param port the Acl Interface port
     * @param addOrRemove whether to delete or add flow
     */
    private void programIpv4BroadcastRule(AclInterface port, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        int lportTag = port.getLPortTag();
        MatchInfoBase lportMatchInfo = AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode);
        List<SubnetInfo> subnetInfoList = port.getSubnetInfo();
        if (subnetInfoList != null) {
            List<String> broadcastAddresses = AclServiceUtils.getIpBroadcastAddresses(subnetInfoList);
            for (String broadcastAddress : broadcastAddresses) {
                List<MatchInfoBase> matches =
                        AclServiceUtils.buildBroadcastIpV4Matches(broadcastAddress);
                matches.add(lportMatchInfo);
                List<InstructionInfo> instructions = new ArrayList<>();
                instructions.add(new InstructionGotoTable(getAclConntrackClassifierTable()));
                String flowName = "Ingress_v4_Broadcast_" + dpId + "_" + lportTag + "_" + broadcastAddress + "_Permit";
                syncFlow(dpId, getAclAntiSpoofingTable(), flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                        AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
            }
        } else {
            LOG.warn("IP Broadcast CIDRs are missing for port {}", port.getInterfaceId());
        }
    }

    @Override
    protected boolean isValidDirection(Class<? extends DirectionBase> direction) {
        return direction.equals(DirectionIngress.class);
    }

    @Override
    protected short getAclAntiSpoofingTable() {
        return NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE;
    }

    @Override
    protected short getAclConntrackClassifierTable() {
        return NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE;
    }

    @Override
    protected short getAclConntrackSenderTable() {
        return NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE;
    }

    @Override
    protected short getAclForExistingTrafficTable() {
        return NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE;
    }

    @Override
    protected short getAclFilterCumDispatcherTable() {
        return NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE;
    }

    @Override
    protected short getAclRuleBasedFilterTable() {
        return NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE;
    }

    @Override
    protected short getAclRemoteAclTable() {
        return NwConstants.EGRESS_REMOTE_ACL_TABLE;
    }

    @Override
    protected short getAclCommitterTable() {
        return NwConstants.EGRESS_ACL_COMMITTER_TABLE;
    }
}

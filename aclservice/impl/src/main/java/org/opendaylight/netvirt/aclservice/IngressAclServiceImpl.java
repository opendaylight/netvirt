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

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv4;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefixBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.SubnetInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
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
                    serviceIndex, serviceMode);

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
                .callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> tx.delete(path))));
    }

    /**
     * Programs DHCP Service flows.
     *
     * @param flowEntries the flow entries
     * @param port the acl interface
     * @param action add/modify/remove action
     * @param addOrRemove addorRemove
     */
    @Override
    protected void programDhcpService(List<FlowEntity> flowEntries, AclInterface port,
            Action action, int addOrRemove) {
        LOG.info("{} programDhcpService for port {}, action={}, addOrRemove={}", this.directionString,
                port.getInterfaceId(), action, addOrRemove);
        Uint64 dpid = Uint64.valueOf(port.getDpId());
        int lportTag = port.getLPortTag();
        allowDhcpClientTraffic(flowEntries, dpid, lportTag, addOrRemove);
        allowDhcpv6ClientTraffic(flowEntries, dpid, lportTag, addOrRemove);
        programArpRule(flowEntries, dpid, lportTag, addOrRemove);
        ingressAclIcmpv6AllowedTraffic(flowEntries, port, InterfaceType.DhcpService, addOrRemove);
        allowIcmpTrafficToDhcpServer(flowEntries, port, port.getAllowedAddressPairs(), addOrRemove);
        dropTrafficToDhcpServer(flowEntries, dpid, lportTag, addOrRemove);
        programCommitterDropFlow(flowEntries, dpid, lportTag, addOrRemove);
    }

    /**
     * Programs DHCP service flows.
     *
     * @param flowEntries the flow entries
     * @param port the acl interface
     * @param allowedAddresses the allowed addresses
     * @param addOrRemove addorRemove
     */
    @Override
    protected void processDhcpServiceUpdate(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, int addOrRemove) {
        allowIcmpTrafficToDhcpServer(flowEntries, port, allowedAddresses, addOrRemove);
    }

    @Override
    protected void programAntiSpoofingRules(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, Action action, int addOrRemove) {
        LOG.info("{} programAntiSpoofingRules for port {}, AAPs={}, action={}, addOrRemove={}", this.directionString,
                port.getInterfaceId(), allowedAddresses, action, addOrRemove);

        Uint64 dpid = Uint64.valueOf(port.getDpId());
        int lportTag = port.getLPortTag();
        if (action == Action.ADD || action == Action.REMOVE) {
            programCommitterDropFlow(flowEntries, dpid, lportTag, addOrRemove);
            ingressAclDhcpAllowServerTraffic(flowEntries, dpid, lportTag, addOrRemove);
            ingressAclDhcpv6AllowServerTraffic(flowEntries, dpid, lportTag, addOrRemove);
            ingressAclIcmpv6AllowedTraffic(flowEntries, port, InterfaceType.AccessPort, addOrRemove);
            programIcmpv6RARule(flowEntries, port, port.getSubnetInfo(), addOrRemove);

            programArpRule(flowEntries, dpid, lportTag, addOrRemove);
            programIpv4BroadcastRule(flowEntries, port, port.getSubnetInfo(), addOrRemove);
        }
    }

    private void programCommitterDropFlow(List<FlowEntity> flowEntries, Uint64 dpId, int lportTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        Uint64 metaData = Uint64.fromLongBits(MetaDataUtil.METADATA_MASK_ACL_DROP.longValue()
                & (AclConstants.METADATA_DROP_FLAG.longValue() << 2));
        Uint64 metaDataMask = Uint64.fromLongBits(MetaDataUtil.METADATA_MASK_ACL_DROP.longValue()
                & (AclConstants.METADATA_DROP_FLAG.longValue() << 2));

        matches.add(new NxMatchRegister(NxmNxReg6.class, MetaDataUtil.getLportTagForReg6(lportTag).longValue(),
                MetaDataUtil.getLportTagMaskForReg6()));
        matches.add(new MatchMetadata(metaData, metaDataMask));

        String flowName = "Ingress_" + dpId + "_" + lportTag + "_Drop";
        addFlowEntryToList(flowEntries, dpId, getAclCommitterTable(), flowName,
                AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY, 0, 0, AclServiceUtils.getDropFlowCookie(lportTag),
                matches, instructions, addOrRemove);
    }

    @Override
    protected void programGotoClassifierTableRules(List<FlowEntity> flowEntries, Uint64 dpId,
            List<AllowedAddressPairs> aaps, int lportTag, int addOrRemove) {
        for (AllowedAddressPairs aap : aaps) {
            IpPrefixOrAddress attachIp = aap.getIpAddress();
            MacAddress mac = aap.getMacAddress();

            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
            matches.add(new MatchEthernetDestination(mac));
            matches.addAll(AclServiceUtils.buildIpMatches(attachIp, MatchCriteria.MATCH_DESTINATION));

            List<InstructionInfo> gotoInstructions = new ArrayList<>();
            gotoInstructions.add(new InstructionGotoTable(getAclConntrackClassifierTable()));

            String flowName = "Ingress_Fixed_Goto_Classifier_" + dpId + "_" + lportTag + "_"
                    + mac.getValue() + "_" + attachIp.stringValue();
            addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                    AclConstants.PROTO_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches, gotoInstructions,
                    addOrRemove);
        }
    }

    @Override
    protected void programRemoteAclTableFlow(List<FlowEntity> flowEntries, Uint64 dpId, Integer aclTag,
            AllowedAddressPairs aap, int addOrRemove) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.addAll(AclServiceUtils.buildIpAndSrcServiceMatch(aclTag, aap));

        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclCommitterTable());
        String flowNameAdded = "Acl_Filter_Ingress_" + aap.getIpAddress().stringValue() + "_" + aclTag;

        addFlowEntryToList(flowEntries, dpId, getAclRemoteAclTable(), flowNameAdded, AclConstants.ACL_DEFAULT_PRIORITY,
                0, 0, AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is allowed.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpid
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     */
    protected void ingressAclDhcpAllowServerTraffic(List<FlowEntity> flowEntries, Uint64 dpId, int lportTag,
            int addOrRemove) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_SERVER_PORT_IPV4,
                AclConstants.DHCP_CLIENT_PORT_IPV4, lportTag, serviceMode);
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        String flowName = "Ingress_DHCP_Server_v4" + dpId + "_" + lportTag + "_Permit_";
        addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is allowed.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpid
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     */
    protected void ingressAclDhcpv6AllowServerTraffic(List<FlowEntity> flowEntries, Uint64 dpId, int lportTag,
            int addOrRemove) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_SERVER_PORT_IPV6,
                AclConstants.DHCP_CLIENT_PORT_IPV6, lportTag, serviceMode);
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        String flowName = "Ingress_DHCP_Server_v6" + "_" + dpId + "_" + lportTag + "_Permit_";
        addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                instructions, addOrRemove);
    }

    /**
     * Add rules to ensure that certain ICMPv6 like MLD_QUERY (130), RS (134), NS (135), NA (136) are
     * allowed into the VM.
     *
     * @param flowEntries the flow entries
     * @param port the port
     * @param port type
     * @param addOrRemove is write or delete
     */
    private void ingressAclIcmpv6AllowedTraffic(List<FlowEntity> flowEntries, AclInterface port,
            InterfaceType interfaceType, int addOrRemove) {
        Uint64 dpId = Uint64.valueOf(port.getDpId());
        int lportTag = port.getLPortTag();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        final short tableId = getAclAntiSpoofingTable();

        if (interfaceType != InterfaceType.DhcpService) {
            // Allow ICMPv6 Multicast Listener Query packets.
            List<MatchInfoBase> matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_MLD_QUERY, 0,
                    lportTag, serviceMode);
            String flowName = "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_"
                    + AclConstants.ICMPV6_TYPE_MLD_QUERY + "_Permit_";
            addFlowEntryToList(flowEntries, dpId, tableId, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }

        // Allow ICMPv6 Neighbor Solicitation packets.
        List<MatchInfoBase> matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_NS, 0, lportTag,
                serviceMode);

        String flowName = "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_"
                + AclConstants.ICMPV6_TYPE_NS + "_Permit_";
        addFlowEntryToList(flowEntries, dpId, tableId, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);

        // Allow ICMPv6 Neighbor Advertisement packets.
        matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_NA, 0, lportTag, serviceMode);

        flowName = "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_NA + "_Permit_";
        addFlowEntryToList(flowEntries, dpId, tableId, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    @Override
    protected void programIcmpv6RARule(List<FlowEntity> flowEntries, AclInterface port, List<SubnetInfo> subnets,
            int addOrRemove) {
        if (!AclServiceUtils.isIpv6Subnet(subnets)) {
            return;
        }

        Uint64 dpid = Uint64.valueOf(port.getDpId());
        /* Allow ICMPv6 Router Advertisement packets from external routers as well as internal routers
         * if subnet is configured with IPv6 version
         * Allow ICMPv6 Router Advertisement packets if originating from any LinkLocal Address.
         */
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        List<MatchInfoBase> matches =
                AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_RA, 0,
                        port.getLPortTag(), serviceMode);
        matches.addAll(AclServiceUtils.buildIpMatches(
                new IpPrefixOrAddress(IpPrefixBuilder.getDefaultInstance(AclConstants.IPV6_LINK_LOCAL_PREFIX)),
                AclServiceManager.MatchCriteria.MATCH_SOURCE));
        String flowName = "Ingress_ICMPv6" + "_" + dpid + "_" + port.getLPortTag() + "_"
                + AclConstants.ICMPV6_TYPE_RA + "_LinkLocal_Permit_";
        addFlowEntryToList(flowEntries, dpid, getAclAntiSpoofingTable(), flowName,
                AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                instructions, addOrRemove);
    }

    /**
     * Adds the rule to allow arp packets.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programArpRule(List<FlowEntity> flowEntries, Uint64 dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.ARP);
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        LOG.debug("{} ARP Rule on DPID {}, lportTag {}", addOrRemove == NwConstants.DEL_FLOW ? "Deleting" : "Adding",
                dpId, lportTag);
        String flowName = "Ingress_ARP_" + dpId + "_" + lportTag;
        addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                AclConstants.PROTO_ARP_TRAFFIC_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                instructions, addOrRemove);
    }


    /**
     * Programs broadcast rules.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param addOrRemove whether to delete or add flow
     */
    @Override
    protected void programBroadcastRules(List<FlowEntity> flowEntries, AclInterface port, Action action,
            int addOrRemove) {
        programIpv4BroadcastRule(flowEntries, port, port.getSubnetInfo(), addOrRemove);
    }

    /**
     * Programs broadcast rules.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param subnetInfoList the port subnet info list
     * @param addOrRemove whether to delete or add flow
     */
    protected void programSubnetBroadcastRules(List<FlowEntity> flowEntries, AclInterface port,
            List<SubnetInfo> subnetInfoList, int addOrRemove) {
        programIpv4BroadcastRule(flowEntries, port, subnetInfoList, addOrRemove);
    }

    /**
     * Programs IPv4 broadcast rules.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param subnetInfoList Port subnet list
     * @param addOrRemove whether to delete or add flow
     */
    private void programIpv4BroadcastRule(List<FlowEntity> flowEntries, AclInterface port,
            List<SubnetInfo> subnetInfoList, int addOrRemove) {
        Uint64 dpId = Uint64.valueOf(port.getDpId());
        int lportTag = port.getLPortTag();
        MatchInfoBase lportMatchInfo = AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode);
        if (subnetInfoList != null) {
            List<String> broadcastAddresses = AclServiceUtils.getIpBroadcastAddresses(subnetInfoList);
            for (String broadcastAddress : broadcastAddresses) {
                List<MatchInfoBase> matches =
                        AclServiceUtils.buildBroadcastIpV4Matches(broadcastAddress);
                matches.add(lportMatchInfo);
                List<InstructionInfo> instructions = new ArrayList<>();
                instructions.add(new InstructionGotoTable(getAclConntrackClassifierTable()));
                String flowName = "Ingress_v4_Broadcast_" + dpId + "_"
                                    + lportTag + "_" + broadcastAddress + "_Permit";
                addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                        AclConstants.PROTO_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions,
                        addOrRemove);
            }
        } else {
            LOG.warn("IP Broadcast CIDRs are missing for port {}", port.getInterfaceId());
        }
    }

    /**
     * Add rule to ensure only DHCP client traffic is allowed.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpid
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     */
    protected void allowDhcpClientTraffic(List<FlowEntity> flowEntries, Uint64 dpId, int lportTag,
            int addOrRemove) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_CLIENT_PORT_IPV4,
                AclConstants.DHCP_SERVER_PORT_IPV4, lportTag, serviceMode);
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        String flowName = "Ingress_DHCP_Service_v4" + dpId + "_" + lportTag + "_Permit_";
        addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 client traffic is allowed.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpid
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     */
    protected void allowDhcpv6ClientTraffic(List<FlowEntity> flowEntries, Uint64 dpId, int lportTag,
            int addOrRemove) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_CLIENT_PORT_IPV6,
                AclConstants.DHCP_SERVER_PORT_IPV6, lportTag, serviceMode);
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        String flowName = "Ingress_DHCP_Service_v6" + "_" + dpId + "_" + lportTag + "_Permit_";
        addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                instructions, addOrRemove);
    }

    /**
     * Add rules to allow ICMP traffic for DHCP server.
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param allowedAddresses the allowed addresses
     * @param addOrRemove the lport tag
     */
    protected void allowIcmpTrafficToDhcpServer(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, int addOrRemove) {
        Uint64 dpId = Uint64.valueOf(port.getDpId());
        int lportTag = port.getLPortTag();
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            if (AclServiceUtils.isIPv4Address(allowedAddress)) {
                MatchInfo reqMatchInfo = new MatchIcmpv4((short) AclConstants.ICMPV4_TYPE_ECHO_REQUEST, (short) 0);
                programIcmpFlow(flowEntries, dpId, lportTag, allowedAddress, MatchIpProtocol.ICMP, reqMatchInfo,
                        AclConstants.ICMPV4_TYPE_ECHO_REQUEST, addOrRemove);
                MatchInfo replyMatchInfo = new MatchIcmpv4((short) AclConstants.ICMPV4_TYPE_ECHO_REPLY, (short) 0);
                programIcmpFlow(flowEntries, dpId, lportTag, allowedAddress, MatchIpProtocol.ICMP, replyMatchInfo,
                        AclConstants.ICMPV4_TYPE_ECHO_REPLY, addOrRemove);
            } else {
                MatchInfo reqMatchInfo = new MatchIcmpv6((short) AclConstants.ICMPV6_TYPE_ECHO_REQUEST, (short) 0);
                programIcmpFlow(flowEntries, dpId, lportTag, allowedAddress, MatchIpProtocol.ICMPV6, reqMatchInfo,
                        AclConstants.ICMPV6_TYPE_ECHO_REQUEST, addOrRemove);
                MatchInfo replyMatchInfo = new MatchIcmpv6((short) AclConstants.ICMPV6_TYPE_ECHO_REPLY, (short) 0);
                programIcmpFlow(flowEntries, dpId, lportTag, allowedAddress, MatchIpProtocol.ICMPV6, replyMatchInfo,
                        AclConstants.ICMPV6_TYPE_ECHO_REPLY, addOrRemove);
            }
        }
    }

    private void programIcmpFlow(List<FlowEntity> flowEntries, Uint64 dpId, int lportTag,
            AllowedAddressPairs allowedAddress, MatchIpProtocol protocol, MatchInfo icmpTypeMatchInfo,
            int icmpType, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(protocol);
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        matches.add(new MatchEthernetDestination(allowedAddress.getMacAddress()));
        matches.addAll(AclServiceUtils.buildIpMatches(allowedAddress.getIpAddress(), MatchCriteria.MATCH_DESTINATION));
        matches.add(icmpTypeMatchInfo);

        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        String flowName = "Ingress_DHCP_Service_ICMP_" + dpId + "_" + lportTag + "_" + icmpType + "_Permit_";
        addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                instructions, addOrRemove);
    }

    /**
     * Add rule to drop BUM traffic to DHCP Server.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpid
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     */
    protected void dropTrafficToDhcpServer(List<FlowEntity> flowEntries, Uint64 dpId, int lportTag,
            int addOrRemove) {
        InstructionInfo writeMetatdata = AclServiceUtils.getWriteMetadataForDropFlag();
        List<InstructionInfo> instructions = Lists.newArrayList(writeMetatdata);
        instructions.addAll(AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclCommitterTable()));

        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new NxMatchRegister(NxmNxReg6.class, MetaDataUtil.getLportTagForReg6(lportTag).longValue(),
                MetaDataUtil.getLportTagMaskForReg6()));

        String flowName = "Ingress_DHCP_Service_" + dpId + "_" + lportTag + "_Drop";
        addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                AclConstants.PROTO_DHCP_SERVER_DROP_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                instructions, addOrRemove);
    }

    @Override
    protected boolean isValidDirection(Class<? extends DirectionBase> direction) {
        return direction.equals(DirectionIngress.class);
    }

    private short getAclAntiSpoofingTable() {
        return NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE;
    }

    private short getAclConntrackClassifierTable() {
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

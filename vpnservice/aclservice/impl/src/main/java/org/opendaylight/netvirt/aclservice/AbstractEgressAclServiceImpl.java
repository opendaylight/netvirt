/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.PacketHandling;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides abstract implementation for egress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */
public abstract class AbstractEgressAclServiceImpl extends AbstractAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractEgressAclServiceImpl.class);

    /**
     * Initialize the member variables.
     *
     * @param dataBroker the data broker instance.
     * @param mdsalManager the mdsal manager instance.
     * @param aclDataUtil
     *            the acl data util.
     * @param aclServiceUtils
     *            the acl service util.
     */
    public AbstractEgressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils) {
        // Service mode is w.rt. switch
        super(ServiceModeIngress.class, dataBroker, mdsalManager, aclDataUtil, aclServiceUtils);
    }

    /**
     * Bind service.
     *
     * @param aclInterface the acl interface
     */
    @Override
    public void bindService(AclInterface aclInterface) {
        String interfaceName = aclInterface.getInterfaceId();
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(interfaceName,
            () -> {
                int instructionKey = 0;
                List<Instruction> instructions = new ArrayList<>();
                Long vpnId = aclInterface.getVpnId();
                if (vpnId != null) {
                    instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnId),
                        MetaDataUtil.METADATA_MASK_VRFID, ++instructionKey));
                } else {
                    Long elanTag = aclInterface.getElanId();
                    instructions.add(
                        MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getElanTagMetadata(elanTag),
                        MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
                }
                instructions.add(
                        MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.INGRESS_ACL_TABLE, ++instructionKey));
                short serviceIndex = ServiceIndex.getIndex(NwConstants.ACL_SERVICE_NAME,
                        NwConstants.ACL_SERVICE_INDEX);
                int flowPriority = AclConstants.EGRESS_ACL_DEFAULT_FLOW_PRIORITY;
                BoundServices serviceInfo = AclServiceUtils.getBoundServices(
                        String.format("%s.%s.%s", "acl", "egressacl", interfaceName), serviceIndex, flowPriority,
                        AclConstants.COOKIE_ACL_BASE, instructions);
                InstanceIdentifier<BoundServices> path =
                    AclServiceUtils.buildServiceId(interfaceName, serviceIndex, ServiceModeIngress.class);

                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                writeTxn.put(LogicalDatastoreType.CONFIGURATION, path, serviceInfo, true);

                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(writeTxn.submit());
                return futures;
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
        InstanceIdentifier<BoundServices> path =
                AclServiceUtils.buildServiceId(interfaceName,
                        ServiceIndex.getIndex(NwConstants.ACL_SERVICE_NAME, NwConstants.ACL_SERVICE_INDEX),
                        ServiceModeIngress.class);

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(interfaceName,
            () -> {
                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                writeTxn.delete(LogicalDatastoreType.CONFIGURATION, path);

                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(writeTxn.submit());
                return futures;
            });
    }

    @Override
    protected void programGeneralFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, Action action, int addOrRemove) {
        LOG.info("programFixedRules :  adding default rules.");

        if (action == Action.ADD || action == Action.REMOVE) {
            egressAclDhcpAllowClientTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove);
            egressAclDhcpv6AllowClientTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove);
            egressAclDhcpDropServerTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove);
            egressAclDhcpv6DropServerTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove);
            egressAclIcmpv6DropRouterAdvts(dpid, lportTag, addOrRemove);
            egressAclIcmpv6AllowedList(dpid, lportTag, addOrRemove);

            programArpRule(dpid, allowedAddresses, lportTag, addOrRemove);
        }
    }

    @Override
    protected void updateArpForAllowedAddressPairs(BigInteger dpId, int lportTag, List<AllowedAddressPairs> deletedAAP,
            List<AllowedAddressPairs> addedAAP) {
        Set<MacAddress> deletedAAPmacs =
                deletedAAP.stream().map(aap -> aap.getMacAddress()).collect(Collectors.toSet());
        Set<MacAddress> addedAAPmacs = addedAAP.stream().map(aap -> aap.getMacAddress()).collect(Collectors.toSet());

        // Remove common macs to avoid delete and add of ARP flows having same MAC.
        deletedAAPmacs.removeAll(addedAAPmacs);

        programArpRule(dpId, deletedAAPmacs, lportTag, NwConstants.DEL_FLOW);
        programArpRule(dpId, addedAAPmacs, lportTag, NwConstants.ADD_FLOW);
    }

    @Override
    protected boolean programAclRules(AclInterface port, List<Uuid> aclUuidList, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        LOG.trace("Applying custom rules DpId {}, lportTag {}", dpId, port.getLPortTag());
        if (aclUuidList == null || dpId == null) {
            LOG.warn("one of the egress acl parameters can not be null. sg {}, dpId {}",
                    aclUuidList, dpId);
            return false;
        }
        for (Uuid sgUuid :aclUuidList) {
            Acl acl = AclServiceUtils.getAcl(dataBroker, sgUuid.getValue());
            if (null == acl) {
                LOG.warn("The ACL is empty");
                continue;
            }
            AccessListEntries accessListEntries = acl.getAccessListEntries();
            List<Ace> aceList = accessListEntries.getAce();
            for (Ace ace: aceList) {
                programAceRule(port, addOrRemove, acl.getAclName(), ace, null);
            }
        }
        return true;
    }

    @Override
    protected void programAceRule(AclInterface port, int addOrRemove, String aclName, Ace ace,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (!aceAttr.getDirection().equals(DirectionEgress.class)) {
            return;
        }
        Matches matches = ace.getMatches();
        AceType aceType = matches.getAceType();
        Map<String,List<MatchInfoBase>> flowMap = null;
        if (aceType instanceof AceIp) {
            flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
            if (syncAllowedAddresses != null) {
                flowMap = AclServiceUtils.getFlowForAllowedAddresses(syncAllowedAddresses, flowMap, false);
            } else if (aceAttr.getRemoteGroupId() != null) {
                flowMap = aclServiceUtils.getFlowForRemoteAcl(port, aceAttr.getRemoteGroupId(), port.getInterfaceId(),
                        flowMap, false);
            }
        }
        int lportTag = port.getLPortTag();
        if (null == flowMap) {
            LOG.error("Failed to apply ACL {} lportTag {}", ace.getKey(), lportTag);
            return;
        }
        for (String flowName : flowMap.keySet()) {
            syncSpecificAclFlow(port.getDpId(), lportTag, addOrRemove, ace, port.getInterfaceId(), flowMap, flowName);
        }
    }

    @Override
    protected void updateRemoteAclTableForPort(AclInterface port, Uuid acl, int addOrRemove,
            AllowedAddressPairs ip, BigInteger aclId, BigInteger dpId) {
        Long elanTag = port.getElanId();
        Long vpnId = port.getVpnId();
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.addAll(AclServiceUtils.buildIpAndDstServiceMatch(elanTag, ip, dataBroker, vpnId));

        List<InstructionInfo> instructions = new ArrayList<>();

        InstructionWriteMetadata writeMetatdata =
                new InstructionWriteMetadata(AclServiceUtils.getAclIdMetadata(aclId),
                        MetaDataUtil.METADATA_MASK_REMOTE_ACL_ID);
        instructions.add(writeMetatdata);
        instructions.add(new InstructionGotoTable(getEgressAclFilterTable()));

        Long serviceTag = vpnId != null ? vpnId : elanTag;
        String flowNameAdded = "Acl_Filter_Egress_" + new String(ip.getIpAddress().getValue()) + "_" + serviceTag;

        syncFlow(dpId, getEgressAclRemoteAclTable(), flowNameAdded, AclConstants.NO_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
    }

    protected short getEgressAclFilterTable() {
        return NwConstants.INGRESS_ACL_FILTER_TABLE;
    }

    protected short getEgressAclRemoteAclTable() {
        return NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE;
    }

    protected abstract String syncSpecificAclFlow(BigInteger dpId, int lportTag, int addOrRemove, Ace ace,
            String portId, Map<String, List<MatchInfoBase>> flowMap, String flowName);

    /**
     * Anti-spoofing rule to block the Ipv4 DHCP server traffic from the port.
     *
     * @param dpId the dpId
     * @param dhcpMacAddress the Dhcp mac address
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    protected void egressAclDhcpDropServerTraffic(BigInteger dpId, String dhcpMacAddress, int lportTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_SERVER_PORT_IPV4,
                AclConstants.DHCP_CLIENT_PORT_IPV4, lportTag, ServiceModeEgress.class);

        String flowName = "Egress_DHCP_Server_v4" + dpId + "_" + lportTag + "_" + dhcpMacAddress + "_Drop_";
        syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName,
                AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, Collections.emptyList(), addOrRemove);
    }

    /**
     * Anti-spoofing rule to block the Ipv6 DHCP server traffic from the port.
     *
     * @param dpId the dpId
     * @param dhcpMacAddress the Dhcp mac address
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    protected void egressAclDhcpv6DropServerTraffic(BigInteger dpId, String dhcpMacAddress, int lportTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_SERVER_PORT_IPV6,
                AclConstants.DHCP_CLIENT_PORT_IPV6, lportTag, ServiceModeEgress.class);

        String flowName = "Egress_DHCP_Server_v6" + "_" + dpId + "_" + lportTag + "_" + dhcpMacAddress + "_Drop_";
        syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName,
                AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, Collections.emptyList(), addOrRemove);
    }

    /**
     * Anti-spoofing rule to block the Ipv6 Router Advts from the VM port.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    private void egressAclIcmpv6DropRouterAdvts(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_RA, 0, lportTag,
                ServiceModeEgress.class);

        String flowName = "Egress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_RA + "_Drop_";
        syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName, AclConstants.PROTO_IPV6_DROP_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, Collections.emptyList(), addOrRemove);
    }

    /**
     * Add rule to allow certain ICMPv6 traffic from VM ports.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    private void egressAclIcmpv6AllowedList(BigInteger dpId, int lportTag, int addOrRemove) {
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        for (Integer icmpv6Type: AclConstants.allowedIcmpv6NdList()) {
            List<MatchInfoBase> matches = AclServiceUtils.buildIcmpV6Matches(icmpv6Type, 0, lportTag,
                    ServiceModeEgress.class);
            String flowName = "Egress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + icmpv6Type + "_Permit_";
            syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY,
                    "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void egressAclDhcpAllowClientTraffic(BigInteger dpId, String dhcpMacAddress, int lportTag,
            int addOrRemove) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_CLIENT_PORT_IPV4,
                AclConstants.DHCP_SERVER_PORT_IPV4, lportTag, ServiceModeEgress.class);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName = "Egress_DHCP_Client_v4" + dpId + "_" + lportTag + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName, AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY,
            "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void egressAclDhcpv6AllowClientTraffic(BigInteger dpId, String dhcpMacAddress, int lportTag,
            int addOrRemove) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_CLIENT_PORT_IPV6,
                AclConstants.DHCP_SERVER_PORT_IPV6, lportTag, ServiceModeEgress.class);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName = "Egress_DHCP_Client_v6" + "_" + dpId + "_" + lportTag + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName, AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY,
            "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Program arp rule.
     *
     * @param dpId the dp id
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programArpRule(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses, int lportTag,
            int addOrRemove) {
        // Collecting macs as a set to avoid duplicate
        Set<MacAddress> macs = allowedAddresses.stream().map(aap -> aap.getMacAddress()).collect(Collectors.toSet());
        programArpRule(dpId, macs, lportTag, addOrRemove);
    }

    /**
     * Adds the rule to allow arp packets.
     *
     * @param dpId the dpId
     * @param macs the set of MACs
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programArpRule(BigInteger dpId, Set<MacAddress> macs, int lportTag, int addOrRemove) {
        for (MacAddress mac : macs) {
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(MatchEthernetType.ARP);
            matches.add(new MatchArpSha(mac));
            matches.add(buildLPortTagMatch(lportTag));

            List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(new ArrayList<>());

            String flowName = "Egress_ARP_" + dpId + "_" + lportTag + "_" + mac.getValue();
            syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName,
                    AclConstants.PROTO_ARP_TRAFFIC_MATCH_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    protected MatchInfoBase buildLPortTagMatch(int lportTag) {
        return AclServiceUtils.buildLPortTagMatch(lportTag, ServiceModeEgress.class);
    }

    protected int getEgressSpecificAclFlowPriority(BigInteger dpId, int addOrRemove, String flowName,
                                                 PacketHandling packetHandling) {
        int priority;
        if (addOrRemove == NwConstants.DEL_FLOW) {
            priority = aclServiceUtils.releaseAndRemoveFlowPriorityFromCache(dpId, NwConstants.INGRESS_ACL_FILTER_TABLE,
                    flowName, packetHandling);
        } else {
            priority = aclServiceUtils.allocateAndSaveFlowPriorityInCache(dpId, NwConstants.INGRESS_ACL_FILTER_TABLE,
                    flowName, packetHandling);
        }
        return priority;
    }
}

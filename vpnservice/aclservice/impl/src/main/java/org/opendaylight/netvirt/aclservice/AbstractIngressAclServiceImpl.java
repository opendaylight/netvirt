/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
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
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides abstract implementation for ingress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */
public abstract class AbstractIngressAclServiceImpl extends AbstractAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractIngressAclServiceImpl.class);

    /**
     * Initialize the member variables.
     *
     * @param dataBroker the data broker instance.
     * @param mdsalManager the mdsal manager.
     * @param aclDataUtil
     *            the acl data util.
     * @param aclServiceUtils
     *            the acl service util.
     */
    public AbstractIngressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils, JobCoordinator jobCoordinator) {
        // Service mode is w.rt. switch
        super(ServiceModeEgress.class, dataBroker, mdsalManager, aclDataUtil, aclServiceUtils, jobCoordinator);
    }

    /**
     * Bind service.
     *
     * @param aclInterface the acl interface
     */
    @Override
    public void bindService(AclInterface aclInterface) {
        String interfaceName = aclInterface.getInterfaceId();
        jobCoordinator.enqueueJob(interfaceName,
            () -> {
                int instructionKey = 0;
                List<Instruction> instructions = new ArrayList<>();
                Long vpnId = aclInterface.getVpnId();
                if (vpnId != null) {
                    instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnId),
                        MetaDataUtil.METADATA_MASK_VRFID, ++instructionKey));
                    LOG.debug("Binding ACL service for interface {} with vpnId {}", interfaceName, vpnId);
                } else {
                    Long elanTag = aclInterface.getElanId();
                    instructions.add(
                            MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getElanTagMetadata(elanTag),
                            MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
                    LOG.debug("Binding ACL service for interface {} with ElanTag {}", interfaceName, elanTag);
                }
                instructions.add(
                        MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.EGRESS_ACL_TABLE, ++instructionKey));
                int flowPriority = AclConstants.INGRESS_ACL_DEFAULT_FLOW_PRIORITY;
                short serviceIndex = ServiceIndex.getIndex(NwConstants.EGRESS_ACL_SERVICE_NAME,
                        NwConstants.EGRESS_ACL_SERVICE_INDEX);
                BoundServices serviceInfo = AclServiceUtils.getBoundServices(
                        String.format("%s.%s.%s", "acl", "ingressacl", interfaceName), serviceIndex, flowPriority,
                        AclConstants.COOKIE_ACL_BASE, instructions);
                InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                        ServiceIndex.getIndex(NwConstants.EGRESS_ACL_SERVICE_NAME,
                        NwConstants.EGRESS_ACL_SERVICE_INDEX), ServiceModeEgress.class);

                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                writeTxn.put(LogicalDatastoreType.CONFIGURATION, path, serviceInfo,
                        WriteTransaction.CREATE_MISSING_PARENTS);

                return Collections.singletonList(writeTxn.submit());
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
                ServiceModeEgress.class);

        LOG.debug("UnBinding ACL service for interface {}", interfaceName);
        jobCoordinator.enqueueJob(interfaceName,
            () -> {
                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                writeTxn.delete(LogicalDatastoreType.CONFIGURATION, path);

                return Collections.singletonList(writeTxn.submit());
            });
    }

    /**
     * Program conntrack rules.
     *
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove add or remove the flow
     */
    @Override
    protected abstract void programSpecificFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove);

    @Override
    protected void programGeneralFixedRules(AclInterface port, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, Action action, int addOrRemove) {
        LOG.info("programFixedRules : {} default rules.", action == Action.ADD ? "adding" : "removing");

        BigInteger dpid = port.getDpId();
        int lportTag = port.getLPortTag();
        if (action == Action.ADD || action == Action.REMOVE) {
            ingressAclDhcpAllowServerTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove,
                    AclConstants.PROTO_PREFIX_MATCH_PRIORITY);
            ingressAclDhcpv6AllowServerTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove,
                    AclConstants.PROTO_PREFIX_MATCH_PRIORITY);
            ingressAclIcmpv6AllowedTraffic(dpid, lportTag, addOrRemove);

            programArpRule(dpid, lportTag, addOrRemove);
            programIpv4BroadcastRule(port, addOrRemove);
        }
    }

    @Override
    protected void updateArpForAllowedAddressPairs(BigInteger dpId, int lportTag, List<AllowedAddressPairs> deletedAAP,
            List<AllowedAddressPairs> addedAAP) {
        // Nothing to do for port update as ingress ARP flow is based only on lportTag

    }

    @Override
    protected boolean programAclRules(AclInterface port, List<Uuid> aclUuidList,int addOrRemove) {
        BigInteger dpId = port.getDpId();
        LOG.debug("Applying custom rules on DpId {}, lportTag {}", dpId, port.getLPortTag());
        if (aclUuidList == null || dpId == null) {
            LOG.warn("one of the ingress acl parameters can not be null. sg {}, dpId {}",
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
            for (Ace ace : aceList) {
                programAceRule(port, addOrRemove, acl.getAclName(), ace, null);
            }
        }
        return true;
    }

    @Override
    protected void programAceRule(AclInterface port, int addOrRemove, String aclName, Ace ace,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (!aceAttr.getDirection().equals(DirectionIngress.class)) {
            return;
        }
        Matches matches = ace.getMatches();
        AceType aceType = matches.getAceType();
        Map<String, List<MatchInfoBase>> flowMap = null;
        if (aceType instanceof AceIp) {
            flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
            if (syncAllowedAddresses != null) {
                flowMap = AclServiceUtils.getFlowForAllowedAddresses(syncAllowedAddresses, flowMap, true);
            } else if (aceAttr.getRemoteGroupId() != null) {
                flowMap = aclServiceUtils.getFlowForRemoteAcl(port, aceAttr.getRemoteGroupId(), port.getInterfaceId(),
                        flowMap, true);
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
        flowMatches.addAll(AclServiceUtils.buildIpAndSrcServiceMatch(elanTag, ip, dataBroker, vpnId));

        List<InstructionInfo> instructions = new ArrayList<>();

        InstructionWriteMetadata writeMetatdata =
                new InstructionWriteMetadata(AclServiceUtils.getAclIdMetadata(aclId),
                        MetaDataUtil.METADATA_MASK_REMOTE_ACL_ID);
        instructions.add(writeMetatdata);
        instructions.add(new InstructionGotoTable(getIngressAclFilterTable()));

        Long serviceTag = vpnId != null ? vpnId : elanTag;
        String flowNameAdded = "Acl_Filter_Ingress_" + new String(ip.getIpAddress().getValue()) + "_" + serviceTag;

        syncFlow(dpId, getIngressAclRemoteAclTable(), flowNameAdded, AclConstants.NO_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
    }

    protected short getIngressAclFilterTable() {
        return NwConstants.EGRESS_ACL_FILTER_TABLE;
    }

    protected short getIngressAclRemoteAclTable() {
        return NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE;
    }

    protected abstract String syncSpecificAclFlow(BigInteger dpId, int lportTag, int addOrRemove, Ace ace,
            String portId, Map<String, List<MatchInfoBase>> flowMap, String flowName);

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     * @param protoPortMatchPriority the priority
     */
    protected void ingressAclDhcpAllowServerTraffic(BigInteger dpId, String dhcpMacAddress, int lportTag,
            int addOrRemove, int protoPortMatchPriority) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_SERVER_PORT_IPV4,
                AclConstants.DHCP_CLIENT_PORT_IPV4, lportTag, ServiceModeIngress.class);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName = "Ingress_DHCP_Server_v4" + dpId + "_" + lportTag + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     * @param protoPortMatchPriority the priority
     */
    protected void ingressAclDhcpv6AllowServerTraffic(BigInteger dpId, String dhcpMacAddress, int lportTag,
            int addOrRemove, Integer protoPortMatchPriority) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_SERVER_PORT_IPV6,
                AclConstants.DHCP_CLIENT_PORT_IPV6, lportTag, ServiceModeIngress.class);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName =
                "Ingress_DHCP_Server_v6" + "_" + dpId + "_" + lportTag + "_" + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rules to ensure that certain ICMPv6 like MLD_QUERY (130), NS (135), NA (136) are allowed into the VM.
     *
     * @param dpId the dpid
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     */
    private void ingressAclIcmpv6AllowedTraffic(BigInteger dpId, int lportTag, int addOrRemove) {
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        // Allow ICMPv6 Multicast Listener Query packets.
        List<MatchInfoBase> matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_MLD_QUERY,
                0, lportTag, ServiceModeIngress.class);

        String flowName =
                "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_MLD_QUERY + "_Permit_";
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);

        // Allow ICMPv6 Neighbor Solicitation packets.
        matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_NS, 0, lportTag,
                ServiceModeIngress.class);

        flowName =
                "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_NS + "_Permit_";
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);

        // Allow ICMPv6 Neighbor Advertisement packets.
        matches = AclServiceUtils.buildIcmpV6Matches(AclConstants.ICMPV6_TYPE_NA, 0, lportTag,
                ServiceModeIngress.class);

        flowName =
                "Ingress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + AclConstants.ICMPV6_TYPE_NA + "_Permit_";
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
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
        matches.add(buildLPortTagMatch(lportTag));
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(new ArrayList<>());
        LOG.debug(addOrRemove == NwConstants.DEL_FLOW ? "Deleting " : "Adding " + "ARP Rule on DPID {}, "
                + "lportTag {}", dpId, lportTag);
        String flowName = "Ingress_ARP_" + dpId + "_" + lportTag;
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName,
                AclConstants.PROTO_ARP_TRAFFIC_MATCH_PRIORITY, "ACL", 0, 0,
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
        MatchInfoBase lportMatchInfo = buildLPortTagMatch(lportTag);
        List<IpPrefixOrAddress> cidrs = port.getSubnetIpPrefixes();
        if (cidrs != null) {
            List<String> broadcastAddresses = AclServiceUtils.getIpBroadcastAddresses(cidrs);
            for (String broadcastAddress : broadcastAddresses) {
                List<MatchInfoBase> matches =
                        AclServiceUtils.buildBroadcastIpV4Matches(broadcastAddress);
                matches.add(lportMatchInfo);
                List<InstructionInfo> instructions = new ArrayList<>();
                instructions.add(new InstructionGotoTable(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE));
                String flowName = "Ingress_v4_Broadcast_" + dpId + "_" + lportTag + "_" + broadcastAddress + "_Permit";
                syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName,
                        AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                        instructions, addOrRemove);
            }
        } else {
            LOG.warn("IP Broadcast CIDRs are missing for port {}", port.getInterfaceId());
        }
    }

    protected MatchInfoBase buildLPortTagMatch(int lportTag) {
        return AclServiceUtils.buildLPortTagMatch(lportTag, ServiceModeIngress.class);
    }
}

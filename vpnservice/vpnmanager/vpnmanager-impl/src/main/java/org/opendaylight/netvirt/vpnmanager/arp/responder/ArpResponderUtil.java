/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.arp.responder;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.vpnmanager.ArpReplyOrRequest;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxOfInPortCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoadBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.DstBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arp Responder Utility Class
 *
 * @author karthik.p
 *
 */
public class ArpResponderUtil {

    private final static Logger LOG = LoggerFactory
            .getLogger(ArpResponderUtil.class);

    /**
     * A Utility class
     */
    private ArpResponderUtil() {

    }

    /**
     * Install Group flow on the DPN
     *
     * @param mdSalManager
     *            Reference of MDSAL API RPC that provides API for installing
     *            group flow
     * @param dpnId
     *            DPN on which group flow to be installed
     * @param groupdId
     *            Uniquely identifiable Group Id for the group flow
     * @param groupName
     *            Name of the group flow
     * @param buckets
     *            List of the bucket actions for the group flow
     */
    public static void installGroup(final IMdsalApiManager mdSalManager,
            final BigInteger dpnId, final long groupdId, final String groupName,
            final List<BucketInfo> buckets) {
        LOG.trace("Installing group flow on dpn {}", dpnId);
        final GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId,
                groupdId, groupName, GroupTypes.GroupAll, buckets);
        mdSalManager.installGroup(groupEntity);
    }

    /**
     * Install Drop flow on the DPN
     *
     * @param mdSalManager
     *            Reference of MDSAL API RPC that provides API for installing
     *            group flow
     * @param dpnId
     *            DPN on which group flow to be installed
     *
     * @param tableId
     *            Table Id for which drop flow to be installed
     * @param flowId
     *            Uniquely Identifiable id for flow
     * @param priority
     *            Priority of the flow
     * @param flowName
     *            Name of the flow
     * @param idleTimeOut
     *            Idle TimeOut of the flow
     * @param hardTimeOut
     *            Hard Timeout of the flow
     * @param cookie
     *            Cookie for the flow
     */
    public static void installDropFlow(final IMdsalApiManager mdSalManager,
            final BigInteger dpnId, final short tableId, final String flowId,
            final int priority, final String flowName, final int idleTimeOut,
            final int hardTimeOut, final BigInteger cookie) {
        LOG.trace("Installing default drop flow for table {} on dpn {}",
                tableId, dpnId);
        final FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, tableId,
                flowId, priority, flowName, idleTimeOut, hardTimeOut, cookie,
                new ArrayList<MatchInfoBase>(),
                Arrays.asList(new InstructionInfo(InstructionType.apply_actions,
                        Arrays.asList(new ActionInfo(ActionType.drop_action,
                                new String[] {})))));
        mdSalManager.installFlow(flowEntity);
    }

    /**
     * Get Default ARP Responder Drop flow on the DPN
     *
     * @param dpnId
     *            DPN on which group flow to be installed
     *
     */
    public static FlowEntity getTableMissFlow(final BigInteger dpnId) {
        return MDSALUtil.buildFlowEntity(dpnId, NwConstants.ARP_RESPONDER_TABLE,
                String.valueOf(NwConstants.ARP_RESPONDER_TABLE), NwConstants.TABLE_MISS_PRIORITY, ArpResponderConstant.DROP_FLOW_NAME.value(), 0, 0, ArpResponderConstant.Cookies.DROP_COOKIE.value(),
                new ArrayList<MatchInfo>(),
                Arrays.asList(new InstructionInfo(InstructionType.apply_actions,
                        Arrays.asList(new ActionInfo(ActionType.drop_action,
                                new String[] {})))));
    }

    /**
     * Get Bucket Actions for ARP Responder Group Flow
     *
     * <p>
     * Install Default Groups, Group has 3 Buckets
     * </p>
     * <ul>
     * <li>Punt to controller</li>
     * <li>Resubmit to Table {@link NwConstants#LPORT_DISPATCHER_TABLE}, for
     * ELAN flooding
     * <li>Resubmit to Table {@link NwConstants#ARP_RESPONDER_TABLE}, for ARP
     * Auto response from DPN itself</li>
     * </ul>
     *
     * @param resubmitTableId
     *            Resubmit Flow Table Id
     * @param resubmitTableId2
     *            Resubmit Flow Table Id
     * @return List of bucket actions
     */
    public static List<BucketInfo> getDefaultBucketInfos(
            final short resubmitTableId, final short resubmitTableId2) {
        final List<BucketInfo> buckets = new ArrayList<>();
        buckets.add(new BucketInfo(Arrays.asList(new ActionInfo(
                ActionType.punt_to_controller, new String[] {}))));
        buckets.add(new BucketInfo(
                Arrays.asList(new ActionInfo(ActionType.nx_resubmit,
                        new String[] { String.valueOf(resubmitTableId) }))));
        buckets.add(new BucketInfo(
                Arrays.asList(new ActionInfo(ActionType.nx_resubmit,
                        new String[] { String.valueOf(resubmitTableId2) }))));
        return buckets;
    }

    /**
     * Get Match Criteria for the ARP Responder Flow
     * <p>
     * List of Match Criteria for ARP Responder
     * </p>
     * <ul>
     * <li>Packet is ARP</li>
     * <li>Packet is ARP Request</li>
     * <li>The ARP packet is requesting for Gateway IP</li>
     * <li>Metadata which is generated by using Service
     * Index({@link NwConstants#L3VPN_SERVICE_INDEX}) Lport Tag
     * ({@link MetaDataUtil#METADATA_MASK_LPORT_TAG}) and VRF
     * ID({@link MetaDataUtil#METADATA_MASK_VRFID})</li>
     * </ul>
     *
     * @param lPortTag
     *            LPort Tag
     * @param vpnId
     *            VPN ID
     * @param ipAddress
     *            Gateway IP
     * @return List of Match criteria
     */
    public static List<MatchInfo> getMatchCriteria(final int lPortTag,
            final long vpnId, final String ipAddress) {

        final List<MatchInfo> matches = new ArrayList<MatchInfo>();
        short mIndex = NwConstants.L3VPN_SERVICE_INDEX;
        final BigInteger metadata = MetaDataUtil.getMetaDataForLPortDispatcher(
                lPortTag, ++mIndex, MetaDataUtil.getVpnIdMetadata(vpnId));
        final BigInteger metadataMask = MetaDataUtil
                .getMetaDataMaskForLPortDispatcher(
                        MetaDataUtil.METADATA_MASK_SERVICE_INDEX,
                        MetaDataUtil.METADATA_MASK_LPORT_TAG,
                        MetaDataUtil.METADATA_MASK_VRFID);

        // Matching Arp request flows
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { NwConstants.ETHTYPE_ARP }));
        matches.add(new MatchInfo(MatchFieldType.metadata,
                new BigInteger[] { metadata, metadataMask }));
        matches.add(new MatchInfo(MatchFieldType.arp_op,
                new long[] { ArpReplyOrRequest.REQUEST.getArpOperation() }));
        matches.add(new MatchInfo(MatchFieldType.arp_tpa,
                new String[] { ipAddress, "32" }));
        return matches;

    }

    /**
     * Get List of actions for ARP Responder Flows
     *
     * Actions consists of all the enum constant value actions from
     * {@link ArpActionField} and Egress Actions Retrieved
     *
     * @param ifaceMgrRpcService
     *            Interface manager RPC reference to invoke RPC to get Egress
     *            actions for the interface
     * @param vpnInterface
     *            VPN Interface for which flow to be installed
     * @param ipAddress
     *            Gateway IP Address
     * @param macAddress
     *            Gateway MacAddress
     * @return List of ARP Responder Actions actions
     */
    public static List<Action> getActions(
            final OdlInterfaceRpcService ifaceMgrRpcService,
            final String vpnInterface, final String ipAddress,
            final MacAddress macAddress) {

        final List<Action> actions = new ArrayList<>();
        int actionCounter = 0;
        actions.add(ArpActionField.MOVE_ETH_SRC_TO_ETH_DST
                .buildAction(actionCounter, actionCounter++));
        actions.add(ArpActionField.SET_SRC_ETH.buildAction(actionCounter,
                actionCounter++, macAddress));
        actions.add(ArpActionField.SET_ARP_OP.buildAction(actionCounter,
                actionCounter++, ArpReplyOrRequest.REPLY));
        actions.add(ArpActionField.MOVE_SHA_TO_THA.buildAction(actionCounter,
                actionCounter++));
        actions.add(ArpActionField.MOVE_SPA_TO_TPA.buildAction(actionCounter,
                actionCounter++));
        actions.add(ArpActionField.LOAD_MAC_TO_SHA.buildAction(actionCounter,
                actionCounter++, macAddress));
        actions.add(ArpActionField.LOAD_IP_TO_SPA.buildAction(actionCounter,
                actionCounter++, ipAddress));
        actions.add(createNxOfInPortAction(actionCounter++, 0));
        /*
         * actions.add(ArpActionField.OUTPUT_TO_INPORT.buildAction(
         * actionCounter, actionCounter++));
         */
        actions.addAll(getEgressActionsForInterface(ifaceMgrRpcService,
                vpnInterface, actionCounter));
        LOG.trace("Total Number of actions is {}", actionCounter);
        return actions;

    }

    /**
     * Install ARP Responder FLOW
     *
     * @param mdSalManager
     *            Reference of MDSAL API RPC that provides API for installing
     *            flow
     * @param dpnId
     *            DPN on which flow to be installed
     * @param flowId
     *            Uniquely Identifiable Arp Responder Table flow Id
     * @param flowName
     *            Readable flow name
     * @param priority
     *            Flow Priority
     * @param cookie
     *            Flow Cookie
     * @param matches
     *            List of Match Criteria for the flow
     * @param actions
     *            List of Actions for the flow
     */
    public static void installFlow(final IMdsalApiManager mdSalManager,
            final BigInteger dpnId, final String flowId, final String flowName,
            final int priority, final BigInteger cookie,
            List<MatchInfo> matches, List<Action> actions) {

        final Flow flowEntity = MDSALUtil.buildFlowNew(
                NwConstants.ARP_RESPONDER_TABLE, flowId, priority, flowName, 0,
                0, cookie, matches,
                Arrays.asList(MDSALUtil.buildApplyActionsInstruction(actions)));
        mdSalManager.installFlow(dpnId, flowEntity);
    }

    /**
     * Remove flow form DPN
     *
     * @param mdSalManager
     *            Reference of MDSAL API RPC that provides API for installing
     *            flow
     * @param dpnId
     *            DPN form which flow to be removed
     * @param flowId
     *            Uniquely Identifiable Arp Responder Table flow Id that is to
     *            be removed
     */
    public static void removeFlow(final IMdsalApiManager mdSalManager,
            final BigInteger dpnId, final String flowId) {
        final Flow flowEntity = MDSALUtil
                .buildFlow(NwConstants.ARP_RESPONDER_TABLE, flowId);
        mdSalManager.removeFlow(dpnId, flowEntity);
    }

    /**
     * Creates Uniquely Identifiable flow Id
     * <p>
     * <b>Refer:</b> {@link ArpResponderConstant#FLOW_ID_FORMAT}
     *
     * @param lportTag
     *            LportTag of the flow
     * @param gwIp
     *            Gateway IP for which ARP Response flow to be installed
     * @return Unique Flow Id
     */
    public static String getFlowID(final int lportTag, final String gwIp) {
        return MessageFormat.format(ArpResponderConstant.FLOW_ID_FORMAT.value(),
                NwConstants.ARP_RESPONDER_TABLE, lportTag, gwIp);
    }

    /**
     * Generate Cookie per flow
     * <p>
     * Cookie is generated by Summation of
     * {@link ArpResponderConstant.Cookies#ARP_RESPONDER_COOKIE} + lportTag +
     * Gateway IP
     *
     * @param lportTag
     *            Lport Tag of the flow
     * @param gwIp
     *            Gateway IP for which ARP Response flow to be installed
     * @return Cookie
     */
    public static BigInteger generateCookie(final long lportTag,
            final String gwIp) {
        LOG.trace("IPAddress in long {}", gwIp);
        return ArpResponderConstant.Cookies.ARP_RESPONDER_COOKIE.value()
                .add(BigInteger.valueOf(lportTag))
                .add(BigInteger.valueOf(ipTolong(gwIp)));
    }

    /**
     * Get IP Address in Long from String
     *
     * @param address
     *            IP Address that to be converted to long
     * @return Long value of the IP Address
     */
    private static long ipTolong(String address) {

        // Parse IP parts into an int array
        long[] ip = new long[4];
        String[] parts = address.split("\\.");

        for (int i = 0; i < 4; i++) {
            ip[i] = Long.parseLong(parts[i]);
        }
        // Add the above IP parts into an int number representing your IP
        // in a 32-bit binary form
        long ipNumbers = 0;
        for (int i = 0; i < 4; i++) {
            ipNumbers += ip[i] << (24 - (8 * i));
        }
        return ipNumbers;

    }

    /**
     * Create Action that sets in_port, it set NICIRA field NXM_OF_IN_PORT
     * <P><b>NOTE:</b>A temporary fix until in_port is overridden in table=0
     * @param actionKey action key
     * @param inPortVal in port value to be set
     * @return  load in_port action
     */
    public static org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action createNxOfInPortAction(final int actionKey, final int inPortVal) {

        NxRegLoad r = new NxRegLoadBuilder().setDst(new DstBuilder().setDstChoice(new DstNxOfInPortCaseBuilder().setOfInPort(Boolean.TRUE).build())
                .setStart(Integer.valueOf(0)).setEnd(Integer.valueOf(15)).build()).setValue(BigInteger.valueOf(inPortVal)).build();
        ActionBuilder abExt = new ActionBuilder();
        abExt.setKey(new ActionKey(actionKey));
        abExt.setOrder(actionKey);
        abExt.setAction(new NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder().setNxRegLoad(r).build());
        return abExt.build();
    }

    /**
     * Get List of Egress Action for the VPN interface
     *
     * @param ifaceMgrRpcService
     *            Interface Manager RPC reference that invokes API to retrieve
     *            Egress Action
     * @param ifName
     *            VPN Interface for which Egress Action to be retrieved
     * @param actionCounter
     *            Action Key
     * @return List of Egress Actions
     */
    public static List<Action> getEgressActionsForInterface(
            final OdlInterfaceRpcService ifaceMgrRpcService, String ifName,
            int actionCounter) {
        final List<Action> listActions = new ArrayList<>();
        try {
            final RpcResult<GetEgressActionsForInterfaceOutput> result = ifaceMgrRpcService
                    .getEgressActionsForInterface(
                            new GetEgressActionsForInterfaceInputBuilder()
                                    .setIntfName(ifName).build())
                    .get();
            if (result.isSuccessful()) {
                final List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actions = result
                        .getResult().getAction();
                for (final Action action : actions) {

                    listActions
                            .add(new org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder(
                                    action).setKey(new ActionKey(actionCounter))
                                            .setOrder(actionCounter++).build());

                }
            } else {
                LOG.warn(
                        "RPC Call to Get egress actions for interface {} returned with Errors {}",
                        ifName, result.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", ifName,
                    e);
        }
        return listActions;
    }

}

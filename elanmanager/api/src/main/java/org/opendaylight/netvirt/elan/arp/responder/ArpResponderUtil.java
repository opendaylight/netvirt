/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.arp.responder;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionLoadIpToSpa;
import org.opendaylight.genius.mdsalutil.actions.ActionLoadMacToSha;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveShaToTha;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationEth;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSpaToTpa;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetArpOp;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchArpOp;
import org.opendaylight.genius.mdsalutil.matches.MatchArpTpa;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.add.group.input.buckets.bucket.action.action.NxActionResubmitRpcAddGroupCase;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arp Responder Utility Class.
 */
public final class ArpResponderUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ArpResponderUtil.class);

    private static final long WAIT_TIME_FOR_SYNC_INSTALL = Long.getLong("wait.time.sync.install", 300L);

    /**
     * A Utility class.
     */
    private ArpResponderUtil() {
    }

    /**
     * Install Group flow on the DPN.
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
    public static void installGroup(IMdsalApiManager mdSalManager, BigInteger dpnId, long groupdId, String groupName,
            List<BucketInfo> buckets) {
        LOG.trace("Installing group flow on dpn {}", dpnId);
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupdId, groupName, GroupTypes.GroupAll, buckets);
        mdSalManager.syncInstallGroup(groupEntity);
        try {
            Thread.sleep(WAIT_TIME_FOR_SYNC_INSTALL);
        } catch (InterruptedException e1) {
            LOG.warn("Error while waiting for ARP Responder Group Entry to be installed on DPN {} ", dpnId);
        }
    }

    /**
     * Get Default ARP Responder Drop flow on the DPN.
     *
     * @param dpnId
     *            DPN on which group flow to be installed
     */
    public static FlowEntity getArpResponderTableMissFlow(BigInteger dpnId) {
        return MDSALUtil.buildFlowEntity(dpnId, NwConstants.ARP_RESPONDER_TABLE,
                String.valueOf(NwConstants.ARP_RESPONDER_TABLE), NwConstants.TABLE_MISS_PRIORITY,
                ArpResponderConstant.DROP_FLOW_NAME.value(), 0, 0, NwConstants.COOKIE_ARP_RESPONDER,
                new ArrayList<MatchInfo>(),
                Collections.singletonList(new InstructionApplyActions(Collections.singletonList(new ActionDrop()))));
    }

    /**
     * Get Bucket Actions for ARP Responder Group Flow.
     *
     * <p>
     * Install Default Groups, Group has 1 Bucket
     * </p>
     * <ul>
     * <li>Resubmit to Table {@link NwConstants#ARP_RESPONDER_TABLE}, for ARP
     * Auto response from DPN itself</li>
     * </ul>
     *
     * @param resubmitTableId
     *            Resubmit Flow Table Id
     * @return List of bucket actions
     */
    public static List<BucketInfo> getDefaultBucketInfos(short resubmitTableId) {
        return Arrays.asList(
                new BucketInfo(Collections.singletonList(new ActionNxResubmit(resubmitTableId))));
    }

    /**
     * Get Match Criteria for the ARP Responder Flow.
     *
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
     * @param lportTag
     *            LPort Tag
     * @param elanInstance
     *            Elan Instance
     * @param ipAddress
     *            Ip Address to be matched to this flow
     * @return List of Match criteria
     */
    public static List<MatchInfo> getMatchCriteria(int lportTag, ElanInstance elanInstance,
            String ipAddress) {

        BigInteger metadata = ElanHelper.getElanMetadataLabel(elanInstance.getElanTag(), lportTag);
        BigInteger metadataMask = ElanHelper.getElanMetadataMask();
        return Arrays.asList(MatchEthernetType.ARP, MatchArpOp.REQUEST, new MatchArpTpa(ipAddress, "32"),
                new MatchMetadata(metadata, metadataMask));

    }

    /**
     * Get List of actions for ARP Responder Flows.
     *
     * <p>
     * Actions consists of all the ARP actions and Resubmit Action to table
     * {@link NwConstants#ELAN_BASE_TABLE} such that packets can flow ELAN Rule
     *
     * @param ipAddress
     *            IP Address for which ARP Response packet is to be generated
     * @param macAddress
     *            MacAddress for which ARP Response packet is to be generated
     * @return List of ARP Responder Actions actions
     */
    public static List<Action> getActions(IInterfaceManager ifaceMgrRpcService, ItmRpcService itmRpcService,
                                          String ifName, String ipAddress, String macAddress,
                                          boolean isTunnelInterface) {

        AtomicInteger actionCounter = new AtomicInteger();
        List<Action> actions = arpActions.apply(actionCounter, macAddress, ipAddress);
        actions.addAll(getEgressActionsForInterface(ifaceMgrRpcService, itmRpcService, ifName, actionCounter.get(),
                isTunnelInterface));
        LOG.trace("Total Number of actions is {}", actionCounter);
        return actions;

    }

    /**
     * A Interface that represent lambda TriFunction.
     *
     * @param <T>
     *            Input type
     * @param <U>
     *            Input type
     * @param <S>
     *            Input type
     * @param <R>
     *            Return Type
     */
    @SuppressWarnings("checkstyle:ParameterName")
    public interface TriFunction<T, U, S, R> {
        /**
         * Apply the Action.
         *
         * @param t
         *            Input1
         * @param u
         *            Input2
         * @param s
         *            Input3
         * @return computed result
         */
        R apply(T t, U u, S s);
    }

    /**
     * Lambda to apply arpAction. Inputs action counter, mac address and ip
     * address
     */
    private static TriFunction<AtomicInteger, String, String, List<Action>> arpActions = (actionCounter, mac, ip) -> {
        List<Action> actions = new ArrayList<>();
        Collections.addAll(actions, new ActionMoveSourceDestinationEth().buildAction(actionCounter.getAndIncrement()),
                new ActionSetFieldEthernetSource(new MacAddress(mac)).buildAction(actionCounter.getAndIncrement()),
                new ActionSetArpOp(NwConstants.ARP_REPLY).buildAction(actionCounter.getAndIncrement()),
                new ActionMoveShaToTha().buildAction(actionCounter.getAndIncrement()),
                new ActionMoveSpaToTpa().buildAction(actionCounter.getAndIncrement()),
                new ActionLoadMacToSha(new MacAddress(mac)).buildAction(actionCounter.getAndIncrement()),
                new ActionLoadIpToSpa(ip).buildAction(actionCounter.getAndIncrement()),
                new ActionNxLoadInPort(BigInteger.ZERO).buildAction(actionCounter.getAndIncrement()));
        return actions;

    };

    /**
     * Get instruction list for ARP responder flows.
     */
    public static List<Instruction> getInterfaceInstructions(IInterfaceManager ifaceMgrRpcService, String interfaceName,
            String ipAddress, String macAddress, ItmRpcService itmRpcService) {
        List<Action> actions = ArpResponderUtil.getActions(ifaceMgrRpcService, itmRpcService, interfaceName, ipAddress,
                macAddress, false);
        return Collections.singletonList(MDSALUtil.buildApplyActionsInstruction(actions));
    }

    /**
     * Get instruction list for ARP responder flows originated from ext-net e.g.
     * router-gw/fip.<br>
     * The split-horizon bit should be reset in order to allow traffic from
     * provider network to be routed back to flat/VLAN network and override the
     * egress table drop flow.<br>
     * In order to allow write-metadata in the ARP responder table the resubmit
     * action needs to be replaced with goto instruction.
     */
    public static List<Instruction> getExtInterfaceInstructions(IInterfaceManager ifaceMgrRpcService,
                                                                ItmRpcService itmRpcService,
                                                                String extInterfaceName, String ipAddress,
                                                                String macAddress) {
        AtomicInteger tableId = new AtomicInteger(-1);
        List<Instruction> instructions = new ArrayList<>();
        List<Action> actions = getActions(ifaceMgrRpcService, itmRpcService, extInterfaceName, ipAddress, macAddress,
                false);
        actions.removeIf(v -> {
            org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action actionClass = v
                    .getAction();
            if (actionClass instanceof NxActionResubmitRpcAddGroupCase) {
                tableId.set(((NxActionResubmitRpcAddGroupCase) actionClass).getNxResubmit().getTable());
                return true;
            } else {
                return false;
            }
        });

        instructions.add(MDSALUtil.buildApplyActionsInstruction(actions, 0));

        if (tableId.get() != -1) {
            // replace resubmit action with goto so it can co-exist with
            // write-metadata
            if ((short) tableId.get() > NwConstants.ARP_RESPONDER_TABLE) {
                instructions.add(new InstructionGotoTable((short) tableId.get()).buildInstruction(2));
            } else {
                LOG.warn("Failed to insall responder flow for interface {}. Resubmit to {} can't be replaced with goto",
                        extInterfaceName, tableId);
            }
        }

        return instructions;
    }

    /**
     * Install ARP Responder FLOW.
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
     * @param instructions
     *            List of Instructions for the flow
     */
    public static void installFlow(IMdsalApiManager mdSalManager, BigInteger dpnId, String flowId, String flowName,
            int priority, BigInteger cookie, List<MatchInfo> matches, List<Instruction> instructions) {
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.ARP_RESPONDER_TABLE, flowId, priority, flowName, 0, 0,
                cookie, matches, instructions);
        mdSalManager.installFlow(dpnId, flowEntity);
    }

    /**
     * Remove flow form DPN.
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
    public static void removeFlow(IMdsalApiManager mdSalManager, BigInteger dpnId, String flowId) {
        Flow flowEntity = MDSALUtil.buildFlow(NwConstants.ARP_RESPONDER_TABLE, flowId);
        mdSalManager.removeFlow(dpnId, flowEntity);
    }

    /**
     * Creates Uniquely Identifiable flow Id.
     *
     * @param lportTag
     *            LportTag of the flow
     * @param ipAdress
     *            Gateway IP for which ARP Response flow to be installed
     * @return Unique Flow Id
     *
     * @see ArpResponderConstant#FLOW_ID_FORMAT_WITH_LPORT
     * @see ArpResponderConstant#FLOW_ID_FORMAT_WITHOUT_LPORT
     */
    public static String getFlowId(int lportTag, String ipAdress) {
        return MessageFormat.format(ArpResponderConstant.FLOW_ID_FORMAT_WITH_LPORT.value(),
                        NwConstants.ARP_RESPONDER_TABLE, lportTag, ipAdress);
    }

    /**
     * Generate Cookie per flow.
     *
     * <p>
     * Cookie is generated by Summation of
     * {@link NwConstants#COOKIE_ARP_RESPONDER} + 1 + lportTag + Gateway IP
     *
     * @param lportTag
     *            Lport Tag of the flow
     * @param ipAddress
     *            Gateway IP for which ARP Response flow to be installed
     * @return Cookie
     */
    public static BigInteger generateCookie(int lportTag, String ipAddress) {
        LOG.trace("IPAddress in long {}", ipAddress);
        BigInteger cookie = NwConstants.COOKIE_ARP_RESPONDER.add(BigInteger.valueOf(255))
                .add(BigInteger.valueOf(ipTolong(ipAddress)));
        return cookie.add(BigInteger.valueOf(lportTag));
    }

    private static BigInteger buildCookie(short tableId, int arpOpType) {
        return NwConstants.COOKIE_ARP_RESPONDER.add(BigInteger.ONE).add(
                BigInteger.valueOf(tableId).add(BigInteger.valueOf(arpOpType)));
    }

    private static String buildFlowRef(short tableId, int arpOpType) {
        return (tableId == NwConstants.ARP_CHECK_TABLE
                ? ArpResponderConstant.FLOWID_PREFIX_FOR_ARP_CHECK.value()
                : ArpResponderConstant.FLOWID_PREFIX_FOR_MY_GW_MAC.value()) + tableId + NwConstants.FLOWID_SEPARATOR
                + (arpOpType == NwConstants.ARP_REQUEST ? "arp.request" : "arp.replay");
    }

    public static FlowEntity createArpDefaultFlow(BigInteger dpId, short tableId, int arpOpType,
            Supplier<List<MatchInfo>> matches, Supplier<List<ActionInfo>> actions) {

        List<InstructionInfo> instructions = Collections.singletonList(new InstructionApplyActions(actions.get()));
        return MDSALUtil.buildFlowEntity(dpId, tableId, buildFlowRef(tableId, arpOpType),
                NwConstants.DEFAULT_ARP_FLOW_PRIORITY, buildFlowRef(tableId, arpOpType), 0, 0,
                buildCookie(tableId, arpOpType), matches.get(), instructions);
    }

    /**
     * Get IP Address in Long from String.
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
     * Get List of Egress Action for the VPN interface.
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
    public static List<Action> getEgressActionsForInterface(IInterfaceManager ifaceMgrRpcService,
                                                            ItmRpcService itmRpcService, String ifName,
                                                            int actionCounter, boolean isTunnelInterface) {
        if (isTunnelInterface && ifaceMgrRpcService.isItmDirectTunnelsEnabled()) {
            try {
                RpcResult result = itmRpcService.getEgressActionsForTunnel(new GetEgressActionsForTunnelInputBuilder()
                        .setIntfName(ifName).build()).get();
                List<Action> listActions = new ArrayList<>();
                if (!result.isSuccessful()) {
                    LOG.error("getEgressActionsForInterface: RPC Call to Get egress actions for interface {} "
                            + "returned with Errors {}", ifName, result.getErrors());
                } else {
                    listActions = ((GetEgressActionsForTunnelOutput) result.getResult()).getAction();
                }
                return listActions;
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("getEgressActionsForInterface: Exception when egress actions for interface {}", ifName, e);
            }
        } else {
            List<ActionInfo> actionInfos = ifaceMgrRpcService.getInterfaceEgressActions(ifName);
            AtomicInteger counter = new AtomicInteger(actionCounter);
            return actionInfos.stream().map(v -> v.buildAction(counter.getAndIncrement())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Uses the IdManager to retrieve ARP Responder GroupId from ELAN pool.
     *
     * @param idManager
     *            the id manager
     * @return the integer
     */
    public static Long retrieveStandardArpResponderGroupId(IdManagerService idManager) {

        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                .setPoolName(ArpResponderConstant.ELAN_ID_POOL_NAME.value())
                .setIdKey(ArpResponderConstant.ARP_RESPONDER_GROUP_ID.value()).build();

        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                LOG.trace("Retrieved Group Id is {}", rpcResult.getResult().getIdValue());
                return rpcResult.getResult().getIdValue();
            } else {
                LOG.warn("RPC Call to Allocate Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when Allocating Id", e);
        }
        return 0L;
    }

}

/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CleanupDpnForVpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CleanupDpnForVpnInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.PopulateFibOnDpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.PopulateFibOnDpnInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToPseudoPortTagData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.pseudo.port.tag.data.VpnToPseudoPortTag;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.pseudo.port.tag.data.VpnToPseudoPortTagBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.pseudo.port.tag.data.VpnToPseudoPortTagKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class VpnServiceChainUtils {
    private static final Logger logger = LoggerFactory.getLogger(VpnServiceChainUtils.class);

    /**
     * L3VPN Service cx
     *
     * @param scfTag
     * @return
     */
    public static BigInteger getMetadataSCF(int scfTag) { // TODO: Move to a common place
        return (new BigInteger("FF", 16).and(BigInteger.valueOf(scfTag))).shiftLeft(32);
    }

    /**
     * @param vpnId
     * @return
     */
    public static BigInteger getCookieL3(int vpnId) {
        return CloudServiceChainConstants.COOKIE_L3_BASE.add(new BigInteger("0610000", 16))
                                                      .add(BigInteger.valueOf(vpnId));
    }


    /**
     * @param rd Route distinguisher
     * @return
     */
    public static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }


    /**
     * Retrieves from MDSAL the Operational Data of the VPN specified by its
     * Route-Distinguisher
     *
     * @param rd Route-Distinguisher of the VPN
     *
     * @return
     */
    public static Optional<VpnInstanceOpDataEntry> getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnServiceChainUtils.getVpnInstanceOpDataIdentifier(rd);
        return MDSALDataStoreUtils.read(broker, LogicalDatastoreType.OPERATIONAL, id);
    }

    /**
     * @param rd Route distinguisher
     * @return
     */
    public static InstanceIdentifier<VrfTables> buildVrfId(String rd) {
        InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder = InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> id = idBuilder.build();
        return id;
    }

    static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, BigInteger dpnId) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd))
            .child(VpnToDpnList.class, new VpnToDpnListKey(dpnId)).build();
    }

    static InstanceIdentifier<VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
                                 .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }

    /**
     * Retrieves the VPN's Route Distinguisher out from the VpnName
     * @param broker
     * @param vpnName The Vpn Instance Name. Typically the UUID.
     * @return
     */
    public static String getVpnRd(DataBroker broker, String vpnName) {

        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<VpnInstance> vpnInstance = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);

        String rd = null;
        if (vpnInstance.isPresent()) {
            rd = vpnInstance.get().getVrfId();
        }
        return rd;
    }

    /**
     * Returns all the VrfEntries that belong to a given VPN
     *
     * @param broker
     * @param rd Route-distinguisher of the VPN
     * @return
     */
    public static List<VrfEntry> getAllVrfEntries(DataBroker broker, String rd) {
        InstanceIdentifier<VrfTables> vpnVrfTables =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTable = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnVrfTables);
        List<VrfEntry> vpnVrfEntries = ( vrfTable.isPresent() ) ? vrfTable.get().getVrfEntry()
                                                                : new ArrayList<VrfEntry>();
        return vpnVrfEntries;
    }

    /**
     * Build the flow that must be inserted when there is a ScHop whose egressPort is a VPN Pseudo Port. In that case,
     * packets must be moved from the SCF to VPN Pipeline
     *
     * Flow matches:  VpnPseudo port lPortTag + SI=L3VPN
     * Actions: Write vrfTag in Metadata + goto FIB Table

     * @param vpnId Id of the Vpn that is the target of the packet
     * @param dpId The DPN where the flow must be installed/removed
     * @param addOrRemove
     * @param lportTag lportTag of the VpnPseudoLPort
     * @return
     */
    public static Flow buildLPortDispFromScfToL3VpnFlow(Long vpnId, BigInteger dpId, Integer lportTag,
                                                        int addOrRemove) {
        logger.info("buildLPortDispFlowForScf vpnId={} dpId={} lportTag={} addOrRemove={} ",
                    vpnId, dpId, lportTag, addOrRemove);
        List<MatchInfo> matches = buildMatchOnLportTagAndSI(lportTag,
                                                            CloudServiceChainConstants.L3VPN_SERVICE_IDENTIFIER);
        List<Instruction> instructions = buildSetVrfTagAndGotoFibInstructions(vpnId.intValue());

        String flowRef = getScfToL3VpnLportDispatcherFlowRef(lportTag);

        Flow result;
        if (addOrRemove == NwConstants.ADD_FLOW) {
            result = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                                            CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY, flowRef,
                                            0, 0, VpnServiceChainUtils.getCookieL3(vpnId.intValue()),
                                            matches, instructions);

        } else {
            result = new FlowBuilder().setTableId(NwConstants.LPORT_DISPATCHER_TABLE)
                                      .setId(new FlowId(flowRef))
                                      .build();
        }
        return result;
    }

    /**
     * @param lportTag
     * @param serviceIndex
     * @return
     */
    public static List<MatchInfo> buildMatchOnLportTagAndSI(Integer lportTag, short serviceIndex) {
        List<MatchInfo> matches = new ArrayList<MatchInfo>();

        matches.add(new MatchInfo(MatchFieldType.metadata,
                new BigInteger[] { MetaDataUtil.getMetaDataForLPortDispatcher(lportTag, serviceIndex),
                                   MetaDataUtil.getMetaDataMaskForLPortDispatcher() }));

        return matches;
    }

    /**
     * Builds a Flow entry that sets the VpnTag in metadata and sends to FIB table.
     * @param vpnTag
     * @return
     */
    public static List<Instruction> buildSetVrfTagAndGotoFibInstructions(Integer vpnTag) {
        List<Instruction> result = new ArrayList<Instruction>();
        int instructionKey = 0;
        result.add(MDSALUtil.buildAndGetWriteMetadaInstruction(BigInteger.valueOf(vpnTag),
                                                               MetaDataUtil.METADATA_MASK_VRFID,
                                                               ++instructionKey));
        result.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_FIB_TABLE, ++instructionKey));
        return result;
    }


    /**
     * Builds a Flow for the LFIB table that sets the LPortTag of the VpnPseudoPort and sends to LPortDispatcher table
     *
     * Matching: eth_type = MPLS, mpls_label = VPN MPLS label
     * Actions: setMetadata LportTag and SI=2, pop MPLS, Go to LPortDispacherTable
     *
     * @param dpId  DpnId
     * @param label MPLS label
     * @param nextHop Next Hop IP
     * @param lportTag Pseudo Logical Port tag
     * @return the FlowEntity
     */
    public static FlowEntity buildLFibVpnPseudoPortFlow(BigInteger dpId, Long label, String nextHop, Integer lportTag) {

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type, new long[] { NwConstants.ETHTYPE_MPLS_UC }));
        matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[] { label.toString() }));

        List<ActionInfo> actionsInfos =
                Arrays.asList(new ActionInfo(ActionType.pop_mpls, new String[] { label.toString() }));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.write_metadata,
                new BigInteger[]{
                       MetaDataUtil.getMetaDataForLPortDispatcher(lportTag,
                                                                  CloudServiceChainConstants.SCF_SERVICE_INDEX),
                       MetaDataUtil.getMetaDataMaskForLPortDispatcher()
                }));

        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] {NwConstants.L3_INTERFACE_TABLE}));
        String flowRef = getLFibVpnPseudoPortFlowRef(lportTag, label, nextHop);
        return MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_LFIB_TABLE, flowRef,
                                         CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY, flowRef, 0, 0,
                                         NwConstants.COOKIE_VM_LFIB_TABLE, matches, instructions);
    }


    /**
     * Modifies the LFIB table by adding/removing flows that redirects traffic
     * from a VPN into the SCF via the VpnPseudoLport.
     * Flows that match on the label and sets the VpnPseudoPort lportTag and
     * sends to LPortDispatcher (via table 80)
     *
     * @param dpId
     * @param addOrRemove
     * @param vrfEntries
     * @param lportTag
     */
    public static void programLFibEntriesForSCF(IMdsalApiManager mdsalMgr, BigInteger dpId, List<VrfEntry> vrfEntries,
                                                Integer lportTag, int addOrRemove) {
        for (VrfEntry vrfEntry : vrfEntries) {
            Long label = vrfEntry.getLabel();
            for (String nexthop : vrfEntry.getNextHopAddressList()) {
                FlowEntity flowEntity = buildLFibVpnPseudoPortFlow(dpId, label, nexthop, lportTag);
                if (addOrRemove == NwConstants.ADD_FLOW) {
                    mdsalMgr.installFlow(flowEntity);
                } else {
                    mdsalMgr.removeFlow(flowEntity);
                }
                logger.debug("LFIB Entry for label={}, destination={}, nexthop={} {} successfully in dpn={}",
                             label, vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddressList(),
                             (addOrRemove == NwConstants.DEL_FLOW ) ? "removed" : "installed", dpId);
            }
        }
    }

    /**
     * Installs/removes a flow in LPortDispatcher table that is in charge of sending the traffic to
     * the SCF Pipeline.
     *
     * @param mdsalManager
     * @param dpId
     * @param lportTag
     * @param scfTag
     * @param gotoTableId
     * @param addOrRemove
     */
    public static void programLPortDispatcherFlowForVpnToScf(IMdsalApiManager mdsalManager, BigInteger dpId,
                                                             Integer lportTag, int scfTag, short gotoTableId,
                                                             int addOrRemove) {
        FlowEntity flowEntity = VpnServiceChainUtils.buildLportFlowDispForVpnToScf(dpId, lportTag, scfTag, gotoTableId);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.installFlow(flowEntity);
        } else {
            mdsalManager.removeFlow(flowEntity);
        }
    }

    /**
     * Creates the flow that sends the packet from the VPN to the SCF pipeline. This usually happens when there is
     * an ScHop whose ingressPort is a VpnPseudoPort.
     *
     * Matches: lportTag = vpnPseudoLPortTag, SI = 1
     * Actions: setMetadata(scfTag), Go to: UpSubFilter table
     *
     * @param dpId
     * @param lportTag
     * @param scfTag
     * @param gotoTableId
     * @return the FlowEntity
     */
    public static FlowEntity buildLportFlowDispForVpnToScf(BigInteger dpId, Integer lportTag, int scfTag,
                                                           short gotoTableId) {
        List<MatchInfo> matches = buildMatchOnLportTagAndSI(lportTag, CloudServiceChainConstants.SCF_SERVICE_INDEX);
        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        instructions.add(new InstructionInfo(InstructionType.write_metadata, new BigInteger[] {
                VpnServiceChainUtils.getMetadataSCF(scfTag), CloudServiceChainConstants.METADATA_MASK_SCF_WRITE
        }));
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { gotoTableId }));
        String flowRef = getL3VpnToScfLportDispatcherFlowRef(lportTag);
        FlowEntity flowEntity =
            MDSALUtil.buildFlowEntity(dpId, NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                                      CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY, flowRef, 0, 0,
                                      getCookieSCHop(scfTag), matches, instructions);
        return flowEntity;

    }

    /**
     * Updates the mapping between VPNs and their respective VpnPseudoLport Tags
     *
     * @param broker
     * @param rd
     * @param lportTag
     * @param addOrRemove
     */
    public static void updateVpnToLportTagMap(final DataBroker broker, final String rd, final int lportTag,
                                              final int addOrRemove) {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            // No need to block Thrift Thread
            @Override
            public void run() {
                VpnToPseudoPortTagKey key = new VpnToPseudoPortTagKey(rd);
                InstanceIdentifier<VpnToPseudoPortTag> path = InstanceIdentifier.builder(VpnToPseudoPortTagData.class)
                                                                                .child(VpnToPseudoPortTag.class, key)
                                                                                .build();
                if ( addOrRemove == NwConstants.ADD_FLOW ) {
                    VpnToPseudoPortTag newValue =
                        new VpnToPseudoPortTagBuilder().setKey(key).setVrfId(rd).setLportTag((long) lportTag).build();
                    MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, path, newValue);
                } else {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, path);
                }

            }
        });
    }


    /**
     * Creates a Flow that does the trick of moving the packets from one VPN to another VPN.
     *
     * @param dstLportTag
     * @param vpnTag
     * @return
     */
    public static Flow buildLPortDispFlowForVpntoVpn(Integer dstLportTag, Integer vpnTag) {
        List<MatchInfo> matches = buildMatchOnLportTagAndSI(dstLportTag,
                                                                 CloudServiceChainConstants.L3VPN_SERVICE_IDENTIFIER);
        List<Instruction> instructions = buildSetVrfTagAndGotoFibInstructions(vpnTag);
        String flowRef = getL3VpnToL3VpnLportDispFlowRef(dstLportTag, vpnTag);
        Flow result = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                                             CloudServiceChainConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY, flowRef,
                                             0, 0, VpnServiceChainUtils.getCookieL3(vpnTag),
                                             matches, instructions);
        return result;
    }

    /**
     * Updates the VPN footprint by adding a 'fake' interface for the VpnPseudoPort. The objective of this
     * operation is that the FibManager, on one hand, maintains the FIB table even in DPNs where there are
     * no real VpnInterfaces and, on other hand, keeps maintaining the FIB table even after the last real
     * VpnInterface is removed.
     *
     * @param broker
     * @param fibRpcService
     * @param vpnId
     * @param dpnId
     * @param intfName
     * @param vpnName
     */
    public static void updateMappingDbs(DataBroker broker, FibRpcService fibRpcService, long vpnId,
                                        BigInteger dpnId, String intfName, String vpnName) {
        if (vpnName != null) {
            synchronized (vpnName.intern()) {
                String routeDistinguisher = getVpnRd(broker, vpnName);
                String rd = (routeDistinguisher == null) ? vpnName : routeDistinguisher;
                InstanceIdentifier<VpnToDpnList> id = getVpnToDpnListIdentifier(rd, dpnId);
                Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
                VpnInterfaces vpnInterface =
                    new VpnInterfacesBuilder().setInterfaceName(intfName).build();
                if (dpnInVpn.isPresent()) {
                    VpnUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                         VpnInterfaces.class,
                          new VpnInterfacesKey(intfName)), vpnInterface);
                } else {
                    VpnInstanceOpDataEntry vpnOpData = new VpnInstanceOpDataEntryBuilder().setVrfId(rd).setVpnId(vpnId)
                                                                                          .setVpnInstanceName(vpnName)
                                                                                          .build();
                    VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL,getVpnInstanceOpDataIdentifier(rd),
                                       vpnOpData);
                    VpnToDpnListBuilder vpnToDpnList = new VpnToDpnListBuilder().setDpnId(dpnId);
                    List<VpnInterfaces> vpnInterfaces =  new ArrayList<>();
                    vpnInterfaces.add(vpnInterface);
                    VpnUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id,
                                      vpnToDpnList.setVpnInterfaces(vpnInterfaces).build());
                    PopulateFibOnDpnInput populateFibInput =
                            new PopulateFibOnDpnInputBuilder().setDpid(dpnId).setVpnId(vpnId)
                                                              .setRd((rd == null) ? vpnName : rd)
                                                              .build();
                    fibRpcService.populateFibOnDpn(populateFibInput);
                }
            }
        } else {
            logger.debug("vpnName is null");
        }
    }

    /**
     * Updates the VPN footprint by removing a 'fake' interface that represents the VpnPseudoPort
     *
     * @param broker
     * @param fibRpcService
     * @param vpnId
     * @param dpnId
     * @param intfName
     * @param vpnName
     */
    public static void removeFromMappingDbs(DataBroker broker, FibRpcService fibRpcService, long vpnId,
                                                          BigInteger dpnId, String intfName, String vpnName) {
        //TODO: Delay 'DPN' removal so that other services can cleanup the entries for this dpn
        if (vpnName != null) {
            synchronized (vpnName.intern()) {
                String rd = VpnUtil.getVpnRd(broker, vpnName);
                InstanceIdentifier<VpnToDpnList> id = getVpnToDpnListIdentifier(rd, dpnId);
                Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
                if (dpnInVpn.isPresent()) {
                    List<VpnInterfaces> vpnInterfaces =
                        dpnInVpn.get().getVpnInterfaces();
                    VpnInterfaces currVpnInterface =
                        new VpnInterfacesBuilder().setInterfaceName(intfName).build();

                    if (vpnInterfaces.remove(currVpnInterface)) {
                        if (vpnInterfaces.isEmpty()) {
                            List<IpAddresses> ipAddresses = dpnInVpn.get().getIpAddresses();
                            if (ipAddresses == null || ipAddresses.isEmpty()) {
                                logger.debug("Sending cleanup event for dpn {} in VPN {}", dpnId, vpnName);
                                MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, id);
                                CleanupDpnForVpnInput cleanupVpnInDpnInput =
                                    new CleanupDpnForVpnInputBuilder().setDpid(dpnId).setVpnId(vpnId)
                                                                      .setRd((rd == null) ? vpnName : rd).build();
                                fibRpcService.cleanupDpnForVpn(cleanupVpnInDpnInput);
                            } else {
                                logger.debug("vpn interfaces are empty but ip addresses are present for the vpn {} in dpn {}",
                                             vpnName, dpnId);
                            }
                        } else {
                            MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                                VpnInterfaces.class,
                                    new VpnInterfacesKey(intfName)));
                        }
                    }
                }
            }
        }
    }



    private static BigInteger getCookieSCHop(int scfInstanceTag) {
        return CloudServiceChainConstants.COOKIE_SCF_BASE.add(new BigInteger("0610000", 16))
                .add(BigInteger.valueOf(scfInstanceTag));
    }

    /*
     * Id for the Flow that is inserted in LFIB that is in charge of receiving packets coming from
     * DC-GW and setting the VpnPseudoLport tag in metadata and send to LPortDispatcher. There is
     * one of this entries per VrfEntry.
     */
    private static String getLFibVpnPseudoPortFlowRef(int vpnLportTag, long label, String nextHop) {
        return new StringBuilder(64).append(CloudServiceChainConstants.VPN_PSEUDO_PORT_FLOWID_PREFIX)
                                    .append(NwConstants.FLOWID_SEPARATOR).append(vpnLportTag)
                                    .append(NwConstants.FLOWID_SEPARATOR).append(label)
                                    .append(NwConstants.FLOWID_SEPARATOR).append(nextHop).toString();
    }

    private static String getL3VpnToL3VpnLportDispFlowRef(Integer lportTag, Integer vpnTag) {
        return new StringBuilder().append(CloudServiceChainConstants.FLOWID_PREFIX_L3).append(lportTag)
                                  .append(NwConstants.FLOWID_SEPARATOR).append(vpnTag)
                                  .append(NwConstants.FLOWID_SEPARATOR)
                                  .append(CloudServiceChainConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY).toString();
    }

    /**
     * Id for the Flow that is inserted in LPortDispatcher table that is in charge of delivering packets
     * from the L3VPN to the SCF Pipeline.
     * The VpnPseudoLPort tag and the SCF_SERVICE_INDEX is enough to identify this kind of flows.
     */
    public static String getL3VpnToScfLportDispatcherFlowRef(Integer lportTag) {
        return new StringBuffer(64).append(CloudServiceChainConstants.VPN_PSEUDO_VPN2SCF_FLOWID_PREFIX).append(lportTag)
                                   .append(NwConstants.FLOWID_SEPARATOR).append(NwConstants.SCF_SERVICE_INDEX)
                                   .append(NwConstants.FLOWID_SEPARATOR)
                                   .append(CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY).toString();
    }

    /**
     * Builds an identifier for the flow that is inserted in LPortDispatcher table and that is in
     * charge of handling packets that are delivered from the SCF to the L3VPN Pipeline
     *
     * @param lportTag VpnPseudoLport Tag
     * @return
     */
    public static String getScfToL3VpnLportDispatcherFlowRef(Integer lportTag) {
        return new StringBuffer().append(CloudServiceChainConstants.VPN_PSEUDO_SCF2VPN_FLOWID_PREFIX).append(lportTag)
                                 .append(NwConstants.FLOWID_SEPARATOR).append(NwConstants.L3VPN_SERVICE_INDEX)
                                 .append(NwConstants.FLOWID_SEPARATOR)
                                 .append(CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY).toString();
    }

    // TODO: this method is copied from VpnUtil. It should be in a more centric place, like the
    // IdManager or the NwUtil.java class or something like that.
    /**
     * Returns the ids of the currently operative DPNs
     *
     * @param dataBroker
     * @return
     */
    public static List<BigInteger> getOperativeDPNs(DataBroker dataBroker) {
        List<BigInteger> result = new LinkedList<BigInteger>();
        InstanceIdentifier<Nodes> nodesInstanceIdentifier = InstanceIdentifier.builder(Nodes.class).build();
        Optional<Nodes> nodesOptional = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                                       nodesInstanceIdentifier);
        if (!nodesOptional.isPresent()) {
            return result;
        }
        Nodes nodes = nodesOptional.get();
        List<Node> nodeList = nodes.getNode();
        for (Node node : nodeList) {
            NodeId nodeId = node.getId();
            if (nodeId != null) {
                BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeId);
                result.add(dpnId);
            }
        }
        return result;
    }
}

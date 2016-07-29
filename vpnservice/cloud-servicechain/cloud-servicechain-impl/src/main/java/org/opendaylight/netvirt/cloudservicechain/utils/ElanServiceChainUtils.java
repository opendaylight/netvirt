/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.utils;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.ElanServiceChainState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.elan.to.pseudo.port.data.list.ElanToPseudoPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.elan.to.pseudo.port.data.list.ElanToPseudoPortDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.elan.to.pseudo.port.data.list.ElanToPseudoPortDataKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class ElanServiceChainUtils {

    private static final Logger logger = LoggerFactory.getLogger(ElanServiceChainUtils.class);


    public static InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
    }

    public static Optional<ElanInstance> getElanInstanceByName(DataBroker broker, String elanName) {
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
                getElanInstanceConfigurationDataPath(elanName));
    }

    public static Optional<Collection<BigInteger>> getElanDnsByName(DataBroker broker, String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnIfacesIid =
                InstanceIdentifier.builder(ElanDpnInterfaces.class)
                                  .child(ElanDpnInterfacesList.class,new ElanDpnInterfacesListKey(elanInstanceName))
                                  .build();
        Optional<ElanDpnInterfacesList> elanDpnIfacesOpc =
                MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnIfacesIid);
        if (!elanDpnIfacesOpc.isPresent()) {
            logger.warn("Could not find and DpnInterface for elan {}", elanInstanceName);
            return Optional.<Collection<BigInteger>>absent();
        }

        Collection<BigInteger> dpns = new HashSet<BigInteger>();
        List<DpnInterfaces> elanDpnIfaces = elanDpnIfacesOpc.get().getDpnInterfaces();
        for ( DpnInterfaces dpnIf : elanDpnIfaces) {
            dpns.add(dpnIf.getDpId());
        }

        return Optional.of(dpns);
    }

    public static BigInteger getElanMetadataLabel(long elanTag) {
        return (BigInteger.valueOf(elanTag)).shiftLeft(24);
    }

    public static BigInteger getElanMetadataMask() {
        return MetaDataUtil.METADATA_MASK_SERVICE.or(MetaDataUtil.METADATA_MASK_LPORT_TAG);
    }


    /* This flow is in charge of handling packets coming from ExtTunnelTable
     * that must be redirected to the SCF Pipeline.
     *  + Matches on lportTag=ElanPseudoLportTag + SI=1
     *  + Sets scfTag and sends to the DlSubsFilter table.
     *
     * @param dpnId
     * @param elanLportTag
     * @param elanTag
     * @param addOrRemove
     */
    public static void programLPortDispatcherToScf(IMdsalApiManager mdsalManager, BigInteger dpnId, int elanTag,
            int elanLportTag, short tableId, int scfTag, int addOrRemove) {
        logger.info("L2-ServiceChaining: programLPortDispatcherToScf dpId={} elanLportTag={} scfTag={} addOrRemove={} ",
                dpnId, elanLportTag, scfTag, addOrRemove);
        String flowRef = buildLportDispToScfFlowRef(elanLportTag, scfTag);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            List<MatchInfo> matches = Arrays.asList(
                    new MatchInfo(MatchFieldType.metadata,
                            new BigInteger[] { MetaDataUtil.getMetaDataForLPortDispatcher(elanLportTag,
                                    NwConstants.SCF_SERVICE_INDEX),
                                    MetaDataUtil.getMetaDataMaskForLPortDispatcher() }));
            int instructionKey = 0;
            List<Instruction> instructions = Arrays.asList(
                    MDSALUtil.buildAndGetWriteMetadaInstruction(VpnServiceChainUtils.getMetadataSCF(scfTag),
                            CloudServiceChainConstants.METADATA_MASK_SCF_WRITE,
                            instructionKey++),
                    MDSALUtil.buildAndGetGotoTableInstruction(tableId, instructionKey++) );

            Flow flow = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                    CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY, flowRef,
                    0, 0, ITMConstants.COOKIE_ITM_EXTERNAL.add(BigInteger.valueOf(elanTag)),
                    matches, instructions);
            mdsalManager.installFlow(dpnId, flow);
        } else {
            Flow flow = new FlowBuilder().setTableId(NwConstants.LPORT_DISPATCHER_TABLE)
                    .setId(new FlowId(flowRef)).build();
            mdsalManager.removeFlow(dpnId, flow);
        }
    }

    /* This flow is in charge of handling packets coming from the SCF Pipeline
     * when there is no matching ServiceChain.
     *
     *  + Matches on ElanPseudoPortTag and SI=3 (ELAN)
     *  + Sets elanTag and sends to DMAC table
     *
     * @param dpnId
     * @param elanLportTag
     * @param elanTag
     * @param addOrRemove
     */
    public static void programLPortDispatcherFromScf(IMdsalApiManager mdsalManager, BigInteger dpnId,
            int elanLportTag, int elanTag, int addOrRemove) {
        logger.info("L2-ServiceChaining: programLPortDispatcherFromScf dpId={} elanLportTag={} elanTag={} addOrRemove={} ",
                dpnId, elanLportTag, elanTag, addOrRemove);
        String flowRef = buildLportDispFromScfFlowRef(elanTag, elanLportTag );
        if (addOrRemove == NwConstants.ADD_FLOW) {
            List<MatchInfo> matches = Arrays.asList(
                    new MatchInfo(MatchFieldType.metadata,
                                  new BigInteger[] { MetaDataUtil.getMetaDataForLPortDispatcher(elanLportTag,
                                                                             NwConstants.ELAN_SERVICE_INDEX)
                                                                  .or(BigInteger.ONE),  // SH FLag
                                                     MetaDataUtil.getMetaDataMaskForLPortDispatcher()
                                                                 .or(BigInteger.ONE) })); // Bit mask for SH Flag
            int instructionKey = 0;
            List<Instruction> instructions = Arrays.asList(
                    MDSALUtil.buildAndGetWriteMetadaInstruction(ElanServiceChainUtils.getElanMetadataLabel(elanTag),
                            ElanServiceChainUtils.getElanMetadataMask(),
                            instructionKey++),
                    MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.ELAN_DMAC_TABLE, instructionKey++) );

            Flow flow = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                    CloudServiceChainConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY,
                    flowRef, 0, 0,
                    ITMConstants.COOKIE_ITM_EXTERNAL.add(BigInteger.valueOf(elanTag)),
                    matches, instructions);
            mdsalManager.installFlow(dpnId, flow);
        } else {
            Flow flow = new FlowBuilder().setTableId(NwConstants.LPORT_DISPATCHER_TABLE)
                    .setId(new FlowId(flowRef)).build();
            mdsalManager.removeFlow(dpnId, flow);
        }
    }


    /* This flow is in charge of receiving packets from the TOR and sending
     * them to the SCF Pipeline by setting the LportTag of ElanPseudoPort.
     * Note that ELAN already has a flow in this table that redirects packets
     * to the ELAN Pipeline. However, the flow for the SCF Pipeline will have
     * higher priority, and will only be present when there is a ServiceChain
     * using this ElanPseudoPort.
     *
     *  + Matches on the VNI
     *  + Sets the ElanPseudoPort tag in the Metadata and sends to
     *    LPortDispatcher via table 80.
     *
     * @param dpnId
     * @param elanLportTag
     * @param vni
     * @param elanTag
     * @param addOrRemove
     */
    public static void programExternalTunnelTable(IMdsalApiManager mdsalManager, BigInteger dpnId, int elanLportTag,
            Long vni, int elanTag, int addOrRemove) {
        logger.info("L2-ServiceChaining: programExternalTunnelTable dpId={} vni={} elanLportTag={} addOrRemove={} ",
                dpnId, vni, elanLportTag, addOrRemove);
        String flowRef = buildExtTunnelTblToLportDispFlowRef(vni, elanLportTag);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            List<MatchInfo> matches = Arrays.asList(new MatchInfo(MatchFieldType.tunnel_id,
                    new BigInteger[] { BigInteger.valueOf(vni) } ) );
            List<Instruction> instructions = buildSetLportTagAndGotoLportDispInstructions(elanLportTag);
            Flow flow = MDSALUtil.buildFlowNew(NwConstants.EXTERNAL_TUNNEL_TABLE, flowRef,
                    CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY, flowRef,
                    0, 0, ITMConstants.COOKIE_ITM_EXTERNAL.add(BigInteger.valueOf(elanTag)),
                    matches, instructions);
            mdsalManager.installFlow(dpnId, flow);
        } else {
            Flow flow = new FlowBuilder().setTableId(NwConstants.EXTERNAL_TUNNEL_TABLE).setId(new FlowId(flowRef)).build();
            mdsalManager.removeFlow(dpnId, flow);
        }
    }

    /**
     * Builds a List of Instructions that set the ElanPseudoPort Tag in
     * metadata and sends to LPortDispatcher table (via Table 80)
     *
     * @param lportTag LPortTag of the ElanPseudoPort
     *
     * @return the List of Instructions
     */
    public static List<Instruction> buildSetLportTagAndGotoLportDispInstructions(long lportTag) {
        int instructionKey = 0;
        List<Instruction> result =
                Arrays.asList(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getLportTagMetaData((int) lportTag),
                        MetaDataUtil.getMetaDataMaskForLPortDispatcher(),
                        ++instructionKey),
                        MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_INTERFACE_TABLE, ++instructionKey));
        return result;
    }

    private static String buildExtTunnelTblToLportDispFlowRef(Long vni, int elanLportTag) {
        return CloudServiceChainConstants.L2_FLOWID_PREFIX + vni
                + NwConstants.FLOWID_SEPARATOR + elanLportTag;
    }

    private static String buildLportDispToScfFlowRef(int elanLportTag, int scfTag) {
        return CloudServiceChainConstants.ELAN_TO_SCF_L2_FLOWID_PREFIX + elanLportTag
                + NwConstants.FLOWID_SEPARATOR + scfTag;
    }

    private static String buildLportDispFromScfFlowRef(int elanTag, int elanLportTag) {
        return CloudServiceChainConstants.SCF_TO_ELAN_L2_FLOWID_PREFIX + elanTag
                + NwConstants.FLOWID_SEPARATOR + elanLportTag;
    }

    /**
     * Stores the relation between elanInstanceName and ElanLport and scfTag.
     *
     * @param broker
     * @param elanInstanceName
     * @param lportTag
     * @param scfTag
     * @param addOrRemove
     */
    public static void updateElanToLportTagMap(final DataBroker broker, final String elanInstanceName,
                                                final int lportTag, final int scfTag, final int addOrRemove) {
        ElanToPseudoPortDataKey key = new ElanToPseudoPortDataKey(elanInstanceName);
        InstanceIdentifier<ElanToPseudoPortData> path = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName))
                .augmentation(ElanServiceChainState.class)
                .child(ElanToPseudoPortData.class, new ElanToPseudoPortDataKey(elanInstanceName)).build();

        if ( addOrRemove == NwConstants.ADD_FLOW ) {
            ElanToPseudoPortData newValue =
                    new ElanToPseudoPortDataBuilder().setKey(key).setElanInstanceName(elanInstanceName)
                                                     .setElanLportTag((long) lportTag).setScfTag(scfTag).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, path, newValue);
        } else {
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, path);
        }
    }

    /**
     * Read from ElanToLportTagMap the PsuedoLogicalPort related with a given elan.
     *
     * @param broker
     * @param elanInstanceName
     * @return Optional containing ElanToPseudoPortData
     */
    public static Optional<ElanToPseudoPortData> getElanToLportTagList(final DataBroker broker, final String elanInstanceName) {
        ElanToPseudoPortDataKey key = new ElanToPseudoPortDataKey(elanInstanceName);
        InstanceIdentifier<ElanToPseudoPortData> path = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName))
                .augmentation(ElanServiceChainState.class)
                .child(ElanToPseudoPortData.class, key).build();

        Optional<ElanToPseudoPortData> elanLPortListOpc =
                MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);
        if (!elanLPortListOpc.isPresent()) {
            logger.warn("Could not find and LPort for elan {}", elanInstanceName);
            return Optional.absent();
        }

        return elanLPortListOpc;

    }
}


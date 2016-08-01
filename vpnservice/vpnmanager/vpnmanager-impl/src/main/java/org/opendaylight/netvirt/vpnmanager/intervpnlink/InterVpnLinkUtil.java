/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.*;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLinkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class contains methods to be used as utilities related with inter-vpn-link.
 *
 */
public class InterVpnLinkUtil {
    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkUtil.class);

    /**
     * Updates VpnToDpn map by adding a fake VpnInterface related to an
     * InterVpnLink in the corresponding DPNs
     *
     * @param broker dataBroker service reference
     * @param dpnList List of DPNs where the fake InterVpnLink interface must
     *     be added
     * @param vpnUuid UUID of the VPN to which the fake interfaces belong
     */
    public static void updateVpnToDpnMap(DataBroker broker, List<BigInteger> dpnList, Uuid vpnUuid) {
        String rd = VpnUtil.getVpnRd(broker, vpnUuid.getValue());
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstOpData = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if ( vpnInstOpData.isPresent() ) {
            for (BigInteger dpnId : dpnList) {
                String linkIfaceName = String.format("InterVpnLink.%s.%s", vpnUuid.getValue(), dpnId.toString());
                VpnUtil.mergeDpnInVpnToDpnMap(broker, vpnInstOpData.get(), dpnId,
                                              Arrays.asList(linkIfaceName));
            }
        }
    }


    /**
     * Retrieves the InterVpnLink object searching by its name
     *
     * @param broker dataBroker service reference
     * @param vpnLinkName Name of the InterVpnLink
     * @return the InterVpnLink or Optional.absent() if there is no
     *     InterVpnLink with the specified name
     */
    public static Optional<InterVpnLink> getInterVpnLinkByName(DataBroker broker, String vpnLinkName) {
        InstanceIdentifier<InterVpnLink> interVpnLinksIid =
                InstanceIdentifier.builder(InterVpnLinks.class)
                                  .child(InterVpnLink.class, new InterVpnLinkKey(vpnLinkName)).build();
        return  VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, interVpnLinksIid);
    }

    /**
     * Updates inter-VPN link state
     *
     * @param broker dataBroker service reference
     * @param vpnLinkName The name of the InterVpnLink
     * @param state Sets the state of the InterVpnLink to Active or Error
     * @param newFirstEndpointState Updates the lportTag and/or DPNs of the 1st
     *     endpoint of the InterVpnLink
     * @param newSecondEndpointState Updates the lportTag and/or DPNs of the
     *     2nd endpoint of the InterVpnLink
     */
    public static void updateInterVpnLinkState(DataBroker broker, String vpnLinkName, InterVpnLinkState.State state,
                                               FirstEndpointState newFirstEndpointState,
                                               SecondEndpointState newSecondEndpointState) {
        InterVpnLinkState oldVpnLinkState = VpnUtil.getInterVpnLinkState(broker, vpnLinkName);
        InterVpnLinkState newVpnLinkState =
                new InterVpnLinkStateBuilder(oldVpnLinkState).setState(state)
                                                             .setFirstEndpointState(newFirstEndpointState)
                                                             .setSecondEndpointState(newSecondEndpointState).build();
        VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, VpnUtil.getInterVpnLinkStateIid(vpnLinkName),
                           newVpnLinkState);
    }

    /**
     * Installs a Flow in LPortDispatcher table that matches on SI=2 and
     * the lportTag of one InterVpnLink's endpoint and sets the vrfTag of the
     * other endpoint and sends to FIB table
     *
     * @param broker dataBroker service reference
     * @param mdsalManager MDSAL API accessor
     * @param interVpnLink Object that holds the needed information about both
     *     endpoints of the InterVpnLink.
     * @param dpnList The list of DPNs where this flow must be installed
     * @param vpnUuidOtherEndpoint UUID of the other endpoint of the
     *     InterVpnLink
     * @param lPortTagOfOtherEndpoint Dataplane identifier of the other
     *     endpoint of the InterVpnLink
     */
    public static void installLPortDispatcherTableFlow(DataBroker broker, IMdsalApiManager mdsalManager,
                                                       InterVpnLink interVpnLink, List<BigInteger> dpnList,
                                                       Uuid vpnUuidOtherEndpoint, Integer lPortTagOfOtherEndpoint) {
        long vpnId = VpnUtil.getVpnId(broker, vpnUuidOtherEndpoint.getValue());
        for ( BigInteger dpnId : dpnList ) {
            // insert into LPortDispatcher table
            Flow lPortDispatcherFlow = buildLPortDispatcherFlow(interVpnLink.getName(), vpnId, lPortTagOfOtherEndpoint);
            mdsalManager.installFlow(dpnId, lPortDispatcherFlow);
        }
    }

   /**
    * Builds a Flow to be installed into LPortDispatcher table, that matches on
    * SI=2 + vpnLinkEndpointPseudoPortTag and sends to FIB
    *
    * @param interVpnLinkName The name of the InterVpnLink
    * @param vpnId Dataplane identifier of the VPN, the Vrf Tag.
    * @param lportTag DataPlane identifier of the LogicalPort.
    * @return the Flow ready to be installed
    */
   public static Flow buildLPortDispatcherFlow(String interVpnLinkName, long vpnId, Integer lportTag) {
       LOG.info("Inter-vpn-link : buildLPortDispatcherFlow. vpnId {}   lportTag {} ", vpnId, lportTag);
       List<MatchInfo> matches = Arrays.asList(new MatchInfo(MatchFieldType.metadata,
                                                              new BigInteger[] {
                                                                  MetaDataUtil.getMetaDataForLPortDispatcher(lportTag,
                                                                                NwConstants.L3VPN_SERVICE_INDEX),
                                                                  MetaDataUtil.getMetaDataMaskForLPortDispatcher() }));
       String flowRef = getLportDispatcherFlowRef(interVpnLinkName, lportTag);
       Flow lPortDispatcherFlow = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                                                         VpnConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY, flowRef,
                                                         0, 0, VpnUtil.getCookieL3((int) vpnId), matches,
                                                         buildLportDispatcherTableInstructions(vpnId));
       return lPortDispatcherFlow;
   }

   /**
   * Builds a flowRef to be assigned to the flow to be installed into
   * LPortDispatcher table
   *
   * @param interVpnLinkName The name of the InterVpnLink
   * @param lportTag Dataplane identifier of the LogicalPort
   * @return the flow reference string
   */
   public static String getLportDispatcherFlowRef(String interVpnLinkName, Integer lportTag) {
       String flowRef = new StringBuffer().append(VpnConstants.FLOWID_PREFIX).append("INTERVPNLINK")
                     .append(NwConstants.FLOWID_SEPARATOR).append(interVpnLinkName)
                     .append(NwConstants.FLOWID_SEPARATOR).append(lportTag)
                     .append(NwConstants.FLOWID_SEPARATOR).append(NwConstants.L3VPN_SERVICE_INDEX)
                     .append(NwConstants.FLOWID_SEPARATOR).append(VpnConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY)
                     .toString();
       return flowRef;
   }


   public static List<Instruction> buildLportDispatcherTableInstructions (long vpnId) {
       int instructionKey = 0;
       List<Instruction> instructions = new ArrayList<Instruction>();
       instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnId),
                                                                    MetaDataUtil.METADATA_MASK_VRFID,
                                                                    ++instructionKey));
       instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_FIB_TABLE, ++instructionKey));

       return instructions;
   }

   /**
    * Retrieves the States of all InterVpnLinks
    *
    * @param broker dataBroker service reference
    * @return the list of objects that holds the InterVpnLink state information
    */
   public static List<InterVpnLinkState> getAllInterVpnLinkState(DataBroker broker) {
       InstanceIdentifier<InterVpnLinkStates> interVpnLinkStateIid = InstanceIdentifier.builder(InterVpnLinkStates.class).build();

       Optional<InterVpnLinkStates> interVpnLinkStateOpData = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
               interVpnLinkStateIid);

       return (interVpnLinkStateOpData.isPresent()) ? interVpnLinkStateOpData.get().getInterVpnLinkState()
               : new ArrayList<InterVpnLinkState>();
   }


}

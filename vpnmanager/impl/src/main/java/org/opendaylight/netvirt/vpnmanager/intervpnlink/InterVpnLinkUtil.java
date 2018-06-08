/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnFootprintService;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLinkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains methods to be used as utilities related with inter-vpn-link.
 */
@Singleton
public final class InterVpnLinkUtil {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkUtil.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final VpnUtil vpnUtil;
    private final VpnFootprintService vpnFootprintService;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;

    @Inject
    public InterVpnLinkUtil(final VpnUtil vpnUtil, final VpnFootprintService vpnFootprintService,
                            final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                            final IBgpManager bgpManager, final IFibManager fibManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.vpnUtil = vpnUtil;
        this.vpnFootprintService = vpnFootprintService;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
    }

    /**
     * Retrieves the Instance Identifier that points to an InterVpnLink object
     * in MD-SAL.
     *
     * @param interVpnLinkName The name of the InterVpnLink
     * @return The requested InstanceIdentifier
     */
    public static InstanceIdentifier<InterVpnLink> getInterVpnLinkPath(String interVpnLinkName) {
        return InstanceIdentifier.builder(InterVpnLinks.class)
            .child(InterVpnLink.class, new InterVpnLinkKey(interVpnLinkName))
            .build();
    }

    /**
     * Retrieves the Instance Identifier that points to an InterVpnLinkState object
     * in MD-SAL.
     *
     * @param vpnLinkName The name of the InterVpnLink
     * @return The requested InstanceIdentifier
     */
    public static InstanceIdentifier<InterVpnLinkState> getInterVpnLinkStateIid(String vpnLinkName) {
        return InstanceIdentifier.builder(InterVpnLinkStates.class)
            .child(InterVpnLinkState.class, new InterVpnLinkStateKey(vpnLinkName))
            .build();
    }

    public static String buildInterVpnLinkIfaceName(String vpnName, BigInteger dpnId) {
        return String.format("InterVpnLink.%s.%s", vpnName, dpnId.toString());
    }

    /**
     * Updates VpnToDpn map by adding a fake VpnInterface related to an
     * InterVpnLink in the corresponding DPNs. If the fake iface is the
     * first one on the any of the specified DPNs, the installation of
     * Fib flows on that DPN will be triggered.
     *
     * @param vpnName Name of the VPN to which the fake interfaces belong
     * @param dpnList List of DPNs where the fake InterVpnLink interface must be added
     */
    void updateVpnFootprint(String vpnName, String primaryRd, List<BigInteger> dpnList) {
        LOG.debug("updateVpnFootprint (add):  vpn={}  dpnList={}", vpnName, dpnList);
        // Note: when a set of DPNs is calculated for Vpn1, these DPNs are added to the VpnToDpn map of Vpn2. Why?
        // because we do the handover from Vpn1 to Vpn2 in those DPNs, so in those DPNs we must know how to reach
        // to Vpn2 targets. If new Vpn2 targets are added later, the Fib will be maintained in these DPNs even if
        // Vpn2 is not physically present there.
        for (BigInteger dpnId : dpnList) {
            String ifaceName = buildInterVpnLinkIfaceName(vpnName, dpnId);
            vpnFootprintService.updateVpnToDpnMapping(dpnId, vpnName, primaryRd, ifaceName,
                    null/*ipAddressSourceValuePair*/, true /* addition */);
        }
    }

    /**
     * Updates VpnToDpn map by removing the fake VpnInterface related to an
     * InterVpnLink in the corresponding DPNs.
     *
     * @param vpnName Name of the VPN to which the fake interfaces belong
     * @param dpnId DPN where the fake InterVpnLink interface must be removed from
     */
    void removeIVpnLinkIfaceFromVpnFootprint(String vpnName, String rd, BigInteger dpnId) {
        String ifaceName = buildInterVpnLinkIfaceName(vpnName, dpnId);
        LOG.debug("updateVpnFootprint (remove):  vpn={}  dpn={}  ifaceName={}", vpnName, dpnId, ifaceName);
        vpnFootprintService.updateVpnToDpnMapping(dpnId, vpnName, rd, ifaceName,
                null/*ipAddressSourceValuePair*/, false /* removal */);
    }


    public static FirstEndpointState buildFirstEndpointState(FirstEndpointState original,
                                                             Optional<List<BigInteger>> new1stEndpointDpns,
                                                             Optional<Long> new1stEndpointLportTag) {
        FirstEndpointStateBuilder builder = new FirstEndpointStateBuilder(original);
        if (new1stEndpointDpns.isPresent()) {
            builder.setDpId(new1stEndpointDpns.get());
        }
        if (new1stEndpointLportTag.isPresent()) {
            builder.setLportTag(new1stEndpointLportTag.get());
        }
        return builder.build();
    }

    public static SecondEndpointState buildSecondEndpointState(SecondEndpointState original,
                                                               Optional<List<BigInteger>> new2ndEndpointDpns,
                                                               Optional<Long> new2ndEndpointLportTag) {
        SecondEndpointStateBuilder builder = new SecondEndpointStateBuilder(original);
        if (new2ndEndpointDpns.isPresent()) {
            builder.setDpId(new2ndEndpointDpns.get());
        }
        if (new2ndEndpointLportTag.isPresent()) {
            builder.setLportTag(new2ndEndpointLportTag.get());
        }
        return builder.build();
    }

    /**
     * Creates an InterVpnLinkState out of an existing one and modifying only the desired attributes.
     *
     * @param original InterVpnLinkState to start from.
     * @param new1stEndpointState Sets this FirstEndpointState if present
     * @param new2ndEndpointState  Sets this SecondEndpointState if present
     * @param errDescription  Sets this ErrorDescription if present
     * @return the newly build InterVpnLinkState
     */
    public static InterVpnLinkState buildIvlStateFromOriginal(InterVpnLinkState original,
                                                             Optional<FirstEndpointState> new1stEndpointState,
                                                             Optional<SecondEndpointState> new2ndEndpointState,
                                                             Optional<String> errDescription) {
        InterVpnLinkStateBuilder ivlStateBuilder = new InterVpnLinkStateBuilder(original);
        if (new1stEndpointState.isPresent()) {
            ivlStateBuilder.setFirstEndpointState(new1stEndpointState.get());
        }
        if (new2ndEndpointState.isPresent()) {
            ivlStateBuilder.setSecondEndpointState(new2ndEndpointState.get());
        }
        if (errDescription.isPresent()) {
            ivlStateBuilder.setErrorDescription(errDescription.get());
        }
        return ivlStateBuilder.build();
    }

    /**
     * Updates inter-VPN link state.
     *
     * @param vpnLinkName The name of the InterVpnLink
     * @param state Sets the state of the InterVpnLink to Active or Error
     * @param newFirstEndpointState Updates the lportTag and/or DPNs of the 1st endpoint of the InterVpnLink
     * @param newSecondEndpointState Updates the lportTag and/or DPNs of the 2nd endpoint of the InterVpnLink
     * @param interVpnLinkCache the InterVpnLinkCache
     */
    void updateInterVpnLinkState(String vpnLinkName, InterVpnLinkState.State state,
            FirstEndpointState newFirstEndpointState, SecondEndpointState newSecondEndpointState,
            InterVpnLinkCache interVpnLinkCache) {
        Optional<InterVpnLinkState> optOldVpnLinkState = getInterVpnLinkState(vpnLinkName);
        if (optOldVpnLinkState.isPresent()) {
            InterVpnLinkState newVpnLinkState =
                new InterVpnLinkStateBuilder(optOldVpnLinkState.get()).setState(state)
                            .setFirstEndpointState(newFirstEndpointState)
                            .setSecondEndpointState(newSecondEndpointState)
                            .build();
            vpnUtil.syncUpdate(LogicalDatastoreType.CONFIGURATION,
                InterVpnLinkUtil.getInterVpnLinkStateIid(vpnLinkName), newVpnLinkState);
            interVpnLinkCache.addInterVpnLinkStateToCaches(newVpnLinkState);
        } else {
            InterVpnLinkState newIVpnLinkState =
                new InterVpnLinkStateBuilder().withKey(new InterVpnLinkStateKey(vpnLinkName))
                    .setInterVpnLinkName(vpnLinkName)
                    .setFirstEndpointState(newFirstEndpointState)
                    .setSecondEndpointState(newSecondEndpointState)
                    .setState(InterVpnLinkState.State.Active)
                    .build();
            vpnUtil.syncWrite(LogicalDatastoreType.CONFIGURATION,
                InterVpnLinkUtil.getInterVpnLinkStateIid(vpnLinkName), newIVpnLinkState);
            interVpnLinkCache.addInterVpnLinkStateToCaches(newIVpnLinkState);
        }
    }

    /**
     * Installs a Flow in LPortDispatcher table that matches on SI=2 and
     * the lportTag of one InterVpnLink's endpoint and sets the vrfTag of the
     * other endpoint and sends to FIB table.
     *
     * @param interVpnLinkName Name of the InterVpnLink.
     * @param dpnList The list of DPNs where this flow must be installed
     * @param vpnUuidOtherEndpoint UUID of the other endpoint of the InterVpnLink
     * @param lportTagOfOtherEndpoint Dataplane identifier of the other endpoint of the InterVpnLink
     * @return the list of Futures for each and every flow that has been installed
     */
    List<ListenableFuture<Void>> installLPortDispatcherTableFlow(String interVpnLinkName, List<BigInteger> dpnList,
                                                                 String vpnUuidOtherEndpoint,
                                                                 Long lportTagOfOtherEndpoint) {
        List<ListenableFuture<Void>> result = new ArrayList<>();
        long vpnId = vpnUtil.getVpnId(vpnUuidOtherEndpoint);
        for (BigInteger dpnId : dpnList) {
            // insert into LPortDispatcher table
            Flow lportDispatcherFlow = buildLPortDispatcherFlow(interVpnLinkName, vpnId,
                                                                lportTagOfOtherEndpoint.intValue());
            result.add(mdsalManager.installFlow(dpnId, lportDispatcherFlow));
        }

        return result;
    }

    /**
     * Builds a Flow to be installed into LPortDispatcher table, that matches on
     * SI=2 + vpnLinkEndpointPseudoPortTag and sends to FIB.
     *
     * @param interVpnLinkName The name of the InterVpnLink
     * @param vpnId Dataplane identifier of the VPN, the Vrf Tag.
     * @param lportTag DataPlane identifier of the LogicalPort.
     * @return the Flow ready to be installed
     */
    public static Flow buildLPortDispatcherFlow(String interVpnLinkName, long vpnId, int lportTag) {
        LOG.info("Inter-vpn-link : buildLPortDispatcherFlow. vpnId {}   lportTag {} ", vpnId, lportTag);
        List<MatchInfo> matches = Collections.singletonList(new MatchMetadata(
                        MetaDataUtil.getMetaDataForLPortDispatcher(lportTag,
                                ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME, NwConstants.L3VPN_SERVICE_INDEX)),
                        MetaDataUtil.getMetaDataMaskForLPortDispatcher()));
        String flowRef = getLportDispatcherFlowRef(interVpnLinkName, lportTag);

        return MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                                      VpnConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY, flowRef,
                                      0, 0, VpnUtil.getCookieL3((int) vpnId), matches,
                                      buildLportDispatcherTableInstructions(vpnId));
    }

    /**
     * Builds a flowRef to be assigned to the flow to be installed into
     * LPortDispatcher table.
     *
     * @param interVpnLinkName The name of the InterVpnLink
     * @param lportTag Dataplane identifier of the LogicalPort
     * @return the flow reference string
     */
    public static String getLportDispatcherFlowRef(String interVpnLinkName, Integer lportTag) {
        return VpnConstants.FLOWID_PREFIX + "INTERVPNLINK" + NwConstants.FLOWID_SEPARATOR + interVpnLinkName
             + NwConstants.FLOWID_SEPARATOR + lportTag
             + NwConstants.FLOWID_SEPARATOR + ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                                                    NwConstants.L3VPN_SERVICE_INDEX)
             + NwConstants.FLOWID_SEPARATOR + VpnConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY;
    }


    public static List<Instruction> buildLportDispatcherTableInstructions(long vpnId) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnId),
            MetaDataUtil.METADATA_MASK_VRFID,
            ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_FIB_TABLE, ++instructionKey));

        return instructions;
    }

    /**
     * Retrieves the State of an InterVpnLink.
     *
     * @param interVpnLinkName The name of the InterVpnLink
     * @return the object that contains the State of the specified InterVpnLink or Optional.absent() if it doesnt exist
     */
    public Optional<InterVpnLinkState> getInterVpnLinkState(String interVpnLinkName) {
        Optional<InterVpnLinkState> interVpnLinkStateOptional = Optional.absent();
        try {
            interVpnLinkStateOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, getInterVpnLinkStateIid(interVpnLinkName));
        } catch (ReadFailedException e) {
            LOG.error("getInterVpnLinkState: Failed to read intervpn link state for {}", interVpnLinkName);
        }
        return interVpnLinkStateOptional;
    }

    public void handleStaticRoute(InterVpnLinkDataComposite interVpnLink, String vpnName,
        String destination, String nexthop, int label) throws Exception {

        LOG.debug("handleStaticRoute [vpnLink={} srcVpn={} destination={} nextHop={} label={}]",
            interVpnLink.getInterVpnLinkName(), vpnName, destination, nexthop, label);

        String vpnRd = vpnUtil.getVpnRd(vpnName);
        if (vpnRd == null) {
            LOG.warn("Could not find Route-Distinguisher for VpnName {}", vpnName);
            return;
        }
        LOG.debug("Writing FibEntry to DS:  vpnRd={}, prefix={}, label={}, nexthop={} (interVpnLink)",
            vpnRd, destination, label, nexthop);
        fibManager.addOrUpdateFibEntry(vpnRd, null /*macAddress*/, destination,
                Collections.singletonList(nexthop), VrfEntry.EncapType.Mplsgre, label,
                0 /*l3vni*/, null /*gatewayMacAddress*/, null /*parentVpnRd*/, RouteOrigin.STATIC, null /*writeTxn*/);

        // Now advertise to BGP. The nexthop that must be advertised to BGP are the IPs of the DPN where the
        // VPN's endpoint have been instantiated
        // List<String> nexthopList = new ArrayList<>(); // The nexthops to be advertised to BGP
        List<BigInteger> endpointDpns = interVpnLink.getEndpointDpnsByVpnName(vpnName);
        List<String> nexthopList =
            endpointDpns.stream().map(dpnId -> InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId))
                        .collect(Collectors.toList());
        LOG.debug("advertising IVpnLink route to BGP:  vpnRd={}, prefix={}, label={}, nexthops={}",
            vpnRd, destination, label, nexthopList);
        bgpManager.advertisePrefix(vpnRd, null /*macAddress*/, destination, nexthopList,
                VrfEntry.EncapType.Mplsgre, label, 0 /*l3vni*/, 0 /*l2vni*/,
                null /*gatewayMacAddress*/);
    }
}

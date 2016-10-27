/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLinkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains methods to be used as utilities related with inter-vpn-link.
 */
public class InterVpnLinkUtil {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkUtil.class);

    /**
     * Retrieves the Instance Identifier that points to an InterVpnLink object
     * in MDSAL.
     *
     * @param ivpnLinkName The name of the InterVpnLink
     * @return The requested InstanceIdentifier
     */
    public static InstanceIdentifier<InterVpnLink> getInterVpnLinkPath(String ivpnLinkName) {
        return InstanceIdentifier.builder(InterVpnLinks.class)
                                 .child(InterVpnLink.class, new InterVpnLinkKey(ivpnLinkName))
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
     * @param vpnFootprintService VpnFootprintService service reference
     * @param vpnName Name of the VPN to which the fake interfaces belong
     * @param dpnList List of DPNs where the fake InterVpnLink interface must be added
     */
    public static void updateVpnFootprint(VpnFootprintService vpnFootprintService, String vpnName,
        List<BigInteger> dpnList) {
        LOG.debug("updateVpnFootprint (add):  vpn={}  dpnList={}", vpnName, dpnList);
        // Note: when a set of DPNs is calculated for Vpn1, these DPNs are added to the VpnToDpn map of Vpn2. Why?
        // because we do the handover from Vpn1 to Vpn2 in those DPNs, so in those DPNs we must know how to reach
        // to Vpn2 targets. If new Vpn2 targets are added later, the Fib will be maintained in these DPNs even if
        // Vpn2 is not physically present there.
        for (BigInteger dpnId : dpnList) {
            String ifaceName = buildInterVpnLinkIfaceName(vpnName, dpnId);
            vpnFootprintService.updateVpnToDpnMapping(dpnId, vpnName, ifaceName, true /* addition */);
        }
    }

    /**
     * Updates VpnToDpn map by removing the fake VpnInterface related to an
     * InterVpnLink in the corresponding DPNs.
     *
     * @param vpnFootprintService VpnFootprintService service reference
     * @param vpnName Name of the VPN to which the fake interfaces belong
     * @param dpnId DPN where the fake InterVpnLink interface must be removed from
     */
    public static void removeIVpnLinkIfaceFromVpnFootprint(VpnFootprintService vpnFootprintService,
        String vpnName, BigInteger dpnId) {
        String ifaceName = buildInterVpnLinkIfaceName(vpnName, dpnId);
        LOG.debug("updateVpnFootprint (remove):  vpn={}  dpn={}  ifaceName={}", vpnName, dpnId, ifaceName);
        vpnFootprintService.updateVpnToDpnMapping(dpnId, vpnName, ifaceName, false /* removal */);
    }

    /**
     * Updates inter-VPN link state.
     *
     * @param broker dataBroker service reference
     * @param vpnLinkName The name of the InterVpnLink
     * @param state Sets the state of the InterVpnLink to Active or Error
     * @param newFirstEndpointState Updates the lportTag and/or DPNs of the 1st endpoint of the InterVpnLink
     * @param newSecondEndpointState Updates the lportTag and/or DPNs of the 2nd endpoint of the InterVpnLink
     */
    public static void updateInterVpnLinkState(DataBroker broker, String vpnLinkName, InterVpnLinkState.State state,
        FirstEndpointState newFirstEndpointState,
        SecondEndpointState newSecondEndpointState) {
        Optional<InterVpnLinkState> optOldVpnLinkState = getInterVpnLinkState(broker, vpnLinkName);
        if (optOldVpnLinkState.isPresent()) {
            InterVpnLinkState newVpnLinkState =
                new InterVpnLinkStateBuilder(optOldVpnLinkState.get()).setState(state)
                    .setFirstEndpointState(newFirstEndpointState)
                    .setSecondEndpointState(newSecondEndpointState)
                    .build();
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION,
                InterVpnLinkUtil.getInterVpnLinkStateIid(vpnLinkName), newVpnLinkState);
            InterVpnLinkCache.addInterVpnLinkStateToCaches(newVpnLinkState);
        } else {
            InterVpnLinkState newIVpnLinkState =
                new InterVpnLinkStateBuilder().setKey(new InterVpnLinkStateKey(vpnLinkName))
                    .setInterVpnLinkName(vpnLinkName)
                    .setFirstEndpointState(newFirstEndpointState)
                    .setSecondEndpointState(newSecondEndpointState)
                    .setState(InterVpnLinkState.State.Active)
                    .build();
            VpnUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                InterVpnLinkUtil.getInterVpnLinkStateIid(vpnLinkName), newIVpnLinkState);
            InterVpnLinkCache.addInterVpnLinkStateToCaches(newIVpnLinkState);
        }
    }

    /**
     * Installs a Flow in LPortDispatcher table that matches on SI=2 and
     * the lportTag of one InterVpnLink's endpoint and sets the vrfTag of the
     * other endpoint and sends to FIB table.
     *
     * @param broker dataBroker service reference
     * @param mdsalManager MDSAL API accessor
     * @param vpnUuidOtherEndpoint UUID of the other endpoint of the InterVpnLink
     * @param lportTagOfOtherEndpoint Dataplane identifier of the other endpoint of the InterVpnLink
     * @return the list of Futures for each and every flow that has been installed
     */
    public static List<ListenableFuture<Void>> installLPortDispatcherTableFlow(DataBroker broker,
                                                                               IMdsalApiManager mdsalManager,
                                                                               String interVpnLinkName,
                                                                               List<BigInteger> dpnList,
                                                                               String vpnUuidOtherEndpoint,
                                                                               Long lportTagOfOtherEndpoint) {
        List<ListenableFuture<Void>> result = new ArrayList<>();
        long vpnId = VpnUtil.getVpnId(broker, vpnUuidOtherEndpoint);
        for ( BigInteger dpnId : dpnList ) {
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
        return new StringBuffer()
            .append(VpnConstants.FLOWID_PREFIX).append("INTERVPNLINK")
            .append(NwConstants.FLOWID_SEPARATOR).append(interVpnLinkName)
            .append(NwConstants.FLOWID_SEPARATOR).append(lportTag)
            .append(NwConstants.FLOWID_SEPARATOR).append(ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                NwConstants.L3VPN_SERVICE_INDEX))
            .append(NwConstants.FLOWID_SEPARATOR)
            .append(VpnConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY)
            .toString();
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
     * Retrieves the States of all InterVpnLinks.
     *
     * @param broker dataBroker service reference
     * @return the list of objects that holds the InterVpnLink state information
     */
    public static List<InterVpnLinkState> getAllInterVpnLinkState(DataBroker broker) {
        InstanceIdentifier<InterVpnLinkStates> interVpnLinkStateIid =
            InstanceIdentifier.builder(InterVpnLinkStates.class).build();

        Optional<InterVpnLinkStates> interVpnLinkStateOpData =
            MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, interVpnLinkStateIid);

        return (interVpnLinkStateOpData.isPresent()) ? interVpnLinkStateOpData.get().getInterVpnLinkState()
            : new ArrayList<>();
    }

    /**
     * Retrieves the State of an InterVpnLink.
     *
     * @param broker dataBroker service reference
     * @param interVpnLinkName The name of the InterVpnLink
     * @return the object that contains the State of the specified InterVpnLink or Optional.absent() if it doesnt exist
     */
    public static Optional<InterVpnLinkState> getInterVpnLinkState(DataBroker broker, String interVpnLinkName) {
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, getInterVpnLinkStateIid(interVpnLinkName));
    }

    /**
     * Checks if the specified InterVpnLink is currently Active.
     *
     * @param broker dataBroker service reference
     * @param interVpnLinkName The name of the InterVpnLink
     * @return true if the InterVpnLink is Active
     */
    public static boolean isInterVpnLinkActive(DataBroker broker, String interVpnLinkName) {
        Optional<InterVpnLinkState> optIVpnLinkState = getInterVpnLinkState(broker, interVpnLinkName);
        if (!optIVpnLinkState.isPresent()) {
            return false;
        }
        InterVpnLinkState interVpnLinkState = optIVpnLinkState.get();
        return interVpnLinkState.getState() == InterVpnLinkState.State.Active;
    }

    /**
     * Retrieves an InterVpnLink by searching by one of its endpoint's IP.
     *
     * @param broker dataBroker service reference
     * @param endpointIp IP to serch for.
     * @return the InterVpnLink or null if no InterVpnLink can be found
     */
    public static Optional<InterVpnLink> getInterVpnLinkByEndpointIp(DataBroker broker, String endpointIp) {
        List<InterVpnLink> allInterVpnLinks = InterVpnLinkUtil.getAllInterVpnLinks(broker);
        for (InterVpnLink interVpnLink : allInterVpnLinks) {
            if (interVpnLink.getFirstEndpoint().getIpAddress().getValue().equals(endpointIp)
                || interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(endpointIp)) {
                return Optional.of(interVpnLink);
            }
        }
        return Optional.absent();
    }

    /**
     * Retrieves the InterVpnLink that has one of its 2 endpoints installed in
     * the specified DpnId.
     *
     * @param broker dataBroker service reference
     * @param dpnId Id of the DPN
     * @return The InterVpnLink object if found, Optional.absent() otherwise
     */
    public static Optional<InterVpnLink> getInterVpnLinkByDpnId(DataBroker broker, BigInteger dpnId) {
        List<InterVpnLink> allInterVpnLinks = InterVpnLinkUtil.getAllInterVpnLinks(broker);
        for (InterVpnLink interVpnLink : allInterVpnLinks) {
            Optional<InterVpnLinkState> optInterVpnLinkState = getInterVpnLinkState(broker, interVpnLink.getName());
            if (optInterVpnLinkState.isPresent()
                && (optInterVpnLinkState.get().getFirstEndpointState().getDpId().contains(dpnId)
                        || optInterVpnLinkState.get().getSecondEndpointState().getDpId().contains(dpnId))) {
                return Optional.fromNullable(interVpnLink);
            }
        }
        return Optional.absent();
    }

    /**
     * Retrieves all configured InterVpnLinks.
     *
     * @param broker dataBroker service reference
     * @return the list of InterVpnLinks
     */
    public static List<InterVpnLink> getAllInterVpnLinks(DataBroker broker) {
        InstanceIdentifier<InterVpnLinks> interVpnLinksIid = InstanceIdentifier.builder(InterVpnLinks.class).build();

        Optional<InterVpnLinks> interVpnLinksOpData =
            MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, interVpnLinksIid);

        return interVpnLinksOpData.isPresent() ? interVpnLinksOpData.get().getInterVpnLink()
            : new ArrayList<>();
    }

    public static void handleStaticRoute(InterVpnLinkDataComposite ivpnLink, String vpnName,
                                         String destination, String nexthop, int label,
                                         DataBroker dataBroker, IFibManager fibManager, IBgpManager bgpManager) {

        LOG.debug("handleStaticRoute [vpnLink={} srcVpn={} destination={} nextHop={} label={}]",
                  ivpnLink.getInterVpnLinkName(), vpnName, destination, nexthop, label);

        String vpnRd = VpnUtil.getVpnRd(dataBroker, vpnName);
        if (vpnRd == null) {
            LOG.warn("Could not find Route-Distinguisher for VpnName {}", vpnName);
            return;
        }
        LOG.debug("Writing FibEntry to DS:  vpnRd={}, prefix={}, label={}, nexthop={} (interVpnLink)",
            vpnRd, destination, label, nexthop);
        fibManager.addOrUpdateFibEntry(dataBroker, vpnRd, destination, Collections.singletonList(nexthop), label,
            RouteOrigin.STATIC, null);

        // Now advertise to BGP. The nexthop that must be advertised to BGP are the IPs of the DPN where the
        // VPN's endpoint have been instantiated
        // List<String> nexthopList = new ArrayList<>(); // The nexthops to be advertised to BGP
        List<BigInteger> endpointDpns = ivpnLink.getEndpointDpnsByVpnName(vpnName);
        List<String> nexthopList =
            endpointDpns.stream().map(dpnId -> InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId))
                .collect(Collectors.toList());
        LOG.debug("advertising IVpnLink route to BGP:  vpnRd={}, prefix={}, label={}, nexthops={}",
                  vpnRd, destination, label, nexthopList);
        bgpManager.advertisePrefix(vpnRd, destination, nexthopList, label);
    }
}

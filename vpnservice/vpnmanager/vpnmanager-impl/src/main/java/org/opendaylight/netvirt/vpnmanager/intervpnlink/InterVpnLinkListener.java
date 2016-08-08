/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkCreationError;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkCreationErrorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.creation.error.InterVpnLinkCreationErrorMessage;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.creation.error.InterVpnLinkCreationErrorMessageBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLinkKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterVpnLinkListener extends AbstractDataChangeListener<InterVpnLink> implements  AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;
    private final IBgpManager bgpManager;
    private final NotificationPublishService notificationsService;
    private static final String NBR_OF_DPNS_PROPERTY_NAME = "vpnservice.intervpnlink.number.dpns";
    private static final int INVALID_ID = 0;

    public InterVpnLinkListener(final DataBroker dataBroker, final IdManagerService idManager,
                                final IMdsalApiManager mdsalManager, final IBgpManager bgpManager,
                                final NotificationPublishService notifService) {
        super(InterVpnLink.class);
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.notificationsService = notifService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                getWildCardPath(), this, AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    private InstanceIdentifier<InterVpnLink> getWildCardPath() {
        return InstanceIdentifier.create(InterVpnLinks.class).child(InterVpnLink.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
            listenerRegistration = null;
        }
        LOG.info("{} close", getClass().getSimpleName());
    }

    private String getInterVpnLinkIfaceName(String vpnUuid, BigInteger dpnId ) {
        return String.format("InterVpnLink.%s.%s", vpnUuid, dpnId.toString());
    }

    @Override
    protected void add(InstanceIdentifier<InterVpnLink> identifier, InterVpnLink add) {

        int numberOfDpns = Integer.getInteger(NBR_OF_DPNS_PROPERTY_NAME, 1);
        // Create VpnLink state
        InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid = VpnUtil.getInterVpnLinkStateIid(add.getName());
        InterVpnLinkState vpnLinkState = new InterVpnLinkStateBuilder().setInterVpnLinkName(add.getName()).build();
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnLinkStateIid, vpnLinkState);

        InterVpnLinkKey key = add.getKey();
        Uuid firstEndpointVpnUuid = add.getFirstEndpoint().getVpnUuid();
        Uuid secondEndpointVpnUuid = add.getSecondEndpoint().getVpnUuid();
        // First VPN
        if (!checkVpnAvailability(key, firstEndpointVpnUuid)) {
            String errMsg = String.format("Vpn already associated with a previous inter-vpn-link {}",
                                          firstEndpointVpnUuid);
            LOG.error(errMsg);
            setInError(vpnLinkStateIid, vpnLinkState, errMsg);
            return;
        }

        // Second VPN
        if (!checkVpnAvailability(key, secondEndpointVpnUuid)) {
            String errMsg = String.format("Vpn already associated with a previous inter-vpn-link {}",
                                          secondEndpointVpnUuid);
            LOG.error(errMsg);
            setInError(vpnLinkStateIid, vpnLinkState, errMsg);
            return;
        }

        // TODO: Doing like this we are retrieving operative DPNs from MDSAL when we just need one. Fix it
        List<BigInteger> firstDpnList = VpnUtil.pickRandomDPNs(dataBroker, numberOfDpns, null);
        if (firstDpnList != null && !firstDpnList.isEmpty()) {
            // TODO: Limitation to be solved later
            // List<BigInteger> secondDpnList = VpnUtil.pickRandomDPNs(dataBroker, numberOfDpns, firstDpnList);
            List<BigInteger> secondDpnList = firstDpnList;

            Integer firstVpnLportTag = allocateVpnLinkLportTag(key.getName() + firstEndpointVpnUuid.getValue());
            Integer secondVpnLportTag = allocateVpnLinkLportTag(key.getName() + secondEndpointVpnUuid.getValue());
            FirstEndpointState firstEndPointState =
                new FirstEndpointStateBuilder().setVpnUuid(firstEndpointVpnUuid).setDpId(firstDpnList)
                                               .setLportTag(firstVpnLportTag).build();
            SecondEndpointState secondEndPointState =
                new SecondEndpointStateBuilder().setVpnUuid(secondEndpointVpnUuid).setDpId(secondDpnList)
                                                .setLportTag(secondVpnLportTag).build();

            InterVpnLinkUtil.updateInterVpnLinkState(dataBroker, add.getName(), InterVpnLinkState.State.Active, firstEndPointState,
                                            secondEndPointState);

            // Note that in the DPN of the firstEndpoint we install the lportTag of the secondEndpoint and viceversa
            InterVpnLinkUtil.installLPortDispatcherTableFlow(dataBroker, mdsalManager, add, firstDpnList,
                                                    secondEndpointVpnUuid, secondVpnLportTag);
            InterVpnLinkUtil.installLPortDispatcherTableFlow(dataBroker, mdsalManager, add, secondDpnList,
                                                    firstEndpointVpnUuid, firstVpnLportTag);
            // Update the VPN -> DPNs Map.
            // Note: when a set of DPNs is calculated for Vpn1, these DPNs are added to the VpnToDpn map of Vpn2. Why?
            // because we do the handover from Vpn1 to Vpn2 in those DPNs, so in those DPNs we must know how to reach
            // to Vpn2 targets. If new Vpn2 targets are added later, the Fib will be maintained in these DPNs even if
            // Vpn2 is not physically present there.
            InterVpnLinkUtil.updateVpnToDpnMap(dataBroker, firstDpnList, secondEndpointVpnUuid);
            InterVpnLinkUtil.updateVpnToDpnMap(dataBroker, secondDpnList, firstEndpointVpnUuid);

            // Now, if the corresponding flags are activated, there will be some routes exchange
            leakRoutesIfNeeded(add);
        } else {
            // If there is no connection to DPNs, the InterVpnLink is created and the InterVpnLinkState is also created
            // with the corresponding LPortTags but no DPN is assigned since there is no DPN operative.
            Integer firstVpnLportTag = allocateVpnLinkLportTag(key.getName() + firstEndpointVpnUuid.getValue());
            Integer secondVpnLportTag = allocateVpnLinkLportTag(key.getName() + secondEndpointVpnUuid.getValue());
            FirstEndpointState firstEndPointState =
                new FirstEndpointStateBuilder().setVpnUuid(firstEndpointVpnUuid)
                                               .setLportTag(firstVpnLportTag).build();
            SecondEndpointState secondEndPointState =
                new SecondEndpointStateBuilder().setVpnUuid(secondEndpointVpnUuid)
                                                .setLportTag(secondVpnLportTag).build();
            InterVpnLinkUtil.updateInterVpnLinkState(dataBroker, add.getName(), InterVpnLinkState.State.Error, firstEndPointState,
                                            secondEndPointState);
        }


    }

    private void leakRoutesIfNeeded(InterVpnLink vpnLink) {

        // The type of routes to exchange depend on the leaking flags that have been activated
        List<RouteOrigin> originsToConsider = new ArrayList<>();
        if ( vpnLink.isBgpRoutesLeaking() ) {
            originsToConsider.add(RouteOrigin.BGP);
        }

        /* For now, only BGP leaking. Leave this here for when the other leakings are activated
        if ( vpnLink.isConnectedRoutesLeaking() ) {
            originsToConsider.add(RouteOrigin.CONNECTED);
        }
        if ( vpnLink.isStaticRoutesLeaking() ) {
            originsToConsider.add(RouteOrigin.STATIC);
            NOTE: There are 2 types of static routes depending on the nexthop:
              + static route when nexthop is a VM, the Dc-GW or a DPNIP
              + static route when nexthop is an InterVPN Link
            Only the 1st type should be considered since the 2nd has a special treatment
        } */
        String vpn1Uuid = vpnLink.getFirstEndpoint().getVpnUuid().getValue();
        String vpn2Uuid = vpnLink.getSecondEndpoint().getVpnUuid().getValue();

        if ( ! originsToConsider.isEmpty() ) {
            // 1st Endpoint ==> 2nd endpoint
            leakRoutes(vpnLink, vpn1Uuid, vpn2Uuid, originsToConsider);

            // 2nd Endpoint ==> 1st endpoint
            leakRoutes(vpnLink, vpnLink.getSecondEndpoint().getVpnUuid().getValue(),
                       vpnLink.getFirstEndpoint().getVpnUuid().getValue(),
                       originsToConsider);
        }

        // Static routes in Vpn1 pointing to Vpn2's endpoint
        leakExtraRoutesToVpnEndpoint(vpnLink, vpn1Uuid, vpn2Uuid);

        // Static routes in Vpn2 pointing to Vpn1's endpoint
        leakExtraRoutesToVpnEndpoint(vpnLink, vpn2Uuid, vpn1Uuid);
    }

    private void leakRoutes(InterVpnLink vpnLink, String srcVpnUuid, String dstVpnUuid,
                            List<RouteOrigin> originsToConsider) {
        String srcVpnRd = VpnUtil.getVpnRd(dataBroker, srcVpnUuid);
        String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnUuid);
        List<VrfEntry> srcVpnRemoteVrfEntries = VpnUtil.getVrfEntriesByOrigin(dataBroker, srcVpnRd, originsToConsider);
        for ( VrfEntry vrfEntry : srcVpnRemoteVrfEntries ) {
            long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                             VpnUtil.getNextHopLabelKey(dstVpnRd, vrfEntry.getDestPrefix()));

            VpnUtil.leakRoute(dataBroker, bgpManager, vpnLink, srcVpnUuid, dstVpnUuid,
                              vrfEntry.getDestPrefix(), label);
        }
    }

    /*
     * Checks if there are static routes in Vpn1 whose nexthop is Vpn2's endpoint. Those routes must be leaked to Vpn1.
     *
     * @param vpnLink
     * @param vpn1Uuid
     * @param vpn2Uuid
     */
    private void leakExtraRoutesToVpnEndpoint(InterVpnLink vpnLink, String vpn1Uuid, String vpn2Uuid) {

        String vpn1Rd = VpnUtil.getVpnRd(dataBroker, vpn1Uuid);
        String vpn2Endpoint = vpnLink.getSecondEndpoint().getIpAddress().getValue();
        List<VrfEntry> allVpnVrfEntries = VpnUtil.getAllVrfEntries(dataBroker, vpn1Rd);
        for ( VrfEntry vrfEntry : allVpnVrfEntries ) {
            if ( vrfEntry.getNextHopAddressList() != null
                && vrfEntry.getNextHopAddressList().contains(vpn2Endpoint) ) {
                // Vpn1 has a route pointing to Vpn2's endpoint. Forcing the leaking of the route will update the
                // BGP accordingly
                long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                                  VpnUtil.getNextHopLabelKey(vpn1Rd, vrfEntry.getDestPrefix()));

                VpnUtil.leakRoute(dataBroker, bgpManager, vpnLink, vpn2Uuid, vpn1Uuid, vrfEntry.getDestPrefix(),
                                  label, RouteOrigin.value(vrfEntry.getOrigin()));
            }
        }

    }

    private boolean checkVpnAvailability(InterVpnLinkKey key, Uuid vpnId) {
        Preconditions.checkNotNull(vpnId);

        List<InterVpnLink> interVpnLinks = VpnUtil.getAllInterVpnLinks(dataBroker);
        if ( interVpnLinks != null ) {
            for (InterVpnLink interVpnLink : interVpnLinks) {
                if (!key.equals(interVpnLink.getKey())
                    && (vpnId.equals(interVpnLink.getFirstEndpoint().getVpnUuid())
                        || vpnId.equals(interVpnLink.getSecondEndpoint().getVpnUuid()))) {
                    return false;
                }
            }
        }
        return true;
    }


    @Override
    protected void remove(InstanceIdentifier<InterVpnLink> identifier, InterVpnLink del) {

        // Remove learnt routes
        // Remove entries in the LPortDispatcher table
        // Remove the corresponding entries in InterVpnLinkState

        // For each endpoint, remove all routes that have been learnt by intervpnLink
        String vpn1Uuid = del.getFirstEndpoint().getVpnUuid().getValue();
        String rd1 = VpnUtil.getVpnRdFromVpnInstanceConfig(dataBroker, vpn1Uuid);
        VpnUtil.removeVrfEntriesByOrigin(dataBroker, rd1, RouteOrigin.INTERVPN);
        VpnUtil.removeVrfEntriesByNexthop(dataBroker, rd1, del.getSecondEndpoint().getIpAddress().getValue());

        String vpn2Uuid = del.getSecondEndpoint().getVpnUuid().getValue();
        String rd2 = VpnUtil.getVpnRdFromVpnInstanceConfig(dataBroker, vpn2Uuid);
        VpnUtil.removeVrfEntriesByOrigin(dataBroker, rd2, RouteOrigin.INTERVPN);
        VpnUtil.removeVrfEntriesByNexthop(dataBroker, rd2, del.getFirstEndpoint().getIpAddress().getValue());

        InterVpnLinkState interVpnLinkState = VpnUtil.getInterVpnLinkState(dataBroker, del.getName());
        Integer firstEndpointLportTag = interVpnLinkState.getFirstEndpointState().getLportTag();

        Integer secondEndpointLportTag = interVpnLinkState.getSecondEndpointState().getLportTag();

        // Remmoving the flow entries in LPortDispatcher table in 1st Endpoint DPNs
        for ( BigInteger dpnId : interVpnLinkState.getFirstEndpointState().getDpId() ) {
            String flowRef = InterVpnLinkUtil.getLportDispatcherFlowRef(del.getName(), secondEndpointLportTag);
            FlowKey flowKey = new FlowKey(new FlowId(flowRef));
            Flow flow = new FlowBuilder().setKey(flowKey).setId(new FlowId(flowRef))
                                         .setTableId(NwConstants.LPORT_DISPATCHER_TABLE).setFlowName(flowRef)
                                         .build();
            mdsalManager.removeFlow(dpnId, flow);

            // Also remove the 'fake' iface from the VpnToDpn map
            VpnUtil.removeIfaceFromVpnToDpnMap(dataBroker, rd1, dpnId, getInterVpnLinkIfaceName(vpn1Uuid, dpnId));
        }

        // Removing the flow entries in 2nd Endpoint DPNs
        for ( BigInteger dpnId : interVpnLinkState.getSecondEndpointState().getDpId() ) {
            String flowRef = InterVpnLinkUtil.getLportDispatcherFlowRef(del.getName(), firstEndpointLportTag);
            FlowKey flowKey = new FlowKey(new FlowId(flowRef));
            Flow flow = new FlowBuilder().setKey(flowKey).setId(new FlowId(flowRef))
                                         .setTableId(NwConstants.LPORT_DISPATCHER_TABLE).setFlowName(flowRef)
                                         .build();
            mdsalManager.removeFlow(dpnId, flow);

            // Also remove the 'fake' iface from the VpnToDpn map
            VpnUtil.removeIfaceFromVpnToDpnMap(dataBroker, rd2, dpnId, getInterVpnLinkIfaceName(vpn2Uuid, dpnId));
        }

        // Release idManager wit LPortTag associated to endpoints
        InterVpnLinkKey key = del.getKey();
        Uuid firstEndpointVpnUuid = del.getFirstEndpoint().getVpnUuid();
        Uuid secondEndpointVpnUuid = del.getSecondEndpoint().getVpnUuid();
        releaseVpnLinkLPortTag(key.getName() + firstEndpointVpnUuid.getValue());
        releaseVpnLinkLPortTag(key.getName() + secondEndpointVpnUuid.getValue());

        // Routes with nextHop pointing to an end-point of the inter-vpn-link are populated into FIB table.
        // The action in that case is a nx_resubmit to LPortDispatcher table. This is done in FibManager.
        // At this point. we need to check if is there any entry in FIB table pointing to LPortDispatcher table.
        // Remove it in that case.

        // 1stEndPoint dpns
        for ( BigInteger dpnId : interVpnLinkState.getFirstEndpointState().getDpId() ) {
            removeRouteFromInterVpnLink(dpnId, del.getName(), del.getSecondEndpoint().getIpAddress().getValue());
        }

        // 2ndtEndPoint dpns
        for ( BigInteger dpnId : interVpnLinkState.getSecondEndpointState().getDpId() ) {
            removeRouteFromInterVpnLink(dpnId, del.getName(), del.getFirstEndpoint().getIpAddress().getValue());
        }

        // Removing the InterVpnLinkState
        InstanceIdentifier<InterVpnLinkState> interVpnLinkStateIid = VpnUtil.getInterVpnLinkStateIid(del.getName());
        VpnUtil.delete(dataBroker, LogicalDatastoreType.CONFIGURATION, interVpnLinkStateIid);
    }

    private void releaseVpnLinkLPortTag(String idKey) {
        ReleaseIdInput releaseIdInput = new ReleaseIdInputBuilder().setPoolName(IfmConstants.IFM_IDPOOL_NAME)
                                                                   .setIdKey(idKey).build();
        idManager.releaseId(releaseIdInput);
    }

    @Override
    protected void update(InstanceIdentifier<InterVpnLink> identifier, InterVpnLink original, InterVpnLink update) {
     // TODO
    }

    private String getInterVpnFibFlowRef(BigInteger dpnId, short tableId, String interVpnLinkName, String nextHop ) {
        return new StringBuilder(64).append(VpnConstants.FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
            .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
            .append(interVpnLinkName).append(NwConstants.FLOWID_SEPARATOR)
            .append(nextHop).toString();
    }

    private void removeRouteFromInterVpnLink(BigInteger dpnId, String interVpnLinkName, final String nextHop) {
        String flowRef = getInterVpnFibFlowRef(dpnId, NwConstants.L3_FIB_TABLE, interVpnLinkName, nextHop);
        FlowKey flowKey = new FlowKey(new FlowId(flowRef));
        Flow flow = new FlowBuilder().setKey(flowKey).setId(new FlowId(flowRef))
                .setTableId(NwConstants.L3_FIB_TABLE).setFlowName(flowRef)
                .build();
        mdsalManager.removeFlow(dpnId, flow);
    }


    private Integer allocateVpnLinkLportTag(String idKey) {
        AllocateIdInput getIdInput =
                new AllocateIdInputBuilder().setPoolName(IfmConstants.IFM_IDPOOL_NAME).setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if(rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id",e);
        }
        return INVALID_ID;
    }

    protected void setInError(final InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid,
                              final InterVpnLinkState vpnLinkState,
                              String errorMsg) {
        // Setting InterVPNLink in error state in MDSAL
        InterVpnLinkState vpnLinkErrorState =
           new InterVpnLinkStateBuilder(vpnLinkState).setState(InterVpnLinkState.State.Error)
                                                     .setErrorDescription(errorMsg)
                                                     .build();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, vpnLinkStateIid, vpnLinkErrorState, true);
        tx.submit();

        // Sending out an error Notification
        InterVpnLinkCreationErrorMessage errMsg =
            new InterVpnLinkCreationErrorMessageBuilder().setErrorMessage(errorMsg).build();
        InterVpnLinkCreationError notif =
            new InterVpnLinkCreationErrorBuilder().setInterVpnLinkCreationErrorMessage(errMsg).build();
        final ListenableFuture<? extends Object> eventFuture = this.notificationsService.offerNotification(notif);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.warn("Error when sending notification about InterVpnLink creation issue. InterVpnLink name={}. Error={}",
                            vpnLinkState.getInterVpnLinkName(), vpnLinkState, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.trace("Error notification for InterVpnLink successfully sent. VpnLink={} error={}",
                             vpnLinkState.getInterVpnLinkName(), vpnLinkState);
            }
        });
    }
}

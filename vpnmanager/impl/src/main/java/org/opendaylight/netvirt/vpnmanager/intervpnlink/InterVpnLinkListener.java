/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnFootprintService;
import org.opendaylight.netvirt.vpnmanager.VpnOpDataSyncer;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.IVpnLinkService;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks.InterVpnLinkCleanedCheckerTask;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks.InterVpnLinkCreatorTask;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks.InterVpnLinkRemoverTask;
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
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InterVpnLinkListener extends AsyncDataTreeChangeListenerBase<InterVpnLink, InterVpnLinkListener> {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkListener.class);

    private static final long INVALID_ID = 0;

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final NotificationPublishService notificationsService;
    private final IVpnLinkService ivpnLinkService;
    private final InterVpnLinkLocator ivpnLinkLocator;
    private final VpnFootprintService vpnFootprintService;
    private final VpnOpDataSyncer vpnOpDataSyncer;
    private final JobCoordinator jobCoordinator;
    private final InterVpnLinkCache interVpnLinkCache;
    private final VpnUtil vpnUtil;
    private final InterVpnLinkUtil interVpnLinkUtil;

    @Inject
    public InterVpnLinkListener(final DataBroker dataBroker, final IdManagerService idManager,
                                final IMdsalApiManager mdsalManager, final IBgpManager bgpManager,
                                final IFibManager fibManager, final NotificationPublishService notifService,
                                final IVpnLinkService interVpnLinkService,
                                final InterVpnLinkLocator interVpnLinkLocator,
                                final VpnFootprintService vpnFootprintService,
                                final VpnOpDataSyncer vpnOpDataSyncer,
                                final JobCoordinator jobCoordinator,
                                final InterVpnLinkCache interVpnLinkCache, final VpnUtil vpnUtil,
                                final InterVpnLinkUtil interVpnLinkUtil) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = idManager;
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.notificationsService = notifService;
        this.ivpnLinkService = interVpnLinkService;
        this.ivpnLinkLocator = interVpnLinkLocator;
        this.vpnFootprintService = vpnFootprintService;
        this.vpnOpDataSyncer = vpnOpDataSyncer;
        this.jobCoordinator = jobCoordinator;
        this.interVpnLinkCache = interVpnLinkCache;
        this.vpnUtil = vpnUtil;
        this.interVpnLinkUtil = interVpnLinkUtil;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<InterVpnLink> getWildCardPath() {
        return InstanceIdentifier.create(InterVpnLinks.class).child(InterVpnLink.class);
    }

    @Override
    protected InterVpnLinkListener getDataTreeChangeListener() {
        return InterVpnLinkListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<InterVpnLink> identifier, InterVpnLink add) {

        String ivpnLinkName = add.getName();
        LOG.debug("Reacting to IVpnLink {} creation. Vpn1=[name={}  EndpointIp={}]  Vpn2=[name={} endpointIP={}]",
                  ivpnLinkName, add.getFirstEndpoint().getVpnUuid(), add.getFirstEndpoint().getIpAddress(),
                  add.getSecondEndpoint().getVpnUuid(), add.getSecondEndpoint().getIpAddress());

        // Create VpnLink state
        InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid = InterVpnLinkUtil.getInterVpnLinkStateIid(ivpnLinkName);
        InterVpnLinkState vpnLinkState = new InterVpnLinkStateBuilder().setInterVpnLinkName(ivpnLinkName).build();
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnLinkStateIid, vpnLinkState);

        InterVpnLinkKey key = add.key();
        Uuid vpn1Uuid = add.getFirstEndpoint().getVpnUuid();
        String vpn1Name = vpn1Uuid.getValue();
        Uuid vpn2Uuid = add.getSecondEndpoint().getVpnUuid();
        String vpn2Name = vpn2Uuid.getValue();
        String vpn1PrimaryRd = vpnUtil.getPrimaryRd(vpn1Name);
        String vpn2PrimaryRd = vpnUtil.getPrimaryRd(vpn1Name);
        if (!vpnUtil.isVpnPendingDelete(vpn1PrimaryRd)
                && !vpnUtil.isVpnPendingDelete(vpn2PrimaryRd)) {
            if (vpnUtil.getVpnInstance(vpn1Name) == null) {
                String errMsg = "InterVpnLink " + ivpnLinkName + " creation error: could not find 1st endpoint Vpn "
                        + vpn1Name;
                setInError(vpnLinkStateIid, vpnLinkState, errMsg);
                return;
            }
            if (!checkVpnAvailability(key, vpn1Name)) {
                String errMsg = "InterVpnLink " + ivpnLinkName + " creation error: Vpn " + vpn1Name
                        + " is already associated to an inter-vpn-link ";
                setInError(vpnLinkStateIid, vpnLinkState, errMsg);
                return;
            }

            // Second VPN
            if (vpnUtil.getVpnInstance(vpn2Name) == null) {
                String errMsg = "InterVpnLink " + ivpnLinkName + " creation error: could not find 2nd endpoint Vpn "
                        + vpn2Name;
                setInError(vpnLinkStateIid, vpnLinkState, errMsg);
                return;
            }
            if (!checkVpnAvailability(key, vpn2Name)) {
                String errMsg = "InterVpnLink " + ivpnLinkName + " creation error: Vpn " + vpn2Name
                        + " is already associated with an inter-vpn-link";
                setInError(vpnLinkStateIid, vpnLinkState, errMsg);
                return;
            }

            interVpnLinkCache.addInterVpnLinkToCaches(add);

            // Wait for VPN Operational data ready
            long vpn1Id = vpnUtil.getVpnId(vpn1Name);
            if (vpn1Id == VpnConstants.INVALID_ID) {
                boolean vpn1Ready =
                        vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataSyncer.VpnOpDataType.vpnInstanceToId, vpn1Name,
                                VpnConstants.PER_VPN_INSTANCE_MAX_WAIT_TIME_IN_MILLISECONDS);
                if (!vpn1Ready) {
                    String errMsg =
                            "InterVpnLink " + ivpnLinkName + " creation error: Operational Data for VPN " + vpn1Name
                                    + " not ready after " + VpnConstants.PER_INTERFACE_MAX_WAIT_TIME_IN_MILLISECONDS
                                    + " milliseconds";
                    setInError(vpnLinkStateIid, vpnLinkState, errMsg);
                    return;
                }
            }
            long vpn2Id = vpnUtil.getVpnId(vpn2Name);
            if (vpn2Id == VpnConstants.INVALID_ID) {
                boolean vpn1Ready =
                        vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataSyncer.VpnOpDataType.vpnInstanceToId,vpn2Name,
                                VpnConstants.PER_VPN_INSTANCE_MAX_WAIT_TIME_IN_MILLISECONDS);
                if (!vpn1Ready) {
                    String errMsg =
                            "InterVpnLink " + ivpnLinkName + " creation error: Operational Data for VPN " + vpn2Name
                                    + " not ready after " + VpnConstants.PER_INTERFACE_MAX_WAIT_TIME_IN_MILLISECONDS
                                    + " milliseconds";
                    setInError(vpnLinkStateIid, vpnLinkState, errMsg);
                    return;
                }
            }

            List<BigInteger> firstDpnList = ivpnLinkLocator.selectSuitableDpns(add);
            if (firstDpnList != null && !firstDpnList.isEmpty()) {
                List<BigInteger> secondDpnList = firstDpnList;

                Long firstVpnLportTag = allocateVpnLinkLportTag(key.getName() + vpn1Name);
                Long secondVpnLportTag = allocateVpnLinkLportTag(key.getName() + vpn2Name);
                FirstEndpointState firstEndPointState =
                        new FirstEndpointStateBuilder().setVpnUuid(vpn1Uuid).setDpId(firstDpnList)
                                .setLportTag(firstVpnLportTag).build();
                SecondEndpointState secondEndPointState =
                        new SecondEndpointStateBuilder().setVpnUuid(vpn2Uuid).setDpId(secondDpnList)
                                .setLportTag(secondVpnLportTag).build();

                interVpnLinkUtil.updateInterVpnLinkState(ivpnLinkName, InterVpnLinkState.State.Active,
                        firstEndPointState, secondEndPointState, interVpnLinkCache);

                // Note that in the DPN of the firstEndpoint we install the lportTag of the secondEndpoint and viceversa
                interVpnLinkUtil.installLPortDispatcherTableFlow(ivpnLinkName, firstDpnList, vpn2Name,
                        secondVpnLportTag);
                interVpnLinkUtil.installLPortDispatcherTableFlow(ivpnLinkName, secondDpnList, vpn1Name,
                        firstVpnLportTag);
                // Update the VPN -> DPNs Map.
                // Note: when a set of DPNs is calculated for Vpn1, these DPNs are added to the VpnToDpn map of Vpn2.
                // Why? because we do the handover from Vpn1 to Vpn2 in those DPNs, so in those DPNs we must know how
                // to reach to Vpn2 targets. If new Vpn2 targets are added later, the Fib will be maintained in these
                // DPNs even if Vpn2 is not physically present there.
                interVpnLinkUtil.updateVpnFootprint(vpn2Name, vpn2PrimaryRd, firstDpnList);
                interVpnLinkUtil.updateVpnFootprint(vpn1Name, vpn1PrimaryRd, secondDpnList);

                // Program static routes if needed
                Optional<InterVpnLinkDataComposite> interVpnLink =
                        interVpnLinkCache.getInterVpnLinkByName(ivpnLinkName);
                ivpnLinkService.handleStaticRoutes(interVpnLink.get());

                // Now, if the corresponding flags are activated, there will be some routes exchange
                ivpnLinkService.exchangeRoutes(interVpnLink.get());
            } else {
                // If there is no connection to DPNs, the InterVpnLink is created and the InterVpnLinkState is also
                // created with the corresponding LPortTags but no DPN is assigned since there is no DPN operative.
                Long firstVpnLportTag = allocateVpnLinkLportTag(key.getName() + vpn1Name);
                Long secondVpnLportTag = allocateVpnLinkLportTag(key.getName() + vpn2Name);
                FirstEndpointState firstEndPointState =
                        new FirstEndpointStateBuilder().setVpnUuid(vpn1Uuid).setLportTag(firstVpnLportTag)
                                .setDpId(Collections.emptyList()).build();
                SecondEndpointState secondEndPointState =
                        new SecondEndpointStateBuilder().setVpnUuid(vpn2Uuid).setLportTag(secondVpnLportTag)
                                .setDpId(Collections.emptyList()).build();
                interVpnLinkUtil.updateInterVpnLinkState(ivpnLinkName, InterVpnLinkState.State.Error,
                        firstEndPointState, secondEndPointState, interVpnLinkCache);
            }
        }
    }

    private boolean checkVpnAvailability(InterVpnLinkKey key, String vpnName) {
        Preconditions.checkNotNull(vpnName);

        List<InterVpnLinkDataComposite> allInterVpnLinks = interVpnLinkCache.getAllInterVpnLinks();
        if (allInterVpnLinks.isEmpty()) {
            return true;
        }

        return allInterVpnLinks.stream().noneMatch(ivl -> !ivl.getInterVpnLinkName().equals(key.getName())
                                                          && ivl.isVpnLinked(vpnName));
    }

    @Override
    protected void remove(InstanceIdentifier<InterVpnLink> identifier, InterVpnLink del) {

        LOG.debug("Reacting to InterVpnLink {} removal", del.getName());

        // Remove learnt routes
        // Remove entries in the LPortDispatcher table
        // Remove the corresponding entries in InterVpnLinkState

        // For each endpoint, remove all routes that have been learnt by intervpnLink
        String vpn1Uuid = del.getFirstEndpoint().getVpnUuid().getValue();
        String rd1 = vpnUtil.getVpnRd(vpn1Uuid);
        LOG.debug("Removing leaked routes in VPN {}  rd={}", vpn1Uuid, rd1);
        vpnUtil.removeVrfEntriesByOrigin(rd1, RouteOrigin.INTERVPN);
        List<VrfEntry> vrfEntriesSecondEndpoint =
            vpnUtil.findVrfEntriesByNexthop(rd1, del.getSecondEndpoint().getIpAddress().getValue());

        String vpn2Uuid = del.getSecondEndpoint().getVpnUuid().getValue();
        String rd2 = vpnUtil.getVpnRd(vpn2Uuid);
        LOG.debug("Removing leaked routes in VPN {}  rd={}", vpn2Uuid, rd2);
        vpnUtil.removeVrfEntriesByOrigin(rd2, RouteOrigin.INTERVPN);
        List<VrfEntry> vrfEntriesFirstEndpoint =
            vpnUtil.findVrfEntriesByNexthop(rd2, del.getFirstEndpoint().getIpAddress().getValue());

        Optional<InterVpnLinkState> optIVpnLinkState = interVpnLinkUtil.getInterVpnLinkState(del.getName());
        if (optIVpnLinkState.isPresent()) {
            InterVpnLinkState interVpnLinkState = optIVpnLinkState.get();
            boolean isVpnFirstEndPoint = true;
            if (interVpnLinkState.getFirstEndpointState() != null) {
                Long firstEndpointLportTag = interVpnLinkState.getFirstEndpointState().getLportTag();
                removeVpnLinkEndpointFlows(del, vpn2Uuid, rd1,
                    interVpnLinkState.getSecondEndpointState().getDpId(),
                    firstEndpointLportTag.intValue(),
                    del.getFirstEndpoint().getIpAddress().getValue(),
                    vrfEntriesSecondEndpoint, isVpnFirstEndPoint);
            } else {
                LOG.info("Could not get first endpoint state attributes for InterVpnLink {}", del.getName());
            }
            isVpnFirstEndPoint = false;
            if (interVpnLinkState.getSecondEndpointState() != null) {
                Long secondEndpointLportTag = interVpnLinkState.getSecondEndpointState().getLportTag();
                removeVpnLinkEndpointFlows(del, vpn1Uuid, rd2,
                                           interVpnLinkState.getFirstEndpointState().getDpId(),
                                           secondEndpointLportTag.intValue(),
                                           del.getSecondEndpoint().getIpAddress().getValue(),
                                           vrfEntriesFirstEndpoint, isVpnFirstEndPoint);
            } else {
                LOG.info("Could not get second endpoint state attributes for InterVpnLink {}", del.getName());
            }
        }

        vpnUtil.removeVrfEntries(rd1, vrfEntriesSecondEndpoint);
        vpnUtil.removeVrfEntries(rd2, vrfEntriesFirstEndpoint);
        vpnUtil.withdrawRoutes(rd1, vrfEntriesSecondEndpoint);
        vpnUtil.withdrawRoutes(rd2, vrfEntriesFirstEndpoint);

        // Release idManager with LPortTag associated to endpoints
        LOG.debug("Releasing InterVpnLink {} endpoints LportTags", del.getName());
        InterVpnLinkKey key = del.key();
        Uuid firstEndpointVpnUuid = del.getFirstEndpoint().getVpnUuid();
        Uuid secondEndpointVpnUuid = del.getSecondEndpoint().getVpnUuid();
        releaseVpnLinkLPortTag(key.getName() + firstEndpointVpnUuid.getValue());
        releaseVpnLinkLPortTag(key.getName() + secondEndpointVpnUuid.getValue());

        // Routes with nextHop pointing to an end-point of the inter-vpn-link are populated into FIB table.
        // The action in that case is a nx_resubmit to LPortDispatcher table. This is done in FibManager.
        // At this point. we need to check if is there any entry in FIB table pointing to LPortDispatcher table.
        // Remove it in that case.

        // Removing the InterVpnLinkState
        InstanceIdentifier<InterVpnLinkState> interVpnLinkStateIid =
            InterVpnLinkUtil.getInterVpnLinkStateIid(del.getName());
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx ->
            tx.delete(LogicalDatastoreType.CONFIGURATION, interVpnLinkStateIid)), LOG,
                "Error deleting inter-VPN link state {}", interVpnLinkStateIid);
    }

    // We're catching Exception here to continue deleting as much as possible
    // TODO Rework this so it's done in one transaction
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeVpnLinkEndpointFlows(InterVpnLink del, String vpnUuid, String rd, List<BigInteger> dpns,
                                            int otherEndpointLportTag, String otherEndpointIpAddr,
                                            List<VrfEntry> vrfEntries, final boolean isVpnFirstEndPoint) {

        String interVpnLinkName = del.getName();
        LOG.debug("Removing endpoint flows for vpn {}.  InterVpnLink={}.  OtherEndpointLportTag={}",
            vpnUuid, interVpnLinkName, otherEndpointLportTag);
        if (dpns == null) {
            LOG.debug("VPN {} endpoint is not instantiated in any DPN for InterVpnLink {}",
                vpnUuid, interVpnLinkName);
            return;
        }

        for (BigInteger dpnId : dpns) {
            try {
                // Removing flow from LportDispatcher table
                String flowRef = InterVpnLinkUtil.getLportDispatcherFlowRef(interVpnLinkName, otherEndpointLportTag);
                FlowKey flowKey = new FlowKey(new FlowId(flowRef));
                Flow flow = new FlowBuilder().withKey(flowKey).setId(new FlowId(flowRef))
                    .setTableId(NwConstants.LPORT_DISPATCHER_TABLE).setFlowName(flowRef)
                    .build();
                mdsalManager.removeFlow(dpnId, flow);

                // Also remove the 'fake' iface from the VpnToDpn map
                interVpnLinkUtil.removeIVpnLinkIfaceFromVpnFootprint(vpnUuid, rd, dpnId);

            } catch (Exception e) {
                // Whatever happens it should not stop it from trying to remove as much as possible
                LOG.warn("Error while removing InterVpnLink {} Endpoint flows on dpn {}. Reason: ",
                    interVpnLinkName, dpnId, e);
            }
        }
        // Removing flow from FIB and LFIB tables
        LOG.trace("Removing flow in FIB and LFIB tables for vpn {} interVpnLink {} otherEndpointIpAddr {}",
            vpnUuid, interVpnLinkName, otherEndpointIpAddr);
        cleanUpInterVPNRoutes(interVpnLinkName, vrfEntries, isVpnFirstEndPoint);
    }


    private void releaseVpnLinkLPortTag(String idKey) {
        ReleaseIdInput releaseIdInput =
            new ReleaseIdInputBuilder().setPoolName(VpnConstants.PSEUDO_LPORT_TAG_ID_POOL_NAME).setIdKey(idKey).build();

        JdkFutures.addErrorLogging(idManager.releaseId(releaseIdInput), LOG, "Release Id");
    }

    @Override
    protected void update(InstanceIdentifier<InterVpnLink> identifier, InterVpnLink original, InterVpnLink update) {

        LOG.debug("Update InterVpnLink {}. "
                + " original=[1stEndpoint=[vpn=<{}> ipAddr=<{}>] 2ndEndpoint=[vpn=<{}> ipAddr=<{}>]]"
                + " update=[1stEndpoint=[vpn=<{}> ipAddr=<{}>] 2ndEndpoint=[vpn=<{}> ipAddr=<{}>]]",
            original.getName(),
            original.getFirstEndpoint().getVpnUuid(), original.getFirstEndpoint().getIpAddress(),
            original.getSecondEndpoint().getVpnUuid(), original.getSecondEndpoint().getIpAddress(),
            update.getFirstEndpoint().getVpnUuid(), update.getFirstEndpoint().getIpAddress(),
            update.getSecondEndpoint().getVpnUuid(), update.getSecondEndpoint().getIpAddress());

        String specificJobKey = "InterVpnLink.update." + original.getName();
        jobCoordinator.enqueueJob(specificJobKey, new InterVpnLinkRemoverTask(dataBroker, identifier));
        jobCoordinator.enqueueJob(specificJobKey, new InterVpnLinkCleanedCheckerTask(dataBroker, original,
                interVpnLinkUtil, vpnUtil));
        jobCoordinator.enqueueJob(specificJobKey, new InterVpnLinkCreatorTask(dataBroker, update));
    }

    private Long allocateVpnLinkLportTag(String idKey) {
        AllocateIdInput getIdInput =
            new AllocateIdInputBuilder().setPoolName(VpnConstants.PSEUDO_LPORT_TAG_ID_POOL_NAME)
                .setIdKey(idKey)
                .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue();
            } else {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id", e);
        }
        return INVALID_ID;
    }

    protected void setInError(final InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid,
        final InterVpnLinkState vpnLinkState,
        String errorMsg) {
        LOG.error("Setting InterVpnLink {} in error. Reason: {}", vpnLinkState.getInterVpnLinkName(), errorMsg);

        // Setting InterVPNLink in error state in MDSAL
        InterVpnLinkState vpnLinkErrorState =
            new InterVpnLinkStateBuilder(vpnLinkState).setState(InterVpnLinkState.State.Error)
                .setErrorDescription(errorMsg)
                .build();
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx ->
            tx.put(LogicalDatastoreType.CONFIGURATION, vpnLinkStateIid, vpnLinkErrorState,
                    WriteTransaction.CREATE_MISSING_PARENTS)),
                LOG, "Error storing the VPN link error state for {}, {}", vpnLinkStateIid, vpnLinkErrorState);

        // Sending out an error Notification
        InterVpnLinkCreationErrorMessage errMsg =
            new InterVpnLinkCreationErrorMessageBuilder().setErrorMessage(errorMsg).build();
        InterVpnLinkCreationError notif =
            new InterVpnLinkCreationErrorBuilder().setInterVpnLinkCreationErrorMessage(errMsg).build();
        final ListenableFuture<?> eventFuture = this.notificationsService.offerNotification(notif);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.warn("Error when sending notification about InterVpnLink creation issue. InterVpnLink name={} "
                                + "state={}.", vpnLinkState.getInterVpnLinkName(), vpnLinkState, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.trace("Error notification for InterVpnLink successfully sent. VpnLinkName={} state={}",
                    vpnLinkState.getInterVpnLinkName(), vpnLinkState);
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Removes all the flows from FIB/LFIB matching an endpoint from the intervpnlink.
     *
     * @param interVpnLinkName name of the intervpnlink
     * @param vrfEntries list of vrfs matching the first/second intervpnlink endpoints
     * @param isVpnFirstEndPoint indicates whether vrfEntries belong to the vpn of the first endpoint
     */
    private void cleanUpInterVPNRoutes(final String interVpnLinkName,
        List<VrfEntry> vrfEntries,
        final boolean isVpnFirstEndPoint) {

        for (VrfEntry vrfEntry : vrfEntries) {
            fibManager.removeInterVPNLinkRouteFlows(interVpnLinkName, isVpnFirstEndPoint, vrfEntry);
        }
    }
}

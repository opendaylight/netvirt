/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.netvirt.vpnmanager.arp.responder.ArpResponderHandler;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.LabelRouteMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnInterfaceManager extends AsyncDataTreeChangeListenerBase<VpnInterface, VpnInterfaceManager> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceManager.class);
    private static final int VPN_INF_UPDATE_TIMER_TASK_DELAY = 1000;
    private static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;
    private final OdlInterfaceRpcService ifaceMgrRpcService;
    private final VpnFootprintService vpnFootprintService;
    private final IInterfaceManager interfaceManager;
    private final IVpnManager vpnManager;
    private final ArpResponderHandler arpResponderHandler;
    private final JobCoordinator jobCoordinator;

    private final ConcurrentHashMap<String, Runnable> vpnIntfMap = new ConcurrentHashMap<>();

    private final BlockingQueue<UpdateData> vpnInterfacesUpdateQueue = new LinkedBlockingQueue<>();
    private final ScheduledThreadPoolExecutor vpnInfUpdateTaskExecutor = (ScheduledThreadPoolExecutor) Executors
            .newScheduledThreadPool(1);

    private final Map<String, ConcurrentLinkedQueue<UnprocessedVpnInterfaceData>> unprocessedVpnInterfaces =
            new ConcurrentHashMap<>();

    @Inject
    public VpnInterfaceManager(final DataBroker dataBroker,
                               final IBgpManager bgpManager,
                               final IdManagerService idManager,
                               final IMdsalApiManager mdsalManager,
                               final IFibManager fibManager,
                               final OdlInterfaceRpcService ifaceMgrRpcService,
                               final VpnFootprintService vpnFootprintService,
                               final IInterfaceManager interfaceManager,
                               final IVpnManager vpnManager,
                               final ArpResponderHandler arpResponderHandler,
                               final JobCoordinator jobCoordinator) {
        super(VpnInterface.class, VpnInterfaceManager.class);

        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.mdsalManager = mdsalManager;
        this.fibManager = fibManager;
        this.ifaceMgrRpcService = ifaceMgrRpcService;
        this.vpnFootprintService = vpnFootprintService;
        this.interfaceManager = interfaceManager;
        this.vpnManager = vpnManager;
        this.arpResponderHandler = arpResponderHandler;
        this.jobCoordinator = jobCoordinator;
        vpnInfUpdateTaskExecutor.scheduleWithFixedDelay(new VpnInterfaceUpdateTimerTask(),
            0, VPN_INF_UPDATE_TIMER_TASK_DELAY, TIME_UNIT);
    }

    public Runnable isNotifyTaskQueued(String intfName) {
        return vpnIntfMap.remove(intfName);
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInterface> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    protected VpnInterfaceManager getDataTreeChangeListener() {
        return VpnInterfaceManager.this;
    }

    @Override
    public void add(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface) {
        LOG.info("add: intfName {} onto vpnName {}",
                 vpnInterface.getName(),
                 VpnHelper.getVpnInterfaceVpnInstanceNamesString(vpnInterface.getVpnInstanceNames()));
        addVpnInterface(identifier, vpnInterface, null, null);
    }

    private boolean canHandleNewVpnInterface(final InstanceIdentifier<VpnInterface> identifier,
                          final VpnInterface vpnInterface, String vpnName) {
        synchronized (vpnName.intern()) {
            if (isVpnInstanceReady(vpnName)) {
                return true;
            }
            addToUnprocessedVpnInterfaces(identifier, vpnInterface, vpnName);
            return false;
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addVpnInterface(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface,
                             final List<Adjacency> oldAdjs, final List<Adjacency> newAdjs) {
        for (VpnInstanceNames vpnInterfaceVpnInstance : vpnInterface.getVpnInstanceNames()) {
            String vpnName = vpnInterfaceVpnInstance.getVpnName();
            addVpnInterfaceCall(identifier, vpnInterface, oldAdjs, newAdjs, vpnName);
        }
    }

    private void addVpnInterfaceCall(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface,
                         final List<Adjacency> oldAdjs, final List<Adjacency> newAdjs, String vpnName) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();

        if (!canHandleNewVpnInterface(identifier, vpnInterface, vpnName)) {
            LOG.error("add: VpnInstance {} for vpnInterface {} not ready, holding on ",
                  vpnName, vpnInterface.getName());
            return;
        }
        InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier = VpnUtil
             .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
        List<Adjacency> copyOldAdjs = null;
        if (oldAdjs != null) {
            copyOldAdjs = new ArrayList<>();
            copyOldAdjs.addAll(oldAdjs);
        }
        List<Adjacency> copyNewAdjs = null;
        if (newAdjs != null) {
            copyNewAdjs = new ArrayList<>();
            copyNewAdjs.addAll(newAdjs);
        }
        addVpnInterfaceToVpn(vpnInterfaceOpIdentifier, vpnInterface, copyOldAdjs, copyNewAdjs, identifier, vpnName);
    }

    private void addVpnInterfaceToVpn(final InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier,
                    final VpnInterface vpnInterface, final List<Adjacency> oldAdjs,
                    final List<Adjacency> newAdjs,
                    final InstanceIdentifier<VpnInterface> identifier, String vpnName) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();
        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
        if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
            Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
            boolean isBgpVpnInternetVpn = VpnUtil.isBgpVpnInternet(dataBroker, vpnName);
            if (interfaceState != null) {
                try {
                    final BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(interfaceState);
                    final int ifIndex = interfaceState.getIfIndex();
                    jobCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName + vpnName, () -> {
                        WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction writeInvTxn = dataBroker.newWriteOnlyTransaction();
                        LOG.info("addVpnInterface: VPN Interface add event - intfName {} vpnName {} on dpn {}" ,
                                vpnInterface.getName(), vpnName, vpnInterface.getDpnId());
                        processVpnInterfaceUp(dpnId, vpnInterface, primaryRd, ifIndex, false, writeConfigTxn,
                                     writeOperTxn, writeInvTxn, interfaceState, vpnName);
                        if (oldAdjs != null && !oldAdjs.equals(newAdjs)) {
                            LOG.info("addVpnInterface: Adjacency changed upon VPNInterface {}"
                                    + " Update for swapping VPN {} case.", interfaceName, vpnName);
                            if (newAdjs != null) {
                                for (Adjacency adj : newAdjs) {
                                    if (oldAdjs.contains(adj)) {
                                        oldAdjs.remove(adj);
                                    } else {
                                        if (!isBgpVpnInternetVpn || VpnUtil.isAdjacencyEligibleToVpnInternet(
                                                                  dataBroker, adj)) {
                                            addNewAdjToVpnInterface(vpnInterfaceOpIdentifier, primaryRd,
                                                  adj, dpnId, writeOperTxn, writeConfigTxn);
                                        }
                                    }
                                }
                            }
                            for (Adjacency adj : oldAdjs) {
                                if (!isBgpVpnInternetVpn || VpnUtil.isAdjacencyEligibleToVpnInternet(
                                                          dataBroker, adj)) {
                                    delAdjFromVpnInterface(vpnInterfaceOpIdentifier, adj, dpnId,
                                        writeOperTxn, writeConfigTxn);
                                }
                            }
                        }
                        ListenableFuture<Void> operFuture = writeOperTxn.submit();
                        try {
                            operFuture.get();
                        } catch (ExecutionException e) {
                            LOG.error("addVpnInterface: Exception encountered while submitting operational future for"
                                    + " addVpnInterface {} on vpn {}: {}", vpnInterface.getName(), vpnName, e);
                            return null;
                        }
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        futures.add(writeConfigTxn.submit());
                        futures.add(writeInvTxn.submit());
                        LOG.info("addVpnInterface: Addition of interface {} in VPN {} on dpn {}"
                                + " processed successfully", interfaceName, vpnName, dpnId);
                        return futures;
                    });
                } catch (NumberFormatException | IllegalStateException e) {
                    LOG.error("addVpnInterface: Unable to retrieve dpnId from interface operational data store for "
                                    + "interface {}. Interface addition on vpn {} failed", interfaceName,
                                    vpnName, e);
                    return;
                }
            } else if (Boolean.TRUE.equals(vpnInterface.isRouterInterface())) {
                jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterface.getName() + vpnName,
                    () -> {
                        WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                        createFibEntryForRouterInterface(primaryRd, vpnInterface, interfaceName,
                                       writeConfigTxn, vpnName);
                        LOG.info("addVpnInterface: Router interface {} for vpn {} on dpn {}", interfaceName,
                                 vpnName, vpnInterface.getDpnId());
                        ListenableFuture<Void> futures = writeConfigTxn.submit();
                        String errorText = "addVpnInterfaceCall: Exception encountered while submitting writeConfigTxn"
                             + " for interface " + vpnInterface.getName() + " on vpn " + vpnName;
                        ListenableFutures.addErrorLogging(futures, LOG, errorText);
                        return Collections.singletonList(futures);
                    });
            } else {
                LOG.info("addVpnInterface: Handling addition of VPN interface {} on vpn {} skipped as interfaceState"
                        + " is not available", interfaceName, vpnName);
            }
        } else {
            LOG.error("addVpnInterface: Handling addition of VPN interface {} on vpn {} dpn {} skipped"
                      + " as vpn is pending delete", interfaceName, vpnName,
                    vpnInterface.getDpnId());
        }
    }

    // "Unconditional wait" and "Wait not in loop" wrt the VpnNotifyTask below - suppressing the FB violation -
    // see comments below.
    @SuppressFBWarnings({"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"})
    protected void processVpnInterfaceUp(final BigInteger dpId, VpnInterface vpnInterface, final String primaryRd,
            final int lportTag, boolean isInterfaceUp,
            WriteTransaction writeConfigTxn,
            WriteTransaction writeOperTxn,
            WriteTransaction writeInvTxn,
            Interface interfaceState,
            final String vpnName) {
        final String interfaceName = vpnInterface.getName();
        Optional<VpnInterfaceOpDataEntry> optOpVpnInterface = VpnUtil.getVpnInterfaceOpDataEntry(dataBroker,
                                                         interfaceName, vpnName);
        VpnInterfaceOpDataEntry opVpnInterface = optOpVpnInterface.isPresent() ? optOpVpnInterface.get() : null;
        boolean isBgpVpnInternetVpn = VpnUtil.isBgpVpnInternet(dataBroker, vpnName);
        if (!isInterfaceUp) {
            LOG.info("processVpnInterfaceUp: Binding vpn service to interface {} onto dpn {} for vpn {}",
                     interfaceName, dpId, vpnName);
            long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            if (vpnId == VpnConstants.INVALID_ID) {
                LOG.warn("processVpnInterfaceUp: VpnInstance to VPNId mapping not available for VpnName {}"
                        + " processing vpninterface {} on dpn {}, bailing out now.", vpnName, interfaceName,
                        dpId);
                return;
            }

            boolean waitForVpnInterfaceOpRemoval = false;
            if (opVpnInterface != null && !opVpnInterface.isScheduledForRemove()) {
                String opVpnName = opVpnInterface.getVpnInstanceName();
                String primaryInterfaceIp = null;
                if (opVpnName.equals(vpnName)) {
                    // Please check if the primary VRF Entry does not exist for VPNInterface
                    // If so, we have to process ADD, as this might be a DPN Restart with Remove and Add triggered
                    // back to back
                    // However, if the primary VRF Entry for this VPNInterface exists, please continue bailing out !
                    List<Adjacency> adjs = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker, interfaceName);
                    if (adjs == null) {
                        LOG.warn("processVpnInterfaceUp: VPN Interface {} on dpn {} for vpn {} failed as adjacencies"
                                + " for this vpn interface could not be obtained", interfaceName, dpId,
                                vpnName);
                        return;
                    }
                    for (Adjacency adj : adjs) {
                        if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                            primaryInterfaceIp = adj.getIpAddress();
                            break;
                        }
                    }
                    if (primaryInterfaceIp == null) {
                        LOG.warn("processVpnInterfaceUp: VPN Interface {} addition on dpn {} for vpn {} failed"
                                + " as primary adjacency for this vpn interface could not be obtained", interfaceName,
                                dpId, vpnName);
                        return;
                    }
                    // Get the rd of the vpn instance
                    VrfEntry vrf = VpnUtil.getVrfEntry(dataBroker, primaryRd, primaryInterfaceIp);
                    if (vrf != null) {
                        LOG.warn("processVpnInterfaceUp: VPN Interface {} on dpn {} for vpn {} already provisioned ,"
                                + " bailing out from here.", interfaceName, dpId, vpnName);
                        return;
                    }
                    waitForVpnInterfaceOpRemoval = true;
                } else {
                    LOG.warn("processVpnInterfaceUp: vpn interface {} to go to configured vpn {} on dpn {},"
                            + " but in operational vpn {}", interfaceName, vpnName, dpId, opVpnName);
                }
            }
            if (!waitForVpnInterfaceOpRemoval) {
                // Add the VPNInterface and quit
                vpnFootprintService.updateVpnToDpnMapping(dpId, vpnName, primaryRd, interfaceName,
                        null/*ipAddressSourceValuePair*/,
                        true /* add */);
                if (!isBgpVpnInternetVpn) {
                    VpnUtil.bindService(vpnName, interfaceName, dataBroker, false /*isTunnelInterface*/,
                            jobCoordinator);
                }
                processVpnInterfaceAdjacencies(dpId, lportTag, vpnName, primaryRd, interfaceName,
                        vpnId, writeConfigTxn, writeOperTxn, writeInvTxn, interfaceState);
                LOG.info("processVpnInterfaceUp: Plumbed vpn interface {} onto dpn {} for vpn {}", interfaceName,
                        dpId, vpnName);
                if (interfaceManager.isExternalInterface(interfaceName)) {
                    processExternalVpnInterface(interfaceName, vpnName, vpnId, dpId, lportTag, writeInvTxn,
                        NwConstants.ADD_FLOW);
                }
                return;
            }

            // FIB didn't get a chance yet to clean up this VPNInterface
            // Let us give it a chance here !
            LOG.info("processVpnInterfaceUp: Trying to add VPN Interface {} on dpn {} for vpn {},"
                    + " but waiting for FIB to clean up! ", interfaceName, dpId, vpnName);
            try {
                Runnable notifyTask = new VpnNotifyTask();
                synchronized (notifyTask) {
                    // Per FB's "Unconditional wait" violation, the code should really verify that the condition it
                    // intends to wait for is not already satisfied before calling wait. However the VpnNotifyTask is
                    // published here while holding the lock on it so this path will hit the wait before notify can be
                    // invoked.
                    vpnIntfMap.put(interfaceName, notifyTask);
                    try {
                        notifyTask.wait(VpnConstants.MAX_WAIT_TIME_IN_MILLISECONDS);
                    } catch (InterruptedException e) {
                        // Ignored
                    }
                }
            } finally {
                vpnIntfMap.remove(interfaceName);
            }

            if (opVpnInterface != null) {
                LOG.warn("processVpnInterfaceUp: VPN Interface {} removal on dpn {} for vpn {}"
                        + " by FIB did not complete on time," + " bailing addition ...", interfaceName,
                        dpId, vpnName);
                return;
            }
            // VPNInterface got removed, proceed with Add
            LOG.info("processVpnInterfaceUp: Continuing to plumb vpn interface {} onto dpn {} for vpn {}",
                    interfaceName, dpId, vpnName);
            vpnFootprintService.updateVpnToDpnMapping(dpId, vpnName, primaryRd, interfaceName,
                    null/*ipAddressSourceValuePair*/,
                    true /* add */);
            if (!isBgpVpnInternetVpn) {
                VpnUtil.bindService(vpnName, interfaceName, dataBroker, false/*isTunnelInterface*/, jobCoordinator);
            }
            processVpnInterfaceAdjacencies(dpId, lportTag, vpnName, primaryRd, interfaceName,
                    vpnId, writeConfigTxn, writeOperTxn, writeInvTxn, interfaceState);
            LOG.info("processVpnInterfaceUp: Plumbed vpn interface {} onto dpn {} for vpn {} after waiting for"
                    + " FIB to clean up", interfaceName, dpId, vpnName);
            if (interfaceManager.isExternalInterface(interfaceName)) {
                processExternalVpnInterface(interfaceName, vpnName, vpnId, dpId,
                               lportTag, writeInvTxn, NwConstants.ADD_FLOW);
            }
        } else {
            // Interface is retained in the DPN, but its Link Up.
            // Advertise prefixes again for this interface to BGP
            InstanceIdentifier<VpnInterface> identifier =
                VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName());
            InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier =
                VpnUtil.getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
            advertiseAdjacenciesForVpnToBgp(primaryRd, dpId, vpnInterfaceOpIdentifier, vpnName, interfaceName);
            // Perform similar operation as interface add event for extraroutes.
            InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> optAdjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
            if (!optAdjacencies.isPresent()) {
                LOG.trace("No config adjacencies present for vpninterface {}", vpnInterface);
                return;
            }
            List<Adjacency> adjacencies = optAdjacencies.get().getAdjacency();
            for (Adjacency adjacency : adjacencies) {
                if (adjacency.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                    continue;
                }
                // if BGPVPN Internet, filter only IPv6 Adjacencies
                if (isBgpVpnInternetVpn && !VpnUtil.isAdjacencyEligibleToVpnInternet(
                                dataBroker, adjacency)) {
                    continue;
                }
                addNewAdjToVpnInterface(vpnInterfaceOpIdentifier, primaryRd, adjacency,
                          dpId, writeOperTxn, writeConfigTxn);
            }
        }
    }

    private void processExternalVpnInterface(String interfaceName, String vpnName, long vpnId, BigInteger dpId,
            int lportTag, WriteTransaction writeInvTxn, int addOrRemove) {
        Uuid extNetworkId;
        try {
            // vpn instance of ext-net interface is the network-id
            extNetworkId = new Uuid(vpnName);
        } catch (IllegalArgumentException e) {
            LOG.error("processExternalVpnInterface: VPN instance {} is not Uuid. Processing external vpn interface {}"
                    + " on dpn {} failed", vpnName, interfaceName, dpId);
            return;
        }

        List<Uuid> routerIds = VpnUtil.getExternalNetworkRouterIds(dataBroker, extNetworkId);
        if (routerIds == null || routerIds.isEmpty()) {
            LOG.info("processExternalVpnInterface: No router is associated with {}."
                    + " Bailing out of processing external vpn interface {} on dpn {} for vpn {}",
                    extNetworkId.getValue(), interfaceName, dpId, vpnName);
            return;
        }

        LOG.info("processExternalVpnInterface: Router-ids {} associated with exernal vpn-interface {} on dpn {}"
                + " for vpn {}", routerIds, interfaceName, dpId, vpnName);
        for (Uuid routerId : routerIds) {
            String routerName = routerId.getValue();
            BigInteger primarySwitch = VpnUtil.getPrimarySwitchForRouter(dataBroker, routerName);
            if (Objects.equals(primarySwitch, dpId)) {
                Routers router = VpnUtil.getExternalRouter(dataBroker, routerName);
                if (router != null) {
                    if (addOrRemove == NwConstants.ADD_FLOW) {
                        vpnManager.addArpResponderFlowsToExternalNetworkIps(routerName,
                                VpnUtil.getIpsListFromExternalIps(router.getExternalIps()), router.getExtGwMacAddress(),
                                dpId, vpnId, interfaceName, lportTag, writeInvTxn);
                    } else {
                        vpnManager.removeArpResponderFlowsToExternalNetworkIps(routerName,
                                VpnUtil.getIpsListFromExternalIps(router.getExternalIps()),
                                dpId, interfaceName, lportTag, writeInvTxn);
                    }
                } else {
                    LOG.error("processExternalVpnInterface: No external-router found for router-id {}. Bailing out of"
                            + " processing external vpn-interface {} on dpn {} for vpn {}", routerName,
                            interfaceName, dpId, vpnName);
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void advertiseAdjacenciesForVpnToBgp(final String rd, BigInteger dpnId,
                                                 final InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
                                                  String vpnName, String interfaceName) {
        if (rd == null) {
            LOG.error("advertiseAdjacenciesForVpnFromBgp: Unable to recover rd for interface {} on dpn {} in vpn {}",
                  interfaceName, dpnId, vpnName);
            return;
        } else {
            if (rd.equals(vpnName)) {
                LOG.info("advertiseAdjacenciesForVpnFromBgp: Ignoring BGP advertisement for interface {} on dpn {}"
                        + " as it is in internal vpn{} with rd {}", interfaceName, dpnId, vpnName, rd);
                return;
            }
        }
        LOG.info("advertiseAdjacenciesForVpnToBgp: Advertising interface {} on dpn {} in vpn {} with rd {} ",
                interfaceName, dpnId, vpnName, rd);

        String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
        if (nextHopIp == null) {
            LOG.error("advertiseAdjacenciesForVpnToBgp: NextHop for interface {} on dpn {} is null,"
                    + " returning from advertising route with rd {} vpn {} to bgp", interfaceName, dpnId,
                    rd, vpnName);
            return;
        }

        //Read NextHops
        InstanceIdentifier<AdjacenciesOp> path = identifier.augmentation(AdjacenciesOp.class);
        Optional<AdjacenciesOp> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.debug("advertiseAdjacenciesForVpnToBgp:  NextHops are {} for interface {} on dpn {} for vpn {}"
                        + " rd {}", nextHops, interfaceName, dpnId, vpnName, rd);
                VpnInstanceOpDataEntry vpnInstanceOpData = VpnUtil.getVpnInstanceOpData(dataBroker, rd);
                long l3vni = vpnInstanceOpData.getL3vni();
                VrfEntry.EncapType encapType = VpnUtil.isL3VpnOverVxLan(l3vni)
                        ? VrfEntry.EncapType.Vxlan : VrfEntry.EncapType.Mplsgre;
                for (Adjacency nextHop : nextHops) {
                    if (nextHop.getAdjacencyType() == AdjacencyType.ExtraRoute) {
                        continue;
                    }
                    String gatewayMac = null;
                    long label = 0;
                    if (VpnUtil.isL3VpnOverVxLan(l3vni)) {
                        final VpnPortipToPort gwPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker,
                            vpnInstanceOpData.getVpnInstanceName(), nextHop.getIpAddress());
                        gatewayMac = arpResponderHandler.getGatewayMacAddressForInterface(gwPort, interfaceName).get();
                    } else {
                        label = nextHop.getLabel();
                    }
                    try {
                        LOG.info("VPN ADVERTISE: advertiseAdjacenciesForVpnToBgp: Adding Fib Entry rd {} prefix {}"
                                + " nexthop {} label {}", rd, nextHop.getIpAddress(), nextHopIp, label);
                        bgpManager.advertisePrefix(rd, nextHop.getMacAddress(), nextHop.getIpAddress(), nextHopIp,
                                encapType, (int)label, l3vni, 0 /*l2vni*/,
                                gatewayMac);
                        LOG.info("VPN ADVERTISE: advertiseAdjacenciesForVpnToBgp: Added Fib Entry rd {} prefix {}"
                                + " nexthop {} label {} for interface {} on dpn {} for vpn {}", rd,
                                nextHop.getIpAddress(), nextHopIp, label, interfaceName, dpnId, vpnName);
                    } catch (Exception e) {
                        LOG.error("advertiseAdjacenciesForVpnToBgp: Failed to advertise prefix {} in vpn {} with rd {}"
                                + " for interface {} on dpn {}", nextHop.getIpAddress(),
                                  vpnName, rd, interfaceName, dpnId, e);
                    }
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void withdrawAdjacenciesForVpnFromBgp(final InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
                        String vpnName, String interfaceName, WriteTransaction writeConfigTxn) {
        //Read NextHops
        InstanceIdentifier<AdjacenciesOp> path = identifier.augmentation(AdjacenciesOp.class);
        Optional<AdjacenciesOp> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);

        String rd = VpnUtil.getVpnRd(dataBroker, interfaceName);
        if (rd == null) {
            LOG.error("withdrawAdjacenciesForVpnFromBgp: Unable to recover rd for interface {} in vpn {}",
                interfaceName, vpnName);
            return;
        } else {
            if (rd.equals(vpnName)) {
                LOG.info(
                        "withdrawAdjacenciesForVpnFromBgp: Ignoring BGP withdrawal for interface {} as it is in "
                                + "internal vpn{} with rd {}", interfaceName, vpnName, rd);
                return;
            }
        }
        LOG.info("withdrawAdjacenciesForVpnFromBgp: For interface {} in vpn {} with rd {}", interfaceName,
               vpnName, rd);
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("withdrawAdjacenciesForVpnFromBgp: NextHops are {} for interface {} in vpn {} rd {}",
                        nextHops, interfaceName, vpnName, rd);
                for (Adjacency nextHop : nextHops) {
                    try {
                        if (nextHop.getAdjacencyType() != AdjacencyType.ExtraRoute) {
                            LOG.info("VPN WITHDRAW: withdrawAdjacenciesForVpnFromBgp: Removing Fib Entry rd {}"
                                    + " prefix {} for interface {} in vpn {}", rd, nextHop.getIpAddress(),
                                    interfaceName, vpnName);
                            bgpManager.withdrawPrefix(rd, nextHop.getIpAddress());
                            LOG.info("VPN WITHDRAW: withdrawAdjacenciesForVpnFromBgp: Removed Fib Entry rd {}"
                                    + " prefix {} for interface {} in vpn {}", rd, nextHop.getIpAddress(),
                                    interfaceName, vpnName);
                        } else {
                            // Perform similar operation as interface delete event for extraroutes.
                            String allocatedRd = nextHop.getVrfId();
                            for (String nh : nextHop.getNextHopIpList()) {
                                deleteExtraRouteFromCurrentAndImportingVpns(
                                    vpnName, nextHop.getIpAddress(), nh, allocatedRd, interfaceName, writeConfigTxn);
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("withdrawAdjacenciesForVpnFromBgp: Failed to withdraw prefix {} in vpn {} with rd {}"
                                + " for interface {} ", nextHop.getIpAddress(), vpnName, rd, interfaceName, e);
                    }
                }
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void processVpnInterfaceAdjacencies(BigInteger dpnId, final int lportTag, String vpnName,
                                                  String primaryRd, String interfaceName, final long vpnId,
                                                  WriteTransaction writeConfigTxn,
                                                  WriteTransaction writeOperTxn,
                                                  final WriteTransaction writeInvTxn,
                                                  Interface interfaceState) {
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        // Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
        if (!adjacencies.isPresent()) {
            addVpnInterfaceToOperational(vpnName, interfaceName, dpnId, null, writeOperTxn);
            return;
        }

        // Get the rd of the vpn instance
        String nextHopIp = null;
        try {
            nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
        } catch (Exception e) {
            LOG.error("processVpnInterfaceAdjacencies: Unable to retrieve endpoint ip address for "
                    + "dpnId {} for vpnInterface {} vpnName {}", dpnId, interfaceName, vpnName);
        }
        List<String> nhList = new ArrayList<>();
        if (nextHopIp != null) {
            nhList.add(nextHopIp);
            LOG.debug("processVpnInterfaceAdjacencies: NextHop for interface {} on dpn {} in vpn {} is {}",
                    interfaceName, dpnId, vpnName, nhList);
        }
        Optional<String> gwMac = Optional.absent();
        String vpnInterfaceSubnetGwMacAddress = null;
        VpnInstanceOpDataEntry vpnInstanceOpData = VpnUtil.getVpnInstanceOpData(dataBroker, primaryRd);
        Long l3vni = vpnInstanceOpData.getL3vni();
        boolean isL3VpnOverVxLan = VpnUtil.isL3VpnOverVxLan(l3vni);
        VrfEntry.EncapType encapType = isL3VpnOverVxLan ? VrfEntry.EncapType.Vxlan : VrfEntry.EncapType.Mplsgre;
        VpnPopulator registeredPopulator = L3vpnRegistry.getRegisteredPopulator(encapType);
        List<Adjacency> nextHops = adjacencies.get().getAdjacency();
        List<Adjacency> value = new ArrayList<>();
        for (Adjacency nextHop : nextHops) {
            String rd = primaryRd;
            Subnetmap sn = VpnUtil.getSubnetmapFromItsUuid(dataBroker, nextHop.getSubnetId());
            if (!VpnUtil.isSubnetPartOfVpn(sn, vpnName)) {
                String prefix = nextHop.getIpAddress() == null ?  "null" :
                      VpnUtil.getIpPrefix(nextHop.getIpAddress());
                LOG.debug("processVpnInterfaceAdjacencies: Not Adding prefix {} to interface {}"
                      + " as the subnet is not part of vpn {}", prefix, interfaceName, vpnName);
                continue;
            }
            if (nextHop.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
                Prefixes.PrefixCue prefixCue = nextHop.isPhysNetworkFunc()
                        ? Prefixes.PrefixCue.PhysNetFunc : Prefixes.PrefixCue.None;
                LOG.debug("processVpnInterfaceAdjacencies: Adding prefix {} to interface {} with nextHops {} on dpn {}"
                        + " for vpn {}", prefix, interfaceName, nhList, dpnId, vpnName);
                writeOperTxn.merge(
                    LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getPrefixToInterfaceIdentifier(
                        VpnUtil.getVpnId(dataBroker, vpnName), prefix),
                    VpnUtil.getPrefixToInterface(dpnId, interfaceName, prefix, nextHop.getSubnetId(),
                            prefixCue), true);
                final Uuid subnetId = nextHop.getSubnetId();
                final Optional<String> gatewayIp = VpnUtil.getVpnSubnetGatewayIp(dataBroker, subnetId);
                if (gatewayIp.isPresent()) {
                    gwMac = getMacAddressForSubnetIp(vpnName, interfaceName, gatewayIp.get());
                    if (gwMac.isPresent()) {
                        // A valid mac-address is available for this subnet-gateway-ip
                        // Use this for programming ARP_RESPONDER table here.  And save this
                        // info into vpnInterface operational, so it can used in VrfEntryProcessor
                        // to populate L3_GW_MAC_TABLE there.
                        arpResponderHandler.addArpResponderFlow(dpnId, lportTag, vpnName, vpnId, interfaceName,
                                subnetId, gatewayIp.get(), gwMac.get());
                        vpnInterfaceSubnetGwMacAddress = gwMac.get();
                    } else {
                        // A valid mac-address is not available for this subnet-gateway-ip
                        // Use the connected-mac-address to configure ARP_RESPONDER Table.
                        // Save this connected-mac-address as gateway-mac-address for the
                        // VrfEntryProcessor to use this later to populate the L3_GW_MAC_TABLE.
                        gwMac = InterfaceUtils.getMacAddressFromInterfaceState(interfaceState);
                        if (gwMac.isPresent()) {
                            VpnUtil.setupGwMacIfExternalVpn(dataBroker, mdsalManager, dpnId, interfaceName,
                                    vpnId, writeInvTxn, NwConstants.ADD_FLOW, interfaceState);
                            arpResponderHandler.addArpResponderFlow(dpnId, lportTag, vpnName, vpnId, interfaceName,
                                    subnetId, gatewayIp.get(), gwMac.get());
                        } else {
                            LOG.error("processVpnInterfaceAdjacencies: Gateway MAC for subnet ID {} could not be "
                                + "obtained, cannot create ARP responder flow for interface name {}, vpnName {}, "
                                + "gwIp {}",
                                interfaceName, vpnName, gatewayIp.get());
                        }
                    }
                } else {
                    LOG.warn("processVpnInterfaceAdjacencies: Gateway IP for subnet ID {} could not be obtained, "
                        + "cannot create ARP responder flow for interface name {}, vpnName {}",
                        subnetId, interfaceName, vpnName);
                }
                LOG.info("processVpnInterfaceAdjacencies: Added prefix {} to interface {} with nextHops {} on dpn {}"
                        + " for vpn {}", prefix, interfaceName, nhList, dpnId, vpnName);
            } else {
                //Extra route adjacency
                String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
                String vpnPrefixKey = VpnUtil.getVpnNamePrefixKey(vpnName, prefix);
                synchronized (vpnPrefixKey.intern()) {
                    java.util.Optional<String> rdToAllocate = VpnUtil
                            .allocateRdForExtraRouteAndUpdateUsedRdsMap(dataBroker, vpnId, null,
                            prefix, vpnName, nextHop.getNextHopIpList().get(0), dpnId, writeOperTxn);
                    if (rdToAllocate.isPresent()) {
                        rd = rdToAllocate.get();
                        LOG.info("processVpnInterfaceAdjacencies: The rd {} is allocated for the extraroute {}",
                            rd, prefix);
                    } else {
                        LOG.error("processVpnInterfaceAdjacencies: No rds to allocate extraroute {}", prefix);
                        continue;
                    }
                }
                LOG.info("processVpnInterfaceAdjacencies: Added prefix {} and nextHopList {} as extra-route for vpn{}"
                        + " interface {} on dpn {}", nextHop.getIpAddress(), nextHop.getNextHopIpList(), vpnName,
                        interfaceName, dpnId);
            }
            // Please note that primary adjacency will use a subnet-gateway-mac-address that
            // can be different from the gateway-mac-address within the VRFEntry as the
            // gateway-mac-address is a superset.
            RouteOrigin origin = nextHop.getAdjacencyType() == AdjacencyType.PrimaryAdjacency ? RouteOrigin.LOCAL
                    : RouteOrigin.STATIC;
            L3vpnInput input = new L3vpnInput().setNextHop(nextHop).setRd(rd).setVpnName(vpnName)
                .setInterfaceName(interfaceName).setNextHopIp(nextHopIp).setPrimaryRd(primaryRd)
                .setSubnetGatewayMacAddress(vpnInterfaceSubnetGwMacAddress)
                .setRouteOrigin(origin);
            Adjacency operationalAdjacency = null;
            try {
                operationalAdjacency = registeredPopulator.createOperationalAdjacency(input);
            } catch (NullPointerException e) {
                LOG.error("processVpnInterfaceAdjacencies: failed to create operational adjacency: input: {}, {}",
                    input, e.getMessage());
                return;
            }
            if (nextHop.getAdjacencyType() != AdjacencyType.PrimaryAdjacency) {
                vpnManager.addExtraRoute(vpnName, nextHop.getIpAddress(), nextHop.getNextHopIpList().get(0), rd,
                        vpnName, operationalAdjacency.getLabel().intValue(), l3vni, origin,
                        interfaceName, operationalAdjacency, encapType, writeConfigTxn);
            }
            value.add(operationalAdjacency);
        }

        AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(value);
        addVpnInterfaceToOperational(vpnName, interfaceName, dpnId, aug, writeOperTxn);

        L3vpnInput input = new L3vpnInput().setNextHopIp(nextHopIp).setL3vni(l3vni).setPrimaryRd(primaryRd)
                .setGatewayMac(gwMac.isPresent() ? gwMac.get() : null).setInterfaceName(interfaceName)
                .setVpnName(vpnName).setDpnId(dpnId).setEncapType(encapType);

        for (Adjacency nextHop : aug.getAdjacency()) {
            // Adjacencies other than primary Adjacencies are handled in the addExtraRoute call above.
            if (nextHop.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                RouteOrigin origin = nextHop.getAdjacencyType() == AdjacencyType.PrimaryAdjacency ? RouteOrigin.LOCAL
                        : RouteOrigin.STATIC;
                input.setNextHop(nextHop).setRd(nextHop.getVrfId()).setRouteOrigin(origin);
                registeredPopulator.populateFib(input, writeConfigTxn, writeOperTxn);
            }
        }
    }

    private void addVpnInterfaceToOperational(String vpnName, String interfaceName, BigInteger dpnId, AdjacenciesOp aug,
            WriteTransaction writeOperTxn) {
        VpnInterfaceOpDataEntry opInterface =
              VpnUtil.getVpnInterfaceOpDataEntry(interfaceName, vpnName, aug, dpnId, Boolean.FALSE);
        InstanceIdentifier<VpnInterfaceOpDataEntry> interfaceId = VpnUtil
            .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
        writeOperTxn.put(LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface,
                WriteTransaction.CREATE_MISSING_PARENTS);
        LOG.info("addVpnInterfaceToOperational: Added VPN Interface {} on dpn {} vpn {} to operational datastore",
                interfaceName, dpnId, vpnName);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateVpnInterfaceOnTepAdd(VpnInterfaceOpDataEntry vpnInterface,
                                           StateTunnelList stateTunnelList,
                                           WriteTransaction writeConfigTxn,
                                           WriteTransaction writeOperTxn) {

        String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        AdjacenciesOp adjacencies = vpnInterface.getAugmentation(AdjacenciesOp.class);
        List<Adjacency> adjList = (adjacencies != null) ? adjacencies.getAdjacency() : new ArrayList<>();
        if (adjList.isEmpty()) {
            LOG.trace("updateVpnInterfaceOnTepAdd: Adjacencies are empty for vpnInterface {} on dpn {}",
                    vpnInterface, srcDpnId);
            return;
        }
        String prefix = null;
        long label = 0;
        boolean isNextHopAddReqd = false;
        String vpnName = vpnInterface.getVpnInstanceName();
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
        LOG.info("updateVpnInterfaceOnTepAdd: AdjacencyList for interface {} on dpn {} vpn {} is {}",
                vpnInterface.getName(), vpnInterface.getDpnId(),
                vpnInterface.getVpnInstanceName(), adjList);
        for (Adjacency adj : adjList) {
            String rd = adj.getVrfId();
            rd = (rd != null) ? rd : vpnName;
            prefix = adj.getIpAddress();
            label = adj.getLabel();
            List<String> nhList = Collections.singletonList(srcTepIp);
            List<String> nextHopList = adj.getNextHopIpList();
            // If TEP is added , update the nexthop of primary adjacency.
            // Secondary adj nexthop is already pointing to primary adj IP address.
            if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                if (!(nextHopList != null && !nextHopList.isEmpty()
                        && nextHopList.get(0).equalsIgnoreCase(srcTepIp))) {
                    isNextHopAddReqd = true;
                    LOG.trace("updateVpnInterfaceOnTepAdd: NextHopList to be updated {} for vpnInterface {} on dpn {} "
                            + "and adjacency {}", nhList, vpnInterface, srcDpnId, adj);
                    InstanceIdentifier<Adjacency> adjId =
                            VpnUtil.getAdjacencyIdentifier(vpnInterface.getName(), prefix);
                    MDSALUtil.syncWrite(dataBroker,  LogicalDatastoreType.OPERATIONAL, adjId,
                            new AdjacencyBuilder(adj).setNextHopIpList(nhList).build());
                }
            } else {
                Optional<VrfEntry> vrfEntryOptional = FibHelper.getVrfEntry(dataBroker, primaryRd, prefix);
                if (!vrfEntryOptional.isPresent()) {
                    continue;
                }
                nhList = FibHelper.getNextHopListFromRoutePaths(vrfEntryOptional.get());
                if (!nhList.contains(srcTepIp)) {
                    nhList.add(srcTepIp);
                    isNextHopAddReqd = true;
                }
            }

            if (isNextHopAddReqd) {
                updateLabelMapper(label, nhList);
                LOG.info("updateVpnInterfaceOnTepAdd: Updated label mapper : label {} dpn {} prefix {} nexthoplist {}"
                        + " vpn {} vpnid {} rd {} interface {}", label, srcDpnId , prefix, nhList,
                        vpnInterface.getVpnInstanceName(), vpnId, rd, vpnInterface.getName());
                // Update the VRF entry with nextHop
                fibManager.updateRoutePathForFibEntry(dataBroker, primaryRd, prefix, srcTepIp, label, true, null);

                //Get the list of VPN's importing this route(prefix) .
                // Then update the VRF entry with nhList
                List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                           VpnUtil.getVpnsImportingMyRoute(dataBroker, vpnName);
                for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                    String vpnRd = vpn.getVrfId();
                    if (vpnRd != null) {
                        fibManager.updateRoutePathForFibEntry(dataBroker, vpnRd, prefix,
                                srcTepIp, label, true, null);
                        LOG.info("updateVpnInterfaceOnTepAdd: Exported route with rd {} prefix {} nhList {} label {}"
                                + " interface {} dpn {} from vpn {} to VPN {} vpnRd {}", rd, prefix, nhList, label,
                            vpnInterface.getName(), srcDpnId, vpnName,
                            vpn.getVpnInstanceName(), vpnRd);
                    }
                }
                // Advertise the prefix to BGP only for external vpn
                // since there is a nexthop change.
                try {
                    if (!rd.equalsIgnoreCase(vpnName)) {
                        bgpManager.advertisePrefix(rd, null /*macAddress*/, prefix, nhList,
                                VrfEntry.EncapType.Mplsgre, (int)label, 0 /*evi*/, 0 /*l2vni*/,
                                null /*gatewayMacAddress*/);
                    }
                    LOG.info("updateVpnInterfaceOnTepAdd: Advertised rd {} prefix {} nhList {} label {}"
                            + " for interface {} on dpn {} vpn {}", rd, prefix, nhList, label, vpnInterface.getName(),
                            srcDpnId, vpnName);
                } catch (Exception ex) {
                    LOG.error("updateVpnInterfaceOnTepAdd: Exception when advertising prefix {} nh {} label {}"
                            + " on rd {} for interface {} on dpn {} vpn {} as {}", prefix, nhList, label, rd,
                            vpnInterface.getName(), srcDpnId,
                            vpnName, ex);
                }
            }
            LOG.info("updateVpnInterfaceOnTepAdd: interface {} updated successully on tep add on dpn {} vpn {}",
                    vpnInterface.getName(), srcDpnId, vpnName);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateVpnInterfaceOnTepDelete(VpnInterfaceOpDataEntry vpnInterface,
                                              StateTunnelList stateTunnelList,
                                              WriteTransaction writeConfigTxn,
                                              WriteTransaction writeOperTxn) {

        AdjacenciesOp adjacencies = vpnInterface.getAugmentation(AdjacenciesOp.class);
        List<Adjacency> adjList = (adjacencies != null) ? adjacencies.getAdjacency() : new ArrayList<>();
        String prefix = null;
        long label = 0;
        boolean isNextHopRemoveReqd = false;
        String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        String vpnName = vpnInterface.getVpnInstanceName();
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        String primaryRd = VpnUtil.getVpnRd(dataBroker, vpnName);
        if (adjList != null) {
            LOG.info("updateVpnInterfaceOnTepDelete: AdjacencyList for interface {} on dpn {} vpn {} is {}",
                    vpnInterface.getName(), vpnInterface.getDpnId(),
                    vpnInterface.getVpnInstanceName(), adjList);
            for (Adjacency adj : adjList) {
                List<String> nhList = new ArrayList<>();
                String rd = adj.getVrfId();
                rd = rd != null ? rd : vpnName;
                prefix = adj.getIpAddress();
                label = adj.getLabel();
                // If TEP is deleted , remove the nexthop from primary adjacency.
                // Secondary adj nexthop will continue to point to primary adj IP address.
                if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                    List<String> nextHopList = adj.getNextHopIpList();
                    if (nextHopList != null && !nextHopList.isEmpty()) {
                        isNextHopRemoveReqd = true;
                        InstanceIdentifier<Adjacency> adjId =
                                VpnUtil.getAdjacencyIdentifier(vpnInterface.getName(), prefix);
                        MDSALUtil.syncWrite(dataBroker,  LogicalDatastoreType.OPERATIONAL, adjId,
                                new AdjacencyBuilder(adj).setNextHopIpList(nhList).build());
                    }
                } else {
                    Optional<VrfEntry> vrfEntryOptional = FibHelper.getVrfEntry(dataBroker, primaryRd, prefix);
                    if (!vrfEntryOptional.isPresent()) {
                        continue;
                    }
                    nhList = FibHelper.getNextHopListFromRoutePaths(vrfEntryOptional.get());
                    if (nhList.contains(srcTepIp)) {
                        nhList.remove(srcTepIp);
                        isNextHopRemoveReqd = true;
                    }
                }

                if (isNextHopRemoveReqd) {
                    updateLabelMapper(label, nhList);
                    LOG.info("updateVpnInterfaceOnTepDelete: Updated label mapper : label {} dpn {} prefix {}"
                            + " nexthoplist {} vpn {} vpnid {} rd {} interface {}", label, srcDpnId,
                            prefix, nhList, vpnName,
                            vpnId, rd, vpnInterface.getName());
                    // Update the VRF entry with removed nextHop
                    fibManager.updateRoutePathForFibEntry(dataBroker, primaryRd, prefix, srcTepIp, label, false, null);

                    //Get the list of VPN's importing this route(prefix) .
                    // Then update the VRF entry with nhList
                    List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                          VpnUtil.getVpnsImportingMyRoute(dataBroker, vpnName);
                    for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                        String vpnRd = vpn.getVrfId();
                        if (vpnRd != null) {
                            fibManager.updateRoutePathForFibEntry(dataBroker, vpnRd, prefix,
                                    srcTepIp, label, false, null);
                            LOG.info("updateVpnInterfaceOnTepDelete: Exported route with rd {} prefix {} nhList {}"
                                    + " label {} interface {} dpn {} from vpn {} to VPN {} vpnRd {}", rd, prefix,
                                    nhList, label, vpnInterface.getName(), srcDpnId,
                                    vpnName,
                                    vpn.getVpnInstanceName(), vpnRd);
                        }
                    }

                    // Withdraw prefix from BGP only for external vpn.
                    try {
                        if (!rd.equalsIgnoreCase(vpnName)) {
                            bgpManager.withdrawPrefix(rd, prefix);
                        }
                        LOG.info("updateVpnInterfaceOnTepDelete: Withdrawn rd {} prefix {} nhList {} label {}"
                                + " for interface {} on dpn {} vpn {}", rd, prefix, nhList, label,
                                vpnInterface.getName(), srcDpnId,
                                vpnName);
                    } catch (Exception ex) {
                        LOG.error("updateVpnInterfaceOnTepDelete: Exception when withdrawing prefix {} nh {} label {}"
                                + " on rd {} for interface {} on dpn {} vpn {} as {}", prefix, nhList, label, rd,
                                vpnInterface.getName(), srcDpnId,
                                vpnName, ex);
                    }
                }
            }
            LOG.info("updateVpnInterfaceOnTepDelete: interface {} updated successully on tep delete on dpn {} vpn {}",
                         vpnInterface.getName(), srcDpnId, vpnName);
        }
    }

    private List<VpnInstanceOpDataEntry> getVpnsExportingMyRoute(final String vpnName) {
        List<VpnInstanceOpDataEntry> vpnsToExportRoute = new ArrayList<>();

        String vpnRd = VpnUtil.getVpnRd(dataBroker, vpnName);
        final VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(dataBroker, vpnRd);
        if (vpnInstanceOpDataEntry == null) {
            LOG.debug("getVpnsExportingMyRoute: Could not retrieve vpn instance op data for {}"
                    + " to check for vpns exporting the routes", vpnName);
            return vpnsToExportRoute;
        }

        Predicate<VpnInstanceOpDataEntry> excludeVpn = input -> {
            if (input.getVpnInstanceName() == null) {
                LOG.error("getVpnsExportingMyRoute.excludeVpn: Received vpn instance with rd {}  without a name",
                        input.getVrfId());
                return false;
            }
            return !input.getVpnInstanceName().equals(vpnName);
        };

        Predicate<VpnInstanceOpDataEntry> matchRTs = input -> {
            Iterable<String> commonRTs =
                    VpnUtil.intersection(VpnUtil.getRts(vpnInstanceOpDataEntry, VpnTarget.VrfRTType.ImportExtcommunity),
                        VpnUtil.getRts(input, VpnTarget.VrfRTType.ExportExtcommunity));
            return Iterators.size(commonRTs.iterator()) > 0;
        };

        vpnsToExportRoute =
                VpnUtil.getAllVpnInstanceOpData(dataBroker).stream().filter(excludeVpn).filter(matchRTs).collect(
                        Collectors.toList());
        return vpnsToExportRoute;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    void handleVpnsExportingRoutes(String vpnName, String vpnRd) {
        List<VpnInstanceOpDataEntry> vpnsToExportRoute = getVpnsExportingMyRoute(vpnName);
        for (VpnInstanceOpDataEntry vpn : vpnsToExportRoute) {
            List<VrfEntry> vrfEntries = VpnUtil.getAllVrfEntries(dataBroker, vpn.getVrfId());
            WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
            if (vrfEntries != null) {
                for (VrfEntry vrfEntry : vrfEntries) {
                    try {
                        if (!FibHelper.isControllerManagedNonInterVpnLinkRoute(
                                RouteOrigin.value(vrfEntry.getOrigin()))) {
                            LOG.info("handleVpnsExportingRoutes: vrfEntry with rd {} prefix {}"
                                    + " is not a controller managed non intervpn link route. Ignoring.",
                                    vpn.getVrfId(), vrfEntry.getDestPrefix());
                            continue;
                        }
                        String prefix = vrfEntry.getDestPrefix();
                        String gwMac = vrfEntry.getGatewayMacAddress();
                        vrfEntry.getRoutePaths().forEach(routePath -> {
                            String nh = routePath.getNexthopAddress();
                            int label = routePath.getLabel().intValue();
                            if (FibHelper.isControllerManagedVpnInterfaceRoute(RouteOrigin.value(
                                    vrfEntry.getOrigin()))) {
                                LOG.info("handleVpnsExportingRoutesImporting: Importing fib entry rd {} prefix {}"
                                        + " nexthop {} label {} to vpn {} vpnRd {}", vpn.getVrfId(), prefix, nh, label,
                                        vpnName, vpnRd);
                                fibManager.addOrUpdateFibEntry(dataBroker, vpnRd, null /*macAddress*/, prefix,
                                        Collections.singletonList(nh), VrfEntry.EncapType.Mplsgre, label,
                                        0 /*l3vni*/, gwMac,  null /*parentVpnRd*/, RouteOrigin.SELF_IMPORTED,
                                        writeConfigTxn);
                            } else {
                                LOG.info("handleVpnsExportingRoutes: Importing subnet route fib entry rd {} prefix {}"
                                        + " nexthop {} label {} to vpn {} vpnRd {}", vpn.getVrfId(), prefix, nh, label,
                                        vpnName, vpnRd);
                                SubnetRoute route = vrfEntry.getAugmentation(SubnetRoute.class);
                                importSubnetRouteForNewVpn(vpnRd, prefix, nh, label, route, writeConfigTxn);
                            }
                        });
                    } catch (RuntimeException e) {
                        LOG.error("getNextHopAddressList: Exception occurred while importing route with rd {}"
                                + " prefix {} routePaths {} to vpn {} vpnRd {}", vpn.getVrfId(),
                                vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(), vpnName, vpnRd);
                    }
                }
                writeConfigTxn.submit();
            } else {
                LOG.info("getNextHopAddressList: No vrf entries to import from vpn {} with rd {} to vpn {} with rd {}",
                        vpn.getVpnInstanceName(), vpn.getVrfId(), vpnName, vpnRd);
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void remove(InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();
        for (VpnInstanceNames vpnInterfaceVpnInstance : vpnInterface.getVpnInstanceNames()) {
            String vpnName = vpnInterfaceVpnInstance.getVpnName();
            removeVpnInterfaceCall(identifier, vpnInterface, vpnName, interfaceName);
        }
    }

    private void removeVpnInterfaceCall(final InstanceIdentifier<VpnInterface> identifier,
                                final VpnInterface vpnInterface, final String vpnName,
                                final String interfaceName) {
        Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
        if (interfaceState != null) {
            removeVpnInterfaceFromVpn(identifier, vpnInterface, vpnName, interfaceName);
        } else if (Boolean.TRUE.equals(vpnInterface.isRouterInterface())) {
            jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterface.getName() + vpnName, () -> {
                WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                deleteFibEntryForRouterInterface(vpnInterface, writeConfigTxn, vpnName);
                LOG.info("remove: Router interface {} for vpn {}", interfaceName, vpnName);
                ListenableFuture<Void> futures = writeConfigTxn.submit();
                String errorText = "removeVpnInterfaceCall: Exception encountered while submitting writeConfigTxn"
                     + " for interface " + vpnInterface.getName() + " on vpn " + vpnName;
                ListenableFutures.addErrorLogging(futures, LOG, errorText);
                return Collections.singletonList(futures);
            });
        } else {
            LOG.error("remove: VPN interface {} on dpn {} for vpn {} was unavailable in operational data "
                    + "store to handle remove event", interfaceName, vpnInterface.getDpnId(), vpnName);
        }
    }

    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
    private void removeVpnInterfaceFromVpn(final InstanceIdentifier<VpnInterface> identifier,
                                final VpnInterface vpnInterface, final String vpnName,
                                final String interfaceName) {
        LOG.info("remove: VPN Interface remove event - intfName {} vpn {} dpn {}" ,vpnInterface.getName(),
                vpnName, vpnInterface.getDpnId());
        removeInterfaceFromUnprocessedList(identifier, vpnInterface);
        Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
        if (interfaceState != null) {
            BigInteger dpId = BigInteger.ZERO;
            try {
                dpId = InterfaceUtils.getDpIdFromInterface(interfaceState);
            } catch (NumberFormatException | IllegalStateException e) {
                LOG.error("remove: Unable to retrieve dpnId from interface operational data store for interface {}"
                        + " on dpn {} for vpn {} Fetching from vpn interface op data store. ", interfaceName,
                        vpnInterface.getDpnId(), vpnName, e);
                dpId = BigInteger.ZERO;
            }
            final int ifIndex = interfaceState.getIfIndex();
            final BigInteger dpnId = dpId;
            jobCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName + vpnName,
                () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeConfigTxn -> {
                        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeOperTxn -> {
                            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeInvTxn -> {
                                LOG.info("remove: - intfName {} onto vpnName {} running config-driven", interfaceName,
                                        vpnName);
                                InstanceIdentifier<VpnInterfaceOpDataEntry> interfaceId =
                                        VpnUtil.getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                                final Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                                        VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, interfaceId);
                                if (optVpnInterface.isPresent()) {
                                    VpnInterfaceOpDataEntry vpnOpInterface = optVpnInterface.get();
                                    BigInteger finalDpnId =
                                            dpnId.equals(BigInteger.ZERO) ? vpnOpInterface.getDpnId() : dpnId;
                                    processVpnInterfaceDown(finalDpnId, interfaceName, ifIndex, interfaceState,
                                            vpnOpInterface, false, writeConfigTxn, writeOperTxn, writeInvTxn);
                                    LOG.info(
                                            "remove: Removal of vpn interface {} on dpn {} for vpn {} processed "
                                                    + "successfully",
                                            interfaceName, vpnInterface.getDpnId(), vpnName);
                                } else {
                                    LOG.warn(
                                            "remove: VPN interface {} on dpn {} for vpn {} was unavailable in "
                                                    + "operational data store to handle remove event",
                                            interfaceName, vpnInterface.getDpnId(), vpnName);
                                }
                            }));
                        }));
                    }));

                    return futures;
                });
        }
    }

    protected void processVpnInterfaceDown(BigInteger dpId,
                                           String interfaceName,
                                           int lportTag,
                                           Interface interfaceState,
                                           VpnInterfaceOpDataEntry vpnOpInterface,
                                           boolean isInterfaceStateDown,
                                           WriteTransaction writeConfigTxn,
                                           WriteTransaction writeOperTxn,
                                           WriteTransaction writeInvTxn) {
        if (vpnOpInterface == null) {
            LOG.error("processVpnInterfaceDown: Unable to process delete/down for interface {} on dpn {}"
                    + " as it is not available in operational data store", interfaceName, dpId);
            return;
        }
        final String vpnName = vpnOpInterface.getVpnInstanceName();
        InstanceIdentifier<VpnInterfaceOpDataEntry> identifier = VpnUtil.getVpnInterfaceOpDataEntryIdentifier(
                                                    interfaceName, vpnName);
        if (!isInterfaceStateDown) {
            final long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            if (!vpnOpInterface.isScheduledForRemove()) {
                VpnUtil.scheduleVpnInterfaceForRemoval(dataBroker, interfaceName, dpId, vpnName, Boolean.TRUE,
                        null);
                final boolean isBgpVpnInternetVpn = VpnUtil.isBgpVpnInternet(dataBroker, vpnName);
                removeAdjacenciesFromVpn(dpId, lportTag, interfaceName, vpnName,
                                 vpnId, writeConfigTxn, writeOperTxn, writeInvTxn, interfaceState);
                if (interfaceManager.isExternalInterface(interfaceName)) {
                    processExternalVpnInterface(interfaceName, vpnName, vpnId, dpId, lportTag, writeInvTxn,
                            NwConstants.DEL_FLOW);
                }
                if (!isBgpVpnInternetVpn) {
                    VpnUtil.unbindService(dataBroker, interfaceName, isInterfaceStateDown, jobCoordinator);
                }
                LOG.info("processVpnInterfaceDown: Unbound vpn service from interface {} on dpn {} for vpn {}"
                        + " successful", interfaceName, dpId, vpnName);
            } else {
                LOG.info("processVpnInterfaceDown: Unbinding vpn service for interface {} on dpn for vpn {}"
                        + " has already been scheduled by a different event ", interfaceName, dpId,
                        vpnName);
                return;
            }
        } else {
            // Interface is retained in the DPN, but its Link Down.
            // Only withdraw the prefixes for this interface from BGP
            withdrawAdjacenciesForVpnFromBgp(identifier, vpnName, interfaceName, writeConfigTxn);
        }
    }

    private void removeAdjacenciesFromVpn(final BigInteger dpnId, final int lportTag, final String interfaceName,
                                          final String vpnName, final long vpnId, WriteTransaction writeConfigTxn,
                                          final WriteTransaction writeOperTxn,
                                          final WriteTransaction writeInvTxn, Interface interfaceState) {
        //Read NextHops
        InstanceIdentifier<VpnInterfaceOpDataEntry> identifier = VpnUtil
                .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
        InstanceIdentifier<AdjacenciesOp> path = identifier.augmentation(AdjacenciesOp.class);
        Optional<AdjacenciesOp> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);

        String primaryRd = VpnUtil.getVpnRd(dataBroker, vpnName);
        LOG.info("removeAdjacenciesFromVpn: For interface {} on dpn {} RD recovered for vpn {} as rd {}",
                interfaceName, dpnId, vpnName, primaryRd);
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();
            if (!nextHops.isEmpty()) {
                LOG.info("removeAdjacenciesFromVpn: NextHops for interface {} on dpn {} for vpn {} are {}",
                        interfaceName, dpnId, vpnName, nextHops);
                for (Adjacency nextHop : nextHops) {
                    if (nextHop.isPhysNetworkFunc()) {
                        LOG.info("removeAdjacenciesFromVpn: Removing PNF FIB entry rd {} prefix {}",
                                nextHop.getSubnetId().getValue(), nextHop.getIpAddress());
                        fibManager.removeFibEntry(dataBroker, nextHop.getSubnetId().getValue(), nextHop.getIpAddress(),
                                null/*writeCfgTxn*/);
                    } else {
                        String rd = nextHop.getVrfId();
                        List<String> nhList = Collections.emptyList();
                        if (nextHop.getAdjacencyType() != AdjacencyType.PrimaryAdjacency) {
                            // This is either an extra-route (or) a learned IP via subnet-route
                            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
                            if (nextHopIp == null || nextHopIp.isEmpty()) {
                                LOG.error("removeAdjacenciesFromVpn: Unable to obtain nextHopIp for"
                                                + " extra-route/learned-route in rd {} prefix {} interface {} on dpn {}"
                                                + " for vpn {}", rd, nextHop.getIpAddress(), interfaceName, dpnId,
                                        vpnName);
                            } else {
                                nhList = Collections.singletonList(nextHopIp);
                            }
                        } else {
                            // This is a primary adjacency
                            nhList = nextHop.getNextHopIpList() != null ? nextHop.getNextHopIpList()
                                    : Collections.emptyList();
                            final Uuid subnetId = nextHop.getSubnetId();
                            if (nextHop.getSubnetGatewayMacAddress() == null) {
                                // A valid mac-address was not available for this subnet-gateway-ip
                                // So a connected-mac-address was used for this subnet and we need
                                // to remove the flows for the same here from the L3_GW_MAC_TABLE.
                                VpnUtil.setupGwMacIfExternalVpn(dataBroker, mdsalManager, dpnId, interfaceName,
                                        vpnId, writeInvTxn, NwConstants.DEL_FLOW, interfaceState);

                            }
                            arpResponderHandler.removeArpResponderFlow(dpnId, lportTag, interfaceName, vpnName, vpnId,
                                    subnetId);
                        }

                        if (!nhList.isEmpty()) {
                            if (rd.equals(vpnName)) {
                                //this is an internal vpn - the rd is assigned to the vpn instance name;
                                //remove from FIB directly
                                for (String nh : nhList) {
                                    fibManager.removeOrUpdateFibEntry(dataBroker, vpnName, nextHop.getIpAddress(), nh,
                                            writeConfigTxn);
                                    LOG.info("removeAdjacenciesFromVpn: removed/updated FIB with rd {} prefix {}"
                                                    + " nexthop {} for interface {} on dpn {} for internal vpn {}",
                                            vpnName, nextHop.getIpAddress(), nh, interfaceName, dpnId, vpnName);
                                }
                            } else {
                                List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                                        VpnUtil.getVpnsImportingMyRoute(dataBroker, vpnName);
                                for (String nh : nhList) {
                                    //IRT: remove routes from other vpns importing it
                                    vpnManager.removePrefixFromBGP(primaryRd, rd, vpnName, nextHop.getIpAddress(),
                                            nextHop.getNextHopIpList().get(0), nh, dpnId, writeConfigTxn);
                                    for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                                        String vpnRd = vpn.getVrfId();
                                        if (vpnRd != null) {
                                            fibManager.removeOrUpdateFibEntry(dataBroker, vpnRd,
                                                    nextHop.getIpAddress(), nh, writeConfigTxn);
                                            LOG.info("removeAdjacenciesFromVpn: Removed Exported route with rd {}"
                                                            + " prefix {} nextHop {} from VPN {} parentVpn {}"
                                                    + " for interface {} on dpn {}", vpnRd, nextHop.getIpAddress(), nh,
                                                    vpn.getVpnInstanceName(), vpnName, interfaceName, dpnId);
                                        }
                                    }
                                }
                            }
                        } else {
                            fibManager.removeFibEntry(dataBroker, primaryRd, nextHop.getIpAddress(), writeConfigTxn);
                        }
                    }

                    String ip = nextHop.getIpAddress().split("/")[0];
                    LearntVpnVipToPort vpnVipToPort = VpnUtil.getLearntVpnVipToPort(dataBroker, vpnName, ip);
                    if (vpnVipToPort != null) {
                        VpnUtil.removeLearntVpnVipToPort(dataBroker, vpnName, ip);
                        LOG.info("removeAdjacenciesFromVpn: VpnInterfaceManager removed adjacency for Interface {}"
                                + " ip {} on dpn {} for vpn {} from VpnPortData Entry", vpnVipToPort.getPortName(),
                                ip, dpnId, vpnName);
                    }
                }
            }
        } else {
            // this vpn interface has no more adjacency left, so clean up the vpn interface from Operational DS
            LOG.info("Clean up vpn interface {} from dpn {} to vpn {} list.", interfaceName, dpnId, primaryRd);
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, identifier);
        }
    }

    private Optional<String> getMacAddressForSubnetIp(String vpnName, String ifName, String ipAddress) {
        VpnPortipToPort gwPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnName, ipAddress);
        //Check if a router gateway interface is available for the subnet gw is so then use Router interface
        // else use connected interface
        if (gwPort != null && gwPort.isSubnetIp()) {
            LOG.info("getGatewayMacAddressForSubnetIp: Retrieved gw Mac as {} for ip {} interface {} vpn {}",
                    gwPort.getMacAddress(), ipAddress, ifName, vpnName);
            return Optional.of(gwPort.getMacAddress());
        }
        return Optional.absent();
    }

    @Override
    protected void update(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface original,
        final VpnInterface update) {
        LOG.info("update: VPN Interface update event - intfName {} on dpn {} oldVpn {} newVpn {}" ,update.getName(),
                update.getDpnId(), original.getVpnInstanceNames(),
                update.getVpnInstanceNames());
        final String vpnInterfaceName = update.getName();
        final BigInteger dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        final Adjacencies origAdjs = original.getAugmentation(Adjacencies.class);
        final List<Adjacency> oldAdjs = origAdjs != null && origAdjs.getAdjacency()
            != null ? origAdjs.getAdjacency() : new ArrayList<>();
        final Adjacencies updateAdjs = update.getAugmentation(Adjacencies.class);
        final List<Adjacency> newAdjs = updateAdjs != null && updateAdjs.getAdjacency()
            != null ? updateAdjs.getAdjacency() : new ArrayList<>();

        LOG.info("VPN Interface update event - intfName {}", vpnInterfaceName);
        //handles switching between <internal VPN - external VPN>
        for (VpnInstanceNames vpnInterfaceVpnInstance : original.getVpnInstanceNames()) {
            String oldVpnName = vpnInterfaceVpnInstance.getVpnName();
            if (oldVpnName != null && (update.getVpnInstanceNames() == null
                || !VpnHelper.doesVpnInterfaceBelongToVpnInstance(oldVpnName, update.getVpnInstanceNames()))) {
                UpdateData updateData = new UpdateData(identifier, original, update);
                vpnInterfacesUpdateQueue.add(updateData);
                LOG.info("update: UpdateData on VPNInterface {} on dpn {} update upon VPN swap from oldVpn(s) {}"
                        + "to newVpn(s) {} added to update queue",
                        updateData.getOriginal().getName(), dpnId,
                        VpnHelper.getVpnInterfaceVpnInstanceNamesString(original.getVpnInstanceNames()),
                        VpnHelper.getVpnInterfaceVpnInstanceNamesString(update.getVpnInstanceNames()));
                return;
            }
        }
        for (VpnInstanceNames vpnInterfaceVpnInstance : update.getVpnInstanceNames()) {
            String newVpnName = vpnInterfaceVpnInstance.getVpnName();
            String primaryRd = VpnUtil.getPrimaryRd(dataBroker, newVpnName);
            if (newVpnName != null && (original.getVpnInstanceNames() == null
                || !VpnHelper.doesVpnInterfaceBelongToVpnInstance(newVpnName,
                                original.getVpnInstanceNames()))) {
                if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                    InstanceIdentifier<VpnInterfaceOpDataEntry> opIdentifier = VpnUtil
                        .getVpnInterfaceOpDataEntryIdentifier(vpnInterfaceName, newVpnName);
                    if (canHandleNewVpnInterface(identifier, update, newVpnName)) {
                        List<Adjacency> copyNewAdjs = new ArrayList<Adjacency>(newAdjs);
                        List<Adjacency> copyOldAdjs = new ArrayList<Adjacency>(oldAdjs);
                        addVpnInterfaceToVpn(opIdentifier, update, copyOldAdjs, copyNewAdjs, identifier, newVpnName);
                    }
                } else {
                    UpdateData updateData = new UpdateData(identifier, original, update);
                    vpnInterfacesUpdateQueue.add(updateData);
                    LOG.info("update: UpdateData on VPNInterface {} on dpn {} update upon VPN swap from oldVpn(s) {}"
                            + "to newVpn(s) {} added to update queue",
                            updateData.getOriginal().getName(), dpnId,
                            VpnHelper.getVpnInterfaceVpnInstanceNamesString(original.getVpnInstanceNames()),
                            VpnHelper.getVpnInterfaceVpnInstanceNamesString(update.getVpnInstanceNames()));
                    return;
                }
            }
        }
        for (VpnInstanceNames vpnInterfaceVpnInstance : update.getVpnInstanceNames()) {
            String newVpnName = vpnInterfaceVpnInstance.getVpnName();
            List<Adjacency> copyNewAdjs = new ArrayList<Adjacency>(newAdjs);
            List<Adjacency> copyOldAdjs = new ArrayList<Adjacency>(oldAdjs);
            String primaryRd = VpnUtil.getPrimaryRd(dataBroker, newVpnName);
            if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterfaceName + newVpnName, () -> {
                    WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                    WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                    InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier =
                        VpnUtil.getVpnInterfaceOpDataEntryIdentifier(vpnInterfaceName, newVpnName);
                    LOG.info("VPN Interface update event - intfName {} onto vpnName {} running config-driven",
                            update.getName(), newVpnName);
                    //handle both addition and removal of adjacencies
                    //currently, new adjacency may be an extra route
                    boolean isBgpVpnInternetVpn = VpnUtil.isBgpVpnInternet(dataBroker, newVpnName);
                    if (!oldAdjs.equals(newAdjs)) {
                        for (Adjacency adj : copyNewAdjs) {
                            if (copyOldAdjs.contains(adj)) {
                                copyOldAdjs.remove(adj);
                            } else {
                                // add new adjacency - right now only extra route will hit this path
                                if (!isBgpVpnInternetVpn || VpnUtil.isAdjacencyEligibleToVpnInternet(dataBroker, adj)) {
                                    addNewAdjToVpnInterface(vpnInterfaceOpIdentifier, primaryRd, adj,
                                             dpnId, writeOperTxn, writeConfigTxn);
                                }
                                LOG.info("update: new Adjacency {} with nextHop {} label {} subnet {} added to vpn "
                                                + "interface {} on vpn {} dpnId {}",
                                        adj.getIpAddress(), adj.getNextHopIpList(),
                                        adj.getLabel(), adj.getSubnetId(), update.getName(),
                                        newVpnName, dpnId);
                            }
                        }
                        for (Adjacency adj : copyOldAdjs) {
                            if (!isBgpVpnInternetVpn || VpnUtil.isAdjacencyEligibleToVpnInternet(dataBroker, adj)) {
                                delAdjFromVpnInterface(vpnInterfaceOpIdentifier, adj, dpnId,
                                       writeOperTxn, writeConfigTxn);
                            }
                            LOG.info("update: Adjacency {} with nextHop {} label {} subnet {} removed from"
                                + " vpn interface {} on vpn {}", adj.getIpAddress(), adj.getNextHopIpList(),
                                adj.getLabel(), adj.getSubnetId(), update.getName(), newVpnName);
                        }
                    }
                    ListenableFuture<Void> operFuture = writeOperTxn.submit();
                    try {
                        operFuture.get();
                    } catch (ExecutionException e) {
                        LOG.error("Exception encountered while submitting operational future for update"
                                + " VpnInterface {} on vpn {}: {}", vpnInterfaceName, newVpnName, e);
                        return null;
                    }
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(writeConfigTxn.submit());
                    LOG.info("update: vpn interface updated for interface {} oldVpn(s) {} newVpn {}"
                        + "processed successfully", update.getName(),
                        VpnHelper.getVpnInterfaceVpnInstanceNamesString(original.getVpnInstanceNames()), newVpnName);
                    return futures;
                });
            } else {
                LOG.error("update: Ignoring update of vpnInterface {}, as newVpnInstance {} with primaryRd {}"
                        + " is already marked for deletion", vpnInterfaceName, newVpnName, primaryRd);
            }
        }
    }

    class VpnInterfaceUpdateTimerTask extends TimerTask {
        private final Logger log = LoggerFactory.getLogger(VpnInterfaceUpdateTimerTask.class);

        @Override
        public void run() {
            List<UpdateData> processQueue = new ArrayList<>();
            List<UpdateData> updateDataList = new ArrayList<>();
            vpnInterfacesUpdateQueue.drainTo(processQueue);
            int maxInterfaceList = 0;

            for (UpdateData updData : processQueue) {
                final VpnInterfaceKey key = updData.getIdentifier()
                    .firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
                final String interfaceName = key.getName();
                Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
                for (VpnInstanceNames vpnInterfaceVpnInstance : updData.getOriginal().getVpnInstanceNames()) {
                    String oldVpnName = vpnInterfaceVpnInstance.getVpnName();
                    if (updData.getUpdate().getVpnInstanceNames() != null
                        && VpnHelper.doesVpnInterfaceBelongToVpnInstance(oldVpnName,
                                    updData.getUpdate().getVpnInstanceNames())) {
                        continue;
                    }
                    log.info("run: VPN Interface update event - intfName {} remove vpnName {} running"
                        + " config-driven swap removal", updData.getOriginal().getName(), oldVpnName);
                    maxInterfaceList ++;
                    removeVpnInterfaceCall(updData.getIdentifier(), updData.getOriginal(),
                                       oldVpnName, interfaceName);
                    log.info("run: Processed Remove for update on VPNInterface {} upon VPN swap from old vpn {}"
                            + " to newVpn(s) {}", updData.getOriginal().getName(),
                            oldVpnName, VpnHelper.getVpnInterfaceVpnInstanceNamesString(updData
                                       .getUpdate().getVpnInstanceNames()));
                }
                updateDataList.add(updData);
            }
            /* Decide the max-wait time based on number of VpnInterfaces.
            *  max-wait-time is num-of-interface * 4seconds (random choice).
            *  Every 2sec poll VpnToDpnList. If VpnInterface is removed ,
            *  remove it from vpnInterfaceList.
            */
            int maxWaitTime =
                maxInterfaceList * (int) (VpnConstants.PER_INTERFACE_MAX_WAIT_TIME_IN_MILLISECONDS / 1000);
            int waitTime = 2;
            Iterator<UpdateData> updateDataIterator = updateDataList.iterator();
            UpdateData updateDataSet;
            while (waitTime < maxWaitTime) {
                try {
                    Thread.sleep(2000); // sleep for 2sec
                } catch (InterruptedException e) {
                    // Ignored
                }

                while (updateDataIterator.hasNext()) {
                    boolean interfaceIsRemoved = true;
                    updateDataSet = updateDataIterator.next();
                    for (VpnInstanceNames vpnInterfaceVpnInstance : updateDataSet.getOriginal().getVpnInstanceNames()) {
                        String oldVpnName = vpnInterfaceVpnInstance.getVpnName();
                        if (oldVpnName != null && updateDataSet.getUpdate().getVpnInstanceNames() != null
                            && VpnHelper.doesVpnInterfaceBelongToVpnInstance(oldVpnName,
                                    updateDataSet.getUpdate().getVpnInstanceNames())) {
                            continue;
                        }
                        if (VpnUtil.isVpnIntfPresentInVpnToDpnList(dataBroker,
                                 updateDataSet.getOriginal(), oldVpnName)) {
                            interfaceIsRemoved = false;
                        }
                    }
                    if (interfaceIsRemoved) {
                        updateDataIterator.remove();
                    }
                }
                if (updateDataList.isEmpty()) {
                    log.info("run: All VpnInterfaces are successfully removed from OLD VPN after time {}", waitTime);
                    break;
                }
                waitTime += 2; //Increment linearly by 2sec.
            }

            if (updateDataList.size() > 0) {
                log.error("run: VpnInterfacesList not removed from old Vpn even after waiting {}", waitTime);
            }
            for (UpdateData updData : processQueue) {
                if (updateDataList.contains(updData)) {
                    log.error("run: Failed to swap VpnInterface {} from oldVpn {} to target VPN {}"
                             + "as it has not been cleaned up from the oldVpn", updData.getOriginal().getName(),
                             VpnHelper.getVpnInterfaceVpnInstanceNamesString(updData.getOriginal()
                                            .getVpnInstanceNames()),
                             VpnHelper.getVpnInterfaceVpnInstanceNamesString(updData.getUpdate()
                                            .getVpnInstanceNames()));
                    continue;
                }
                for (VpnInstanceNames vpnInterfaceVpnInstance : updData.getUpdate().getVpnInstanceNames()) {
                    String newVpnName = vpnInterfaceVpnInstance.getVpnName();
                    if (updData.getOriginal().getVpnInstanceNames() != null
                        && VpnHelper.doesVpnInterfaceBelongToVpnInstance(newVpnName,
                                              updData.getOriginal().getVpnInstanceNames())) {
                        continue;
                    }
                    log.info("VPN Interface update event - intfName {} onto vpnName {} running config-driven"
                           + " swap addition", updData.getUpdate().getName(), newVpnName);
                    final Adjacencies origAdjs = updData.getOriginal().getAugmentation(Adjacencies.class);
                    final List<Adjacency> oldAdjs = (origAdjs != null && origAdjs.getAdjacency() != null)
                        ? origAdjs.getAdjacency() : new ArrayList<>();
                    final Adjacencies updateAdjs = updData.getUpdate().getAugmentation(Adjacencies.class);
                    final List<Adjacency> newAdjs = (updateAdjs != null && updateAdjs.getAdjacency() != null)
                        ? updateAdjs.getAdjacency() : new ArrayList<>();

                    addVpnInterfaceCall(updData.getIdentifier(), updData.getUpdate(),
                                      oldAdjs, newAdjs, newVpnName);
                    log.info("run: Processed Add for update on VPNInterface {} from oldVpn(s) {} to newVpn {}"
                            + " upon VPN swap", updData.getUpdate().getName(),
                            VpnHelper.getVpnInterfaceVpnInstanceNamesString(updData.getOriginal()
                                       .getVpnInstanceNames()), newVpnName);
                }
            }
        }
    }

    private void updateLabelMapper(Long label, List<String> nextHopIpList) {
        Preconditions.checkNotNull(label, "updateLabelMapper: label cannot be null or empty!");
        synchronized (label.toString().intern()) {
            InstanceIdentifier<LabelRouteInfo> lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
                    .child(LabelRouteInfo.class, new LabelRouteInfoKey(label)).build();
            Optional<LabelRouteInfo> opResult = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, lriIid);
            if (opResult.isPresent()) {
                LabelRouteInfo labelRouteInfo =
                    new LabelRouteInfoBuilder(opResult.get()).setNextHopIpList(nextHopIpList).build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, lriIid, labelRouteInfo);
            }
        }
        LOG.info("updateLabelMapper: Updated label rotue info for label {} with nextHopList {}", label, nextHopIpList);
    }

    public synchronized void importSubnetRouteForNewVpn(String rd, String prefix, String nextHop, int label,
        SubnetRoute route, WriteTransaction writeConfigTxn) {

        RouteOrigin origin = RouteOrigin.SELF_IMPORTED;
        VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder(prefix, label, nextHop, origin, null /* parentVpnRd */)
                .addAugmentation(SubnetRoute.class, route).build();
        List<VrfEntry> vrfEntryList = Collections.singletonList(vrfEntry);
        InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
        VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).setVrfEntry(vrfEntryList).build();
        if (writeConfigTxn != null) {
            writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew, true);
        } else {
            VpnUtil.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
        }
        LOG.info("SUBNETROUTE: importSubnetRouteForNewVpn: Created vrfEntry for rd {} prefix {} nexthop {} label {}"
                + " and elantag {}", rd, prefix, nextHop, label, route.getElantag());
    }

    protected void addNewAdjToVpnInterface(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, String primaryRd,
                                           Adjacency adj, BigInteger dpnId, WriteTransaction writeOperTxn,
                                           WriteTransaction writeConfigTxn) {

        Optional<VpnInterfaceOpDataEntry> optVpnInterface = VpnUtil.read(dataBroker,
                                                LogicalDatastoreType.OPERATIONAL, identifier);

        if (optVpnInterface.isPresent()) {
            VpnInterfaceOpDataEntry currVpnIntf = optVpnInterface.get();
            String prefix = VpnUtil.getIpPrefix(adj.getIpAddress());
            String vpnName = currVpnIntf.getVpnInstanceName();
            VpnInstanceOpDataEntry vpnInstanceOpData = VpnUtil.getVpnInstanceOpData(dataBroker, primaryRd);
            InstanceIdentifier<AdjacenciesOp> adjPath = identifier.augmentation(AdjacenciesOp.class);
            Optional<AdjacenciesOp> optAdjacencies = VpnUtil.read(dataBroker,
                                            LogicalDatastoreType.OPERATIONAL, adjPath);
            boolean isL3VpnOverVxLan = VpnUtil.isL3VpnOverVxLan(vpnInstanceOpData.getL3vni());
            VrfEntry.EncapType encapType = VpnUtil.getEncapType(isL3VpnOverVxLan);
            long l3vni = vpnInstanceOpData.getL3vni() == null ? 0L :  vpnInstanceOpData.getL3vni();
            VpnPopulator populator = L3vpnRegistry.getRegisteredPopulator(encapType);
            List<Adjacency> adjacencies;
            if (optAdjacencies.isPresent()) {
                adjacencies = optAdjacencies.get().getAdjacency();
            } else {
                // This code will be hit in case of first PNF adjacency
                adjacencies = new ArrayList<>();
            }
            long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            L3vpnInput input = new L3vpnInput().setNextHop(adj).setVpnName(vpnName)
                    .setInterfaceName(currVpnIntf.getName()).setPrimaryRd(primaryRd).setRd(primaryRd);
            Adjacency operationalAdjacency = null;
            if (adj.getNextHopIpList() != null && !adj.getNextHopIpList().isEmpty()) {
                RouteOrigin origin = adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency ? RouteOrigin.LOCAL
                        : RouteOrigin.STATIC;
                String nh = adj.getNextHopIpList().get(0);
                String vpnPrefixKey = VpnUtil.getVpnNamePrefixKey(vpnName, prefix);
                synchronized (vpnPrefixKey.intern()) {
                    java.util.Optional<String> rdToAllocate = VpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(
                            dataBroker, vpnId, null, prefix, vpnName, nh, dpnId, writeOperTxn);
                    if (rdToAllocate.isPresent()) {
                        input.setRd(rdToAllocate.get());
                        operationalAdjacency = populator.createOperationalAdjacency(input);
                        int label = operationalAdjacency.getLabel().intValue();
                        vpnManager.addExtraRoute(vpnName, adj.getIpAddress(), nh, rdToAllocate.get(),
                                currVpnIntf.getVpnInstanceName(), label, l3vni, origin,
                                currVpnIntf.getName(), operationalAdjacency, encapType, writeConfigTxn);
                        LOG.info("addNewAdjToVpnInterface: Added extra route ip {} nh {} rd {} vpnname {} label {}"
                                + " Interface {} on dpn {}", adj.getIpAddress(), nh, rdToAllocate.get(),
                                vpnName, label, currVpnIntf.getName(), dpnId);
                    } else {
                        LOG.error("addNewAdjToVpnInterface: No rds to allocate extraroute vpn {} prefix {}", vpnName,
                                prefix);
                        return;
                    }
                    // iRT/eRT use case Will be handled in a new patchset for L3VPN Over VxLAN.
                    // Keeping the MPLS check for now.
                    if (encapType.equals(VrfEntryBase.EncapType.Mplsgre)) {
                        final Adjacency opAdjacency = new AdjacencyBuilder(operationalAdjacency).build();
                        List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                                VpnUtil.getVpnsImportingMyRoute(dataBroker, vpnName);
                        vpnsToImportRoute.forEach(vpn -> {
                            if (vpn.getVrfId() != null) {
                                VpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(
                                        dataBroker, vpn.getVpnId(), vpnId, prefix,
                                        VpnUtil.getVpnName(dataBroker, vpn.getVpnId()), nh, dpnId,
                                        writeOperTxn)
                                        .ifPresent(
                                            rds -> vpnManager.addExtraRoute(
                                                    VpnUtil.getVpnName(dataBroker, vpn.getVpnId()),
                                                    adj.getIpAddress(), nh, rds,
                                                    currVpnIntf.getVpnInstanceName(),
                                                    opAdjacency.getLabel().intValue(),
                                                    l3vni, RouteOrigin.SELF_IMPORTED,
                                                    currVpnIntf.getName(), opAdjacency, encapType, writeConfigTxn));
                            }
                        });
                    }
                }
            } else if (adj.isPhysNetworkFunc()) { // PNF adjacency.
                LOG.trace("addNewAdjToVpnInterface: Adding prefix {} to interface {} for vpn {}", prefix,
                        currVpnIntf.getName(), vpnName);

                String parentVpnRd = getParentVpnRdForExternalSubnet(adj);

                writeOperTxn.merge(
                        LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getPrefixToInterfaceIdentifier(VpnUtil.getVpnId(dataBroker,
                                adj.getSubnetId().getValue()), prefix),
                        VpnUtil.getPrefixToInterface(BigInteger.ZERO, currVpnIntf.getName(), prefix,
                                adj.getSubnetId(), Prefixes.PrefixCue.PhysNetFunc), true);

                fibManager.addOrUpdateFibEntry(dataBroker, adj.getSubnetId().getValue(), adj.getMacAddress(),
                        adj.getIpAddress(), Collections.emptyList(), null /* EncapType */, 0 /* label */, 0 /*l3vni*/,
                      null /* gw-mac */, parentVpnRd, RouteOrigin.LOCAL, writeConfigTxn);

                input.setRd(adj.getVrfId());
            }
            if (operationalAdjacency == null) {
                operationalAdjacency = populator.createOperationalAdjacency(input);
            }
            adjacencies.add(operationalAdjacency);
            AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(adjacencies);
            VpnInterfaceOpDataEntry newVpnIntf =
                VpnUtil.getVpnInterfaceOpDataEntry(currVpnIntf.getName(), currVpnIntf.getVpnInstanceName(),
                    aug, dpnId, currVpnIntf.isScheduledForRemove());

            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf, true);
        }
    }

    private String getParentVpnRdForExternalSubnet(Adjacency adj) {
        Subnets subnets = VpnUtil.getExternalSubnet(dataBroker, adj.getSubnetId());
        return subnets != null ? subnets.getExternalNetworkId().getValue() : null;
    }

    protected void delAdjFromVpnInterface(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, Adjacency adj,
            BigInteger dpnId, WriteTransaction writeOperTxn, WriteTransaction writeConfigTxn) {
        Optional<VpnInterfaceOpDataEntry> optVpnInterface = VpnUtil.read(dataBroker,
                                          LogicalDatastoreType.OPERATIONAL, identifier);

        if (optVpnInterface.isPresent()) {
            VpnInterfaceOpDataEntry currVpnIntf = optVpnInterface.get();

            InstanceIdentifier<AdjacenciesOp> path = identifier.augmentation(AdjacenciesOp.class);
            Optional<AdjacenciesOp> optAdjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
            if (optAdjacencies.isPresent()) {
                List<Adjacency> adjacencies = optAdjacencies.get().getAdjacency();

                if (!adjacencies.isEmpty()) {
                    LOG.trace("delAdjFromVpnInterface: Adjacencies are " + adjacencies);
                    Iterator<Adjacency> adjIt = adjacencies.iterator();
                    while (adjIt.hasNext()) {
                        Adjacency adjElem = adjIt.next();
                        if (adjElem.getIpAddress().equals(adj.getIpAddress())) {
                            String rd = adjElem.getVrfId();
                            adjIt.remove();

                            AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(adjacencies);
                            VpnInterfaceOpDataEntry newVpnIntf = VpnUtil
                                    .getVpnInterfaceOpDataEntry(currVpnIntf.getName(),
                                    currVpnIntf.getVpnInstanceName(),
                                    aug, dpnId, currVpnIntf.isScheduledForRemove());

                            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf, true);
                            if (adj.getNextHopIpList() != null) {
                                for (String nh : adj.getNextHopIpList()) {
                                    deleteExtraRouteFromCurrentAndImportingVpns(
                                        currVpnIntf.getVpnInstanceName(), adj.getIpAddress(), nh, rd,
                                        currVpnIntf.getName(), writeConfigTxn);
                                }
                            } else if (adj.isPhysNetworkFunc()) {
                                LOG.info("delAdjFromVpnInterface: deleting PNF adjacency prefix {} subnet [}",
                                        adj.getIpAddress(), adj.getSubnetId());
                                fibManager.removeFibEntry(dataBroker, adj.getSubnetId().getValue(), adj.getIpAddress(),
                                        writeConfigTxn);
                            }
                            break;
                        }

                    }
                }
                LOG.info("delAdjFromVpnInterface: Removed adj {} on dpn {} rd {}", adj.getIpAddress(),
                        dpnId, adj.getVrfId());
            } else {
                LOG.error("delAdjFromVpnInterface: Cannnot DEL adjacency, since operational interface is "
                        + "unavailable dpnId {} adjIP {} rd {}", dpnId, adj.getIpAddress(), adj.getVrfId());
            }
        }
    }

    private void deleteExtraRouteFromCurrentAndImportingVpns(String vpnName, String destination, String nextHop,
        String rd, String intfName, WriteTransaction writeConfigTxn) {
        vpnManager.delExtraRoute(vpnName, destination, nextHop, rd, vpnName, intfName, writeConfigTxn);
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = VpnUtil.getVpnsImportingMyRoute(dataBroker, vpnName);
        for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
            String vpnRd = vpn.getVrfId();
            if (vpnRd != null) {
                vpnManager.delExtraRoute(vpnName, destination, nextHop, vpnRd, vpnName, intfName, writeConfigTxn);
            }
        }
    }

    InstanceIdentifier<DpnVpninterfacesList> getRouterDpnId(String routerName, BigInteger dpnId) {
        return InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerName))
            .child(DpnVpninterfacesList.class, new DpnVpninterfacesListKey(dpnId)).build();
    }

    InstanceIdentifier<RouterDpnList> getRouterId(String routerName) {
        return InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerName)).build();
    }

    protected void addToNeutronRouterDpnsMap(String routerName, String vpnInterfaceName,
        WriteTransaction writeOperTxn) {
        BigInteger dpId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.error("addToNeutronRouterDpnsMap: Could not retrieve dp id for interface {} to handle router {}"
                    + " association model", vpnInterfaceName, routerName);

            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);

        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(dataBroker, LogicalDatastoreType
            .OPERATIONAL, routerDpnListIdentifier);
        RouterInterfaces routerInterface =
            new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(
                vpnInterfaceName).build();
        if (optionalRouterDpnList.isPresent()) {
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                RouterInterfaces.class, new RouterInterfacesKey(vpnInterfaceName)), routerInterface, true);
        } else {
            RouterDpnListBuilder builder = new RouterDpnListBuilder();
            builder.setRouterId(routerName);
            DpnVpninterfacesListBuilder dpnVpnList = new DpnVpninterfacesListBuilder().setDpnId(dpId);
            builder.setDpnVpninterfacesList(Collections.singletonList(dpnVpnList.build()));
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL,
                getRouterId(routerName),
                builder.build(), true);
        }
    }

    protected void removeFromNeutronRouterDpnsMap(String routerName, String vpnInterfaceName,
        WriteTransaction writeOperTxn) {
        BigInteger dpId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.error("removeFromNeutronRouterDpnsMap: Could not retrieve dp id for interface {} to handle router {}"
                    + " dissociation model", vpnInterfaceName, routerName);

            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(dataBroker, LogicalDatastoreType
            .OPERATIONAL, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            List<RouterInterfaces> routerInterfaces = optionalRouterDpnList.get().getRouterInterfaces();
            RouterInterfaces routerInterface =
                new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(
                    vpnInterfaceName).build();

            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    if (writeOperTxn != null) {
                        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                    } else {
                        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                    }
                } else {
                    if (writeOperTxn != null) {
                        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                            RouterInterfaces.class,
                            new RouterInterfacesKey(vpnInterfaceName)));
                    } else {
                        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            routerDpnListIdentifier.child(
                                RouterInterfaces.class,
                                new RouterInterfacesKey(vpnInterfaceName)));
                    }
                }
            }
        }
    }

    protected void removeFromNeutronRouterDpnsMap(String routerName, String vpnInterfaceName, BigInteger dpId,
        WriteTransaction writeOperTxn) {
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.error("removeFromNeutronRouterDpnsMap: Could not retrieve dp id for interface {} to handle router {}"
                    + " dissociation model", vpnInterfaceName, routerName);

            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(dataBroker, LogicalDatastoreType
            .OPERATIONAL, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            List<RouterInterfaces> routerInterfaces = optionalRouterDpnList.get().getRouterInterfaces();
            RouterInterfaces routerInterface =
                new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(
                    vpnInterfaceName).build();
            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                } else {
                    writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                        RouterInterfaces.class,
                        new RouterInterfacesKey(vpnInterfaceName)));
                }
            }
        }
    }

    protected void createFibEntryForRouterInterface(String primaryRd, VpnInterface vpnInterface, String interfaceName,
                                                    WriteTransaction writeConfigTxn, String vpnName) {
        if (vpnInterface == null) {
            return;
        }
        List<Adjacency> adjs = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker, interfaceName);
        if (adjs == null) {
            LOG.error("createFibEntryForRouterInterface: VPN Interface {} of router addition failed as adjacencies for"
                    + " this vpn interface could not be obtained. vpn {}", interfaceName, vpnName);
            return;
        }
        for (Adjacency adj : adjs) {
            if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                String primaryInterfaceIp = adj.getIpAddress();
                String macAddress = adj.getMacAddress();
                String prefix = VpnUtil.getIpPrefix(primaryInterfaceIp);

                long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                    VpnUtil.getNextHopLabelKey(primaryRd, prefix));

                RouterInterface routerInt = new RouterInterfaceBuilder().setUuid(vpnName)
                        .setIpAddress(primaryInterfaceIp).setMacAddress(macAddress).build();
                fibManager.addFibEntryForRouterInterface(dataBroker, primaryRd, prefix,
                        routerInt, label, writeConfigTxn);
                LOG.info("createFibEntryForRouterInterface: Router interface {} for vpn {} rd {} prefix {} label {}"
                        + " macAddress {} processed successfully;", interfaceName, vpnName, primaryRd, prefix, label,
                        macAddress);
                return;
            }
        }
        LOG.error("createFibEntryForRouterInterface: VPN Interface {} of router addition failed as primary"
                + " adjacency for this vpn interface could not be obtained. rd {} vpnName {}", interfaceName,
                primaryRd, vpnName);
    }

    protected void deleteFibEntryForRouterInterface(VpnInterface vpnInterface,
                                                    WriteTransaction writeConfigTxn, String vpnName) {
        Adjacencies adjs = vpnInterface.getAugmentation(Adjacencies.class);
        String rd = VpnUtil.getVpnRd(dataBroker, vpnName);
        if (adjs != null) {
            List<Adjacency> adjsList = adjs.getAdjacency();
            for (Adjacency adj : adjsList) {
                if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                    String primaryInterfaceIp = adj.getIpAddress();
                    String prefix = VpnUtil.getIpPrefix(primaryInterfaceIp);
                    fibManager.removeFibEntry(dataBroker, rd, prefix, writeConfigTxn);
                    LOG.info("deleteFibEntryForRouterInterface: FIB for router interface {} deleted for vpn {} rd {}"
                            + " prefix {}", vpnInterface.getName(), vpnName, rd, prefix);
                    return;
                }
            }
        } else {
            LOG.error("deleteFibEntryForRouterInterface: Adjacencies for vpninterface {} is null, rd: {}",
                    vpnInterface.getName(), rd);
        }
    }

    private void processSavedInterface(UnprocessedVpnInterfaceData intefaceData, String vpnName) {
        if (!canHandleNewVpnInterface(intefaceData.identifier, intefaceData.vpnInterface, vpnName)) {
            LOG.error("add: VpnInstance {} for vpnInterface {} not ready, holding on ",
                  vpnName, intefaceData.vpnInterface.getName());
            return;
        }
        final VpnInterfaceKey key = intefaceData.identifier
               .firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();
        InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier = VpnUtil
                 .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
        addVpnInterfaceToVpn(vpnInterfaceOpIdentifier, intefaceData.vpnInterface, null, null,
                  intefaceData.identifier, vpnName);
        return;
    }

    private void addToUnprocessedVpnInterfaces(InstanceIdentifier<VpnInterface> identifier,
                                              VpnInterface vpnInterface, String vpnName) {
        ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces = unprocessedVpnInterfaces
               .get(vpnName);
        if (vpnInterfaces == null) {
            vpnInterfaces = new ConcurrentLinkedQueue<>();
        }
        vpnInterfaces.add(new UnprocessedVpnInterfaceData(identifier, vpnInterface));
        unprocessedVpnInterfaces.put(vpnName, vpnInterfaces);
        LOG.info("addToUnprocessedVpnInterfaces: Saved unhandled vpn interface {} in vpn instance {}",
                vpnInterface.getName(), vpnName);
    }

    public boolean isVpnInstanceReady(String vpnInstanceName) {
        String vpnRd = VpnUtil.getVpnRd(dataBroker, vpnInstanceName);
        if (vpnRd == null) {
            return false;
        }
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(dataBroker, vpnRd);

        return vpnInstanceOpDataEntry != null;
    }

    public void processSavedInterfaces(String vpnInstanceName, boolean hasVpnInstanceCreatedSuccessfully) {
        synchronized (vpnInstanceName.intern()) {
            ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces =
                    unprocessedVpnInterfaces.get(vpnInstanceName);
            if (vpnInterfaces != null) {
                while (!vpnInterfaces.isEmpty()) {
                    UnprocessedVpnInterfaceData savedInterface = vpnInterfaces.poll();
                    if (hasVpnInstanceCreatedSuccessfully) {
                        processSavedInterface(savedInterface, vpnInstanceName);
                        LOG.info("processSavedInterfaces: Handle saved vpn interfaces {} in vpn instance {}",
                                savedInterface.vpnInterface.getName(), vpnInstanceName);
                    } else {
                        LOG.error("processSavedInterfaces: Cannot process vpn interface {} in vpn instance {}",
                                savedInterface.vpnInterface.getName(), vpnInstanceName);
                    }
                }
            } else {
                LOG.info("processSavedInterfaces: No interfaces in queue for VPN {}", vpnInstanceName);
            }
        }
    }

    private void removeInterfaceFromUnprocessedList(InstanceIdentifier<VpnInterface> identifier,
            VpnInterface vpnInterface) {
        synchronized (VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface).intern()) {
            ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces =
                unprocessedVpnInterfaces.get(VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface));
            if (vpnInterfaces != null) {
                if (vpnInterfaces.remove(new UnprocessedVpnInterfaceData(identifier, vpnInterface))) {
                    LOG.info("removeInterfaceFromUnprocessedList: Removed vpn interface {} in vpn instance {} from "
                            + "unprocessed list", vpnInterface.getName(),
                            VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface));
                }
            } else {
                LOG.info("removeInterfaceFromUnprocessedList: No interfaces in queue for VPN {}",
                        VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface));
            }
        }
    }

    public void vpnInstanceIsReady(String vpnInstanceName) {
        processSavedInterfaces(vpnInstanceName, true);
    }

    public void vpnInstanceFailed(String vpnInstanceName) {
        processSavedInterfaces(vpnInstanceName, false);
    }

    private static class UnprocessedVpnInterfaceData {
        InstanceIdentifier<VpnInterface> identifier;
        VpnInterface vpnInterface;

        UnprocessedVpnInterfaceData(InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
            this.identifier = identifier;
            this.vpnInterface = vpnInterface;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (identifier == null ? 0 : identifier.hashCode());
            result = prime * result + (vpnInterface == null ? 0 : vpnInterface.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            UnprocessedVpnInterfaceData other = (UnprocessedVpnInterfaceData) obj;
            if (identifier == null) {
                if (other.identifier != null) {
                    return false;
                }
            } else if (!identifier.equals(other.identifier)) {
                return false;
            }
            if (vpnInterface == null) {
                if (other.vpnInterface != null) {
                    return false;
                }
            } else if (!vpnInterface.equals(other.vpnInterface)) {
                return false;
            }
            return true;
        }
    }

    public void updateVpnInterfacesForUnProcessAdjancencies(String vpnName) {
        String primaryRd = VpnUtil.getVpnRd(dataBroker, vpnName);
        VpnInstanceOpDataEntry vpnInstanceOpData = VpnUtil.getVpnInstanceOpData(dataBroker, primaryRd);
        if (vpnInstanceOpData == null) {
            return;
        }
        List<VpnToDpnList> vpnToDpnLists = vpnInstanceOpData.getVpnToDpnList();
        if (vpnToDpnLists == null || vpnToDpnLists.isEmpty()) {
            return;
        }
        LOG.debug("Update the VpnInterfaces for Unprocessed Adjancencies for vpnName:{}", vpnName);
        vpnToDpnLists.forEach(vpnToDpnList -> vpnToDpnList.getVpnInterfaces().forEach(vpnInterface -> {
            InstanceIdentifier<VpnInterfaceOpDataEntry> existingVpnInterfaceId =
                VpnUtil.getVpnInterfaceOpDataEntryIdentifier(vpnInterface.getInterfaceName(), vpnName);
            Optional<VpnInterfaceOpDataEntry> vpnInterfaceOptional = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, existingVpnInterfaceId);
            if (!vpnInterfaceOptional.isPresent()) {
                return;
            }
            List<Adjacency> configVpnAdjacencies = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker,
                    vpnInterface.getInterfaceName());
            if (configVpnAdjacencies == null) {
                LOG.debug("There is no adjacency available for vpnInterface:{}", vpnInterface);
                return;
            }
            List<Adjacency> operationVpnAdjacencies = vpnInterfaceOptional.get()
                    .getAugmentation(AdjacenciesOp.class).getAdjacency();
            // Due to insufficient rds,  some of the extra route wont get processed when it is added.
            // The unprocessed adjacencies will be present in config vpn interface DS but will be missing
            // in operational DS. These unprocessed adjacencies will be handled below.
            // To obtain unprocessed adjacencies, filtering is done by which the missing adjacencies in operational
            // DS are retrieved which is used to call addNewAdjToVpnInterface method.
            configVpnAdjacencies.stream()
                    .filter(adjacency -> operationVpnAdjacencies.stream()
                            .noneMatch(operationalAdjacency ->
                                    operationalAdjacency.getIpAddress().equals(adjacency.getIpAddress())))
                    .forEach(adjacency -> {
                        LOG.debug("Processing the vpnInterface{} for the Ajacency:{}", vpnInterface, adjacency);
                        jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterface.getInterfaceName() + vpnName,
                            () -> {
                                WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                                if (VpnUtil.isAdjacencyEligibleToVpn(
                                     dataBroker, adjacency, vpnName, vpnInterface.getInterfaceName())) {
                                    addNewAdjToVpnInterface(existingVpnInterfaceId, primaryRd, adjacency,
                                            vpnInterfaceOptional.get().getDpnId(), writeConfigTxn, writeOperTxn);
                                    ListenableFuture<Void> operFuture = writeOperTxn.submit();
                                    try {
                                        operFuture.get();
                                    } catch (ExecutionException | InterruptedException e) {
                                        LOG.error("Exception encountered while submitting operational"
                                                + " future for vpnInterface {}", vpnInterface, e);
                                    }
                                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                                    futures.add(writeConfigTxn.submit());
                                    return futures;
                                }  else {
                                    return Collections.emptyList();
                                }
                            });
                    });
        }));
    }
}

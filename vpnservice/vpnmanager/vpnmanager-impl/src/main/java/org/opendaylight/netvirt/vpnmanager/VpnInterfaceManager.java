/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.netvirt.vpnmanager.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkUtil;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.LabelRouteMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRouteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInterfaceManager extends AsyncDataTreeChangeListenerBase<VpnInterface, VpnInterfaceManager>
    implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceManager.class);
    private static final int VPN_INF_UPDATE_TIMER_TASK_DELAY = 1000;
    private static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    private final DataBroker dataBroker;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;
    private final OdlArputilService arpManager;
    private final OdlInterfaceRpcService ifaceMgrRpcService;
    private final VpnFootprintService vpnFootprintService;
    private final IInterfaceManager interfaceManager;
    private final IVpnManager vpnManager;

    private ConcurrentHashMap<String, Runnable> vpnIntfMap = new ConcurrentHashMap<>();

    private BlockingQueue<UpdateData> vpnInterfacesUpdateQueue = new LinkedBlockingQueue<>();
    private ScheduledThreadPoolExecutor vpnInfUpdateTaskExecutor = (ScheduledThreadPoolExecutor) Executors
            .newScheduledThreadPool(1);

    private final Map<String, ConcurrentLinkedQueue<UnprocessedVpnInterfaceData>> unprocessedVpnInterfaces =
            new ConcurrentHashMap<>();

    public VpnInterfaceManager(final DataBroker dataBroker,
                               final IBgpManager bgpManager,
                               final OdlArputilService arpManager,
                               final IdManagerService idManager,
                               final IMdsalApiManager mdsalManager,
                               final IFibManager fibManager,
                               final OdlInterfaceRpcService ifaceMgrRpcService,
                               final VpnFootprintService vpnFootprintService,
                               final IInterfaceManager interfaceManager,
                               final IVpnManager vpnManager) {
        super(VpnInterface.class, VpnInterfaceManager.class);

        this.dataBroker = dataBroker;
        this.bgpManager = bgpManager;
        this.arpManager = arpManager;
        this.idManager = idManager;
        this.mdsalManager = mdsalManager;
        this.fibManager = fibManager;
        this.ifaceMgrRpcService = ifaceMgrRpcService;
        this.vpnFootprintService = vpnFootprintService;
        this.interfaceManager = interfaceManager;
        this.vpnManager = vpnManager;
        vpnInfUpdateTaskExecutor.scheduleWithFixedDelay(new VpnInterfaceUpdateTimerTask(),
            0, VPN_INF_UPDATE_TIMER_TASK_DELAY, TIME_UNIT);
    }

    public Runnable isNotifyTaskQueued(String intfName) {
        return vpnIntfMap.remove(intfName);
    }

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

    private InstanceIdentifier<Interface> getInterfaceListenerPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    public void add(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface) {
        if (canHandleNewVpnInterface(identifier, vpnInterface)) {
            addVpnInterface(identifier, vpnInterface, null, null);
        }
    }

    private boolean canHandleNewVpnInterface(final InstanceIdentifier<VpnInterface> identifier,
            final VpnInterface vpnInterface) {
        synchronized (vpnInterface.getVpnInstanceName().intern()) {
            if (isVpnInstanceReady(vpnInterface.getVpnInstanceName())) {
                return true;
            }
            addToUnprocessedVpnInterfaces(identifier, vpnInterface);
            return false;
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addVpnInterface(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface,
        final List<Adjacency> oldAdjs, final List<Adjacency> newAdjs) {
        LOG.trace("VPN Interface add event - key: {}, value: {}", identifier, vpnInterface);
        LOG.info("VPN Interface add event - intfName {}", vpnInterface.getName());
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();

        Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
        if (interfaceState != null) {
            final BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(interfaceState);
            final int ifIndex = interfaceState.getIfIndex();
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
                () -> {
                    WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                    WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                    WriteTransaction writeInvTxn = dataBroker.newWriteOnlyTransaction();
                    processVpnInterfaceUp(dpnId, vpnInterface, ifIndex, false, writeConfigTxn, writeOperTxn,
                            writeInvTxn);
                    if (oldAdjs != null && !oldAdjs.equals(newAdjs)) {
                        LOG.trace("Adjacency changed upon VPNInterface {} Update for swapping VPN case",
                                interfaceName);
                        if (newAdjs != null) {
                            for (Adjacency adj : newAdjs) {
                                if (oldAdjs.contains(adj)) {
                                    oldAdjs.remove(adj);
                                } else {
                                    addNewAdjToVpnInterface(identifier, adj, dpnId, writeOperTxn,
                                            writeConfigTxn);
                                }
                            }
                        }
                        for (Adjacency adj : oldAdjs) {
                            delAdjFromVpnInterface(identifier, adj, dpnId, writeOperTxn, writeConfigTxn);
                        }
                    }
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(writeOperTxn.submit());
                    futures.add(writeConfigTxn.submit());
                    futures.add(writeInvTxn.submit());
                    return futures;
                });
        } else if (Boolean.TRUE.equals(vpnInterface.isIsRouterInterface())) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterface.getName(),
                () -> {
                    WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                    createFibEntryForRouterInterface(vpnInterface, interfaceName, writeConfigTxn);
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(writeConfigTxn.submit());
                    return futures;
                });
        } else {
            LOG.error("Handling addition of VPN interface {} skipped as interfaceState is not available",
                    interfaceName);
        }
    }

    protected void processVpnInterfaceUp(final BigInteger dpId, VpnInterface vpnInterface,
            final int lportTag, boolean isInterfaceUp,
            WriteTransaction writeConfigTxn,
            WriteTransaction writeOperTxn,
            WriteTransaction writeInvTxn) {

        final String interfaceName = vpnInterface.getName();
        if (!isInterfaceUp) {
            final String vpnName = vpnInterface.getVpnInstanceName();
            LOG.info("Binding vpn service to interface {} ", interfaceName);
            long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            if (vpnId == VpnConstants.INVALID_ID) {
                LOG.error("VpnInstance to VPNId mapping not available for VpnName {} processing vpninterface {}"
                        + ", bailing out now.", vpnName, interfaceName);
                return;
            }

            boolean waitForVpnInterfaceOpRemoval = false;
            VpnInterface opVpnInterface = VpnUtil.getOperationalVpnInterface(dataBroker, vpnInterface.getName());
            if (opVpnInterface != null) {
                String opVpnName = opVpnInterface.getVpnInstanceName();
                String primaryInterfaceIp = null;
                if (opVpnName.equals(vpnName)) {
                    // Please check if the primary VRF Entry does not exist for VPNInterface
                    // If so, we have to process ADD, as this might be a DPN Restart with Remove and Add triggered
                    // back to back
                    // However, if the primary VRF Entry for this VPNInterface exists, please continue bailing out !
                    List<Adjacency> adjs = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker, interfaceName);
                    if (adjs == null) {
                        LOG.info(
                                "VPN Interface {} addition failed as adjacencies for this vpn interface could not be "
                                        + "obtained", interfaceName);
                        return;
                    }
                    for (Adjacency adj : adjs) {
                        if (adj.isPrimaryAdjacency()) {
                            primaryInterfaceIp = adj.getIpAddress();
                            break;
                        }
                    }
                    if (primaryInterfaceIp == null) {
                        LOG.info("VPN Interface {} addition failed as primary adjacency "
                                + "for this vpn interface could not be obtained", interfaceName);
                        return;
                    }
                    // Get the rd of the vpn instance
                    String primaryRd = VpnUtil.getPrimaryRd(dataBroker, opVpnName);
                    VrfEntry vrf = VpnUtil.getVrfEntry(dataBroker, primaryRd, primaryInterfaceIp);
                    if (vrf != null) {
                        LOG.info("VPN Interface {} already provisioned , bailing out from here.", interfaceName);
                        return;
                    }
                    waitForVpnInterfaceOpRemoval = true;
                } else {
                    LOG.info("vpn interface {} to go to configured vpn {}, but in operational vpn {}",
                            interfaceName, vpnName, opVpnName);
                }
            }
            if (!waitForVpnInterfaceOpRemoval) {
                // Add the VPNInterface and quit
                vpnFootprintService.updateVpnToDpnMapping(dpId, vpnName, interfaceName, true /* add */);
                bindService(dpId, vpnName, interfaceName, lportTag);
                processVpnInterfaceAdjacencies(dpId, lportTag, vpnName, interfaceName,
                        vpnId, writeConfigTxn, writeOperTxn, writeInvTxn);
                if (interfaceManager.isExternalInterface(interfaceName)) {
                    processExternalVpnInterface(vpnInterface, vpnId, dpId, lportTag, writeInvTxn, NwConstants.ADD_FLOW);
                }
                return;
            }

            // FIB didn't get a chance yet to clean up this VPNInterface
            // Let us give it a chance here !
            LOG.info("Trying to add VPN Interface {}, but waiting for FIB to clean up! ", interfaceName);
            try {
                Runnable notifyTask = new VpnNotifyTask();
                vpnIntfMap.put(interfaceName, notifyTask);
                synchronized (notifyTask) {
                    try {
                        notifyTask.wait(VpnConstants.MAX_WAIT_TIME_IN_MILLISECONDS);
                    } catch (InterruptedException e) {
                        // Ignored
                    }
                }
            } finally {
                vpnIntfMap.remove(interfaceName);
            }

            opVpnInterface = VpnUtil.getOperationalVpnInterface(dataBroker, interfaceName);
            if (opVpnInterface != null) {
                LOG.error("VPN Interface {} removal by FIB did not complete on time, bailing addition ...",
                        interfaceName);
                return;
            }
            // VPNInterface got removed, proceed with Add
            vpnFootprintService.updateVpnToDpnMapping(dpId, vpnName, interfaceName, true /* add */);
            bindService(dpId, vpnName, interfaceName, lportTag);
            processVpnInterfaceAdjacencies(dpId, lportTag, vpnName, interfaceName,
                    vpnId, writeConfigTxn, writeOperTxn, writeInvTxn);
            if (interfaceManager.isExternalInterface(interfaceName)) {
                processExternalVpnInterface(vpnInterface, vpnId, dpId, lportTag, writeInvTxn, NwConstants.ADD_FLOW);
            }
        } else {
            // Interface is retained in the DPN, but its Link Up.
            // Advertise prefixes again for this interface to BGP
            advertiseAdjacenciesForVpnToBgp(dpId, VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()),
                    vpnInterface);
        }
    }

    private void processExternalVpnInterface(VpnInterface vpnInterface, long vpnId, BigInteger dpId, int lportTag,
            WriteTransaction writeInvTxn, int addOrRemove) {
        Uuid extNetworkId;
        try {
            // vpn instance of ext-net interface is the network-id
            extNetworkId = new Uuid(vpnInterface.getVpnInstanceName());
        } catch (IllegalArgumentException e) {
            LOG.debug("VPN instance {} is not Uuid", vpnInterface.getVpnInstanceName());
            return;
        }

        List<Uuid> routerIds = VpnUtil.getExternalNetworkRouterIds(dataBroker, extNetworkId);
        if (routerIds == null || routerIds.isEmpty()) {
            LOG.debug("No router is associated with {}", extNetworkId.getValue());
            return;
        }

        LOG.debug("Router-ids {} associated with exernal vpn-interface {}", routerIds, vpnInterface.getName());
        for (Uuid routerId : routerIds) {
            String routerName = routerId.getValue();
            BigInteger primarySwitch = VpnUtil.getPrimarySwitchForRouter(dataBroker, routerName);
            if (Objects.equals(primarySwitch, dpId)) {
                Routers router = VpnUtil.getExternalRouter(dataBroker, routerName);
                if (router != null) {
                    vpnManager.setupArpResponderFlowsToExternalNetworkIps(routerName, router.getExternalIps(),
                            router.getExtGwMacAddress(), dpId, vpnId, vpnInterface.getName(), lportTag, writeInvTxn,
                            addOrRemove);
                } else {
                    LOG.error("No external-router found for router-id {}", routerName);
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void advertiseAdjacenciesForVpnToBgp(BigInteger dpnId, final InstanceIdentifier<VpnInterface> identifier,
            VpnInterface intf) {

        String rd = VpnUtil.getVpnRd(dataBroker, intf.getVpnInstanceName());
        if (rd == null) {
            LOG.error("advertiseAdjacenciesForVpnFromBgp: Unable to recover rd for interface {} in vpn {}",
                    intf.getName(), intf.getVpnInstanceName());
            return;
        } else {
            if (rd.equals(intf.getVpnInstanceName())) {
                LOG.info(
                        "advertiseAdjacenciesForVpnFromBgp: Ignoring BGP advertisement for interface {} as it is in "
                                + "internal vpn{} with rd {}",
                        intf.getName(), intf.getVpnInstanceName(), rd);

                return;
            }
        }
        LOG.info("advertiseAdjacenciesForVpnToBgp: Advertising interface {} in vpn {} with rd {} ", intf.getName(),
                intf.getVpnInstanceName(), rd);

        String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
        if (nextHopIp == null) {
            LOG.trace("advertiseAdjacenciesForVpnToBgp: NextHop for interface {} is null, returning", intf.getName());
            return;
        }

        //Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are {}", nextHops);
                for (Adjacency nextHop : nextHops) {
                    long label = nextHop.getLabel();
                    try {
                        LOG.info("VPN ADVERTISE: Adding Fib Entry rd {} prefix {} nexthop {} label {}", rd,
                                nextHop.getIpAddress(), nextHopIp, label);
                        bgpManager.advertisePrefix(rd, nextHop.getIpAddress(), nextHopIp, (int) label);
                        LOG.info("VPN ADVERTISE: Added Fib Entry rd {} prefix {} nexthop {} label {}", rd,
                                nextHop.getIpAddress(), nextHopIp, label);
                    } catch (Exception e) {
                        LOG.error("Failed to advertise prefix {} in vpn {} with rd {} for interface {} ",
                                nextHop.getIpAddress(), intf.getVpnInstanceName(), rd, intf.getName(), e);
                    }
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void withdrawAdjacenciesForVpnFromBgp(final InstanceIdentifier<VpnInterface> identifier,
            VpnInterface intf) {
        //Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);

        String rd = VpnUtil.getVpnRd(dataBroker, intf.getVpnInstanceName());
        if (rd == null) {
            LOG.error("withdrawAdjacenciesForVpnFromBgp: Unable to recover rd for interface {} in vpn {}",
                    intf.getName(), intf.getVpnInstanceName());
            return;
        } else {
            if (rd.equals(intf.getVpnInstanceName())) {
                LOG.info(
                        "withdrawAdjacenciesForVpnFromBgp: Ignoring BGP withdrawal for interface {} as it is in "
                                + "internal vpn{} with rd {}",
                        intf.getName(), intf.getVpnInstanceName(), rd);
                return;
            }
        }
        LOG.info("withdrawAdjacenciesForVpnFromBgp: For interface {} in vpn {} with rd {}", intf.getName(),
                intf.getVpnInstanceName(), rd);
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    try {
                        LOG.info("VPN WITHDRAW: Removing Fib Entry rd {} prefix {}", rd, nextHop.getIpAddress());
                        bgpManager.withdrawPrefix(rd, nextHop.getIpAddress());
                        LOG.info("VPN WITHDRAW: Removed Fib Entry rd {} prefix {}", rd, nextHop.getIpAddress());
                    } catch (Exception e) {
                        LOG.error("Failed to withdraw prefix {} in vpn {} with rd {} for interface {} ",
                                nextHop.getIpAddress(), intf.getVpnInstanceName(), rd, intf.getName(), e);
                    }
                }
            }
        }
    }

    private void bindService(BigInteger dpId, final String vpnInstanceName, final String vpnInterfaceName,
            int lportTag) {
        final int priority = VpnConstants.DEFAULT_FLOW_PRIORITY;
        final long vpnId = VpnUtil.getVpnId(dataBroker, vpnInstanceName);

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(vpnInterfaceName,
            () -> {
                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                int instructionKey = 0;
                List<Instruction> instructions = new ArrayList<>();

                instructions.add(
                        MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnId),
                                MetaDataUtil.METADATA_MASK_VRFID, ++instructionKey));
                instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_GW_MAC_TABLE,
                        ++instructionKey));

                BoundServices serviceInfo = InterfaceUtils.getBoundServices(
                        String.format("%s.%s.%s", "vpn", vpnInstanceName, vpnInterfaceName),
                        ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME, NwConstants.L3VPN_SERVICE_INDEX),
                        priority, NwConstants.COOKIE_VM_INGRESS_TABLE, instructions);
                writeTxn.put(LogicalDatastoreType.CONFIGURATION, InterfaceUtils.buildServiceId(vpnInterfaceName,
                        ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME, NwConstants.L3VPN_SERVICE_INDEX)),
                        serviceInfo, true);
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(writeTxn.submit());
                return futures;
            });
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void processVpnInterfaceAdjacencies(BigInteger dpnId, final int lportTag, String vpnName,
                                                  String interfaceName, final long vpnId,
                                                  WriteTransaction writeConfigTxn,
                                                  WriteTransaction writeOperTxn,
                                                  final WriteTransaction writeInvTxn) {
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        // Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);

        if (!adjacencies.isPresent()) {
            addVpnInterfaceToOperational(vpnName, interfaceName, dpnId, null, writeOperTxn);
            return;
        }
        List<Adjacency> nextHops = adjacencies.get().getAdjacency();
        List<Adjacency> value = new ArrayList<>();

        // Get the rd of the vpn instance
        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
        String nextHopIp = null;
        try {
            nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
        } catch (Exception e) {
            LOG.warn("Unable to retrieve enpoint ip address for dpnId {} for vpnInterface {} vpnName {}",
                    dpnId, interfaceName, vpnName);
        }
        List<String> nhList = new ArrayList<>();
        if (nextHopIp != null) {
            nhList.add(nextHopIp);
            LOG.trace("NextHop for interface {} is {}", interfaceName, nhList);
        }

        List<VpnInstanceOpDataEntry> vpnsToImportRoute = getVpnsImportingMyRoute(vpnName);

        LOG.trace("NextHops for interface {} are {}", interfaceName, nextHops);
        for (Adjacency nextHop : nextHops) {
            String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
            long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                    VpnUtil.getNextHopLabelKey(primaryRd, prefix));
            if (label == VpnConstants.INVALID_LABEL) {
                LOG.error(
                    "Unable to fetch label from Id Manager. Bailing out of processing add/update of vpn interface {} "
                        + "for vpn {}",
                    interfaceName, vpnName);
                return;
            }
            List<String> adjNextHop = nextHop.getNextHopIpList();
            AdjacencyBuilder adjacencyBuilder = new AdjacencyBuilder(nextHop).setLabel(label).setNextHopIpList(
                    (adjNextHop != null && !adjNextHop.isEmpty()) ? adjNextHop : nhList)
                    .setIpAddress(prefix).setKey(new AdjacencyKey(prefix));

            if (nextHop.isPrimaryAdjacency()) {
                LOG.trace("Adding prefix {} to interface {} for vpn {}", prefix, interfaceName, vpnName);
                adjacencyBuilder.setVrfId(primaryRd);
                writeOperTxn.merge(
                    LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getPrefixToInterfaceIdentifier(
                        VpnUtil.getVpnId(dataBroker, vpnName), prefix),
                    VpnUtil.getPrefixToInterface(dpnId, interfaceName, prefix), true);
                final Uuid subnetId = nextHop.getSubnetId();
                setupGwMacIfRequired(dpnId, vpnName, interfaceName, vpnId, subnetId,
                        writeInvTxn, NwConstants.ADD_FLOW);
                addArpResponderFlow(dpnId, lportTag, vpnName, vpnId, interfaceName, subnetId, writeInvTxn);
            } else {
                //Extra route adjacency
                synchronized (prefix.intern()) {
                    java.util.Optional<String> rdToAllocate = VpnUtil
                            .allocateRdForExtraRouteAndUpdateUsedRdsMap(dataBroker,
                            vpnId, prefix, vpnName, dpnId, nextHop, writeOperTxn);
                    if (rdToAllocate.isPresent()) {
                        adjacencyBuilder.setVrfId(rdToAllocate.get());
                        VpnUtil.syncUpdate(
                                dataBroker,
                                LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getVpnToExtrarouteIdentifier(vpnName,
                                        rdToAllocate.get(), nextHop.getIpAddress()),
                                VpnUtil.getVpnToExtraroute(nextHop.getIpAddress(), nextHop.getNextHopIpList()));
                    } else {
                        LOG.error("No rds to allocate extraroute {}", prefix);
                        return;
                    }
                }
            }
            value.add(adjacencyBuilder.build());
        }

        Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);

        addVpnInterfaceToOperational(vpnName, interfaceName, dpnId, aug, writeOperTxn);

        for (Adjacency nextHop : aug.getAdjacency()) {
            String rd = nextHop.getVrfId();
            long label = nextHop.getLabel();
            if (rd != null) {
                addToLabelMapper(label, dpnId, nextHop.getIpAddress(), nhList, vpnId,
                        interfaceName, null,false, primaryRd, writeOperTxn);
                addPrefixToBGP(vpnName, rd, nextHop.getIpAddress(), nhList, label, RouteOrigin.LOCAL, writeConfigTxn);
                //TODO: ERT - check for VPNs importing my route
                for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                    String vpnRd = vpn.getVrfId();
                    if (vpnRd != null) {
                        LOG.debug("Exporting route with rd {} prefix {} nexthop {} label {} to VPN {}", vpnRd,
                            nextHop.getIpAddress(), nextHopIp, label, vpn);
                        fibManager.addOrUpdateFibEntry(dataBroker, vpnRd, nextHop.getIpAddress(), nhList, (int) label,
                            RouteOrigin.SELF_IMPORTED, writeConfigTxn);
                    }
                }
            } else {
                // ### add FIB route directly
                fibManager.addOrUpdateFibEntry(dataBroker, vpnName, nextHop.getIpAddress(),
                        (nextHopIp == null ? Collections.emptyList() : Collections.singletonList(nextHopIp)),
                        (int) label, RouteOrigin.LOCAL, writeConfigTxn);
            }
        }
    }

    private void addVpnInterfaceToOperational(String vpnName, String interfaceName, BigInteger dpnId, Adjacencies aug,
        WriteTransaction writeOperTxn) {
        VpnInterface opInterface = VpnUtil.getVpnInterface(interfaceName, vpnName, aug, dpnId, Boolean.FALSE);
        InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        writeOperTxn.put(LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface, true);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateVpnInterfaceOnTepAdd(VpnInterface vpnInterface,
        StateTunnelList stateTunnelList) {

        String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        Adjacencies adjacencies = vpnInterface.getAugmentation(Adjacencies.class);
        List<Adjacency> adjList = (adjacencies != null) ? adjacencies.getAdjacency() : new ArrayList<>();
        String prefix = null;
        long label = 0;
        List<String> nhList = new ArrayList<>();
        boolean nextHopAdded = false;
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnInterface.getVpnInstanceName());
        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnInterface.getVpnInstanceName());

        if (adjList != null) {
            List<Adjacency> value = new ArrayList<>();
            LOG.trace("AdjacencyList for interface {} is {}", vpnInterface, adjList);
            for (Adjacency adj : adjList) {
                String rd = adj.getVrfId();
                rd = (rd != null) ? rd : vpnInterface.getVpnInstanceName();
                prefix = adj.getIpAddress();
                label = adj.getLabel();
                nhList = Collections.singletonList(srcTepIp);
                //Only for primary adjacency update the nexthop ip
                if (adj.getMacAddress() != null && !adj.getMacAddress().isEmpty()) {
                    List<String> nextHopList = adj.getNextHopIpList();
                    // Incase nextHopList contains some other TEP IP ,
                    // it needs to be updated with new one.
                    // Incase nextHopIp was null at the time of VpnInterface creation,
                    // it needs to be updated with new one.
                    if (nextHopList != null && (!nextHopList.isEmpty())
                        && (nextHopList.get(0).equalsIgnoreCase(srcTepIp))) {
                            /* everything right already */
                    } else {
                        /* update the adjacency here itself */
                        nextHopAdded = true;
                        LOG.trace("NextHopList to be updated {}", nhList);
                        // Update the VpnInterface Op DS with new nextHopList
                        value.add(new AdjacencyBuilder(adj).setNextHopIpList(nhList).build());
                        Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);
                        VpnInterface opInterface =
                            new VpnInterfaceBuilder(vpnInterface).setKey(new VpnInterfaceKey(vpnInterface.getName()))
                                .addAugmentation(Adjacencies.class, aug).build();
                        InstanceIdentifier<VpnInterface> interfaceId =
                            VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName());
                        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface);
                    }
                }

                if (nextHopAdded) {
                    LOG.info("Updating label mapper : label {} dpn {} prefix {} nexthoplist {} vpnid {} rd {}", label,
                        srcDpnId, prefix, nhList, vpnId, rd);
                    updateLabelMapper(label, nhList);

                    // Update the VRF entry with nextHop
                    fibManager.updateFibEntry(dataBroker, primaryRd, prefix, nhList, label, null);

                    //Get the list of VPN's importing this route(prefix) .
                    // Then update the VRF entry with nhList
                    List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                        getVpnsImportingMyRoute(vpnInterface.getVpnInstanceName());
                    for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                        String vpnRd = vpn.getVrfId();
                        if (vpnRd != null) {
                            LOG.debug("Exporting route with rd {} prefix {} nhList {} label {} to VPN {}", vpnRd,
                                prefix, nhList, label, vpn);
                            fibManager.updateFibEntry(dataBroker, vpnRd, prefix, nhList, label, null);
                        }
                    }
                    // Advertise the prefix to BGP only for external vpn
                    // since there is a nexthop change.
                    try {
                        if (!rd.equalsIgnoreCase(vpnInterface.getVpnInstanceName())) {
                            bgpManager.advertisePrefix(rd, prefix, nhList, (int) label);
                        }
                    } catch (Exception ex) {
                        LOG.error("Exception when advertising prefix {} on rd {} as {}", prefix, rd, ex);
                    }
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateVpnInterfaceOnTepDelete(VpnInterface vpnInterface,
        StateTunnelList stateTunnelList) {

        Adjacencies adjacencies = vpnInterface.getAugmentation(Adjacencies.class);
        List<Adjacency> adjList = (adjacencies != null) ? adjacencies.getAdjacency() : new ArrayList<>();
        String prefix = null;
        long label = 0;
        List<String> nhList = new ArrayList<>();
        boolean isNextHopRemoveReqd = false;
        BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnInterface.getVpnInstanceName());
        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnInterface.getVpnInstanceName());

        if (adjList != null) {
            List<Adjacency> value = new ArrayList<>();
            LOG.trace("AdjacencyList for interface {} is {}", vpnInterface, adjList);
            for (Adjacency adj : adjList) {
                String rd = adj.getVrfId();
                rd = (rd != null) ? rd : vpnInterface.getVpnInstanceName();
                prefix = adj.getIpAddress();
                // If TEP is deleted , then only remove the nexthop from
                // primary adjacency.
                if (adj.getMacAddress() != null && !adj.getMacAddress().isEmpty()) {
                    List<String> nextHopList = adj.getNextHopIpList();
                    // If nextHopList is already cleaned , no need to modify again
                    if ((nextHopList != null) & (!nextHopList.isEmpty())) {
                        label = adj.getLabel();
                        isNextHopRemoveReqd = true;
                        value.add(new AdjacencyBuilder(adj).setNextHopIpList(nhList).build());
                        Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);

                        VpnInterface opInterface =
                            new VpnInterfaceBuilder(vpnInterface).setKey(new VpnInterfaceKey(vpnInterface.getName()))
                                .addAugmentation(Adjacencies.class, aug).build();
                        InstanceIdentifier<VpnInterface> interfaceId =
                            VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName());
                        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface);
                    }
                }

                if (isNextHopRemoveReqd) {
                    LOG.info("Updating label mapper : label {} dpn {} prefix {} nexthoplist {} vpnid {} rd {}", label,
                        srcDpnId, prefix, nhList, vpnId, rd);
                    updateLabelMapper(label, nhList);

                    // Update the VRF entry with emtpy nextHop
                    fibManager.updateFibEntry(dataBroker, primaryRd, prefix, new ArrayList<>() /* empty */, label, null);


                    //Get the list of VPN's importing this route(prefix) .
                    // Then update the VRF entry with nhList
                    List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                        getVpnsImportingMyRoute(vpnInterface.getVpnInstanceName());
                    for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                        String vpnRd = vpn.getVrfId();
                        if (vpnRd != null) {
                            LOG.debug("Exporting route with rd {} prefix {} nhList {} label {} to VPN {}", vpnRd,
                                prefix, nhList, label, vpn);
                            fibManager.updateFibEntry(dataBroker, vpnRd, prefix, nhList, label, null);
                        }
                    }

                    // Withdraw prefix from BGP only for external vpn.
                    try {
                        if (!rd.equalsIgnoreCase(vpnInterface.getVpnInstanceName())) {
                            bgpManager.withdrawPrefix(rd, prefix);
                        }
                    } catch (Exception ex) {
                        LOG.error("Exception when withdrawing prefix {} on rd {} as {}", prefix, rd, ex);
                    }
                }
            }
        }
    }

    private List<VpnInstanceOpDataEntry> getVpnsImportingMyRoute(final String vpnName) {
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = new ArrayList<>();

        final String vpnRd = VpnUtil.getVpnRd(dataBroker, vpnName);
        final VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(dataBroker, vpnRd);
        if (vpnInstanceOpDataEntry == null) {
            LOG.debug("Could not retrieve vpn instance op data for {} to check for vpns importing the routes", vpnName);
            return vpnsToImportRoute;
        }

        Predicate<VpnInstanceOpDataEntry> excludeVpn = input -> {
            if (input.getVpnInstanceName() == null) {
                LOG.error("Received vpn instance without identity");
                return false;
            }
            return !input.getVpnInstanceName().equals(vpnName);
        };

        Predicate<VpnInstanceOpDataEntry> matchRTs = input -> {
            Iterable<String> commonRTs =
                intersection(getRts(vpnInstanceOpDataEntry, VpnTarget.VrfRTType.ExportExtcommunity),
                    getRts(input, VpnTarget.VrfRTType.ImportExtcommunity));
            return Iterators.size(commonRTs.iterator()) > 0;
        };

        vpnsToImportRoute = VpnUtil.getAllVpnInstanceOpData(dataBroker)
                .stream()
                .filter(excludeVpn)
                .filter(matchRTs)
                .collect(Collectors.toList());
        return vpnsToImportRoute;
    }

    private List<VpnInstanceOpDataEntry> getVpnsExportingMyRoute(final String vpnName) {
        List<VpnInstanceOpDataEntry> vpnsToExportRoute = new ArrayList<>();

        String vpnRd = VpnUtil.getVpnRd(dataBroker, vpnName);
        final VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(dataBroker, vpnRd);
        if (vpnInstanceOpDataEntry == null) {
            LOG.debug("Could not retrieve vpn instance op data for {} to check for vpns exporting the routes", vpnName);
            return vpnsToExportRoute;
        }

        Predicate<VpnInstanceOpDataEntry> excludeVpn = input -> {
            if (input.getVpnInstanceName() == null) {
                LOG.error("Received vpn instance without identity");
                return false;
            }
            return !input.getVpnInstanceName().equals(vpnName);
        };

        Predicate<VpnInstanceOpDataEntry> matchRTs = input -> {
            Iterable<String> commonRTs =
                intersection(getRts(vpnInstanceOpDataEntry, VpnTarget.VrfRTType.ImportExtcommunity),
                    getRts(input, VpnTarget.VrfRTType.ExportExtcommunity));
            return Iterators.size(commonRTs.iterator()) > 0;
        };

        vpnsToExportRoute =
                VpnUtil.getAllVpnInstanceOpData(dataBroker).stream().filter(excludeVpn).filter(matchRTs).collect(
                        Collectors.toList());
        return vpnsToExportRoute;
    }

    private <T> Iterable<T> intersection(final Collection<T> collection1, final Collection<T> collection2) {
        Set<T> intersection = new HashSet<>(collection1);
        intersection.retainAll(collection2);
        return intersection;
    }

    private List<String> getRts(VpnInstanceOpDataEntry vpnInstance, VpnTarget.VrfRTType rtType) {
        String name = vpnInstance.getVpnInstanceName();
        List<String> rts = new ArrayList<>();
        VpnTargets targets = vpnInstance.getVpnTargets();
        if (targets == null) {
            LOG.trace("vpn targets not available for {}", name);
            return rts;
        }
        List<VpnTarget> vpnTargets = targets.getVpnTarget();
        if (vpnTargets == null) {
            LOG.trace("vpnTarget values not available for {}", name);
            return rts;
        }
        for (VpnTarget target : vpnTargets) {
            //TODO: Check for RT type is Both
            if (target.getVrfRTType().equals(rtType) || target.getVrfRTType().equals(VpnTarget.VrfRTType.Both)) {
                String rtValue = target.getVrfRTValue();
                rts.add(rtValue);
            }
        }
        return rts;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    void handleVpnsExportingRoutes(String vpnName, String vpnRd) {
        List<VpnInstanceOpDataEntry> vpnsToExportRoute = getVpnsExportingMyRoute(vpnName);
        for (VpnInstanceOpDataEntry vpn : vpnsToExportRoute) {
            String rd = vpn.getVrfId();
            List<VrfEntry> vrfEntries = VpnUtil.getAllVrfEntries(dataBroker, vpn.getVrfId());
            WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
            if (vrfEntries != null) {
                for (VrfEntry vrfEntry : vrfEntries) {
                    try {
                        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.STATIC) {
                            continue;
                        }
                        String prefix = vrfEntry.getDestPrefix();
                        SubnetRoute route = vrfEntry.getAugmentation(SubnetRoute.class);
                        for (RoutePaths routePath : vrfEntry.getRoutePaths()) {
                            String nh = routePath.getNexthopAddress();
                            int label = routePath.getLabel().intValue();
                            if (route != null) {
                                LOG.info(
                                    "Importing subnet route fib entry rd {} prefix {} nexthop {} label {} to vpn {}",
                                    vpnRd, prefix, nh, label, vpn.getVpnInstanceName());
                                importSubnetRouteForNewVpn(vpnRd, prefix, nh, (int) label, route, writeConfigTxn);
                            } else {
                                LOG.info("Importing fib entry rd {} prefix {} nexthop {} label {} to vpn {}", vpnRd,
                                    prefix, nh, label, vpn.getVpnInstanceName());
                                fibManager.addOrUpdateFibEntry(dataBroker, vpnRd, prefix,
                                    Collections.singletonList(nh), (int) label,
                                    RouteOrigin.SELF_IMPORTED, writeConfigTxn);
                            }
                        }
                    } catch (Exception e) {
                        LOG.error(
                            "Exception occurred while importing route with prefix {} route-path {} from vpn {} "
                                + "to vpn {}",
                            vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(),
                            vpn.getVpnInstanceName(), vpnName);
                    }
                }
                writeConfigTxn.submit();
            } else {
                LOG.info("No vrf entries to import from vpn {} with rd {}", vpn.getVpnInstanceName(), vpn.getVrfId());
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addPrefixToBGP(String vpnName, String rd, String prefix, List<String> nextHopList, long label,
                                RouteOrigin origin, WriteTransaction writeConfigTxn) {
        try {
            LOG.info("ADD: Adding Fib entry rd {} prefix {} nextHop {} label {}", rd, prefix, nextHopList, label);
            fibManager.addOrUpdateFibEntry(dataBroker,VpnUtil.getPrimaryRd(dataBroker, vpnName), prefix,
                    nextHopList, (int) label, origin, writeConfigTxn);
            LOG.info("ADD: Added Fib entry rd {} prefix {} nextHop {} label {}", rd, prefix, nextHopList, label);
            // Advertize the prefix to BGP only if nexthop ip is available
            if (nextHopList != null && !nextHopList.isEmpty()) {
                bgpManager.advertisePrefix(rd, prefix, nextHopList, (int)label);
            } else {
                LOG.warn("NextHopList is null/empty. Hence rd {} prefix {} is not advertised to BGP", rd, prefix);
            }
        } catch (Exception e) {
            LOG.error("Add prefix failed", e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void remove(InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
        LOG.trace("Remove event - key: {}, value: {}" ,identifier, vpnInterface );
        LOG.info("VPN Interface remove event - intfName {}" ,vpnInterface.getName());
        removeInterfaceFromUnprocessedList(identifier, vpnInterface);

        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();
        InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);

        final Optional<VpnInterface> optVpnInterface =
                VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, interfaceId);
        Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
        if (optVpnInterface.isPresent()) {
            BigInteger dpnId = BigInteger.ZERO;
            Boolean dpnIdRetrieved = Boolean.FALSE;
            if (interfaceState != null) {
                try {
                    dpnId = InterfaceUtils.getDpIdFromInterface(interfaceState);
                    dpnIdRetrieved = Boolean.TRUE;
                } catch (Exception e) {
                    LOG.error(
                        "Unable to retrieve dpnId from interface operational data store for interface {}. Fetching "
                            + "from vpn interface op data store. ",
                        interfaceName, e);
                }
            } else {
                LOG.error("Unable to retrieve interfaceState for interface {} , quitting ", interfaceName);
                return;
            }
            final VpnInterface vpnOpInterface = optVpnInterface.get();
            if (dpnIdRetrieved == Boolean.FALSE) {
                LOG.info("dpnId for {} has not been retrieved yet. Fetching from vpn interface operational DS",
                    interfaceName);
                dpnId = vpnOpInterface.getDpnId();
            }
            final int ifIndex = interfaceState.getIfIndex();
            final BigInteger dpId = dpnId;
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
                () -> {
                    WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                    WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                    WriteTransaction writeInvTxn = dataBroker.newWriteOnlyTransaction();
                    processVpnInterfaceDown(dpId, interfaceName, ifIndex, false, true, writeConfigTxn, writeOperTxn,
                        writeInvTxn);
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(writeOperTxn.submit());
                    futures.add(writeConfigTxn.submit());
                    futures.add(writeInvTxn.submit());
                    return futures;
                });

        } else if (Boolean.TRUE.equals(vpnInterface.isIsRouterInterface())) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterface.getName(),
                () -> {
                    WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                    deleteFibEntryForRouterInterface(vpnInterface, writeConfigTxn);
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(writeConfigTxn.submit());
                    return futures;
                });
        } else {
            LOG.warn("VPN interface {} was unavailable in operational data store to handle remove event",
                interfaceName);
        }
    }

    protected void processVpnInterfaceDown(BigInteger dpId,
                                           String interfaceName,
                                           int lportTag,
                                           boolean isInterfaceStateDown,
                                           boolean isConfigRemoval,
                                           WriteTransaction writeConfigTxn,
                                           WriteTransaction writeOperTxn,
                                           WriteTransaction writeInvTxn) {
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        if (!isInterfaceStateDown) {
            VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(dataBroker, interfaceName);
            if (vpnInterface == null) {
                LOG.info(
                    "Unable to process delete/down for interface {} as it is not available in operational data store",
                    interfaceName);
                return;
            } else {
                final String vpnName = vpnInterface.getVpnInstanceName();
                final long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
                if (!vpnInterface.isScheduledForRemove()) {
                    VpnUtil.scheduleVpnInterfaceForRemoval(dataBroker, interfaceName, dpId, vpnName, Boolean.TRUE,
                            writeOperTxn);
                    removeAdjacenciesFromVpn(dpId, lportTag, interfaceName, vpnInterface.getVpnInstanceName(),
                            vpnId, writeConfigTxn, writeInvTxn);
                    if (interfaceManager.isExternalInterface(interfaceName)) {
                        processExternalVpnInterface(vpnInterface, vpnId, dpId, lportTag, writeInvTxn,
                                NwConstants.DEL_FLOW);
                    }
                    LOG.info("Unbinding vpn service from interface {} ", interfaceName);
                    unbindService(dpId, vpnName, interfaceName, lportTag, isInterfaceStateDown, isConfigRemoval);

                } else {
                    LOG.info("Unbinding vpn service for interface {} has already been scheduled by a different event ",
                            interfaceName);
                    return;
                }
            }
        } else {
            // Interface is retained in the DPN, but its Link Down.
            // Only withdraw the prefixes for this interface from BGP
            VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(dataBroker, interfaceName);
            if (vpnInterface == null) {
                LOG.info(
                    "Unable to withdraw adjacencies for vpn interface {} from BGP as it is not available in "
                        + "operational data store",
                    interfaceName);
                return;
            } else {
                withdrawAdjacenciesForVpnFromBgp(identifier, vpnInterface);
            }
        }
    }

    private void removeAdjacenciesFromVpn(final BigInteger dpnId, final int lportTag, final String interfaceName,
                                          final String vpnName, final long vpnId, WriteTransaction writeConfigTxn,
                                          final WriteTransaction writeInvTxn) {
        //Read NextHops
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);

        String rd = VpnUtil.getVpnRd(dataBroker, vpnName);
        LOG.trace("removeAdjacenciesFromVpn: For interface {} RD recovered for vpn {} as rd {}", interfaceName,
            vpnName, rd);
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    List<String> nhList = new ArrayList<>();
                    if (!nextHop.isPrimaryAdjacency()) {
                        // This is either an extra-route (or) a learned IP via subnet-route
                        String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
                        if (nextHopIp == null || nextHopIp.isEmpty()) {
                            LOG.warn("Unable to obtain nextHopIp for extra-route/learned-route in rd {} prefix {}",
                                rd, nextHop.getIpAddress());
                        } else {
                            nhList = Collections.singletonList(nextHopIp);
                        }
                    } else {
                        // This is a primary adjacency
                        nhList = nextHop.getNextHopIpList();
                        final Uuid subnetId = nextHop.getSubnetId();
                        setupGwMacIfRequired(dpnId, vpnName, interfaceName, vpnId, subnetId,
                                writeInvTxn, NwConstants.DEL_FLOW);
                        removeArpResponderFlow(dpnId, lportTag, subnetId, writeInvTxn);
                    }

                    if (!nhList.isEmpty()) {
                        if (rd.equals(vpnName)) {
                            //this is an internal vpn - the rd is assigned to the vpn instance name;
                            //remove from FIB directly
                            for (String nh : nhList) {
                                fibManager.removeOrUpdateFibEntry(dataBroker, vpnName, nextHop.getIpAddress(), nh,
                                    writeConfigTxn);
                            }
                        } else {
                            List<VpnInstanceOpDataEntry> vpnsToImportRoute = getVpnsImportingMyRoute(vpnName);
                            for (String nh : nhList) {
                                //IRT: remove routes from other vpns importing it
                                removePrefixFromBGP(rd, nextHop.getIpAddress(), nh, writeConfigTxn);
                                for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                                    String vpnRd = vpn.getVrfId();
                                    if (vpnRd != null) {
                                        LOG.info("Removing Exported route with rd {} prefix {} from VPN {}", vpnRd,
                                            nextHop.getIpAddress(), vpn.getVpnInstanceName());
                                        fibManager.removeOrUpdateFibEntry(dataBroker, vpnRd, nextHop.getIpAddress(), nh,
                                            writeConfigTxn);
                                    }
                                }
                            }
                        }
                    } else {
                        fibManager.removeFibEntry(dataBroker, rd, nextHop.getIpAddress(), writeConfigTxn);
                    }

                    String ip = nextHop.getIpAddress().split("/")[0];
                    LearntVpnVipToPort vpnVipToPort = VpnUtil.getLearntVpnVipToPort(dataBroker, vpnName, ip);
                    if (vpnVipToPort != null) {
                        LOG.trace(
                                "VpnInterfaceManager removing adjacency for Interface {} ip {} from VpnPortData Entry",
                                vpnVipToPort.getPortName(), ip);
                        VpnUtil.removeLearntVpnVipToPort(dataBroker, vpnName, ip);
                    }
                }
            }
        }
    }

    private void addArpResponderFlow(final BigInteger dpId, final int lportTag, final String vpnName,
                                     final long vpnId, final String ifName, final Uuid subnetId,
                                     final WriteTransaction writeInvTxn) {
        LOG.trace("Creating the ARP Responder flow for VPN Interface {}",ifName);
        final Optional<String> gatewayIp = VpnUtil.getVpnSubnetGatewayIp(dataBroker, subnetId);
        if (gatewayIp.isPresent()) {
            String gwIp = gatewayIp.get();
            LOG.trace("VPN Interface Adjacency Subnet Gateway IP {}", gwIp);
            VpnPortipToPort gwPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnName, gwIp);
            //Check if a router gateway interface is available for the subnet gw is so then use Router interface
            // else use connected interface
            final String subNetGwMac = (gwPort != null && gwPort.isSubnetIp())
                ? gwPort.getMacAddress() : InterfaceUtils.getMacAddressForInterface(dataBroker, ifName).get();
            LOG.debug("VPN Interface Subnet Gateway MAC for {} interface to be used for ARPResponder is {}",
                    (gwPort != null && gwPort.isSubnetIp()) ? "Router" : "Connected", subNetGwMac);
            final String flowId = ArpResponderUtil.getFlowID(lportTag, gwIp);
            List<Action> actions = ArpResponderUtil.getActions(ifaceMgrRpcService, ifName, gwIp, subNetGwMac);
            ArpResponderUtil.installFlow(mdsalManager, writeInvTxn, dpId, flowId, flowId,
                    NwConstants.DEFAULT_ARP_FLOW_PRIORITY, ArpResponderUtil.generateCookie(lportTag, gwIp),
                    ArpResponderUtil.getMatchCriteria(lportTag, vpnId, gwIp),
                    Collections.singletonList(MDSALUtil.buildApplyActionsInstruction(actions)));
            LOG.trace("Installed the ARP Responder flow for VPN Interface {}", ifName);
        }
    }

    private void removeArpResponderFlow(final BigInteger dpId, final int lportTag, final Uuid subnetUuid,
                                        final WriteTransaction writeInvTxn) {
        final Optional<String> gwIp = VpnUtil.getVpnSubnetGatewayIp(dataBroker, subnetUuid);
        if (gwIp.isPresent()) {
            LOG.trace("VPNInterface adjacency Gsteway IP {} for ARP Responder removal", gwIp.get());
            final String flowId = ArpResponderUtil.getFlowID(lportTag, gwIp.get());
            ArpResponderUtil.removeFlow(mdsalManager, writeInvTxn, dpId, flowId);
        }
    }

    private void unbindService(BigInteger dpId, String vpnInstanceName, final String vpnInterfaceName,
                               int lportTag, boolean isInterfaceStateDown, boolean isConfigRemoval) {
        if (!isInterfaceStateDown && isConfigRemoval) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(vpnInterfaceName,
                () -> {
                    WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                    writeTxn.delete(LogicalDatastoreType.CONFIGURATION,
                        InterfaceUtils.buildServiceId(vpnInterfaceName,
                            ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                NwConstants.L3VPN_SERVICE_INDEX)));

                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(writeTxn.submit());
                    return futures;
                });
        }
    }

    private void setupGwMacIfRequired(BigInteger dpId, String vpnInstanceName, final String vpnInterfaceName,
                                      long vpnId, final Uuid subnetUuid, WriteTransaction writeInvTxn,
                                      int addOrRemove) {
        // check for router is present for the given vpn interface, if present return it immediately and
        // do not need to proceed with adding/removing interface mac as l3_gwmac_table flow entry
        Optional<VpnPortipToPort> routerInterfaceOptional = VpnUtil.getRouterInterfaceForVpnInterface(dataBroker,
                vpnInterfaceName, vpnInstanceName, subnetUuid);
        if (routerInterfaceOptional.isPresent() && routerInterfaceOptional.get().getMacAddress() != null ) {
            return;
        }
        VpnUtil.setupGwMacIfExternalVpn(dataBroker, mdsalManager, dpId, vpnInterfaceName,
                vpnId, writeInvTxn, addOrRemove);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removePrefixFromBGP(String rd, String prefix, String nextHop, WriteTransaction writeConfigTxn) {
        try {
            LOG.info("VPN WITHDRAW: Removing Fib Entry rd {} prefix {}", rd, prefix);
            fibManager.removeOrUpdateFibEntry(dataBroker, rd, prefix, nextHop, writeConfigTxn);
            bgpManager.withdrawPrefix(rd, prefix); // TODO: Might be needed to include nextHop here
            LOG.info("VPN WITHDRAW: Removed Fib Entry rd {} prefix {}", rd, prefix);
        } catch (Exception e) {
            LOG.error("Delete prefix failed", e);
        }
    }

    @Override
    protected void update(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface original,
        final VpnInterface update) {
        LOG.trace("Updating VPN Interface : key {},  original value={}, update value={}", identifier, original, update);
        LOG.info("VPN Interface update event - intfName {}", update.getName());
        final String oldVpnName = original.getVpnInstanceName();
        final String newVpnName = update.getVpnInstanceName();
        final BigInteger dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, update.getName());
        final UpdateData updateData = new UpdateData(identifier, original, update);
        final Adjacencies origAdjs = original.getAugmentation(Adjacencies.class);
        final List<Adjacency> oldAdjs = (origAdjs != null && origAdjs.getAdjacency()
            != null) ? origAdjs.getAdjacency() : new ArrayList<>();
        final Adjacencies updateAdjs = update.getAugmentation(Adjacencies.class);
        final List<Adjacency> newAdjs = (updateAdjs != null && updateAdjs.getAdjacency()
            != null) ? updateAdjs.getAdjacency() : new ArrayList<>();

        //handles switching between <internal VPN - external VPN>
        if (oldVpnName != null && !oldVpnName.equals(newVpnName)) {
            vpnInterfacesUpdateQueue.add(updateData);
            LOG.trace("UpdateData on VPNInterface {} update upon VPN swap added to update queue",
                updateData.getOriginal().getName());
            return;
        }
        final DataStoreJobCoordinator vpnInfAdjUpdateDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        vpnInfAdjUpdateDataStoreCoordinator.enqueueJob("VPNINTERFACE-" + update.getName(),
            () -> {
                WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                //handle both addition and removal of adjacencies
                //currently, new adjacency may be an extra route
                if (!oldAdjs.equals(newAdjs)) {
                    for (Adjacency adj : newAdjs) {
                        if (oldAdjs.contains(adj)) {
                            oldAdjs.remove(adj);
                        } else {
                            // add new adjacency - right now only extra route will hit this path
                            addNewAdjToVpnInterface(identifier, adj, dpnId, writeOperTxn, writeConfigTxn);
                        }
                    }
                    for (Adjacency adj : oldAdjs) {
                        delAdjFromVpnInterface(identifier, adj, dpnId, writeOperTxn, writeConfigTxn);
                    }
                }
                futures.add(writeOperTxn.submit());
                futures.add(writeConfigTxn.submit());
                return futures;
            });
    }

    class VpnInterfaceUpdateTimerTask extends TimerTask {

        @Override
        public void run() {
            List<UpdateData> processQueue = new ArrayList<>();
            List<VpnInterface> vpnInterfaceList = new ArrayList<>();
            vpnInterfacesUpdateQueue.drainTo(processQueue);
            for (UpdateData updData : processQueue) {
                remove(updData.getIdentifier(), updData.getOriginal());
                LOG.trace("Processed Remove for update on VPNInterface {} upon VPN swap",
                    updData.getOriginal().getName());
                vpnInterfaceList.add(updData.getOriginal());
            }

            /* Decide the max-wait time based on number of VpnInterfaces.
            *  max-wait-time is num-of-interface * 4seconds (random choice).
            *  Every 2sec poll VpnToDpnList. If VpnInterface is removed ,
            *  remove it from vpnInterfaceList.
            */

            int maxWaitTime =
                vpnInterfaceList.size() * (int) (VpnConstants.PER_INTERFACE_MAX_WAIT_TIME_IN_MILLISECONDS / 1000);
            int waitTime = 2;
            Iterator<VpnInterface> vpnInterfaceIterator = vpnInterfaceList.iterator();
            VpnInterface vpnInterface;
            while (waitTime < maxWaitTime) {
                try {
                    Thread.sleep(2000); // sleep for 2sec
                } catch (InterruptedException e) {
                    // Ignored
                }

                while (vpnInterfaceIterator.hasNext()) {
                    vpnInterface = vpnInterfaceIterator.next();
                    if (!VpnUtil.isVpnIntfPresentInVpnToDpnList(dataBroker, vpnInterface)) {
                        vpnInterfaceIterator.remove();
                    }
                }
                if (vpnInterfaceList.size() == 0) {
                    LOG.trace("All VpnInterfaces are successfully removed from OLD VPN after time {}", waitTime);
                    break;
                }
                waitTime += 2; //Increment linearly by 2sec.
            }

            if (vpnInterfaceList.size() > 0) {
                LOG.error("VpnInterfacesList {} not removed from old Vpn even after waiting {}", vpnInterfaceList,
                    waitTime);
            }

            for (UpdateData updData : processQueue) {
                if (vpnInterfaceList.contains(updData.getOriginal())) {
                    LOG.warn("Failed to swap VpnInterfaces {} to target VPN {}", updData.getOriginal(),
                        updData.getUpdate().getVpnInstanceName());
                    continue;
                }
                final Adjacencies origAdjs = updData.getOriginal().getAugmentation(Adjacencies.class);
                final List<Adjacency> oldAdjs = (origAdjs != null && origAdjs.getAdjacency() != null)
                    ? origAdjs.getAdjacency() : new ArrayList<>();
                final Adjacencies updateAdjs = updData.getUpdate().getAugmentation(Adjacencies.class);
                final List<Adjacency> newAdjs = (updateAdjs != null && updateAdjs.getAdjacency() != null)
                    ? updateAdjs.getAdjacency() : new ArrayList<>();
                addVpnInterface(updData.getIdentifier(), updData.getUpdate(), oldAdjs, newAdjs);
                LOG.trace("Processed Add for update on VPNInterface {} upon VPN swap",
                    updData.getUpdate().getName());
            }
        }
    }

    private String getErrorText(Collection<RpcError> errors) {
        StringBuilder errorText = new StringBuilder();
        for (RpcError error : errors) {
            errorText.append(",").append(error.getErrorType()).append("-")
                .append(error.getMessage());
        }
        return errorText.toString();
    }

    private void addToLabelMapper(Long label, BigInteger dpnId, String prefix, List<String> nextHopIpList, Long vpnId,
        String vpnInterfaceName, Long elanTag, boolean isSubnetRoute, String rd,
        WriteTransaction writeOperTxn) {
        Preconditions.checkNotNull(label, "label cannot be null or empty!");
        Preconditions.checkNotNull(prefix, "prefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnId, "vpnId cannot be null or empty!");
        Preconditions.checkNotNull(rd, "rd cannot be null or empty!");
        if (!isSubnetRoute) {
            // NextHop must be present for non-subnetroute entries
            Preconditions.checkNotNull(nextHopIpList, "nextHopIp cannot be null or empty!");
        }
        LOG.info("Adding to label mapper : label {} dpn {} prefix {} nexthoplist {} vpnid {} vpnIntfcName {} rd {}",
            label, dpnId, prefix, nextHopIpList, vpnId, vpnInterfaceName, rd);
        if (dpnId != null) {
            InstanceIdentifier<LabelRouteInfo> lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
                .child(LabelRouteInfo.class, new LabelRouteInfoKey((long) label)).build();
            LabelRouteInfoBuilder lriBuilder = new LabelRouteInfoBuilder();
            lriBuilder.setLabel(label).setDpnId(dpnId).setPrefix(prefix)
                    .setNextHopIpList(nextHopIpList).setParentVpnid(vpnId)
                    .setIsSubnetRoute(isSubnetRoute);
            if (elanTag != null) {
                lriBuilder.setElanTag(elanTag);
            }
            if (vpnInterfaceName != null) {
                lriBuilder.setVpnInterfaceName(vpnInterfaceName);
            }
            lriBuilder.setParentVpnRd(rd);
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(dataBroker, rd);
            if (vpnInstanceOpDataEntry != null) {
                List<String> vpnInstanceNames = Collections.singletonList(vpnInstanceOpDataEntry.getVpnInstanceName());
                lriBuilder.setVpnInstanceList(vpnInstanceNames);
            }
            LabelRouteInfo lri = lriBuilder.build();
            LOG.trace("Adding route info to label map: {}", lri);
            if (writeOperTxn != null) {
                writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, lriIid, lri, true);
            } else {
                VpnUtil.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL, lriIid, lri);
            }
        } else {
            LOG.trace("Can't add entry to label map for lable {},dpnId is null", label);
        }
    }

    private void updateLabelMapper(Long label, List<String> nextHopIpList) {
        Preconditions.checkNotNull(label, "label cannot be null or empty!");

        InstanceIdentifier<LabelRouteInfo> lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
            .child(LabelRouteInfo.class, new LabelRouteInfoKey((long) label)).build();
        Optional<LabelRouteInfo> opResult = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, lriIid);
        if (opResult.isPresent()) {
            LabelRouteInfo labelRouteInfo =
                new LabelRouteInfoBuilder(opResult.get()).setNextHopIpList(nextHopIpList).build();
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, lriIid, labelRouteInfo);
        }
    }

    public void addSubnetRouteFibEntryToDS(String rd, String vpnName, String prefix, String nextHop, int label,
        long elantag, BigInteger dpnId, WriteTransaction writeTxn) {
        SubnetRoute route = new SubnetRouteBuilder().setElantag(elantag).build();
        RouteOrigin origin = RouteOrigin.CONNECTED; // Only case when a route is considered as directly connected
        RoutePaths routePaths = VpnHelper.buildRoutePath(nextHop, label);

        VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setRoutePaths(Collections.singletonList(routePaths))
                .setOrigin(origin.getValue())
                .addAugmentation(SubnetRoute.class, route).build();

        LOG.debug("Created vrfEntry for {} nexthop {} label {} and elantag {}", prefix, nextHop, label, elantag);

        //TODO: What should be parentVpnId? Get it from RD?
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        addToLabelMapper((long) label, dpnId, prefix, Collections.singletonList(nextHop), vpnId, null, elantag, true,
                rd, null);
        InstanceIdentifier<VrfEntry> vrfEntryId =
            InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class, new VrfTablesKey(rd))
                .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> entry = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

        if (!entry.isPresent()) {
            List<VrfEntry> vrfEntryList = Collections.singletonList(vrfEntry);

            InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
            InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

            VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).setVrfEntry(vrfEntryList).build();

            if (writeTxn != null) {
                writeTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew, true);
            } else {
                VpnUtil.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
            }
        } else { // Found in MDSAL database
            if (writeTxn != null) {
                writeTxn.put(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
            } else {
                VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
            }
            LOG.debug("Updated vrfEntry for {} nexthop {} label {}", prefix, nextHop, label);
        }

        List<VpnInstanceOpDataEntry> vpnsToImportRoute = getVpnsImportingMyRoute(vpnName);
        if (vpnsToImportRoute.size() > 0) {
            VrfEntry importingVrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setRoutePaths(Collections.singletonList(routePaths))
                    .setOrigin(RouteOrigin.SELF_IMPORTED.getValue())
                    .addAugmentation(SubnetRoute.class, route).build();
            List<VrfEntry> importingVrfEntryList = Collections.singletonList(importingVrfEntry);
            for (VpnInstanceOpDataEntry vpnInstance : vpnsToImportRoute) {
                LOG.info("Exporting subnet route rd {} prefix {} nexthop {} label {} to vpn {}", rd, prefix, nextHop,
                    label, vpnInstance.getVpnInstanceName());
                String importingRd = vpnInstance.getVrfId();
                InstanceIdentifier<VrfTables> importingVrfTableId =
                    InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class,
                        new VrfTablesKey(importingRd)).build();
                VrfTables importingVrfTable = new VrfTablesBuilder().setRouteDistinguisher(importingRd).setVrfEntry(
                    importingVrfEntryList).build();
                if (writeTxn != null) {
                    writeTxn.merge(LogicalDatastoreType.CONFIGURATION, importingVrfTableId, importingVrfTable, true);
                } else {
                    VpnUtil.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, importingVrfTableId,
                        importingVrfTable);
                }
            }
        }
    }

    public synchronized void importSubnetRouteForNewVpn(String rd, String prefix, String nextHop, int label,
        SubnetRoute route, WriteTransaction writeConfigTxn) {

        RouteOrigin origin = RouteOrigin.SELF_IMPORTED;
        RoutePaths routePaths = VpnHelper.buildRoutePath(nextHop, label);
        VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setRoutePaths(Collections.singletonList(routePaths))
                .setOrigin(origin.getValue())
                .addAugmentation(SubnetRoute.class, route).build();
        LOG.debug("Created vrfEntry for {} nexthop {} label {} and elantag {}", prefix, nextHop, label,
                route.getElantag());
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
    }

    public void deleteSubnetRouteFibEntryFromDS(String rd, String prefix, String vpnName) {
        fibManager.removeFibEntry(dataBroker, rd, prefix, null);
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = getVpnsImportingMyRoute(vpnName);
        for (VpnInstanceOpDataEntry vpnInstance : vpnsToImportRoute) {
            String importingRd = vpnInstance.getVrfId();
            LOG.info("Deleting imported subnet route rd {} prefix {} from vpn {}", rd, prefix,
                vpnInstance.getVpnInstanceName());
            fibManager.removeFibEntry(dataBroker, importingRd, prefix, null);
        }
    }

    protected void addNewAdjToVpnInterface(InstanceIdentifier<VpnInterface> identifier, Adjacency adj,
                                           BigInteger dpnId,WriteTransaction writeOperTxn,
                                           WriteTransaction writeConfigTxn) {

        Optional<VpnInterface> optVpnInterface = VpnUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, identifier);

        if (optVpnInterface.isPresent()) {
            VpnInterface currVpnIntf = optVpnInterface.get();
            String prefix = VpnUtil.getIpPrefix(adj.getIpAddress());
            String vpnName = currVpnIntf.getVpnInstanceName();
            String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
            InstanceIdentifier<Adjacencies> adjPath = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> optAdjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, adjPath);
            long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                    VpnUtil.getNextHopLabelKey(primaryRd, prefix));
            if (label == 0) {
                LOG.error(
                    "Unable to fetch label from Id Manager. Bailing out of adding new adjacency {} to vpn interface "
                        + "{} for vpn {}",
                    adj.getIpAddress(), currVpnIntf.getName(), currVpnIntf.getVpnInstanceName());
                return;
            }
            List<Adjacency> adjacencies;
            if (optAdjacencies.isPresent()) {
                adjacencies = optAdjacencies.get().getAdjacency();
            } else {
                //This code will not be hit since VM adjacency will always be there
                adjacencies = new ArrayList<>();
            }
            long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            AdjacencyBuilder adjBuilder = new AdjacencyBuilder(adj).setLabel(label)
                    .setNextHopIpList(adj.getNextHopIpList()).setIpAddress(prefix).setKey(new AdjacencyKey(prefix));
            if (adj.getNextHopIpList() != null && !adj.getNextHopIpList().isEmpty()) {
                RouteOrigin origin = adj.isPrimaryAdjacency() ? RouteOrigin.LOCAL : RouteOrigin.STATIC;
                String nh = adj.getNextHopIpList().get(0);
                synchronized (prefix.intern()) {
                    java.util.Optional<String> rdToAllocate = VpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(
                                    dataBroker, vpnId, prefix, vpnName, dpnId,adj, writeOperTxn);
                    if (rdToAllocate.isPresent()) {
                        adjBuilder.setVrfId(rdToAllocate.get());
                        addExtraRoute(vpnName, adj.getIpAddress(), nh,rdToAllocate.get(),
                                currVpnIntf.getVpnInstanceName(), (int) label,
                                origin, currVpnIntf.getName(), writeConfigTxn);
                    }
                }
            }
            adjacencies.add(adjBuilder.build());
            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencies);
            VpnInterface newVpnIntf =
                VpnUtil.getVpnInterface(currVpnIntf.getName(), currVpnIntf.getVpnInstanceName(), aug, dpnId,
                    currVpnIntf.isScheduledForRemove());

            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf, true);
        }
    }

    protected void delAdjFromVpnInterface(InstanceIdentifier<VpnInterface> identifier, Adjacency adj, BigInteger dpnId,
        WriteTransaction writeOperTxn, WriteTransaction writeConfigTxn) {
        Optional<VpnInterface> optVpnInterface = VpnUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, identifier);

        if (optVpnInterface.isPresent()) {
            VpnInterface currVpnIntf = optVpnInterface.get();

            InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> optAdjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
            if (optAdjacencies.isPresent()) {
                List<Adjacency> adjacencies = optAdjacencies.get().getAdjacency();

                if (!adjacencies.isEmpty()) {
                    String rd = VpnUtil.getPrimaryRd(dataBroker, currVpnIntf.getVpnInstanceName());
                    LOG.trace("Adjacencies are " + adjacencies);
                    Iterator<Adjacency> adjIt = adjacencies.iterator();
                    while (adjIt.hasNext()) {
                        Adjacency adjElem = adjIt.next();
                        if (adjElem.getIpAddress().equals(adj.getIpAddress())) {
                            adjIt.remove();

                            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencies);
                            VpnInterface newVpnIntf = VpnUtil.getVpnInterface(currVpnIntf.getName(),
                                currVpnIntf.getVpnInstanceName(),
                                aug, dpnId, currVpnIntf.isScheduledForRemove());

                            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf, true);
                            if (adj.getNextHopIpList() != null) {
                                for (String nh : adj.getNextHopIpList()) {
                                    delExtraRoute(adj.getIpAddress(), nh, rd, currVpnIntf.getVpnInstanceName(),
                                        currVpnIntf.getName(), writeConfigTxn);
                                }
                            }
                            break;
                        }

                    }
                }
            }
        }

    }

    protected void addExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID,
                                 int label, RouteOrigin origin, String intfName,
                                 WriteTransaction writeConfigTxn) {

        Boolean writeConfigTxnPresent = true;
        if (writeConfigTxn == null) {
            writeConfigTxnPresent = false;
            writeConfigTxn = dataBroker.newWriteOnlyTransaction();
        }

        //add extra route to vpn mapping; advertise with nexthop as tunnel ip
        VpnUtil.syncUpdate(
                dataBroker,
                LogicalDatastoreType.OPERATIONAL,
                VpnUtil.getVpnToExtrarouteIdentifier(vpnName, (rd != null) ? rd : routerID, destination),
                VpnUtil.getVpnToExtraroute(destination, Collections.singletonList(nextHop)));

        BigInteger dpnId = null;
        if (intfName != null && !intfName.isEmpty()) {
            dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.error(
                    "NextHop for interface {} is null / empty. Failed advertising extra route for rd {} prefix {}",
                    intfName, rd, destination);
                return;
            }
            nextHop = nextHopIp;
        }
        List<String> nextHopIpList = Collections.singletonList(nextHop);
        if (rd != null) {
            /* Label mapper is required only for BGP VPN and not for Internal VPN */
            addToLabelMapper((long) label, dpnId, destination, nextHopIpList, VpnUtil.getVpnId(dataBroker, routerID),
                intfName, null, false, rd, null);
        }

        // TODO: This is a limitation to be stated in docs. When configuring static route to go to
        // another VPN, there can only be one nexthop or, at least, the nexthop to the interVpnLink should be in
        // first place.
        Optional<InterVpnLink> optInterVpnLink = InterVpnLinkUtil.getInterVpnLinkByEndpointIp(dataBroker, nextHop);
        if (optInterVpnLink.isPresent()) {
            InterVpnLink interVpnLink = optInterVpnLink.get();
            // If the nexthop is the endpoint of Vpn2, then prefix must be advertised to Vpn1 in DC-GW, with nexthops
            // pointing to the DPNs where Vpn1 is instantiated. LFIB in these DPNS must have a flow entry, with lower
            // priority, where if Label matches then sets the lportTag of the Vpn2 endpoint and goes to LportDispatcher
            // This is like leaking one of the Vpn2 routes towards Vpn1
            boolean nexthopIsVpn2 = interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(nextHop);
            String srcVpnUuid = nexthopIsVpn2 ? interVpnLink.getSecondEndpoint().getVpnUuid().getValue()
                : interVpnLink.getFirstEndpoint().getVpnUuid().getValue();
            String dstVpnUuid = nexthopIsVpn2 ? interVpnLink.getFirstEndpoint().getVpnUuid().getValue()
                : interVpnLink.getSecondEndpoint().getVpnUuid().getValue();
            String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnUuid);
            long newLabel = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                VpnUtil.getNextHopLabelKey(dstVpnRd, destination));
            if (newLabel == 0) {
                LOG.error(
                    "Unable to fetch label from Id Manager. Bailing out of adding intervpnlink route for destination "
                        + "{}",
                    destination);
                return;
            }
            InterVpnLinkUtil.leakRoute(dataBroker, bgpManager, interVpnLink, srcVpnUuid, dstVpnUuid, destination,
                newLabel);
        } else {
            if (rd != null) {
                addPrefixToBGP(vpnName, rd, destination, nextHopIpList, label, origin, writeConfigTxn);
            } else {
                // ### add FIB route directly
                fibManager.addOrUpdateFibEntry(dataBroker, routerID, destination, nextHopIpList, label, origin,
                    writeConfigTxn);
            }
        }
        if (!writeConfigTxnPresent) {
            writeConfigTxn.submit();
        }
    }

    protected void delExtraRoute(String destination, String nextHop, String rd, String routerID, String intfName,
        WriteTransaction writeConfigTxn) {
        Boolean writeConfigTxnPresent = true;
        if (writeConfigTxn == null) {
            writeConfigTxnPresent = false;
            writeConfigTxn = dataBroker.newWriteOnlyTransaction();
        }
        if (intfName != null && !intfName.isEmpty()) {
            BigInteger dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.warn("NextHop for interface {} is null / empty. Failed advertising extra route for prefix {}",
                    intfName, destination);
            }
            nextHop = nextHopIp;
        }

        if (rd != null) {
            removePrefixFromBGP(rd, destination, nextHop, writeConfigTxn);
        } else {
            // ### add FIB route directly
            fibManager.removeOrUpdateFibEntry(dataBroker, routerID, destination, nextHop, writeConfigTxn);
        }
        if (!writeConfigTxnPresent) {
            writeConfigTxn.submit();
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
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} association model",
                vpnInterfaceName, routerName);
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
            List<RouterInterfaces> routerInterfaces = new ArrayList<>();
            routerInterfaces.add(routerInterface);
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
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} dissociation model",
                vpnInterfaceName, routerName);
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
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} dissociation model",
                vpnInterfaceName, routerName);
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

    protected void createFibEntryForRouterInterface(VpnInterface vpnInterface, String interfaceName,
                                                    WriteTransaction writeConfigTxn) {
        if (vpnInterface == null) {
            return;
        }
        String vpnName = vpnInterface.getVpnInstanceName();
        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
        List<Adjacency> adjs = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker, interfaceName);
        if (adjs == null) {
            LOG.info("VPN Interface {} of router addition failed as adjacencies for "
                + "this vpn interface could not be obtained", interfaceName);
            return;
        }
        for (Adjacency adj : adjs) {
            if (adj.isPrimaryAdjacency()) {
                String primaryInterfaceIp = adj.getIpAddress();
                String macAddress = adj.getMacAddress();
                String prefix = VpnUtil.getIpPrefix(primaryInterfaceIp);

                long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                        VpnUtil.getNextHopLabelKey(primaryRd, prefix));

                RouterInterface routerInt = new RouterInterfaceBuilder().setUuid(vpnName)
                    .setMacAddress(macAddress).setIpAddress(primaryInterfaceIp).build();

                fibManager.addFibEntryForRouterInterface(dataBroker, primaryRd,
                        prefix, routerInt, label, writeConfigTxn);
                return;
            }
        }
        LOG.trace("VPN Interface {} of router addition failed as primary adjacency for"
            + " this vpn interface could not be obtained", interfaceName);
    }

    protected void deleteFibEntryForRouterInterface(VpnInterface vpnInterface, WriteTransaction writeConfigTxn) {
        List<Adjacency> adjsList = new ArrayList<>();
        Adjacencies adjs = vpnInterface.getAugmentation(Adjacencies.class);
        if (adjs != null) {
            adjsList = adjs.getAdjacency();
            for (Adjacency adj : adjsList) {
                if (adj.isPrimaryAdjacency()) {
                    String primaryInterfaceIp = adj.getIpAddress();
                    String prefix = VpnUtil.getIpPrefix(primaryInterfaceIp);
                    String rd = VpnUtil.getVpnRd(dataBroker, vpnInterface.getVpnInstanceName());
                    fibManager.removeFibEntry(dataBroker, rd, prefix, writeConfigTxn);
                    return;
                }
            }
        }
    }

    private void processSavedInterface(UnprocessedVpnInterfaceData intefaceData) {
        addVpnInterface(intefaceData.identifier, intefaceData.vpnInterface, null, null);
    }

    private void addToUnprocessedVpnInterfaces(InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
        LOG.info("Saving unhandled vpn interface {} in vpn instance {}",
                 vpnInterface.getName(), vpnInterface.getVpnInstanceName());

        ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces = unprocessedVpnInterfaces
                .get(vpnInterface.getVpnInstanceName());
        if (vpnInterfaces == null) {
            vpnInterfaces = new ConcurrentLinkedQueue<>();
        }
        vpnInterfaces.add(new UnprocessedVpnInterfaceData(identifier, vpnInterface));
        unprocessedVpnInterfaces.put(vpnInterface.getVpnInstanceName(), vpnInterfaces);
    }

    private boolean isVpnInstanceReady(String vpnInstanceName) {
        String vpnRd = VpnUtil.getVpnRd(dataBroker, vpnInstanceName);
        if (vpnRd == null) {
            return false;
        }
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(dataBroker, vpnRd);

        return (vpnInstanceOpDataEntry != null);
    }

    public void processSavedInterfaces(String vpnInstanceName, boolean hasVpnInstanceCreatedSuccessfully) {
        synchronized (vpnInstanceName.intern()) {
            ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces =
                    unprocessedVpnInterfaces.get(vpnInstanceName);
            if (vpnInterfaces != null) {
                while (!vpnInterfaces.isEmpty()) {
                    UnprocessedVpnInterfaceData savedInterface = vpnInterfaces.poll();
                    LOG.info("Handle saved vpn interface {} in vpn instance {}",
                             savedInterface.vpnInterface.getName(), vpnInstanceName);

                    if (hasVpnInstanceCreatedSuccessfully) {
                        processSavedInterface(savedInterface);
                    } else {
                        LOG.error("Cannot process vpn interface {} in vpn instance {}",
                                savedInterface.vpnInterface.getName(), vpnInstanceName);
                    }
                }
            }
        }
    }

    private void removeInterfaceFromUnprocessedList(InstanceIdentifier<VpnInterface> identifier,
            VpnInterface vpnInterface) {
        synchronized (vpnInterface.getVpnInstanceName().intern()) {
            ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces =
                    unprocessedVpnInterfaces.get(vpnInterface.getVpnInstanceName());
            if (vpnInterfaces != null) {
                if (vpnInterfaces.remove(new UnprocessedVpnInterfaceData(identifier, vpnInterface))) {
                    LOG.info("Removed vpn interface {} in vpn instance {} from unprocessed list",
                              vpnInterface.getName(), vpnInterface.getVpnInstanceName());
                }
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
            super();
            this.identifier = identifier;
            this.vpnInterface = vpnInterface;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
            result = prime * result + ((vpnInterface == null) ? 0 : vpnInterface.hashCode());
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
}

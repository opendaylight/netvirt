/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.*;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.bgpmanager.api.RouteOrigin;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;

import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.genius.mdsalutil.*;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeHwvtep;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.LabelRouteMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRouteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.*;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
        .VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpResponseInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpResponseInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.*;

import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInterfaceManager extends AbstractDataChangeListener<VpnInterface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration, opListenerRegistration, tunnelInterfaceStateListenerRegistration;
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private IFibManager fibManager;
    private IMdsalApiManager mdsalManager;
    private OdlInterfaceRpcService ifaceMgrRpcService;
    private ItmRpcService itmProvider;
    private IdManagerService idManager;
    private OdlArputilService arpManager;
    private NeutronvpnService neuService;
    private VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private ConcurrentHashMap<String, Runnable> vpnIntfMap = new ConcurrentHashMap<String, Runnable>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private InterfaceStateChangeListener interfaceListener;
    private SubnetRouteInterfaceStateChangeListener subnetRouteInterfaceListener;
    private TunnelInterfaceStateListener tunnelInterfaceStateListener;
    private VpnInterfaceOpListener vpnInterfaceOpListener;
    private ArpNotificationHandler arpNotificationHandler;
    private DpnInVpnChangeListener dpnInVpnChangeListener;
    private NotificationPublishService notificationPublishService;
    private FibRpcService fibService;
    protected enum UpdateRouteAction {
        ADVERTISE_ROUTE, WITHDRAW_ROUTE
    }

    /**
     * Responsible for listening to data change related to VPN Interface
     * Bind VPN Service on the interface and informs the BGP service
     *
     * @param db - dataBroker service reference
     * @param bgpManager Used to advertise routes to the BGP Router
     * @param notificationService Used to subscribe to notification events
     */
    public VpnInterfaceManager(final DataBroker db, final IBgpManager bgpManager, NotificationService notificationService) {
        super(VpnInterface.class);
        broker = db;
        this.bgpManager = bgpManager;
        interfaceListener = new InterfaceStateChangeListener(db, this);
        subnetRouteInterfaceListener = new SubnetRouteInterfaceStateChangeListener(db, this);
        vpnInterfaceOpListener = new VpnInterfaceOpListener();
        arpNotificationHandler = new ArpNotificationHandler(this, broker);
        vpnSubnetRouteHandler = new VpnSubnetRouteHandler(broker, bgpManager, this);
        dpnInVpnChangeListener = new DpnInVpnChangeListener(broker);
        tunnelInterfaceStateListener = new TunnelInterfaceStateListener(broker, this);
        notificationService.registerNotificationListener(vpnSubnetRouteHandler);
        notificationService.registerNotificationListener(arpNotificationHandler);
        notificationService.registerNotificationListener(dpnInVpnChangeListener);
        registerListener(db);
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setIfaceMgrRpcService(OdlInterfaceRpcService ifMgrRpcService) {
        this.ifaceMgrRpcService = ifMgrRpcService;
        interfaceListener.setIfaceMgrRpcService(ifMgrRpcService);
    }

    public void setITMProvider(ItmRpcService itmProvider) {
        this.itmProvider = itmProvider;
    }


    public void setFibManager(IFibManager fibManager) {
        this.fibManager = fibManager;
    }

    public IFibManager getFibManager() {
        return this.fibManager;
    }


    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
        vpnSubnetRouteHandler.setIdManager(idManager);
    }

    public void setArpManager(OdlArputilService arpManager) {
        this.arpManager = arpManager;
    }

    void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }

    public void setNeutronvpnManager(NeutronvpnService neuService) { this.neuService = neuService; }

    public void setFibRpcService(FibRpcService fibService) {
        this.fibService = fibService;
    }

    public FibRpcService getFibRpcService() {
        return fibService;
    }

    public VpnSubnetRouteHandler getVpnSubnetRouteHandler() {
        return this.vpnSubnetRouteHandler;
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
                opListenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
            opListenerRegistration = null;
        }
        LOG.info("VPN Interface Manager Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), VpnInterfaceManager.this, DataChangeScope.SUBTREE);
            opListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                                                                   getWildCardPath(), vpnInterfaceOpListener, DataChangeScope.SUBTREE);
            tunnelInterfaceStateListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getTunnelInterfaceStateListenerPath(), tunnelInterfaceStateListener, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("VPN Service DataChange listener registration fail!", e);
            throw new IllegalStateException("VPN Service registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<StateTunnelList> getTunnelInterfaceStateListenerPath() {
        return InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class);
    }

    private InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> getInterfaceListenerPath() {
        return InstanceIdentifier.create(InterfacesState.class)
            .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.class);
    }

    @Override
    public void add(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface) {
        LOG.trace("VPN Interface add event - key: {}, value: {}" ,identifier, vpnInterface );
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface interfaceState =
            InterfaceUtils.getInterfaceStateFromOperDS(broker, interfaceName);
        if(interfaceState != null){
            try{
                final BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(interfaceState);
                final int ifIndex = interfaceState.getIfIndex();
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("VPNINTERFACE-"+ interfaceName,
                        new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                WriteTransaction writeTxn = broker.newWriteOnlyTransaction();
                                processVpnInterfaceUp(dpnId, vpnInterface, ifIndex, false, writeTxn);
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                futures.add(writeTxn.submit());
                                return futures;
                            }
                        });
            }catch (Exception e){
                LOG.error("Unable to retrieve dpnId from interface operational data store for interface {}. ", interfaceName, e);
                return;
            }
        } else {
            LOG.info("Handling addition of VPN interface {} skipped as interfaceState is not available", interfaceName);
        }
    }

    protected void processVpnInterfaceUp(final BigInteger dpId, VpnInterface vpnInterface,
                                         final int lPortTag, boolean isInterfaceUp, WriteTransaction writeTxn) {

        final String interfaceName = vpnInterface.getName();
        if (!isInterfaceUp) {
            final String vpnName = vpnInterface.getVpnInstanceName();
            LOG.info("Binding vpn service to interface {} ", interfaceName);
            long vpnId = VpnUtil.getVpnId(broker, vpnName);
            if (vpnId == VpnConstants.INVALID_ID) {
                LOG.trace("VpnInstance to VPNId mapping is not yet available, bailing out now.");
                return;
            }
            boolean waitForVpnInterfaceOpRemoval = false;
            int numAdjs = 0;
            VpnInterface opVpnInterface = null;
            synchronized (interfaceName.intern()) {
                opVpnInterface = VpnUtil.getOperationalVpnInterface(broker, vpnInterface.getName());
                if (opVpnInterface != null ) {
                    String opVpnName = opVpnInterface.getVpnInstanceName();
                    String primaryInterfaceIp = null;
                    if(opVpnName.equals(vpnName)) {
                        // Please check if the primary VRF Entry does not exist for VPNInterface
                        // If so, we have to process ADD, as this might be a DPN Restart with Remove and Add triggered
                        // back to back
                        // However, if the primary VRF Entry for this VPNInterface exists, please continue bailing out !
                        List<Adjacency> adjs = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(broker, interfaceName);
                        if (adjs == null) {
                            LOG.info("VPN Interface {} addition failed as adjacencies for this vpn interface could not be obtained", interfaceName);
                            return;
                        }
                        numAdjs = adjs.size();
                        for (Adjacency adj : adjs) {
                            if (adj.getMacAddress() != null && !adj.getMacAddress().isEmpty()) {
                                primaryInterfaceIp = adj.getIpAddress();
                                break;
                            }
                        }
                        if (primaryInterfaceIp == null) {
                            LOG.info("VPN Interface {} addition failed as primary adjacency for this vpn interface could not be obtained", interfaceName);
                            return;
                        }
                        // Get the rd of the vpn instance
                        String rd = getRouteDistinguisher(opVpnName);
                        VrfEntry vrf = VpnUtil.getVrfEntry(broker, rd, primaryInterfaceIp);
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
                    DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                    dataStoreCoordinator.enqueueJob((interfaceName + dpId.toString()),
                            new UpdateDpnToVpnWorker(dpId, vpnName, interfaceName, lPortTag, true));
                    return;
                }
            }

            // FIB didn't get a chance yet to clean up this VPNInterface
            // Let us give it a chance here !
            LOG.info("VPN Interface {} waiting for FIB to clean up! ", interfaceName);
            try {
                Runnable notifyTask = new VpnNotifyTask();
                vpnIntfMap.put(interfaceName, notifyTask);
                synchronized (notifyTask) {
                    try {
                        notifyTask.wait(VpnConstants.MAX_WAIT_TIME_IN_MILLISECONDS);
                    } catch (InterruptedException e) {
                    }
                }
            } finally {
                vpnIntfMap.remove(interfaceName);
            }

            opVpnInterface = VpnUtil.getOperationalVpnInterface(broker, interfaceName);
            if (opVpnInterface != null) {
                LOG.error("VPN Interface {} removal by FIB did not complete on time, bailing addition ...", interfaceName);
                return;
            }
            // VPNInterface got removed, proceed with Add
            synchronized (interfaceName.intern()) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob((interfaceName + dpId.toString()),
                        new UpdateDpnToVpnWorker(dpId, vpnName, interfaceName, lPortTag, true));
            }
        } else {
            synchronized (interfaceName.intern()) {
                // Interface is retained in the DPN, but its Link Up.
                // Advertise prefixes again for this interface to BGP
                advertiseAdjacenciesForVpnToBgp(dpId, VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()),
                        vpnInterface, writeTxn);
            }
        }
    }


    private class UpdateDpnToVpnWorker implements Callable<List<ListenableFuture<Void>>> {
        BigInteger dpnId;
        String vpnName;
        String interfaceName;
        boolean addToDpn;
        int lPortTag;

        public UpdateDpnToVpnWorker(BigInteger dpnId, String vpnName, String interfaceName,
                                    int lPortTag, boolean addToDpn) {
            this.dpnId= dpnId;
            this.vpnName = vpnName;
            this.interfaceName = interfaceName;
            this.lPortTag = lPortTag;
            this.addToDpn = addToDpn;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            WriteTransaction writeTxn = broker.newWriteOnlyTransaction();
            updateDpnDbs(dpnId, vpnName, interfaceName, addToDpn, writeTxn);
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeTxn.submit());
            ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
            Futures.addCallback(listenableFuture,
                    new UpdateDpnToVpnCallback(dpnId, vpnName, interfaceName, lPortTag, addToDpn));
            return futures;
        }
    }


    /**
     * JobCallback class is used as a future callback for
     * main and rollback workers to handle success and failure.
     */
    private class UpdateDpnToVpnCallback implements FutureCallback<List<Void>> {
        BigInteger dpnId;
        String vpnName;
        String interfaceName;
        boolean addToDpn;
        int lPortTag;

        public UpdateDpnToVpnCallback(BigInteger dpnId, String vpnName, String interfaceName,
                                      int lPortTag, boolean addToDpn) {
            this.dpnId= dpnId;
            this.vpnName = vpnName;
            this.interfaceName = interfaceName;
            this.lPortTag = lPortTag;
            this.addToDpn = addToDpn;
        }

        /**
         * @param voids
         * This implies that all the future instances have returned success. -- TODO: Confirm this
         */
        @Override
        public void onSuccess(List<Void> voids) {
            WriteTransaction writeTxn = broker.newWriteOnlyTransaction();
            bindService(dpnId, vpnName, interfaceName, lPortTag, writeTxn);
            processVpnInterfaceAdjacencies(dpnId, vpnName, interfaceName, writeTxn);
            writeTxn.submit();
        }

        /**
         *
         * @param throwable
         * This method is used to handle failure callbacks.
         * If more retry needed, the retrycount is decremented and mainworker is executed again.
         * After retries completed, rollbackworker is executed.
         * If rollbackworker fails, this is a double-fault. Double fault is logged and ignored.
         */

        @Override
        public void onFailure(Throwable throwable) {
            LOG.warn("Job: failed with exception: {}", throwable.getStackTrace());
        }
    }


    private void advertiseAdjacenciesForVpnToBgp(BigInteger dpnId, final InstanceIdentifier<VpnInterface> identifier,
                                                 VpnInterface intf, WriteTransaction writeTxn) {
        //Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);

        String rd = VpnUtil.getVpnRd(broker, intf.getVpnInstanceName());
        if (rd == null) {
            LOG.error("advertiseAdjacenciesForVpnFromBgp: Unable to recover rd for interface {} in vpn {}",
                    intf.getName(), intf.getVpnInstanceName());
            return;
        } else {
            if (rd.equals(intf.getVpnInstanceName())) {
                LOG.info("advertiseAdjacenciesForVpnFromBgp: Ignoring BGP advertisement for interface {} as it is in " +
                        "internal vpn{} with rd {}", intf.getName(), intf.getVpnInstanceName(), rd);

                return;
            }
        }
        LOG.info("advertiseAdjacenciesForVpnToBgp: Advertising interface {} in vpn {} with rd {} ", intf.getName(),
                intf.getVpnInstanceName(), rd);

        String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId);
        if (nextHopIp == null){
            LOG.trace("advertiseAdjacenciesForVpnToBgp: NextHop for interface {} is null, returning", intf.getName());
            return;
        }

        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    long label = nextHop.getLabel();
                    try {
                        LOG.info("VPN ADVERTISE: Adding Fib Entry rd {} prefix {} nexthop {} label {}", rd, nextHop.getIpAddress(), nextHopIp, label);
                        bgpManager.advertisePrefix(rd, nextHop.getIpAddress(), nextHopIp, (int)label);
                        LOG.info("VPN ADVERTISE: Added Fib Entry rd {} prefix {} nexthop {} label {}", rd, nextHop.getIpAddress(), nextHopIp, label);
                    } catch(Exception e) {
                        LOG.error("Failed to advertise prefix {} in vpn {} with rd {} for interface {} ",
                                nextHop.getIpAddress(), intf.getVpnInstanceName(), rd, intf.getName(), e);
                    }
                }
            }
        }
    }

    private void withdrawAdjacenciesForVpnFromBgp(final InstanceIdentifier<VpnInterface> identifier, VpnInterface intf) {
        //Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);

        String rd = VpnUtil.getVpnRd(broker, intf.getVpnInstanceName());
        if (rd == null) {
            LOG.error("withdrawAdjacenciesForVpnFromBgp: Unable to recover rd for interface {} in vpn {}",
                    intf.getName(), intf.getVpnInstanceName());
            return;
        } else {
            if (rd.equals(intf.getVpnInstanceName())) {
                LOG.info("withdrawAdjacenciesForVpnFromBgp: Ignoring BGP withdrawal for interface {} as it is in " +
                        "internal vpn{} with rd {}", intf.getName(), intf.getVpnInstanceName(), rd);
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
                    } catch(Exception e) {
                        LOG.error("Failed to withdraw prefix {} in vpn {} with rd {} for interface {} ",
                                nextHop.getIpAddress(), intf.getVpnInstanceName(), rd, intf.getName(), e);
                    }
                }
            }
        }
    }

    private void updateDpnDbs(BigInteger dpId, String vpnName, String interfaceName, boolean add, WriteTransaction writeTxn) {
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        if (dpId == null) {
            dpId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, interfaceName);
        }
        if(!dpId.equals(BigInteger.ZERO)) {
            if(add)
                updateMappingDbs(vpnId, dpId, interfaceName, vpnName, writeTxn);
            else
                removeFromMappingDbs(vpnId, dpId, interfaceName, vpnName, writeTxn);
        }
    }

    private void bindService(BigInteger dpId, String vpnInstanceName, String vpnInterfaceName, int lPortTag,
                             WriteTransaction writeTxn) {
        int priority = VpnConstants.DEFAULT_FLOW_PRIORITY;
        long vpnId = VpnUtil.getVpnId(broker, vpnInstanceName);

        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<Instruction>();

        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_FIB_TABLE, ++instructionKey));

        BoundServices
            serviceInfo =
            InterfaceUtils.getBoundServices(String.format("%s.%s.%s", "vpn",vpnInstanceName, vpnInterfaceName),
                                            VpnConstants.L3VPN_SERVICE_IDENTIFIER, priority,
                                            VpnConstants.COOKIE_VM_INGRESS_TABLE, instructions);
        writeTxn.put(LogicalDatastoreType.CONFIGURATION,
                InterfaceUtils.buildServiceId(vpnInterfaceName, VpnConstants.L3VPN_SERVICE_IDENTIFIER), serviceInfo, true);
        makeArpFlow(dpId, VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                vpnId, ArpReplyOrRequest.REQUEST, NwConstants.ADD_FLOW, writeTxn);
        makeArpFlow(dpId, VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                vpnId, ArpReplyOrRequest.REPLY, NwConstants.ADD_FLOW, writeTxn);

    }

    private void processVpnInterfaceAdjacencies(BigInteger dpnId, String vpnName, String interfaceName,
                                                WriteTransaction writeTxn) {
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);

        synchronized (interfaceName) {
            // Read NextHops
            InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, path);

            if (adjacencies.isPresent()) {
                List<Adjacency> nextHops = adjacencies.get().getAdjacency();
                List<Adjacency> value = new ArrayList<>();

                // Get the rd of the vpn instance
                String rd = getRouteDistinguisher(vpnName);

                String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId);
                if (nextHopIp == null){
                    LOG.error("NextHop for interface {} is null", interfaceName);
                    return;
                }

                List<VpnInstance> vpnsToImportRoute = getVpnsImportingMyRoute(vpnName);

                LOG.trace("NextHops for interface {} are {}", interfaceName, nextHops);
                for (Adjacency nextHop : nextHops) {
                    String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
                    long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                            VpnUtil.getNextHopLabelKey((rd == null) ? vpnName
                                    : rd, prefix));
                    List<String> adjNextHop = nextHop.getNextHopIpList();
                    value.add(new AdjacencyBuilder(nextHop).setLabel(label).setNextHopIpList(
                            (adjNextHop != null && !adjNextHop.isEmpty()) ? adjNextHop : Arrays.asList(nextHopIp))
                            .setIpAddress(prefix).setKey(new AdjacencyKey(prefix)).build());

                    if (nextHop.getMacAddress() != null && !nextHop.getMacAddress().isEmpty()) {
                        LOG.trace("Adding prefix {} to interface {} for vpn {}", prefix, interfaceName, vpnName);
                        writeTxn.merge(
                                LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(
                                        VpnUtil.getVpnId(broker, vpnName), prefix),
                                VpnUtil.getPrefixToInterface(dpnId, interfaceName, prefix), true);
                    } else {
                        //Extra route adjacency
                        LOG.trace("Adding prefix {} and nexthopList {} as extra-route for vpn", nextHop.getIpAddress(), nextHop.getNextHopIpList(), vpnName);
                        writeTxn.merge(
                                LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getVpnToExtrarouteIdentifier(
                                        (rd != null) ? rd : vpnName, nextHop.getIpAddress()),
                                VpnUtil.getVpnToExtraroute(nextHop.getIpAddress(), nextHop.getNextHopIpList()), true);
                    }
                }

                Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);

                VpnInterface opInterface = VpnUtil.getVpnInterface(interfaceName, vpnName, aug, dpnId, Boolean.FALSE);
                InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
                writeTxn.put(LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface, true);
                long vpnId = VpnUtil.getVpnId(broker, vpnName);

                for (Adjacency nextHop : aug.getAdjacency()) {
                    long label = nextHop.getLabel();
                    List<String> nextHopList = new ArrayList<>(nextHop.getNextHopIpList());
                    if (rd != null) {

                        addToLabelMapper(label, dpnId, nextHop.getIpAddress(), nextHopList, vpnId,
                                interfaceName, null,false, rd);
                        addPrefixToBGP(rd, nextHop.getIpAddress(), nextHopIp, label, writeTxn);
                        //TODO: ERT - check for VPNs importing my route
                        for (VpnInstance vpn : vpnsToImportRoute) {
                            String vpnRd = vpn.getIpv4Family().getRouteDistinguisher();
                            if (vpnRd != null) {
                                LOG.debug("Exporting route with rd {} prefix {} nexthop {} label {} to VPN {}", vpnRd, nextHop.getIpAddress(), nextHopIp, label, vpn);
                                VpnUtil.addFibEntryToDS(broker, vpnRd, nextHop.getIpAddress(), nextHopIp, (int) label, RouteOrigin.SELF_IMPORTED, writeTxn);
                            }
                        }
                    } else {
                        // ### add FIB route directly
                        VpnUtil.addFibEntryToDS(broker, vpnName, nextHop.getIpAddress(), nextHopIp,
                                (int) label, RouteOrigin.STATIC, writeTxn);
                    }
                }
            }
        }
    }

    private List<VpnInstance> getVpnsImportingMyRoute(final String vpnName) {
        List<VpnInstance> vpnsToImportRoute = new ArrayList<>();

        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> optVpnInstance = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
        final VpnInstance vpnInstance;
        if (optVpnInstance.isPresent()) {
            vpnInstance = optVpnInstance.get();
        } else {
            LOG.debug("Could not retrieve vpn instance {} to check for vpns importing the routes", vpnName);
            return vpnsToImportRoute;
        }

        Predicate<VpnInstance> excludeVpn = new Predicate<VpnInstance>() {
            @Override
            public boolean apply(VpnInstance input) {
                return !input.getVpnInstanceName().equals(vpnName);
            }
        };

        Predicate<VpnInstance> matchRTs = new Predicate<VpnInstance>() {
            @Override
            public boolean apply(VpnInstance input) {
                Iterable<String> commonRTs = intersection(getRts(vpnInstance, VpnTarget.VrfRTType.ExportExtcommunity),
                        getRts(input, VpnTarget.VrfRTType.ImportExtcommunity));
                return Iterators.size(commonRTs.iterator()) > 0;
            }
        };

        Function<VpnInstance, String> toInstanceName = new Function<VpnInstance, String>() {
            @Override
            public String apply(VpnInstance vpnInstance) {
                //return vpnInstance.getVpnInstanceName();
                return vpnInstance.getIpv4Family().getRouteDistinguisher();
            }
        };

        vpnsToImportRoute = FluentIterable.from(VpnUtil.getAllVpnInstance(broker)).
                filter(excludeVpn).
                filter(matchRTs).toList();
        return vpnsToImportRoute;
    }

    private List<VpnInstance> getVpnsExportingMyRoute(final String vpnName) {
        List<VpnInstance> vpnsToExportRoute = new ArrayList<>();

        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> optVpnInstance = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
        final VpnInstance vpnInstance;
        if (optVpnInstance.isPresent()) {
            vpnInstance = optVpnInstance.get();
        } else {
            LOG.debug("Could not retrieve vpn instance {} to check for vpns exporting the routes", vpnName);
            return vpnsToExportRoute;
        }

        Predicate<VpnInstance> excludeVpn = new Predicate<VpnInstance>() {
            @Override
            public boolean apply(VpnInstance input) {
                return !input.getVpnInstanceName().equals(vpnName);
            }
        };

        Predicate<VpnInstance> matchRTs = new Predicate<VpnInstance>() {
            @Override
            public boolean apply(VpnInstance input) {
                Iterable<String> commonRTs = intersection(getRts(vpnInstance, VpnTarget.VrfRTType.ImportExtcommunity),
                        getRts(input, VpnTarget.VrfRTType.ExportExtcommunity));
                return Iterators.size(commonRTs.iterator()) > 0;
            }
        };

        Function<VpnInstance, String> toInstanceName = new Function<VpnInstance, String>() {
            @Override
            public String apply(VpnInstance vpnInstance) {
                return vpnInstance.getVpnInstanceName();
            }
        };

        vpnsToExportRoute = FluentIterable.from(VpnUtil.getAllVpnInstance(broker)).
                filter(excludeVpn).
                filter(matchRTs).toList();
        return vpnsToExportRoute;
    }

    private <T> Iterable<T> intersection(final Collection<T> collection1, final Collection<T> collection2) {
        final Predicate<T> inPredicate = Predicates.<T>in(collection2);
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return Iterators.filter(collection1.iterator(), inPredicate);
            }
        };
    }

    private List<String> getRts(VpnInstance vpnInstance, VpnTarget.VrfRTType rtType) {
        String name = vpnInstance.getVpnInstanceName();
        List<String> rts = new ArrayList<>();
        VpnAfConfig vpnConfig = vpnInstance.getIpv4Family();
        if (vpnConfig == null) {
            LOG.trace("vpn config is not available for {}", name);
            return rts;
        }
        VpnTargets targets = vpnConfig.getVpnTargets();
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
            if(target.getVrfRTType().equals(rtType) ||
                    target.getVrfRTType().equals(VpnTarget.VrfRTType.Both)) {
                String rtValue = target.getVrfRTValue();
                rts.add(rtValue);
            }
        }
        return rts;
    }

    private List<String> getExportRts(VpnInstance vpnInstance) {
        List<String> exportRts = new ArrayList<>();
        VpnAfConfig vpnConfig = vpnInstance.getIpv4Family();
        VpnTargets targets = vpnConfig.getVpnTargets();
        List<VpnTarget> vpnTargets = targets.getVpnTarget();
        for (VpnTarget target : vpnTargets) {
            if (target.getVrfRTType().equals(VpnTarget.VrfRTType.ExportExtcommunity)) {
                String rtValue = target.getVrfRTValue();
                exportRts.add(rtValue);
            }
        }
        return exportRts;
    }

    private void makeArpFlow(BigInteger dpId,short sIndex, int lPortTag, String vpnInterfaceName,
                             long vpnId, ArpReplyOrRequest replyOrRequest, int addOrRemoveFlow, WriteTransaction writeTxn){
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        BigInteger metadata = MetaDataUtil.getMetaDataForLPortDispatcher(lPortTag, ++sIndex, BigInteger.valueOf(vpnId));
        BigInteger metadataMask = MetaDataUtil.getMetaDataMaskForLPortDispatcher(MetaDataUtil.METADATA_MASK_SERVICE_INDEX,
                MetaDataUtil.METADATA_MASK_LPORT_TAG, MetaDataUtil.METADATA_MASK_VRFID);

        // Matching Arp reply flows
        matches.add(new MatchInfo(MatchFieldType.eth_type, new long[] { NwConstants.ETHTYPE_ARP }));
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                metadata, metadataMask }));

        matches.add(new MatchInfo(MatchFieldType.arp_op, new long[] { replyOrRequest.getArpOperation() }));

        // Instruction to punt to controller
        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
        actionsInfos.add(new ActionInfo(ActionType.punt_to_controller, new String[] {}));
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[]{
                Short.toString(NwConstants.LPORT_DISPATCHER_TABLE)}));

        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        // Install the flow entry in L3_INTERFACE_TABLE
        String flowRef = VpnUtil.getFlowRef(dpId, NwConstants.L3_INTERFACE_TABLE,
                    NwConstants.ETHTYPE_ARP, lPortTag, replyOrRequest.getArpOperation());
        FlowEntity flowEntity;
        flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_INTERFACE_TABLE, flowRef,
                NwConstants.DEFAULT_ARP_FLOW_PRIORITY, replyOrRequest.getName(), 0, 0,
                VpnUtil.getCookieArpFlow(lPortTag), matches, instructions);
        Flow flow = flowEntity.getFlowBuilder().build();
        String flowId = flowEntity.getFlowId();
        FlowKey flowKey = new FlowKey( new FlowId(flowId));
        Node nodeDpn = buildDpnNode(dpId);

        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();

        if (writeTxn != null) {
            if (addOrRemoveFlow == NwConstants.ADD_FLOW) {
                LOG.debug("Creating ARP Flow for interface {}", vpnInterfaceName);
                writeTxn.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flow, true);
            } else {
                LOG.debug("Deleting ARP Flow for interface {}", vpnInterfaceName);
                writeTxn.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
            }
        } else {
            if (addOrRemoveFlow == NwConstants.ADD_FLOW) {
                LOG.debug("Creating ARP Flow for interface {}",vpnInterfaceName);
                mdsalManager.installFlow(flowEntity);
            } else {
                LOG.debug("Deleting ARP Flow for interface {}",vpnInterfaceName);
                mdsalManager.removeFlow(flowEntity);
            }
        }
    }

    //TODO: How to handle the below code, its a copy paste from MDSALManager.java
    private Node buildDpnNode(BigInteger dpnId) {
        NodeId nodeId = new NodeId("openflow:" + dpnId);
        Node nodeDpn = new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();

        return nodeDpn;
    }

    private String getRouteDistinguisher(String vpnName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                                      .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> vpnInstance = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
        String rd = "";
        if(vpnInstance.isPresent()) {
            VpnInstance instance = vpnInstance.get();
            VpnAfConfig config = instance.getIpv4Family();
            rd = config.getRouteDistinguisher();
        }
        return rd;
    }

    private void updateMappingDbs(long vpnId, BigInteger dpnId, String intfName, String vpnName,
                                  WriteTransaction writeTxn) {
        String routeDistinguisher = getRouteDistinguisher(vpnName);
        String rd = (routeDistinguisher == null) ? vpnName : routeDistinguisher;
        synchronized (vpnName.intern()) {
            InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
            Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op
                    .data.entry.vpn.to.dpn.list.VpnInterfaces
                    vpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();

            if (dpnInVpn.isPresent()) {
                if (writeTxn != null) {
                    writeTxn.put(LogicalDatastoreType.OPERATIONAL, id.child(
                            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance
                                    .op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                            new VpnInterfacesKey(intfName)), vpnInterface, true);
                } else {
                    VpnUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance
                                    .op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                            new VpnInterfacesKey(intfName)), vpnInterface);
                }
            } else {

                /*
                * Increment the active dpn count in VpnInstanceOpData.
                * ActiveDpnCount will be decremented when all the VpnInt in that Dpn are removed.
                */
                InstanceIdentifier<VpnInstanceOpDataEntry> vpnInsOpDataId = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
                Optional<VpnInstanceOpDataEntry> vpnInstOpData = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnInsOpDataId);

                java.lang.Long activeDpnCnt = vpnInstOpData.get().getActiveDpnCount();

                VpnInstanceOpDataEntryBuilder builder =
                        new VpnInstanceOpDataEntryBuilder(vpnInstOpData.get()).setVrfId(rd).setVpnId(vpnId).setVpnInstanceName(vpnName).setActiveDpnCount(++activeDpnCnt);

                if (writeTxn != null) {
                    writeTxn.merge(LogicalDatastoreType.OPERATIONAL,
                            vpnInsOpDataId,
                            builder.build(), true);
                } else {
                    VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                            vpnInsOpDataId,
                            builder.build());
                }

                VpnToDpnListBuilder vpnToDpnList = new VpnToDpnListBuilder().setDpnId(dpnId).setDpnState(VpnToDpnList.DpnState.Active);
                List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                        .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> vpnInterfaces = new ArrayList<>();
                vpnInterfaces.add(vpnInterface);
                if (writeTxn != null) {
                    writeTxn.merge(LogicalDatastoreType.OPERATIONAL, id,
                            vpnToDpnList.setVpnInterfaces(vpnInterfaces).build(), true);
                } else {
                    VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, id,
                            vpnToDpnList.setVpnInterfaces(vpnInterfaces).build());
                }
                /**
                 * FIXME: DC Gateway tunnel should be built dynamically
                 //this is the first VM in this VPN on the DPN, may be a new DPN has come up,
                 //if tunnel to DC GW does not exist, create it
                 //if(!tunnelExists(dpnID, bgpManager.getDCGWIP()))
                 String dcGW = bgpManager.getDCGwIP();
                 if(dcGW != null && !dcGW.isEmpty())
                 {
                 LOG.debug("Building tunnel from DPN {} to DC GW {}", dpnId, dcGW);
                 itmProvider.buildTunnelFromDPNToDCGW(dpnId, new IpAddress(dcGW.toCharArray()));
                 }*/
                fibManager.populateFibOnNewDpn(dpnId, vpnId, (rd == null) ? vpnName : rd);
                publishAddNotification(dpnId, vpnName, rd);
                //TODO: IRT - import local routes to vpn. check for the VPNs exporting the routes
                //FIXME: do we need to handle here. since already routes are imported to this vpn

            }
        }
    }

    void handleVpnsExportingRoutes(String vpnName, String vpnRd) {
        List<VpnInstance> vpnsToExportRoute = getVpnsExportingMyRoute(vpnName);
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        for (VpnInstance vpn : vpnsToExportRoute) {
            String rd = vpn.getIpv4Family().getRouteDistinguisher();
            long exportingVpnId = VpnUtil.getVpnId(broker, vpn.getVpnInstanceName());
            List<VrfEntry> vrfEntries = VpnUtil.getAllVrfEntries(broker, vpn.getIpv4Family().getRouteDistinguisher());
            if (vrfEntries != null) {
                for (VrfEntry vrfEntry : vrfEntries) {
                    try {
                        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.STATIC) {
                            continue;
                        }
                        String prefix = vrfEntry.getDestPrefix();
                        long label = vrfEntry.getLabel();
                        List<String> nextHops = vrfEntry.getNextHopAddressList();
                        SubnetRoute route = vrfEntry.getAugmentation(SubnetRoute.class);
                        for (String nh : nextHops) {
                            if (route != null) {
                                LOG.info("Importing subnet route fib entry rd {} prefix {} nexthop {} label {} to vpn {}", vpnRd, prefix, nh, label, vpn.getVpnInstanceName());
                                importSubnetRouteForNewVpn(rd, prefix, nh, (int)label, route);
                            } else {
                                LOG.info("Importing fib entry rd {} prefix {} nexthop {} label {} to vpn {}", vpnRd, prefix, nh, label, vpn.getVpnInstanceName());
                                VpnUtil.addFibEntryToDS(broker, vpnRd, prefix, nh, (int)label, RouteOrigin
                                        .SELF_IMPORTED, null);
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("Exception occurred while importing route with prefix {} label {} nexthop {} from vpn {} to vpn {}", vrfEntry.getDestPrefix(), vrfEntry.getLabel(), vrfEntry.getNextHopAddressList(), vpn.getVpnInstanceName(), vpnName);
                    }
                }
            } else {
                LOG.info("No vrf entries to import from vpn {} with rd {}", vpn.getVpnInstanceName(), vpn.getIpv4Family().getRouteDistinguisher());
            }
        }
    }

    private void removeFromMappingDbs(long vpnId, BigInteger dpnId, String intfName, String vpnName,
                                      WriteTransaction writeTxn) {
        //TODO: Delay 'DPN' removal so that other services can cleanup the entries for this dpn
        synchronized (vpnName.intern()) {
            String rd = VpnUtil.getVpnRd(broker, vpnName);
            InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
            Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
            if (dpnInVpn.isPresent()) {
                List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                        .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn
                        .instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces
                        currVpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();

                if (vpnInterfaces.remove(currVpnInterface)) {
                    if (vpnInterfaces.isEmpty()) {
                        List<IpAddresses> ipAddresses = dpnInVpn.get().getIpAddresses();
                        if (ipAddresses == null || ipAddresses.isEmpty()) {

                           /*
                           * Mark the DPN state as Inactive as the Vpnintf list is empty.
                           * This DPN will be removed in DpnToVpnChangeListner only if its
                           * inactive and ActiveDpnCount is zero.
                           */
                            VpnToDpnListBuilder vpnToDpnList =
                                    new VpnToDpnListBuilder(dpnInVpn.get())
                                            .setDpnId(dpnId)
                                            .setDpnState(VpnToDpnList.DpnState.Inactive)
                                            .setVpnInterfaces(vpnInterfaces);

                            if (writeTxn != null) {
                                writeTxn.merge(LogicalDatastoreType.OPERATIONAL, id , vpnToDpnList.build(), true);
                            } else {
                                VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, id, vpnToDpnList.build());
                            }

                            InstanceIdentifier<VpnInstanceOpDataEntry> vpnInstOpDataId = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
                            Optional<VpnInstanceOpDataEntry> vpnInstOpData = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnInstOpDataId);

                            java.lang.Long activeDpnCnt = vpnInstOpData.get().getActiveDpnCount();

                            VpnInstanceOpDataEntryBuilder builder =
                                    new VpnInstanceOpDataEntryBuilder(vpnInstOpData.get()).setVrfId(rd).setVpnId(vpnId).setVpnInstanceName(vpnName).setActiveDpnCount(--activeDpnCnt);

                            if (writeTxn != null) {
                                writeTxn.merge(LogicalDatastoreType.OPERATIONAL, vpnInstOpDataId , builder.build(), true);
                            } else {
                                VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, vpnInstOpDataId, builder.build());
                            }

                            LOG.debug("Sending cleanup event for dpn {} in VPN {}", dpnId, vpnName);
                            fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd);
                            publishRemoveNotification(dpnId, vpnName, rd);
                        } else {
                            LOG.debug("vpn interfaces are empty but ip addresses are present for the vpn {} in dpn {}", vpnName, dpnId);
                        }
                    } else {
                        if (writeTxn != null) {
                            writeTxn.delete(LogicalDatastoreType.OPERATIONAL, id.child(
                                    org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op
                                            .data
                                            .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                                    new VpnInterfacesKey(intfName)));
                        } else {
                            VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                                    org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                                            .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                                    new VpnInterfacesKey(intfName)), VpnUtil.DEFAULT_CALLBACK);
                        }
                    }
                }
            }
        }
    }

    private void addPrefixToBGP(String rd, String prefix, String nextHopIp, long label, WriteTransaction writeTxn) {

        try {
            LOG.info("ADD: Adding Fib entry rd {} prefix {} nextHop {} label {}", rd, prefix, nextHopIp, label);
            VpnUtil.addFibEntryToDS(broker, rd, prefix, nextHopIp, (int)label, RouteOrigin.STATIC, writeTxn);
            bgpManager.advertisePrefix(rd, prefix, Arrays.asList(nextHopIp), (int)label);
            LOG.info("ADD: Added Fib entry rd {} prefix {} nextHop {} label {}", rd, prefix, nextHopIp, label);
        } catch(Exception e) {
            LOG.error("Add prefix failed", e);
        }
    }


    private InstanceIdentifier<VpnInterface> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    public void remove( InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
        LOG.trace("Remove event - key: {}, value: {}" ,identifier, vpnInterface );
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();

        InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        Optional<VpnInterface> existingVpnInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, interfaceId);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface interfaceState =
            InterfaceUtils.getInterfaceStateFromOperDS(broker, interfaceName);
        if (existingVpnInterface.isPresent()){
            BigInteger dpnId = BigInteger.ZERO;
            Boolean dpnIdRetrieved = Boolean.FALSE;
            if(interfaceState != null){
                try{
                    dpnId = InterfaceUtils.getDpIdFromInterface(interfaceState);
                    dpnIdRetrieved = Boolean.TRUE;
                }catch (Exception e){
                    LOG.error("Unable to retrieve dpnId from interface operational data store for interface {}. Fetching from vpn interface op data store. ", interfaceName, e);
                }
            } else {
                LOG.error("Unable to retrieve interfaceState for interface {} , quitting ", interfaceName);
                return;
            }
            if(dpnIdRetrieved == Boolean.FALSE){
                LOG.info("dpnId for {} has not been retrieved yet. Fetching from vpn interface operational DS", interfaceName);
                dpnId = existingVpnInterface.get().getDpnId();
            }
            final int ifIndex = interfaceState.getIfIndex();
            final BigInteger dpId = dpnId;
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            WriteTransaction writeTxn = broker.newWriteOnlyTransaction();
                            processVpnInterfaceDown(dpId, interfaceName, ifIndex, false, true, writeTxn);
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            futures.add(writeTxn.submit());
                            waitForFibToRemoveVpnPrefix(interfaceName);
                            return futures;
                        }
                    });
            //processVpnInterfaceDown(dpnId, interfaceName, interfaceState.getIfIndex(), false, true);
        }else{
            LOG.warn("VPN interface {} was unavailable in operational data store to handle remove event", interfaceName);
        }
    }

    protected void processVpnInterfaceDown(BigInteger dpId, String interfaceName, int lPortTag, boolean isInterfaceStateDown,
                                           boolean isConfigRemoval, WriteTransaction writeTxn) {
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        if (!isInterfaceStateDown) {
            synchronized (interfaceName.intern()) {
                VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(broker, interfaceName);
                if(vpnInterface == null){
                    LOG.info("Unable to process delete/down for interface {} as it is not available in operational data store", interfaceName);
                    return;
                }else{
                    final String vpnName = vpnInterface.getVpnInstanceName();
                    if(!vpnInterface.isScheduledForRemove()){
                        VpnUtil.updateVpnInterface(broker, interfaceName, dpId, vpnName, Boolean.TRUE, writeTxn);
                        removeAdjacenciesFromVpn(dpId, interfaceName, vpnInterface.getVpnInstanceName(), writeTxn);
                        LOG.info("Unbinding vpn service from interface {} ", interfaceName);
                        unbindService(dpId, vpnName, interfaceName, lPortTag, isInterfaceStateDown, isConfigRemoval, writeTxn);
                    }else{
                        LOG.info("Unbinding vpn service for interface {} has already been scheduled by a different event ", interfaceName);
                        return;
                    }
                }
            }
        } else {
                synchronized (interfaceName.intern()) {
                    // Interface is retained in the DPN, but its Link Down.
                    // Only withdraw the prefixes for this interface from BGP
                    VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(broker, interfaceName);
                    if(vpnInterface == null){
                        LOG.info("Unable to withdraw adjacencies for vpn interface {} from BGP as it is not available in operational data store", interfaceName);
                        return;
                    }else {
                        withdrawAdjacenciesForVpnFromBgp(identifier, vpnInterface);
                    }
                }
        }

    }

    private void waitForFibToRemoveVpnPrefix(String interfaceName) {
        // FIB didn't get a chance yet to clean up this VPNInterface
        // Let us give it a chance here !
        LOG.info("VPN Interface {} removal waiting for FIB to clean up ! ", interfaceName);
        try {
            Runnable notifyTask = new VpnNotifyTask();
            vpnIntfMap.put(interfaceName, notifyTask);
            synchronized (notifyTask) {
                try {
                    notifyTask.wait(VpnConstants.PER_INTERFACE_MAX_WAIT_TIME_IN_MILLISECONDS);
                } catch (InterruptedException e) {
                }
            }
        } finally {
            vpnIntfMap.remove(interfaceName);
        }
    }

    private void removeAdjacenciesFromVpn(final BigInteger dpnId, final String interfaceName, final String vpnName,
                                          WriteTransaction writeTxn) {
        //Read NextHops
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);

        String rd = VpnUtil.getVpnRd(broker, vpnName);
        LOG.trace("removeAdjacenciesFromVpn: For interface {} RD recovered for vpn {} as rd {}", interfaceName,
                vpnName, rd);
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    List<String> nhList = new ArrayList<String>();
                    if (nextHop.getMacAddress() == null || nextHop.getMacAddress().isEmpty()) {
                        // This is either an extra-route (or) a learned IP via subnet-route
                        String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId);
                        if (nextHopIp == null || nextHopIp.isEmpty()) {
                            LOG.error("Unable to obtain nextHopIp for extra-route/learned-route in rd {} prefix {}",
                                    rd, nextHop.getIpAddress());
                            continue;
                        }
                        nhList = Arrays.asList(nextHopIp);
                    } else {
                        // This is a primary adjacency
                        nhList = nextHop.getNextHopIpList();
                    }
                    if (rd.equals(vpnName)) {
                        //this is an internal vpn - the rd is assigned to the vpn instance name;
                        //remove from FIB directly
                        for(String nh : nhList) {
                            VpnUtil.removeFibEntryFromDS(broker, vpnName, nextHop.getIpAddress(), nh, writeTxn);
                        }
                    } else {
                        List<VpnInstance> vpnsToImportRoute = getVpnsImportingMyRoute(vpnName);
                        for (String nh : nextHop.getNextHopIpList()) {
                            //IRT: remove routes from other vpns importing it
                            removePrefixFromBGP(rd, nextHop.getIpAddress(), nh, writeTxn);
                            for (VpnInstance vpn : vpnsToImportRoute) {
                                String vpnRd = vpn.getIpv4Family().getRouteDistinguisher();
                                if (vpnRd != null) {
                                    LOG.info("Removing Exported route with rd {} prefix {} from VPN {}", vpnRd, nextHop.getIpAddress(), vpn.getVpnInstanceName());
                                    VpnUtil.removeFibEntryFromDS(broker, vpnRd, nextHop.getIpAddress(), nh, writeTxn);
                                }
                            }
                        }
                    }
                    String ip = nextHop.getIpAddress().split("/")[0];
                    VpnPortipToPort vpnPortipToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(broker,
                            vpnName, ip);
                    if (vpnPortipToPort != null && !vpnPortipToPort.isConfig()) {
                        LOG.trace("VpnInterfaceManager removing adjacency for Interface {} ip {} from VpnPortData Entry",
                                vpnPortipToPort.getPortName(),ip);
                        VpnUtil.removeVpnPortFixedIpToPort(broker, vpnName, ip);
                    }
                }
            }
        }
    }


    private void unbindService(BigInteger dpId, String vpnInstanceName, String vpnInterfaceName,
                               int lPortTag, boolean isInterfaceStateDown, boolean isConfigRemoval,
                               WriteTransaction writeTxn) {
        if (!isInterfaceStateDown && isConfigRemoval) {
            writeTxn.delete(LogicalDatastoreType.CONFIGURATION,
                    InterfaceUtils.buildServiceId(vpnInterfaceName,
                            VpnConstants.L3VPN_SERVICE_IDENTIFIER));
        }
        long vpnId = VpnUtil.getVpnId(broker, vpnInstanceName);
        makeArpFlow(dpId, VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                vpnId, ArpReplyOrRequest.REQUEST, NwConstants.DEL_FLOW, writeTxn);
        makeArpFlow(dpId, VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                vpnId, ArpReplyOrRequest.REPLY, NwConstants.DEL_FLOW, writeTxn);
    }


    private void removePrefixFromBGP(String rd, String prefix, String nextHop, WriteTransaction writeTxn) {
        VpnUtil.removeFibEntryFromDS(broker, rd, prefix, nextHop, writeTxn);
        try {
            LOG.info("VPN WITHDRAW: Removing Fib Entry rd {} prefix {}", rd, prefix);
            VpnUtil.removeFibEntryFromDS(broker, rd, prefix, nextHop, writeTxn);
            bgpManager.withdrawPrefix(rd, prefix); // TODO: Might be needed to include nextHop here
            LOG.info("VPN WITHDRAW: Removed Fib Entry rd {} prefix {}", rd, prefix);
        } catch(Exception e) {
            LOG.error("Delete prefix failed", e);
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> identifier, VpnInterface original, VpnInterface update) {
        LOG.trace("Updating VPN Interface : key {},  original value={}, update value={}", identifier, original, update);
        String oldVpnName = original.getVpnInstanceName();
        String newVpnName = update.getVpnInstanceName();
        BigInteger dpnId = update.getDpnId();
        List<Adjacency> oldAdjs = original.getAugmentation(Adjacencies.class).getAdjacency();
        List<Adjacency> newAdjs = update.getAugmentation(Adjacencies.class).getAdjacency();
        if (oldAdjs == null) {
            oldAdjs = new ArrayList<>();
        }
        if (newAdjs == null) {
            newAdjs = new ArrayList<>();
        }
        //handles switching between <internal VPN - external VPN>
        if (!oldVpnName.equals(newVpnName)) {
            remove(identifier, original);
            add(identifier, update);
        }
        //handle both addition and removal of adjacencies
        //currently, new adjacency may be an extra route
        if (!oldAdjs.equals(newAdjs)) {
            for (Adjacency adj : newAdjs) {
                if (oldAdjs.contains(adj)) {
                    oldAdjs.remove(adj);
                } else {
                    // add new adjacency - right now only extra route will hit this path
                    addNewAdjToVpnInterface(identifier, adj, dpnId);
                }
            }
            for (Adjacency adj : oldAdjs) {
                delAdjFromVpnInterface(identifier, adj, dpnId);
            }
        }
    }

    public void processArpRequest(IpAddress srcIP, PhysAddress srcMac, IpAddress targetIP, PhysAddress targetMac,String srcInterface){
        //Build ARP response with ARP requests TargetIp TargetMac as the Arp Response SrcIp and SrcMac
        SendArpResponseInput input = new SendArpResponseInputBuilder().setInterface(srcInterface)
                .setDstIpaddress(srcIP).setDstMacaddress(srcMac).setSrcIpaddress(targetIP).setSrcMacaddress(targetMac).build();
        final String msgFormat = String.format("Send ARP Response on interface %s to destination %s", srcInterface, srcIP);
        Future<RpcResult<Void>> future = arpManager.sendArpResponse(input);
        Futures.addCallback(JdkFutureAdapters.listenInPoolThread(future), new FutureCallback<RpcResult<Void>>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error - {}", msgFormat, error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if(!result.isSuccessful()) {
                    LOG.warn("Rpc call to {} failed", msgFormat, getErrorText(result.getErrors()));
                } else {
                    LOG.debug("Successful RPC Result - {}", msgFormat);
                }
            }
        });
    }

    private String getErrorText(Collection<RpcError> errors) {
        StringBuilder errorText = new StringBuilder();
        for(RpcError error : errors) {
            errorText.append(",").append(error.getErrorType()).append("-")
                     .append(error.getMessage());
        }
        return errorText.toString();
    }

    private void addToLabelMapper(Long label, BigInteger dpnId, String prefix, List<String> nextHopIpList, Long vpnId,
                                  String vpnInterfaceName, Long elanTag, boolean isSubnetRoute, String rd) {
        Preconditions.checkNotNull(label, "label cannot be null or empty!");
        Preconditions.checkNotNull(prefix, "prefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnId, "vpnId cannot be null or empty!");
        Preconditions.checkNotNull(rd, "rd cannot be null or empty!");
        if (!isSubnetRoute) {
            // NextHop must be present for non-subnetroute entries
            Preconditions.checkNotNull(nextHopIpList, "nextHopIp cannot be null or empty!");
        }
        LOG.info("Adding to label mapper : label {} dpn {} prefix {} nexthoplist {} vpnid {} vpnIntfcName {} rd {}", label, dpnId, prefix, nextHopIpList, vpnId, vpnInterfaceName, rd);
        if (dpnId != null) {
            InstanceIdentifier<LabelRouteInfo> lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
                    .child(LabelRouteInfo.class, new LabelRouteInfoKey((long)label)).build();
            LabelRouteInfoBuilder lriBuilder = new LabelRouteInfoBuilder();
            lriBuilder.setLabel(label).setDpnId(dpnId).setPrefix(prefix).setNextHopIpList(nextHopIpList).setParentVpnid(vpnId)
                    .setIsSubnetRoute(isSubnetRoute);
            if (elanTag != null) {
                lriBuilder.setElanTag(elanTag);
            }
            if (vpnInterfaceName != null) {
                lriBuilder.setVpnInterfaceName(vpnInterfaceName);
            }
            lriBuilder.setParentVpnRd(rd);
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(broker, rd);
            if (vpnInstanceOpDataEntry != null) {
                List<String> vpnInstanceNames = Arrays.asList(vpnInstanceOpDataEntry.getVpnInstanceName());
                lriBuilder.setVpnInstanceList(vpnInstanceNames);
            }
            LabelRouteInfo lri = lriBuilder.build();
            LOG.trace("Adding route info to label map: {}", lri);
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, lriIid, lri);
        } else {
            LOG.trace("Can't add entry to label map for lable {},dpnId is null", label);
        }
    }

    public synchronized void addSubnetRouteFibEntryToDS(String rd, String vpnName, String prefix, String nextHop, int label,
                                                        long elantag, BigInteger dpnId, WriteTransaction writeTxn) {

        SubnetRoute route = new SubnetRouteBuilder().setElantag(elantag).build();
        RouteOrigin origin = RouteOrigin.STATIC; // Only case when a route is considered as directly connected
        VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddressList(Arrays.asList(nextHop))
                                                 .setLabel((long)label).setOrigin(origin.getValue())
                                                 .addAugmentation(SubnetRoute.class, route).build();

        LOG.debug("Created vrfEntry for {} nexthop {} label {} and elantag {}", prefix, nextHop, label, elantag);

        //TODO: What should be parentVpnId? Get it from RD?
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        addToLabelMapper((long)label, dpnId, prefix, Arrays.asList(nextHop), vpnId, null, elantag, true, rd);
        List<VrfEntry> vrfEntryList = Arrays.asList(vrfEntry);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).
                setVrfEntry(vrfEntryList).build();

        if (writeTxn != null) {
            writeTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew, true);
        } else {
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
        }

        List<VpnInstance> vpnsToImportRoute = getVpnsImportingMyRoute(vpnName);
        if (vpnsToImportRoute.size() > 0) {
            VrfEntry importingVrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddressList(Arrays.asList(nextHop))
                    .setLabel((long) label).setOrigin(RouteOrigin.SELF_IMPORTED.getValue())
                    .addAugmentation(SubnetRoute.class, route).build();
            List<VrfEntry> importingVrfEntryList = Arrays.asList(importingVrfEntry);
            for (VpnInstance vpnInstance : vpnsToImportRoute) {
                LOG.info("Exporting subnet route rd {} prefix {} nexthop {} label {} to vpn {}", rd, prefix, nextHop, label, vpnInstance.getVpnInstanceName());
                String importingRd = vpnInstance.getIpv4Family().getRouteDistinguisher();
                InstanceIdentifier<VrfTables> importingVrfTableId = InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(importingRd)).build();
                VrfTables importingVrfTable = new VrfTablesBuilder().setRouteDistinguisher(importingRd).setVrfEntry(importingVrfEntryList).build();
                if (writeTxn != null) {
                    writeTxn.merge(LogicalDatastoreType.CONFIGURATION, importingVrfTableId, importingVrfTable, true);
                } else {
                    VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, importingVrfTableId, importingVrfTable);
                }
            }
        }
    }

    public synchronized void importSubnetRouteForNewVpn(String rd, String prefix, String nextHop, int label,
                                                        SubnetRoute route) {

        RouteOrigin origin = RouteOrigin.SELF_IMPORTED;
        VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddressList(Arrays.asList(nextHop))
                .setLabel((long)label).setOrigin(origin.getValue())
                .addAugmentation(SubnetRoute.class, route).build();
        LOG.debug("Created vrfEntry for {} nexthop {} label {} and elantag {}", prefix, nextHop, label, route.getElantag());
        List<VrfEntry> vrfEntryList = Arrays.asList(vrfEntry);
        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
        VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).
                setVrfEntry(vrfEntryList).build();
        VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
    }

    public synchronized void deleteSubnetRouteFibEntryFromDS(String rd, String prefix, String vpnName){
        VpnUtil.removeFibEntryFromDS(broker, rd, prefix, null /* nextHopToRemove */, null);
        List<VpnInstance> vpnsToImportRoute = getVpnsImportingMyRoute(vpnName);
        for (VpnInstance vpnInstance : vpnsToImportRoute) {
            String importingRd = vpnInstance.getIpv4Family().getRouteDistinguisher();
            LOG.info("Deleting imported subnet route rd {} prefix {} from vpn {}", rd, prefix, vpnInstance.getVpnInstanceName());
            VpnUtil.removeFibEntryFromDS(broker, importingRd, prefix, null, null);
        }
    }

    public synchronized void removeVrfFromDS(String rd) {
        LOG.debug("Removing vrf table for rd {}", rd);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        VpnUtil.delete(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, VpnUtil.DEFAULT_CALLBACK);

    }

    protected void addNewAdjToVpnInterface(InstanceIdentifier<VpnInterface> identifier, Adjacency adj, BigInteger dpnId) {

        Optional<VpnInterface> optVpnInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, identifier);

        if (optVpnInterface.isPresent()) {
            VpnInterface currVpnIntf = optVpnInterface.get();
            String prefix = VpnUtil.getIpPrefix(adj.getIpAddress());
            String rd = getRouteDistinguisher(currVpnIntf.getVpnInstanceName());
            InstanceIdentifier<Adjacencies> adjPath = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> optAdjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, adjPath);
            long label =
                    VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                            VpnUtil.getNextHopLabelKey((rd != null) ? rd : currVpnIntf.getVpnInstanceName(), prefix));

            List<Adjacency> adjacencies;
            if (optAdjacencies.isPresent()) {
                adjacencies = optAdjacencies.get().getAdjacency();
            } else {
                //This code will not be hit since VM adjacency will always be there
                adjacencies = new ArrayList<>();
            }


            adjacencies.add(new AdjacencyBuilder(adj).setLabel(label).setNextHopIpList(adj.getNextHopIpList())
                    .setIpAddress(prefix).setKey(new AdjacencyKey(prefix)).build());
            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencies);
            VpnInterface newVpnIntf = VpnUtil.getVpnInterface(currVpnIntf.getName(), currVpnIntf.getVpnInstanceName(), aug, dpnId, currVpnIntf.isScheduledForRemove());
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf);
            for (String nh : adj.getNextHopIpList()) {
                addExtraRoute(adj.getIpAddress(), nh, rd, currVpnIntf.getVpnInstanceName(), (int) label,
                              currVpnIntf.getName());
            }

        }

    }

    protected void delAdjFromVpnInterface(InstanceIdentifier<VpnInterface> identifier, Adjacency adj, BigInteger dpnId) {
        Optional<VpnInterface> optVpnInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, identifier);

        if (optVpnInterface.isPresent()) {
            VpnInterface currVpnIntf = optVpnInterface.get();

            InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> optAdjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);
            if (optAdjacencies.isPresent()) {
                List<Adjacency> adjacencies = optAdjacencies.get().getAdjacency();

                if (!adjacencies.isEmpty()) {
                    String rd = getRouteDistinguisher(currVpnIntf.getVpnInstanceName());
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

                            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf);

                            for (String nh : adj.getNextHopIpList()) {
                                delExtraRoute(adj.getIpAddress(), nh, rd, currVpnIntf.getVpnInstanceName(),
                                              currVpnIntf.getName());
                            }
                            break;
                        }

                    }
                }
            }
        }

    }

    protected void addExtraRoute(String destination, String nextHop, String rd, String routerID, int label,
                                 String intfName) {

        //add extra route to vpn mapping; advertise with nexthop as tunnel ip
        VpnUtil.syncUpdate(
            broker,
            LogicalDatastoreType.OPERATIONAL,
            VpnUtil.getVpnToExtrarouteIdentifier( (rd != null) ? rd : routerID, destination),
            VpnUtil.getVpnToExtraroute(destination, Arrays.asList(nextHop)));

        BigInteger dpnId = null;
        if (intfName != null && !intfName.isEmpty()) {
            dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.error("NextHop for interface {} is null / empty. Failed advertising extra route for prefix {}",
                          intfName, destination);
                return;
            }
            nextHop = nextHopIp;
        }
        List<String> nextHopIpList = Arrays.asList(nextHop);
        addToLabelMapper((long)label, dpnId, destination, nextHopIpList, VpnUtil.getVpnId(broker, routerID),
                intfName, null, false, rd);

        // TODO (eperefr): This is a limitation to be stated in docs. When configuring static route to go to
        // another VPN, there can only be one nexthop or, at least, the nexthop to the interVpnLink should be in
        // first place.
        InterVpnLink interVpnLink = VpnUtil.getInterVpnLinkByEndpointIp(broker, nextHop);
        if ( interVpnLink != null ) {
            // If the nexthop is the endpoint of Vpn2, then prefix must be advertised to Vpn1 in DC-GW, with nexthops
            // pointing to the DPNs where Vpn1 is instantiated. LFIB in these DPNS must have a flow entry, with lower
            // priority, where if Label matches then sets the lportTag of the Vpn2 endpoint and goes to LportDispatcher
            // This is like leaking one of the Vpn2 routes towards Vpn1
            boolean nexthopIsVpn2 = ( interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(nextHop) );
            String srcVpnUuid = (nexthopIsVpn2) ? interVpnLink.getSecondEndpoint().getVpnUuid().getValue()
                                                : interVpnLink.getFirstEndpoint().getVpnUuid().getValue();
            String dstVpnUuid = (nexthopIsVpn2) ? interVpnLink.getFirstEndpoint().getVpnUuid().getValue()
                                                : interVpnLink.getSecondEndpoint().getVpnUuid().getValue();
            String dstVpnRd = VpnUtil.getVpnRd(broker, dstVpnUuid);
            long newLabel = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                                VpnUtil.getNextHopLabelKey(dstVpnRd, destination));
            VpnUtil.leakRoute(broker, bgpManager, interVpnLink, srcVpnUuid, dstVpnUuid, destination, newLabel);
        } else {
            if (rd != null) {
                addPrefixToBGP(rd, destination, nextHop, label, null);
            } else {
                // ### add FIB route directly
                VpnUtil.addFibEntryToDS(broker, routerID, destination, nextHop, label, RouteOrigin.STATIC, null);
            }
        }
    }

    protected void delExtraRoute(String destination, String nextHop, String rd, String routerID, String intfName) {
        if (intfName != null && !intfName.isEmpty()) {
            BigInteger dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.warn("NextHop for interface {} is null / empty. Failed advertising extra route for prefix {}",
                        intfName, destination);
            }
            nextHop = nextHopIp;
        }

        if (rd != null) {
            removePrefixFromBGP(rd, destination, nextHop, null);
        } else {
            // ### add FIB route directly
            VpnUtil.removeFibEntryFromDS(broker, routerID, destination, nextHop, null);
        }
    }

    class VpnInterfaceOpListener extends AbstractDataChangeListener<VpnInterface> {

        public VpnInterfaceOpListener() {
            super(VpnInterface.class);
        }

        @Override
        protected void remove(InstanceIdentifier<VpnInterface> identifier, VpnInterface del) {
            final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
            String interfaceName = key.getName();
            String vpnName = del.getVpnInstanceName();

            LOG.trace("VpnInterfaceOpListener removed: interface name {} vpnName {}", interfaceName, vpnName);
            //decrement the vpn interface count in Vpn Instance Op Data
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance>
                    id = VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnName);
            Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnInstance
                    = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);

            if (vpnInstance.isPresent()) {
                String rd = null;
                rd = vpnInstance.get().getVrfId();
                //String rd = getRouteDistinguisher(del.getVpnInstanceName());

                VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(broker, rd);
                LOG.trace("VpnInterfaceOpListener removed: interface name {} rd {} vpnName {} in Vpn Op Instance {}",
                        interfaceName, rd, vpnName, vpnInstOp);

                if (vpnInstOp != null) {
                    // Vpn Interface removed => No more adjacencies from it.
                    // Hence clean up interface from vpn-dpn-interface list.
                    Adjacency adjacency = del.getAugmentation(Adjacencies.class).getAdjacency().get(0);
                    List<Prefixes> prefixToInterface = new ArrayList<>();
                    Optional<Prefixes> prefix = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                    VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                    if (prefix.isPresent()) {
                        prefixToInterface.add(prefix.get());
                    }
                    if (prefixToInterface.isEmpty()) {
                        for (String nh : adjacency.getNextHopIpList()) {
                            prefix = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                                     VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                                                           VpnUtil.getIpPrefix(nh)));
                            if (prefix.isPresent())
                                prefixToInterface.add(prefix.get());
                        }
                    }
                    for (Prefixes pref : prefixToInterface) {
                        VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL,
                                       VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(), pref.getIpAddress()),
                                       VpnUtil.DEFAULT_CALLBACK);
                        synchronized (interfaceName.intern()) {
                            updateDpnDbs(pref.getDpnId(), del.getVpnInstanceName(), interfaceName, false, null);
                        }
                    }
                }
            } else {
                LOG.error("rd not retrievable as vpninstancetovpnid for vpn {} is absent, trying rd as ", vpnName, vpnName);
            }
            notifyTaskIfRequired(interfaceName);
        }

        private void notifyTaskIfRequired(String intfName) {
            Runnable notifyTask = vpnIntfMap.remove(intfName);
            if (notifyTask == null) {
                LOG.trace("VpnInterfaceOpListener update: No Notify Task queued for vpnInterface {}", intfName);
                return;
            }
            executorService.execute(notifyTask);
        }

        @Override
        protected void update(InstanceIdentifier<VpnInterface> identifier, VpnInterface original, VpnInterface update) {
            final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
            String interfaceName = key.getName();

            if (original.getVpnInstanceName().equals(update.getVpnInstanceName())) {
                return;
            }

            //increment the vpn interface count in Vpn Instance Op Data
            VpnInstanceOpDataEntry vpnInstOp = null;
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance>
                    origId = VpnUtil.getVpnInstanceToVpnIdIdentifier(original.getVpnInstanceName());
            Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> origVpnInstance
                    = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, origId);

            if (origVpnInstance.isPresent()) {
                String rd = null;
                rd = origVpnInstance.get().getVrfId();

                vpnInstOp = VpnUtil.getVpnInstanceOpData(broker, rd);
                LOG.trace("VpnInterfaceOpListener updated: interface name {} original rd {} original vpnName {}",
                        interfaceName, rd, original.getVpnInstanceName());

                if (vpnInstOp != null) {
                    Adjacency adjacency = original.getAugmentation(Adjacencies.class).getAdjacency().get(0);
                    List<Prefixes> prefixToInterfaceList = new ArrayList<>();
                    Optional<Prefixes> prefixToInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                    VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                    if (prefixToInterface.isPresent()) {
                        prefixToInterfaceList.add(prefixToInterface.get());
                    } else {
                        for (String adj : adjacency.getNextHopIpList()) {
                            prefixToInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                                          VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                          VpnUtil.getIpPrefix(adj)));
                            if (prefixToInterface.isPresent()) {
                                prefixToInterfaceList.add(prefixToInterface.get());
                            }
                        }
                    }
                    synchronized (interfaceName.intern()) {
                        for (Prefixes prefix : prefixToInterfaceList) {
                            updateDpnDbs(prefix.getDpnId(), original.getVpnInstanceName(), interfaceName, false, null);
                        }
                    }
                }
            }
            notifyTaskIfRequired(interfaceName);
        }

        @Override
        protected void add(InstanceIdentifier<VpnInterface> identifier, VpnInterface add) {

        }
    }

    protected void handlePrefixesForDPNs(StateTunnelList stateTunnelList, UpdateRouteAction action) {
        BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        BigInteger destDpnId;
        String srcTepIp =  String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());

        InstanceIdentifierBuilder<VpnInstances> idBuilder = InstanceIdentifier.builder(VpnInstances.class);
        InstanceIdentifier<VpnInstances> vpnInstancesId = idBuilder.build();
        Optional<VpnInstances> vpnInstances = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnInstancesId);
        long tunTypeVal = 0, vpnId;

        if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeInternal.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Internal.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeExternal.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.External.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeHwvtep.class){
            tunTypeVal = VpnConstants.ITMTunnelLocType.Hwvtep.getValue();
        } else {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Invalid.getValue();
        }
        LOG.trace("tunTypeVal is {}", tunTypeVal);
        long dcgwPresentStatus = VpnConstants.DCGWPresentStatus.Invalid.getValue();
        if (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) {
            Future<RpcResult<IsDcgwPresentOutput>> result;
            try {
                result = itmProvider.isDcgwPresent(new IsDcgwPresentInputBuilder()
                        .setDcgwIp(destTepIp)
                        .build());
                RpcResult<IsDcgwPresentOutput> rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.warn("RPC Call to isDcgwPresent {} returned with Errors {}", destTepIp, rpcResult.getErrors());
                } else {
                    dcgwPresentStatus = rpcResult.getResult().getRetVal();
                }
            } catch (Exception e) {
                LOG.warn("Exception {} when querying for isDcgwPresent {}, trace {}", e, destTepIp, e.getStackTrace());
            }
        }

        if (vpnInstances.isPresent()) {
            List<VpnInstance> vpnInstanceList = vpnInstances.get().getVpnInstance();
            Iterator<VpnInstance> vpnInstIter = vpnInstanceList.iterator();
            LOG.trace("vpnInstIter {}", vpnInstIter);
            while (vpnInstIter.hasNext()) {
                VpnInstance vpnInstance = vpnInstIter.next();
                LOG.trace("vpnInstance {}", vpnInstance);
                vpnId = VpnUtil.getVpnId(broker, vpnInstance.getVpnInstanceName());
                try {
                    VpnAfConfig vpnConfig = vpnInstance.getIpv4Family();
                    LOG.trace("vpnConfig {}", vpnConfig);
                    String rd = vpnConfig.getRouteDistinguisher();
                    if (rd == null || rd.isEmpty()) {
                        rd = vpnInstance.getVpnInstanceName();
                        LOG.trace("rd is null or empty. Assigning VpnInstanceName to rd {}", rd);
                    }
                    InstanceIdentifier<VpnToDpnList> srcId =
                        VpnUtil.getVpnToDpnListIdentifier(rd, srcDpnId);
                    Optional<VpnToDpnList> srcDpnInVpn =
                            VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, srcId);
                    if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                        destDpnId = new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId());
                        InstanceIdentifier<VpnToDpnList> destId =
                                VpnUtil.getVpnToDpnListIdentifier(rd, destDpnId);
                        Optional<VpnToDpnList> destDpnInVpn =
                                VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, destId);
                        if (!(srcDpnInVpn.isPresent() &&
                                destDpnInVpn.isPresent())) {
                            LOG.trace(" srcDpn {} - destDPN {}, do not share the VPN {} with rd {}.",
                                    srcDpnId, destDpnId, vpnInstance.getVpnInstanceName(), rd);
                            continue;
                        }
                    }
                    if (srcDpnInVpn.isPresent()) {
                        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                                .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces>
                            vpnInterfaces = srcDpnInVpn.get().getVpnInterfaces();
                        for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                                .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces vpnInterface : vpnInterfaces) {
                            InstanceIdentifier<VpnInterface> vpnIntfId =
                                VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getInterfaceName());
                            LOG.trace("vpnInterface {}", vpnInterface);
                            InstanceIdentifier<Adjacencies> path =
                                vpnIntfId.augmentation(Adjacencies.class);
                            Optional<Adjacencies> adjacencies =
                                VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);
                            LOG.trace("adjacencies {}", adjacencies);
                            if (adjacencies.isPresent()) {
                                List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
                                Iterator<Adjacency> adjacencyIterator = adjacencyList.iterator();

                                while (adjacencyIterator.hasNext()) {
                                    Adjacency adjacency = adjacencyIterator.next();
                                    try {
                                        if (action == UpdateRouteAction.ADVERTISE_ROUTE) {
                                            LOG.info("VPNInterfaceManager : Added Fib Entry rd {} prefix {} nextHop {} label {}",
                                                     rd, adjacency.getIpAddress(), adjacency.getNextHopIpList(),
                                                     adjacency.getLabel());
//                                            vrf = new VrfEntryBuilder().set
                                            if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                                                fibManager.handleRemoteRoute(true,
                                                        new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId()),
                                                        new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId()),
                                                        VpnUtil.getVpnId(broker, vpnInstance.getVpnInstanceName()),
                                                        rd, adjacency.getIpAddress(), srcTepIp, destTepIp);
                                            }
                                            if (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) {
                                                fibManager.populateFibOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                                            }
                                        }
                                        else if (action == UpdateRouteAction.WITHDRAW_ROUTE) {
                                            LOG.info("VPNInterfaceManager : Removed Fib entry rd {} prefix {}",
                                                     rd, adjacency.getIpAddress());
                                            if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                                                fibManager.handleRemoteRoute(false, srcDpnId,
                                                                new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId()),
                                                                vpnId, rd, adjacency.getIpAddress(), srcTepIp, destTepIp);
                                            }
                                            if ((tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) &&
                                                    (dcgwPresentStatus == VpnConstants.DCGWPresentStatus.Absent.getValue())) {
                                                bgpManager.withdrawPrefix(rd, adjacency.getIpAddress());
                                                fibManager.cleanUpDpnForVpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                                            }
                                        }
                                    } catch (Exception e) {
                                        LOG.error("Exception when updating prefix {} in vrf {} to BGP",
                                            adjacency.getIpAddress(), rd);
                                    }
                                }
                            } else {
                                LOG.trace("no adjacencies present for path {}.", path);
                            }

                        }
                        // if (action == UpdateRouteAction.WITHDRAW_ROUTE) {
                        //    fibManager.cleanUpDpnForVpn(dpnId, VpnUtil.getVpnId(broker, vpnInstance.getVpnInstanceName()), rd);
                        // }
                        // Go through all the VrfEntries and withdraw and readvertise the prefixes to BGP for which the nextHop is the SrcTepIp
                        if ((action == UpdateRouteAction.ADVERTISE_ROUTE) &&
                                (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue())) {
                            List<VrfEntry> vrfEntries = VpnUtil.getAllVrfEntries(broker, rd);
                            if (vrfEntries != null) {
                                for (VrfEntry vrfEntry : vrfEntries) {
                                    String destPrefix = vrfEntry.getDestPrefix().trim();
                                    int vpnLabel = vrfEntry.getLabel().intValue();
                                    List<String> nextHops = vrfEntry.getNextHopAddressList();
                                    if (nextHops.contains(srcTepIp.trim())) {
                                        bgpManager.withdrawPrefix(rd, destPrefix);
                                        bgpManager.advertisePrefix(rd, destPrefix, nextHops, vpnLabel);
                                    }
                                }
                            }
                        }
                    } else {
                        LOG.trace("dpnInVpn check failed for srcDpnId {}.", srcDpnId);
                    }
                } catch (Exception e) {
                    LOG.error("updatePrefixesForDPN {} in vpn {} failed", 0, vpnInstance.getVpnInstanceName(), e);
                }
            }
        } else {
            LOG.trace("No vpn instances present.");
        }
    }

    void publishAddNotification(final BigInteger dpnId, final String vpnName, final String rd) {
        LOG.debug("Sending notification for add dpn {} in vpn {} event ", dpnId, vpnName);
        AddEventData data = new AddEventDataBuilder().setVpnName(vpnName).setRd(rd).setDpnId(dpnId).build();
        AddDpnEvent event = new AddDpnEventBuilder().setAddEventData(data).build();
        final ListenableFuture<? extends Object> eventFuture = notificationPublishService.offerNotification(event);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.warn("Error in notifying listeners for add dpn {} in vpn {} event ", dpnId, vpnName, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.trace("Successful in notifying listeners for add dpn {} in vpn {} event ", dpnId, vpnName);
            }
        });
    }

    void publishRemoveNotification(final BigInteger dpnId, final String vpnName, final String rd) {
        LOG.debug("Sending notification for remove dpn {} in vpn {} event ", dpnId, vpnName);
        RemoveEventData data = new RemoveEventDataBuilder().setVpnName(vpnName).setRd(rd).setDpnId(dpnId).build();
        RemoveDpnEvent event = new RemoveDpnEventBuilder().setRemoveEventData(data).build();
        final ListenableFuture<? extends Object> eventFuture = notificationPublishService.offerNotification(event);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.warn("Error in notifying listeners for remove dpn {} in vpn {} event ", dpnId, vpnName, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.trace("Successful in notifying listeners for remove dpn {} in vpn {} event ", dpnId, vpnName);
            }
        });
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

    protected void addToNeutronRouterDpnsMap(String routerName, String vpnInterfaceName, WriteTransaction writeTxn) {
        BigInteger dpId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        if(dpId.equals(BigInteger.ZERO)) {
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} association model", vpnInterfaceName, routerName);
            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);

        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(broker, LogicalDatastoreType
                .OPERATIONAL, routerDpnListIdentifier);
        RouterInterfaces routerInterface = new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(vpnInterfaceName).build();
        if (optionalRouterDpnList.isPresent()) {
            writeTxn.merge(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                    RouterInterfaces.class, new RouterInterfacesKey(vpnInterfaceName)), routerInterface, true);
        } else {
            RouterDpnListBuilder builder = new RouterDpnListBuilder();
            builder.setRouterId(routerName);
            DpnVpninterfacesListBuilder dpnVpnList = new DpnVpninterfacesListBuilder().setDpnId(dpId);
            List<RouterInterfaces> routerInterfaces =  new ArrayList<>();
            routerInterfaces.add(routerInterface);
            builder.setDpnVpninterfacesList(Arrays.asList(dpnVpnList.build()));
            writeTxn.merge(LogicalDatastoreType.OPERATIONAL,
                    getRouterId(routerName),
                    new RouterDpnListBuilder().setRouterId(routerName).build(), true);
        }
    }

    protected void removeFromNeutronRouterDpnsMap(String routerName, String vpnInterfaceName, WriteTransaction writeTxn) {
        BigInteger dpId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        if(dpId.equals(BigInteger.ZERO)) {
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} dissociation model", vpnInterfaceName, routerName);
            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(broker, LogicalDatastoreType
                .OPERATIONAL, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            List<RouterInterfaces> routerInterfaces = optionalRouterDpnList.get().getRouterInterfaces();
            RouterInterfaces routerInterface = new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(vpnInterfaceName).build();

            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    if (writeTxn != null) {
                        writeTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                    } else {
                        MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                    }
                } else {
                    if (writeTxn != null) {
                        writeTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                                RouterInterfaces.class,
                                new RouterInterfacesKey(vpnInterfaceName)));
                    } else {
                        MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                                RouterInterfaces.class,
                                new RouterInterfacesKey(vpnInterfaceName)));
                    }
                }
            }
        }
    }

    protected void removeFromNeutronRouterDpnsMap(String routerName, String vpnInterfaceName, BigInteger dpId, WriteTransaction writeTxn) {
        if(dpId.equals(BigInteger.ZERO)) {
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} dissociation model", vpnInterfaceName, routerName);
            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(broker, LogicalDatastoreType
                .OPERATIONAL, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            List<RouterInterfaces> routerInterfaces = optionalRouterDpnList.get().getRouterInterfaces();
            RouterInterfaces routerInterface = new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(vpnInterfaceName).build();
            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    writeTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                } else {
                    writeTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                            RouterInterfaces.class,
                            new RouterInterfacesKey(vpnInterfaceName)));
                }
            }
        }
    }

    public void addMIPAdjacency(String vpnName,String vpnInterface, IpAddress prefix){

        LOG.trace("Adding {} adjacency to VPN Interface {} ",prefix,vpnInterface);
        InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
        InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, path);
        String nextHopIpAddr = null;
        String nextHopMacAddress = null;
        String ip = prefix.getIpv4Address().getValue();
        if (adjacencies.isPresent()) {
            List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
            ip = VpnUtil.getIpPrefix(ip);
            for (Adjacency adjacs : adjacencyList) {
                if (adjacs.getMacAddress() != null && !adjacs.getMacAddress().isEmpty()) {
                    nextHopIpAddr = adjacs.getIpAddress();
                    nextHopMacAddress = adjacs.getMacAddress();
                    break;
                }
            }
            if (nextHopMacAddress != null && ip != null) {
                synchronized (vpnInterface.intern()) {
                    String rd = VpnUtil.getVpnRd(broker, vpnName);
                    long label =
                            VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                    VpnUtil.getNextHopLabelKey((rd != null) ? rd : vpnName, ip));
                    String nextHopIp = nextHopIpAddr.split("/")[0];
                    Adjacency newAdj = new AdjacencyBuilder().setIpAddress(ip).setKey
                            (new AdjacencyKey(ip)).setNextHopIpList(Arrays.asList(nextHopIp)).build();
                    adjacencyList.add(newAdj);
                    Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencyList);
                    VpnInterface newVpnIntf = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(vpnInterface)).
                            setName(vpnInterface).setVpnInstanceName(vpnName).addAugmentation(Adjacencies.class, aug).build();
                    VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vpnIfId, newVpnIntf);
                }
                LOG.debug(" Successfully stored subnetroute Adjacency into VpnInterface {}", vpnInterface);
            }
        }

    }

    public void removeMIPAdjacency(String vpnName, String vpnInterface, IpAddress prefix) {
        String ip = VpnUtil.getIpPrefix(prefix.getIpv4Address().getValue());
        LOG.trace("Removing {} adjacency from Old VPN Interface {} ",ip,vpnInterface);
        InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
        InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);
        if (adjacencies.isPresent()) {
            synchronized (vpnInterface.intern()) {
                InstanceIdentifier<Adjacency> adjacencyIdentifier = InstanceIdentifier.builder(VpnInterfaces.class).
                        child(VpnInterface.class, new VpnInterfaceKey(vpnInterface)).augmentation(Adjacencies.class)
                        .child(Adjacency.class, new AdjacencyKey(ip)).build();
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, adjacencyIdentifier);
            }
            LOG.trace("Successfully Deleted Adjacency into VpnInterface {}", vpnInterface);
        }
    }

    class TunnelInterfaceStateListener extends AbstractDataChangeListener<StateTunnelList>  {
        public TunnelInterfaceStateListener(final DataBroker db, VpnInterfaceManager vpnIfMgr) {
            super(StateTunnelList.class);
        }


        @Override
        protected void remove(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList del) {
            LOG.trace("Tunnel deletion---- {}", del);
            handlePrefixesForDPNs(del, UpdateRouteAction.WITHDRAW_ROUTE);
        }

        @Override
        protected void update(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList original, StateTunnelList update) {
            LOG.trace("Tunnel updation---- {}", update);
            LOG.trace("ITM Tunnel {} of type {} state event changed from :{} to :{}",
                    update.getTunnelInterfaceName(),
                    fibManager.getTransportTypeStr(update.getTransportType().toString()),
                    original.isTunnelState(), update.isTunnelState());
                //withdraw all prefixes in all vpns for this dpn
            boolean isTunnelUp = update.isTunnelState();
                handlePrefixesForDPNs(update,isTunnelUp ?  UpdateRouteAction.ADVERTISE_ROUTE :
                                                            UpdateRouteAction.WITHDRAW_ROUTE);
        }

        @Override
        protected void add(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList add) {
            LOG.trace("Tunnel addition---- {}", add);

            if(!add.isTunnelState()) {
                LOG.trace(  "Tunnel {} is not yet UP.",
                            add.getTunnelInterfaceName());
                return;
            } else {
                LOG.trace("ITM Tunnel ,type {} ,State is UP b/w src: {} and dest: {}",
                        fibManager.getTransportTypeStr(add.getTransportType().toString()),
                        add.getSrcInfo().getTepDeviceId(), add.getDstInfo().getTepDeviceId());
                handlePrefixesForDPNs(add, UpdateRouteAction.ADVERTISE_ROUTE);
            }
        }
    }

}

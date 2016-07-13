/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;

import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.genius.mdsalutil.*;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRouteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PrefixToInterface;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
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
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

import com.google.common.base.Optional;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
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
    private ListenerRegistration<DataChangeListener> listenerRegistration, opListenerRegistration;
    private ConcurrentMap<String, Runnable> vpnIntfMap = new ConcurrentHashMap<>();
    private static final ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("NV-VpnIntfMgr-%d").build();
    private ExecutorService executorService = Executors.newSingleThreadExecutor(threadFactory);
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private IFibManager fibManager;
    private IMdsalApiManager mdsalManager;
    private OdlInterfaceRpcService interfaceManager;
    private ItmRpcService itmProvider;
    private IdManagerService idManager;
    private OdlArputilService arpManager;
    private NeutronvpnService neuService;
    private VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private InterfaceStateChangeListener interfaceListener;
    private VpnInterfaceOpListener vpnInterfaceOpListener;
    private ArpNotificationHandler arpNotificationHandler;
    protected enum UpdateRouteAction {
        ADVERTISE_ROUTE, WITHDRAW_ROUTE
    }
    /**
     * Responsible for listening to data change related to VPN Interface
     * Bind VPN Service on the interface and informs the BGP service
     *
     * @param db - dataBroker service reference
     */
    public VpnInterfaceManager(final DataBroker db, final IBgpManager bgpManager, NotificationService notificationService) {
        super(VpnInterface.class);
        broker = db;
        this.bgpManager = bgpManager;
        interfaceListener = new InterfaceStateChangeListener(db, this);
        vpnInterfaceOpListener = new VpnInterfaceOpListener();
        arpNotificationHandler = new ArpNotificationHandler(this, broker);
        notificationService.registerNotificationListener(arpNotificationHandler);
        vpnSubnetRouteHandler = new VpnSubnetRouteHandler(broker, bgpManager, this);
        notificationService.registerNotificationListener(vpnSubnetRouteHandler);
        registerListener(db);
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setInterfaceManager(OdlInterfaceRpcService interfaceManager) {
        this.interfaceManager = interfaceManager;
        interfaceListener.setInterfaceManager(interfaceManager);
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

    public void setNeutronvpnManager(NeutronvpnService neuService) { this.neuService = neuService; }

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
        } catch (final Exception e) {
            LOG.error("VPN Service DataChange listener registration fail!", e);
            throw new IllegalStateException("VPN Service registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> getInterfaceListenerPath() {
        return InstanceIdentifier.create(InterfacesState.class)
            .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.class);
    }

    @Override
    protected void add(final InstanceIdentifier<VpnInterface> identifier,
            final VpnInterface vpnInterface) {
        LOG.trace("VPN Interface key: {} , value: {}", identifier, vpnInterface );
        addInterface(identifier, vpnInterface);
    }

    private void addInterface(final InstanceIdentifier<VpnInterface> identifier,
                              final VpnInterface vpnInterface) {
        LOG.trace("VPN Interface add event - key: {}, value: {}" ,identifier, vpnInterface );
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface interfaceState =
            InterfaceUtils.getInterfaceStateFromOperDS(broker, interfaceName);
        if (interfaceState != null) {
            // Interface state is up
            processVpnInterfaceUp(InterfaceUtils.getDpIdFromInterface(interfaceState), interfaceName, interfaceState.getIfIndex());
        } else {
            LOG.trace("VPN interfaces are not yet operational.");
        }
    }

    protected void processVpnInterfaceUp(BigInteger dpId, String interfaceName, int lPortTag) {

        VpnInterface vpnInterface = VpnUtil.getConfiguredVpnInterface(broker, interfaceName);
        if(vpnInterface == null) {
            LOG.info("Unable to process add/up for interface {} as it is not configured", interfaceName);
            return;
        }
        String vpnName = vpnInterface.getVpnInstanceName();
        LOG.info("Binding vpn service to interface {} ", interfaceName);
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        if (vpnId == VpnConstants.INVALID_ID) {
            LOG.trace("VpnInstance to VPNId mapping is not yet available, bailing out now.");
            return;
        }
        synchronized (interfaceName.intern()) {
            if (VpnUtil.getOperationalVpnInterface(broker, vpnInterface.getName()) != null) {
                LOG.trace("VPN Interface already provisioned , bailing out from here.");
                return;
            }
            bindService(dpId, vpnName, interfaceName, lPortTag);
            updateDpnDbs(dpId, vpnName, interfaceName, true);
            processVpnInterfaceAdjacencies(dpId, VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()), vpnInterface);
        }

    }

    private void updateDpnDbs(BigInteger dpId, String vpnName, String interfaceName, boolean add) {
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        if (dpId == null) {
            dpId = InterfaceUtils.getDpnForInterface(interfaceManager, interfaceName);
        }
        if(!dpId.equals(BigInteger.ZERO)) {
            if(add)
                updateMappingDbs(vpnId, dpId, interfaceName, vpnName);
            else
                removeFromMappingDbs(vpnId, dpId, interfaceName, vpnName);
        }

    }

    private void bindService(BigInteger dpId, String vpnInstanceName, String vpnInterfaceName, int lPortTag) {
        int priority = VpnConstants.DEFAULT_FLOW_PRIORITY;
        long vpnId = VpnUtil.getVpnId(broker, vpnInstanceName);

        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();

        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_FIB_TABLE, ++instructionKey));

        BoundServices
            serviceInfo =
            InterfaceUtils.getBoundServices(String.format("%s.%s.%s", "vpn",vpnInstanceName, vpnInterfaceName),
                                            VpnConstants.L3VPN_SERVICE_IDENTIFIER, priority,
                                            VpnConstants.COOKIE_VM_INGRESS_TABLE, instructions);
        VpnUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                          InterfaceUtils.buildServiceId(vpnInterfaceName, VpnConstants.L3VPN_SERVICE_IDENTIFIER), serviceInfo);
        makeArpFlow(dpId, VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                    vpnId, ArpReplyOrRequest.REQUEST, NwConstants.ADD_FLOW);
        makeArpFlow(dpId, VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                vpnId, ArpReplyOrRequest.REPLY, NwConstants.ADD_FLOW);

    }

    private void processVpnInterfaceAdjacencies(BigInteger dpnId, final InstanceIdentifier<VpnInterface> identifier, VpnInterface intf) {
        String intfName = intf.getName();

        synchronized (intfName) {
            // Read NextHops
            InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, path);

            if (adjacencies.isPresent()) {
                List<Adjacency> nextHops = adjacencies.get().getAdjacency();
                List<Adjacency> value = new ArrayList<>();

                // Get the rd of the vpn instance
                String rd = getRouteDistinguisher(intf.getVpnInstanceName());

                String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId);
                if (nextHopIp == null){
                    LOG.error("NextHop for interface {} is null", intfName);
                }

                LOG.trace("NextHops are {}", nextHops);
                for (Adjacency nextHop : nextHops) {
                    String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
                    long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, VpnUtil
                            .getNextHopLabelKey((rd == null) ? intf.getVpnInstanceName() : rd, prefix));
                    String adjNextHop = nextHop.getNextHopIp();
                    value.add(new AdjacencyBuilder(nextHop).setLabel(label).setNextHopIp((adjNextHop != null && !adjNextHop.isEmpty()) ? adjNextHop : nextHopIp)
                            .setIpAddress(prefix).setKey(new AdjacencyKey(prefix)).build());
                    if(nextHop.getMacAddress() != null && !nextHop.getMacAddress().isEmpty()) {
                        VpnUtil.syncUpdate(
                                broker,
                                LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(
                                        VpnUtil.getVpnId(broker, intf.getVpnInstanceName()), prefix),
                                VpnUtil.getPrefixToInterface(dpnId, intf.getName(), prefix));
                    } else {
                        //Extra route adjacency
                        VpnUtil.syncUpdate(
                                broker,
                                LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getVpnToExtrarouteIdentifier(
                                        (rd != null) ? rd : intf.getVpnInstanceName(), nextHop.getIpAddress()),
                                VpnUtil.getVpnToExtraroute(nextHop.getIpAddress(), nextHop.getNextHopIp()));

                    }
                }

                Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);
                VpnInterface opInterface = VpnUtil.getVpnInterface(intfName, intf.getVpnInstanceName(), aug);
                InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(intfName);
                VpnUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface);
                for (Adjacency nextHop : aug.getAdjacency()) {
                    long label = nextHop.getLabel();
                    //String adjNextHop = nextHop.getNextHopIp();
                    if (rd != null) {
                        addPrefixToBGP(rd, nextHop.getIpAddress(),
                                            nextHopIp, label);
                    } else {
                        // ### add FIB route directly
                        addFibEntryToDS(intf.getVpnInstanceName(), nextHop.getIpAddress(),
                                            nextHopIp, (int) label);
                    }
                }
            }
        }
    }

    private void makeArpFlow(BigInteger dpId,short sIndex, int lPortTag, String vpnInterfaceName,
                             long vpnId, ArpReplyOrRequest replyOrRequest, int addOrRemoveFlow){
        List<MatchInfo> matches = new ArrayList<>();
        BigInteger metadata = MetaDataUtil.getMetaDataForLPortDispatcher(lPortTag, ++sIndex, BigInteger.valueOf(vpnId));
        BigInteger metadataMask = MetaDataUtil.getMetaDataMaskForLPortDispatcher(MetaDataUtil.METADATA_MASK_SERVICE_INDEX,
                MetaDataUtil.METADATA_MASK_LPORT_TAG, MetaDataUtil.METADATA_MASK_VRFID);

        // Matching Arp reply flows
        matches.add(new MatchInfo(MatchFieldType.eth_type, new long[] { NwConstants.ETHTYPE_ARP }));
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                metadata, metadataMask }));

        matches.add(new MatchInfo(MatchFieldType.arp_op, new long[] { replyOrRequest.getArpOperation() }));

        // Instruction to punt to controller
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.punt_to_controller, new String[] {}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        // Install the flow entry in L3_INTERFACE_TABLE
        String flowRef = VpnUtil.getFlowRef(dpId, NwConstants.L3_INTERFACE_TABLE,
                    NwConstants.ETHTYPE_ARP, lPortTag, replyOrRequest.getArpOperation());
        FlowEntity flowEntity;
        flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_INTERFACE_TABLE, flowRef,
                NwConstants.DEFAULT_ARP_FLOW_PRIORITY, replyOrRequest.getName(), 0, 0,
                VpnUtil.getCookieArpFlow(lPortTag), matches, instructions);

        if (addOrRemoveFlow == NwConstants.ADD_FLOW) {
            LOG.debug("Creating ARP Flow for interface {}",vpnInterfaceName);
            mdsalManager.installFlow(flowEntity);
        } else {
            LOG.debug("Deleting ARP Flow for interface {}",vpnInterfaceName);
            mdsalManager.removeFlow(flowEntity);
        }
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

    private synchronized void updateMappingDbs(long vpnId, BigInteger dpnId, String intfName, String vpnName) {
        String routeDistinguisher = getRouteDistinguisher(vpnName);
        String rd = (routeDistinguisher == null) ? vpnName : routeDistinguisher;
        InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
        Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces
            vpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();

        if (dpnInVpn.isPresent()) {
            VpnUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance
                    .op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                    new VpnInterfacesKey(intfName)), vpnInterface);
        } else {
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                                    VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                                    VpnUtil.getVpnInstanceOpDataBuilder(rd, vpnId));
            VpnToDpnListBuilder vpnToDpnList = new VpnToDpnListBuilder().setDpnId(dpnId);
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> vpnInterfaces =  new ArrayList<>();
            vpnInterfaces.add(vpnInterface);
            VpnUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id,
                              vpnToDpnList.setVpnInterfaces(vpnInterfaces).build());

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
        }
    }

    private synchronized void removeFromMappingDbs(long vpnId, BigInteger dpnId, String intfName, String vpnName) {
        //TODO: Delay 'DPN' removal so that other services can cleanup the entries for this dpn
        String rd = VpnUtil.getVpnRd(broker, vpnName);
        InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
        Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (dpnInVpn.isPresent()) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces
                    currVpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();

            if (vpnInterfaces.remove(currVpnInterface)) {
                if (vpnInterfaces.isEmpty()) {
                    VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id, VpnUtil.DEFAULT_CALLBACK);
                    fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd);
                } else {
                    VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                            .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                            new VpnInterfacesKey(intfName)), VpnUtil.DEFAULT_CALLBACK);
                }
            }
        }
    }

    private void addPrefixToBGP(String rd, String prefix, String nextHopIp, long label) {
        try {
            //FIXME: TBD once odl-fib yang has nexthoplist and related changes follow
            //bgpManager.addPrefix(rd, prefix, nextHopIp, (int)label);
        } catch(Exception e) {
            LOG.error("Add prefix failed", e);
        }
    }


    private InstanceIdentifier<VpnInterface> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    protected void remove( InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
        LOG.trace("Remove event - key: {}, value: {}" ,identifier, vpnInterface );
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();

        InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        Optional<VpnInterface> existingVpnInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, interfaceId);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface interfaceState =
            InterfaceUtils.getInterfaceStateFromOperDS(broker, interfaceName);

        if (existingVpnInterface.isPresent() && interfaceState != null) {
            processVpnInterfaceDown(InterfaceUtils.getDpIdFromInterface(interfaceState), interfaceName, interfaceState.getIfIndex(), false);
        } else {
            LOG.warn("VPN interface {} was unavailable in operational data store to handle remove event", interfaceName);
        }
    }

    protected void processVpnInterfaceDown(BigInteger dpId, String interfaceName, int lPortTag, boolean isInterfaceStateDown) {
        VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(broker, interfaceName);
        if(vpnInterface == null) {
            LOG.info("Unable to process delete/down for interface {} as it is not available in operational data store", interfaceName);
            return;
        }
        String vpnName = vpnInterface.getVpnInstanceName();
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);

        synchronized (interfaceName.intern()) {
            removeAdjacenciesFromVpn(identifier, vpnInterface);
            LOG.info("Unbinding vpn service from interface {} ", interfaceName);
            unbindService(dpId, vpnName, interfaceName, lPortTag, isInterfaceStateDown);

            //wait till DCN for removal of vpn interface in operational DS arrives
            Runnable notifyTask = new VpnNotifyTask();
            synchronized (interfaceName.intern()) {
                vpnIntfMap.put(interfaceName, notifyTask);
                synchronized (notifyTask) {
                    try {
                        notifyTask.wait(VpnConstants.MIN_WAIT_TIME_IN_MILLISECONDS);
                    } catch (InterruptedException e) {
                    }
                }
            }

        }
    }

    private void removeAdjacenciesFromVpn(final InstanceIdentifier<VpnInterface> identifier, VpnInterface intf) {
        //Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);

        String rd = VpnUtil.getVpnRd(broker, intf.getVpnInstanceName());
        LOG.trace("removeAdjacenciesFromVpn: For interface {} RD recovered for vpn {} as rd {}", intf.getName(),
                intf.getVpnInstanceName(), rd);
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    // Commenting the release of ID here as it will be released by FIB
                   /* VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                      VpnUtil.getNextHopLabelKey(rd, nextHop.getIpAddress()));
                    VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL,
                                   VpnUtil.getPrefixToInterfaceIdentifier(
                                       VpnUtil.getVpnId(broker, intf.getVpnInstanceName()),
                                       nextHop.getIpAddress()),
                                   VpnUtil.DEFAULT_CALLBACK);*/
                    if (rd.equals(intf.getVpnInstanceName())) {
                        //this is an internal vpn - the rd is assigned to the vpn instance name;
                        //remove from FIB directly
                        removeFibEntryFromDS(intf.getVpnInstanceName(), nextHop.getIpAddress());
                    } else {
                        removePrefixFromBGP(rd, nextHop.getIpAddress());
                    }
                }
            }
        }
    }


    private void unbindService(BigInteger dpId, String vpnInstanceName, String vpnInterfaceName,
                               int lPortTag, boolean isInterfaceStateDown) {
        if (!isInterfaceStateDown) {
            VpnUtil.delete(broker, LogicalDatastoreType.CONFIGURATION,
                           InterfaceUtils.buildServiceId(vpnInterfaceName,
                                                         VpnConstants.L3VPN_SERVICE_IDENTIFIER),
                           VpnUtil.DEFAULT_CALLBACK);
        }
        long vpnId = VpnUtil.getVpnId(broker, vpnInstanceName);
        makeArpFlow(dpId, VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                    vpnId, ArpReplyOrRequest.REQUEST, NwConstants.DEL_FLOW);
        makeArpFlow(dpId, VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                vpnId, ArpReplyOrRequest.REPLY, NwConstants.DEL_FLOW);
    }


    private void removePrefixFromBGP(String rd, String prefix) {
        try {
            bgpManager.deletePrefix(rd, prefix);
        } catch(Exception e) {
            LOG.error("Delete prefix failed", e);
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> identifier, VpnInterface original, VpnInterface update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating VPN Interface : key " + identifier + ",  original value=" + original + ", update " +
                    "value=" + update);
        }
        String oldVpnName = original.getVpnInstanceName();
        String newVpnName = update.getVpnInstanceName();
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
                    addNewAdjToVpnInterface(identifier, adj);
                }
            }
            for (Adjacency adj : oldAdjs) {
                delAdjFromVpnInterface(identifier, adj);
            }
        }
    }

    public void processArpRequest(IpAddress srcIP, PhysAddress srcMac, IpAddress targetIP, String srcInterface){
        SendArpResponseInput input = new SendArpResponseInputBuilder().setInterface(srcInterface)
                                                                    .setIpaddress(srcIP).setSrcIpAddress(targetIP).setMacaddress(srcMac).build();
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

    private String getTunnelInterfaceFlowRef(BigInteger dpnId, short tableId, String ifName) {
        return new StringBuilder().append(dpnId).append(tableId).append(ifName).toString();
    }



    public synchronized void addFibEntryToDS(String rd, String prefix,
            String nexthop, int label) {

        VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).
                    setNextHopAddress(nexthop).setLabel((long)label).build();
        LOG.debug("Created vrfEntry for {} nexthop {} label {}", prefix, nexthop, label);

        List<VrfEntry> vrfEntryList = new ArrayList<>();
        vrfEntryList.add(vrfEntry);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                    InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).
                    setVrfEntry(vrfEntryList).build();

        VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
    }

    public synchronized void addSubnetRouteFibEntryToDS(String rd, String prefix,
                                                        String nexthop, int label,long elantag) {

        SubnetRoute route = new SubnetRouteBuilder().setElantag(elantag).build();

        VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).
                setNextHopAddress(nexthop).setLabel((long)label).addAugmentation(SubnetRoute.class,route).build();
        LOG.debug("Created vrfEntry for {} nexthop {} label {} and elantag {}", prefix, nexthop, label, elantag);

        List<VrfEntry> vrfEntryList = new ArrayList<VrfEntry>();
        vrfEntryList.add(vrfEntry);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).
                setVrfEntry(vrfEntryList).build();

        VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
    }

    public synchronized void removeFibEntryFromDS(String rd, String prefix) {

        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {}", prefix, rd);

        InstanceIdentifierBuilder<VrfEntry> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).child(VrfEntry.class, new VrfEntryKey(prefix));
        InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
        VpnUtil.delete(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, VpnUtil.DEFAULT_CALLBACK);

    }

    public synchronized void removeVrfFromDS(String rd) {
        LOG.debug("Removing vrf table for  rd {}", rd);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        VpnUtil.delete(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, VpnUtil.DEFAULT_CALLBACK);

    }

    protected void addNewAdjToVpnInterface(InstanceIdentifier<VpnInterface> identifier, Adjacency adj) {

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

            adjacencies.add(new AdjacencyBuilder(adj).setLabel(label).setNextHopIp(adj.getNextHopIp())
                    .setIpAddress(prefix).setKey(new AdjacencyKey(prefix)).build());

            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencies);
            VpnInterface newVpnIntf = VpnUtil.getVpnInterface(currVpnIntf.getName(), currVpnIntf.getVpnInstanceName(), aug);

            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf);
            addExtraRoute(adj.getIpAddress(), adj.getNextHopIp(), rd, currVpnIntf.getVpnInstanceName(), (int) label, currVpnIntf.getName());

        }

    }

    protected void delAdjFromVpnInterface(InstanceIdentifier<VpnInterface> identifier, Adjacency adj) {
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
                            // Commenting the release of ID here as it will be released by FIB
                           /* VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                    VpnUtil.getNextHopLabelKey(rd, adj.getIpAddress()));*/
                            adjIt.remove();

                            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencies);
                            VpnInterface newVpnIntf = VpnUtil.getVpnInterface(currVpnIntf.getName(),
                                                                              currVpnIntf.getVpnInstanceName(),
                                                                              aug);

                            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf);

                            delExtraRoute(adj.getIpAddress(), rd, currVpnIntf.getVpnInstanceName());
                            break;
                        }

                    }
                }
            }
        }

    }

    protected void addExtraRoute(String destination, String nextHop, String rd, String routerID, int label, String intfName) {

        //add extra route to vpn mapping; advertise with nexthop as tunnel ip
        VpnUtil.syncUpdate(
            broker,
            LogicalDatastoreType.OPERATIONAL,
            VpnUtil.getVpnToExtrarouteIdentifier(
                (rd != null) ? rd : routerID, destination),
            VpnUtil.getVpnToExtraroute(destination, nextHop));

        if (intfName != null && !intfName.isEmpty()) {
            BigInteger dpnId = InterfaceUtils.getDpnForInterface(interfaceManager, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.warn("NextHop for interface {} is null / empty. Failed advertising extra route for prefix {}", intfName, destination);
            }
            nextHop = nextHopIp;
        }
        if (rd != null) {
            addPrefixToBGP(rd, destination, nextHop, label);
        } else {
            // ### add FIB route directly
            addFibEntryToDS(routerID, destination, nextHop, label);
        }
    }

    protected void delExtraRoute(String destination, String rd, String routerID) {
        if (rd != null) {
            removePrefixFromBGP(rd, destination);
        } else {
            // ### add FIB route directly
            removeFibEntryFromDS(routerID, destination);
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
                    Optional<Prefixes> prefixToInterface = Optional.absent();
                    prefixToInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                    VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                    if (!prefixToInterface.isPresent()) {
                        prefixToInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                        VpnUtil.getIpPrefix(adjacency.getNextHopIp())));
                    }
                    if (prefixToInterface.isPresent()) {
                        VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                        prefixToInterface.get().getIpAddress()),
                                VpnUtil.DEFAULT_CALLBACK);
                        updateDpnDbs(prefixToInterface.get().getDpnId(), del.getVpnInstanceName(), interfaceName, false);
                    }
                    Long ifCnt = 0L;
                    ifCnt = vpnInstOp.getVpnInterfaceCount();
                    LOG.trace("VpnInterfaceOpListener removed: interface name {} rd {} vpnName {} Intf count {}",
                            interfaceName, rd, vpnName, ifCnt);
                    if ((ifCnt != null) && (ifCnt > 0)) {
                        VpnUtil.asyncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                                VpnUtil.updateIntfCntInVpnInstOpData(ifCnt - 1, rd), VpnUtil.DEFAULT_CALLBACK);
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
                return;
            }
            executorService.execute(notifyTask);
        }

        @Override
        protected void update(InstanceIdentifier<VpnInterface> identifier, VpnInterface original, VpnInterface update) {
        }

        @Override
        protected void add(InstanceIdentifier<VpnInterface> identifier, VpnInterface add) {
            final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
            String interfaceName = key.getName();

            //increment the vpn interface count in Vpn Instance Op Data
            Long ifCnt = 0L;
            String rd = getRouteDistinguisher(add.getVpnInstanceName());
            if(rd == null || rd.isEmpty()) rd = add.getVpnInstanceName();
            VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(broker, rd);
            if(vpnInstOp != null &&  vpnInstOp.getVpnInterfaceCount() != null) {
                ifCnt = vpnInstOp.getVpnInterfaceCount();
            }

            LOG.trace("VpnInterfaceOpListener add: interface name {} rd {} interface count in Vpn Op Instance {}", interfaceName, rd, ifCnt);

            VpnUtil.asyncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                    VpnUtil.updateIntfCntInVpnInstOpData(ifCnt + 1, rd), VpnUtil.DEFAULT_CALLBACK);


        }
    }

    protected void updatePrefixesForDPN(BigInteger dpnId, UpdateRouteAction action) {

        LOG.info("Tunnel event triggered {} for Dpn:{} ", action.name(), dpnId);
        InstanceIdentifierBuilder<VpnInstances> idBuilder = InstanceIdentifier.builder(VpnInstances.class);
        InstanceIdentifier<VpnInstances> vpnInstancesId = idBuilder.build();
        Optional<VpnInstances> vpnInstances = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnInstancesId);

        if (vpnInstances.isPresent()) {
            List<VpnInstance> vpnInstanceList = vpnInstances.get().getVpnInstance();
            Iterator<VpnInstance> vpnInstIter = vpnInstanceList.iterator();
            while (vpnInstIter.hasNext()) {
                VpnInstance vpnInstance = vpnInstIter.next();
                try {
                    VpnAfConfig vpnConfig = vpnInstance.getIpv4Family();
                    String rd = vpnConfig.getRouteDistinguisher();
                    if (rd == null || rd.isEmpty()) {
                        rd = vpnInstance.getVpnInstanceName();
                    }
                    InstanceIdentifier<VpnToDpnList> id =
                        VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
                    Optional<VpnToDpnList> dpnInVpn =
                        VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
                    if (dpnInVpn.isPresent()) {
                        // if (action == UpdateRouteAction.ADVERTISE_ROUTE) {
                        //    fibManager.populateFibOnNewDpn(dpnId, VpnUtil
                        //        .getVpnId(broker, vpnInstance.getVpnInstanceName()), rd);
                        // }
                        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                            .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces>
                            vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
                        for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                            .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces vpnInterface : vpnInterfaces) {
                            InstanceIdentifier<VpnInterface> vpnIntfId =
                                VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getInterfaceName());
                            InstanceIdentifier<Adjacencies> path =
                                vpnIntfId.augmentation(Adjacencies.class);
                            Optional<Adjacencies> adjacencies =
                                VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);

                            if (adjacencies.isPresent()) {
                                List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
                                Iterator<Adjacency> adjacencyIterator = adjacencyList.iterator();

                                while (adjacencyIterator.hasNext()) {
                                    Adjacency adjacency = adjacencyIterator.next();
                                    try {
                                        if (action == UpdateRouteAction.ADVERTISE_ROUTE) {
                                            //FIXME: TBD once odl-fib yang has nexthoplist and related changes follow
                                            //bgpManager.addPrefix(rd, adjacency.getIpAddress(), adjacency.getNextHopIp(), adjacency.getLabel().intValue());

                                        }
                                        else if (action == UpdateRouteAction.WITHDRAW_ROUTE)
                                            bgpManager.deletePrefix(rd, adjacency.getIpAddress());
                                    } catch (Exception e) {
                                        LOG.error("Exception when updating prefix {} in vrf {} to BGP",
                                            adjacency.getIpAddress(), rd);
                                    }
                                }
                            }

                        }
                        // if (action == UpdateRouteAction.WITHDRAW_ROUTE) {
                        //    fibManager.cleanUpDpnForVpn(dpnId, VpnUtil.getVpnId(broker, vpnInstance.getVpnInstanceName()), rd);
                        // }
                    }
                } catch (Exception e) {
                    LOG.error("updatePrefixesForDPN {} in vpn {} failed", dpnId, vpnInstance.getVpnInstanceName(), e);
                }
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

    protected void addToNeutronRouterDpnsMap(String routerName, String vpnInterfaceName) {
        BigInteger dpId = InterfaceUtils.getDpnForInterface(interfaceManager, vpnInterfaceName);
        if(dpId.equals(BigInteger.ZERO)) {
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} association model", vpnInterfaceName, routerName);
            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);

        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(broker, LogicalDatastoreType
                .CONFIGURATION, routerDpnListIdentifier);
        RouterInterfaces routerInterface = new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(vpnInterfaceName).build();
        if (optionalRouterDpnList.isPresent()) {
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routerDpnListIdentifier.child(
                    RouterInterfaces.class,  new RouterInterfacesKey(vpnInterfaceName)), routerInterface);
        } else {
            MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION,
                    getRouterId(routerName),
                    new RouterDpnListBuilder().setRouterId(routerName).build());
            //VpnToDpnListBuilder vpnToDpnList = new VpnToDpnListBuilder().setDpnId(dpnId);
            DpnVpninterfacesListBuilder dpnVpnList = new DpnVpninterfacesListBuilder().setDpnId(dpId);
            List<RouterInterfaces> routerInterfaces =  new ArrayList<>();
            routerInterfaces.add(routerInterface);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routerDpnListIdentifier,
                    dpnVpnList.setRouterInterfaces(routerInterfaces).build());
        }
    }

    protected void removeFromNeutronRouterDpnsMap(String routerName, String vpnInterfaceName) {
        BigInteger dpId = InterfaceUtils.getDpnForInterface(interfaceManager, vpnInterfaceName);
        if(dpId.equals(BigInteger.ZERO)) {
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} dissociation model", vpnInterfaceName, routerName);
            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(broker, LogicalDatastoreType
                .CONFIGURATION, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            List<RouterInterfaces> routerInterfaces = optionalRouterDpnList.get().getRouterInterfaces();
            RouterInterfaces routerInterface = new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(vpnInterfaceName).build();

            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routerDpnListIdentifier);
                } else {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routerDpnListIdentifier.child(
                            RouterInterfaces.class,
                            new RouterInterfacesKey(vpnInterfaceName)));
                }
            }
        }
    }
	
	protected void removeFromNeutronRouterDpnsMap(String routerName, String vpnInterfaceName,BigInteger dpId) {
        if(dpId.equals(BigInteger.ZERO)) {
            LOG.warn("Could not retrieve dp id for interface {} to handle router {} dissociation model", vpnInterfaceName, routerName);
            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList = VpnUtil.read(broker, LogicalDatastoreType
                .CONFIGURATION, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            List<RouterInterfaces> routerInterfaces = optionalRouterDpnList.get().getRouterInterfaces();
            RouterInterfaces routerInterface = new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName)).setInterface(vpnInterfaceName).build();

            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routerDpnListIdentifier);
                } else {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routerDpnListIdentifier.child(
                            RouterInterfaces.class,
                            new RouterInterfacesKey(vpnInterfaceName)));
                }
            }
        }
    }

}

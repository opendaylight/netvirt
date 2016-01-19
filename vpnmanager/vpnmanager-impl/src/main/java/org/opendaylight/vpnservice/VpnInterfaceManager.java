/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import org.opendaylight.vpnservice.utilities.InterfaceUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;

import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.SendArpResponseInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.SendArpResponseInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

import com.google.common.base.Optional;

import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
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
    private ConcurrentMap<String, Runnable> vpnIntfMap = new ConcurrentHashMap<String, Runnable>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private IFibManager fibManager;
    private IMdsalApiManager mdsalManager;
    private OdlInterfaceRpcService interfaceManager;
    private ItmRpcService itmProvider;
    private IdManagerService idManager;
    private OdlArputilService arpManager;
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

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    public void setArpManager(OdlArputilService arpManager) {
        this.arpManager = arpManager;
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
            processVpnInterfaceUp(interfaceName, interfaceState.getIfIndex());
        } else {
            LOG.trace("VPN interfaces are not yet operational.");
        }
    }

    protected void processVpnInterfaceUp(String interfaceName, int lPortTag) {

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
        if (VpnUtil.getOperationalVpnInterface(broker, vpnInterface.getName()) != null) {
            LOG.trace("VPN Interface already provisioned , bailing out from here.");
            return;
        }
        synchronized (interfaceName.intern()) {

            bindService(vpnName, interfaceName, lPortTag);
            updateDpnDbs(vpnName, interfaceName, true);
            processVpnInterfaceAdjacencies(VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()), vpnInterface);
        }

    }

    private void updateDpnDbs(String vpnName, String interfaceName, boolean add) {
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        BigInteger dpId = InterfaceUtils.getDpnForInterface(interfaceManager, interfaceName);
        if(!dpId.equals(BigInteger.ZERO)) {
            if(add)
                updateMappingDbs(vpnId, dpId, interfaceName, vpnName);
            else
                removeFromMappingDbs(vpnId, dpId, interfaceName, vpnName);
        }

    }

    private void bindService(String vpnInstanceName, String vpnInterfaceName, int lPortTag) {
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
        VpnUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                          InterfaceUtils.buildServiceId(vpnInterfaceName, VpnConstants.L3VPN_SERVICE_IDENTIFIER), serviceInfo);
        makeArpFlow(VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName, vpnId, ArpReplyOrRequest.REQUEST, NwConstants.ADD_FLOW);

    }

    private void processVpnInterfaceAdjacencies(final InstanceIdentifier<VpnInterface> identifier, VpnInterface intf) {
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

                BigInteger dpnId = InterfaceUtils.getDpnForInterface(interfaceManager, intfName);
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
                    }
                }

                Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);
                VpnInterface opInterface = VpnUtil.getVpnInterface(intfName, intf.getVpnInstanceName(), aug);
                InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(intfName);
                VpnUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface);
                for (Adjacency nextHop : aug.getAdjacency()) {
                    long label = nextHop.getLabel();
                    String adjNextHop = nextHop.getNextHopIp();
                    if (rd != null) {
                        addPrefixToBGP(rd, nextHop.getIpAddress(),
                                            (adjNextHop != null && !adjNextHop.isEmpty()) ? adjNextHop : nextHopIp, label);
                    } else {
                        // ### add FIB route directly
                        addFibEntryToDS(intf.getVpnInstanceName(), nextHop.getIpAddress(),
                                            (adjNextHop != null && !adjNextHop.isEmpty()) ? adjNextHop : nextHopIp, (int) label);
                    }
                }
            }
        }
    }

    private void makeArpFlow(short sIndex, int lPortTag, String vpnInterfaceName, long vpnId, ArpReplyOrRequest replyOrRequest, int addOrRemoveFlow){
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
        instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));

        // Install the flow entry in L3_INTERFACE_TABLE
        BigInteger dpId = InterfaceUtils.getDpnForInterface(interfaceManager, vpnInterfaceName);
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
        org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces
            vpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();

        if (dpnInVpn.isPresent()) {
            VpnUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance
                    .op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                    new VpnInterfacesKey(intfName)), vpnInterface);
        } else {
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                                    VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                                    VpnUtil.getVpnInstanceOpDataBuilder(rd, vpnId));
            VpnToDpnListBuilder vpnToDpnList = new VpnToDpnListBuilder().setDpnId(dpnId);
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data
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
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data
                .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
            org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces
                    currVpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();

            if (vpnInterfaces.remove(currVpnInterface)) {
                if (vpnInterfaces.isEmpty()) {
                    VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id, VpnUtil.DEFAULT_CALLBACK);
                    fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd);
                } else {
                    VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                        org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data
                            .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                            new VpnInterfacesKey(intfName)), VpnUtil.DEFAULT_CALLBACK);
                }
            }
        }
    }

    private void addPrefixToBGP(String rd, String prefix, String nextHopIp, long label) {
        try {
            bgpManager.addPrefix(rd, prefix, nextHopIp, (int)label);
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
            processVpnInterfaceDown(interfaceName, interfaceState.getIfIndex(), false);
        } else {
            LOG.warn("VPN interface {} was unavailable in operational data store to handle remove event", interfaceName);
        }
    }

    protected void processVpnInterfaceDown(String interfaceName, int lPortTag, boolean isInterfaceStateDown) {
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
            unbindService(vpnName, interfaceName, lPortTag, isInterfaceStateDown);

            //wait till DCN for removal of vpn interface in operational DS arrives
            Runnable notifyTask = new VpnNotifyTask();
            synchronized (interfaceName.intern()) {
                vpnIntfMap.put(interfaceName, notifyTask);
                synchronized (notifyTask) {
                    try {
                        notifyTask.wait(VpnConstants.WAIT_TIME_IN_MILLISECONDS);
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
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                      VpnUtil.getNextHopLabelKey(rd, nextHop.getIpAddress()));
                    /*VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL,
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


    private void unbindService(String vpnInstanceName, String vpnInterfaceName, int lPortTag, boolean isInterfaceStateDown) {
        if (!isInterfaceStateDown) {
            VpnUtil.delete(broker, LogicalDatastoreType.CONFIGURATION,
                           InterfaceUtils.buildServiceId(vpnInterfaceName,
                                                         VpnConstants.L3VPN_SERVICE_IDENTIFIER),
                           VpnUtil.DEFAULT_CALLBACK);
        }
        long vpnId = VpnUtil.getVpnId(broker, vpnInstanceName);
        makeArpFlow(VpnConstants.L3VPN_SERVICE_IDENTIFIER, lPortTag, vpnInterfaceName,
                    vpnId, ArpReplyOrRequest.REQUEST, NwConstants.DEL_FLOW);
    }


    private void removePrefixFromBGP(String rd, String prefix) {
        try {
            bgpManager.deletePrefix(rd, prefix);
        } catch(Exception e) {
            LOG.error("Delete prefix failed", e);
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> identifier,
                                   VpnInterface original, VpnInterface update) {
        LOG.trace("Update VPN Interface {} , original {}, update {}",
                                                  identifier, original, update);
        String vpnName = original.getVpnInstanceName();

        boolean vpnNameChanged = false;
        String rd = getRouteDistinguisher(vpnName);
        String newRd = rd;
        String newVpnName = update.getVpnInstanceName();
        if(!vpnName.equals(newVpnName)) {
            //VPN for this interface got changed.
            //Remove the interface from old VPN and add it to new VPN
            newRd = getRouteDistinguisher(newVpnName);
            if(newRd.equals("")) {
                LOG.warn("VPN Instance {} not found. Update operation aborted", newVpnName);
                return;
            }
            vpnNameChanged = true;
            LOG.debug("New VPN Name for the interface {} is {}", newVpnName, original.getName());
        }

        List<Adjacency> oldAdjs = original.getAugmentation(Adjacencies.class).getAdjacency();
        List<Adjacency> newAdjs = update.getAugmentation(Adjacencies.class).getAdjacency();
        if(vpnNameChanged && newAdjs != null && !newAdjs.isEmpty()) {
            long label = VpnConstants.INVALID_ID;
            InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);
            if (adjacencies.isPresent()) {
                List<Adjacency> nextHops = adjacencies.get().getAdjacency();
                for(Adjacency nextHop : nextHops) {
                    label = nextHop.getLabel();
                    if(label == VpnConstants.INVALID_ID) {
                        //Generate label using ID Manager
                        label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                                    VpnUtil.getNextHopLabelKey(newRd, nextHop.getIpAddress()));
                    }
                    if (rd != null) {
                        removePrefixFromBGP(rd, nextHop.getIpAddress());
                    } else {
                        removeFibEntryFromDS(vpnName, nextHop.getIpAddress());
                    }
                    //updatePrefixToBGP(newRd, nextHop, nextHopIp, label);
                }
                processVpnInterfaceAdjacencies(identifier, update);
                VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, identifier, update);
            }
        } else if (oldAdjs != newAdjs) {
            //handle both addition and removal of adjacencies
            //currently, new adjacency may be an extra route
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
        else {
            LOG.debug("No Update information is available for VPN Interface to proceed");
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
                            VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                    VpnUtil.getNextHopLabelKey(rd, adj.getIpAddress()));
                            adjIt.remove();

                            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencies);
                            VpnInterface newVpnIntf = VpnUtil.getVpnInterface(currVpnIntf.getName(), currVpnIntf.getVpnInstanceName(), aug);

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

        if(intfName != null && !intfName.isEmpty()) {
            BigInteger dpnId = InterfaceUtils.getDpnForInterface(interfaceManager, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId);
            if (nextHopIp == null && !nextHopIp.isEmpty()) {
                LOG.error("NextHop for interface {} is null. Failed adding extra route for prefix {}", intfName, destination);
                return;
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

    class VpnInterfaceOpListener extends org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener<VpnInterface> {

        public VpnInterfaceOpListener() {
            super(VpnInterface.class);
        }

        @Override
        protected void remove(InstanceIdentifier<VpnInterface> identifier, VpnInterface del) {
            final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
            String interfaceName = key.getName();

            //increment the vpn interface count in Vpn Instance Op Data
            Long ifCnt = 0L;
            String rd = getRouteDistinguisher(del.getVpnInstanceName());
            if(rd.isEmpty()) rd = del.getVpnInstanceName();
            VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(broker, rd);
            if(vpnInstOp != null && vpnInstOp.getVpnInterfaceCount() != null) {
                ifCnt = vpnInstOp.getVpnInterfaceCount();
            }

            LOG.trace("VpnInterfaceOpListener remove: interface name {} rd {} interface count in Vpn Op Instance {}", interfaceName, rd, ifCnt);

            VpnUtil.asyncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                    VpnUtil.updateIntfCntInVpnInstOpData(ifCnt - 1, rd), VpnUtil.DEFAULT_CALLBACK);

            //TODO: Clean up the DPN List in Vpn Instance Op if ifCnt is zero

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
            if(rd.isEmpty()) rd = add.getVpnInstanceName();
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

        InstanceIdentifierBuilder<VpnInstances> idBuilder = InstanceIdentifier.builder(VpnInstances.class);
        InstanceIdentifier<VpnInstances> vpnInstancesId = idBuilder.build();
        Optional<VpnInstances> vpnInstances = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnInstancesId);

        if (vpnInstances.isPresent()) {
            List<VpnInstance> vpnInstanceList = vpnInstances.get().getVpnInstance();
            Iterator<VpnInstance> vpnInstIter = vpnInstanceList.iterator();
            while (vpnInstIter.hasNext()) {
                VpnInstance vpnInstance = vpnInstIter.next();
                VpnAfConfig vpnConfig = vpnInstance.getIpv4Family();
                String rd = vpnConfig.getRouteDistinguisher();

                InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
                Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
                if (dpnInVpn.isPresent()) {
                    if (action == UpdateRouteAction.ADVERTISE_ROUTE) {
                        fibManager.populateFibOnNewDpn(dpnId, VpnUtil
                            .getVpnId(broker, vpnInstance.getVpnInstanceName()), rd);
                    }
                    List<org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data
                        .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
                    for(org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data
                        .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces vpnInterface : vpnInterfaces) {
                        InstanceIdentifier<VpnInterface> vpnIntfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getInterfaceName());
                        InstanceIdentifier<Adjacencies> path = vpnIntfId.augmentation(Adjacencies.class);
                        Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, path);

                        if (adjacencies.isPresent()) {
                            List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
                            Iterator<Adjacency> adjacencyIterator = adjacencyList.iterator();

                            while (adjacencyIterator.hasNext()) {
                                Adjacency adjacency = adjacencyIterator.next();
                                try {
                                    if(action == UpdateRouteAction.ADVERTISE_ROUTE)
                                        bgpManager.addPrefix(rd, adjacency.getIpAddress(), adjacency.getNextHopIp(), adjacency.getLabel().intValue());
                                    else if(action == UpdateRouteAction.WITHDRAW_ROUTE)
                                        bgpManager.deletePrefix(rd, adjacency.getIpAddress());
                                } catch (Exception e) {
                                    LOG.error("Exception when updating prefix {} in vrf {} to BGP", adjacency.getIpAddress(), rd);
                                }
                            }
                        }

                    }
                    if (action == UpdateRouteAction.WITHDRAW_ROUTE) {
                        fibManager.cleanUpDpnForVpn(dpnId, VpnUtil
                            .getVpnId(broker, vpnInstance.getVpnInstanceName()), rd);
                    }
                }
            }
        }
    }

}

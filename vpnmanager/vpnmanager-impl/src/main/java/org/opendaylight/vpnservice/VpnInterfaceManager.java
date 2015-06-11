/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.L3tunnel;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.fibmanager.api.IFibManager;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.AdjacencyList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstance1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.AdjacenciesBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInterfaceManager extends AbstractDataChangeListener<VpnInterface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration, interfaceListenerRegistration;
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private IFibManager fibManager;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;
    private IdManagerService idManager;
    private Map<Long, Collection<BigInteger>> vpnToDpnsDb;
    private Map<BigInteger, Collection<String>> dpnToInterfaceDb;
    private InterfaceListener interfaceListener;

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                public void onSuccess(Void result) {
                    LOG.debug("Success in Datastore operation");
                }

                public void onFailure(Throwable error) {
                    LOG.error("Error in Datastore operation", error);
                };
            };

    /**
     * Responsible for listening to data change related to VPN Interface
     * Bind VPN Service on the interface and informs the BGP service
     * 
     * @param db - dataBroker service reference
     */
    public VpnInterfaceManager(final DataBroker db, final IBgpManager bgpManager) {
        super(VpnInterface.class);
        broker = db;
        this.bgpManager = bgpManager;
        vpnToDpnsDb = new ConcurrentHashMap<>();
        dpnToInterfaceDb = new ConcurrentHashMap<>();
        interfaceListener = new InterfaceListener();
        registerListener(db);
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setFibManager(IFibManager fibManager) {
        this.fibManager = fibManager;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
                interfaceListenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
            interfaceListenerRegistration = null;
        }
        LOG.info("VPN Interface Manager Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), VpnInterfaceManager.this, DataChangeScope.SUBTREE);
            interfaceListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getInterfaceListenerPath(), interfaceListener, DataChangeScope.SUBTREE);
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
        LOG.trace("key: {} , value: {}", identifier, vpnInterface );
        addInterface(identifier, vpnInterface);
    }

    private void addInterface(final InstanceIdentifier<VpnInterface> identifier,
                              final VpnInterface vpnInterface) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();
        InstanceIdentifierBuilder<Interface> idBuilder = 
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        Optional<Interface> port = read(LogicalDatastoreType.CONFIGURATION, id);
        if (port.isPresent()) {
            Interface interf = port.get();
            bindServiceOnInterface(interf, vpnInterface.getVpnInstanceName());
            updateNextHops(identifier, vpnInterface);
        }
    }

    private void updateNextHops(final InstanceIdentifier<VpnInterface> identifier, VpnInterface intf) {
        //Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = read(LogicalDatastoreType.CONFIGURATION, path);
        String intfName = intf.getName();

        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();
            List<Adjacency> value = new ArrayList<>();

            //Get the rd of the vpn instance
            String rd = getRouteDistinguisher(intf.getVpnInstanceName());

            BigInteger dpnId = interfaceManager.getDpnForInterface(intfName);
            String nextHopIp = interfaceManager.getEndpointIpForDpn(dpnId);


            LOG.trace("NextHops are {}", nextHops);
            for (Adjacency nextHop : nextHops) {
                String key = rd + VpnConstants.SEPARATOR + nextHop.getIpAddress();
                long label = getUniqueId(key);
                value.add(new AdjacencyBuilder(nextHop).setLabel(label).build());
            }

            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);
            VpnInterface opInterface = VpnUtil.getVpnInterface(intfName, intf.getVpnInstanceName(), aug);
            InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(intfName);
            syncWrite(LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface, DEFAULT_CALLBACK);
            for (Adjacency nextHop : nextHops) {
                String key = rd + VpnConstants.SEPARATOR + nextHop.getIpAddress();
                long label = getUniqueId(key);
                updatePrefixToBGP(rd, nextHop, nextHopIp, label);
            }
        }
    }

    private Integer getUniqueId(String idKey) {
        GetUniqueIdInput getIdInput = new GetUniqueIdInputBuilder()
                                           .setPoolName(VpnConstants.VPN_IDPOOL_NAME)
                                           .setIdKey(idKey).build();

        try {
            Future<RpcResult<GetUniqueIdOutput>> result = idManager.getUniqueId(getIdInput);
            RpcResult<GetUniqueIdOutput> rpcResult = result.get();
            if(rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id",e);
        }
        return 0;
    }

    private long getVpnId(String vpnName) {
        //TODO: This should be a Util function
        InstanceIdentifier<VpnInstance1> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).augmentation(VpnInstance1.class).build();
        Optional<VpnInstance1> vpnInstance = read(LogicalDatastoreType.OPERATIONAL, id);

        long vpnId = VpnConstants.INVALID_ID;
        if(vpnInstance.isPresent()) {
            vpnId = vpnInstance.get().getVpnId();
        }
        return vpnId;
    }

    private String getRouteDistinguisher(String vpnName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                                      .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> vpnInstance = read(LogicalDatastoreType.CONFIGURATION, id);
        String rd = "";
        if(vpnInstance.isPresent()) {
            VpnInstance instance = vpnInstance.get();
            VpnAfConfig config = instance.getIpv4Family();
            rd = config.getRouteDistinguisher();
        }
        return rd;
    }

    private synchronized void updateMappingDbs(long vpnId, BigInteger dpnId, String intfName, String rd) {
        Collection<BigInteger> dpnIds = vpnToDpnsDb.get(vpnId);
        if(dpnIds == null) {
            dpnIds = new HashSet<>();
        }
        if(dpnIds.add(dpnId)) {
            vpnToDpnsDb.put(vpnId, dpnIds);
            fibManager.populateFibOnNewDpn(dpnId, vpnId, rd);
        }

        Collection<String> intfNames = dpnToInterfaceDb.get(dpnId);
        if(intfNames == null) {
            intfNames = new ArrayList<>();
        }
        intfNames.add(intfName);
        dpnToInterfaceDb.put(dpnId, intfNames);
    }

    private synchronized void remoteFromMappingDbs(long vpnId, BigInteger dpnId, String inftName, String rd) {
        Collection<String> intfNames = dpnToInterfaceDb.get(dpnId);
        if(intfNames == null) {
            return;
        }
        intfNames.remove(inftName);
        dpnToInterfaceDb.put(dpnId, intfNames);
        //TODO: Delay 'DPN' removal so that other services can cleanup the entries for this dpn
        if(intfNames.isEmpty()) {
            Collection<BigInteger> dpnIds = vpnToDpnsDb.get(vpnId);
            if(dpnIds == null) {
                return;
            }
            dpnIds.remove(dpnId);
            vpnToDpnsDb.put(vpnId, dpnIds);
            fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd);
        }
    }

    private void bindServiceOnInterface(Interface intf, String vpnName) {
        LOG.trace("Bind service on interface {} for VPN: {}", intf, vpnName);

        long vpnId = getVpnId(vpnName);
        BigInteger dpId = interfaceManager.getDpnForInterface(intf.getName()); 
        if(dpId.equals(BigInteger.ZERO)) {
            LOG.warn("DPN for interface {} not found. Bind service on this interface aborted.", intf.getName());
            return;
        } else {
            String rd = getRouteDistinguisher(vpnName);
            updateMappingDbs(vpnId, dpId, intf.getName(), rd);
        }

        long portNo = interfaceManager.getPortForInterface(intf.getName());
        String flowRef = getVpnInterfaceFlowRef(dpId, VpnConstants.LPORT_INGRESS_TABLE, vpnId, portNo);

        String flowName = intf.getName();
        BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("8000001", 16);

        int priority = VpnConstants.DEFAULT_FLOW_PRIORITY;
        short gotoTableId = VpnConstants.FIB_TABLE;
        if(intf.getType().equals(L3tunnel.class)){
            gotoTableId = VpnConstants.LFIB_TABLE;
        }

        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        mkInstructions.add(new InstructionInfo(InstructionType.write_metadata, new BigInteger[] {
                BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { gotoTableId }));

        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[] {
                dpId, BigInteger.valueOf(portNo) }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, VpnConstants.LPORT_INGRESS_TABLE, flowRef,
                          priority, flowName, 0, 0, COOKIE_VM_INGRESS_TABLE, matches, mkInstructions);

        mdsalManager.installFlow(flowEntity);
    }

    private String getVpnInterfaceFlowRef(BigInteger dpId, short tableId,
            long vpnId, long portNo) {
        return new StringBuilder().append(dpId).append(tableId).append(vpnId).append(portNo).toString();
    }

    private void updatePrefixToBGP(String rd, Adjacency nextHop, String nextHopIp, long label) {
        try {
            bgpManager.addPrefix(rd, nextHop.getIpAddress(), nextHopIp, (int)label);
        } catch(Exception e) {
            LOG.error("Add prefix failed", e);
        }
    }

    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private InstanceIdentifier<VpnInterface> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    protected void remove( InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
        LOG.trace("Remove event - key: {}, value: {}" ,identifier, vpnInterface );
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();
        InstanceIdentifierBuilder<Interface> idBuilder = 
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        Optional<Interface> port = read(LogicalDatastoreType.CONFIGURATION, id);
        if (port.isPresent()) {
            Interface interf = port.get();
            removeNextHops(identifier, vpnInterface);
            unbindServiceOnInterface(interf, vpnInterface.getVpnInstanceName());
            //InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
            delete(LogicalDatastoreType.OPERATIONAL, identifier);
        } else {
            LOG.warn("No nexthops were available to handle remove event {}", interfaceName);
        }
    }

    private void removeNextHops(final InstanceIdentifier<VpnInterface> identifier, VpnInterface intf) {
        //Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = read(LogicalDatastoreType.OPERATIONAL, path);
        String intfName = intf.getName();
        String rd = getRouteDistinguisher(intf.getVpnInstanceName());
        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    removePrefixFromBGP(rd, nextHop);
                }
            }
        }
    }

    private <T extends DataObject> void delete(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    private void unbindServiceOnInterface(Interface intf, String vpnName) {
        LOG.trace("Unbind service on interface {} for VPN: {}", intf, vpnName);

        long vpnId = getVpnId(vpnName);
        BigInteger dpId = interfaceManager.getDpnForInterface(intf);
        if(dpId.equals(BigInteger.ZERO)) {
            LOG.warn("DPN for interface {} not found. Unbind service on this interface aborted.", intf.getName());
            return;
        } else {
            String rd = getRouteDistinguisher(vpnName);
            remoteFromMappingDbs(vpnId, dpId, intf.getName(), rd);
            LOG.debug("removed vpn mapping for interface {} from VPN RD {}", intf.getName(), rd);
        }

        long portNo = interfaceManager.getPortForInterface(intf);
        String flowRef = getVpnInterfaceFlowRef(dpId, VpnConstants.LPORT_INGRESS_TABLE, vpnId, portNo);

        String flowName = intf.getName();

        int priority = VpnConstants.DEFAULT_FLOW_PRIORITY;

        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[] {
                dpId, BigInteger.valueOf(portNo) }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, VpnConstants.LPORT_INGRESS_TABLE, flowRef,
                          priority, flowName, 0, 0, null, matches, null);
        LOG.debug("Remove ingress flow for port {} in dpn {}", portNo, dpId.intValue());

        mdsalManager.removeFlow(flowEntity);
    }

    private void removePrefixFromBGP(String rd, Adjacency nextHop) {
        try {
            bgpManager.deletePrefix(rd, nextHop.getIpAddress());
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
        if(!vpnName.equals(update.getVpnInstanceName())) {
            //VPN for this interface got changed. 
            //Remove the interface from old VPN and add it to new VPN
            String newVpnName = update.getVpnInstanceName();
            newRd = getRouteDistinguisher(newVpnName);
            if(newRd.equals("")) {
                LOG.warn("VPN Instance {} not found. Update operation aborted", newVpnName);
                return;
            }
            vpnNameChanged = true;
            LOG.debug("New VPN Name for the interface {} is {}", newVpnName, original.getName());
        }

        BigInteger dpnId = interfaceManager.getDpnForInterface(original.getName());
        String nextHopIp = interfaceManager.getEndpointIpForDpn(dpnId);
        //List<Adjacency> oldAdjs = original.getAugmentation(Adjacencies.class).getAdjacency();
        List<Adjacency> newAdjs = update.getAugmentation(Adjacencies.class).getAdjacency();
        if(vpnNameChanged && newAdjs != null && !newAdjs.isEmpty()) {
            long label = VpnConstants.INVALID_ID;
            InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
            Optional<Adjacencies> adjacencies = read(LogicalDatastoreType.OPERATIONAL, path);
            if (adjacencies.isPresent()) {
                List<Adjacency> nextHops = adjacencies.get().getAdjacency();
                for(Adjacency nextHop : nextHops) {
                    label = nextHop.getLabel();
                    if(label == VpnConstants.INVALID_ID) {
                        //Generate label using ID Manager
                        String key = newRd + VpnConstants.SEPARATOR + nextHop.getIpAddress();
                        label = getUniqueId(key);
                    }
                    removePrefixFromBGP(rd, nextHop);
                    //updatePrefixToBGP(newRd, nextHop, nextHopIp, label);
                }
                updateNextHops(identifier, update);
                asyncUpdate(LogicalDatastoreType.OPERATIONAL, identifier, update, DEFAULT_CALLBACK);
            }
        } else {
            LOG.debug("No Update information is available for VPN Interface to proceed");
        }
    }

    protected <T extends DataObject> void asyncUpdate(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                        InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private <T extends DataObject> void syncWrite(LogicalDatastoreType datastoreType,
                        InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        tx.submit();
    }

    synchronized Collection<BigInteger> getDpnsForVpn(long vpnId) {
        Collection<BigInteger> dpnIds = vpnToDpnsDb.get(vpnId);
        if(dpnIds != null) {
            return ImmutableList.copyOf(dpnIds);
        } else {
            return Collections.emptyList();
        }
    }

    VpnInterface getVpnInterface(String interfaceName) {
        Optional<VpnInterfaces> optVpnInterfaces = read(LogicalDatastoreType.CONFIGURATION, VpnUtil.getVpnInterfacesIdentifier());
        if(optVpnInterfaces.isPresent()) {
            List<VpnInterface> interfaces = optVpnInterfaces.get().getVpnInterface();
            for(VpnInterface intf : interfaces) {
                if(intf.getName().equals(interfaceName)) {
                    return intf;
                }
            }
        }
        return null;
    }

    private Interface getInterface(String interfaceName) {
        Optional<Interface> optInterface = read(LogicalDatastoreType.CONFIGURATION, VpnUtil.getInterfaceIdentifier(interfaceName));
        if(optInterface.isPresent()) {
            return optInterface.get();
        }
        return null;
    }

    private String getTunnelInterfaceFlowRef(BigInteger dpnId, short tableId, String ifName) {
        return new StringBuilder().append(dpnId).append(tableId).append(ifName).toString();
    }


    private void makeTunnelIngressFlow(BigInteger dpnId, String ifName, int addOrRemoveFlow) {
        long portNo = 0;
        String flowName = ifName;
        String flowRef = getTunnelInterfaceFlowRef(dpnId, VpnConstants.LPORT_INGRESS_TABLE, ifName);
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        if (NwConstants.ADD_FLOW == addOrRemoveFlow) {
            portNo = interfaceManager.getPortForInterface(ifName);
            matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[] {
                dpnId, BigInteger.valueOf(portNo) }));
            mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] {VpnConstants.LFIB_TABLE}));
        }

        BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("8000001", 16);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, VpnConstants.LPORT_INGRESS_TABLE, flowRef,
                VpnConstants.DEFAULT_FLOW_PRIORITY, flowName, 0, 0, COOKIE_VM_INGRESS_TABLE, matches, mkInstructions);

        if (NwConstants.ADD_FLOW == addOrRemoveFlow) {
            mdsalManager.installFlow(flowEntity);
        } else {
            mdsalManager.removeFlow(flowEntity);
        }
    }

    private class InterfaceListener extends AbstractDataChangeListener<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface>  {

        public InterfaceListener() {
            super(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.class);
        }

        @Override
        protected void remove(InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> identifier,
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface del) {
            LOG.trace("Operational Interface remove event - {}", del);
        }

        @Override
        protected void update(InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> identifier,
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface original, 
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface update) {
            LOG.trace("Operation Interface update event - Old: {}, New: {}", original, update);
            String interfaceName = update.getName();
            Interface intf = getInterface(interfaceName);
            if (intf != null && intf.getType().equals(L3tunnel.class)) {
                BigInteger dpnId = interfaceManager.getDpnForInterface(interfaceName);
                if(update.getOperStatus().equals(OperStatus.Up)) {
                    //Create ingress to LFIB
                    LOG.debug("Installing Ingress for tunnel interface {}", interfaceName);
                    makeTunnelIngressFlow(dpnId, interfaceName, NwConstants.ADD_FLOW);
                } else if(update.getOperStatus().equals(OperStatus.Down)) {
                    LOG.debug("Removing Ingress flow for tunnel interface {}", interfaceName);
                    makeTunnelIngressFlow(dpnId, interfaceName, NwConstants.DEL_FLOW);
                }
            } else {
                VpnInterface vpnInterface = getVpnInterface(interfaceName);
                if(vpnInterface != null) {
                    if(update.getOperStatus().equals(OperStatus.Up)) {
                        LOG.debug("Installing VPN related rules for interface {}", interfaceName);
                        addInterface(VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()), vpnInterface);
                    } else if(update.getOperStatus().equals(OperStatus.Down)) {
                        LOG.debug("Removing VPN related rules for interface {}", interfaceName);
                        VpnInterfaceManager.this.remove(VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()), vpnInterface);
                    }
                } else {
                    LOG.debug("No VPN Interface associated with interface {} to handle Update Operation", interfaceName);
                }
            }
        }

        @Override
        protected void add(InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> identifier,
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface add) {
            LOG.trace("Operational Interface add event - {}", add);
            String interfaceName = add.getName();
            Interface intf = getInterface(interfaceName);
            if (intf != null && intf.getType().equals(L3tunnel.class)) {
                BigInteger dpnId = interfaceManager.getDpnForInterface(interfaceName);
                if(add.getOperStatus().equals(OperStatus.Up)) {
                    //Create ingress to LFIB
                    LOG.debug("Installing Ingress for tunnel interface {}", interfaceName);
                    makeTunnelIngressFlow(dpnId, interfaceName, NwConstants.ADD_FLOW);
                }
            } else {
                VpnInterface vpnInterface = getVpnInterface(interfaceName);
                if(vpnInterface != null) {
                    if(add.getOperStatus().equals(OperStatus.Up)) {
                        LOG.debug("Installing VPN related rules for interface {}", interfaceName);
                        addInterface(VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()), vpnInterface);
                    }
                } else {
                    LOG.debug("No VPN Interface associated with interface {} to handle add Operation", interfaceName);
                }
            }
        }
    }
}

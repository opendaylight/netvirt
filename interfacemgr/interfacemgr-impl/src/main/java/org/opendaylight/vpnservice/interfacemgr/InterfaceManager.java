/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr;

import java.math.BigInteger;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.AbstractDataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.id.pool.GeneratedIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.BaseIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL3tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfMpls;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfStackedVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.L3tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.Mpls;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.StackedVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceManager extends AbstractDataChangeListener<Interface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private final IdManager idManager;
    private final Map<NodeConnectorId, String> mapNcToInterfaceName = new ConcurrentHashMap<>();
    private final Map<NodeId, String> dbDpnEndpoints = new ConcurrentHashMap<>();

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
                    new FutureCallback<Void>() {
                        public void onSuccess(Void result) {
                            LOG.debug("Success in Datastore write operation");
                        }

                        public void onFailure(Throwable error) {
                            LOG.error("Error in Datastore write operation", error);
                        }
                    };

    public InterfaceManager(final DataBroker db, final IdManager idmgr) {
        super(Interface.class);
        broker = db;
        idManager = idmgr;
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("Interface Manager Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), InterfaceManager.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("InterfaceManager DataChange listener registration fail!", e);
            throw new IllegalStateException("InterfaceManager registration Listener failed.", e);
        }
    }

    @Override
    protected void add(final InstanceIdentifier<Interface> identifier,
            final Interface imgrInterface) {
        LOG.trace("Adding interface key: " + identifier + ", value=" + imgrInterface );
        addInterface(identifier, imgrInterface);
    }

    private InstanceIdentifier<Interface> buildId(final InstanceIdentifier<Interface> identifier) {
        //TODO Make this generic and move to AbstractDataChangeListener or Utils.
        final InterfaceKey key = identifier.firstKeyOf(Interface.class, InterfaceKey.class);
        return buildId(key.getName());
    }

    private InstanceIdentifier<Interface> buildId(String interfaceName) {
        //TODO Make this generic and move to AbstractDataChangeListener or Utils.
        InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        return id;
    }


    private void addInterface(final InstanceIdentifier<Interface> identifier,
                              final Interface interf) {
        NodeConnector nodeConn = getNodeConnectorFromDataStore(interf);
        NodeConnectorId ncId = null;
        updateInterfaceState(identifier, interf, nodeConn);
        if (nodeConn == null) {
            ncId = getNodeConnectorIdFromInterface(interf);
        } else {
            ncId = nodeConn.getId();
        }
        mapNcToInterfaceName.put(ncId, interf.getName());
        if(interf.getType().isAssignableFrom(L3tunnel.class)) {
            NodeId nodeId = getNodeIdFromNodeConnectorId(ncId);
            IfL3tunnel l3Tunnel = interf.getAugmentation(IfL3tunnel.class);
            dbDpnEndpoints.put(nodeId, l3Tunnel.getLocalIp().getIpv4Address().getValue());
            LOG.trace("dbDpnEndpoints: {}",dbDpnEndpoints);
        }
    }

    private void updateInterfaceState(InstanceIdentifier<Interface> identifier,
                    Interface interf, NodeConnector nodeConn) {
        /* Update InterfaceState
         * 1. Get interfaces-state Identifier
         * 2. Add interface to interfaces-state/interface
         * 3. Get interface-id from id manager
         * 4. Update interface-state with following:
         *    admin-status = set to enable value
         *    oper-status = Down [?]
         *    if-index = interface-id
        */
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> id =
                        IfmUtil.buildStateInterfaceId(interf.getName());
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> stateIf =
                        read(LogicalDatastoreType.OPERATIONAL, id);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface stateIface;
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder ifaceBuilder =
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder();
        if(!stateIf.isPresent()) {
            // TODO: Get interface-id from IdManager
            String ifName = interf.getName();
            ifaceBuilder.setAdminStatus((interf.isEnabled()) ?  org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Up :
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Down);
            ifaceBuilder.setOperStatus(getOperStatus(nodeConn));

            ifaceBuilder.setIfIndex(getIfIndex(ifName)).setName(ifName).setType(interf.getType());
            ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(ifName));
            stateIface = ifaceBuilder.build();
            LOG.trace("Adding stateIface {} and id {} to OPERATIONAL DS", stateIface, id);
            asyncWrite(LogicalDatastoreType.OPERATIONAL, id, stateIface, DEFAULT_CALLBACK);
        } else {
            if(interf.isEnabled() != null) {
                ifaceBuilder.setAdminStatus((interf.isEnabled()) ?  org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Up :
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Down);
            }
            if(interf.getType() != null) {
                ifaceBuilder.setType(interf.getType());
            }
            ifaceBuilder.setOperStatus(getOperStatus(nodeConn));
            stateIface = ifaceBuilder.build();
            LOG.trace("updating OPERATIONAL data store with stateIface {} and id {}", stateIface, id);
            asyncUpdate(LogicalDatastoreType.OPERATIONAL, id, stateIface, DEFAULT_CALLBACK);
        }
    }

    private Integer getIfIndex(String ifName) {
        GetUniqueIdInput getIdInput = new GetUniqueIdInputBuilder()
        .setPoolName(IfmConstants.IFM_IDPOOL_NAME).setIdKey(ifName)
        .build();
        //TODO: Proper error handling once IdManager code is complete
        try {
            Future<RpcResult<GetUniqueIdOutput>> result = idManager.getUniqueId(getIdInput);
            RpcResult<GetUniqueIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue().intValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.trace("",e);
        }
        /* This currently returns null, so let us get it from IdManager DS
         * TODO: Start-delete-me
         */

        InstanceIdentifier<IdPool> id = IfmUtil.getPoolId(IfmConstants.IFM_IDPOOL_NAME);
        Optional<IdPool> globalPool = read(LogicalDatastoreType.OPERATIONAL, id );
        Long newIdValue = null;
        if (globalPool.isPresent()) {
            IdPool pool = globalPool.get();
            List<GeneratedIds> generatedIds = pool.getGeneratedIds();
            if ((generatedIds != null) && !generatedIds.isEmpty()) {
                for (GeneratedIds gen_id : generatedIds) {
                    if (gen_id.getIdKey().equals(ifName)) {
                        newIdValue = gen_id.getIdValue();
                        LOG.debug("Id {} found for interface %s ", newIdValue, ifName);
                        return newIdValue.intValue();
                    }
                }
            }
        }
        //TODO: End-delete-me
        LOG.debug("Unable to get valid ifIndex for interface {}", ifName);
        return IfmConstants.DEFAULT_IFINDEX;
    }

    private OperStatus getOperStatus(NodeConnector nodeConn) {
        LOG.trace("nodeConn is {}", nodeConn);
        if(nodeConn == null) {
            return OperStatus.Down;
        }else {
            return OperStatus.Up;
        }
    }

    private NodeConnector getNodeConnectorFromDataStore(Interface interf) {
        NodeConnectorId ncId = interf.getAugmentation(BaseIds.class).getOfPortId();
        //TODO: Replace with MDSAL Util method
        NodeId nodeId = getNodeIdFromNodeConnectorId(ncId);
        InstanceIdentifier<NodeConnector> ncIdentifier = InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class, new NodeKey(nodeId))
                        .child(NodeConnector.class, new NodeConnectorKey(ncId)).build();

        Optional<NodeConnector> nc = read(LogicalDatastoreType.OPERATIONAL, ncIdentifier);
        if(nc.isPresent()) {
            NodeConnector nodeConn = nc.get();
            LOG.trace("nodeConnector: {}",nodeConn);
            return nodeConn;
        }
        return null;
    }

    private NodeConnectorId getNodeConnectorIdFromInterface(Interface interf) {
        return interf.getAugmentation(BaseIds.class).getOfPortId();
    }

    private void delInterface(final InstanceIdentifier<Interface> identifier,
                              final Interface delInterface) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> id =
                        IfmUtil.buildStateInterfaceId(delInterface.getName());
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> stateIf =
                        read(LogicalDatastoreType.OPERATIONAL, id);
        if(stateIf.isPresent()) {
            LOG.trace("deleting interfaces:state OPERATIONAL data store with id {}", id);
            asyncRemove(LogicalDatastoreType.OPERATIONAL, id, DEFAULT_CALLBACK);
            NodeConnectorId ncId = getNodeConnectorIdFromInterface(delInterface);
            if(ncId != null) {
                mapNcToInterfaceName.remove(ncId);
                if(delInterface.getType().isAssignableFrom(L3tunnel.class)) {
                    Node node = getNodeFromDataStore(delInterface);
                    if((node != null) &&(node.getNodeConnector().isEmpty())) {
                        dbDpnEndpoints.remove(node.getId());
                        LOG.trace("dbDpnEndpoints: {}",dbDpnEndpoints);
                    }
                }
            }
        }
    }

    private Node getNodeFromDataStore(Interface interf) {
        NodeConnectorId ncId = interf.getAugmentation(BaseIds.class).getOfPortId();
        //TODO: Replace with MDSAL Util method
        NodeId nodeId = getNodeIdFromNodeConnectorId(ncId);
        InstanceIdentifier<Node> ncIdentifier = InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class, new NodeKey(nodeId)).build();

        Optional<Node> dpn = read(LogicalDatastoreType.OPERATIONAL, ncIdentifier);
        if(dpn.isPresent()) {
            Node node = dpn.get();
            LOG.trace("node: {}",node);
            return node;
        }
        return null;
    }

    private void updateInterface(final InstanceIdentifier<Interface> identifier,
                              final Interface original, final Interface update) {
        InstanceIdentifier<Interface> id = buildId(identifier);
        Optional<Interface> port = read(LogicalDatastoreType.CONFIGURATION, id);
        if(port.isPresent()) {
            Interface interf = port.get();
            NodeConnector nc = getNodeConnectorFromDataStore(update);
            updateInterfaceState(identifier, update, nc);
            /*
             * Alternative is to get from interf and update map irrespective if NCID changed or not.
             */
            if(nc != null) {
                // Name doesn't change. Is it present in update?
                mapNcToInterfaceName.put(nc.getId(), original.getName());
                if(interf.getType().isAssignableFrom(L3tunnel.class)) {
                    NodeId nodeId = getNodeIdFromNodeConnectorId(nc.getId());
                    IfL3tunnel l3Tunnel = interf.getAugmentation(IfL3tunnel.class);
                    dbDpnEndpoints.put(nodeId, l3Tunnel.getLocalIp().getIpv4Address().getValue());
                    LOG.trace("dbEndpoints: {}",dbDpnEndpoints);
                }
            }
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

    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        LOG.trace("remove - key: " + identifier + ", value=" + del );
        delInterface(identifier, del);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        LOG.trace("update - key: " + identifier + ", original=" + original + ", update=" + update );
        updateInterface(identifier, original, update);
    }

    protected <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    protected <T extends DataObject> void asyncUpdate(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    protected <T extends DataObject> void asyncRemove(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), callback);
    }

    void processPortAdd(NodeConnector port) {
        NodeConnectorId portId = port.getId();
        FlowCapableNodeConnector ofPort = port.getAugmentation(FlowCapableNodeConnector.class);
        LOG.debug("PortAdd: PortId { " + portId.getValue() + "} PortName {" + ofPort.getName() + "}");
        String ifName = this.mapNcToInterfaceName.get(portId);
        setInterfaceOperStatus(ifName, OperStatus.Up);
    }

    void processPortUpdate(NodeConnector oldPort, NodeConnector update) {
        //TODO: Currently nothing to do here.
    }

    void processPortDelete(NodeConnector port) {
        NodeConnectorId portId = port.getId();
        FlowCapableNodeConnector ofPort = port.getAugmentation(FlowCapableNodeConnector.class);
        LOG.debug("PortDelete: PortId { "+portId.getValue()+"} PortName {"+ofPort.getName()+"}");
        String ifName = this.mapNcToInterfaceName.get(portId);
        setInterfaceOperStatus(ifName, OperStatus.Down);
    }

    private void setInterfaceOperStatus(String ifName, OperStatus opStatus) {
        if (ifName != null) {
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> id =
                            IfmUtil.buildStateInterfaceId(ifName);
            Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> stateIf =
                            read(LogicalDatastoreType.OPERATIONAL, id);
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface stateIface;
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder ifaceBuilder =
                            new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder();
            if (stateIf.isPresent()) {
                stateIface = ifaceBuilder.setOperStatus(opStatus).setKey(IfmUtil.getStateInterfaceKeyFromName(ifName)).build();
                LOG.trace("Setting OperStatus for {} to {} in OPERATIONAL DS", ifName, opStatus);
                asyncUpdate(LogicalDatastoreType.OPERATIONAL, id, stateIface, DEFAULT_CALLBACK);
            }
        }
    }

    private Interface getInterfaceByIfName(String ifName) {
        InstanceIdentifier<Interface> id = buildId(ifName);
        Optional<Interface> port = read(LogicalDatastoreType.CONFIGURATION, id);
        if(port.isPresent()) {
            return port.get();
        }
        return null;
    }

    Long getPortForInterface(String ifName) {
        Interface iface = getInterfaceByIfName(ifName);
        return getPortNumForInterface(iface);
    }

    Long getPortForInterface(Interface intrf) {
        try {
            NodeConnector port = getNodeConnectorFromDataStore(intrf);
            NodeConnectorId id = null;
            if(port == null) {
                id = getNodeConnectorIdFromInterface(intrf);
            } else {
                id = port.getId();
            }
            String portNo = IfmUtil.getPortNoFromNodeConnectorId(id);
            return Long.parseLong(portNo);
        } catch (NullPointerException e) {
            LOG.error("Port for Interface {} not found", intrf.getName(), e);
        }
        return 0L;
    }

    public BigInteger getDpnForInterface(Interface intrf) {
        try {
            NodeConnector port = getNodeConnectorFromDataStore(intrf);
            NodeConnectorId id = null;
            if(port == null) {
                id = getNodeConnectorIdFromInterface(intrf);
            } else {
                id = port.getId();
            }
            //TODO: This should be an MDSAL Util method
            return new BigInteger(IfmUtil.getDpnFromNodeConnectorId(id));
        } catch (NullPointerException e) {
            LOG.error("dpn for Interface {} not found", intrf.getName(), e);
        }
        return BigInteger.ZERO;
    }

    BigInteger getDpnForInterface(String ifName) {
        Interface iface = getInterfaceByIfName(ifName);
        if(iface != null) {
            return getDpnForInterface(iface);
        }
        LOG.error("Interface {} doesn't exist", ifName);
        return BigInteger.ZERO;
    }

    String getEndpointIpForDpn(BigInteger dpnId) {
        //TODO: This should be MDSAL Util function
        NodeId dpnNodeId = IfmUtil.buildDpnNodeId(dpnId);
        return dbDpnEndpoints.get(dpnNodeId);
    }

    List<MatchInfo> getInterfaceIngressRule(String ifName) {
        Interface iface = getInterfaceByIfName(ifName);
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        Class<? extends InterfaceType> ifType = iface.getType();
        BigInteger dpn = this.getDpnForInterface(ifName);
        long portNo = this.getPortNumForInterface(iface).longValue();
        matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[] {dpn, BigInteger.valueOf(portNo)}));

        if (ifType.isInstance(L2vlan.class)) {
            IfL2vlan vlanIface = iface.getAugmentation(IfL2vlan.class);
            long vlanVid = vlanIface.getVlanId().longValue();
            if (vlanVid != 0) {
                matches.add(new MatchInfo(MatchFieldType.vlan_vid,
                            new long[] {vlanVid}));
                LOG.trace("L2Vlan: {}",vlanIface);
            }
        } else if (ifType.isInstance(L3tunnel.class)) {
            //TODO: Handle different tunnel types
            IfL3tunnel ifL3Tunnel = iface.getAugmentation(IfL3tunnel.class);
            Class<? extends TunnelTypeBase> tunnType = ifL3Tunnel.getTunnelType();
            LOG.trace("L3Tunnel: {}",ifL3Tunnel);
        } else if (ifType.isAssignableFrom(StackedVlan.class)) {
            IfStackedVlan ifStackedVlan = iface.getAugmentation(IfStackedVlan.class);
            LOG.trace("StackedVlan: {}",ifStackedVlan);
        } else if (ifType.isAssignableFrom(Mpls.class)) {
            IfMpls ifMpls = iface.getAugmentation(IfMpls.class);
            LOG.trace("Mpls: {}",ifMpls);
        }
        return matches;
    }

    public List<ActionInfo> getInterfaceEgressActions(String ifName) {
        Interface iface = getInterfaceByIfName(ifName);

        List<ActionInfo> listActionInfo = new ArrayList<ActionInfo>();
        Class<? extends InterfaceType> ifType = iface.getType();
        BigInteger dpn = this.getDpnForInterface(ifName);
        long portNo = this.getPortNumForInterface(iface).longValue();
        if (iface.isEnabled()) {

            if(ifType.isAssignableFrom(L2vlan.class)) {
                IfL2vlan vlanIface = iface.getAugmentation(IfL2vlan.class);
                LOG.trace("L2Vlan: {}",vlanIface);
                long vlanVid = (vlanIface == null) ? 0 : vlanIface.getVlanId();
                if (vlanVid != 0) {
                    listActionInfo.add(new ActionInfo(ActionType.push_vlan, new String[] {}));
                    listActionInfo.add(new ActionInfo(ActionType.set_field_vlan_vid,
                            new String[] { Long.toString(vlanVid) }));
                }
                listActionInfo.add(new ActionInfo(ActionType.output, new String[] { Long.toString(portNo)}));

            } else if (ifType.isAssignableFrom(L3tunnel.class)) {
                //TODO: Handle different tunnel types
                IfL3tunnel ifL3Tunnel = iface.getAugmentation(IfL3tunnel.class);
                Class<? extends TunnelTypeBase> tunnType = ifL3Tunnel.getTunnelType();
                LOG.trace("L3Tunnel: {}",ifL3Tunnel);
                //TODO: check switch_type and configure accordingly
                listActionInfo.add(new ActionInfo(ActionType.output, new String[] { Long.toString(portNo)}));

            } else if (ifType.isAssignableFrom(StackedVlan.class)) {
                IfStackedVlan ifStackedVlan = iface.getAugmentation(IfStackedVlan.class);
                LOG.trace("StackedVlan: {}",ifStackedVlan);
                // TODO: TBD
            } else if (ifType.isAssignableFrom(Mpls.class)) {
                IfMpls ifMpls = iface.getAugmentation(IfMpls.class);
                LOG.trace("Mpls: {}",ifMpls);
                // TODO: TBD
            }
        }
        return listActionInfo;

    }

    private NodeId getNodeIdFromNodeConnectorId(NodeConnectorId ncId) {
        return new NodeId(ncId.getValue().substring(0,ncId.getValue().lastIndexOf(":")));
    }

    private Long getPortNumForInterface(Interface iface) {
        try {
            NodeConnector port = getNodeConnectorFromDataStore(iface);
            FlowCapableNodeConnector ofPort = port.getAugmentation(FlowCapableNodeConnector.class);
            return ofPort.getPortNumber().getUint32();
        } catch (Exception e) {
            LOG.error("OFPort for Interface {} not found", iface.getName());
        }
        return 0L;
    }

}

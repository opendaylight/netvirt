/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import java.math.BigInteger;
import java.util.List;
import java.util.ArrayList;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;

import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.AdjacencyList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstance1;
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
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;

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
        registerListener(db);
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
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
        LOG.info("VPN Interface Manager Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), VpnInterfaceManager.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("VPN Service DataChange listener registration fail!", e);
            throw new IllegalStateException("VPN Service registration Listener failed.", e);
        }
    }

    @Override
    protected void add(final InstanceIdentifier<VpnInterface> identifier,
            final VpnInterface vpnInterface) {
        LOG.info("key: {} , value: {}", identifier, vpnInterface );
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
            bindServiceOnInterface(interf, getVpnId(vpnInterface.getVpnInstanceName()));
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
            //TODO: Get the endpoint IP from interface manager
            String nextHopIp = "10.0.0.1";

            if (!nextHops.isEmpty()) {
                LOG.info("NextHops are {}", nextHops);
                for (Adjacency nextHop : nextHops) {
                    //TODO: Generate label for the prefix and store it in the next hop model
                    long label = 200;

                    //TODO: Update BGP
                    updatePrefixToBGP(rd, nextHop, nextHopIp);
                    value.add(new AdjacencyBuilder(nextHop).setLabel(label).build());
                }
            }
            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);
            VpnInterface opInterface = VpnUtil.getVpnInterface(intfName, intf.getVpnInstanceName(), aug);
            InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(intfName);
            asyncWrite(LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface, DEFAULT_CALLBACK);
        }
    }

    private long getVpnId(String vpnName) {
        InstanceIdentifier<VpnInstance1> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).augmentation(VpnInstance1.class).build();
        Optional<VpnInstance1> vpnInstance = read(LogicalDatastoreType.CONFIGURATION, id);
        long vpnId = -1;
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

    private void bindServiceOnInterface(Interface intf, long vpnId) {
        LOG.info("Bind service on interface {} for VPN: {}", intf, vpnId);
        //TODO: Create Ingress flow on the interface to bind the VPN service
        //TODO: Get dpn ID from the interface manager
        long dpId = 1;
        short LPORT_INGRESS_TABLE = 0;
        //TODO: Get the port no from interface manager
        int portNo = 1;
        String flowRef = getL3InterfaceFlowRef(dpId, LPORT_INGRESS_TABLE, vpnId, portNo);

        String flowName = intf.getName();
        BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("8000001", 16);

        int priority = 10; //L3Constants.DEFAULT_L3_FLOW_PRIORITY;
        short gotoTableId = 21; //L3Constants.L3_FIB_TABLE;

        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        mkInstructions.add(new InstructionInfo(InstructionType.write_metadata, new BigInteger[] {
                BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { gotoTableId }));

        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.in_port, new long[] {
                dpId, portNo }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, LPORT_INGRESS_TABLE, flowRef,
                          priority, flowName, 0, 0, COOKIE_VM_INGRESS_TABLE, matches, mkInstructions);

        mdsalManager.installFlow(flowEntity);
    }

    private String getL3InterfaceFlowRef(long dpId, short tableId,
            long vpnId, int portNo) {
        return new StringBuilder().append(dpId).append(tableId).append(vpnId).append(portNo).toString();
    }

    private void updatePrefixToBGP(String rd, Adjacency nextHop, String nextHopIp) {
        //TODO: Update the Prefix to BGP
        //public void addPrefix(String rd, String prefix, String nextHop, int vpnLabel)
        int label = nextHop.getLabel().intValue();
        try {
            bgpManager.addPrefix(rd, nextHop.getIpAddress(), nextHopIp, label);
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
        LOG.info("Remove event - key: {}, value: {}" ,identifier, vpnInterface );
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();
        InstanceIdentifierBuilder<Interface> idBuilder = 
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        Optional<Interface> port = read(LogicalDatastoreType.CONFIGURATION, id);
        if (port.isPresent()) {
            Interface interf = port.get();
            unbindServiceOnInterface(interf);
            removeNextHops(identifier, vpnInterface);
        } else {
            LOG.info("No nexthops were available to handle remove event {}", interfaceName);
        }
    }

    private void removeNextHops(final InstanceIdentifier<VpnInterface> identifier, VpnInterface intf) {
        //Read NextHops
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = read(LogicalDatastoreType.OPERATIONAL, path);
        String intfName = intf.getName();

        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();

            if (!nextHops.isEmpty()) {
                LOG.trace("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    //TODO: Update BGP
                    removePrefixFromBGP(nextHop);
                }
            }

            InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(intfName);
            delete(LogicalDatastoreType.OPERATIONAL, interfaceId);
        }
    }

    private <T extends DataObject> void delete(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    private void unbindServiceOnInterface(Interface intf) {
        //TODO: Remove Ingress flow on the interface to unbind the VPN service
    }

    private void removePrefixFromBGP(Adjacency nextHop) {
        //TODO: Update the Prefix to BGP
    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> identifier, 
                                   VpnInterface original, VpnInterface update) {
        // TODO Auto-generated method stub

    }

    private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                        InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }
}

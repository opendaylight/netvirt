/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import java.util.List;
import java.util.ArrayList;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
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
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
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

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                public void onSuccess(Void result) {
                    LOG.info("Success in Datastore operation");
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
    public VpnInterfaceManager(final DataBroker db) {
        super(VpnInterface.class);
        broker = db;
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
        LOG.info("key: " + identifier + ", value=" + vpnInterface );
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
            bindServiceOnInterface(interf);
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

            if (!nextHops.isEmpty()) {
                LOG.info("NextHops are " + nextHops);
                for (Adjacency nextHop : nextHops) {
                    //TODO: Generate label for the prefix and store it in the next hop model
                    long label = 200;

                    //TODO: Update BGP
                    updatePrefixToBGP(nextHop);
                    value.add(new AdjacencyBuilder(nextHop).setLabel(label).build());
                }
            }
            Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(value);
            VpnInterface opInterface = VpnUtil.getVpnInterface(intfName, intf.getVpnInstanceName(), aug);
            InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(intfName);
            asyncWrite(LogicalDatastoreType.OPERATIONAL, interfaceId, opInterface, DEFAULT_CALLBACK);
        }
    }

    private void bindServiceOnInterface(Interface intf) {
        //TODO: Create Ingress flow on the interface to bind the VPN service
    }

    private void updatePrefixToBGP(Adjacency nextHop) {
        //TODO: Update the Prefix to BGP
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

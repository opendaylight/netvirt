/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.NextHopList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.next.hop.list.*;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInterface1;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

public class VpnInterfaceManager extends AbstractDataChangeListener<VpnInterface> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    
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
        if(port.isPresent()) {
            Interface interf = port.get();
            bindServiceOnInterface(interf);
            updateNextHops(identifier);
        }
    }

    private void updateNextHops(final InstanceIdentifier<VpnInterface> identifier) {
        //Read NextHops
        InstanceIdentifier<VpnInterface1> path = identifier.augmentation(VpnInterface1.class);
        Optional<VpnInterface1> nextHopList = read(LogicalDatastoreType.CONFIGURATION, path);
        
        if(nextHopList.isPresent()) {
            List<L3NextHops> nextHops = nextHopList.get().getL3NextHops();
            
            if(!nextHops.isEmpty()) {
                LOG.info("NextHops are "+ nextHops);
                for(L3NextHops nextHop : nextHops) {
                    //TODO: Generate label for the prefix and store it in the next hop model
                    
                    //TODO: Update BGP
                    updatePrefixToBGP(nextHop);
                }
            }
        }
    }

    private void bindServiceOnInterface(Interface intf) {
        //TODO: Create Ingress flow on the interface to bind the VPN service
    }

    private void updatePrefixToBGP(L3NextHops nextHop) {
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
    protected void remove( InstanceIdentifier<VpnInterface> identifier, VpnInterface del) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> identifier, 
                                   VpnInterface original, VpnInterface update) {
        // TODO Auto-generated method stub

    }

}

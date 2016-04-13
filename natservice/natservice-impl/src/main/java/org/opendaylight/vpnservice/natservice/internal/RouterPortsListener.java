/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.natservice.internal;

import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.floating.ip.info.RouterPortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.floating.ip.info.RouterPortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.floating.ip.info.router.ports.PortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.floating.ip.info.router.ports.PortsKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class RouterPortsListener extends AbstractDataChangeListener<RouterPorts> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(RouterPortsListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;


    public RouterPortsListener (final DataBroker db) {
        super(RouterPorts.class);
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
        LOG.info("Router ports Listener Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), RouterPortsListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("RouterPorts DataChange listener registration fail!", e);
            throw new IllegalStateException("RouterPorts Listener registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<RouterPorts> getWildCardPath() {
        return InstanceIdentifier.create(FloatingIpInfo.class).child(RouterPorts.class);
    }


    @Override
    protected void add(final InstanceIdentifier<RouterPorts> identifier, final RouterPorts routerPorts) {
        LOG.trace("Add router ports method - key: " + identifier + ", value=" + routerPorts );
        Optional<RouterPorts> optRouterPorts = NatUtil.read(broker, LogicalDatastoreType.OPERATIONAL, identifier);
        if(optRouterPorts.isPresent()) {
            RouterPorts ports = optRouterPorts.get();
            String routerName = ports.getRouterId();
            MDSALUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, identifier, 
                new RouterPortsBuilder().setKey(new RouterPortsKey(routerName)).setRouterId(routerName)
                    .setExternalNetworkId(routerPorts.getExternalNetworkId()).build());
        } else {
            String routerName = routerPorts.getRouterId();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, identifier, 
                new RouterPortsBuilder().setKey(new RouterPortsKey(routerName)).setRouterId(routerName)
                        .setExternalNetworkId(routerPorts.getExternalNetworkId()).build());
        }
    }

    @Override
    protected void remove(InstanceIdentifier<RouterPorts> identifier, RouterPorts routerPorts) {
        LOG.trace("Remove router ports method - key: " + identifier + ", value=" + routerPorts );
        //MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, identifier);

    }

    @Override
    protected void update(InstanceIdentifier<RouterPorts> identifier, RouterPorts original, RouterPorts update) {
        LOG.trace("Update router ports method - key: " + identifier + ", original=" + original + ", update=" + update );
    }
}

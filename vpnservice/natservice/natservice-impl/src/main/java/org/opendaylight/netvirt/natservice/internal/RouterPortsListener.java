/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.to.vpn.mapping.Routermapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.to.vpn.mapping.RoutermappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.to.vpn.mapping.RoutermappingKey;
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
        //Check if the router is associated with any BGP VPN and update the association
        String routerName = routerPorts.getRouterId();
        Uuid vpnName = NatUtil.getVpnForRouter(broker, routerName);
        if(vpnName != null) {
            InstanceIdentifier<Routermapping> routerMappingId = NatUtil.getRouterVpnMappingId(routerName);
            Optional<Routermapping> optRouterMapping = NatUtil.read(broker, LogicalDatastoreType.OPERATIONAL, routerMappingId);
            if(!optRouterMapping.isPresent()){
                Long vpnId = NatUtil.getVpnId(broker, vpnName.getValue());
                LOG.debug("Updating router {} to VPN {} association with Id {}", routerName, vpnName, vpnId);
                Routermapping routerMapping = new RoutermappingBuilder().setKey(new RoutermappingKey(routerName))
                                                 .setRouterName(routerName).setVpnName(vpnName.getValue()).setVpnId(vpnId).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, routerMappingId, routerMapping);
            }
        }
    }

    @Override
    protected void remove(InstanceIdentifier<RouterPorts> identifier, RouterPorts routerPorts) {
        LOG.trace("Remove router ports method - key: " + identifier + ", value=" + routerPorts );
        //MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, identifier);
        //Remove the router to vpn association mapping entry if at all present
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, NatUtil.getRouterVpnMappingId(routerPorts.getRouterId()));
    }

    @Override
    protected void update(InstanceIdentifier<RouterPorts> identifier, RouterPorts original, RouterPorts update) {
        LOG.trace("Update router ports method - key: " + identifier + ", original=" + original + ", update=" + update );
    }
}

/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6ServiceInterfaceEventListener
        extends AsyncClusteredDataTreeChangeListenerBase<Interface, Ipv6ServiceInterfaceEventListener> {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceInterfaceEventListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;
    private final Ipv6ServiceUtils ipv6ServiceUtils;
    private final JobCoordinator jobCoordinator;
    private final Ipv6ServiceEosHandler ipv6ServiceEosHandler;

    /**
     * Intialize the member variables.
     * @param broker the data broker instance.
     */
    @Inject
    public Ipv6ServiceInterfaceEventListener(DataBroker broker, IfMgr ifMgr, Ipv6ServiceUtils ipv6ServiceUtils,
            final JobCoordinator jobCoordinator, Ipv6ServiceEosHandler ipv6ServiceEosHandler) {
        super(Interface.class, Ipv6ServiceInterfaceEventListener.class);
        this.dataBroker = broker;
        this.ifMgr = ifMgr;
        this.ipv6ServiceUtils = ipv6ServiceUtils;
        this.jobCoordinator = jobCoordinator;
        this.ipv6ServiceEosHandler = ipv6ServiceEosHandler;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface del) {
        LOG.debug("Port removed {}, {}", key, del);
        if (!L2vlan.class.equals(del.getType())) {
            return;
        }

        // In ipv6service, we are only interested in the notification for NeutronPort, so we skip other notifications
        List<String> ofportIds = del.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty() || !isNeutronPort(del.getName())) {
            return;
        }

        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skipping further IPv6 processing on this node.");
            return;
        }
        Uuid portId = new Uuid(del.getName());
        VirtualPort port = ifMgr.obtainV6Interface(portId);
        if (port == null) {
            LOG.info("Port {} does not include IPv6Address, skipping.", portId);
            return;
        }

        if (port.getServiceBindingStatus()) {
            jobCoordinator.enqueueJob("IPv6-" + String.valueOf(portId), () -> {
                // Unbind Service
                ipv6ServiceUtils.unbindIpv6Service(portId.getValue());
                port.setServiceBindingStatus(false);
                return Collections.emptyList();
            }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
        }

        VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(port.getNetworkID());
        ifMgr.handleInterfaceStateEvent(port, ipv6ServiceUtils.getDpIdFromInterfaceState(del), routerPort,
                Ipv6ServiceConstants.DEL_FLOW);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface before, Interface after) {
        if (before.getType() == null && L2vlan.class.equals(after.getType())) {
            add(key, after);
        }
    }

    private boolean isNeutronPort(String name) {
        try {
            new Uuid(name);
            return true;
        } catch (IllegalArgumentException e) {
            LOG.debug("Port {} is not a Neutron Port, skipping.", name);
        }
        return false;
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface add) {
        List<String> ofportIds = add.getLowerLayerIf();

        if (!L2vlan.class.equals(add.getType())) {
            return;
        }

        // When a port is created, we receive multiple notifications.
        // In ipv6service, we are only interested in the notification for NeutronPort, so we skip other notifications
        if (ofportIds == null || ofportIds.isEmpty() || !isNeutronPort(add.getName())) {
            return;
        }

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface;
        iface = ipv6ServiceUtils.getInterface(add.getName());
        if (null != iface) {
            LOG.debug("Port {} is a Neutron port", iface.getName());
            NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
            BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));

            if (!dpId.equals(Ipv6ServiceConstants.INVALID_DPID)) {
                Uuid portId = new Uuid(iface.getName());
                VirtualPort port = ifMgr.obtainV6Interface(portId);
                if (port == null) {
                    LOG.info("Port {} does not include IPv6Address, skipping.", portId);
                    return;
                }

                Long ofPort = MDSALUtil.getOfPortNumberFromPortName(nodeConnectorId);
                ifMgr.updateDpnInfo(portId, dpId, ofPort);

                if (!ipv6ServiceEosHandler.isClusterOwner()) {
                    LOG.trace("Not a cluster Owner, skipping further IPv6 processing on this node.");
                    return;
                }

                VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(port.getNetworkID());
                if (routerPort == null) {
                    LOG.info("Port {} is not associated to a Router, skipping.", portId);
                    return;
                }
                ifMgr.handleInterfaceStateEvent(port, dpId, routerPort, Ipv6ServiceConstants.ADD_FLOW);

                if (!port.getServiceBindingStatus()) {
                    jobCoordinator.enqueueJob("IPv6-" + String.valueOf(portId), () -> {
                        // Bind Service
                        Long elanTag = ifMgr.getNetworkElanTag(routerPort.getNetworkID());
                        ipv6ServiceUtils.bindIpv6Service(portId.getValue(), elanTag, NwConstants.IPV6_TABLE);
                        port.setServiceBindingStatus(true);
                        return Collections.emptyList();
                    }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                }
            }
        }
    }

    @Override
    protected Ipv6ServiceInterfaceEventListener getDataTreeChangeListener() {
        return Ipv6ServiceInterfaceEventListener.this;
    }
}

/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubnetRouteInterfaceStateChangeListener extends AsyncDataTreeChangeListenerBase<Interface,
              SubnetRouteInterfaceStateChangeListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetRouteInterfaceStateChangeListener.class);
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;

    public SubnetRouteInterfaceStateChangeListener(final DataBroker dataBroker,
                                                   final VpnInterfaceManager vpnInterfaceManager,
                                                   final VpnSubnetRouteHandler vpnSubnetRouteHandler) {
        super(Interface.class, SubnetRouteInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected SubnetRouteInterfaceStateChangeListener getDataTreeChangeListener() {
        return SubnetRouteInterfaceStateChangeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("SubnetRouteInterfaceListener add: Received interface {} up event", intrf);
        try {

            if (intrf != null && intrf.getType() != null && (!intrf.getType().equals(Tunnel.class))) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("SUBNETROUTEINTF-" + intrf.getName(),
                        new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
                                String interfaceName = intrf.getName();
                                LOG.info("SubnetRouteInterfaceListener add: Received port UP event for interface {} ", interfaceName);
                                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
                                configInterface = InterfaceUtils.getInterface(dataBroker, interfaceName);
                                if (configInterface != null && (configInterface.getType() != null) && !configInterface.getType().equals(Tunnel.class)) {
                                    final VpnInterface vpnInterface = VpnUtil.getConfiguredVpnInterface(dataBroker, interfaceName);
                                    if (vpnInterface != null) {
                                        BigInteger dpnId = BigInteger.ZERO;
                                        try {
                                            dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                                        } catch (Exception e) {
                                            LOG.error("SubnetRouteInterfaceListener add: Unable to obtain dpnId for interface {},",
                                                    " subnetroute inclusion for this interface failed with exception {}",
                                                    interfaceName, e);
                                            return futures;
                                        }
                                        if (intrf.getOperStatus().equals(Interface.OperStatus.Up)) {
                                            /* Only if the interface is up, consider it for subnetRoute */
                                            vpnSubnetRouteHandler.onInterfaceUp(dpnId, intrf.getName());
                                        }
                                    } else {
                                        LOG.debug("SubnetRouteInterfaceListener add: Interface {} is not a vpninterface, ignoring.", intrf.getName());
                                    }
                                }

                                return futures;
                            }
                        });
            }
        } catch (Exception e) {
            LOG.error("SubnetRouteInterfaceListener add: Exception observed in handling addition for VPN Interface {}. ", intrf.getName(), e);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("SubnetRouteInterfaceListener remove: Received interface {} down event", intrf);
        try {
            if (intrf != null && intrf.getType() != null && (!intrf.getType().equals(Tunnel.class))) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("SUBNETROUTEINTF-" + intrf.getName(),
                        new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
                                String interfaceName = intrf.getName();
                                LOG.info("SubnetRouteInterfaceListener remove: Received port DOWN event for interface {} ", interfaceName);

                                InstanceIdentifier<VpnInterface> id = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
                                Optional<VpnInterface> optVpnInterface = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
                                if (optVpnInterface.isPresent()) {
                                    BigInteger dpnId = BigInteger.ZERO;
                                    VpnInterface vpnInterface = optVpnInterface.get();
                                    try {
                                        dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                                    } catch (Exception e) {
                                        LOG.error("SubnetRouteInterfaceListener remove: Unable to retrieve dpnId for interface {}. " +
                                                        "Fetching from vpn interface itself due to exception {}",
                                                intrf.getName(), e);
                                        dpnId = vpnInterface.getDpnId();
                                    }
                                    vpnSubnetRouteHandler.onInterfaceDown(dpnId, intrf.getName());
                                } else {
                                    LOG.debug("SubnetRouteInterfaceListener remove: Interface {} is not a vpninterface, ignoring.", intrf.getName());
                                }

                                return futures;
                            }
                        });
            }
        } catch (Exception e) {
            LOG.error("SubnetRouteInterfaceListener remove: Exception observed in handling deletion of VPN Interface {}. ",
                    intrf.getName(), e);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
                          Interface original, Interface update) {
        LOG.trace("SubnetRouteInterfaceListener update: Operation Interface update event - Old: {}, New: {}", original, update);
        String interfaceName = update.getName();

        try {
            if (update != null && (update.getType() != null) && !update.getType().equals(Tunnel.class)) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("SUBNETROUTEINTF-" + update.getName(),
                        new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();

                                final VpnInterface vpnInterface = VpnUtil.getConfiguredVpnInterface(dataBroker, interfaceName);
                                if (vpnInterface != null) {
                                    BigInteger dpnId = BigInteger.ZERO;
                                    try {
                                        dpnId = InterfaceUtils.getDpIdFromInterface(update);
                                    } catch (Exception e) {
                                        LOG.error("SubnetRouteInterfaceListener update: Unable to obtain dpnId for interface {} port down,",
                                                "failed with exception {}", interfaceName, e);
                                        return futures;
                                    }
                                    if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                                        vpnSubnetRouteHandler.onInterfaceUp(dpnId, update.getName());
                                    } else if (update.getOperStatus().equals(Interface.OperStatus.Down)
                                            || update.getOperStatus().equals(Interface.OperStatus.Unknown)) {
                                        /*
                                         * If the interface went down voluntarily (or) if the interface is not
                                         * reachable from control-path involuntarily, trigger subnetRoute election
                                         */
                                        vpnSubnetRouteHandler.onInterfaceDown(dpnId, update.getName());
                                    }
                                } else {
                                    LOG.debug("SubnetRouteInterfaceListener update: Interface {} is not a vpninterface, ignoring.", interfaceName);
                                }

                                return futures;
                            }
                        });
            }
        } catch (Exception e) {
            LOG.error("SubnetRouteInterfaceListener update: Exception observed in handling deletion of VPNInterface {}",
                    update.getName(), e);
        }
    }
}



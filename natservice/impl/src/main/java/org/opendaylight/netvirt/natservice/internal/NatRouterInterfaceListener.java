/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterInterfacesMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.Interfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatRouterInterfaceListener
    extends AsyncDataTreeChangeListenerBase<Interfaces, NatRouterInterfaceListener> {

    private static final Logger LOG = LoggerFactory.getLogger(NatRouterInterfaceListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final OdlInterfaceRpcService interfaceManager;
    private final IMdsalApiManager mdsalManager;
    private final NaptManager naptManager;
    private final NeutronvpnService neutronVpnService;

    @Inject
    public NatRouterInterfaceListener(final DataBroker dataBroker, final OdlInterfaceRpcService interfaceManager,
        final IMdsalApiManager mdsalManager,final NaptManager naptManager,
        final NeutronvpnService neutronvpnService) {
        super(Interfaces.class, NatRouterInterfaceListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interfaceManager = interfaceManager;
        this.mdsalManager = mdsalManager;
        this.naptManager = naptManager;
        this.neutronVpnService = neutronvpnService;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected NatRouterInterfaceListener getDataTreeChangeListener() {
        return NatRouterInterfaceListener.this;
    }

    @Override
    protected InstanceIdentifier<Interfaces> getWildCardPath() {
        return InstanceIdentifier.create(RouterInterfacesMap.class)
            .child(RouterInterfaces.class).child(Interfaces.class);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Interfaces> identifier, Interfaces interfaceInfo) {
        LOG.trace("add : Add event - key: {}, value: {}", interfaceInfo.key(), interfaceInfo);
        final String routerId = identifier.firstKeyOf(RouterInterfaces.class).getRouterId().getValue();
        final String interfaceName = interfaceInfo.getInterfaceId();
        if (NatUtil.isRouterInterfacePort(dataBroker, interfaceName)) {
            LOG.info("ADD: Ignoring Router Interface Port {} for processing of router {}", interfaceName, routerId);
            return;
        }
        try {
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                NatUtil.getRouterInterfaceId(interfaceName), getRouterInterface(interfaceName, routerId));
        } catch (Exception e) {
            LOG.error("add: Unable to write data in RouterInterface model", e);
        }

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .state.Interface interfaceState = NatUtil.getInterfaceStateFromOperDS(dataBroker, interfaceName);
        if (interfaceState != null) {
            Uint64 dpId = NatUtil.getDpIdFromInterface(interfaceState);
            if (dpId.equals(Uint64.ZERO)) {
                LOG.warn("ADD : Could not retrieve dp id for interface {} to handle router {} association model",
                        interfaceName, routerId);
                return;
            }
            final ReentrantLock lock = NatUtil.lockForNat(dpId);
            lock.lock();
            try {
                ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL,
                    operTx -> {
                        NatUtil.addToNeutronRouterDpnsMap(routerId, interfaceName, dpId, operTx);
                        NatUtil.addToDpnRoutersMap(routerId, interfaceName, dpId, operTx);
                    }), LOG, "Error processing NAT router interface addition");
            } finally {
                lock.unlock();
            }
            LOG.info("ADD: Added neutron-router-dpns mapping for interface {} of router {}", interfaceName, routerId);
        } else {
            LOG.info("add : Interface {} not yet operational to handle router interface add event in router {}",
                    interfaceName, routerId);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Interfaces> identifier, Interfaces interfaceInfo) {
        LOG.trace("remove : Remove event - key: {}, value: {}", interfaceInfo.key(), interfaceInfo);
        final String routerId = identifier.firstKeyOf(RouterInterfaces.class).getRouterId().getValue();
        final String interfaceName = interfaceInfo.getInterfaceId();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
            interfaceState = NatUtil.getInterfaceStateFromOperDS(dataBroker, interfaceName);
        if (interfaceState != null) {
            Uint64 dpId = NatUtil.getDpIdFromInterface(interfaceState);
            if (dpId.equals(Uint64.ZERO)) {
                LOG.warn(
                    "REMOVE : Could not retrieve DPN ID for interface {} to handle router {} dissociation model",
                    interfaceName, routerId);
                return;
            }

            final ReentrantLock lock = NatUtil.lockForNat(dpId);
            lock.lock();
            try {
                if (NatUtil.isSnatEnabledForRouterId(dataBroker, routerId)) {
                    NatUtil.removeSnatEntriesForPort(dataBroker, naptManager, mdsalManager, neutronVpnService,
                        interfaceName, routerId);
                }
                ListenableFutures.addErrorLogging(
                    txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, operTx -> {
                        //Delete the NeutronRouterDpnMap from the ODL:L3VPN operational model
                        NatUtil
                            .removeFromNeutronRouterDpnsMap(routerId, interfaceName, dpId, operTx);
                        //Delete the DpnRouterMap from the ODL:L3VPN operational model
                        NatUtil.removeFromDpnRoutersMap(dataBroker, routerId, interfaceName, dpId,
                            interfaceManager, operTx);
                    }), LOG, "Error handling NAT router interface removal");
                //Delete the RouterInterfaces maintained in the ODL:L3VPN configuration model
                ListenableFutures
                    .addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                        confTx -> confTx.delete(NatUtil.getRouterInterfaceId(interfaceName))), LOG,
                        "Error handling NAT router interface removal");
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interfaces> identifier, Interfaces original, Interfaces update) {
        LOG.trace("update key: {}, original: {}, update: {}", update.key(), original, update);
    }

    static RouterInterface getRouterInterface(String interfaceName, String routerName) {
        return new RouterInterfaceBuilder().withKey(new RouterInterfaceKey(interfaceName))
            .setInterfaceName(interfaceName).setRouterName(routerName).build();
    }
}


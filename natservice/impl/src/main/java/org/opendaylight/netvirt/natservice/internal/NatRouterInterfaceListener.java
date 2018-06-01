/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterInterfacesMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.Interfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatRouterInterfaceListener
    extends AsyncDataTreeChangeListenerBase<Interfaces, NatRouterInterfaceListener> {

    private static final Logger LOG = LoggerFactory.getLogger(NatRouterInterfaceListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final OdlInterfaceRpcService interfaceManager;

    @Inject
    public NatRouterInterfaceListener(final DataBroker dataBroker, final OdlInterfaceRpcService interfaceManager) {
        super(Interfaces.class, NatRouterInterfaceListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interfaceManager = interfaceManager;
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

        try {
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                NatUtil.getRouterInterfaceId(interfaceName), getRouterInterface(interfaceName, routerId));
        } catch (Exception e) {
            LOG.error("add: Unable to write data in RouterInterface model", e);
        }

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .state.Interface interfaceState = NatUtil.getInterfaceStateFromOperDS(dataBroker, interfaceName);
        if (interfaceState != null) {
            BigInteger dpId = NatUtil.getDpnForInterface(interfaceManager, interfaceName);
            if (dpId.equals(BigInteger.ZERO)) {
                LOG.warn("ADD : Could not retrieve dp id for interface {} to handle router {} association model",
                        interfaceName, routerId);
                return;
            }
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(operTx -> {
                NatUtil.addToNeutronRouterDpnsMap(dataBroker, routerId, interfaceName, dpId, operTx);
                NatUtil.addToDpnRoutersMap(dataBroker, routerId, interfaceName, dpId, operTx);
            }), LOG, "Error processing NAT router interface addition");
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

        //Delete the RouterInterfaces maintained in the ODL:L3VPN configuration model
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(confTx -> {
            confTx.delete(LogicalDatastoreType.CONFIGURATION, NatUtil.getRouterInterfaceId(interfaceName));
        }), LOG, "Error handling NAT router interface removal");

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(operTx -> {
            //Delete the NeutronRouterDpnMap from the ODL:L3VPN operational model
            NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerId, interfaceName, interfaceManager, operTx);

            //Delete the DpnRouterMap from the ODL:L3VPN operational model
            NatUtil.removeFromDpnRoutersMap(dataBroker, routerId, interfaceName, interfaceManager, operTx);
        }), LOG, "Error handling NAT router interface removal");
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

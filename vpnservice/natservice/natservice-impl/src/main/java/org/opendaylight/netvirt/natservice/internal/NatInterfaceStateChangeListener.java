/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatInterfaceStateChangeListener
    extends AsyncDataTreeChangeListenerBase<Interface, NatInterfaceStateChangeListener>
    implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NatInterfaceStateChangeListener.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private static final String NAT_DS = "NATDS";

    @Inject
    public NatInterfaceStateChangeListener(final DataBroker dataBroker,
            final OdlInterfaceRpcService odlInterfaceRpcService) {
        super(Interface.class, NatInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
    }

    @Override
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
    protected NatInterfaceStateChangeListener getDataTreeChangeListener() {
        return NatInterfaceStateChangeListener.this;
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("add : Interface {} up event received", intrf);
        if (!L2vlan.class.equals(intrf.getType())) {
            LOG.debug("add : Interface {} is not Vlan Interface.Ignoring", intrf.getName());
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        NatInterfaceStateAddWorker natIfaceStateAddWorker = new NatInterfaceStateAddWorker(intrf);
        coordinator.enqueueJob(NAT_DS + "-" + intrf.getName(), natIfaceStateAddWorker);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("remove : Interface {} removed event received", intrf);
        if (!L2vlan.class.equals(intrf.getType())) {
            LOG.debug("remove : Interface {} is not Vlan Interface.Ignoring", intrf.getName());
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        NatInterfaceStateRemoveWorker natIfaceStateRemoveWorker = new NatInterfaceStateRemoveWorker(intrf);
        coordinator.enqueueJob(NAT_DS + "-" + intrf.getName(), natIfaceStateRemoveWorker);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        LOG.trace("update : Operation Interface update event - Old: {}, New: {}", original, update);
        if (!L2vlan.class.equals(update.getType())) {
            LOG.debug("update : Interface {} is not Vlan Interface.Ignoring", update.getName());
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        NatInterfaceStateUpdateWorker natIfaceStateupdateWorker = new NatInterfaceStateUpdateWorker(original,update);
        coordinator.enqueueJob(NAT_DS + "-" + update.getName(), natIfaceStateupdateWorker);
    }

    void handleRouterInterfacesUpEvent(String routerName, String interfaceName, WriteTransaction writeOperTxn) {
        LOG.debug("handleRouterInterfacesUpEvent : Handling UP event for router interface {} in Router {}",
                interfaceName, routerName);
        BigInteger dpId = NatUtil.getDpnForInterface(odlInterfaceRpcService, interfaceName);
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.warn("handleRouterInterfacesUpEvent : Could not retrieve dp id for interface {} to handle router {} "
                    + "association model", interfaceName, routerName);
            return;
        }
        NatUtil.addToNeutronRouterDpnsMap(dataBroker, routerName, interfaceName, dpId, writeOperTxn);
        NatUtil.addToDpnRoutersMap(dataBroker, routerName, interfaceName, dpId, writeOperTxn);
    }

    void handleRouterInterfacesDownEvent(String routerName, String interfaceName, BigInteger dpnId,
                                         WriteTransaction writeOperTxn) {
        LOG.debug("handleRouterInterfacesDownEvent : Handling DOWN event for router Interface {} in Router {}",
                interfaceName, routerName);
        NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, interfaceName, dpnId, writeOperTxn);
        NatUtil.removeFromDpnRoutersMap(dataBroker, routerName, interfaceName, dpnId, odlInterfaceRpcService,
                writeOperTxn);
    }

    private class NatInterfaceStateAddWorker implements Callable<List<ListenableFuture<Void>>> {
        Interface iface;

        NatInterfaceStateAddWorker(Interface iface) {
            this.iface = iface;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            final String interfaceName = iface.getName();
            try {
                LOG.trace("call : Received interface {} PORT UP OR ADD event ", interfaceName);
                // We service only VM interfaces and Router interfaces here. We do not service Tunnel Interfaces here.
                // Tunnel events are directly serviced by TunnelInterfacesStateListener present as part of
                // VpnInterfaceManager
                RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
                if (routerInterface != null) {
                    String routerName = routerInterface.getRouterName();
                    LOG.debug("call : Router Name {} ", routerInterface.getRouterName());
                    WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                    handleRouterInterfacesUpEvent(routerName, interfaceName, writeOperTxn);
                    futures.add(writeOperTxn.submit());
                } else {
                    LOG.info("call : Unable to process add for interface {}", interfaceName);
                }
            } catch (Exception e) {
                LOG.error("call : Exception caught in Interface {} Operational State Up event",
                        interfaceName, e);
            }
            return futures;
        }
    }

    private class NatInterfaceStateRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        Interface iface;

        NatInterfaceStateRemoveWorker(Interface iface) {
            this.iface = iface;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final String interfaceName = iface.getName();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            try {
                LOG.trace("call : Received interface {} PORT DOWN or REMOVE event", interfaceName);
                BigInteger dpId = null;
                try {
                    dpId = NatUtil.getDpIdFromInterface(iface);
                } catch (Exception e) {
                    LOG.error("call : Unable to retrieve DPNID from Interface operational data store for "
                            + "Interface {}.", interfaceName, e);
                    InstanceIdentifier<VpnInterface> id = NatUtil.getVpnInterfaceIdentifier(interfaceName);
                    Optional<VpnInterface> cfgVpnInterface =
                            SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                                    dataBroker, LogicalDatastoreType.CONFIGURATION, id);
                    if (!cfgVpnInterface.isPresent()) {
                        LOG.warn("call : Interface {} is not a VPN Interface, ignoring.", interfaceName);
                        return futures;
                    }
                    for (String vpnName : cfgVpnInterface.get().getVpnInstanceNames()) {
                        InstanceIdentifier<VpnInterfaceOpDataEntry> idOper = NatUtil
                              .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                        Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                              SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                                    dataBroker, LogicalDatastoreType.CONFIGURATION, idOper);
                        if (optVpnInterface.isPresent()) {
                            dpId = optVpnInterface.get().getDpnId();
                            break;
                        }
                    }
                }
                if (dpId == null || dpId.equals(BigInteger.ZERO)) {
                    LOG.error("call : Unable to get DPN ID for the Interface {}", interfaceName);
                    return futures;
                }
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
                if (routerInterface != null) {
                    handleRouterInterfacesDownEvent(routerInterface.getRouterName(), interfaceName, dpId, writeOperTxn);
                }
                futures.add(writeOperTxn.submit());
            } catch (Exception e) {
                LOG.error("call : Exception observed in handling deletion of VPN Interface {}.", interfaceName, e);
            }
            return futures;
        }
    }

    private class NatInterfaceStateUpdateWorker implements Callable<List<ListenableFuture<Void>>> {
        Interface original;
        Interface update;

        NatInterfaceStateUpdateWorker(Interface original, Interface update) {
            this.original = original;
            this.update = update;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            try {
                final String interfaceName = update.getName();
                LOG.trace("call : Received interface {} state change event", interfaceName);
                BigInteger dpId = null;
                try {
                    dpId = NatUtil.getDpIdFromInterface(update);
                } catch (Exception e) {
                    LOG.error("call : Unable to retrieve DPN ID from Interface operational data "
                                    + "store for Interface {}",  update.getName(), e);
                    InstanceIdentifier<VpnInterface> id = NatUtil.getVpnInterfaceIdentifier(interfaceName);
                    Optional<VpnInterface> cfgVpnInterface =
                            SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                                    dataBroker, LogicalDatastoreType.CONFIGURATION, id);
                    if (!cfgVpnInterface.isPresent()) {
                        LOG.warn("call : Interface {} is not a VPN Interface, ignoring.", interfaceName);
                        return futures;
                    }
                    for (String vpnName : cfgVpnInterface.get().getVpnInstanceNames()) {
                        InstanceIdentifier<VpnInterfaceOpDataEntry> idOper = NatUtil
                              .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                        Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                            SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                                  dataBroker, LogicalDatastoreType.OPERATIONAL, idOper);
                        if (optVpnInterface.isPresent()) {
                            dpId = optVpnInterface.get().getDpnId();
                            break;
                        }
                    }
                }
                if (dpId == null || dpId.equals(BigInteger.ZERO)) {
                    LOG.error("call : Unable to get DPN ID for the Interface {}", interfaceName);
                    return futures;
                }
                LOG.debug("call : DPN ID {} for the interface {} ", dpId, interfaceName);
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
                if (routerInterface != null) {
                    Interface.OperStatus originalOperStatus = original.getOperStatus();
                    Interface.OperStatus updateOperStatus = update.getOperStatus();
                    if (originalOperStatus != updateOperStatus) {
                        String routerName = routerInterface.getRouterName();
                        if (updateOperStatus == Interface.OperStatus.Unknown) {
                            LOG.debug("call : DPN {} connnected to the interface {} has gone down."
                                    + "Hence clearing the dpn-vpninterfaces-list entry from the"
                                    + " neutron-router-dpns model in the ODL:L3VPN", dpId, interfaceName);
                            // If the interface state is unknown, it means that the corresponding DPN has gone down.
                            // So remove the dpn-vpninterfaces-list from the neutron-router-dpns model.
                            NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, dpId, writeOperTxn);
                        } else if (updateOperStatus == Interface.OperStatus.Up) {
                            LOG.debug("call : DPN {} connnected to the interface {} has come up. Hence adding"
                                    + " the dpn-vpninterfaces-list entry from the neutron-router-dpns model"
                                    + " in the ODL:L3VPN", dpId, interfaceName);
                            handleRouterInterfacesUpEvent(routerName, interfaceName, writeOperTxn);
                        }
                    }
                }
                futures.add(writeOperTxn.submit());
            } catch (Exception e) {
                LOG.error("call : Exception observed in handling updation of VPN Interface {}.", update.getName(), e);
            }
            return futures;
        }
    }
}

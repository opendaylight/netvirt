/*
 * Copyright (c) 2016 - 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.Optional;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.neutron.vip.states.VipState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatSouthboundEventHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(NatSouthboundEventHandlers.class);
    private static final String NAT_DS = "NATDS";
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final JobCoordinator coordinator;
    private final FloatingIPListener floatingIPListener;
    private final NeutronvpnService neutronVpnService;
    private final IMdsalApiManager mdsalManager;
    private final NaptManager naptManager;
    private final VipStateTracker vipStateTracker;
    Table<Interface.OperStatus, Interface.OperStatus, IntfTransitionState> stateTable = HashBasedTable.create();

    enum IntfTransitionState {
        STATE_UP,
        STATE_DOWN,
        STATE_IGNORE
    }

    private void initialize() {
        stateTable.put(Interface.OperStatus.Up, Interface.OperStatus.Down, IntfTransitionState.STATE_DOWN);
        stateTable.put(Interface.OperStatus.Down, Interface.OperStatus.Up, IntfTransitionState.STATE_UP);
        stateTable.put(Interface.OperStatus.Unknown, Interface.OperStatus.Up, IntfTransitionState.STATE_UP);
        stateTable.put(Interface.OperStatus.Unknown, Interface.OperStatus.Down, IntfTransitionState.STATE_DOWN);
        stateTable.put(Interface.OperStatus.Up, Interface.OperStatus.Unknown, IntfTransitionState.STATE_DOWN);
    }

    @Inject
    public NatSouthboundEventHandlers(final DataBroker dataBroker,
            final OdlInterfaceRpcService odlInterfaceRpcService, final JobCoordinator coordinator,
            final FloatingIPListener floatingIPListener,final NeutronvpnService neutronvpnService,
            final IMdsalApiManager mdsalManager, final NaptManager naptManager, final VipStateTracker vipStateTracker) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.coordinator = coordinator;
        this.floatingIPListener = floatingIPListener;
        this.neutronVpnService = neutronvpnService;
        this.mdsalManager = mdsalManager;
        this.naptManager = naptManager;
        this.vipStateTracker = vipStateTracker;
        initialize();
    }

    public void handleAdd(String interfaceName, Uint64 intfDpnId, RouterInterface routerInterface) {
        handleAdd(interfaceName, intfDpnId, routerInterface, null);
    }

    public void handleAdd(String interfaceName, Uint64 intfDpnId,
                          RouterInterface routerInterface, @Nullable VipState vipState) {
        String routerName = routerInterface.getRouterName();
        if (NatUtil.validateIsIntefacePartofRouter(dataBroker, routerName, interfaceName)) {
            NatInterfaceStateAddWorker natIfaceStateAddWorker = new NatInterfaceStateAddWorker(
                interfaceName,
                intfDpnId, routerName);
            coordinator.enqueueJob(NAT_DS + "-" + interfaceName, natIfaceStateAddWorker);

            NatFlowAddWorker natFlowAddWorker = new NatFlowAddWorker(interfaceName, routerName,
                intfDpnId, vipState);
            coordinator.enqueueJob(NAT_DS + "-" + interfaceName, natFlowAddWorker,
                NatConstants.NAT_DJC_MAX_RETRIES);
        } else {
            LOG.error("NAT Service : Router {} not valid for interface {} during add. Deleting it from "
                + "router-interfaces DS", routerName, routerInterface);
            try {
                SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    NatUtil.getRouterInterfaceId(interfaceName));
            } catch (TransactionCommitFailedException e) {
                LOG.error("NAT Service : Failed to stale Router Interface entry for interface {}",
                    interfaceName, e);
            }
        }
    }

    public void handleRemove(String interfaceName, Uint64 intfDpnId, RouterInterface routerInterface) {
        String routerName = routerInterface.getRouterName();
        NatInterfaceStateRemoveWorker natIfaceStateRemoveWorker = new NatInterfaceStateRemoveWorker(interfaceName,
                intfDpnId, routerName);
        coordinator.enqueueJob(NAT_DS + "-" + interfaceName, natIfaceStateRemoveWorker);

        NatFlowRemoveWorker natFlowRemoveWorker = new NatFlowRemoveWorker(interfaceName, intfDpnId, routerName);
        coordinator.enqueueJob(NAT_DS + "-" + interfaceName, natFlowRemoveWorker,
                NatConstants.NAT_DJC_MAX_RETRIES);
        // Validate whether the routerInterface is still part of given router in router-interface-map
        if (!NatUtil.validateIsIntefacePartofRouter(dataBroker, routerName, interfaceName)) {
            LOG.error("NAT Service : Router {} not valid for interface {} during remove. "
                + "Deleting it from router-interfaces DS", routerName, routerInterface);
            try {
                SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    NatUtil.getRouterInterfaceId(interfaceName));
            } catch (TransactionCommitFailedException e) {
                LOG.error("NAT Service : Failed to stale Router Interface entry for interface {}",
                    interfaceName, e);
            }
        }
    }

    public void handleUpdate(Interface original, Interface update,
                             Uint64 intfDpnId, RouterInterface routerInterface) {
        String routerName = routerInterface.getRouterName();
        if (NatUtil.validateIsIntefacePartofRouter(dataBroker, routerName, update.getName())) {
            NatInterfaceStateUpdateWorker natIfaceStateupdateWorker = new NatInterfaceStateUpdateWorker(
                original,
                update, intfDpnId, routerName);
            coordinator.enqueueJob(NAT_DS + "-" + update.getName(), natIfaceStateupdateWorker);
            NatFlowUpdateWorker natFlowUpdateWorker = new NatFlowUpdateWorker(original, update,
                routerName);
            coordinator.enqueueJob(NAT_DS + "-" + update.getName(), natFlowUpdateWorker,
                NatConstants.NAT_DJC_MAX_RETRIES);
        }
    }

    void handleRouterInterfacesUpEvent(String routerName, String interfaceName, Uint64 dpId,
            TypedReadWriteTransaction<Operational> operTx) throws ExecutionException, InterruptedException {
        LOG.debug("handleRouterInterfacesUpEvent : Handling UP event for router interface {} in Router {} on Dpn {}",
                interfaceName, routerName, dpId);
        NatUtil.addToNeutronRouterDpnsMap(routerName, interfaceName, dpId, operTx);
        NatUtil.addToDpnRoutersMap(routerName, interfaceName, dpId, operTx);
    }

    void handleRouterInterfacesDownEvent(String routerName, String interfaceName, Uint64 dpnId,
                                         TypedReadWriteTransaction<Operational> operTx)
        throws ExecutionException, InterruptedException {
        LOG.debug("handleRouterInterfacesDownEvent : Handling DOWN event for router Interface {} in Router {}",
                interfaceName, routerName);
        NatUtil.removeFromNeutronRouterDpnsMap(routerName, dpnId, operTx);
        NatUtil.removeFromDpnRoutersMap(dataBroker, routerName, interfaceName, dpnId, odlInterfaceRpcService,
                operTx);
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private IntfTransitionState getTransitionState(Interface.OperStatus original , Interface.OperStatus updated) {
        IntfTransitionState transitionState = stateTable.get(original, updated);

        if (transitionState == null) {
            return IntfTransitionState.STATE_IGNORE;
        }
        return transitionState;
    }

    private class NatInterfaceStateAddWorker implements Callable<List<ListenableFuture<Void>>> {
        private final String interfaceName;
        private final String routerName;
        private final Uint64 intfDpnId;

        NatInterfaceStateAddWorker(String interfaceName, Uint64 intfDpnId, String routerName) {
            this.interfaceName = interfaceName;
            this.routerName = routerName;
            this.intfDpnId = intfDpnId;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            LOG.trace("call : Received interface {} PORT UP OR ADD event ", interfaceName);
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            final ReentrantLock lock = NatUtil.lockForNat(intfDpnId);
            lock.lock();
            try {
                futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx ->
                    handleRouterInterfacesUpEvent(routerName, interfaceName, intfDpnId, tx)));
            } catch (Exception e) {
                LOG.error("call : Exception caught in Interface {} Operational State Up event",
                    interfaceName, e);
            } finally {
                lock.unlock();
            }
            return futures;
        }
    }

    private class NatInterfaceStateRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        private final String interfaceName;
        private final String routerName;
        private final Uint64 intfDpnId;

        NatInterfaceStateRemoveWorker(String interfaceName, Uint64 intfDpnId, String routerName) {
            this.interfaceName = interfaceName;
            this.routerName = routerName;
            this.intfDpnId = intfDpnId;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            LOG.trace("call : Received interface {} PORT DOWN or REMOVE event", interfaceName);
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            final ReentrantLock lock = NatUtil.lockForNat(intfDpnId);
            lock.lock();
            try {
                futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx ->
                    handleRouterInterfacesDownEvent(routerName, interfaceName, intfDpnId, tx)));
            } catch (Exception e) {
                LOG.error("call : Exception observed in handling deletion of VPN Interface {}.", interfaceName, e);
            } finally {
                lock.unlock();
            }
            return futures;
        }
    }

    private class NatInterfaceStateUpdateWorker implements Callable<List<ListenableFuture<Void>>> {
        private final Interface original;
        private final Interface update;
        private final Uint64 intfDpnId;
        private final String routerName;

        NatInterfaceStateUpdateWorker(Interface original, Interface update, Uint64 intfDpnId, String routerName) {
            this.original = original;
            this.update = update;
            this.intfDpnId = intfDpnId;
            this.routerName = routerName;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final String interfaceName = update.getName();
            LOG.trace("call : Received interface {} state change event", interfaceName);
            LOG.debug("call : DPN ID {} for the interface {} ", intfDpnId, interfaceName);

            List<ListenableFuture<Void>> futures = new ArrayList<>();
            final ReentrantLock lock = NatUtil.lockForNat(intfDpnId);
            lock.lock();
            try {
                IntfTransitionState state = getTransitionState(original.getOperStatus(), update.getOperStatus());
                if (state.equals(IntfTransitionState.STATE_IGNORE)) {
                    LOG.info("NAT Service: Interface {} state original {} updated {} not handled",
                        interfaceName, original.getOperStatus(), update.getOperStatus());
                    return futures;
                }
                futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
                    if (state.equals(IntfTransitionState.STATE_DOWN)) {
                        LOG.debug("call : DPN {} connnected to the interface {} has gone down."
                                + "Hence clearing the dpn-vpninterfaces-list entry from the"
                                + " neutron-router-dpns model in the ODL:L3VPN", intfDpnId, interfaceName);
                        // If the interface state is unknown, it means that the corresponding DPN has gone down.
                        // So remove the dpn-vpninterfaces-list from the neutron-router-dpns model.
                        NatUtil.removeFromNeutronRouterDpnsMap(routerName, interfaceName,
                            intfDpnId, tx);
                    } else if (state.equals(IntfTransitionState.STATE_UP)) {
                        LOG.debug("call : DPN {} connnected to the interface {} has come up. Hence adding"
                                + " the dpn-vpninterfaces-list entry from the neutron-router-dpns model"
                                + " in the ODL:L3VPN", intfDpnId, interfaceName);
                        handleRouterInterfacesUpEvent(routerName, interfaceName, intfDpnId, tx);
                    }
                }));
            } catch (Exception e) {
                LOG.error("call : Exception observed in handling updation of VPN Interface {}.", update.getName(), e);
            } finally {
                lock.unlock();
            }
            return futures;
        }
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void processInterfaceAdded(String portName, String routerId, Uint64 dpnId, VipState vipState) {
        LOG.trace("processInterfaceAdded : Processing Interface Add Event for interface {}", portName);
        List<InternalToExternalPortMap> intExtPortMapList = getIntExtPortMapListForPortName(portName, routerId);
        if (intExtPortMapList.isEmpty()) {
            LOG.debug("processInterfaceAdded : Ip Mapping list is empty/null for portname {}", portName);
            return;
        }
        InstanceIdentifier<RouterPorts> portIid = NatUtil.buildRouterPortsIdentifier(routerId);
        FluentFuture<Void> future = txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
                floatingIPListener.createNATFlowEntries(portName, intExtPortMap, portIid, routerId, dpnId, tx);
            }
        });
        future.transform((ignored) -> {
            if (vipState != null) {
                return this.vipStateTracker.writeVipState(vipState);
            }
            return null;
        }, MoreExecutors.directExecutor());
    }

    @NonNull
    private List<InternalToExternalPortMap> getIntExtPortMapListForPortName(String portName, String routerId) {
        InstanceIdentifier<Ports> portToIpMapIdentifier = NatUtil.buildPortToIpMapIdentifier(routerId, portName);
        Optional<Ports> port =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, portToIpMapIdentifier);
        if (!port.isPresent()) {
            LOG.info("getIntExtPortMapListForPortName : Unable to read router port entry for router ID {} "
                    + "and port name {}", routerId, portName);
            return Collections.emptyList();
        }
        return port.get().nonnullInternalToExternalPortMap();
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void processInterfaceRemoved(String portName, Uint64 dpnId, String routerId,
            List<ListenableFuture<Void>> futures) {
        LOG.trace("processInterfaceRemoved : Processing Interface Removed Event for interface {} on DPN ID {}",
                portName, dpnId);
        List<InternalToExternalPortMap> intExtPortMapList = getIntExtPortMapListForPortName(portName, routerId);
        if (intExtPortMapList.isEmpty()) {
            LOG.debug("processInterfaceRemoved : Ip Mapping list is empty/null for portName {}", portName);
            return;
        }
        InstanceIdentifier<RouterPorts> portIid = NatUtil.buildRouterPortsIdentifier(routerId);
        ListenableFuture<Void> future = txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
                LOG.trace("processInterfaceRemoved : Removing DNAT Flow entries for dpnId {} ", dpnId);
                floatingIPListener.removeNATFlowEntries(portName, intExtPortMap, portIid, routerId, dpnId, tx);
            }
        });
        futures.add(future);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error processing interface removal", e);
        }
    }

    private class NatFlowAddWorker implements Callable<List<ListenableFuture<Void>>> {
        private final String interfaceName;
        private final String routerName;
        private final Uint64 dpnId;
        private final VipState vipState;

        NatFlowAddWorker(String interfaceName,String routerName, Uint64 dpnId, VipState vipState) {
            this.interfaceName = interfaceName;
            this.routerName = routerName;
            this.dpnId = dpnId;
            this.vipState = vipState;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            LOG.trace("call : Interface {} up event received", interfaceName);
            try {
                LOG.trace("call : Port added event received for interface {} ", interfaceName);
                processInterfaceAdded(interfaceName, routerName, dpnId, vipState);
            } catch (Exception ex) {
                LOG.error("call : Exception caught in Interface {} Operational State Up event",
                        interfaceName, ex);
            }
            return futures;
        }
    }

    private class NatFlowUpdateWorker implements Callable<List<ListenableFuture<Void>>> {
        private final Interface original;
        private final Interface update;
        private final String routerName;

        NatFlowUpdateWorker(Interface original, Interface update, String routerName) {
            this.original = original;
            this.update = update;
            this.routerName = routerName;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            String interfaceName = update.getName();
            IntfTransitionState state = getTransitionState(original.getOperStatus(), update.getOperStatus());
            if (state.equals(IntfTransitionState.STATE_IGNORE)) {
                LOG.info("NAT Service: Interface {} state original {} updated {} not handled",
                        interfaceName, original.getOperStatus(), update.getOperStatus());
                return futures;
            }
            if (state.equals(IntfTransitionState.STATE_UP)) {
                LOG.debug("call : Port UP event received for interface {} ", interfaceName);
            } else if (state.equals(IntfTransitionState.STATE_DOWN)) {
                LOG.debug("call : Port DOWN event received for interface {} ", interfaceName);
                try {
                    if (NatUtil.isSnatEnabledForRouterId(dataBroker, routerName)) {
                        NatUtil.removeSnatEntriesForPort(dataBroker, naptManager, mdsalManager, neutronVpnService,
                            interfaceName, routerName);
                    }
                } catch (Exception ex) {
                    LOG.error("call : Exception caught in Interface {} OperationalStateDown", interfaceName, ex);
                }
            }
            return futures;
        }
    }

    private class NatFlowRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        private final String interfaceName;
        private final String routerName;
        private final Uint64 intfDpnId;

        NatFlowRemoveWorker(String interfaceName, Uint64 intfDpnId, String routerName) {
            this.interfaceName = interfaceName;
            this.routerName = routerName;
            this.intfDpnId = intfDpnId;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            LOG.trace("call : Interface {} removed event received", interfaceName);
            try {
                LOG.trace("call : Port removed event received for interface {} ", interfaceName);
                processInterfaceRemoved(interfaceName, intfDpnId, routerName, futures);
                if (NatUtil.isSnatEnabledForRouterId(dataBroker, routerName)) {
                    NatUtil.removeSnatEntriesForPort(dataBroker, naptManager, mdsalManager, neutronVpnService,
                        interfaceName, routerName);
                }
            } catch (Exception e) {
                LOG.error("call : Exception caught in Interface {} OperationalStateRemove", interfaceName, e);
            }
            return futures;
        }
    }
}

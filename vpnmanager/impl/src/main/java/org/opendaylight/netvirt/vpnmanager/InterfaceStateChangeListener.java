/*
 * Copyright (c) 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.metrics.Counter;
import org.opendaylight.infrautils.metrics.Labeled;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InterfaceStateChangeListener extends AbstractAsyncDataTreeChangeListener<Interface> {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateChangeListener.class);
    private static final short DJC_MAX_RETRIES = 3;
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnUtil vpnUtil;
    private final JobCoordinator jobCoordinator;
    private final IFibManager fibManager;
    private static final String FIB_EVENT_SOURCE_INTERFACE_STATE = "interfaceStateEvent";
    private final MetricProvider metricProvider;
    private final Labeled<Labeled<Labeled<Counter>>> intfCounter;
    private Counter counter;
    private final IVpnManager vpnManager;

    Table<OperStatus, OperStatus, IntfTransitionState> stateTable = HashBasedTable.create();

    enum IntfTransitionState {
        STATE_UP,
        STATE_DOWN,
        STATE_IGNORE
    }

    private void initialize() {
        //  Interface State Transition Table
        //               Up                Down            Unknown
        // ---------------------------------------------------------------
        /* Up       { STATE_IGNORE,   STATE_DOWN,     STATE_IGNORE }, */
        /* Down     { STATE_UP,       STATE_IGNORE,   STATE_IGNORE }, */
        /* Unknown  { STATE_UP,       STATE_DOWN,     STATE_IGNORE }, */

        stateTable.put(Interface.OperStatus.Up, Interface.OperStatus.Down, IntfTransitionState.STATE_DOWN);
        stateTable.put(Interface.OperStatus.Down, Interface.OperStatus.Up, IntfTransitionState.STATE_UP);
        stateTable.put(Interface.OperStatus.Unknown, Interface.OperStatus.Up, IntfTransitionState.STATE_UP);
        stateTable.put(Interface.OperStatus.Unknown, Interface.OperStatus.Down, IntfTransitionState.STATE_DOWN);
    }

    @Inject
    public InterfaceStateChangeListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager,
            final VpnUtil vpnUtil, final JobCoordinator jobCoordinator, final IFibManager fibManager,
                                        final IVpnManager vpnManager, MetricProvider metricProvider) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(InterfacesState.class)
                .child(Interface.class),
                Executors.newListeningSingleThreadExecutor("InterfaceStateChangeListener", LOG));

        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnUtil = vpnUtil;
        this.jobCoordinator = jobCoordinator;
        this.fibManager = fibManager;
        this.metricProvider = metricProvider;
        this.vpnManager = vpnManager;
        intfCounter =  metricProvider.newCounter(MetricDescriptor.builder().anchor(this)
                .project("netvirt").module("vpnmanager")
                .id("interfacestate").build(), "entitytype", "action",
                "name");
        initialize();
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }


    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                LOG.info("VPN Interface add event - intfName {} from InterfaceStateChangeListener",
                                intrf.getName());
                counter = intfCounter.label("interfaceState.add").label("interface")
                        .label(intrf.getName());
                counter.increment();
                jobCoordinator.enqueueJob("VPNINTERFACE-" + intrf.getName(), () -> {
                    List<ListenableFuture<?>> futures = new ArrayList<>(3);
                    List<String> ipAddressList = new ArrayList<String>();
                    futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, writeInvTxn -> {
                        //map of prefix and vpn name used, as entry in prefix-to-interface datastore
                        // is prerequisite for refresh Fib to avoid race condition leading to missing remote next hop
                        // in bucket actions on bgp-vpn delete
                        Map<String, Set<String>> mapOfRdAndPrefixesForRefreshFib = new HashMap<>();
                        ListenableFuture<?> configFuture
                            = txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, writeConfigTxn -> {
                                ListenableFuture<?> operFuture
                                    = txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, writeOperTxn -> {
                                        final String interfaceName = intrf.getName();
                                        LOG.info("Detected interface add event for interface {}", interfaceName);
                                        final VpnInterface vpnIf = vpnUtil.getConfiguredVpnInterface(interfaceName);
                                        if (vpnIf != null) {
                                            for (VpnInstanceNames vpnInterfaceVpnInstance :
                                                    vpnIf.nonnullVpnInstanceNames().values()) {
                                                String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                                String primaryRd = vpnUtil.getPrimaryRd(vpnName);
                                                counter = intfCounter.label("interfaceState.add.djc")
                                                        .label("rd.interface").label(primaryRd + "."
                                                                + vpnIf.getName());
                                                counter.increment();
                                                if (!vpnInterfaceManager.isVpnInstanceReady(vpnName)) {
                                                    LOG.info("VPN Interface add event - intfName {} onto vpnName {} "
                                                            + "running oper-driven, VpnInstance not ready, holding"
                                                            + " on", vpnIf.getName(), vpnName);
                                                } else if (vpnUtil.isVpnPendingDelete(primaryRd)) {
                                                    LOG.error("add: Ignoring addition of vpnInterface {}, as"
                                                            + " vpnInstance {} with primaryRd {} is already marked for"
                                                            + " deletion", interfaceName, vpnName, primaryRd);
                                                } else {
                                                    Uint64 intfDpnId = Uint64.ZERO;
                                                    try {
                                                        intfDpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                                                    } catch (Exception e) {
                                                        LOG.error("Unable to retrieve dpnId for interface {}. "
                                                                + "Process vpn interface add failed",intrf.getName(),
                                                                e);
                                                        return;
                                                    }
                                                    LOG.error("InterfaceStateChangeListener- Processing ifState"
                                                                    + " {} add event with dpnId {}",
                                                            intrf.getName(), intfDpnId);
                                                    final Uint64 dpnId = intfDpnId;
                                                    final int ifIndex = intrf.getIfIndex();
                                                    LOG.info("VPN Interface add event - intfName {} onto vpnName {}"
                                                            + " running oper-driven", vpnIf.getName(), vpnName);
                                                    Set<String> prefixes = new HashSet<>();
                                                    vpnInterfaceManager.processVpnInterfaceUp(dpnId, vpnIf, primaryRd,
                                                            ifIndex, false, writeConfigTxn, writeOperTxn, writeInvTxn,
                                                            intrf, vpnName, prefixes, FIB_EVENT_SOURCE_INTERFACE_STATE,
                                                            ipAddressList);
                                                    mapOfRdAndPrefixesForRefreshFib.put(primaryRd, prefixes);
                                                }
                                            }

                                        }
                                    });
                                futures.add(operFuture);
                                operFuture.get(); //Synchronous submit of operTxn
                            });
                        Futures.addCallback(configFuture,
                                new VpnInterfaceCallBackHandler(mapOfRdAndPrefixesForRefreshFib),
                                MoreExecutors.directExecutor());
                        futures.add(configFuture);
                        //TODO: Allow immediateFailedFuture from writeCfgTxn to cancel writeInvTxn as well.
                        Futures.addCallback(configFuture, new PostVpnInterfaceThreadWorker(intrf.getName(),
                                ipAddressList,true, "Operational"), MoreExecutors.directExecutor());
                    }));
                    return futures;
                });
            }
        } catch (Exception e) {
            LOG.error("Exception caught in Interface {} Operational State Up event", intrf.getName(), e);
        }
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        final String ifName = intrf.getName();
        Uint64 dpId = Uint64.ZERO;
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                LOG.info("VPN Interface remove event - intfName {} from InterfaceStateChangeListener",
                                intrf.getName());
                counter = intfCounter.label("interfaceState.remove").label("interface")
                        .label(intrf.getName());
                counter.increment();
                try {
                    dpId = InterfaceUtils.getDpIdFromInterface(intrf);
                } catch (Exception e) {
                    LOG.error("Unable to retrieve dpnId from interface operational data store for interface"
                            + " {}. Fetching from vpn interface op data store. ", ifName, e);
                }
                final Uint64 inputDpId = dpId;
                jobCoordinator.enqueueJob("VPNINTERFACE-" + ifName, () -> {
                    List<ListenableFuture<?>> futures = new ArrayList<>(3);
                    List<String> ipAddressList = new ArrayList<String>();
                    ListenableFuture<?> configFuture =
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                            writeConfigTxn -> futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                                writeOperTxn -> futures.add(
                                    txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, writeInvTxn -> {
                                        VpnInterface cfgVpnInterface =
                                            vpnUtil.getConfiguredVpnInterface(ifName);
                                        if (cfgVpnInterface == null) {
                                            LOG.debug("Interface {} is not a vpninterface, ignoring.", ifName);
                                            return;
                                        }
                                        for (VpnInstanceNames vpnInterfaceVpnInstance :
                                                cfgVpnInterface.nonnullVpnInstanceNames().values()) {
                                            String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                            Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                                                vpnUtil.getVpnInterfaceOpDataEntry(ifName, vpnName);
                                            if (!optVpnInterface.isPresent()) {
                                                LOG.debug("Interface {} vpn {} is not a vpninterface, or deletion"
                                                    + " triggered by northbound agent. ignoring.", ifName, vpnName);
                                                continue;
                                            }
                                            handleMipAdjRemoval(cfgVpnInterface, vpnName);
                                            final VpnInterfaceOpDataEntry vpnInterface = optVpnInterface.get();
                                            String rd = vpnManager.getVpnRd(dataBroker,
                                                    vpnInterface.getVpnInstanceName());
                                            counter = intfCounter.label("interfaceState.remove.djc")
                                                    .label("rd.interface")
                                                    .label(rd + "." + vpnInterface.getName());
                                            counter.increment();
                                            String gwMac = intrf.getPhysAddress() != null ? intrf.getPhysAddress()
                                                .getValue() : vpnInterface.getGatewayMacAddress();
                                            Uint64 dpnId = inputDpId;
                                            if (dpnId == null || dpnId.equals(Uint64.ZERO)) {
                                                dpnId = vpnInterface.getDpnId();
                                            }
                                            final int ifIndex = intrf.getIfIndex();
                                            LOG.info("VPN Interface remove event - intfName {} onto vpnName {}"
                                                + " running oper-driver", vpnInterface.getName(), vpnName);
                                            vpnInterfaceManager.processVpnInterfaceDown(dpnId, ifName, ifIndex, gwMac,
                                                vpnInterface, false, FIB_EVENT_SOURCE_INTERFACE_STATE,
                                                    ipAddressList, writeConfigTxn, writeOperTxn, writeInvTxn);
                                        }
                                    })))));
                    futures.add(configFuture);
                    Futures.addCallback(configFuture, new PostVpnInterfaceThreadWorker(intrf.getName(), ipAddressList,
                            false, "Operational"), MoreExecutors.directExecutor());
                    return futures;
                }, DJC_MAX_RETRIES);
            }
        } catch (Exception e) {
            LOG.error("Exception observed in handling deletion of VPN Interface {}. ", ifName, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void update(InstanceIdentifier<Interface> identifier,
                    Interface original, Interface update) {
        final String ifName = update.getName();
        try {
            if (update.getIfIndex() == null) {
                return;
            }
            if (L2vlan.class.equals(update.getType())) {
                LOG.info("VPN Interface update event - intfName {} from InterfaceStateChangeListener",
                        update.getName());
                counter = intfCounter.label("interfaceState.update").label("interface")
                        .label(update.getName());
                counter.increment();
                jobCoordinator.enqueueJob("VPNINTERFACE-" + ifName, () -> {
                    List<ListenableFuture<?>> futures = new ArrayList<>(3);
                    futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, writeOperTxn -> {
                        //map of prefix and vpn name used, as entry in prefix-to-interface datastore
                        // is prerequisite for refresh Fib to avoid race condition leading to missing remote
                        // next hop in bucket actions on bgp-vpn delete
                        Map<String, Set<String>> mapOfRdAndPrefixesForRefreshFib = new HashMap<>();
                        ListenableFuture<?> configTxFuture =
                                txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, writeConfigTxn ->
                                    futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                                        writeInvTxn -> {
                                            final VpnInterface vpnIf = vpnUtil.getConfiguredVpnInterface(ifName);
                                            if (vpnIf != null) {
                                                final int ifIndex = update.getIfIndex();
                                                Uint64 dpnId;
                                                try {
                                                    dpnId = InterfaceUtils.getDpIdFromInterface(update);
                                                } catch (Exception e) {
                                                    LOG.error("remove: Unable to retrieve dpnId for interface {}",
                                                        ifName, e);
                                                    return;
                                                }
                                                List<String> ipAddressList = new ArrayList<String>();
                                                IntfTransitionState state = getTransitionState(
                                                        original.getOperStatus(), update.getOperStatus());
                                                if (state.equals(IntfTransitionState.STATE_IGNORE)) {
                                                    LOG.info("InterfaceStateChangeListener: Interface {} state "
                                                         + "original {}" + "updated {} not handled", ifName,
                                                         original.getOperStatus(), update.getOperStatus());
                                                    return;
                                                }
                                                LOG.error("InterfaceStateChangeListener- Processing ifState {} "
                                                                + "update event "
                                                                + "with dpnId {} operstate {}",
                                                        ifName, dpnId, update.getOperStatus());
                                                if (state.equals(IntfTransitionState.STATE_UP)
                                                        && vpnIf.getVpnInstanceNames() != null) {
                                                    for (VpnInstanceNames vpnInterfaceVpnInstance :
                                                            vpnIf.getVpnInstanceNames().values()) {
                                                        String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                                        String primaryRd = vpnUtil.getPrimaryRd(vpnName);
                                                        counter = intfCounter
                                                                .label("interfaceState.update.state.up")
                                                                .label("rd.interface")
                                                                .label(primaryRd + "." + vpnIf.getName());
                                                        counter.increment();
                                                        Set<String> prefixes = new HashSet<>();
                                                        if (!vpnInterfaceManager.isVpnInstanceReady(vpnName)) {
                                                            LOG.error("VPN Interface update event - intfName {} "
                                                                + "onto vpnName {} running oper-driven UP, "
                                                                + "VpnInstance not ready, holding on",
                                                                vpnIf.getName(), vpnName);
                                                        } else if (vpnUtil.isVpnPendingDelete(primaryRd)) {
                                                            LOG.error("update: Ignoring UP event for vpnInterface "
                                                                + "{}, as vpnInstance {} with primaryRd {} is "
                                                                + "already marked for deletion ",
                                                                vpnIf.getName(), vpnName, primaryRd);
                                                            counter = intfCounter
                                                                    .label("interfaceState.update.state.up")
                                                                    .label("ignore.vpn."
                                                                            + "marked.del.rd.interface")
                                                                    .label(primaryRd + "." + vpnIf.getName());
                                                            counter.increment();
                                                        } else {
                                                            vpnInterfaceManager.processVpnInterfaceUp(dpnId, vpnIf,
                                                                primaryRd, ifIndex, true, writeConfigTxn,
                                                                writeOperTxn, writeInvTxn, update, vpnName, prefixes,
                                                                    FIB_EVENT_SOURCE_INTERFACE_STATE, ipAddressList);
                                                            mapOfRdAndPrefixesForRefreshFib.put(primaryRd, prefixes);
                                                        }
                                                    }
                                                } else if (state.equals(IntfTransitionState.STATE_DOWN)
                                                        && vpnIf.getVpnInstanceNames() != null) {
                                                    for (VpnInstanceNames vpnInterfaceVpnInstance :
                                                            vpnIf.getVpnInstanceNames().values()) {
                                                        String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                                        String primaryRd = vpnUtil.getPrimaryRd(vpnName);
                                                        counter = intfCounter
                                                                .label("interfaceState.update.state.down")
                                                                .label("rd.interface")
                                                                .label(primaryRd + "." + vpnIf.getName());
                                                        counter.increment();
                                                        LOG.info("VPN Interface update event - intfName {} "
                                                            + " onto vpnName {} running oper-driven DOWN",
                                                            vpnIf.getName(), vpnName);
                                                        Optional<VpnInterfaceOpDataEntry> optVpnInterface = vpnUtil
                                                            .getVpnInterfaceOpDataEntry(vpnIf.getName(), vpnName);
                                                        if (optVpnInterface.isPresent()) {
                                                            VpnInterfaceOpDataEntry vpnOpInterface =
                                                                optVpnInterface.get();
                                                            vpnInterfaceManager.processVpnInterfaceDown(dpnId,
                                                                vpnIf.getName(), ifIndex, update.getPhysAddress()
                                                                .getValue(), vpnOpInterface, true,
                                                                    FIB_EVENT_SOURCE_INTERFACE_STATE, ipAddressList,
                                                                writeConfigTxn, writeOperTxn, writeInvTxn);
                                                        } else {
                                                            LOG.error("InterfaceStateChangeListener Update DOWN - "
                                                                + " vpnInterface {}not available, ignoring event",
                                                                vpnIf.getName());
                                                            continue;
                                                        }
                                                    }
                                                }
                                            } else {
                                                LOG.debug("Interface {} is not a vpninterface, ignoring.", ifName);
                                            }
                                        })));
                        Futures.addCallback(configTxFuture,
                            new VpnInterfaceCallBackHandler(mapOfRdAndPrefixesForRefreshFib),
                            MoreExecutors.directExecutor());
                        futures.add(configTxFuture);
                    }));
                    return futures;
                });
            }
        } catch (Exception e) {
            LOG.error("Exception observed in handling updation of VPN Interface {}. ", update.getName(), e);
        }
    }

    private void handleMipAdjRemoval(VpnInterface cfgVpnInterface, String vpnName) {
        String interfaceName = cfgVpnInterface.getName();
        Adjacencies adjacencies = cfgVpnInterface.augmentation(Adjacencies.class);
        if (adjacencies != null) {
            Map<AdjacencyKey, Adjacency> adjacencyMap = adjacencies.nonnullAdjacency();
            if (!adjacencyMap.isEmpty()) {
                for (Adjacency adj : adjacencyMap.values()) {
                    if (adj.getAdjacencyType() != Adjacency.AdjacencyType.PrimaryAdjacency) {
                        String ipAddress = adj.getIpAddress();
                        String prefix = ipAddress.split("/")[0];
                        LearntVpnVipToPort vpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, prefix);
                        if (vpnVipToPort != null && vpnVipToPort.getPortName().equals(interfaceName)) {
                            vpnUtil.removeMipAdjacency(vpnName, interfaceName, ipAddress, null);
                        } else {
                            LOG.debug("IP {} could be extra-route or learnt-ip on different interface"
                                    + "than oper-vpn-interface {}", ipAddress, interfaceName);
                        }
                    }
                }
            }
        }
    }

    private class PostVpnInterfaceThreadWorker implements FutureCallback<Object> {
        private final String interfaceName;
        private final boolean add;
        private final String txnDestination;
        List<String> ipPrefixList;

        PostVpnInterfaceThreadWorker(String interfaceName, List<String> ipAddrList,
                                     boolean add, String transactionDest) {
            this.interfaceName = interfaceName;
            this.add = add;
            this.txnDestination = transactionDest;
            this.ipPrefixList = ipAddrList;
        }

        @Override
        public void onSuccess(Object voidObj) {
            if (add) {
                LOG.debug("InterfaceStateChangeListener: VrfEntries for {} ipPrefixList {} stored into destination"
                        + " {} successfully", interfaceName, ipPrefixList, txnDestination);
            } else {
                LOG.debug("InterfaceStateChangeListener:  VrfEntries for {} ipPrefixList {} removed successfully",
                        ipPrefixList, interfaceName);
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            if (add) {
                LOG.error("InterfaceStateChangeListener: VrfEntries for {} ipPrefixList {} failed to store into"
                        + "destination {} with exception: ", interfaceName, ipPrefixList, txnDestination, throwable);
            } else {
                LOG.error("InterfaceStateChangeListener: VrfEntries for {} ipPrefixList {} removal failed with "
                        + "exception: ", interfaceName, ipPrefixList, throwable);
                vpnUtil.unsetScheduledToRemoveForVpnInterface(interfaceName);
            }
        }
    }

    private IntfTransitionState getTransitionState(Interface.OperStatus original , Interface.OperStatus updated) {
        IntfTransitionState transitionState = stateTable.get(original, updated);

        if (transitionState == null) {
            return IntfTransitionState.STATE_IGNORE;
        }
        return transitionState;
    }

    private class VpnInterfaceCallBackHandler implements FutureCallback<Object> {
        private final Map<String, Set<String>> mapOfRdAndPrefixesForRefreshFib;

        VpnInterfaceCallBackHandler(Map<String, Set<String>> mapOfRdAndPrefixesForRefreshFib) {
            this.mapOfRdAndPrefixesForRefreshFib = mapOfRdAndPrefixesForRefreshFib;
        }

        @Override
        public void onSuccess(Object voidObj) {
            mapOfRdAndPrefixesForRefreshFib.forEach((primaryRd, prefixes) -> {
                prefixes.forEach(prefix -> {
                    fibManager.refreshVrfEntry(primaryRd, prefix);
                });
            });
        }

        @Override
        public void onFailure(Throwable throwable) {
            LOG.debug("write Tx config operation failedTunnelEndPointChangeListener", throwable);
        }
    }
}

/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanLearntVpnVipToPortListener extends
        AsyncDataTreeChangeListenerBase<VpnPortipToPort, ElanLearntVpnVipToPortListener> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanLearntVpnVipToPortListener.class);
    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IInterfaceManager interfaceManager;
    private final ElanUtils elanUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanInterfaceCache elanInterfaceCache;

    @Inject
    public ElanLearntVpnVipToPortListener(DataBroker broker, IInterfaceManager interfaceManager, ElanUtils elanUtils,
            JobCoordinator jobCoordinator, ElanInstanceCache elanInstanceCache, ElanInterfaceCache elanInterfaceCache) {
        super(VpnPortipToPort.class, ElanLearntVpnVipToPortListener.class);
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.interfaceManager = interfaceManager;
        this.elanUtils = elanUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
        this.elanInterfaceCache = elanInterfaceCache;
    }

    @Override
    @PostConstruct
    public void init() {
        /* ELAN will learn the MAC by itself using ElanPacketInHandler class.
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
         */
    }

    @Override
    protected InstanceIdentifier<VpnPortipToPort> getWildCardPath() {
        return InstanceIdentifier.create(NeutronVpnPortipPortData.class).child(VpnPortipToPort.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort dataObjectModification) {
        if (!dataObjectModification.isLearntIp()) {
            return;
        }
        String macAddress = dataObjectModification.getMacAddress();
        String interfaceName = dataObjectModification.getPortName();
        LOG.trace("Removing mac address {} from interface {} ", macAddress, interfaceName);
        jobCoordinator.enqueueJob(buildJobKey(macAddress, interfaceName),
                new StaticMacRemoveWorker(macAddress, interfaceName));
    }

    @Override
    protected void update(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort dataObjectModificationBefore,
                          VpnPortipToPort dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort dataObjectModification) {
        if (!dataObjectModification.isLearntIp()) {
            return;
        }
        String macAddress = dataObjectModification.getMacAddress();
        String interfaceName = dataObjectModification.getPortName();
        LOG.trace("Adding mac address {} to interface {} ", macAddress, interfaceName);
        jobCoordinator.enqueueJob(buildJobKey(macAddress, interfaceName),
                new StaticMacAddWorker(macAddress, interfaceName));
    }

    @Override
    protected ElanLearntVpnVipToPortListener getDataTreeChangeListener() {
        return this;
    }

    private class StaticMacAddWorker implements Callable<List<ListenableFuture<Void>>> {
        String macAddress;
        String interfaceName;

        StaticMacAddWorker(String macAddress, String interfaceName) {
            this.macAddress = macAddress;
            this.interfaceName = interfaceName;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            Optional<ElanInterface> elanInterface = elanInterfaceCache.get(interfaceName);
            if (!elanInterface.isPresent()) {
                LOG.debug("ElanInterface Not present for interfaceName {} for add event", interfaceName);
                return Collections.emptyList();
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, interfaceTx ->
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, flowTx ->
                    addMacEntryToDsAndSetupFlows(elanInterface.get().getElanInstanceName(), interfaceTx,
                            flowTx, ElanConstants.STATIC_MAC_TIMEOUT)))));
            return futures;
        }

        private void addMacEntryToDsAndSetupFlows(String elanName, TypedWriteTransaction<Operational> interfaceTx,
                TypedWriteTransaction<Configuration> flowTx, int macTimeOut) {
            LOG.trace("Adding mac address {} and interface name {} to ElanInterfaceForwardingEntries and "
                + "ElanForwardingTables DS", macAddress, interfaceName);
            BigInteger timeStamp = new BigInteger(String.valueOf(System.currentTimeMillis()));
            PhysAddress physAddress = new PhysAddress(macAddress);
            MacEntry macEntry = new MacEntryBuilder().setInterface(interfaceName).setMacAddress(physAddress)
                    .withKey(new MacEntryKey(physAddress)).setControllerLearnedForwardingEntryTimestamp(timeStamp)
                    .setIsStaticAddress(false).build();
            InstanceIdentifier<MacEntry> macEntryId = ElanUtils
                    .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
            interfaceTx.put(macEntryId, macEntry);
            InstanceIdentifier<MacEntry> elanMacEntryId =
                    ElanUtils.getMacEntryOperationalDataPath(elanName, physAddress);
            interfaceTx.put(elanMacEntryId, macEntry);
            ElanInstance elanInstance = elanInstanceCache.get(elanName).orNull();
            elanUtils.setupMacFlows(elanInstance, interfaceManager.getInterfaceInfo(interfaceName), macTimeOut,
                    macAddress, true, flowTx);
        }
    }

    private class StaticMacRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        String macAddress;
        String interfaceName;

        StaticMacRemoveWorker(String macAddress, String interfaceName) {
            this.macAddress = macAddress;
            this.interfaceName = interfaceName;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            Optional<ElanInterface> elanInterface = elanInterfaceCache.get(interfaceName);
            if (!elanInterface.isPresent()) {
                LOG.debug("ElanInterface Not present for interfaceName {} for delete event", interfaceName);
                return Collections.emptyList();
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, interfaceTx ->
                futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, flowTx ->
                    deleteMacEntryFromDsAndRemoveFlows(elanInterface.get().getElanInstanceName(),
                            interfaceTx, flowTx)))));
            return futures;
        }

        private void deleteMacEntryFromDsAndRemoveFlows(String elanName,
                TypedWriteTransaction<Operational> interfaceTx, TypedReadWriteTransaction<Configuration> flowTx)
                throws ExecutionException, InterruptedException {
            LOG.trace("Deleting mac address {} and interface name {} from ElanInterfaceForwardingEntries "
                    + "and ElanForwardingTables DS", macAddress, interfaceName);
            PhysAddress physAddress = new PhysAddress(macAddress);
            MacEntry macEntry = elanUtils.getInterfaceMacEntriesOperationalDataPath(interfaceName, physAddress);
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            if (macEntry != null && interfaceInfo != null) {
                elanUtils.deleteMacFlows(elanInstanceCache.get(elanName).orNull(), interfaceInfo, macEntry, flowTx);
                interfaceTx.delete(
                        ElanUtils.getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress));
                interfaceTx.delete(
                        ElanUtils.getMacEntryOperationalDataPath(elanName, physAddress));
            }
        }
    }

    private static String buildJobKey(String mac, String interfaceName) {
        return "ENTERPRISEMACJOB" + mac + interfaceName;
    }
}

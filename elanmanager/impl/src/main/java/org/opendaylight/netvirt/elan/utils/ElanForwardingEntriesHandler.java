/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import static org.opendaylight.mdsal.binding.api.WriteTransaction.CREATE_MISSING_PARENTS;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanForwardingEntriesHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ElanForwardingEntriesHandler.class);

    private final ManagedNewTransactionRunner txRunner;
    private final ElanUtils elanUtils;

    @Inject
    public ElanForwardingEntriesHandler(DataBroker dataBroker, ElanUtils elanUtils) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.elanUtils = elanUtils;
    }

    public void updateElanInterfaceForwardingTablesList(String elanInstanceName, String interfaceName,
            String existingInterfaceName, MacEntry mac, TypedReadWriteTransaction<Operational> tx)
            throws ExecutionException, InterruptedException {
        if (existingInterfaceName.equals(interfaceName)) {
            LOG.error("Static MAC address {} has already been added for the same ElanInstance "
                            + "{} on the same Logical Interface Port {}."
                            + " No operation will be done.",
                    mac.getMacAddress().toString(), elanInstanceName, interfaceName);
        } else {
            LOG.warn("Static MAC address {} had already been added for ElanInstance {} on Logical Interface Port {}. "
                            + "This would be considered as MAC movement scenario and old static mac will be removed "
                            + "and new static MAC will be added"
                            + "for ElanInstance {} on Logical Interface Port {}",
                    mac.getMacAddress().toString(), elanInstanceName, interfaceName, elanInstanceName, interfaceName);
            //Update the  ElanInterface Forwarding Container & ElanForwarding Container
            deleteElanInterfaceForwardingTablesList(existingInterfaceName, mac, tx);
            createElanInterfaceForwardingTablesList(interfaceName, mac, tx);
            updateElanForwardingTablesList(elanInstanceName, interfaceName, mac, tx);
        }

    }

    public void addElanInterfaceForwardingTableList(String elanInstanceName, String interfaceName,
            StaticMacEntries staticMacEntries, TypedReadWriteTransaction<Operational> tx)
            throws ExecutionException, InterruptedException {
        MacEntry macEntry = new MacEntryBuilder().setIsStaticAddress(true)
                .setMacAddress(staticMacEntries.getMacAddress())
                .setIpPrefix(staticMacEntries.getIpPrefix())
                .setInterface(interfaceName).withKey(new MacEntryKey(staticMacEntries.getMacAddress())).build();

        createElanForwardingTablesList(elanInstanceName, macEntry, tx);
        createElanInterfaceForwardingTablesList(interfaceName, macEntry, tx);
    }

    public void deleteElanInterfaceForwardingTablesList(String interfaceName, MacEntry mac,
                                                        TypedReadWriteTransaction<Operational> interfaceTx)
            throws ExecutionException, InterruptedException {
        InstanceIdentifier<MacEntry> existingMacEntryId = ElanUtils
                .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, mac.getMacAddress());
        MacEntry existingInterfaceMacEntry = elanUtils
                .getInterfaceMacEntriesOperationalDataPathFromId(interfaceTx, existingMacEntryId);
        if (existingInterfaceMacEntry != null) {
            interfaceTx.delete(existingMacEntryId);
        }
    }

    public void createElanInterfaceForwardingTablesList(String interfaceName, MacEntry mac,
            TypedReadWriteTransaction<Operational> tx) throws ExecutionException, InterruptedException {
        InstanceIdentifier<MacEntry> existingMacEntryId = ElanUtils
                .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, mac.getMacAddress());
        MacEntry existingInterfaceMacEntry = elanUtils
                .getInterfaceMacEntriesOperationalDataPathFromId(tx, existingMacEntryId);
        if (existingInterfaceMacEntry == null) {
            MacEntry macEntry = new MacEntryBuilder().setMacAddress(mac.getMacAddress()).setIpPrefix(mac.getIpPrefix())
                    .setInterface(interfaceName)
                    .setIsStaticAddress(true).withKey(new MacEntryKey(mac.getMacAddress())).build();
            tx.put(existingMacEntryId, macEntry, CREATE_MISSING_PARENTS);
        }
    }

    public void updateElanForwardingTablesList(String elanName, String interfaceName, MacEntry mac,
            TypedReadWriteTransaction<Operational> tx) throws ExecutionException, InterruptedException {
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils.getMacEntryOperationalDataPath(elanName,
                mac.getMacAddress());
        MacEntry existingMacEntry = elanUtils.getMacEntryFromElanMacId(tx, macEntryId);
        if (existingMacEntry != null && elanUtils.getElanMacTable(elanName) != null) {
            MacEntry newMacEntry = new MacEntryBuilder().setInterface(interfaceName).setIsStaticAddress(true)
                    .setMacAddress(mac.getMacAddress()).setIpPrefix(mac.getIpPrefix())
                    .withKey(new MacEntryKey(mac.getMacAddress())).build();
            tx.put(macEntryId, newMacEntry);
        }
    }

    private void createElanForwardingTablesList(String elanName, MacEntry macEntry,
            TypedReadWriteTransaction<Operational> tx) throws ExecutionException, InterruptedException {
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils.getMacEntryOperationalDataPath(elanName,
                macEntry.getMacAddress());
        Optional<MacEntry> existingMacEntry = tx.read(macEntryId).get();
        if (!existingMacEntry.isPresent() && elanUtils.getElanMacTable(elanName) != null) {
            tx.put(macEntryId, macEntry, CREATE_MISSING_PARENTS);
        }
    }

    public void deleteElanInterfaceForwardingEntries(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            MacEntry macEntry) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, interfaceTx -> {
            InstanceIdentifier<MacEntry> macEntryId = ElanUtils
                    .getMacEntryOperationalDataPath(elanInfo.getElanInstanceName(), macEntry.getMacAddress());
            interfaceTx.delete(macEntryId);
            deleteElanInterfaceForwardingTablesList(interfaceInfo.getInterfaceName(), macEntry, interfaceTx);
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                flowTx -> elanUtils.deleteMacFlows(elanInfo, interfaceInfo, macEntry, flowTx)));
        }));
        for (ListenableFuture<Void> future : futures) {
            LoggingFutures.addErrorLogging(future, LOG, "Error deleting ELAN interface forwarding entries");
        }
    }
}

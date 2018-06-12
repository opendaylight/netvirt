/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
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

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final ElanUtils elanUtils;

    @Inject
    public ElanForwardingEntriesHandler(DataBroker dataBroker, ElanUtils elanUtils) {
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.elanUtils = elanUtils;
    }

    public void updateElanInterfaceForwardingTablesList(String elanInstanceName, String interfaceName,
            String existingInterfaceName, MacEntry mac, WriteTransaction operTx) {
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
            deleteElanInterfaceForwardingTablesList(existingInterfaceName, mac, operTx);
            createElanInterfaceForwardingTablesList(interfaceName, mac, operTx);
            updateElanForwardingTablesList(elanInstanceName, interfaceName, mac, operTx);
        }

    }

    public void addElanInterfaceForwardingTableList(String elanInstanceName, String interfaceName,
                                                    StaticMacEntries staticMacEntries, WriteTransaction operTx) {
        MacEntry macEntry = new MacEntryBuilder().setIsStaticAddress(true)
                .setMacAddress(staticMacEntries.getMacAddress())
                .setIpPrefix(staticMacEntries.getIpPrefix())
                .setInterface(interfaceName).withKey(new MacEntryKey(staticMacEntries.getMacAddress())).build();

        createElanForwardingTablesList(elanInstanceName, macEntry, operTx);
        createElanInterfaceForwardingTablesList(interfaceName, macEntry, operTx);
    }

    public void deleteElanInterfaceForwardingTablesList(String interfaceName, MacEntry mac, WriteTransaction operTx) {
        InstanceIdentifier<MacEntry> existingMacEntryId = ElanUtils
                .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, mac.getMacAddress());
        MacEntry existingInterfaceMacEntry = elanUtils
                .getInterfaceMacEntriesOperationalDataPathFromId(existingMacEntryId);
        if (existingInterfaceMacEntry != null) {
            operTx.delete(LogicalDatastoreType.OPERATIONAL, existingMacEntryId);
        }
    }

    public void createElanInterfaceForwardingTablesList(String interfaceName, MacEntry mac, WriteTransaction operTx) {
        InstanceIdentifier<MacEntry> existingMacEntryId = ElanUtils
                .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, mac.getMacAddress());
        MacEntry existingInterfaceMacEntry = elanUtils
                .getInterfaceMacEntriesOperationalDataPathFromId(existingMacEntryId);
        if (existingInterfaceMacEntry == null) {
            MacEntry macEntry = new MacEntryBuilder().setMacAddress(mac.getMacAddress()).setIpPrefix(mac.getIpPrefix())
                    .setInterface(interfaceName)
                    .setIsStaticAddress(true).withKey(new MacEntryKey(mac.getMacAddress())).build();
            operTx.put(LogicalDatastoreType.OPERATIONAL, existingMacEntryId, macEntry,
                    WriteTransaction.CREATE_MISSING_PARENTS);
        }
    }

    public void updateElanForwardingTablesList(String elanName, String interfaceName, MacEntry mac,
            WriteTransaction operTx) {
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils.getMacEntryOperationalDataPath(elanName,
                mac.getMacAddress());
        MacEntry existingMacEntry = elanUtils.getMacEntryFromElanMacId(macEntryId);
        if (existingMacEntry != null && elanUtils.getElanMacTable(elanName) != null) {
            MacEntry newMacEntry = new MacEntryBuilder().setInterface(interfaceName).setIsStaticAddress(true)
                    .setMacAddress(mac.getMacAddress()).setIpPrefix(mac.getIpPrefix())
                    .withKey(new MacEntryKey(mac.getMacAddress())).build();
            operTx.put(LogicalDatastoreType.OPERATIONAL, macEntryId, newMacEntry);
        }
    }

    private void createElanForwardingTablesList(String elanName, MacEntry macEntry, WriteTransaction operTx) {
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils.getMacEntryOperationalDataPath(elanName,
                macEntry.getMacAddress());
        Optional<MacEntry> existingMacEntry = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, macEntryId);
        if (!existingMacEntry.isPresent() && elanUtils.getElanMacTable(elanName) != null) {
            operTx.put(LogicalDatastoreType.OPERATIONAL, macEntryId, macEntry, WriteTransaction.CREATE_MISSING_PARENTS);
        }
    }

    public void deleteElanInterfaceForwardingEntries(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            MacEntry macEntry) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(interfaceTx -> {
            InstanceIdentifier<MacEntry> macEntryId = ElanUtils
                    .getMacEntryOperationalDataPath(elanInfo.getElanInstanceName(), macEntry.getMacAddress());
            interfaceTx.delete(LogicalDatastoreType.OPERATIONAL, macEntryId);
            deleteElanInterfaceForwardingTablesList(interfaceInfo.getInterfaceName(), macEntry, interfaceTx);
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(flowTx -> {
                elanUtils.deleteMacFlows(elanInfo, interfaceInfo, macEntry, flowTx);
            }));
        }));
        for (ListenableFuture<Void> future : futures) {
            ListenableFutures.addErrorLogging(future, LOG, "Error deleting ELAN interface forwarding entries");
        }
    }

    public void deleteElanInterfaceMacForwardingEntries(String interfaceName, PhysAddress physAddress,
            WriteTransaction tx) {
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils
                .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
        tx.delete(LogicalDatastoreType.OPERATIONAL, macEntryId);
    }
}

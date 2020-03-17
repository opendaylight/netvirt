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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowAdded;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowRemoved;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowUpdated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.NodeErrorNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.NodeExperimenterErrorNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SwitchFlowRemoved;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanSmacFlowEventListener implements SalFlowListener {

    private static final Logger LOG = LoggerFactory.getLogger(ElanSmacFlowEventListener.class);

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IInterfaceManager interfaceManager;
    private final ElanUtils elanUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public ElanSmacFlowEventListener(DataBroker broker, IInterfaceManager interfaceManager, ElanUtils elanUtils,
            JobCoordinator jobCoordinator, ElanInstanceCache elanInstanceCache) {
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.interfaceManager = interfaceManager;
        this.elanUtils = elanUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
    }

    @Override
    public void onFlowAdded(FlowAdded arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onFlowRemoved(FlowRemoved flowRemoved) {
        short tableId = flowRemoved.getTableId().toJava();
        if (tableId == NwConstants.ELAN_SMAC_TABLE) {
            Uint64 metadata = flowRemoved.getMatch().getMetadata().getMetadata();
            Uint32 elanTag = Uint32.valueOf(MetaDataUtil.getElanTagFromMetadata(metadata));
            ElanTagName elanTagInfo = elanUtils.getElanInfoByElanTag(elanTag);
            if (elanTagInfo == null) {
                return;
            }
            final String srcMacAddress = flowRemoved.getMatch().getEthernetMatch()
                    .getEthernetSource().getAddress().getValue().toUpperCase(Locale.getDefault());
            Uint64 portTag = MetaDataUtil.getLportFromMetadata(metadata);
            if (portTag.intValue() == 0) {
                LOG.debug("Flow removed event on SMAC flow entry. But having port Tag as 0 ");
                return;
            }
            Optional<IfIndexInterface> existingInterfaceInfo = elanUtils
                    .getInterfaceInfoByInterfaceTag(Uint32.valueOf(portTag.longValue()));
            if (!existingInterfaceInfo.isPresent()) {
                LOG.debug("Interface is not available for port Tag {}", portTag);
                return;
            }
            String interfaceName = existingInterfaceInfo.get().getInterfaceName();
            PhysAddress physAddress = new PhysAddress(srcMacAddress);
            if (interfaceName == null) {
                LOG.error("LPort record not found for tag {}", portTag);
                return;
            }
            jobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(interfaceName), () -> {
                List<ListenableFuture<Void>> elanFutures = new ArrayList<>();
                MacEntry macEntry = elanUtils.getInterfaceMacEntriesOperationalDataPath(interfaceName, physAddress);
                InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
                String elanInstanceName = elanTagInfo.getName();
                LOG.info("Deleting the Mac-Entry:{} present on ElanInstance:{}", macEntry, elanInstanceName);
                ListenableFuture<Void> result = txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                    if (macEntry != null && interfaceInfo != null) {
                        deleteSmacAndDmacFlows(elanInstanceCache.get(elanInstanceName).orElse(null),
                                interfaceInfo, srcMacAddress, tx);
                    } else if (macEntry == null) { //Remove flow of src flow entry only for MAC movement
                        MacEntry macEntryOfElanForwarding = elanUtils.getMacEntryForElanInstance(elanTagInfo.getName(),
                                physAddress).orElse(null);
                        if (macEntryOfElanForwarding != null) {
                            String macAddress = macEntryOfElanForwarding.getMacAddress().getValue();
                            elanUtils.deleteSmacFlowOnly(elanInstanceCache.get(elanInstanceName).orElse(null),
                                    interfaceInfo, macAddress, tx);
                        } else {
                            deleteSmacAndDmacFlows(elanInstanceCache.get(elanInstanceName).orElse(null), interfaceInfo,
                                    srcMacAddress, tx);
                        }
                    }
                });
                elanFutures.add(result);
                addCallBack(result, srcMacAddress);
                InstanceIdentifier<MacEntry> macEntryIdForElanInterface = ElanUtils
                        .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
                Optional<MacEntry> existingInterfaceMacEntry = ElanUtils.read(broker,
                        LogicalDatastoreType.OPERATIONAL, macEntryIdForElanInterface);
                if (existingInterfaceMacEntry.isPresent()) {
                    ListenableFuture<Void> future = txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                        tx -> {
                            tx.delete(macEntryIdForElanInterface);
                            MacEntry macEntryInElanInstance = elanUtils.getMacEntryForElanInstance(elanInstanceName,
                                physAddress).orElse(null);
                            if (macEntryInElanInstance != null
                                && macEntryInElanInstance.getInterface().equals(interfaceName)) {
                                InstanceIdentifier<MacEntry> macEntryIdForElanInstance = ElanUtils
                                    .getMacEntryOperationalDataPath(elanInstanceName, physAddress);
                                tx.delete(macEntryIdForElanInstance);
                            }
                        });
                    elanFutures.add(future);
                    addCallBack(future, srcMacAddress);
                }
                return elanFutures;
            }, ElanConstants.JOB_MAX_RETRIES);
        }
    }

    private static void addCallBack(ListenableFuture<Void> writeResult, String srcMacAddress) {
        //WRITE Callback
        Futures.addCallback(writeResult, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void noarg) {
                LOG.debug("Successfully removed macEntry {} from Operational Datastore", srcMacAddress);
            }

            @Override
            public void onFailure(Throwable error) {
                LOG.debug("Error {} while removing macEntry {} from Operational Datastore", error, srcMacAddress);
            }
        }, MoreExecutors.directExecutor());
    }

    private void deleteSmacAndDmacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
                                        TypedReadWriteTransaction<Datastore.Configuration> deleteFlowTx)
            throws ExecutionException, InterruptedException {
        if (elanInfo == null || interfaceInfo == null) {
            return;
        }
        try (NamedSimpleReentrantLock.Acquired lock = elanUtils.lockElanMacDPN(elanInfo.getElanTag().toJava(),
                macAddress, interfaceInfo.getDpId())) {
            elanUtils.deleteMacFlows(elanInfo, interfaceInfo, macAddress, /* alsoDeleteSMAC */ true, deleteFlowTx);
        }
    }

    @Override
    public void onFlowUpdated(FlowUpdated arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onNodeErrorNotification(NodeErrorNotification arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onNodeExperimenterErrorNotification(NodeExperimenterErrorNotification arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSwitchFlowRemoved(SwitchFlowRemoved switchFlowRemoved) {

    }


}

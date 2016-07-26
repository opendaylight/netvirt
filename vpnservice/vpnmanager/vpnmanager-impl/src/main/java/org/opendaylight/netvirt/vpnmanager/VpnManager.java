/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import com.google.common.util.concurrent.*;

import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op
        .data.entry.VpnToDpnList;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class VpnManager extends AbstractDataChangeListener<VpnInstance> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration, fibListenerRegistration;
    private ConcurrentMap<String, Runnable> vpnOpMap = new ConcurrentHashMap<String, Runnable>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private IdManagerService idManager;
    private VpnInterfaceManager vpnInterfaceManager;
    private final FibEntriesListener fibListener;
    private NotificationService notificationService;

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    LOG.debug("Success in Datastore operation");
                }

                @Override
                public void onFailure(Throwable error) {
                    LOG.error("Error in Datastore operation", error);
                };
            };

    /**
     * Listens for data change related to VPN Instance
     * Informs the BGP about VRF information
     *
     * @param db dataBroker reference
     * @param bgpManager Used to advertise routes to the BGP Router
     */
    public VpnManager(final DataBroker db, final IBgpManager bgpManager) {
        super(VpnInstance.class);
        broker = db;
        this.bgpManager = bgpManager;
        this.fibListener = new FibEntriesListener();
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), VpnManager.this, DataChangeScope.SUBTREE);
            fibListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getFibEntryListenerPath(), fibListener, DataChangeScope.BASE);
        } catch (final Exception e) {
            LOG.error("VPN Service DataChange listener registration fail !", e);
            throw new IllegalStateException("VPN Service registration Listener failed.", e);
        }
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    public void setVpnInterfaceManager(VpnInterfaceManager vpnInterfaceManager) {
        this.vpnInterfaceManager = vpnInterfaceManager;
    }

    private void waitForOpRemoval(String rd, String vpnName) {
        //wait till DCN for update on VPN Instance Op Data signals that vpn interfaces linked to this vpn instance is zero
        //TODO(vpnteam): Entire code would need refactoring to listen only on the parent object - VPNInstance
        VpnInstanceOpDataEntry vpnOpEntry = null;
        Long intfCount = 0L;
        Long currentIntfCount = 0L;
        Integer retryCount = 1;
        long timeout = VpnConstants.MIN_WAIT_TIME_IN_MILLISECONDS;
        Optional<VpnInstanceOpDataEntry> vpnOpValue = null;
        vpnOpValue = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(rd));

        if ((vpnOpValue != null) && (vpnOpValue.isPresent())) {
            vpnOpEntry = vpnOpValue.get();
            List<VpnToDpnList> dpnToVpns = vpnOpEntry.getVpnToDpnList();
            if (dpnToVpns != null) {
                for (VpnToDpnList dpn : dpnToVpns) {
                    if (dpn.getVpnInterfaces() != null) {
                        intfCount = intfCount + dpn.getVpnInterfaces().size();
                    }
                }
            }
            //intfCount = vpnOpEntry.getVpnInterfaceCount();
            while (true) {
                if (intfCount > 0) {
                    // Minimum wait time of 5 seconds for one VPN Interface clearance (inclusive of full trace on)
                    timeout = intfCount * VpnConstants.MIN_WAIT_TIME_IN_MILLISECONDS;
                    // Maximum wait time of 90 seconds for all VPN Interfaces clearance (inclusive of full trace on)
                    if (timeout > VpnConstants.MAX_WAIT_TIME_IN_MILLISECONDS) {
                        timeout = VpnConstants.MAX_WAIT_TIME_IN_MILLISECONDS;
                    }
                    LOG.info("VPNInstance removal count of interface at {} for for rd {}, vpnname {}",
                            intfCount, rd, vpnName);
                }
                LOG.info("VPNInstance removal thread waiting for {} seconds for rd {}, vpnname {}",
                        (timeout / 1000), rd, vpnName);

                try {
                    Thread.sleep(timeout);
                } catch (java.lang.InterruptedException e) {
                }

                // Check current interface count
                vpnOpValue = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getVpnInstanceOpDataIdentifier(rd));
                if ((vpnOpValue != null) && (vpnOpValue.isPresent())) {
                    vpnOpEntry = vpnOpValue.get();
                    dpnToVpns = vpnOpEntry.getVpnToDpnList();
                    currentIntfCount = 0L;
                    if (dpnToVpns != null) {
                        for (VpnToDpnList dpn : dpnToVpns) {
                            if (dpn.getVpnInterfaces() != null) {
                                currentIntfCount = currentIntfCount + dpn.getVpnInterfaces().size();
                            }
                        }
                    }
                    if ((currentIntfCount == 0) || (currentIntfCount >= intfCount)) {
                        // Either the FibManager completed its job to cleanup all vpnInterfaces in VPN
                        // OR
                        // There is no progress by FibManager in removing all the interfaces even after good time!
                        // In either case, let us quit and take our chances.
                        //TODO(vpnteam): L3VPN refactoring to take care of this case.
                        if ((dpnToVpns == null) || dpnToVpns.size() <= 0) {
                            LOG.info("VPN Instance vpn {} rd {} ready for removal, exiting wait loop", vpnName, rd);
                            break;
                        } else {
                            if (retryCount > 0) {
                                retryCount--;
                                LOG.info("Retrying clearing vpn with vpnname {} rd {} since current interface count {} ", vpnName, rd, currentIntfCount);
                                if (currentIntfCount > 0) {
                                    intfCount = currentIntfCount;
                                } else {
                                    LOG.info("Current interface count is zero, but instance Op for vpn {} and rd {} not cleared yet. Waiting for 5 more seconds.", vpnName, rd);
                                    intfCount = 1L;
                                }
                            } else {
                                LOG.info("VPNInstance bailing out of wait loop as current interface count is {} and max retries exceeded for for vpnName {}, rd {}",
                                        currentIntfCount, vpnName, rd);
                                break;
                            }
                        }
                    }
                } else {
                    // There is no VPNOPEntry.  Something else happened on the system !
                    // So let us quit and take our chances.
                    //TODO(vpnteam): L3VPN refactoring to take care of this case.
                    break;
                }
            }
        }
        LOG.info("Returned out of waiting for  Op Data removal for rd {}, vpnname {}", rd, vpnName);
    }
    @Override
    protected void remove(InstanceIdentifier<VpnInstance> identifier, VpnInstance del) {
        LOG.trace("Remove VPN event key: {}, value: {}", identifier, del);
        final String vpnName = del.getVpnInstanceName();
        final String rd = del.getIpv4Family().getRouteDistinguisher();
        final long vpnId = VpnUtil.getVpnId(broker, vpnName);
        Optional<VpnInstanceOpDataEntry> vpnOpValue = null;

        //TODO(vpnteam): Entire code would need refactoring to listen only on the parent object - VPNInstance
        try {
            if ((rd != null) && (!rd.isEmpty())) {
                vpnOpValue = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getVpnInstanceOpDataIdentifier(rd));
            } else {
                vpnOpValue = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getVpnInstanceOpDataIdentifier(vpnName));
            }
        } catch (Exception e) {
            LOG.error("Exception when attempting to retrieve VpnInstanceOpDataEntry for VPN {}. ", vpnName, e);
            return;
        }

        if (vpnOpValue == null || !vpnOpValue.isPresent()) {
            LOG.error("Unable to retrieve VpnInstanceOpDataEntry for VPN {}. ", vpnName);
            return;
        }

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPN-" + vpnName,
                new DeleteVpnInstanceWorker(idManager, broker, del));
    }

    private class DeleteVpnInstanceWorker implements Callable<List<ListenableFuture<Void>>> {
        IdManagerService idManager;
        DataBroker broker;
        VpnInstance vpnInstance;

        public DeleteVpnInstanceWorker(IdManagerService idManager,
                                       DataBroker broker,
                                       VpnInstance value) {
            this.idManager = idManager;
            this.broker = broker;
            this.vpnInstance = value;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            final String vpnName = vpnInstance.getVpnInstanceName();
            final String rd = vpnInstance.getIpv4Family().getRouteDistinguisher();
            final long vpnId = VpnUtil.getVpnId(broker, vpnName);
            WriteTransaction writeTxn = broker.newWriteOnlyTransaction();
            if ((rd != null) && (!rd.isEmpty())) {
                waitForOpRemoval(rd, vpnName);
            } else {
                waitForOpRemoval(vpnName, vpnName);
            }

            // Clean up VpnInstanceToVpnId from Config DS
            VpnUtil.removeVpnIdToVpnInstance(broker, vpnId, writeTxn);
            VpnUtil.removeVpnInstanceToVpnId(broker, vpnName, writeTxn);

            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeTxn.submit());
            LOG.trace("Removed vpnIdentifier for  rd{} vpnname {}", rd, vpnName);
            if (rd != null) {
                synchronized (rd.intern()) {
                    try {
                        bgpManager.deleteVrf(rd);
                    } catch (Exception e) {
                        LOG.error("Exception when removing VRF from BGP for RD {} in VPN {} exception " + e, rd, vpnName);
                    }
                }

                // Clean up VPNExtraRoutes Operational DS
                VpnUtil.removeVpnExtraRouteForVpn(broker, rd, null);

                // Clean up VPNInstanceOpDataEntry
                VpnUtil.removeVpnOpInstance(broker, rd, null);
            } else {
                // Clean up FIB Entries Config DS
                synchronized (vpnName.intern()) {
                    VpnUtil.removeVrfTableForVpn(broker, vpnName, null);
                }
                // Clean up VPNExtraRoutes Operational DS
                VpnUtil.removeVpnExtraRouteForVpn(broker, vpnName, null);

                // Clean up VPNInstanceOpDataEntry
                VpnUtil.removeVpnOpInstance(broker, vpnName, null);
            }
            // Clean up PrefixToInterface Operational DS
            VpnUtil.removePrefixToInterfaceForVpnId(broker, vpnId, null);

            // Clean up L3NextHop Operational DS
            VpnUtil.removeL3nexthopForVpnId(broker, vpnId, null);

            // Release the ID used for this VPN back to IdManager
            VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME, vpnName);

            return futures;
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> identifier,
                          VpnInstance original, VpnInstance update) {
        LOG.trace("Update VPN event key: {}, value: {}", identifier, update);
    }

    @Override
    protected void add(final InstanceIdentifier<VpnInstance> identifier, final VpnInstance value) {
        LOG.trace("Add VPN event key: {}, value: {}", identifier, value);
        final VpnAfConfig config = value.getIpv4Family();
        final String rd = config.getRouteDistinguisher();
        final String vpnName = value.getVpnInstanceName();

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPN-" + vpnName,
                new AddVpnInstanceWorker(idManager, vpnInterfaceManager, broker, value));
    }

    private class AddVpnInstanceWorker implements Callable<List<ListenableFuture<Void>>> {
        IdManagerService idManager;
        VpnInterfaceManager vpnInterfaceManager;
        VpnInstance vpnInstance;
        DataBroker broker;

        public AddVpnInstanceWorker(IdManagerService idManager,
                                    VpnInterfaceManager vpnInterfaceManager,
                                    DataBroker broker,
                                    VpnInstance value) {
            this.idManager = idManager;
            this.vpnInterfaceManager = vpnInterfaceManager;
            this.broker = broker;
            this.vpnInstance = value;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            final VpnAfConfig config = vpnInstance.getIpv4Family();
            final String rd = config.getRouteDistinguisher();
            WriteTransaction writeTxn = broker.newWriteOnlyTransaction();
            addVpnInstance(vpnInstance, writeTxn);
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeTxn.submit());
            ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
            if (rd != null) {
                Futures.addCallback(listenableFuture,
                        new AddBgpVrfWorker(config , vpnInstance.getVpnInstanceName()));
            }
            return futures;
        }
    }

    private void addVpnInstance(VpnInstance value, WriteTransaction writeTxn) {
        VpnAfConfig config = value.getIpv4Family();
        String rd = config.getRouteDistinguisher();
        String vpnInstanceName = value.getVpnInstanceName();

        long vpnId = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, vpnInstanceName);
        LOG.trace("VPN instance to ID generated.");
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
                vpnInstanceToVpnId = VpnUtil.getVpnInstanceToVpnId(vpnInstanceName, vpnId, (rd != null) ? rd
                : vpnInstanceName);

        if (writeTxn != null) {
            writeTxn.put(LogicalDatastoreType.CONFIGURATION, VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnInstanceName),
                    vpnInstanceToVpnId, true);
        } else {
            syncWrite(LogicalDatastoreType.CONFIGURATION, VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnInstanceName),
                    vpnInstanceToVpnId, DEFAULT_CALLBACK);
        }

        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds
                vpnIdToVpnInstance = VpnUtil.getVpnIdToVpnInstance(vpnId, value.getVpnInstanceName(),
                (rd != null) ? rd : value.getVpnInstanceName(), (rd != null)/*isExternalVpn*/);

        if (writeTxn != null) {
            writeTxn.put(LogicalDatastoreType.CONFIGURATION,
                    VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId),
                    vpnIdToVpnInstance, true);
        } else {
            syncWrite(LogicalDatastoreType.CONFIGURATION,
                    VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId),
                    vpnIdToVpnInstance, DEFAULT_CALLBACK);
        }

        IFibManager fibManager = vpnInterfaceManager.getFibManager();
        try {
            String cachedTransType = fibManager.getConfTransType();
            LOG.trace("Value for confTransportType is " + cachedTransType);
            if (cachedTransType.equals("Invalid")) {
                try {
                    fibManager.setConfTransType("L3VPN", "VXLAN");
                } catch (Exception e) {
                    LOG.trace("Exception caught setting the cached value for transportType");
                    LOG.error(e.getMessage());
                }
            } else {
                LOG.trace(":cached val is neither unset/invalid. NO-op.");
            }
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }

        if (rd == null) {
            VpnInstanceOpDataEntryBuilder builder =
                    new VpnInstanceOpDataEntryBuilder().setVrfId(vpnInstanceName).setVpnId(vpnId)
                            .setVpnInstanceName(vpnInstanceName)
                            .setVpnInterfaceCount(0L).setActiveDpnCount(0L);
            if (writeTxn != null) {
                writeTxn.merge(LogicalDatastoreType.OPERATIONAL, VpnUtil.getVpnInstanceOpDataIdentifier(vpnInstanceName),
                        builder.build(), true);
            } else {
                syncWrite(LogicalDatastoreType.OPERATIONAL, VpnUtil.getVpnInstanceOpDataIdentifier(vpnInstanceName),
                        builder.build(), DEFAULT_CALLBACK);
            }
        } else {
            VpnInstanceOpDataEntryBuilder builder =
                    new VpnInstanceOpDataEntryBuilder().setVrfId(rd).setVpnId(vpnId).setVpnInstanceName(vpnInstanceName)
                            .setVpnInterfaceCount(0L).setActiveDpnCount(0L);

            if (writeTxn != null) {
                writeTxn.merge(LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                        builder.build(), true);
            } else {
                syncWrite(LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                        builder.build(), DEFAULT_CALLBACK);
            }
        }
    }


    private class AddBgpVrfWorker implements FutureCallback<List<Void>> {
        VpnAfConfig config;
        String vpnName;

        public AddBgpVrfWorker(VpnAfConfig config, String vpnName)  {
            this.config = config;
            this.vpnName = vpnName;
        }

        /**
         * @param voids
         * This implies that all the future instances have returned success. -- TODO: Confirm this
         */
        @Override
        public void onSuccess(List<Void> voids) {
            String rd = config.getRouteDistinguisher();
            if (rd != null) {
                List<VpnTarget> vpnTargetList = config.getVpnTargets().getVpnTarget();

                List<String> ertList = new ArrayList<String>();
                List<String> irtList = new ArrayList<String>();

                for (VpnTarget vpnTarget : vpnTargetList) {
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ExportExtcommunity) {
                        ertList.add(vpnTarget.getVrfRTValue());
                    }
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ImportExtcommunity) {
                        irtList.add(vpnTarget.getVrfRTValue());
                    }
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.Both) {
                        ertList.add(vpnTarget.getVrfRTValue());
                        irtList.add(vpnTarget.getVrfRTValue());
                    }
                }

                try {
                    bgpManager.addVrf(rd, irtList, ertList);
                } catch (Exception e) {
                    LOG.error("Exception when adding VRF to BGP", e);
                    return;
                }
                vpnInterfaceManager.handleVpnsExportingRoutes(this.vpnName, rd);
            }
        }
        /**
         *
         * @param throwable
         * This method is used to handle failure callbacks.
         * If more retry needed, the retrycount is decremented and mainworker is executed again.
         * After retries completed, rollbackworker is executed.
         * If rollbackworker fails, this is a double-fault. Double fault is logged and ignored.
         */

        @Override
        public void onFailure(Throwable throwable) {
            LOG.warn("Job: failed with exception: {}", throwable.getStackTrace());
        }
    }

    public boolean isVPNConfigured() {

        InstanceIdentifier<VpnInstances> vpnsIdentifier =
                InstanceIdentifier.builder(VpnInstances.class).build();
        Optional<VpnInstances> optionalVpns = read( LogicalDatastoreType.CONFIGURATION,
                vpnsIdentifier);
        if (!optionalVpns.isPresent() ||
                optionalVpns.get().getVpnInstance() == null ||
                optionalVpns.get().getVpnInstance().isEmpty()) {
            LOG.trace("No VPNs configured.");
            return false;
        }
        LOG.trace("VPNs are configured on the system.");
        return true;
    }

    private InstanceIdentifier<?> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstances.class).child(VpnInstance.class);
    }

    private InstanceIdentifier<?> getFibEntryListenerPath() {
        return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class)
                .child(VrfEntry.class);
    }

    private InstanceIdentifier<?> getVpnInstanceOpListenerPath() {
        return InstanceIdentifier.create(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up Vpn DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        if (fibListenerRegistration != null) {
            try {
                fibListenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up Fib entries DataChangeListener.", e);
            }
            fibListenerRegistration = null;
        }
        LOG.trace("VPN Manager Closed");
    }

    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private <T extends DataObject> void syncWrite(LogicalDatastoreType datastoreType,
                                                   InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing VPN instance to ID info to datastore (path, data) : ({}, {})", path, data);
            throw new RuntimeException(e.getMessage());
        }
    }

    protected VpnInstance getVpnInstance(String vpnInstanceName) {
        return VpnUtil.getVpnInstance(broker, vpnInstanceName);
    }

    protected VpnInstanceOpDataEntry getVpnInstanceOpData(String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = read(LogicalDatastoreType.OPERATIONAL, id);
        if(vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    private class FibEntriesListener extends AbstractDataChangeListener<VrfEntry>  {

        public FibEntriesListener() {
            super(VrfEntry.class);
        }

        @Override
        protected void remove(InstanceIdentifier<VrfEntry> identifier,
                VrfEntry del) {
            LOG.trace("Remove Fib event - Key : {}, value : {} ", identifier, del);
            final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
            String rd = key.getRouteDistinguisher();
            Long label = del.getLabel();
            VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(rd);
            if(vpnInstanceOpData != null) {
                List<Long> routeIds = vpnInstanceOpData.getRouteEntryId();
                if(routeIds == null) {
                    LOG.debug("Fib Route entry is empty.");
                    return;
                }
                LOG.debug("Removing label from vpn info - {}", label);
                routeIds.remove(label);
                asyncWrite(LogicalDatastoreType.OPERATIONAL, VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                           new VpnInstanceOpDataEntryBuilder(vpnInstanceOpData).setRouteEntryId(routeIds).build(), DEFAULT_CALLBACK);
            } else {
                LOG.warn("No VPN Instance found for RD: {}", rd);
            }
        }

        @Override
        protected void update(InstanceIdentifier<VrfEntry> identifier,
                VrfEntry original, VrfEntry update) {
            // TODO Auto-generated method stub

        }

        @Override
        protected void add(InstanceIdentifier<VrfEntry> identifier,
                           VrfEntry add) {
            LOG.trace("Add Vrf Entry event - Key : {}, value : {}", identifier, add);
            final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
            String rd = key.getRouteDistinguisher();
            Long label = add.getLabel();
            VpnInstanceOpDataEntry vpn = getVpnInstanceOpData(rd);
            if(vpn != null) {
                List<Long> routeIds = vpn.getRouteEntryId();
                if(routeIds == null) {
                    routeIds = new ArrayList<>();
                }
                LOG.debug("Adding label to vpn info - {}", label);
                routeIds.add(label);
                asyncWrite(LogicalDatastoreType.OPERATIONAL, VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                           new VpnInstanceOpDataEntryBuilder(vpn).setRouteEntryId(routeIds).build(), DEFAULT_CALLBACK);
            } else {
                LOG.warn("No VPN Instance found for RD: {}", rd);
            }
        }
    }
}

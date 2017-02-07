/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.Vpn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInstanceListener extends AsyncDataTreeChangeListenerBase<VpnInstance, VpnInstanceListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInstanceListener.class);
    private final DataBroker dataBroker;
    private final IBgpManager bgpManager;
    private final IdManagerService idManager;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final IFibManager fibManager;
    private final VpnOpDataSyncer vpnOpDataNotifier;

    public VpnInstanceListener(final DataBroker dataBroker, final IBgpManager bgpManager,
        final IdManagerService idManager, final VpnInterfaceManager vpnInterfaceManager, final IFibManager fibManager,
        final VpnOpDataSyncer vpnOpDataSyncer) {
        super(VpnInstance.class, VpnInstanceListener.class);
        this.dataBroker = dataBroker;
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.fibManager = fibManager;
        this.vpnOpDataNotifier = vpnOpDataSyncer;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInstance> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstances.class).child(VpnInstance.class);
    }

    @Override
    protected VpnInstanceListener getDataTreeChangeListener() {
        return VpnInstanceListener.this;
    }

    private void waitForOpRemoval(String rd, String vpnName) {
        //wait till DCN for update on VPN Instance Op Data signals that vpn interfaces linked to this vpn instance is
        // zero
        //TODO(vpnteam): Entire code would need refactoring to listen only on the parent object - VPNInstance
        VpnInstanceOpDataEntry vpnOpEntry = null;
        Long intfCount = 0L;
        Long currentIntfCount = 0L;
        Integer retryCount = 3;
        long timeout = VpnConstants.MIN_WAIT_TIME_IN_MILLISECONDS;
        Optional<VpnInstanceOpDataEntry> vpnOpValue = null;
        vpnOpValue = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
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
                } catch (InterruptedException e) {
                    // Ignored
                }

                // Check current interface count
                vpnOpValue = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
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
                                LOG.info(
                                    "Retrying clearing vpn with vpnname {} rd {} since current interface count {} ",
                                    vpnName, rd, currentIntfCount);
                                if (currentIntfCount > 0) {
                                    intfCount = currentIntfCount;
                                } else {
                                    LOG.info(
                                        "Current interface count is zero, but instance Op for vpn {} and rd {} not "
                                            + "cleared yet. Waiting for 5 more seconds.",
                                        vpnName, rd);
                                    intfCount = 1L;
                                }
                            } else {
                                LOG.info(
                                    "VPNInstance bailing out of wait loop as current interface count is {} and max "
                                        + "retries exceeded for for vpnName {}, rd {}",
                                    currentIntfCount, vpnName, rd);
                                break;
                            }
                        }
                    } else {
                        LOG.info("Retrying clearing because not all vpnInterfaces removed : current interface count {},"
                                + " initial count {} for rd {}, vpnname {}", currentIntfCount, intfCount, rd, vpnName);
                        intfCount = currentIntfCount;
                    }
                } else {
                    // There is no VPNOPEntry.  Something else happened on the system !
                    // So let us quit and take our chances.
                    //TODO(vpnteam): L3VPN refactoring to take care of this case.
                    LOG.error("VpnInstanceOpData is not present in the operational DS for rd {}, vpnname {}", rd,
                            vpnName);
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
        Optional<VpnInstanceOpDataEntry> vpnOpValue = null;
        String primaryRd = VpnUtil.getPrimaryRd(del);

        //TODO(vpnteam): Entire code would need refactoring to listen only on the parent object - VPNInstance
        try {
            vpnOpValue = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd));
        } catch (ReadFailedException e) {
            LOG.error("Exception when attempting to retrieve VpnInstanceOpDataEntry for VPN {}. ", vpnName, e);
            return;
        }

        if (vpnOpValue == null || !vpnOpValue.isPresent()) {
            LOG.error("Unable to retrieve VpnInstanceOpDataEntry for VPN {}. ", vpnName);
            return;
        }

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPN-" + vpnName,
            new DeleteVpnInstanceWorker(idManager, dataBroker, del));
    }

    private class DeleteVpnInstanceWorker implements Callable<List<ListenableFuture<Void>>> {
        IdManagerService idManager;
        DataBroker broker;
        VpnInstance vpnInstance;

        DeleteVpnInstanceWorker(IdManagerService idManager,
            DataBroker broker,
            VpnInstance value) {
            this.idManager = idManager;
            this.broker = broker;
            this.vpnInstance = value;
        }

        // TODO Clean up the exception handling
        @SuppressWarnings("checkstyle:IllegalCatch")
        @Override
        public List<ListenableFuture<Void>> call() {
            final String vpnName = vpnInstance.getVpnInstanceName();
            final List<String> rds = vpnInstance.getIpv4Family().getRouteDistinguisher();
            String primaryRd = VpnUtil.getPrimaryRd(vpnInstance);
            final long vpnId = VpnUtil.getVpnId(broker, vpnName);
            WriteTransaction writeTxn = broker.newWriteOnlyTransaction();
            waitForOpRemoval(primaryRd, vpnName);

            // Clean up VpnInstanceToVpnId from Config DS
            VpnUtil.removeVpnIdToVpnInstance(broker, vpnId, writeTxn);
            VpnUtil.removeVpnInstanceToVpnId(broker, vpnName, writeTxn);
            LOG.trace("Removed vpnIdentifier for  rd{} vpnname {}", primaryRd, vpnName);
            // Clean up FIB Entries Config DS
            synchronized (vpnName.intern()) {
                fibManager.removeVrfTable(broker, primaryRd, null);
            }
            if (!VpnUtil.isInternalVpn(vpnName, primaryRd)) {
                rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(rd, false));
            }
            // Clean up VPNExtraRoutes Operational DS
            InstanceIdentifier<Vpn> vpnToExtraroute = VpnUtil.getVpnToExtrarouteIdentifier(primaryRd);
            Optional<Vpn> optVpnToExtraroute = VpnUtil.read(broker,
                LogicalDatastoreType.OPERATIONAL, vpnToExtraroute);
            if (optVpnToExtraroute.isPresent()) {
                VpnUtil.removeVpnExtraRouteForVpn(broker, primaryRd, writeTxn);
            }

            // Clean up VPNInstanceOpDataEntry
            VpnUtil.removeVpnOpInstance(broker, primaryRd, writeTxn);
            // Clean up PrefixToInterface Operational DS
            VpnUtil.removePrefixToInterfaceForVpnId(broker, vpnId, writeTxn);

            // Clean up L3NextHop Operational DS
            VpnUtil.removeL3nexthopForVpnId(broker, vpnId, writeTxn);

            // Release the ID used for this VPN back to IdManager
            VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME, vpnName);

            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeTxn.submit());
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
        final String vpnName = value.getVpnInstanceName();

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPN-" + vpnName,
            new AddVpnInstanceWorker(idManager, vpnInterfaceManager, dataBroker, value));
    }

    private class AddVpnInstanceWorker implements Callable<List<ListenableFuture<Void>>> {
        IdManagerService idManager;
        VpnInterfaceManager vpnInterfaceManager;
        VpnInstance vpnInstance;
        DataBroker broker;

        AddVpnInstanceWorker(IdManagerService idManager,
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
            WriteTransaction writeConfigTxn = broker.newWriteOnlyTransaction();
            WriteTransaction writeOperTxn = broker.newWriteOnlyTransaction();
            addVpnInstance(vpnInstance, writeConfigTxn, writeOperTxn);
            CheckedFuture<Void, TransactionCommitFailedException> checkFutures = writeOperTxn.submit();
            try {
                checkFutures.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error creating vpn {} ", vpnInstance.getVpnInstanceName());
                throw new RuntimeException(e.getMessage());
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeConfigTxn.submit());
            ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
            Futures.addCallback(listenableFuture,
                new PostAddVpnInstanceWorker(config, vpnInstance.getVpnInstanceName()));
            return futures;
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addVpnInstance(VpnInstance value, WriteTransaction writeConfigTxn,
        WriteTransaction writeOperTxn) {
        VpnAfConfig config = value.getIpv4Family();
        String vpnInstanceName = value.getVpnInstanceName();

        long vpnId = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, vpnInstanceName);
        if (vpnId == 0) {
            LOG.error(
                "Unable to fetch label from Id Manager. Bailing out of adding operational data for Vpn Instance {}",
                value.getVpnInstanceName());
            LOG.error(
                "Unable to fetch label from Id Manager. Bailing out of adding operational data for Vpn Instance {}",
                value.getVpnInstanceName());
            return;
        }
        LOG.info("VPN Id {} generated for VpnInstanceName {}", vpnId, vpnInstanceName);
        String primaryRd = VpnUtil.getPrimaryRd(value);
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
            vpnInstanceToVpnId = VpnUtil.getVpnInstanceToVpnId(vpnInstanceName, vpnId, primaryRd);

        if (writeConfigTxn != null) {
            writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION,
                VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnInstanceName),
                vpnInstanceToVpnId, true);
        } else {
            TransactionUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnInstanceName),
                vpnInstanceToVpnId, TransactionUtil.DEFAULT_CALLBACK);
        }

        VpnIds vpnIdToVpnInstance = VpnUtil.getVpnIdToVpnInstance(vpnId, value.getVpnInstanceName(),
            primaryRd, !VpnUtil.isInternalVpn(vpnInstanceName, primaryRd));

        if (writeConfigTxn != null) {
            writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION,
                VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId),
                vpnIdToVpnInstance, true);
        } else {
            TransactionUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId),
                vpnIdToVpnInstance, TransactionUtil.DEFAULT_CALLBACK);
        }

        try {
            String cachedTransType = fibManager.getConfTransType();
            if (cachedTransType.equals("Invalid")) {
                try {
                    fibManager.setConfTransType("L3VPN", "VXLAN");
                } catch (Exception e) {
                    LOG.error("Exception caught setting the L3VPN tunnel transportType", e);
                }
            } else {
                LOG.trace("Configured tunnel transport type for L3VPN as {}", cachedTransType);
            }
        } catch (Exception e) {
            LOG.error("Error when trying to retrieve tunnel transport type for L3VPN ", e);
        }

        VpnInstanceOpDataEntryBuilder builder =
                new VpnInstanceOpDataEntryBuilder().setVrfId(primaryRd).setVpnId(vpnId)
                        .setVpnInstanceName(vpnInstanceName);
        if (!VpnUtil.isInternalVpn(vpnInstanceName, primaryRd)) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn
                .instance.op.data.entry.vpntargets.VpnTarget> opVpnTargetList = new ArrayList<>();
            if (value.getL3vni() != null) {
                builder.setL3vni(value.getL3vni());
            }
            VpnTargets vpnTargets = config.getVpnTargets();
            if (vpnTargets != null) {
                List<VpnTarget> vpnTargetList = vpnTargets.getVpnTarget();
                if (vpnTargetList != null) {
                    for (VpnTarget vpnTarget : vpnTargetList) {
                        VpnTargetBuilder vpnTargetBuilder =
                            new VpnTargetBuilder().setKey(new VpnTargetKey(vpnTarget.getKey().getVrfRTValue()))
                                .setVrfRTType(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                                    .instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget.VrfRTType
                                    .forValue(vpnTarget.getVrfRTType().getIntValue())).setVrfRTValue(
                                vpnTarget.getVrfRTValue());
                        opVpnTargetList.add(vpnTargetBuilder.build());
                    }
                }
            }
            VpnTargetsBuilder vpnTargetsBuilder = new VpnTargetsBuilder().setVpnTarget(opVpnTargetList);
            builder.setVpnTargets(vpnTargetsBuilder.build());

            if (writeOperTxn != null) {
                writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd),
                    builder.build(), true);
            } else {
                TransactionUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd),
                    builder.build(), TransactionUtil.DEFAULT_CALLBACK);
            }
        }
        LOG.info("VpnInstanceOpData populated successfully for vpn {} rd {}", vpnInstanceName, primaryRd);
    }

    private class PostAddVpnInstanceWorker implements FutureCallback<List<Void>> {
        VpnAfConfig config;
        String vpnName;

        PostAddVpnInstanceWorker(VpnAfConfig config, String vpnName) {
            this.config = config;
            this.vpnName = vpnName;
        }

        /**
         * This implies that all the future instances have returned success. -- TODO: Confirm this
         */
        @Override
        public void onSuccess(List<Void> voids) {
            /*
            if rd is null, then its either a router vpn instance (or) a vlan external network vpn instance.
            if rd is non-null, then it is a bgpvpn instance
             */
            List<String> rd = config.getRouteDistinguisher();
            if ((rd == null) || addBgpVrf(voids)) {
                notifyTask();
                vpnInterfaceManager.vpnInstanceIsReady(vpnName);
            }
        }

        // TODO Clean up the exception handling
        @SuppressWarnings("checkstyle:IllegalCatch")
        private boolean addBgpVrf(List<Void> voids) {
            List<String> rds = config.getRouteDistinguisher();
            String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
            List<VpnTarget> vpnTargetList = config.getVpnTargets().getVpnTarget();

            List<String> ertList = new ArrayList<>();
            List<String> irtList = new ArrayList<>();

            if (vpnTargetList != null) {
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
            } else {
                LOG.error("vpn target list is empty, cannot add BGP VPN {} VRF {}", this.vpnName, primaryRd);
                return false;
            }
            rds.parallelStream().forEach(rd -> bgpManager.addVrf(rd, irtList, ertList));
            vpnInterfaceManager.handleVpnsExportingRoutes(this.vpnName, primaryRd);
            return true;
        }

        private void notifyTask() {
            vpnOpDataNotifier.notifyVpnOpDataReady(VpnOpDataSyncer.VpnOpDataType.vpnInstanceToId, vpnName);
            vpnOpDataNotifier.notifyVpnOpDataReady(VpnOpDataSyncer.VpnOpDataType.vpnOpData, vpnName);
        }

        /**
         * This method is used to handle failure callbacks.
         * If more retry needed, the retrycount is decremented and mainworker is executed again.
         * After retries completed, rollbackworker is executed.
         * If rollbackworker fails, this is a double-fault. Double fault is logged and ignored.
         */

        @Override
        public void onFailure(Throwable throwable) {
            LOG.error("Job for vpnInstance: {} failed with exception: {}", vpnName ,throwable);
            vpnInterfaceManager.vpnInstanceFailed(vpnName);
        }
    }

    public boolean isVPNConfigured() {

        InstanceIdentifier<VpnInstances> vpnsIdentifier = InstanceIdentifier.builder(VpnInstances.class).build();
        Optional<VpnInstances> optionalVpns = TransactionUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
            vpnsIdentifier);
        if (!optionalVpns.isPresent()
            || optionalVpns.get().getVpnInstance() == null
            || optionalVpns.get().getVpnInstance().isEmpty()) {
            LOG.trace("No VPNs configured.");
            return false;
        }
        LOG.trace("VPNs are configured on the system.");
        return true;
    }

    protected VpnInstanceOpDataEntry getVpnInstanceOpData(String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData =
            TransactionUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }
}

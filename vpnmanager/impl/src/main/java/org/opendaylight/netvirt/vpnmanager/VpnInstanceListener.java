/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.mdsal.binding.api.WriteTransaction.CREATE_MISSING_PARENTS;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.DcGatewayIpList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.dc.gateway.ip.list.DcGatewayIp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.vpntargets.VpnTarget;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnInstanceListener extends AbstractAsyncDataTreeChangeListener<VpnInstance> {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInstanceListener.class);
    private static final String LOGGING_PREFIX_ADD = "VPN-ADD:";
    private static final String LOGGING_PREFIX_UPDATE = "VPN-UPDATE:";
    private static final String LOGGING_PREFIX_DELETE = "VPN-REMOVE:";
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IdManagerService idManager;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final IFibManager fibManager;
    private final VpnOpDataSyncer vpnOpDataNotifier;
    private final IMdsalApiManager mdsalManager;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;

    @Inject
    public VpnInstanceListener(final DataBroker dataBroker, final IdManagerService idManager,
            final VpnInterfaceManager vpnInterfaceManager, final IFibManager fibManager,
            final VpnOpDataSyncer vpnOpDataSyncer, final IMdsalApiManager mdsalManager,
            final JobCoordinator jobCoordinator, VpnUtil vpnUtil) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(VpnInstances.class).child(VpnInstance.class),
                Executors.newListeningSingleThreadExecutor("VpnInstanceListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = idManager;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.fibManager = fibManager;
        this.vpnOpDataNotifier = vpnOpDataSyncer;
        this.mdsalManager = mdsalManager;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<VpnInstance> identifier, VpnInstance del) {
        LOG.trace("{} : VPN event key: {}, value: {}", LOGGING_PREFIX_DELETE, identifier, del);
        final String vpnName = del.getVpnInstanceName();
        Optional<VpnInstanceOpDataEntry> vpnOpValue;
        String primaryRd = vpnUtil.getVpnRd(vpnName);
        if (primaryRd == null) {
            LOG.error("{}, failed to remove VPN: primaryRd is null for vpn {}", LOGGING_PREFIX_DELETE, vpnName);
            return;
        }

        //TODO(vpnteam): Entire code would need refactoring to listen only on the parent object - VPNInstance
        try {
            vpnOpValue = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd));
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{}, failed to remove VPN: Exception while retrieving VpnInstanceOpDataEntry for VPN {}. ",
                    LOGGING_PREFIX_DELETE,  vpnName, e);
            return;
        }

        if (!vpnOpValue.isPresent()) {
            LOG.error("{}, failed to remove VPN: Unable to retrieve VpnInstanceOpDataEntry for VPN {}. ",
                LOGGING_PREFIX_DELETE, vpnName);
            return;
        } else {
            jobCoordinator.enqueueJob("VPN-" + vpnName, () ->
                Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
                    VpnInstanceOpDataEntryBuilder builder = null;
                    InstanceIdentifier<VpnInstanceOpDataEntry> id = null;
                    if (primaryRd != null) {
                        builder = new VpnInstanceOpDataEntryBuilder().setVrfId(primaryRd)
                                .setVpnState(VpnInstanceOpDataEntry.VpnState.PendingDelete);
                        id = VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd);
                    } else {
                        builder = new VpnInstanceOpDataEntryBuilder().setVrfId(vpnName)
                                .setVpnState(VpnInstanceOpDataEntry.VpnState.PendingDelete);
                        id = VpnUtil.getVpnInstanceOpDataIdentifier(vpnName);
                    }
                    tx.merge(id, builder.build());

                    LOG.info("{} call: Operational status set to PENDING_DELETE for vpn {} with rd {}",
                            LOGGING_PREFIX_DELETE, vpnName, primaryRd);
                })), SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
        }
    }

    @Override
    public void update(InstanceIdentifier<VpnInstance> identifier,
        VpnInstance original, VpnInstance update) {
        LOG.trace("VPN-UPDATE: update: VPN event key: {}, value: {}.", identifier, update);
        String vpnName = update.getVpnInstanceName();
        if (original != null && update != null
                && original.getRouteDistinguisher() != null
                && update.getRouteDistinguisher() != null
                && original.getRouteDistinguisher().size()
                !=  update.getRouteDistinguisher().size()) {
            LOG.debug("VPN-UPDATE: VpnInstance:{} updated with new RDs: {} from old RDs: {}", vpnName,
                    update.getRouteDistinguisher(),  original.getRouteDistinguisher());
            vpnUtil.updateVpnInstanceWithRdList(vpnName, update.getRouteDistinguisher());
        }
        vpnInterfaceManager.updateVpnInterfacesForUnProcessAdjancencies(vpnName);
    }

    @Override
    public void add(final InstanceIdentifier<VpnInstance> identifier, final VpnInstance value) {
        LOG.trace("{} add: Add VPN event key: {}, value: {}", LOGGING_PREFIX_ADD, identifier, value);
        final String vpnName = value.getVpnInstanceName();
        jobCoordinator.enqueueJob("VPN-" + vpnName, new AddVpnInstanceWorker(dataBroker, value),
                SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
    }

    private class AddVpnInstanceWorker implements Callable<List<ListenableFuture<Void>>> {
        private final Logger log = LoggerFactory.getLogger(AddVpnInstanceWorker.class);
        VpnInstance vpnInstance;
        DataBroker broker;

        AddVpnInstanceWorker(DataBroker broker,
                VpnInstance value) {
            this.broker = broker;
            this.vpnInstance = value;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            List<ListenableFuture<Void>> futures = new ArrayList<>(2);
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, confTx -> {
                ListenableFuture<Void> future = txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, operTx ->
                        addVpnInstance(vpnInstance, confTx, operTx));
                ListenableFutures.addErrorLogging(future, LOG, "{} call: error creating VPN {} rd {}",
                        LOGGING_PREFIX_ADD, vpnInstance.getVpnInstanceName(),
                        vpnInstance.getRouteDistinguisher());
                futures.add(future);
            }));
            Futures.addCallback(Futures.allAsList(futures),
                                new PostAddVpnInstanceWorker(vpnInstance , vpnInstance.getVpnInstanceName()),
                                MoreExecutors.directExecutor());
            return futures;
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addVpnInstance(VpnInstance value, TypedWriteTransaction<Configuration> writeConfigTxn,
            TypedWriteTransaction<Operational> writeOperTxn) {
        if (writeConfigTxn == null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx ->
                addVpnInstance(value, tx, writeOperTxn)), LOG, "Error adding VPN instance {}", value);
            return;
        }
        if (writeOperTxn == null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx ->
                addVpnInstance(value, writeConfigTxn, tx)), LOG, "Error adding VPN instance {}", value);
            return;
        }
        String vpnInstanceName = value.getVpnInstanceName();

        Uint32 vpnId = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME, vpnInstanceName);
        if (vpnId.longValue() == 0) {
            LOG.error("{} addVpnInstance: Unable to fetch label from Id Manager. Bailing out of adding operational"
                    + " data for Vpn Instance {}", LOGGING_PREFIX_ADD, value.getVpnInstanceName());
            return;
        }
        LOG.info("{} addVpnInstance: VPN Id {} generated for VpnInstanceName {}", LOGGING_PREFIX_ADD, vpnId,
                vpnInstanceName);
        String primaryRd = VpnUtil.getPrimaryRd(value);
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
            vpnInstanceToVpnId = VpnUtil.getVpnInstanceToVpnId(vpnInstanceName, vpnId, primaryRd);

        writeConfigTxn.put(VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnInstanceName),
            vpnInstanceToVpnId, CREATE_MISSING_PARENTS);

        VpnIds vpnIdToVpnInstance = VpnUtil.getVpnIdToVpnInstance(vpnId, value.getVpnInstanceName(),
            primaryRd, VpnUtil.isBgpVpn(vpnInstanceName, primaryRd));

        writeConfigTxn.put(VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId), vpnIdToVpnInstance, CREATE_MISSING_PARENTS);

        try {
            String cachedTransType = fibManager.getConfTransType();
            if (cachedTransType.equals("Invalid")) {
                try {
                    fibManager.setConfTransType("L3VPN", "VXLAN");
                } catch (Exception e) {
                    LOG.error("{} addVpnInstance: Exception caught setting the L3VPN tunnel transportType for vpn {}",
                            LOGGING_PREFIX_ADD, vpnInstanceName, e);
                }
            } else {
                LOG.debug("{} addVpnInstance: Configured tunnel transport type for L3VPN {} as {}", LOGGING_PREFIX_ADD,
                        vpnInstanceName, cachedTransType);
            }
        } catch (Exception e) {
            LOG.error("{} addVpnInstance: Error when trying to retrieve tunnel transport type for L3VPN {}",
                    LOGGING_PREFIX_ADD, vpnInstanceName, e);
        }

        VpnInstanceOpDataEntryBuilder builder =
                new VpnInstanceOpDataEntryBuilder().setVrfId(primaryRd).setVpnId(vpnId)
                        .setVpnInstanceName(vpnInstanceName)
                        .setVpnState(VpnInstanceOpDataEntry.VpnState.Created);
        if (VpnUtil.isBgpVpn(vpnInstanceName, primaryRd)) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn
                .instance.op.data.entry.vpntargets.VpnTarget> opVpnTargetList = new ArrayList<>();
            builder.setBgpvpnType(VpnInstanceOpDataEntry.BgpvpnType.BGPVPNExternal);
            if (value.getL3vni() != null) {
                builder.setL3vni(value.getL3vni());
            }
            if (value.isL2vpn()) {
                builder.setType(VpnInstanceOpDataEntry.Type.L2);
            }
            VpnTargets vpnTargets = value.getVpnTargets();
            if (vpnTargets != null) {
                List<VpnTarget> vpnTargetList = vpnTargets.getVpnTarget();
                if (vpnTargetList != null) {
                    for (VpnTarget vpnTarget : vpnTargetList) {
                        VpnTargetBuilder vpnTargetBuilder =
                            new VpnTargetBuilder().withKey(new VpnTargetKey(vpnTarget.key().getVrfRTValue()))
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

            List<String> rds = value.getRouteDistinguisher();
            builder.setRd(rds);
        } else {
            builder.setBgpvpnType(VpnInstanceOpDataEntry.BgpvpnType.VPN);
        }
        writeOperTxn.merge(VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd), builder.build(), CREATE_MISSING_PARENTS);
        LOG.info("{} addVpnInstance: VpnInstanceOpData populated successfully for vpn {} rd {}", LOGGING_PREFIX_ADD,
                vpnInstanceName, primaryRd);
    }

    private class PostAddVpnInstanceWorker implements FutureCallback<List<Void>> {
        private final Logger log = LoggerFactory.getLogger(PostAddVpnInstanceWorker.class);
        VpnInstance vpnInstance;
        String vpnName;

        PostAddVpnInstanceWorker(VpnInstance vpnInstance, String vpnName) {
            this.vpnInstance = vpnInstance;
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
            List<String> rd = vpnInstance.getRouteDistinguisher();
            if (rd == null || addBgpVrf()) {
                notifyTask();
                vpnInterfaceManager.vpnInstanceIsReady(vpnName);
            }
            log.info("{} onSuccess: Vpn Instance Op Data addition for {} successful.", LOGGING_PREFIX_ADD, vpnName);
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vpnUtil.getVpnInstanceOpData(
                    VpnUtil.getPrimaryRd(vpnInstance));

            // bind service on each tunnel interface
            //TODO (KIRAN): Add a new listener to handle creation of new DC-GW binding and deletion of existing DC-GW.
            if (VpnUtil.isL3VpnOverVxLan(vpnInstance.getL3vni())) { //Handled for L3VPN Over VxLAN
                for (String tunnelInterfaceName: getDcGatewayTunnelInterfaceNameList()) {
                    vpnUtil.bindService(vpnInstance.getVpnInstanceName(), tunnelInterfaceName,
                            true/*isTunnelInterface*/);
                }

                // install flow
                List<MatchInfo> mkMatches = new ArrayList<>();
                mkMatches.add(new MatchTunnelId(Uint64.valueOf(vpnInstance.getL3vni())));

                List<InstructionInfo> instructions =
                        Arrays.asList(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(vpnInstanceOpDataEntry
                                .getVpnId().toJava()), MetaDataUtil.METADATA_MASK_VRFID),
                                new InstructionGotoTable(NwConstants.L3_GW_MAC_TABLE));
                try {
                    for (Uint64 dpnId: NWUtil.getOperativeDPNs(dataBroker)) {
                        String flowRef = getFibFlowRef(dpnId, NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                                vpnName, VpnConstants.DEFAULT_FLOW_PRIORITY);
                        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId,
                                NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE, flowRef, VpnConstants.DEFAULT_FLOW_PRIORITY,
                                "VxLAN VPN Tunnel Bind Service", 0, 0, NwConstants.COOKIE_VM_FIB_TABLE,
                                mkMatches, instructions);
                        mdsalManager.installFlow(dpnId, flowEntity);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    LOG.error("PostAddVpnInstanceWorker: Exception while getting the list of Operative DPNs for Vpn {}",
                            vpnName, e);
                }

                ///////////////////////
            }
        }

        // TODO Clean up the exception handling
        @SuppressWarnings("checkstyle:IllegalCatch")
        private boolean addBgpVrf() {
            String primaryRd = vpnUtil.getPrimaryRd(vpnName);
            List<VpnTarget> vpnTargetList = vpnInstance.getVpnTargets().getVpnTarget();

            if (vpnTargetList == null) {
                log.error("{} addBgpVrf: vpn target list is empty for vpn {} RD {}", LOGGING_PREFIX_ADD,
                        this.vpnName, primaryRd);
                return false;
            }
            // FIXME: separate out to somehow?
            final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName);
            lock.lock();
            try {
                fibManager.addVrfTable(primaryRd, null);
            } finally {
                lock.unlock();
            }
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
            log.error("{} onFailure: Job for vpnInstance: {} failed with exception:", LOGGING_PREFIX_ADD, vpnName,
                    throwable);
            vpnInterfaceManager.vpnInstanceFailed(vpnName);
        }
    }

    @Nullable
    protected VpnInstanceOpDataEntry getVpnInstanceOpData(String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        try {
            return SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    id).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error reading VPN instance data for " + rd, e);
        }
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private List<String> getDcGatewayTunnelInterfaceNameList() {
        List<String> tunnelInterfaceNameList = new ArrayList<>();
        try {
            InstanceIdentifier<DcGatewayIpList> dcGatewayIpListInstanceIdentifier = InstanceIdentifier
                    .create(DcGatewayIpList.class);
            Optional<DcGatewayIpList> dcGatewayIpListOptional = SingleTransactionDataBroker.syncReadOptional(
                    dataBroker, LogicalDatastoreType.CONFIGURATION, dcGatewayIpListInstanceIdentifier);
            if (!dcGatewayIpListOptional.isPresent()) {
                LOG.info("No DC gateways configured.");
                return tunnelInterfaceNameList;
            }
            List<DcGatewayIp> dcGatewayIps = dcGatewayIpListOptional.get().nonnullDcGatewayIp();
            InstanceIdentifier<ExternalTunnelList> externalTunnelListId = InstanceIdentifier
                    .create(ExternalTunnelList.class);
            Optional<ExternalTunnelList> externalTunnelListOptional = SingleTransactionDataBroker.syncReadOptional(
                    dataBroker, LogicalDatastoreType.OPERATIONAL, externalTunnelListId);
            if (externalTunnelListOptional.isPresent()) {
                List<ExternalTunnel> externalTunnels = externalTunnelListOptional.get().nonnullExternalTunnel();
                List<String> externalTunnelIpList = new ArrayList<>();
                for (ExternalTunnel externalTunnel: externalTunnels) {
                    externalTunnelIpList.add(externalTunnel.getDestinationDevice());
                }
                List<String> dcGatewayIpList = new ArrayList<>();
                for (DcGatewayIp dcGatewayIp: dcGatewayIps) {
                    dcGatewayIpList.add(dcGatewayIp.getIpAddress().getIpv4Address().toString());
                }
                // Find all externalTunnelIps present in dcGateWayIpList
                List<String> externalTunnelIpsInDcGatewayIpList = new ArrayList<>();
                for (String externalTunnelIp: externalTunnelIpList) {
                    for (String dcGateWayIp: dcGatewayIpList) {
                        if (externalTunnelIp.contentEquals(dcGateWayIp)) {
                            externalTunnelIpsInDcGatewayIpList.add(externalTunnelIp);
                        }
                    }
                }
                for (String externalTunnelIpsInDcGatewayIp: externalTunnelIpsInDcGatewayIpList) {
                    for (ExternalTunnel externalTunnel: externalTunnels) {
                        if (externalTunnel.getDestinationDevice().contentEquals(externalTunnelIpsInDcGatewayIp)) {
                            tunnelInterfaceNameList.add(externalTunnel.getTunnelInterfaceName());
                        }
                    }
                }

            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getDcGatewayTunnelInterfaceNameList: Failed to read data store");
        }
        return tunnelInterfaceNameList;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private String getFibFlowRef(Uint64 dpnId, short tableId, String vpnName, int priority) {
        return VpnConstants.FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + vpnName + NwConstants.FLOWID_SEPARATOR + priority;
    }
}

/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
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
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.LayerType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
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
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInstanceListener extends AsyncDataTreeChangeListenerBase<VpnInstance, VpnInstanceListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInstanceListener.class);
    private static final String LOGGING_PREFIX_ADD = "VPN-ADD:";
    private static final String LOGGING_PREFIX_DELETE = "VPN-REMOVE:";
    private final DataBroker dataBroker;
    private final IBgpManager bgpManager;
    private final IdManagerService idManager;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final IFibManager fibManager;
    private final VpnOpDataSyncer vpnOpDataNotifier;
    private final IMdsalApiManager mdsalManager;

    public VpnInstanceListener(final DataBroker dataBroker, final IBgpManager bgpManager,
        final IdManagerService idManager, final VpnInterfaceManager vpnInterfaceManager, final IFibManager fibManager,
        final VpnOpDataSyncer vpnOpDataSyncer, final IMdsalApiManager mdsalManager) {
        super(VpnInstance.class, VpnInstanceListener.class);
        this.dataBroker = dataBroker;
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.fibManager = fibManager;
        this.vpnOpDataNotifier = vpnOpDataSyncer;
        this.mdsalManager = mdsalManager;
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

    @Override
    protected void remove(InstanceIdentifier<VpnInstance> identifier, VpnInstance del) {
        LOG.trace("{} remove: VPN event key: {}, value: {}", LOGGING_PREFIX_DELETE, identifier, del);
        final String vpnName = del.getVpnInstanceName();
        Optional<VpnInstanceOpDataEntry> vpnOpValue;
        String primaryRd = VpnUtil.getPrimaryRd(del);

        //TODO(vpnteam): Entire code would need refactoring to listen only on the parent object - VPNInstance
        try {
            vpnOpValue = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd));
        } catch (ReadFailedException e) {
            LOG.error("{} remove: Exception when attempting to retrieve VpnInstanceOpDataEntry for VPN {}. ",
                    LOGGING_PREFIX_DELETE,  vpnName, e);
            return;
        }

        if (!vpnOpValue.isPresent()) {
            LOG.error("{} remove: Unable to retrieve VpnInstanceOpDataEntry for VPN {}. ", LOGGING_PREFIX_DELETE,
                    vpnName);
            return;
        } else {
            DataStoreJobCoordinator djc = DataStoreJobCoordinator.getInstance();
            djc.enqueueJob("VPN-" + vpnName, () -> {
                VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder().setVrfId(primaryRd)
                        .setVpnState(VpnInstanceOpDataEntry.VpnState.PendingDelete);
                InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd);
                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                writeTxn.merge(LogicalDatastoreType.OPERATIONAL, id, builder.build());

                LOG.info("{} call: Operational status set to PENDING_DELETE for vpn {} with rd {}",
                        LOGGING_PREFIX_DELETE, vpnName, primaryRd);
                return Collections.singletonList(writeTxn.submit());
            }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> identifier,
        VpnInstance original, VpnInstance update) {
        LOG.trace("VPN-UPDATE: update: VPN event key: {}, value: {}. Ignoring", identifier, update);
        String vpnName = update.getVpnInstanceName();
        vpnInterfaceManager.updateVpnInterfacesForUnProcessAdjancencies(dataBroker,vpnName);
    }

    @Override
    protected void add(final InstanceIdentifier<VpnInstance> identifier, final VpnInstance value) {
        LOG.trace("{} add: Add VPN event key: {}, value: {}", LOGGING_PREFIX_ADD, identifier, value);
        final String vpnName = value.getVpnInstanceName();

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPN-" + vpnName,
            new AddVpnInstanceWorker(idManager, vpnInterfaceManager, dataBroker, value));
    }

    private class AddVpnInstanceWorker implements Callable<List<ListenableFuture<Void>>> {
        private final Logger log = LoggerFactory.getLogger(AddVpnInstanceWorker.class);
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
            WriteTransaction writeConfigTxn = broker.newWriteOnlyTransaction();
            WriteTransaction writeOperTxn = broker.newWriteOnlyTransaction();
            addVpnInstance(vpnInstance, writeConfigTxn, writeOperTxn);

            try {
                writeOperTxn.submit().get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("{} call: Error creating vpn {} ", LOGGING_PREFIX_ADD, vpnInstance.getVpnInstanceName());
                throw new RuntimeException(e.getMessage());
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeConfigTxn.submit());
            ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
            Futures.addCallback(listenableFuture,
                    new PostAddVpnInstanceWorker(vpnInstance , vpnInstance.getVpnInstanceName()));
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
            LOG.error("{} addVpnInstance: Unable to fetch label from Id Manager. Bailing out of adding operational"
                    + " data for Vpn Instance {}", LOGGING_PREFIX_ADD, value.getVpnInstanceName());
            return;
        }
        LOG.info("{} addVpnInstance: VPN Id {} generated for VpnInstanceName {}", LOGGING_PREFIX_ADD, vpnId,
                vpnInstanceName);
        String primaryRd = VpnUtil.getPrimaryRd(value);
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
            vpnInstanceToVpnId = VpnUtil.getVpnInstanceToVpnId(vpnInstanceName, vpnId, primaryRd);

        if (writeConfigTxn != null) {
            writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION,
                VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnInstanceName),
                vpnInstanceToVpnId, WriteTransaction.CREATE_MISSING_PARENTS);
        } else {
            TransactionUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnInstanceName),
                vpnInstanceToVpnId, TransactionUtil.DEFAULT_CALLBACK);
        }

        VpnIds vpnIdToVpnInstance = VpnUtil.getVpnIdToVpnInstance(vpnId, value.getVpnInstanceName(),
            primaryRd, VpnUtil.isBgpVpn(vpnInstanceName, primaryRd));

        if (writeConfigTxn != null) {
            writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION,
                VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId),
                vpnIdToVpnInstance, WriteTransaction.CREATE_MISSING_PARENTS);
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
                        .setVpnInstanceName(vpnInstanceName).setVpnState(VpnInstanceOpDataEntry.VpnState.Created);
        if (VpnUtil.isBgpVpn(vpnInstanceName, primaryRd)) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn
                .instance.op.data.entry.vpntargets.VpnTarget> opVpnTargetList = new ArrayList<>();
            if (value.getL3vni() != null) {
                builder.setL3vni(value.getL3vni());
            }
            if (value.getType() == VpnInstance.Type.L2) {
                builder.setType(VpnInstanceOpDataEntry.Type.L2);
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

            List<String> rds = config.getRouteDistinguisher();
            builder.setRd(rds);
        }
        if (writeOperTxn != null) {
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL,
                VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd),
                builder.build(), true);
        } else {
            TransactionUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd),
                builder.build(), TransactionUtil.DEFAULT_CALLBACK);
        }
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
            VpnAfConfig config = vpnInstance.getIpv4Family();
            List<String> rd = config.getRouteDistinguisher();
            if (rd == null || addBgpVrf(voids)) {
                notifyTask();
                vpnInterfaceManager.vpnInstanceIsReady(vpnName);
            }
            log.info("{} onSuccess: Vpn Instance Op Data addition for {} successful.", LOGGING_PREFIX_ADD, vpnName);
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(dataBroker,
                    VpnUtil.getPrimaryRd(vpnInstance));

            // bind service on each tunnel interface
            //TODO (KIRAN): Add a new listener to handle creation of new DC-GW binding and deletion of existing DC-GW.
            if (VpnUtil.isL3VpnOverVxLan(vpnInstance.getL3vni())) { //Handled for L3VPN Over VxLAN
                for (String tunnelInterfaceName: getDcGatewayTunnelInterfaceNameList()) {
                    VpnUtil.bindService(vpnInstance.getVpnInstanceName(), tunnelInterfaceName, dataBroker,
                            true/*isTunnelInterface*/);
                }

                // install flow
                List<MatchInfo> mkMatches = new ArrayList<>();
                mkMatches.add(new MatchTunnelId(BigInteger.valueOf(vpnInstance.getL3vni())));

                List<InstructionInfo> instructions =
                        Arrays.asList(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(vpnInstanceOpDataEntry
                                .getVpnId()), MetaDataUtil.METADATA_MASK_VRFID),
                                new InstructionGotoTable(NwConstants.L3_GW_MAC_TABLE));

                for (BigInteger dpnId: NWUtil.getOperativeDPNs(dataBroker)) {
                    String flowRef = getFibFlowRef(dpnId, NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                            vpnName, VpnConstants.DEFAULT_FLOW_PRIORITY);
                    FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId,
                            NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE, flowRef, VpnConstants.DEFAULT_FLOW_PRIORITY,
                            "VxLAN VPN Tunnel Bind Service", 0, 0, NwConstants.COOKIE_VM_FIB_TABLE,
                            mkMatches, instructions);
                    mdsalManager.installFlow(dpnId, flowEntity);
                }

                ///////////////////////
            }
        }

        // TODO Clean up the exception handling
        @SuppressWarnings("checkstyle:IllegalCatch")
        private boolean addBgpVrf(List<Void> voids) {
            VpnAfConfig config = vpnInstance.getIpv4Family();
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
                log.error("{} addBgpVrf: vpn target list is empty, cannot add BGP VPN {} VRF {}", LOGGING_PREFIX_ADD,
                        this.vpnName, primaryRd);
                return false;
            }
            //Advertise all the rds and check if primary Rd advertisement fails
            long primaryRdAddFailed = rds.parallelStream().filter(rd -> {
                try {
                    LayerType layerType = vpnInstance.getType() == VpnInstance.Type.L2 ? LayerType.LAYER2 :
                            LayerType.LAYER3;
                    bgpManager.addVrf(rd, irtList, ertList, layerType);
                } catch (Exception e) {
                    log.error("{} addBgpVrf: Exception when adding VRF to BGP for vpn {} rd {}", LOGGING_PREFIX_ADD,
                            vpnName, rd, e);
                    return rd.equals(primaryRd);
                }
                return false;
            }).count();
            if (primaryRdAddFailed == 1) {
                return false;
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
            log.error("{} onFailure: Job for vpnInstance: {} failed with exception: {}", LOGGING_PREFIX_ADD, vpnName,
                    throwable);
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
            LOG.trace("isVPNConfigured: No VPNs configured.");
            return false;
        }
        LOG.trace("isVPNConfigured: VPNs are configured on the system.");
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

    private List<String> getDcGatewayTunnelInterfaceNameList() {
        List<String> tunnelInterfaceNameList = new ArrayList<>();

        InstanceIdentifier<DcGatewayIpList> dcGatewayIpListInstanceIdentifier = InstanceIdentifier
                .create(DcGatewayIpList.class);
        Optional<DcGatewayIpList> dcGatewayIpListOptional = VpnUtil.read(dataBroker,
                LogicalDatastoreType.CONFIGURATION, dcGatewayIpListInstanceIdentifier);
        if (!dcGatewayIpListOptional.isPresent()) {
            LOG.info("No DC gateways configured.");
            return tunnelInterfaceNameList;
        }
        List<DcGatewayIp> dcGatewayIps = dcGatewayIpListOptional.get().getDcGatewayIp();

        InstanceIdentifier<ExternalTunnelList> externalTunnelListId = InstanceIdentifier
                .create(ExternalTunnelList.class);

        Optional<ExternalTunnelList> externalTunnelListOptional = VpnUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, externalTunnelListId);
        if (externalTunnelListOptional.isPresent()) {
            List<ExternalTunnel> externalTunnels = externalTunnelListOptional.get().getExternalTunnel();

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
        return tunnelInterfaceNameList;
    }

    private String getFibFlowRef(BigInteger dpnId, short tableId, String vpnName, int priority) {
        return VpnConstants.FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + vpnName + NwConstants.FLOWID_SEPARATOR + priority;
    }
}

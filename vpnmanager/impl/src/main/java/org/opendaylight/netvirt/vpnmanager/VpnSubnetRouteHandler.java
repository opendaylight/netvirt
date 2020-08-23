/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnOpDataSyncer.VpnOpDataType;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.TaskState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnSubnetRouteHandler {
    private static final Logger LOG = LoggerFactory.getLogger(VpnSubnetRouteHandler.class);
    private static final String LOGGING_PREFIX = "SUBNETROUTE:";
    private static final String VPN_EVENT_SOURCE_SUBNET_ROUTE = "vpnSubnetRouteEvent";
    private final DataBroker dataBroker;
    private final SubnetOpDpnManager subOpDpnManager;
    private final IBgpManager bgpManager;
    private final VpnOpDataSyncer vpnOpDataSyncer;
    private final VpnNodeListener vpnNodeListener;
    private final IFibManager fibManager;
    private final VpnUtil vpnUtil;

    @Inject
    public VpnSubnetRouteHandler(final DataBroker dataBroker, final SubnetOpDpnManager subnetOpDpnManager,
                                 final IBgpManager bgpManager, final VpnOpDataSyncer vpnOpDataSyncer,
                                 final VpnNodeListener vpnNodeListener,
                                 final IFibManager fibManager, VpnUtil vpnUtil) {
        this.dataBroker = dataBroker;
        this.subOpDpnManager = subnetOpDpnManager;
        this.bgpManager = bgpManager;
        this.vpnOpDataSyncer = vpnOpDataSyncer;
        this.vpnNodeListener = vpnNodeListener;
        this.fibManager = fibManager;
        this.vpnUtil = vpnUtil;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onSubnetAddedToVpn(Subnetmap subnetmap, boolean isBgpVpn, Long elanTag) {
        Uuid subnetId = subnetmap.getId();
        String subnetIp = subnetmap.getSubnetIp();
        SubnetOpDataEntry subOpEntry = null;
        SubnetOpDataEntryBuilder subOpBuilder = null;
        InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = null;
        Optional<SubnetOpDataEntry> optionalSubs = null;
        Uint32 label;

        Preconditions.checkNotNull(subnetId,
                LOGGING_PREFIX + " onSubnetAddedToVpn: SubnetId cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp,
                LOGGING_PREFIX + " onSubnetAddedToVpn: SubnetPrefix cannot be null or empty!");
        Preconditions.checkNotNull(elanTag,
                LOGGING_PREFIX + " onSubnetAddedToVpn: ElanTag cannot be null or empty!");

        if (subnetmap.getVpnId() == null) {
            LOG.error("onSubnetAddedToVpn: VpnId {} for subnet {} not found, bailing out", subnetmap.getVpnId(),
                    subnetId);
            return;
        }
        String vpnName = subnetmap.getVpnId().getValue();
        Uint32 vpnId = waitAndGetVpnIdIfInvalid(vpnName);
        if (VpnConstants.INVALID_ID.equals(vpnId)) {
            LOG.error(
                    "{} onSubnetAddedToVpn: VpnInstance to VPNId mapping not yet available for VpnName {} "
                            + "processing subnet {} with IP {}, bailing out now.",
                    LOGGING_PREFIX, vpnName, subnetId, subnetIp);
            return;
        }

        String primaryRd = vpnUtil.getPrimaryRd(vpnName);
        VpnInstanceOpDataEntry vpnInstanceOpData = waitAndGetVpnInstanceOpDataIfNull(vpnName, primaryRd);
        if (vpnInstanceOpData == null) {
            LOG.error(
                    "{} onSubnetAddedToVpn: VpnInstanceOpData not yet available for VpnName {} "
                            + "processing subnet {} with IP {}, bailing out now.",
                    LOGGING_PREFIX, vpnName, subnetId, subnetIp);
            return;
        }
        LOG.info("{} onSubnetAddedToVpn: Subnet {} with IP {} being added to vpn {}", LOGGING_PREFIX,
                subnetId.getValue(), subnetIp, vpnName);
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            vpnUtil.lockSubnet(subnetId.getValue());
            if (isBgpVpn) {
                InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
                        .child(Networks.class, new NetworksKey(subnetmap.getNetworkId())).build();
                Optional<Networks> optionalNets = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, netsIdentifier);
                if (optionalNets.isPresent()) {
                    LOG.info("{} onSubnetAddedToVpn: subnet {} with IP {} is an external subnet on external "
                                    + "network {}, so ignoring this for SubnetRoute on vpn {}", LOGGING_PREFIX,
                            subnetId.getValue(), subnetIp, subnetmap.getNetworkId().getValue(), vpnName);
                    return;
                }
            }
            //Create and add SubnetOpDataEntry object for this subnet to the SubnetOpData container
            subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                    new SubnetOpDataEntryKey(subnetId)).build();
            optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
            if (optionalSubs.isPresent()) {
                LOG.error("{} onSubnetAddedToVpn: SubnetOpDataEntry for subnet {} with ip {} and vpn {} already"
                        + " detected to be present", LOGGING_PREFIX, subnetId.getValue(), subnetIp, vpnName);
                return;
            }
            LOG.debug("{} onSubnetAddedToVpn: Creating new SubnetOpDataEntry node for subnet {} subnetIp {} "
                    + "vpn {}", LOGGING_PREFIX, subnetId.getValue(), subnetIp, vpnName);
            subOpBuilder = new SubnetOpDataEntryBuilder().withKey(new SubnetOpDataEntryKey(subnetId));
            subOpBuilder.setSubnetId(subnetId);
            subOpBuilder.setSubnetCidr(subnetIp);

            if (isBgpVpn && !VpnUtil.isBgpVpn(vpnName, primaryRd)) {
                LOG.error("{} onSubnetAddedToVpn: The VPN Instance name {} does not have RD. Bailing out for"
                        + " subnet {} subnetIp {} ", LOGGING_PREFIX, vpnName, subnetId.getValue(), subnetIp);
                return;
            }
            //Allocate MPLS label for subnet-route at the time SubnetOpDataEntry creation
            label = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME,
                    VpnUtil.getNextHopLabelKey(primaryRd, subnetIp));
            if (label == VpnConstants.INVALID_ID) {
                LOG.error("onSubnetAddedToVpn: Unable to retrieve label for rd {}, subnetIp {}",
                        primaryRd, subnetIp);
                return;
            }
            subOpBuilder.setVrfId(primaryRd);
            subOpBuilder.setVpnName(vpnName);
            subOpBuilder.setSubnetToDpn(new ArrayList<>());
            subOpBuilder.setRouteAdvState(TaskState.Idle);
            subOpBuilder.setElanTag(elanTag);
            subOpBuilder.setLabel(label);
            Long l3Vni = vpnInstanceOpData.getL3vni() != null ? vpnInstanceOpData.getL3vni().toJava() : 0L;
            subOpBuilder.setL3vni(l3Vni);
            subOpEntry = subOpBuilder.build();
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                    subOpEntry, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
            LOG.info("{} onSubnetAddedToVpn: Added subnetopdataentry to OP Datastore for subnet {}", LOGGING_PREFIX,
                    subnetId.getValue());
        } catch (TransactionCommitFailedException e) {
            LOG.error("{} Creation of SubnetOpDataEntry for subnet {} failed ", LOGGING_PREFIX,
                    subnetId.getValue(), e);
            // The second part of this method depends on subnetmap being non-null so fail fast here.
            return;
        } catch (RuntimeException e) { //TODO: Avoid this
            LOG.error("{} onSubnetAddedToVpn: Unable to handle subnet {} with ip {} added to vpn {}", LOGGING_PREFIX,
                    subnetId.getValue(), subnetIp, vpnName, e);
            return;
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} onSubnetAddedToVpn: Failed to read data store for subnet {} ip {} vpn {}", LOGGING_PREFIX,
                    subnetId, subnetIp, vpnName);
            return;
        } finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
        try {
            //In second critical section , Port-Op-Data will be updated.
            vpnUtil.lockSubnet(subnetId.getValue());
            Uint64 dpnId = null;
            SubnetToDpn subDpn = null;
            Map<Uint64, SubnetToDpn> subDpnMap = new HashMap<>();

            optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
            subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get())
                    .withKey(new SubnetOpDataEntryKey(subnetId));
            List<Uuid> portList = subnetmap.getPortList();
            if (portList != null) {
                for (Uuid port : portList) {
                    Interface intfState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker,port.getValue());
                    if (intfState != null) {
                        try {
                            dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                        } catch (Exception e) {
                            LOG.error("{} onSubnetAddedToVpn: Unable to obtain dpnId for interface {},"
                                            + " subnetroute inclusion for this interface for subnet {} subnetIp {} "
                                            + "vpn {} failed with exception", LOGGING_PREFIX, port.getValue(),
                                    subnetId.getValue(), subnetIp, vpnName, e);
                            continue;
                        }
                        if (dpnId.equals(Uint64.ZERO)) {
                            LOG.error("{} onSubnetAddedToVpn: Port {} is not assigned DPN yet,"
                                            + " ignoring subnet {} subnetIP {} vpn {}", LOGGING_PREFIX, port.getValue(),
                                    subnetId.getValue(), subnetIp, vpnName);
                            continue;
                        }
                        subOpDpnManager.addPortOpDataEntry(port.getValue(), subnetId, dpnId);
                        if (intfState.getOperStatus() != OperStatus.Up) {
                            LOG.error("{} onSubnetAddedToVpn: Port {} is not UP yet, ignoring subnet {}"
                                            + " subnetIp {} vpn {}", LOGGING_PREFIX, port.getValue(),
                                    subnetId.getValue(), subnetIp, vpnName);
                            continue;
                        }
                        subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, port.getValue());
                        if (intfState.getOperStatus() == OperStatus.Up) {
                            // port is UP
                            subDpnMap.put(dpnId, subDpn);
                        }
                    } else {
                        subOpDpnManager.addPortOpDataEntry(port.getValue(), subnetId, null);
                    }
                }
                if (subDpnMap.size() > 0) {
                    subOpBuilder.setSubnetToDpn(new ArrayList<>(subDpnMap.values()));
                }
            }
            electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId,
                    subnetmap.getNetworkId(), isBgpVpn, label);
            subOpEntry = subOpBuilder.build();
            SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                    subOpEntry, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
            LOG.info("{} onSubnetAddedToVpn: Added PortOpDataEntry and VpnInterfaces to SubnetOpData"
                            + " for subnet {} subnetIp {} vpn {} TaskState {} lastTaskState {}", LOGGING_PREFIX,
                    subnetId.getValue(), subnetIp, vpnName, subOpEntry.getRouteAdvState(),
                    subOpEntry.getLastAdvState());
        } catch (RuntimeException e) {
            LOG.error("{} onSubnetAddedToVpn: Unable to handle subnet {} with ip {} added to vpn {}", LOGGING_PREFIX,
                    subnetId.getValue(), subnetIp, vpnName, e);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} onSubnetAddedToVpn: Failed to read data store for subnet {} ip {} vpn {}", LOGGING_PREFIX,
                    subnetId, subnetIp, vpnName);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("{} onSubnetAddedToVpn: Creation of SubnetOpDataEntry for subnet {} subnetIp {} vpn {} failed",
                    LOGGING_PREFIX, subnetId.getValue(), subnetIp, vpnName, ex);
        } finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
    }

    private Uint32 waitAndGetVpnIdIfInvalid(String vpnName) {
        Uint32 vpnId = vpnUtil.getVpnId(vpnName);
        if (VpnConstants.INVALID_ID.equals(vpnId)) {
            LOG.debug("VpnId is invalid, waiting to fetch again: vpnName={}, vpnId={}", vpnName, vpnId);
            vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataType.vpnInstanceToId, vpnName,
                    VpnConstants.PER_VPN_INSTANCE_MAX_WAIT_TIME_IN_MILLISECONDS);
            vpnId = vpnUtil.getVpnId(vpnName);
        }
        return vpnId;
    }

    private VpnInstanceOpDataEntry waitAndGetVpnInstanceOpDataIfNull(String vpnName, String primaryRd) {
        VpnInstanceOpDataEntry vpnInstanceOpData = vpnUtil.getVpnInstanceOpData(primaryRd);
        if (vpnInstanceOpData == null) {
            LOG.debug("vpnInstanceOpData is null, waiting to fetch again: vpnName={}", vpnName);
            vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataType.vpnOpData, vpnName,
                    VpnConstants.PER_VPN_INSTANCE_OPDATA_MAX_WAIT_TIME_IN_MILLISECONDS);
            vpnInstanceOpData = vpnUtil.getVpnInstanceOpData(primaryRd);
        }
        return vpnInstanceOpData;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onSubnetDeletedFromVpn(Subnetmap subnetmap, boolean isBgpVpn) {
        Uuid subnetId = subnetmap.getId();
        LOG.info("{} onSubnetDeletedFromVpn: Subnet {} with ip {} being removed from vpnId {}", LOGGING_PREFIX,
                subnetId, subnetmap.getSubnetIp(), subnetmap.getVpnId());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            vpnUtil.lockSubnet(subnetId.getValue());
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                            new SubnetOpDataEntryKey(subnetId)).build();
            Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
            if (!optionalSubs.isPresent()) {
                LOG.error("{} onSubnetDeletedFromVpn: SubnetOpDataEntry for subnet {} subnetIp {} vpn {}"
                                + " not available in datastore", LOGGING_PREFIX, subnetId.getValue(),
                        subnetId.getValue(), subnetmap.getVpnId());
                return;
            }
            LOG.trace("{} onSubnetDeletedFromVpn: Removing the SubnetOpDataEntry node for subnet {} subnetIp {}"
                            + " vpnName {} rd {} TaskState {}", LOGGING_PREFIX, subnetId.getValue(),
                    optionalSubs.get().getSubnetCidr(), optionalSubs.get().getVpnName(),
                    optionalSubs.get().getVrfId(), optionalSubs.get().getRouteAdvState());
            /* If subnet is deleted (or if its removed from VPN), the ports that are DOWN on that subnet
             * will continue to be stale in portOpData DS, as subDpnList used for portOpData removal will
             * contain only ports that are UP. So here we explicitly cleanup the ports of the subnet by
             * going through the list of ports on the subnet
             */
            List<Uuid> portList = subnetmap.getPortList();
            if (portList != null) {
                for (Uuid port : portList) {
                    InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
                            InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                                    new PortOpDataEntryKey(port.getValue())).build();
                    LOG.trace("{} onSubnetDeletedFromVpn: Deleting portOpData entry for port {}"
                                    + " from subnet {} subnetIp {} vpnName {} TaskState {}",
                            LOGGING_PREFIX, port.getValue(), subnetId.getValue(),
                            optionalSubs.get().getSubnetCidr(), optionalSubs.get().getVpnName(),
                            optionalSubs.get().getRouteAdvState());
                    SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            portOpIdentifier, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
                }
            }

            SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
            String rd = subOpBuilder.getVrfId();
            String subnetIp = subOpBuilder.getSubnetCidr();
            String vpnName = subOpBuilder.getVpnName();
            //Withdraw the routes for all the interfaces on this subnet
            //Remove subnet route entry from FIB
            deleteSubnetRouteFromFib(rd, subnetIp, vpnName, isBgpVpn);
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                    VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
            int releasedLabel = vpnUtil.releaseId(VpnConstants.VPN_IDPOOL_NAME,
                    VpnUtil.getNextHopLabelKey(rd, subnetIp));
            if (releasedLabel == VpnConstants.INVALID_LABEL) {
                LOG.error("onSubnetDeletedFromVpn: Unable to release label for key {}",
                        VpnUtil.getNextHopLabelKey(rd, subnetIp));
            }
            LOG.info("{} onSubnetDeletedFromVpn: Removed subnetopdataentry successfully from Datastore"
                            + " for subnet {} subnetIp {} vpnName {} rd {}", LOGGING_PREFIX, subnetId.getValue(),
                    subnetIp, vpnName, rd);
        } catch (RuntimeException e) { //TODO: Avoid this
            LOG.error("{} onSubnetDeletedFromVpn: Unable to handle subnet {} with Ip {} removed from vpn {}",
                    LOGGING_PREFIX, subnetId.getValue(), subnetmap.getSubnetIp(), subnetmap.getVpnId(), e);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("{} onSubnetDeletedFromVpn: Removal of SubnetOpDataEntry for subnet {} subnetIp {}"
                            + " vpnId {} failed", LOGGING_PREFIX, subnetId.getValue(), subnetmap.getSubnetIp(),
                    subnetmap.getVpnId(), ex);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} onSubnetDeletedFromVpn: Failed to read data store for subnet {} ip {} vpn {}",
                    LOGGING_PREFIX, subnetId, subnetmap.getSubnetIp(), subnetmap.getVpnId());
        } finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
    }

    public void onSubnetUpdatedInVpn(Subnetmap subnetmap, Long elanTag) {
        Uuid subnetId = subnetmap.getId();
        String vpnName = subnetmap.getVpnId().getValue();
        String subnetIp = subnetmap.getSubnetIp();

        Preconditions.checkNotNull(subnetId,
                LOGGING_PREFIX + " onSubnetUpdatedInVpn: SubnetId cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp,
                LOGGING_PREFIX + " onSubnetUpdatedInVpn: SubnetPrefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, LOGGING_PREFIX + " onSubnetUpdatedInVpn: VpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, LOGGING_PREFIX + " onSubnetUpdatedInVpn: ElanTag cannot be null or empty!");
        try {
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                            new SubnetOpDataEntryKey(subnetId)).build();
            Optional<SubnetOpDataEntry> optionalSubs =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            subOpIdentifier);
            if (optionalSubs.isPresent()) {
                onSubnetDeletedFromVpn(subnetmap, true);
            } else {
                onSubnetAddedToVpn(subnetmap, true, elanTag);
            }
            LOG.info("{} onSubnetUpdatedInVpn: subnet {} with Ip {} updated successfully for vpn {}", LOGGING_PREFIX,
                    subnetId.getValue(), subnetIp, vpnName);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("onSubnetUpdatedInVpn: Failed to read data store for subnet{} ip {} elanTag {} vpn {}",subnetId,
                    subnetIp, elanTag, vpnName);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onPortAddedToSubnet(Subnetmap subnetmap, Uuid portId) {
        Uuid subnetId = subnetmap.getId();
        LOG.info("{} onPortAddedToSubnet: Port {} being added to subnet {}", LOGGING_PREFIX, portId.getValue(),
                subnetId.getValue());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            vpnUtil.lockSubnet(subnetId.getValue());
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                            new SubnetOpDataEntryKey(subnetId)).build();

            Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
            if (!optionalSubs.isPresent()) {
                LOG.info("{} onPortAddedToSubnet: Port {} is part of a subnet {} that is not in VPN, ignoring",
                        LOGGING_PREFIX, portId.getValue(), subnetId.getValue());
                return;
            }
            String vpnName = optionalSubs.get().getVpnName();
            String subnetIp = optionalSubs.get().getSubnetCidr();
            String rd = optionalSubs.get().getVrfId();
            String routeAdvState = optionalSubs.get().getRouteAdvState().toString();
            Uint32 label = optionalSubs.get().getLabel();
            LOG.info("{} onPortAddedToSubnet: Port {} being added to subnet {} subnetIp {} vpnName {} rd {} "
                            + "TaskState {}", LOGGING_PREFIX, portId.getValue(), subnetId.getValue(), subnetIp,
                    vpnName, rd, routeAdvState);
            subOpDpnManager.addPortOpDataEntry(portId.getValue(), subnetId, null);
            Interface intfState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker,portId.getValue());
            if (intfState == null) {
                // Interface State not yet available
                return;
            }
            final Uint64 dpnId;
            try {
                dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
            } catch (Exception e) {
                LOG.error("{} onPortAddedToSubnet: Unable to obtain dpnId for interface {}. subnetroute inclusion"
                                + " for this interface failed for subnet {} subnetIp {} vpn {} rd {}",
                        LOGGING_PREFIX, portId.getValue(), subnetId.getValue(), subnetIp, vpnName, rd, e);
                return;
            }
            if (dpnId.equals(Uint64.ZERO)) {
                LOG.error("{} onPortAddedToSubnet: Port {} is not assigned DPN yet, ignoring subnetRoute "
                                + "inclusion for the interface into subnet {} subnetIp {} vpnName {} rd {}",
                        LOGGING_PREFIX, portId.getValue(), subnetId.getValue(), subnetIp, vpnName, rd);
                return;
            }
            subOpDpnManager.addPortOpDataEntry(portId.getValue(), subnetId, dpnId);
            if (intfState.getOperStatus() != OperStatus.Up) {
                LOG.error("{} onPortAddedToSubnet: Port {} is not UP yet, ignoring subnetRoute inclusion for "
                                + "the interface into subnet {} subnetIp {} vpnName {} rd {}", LOGGING_PREFIX,
                        portId.getValue(), subnetId.getValue(), subnetIp, vpnName, rd);
                return;
            }
            LOG.debug("{} onPortAddedToSubnet: Port {} added. Updating the SubnetOpDataEntry node for subnet {} "
                            + "subnetIp {} vpnName {} rd {} TaskState {}", LOGGING_PREFIX, portId.getValue(),
                    subnetId.getValue(), subnetIp, vpnName, rd, routeAdvState);
            SubnetToDpn subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, portId.getValue());
            if (subDpn == null) {
                LOG.error("{} onPortAddedToSubnet: subnet-to-dpn list is null for subnetId {}. portId {}, "
                                + "vpnName {}, rd {}, subnetIp {}", LOGGING_PREFIX, subnetId.getValue(),
                        portId.getValue(), vpnName, rd, subnetIp);
                return;
            }
            SubnetOpDataEntry subnetOpDataEntry = optionalSubs.get();
            SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subnetOpDataEntry);
            List<SubnetToDpn> subnetToDpnList = concat(new ArrayList<SubnetToDpn>(subnetOpDataEntry
                    .nonnullSubnetToDpn().values()), subDpn);
            subOpBuilder.setSubnetToDpn(getSubnetToDpnMap(subnetToDpnList));
            if (subOpBuilder.getRouteAdvState() != TaskState.Advertised) {
                if (subOpBuilder.getNhDpnId() == null) {
                    // No nexthop selected yet, elect one now
                    electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId,
                            subnetmap.getNetworkId(), true, label);
                } else if (!VpnUtil.isExternalSubnetVpn(subnetOpDataEntry.getVpnName(), subnetId.getValue())) {
                    // Already nexthop has been selected, only publishing to bgp required, so publish to bgp
                    getNexthopTepAndPublishRoute(subOpBuilder, subnetId);
                }
            }
            SubnetOpDataEntry subOpEntry = subOpBuilder.build();
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                    subOpEntry, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
            LOG.info("{} onPortAddedToSubnet: Updated subnetopdataentry to OP Datastore for port {} subnet {}"
                            + " subnetIp {} vpnName {} rd {} TaskState {} lastTaskState {}", LOGGING_PREFIX,
                    portId.getValue(), subnetId.getValue(), subOpEntry.getSubnetCidr(), subOpEntry.getVpnName(),
                    subOpBuilder.getVrfId(), subOpEntry.getRouteAdvState(), subOpEntry.getLastAdvState());
        } catch (RuntimeException e) { //TODO: Avoid this
            LOG.error("{} onPortAddedToSubnet: Unable to handle port {} added to subnet {}", LOGGING_PREFIX,
                    portId.getValue(), subnetId.getValue(), e);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} onPortAddedToSubnet: Failed to read data store for port {} subnet {}", LOGGING_PREFIX,
                    portId, subnetId);
        } catch (TransactionCommitFailedException e) {
            LOG.error("{} onPortAddedToSubnet: Updation of subnetOpEntry for port {} subnet {} falied",
                    LOGGING_PREFIX, portId.getValue(), subnetId.getValue(), e);
        } finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onPortRemovedFromSubnet(Subnetmap subnetmap, Uuid portId) {
        Uuid subnetId = subnetmap.getId();
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            vpnUtil.lockSubnet(subnetId.getValue());
            PortOpDataEntry portOpEntry = subOpDpnManager.removePortOpDataEntry(portId.getValue(),
                    subnetmap.getId());
            if (portOpEntry == null) {
                return;
            }
            Uint64 dpnId = portOpEntry.getDpnId();
            if (dpnId == null) {
                LOG.error("{} onPortRemovedFromSubnet:  Port {} does not have a DPNId associated,"
                                + " ignoring removal from subnet {}", LOGGING_PREFIX, portId.getValue(),
                        subnetId.getValue());
                return;
            }
            boolean last = subOpDpnManager.removeInterfaceFromDpn(subnetId, dpnId, portId.getValue());
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                            new SubnetOpDataEntryKey(subnetId)).build();
            Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
            if (!optionalSubs.isPresent()) {
                LOG.info("{} onPortRemovedFromSubnet: Port {} is part of a subnet {} that is not in VPN,"
                        + " ignoring", LOGGING_PREFIX, portId.getValue(), subnetId.getValue());
                return;
            }
            LOG.info("{} onPortRemovedFromSubnet: Port {} being removed. Updating the SubnetOpDataEntry"
                            + " for subnet {} subnetIp {} vpnName {} rd {} TaskState {} lastTaskState {}",
                    LOGGING_PREFIX, portId.getValue(), subnetId.getValue(), optionalSubs.get().getSubnetCidr(),
                    optionalSubs.get().getVpnName(), optionalSubs.get().getVrfId(),
                    optionalSubs.get().getRouteAdvState(), optionalSubs.get().getLastAdvState());
            SubnetOpDataEntry subnetOpDataEntry = optionalSubs.get();
            SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subnetOpDataEntry);
            Uint64 nhDpnId = subOpBuilder.getNhDpnId();
            if (nhDpnId != null && nhDpnId.equals(dpnId)) {
                // select another NhDpnId
                if (last) {
                    LOG.debug("{} onPortRemovedFromSubnet: Last port {} being removed from subnet {} subnetIp {}"
                                    + " vpnName {} rd {}", LOGGING_PREFIX, portId.getValue(), subnetId.getValue(),
                            subOpBuilder.getSubnetCidr(), subOpBuilder.getVpnName(), subOpBuilder.getVrfId());
                    // last port on this DPN, so we need to elect the new NHDpnId
                    electNewDpnForSubnetRoute(subOpBuilder, nhDpnId, subnetId, subnetmap.getNetworkId(),
                            !VpnUtil.isExternalSubnetVpn(subnetOpDataEntry.getVpnName(), subnetId.getValue()),
                            subOpBuilder.getLabel());
                    SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            subOpIdentifier, subOpBuilder.build(), VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
                    LOG.info("{} onPortRemovedFromSubnet: Updated subnetopdataentry to OP Datastore"
                                    + " removing port {} from subnet {} subnetIp {} vpnName {} rd {}", LOGGING_PREFIX,
                            portId.getValue(), subnetId.getValue(), subOpBuilder.getSubnetCidr(),
                            subOpBuilder.getVpnName(), subOpBuilder.getVrfId());
                }
            }
        } catch (RuntimeException e) {
            LOG.error("{} onPortRemovedFromSubnet: Unable to handle port {} removed from subnet {}", LOGGING_PREFIX,
                    portId.getValue(), subnetId.getValue(), e);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} onPortRemovedFromSubnet: Failed to read data store for port {} subnet {}", LOGGING_PREFIX,
                    portId, subnetId);
        } catch (TransactionCommitFailedException e) {
            LOG.error("{} onPortRemovedFromSubnet: Removal of portOp for {} from subnet {} failed", LOGGING_PREFIX,
                    portId.getValue(), subnetId.getValue(), e);
        } finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onInterfaceUp(Uint64 dpnId, String intfName, Uuid subnetId) {
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        SubnetToDpn subDpn = null;
        if (dpnId == null || Objects.equals(dpnId, Uint64.ZERO)) {
            LOG.error("{} onInterfaceUp: Unable to determine the DPNID for port {} on subnet {}", LOGGING_PREFIX,
                    intfName, subnetId.getValue());
            return;
        }
        try {
            vpnUtil.lockSubnet(subnetId.getValue());
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                            new SubnetOpDataEntryKey(subnetId)).build();
            Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
            if (!optionalSubs.isPresent()) {
                LOG.trace("{} onInterfaceUp: SubnetOpDataEntry for subnet {} is not available."
                        + " Ignoring interfaceUp for port{}", LOGGING_PREFIX, subnetId.getValue(), intfName);
                return;
            }
            subOpDpnManager.addPortOpDataEntry(intfName, subnetId, dpnId);
            subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, intfName);
            if (subDpn == null) {
                return;
            }
            SubnetOpDataEntry subnetOpDataEntry = optionalSubs.get();
            SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subnetOpDataEntry);
            LOG.info("{} onInterfaceUp: Updating the SubnetOpDataEntry node for subnet {} subnetIp {} vpn {}"
                            + " rd {} TaskState {} lastTaskState {}" , LOGGING_PREFIX, subnetId.getValue(),
                    subOpBuilder.getSubnetCidr(), subOpBuilder.getVpnName(), subOpBuilder.getVrfId(),
                    subOpBuilder.getRouteAdvState(), subOpBuilder.getLastAdvState());
            boolean isExternalSubnetVpn = VpnUtil.isExternalSubnetVpn(subnetOpDataEntry.getVpnName(),
                    subnetId.getValue());
            List<SubnetToDpn> subnetToDpnList = concat(new ArrayList<SubnetToDpn>(subnetOpDataEntry
                    .nonnullSubnetToDpn().values()), subDpn);
            subOpBuilder.setSubnetToDpn(getSubnetToDpnMap(subnetToDpnList));
            Uint32 label = optionalSubs.get().getLabel();
            if (subOpBuilder.getRouteAdvState() != TaskState.Advertised) {
                if (subOpBuilder.getNhDpnId() == null) {
                    // No nexthop selected yet, elect one now
                    electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId,
                            null /*networkId*/, !isExternalSubnetVpn, label);
                } else if (!isExternalSubnetVpn) {
                    // Already nexthop has been selected, only publishing to bgp required, so publish to bgp
                    getNexthopTepAndPublishRoute(subOpBuilder, subnetId);
                }
            }
            SubnetOpDataEntry subOpEntry = subOpBuilder.build();
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                    subOpEntry, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
            LOG.info("{} onInterfaceUp: Updated subnetopdataentry to OP Datastore port {} up for subnet {}"
                            + " subnetIp {} vpnName {} rd {} TaskState {} lastTaskState {} ", LOGGING_PREFIX, intfName,
                    subnetId.getValue(), subOpEntry.getSubnetCidr(), subOpEntry.getVpnName(),
                    subOpEntry.getVrfId(), subOpEntry.getRouteAdvState(), subOpEntry.getLastAdvState());
        } catch (RuntimeException e) {
            LOG.error("{} onInterfaceUp: Unable to handle interface up event for port {} in subnet {}",
                    LOGGING_PREFIX, intfName, subnetId.getValue(), e);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} onInterfaceUp: Failed to read data store for interface {} dpn {} subnet {}", LOGGING_PREFIX,
                    intfName, dpnId, subnetId);
        } catch (TransactionCommitFailedException e) {
            LOG.error("{} onInterfaceUp: Updation of SubnetOpDataEntry for subnet {} on port {} up failed",
                    LOGGING_PREFIX, subnetId.getValue(), intfName, e);
        } finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
    }

    private Map<SubnetToDpnKey, SubnetToDpn> getSubnetToDpnMap(List<SubnetToDpn> subnetToDpnList) {
        //convert to set to remove duplicates.
        Set<SubnetToDpn> subnetToDpnSet = subnetToDpnList.stream().collect(Collectors.toSet());
        Map<SubnetToDpnKey, SubnetToDpn> subnetToDpnMap = new HashMap<>();
        for (SubnetToDpn subnetToDpn : subnetToDpnSet) {
            subnetToDpnMap.put(new SubnetToDpnKey(subnetToDpn.key()), subnetToDpn);
        }
        return subnetToDpnMap;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onInterfaceDown(final Uint64 dpnId, final String interfaceName, Uuid subnetId) {
        if (dpnId == null || Objects.equals(dpnId, Uint64.ZERO)) {
            LOG.error("{} onInterfaceDown: Unable to determine the DPNID for port {} on subnet {}", LOGGING_PREFIX,
                    interfaceName, subnetId.getValue());
            return;
        }
        try {
            vpnUtil.lockSubnet(subnetId.getValue());
            boolean last = subOpDpnManager.removeInterfaceFromDpn(subnetId, dpnId, interfaceName);
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                            new SubnetOpDataEntryKey(subnetId)).build();
            Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
            if (!optionalSubs.isPresent()) {
                LOG.info("{} onInterfaceDown: SubnetOpDataEntry for subnet {} is not available."
                        + " Ignoring port {} down event.", LOGGING_PREFIX, subnetId.getValue(), interfaceName);
                return;
            }
            SubnetOpDataEntry subnetOpDataEntry = optionalSubs.get();
            SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subnetOpDataEntry);
            LOG.info("{} onInterfaceDown: Updating the SubnetOpDataEntry node for subnet {} subnetIp {}"
                            + " vpnName {} rd {} TaskState {} lastTaskState {} on port {} down", LOGGING_PREFIX,
                    subnetId.getValue(), subOpBuilder.getSubnetCidr(), subOpBuilder.getVpnName(),
                    subOpBuilder.getVrfId(), subOpBuilder.getRouteAdvState(), subOpBuilder.getLastAdvState(),
                    interfaceName);
            Uint64 nhDpnId = subOpBuilder.getNhDpnId();
            if (nhDpnId != null && nhDpnId.equals(dpnId)) {
                // select another NhDpnId
                if (last) {
                    LOG.debug("{} onInterfaceDown: Last active port {} on the subnet {} subnetIp {} vpn {}"
                                    + " rd {}", LOGGING_PREFIX, interfaceName, subnetId.getValue(),
                            subOpBuilder.getSubnetCidr(), subOpBuilder.getVpnName(), subOpBuilder.getVrfId());
                    // last port on this DPN, so we need to elect the new NHDpnId
                    electNewDpnForSubnetRoute(subOpBuilder, dpnId, subnetId, null /*networkId*/,
                            !VpnUtil.isExternalSubnetVpn(subnetOpDataEntry.getVpnName(), subnetId.getValue()),
                            subOpBuilder.getLabel());
                    SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            subOpIdentifier, subOpBuilder.build(), VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
                    LOG.info("{} onInterfaceDown: Updated subnetopdataentry for subnet {} subnetIp {} vpnName {}"
                                    + " rd {} to OP Datastore on port {} down ", LOGGING_PREFIX, subnetId.getValue(),
                            subOpBuilder.getSubnetCidr(), subOpBuilder.getVpnName(), subOpBuilder.getVrfId(),
                            interfaceName);
                }
            }
        } catch (RuntimeException e) { //TODO: Remove RuntimeException
            LOG.error("{} onInterfaceDown: Unable to handle interface down event for port {} in subnet {}",
                    LOGGING_PREFIX, interfaceName, subnetId.getValue(), e);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} onInterfaceDown: Failed to read data store for interface {} dpn {} subnet {}",
                    LOGGING_PREFIX, interfaceName, dpnId, subnetId.getValue(), e);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("{} onInterfaceDown: SubnetOpDataEntry update on interface {} down event for subnet {} failed",
                    LOGGING_PREFIX, interfaceName, subnetId.getValue(), ex);
        } finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateSubnetRouteOnTunnelUpEvent(Uuid subnetId, Uint64 dpnId) {
        LOG.info("{} updateSubnetRouteOnTunnelUpEvent: Subnet {} Dpn {}", LOGGING_PREFIX, subnetId.getValue(),
                dpnId.toString());
        try {
            vpnUtil.lockSubnet(subnetId.getValue());
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                            new SubnetOpDataEntryKey(subnetId)).build();
            Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
            if (!optionalSubs.isPresent()) {
                LOG.error("{} updateSubnetRouteOnTunnelUpEvent: SubnetOpDataEntry for subnet {} is not available",
                        LOGGING_PREFIX, subnetId.getValue());
                return;
            }
            LOG.info("{} updateSubnetRouteOnTunnelUpEvent: Subnet {} subnetIp {} vpnName {} rd {} TaskState {}"
                            + " lastTaskState {} Dpn {}", LOGGING_PREFIX, subnetId.getValue(),
                    optionalSubs.get().getSubnetCidr(), optionalSubs.get().getVpnName(),
                    optionalSubs.get().getVrfId(), optionalSubs.get().getRouteAdvState(),
                    optionalSubs.get().getLastAdvState(), dpnId.toString());
            SubnetOpDataEntry subOpEntry = optionalSubs.get();
            SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subOpEntry);
            Uint32 label = optionalSubs.get().getLabel();
            boolean isExternalSubnetVpn = VpnUtil.isExternalSubnetVpn(subOpEntry.getVpnName(), subnetId.getValue());
            if (subOpBuilder.getRouteAdvState() != TaskState.Advertised) {
                if (subOpBuilder.getNhDpnId() == null) {
                    // No nexthop selected yet, elect one now
                    electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId,
                            null /*networkId*/, !isExternalSubnetVpn, label);
                } else if (!isExternalSubnetVpn) {
                    // Already nexthop has been selected, only publishing to bgp required, so publish to bgp
                    getNexthopTepAndPublishRoute(subOpBuilder, subnetId);
                }
            }
            subOpEntry = subOpBuilder.build();
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                    subOpEntry, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
            LOG.info("{} updateSubnetRouteOnTunnelUpEvent: Updated subnetopdataentry to OP Datastore tunnel up"
                            + " on dpn {} for subnet {} subnetIp {} vpnName {} rd {} TaskState {} lastTaskState {}",
                    LOGGING_PREFIX, dpnId.toString(), subnetId.getValue(), subOpEntry.getSubnetCidr(),
                    subOpEntry.getVpnName(), subOpEntry.getVrfId(), subOpEntry.getRouteAdvState(),
                    subOpEntry.getLastAdvState());
        } catch (RuntimeException e) { //TODO: lockSubnet() throws this exception. Rectify lockSubnet()
            LOG.error("{} updateSubnetRouteOnTunnelUpEvent: Unable to handle tunnel up event for subnetId {} dpnId {}",
                    LOGGING_PREFIX, subnetId.getValue(), dpnId.toString(), e);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("{} updateSubnetRouteOnTunnelUpEvent: Failed to update subnetRoute for subnet {} on dpn {}",
                    LOGGING_PREFIX, subnetId.getValue(), dpnId.toString(), ex);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} updateSubnetRouteOnTunnelUpEvent: Failed to read data store for subnet {} on dpn {}",
                    LOGGING_PREFIX, subnetId.getValue(), dpnId.toString(), e);
        }
        finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateSubnetRouteOnTunnelDownEvent(Uuid subnetId, Uint64 dpnId) {
        LOG.info("updateSubnetRouteOnTunnelDownEvent: Subnet {} Dpn {}", subnetId.getValue(), dpnId.toString());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            vpnUtil.lockSubnet(subnetId.getValue());
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                            new SubnetOpDataEntryKey(subnetId)).build();
            Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
            if (!optionalSubs.isPresent()) {
                LOG.error("{} updateSubnetRouteOnTunnelDownEvent: SubnetOpDataEntry for subnet {}"
                        + " is not available", LOGGING_PREFIX, subnetId.getValue());
                return;
            }
            LOG.debug("{} updateSubnetRouteOnTunnelDownEvent: Dpn {} Subnet {} subnetIp {} vpnName {} rd {}"
                            + " TaskState {} lastTaskState {}", LOGGING_PREFIX, dpnId.toString(), subnetId.getValue(),
                    optionalSubs.get().getSubnetCidr(), optionalSubs.get().getVpnName(),
                    optionalSubs.get().getVrfId(), optionalSubs.get().getRouteAdvState(),
                    optionalSubs.get().getLastAdvState());
            SubnetOpDataEntry subOpEntry = null;
            SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
            Uint64 nhDpnId = subOpBuilder.getNhDpnId();
            Uint32 label = optionalSubs.get().getLabel();
            if (nhDpnId != null && nhDpnId.equals(dpnId)) {
                electNewDpnForSubnetRoute(subOpBuilder, dpnId, subnetId, null /*networkId*/, true,
                        label);
                subOpEntry = subOpBuilder.build();
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                        subOpEntry, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
                LOG.info("{} updateSubnetRouteOnTunnelDownEvent: Subnet {} Dpn {} subnetIp {} vpnName {} rd {}"
                                + " TaskState {} lastTaskState {}", LOGGING_PREFIX, subnetId.getValue(),
                        dpnId.toString(), optionalSubs.get().getSubnetCidr(), optionalSubs.get().getVpnName(),
                        optionalSubs.get().getVrfId(), optionalSubs.get().getRouteAdvState(),
                        optionalSubs.get().getLastAdvState());
            }
        } catch (RuntimeException e) {
            LOG.error("{} updateSubnetRouteOnTunnelDownEvent: Unable to handle tunnel down event for subnetId {}"
                    + " dpnId {}", LOGGING_PREFIX, subnetId.getValue(), dpnId.toString(), e);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} Failed to read data store for subnet {} dpn {}", LOGGING_PREFIX, subnetId, dpnId);
        } catch (TransactionCommitFailedException e) {
            LOG.error("{} updateSubnetRouteOnTunnelDownEvent: Updation of SubnetOpDataEntry for subnet {}"
                    + " on dpn {} failed", LOGGING_PREFIX, subnetId.getValue(), dpnId, e);
        } finally {
            vpnUtil.unlockSubnet(subnetId.getValue());
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void publishSubnetRouteToBgp(SubnetOpDataEntryBuilder subOpBuilder, String nextHopIp) {
        try {
            //BGP manager will handle withdraw and advertise internally if prefix
            //already exist
            Uint32 label = Uint32.ZERO;
            Uint32 l3vni = Uint32.ZERO;

            VrfEntry.EncapType encapType =  VpnUtil.getEncapType(VpnUtil.isL3VpnOverVxLan(l3vni));
            if (encapType.equals(VrfEntry.EncapType.Vxlan)) {
                l3vni = subOpBuilder.getL3vni();
            } else {
                label = subOpBuilder.getLabel();
                if (label.longValue() == VpnConstants.INVALID_LABEL) {
                    LOG.error("publishSubnetRouteToBgp: Label not found for rd {}, subnetIp {}",
                            subOpBuilder.getVrfId(), subOpBuilder.getSubnetCidr());
                    return;
                }
            }
            bgpManager.advertisePrefix(subOpBuilder.getVrfId(), null /*macAddress*/, subOpBuilder.getSubnetCidr(),
                    Arrays.asList(nextHopIp), encapType,  label, l3vni,
                    Uint32.ZERO /*l2vni*/, null /*gatewayMacAddress*/);
            subOpBuilder.setLastAdvState(subOpBuilder.getRouteAdvState()).setRouteAdvState(TaskState.Advertised);
        } catch (Exception e) {
            LOG.error("{} publishSubnetRouteToBgp: Subnet route not advertised for subnet {} subnetIp {} vpn {} rd {}"
                            + " with dpnid {}", LOGGING_PREFIX, subOpBuilder.getSubnetId().getValue(),
                    subOpBuilder.getSubnetCidr(), subOpBuilder.getVpnName(), subOpBuilder.getVrfId(), nextHopIp, e);
        }
    }

    private void getNexthopTepAndPublishRoute(SubnetOpDataEntryBuilder subOpBuilder, Uuid subnetId) {
        String nhTepIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker,
                subOpBuilder.getNhDpnId());
        if (nhTepIp != null) {
            publishSubnetRouteToBgp(subOpBuilder, nhTepIp);
        } else {
            LOG.warn("Unable to find nexthopip for rd {} subnetroute subnetip {} for dpnid {}",
                    subOpBuilder.getVrfId(), subOpBuilder.getSubnetCidr(),
                    subOpBuilder.getNhDpnId().toString());
            electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId, null /*networkId*/,
                    true, subOpBuilder.getLabel());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean addSubnetRouteToFib(String rd, String subnetIp, Uint64 nhDpnId, String nextHopIp,
                                        String vpnName, Long elanTag, Uint32 label, Uint32 l3vni,
                                        Uuid subnetId, boolean isBgpVpn, String networkName) {

        Preconditions.checkNotNull(rd,
                LOGGING_PREFIX + " addSubnetRouteToFib: RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp,
                LOGGING_PREFIX + " addSubnetRouteToFib: SubnetRouteIp cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, LOGGING_PREFIX + " addSubnetRouteToFib: vpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, LOGGING_PREFIX + " addSubnetRouteToFib: elanTag cannot be null or empty!");
        Preconditions.checkNotNull(label, LOGGING_PREFIX + " addSubnetRouteToFib: label cannot be null or empty!");
        VrfEntry.EncapType encapType = VpnUtil.getEncapType(VpnUtil.isL3VpnOverVxLan(l3vni));
        VpnPopulator vpnPopulator = L3vpnRegistry.getRegisteredPopulator(encapType);
        LOG.info("{} addSubnetRouteToFib: Adding SubnetRoute fib entry for vpnName {}, subnetIP {}, elanTag {}",
                LOGGING_PREFIX, vpnName, subnetIp, elanTag);
        L3vpnInput input = new L3vpnInput().setRouteOrigin(RouteOrigin.CONNECTED).setRd(rd).setVpnName(vpnName)
                .setSubnetIp(subnetIp).setNextHopIp(nextHopIp).setL3vni(l3vni.longValue())
                .setLabel(label.longValue()).setElanTag(elanTag)
                .setDpnId(nhDpnId).setEncapType(encapType).setNetworkName(networkName).setPrimaryRd(rd);
        if (!isBgpVpn) {
            vpnPopulator.populateFib(input, null /*writeCfgTxn*/);
            return true;
        }
        Preconditions.checkNotNull(nextHopIp, LOGGING_PREFIX + "NextHopIp cannot be null or empty!");
        vpnUtil.syncWrite(LogicalDatastoreType.OPERATIONAL, VpnUtil
                .getPrefixToInterfaceIdentifier(vpnUtil.getVpnId(vpnName), subnetIp), VpnUtil
                .getPrefixToInterface(nhDpnId, subnetId.getValue(), subnetIp, Prefixes.PrefixCue.SubnetRoute));
        vpnPopulator.populateFib(input, null /*writeCfgTxn*/);
        try {
            // BGP manager will handle withdraw and advertise internally if prefix
            // already exist
            bgpManager.advertisePrefix(rd, null /*macAddress*/, subnetIp, Collections.singletonList(nextHopIp),
                    encapType, label, l3vni, Uint32.ZERO /*l2vni*/, null /*gatewayMacAddress*/);
        } catch (Exception e) {
            LOG.error("{} addSubnetRouteToFib: Subnet route not advertised for subnet {} subnetIp {} vpnName {} rd {} "
                    + "with dpnid {}", LOGGING_PREFIX, subnetId.getValue(), subnetIp, vpnName, rd, nhDpnId, e);
            return false;
        }
        return true;
    }

    @SuppressFBWarnings
    private Uint32 getLabel(String rd, String subnetIp) {
        Uint32 label = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME, VpnUtil.getNextHopLabelKey(rd, subnetIp));
        LOG.trace("{} getLabel: Allocated subnetroute label {} for rd {} prefix {}", LOGGING_PREFIX, label, rd,
                subnetIp);
        return label;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean deleteSubnetRouteFromFib(String rd, String subnetIp, String vpnName, boolean isBgpVpn) {
        Preconditions.checkNotNull(rd,
                LOGGING_PREFIX + " deleteSubnetRouteFromFib: RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp,
                LOGGING_PREFIX +  " deleteSubnetRouteFromFib: SubnetRouteIp cannot be null or empty!");
        deleteSubnetRouteFibEntryFromDS(rd, subnetIp, vpnName);
        if (isBgpVpn) {
            try {
                bgpManager.withdrawPrefix(rd, subnetIp);
            } catch (Exception e) {
                LOG.error("{} deleteSubnetRouteFromFib: Subnet route not withdrawn for subnetIp {} vpn {} rd {}",
                        LOGGING_PREFIX, subnetIp, vpnName, rd, e);
                return false;
            }
        }
        return true;
    }

    public void deleteSubnetRouteFibEntryFromDS(String rd, String prefix, String vpnName) {
        fibManager.removeFibEntry(rd, prefix, VPN_EVENT_SOURCE_SUBNET_ROUTE, null);
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = vpnUtil.getVpnsImportingMyRoute(vpnName);
        for (VpnInstanceOpDataEntry vpnInstance : vpnsToImportRoute) {
            String importingRd = vpnInstance.getVrfId();
            fibManager.removeFibEntry(importingRd, prefix, VPN_EVENT_SOURCE_SUBNET_ROUTE, null);
            LOG.info("SUBNETROUTE: deleteSubnetRouteFibEntryFromDS: Deleted imported subnet route rd {} prefix {}"
                    + " from vpn {} importingRd {}", rd, prefix, vpnInstance.getVpnInstanceName(), importingRd);
        }
        LOG.info("SUBNETROUTE: deleteSubnetRouteFibEntryFromDS: Removed subnetroute FIB for prefix {} rd {}"
                + " vpnName {}", prefix, rd, vpnName);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void electNewDpnForSubnetRoute(SubnetOpDataEntryBuilder subOpBuilder, @Nullable Uint64 oldDpnId,
                                           Uuid subnetId, Uuid networkId, boolean isBgpVpn, Uint32 label) {
        boolean isRouteAdvertised = false;
        String rd = subOpBuilder.getVrfId();
        String subnetIp = subOpBuilder.getSubnetCidr();
        String vpnName = subOpBuilder.getVpnName();
        long elanTag = subOpBuilder.getElanTag().toJava();
        boolean isAlternateDpnSelected = false;
        Uint32 l3vni = Uint32.ZERO;
        String networkName = networkId != null ? networkId.getValue() : null;

        LOG.info("{} electNewDpnForSubnetRoute: Handling subnet {} subnetIp {} vpn {} rd {} label {} TaskState {}"
                        + " lastTaskState {}", LOGGING_PREFIX, subnetId.getValue(), subnetIp, subOpBuilder.getVpnName(),
                subOpBuilder.getVrfId(), label, subOpBuilder.getRouteAdvState(), subOpBuilder.getLastAdvState());
        if (!isBgpVpn) {
            // Non-BGPVPN as it stands here represents use-case of External Subnets of VLAN-Provider-Network
            //  TODO(Tomer):  Pulling in both external and internal VLAN-Provider-Network need to be
            // blended more better into this design.
            if (VpnUtil.isL3VpnOverVxLan(subOpBuilder.getL3vni())) {
                l3vni = subOpBuilder.getL3vni();
            } else {
                subOpBuilder.setLabel(label);
            }
            isRouteAdvertised = addSubnetRouteToFib(rd, subnetIp, null /* nhDpnId */, null /* nhTepIp */,
                    vpnName, elanTag, label, l3vni, subnetId, isBgpVpn, networkName);
            if (isRouteAdvertised) {
                subOpBuilder.setRouteAdvState(TaskState.Advertised);
            } else {
                LOG.error("{} electNewDpnForSubnetRoute: Unable to find TepIp for subnet {} subnetip {} vpnName {}"
                                + " rd {}, attempt next dpn", LOGGING_PREFIX, subnetId.getValue(), subnetIp,
                        vpnName, rd);
                subOpBuilder.setRouteAdvState(TaskState.PendingAdvertise);
            }
            return;
        }

        String nhTepIp = null;
        Uint64 nhDpnId = null;
        Map<SubnetToDpnKey, SubnetToDpn> toDpnKeySubnetToDpnMap = subOpBuilder.getSubnetToDpn();
        if (toDpnKeySubnetToDpnMap != null) {
            for (SubnetToDpn subnetToDpn : toDpnKeySubnetToDpnMap.values()) {
                if (subnetToDpn.getDpnId().equals(oldDpnId)) {
                    // Is this same is as input dpnId, then ignore it
                    continue;
                }
                nhDpnId = subnetToDpn.getDpnId();
                if (vpnNodeListener.isConnectedNode(nhDpnId)) {
                    // selected dpnId is connected to ODL
                    // but does it have a TEP configured at all?
                    try {
                        nhTepIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, nhDpnId);
                        if (nhTepIp != null) {
                            isAlternateDpnSelected = true;
                            break;
                        }
                    } catch (Exception e) {
                        LOG.warn("{} electNewDpnForSubnetRoute: Unable to find TepIp for rd {} subnetroute subnetip {}"
                                + " for dpnid {}, attempt next", LOGGING_PREFIX, rd, subnetIp, nhDpnId.toString(), e);
                    }
                }
            }
        }
        if (!isAlternateDpnSelected) {
            //If no alternate Dpn is selected as nextHopDpn, withdraw the subnetroute if it had a nextHop already.
            if (isRouteAdvertised(subOpBuilder) && oldDpnId != null) {
                LOG.info("{} electNewDpnForSubnetRoute: No alternate DPN available for subnet {} subnetIp {} vpn {}"
                                + " rd {} Prefix withdrawn from BGP", LOGGING_PREFIX, subnetId.getValue(), subnetIp,
                        vpnName, rd);
                // Withdraw route from BGP for this subnet
                boolean routeWithdrawn = deleteSubnetRouteFromFib(rd, subnetIp, vpnName, isBgpVpn);
                //subOpBuilder.setNhDpnId(Uint64.valueOf(null));
                subOpBuilder.setLastAdvState(subOpBuilder.getRouteAdvState());
                if (routeWithdrawn) {
                    subOpBuilder.setRouteAdvState(TaskState.Withdrawn);
                } else {
                    LOG.error("{} electNewDpnForSubnetRoute: Withdrawing NextHopDPN {} for subnet {} subnetIp {}"
                                    + " vpn {} rd {} from BGP failed", LOGGING_PREFIX, oldDpnId, subnetId.getValue(),
                            subnetIp, vpnName, rd);
                    subOpBuilder.setRouteAdvState(TaskState.PendingWithdraw);
                }
            }
        } else {
            //If alternate Dpn is selected as nextHopDpn, use that for subnetroute.
            subOpBuilder.setNhDpnId(nhDpnId);
            if (VpnUtil.isL3VpnOverVxLan(subOpBuilder.getL3vni())) {
                l3vni = subOpBuilder.getL3vni();
            } else {
                subOpBuilder.setLabel(label);
            }
            //update the VRF entry for the subnetroute.
            isRouteAdvertised = addSubnetRouteToFib(rd, subnetIp, nhDpnId, nhTepIp,
                    vpnName, elanTag, label, l3vni, subnetId, isBgpVpn, networkName);
            subOpBuilder.setLastAdvState(subOpBuilder.getRouteAdvState());
            if (isRouteAdvertised) {
                subOpBuilder.setRouteAdvState(TaskState.Advertised);
            } else {
                LOG.error("{} electNewDpnForSubnetRoute: Swapping to add new NextHopDpn {} for subnet {} subnetIp {}"
                        + " vpn {} rd {} failed", LOGGING_PREFIX, nhDpnId, subnetId.getValue(), subnetIp, vpnName, rd);
                subOpBuilder.setRouteAdvState(TaskState.PendingAdvertise);
            }
        }
    }

    private static boolean isRouteAdvertised(SubnetOpDataEntryBuilder subOpBuilder) {
        return subOpBuilder.getRouteAdvState() == TaskState.Advertised
                || subOpBuilder.getRouteAdvState() == TaskState.PendingAdvertise;
    }

    private static @NonNull List<SubnetToDpn> concat(@Nullable List<SubnetToDpn> list, @NonNull SubnetToDpn entry) {
        final List<SubnetToDpn> ret = new ArrayList<>();
        if (list != null) {
            ret.addAll(list);
        }
        ret.add(entry);
        return ret;
    }
}


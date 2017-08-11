/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnOpDataSyncer.VpnOpDataType;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.TaskState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnSubnetRouteHandler {
    private static final Logger LOG = LoggerFactory.getLogger(VpnSubnetRouteHandler.class);
    private static final String LOGGING_PREFIX = "SUBNETROUTE:";
    private final DataBroker dataBroker;
    private final SubnetOpDpnManager subOpDpnManager;
    private final IBgpManager bgpManager;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final IdManagerService idManager;
    private LockManagerService lockManager;
    private final VpnOpDataSyncer vpnOpDataSyncer;
    private final VpnNodeListener vpnNodeListener;

    public VpnSubnetRouteHandler(final DataBroker dataBroker, final SubnetOpDpnManager subnetOpDpnManager,
        final IBgpManager bgpManager, final VpnInterfaceManager vpnIntfManager, final IdManagerService idManager,
        LockManagerService lockManagerService, final VpnOpDataSyncer vpnOpDataSyncer,
        final VpnNodeListener vpnNodeListener) {
        this.dataBroker = dataBroker;
        this.subOpDpnManager = subnetOpDpnManager;
        this.bgpManager = bgpManager;
        this.vpnInterfaceManager = vpnIntfManager;
        this.idManager = idManager;
        this.lockManager = lockManagerService;
        this.vpnOpDataSyncer = vpnOpDataSyncer;
        this.vpnNodeListener = vpnNodeListener;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onSubnetAddedToVpn(Subnetmap subnetmap, boolean isBgpVpn, Long elanTag) {
        Uuid subnetId = subnetmap.getId();
        String subnetIp = subnetmap.getSubnetIp();
        boolean isRouteAdvertised = false;

        Preconditions.checkNotNull(subnetId, "SubnetId cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetPrefix cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "ElanTag cannot be null or empty!");

        LOG.info("onSubnetAddedToVpn: Subnet {} being added to vpn", subnetId.getValue());

        String vpnName;
        if (subnetmap.getVpnId() != null) {
            vpnName = subnetmap.getVpnId().getValue();
            long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            if (vpnId == VpnConstants.INVALID_ID) {
                vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataType.vpnInstanceToId, vpnName,
                        VpnConstants.PER_VPN_INSTANCE_MAX_WAIT_TIME_IN_MILLISECONDS);
                vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
                if (vpnId == VpnConstants.INVALID_ID) {
                    LOG.error("onSubnetAddedToVpn: VpnInstance to VPNId mapping not yet available for VpnName {} "
                              + "processing subnet {} with IP {}, bailing out now.", vpnName, subnetId, subnetIp);
                    return;
                }
            }
        } else {
            LOG.error("onSubnetAddedToVpn: VpnId {} for subnet {} not found, bailing out", subnetmap.getVpnId(),
                      subnetId);
            return;
        }

        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                Subnetmap subMap = null;

                // Please check if subnetId belongs to an External Network
                InstanceIdentifier<Subnetmap> subMapid =
                    InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                        new SubnetmapKey(subnetId)).build();
                Optional<Subnetmap> sm = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, subMapid);
                if (!sm.isPresent()) {
                    LOG.error("onSubnetAddedToVpn: Unable to retrieve subnetmap entry for subnet {}", subnetId);
                    return;
                }
                subMap = sm.get();

                if (isBgpVpn) {
                    InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
                            .child(Networks.class, new NetworksKey(subMap.getNetworkId())).build();
                    Optional<Networks> optionalNets = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            netsIdentifier);
                    if (optionalNets.isPresent()) {
                        LOG.info("onSubnetAddedToVpn: subnet {} is an external subnet on external network" + " {},"
                                + " so ignoring this" + " for SubnetRoute",
                                subnetId.getValue(), subMap.getNetworkId().getValue());
                        return;
                    }
                }
                //Create and add SubnetOpDataEntry object for this subnet to the SubnetOpData container
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                        LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (optionalSubs.isPresent()) {
                    LOG.error("onSubnetAddedToVpn: SubnetOpDataEntry for subnet {} already detected to be present",
                        subnetId.getValue());
                    return;
                }

                LOG.debug("onSubnetAddedToVpn: Creating new SubnetOpDataEntry node for subnet {}", subnetId.getValue());
                Map<BigInteger, SubnetToDpn> subDpnMap = new HashMap<>();
                BigInteger dpnId = null;
                SubnetToDpn subDpn = null;
                SubnetOpDataEntryBuilder subOpBuilder =
                    new SubnetOpDataEntryBuilder().setKey(new SubnetOpDataEntryKey(subnetId));
                subOpBuilder.setSubnetId(subnetId);
                subOpBuilder.setSubnetCidr(subnetIp);
                String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);

                if (isBgpVpn && !VpnUtil.isBgpVpn(vpnName, primaryRd)) {
                    LOG.error("onSubnetAddedToVpn: The VPN Instance name {} does not have RD", vpnName);
                    return;
                }

                subOpBuilder.setVrfId(primaryRd);
                subOpBuilder.setVpnName(vpnName);
                subOpBuilder.setSubnetToDpn(new ArrayList<>());
                subOpBuilder.setRouteAdvState(TaskState.Idle);
                subOpBuilder.setElanTag(elanTag);
                Long l3Vni = VpnUtil.getVpnInstanceOpData(dataBroker, primaryRd).getL3vni();
                subOpBuilder.setL3vni(l3Vni);

                // First recover set of ports available in this subnet
                List<Uuid> portList = subMap.getPortList();
                if (portList != null) {
                    for (Uuid port : portList) {
                        Interface intfState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker,port.getValue());
                        if (intfState != null) {
                            try {
                                dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                            } catch (Exception e) {
                                LOG.error("onSubnetAddedToVpn: Unable to obtain dpnId for interface {},",
                                        " subnetroute inclusion for this interface failed with exception {}",
                                        port.getValue(), e);
                                continue;
                            }
                            if (dpnId.equals(BigInteger.ZERO)) {
                                LOG.info("onSubnetAddedToVpn: Port {} is not assigned DPN yet ignoring",
                                        port.getValue());
                                continue;
                            }
                            subOpDpnManager.addPortOpDataEntry(port.getValue(), subnetId, dpnId);
                            if (intfState.getOperStatus() != OperStatus.Up) {
                                LOG.info("onSubnetAddedToVpn: Port {} is not UP yet, ignoring", port.getValue());
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
                        subMap.getNetworkId(), isBgpVpn);
                SubnetOpDataEntry subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info("onSubnetAddedToVpn: Added subnetopdataentry to OP Datastore for subnet {}",
                        subnetId.getValue());
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle subnet {} added to vpn {} {}", subnetIp, vpnName, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onSubnetDeletedFromVpn(Subnetmap subnetmap, boolean isBgpVpn) {
        Uuid subnetId = subnetmap.getId();
        LOG.info("onSubnetDeletedFromVpn: Subnet {} being removed from vpn", subnetId.getValue());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                LOG.trace(" Removing the SubnetOpDataEntry node for subnet {}", subnetId.getValue());
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                        LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("onSubnetDeletedFromVpn: SubnetOpDataEntry for subnet {} not available in datastore",
                        subnetId.getValue());
                    return;
                }

                /* If subnet is deleted (or if its removed from VPN), the ports that are DOWN on that subnet
                 * will continue to be stale in portOpData DS, as subDpnList used for portOpData removal will
                 * contain only ports that are UP. So here we explicitly cleanup the ports of the subnet by
                 * going through the list of ports on the subnet
                 */
                InstanceIdentifier<Subnetmap> subMapid =
                    InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                        new SubnetmapKey(subnetId)).build();
                Optional<Subnetmap> sm = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, subMapid);
                if (!sm.isPresent()) {
                    LOG.error("Stale ports removal: Unable to retrieve subnetmap entry for subnet {}", subnetId);
                } else {
                    Subnetmap subMap = sm.get();
                    List<Uuid> portList = subMap.getPortList();
                    if (portList != null) {
                        for (Uuid port : portList) {
                            InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
                                InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                                    new PortOpDataEntryKey(port.getValue())).build();
                            LOG.trace("Deleting portOpData entry for port {}", port.getValue());
                            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                        }
                    }
                }

                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                //Withdraw the routes for all the interfaces on this subnet
                //Remove subnet route entry from FIB
                deleteSubnetRouteFromFib(rd, subnetIp, vpnName, isBgpVpn);
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
                LOG.info("onSubnetDeletedFromVpn: Removed subnetopdataentry for subnet {} successfully from Datastore",
                    subnetId.getValue());
            } catch (Exception ex) {
                LOG.error("Removal of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle subnet {} removed to vpn {}", subnetmap.getSubnetIp(),
                subnetmap.getVpnId().getValue(), e);
        }
    }

    public void onSubnetUpdatedInVpn(Subnetmap subnetmap, Long elanTag) {
        Uuid subnetId = subnetmap.getId();
        String vpnName = subnetmap.getVpnId().getValue();
        String subnetIp = subnetmap.getSubnetIp();

        Preconditions.checkNotNull(subnetId, "SubnetId cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetPrefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "VpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "ElanTag cannot be null or empty!");

        InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
            InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                new SubnetOpDataEntryKey(subnetId)).build();
        Optional<SubnetOpDataEntry> optionalSubs =
            VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
        if (optionalSubs.isPresent()) {
            onSubnetDeletedFromVpn(subnetmap, true);
        } else {
            onSubnetAddedToVpn(subnetmap, true, elanTag);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onPortAddedToSubnet(Subnetmap subnetmap, Uuid portId) {
        Uuid subnetId = subnetmap.getId();
        boolean isRouteAdvertised = false;
        LOG.info("onPortAddedToSubnet: Port {} being added to subnet {}", portId.getValue(), subnetId.getValue());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();

                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.info("onPortAddedToSubnet: Port {} is part of a subnet {} that is not in VPN, ignoring",
                        portId.getValue(), subnetId.getValue());
                    return;
                }
                String vpnName = optionalSubs.get().getVpnName();
                String subnetIp = optionalSubs.get().getSubnetCidr();
                String rd = optionalSubs.get().getVrfId();
                String routeAdvState = optionalSubs.get().getRouteAdvState().toString();
                LOG.info("{} onPortAddedToSubnet: Port {} being added to subnet {} subnetIp {} vpnName {} rd {} "
                                + "TaskState {}", LOGGING_PREFIX, portId.getValue(), subnetId.getValue(), subnetIp,
                        vpnName, rd, routeAdvState);
                subOpDpnManager.addPortOpDataEntry(portId.getValue(), subnetId, null);
                Interface intfState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker,portId.getValue());
                if (intfState == null) {
                    // Interface State not yet available
                    return;
                }
                BigInteger dpnId = BigInteger.ZERO;
                try {
                    dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                } catch (Exception e) {
                    LOG.error("{} onPortAddedToSubnet: Unable to obtain dpnId for interface {}. subnetroute inclusion"
                                    + " for this interface failed for subnet {} subnetIp {} vpn {} rd {} with "
                                    + "exception {}", LOGGING_PREFIX, portId.getValue(), subnetId.getValue(), subnetIp,
                            vpnName, rd, e);
                    return;
                }
                if (dpnId.equals(BigInteger.ZERO)) {
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
                List<SubnetToDpn> subDpnList = subOpBuilder.getSubnetToDpn();
                subDpnList.add(subDpn);
                subOpBuilder.setSubnetToDpn(subDpnList);
                if (subOpBuilder.getRouteAdvState() != TaskState.Advertised) {
                    if (subOpBuilder.getNhDpnId() == null) {
                        // No nexthop selected yet, elect one now
                        electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId,
                                subnetmap.getNetworkId(), true);
                    } else if (!VpnUtil.isExternalSubnetVpn(subnetOpDataEntry.getVpnName(), subnetId.getValue())) {
                        // Already nexthop has been selected, only publishing to bgp required, so publish to bgp
                        getNexthopTepAndPublishRoute(subOpBuilder, subnetId);
                    }
                }
                SubnetOpDataEntry subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info("onPortAddedToSubnet: Updated subnetopdataentry to OP Datastore for port {}",
                    portId.getValue());
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle port {} added to subnet {}", portId.getValue(), subnetId.getValue(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onPortRemovedFromSubnet(Subnetmap subnetmap, Uuid portId) {
        Uuid subnetId = subnetmap.getId();
        boolean isRouteAdvertised = false;

        LOG.info("onPortRemovedFromSubnet: Port {} being removed from subnet {}",
                portId.getValue(), subnetId.getValue());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                PortOpDataEntry portOpEntry = subOpDpnManager.removePortOpDataEntry(portId.getValue());
                if (portOpEntry == null) {
                    return;
                }
                BigInteger dpnId = portOpEntry.getDpnId();
                if (dpnId == null) {
                    LOG.debug("onPortRemovedFromSubnet:  Port {} does not have a DPNId associated, ignoring",
                        portId.getValue());
                    return;
                }
                LOG.debug(
                    "onPortRemovedFromSubnet: Updating the SubnetOpDataEntry node for subnet {} ", subnetId.getValue());
                boolean last = subOpDpnManager.removeInterfaceFromDpn(subnetId, dpnId, portId.getValue());
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.info("onPortRemovedFromSubnet: Port {} is part of a subnet {} that is not in VPN, ignoring",
                        portId.getValue(), subnetId.getValue());
                    return;
                }
                SubnetOpDataEntry subnetOpDataEntry = optionalSubs.get();
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subnetOpDataEntry);
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                if ((nhDpnId != null) && (nhDpnId.equals(dpnId))) {
                    // select another NhDpnId
                    if (last) {
                        LOG.debug("onPortRemovedFromSubnet: Last port {} on the subnet {}", portId,
                            subnetId.getValue());
                        // last port on this DPN, so we need to elect the new NHDpnId
                        electNewDpnForSubnetRoute(subOpBuilder, nhDpnId, subnetId, subnetmap.getNetworkId(),
                                !VpnUtil.isExternalSubnetVpn(subnetOpDataEntry.getVpnName(), subnetId.getValue()));
                        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                                subOpBuilder.build());
                        LOG.info("onPortRemovedFromSubnet: Updated subnetopdataentry to OP Datastore removing port "
                                + portId.getValue());
                    }
                }
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle port {} removed from subnet {}", portId.getValue(), subnetId.getValue(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onInterfaceUp(BigInteger dpnId, String intfName, Uuid subnetId) {
        LOG.info("onInterfaceUp: Port {}", intfName);
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        SubnetToDpn subDpn = null;
        if ((dpnId == null) || Objects.equals(dpnId, BigInteger.ZERO)) {
            LOG.error("onInterfaceUp: Unable to determine the DPNID for port {}", intfName);
            return;
        }
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("onInterfaceUp: SubnetOpDataEntry for subnet {} is not available", subnetId.getValue());
                    return;
                }

                LOG.debug("onInterfaceUp: Updating the SubnetOpDataEntry node for subnet {}", subnetId.getValue());
                subOpDpnManager.addPortOpDataEntry(intfName, subnetId, dpnId);
                subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, intfName);
                if (subDpn == null) {
                    return;
                }
                SubnetOpDataEntry subnetOpDataEntry = optionalSubs.get();
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subnetOpDataEntry);
                boolean isExternalSubnetVpn = VpnUtil.isExternalSubnetVpn(subnetOpDataEntry.getVpnName(),
                        subnetId.getValue());
                List<SubnetToDpn> subDpnList = subOpBuilder.getSubnetToDpn();
                subDpnList.add(subDpn);
                subOpBuilder.setSubnetToDpn(subDpnList);
                if (subOpBuilder.getRouteAdvState() != TaskState.Advertised) {
                    if (subOpBuilder.getNhDpnId() == null) {
                        // No nexthop selected yet, elect one now
                        electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId,
                                null /*networkId*/, !isExternalSubnetVpn);
                    } else if (!isExternalSubnetVpn) {
                        // Already nexthop has been selected, only publishing to bgp required, so publish to bgp
                        getNexthopTepAndPublishRoute(subOpBuilder, subnetId);
                    }
                }
                SubnetOpDataEntry subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info("onInterfaceUp: Updated subnetopdataentry to OP Datastore port up {}", intfName);
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle interface up event for port {} in subnet {}", intfName, subnetId.getValue(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onInterfaceDown(final BigInteger dpnId, final String interfaceName, Uuid subnetId) {
        LOG.info("onInterfaceDown: Port {}", interfaceName);
        if ((dpnId == null) || (Objects.equals(dpnId, BigInteger.ZERO))) {
            LOG.error("onInterfaceDown: Unable to determine the DPNID for port {}", interfaceName);
            return;
        }
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                LOG.debug("onInterfaceDown: Updating the SubnetOpDataEntry node for subnet {} ", subnetId.getValue());
                boolean last = subOpDpnManager.removeInterfaceFromDpn(subnetId, dpnId, interfaceName);
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("onInterfaceDown: SubnetOpDataEntry for subnet {} is not available", subnetId.getValue());
                    return;
                }
                SubnetOpDataEntry subnetOpDataEntry = optionalSubs.get();
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subnetOpDataEntry);
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                if ((nhDpnId != null) && (nhDpnId.equals(dpnId))) {
                    // select another NhDpnId
                    if (last) {
                        LOG.debug(
                            "onInterfaceDown: Last active port {} on the subnet {}",
                                interfaceName, subnetId.getValue());
                        // last port on this DPN, so we need to elect the new NHDpnId
                        electNewDpnForSubnetRoute(subOpBuilder, dpnId, subnetId, null /*networkId*/,
                                !VpnUtil.isExternalSubnetVpn(subnetOpDataEntry.getVpnName(), subnetId.getValue()));
                        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier,
                                subOpBuilder.build());
                        LOG.info("onInterfaceDown: Updated subnetopdataentry to OP Datastore for port {}",
                                interfaceName);
                    }
                }
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle interface down event for port {} in subnet {}", interfaceName,
                    subnetId.getValue(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateSubnetRouteOnTunnelUpEvent(Uuid subnetId, BigInteger dpnId) {
        boolean isRouteAdvertised = false;
        LOG.info("updateSubnetRouteOnTunnelUpEvent: Subnet {} Dpn {}", subnetId.getValue(), dpnId.toString());
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("updateSubnetRouteOnTunnelUpEvent: SubnetOpDataEntry for subnet {} is not available",
                        subnetId.getValue());
                    return;
                }
                SubnetOpDataEntry subOpEntry = optionalSubs.get();
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(subOpEntry);
                boolean isExternalSubnetVpn = VpnUtil.isExternalSubnetVpn(subOpEntry.getVpnName(), subnetId.getValue());
                if (subOpBuilder.getRouteAdvState() != TaskState.Advertised) {
                    if (subOpBuilder.getNhDpnId() == null) {
                        // No nexthop selected yet, elect one now
                        electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId,
                                null /*networkId*/, !isExternalSubnetVpn);
                    } else if (!isExternalSubnetVpn) {
                        // Already nexthop has been selected, only publishing to bgp required, so publish to bgp
                        getNexthopTepAndPublishRoute(subOpBuilder, subnetId);
                    }
                }
                subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info(
                    "updateSubnetRouteOnTunnelUpEvent: Updated subnetopdataentry to OP Datastore tunnel up on dpn"
                        + " {} for subnet {}",
                    dpnId.toString(), subnetId.getValue());
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle tunnel up event for subnetId {} dpnId {}", subnetId.getValue(),
                dpnId.toString(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateSubnetRouteOnTunnelDownEvent(Uuid subnetId, BigInteger dpnId) {
        LOG.info("updateSubnetRouteOnTunnelDownEvent: Subnet {} Dpn {}", subnetId.getValue(), dpnId.toString());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("updateSubnetRouteOnTunnelDownEvent: SubnetOpDataEntry for subnet {} is not available",
                        subnetId.getValue());
                    return;
                }
                SubnetOpDataEntry subOpEntry = null;
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                if ((nhDpnId != null) && (nhDpnId.equals(dpnId))) {
                    electNewDpnForSubnetRoute(subOpBuilder, dpnId, subnetId, null /*networkId*/, true);
                    subOpEntry = subOpBuilder.build();
                    MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                    LOG.info(
                        "updateSubnetRouteOnTunnelDownEvent: Updated subnetopdataentry to OP Datastore tunnnel down "
                            + "on dpn {} for subnet {}",
                        dpnId.toString(), subnetId.getValue());
                }
            } catch (Exception ex) {
                LOG.error("Updation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle tunnel down event for subnetId {} dpnId {}", subnetId.getValue(),
                dpnId.toString(), e);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void publishSubnetRouteToBgp(SubnetOpDataEntryBuilder subOpBuilder, String nextHopIp) {
        try {
            //BGP manager will handle withdraw and advertise internally if prefix
            //already exist
            long label = 0;
            long l3vni = 0;

            VrfEntry.EncapType encapType =  VpnUtil.getEncapType(VpnUtil.isL3VpnOverVxLan(l3vni));
            if (encapType.equals(VrfEntry.EncapType.Vxlan)) {
                l3vni = subOpBuilder.getL3vni();
            } else {
                label = subOpBuilder.getLabel();
            }
            bgpManager.advertisePrefix(subOpBuilder.getVrfId(), null /*macAddress*/, subOpBuilder.getSubnetCidr(),
                    Arrays.asList(nextHopIp), encapType,  label, l3vni,
                    0 /*l2vni*/, null /*gatewayMacAddress*/);
            subOpBuilder.setRouteAdvState(TaskState.Advertised);
        } catch (Exception e) {
            LOG.error("Fail: Subnet route not advertised for rd {} subnetIp {} with dpnid {}",
                    subOpBuilder.getVrfId(), subOpBuilder.getSubnetCidr(), nextHopIp, e);
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
            electNewDpnForSubnetRoute(subOpBuilder, null /* oldDpnId */, subnetId, null /*networkId*/, true);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean addSubnetRouteToFib(String rd, String subnetIp, BigInteger nhDpnId, String nextHopIp,
                                        String vpnName, Long elanTag, long label, long l3vni,
                                        Uuid subnetId, boolean isBgpVpn, String networkName) {

        Preconditions.checkNotNull(rd, "RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetRouteIp cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "vpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "elanTag cannot be null or empty!");
        Preconditions.checkNotNull(label, "label cannot be null or empty!");
        VrfEntry.EncapType encapType = VpnUtil.getEncapType(VpnUtil.isL3VpnOverVxLan(l3vni));
        VpnPopulator vpnPopulator = L3vpnRegistry.getRegisteredPopulator(encapType);
        LOG.info("Adding SubnetRoute fib entry for vpnName {}, subnetIP {}, elanTag {}",
                vpnName, subnetIp, elanTag);
        L3vpnInput input = new L3vpnInput().setRouteOrigin(RouteOrigin.CONNECTED).setRd(rd).setVpnName(vpnName)
                .setSubnetIp(subnetIp).setNextHopIp(nextHopIp).setL3vni(l3vni).setLabel(label).setElanTag(elanTag)
                .setDpnId(nhDpnId).setEncapType(encapType).setNetworkName(networkName).setPrimaryRd(rd);
        if (!isBgpVpn) {
            vpnPopulator.populateFib(input, null /*writeCfgTxn*/, null /*writeOperTxn*/);
            return true;
        }
        Preconditions.checkNotNull(nextHopIp, "NextHopIp cannot be null or empty!");
        VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, VpnUtil
                .getPrefixToInterfaceIdentifier(VpnUtil.getVpnId(dataBroker, vpnName), subnetIp), VpnUtil
                .getPrefixToInterface(nhDpnId, subnetId.getValue(), subnetIp, subnetId, true /*isNatPrefix*/));
        vpnPopulator.populateFib(input, null /*writeCfgTxn*/, null /*writeOperTxn*/);
        try {
            // BGP manager will handle withdraw and advertise internally if prefix
            // already exist
            bgpManager.advertisePrefix(rd, null /*macAddress*/, subnetIp, Collections.singletonList(nextHopIp),
                    encapType, label, l3vni, 0 /*l2vni*/, null /*gatewayMacAddress*/);
        } catch (Exception e) {
            LOG.error("Fail: Subnet route not advertised for rd {} subnetIp {} with dpnId {}", rd, subnetIp,
                    nhDpnId, e);
            return false;
        }
        return true;
    }

    private int getLabel(String rd, String subnetIp) {
        int label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
            VpnUtil.getNextHopLabelKey(rd, subnetIp));
        LOG.trace("Allocated subnetroute label {} for rd {} prefix {}", label, rd, subnetIp);
        return label;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean deleteSubnetRouteFromFib(String rd, String subnetIp, String vpnName, boolean isBgpVpn) {
        Preconditions.checkNotNull(rd, "RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetRouteIp cannot be null or empty!");
        vpnInterfaceManager.deleteSubnetRouteFibEntryFromDS(rd, subnetIp, vpnName);
        if (isBgpVpn) {
            try {
                bgpManager.withdrawPrefix(rd, subnetIp);
            } catch (Exception e) {
                LOG.error("Fail: Subnet route not withdrawn for rd {} subnetIp {} due to exception",
                        rd, subnetIp, e);
                return false;
            }
        }
        return true;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void electNewDpnForSubnetRoute(SubnetOpDataEntryBuilder subOpBuilder, BigInteger oldDpnId, Uuid subnetId,
                                           Uuid networkId, boolean isBgpVpn) {
        List<SubnetToDpn> subDpnList = null;
        boolean isRouteAdvertised = false;
        subDpnList = subOpBuilder.getSubnetToDpn();
        String rd = subOpBuilder.getVrfId();
        String subnetIp = subOpBuilder.getSubnetCidr();
        String vpnName = subOpBuilder.getVpnName();
        long elanTag = subOpBuilder.getElanTag();
        BigInteger nhDpnId = null;
        String nhTepIp = null;
        boolean isAlternateDpnSelected = false;
        Iterator<SubnetToDpn> subnetDpnIter = subDpnList.iterator();
        long l3vni = 0;
        long label = 0;
        if (VpnUtil.isL3VpnOverVxLan(subOpBuilder.getL3vni())) {
            l3vni = subOpBuilder.getL3vni();
        } else {
            label = getLabel(rd, subnetIp);
            subOpBuilder.setLabel(label);
        }
        if (!isBgpVpn) {
            // Non-BGPVPN as it stands here represents use-case of External Subnets of VLAN-Provider-Network
            //  TODO(Tomer):  Pulling in both external and internal VLAN-Provider-Network need to be
            // blended more better into this design.
            isRouteAdvertised = addSubnetRouteToFib(rd, subnetIp, nhDpnId, nhTepIp,
                    vpnName, elanTag, label, l3vni, subnetId, isBgpVpn, networkId.getValue());
            if (isRouteAdvertised) {
                subOpBuilder.setRouteAdvState(TaskState.Advertised);
            } else {
                LOG.error("electNewDpnForSubnetRoute: Adding FibEntry for rd {} subnet {} failed",
                        rd, subnetId.getValue());
                subOpBuilder.setRouteAdvState(TaskState.PendingAdvertise);
            }
            return;
        }
        while (subnetDpnIter.hasNext()) {
            SubnetToDpn subnetToDpn = subnetDpnIter.next();
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
                    LOG.warn("Unable to find TepIp for rd {} subnetroute subnetip {} for dpnid {}, attempt next",
                            rd, subnetIp, nhDpnId.toString(), e);
                    continue;
                }
            }
        }
        if (!isAlternateDpnSelected) {
            //If no alternate Dpn is selected as nextHopDpn, withdraw the subnetroute if it had a nextHop already.
            if (isRouteAdvertised(subOpBuilder) && (oldDpnId != null)) {
                LOG.info("electNewDpnForSubnetRoute: No alternate DPN available for subnet {}."
                        + "  Prefix withdrawn from BGP", subnetIp);
                // Withdraw route from BGP for this subnet
                boolean routeWithdrawn = deleteSubnetRouteFromFib(rd, subnetIp, vpnName, isBgpVpn);
                subOpBuilder.setNhDpnId(null);
                if (routeWithdrawn) {
                    subOpBuilder.setRouteAdvState(TaskState.Withdrawn);
                } else {
                    LOG.error("electNewDpnForSubnetRoute: Withdrawing NextHopDPN {} for subnet {}"
                            + " from BGP failed", oldDpnId.toString(), subnetId.getValue());
                    subOpBuilder.setRouteAdvState(TaskState.PendingWithdraw);
                }
            }
        } else {
            //If alternate Dpn is selected as nextHopDpn, use that for subnetroute.
            subOpBuilder.setNhDpnId(nhDpnId);
            //update the VRF entry for the subnetroute.
            isRouteAdvertised = addSubnetRouteToFib(rd, subnetIp, nhDpnId, nhTepIp,
                    vpnName, elanTag, label, l3vni, subnetId, isBgpVpn, networkId.getValue());
            if (isRouteAdvertised) {
                subOpBuilder.setRouteAdvState(TaskState.Advertised);
            } else {
                LOG.error("electNewDpnForSubnetRoute: Swapping to add new NextHopDpn {} for rd {} subnet {} failed",
                        nhDpnId, rd, subnetId.getValue());
                subOpBuilder.setRouteAdvState(TaskState.PendingAdvertise);
            }
        }
    }

    private boolean isRouteAdvertised(SubnetOpDataEntryBuilder subOpBuilder) {
        return ((subOpBuilder.getRouteAdvState() == TaskState.Advertised)
                || (subOpBuilder.getRouteAdvState() == TaskState.PendingAdvertise));
    }
}


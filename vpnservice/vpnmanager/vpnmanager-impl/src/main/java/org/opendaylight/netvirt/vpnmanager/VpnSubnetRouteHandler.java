/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.google.common.base.Preconditions;

import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.subnet.to.dpn.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;


public class VpnSubnetRouteHandler implements NeutronvpnListener {
    private static final Logger logger = LoggerFactory.getLogger(VpnSubnetRouteHandler.class);

    private final DataBroker broker;
    private SubnetOpDpnManager subOpDpnManager;
    private final IBgpManager bgpManager;
    private IdManagerService idManager;
    private VpnInterfaceManager vpnInterfaceManager;

    public VpnSubnetRouteHandler(final DataBroker db, IBgpManager bgpManager, VpnInterfaceManager vpnIntfManager) {
        broker = db;
        subOpDpnManager = new SubnetOpDpnManager(broker);
        this.bgpManager = bgpManager;
        this.vpnInterfaceManager = vpnIntfManager;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    @Override
    public void onSubnetAddedToVpn(SubnetAddedToVpn notification) {
        if (!notification.isExternalVpn()) {
            return;
        }

        Uuid subnetId = notification.getSubnetId();
        String vpnName = notification.getVpnName();
        String subnetIp = notification.getSubnetIp();
        Long elanTag = notification.getElanTag();

        Preconditions.checkNotNull(subnetId, "SubnetId cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetPrefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "VpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "ElanTag cannot be null or empty!");

        logger.info("onSubnetAddedToVpn: Subnet " + subnetId.getValue() + " being added to vpn");
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        synchronized (this) {
            try {
                Subnetmap subMap = null;

                // Please check if subnetId belongs to an External Network
                InstanceIdentifier<Subnetmap> subMapid = InstanceIdentifier.builder(Subnetmaps.class).
                        child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
                Optional<Subnetmap> sm = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, subMapid);
                if (!sm.isPresent()) {
                    logger.error("onSubnetAddedToVpn: Unable to retrieve subnetmap entry for subnet : " + subnetId);
                    return;
                }
                subMap = sm.get();
                InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class).
                        child(Networks.class, new NetworksKey(subMap.getNetworkId())).build();
                Optional<Networks> optionalNets = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, netsIdentifier);
                if (optionalNets.isPresent()) {
                    logger.info("onSubnetAddedToVpn: subnet {} is an external subnet on external network {}, so ignoring this for SubnetRoute",
                            subnetId.getValue(), subMap.getNetworkId().getValue());
                    return;
                }
                //Create and add SubnetOpDataEntry object for this subnet to the SubnetOpData container
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).
                        child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(broker,
                        LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (optionalSubs.isPresent()) {
                    logger.error("onSubnetAddedToVpn: SubnetOpDataEntry for subnet " + subnetId.getValue() +
                            " already detected to be present");
                    return;
                }
                logger.debug("onSubnetAddedToVpn: Creating new SubnetOpDataEntry node for subnet: " +  subnetId.getValue());
                Map<BigInteger, SubnetToDpn> subDpnMap = new HashMap<>();
                SubnetOpDataEntry subOpEntry = null;
                BigInteger dpnId = null;
                BigInteger nhDpnId = null;
                SubnetToDpn subDpn = null;

                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder().setKey(new SubnetOpDataEntryKey(subnetId));
                subOpBuilder.setSubnetId(subnetId);
                subOpBuilder.setSubnetCidr(subnetIp);
                String rd = VpnUtil.getVpnRdFromVpnInstanceConfig(broker, vpnName);
                if (rd == null) {
                    logger.error("onSubnetAddedToVpn: The VPN Instance name " + notification.getVpnName() + " does not have RD ");
                    return;
                }
                subOpBuilder.setVrfId(rd);
                subOpBuilder.setVpnName(vpnName);
                subOpBuilder.setSubnetToDpn(new ArrayList<>());
                subOpBuilder.setRouteAdvState(TaskState.Na);
                subOpBuilder.setElanTag(elanTag);

                // First recover set of ports available in this subnet
                List<Uuid> portList = subMap.getPortList();
                if (portList != null) {
                    for (Uuid port: portList) {
                        Interface intfState = InterfaceUtils.getInterfaceStateFromOperDS(broker,port.getValue());
                        if (intfState != null) {
                            dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                            if (dpnId == null) {
                                logger.info("onSubnetAddedToVpn: Port " + port.getValue() + " is not assigned DPN yet, ignoring ");
                                continue;
                            }
                            subOpDpnManager.addPortOpDataEntry(port.getValue(), subnetId, dpnId);
                            if (intfState.getOperStatus() != OperStatus.Up) {
                                logger.info("onSubnetAddedToVpn: Port " + port.getValue() + " is not UP yet, ignoring ");
                                continue;
                            }
                            subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, port.getValue());
                            if (intfState.getOperStatus() == OperStatus.Up) {
                                // port is UP
                                subDpnMap.put(dpnId, subDpn);
                                if (nhDpnId == null) {
                                    nhDpnId = dpnId;
                                }
                            }
                        } else {
                            subOpDpnManager.addPortOpDataEntry(port.getValue(), subnetId, null);
                        }
                    }
                    if (subDpnMap.size() > 0) {
                        subOpBuilder.setSubnetToDpn(new ArrayList<>(subDpnMap.values()));
                    }
                }

                if (nhDpnId != null) {
                    subOpBuilder.setNhDpnId(nhDpnId);
                    try {
                        /*
                        Write the subnet route entry to the FIB.
                        And also advertise the subnet route entry via BGP.
                        */
                        int label = getLabel(rd, subnetIp);
                        addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                        advertiseSubnetRouteToBgp(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                        subOpBuilder.setRouteAdvState(TaskState.Done);
                    } catch (Exception ex) {
                        logger.error("onSubnetAddedToVpn: FIB rules and Advertising nhDpnId " + nhDpnId +
                                " information for subnet " + subnetId.getValue() + " to BGP failed {}", ex);
                        subOpBuilder.setRouteAdvState(TaskState.Pending);
                    }
                } else {
                    try {
                        /*
                        Write the subnet route entry to the FIB.
                        NOTE: Will not advertise to BGP as NextHopDPN is not available yet.
                        */
                        int label = getLabel(rd, subnetIp);
                        addSubnetRouteToFib(rd, subnetIp, null, vpnName, elanTag, label);
                    } catch (Exception ex) {
                        logger.error("onSubnetAddedToVpn: FIB rules writing for subnet {} with exception {} " +
                                subnetId.getValue(), ex);
                        subOpBuilder.setRouteAdvState(TaskState.Pending);
                    }
                }

                subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                logger.info("onSubnetAddedToVpn: Added subnetopdataentry to OP Datastore for subnet " + subnetId.getValue());
            } catch (Exception ex) {
                logger.error("Creation of SubnetOpDataEntry for subnet " +
                        subnetId.getValue() + " failed {}", ex);
            } finally {
            }
        }
    }

    @Override
    public void onSubnetDeletedFromVpn(SubnetDeletedFromVpn notification) {
        Uuid subnetId = notification.getSubnetId();

        if (!notification.isExternalVpn()) {
            return;
        }
        logger.info("onSubnetDeletedFromVpn: Subnet " + subnetId.getValue() + " being removed to vpn");
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        synchronized (this) {
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).
                        child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
                logger.trace(" Removing the SubnetOpDataEntry node for subnet: " +  subnetId.getValue());
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(broker,
                        LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    logger.error("onSubnetDeletedFromVpn: SubnetOpDataEntry for subnet " + subnetId.getValue() +
                            " not available in datastore");
                    return;
                }
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                List<SubnetToDpn> subDpnList = subOpBuilder.getSubnetToDpn();
                for (SubnetToDpn subDpn: subDpnList) {
                    List<VpnInterfaces> vpnIntfList = subDpn.getVpnInterfaces();
                    for (VpnInterfaces vpnIntf: vpnIntfList) {
                        subOpDpnManager.removePortOpDataEntry(vpnIntf.getInterfaceName());
                    }
                }
                //Removing Stale Ports in portOpData
                InstanceIdentifier<Subnetmap> subMapid = InstanceIdentifier.builder(Subnetmaps.class).
                        child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
                Optional<Subnetmap> sm = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, subMapid);
                if (!sm.isPresent()) {
                    logger.error("Stale ports removal: Unable to retrieve subnetmap entry for subnet : " + subnetId);
                }
                Subnetmap subMap = sm.get();
                List<Uuid> portList = subMap.getPortList();
                if(portList!=null){
                    InstanceIdentifier<PortOpData> portOpIdentifier = InstanceIdentifier.builder(PortOpData.class).build();
                    Optional<PortOpData> optionalPortOp = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                    if(!optionalPortOp.isPresent()){
                        logger.error("Stale ports removal: Cannot delete port. Not available in data store");
                        return;
                    } else{
                        PortOpData portOpData = optionalPortOp.get();
                        List<PortOpDataEntry> portOpDataList = portOpData.getPortOpDataEntry();
                        if(portOpDataList!=null){
                            for(PortOpDataEntry portOpDataListEntry :  portOpDataList){
                                if(portList.contains(new Uuid(portOpDataListEntry.getPortId()))){
                                    logger.trace("Removing stale port: " + portOpDataListEntry + "for dissociated subnetId: " + subnetId);
                                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier.
                                            child(PortOpDataEntry.class, new PortOpDataEntryKey(portOpDataListEntry.getKey())));
                                }
                            }
                        }
                    }
                }

                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
                logger.info("onSubnetDeletedFromVpn: Removed subnetopdataentry for subnet {} successfully from Datastore", subnetId.getValue());
                try {
                    //Withdraw the routes for all the interfaces on this subnet
                    //Remove subnet route entry from FIB
                    deleteSubnetRouteFromFib(rd, subnetIp);
                    withdrawSubnetRoutefromBgp(rd, subnetIp);
                } catch (Exception ex) {
                    logger.error("onSubnetAddedToVpn: Withdrawing routes from BGP for subnet " +
                            subnetId.getValue() + " failed {}" + ex);
                }
            } catch (Exception ex) {
                logger.error("Removal of SubnetOpDataEntry for subnet " +
                        subnetId.getValue() + " failed {}" + ex);
            } finally {
            }
        }
    }

    @Override
    public void onSubnetUpdatedInVpn(SubnetUpdatedInVpn notification) {
        Uuid subnetId = notification.getSubnetId();
        String vpnName = notification.getVpnName();
        String subnetIp = notification.getSubnetIp();
        Long elanTag = notification.getElanTag();

        Preconditions.checkNotNull(subnetId, "SubnetId cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetPrefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "VpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "ElanTag cannot be null or empty!");

        InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).
                child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
        Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(broker,
                LogicalDatastoreType.OPERATIONAL,
                subOpIdentifier);
        if (optionalSubs.isPresent()) {
            if (!notification.isExternalVpn()) {
                SubnetDeletedFromVpnBuilder bldr = new SubnetDeletedFromVpnBuilder().setVpnName(vpnName);
                bldr.setElanTag(elanTag).setExternalVpn(true).setSubnetIp(subnetIp).setSubnetId(subnetId);
                onSubnetDeletedFromVpn(bldr.build());
            }
            // TODO(vivek): Something got updated, but we donot know what ?
        } else {
            if (notification.isExternalVpn()) {
                SubnetAddedToVpnBuilder bldr = new SubnetAddedToVpnBuilder().setVpnName(vpnName).setElanTag(elanTag);
                bldr.setSubnetIp(subnetIp).setSubnetId(subnetId).setExternalVpn(true);
                onSubnetAddedToVpn(bldr.build());
            }
            // TODO(vivek): Something got updated, but we donot know what ?
        }
    }

    @Override
    public void onPortAddedToSubnet(PortAddedToSubnet notification) {
        Uuid subnetId = notification.getSubnetId();
        Uuid portId = notification.getPortId();

        logger.info("onPortAddedToSubnet: Port " + portId.getValue() + " being added to subnet " + subnetId.getValue());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        synchronized (this) {
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).
                        child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();

                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    logger.info("onPortAddedToSubnet: Port " + portId.getValue() + " is part of a subnet " + subnetId.getValue() +
                            " that is not in VPN, ignoring");
                    return;
                }
                Interface intfState = InterfaceUtils.getInterfaceStateFromOperDS(broker,portId.getValue());
                if (intfState == null) {
                    // Interface State not yet available
                    subOpDpnManager.addPortOpDataEntry(portId.getValue(), subnetId, null);
                    return;
                }
                BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                if (dpnId == null) {
                    logger.info("onPortAddedToSubnet: Port " + portId.getValue() + " is not assigned DPN yet, ignoring ");
                    return;
                }
                subOpDpnManager.addPortOpDataEntry(portId.getValue(), subnetId, dpnId);
                if (intfState.getOperStatus() != OperStatus.Up) {
                    logger.info("onPortAddedToSubnet: Port " + portId.getValue() + " is not UP yet, ignoring ");
                    return;
                }
                logger.debug("onPortAddedToSubnet: Updating the SubnetOpDataEntry node for subnet: " + subnetId.getValue());
                SubnetToDpn subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, portId.getValue());
                if (subDpn == null) {
                    return;
                }
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                List<SubnetToDpn> subDpnList = subOpBuilder.getSubnetToDpn();
                subDpnList.add(subDpn);
                subOpBuilder.setSubnetToDpn(subDpnList);
                if (subOpBuilder.getNhDpnId()  == null) {
                    subOpBuilder.setNhDpnId(dpnId);
                }
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                Long elanTag = subOpBuilder.getElanTag();
                if ((subOpBuilder.getRouteAdvState() == TaskState.Pending) ||
                        (subOpBuilder.getRouteAdvState() == TaskState.Na)) {
                    try {
                        // Write the Subnet Route Entry to FIB
                        // Advertise BGP Route here and set route_adv_state to DONE
                        int label = getLabel(rd, subnetIp);
                        addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                        advertiseSubnetRouteToBgp(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                        subOpBuilder.setRouteAdvState(TaskState.Done);
                    } catch (Exception ex) {
                        logger.error("onPortAddedToSubnet: Advertising NextHopDPN "+ nhDpnId +
                                " information for subnet " + subnetId.getValue() + " to BGP failed {}", ex);
                    }
                }
                SubnetOpDataEntry subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                logger.info("onPortAddedToSubnet: Updated subnetopdataentry to OP Datastore for port " + portId.getValue());

            } catch (Exception ex) {
                logger.error("Creation of SubnetOpDataEntry for subnet " +
                        subnetId.getValue() + " failed {}", ex);
            } finally {
            }
        }
    }

    @Override
    public void onPortRemovedFromSubnet(PortRemovedFromSubnet notification) {
        Uuid subnetId = notification.getSubnetId();
        Uuid portId = notification.getPortId();

        logger.info("onPortRemovedFromSubnet: Port " + portId.getValue() + " being removed from subnet " + subnetId.getValue());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        synchronized (this) {
            try {
                PortOpDataEntry portOpEntry = subOpDpnManager.removePortOpDataEntry(portId.getValue());
                if (portOpEntry == null) {
                    return;
                }
                BigInteger dpnId = portOpEntry.getDpnId();
                if (dpnId == null) {
                    logger.debug("onPortRemovedFromSubnet:  Port {} does not have a DPNId associated, ignoring", portId.getValue());
                    return;
                }
                logger.debug("onPortRemovedFromSubnet: Updating the SubnetOpDataEntry node for subnet: " +  subnetId.getValue());
                boolean last = subOpDpnManager.removeInterfaceFromDpn(subnetId, dpnId, portId.getValue());
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).
                        child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    logger.info("onPortRemovedFromSubnet: Port " + portId.getValue() + " is part of a subnet " + subnetId.getValue() +
                            " that is not in VPN, ignoring");
                    return;
                }
                SubnetOpDataEntry subOpEntry = null;
                List<SubnetToDpn> subDpnList = null;
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                Long elanTag = subOpBuilder.getElanTag();
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                if ((nhDpnId != null) && (nhDpnId.equals(dpnId))) {
                    // select another NhDpnId
                    if (last) {
                        logger.debug("onPortRemovedFromSubnet: Last port " + portId + " on the subnet: " +  subnetId.getValue());
                        // last port on this DPN, so we need to swap the NHDpnId
                        subDpnList = subOpBuilder.getSubnetToDpn();
                        if (subDpnList.isEmpty()) {
                            subOpBuilder.setNhDpnId(null);
                            try {
                                // withdraw route from BGP
                                deleteSubnetRouteFromFib(rd, subnetIp);
                                withdrawSubnetRoutefromBgp(rd, subnetIp);
                                subOpBuilder.setRouteAdvState(TaskState.Na);
                            } catch (Exception ex) {
                                logger.error("onPortRemovedFromSubnet: Withdrawing NextHopDPN " + dpnId + " information for subnet " +
                                        subnetId.getValue() + " from BGP failed ", ex);
                                subOpBuilder.setRouteAdvState(TaskState.Pending);
                            }
                        } else {
                            nhDpnId = subDpnList.get(0).getDpnId();
                            subOpBuilder.setNhDpnId(nhDpnId);
                            logger.debug("onInterfaceDown: Swapping the Designated DPN to " + nhDpnId + " for subnet " + subnetId.getValue());
                            try {
                                // Best effort Withdrawal of route from BGP for this subnet
                                // Advertise the new NexthopIP to BGP for this subnet
                                //withdrawSubnetRoutefromBgp(rd, subnetIp);
                                int label = getLabel(rd, subnetIp);
                                addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                                advertiseSubnetRouteToBgp(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                                subOpBuilder.setRouteAdvState(TaskState.Done);
                            } catch (Exception ex) {
                                logger.error("onPortRemovedFromSubnet: Swapping Withdrawing NextHopDPN " + dpnId +
                                        " information for subnet " + subnetId.getValue() +
                                        " to BGP failed {}" + ex);
                                subOpBuilder.setRouteAdvState(TaskState.Pending);
                            }
                        }
                    }
                }
                subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                logger.info("onPortRemovedFromSubnet: Updated subnetopdataentry to OP Datastore removing port " + portId.getValue());
            } catch (Exception ex) {
                logger.error("Creation of SubnetOpDataEntry for subnet " +
                        subnetId.getValue() + " failed {}" + ex);
            } finally {
            }
        }
    }

    public void onInterfaceUp(Interface intfState) {

        logger.info("onInterfaceUp: Port " + intfState.getName());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        synchronized (this) {
            SubnetToDpn subDpn = null;
            String intfName = intfState.getName();
            PortOpDataEntry portOpEntry = subOpDpnManager.getPortOpDataEntry(intfName);
            if (portOpEntry == null) {
                logger.info("onInterfaceUp: Port " + intfState.getName()  + "is part of a subnet not in VPN, ignoring");
                return;
            }
            BigInteger dpnId = portOpEntry.getDpnId();
            if (dpnId  == null) {
                dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                if (dpnId == null) {
                    logger.error("onInterfaceUp: Unable to determine the DPNID for port " + intfName);
                    return;
                }
            }
            Uuid subnetId = portOpEntry.getSubnetId();
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).
                        child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    logger.error("onInterfaceUp: SubnetOpDataEntry for subnet " + subnetId.getValue() +
                            " is not available");
                    return;
                }

                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                logger.debug("onInterfaceUp: Updating the SubnetOpDataEntry node for subnet: " +  subnetId.getValue());
                subOpDpnManager.addPortOpDataEntry(intfName, subnetId, dpnId);
                subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, intfName);
                if (subDpn == null) {
                    return;
                }
                List<SubnetToDpn> subDpnList = subOpBuilder.getSubnetToDpn();
                subDpnList.add(subDpn);
                subOpBuilder.setSubnetToDpn(subDpnList);
                if (subOpBuilder.getNhDpnId()  == null) {
                    subOpBuilder.setNhDpnId(dpnId);
                }
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                Long elanTag = subOpBuilder.getElanTag();
                if ((subOpBuilder.getRouteAdvState() == TaskState.Pending) || (subOpBuilder.getRouteAdvState() == TaskState.Na)) {
                    try {
                        // Write the Subnet Route Entry to FIB
                        // Advertise BGP Route here and set route_adv_state to DONE
                        int label = getLabel(rd, subnetIp);
                        addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                        advertiseSubnetRouteToBgp(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                        subOpBuilder.setRouteAdvState(TaskState.Done);
                    } catch (Exception ex) {
                        logger.error("onInterfaceUp: Advertising NextHopDPN " + nhDpnId + " information for subnet " +
                                subnetId.getValue() + " to BGP failed {}" + ex);
                    }
                }
                SubnetOpDataEntry subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                logger.info("onInterfaceUp: Updated subnetopdataentry to OP Datastore port up " + intfName);
            } catch (Exception ex) {
                logger.error("Creation of SubnetOpDataEntry for subnet " +
                        subnetId.getValue() + " failed {}" + ex);
            } finally {
            }
        }
    }

    public void onInterfaceDown(Interface intfState) {
        logger.info("onInterfaceDown: Port " + intfState.getName());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        synchronized (this) {
            String intfName = intfState.getName();
            PortOpDataEntry portOpEntry = subOpDpnManager.getPortOpDataEntry(intfName);
            if (portOpEntry == null) {
                logger.info("onInterfaceDown: Port " + intfState.getName()  + "is part of a subnet not in VPN, ignoring");
                return;
            }
            BigInteger dpnId = portOpEntry.getDpnId();
            if (dpnId  == null) {
                dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                if (dpnId == null) {
                    logger.error("onInterfaceDown: Unable to determine the DPNID for port " + intfName);
                    return;
                }
            }
            Uuid subnetId = portOpEntry.getSubnetId();
            try {
                logger.debug("onInterfaceDown: Updating the SubnetOpDataEntry node for subnet: " +  subnetId.getValue());
                boolean last = subOpDpnManager.removeInterfaceFromDpn(subnetId, dpnId, intfName);
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).
                        child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(broker,
                        LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    logger.error("onInterfaceDown: SubnetOpDataEntry for subnet " + subnetId.getValue() +
                            " is not available");
                    return;
                }
                SubnetOpDataEntry subOpEntry = null;
                List<SubnetToDpn> subDpnList = null;
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                Long elanTag = subOpBuilder.getElanTag();
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                if ((nhDpnId != null) && (nhDpnId.equals(dpnId))) {
                    // select another NhDpnId
                    if (last) {
                        logger.debug("onInterfaceDown: Last active port " + intfState.getName() + " on the subnet: " +  subnetId.getValue());
                        // last port on this DPN, so we need to swap the NHDpnId
                        subDpnList = subOpBuilder.getSubnetToDpn();
                        if (subDpnList.isEmpty()) {
                            subOpBuilder.setNhDpnId(null);
                            try {
                                // Withdraw route from BGP for this subnet
                                deleteSubnetRouteFromFib(rd, subnetIp);
                                withdrawSubnetRoutefromBgp(rd, subnetIp);
                                subOpBuilder.setRouteAdvState(TaskState.Na);
                            } catch (Exception ex) {
                                logger.error("onInterfaceDown: Withdrawing NextHopDPN " + dpnId + " information for subnet " +
                                        subnetId.getValue() + " from BGP failed {}" + ex);
                                subOpBuilder.setRouteAdvState(TaskState.Pending);
                            }
                        } else {
                            nhDpnId = subDpnList.get(0).getDpnId();
                            subOpBuilder.setNhDpnId(nhDpnId);
                            logger.debug("onInterfaceDown: Swapping the Designated DPN to " + nhDpnId + " for subnet " + subnetId.getValue());
                            try {
                                // Best effort Withdrawal of route from BGP for this subnet
                                //withdrawSubnetRoutefromBgp(rd, subnetIp);
                                int label = getLabel(rd, subnetIp);
                                addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                                advertiseSubnetRouteToBgp(rd, subnetIp, nhDpnId, vpnName, elanTag, label);
                                subOpBuilder.setRouteAdvState(TaskState.Done);
                            } catch (Exception ex) {
                                logger.error("onInterfaceDown: Swapping Withdrawing NextHopDPN " + dpnId + " information for subnet " +
                                        subnetId.getValue() + " to BGP failed {}" + ex);
                                subOpBuilder.setRouteAdvState(TaskState.Pending);
                            }
                        }
                    }
                }
                subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                logger.info("onInterfaceDown: Updated subnetopdataentry to OP Datastore port down " + intfName);
            } catch (Exception ex) {
                logger.error("Creation of SubnetOpDataEntry for subnet " +
                        subnetId.getValue() + " failed {}" + ex);
            } finally {
            }
        }
    }

    private void addSubnetRouteToFib(String rd, String subnetIp, BigInteger nhDpnId, String vpnName,
                                     Long elanTag, int label) {
        Preconditions.checkNotNull(rd, "RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetRouteIp cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "vpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "elanTag cannot be null or empty!");
        String nexthopIp = null;
        if (nhDpnId != null) {
            nexthopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, nhDpnId);
        }
        vpnInterfaceManager.addSubnetRouteFibEntryToDS(rd, subnetIp, nexthopIp, label, elanTag);
    }

    private int getLabel(String rd, String subnetIp) {
        int label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                VpnUtil.getNextHopLabelKey(rd, subnetIp));
        logger.trace("Allocated subnetroute label {} for rd {} prefix {}", label, rd, subnetIp);
        return label;
    }

    private void deleteSubnetRouteFromFib(String rd, String subnetIp) {
        Preconditions.checkNotNull(rd, "RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetRouteIp cannot be null or empty!");
        vpnInterfaceManager.removeFibEntryFromDS(rd, subnetIp);
    }

    private void advertiseSubnetRouteToBgp(String rd, String subnetIp, BigInteger nhDpnId, String vpnName,
                                           Long elanTag, int label) throws Exception {
        Preconditions.checkNotNull(rd, "RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetRouteIp cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "elanTag cannot be null or empty!");
        Preconditions.checkNotNull(nhDpnId, "nhDpnId cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "vpnName cannot be null or empty!");
        String nexthopIp = null;
        nexthopIp = InterfaceUtils.getEndpointIpAddressForDPN(broker, nhDpnId);
        if (nexthopIp == null) {
            logger.error("createSubnetRouteInVpn: Unable to obtain endpointIp address for DPNId " + nhDpnId);
            throw new Exception("Unable to obtain endpointIp address for DPNId " + nhDpnId);
        }
        try {
            // BGPManager (inside ODL) requires a withdraw followed by advertise
            // due to bugs with ClusterDataChangeListener used by BGPManager.
            bgpManager.withdrawPrefix(rd, subnetIp);
            bgpManager.advertisePrefix(rd, subnetIp, nexthopIp, label);
        } catch (Exception e) {
            logger.error("Subnet route not advertised for rd " + rd + " failed ", e);
            throw e;
        }
    }

    private void withdrawSubnetRoutefromBgp(String rd, String subnetIp) throws Exception {
        Preconditions.checkNotNull(rd, "RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetIp cannot be null or empty!");
        try {
            bgpManager.withdrawPrefix(rd, subnetIp);
        } catch (Exception e) {
            logger.error("Subnet route not advertised for rd " + rd + " failed ", e);
            throw e;
        }
    }

    @Override
    public void onRouterAssociatedToVpn(RouterAssociatedToVpn notification) {
    }

    @Override
    public void onRouterDisassociatedFromVpn(RouterDisassociatedFromVpn notification) {

    }
}


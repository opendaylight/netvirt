/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.liblldp.PacketException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.NoMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketInReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class ElanPacketInHandler implements PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(ElanPacketInHandler.class);

    private final DataBroker broker;
    private final IInterfaceManager interfaceManager;
    private final ElanUtils elanUtils;
    private final ElanL2GatewayUtils elanL2GatewayUtils;

    public ElanPacketInHandler(DataBroker dataBroker, final IInterfaceManager interfaceManager, ElanUtils elanUtils) {
        broker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.elanUtils = elanUtils;
        this.elanL2GatewayUtils = elanUtils.getElanL2GatewayUtils();
    }

    @Override
    public void onPacketReceived(PacketReceived notification) {
        Class<? extends PacketInReason> pktInReason = notification.getPacketInReason();
        short tableId = notification.getTableId().getValue();
        if (pktInReason == NoMatch.class && tableId == NwConstants.ELAN_SMAC_TABLE) {
            ElanManagerCounters.unknown_smac_pktin_rcv.inc();
            try {
                byte[] data = notification.getPayload();
                Ethernet res = new Ethernet();

                res.deserialize(data, 0, data.length * NetUtils.NumBitsInAByte);

                byte[] srcMac = res.getSourceMACAddress();
                final String macAddress = NWUtil.toStringMacAddress(srcMac);
                final BigInteger metadata = notification.getMatch().getMetadata().getMetadata();
                final long elanTag = MetaDataUtil.getElanTagFromMetadata(metadata);

                long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();

                Optional<IfIndexInterface> interfaceInfoOp = elanUtils.getInterfaceInfoByInterfaceTag(portTag);
                if (!interfaceInfoOp.isPresent()) {
                    LOG.warn("There is no interface for given portTag {}", portTag);
                    return;
                }
                String interfaceName = interfaceInfoOp.get().getInterfaceName();
                LOG.debug("Received a packet with srcMac: {} ElanTag: {} PortTag: {} InterfaceName: {}", macAddress,
                        elanTag, portTag, interfaceName);
                ElanTagName elanTagName = elanUtils.getElanInfoByElanTag(elanTag);
                if (elanTagName == null) {
                    LOG.warn("not able to find elanTagName in elan-tag-name-map for elan tag {}", elanTag);
                    return;
                }
                String elanName = elanTagName.getName();
                PhysAddress physAddress = new PhysAddress(macAddress);
                ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanName);
                InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
                MacEntry macEntry = elanUtils.getInterfaceMacEntriesOperationalDataPath(interfaceName, physAddress);
                if (didMacMigrated(interfaceName, macEntry)) {
                    return;
                }

                BigInteger timeStamp = new BigInteger(String.valueOf(System.currentTimeMillis()));
                MacEntry newMacEntry = new MacEntryBuilder().setInterface(interfaceName).setMacAddress(physAddress)
                        .setKey(new MacEntryKey(physAddress)).setControllerLearnedForwardingEntryTimestamp(timeStamp)
                        .setIsStaticAddress(false).build();

                final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                enqueueJobForMacSpecificTasks(macAddress, elanTag, interfaceName, elanName, physAddress, newMacEntry,
                        portDataStoreCoordinator);

                enqueueJobForDPNSpecificTasks(macAddress, elanTag, interfaceName, physAddress, elanInstance,
                        interfaceInfo, macEntry, newMacEntry, portDataStoreCoordinator);


            } catch (PacketException e) {
                LOG.error("Failed to decode packet: {}", notification, e);
            }
        }
    }

    /**
     * The code currently has a bug, and this condition will never be true.
     * I am preserving the same logic.
     */
    private boolean didMacMigrated(String interfaceName, MacEntry macEntry) {
        if (macEntry != null && !macEntry.getInterface().equals(interfaceName)) {
            long macTimeStamp = macEntry.getControllerLearnedForwardingEntryTimestamp().longValue();
            if (System.currentTimeMillis() < macTimeStamp + 10000) {
                // New FEs flood their packets on all interfaces. This
                // can lead
                // to many contradicting packet_ins. Ignore all packets
                // received
                // within 1s after the first packet_in
                ElanManagerCounters.unknown_smac_pktin_mac_migration_ignored_due_to_protection.inc();
                return true;
            }
        }
        return false;
    }

    private void enqueueJobForMacSpecificTasks(final String macAddress, final long elanTag, String interfaceName,
            String elanName, PhysAddress physAddress,
            MacEntry newMacEntry, final DataStoreJobCoordinator portDataStoreCoordinator) {
        portDataStoreCoordinator.enqueueJob(ElanUtils.getElanMacKey(elanTag, macAddress), () -> {
            MacEntry macEntry = elanUtils.getInterfaceMacEntriesOperationalDataPath(interfaceName, physAddress);
            WriteTransaction writeTx = broker.newWriteOnlyTransaction();
            if (macEntry != null && macEntry.getInterface().equals(interfaceName)) {
                ElanManagerCounters.unknown_smac_pktin_forwarding_entries_removed.inc();
            } else if (macEntry != null) {
                // MAC address has moved. Overwrite the mapping and replace
                // MAC flows
                ElanManagerCounters.unknown_smac_pktin_removed_for_relearned.inc();
            }
            /*
             * Protection time expired. Even though the MAC has been learnt (it is in the cache) the packets are punted
             * to controller. Which means, the the flows were not successfully created in the DPN, but the MAC entry has
             * been added successfully in the cache.
             *
             * So, the cache has to be cleared and the flows and cache should be recreated (clearing of cache is
             * required so that the timestamp is updated).
             */

            InstanceIdentifier<MacEntry> elanMacEntryId =
                    ElanUtils.getMacEntryOperationalDataPath(elanName, physAddress);
            writeTx.put(LogicalDatastoreType.OPERATIONAL, elanMacEntryId, newMacEntry, true);
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeTx.submit());
            return futures;
        });
    }

    private void enqueueJobForDPNSpecificTasks(final String macAddress, final long elanTag, String interfaceName,
            PhysAddress physAddress, ElanInstance elanInstance, InterfaceInfo interfaceInfo, MacEntry macEntry,
            MacEntry newMacEntry, final DataStoreJobCoordinator portDataStoreCoordinator) {
        portDataStoreCoordinator
                .enqueueJob(ElanUtils.getElanMacDPNKey(elanTag, macAddress, interfaceInfo.getDpId()), () -> {

                    macMigrationFlowsCleanup(interfaceName, elanInstance, macEntry);
                    boolean isVlanOrFlatProviderIface = interfaceManager.isExternalInterface(interfaceName);

                    BigInteger dpId = interfaceManager.getDpnForInterface(interfaceName);
                    elanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanInstance.getElanInstanceName(), dpId,
                            Collections.singletonList(physAddress));

                    ElanManagerCounters.unknown_smac_pktin_learned.inc();

                    WriteTransaction flowWritetx = broker.newWriteOnlyTransaction();
                    elanUtils.setupMacFlows(elanInstance, interfaceInfo, elanInstance.getMacTimeout(),
                            macAddress, !isVlanOrFlatProviderIface, flowWritetx);
                    InstanceIdentifier<MacEntry> macEntryId =
                            ElanUtils.getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
                    flowWritetx.put(LogicalDatastoreType.OPERATIONAL, macEntryId, newMacEntry, true);

                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(flowWritetx.submit());
                    return futures;
                });
    }

    /**
     *  This condition is never true because of a bug. Preserving the logic.
     */
    private void macMigrationFlowsCleanup(String interfaceName, ElanInstance elanInstance, MacEntry macEntry) {
        if (macEntry != null && !macEntry.getInterface().equals(interfaceName)) {
            tryAndRemoveInvalidMacEntry(elanInstance.getElanInstanceName(), macEntry);
            ElanManagerCounters.unknown_smac_pktin_flows_removed_for_relearned.inc();
        }
    }

    /*
     * Though this method is a little costlier because it uses try-catch
     * construct, it is used only in rare scenarios like MAC movement or invalid
     * Static MAC having been added on a wrong ELAN.
     */
    private void tryAndRemoveInvalidMacEntry(String elanName, MacEntry macEntry) {
        ElanInstance elanInfo = ElanUtils.getElanInstanceByName(broker, elanName);
        if (elanInfo == null) {
            LOG.warn("MAC {} is been added (either statically or dynamically) for an invalid Elan {}. "
                    + "Manual cleanup may be necessary", macEntry.getMacAddress(), elanName);
            return;
        }

        InterfaceInfo oldInterfaceLport = interfaceManager.getInterfaceInfo(macEntry.getInterface());
        if (oldInterfaceLport == null) {
            LOG.warn("MAC {} is been added (either statically or dynamically) on an invalid Logical Port {}. "
                     + "Manual cleanup may be necessary",
                     macEntry.getMacAddress(), macEntry.getInterface());
            return;
        }
        WriteTransaction flowDeletetx = broker.newWriteOnlyTransaction();
        elanUtils.deleteMacFlows(elanInfo, oldInterfaceLport, macEntry, flowDeletetx);
        flowDeletetx.submit();
        elanL2GatewayUtils.removeMacsFromElanExternalDevices(elanInfo,
                Collections.singletonList(macEntry.getMacAddress()));
    }

}

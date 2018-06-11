/*
 * Copyright (c) 2016, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.openflowplugin.libraries.liblldp.NetUtils;
import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
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

@Singleton
public class ElanPacketInHandler implements PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(ElanPacketInHandler.class);

    private final ManagedNewTransactionRunner txRunner;
    private final IInterfaceManager interfaceManager;
    private final ElanUtils elanUtils;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final EvpnUtils evpnUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanManagerCounters elanManagerCounters;

    @Inject
    public ElanPacketInHandler(DataBroker dataBroker, final IInterfaceManager interfaceManager, ElanUtils elanUtils,
            EvpnUtils evpnUtils, ElanL2GatewayUtils elanL2GatewayUtils, JobCoordinator jobCoordinator,
            ElanInstanceCache elanInstanceCache, ElanManagerCounters elanManagerCounters) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interfaceManager = interfaceManager;
        this.elanUtils = elanUtils;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.evpnUtils = evpnUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
        this.elanManagerCounters = elanManagerCounters;
    }

    @Override
    public void onPacketReceived(PacketReceived notification) {
        Class<? extends PacketInReason> pktInReason = notification.getPacketInReason();
        short tableId = notification.getTableId().getValue();
        if (pktInReason == NoMatch.class && tableId == NwConstants.ELAN_SMAC_TABLE) {
            elanManagerCounters.unknownSmacPktinRcv();
            try {
                byte[] data = notification.getPayload();
                Ethernet res = new Ethernet();

                res.deserialize(data, 0, data.length * NetUtils.NUM_BITS_IN_A_BYTE);

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
                ElanInterfaceMac elanInterfaceMac = elanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
                if (elanInterfaceMac == null) {
                    LOG.info("There is no ElanInterfaceForwardingEntryDS created for interface :{}", interfaceName);
                    return;
                }
                String elanName = elanTagName.getName();
                PhysAddress physAddress = new PhysAddress(macAddress);
                MacEntry oldMacEntry = elanUtils.getMacEntryForElanInstance(elanName, physAddress).orNull();
                boolean isVlanOrFlatProviderIface = interfaceManager.isExternalInterface(interfaceName);

                Optional<IpAddress> srcIpAddress = elanUtils.getSourceIpAddress(res);
                MacEntry newMacEntry = null;
                BigInteger timeStamp = new BigInteger(String.valueOf(System.currentTimeMillis()));
                if (!srcIpAddress.isPresent()) {
                    newMacEntry = new MacEntryBuilder().setInterface(interfaceName).setMacAddress(physAddress)
                            .withKey(new MacEntryKey(physAddress))
                            .setControllerLearnedForwardingEntryTimestamp(timeStamp)
                            .setIsStaticAddress(false).build();
                } else {
                    newMacEntry = new MacEntryBuilder().setInterface(interfaceName).setMacAddress(physAddress)
                            .setIpPrefix(srcIpAddress.get()).withKey(new MacEntryKey(physAddress))
                            .setControllerLearnedForwardingEntryTimestamp(timeStamp)
                            .setIsStaticAddress(false).build();
                }
                if (srcIpAddress.isPresent()) {
                    String prefix = srcIpAddress.get().getIpv4Address().getValue();
                    InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
                    ElanInstance elanInstance = elanInstanceCache.get(elanName).orNull();
                    evpnUtils.advertisePrefix(elanInstance, macAddress, prefix, interfaceName, interfaceInfo.getDpId());
                }
                enqueueJobForMacSpecificTasks(macAddress, elanTag, interfaceName, elanName, physAddress, oldMacEntry,
                        newMacEntry, isVlanOrFlatProviderIface);

                ElanInstance elanInstance = elanInstanceCache.get(elanName).orNull();
                InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
                if (interfaceInfo == null) {
                    LOG.trace("Interface:{} is not present under Config DS", interfaceName);
                    return;
                }
                enqueueJobForDPNSpecificTasks(macAddress, elanTag, interfaceName, physAddress, elanInstance,
                        interfaceInfo, oldMacEntry, newMacEntry, isVlanOrFlatProviderIface);


            } catch (PacketException e) {
                LOG.error("Failed to decode packet: {}", notification, e);
            }
        }
    }

    private void enqueueJobForMacSpecificTasks(final String macAddress, final long elanTag, String interfaceName,
                                               String elanName, PhysAddress physAddress,
                                               MacEntry oldMacEntry, MacEntry newMacEntry,
                                               final boolean isVlanOrFlatProviderIface) {
        jobCoordinator.enqueueJob(ElanUtils.getElanMacKey(elanTag, macAddress),
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                if (oldMacEntry != null && oldMacEntry.getInterface().equals(interfaceName)) {
                    // This should never occur because of ovs temporary mac learning
                    elanManagerCounters.unknownSmacPktinForwardingEntriesRemoved();
                } else if (oldMacEntry != null && !isVlanOrFlatProviderIface) {
                    long macTimeStamp = oldMacEntry.getControllerLearnedForwardingEntryTimestamp().longValue();
                    if (System.currentTimeMillis() > macTimeStamp + 1000) {
                        InstanceIdentifier<MacEntry> macEntryId = ElanUtils
                                .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName,
                                        physAddress);
                        tx.delete(LogicalDatastoreType.OPERATIONAL, macEntryId);
                    } else {
                        // New FEs flood their packets on all interfaces. This can lead
                        // to many contradicting packet_ins. Ignore all packets received
                        // within 1s after the first packet_in
                        elanManagerCounters.unknownSmacPktinMacMigrationIgnoredDueToProtection();
                    }
                } else if (oldMacEntry != null) {
                    elanManagerCounters.unknownSmacPktinRemovedForRelearned();
                }
                // This check is required only to update elan-forwarding-tables when mac is learned
                // in ports (example: VM interfaces) other than on vlan provider port.
                if (!isVlanOrFlatProviderIface && oldMacEntry == null) {
                    InstanceIdentifier<MacEntry> elanMacEntryId =
                            ElanUtils.getMacEntryOperationalDataPath(elanName, physAddress);
                    tx.put(LogicalDatastoreType.OPERATIONAL, elanMacEntryId, newMacEntry,
                            WriteTransaction.CREATE_MISSING_PARENTS);
                }
            })));
    }

    private void enqueueJobForDPNSpecificTasks(final String macAddress, final long elanTag, String interfaceName,
                                               PhysAddress physAddress, ElanInstance elanInstance,
                                               InterfaceInfo interfaceInfo, MacEntry oldMacEntry,
                                               MacEntry newMacEntry, boolean isVlanOrFlatProviderIface) {
        jobCoordinator.enqueueJob(ElanUtils.getElanMacDPNKey(elanTag, macAddress, interfaceInfo.getDpId()), () -> {
            macMigrationFlowsCleanup(interfaceName, elanInstance, oldMacEntry, isVlanOrFlatProviderIface);
            BigInteger dpId = interfaceManager.getDpnForInterface(interfaceName);
            elanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanInstance.getElanInstanceName(), dpId,
                    Collections.singletonList(physAddress));
            elanManagerCounters.unknownSmacPktinLearned();
            return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                elanUtils.setupMacFlows(elanInstance, interfaceInfo, elanInstance.getMacTimeout(),
                        macAddress, !isVlanOrFlatProviderIface, tx);
                InstanceIdentifier<MacEntry> macEntryId =
                        ElanUtils.getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
                tx.put(LogicalDatastoreType.OPERATIONAL, macEntryId, newMacEntry,
                        WriteTransaction.CREATE_MISSING_PARENTS);
            }));
        });
    }

    private void macMigrationFlowsCleanup(String interfaceName, ElanInstance elanInstance, MacEntry macEntry,
                                          boolean isVlanOrFlatProviderIface) {
        if (macEntry != null && !macEntry.getInterface().equals(interfaceName)
                && !isVlanOrFlatProviderIface) {
            tryAndRemoveInvalidMacEntry(elanInstance.getElanInstanceName(), macEntry);
            elanManagerCounters.unknownSmacPktinFlowsRemovedForRelearned();
        }
    }

    /*
     * Though this method is a little costlier because it uses try-catch
     * construct, it is used only in rare scenarios like MAC movement or invalid
     * Static MAC having been added on a wrong ELAN.
     */
    private void tryAndRemoveInvalidMacEntry(String elanName, MacEntry macEntry) {
        ElanInstance elanInfo = elanInstanceCache.get(elanName).orNull();
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
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> elanUtils.deleteMacFlows(elanInfo, oldInterfaceLport, macEntry, tx)), LOG,
            "Error deleting invalid MAC entry");
        elanL2GatewayUtils.removeMacsFromElanExternalDevices(elanInfo,
                Collections.singletonList(macEntry.getMacAddress()));
    }

}

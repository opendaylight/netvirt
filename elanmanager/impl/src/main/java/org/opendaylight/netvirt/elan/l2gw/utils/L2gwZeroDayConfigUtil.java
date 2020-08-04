/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSetBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class L2gwZeroDayConfigUtil {

    public static final String ZERO_DAY_LS_NAME = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA";
    private static final String ZERO_DAY_LS_VNI = "65535";

    L2GatewayCache l2GatewayCache;

    @Inject
    public L2gwZeroDayConfigUtil(L2GatewayCache l2GatewayCache) {
        this.l2GatewayCache = l2GatewayCache;
    }

    public void createZeroDayConfig(ReadWriteTransaction tx,
                                    InstanceIdentifier<Node> dstPsPath,
                                    L2GatewayDevice l2GatewayDevice,
                                    Collection<TransportZone> zones) {
        writeZeroDayLogicalSwitch(dstPsPath, tx, true);
        writeMcastsForZeroDayConfig(dstPsPath, l2GatewayDevice, zones, tx, true);
    }

    public void deleteZeroDayConfig(ReadWriteTransaction tx,
                                    InstanceIdentifier<Node> dstPsPath,
                                    L2GatewayDevice l2GatewayDevice) {
        writeZeroDayLogicalSwitch(dstPsPath, tx, false);
        writeMcastsForZeroDayConfig(dstPsPath, l2GatewayDevice, Collections.emptyList(), tx, false);
    }

    private List<IpAddress> getDpnTeps(Collection<TransportZone> zones) {
        return zones.stream()
                .filter(zone -> zone.getSubnets() != null)
                .flatMap(zone -> zone.getSubnets().stream())
                .filter(subnet -> subnet.getVteps() != null)
                .flatMap(subnet -> subnet.getVteps().stream())
                .filter(vtep -> vtep.getIpAddress() != null)
                .map(vtep -> vtep.getIpAddress())
                .collect(Collectors.toList());
    }

    private List<IpAddress> getOtherTorTeps(L2GatewayDevice l2GatewayDevice) {
        return l2GatewayCache.getAll().stream()
                .filter(device -> !device.getDeviceName().equals(l2GatewayDevice.getDeviceName()))
                .filter(device -> device.getTunnelIp() != null)
                .map(device -> device.getTunnelIp())
                .collect(Collectors.toList());
    }

    private void writeMcastsForZeroDayConfig(InstanceIdentifier<Node> dstPath,
                                             L2GatewayDevice l2GatewayDevice,
                                             Collection<TransportZone> zones,
                                             ReadWriteTransaction tx,
                                             boolean add) {
        List<IpAddress> otherTorTeps = getOtherTorTeps(l2GatewayDevice);
        List<IpAddress> dpnsTepIps = getDpnTeps(zones);
        ArrayList<IpAddress> remoteTepIps = new ArrayList<>(dpnsTepIps);
        remoteTepIps.addAll(otherTorTeps);

        List<LocatorSet> locators = new ArrayList<>();
        NodeId nodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
        if (add) {
            for (IpAddress tepIp : remoteTepIps) {
                HwvtepPhysicalLocatorAugmentation phyLocatorAug = HwvtepSouthboundUtils
                        .createHwvtepPhysicalLocatorAugmentation(String.valueOf(tepIp.getValue()));
                HwvtepPhysicalLocatorRef phyLocRef = new HwvtepPhysicalLocatorRef(
                        HwvtepSouthboundUtils.createPhysicalLocatorInstanceIdentifier(nodeId, phyLocatorAug));
                locators.add(new LocatorSetBuilder().setLocatorRef(phyLocRef).build());
            }
        }
        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(ZERO_DAY_LS_NAME)));
        RemoteMcastMacs remoteMcastMac = new RemoteMcastMacsBuilder()
                .setMacEntryKey(new MacAddress(ElanConstants.UNKNOWN_DMAC)).setLogicalSwitchRef(lsRef)
                .setLocatorSet(locators).build();
        InstanceIdentifier<RemoteMcastMacs> iid = HwvtepSouthboundUtils.createRemoteMcastMacsInstanceIdentifier(nodeId,
                remoteMcastMac.getKey());
        if (add) {
            tx.put(CONFIGURATION, iid, remoteMcastMac, true);
        } else {
            tx.delete(CONFIGURATION, iid);
        }
    }

    private void writeZeroDayLogicalSwitch(InstanceIdentifier<Node> dstPsPath, ReadWriteTransaction tx, boolean add) {

        String vniToUse = System.getProperty("zero.day.ls.vni");
        vniToUse = (vniToUse == null) ? ZERO_DAY_LS_VNI : vniToUse;
        LogicalSwitches logicalSwitch = new LogicalSwitchesBuilder()
                .setHwvtepNodeName(new HwvtepNodeName(ZERO_DAY_LS_NAME))
                .setTunnelKey(vniToUse)
                .build();
        InstanceIdentifier<LogicalSwitches> path = dstPsPath
                .augmentation(HwvtepGlobalAugmentation.class)
                .child(LogicalSwitches.class, new LogicalSwitchesKey(logicalSwitch.getKey()));
        if (add) {
            tx.put(LogicalDatastoreType.CONFIGURATION, path, logicalSwitch, true);
        } else {
            tx.delete(LogicalDatastoreType.CONFIGURATION, path);
        }
    }
}
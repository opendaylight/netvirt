/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.nodehandlertest;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeVxlanOverIpv4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.ManagersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.ManagersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.managers.ManagerOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.managers.ManagerOtherConfigsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.managers.ManagerOtherConfigsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSetBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Created by ekvsver on 8/6/2016.
 */
public final class TestBuilders {
    private TestBuilders() {

    }

    public static HwvtepLogicalSwitchRef buildLogicalSwitchesRef(InstanceIdentifier<Node> nodeIid,
                                                                 String logicalSwitchName) {
        InstanceIdentifier<LogicalSwitches> logicalSwitchInstanceIid =
                nodeIid.augmentation(HwvtepGlobalAugmentation.class)
                .child(LogicalSwitches.class, new LogicalSwitchesKey(new HwvtepNodeName(logicalSwitchName)));
        return new HwvtepLogicalSwitchRef(logicalSwitchInstanceIid);
    }

    public static LocalUcastMacs buildLocalUcastMacs(InstanceIdentifier<Node> nodeIid, String vmMac,
                                                     String vmip, String tepIp, String logicalSwitchName) {
        LocalUcastMacsBuilder ucmlBuilder = new LocalUcastMacsBuilder();
        ucmlBuilder.setIpaddr(IpAddressBuilder.getDefaultInstance(vmip));
        ucmlBuilder.setMacEntryKey(new MacAddress(vmMac));
        ucmlBuilder.setMacEntryUuid(getUUid(vmMac));
        ucmlBuilder.setLocatorRef(buildLocatorRef(nodeIid, tepIp));
        ucmlBuilder.setLogicalSwitchRef(buildLogicalSwitchesRef(nodeIid, logicalSwitchName));
        return ucmlBuilder.build();
    }

    public static RemoteUcastMacs buildRemoteUcastMacs(InstanceIdentifier<Node> nodeIid, String vmMac,
                                                       String vmip, String tepIp, String logicalSwitchName) {
        RemoteUcastMacsBuilder ucmlBuilder = new RemoteUcastMacsBuilder();
        ucmlBuilder.setIpaddr(IpAddressBuilder.getDefaultInstance(vmip));
        ucmlBuilder.setMacEntryKey(new MacAddress(vmMac));
        ucmlBuilder.setMacEntryUuid(getUUid(vmMac));
        ucmlBuilder.setLocatorRef(buildLocatorRef(nodeIid, tepIp));
        ucmlBuilder.setLogicalSwitchRef(buildLogicalSwitchesRef(nodeIid, logicalSwitchName));
        return ucmlBuilder.build();
    }

    public static TerminationPoint buildTerminationPoint(InstanceIdentifier<Node> nodeIid, String ip) {
        TerminationPointKey tpKey = new TerminationPointKey(new TpId("vxlan_over_ipv4:" + ip));
        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        if (nodeIid != null && tpKey != null) {
            tpBuilder.withKey(tpKey);
            tpBuilder.setTpId(tpKey.getTpId());
            InstanceIdentifier<TerminationPoint> tpPath =
                    buildTpId(nodeIid, ip);
            HwvtepPhysicalLocatorAugmentationBuilder tpAugmentationBuilder =
                    new HwvtepPhysicalLocatorAugmentationBuilder();
            tpAugmentationBuilder.setPhysicalLocatorUuid(getUUid(ip));
            tpAugmentationBuilder.setEncapsulationType(createEncapsulationType("vxlan_over_ipv4"));
            tpAugmentationBuilder.setDstIp(IpAddressBuilder.getDefaultInstance(ip));
            tpBuilder.addAugmentation(tpAugmentationBuilder.build());
        }
        return tpBuilder.build();
    }

    public static LogicalSwitches buildLogicalSwitch(String logicalSwitch, String tunnelKey) {
        LogicalSwitchesBuilder logicalSwitchesBuilder = new LogicalSwitchesBuilder();
        logicalSwitchesBuilder.withKey(new LogicalSwitchesKey(new HwvtepNodeName(logicalSwitch)));
        logicalSwitchesBuilder.setHwvtepNodeName(new HwvtepNodeName(logicalSwitch));
        logicalSwitchesBuilder.setTunnelKey(tunnelKey);
        Uuid lgoicalSwitchUuid = getUUid(logicalSwitch);
        logicalSwitchesBuilder.setLogicalSwitchUuid(lgoicalSwitchUuid);
        return logicalSwitchesBuilder.build();
    }

    public static LocalMcastMacs buildLocalMcastMacs(InstanceIdentifier<Node> iid, String mac,
                                                     String logicalSwitchName, String tepIp) {
        LocalMcastMacsBuilder localMcastMacsBuilder = new LocalMcastMacsBuilder();
        if (mac.equals("unknown-dst")) {
            localMcastMacsBuilder.setMacEntryKey(new MacAddress("00:00:00:00:00:00"));
        } else {
            localMcastMacsBuilder.setMacEntryKey(new MacAddress(mac));
        }
        localMcastMacsBuilder.setMacEntryUuid(getUUid(mac));
        //mMacLocalBuilder.setIpaddr(new IpAddress(ip.toCharArray()));
        localMcastMacsBuilder.setLogicalSwitchRef(buildLogicalSwitchesRef(iid, logicalSwitchName));
        List<LocatorSet> locatorSets = new ArrayList<>();
        locatorSets.add(new LocatorSetBuilder().setLocatorRef(
                buildLocatorRef(iid, tepIp)).build());
        localMcastMacsBuilder.setLocatorSet(locatorSets);

        return localMcastMacsBuilder.build();
    }

    public static RemoteMcastMacs buildRemoteMcastMacs(InstanceIdentifier<Node> iid, String mac,
                                                       String logicalSwitchName, String [] tepIps) {

        RemoteMcastMacsBuilder remoteMcastMacsBuilder = new RemoteMcastMacsBuilder();
        if (mac.equals("unknown-dst")) {
            remoteMcastMacsBuilder.setMacEntryKey(new MacAddress("00:00:00:00:00:00"));
        } else {
            remoteMcastMacsBuilder.setMacEntryKey(new MacAddress(mac));
        }
        remoteMcastMacsBuilder.setMacEntryUuid(getUUid(mac));
        //mMacLocalBuilder.setIpaddr(new IpAddress(ip.toCharArray()));
        remoteMcastMacsBuilder.setLogicalSwitchRef(buildLogicalSwitchesRef(iid, logicalSwitchName));
        List<LocatorSet> locatorSets = new ArrayList<>();
        for (String tepIp : tepIps) {
            locatorSets.add(new LocatorSetBuilder().setLocatorRef(
                    buildLocatorRef(iid, tepIp)).build());
        }
        remoteMcastMacsBuilder.setLocatorSet(locatorSets);
        return remoteMcastMacsBuilder.build();
    }

    public static List<Managers> buildManagers() {
        ManagersBuilder builder1 = new ManagersBuilder();

        builder1.withKey(new ManagersKey(new Uri("test")));
        List<ManagerOtherConfigs> otherConfigses = new ArrayList<>();

        otherConfigses.add(buildOtherConfig("ha_enabled", "true"));
        otherConfigses.add(buildOtherConfig("ha_id", "switchxyz"));
        builder1.setManagerOtherConfigs(otherConfigses);
        List<Managers> managers = new ArrayList<>();
        managers.add(builder1.build());
        return managers;
    }

    public static List<Managers> buildManagers1() {
        ManagersBuilder builder1 = new ManagersBuilder();
        builder1.withKey(new ManagersKey(new Uri("test")));
        builder1.setManagerOtherConfigs(Collections.emptyList());
        return ImmutableList.of(builder1.build());
    }

    public static ManagerOtherConfigs buildOtherConfig(String key, String val) {
        ManagerOtherConfigsBuilder otherConfigsBuilder = new ManagerOtherConfigsBuilder();
        ManagerOtherConfigsKey managerOtherConfigsKey = new ManagerOtherConfigsKey(key);
        otherConfigsBuilder.withKey(managerOtherConfigsKey);
        otherConfigsBuilder.setOtherConfigKey(key);
        otherConfigsBuilder.setOtherConfigValue(val);
        return otherConfigsBuilder.build();
    }

    public static Uuid getUUid(String key) {
        return new Uuid(UUID.nameUUIDFromBytes(key.getBytes()).toString());
    }

    public static HwvtepPhysicalLocatorRef buildLocatorRef(InstanceIdentifier<Node> nodeIid, String tepIp) {
        InstanceIdentifier<TerminationPoint> tepId = buildTpId(nodeIid, tepIp);
        return new HwvtepPhysicalLocatorRef(tepId);
    }

    public static InstanceIdentifier<TerminationPoint> buildTpId(InstanceIdentifier<Node> nodeIid, String tepIp) {
        String tpKeyStr = "vxlan_over_ipv4" + ':' + tepIp;
        TerminationPointKey tpKey = new TerminationPointKey(new TpId(tpKeyStr));
        InstanceIdentifier<TerminationPoint> plIid = nodeIid.child(TerminationPoint.class, tpKey);
        return plIid;
    }

    public static Class<? extends EncapsulationTypeBase> createEncapsulationType(String type) {
        Preconditions.checkNotNull(type);
        if (type.isEmpty()) {
            return EncapsulationTypeVxlanOverIpv4.class;
        } else {
            ImmutableBiMap<Class<? extends EncapsulationTypeBase>, String> encapsTypeMap
                    = new ImmutableBiMap.Builder<Class<? extends EncapsulationTypeBase>, String>()
                    .put(EncapsulationTypeVxlanOverIpv4.class, "vxlan_over_ipv4")
                    .build();
            ImmutableBiMap<String, Class<? extends EncapsulationTypeBase>> mapper =
                    encapsTypeMap.inverse();
            return mapper.get(type);
        }
    }
}

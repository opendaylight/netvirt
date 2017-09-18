/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsSimulator;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsdbSwitch;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZonesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.VtepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.VtepsKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItmUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ItmUtils.class);

    public static final String TRANSPORT_ZONE_NAME = "TZA";
    public static final Integer VLAN_ID = 0;
    public static final String PORT_NAME = "eth0";

    public static void createMesh(OvsSimulator ovsSimulator) {

        List<Vteps> vtepsList = new ArrayList<>();
        ovsSimulator.getOvsSwitches();
        for (Entry<IpAddress, OvsdbSwitch> entry : ovsSimulator.getOvsSwitches().entrySet()) {
            OvsdbSwitch ovsdbSwitch = entry.getValue();
            IpAddress ipAddress = entry.getKey();
            BigInteger dpnId = ovsdbSwitch.getBridge("br-int").getOpenflowNodeId();

            Vteps vteps = new VtepsBuilder().setDpnId(dpnId).setIpAddress(ipAddress)
                .setKey(new VtepsKey(dpnId, PORT_NAME)).setPortname(PORT_NAME).build();
            vtepsList.add(vteps);
        }
        createTransportZone(ovsSimulator.getBroker(), vtepsList);
    }

    private static void createTransportZone(DataBroker broker, List<Vteps> vtepsList) {
        Subnets subnet = new SubnetsBuilder().setGatewayIp(new IpAddress(RandomUtils.GATEWAY_IP.toCharArray()))
            .setKey(new SubnetsKey(new IpPrefix(RandomUtils.SUBNET_MASK.toCharArray())))
            .setPrefix(new IpPrefix(RandomUtils.SUBNET_MASK.toCharArray())).setVlanId(VLAN_ID)
            .setVteps(vtepsList).build();
        List<Subnets> subnetList = new ArrayList<>();
        subnetList.add(subnet);
        InstanceIdentifier<TransportZones> path = InstanceIdentifier.builder(TransportZones.class).build();
        TransportZone tzone = new TransportZoneBuilder().setKey(new TransportZoneKey(TRANSPORT_ZONE_NAME))
            .setTunnelType(TunnelTypeVxlan.class).setSubnets(subnetList)
            .setZoneName(TRANSPORT_ZONE_NAME).build();
        List<TransportZone> tzoneList = new ArrayList<>();
        tzoneList.add(tzone);
        TransportZones tzones = new TransportZonesBuilder().setTransportZone(tzoneList).build();
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(LogicalDatastoreType.CONFIGURATION, path, tzones, true);
        MdsalUtils.submitTransaction(tx);
    }

    public static void deleteMesh(DataBroker broker) {
        InstanceIdentifier<TransportZones> path = InstanceIdentifier.builder(TransportZones.class).build();
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.CONFIGURATION, path);
    }
}

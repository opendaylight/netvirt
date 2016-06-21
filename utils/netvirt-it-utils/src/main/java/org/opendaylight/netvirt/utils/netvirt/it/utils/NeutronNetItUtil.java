/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.utils.netvirt.it.utils;

import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import com.google.common.collect.Maps;
import org.junit.Assert;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronNetwork;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSubnet;
import org.opendaylight.netvirt.utils.neutron.utils.NeutronUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

/**
 * A utility class used in integration tests that need to create neutron networks with some ports.
 * Please see NetvirtIT#testNeutronNet for an example of how this class is used
 */
public class NeutronNetItUtil {

    public final String tenantId;
    public final String id;
    public final String subnetId;
    public NeutronNetwork neutronNetwork;
    public NeutronSubnet neutronSubnet;
    public String segId = "100";
    public String macPfx;
    public String ipPfx;
    public String cidr;

    public SouthboundUtils southboundUtils;
    public NeutronUtils neutronUtils;
    public Vector<NeutronPort> neutronPorts = new Vector();

    /**
     * Construct a new NeutronNetItUtil.
     * @param southboundUtils used to create termination points
     * @param tenantId tenant ID
     */
    public NeutronNetItUtil(SouthboundUtils southboundUtils, String tenantId) {
        this(southboundUtils, tenantId, "101", "f7:00:00:0f:00:", "10.0.0.", "10.0.0.0/24");
    }

    /**
     * Construct a new NeutronNetItUtil.
     * @param southboundUtils used to create termination points
     * @param tenantId tenant ID
     * @param segId the segmentation id to use for the neutron network
     * @param macPfx the first characters of the mac addresses generated for ports. Format is "f7:00:00:0f:00:"
     * @param ipPfx the first characters of the ip addresses generated for ports. Format is "10.0.0."
     * @param cidr the cidr for this network, e.g., "10.0.0.0/24"
     */
    public NeutronNetItUtil(SouthboundUtils southboundUtils, String tenantId,
                            String segId, String macPfx, String ipPfx, String cidr) {
        this.tenantId = tenantId;
        this.segId = segId;
        this.macPfx = macPfx;
        this.ipPfx = ipPfx;
        this.cidr = cidr;

        this.id = UUID.randomUUID().toString();
        this.subnetId = UUID.randomUUID().toString();

        this.southboundUtils = southboundUtils;
        neutronUtils = new NeutronUtils();
        neutronNetwork = null;
        neutronSubnet = null;
    }

    /**
     * Create the network and subnet.
     */
    public void create() {
        neutronNetwork = neutronUtils.createNeutronNetwork(id, tenantId, "vxlan", segId);
        neutronSubnet = neutronUtils.createNeutronSubnet(subnetId, tenantId, id, "10.0.0.0/24");
    }

    /**
     * Clean up all created neutron objects.
     */
    public void destroy() {
        for (NeutronPort port : neutronPorts) {
            neutronUtils.removeNeutronPort(port.getID());
        }
        neutronPorts.clear();

        if (neutronSubnet != null) {
            neutronUtils.removeNeutronSubnet(neutronSubnet.getID());
            neutronSubnet = null;
        }

        if (neutronNetwork != null) {
            neutronUtils.removeNeutronNetwork(neutronNetwork.getID());
            neutronNetwork = null;
        }
    }

    /**
     * Create a port on the network. The deviceOwner will be set to "compute:None".
     * @param bridge bridge where the port will be created on OVS
     * @param portName name for this port
     * @throws InterruptedException if we're interrupted while waiting for objects to be created
     */
    public void createPort(Node bridge, String portName) throws InterruptedException {
        createPort(bridge, portName, "compute:None");
    }

    /**
     * Create a port on the network. The deviceOwner will be set to "compute:None".
     * @param bridge bridge where the port will be created on OVS
     * @param portName name for this port
     * @param owner deviceOwner, e.g., "network:dhcp"
     * @param secGroups Optional NeutronSecurityGroup objects see NeutronUtils.createNeutronSecurityGroup()
     * @throws InterruptedException if we're interrupted while waiting for objects to be created
     */
    public void createPort(Node bridge, String portName, String owner, NeutronSecurityGroup... secGroups) throws InterruptedException {
        long idx = neutronPorts.size() + 1;
        Assert.assertTrue(idx < 256);
        String mac = macFor(idx);
        String ip = ipFor(idx);
        String portId = UUID.randomUUID().toString();
        neutronPorts.add(neutronUtils.createNeutronPort(id, subnetId, portId, owner, ip, mac, secGroups));

        //TBD: Use NotifyingDataChangeListener
        Thread.sleep(1000);

        Map<String, String> externalIds = Maps.newHashMap();
        externalIds.put("attached-mac", mac);
        externalIds.put("iface-id", portId);
        southboundUtils.addTerminationPoint(bridge, portName, "internal", null, externalIds, idx);
    }

    /**
     * Get the mac address for the n'th port created on this network (starts at 1).
     * @param portNum index of port created
     * @return the mac address
     */
    public String macFor(long portNum) {
        return macPfx + String.format("%02x", portNum);
    }

    /**
     * Get the ip address for the n'th port created on this network (starts at 1).
     * @param portNum index of port created
     * @return the mac address
     */
    public String ipFor(long portNum) {
        return ipPfx + portNum;
    }
}


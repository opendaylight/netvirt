/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.simulator.ovs;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;

public class OvsConstants {
    public static final String DEFAULT_BRIDGE = "br-int";
    public static final String ODL_IP = "192.168.56.1";
    public static final Integer ODL_PORT = 6640;
    public static final String PASSIVE_TARGET = "ptcp:" + ODL_PORT;
    public static final String ACTIVE_TARGET = "tcp:" + ODL_IP + ":" + ODL_PORT;
    public static final IpAddress ODL_IP_ADDR = new IpAddress(ODL_IP.toCharArray());
    public static final String CONTROLLER_TARGET = "tcp:" + ODL_IP + ":6653";

    public static final TopologyId OVS_TOPOLOGY_ID = new TopologyId("ovsdb:1");
    public static final String OVS_ACTIVE_URI_PREFIX = "ovsdb:1//uuid/";
    public static final String LOCAL_IP = "local_ip";

    public static final Integer OFPP_IN_PORT = 65228; //0xfff8
}

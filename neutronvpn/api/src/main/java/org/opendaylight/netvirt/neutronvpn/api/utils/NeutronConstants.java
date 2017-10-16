/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.api.utils;

import java.util.function.Predicate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;

public interface NeutronConstants {

    String DEVICE_OWNER_GATEWAY_INF = "network:router_gateway";
    String DEVICE_OWNER_ROUTER_INF = "network:router_interface";
    String DEVICE_OWNER_FLOATING_IP = "network:floatingip";
    String DEVICE_OWNER_DHCP = "network:dhcp";
    String FLOATING_IP_DEVICE_ID_PENDING = "PENDING";
    String PREFIX_TAP = "tap";
    String PREFIX_VHOSTUSER = "vhu";
    String RD_IDPOOL_NAME = "RouteDistinguisherPool";
    String RD_IDPOOL_SIZE = "65535";// 16 bit AS specific part of RD
    long RD_IDPOOL_START = 1L;
    String RD_PROPERTY_KEY = "vpnservice.admin.rdvalue";//stored in etc/custom.properties
    String VIF_TYPE_VHOSTUSER = "vhostuser";
    String VIF_TYPE_UNBOUND = "unbound";
    String VIF_TYPE_BINDING_FAILED = "binding_failed";
    String VIF_TYPE_DISTRIBUTED = "distributed";
    String VIF_TYPE_OVS = "ovs";
    String VIF_TYPE_BRIDGE = "bridge";
    String VIF_TYPE_OTHER = "other";
    String VIF_TYPE_MACVTAP = "macvtap";
    String VNIC_TYPE_NORMAL = "normal";
    String VNIC_TYPE_DIRECT = "direct";
    String BINDING_PROFILE_CAPABILITIES = "capabilities";
    String SWITCHDEV = "switchdev";
    int MAX_ROUTERS_PER_BGPVPN = 2;

    Predicate<Port> IS_DHCP_PORT = port -> port != null
            && DEVICE_OWNER_DHCP.equals(port.getDeviceOwner());

    Predicate<Port> IS_ODL_DHCP_PORT = port -> port != null
            && DEVICE_OWNER_DHCP.equals(port.getDeviceOwner()) && port.getDeviceId() != null
            && port.getDeviceId().startsWith("OpenDaylight");

}

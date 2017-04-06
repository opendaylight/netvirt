/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin;

import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;

public class FederationPluginConstants {

    public static final String PLUGIN_TYPE = "NETVIRT";

    public static final String INVENTORY_NODE_CONFIG_KEY = "INVENTORY_NODE_CONFIG";

    public static final String INVENTORY_NODE_OPER_KEY = "INVENTORY_NODE_OPER";

    public static final String TOPOLOGY_HWVTEP_NODE_CONFIG_KEY = "TOPOLOGY_HWVTEP_NODE_CONFIG";

    public static final String TOPOLOGY_HWVTEP_NODE_OPER_KEY = "TOPOLOGY_HWVTEP_NODE_OPER";

    public static final String TOPOLOGY_NODE_CONFIG_KEY = "TOPOLOGY_NODE_CONFIG";

    public static final String TOPOLOGY_NODE_OPER_KEY = "TOPOLOGY_NODE_OPER";

    public static final String IETF_INTERFACE_KEY = "IETF_INTERFACE";

    public static final String ELAN_INTERFACE_KEY = "ELAN_INTERFACE";

    public static final String VPN_INTERFACE_KEY = "VPN_INTERFACE";

    public static final String L2_GATEWAY_KEY = "L2_GATEWAY";

    public static final String L2_GATEWAY_CONNECTION_KEY = "L2_GATEWAY_CONNECTIONS";

    public static final String RPC_ROUTE_KEY = "FEDERATION_ROUTE_KEY";

    public static final String INTEGRATION_BRIDGE_PREFIX = ITMConstants.BRIDGE_URI_PREFIX + "/"
            + ITMConstants.DEFAULT_BRIDGE_NAME;

    public static final String TUNNEL_PREFIX = "tun";

    public static final TopologyKey OVSDB_TOPOLOGY_KEY = new TopologyKey(new TopologyId(new Uri("ovsdb:1")));

    public static final TopologyKey HWVTEP_TOPOLOGY_KEY = new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID);

    public static final String HWVTEP_PHYSICAL_SWITCH = "physicalswitch";
}

/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import org.opendaylight.genius.mdsalutil.NwConstants;

/**
 * Constants for NetvirtIT.
 */
public final class NetvirtITConstants {
    private NetvirtITConstants() {
    }

    public static final String ORG_OPS4J_PAX_LOGGING_CFG = "etc/org.ops4j.pax.logging.cfg";
    public static final String CUSTOM_PROPERTIES = "etc/custom.properties";
    public static final String SERVER_IPADDRESS = "ovsdbserver.ipaddress";
    public static final String SERVER_PORT = "ovsdbserver.port";
    public static final String CONTROLLER_IPADDRESS = "ovsdb.controller.address";
    public static final String USERSPACE_ENABLED = "ovsdb.userspace.enabled";
    public static final String SERVER_EXTRAS = "ovsdbserver.extras";
    public static final String CONNECTION_TYPE = "ovsdbserver.connection";
    public static final String CONNECTION_TYPE_ACTIVE = "active";
    public static final String CONNECTION_TYPE_PASSIVE = "passive";
    public static final int CONNECTION_INIT_TIMEOUT = 10000;
    public static final String DEFAULT_SERVER_IPADDRESS = "127.0.0.1";
    public static final String DEFAULT_SERVER_PORT = "6640";
    public static final String DEFAULT_OPENFLOW_PORT = "6653";
    public static final String DEFAULT_SERVER_EXTRAS = "false";
    public static final String BRIDGE_NAME = "brtest";
    public static final String PORT_NAME = "porttest";
    public static final String INTEGRATION_BRIDGE_NAME = "br-int";
    public static final String OPENFLOW_CONNECTION_PROTOCOL = "tcp";
    public static final int GATEWAY_SUFFIX = 254;

    public enum DefaultFlow {
        DHCP_EXTERNAL_TUNNEL("DHCPTableMissFlowForExternalTunnel", NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL),
        DHCP("DHCPTableMissFlow", NwConstants.DHCP_TABLE),
        IPV6("IPv6TableMissFlow", NwConstants.IPV6_TABLE);

        String flowId;
        short tableId;

        DefaultFlow(String flowId, short tableId) {
            this.flowId = flowId;
            this.tableId = tableId;
        }

        public String getFlowId() {
            return flowId;
        }

        public short getTableId() {
            return tableId;
        }
    }
}

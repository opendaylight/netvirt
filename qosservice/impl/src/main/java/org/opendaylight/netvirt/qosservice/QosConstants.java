/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;


public interface QosConstants {
    int QOS_DEFAULT_FLOW_PRIORITY = 10;
    String ALERT_MSG_FORMAT = "Packet drop threshold hit for qos policy {} with qos-id {} for port port-{}"
        + " on network network-{} rx_received {} rx_dropped {}";
    String QOS_ALERT_PROPERTIES_PID = "org.opendaylight.netvirt.qosservice";
    String ORG_OPS4J_PAX_LOGGING = "org.ops4j.pax.logging";
    String FELIX_FILEINSTALL_FILENAME = "felix.fileinstall.filename";
    String SERVICE_PID = "service.pid";
    String QOS_ALERT_OWNER_ENTITY_TYPE = "netvirt-qos-owner-entity";

    short IPV4_ADDR_MASK_BIT = 0;
    short IPV6_ADDR_MASK_BIT = 1;
}

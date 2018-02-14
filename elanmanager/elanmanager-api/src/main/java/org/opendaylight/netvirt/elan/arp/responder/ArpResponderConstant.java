/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.arp.responder;

public enum ArpResponderConstant {

    /**
     * ARP Responder table name.
     *
     * <p>Value:<b>Arp_Responder_Table</b>
     */
    TABLE_NAME("Arp_Responder_Table"),
    /**
     * ARP Responder group table name.
     *
     * <p>Value:<b>Arp_Responder_Group_Flow</b>
     */
    GROUP_FLOW_NAME("Arp_Responder_Group_Flow"),
    /**
     * ARP Responder Drop Flow name.
     *
     * <p>Value:<b>Arp_Responder_Drop_Flow</b>
     */
    DROP_FLOW_NAME("Arp_Responder_Drop_Flow"),
    /**
     * ARP Responder Flow ID.
     *
     * <p>Value:<b>Arp:tbl_{0}:lport_{1}:gw_{2}</b>
     * <ul><li>0: Table Id</li>
     * <li>1: LPort Tag</li>
     * <li>2: Target Protocol Address IP in String</li></ul>
     */
    FLOW_ID_FORMAT_WITH_LPORT("Arp:tbl_{0}:lport_{1}:tpa_{2}"),
    /**
     * ARP Responder Flow ID.
     *
     * <p>Value:<b>Arp:tbl_{0}:lport_{1}:gw_{2}</b>
     * <ul><li>0: Table Id</li>
     * <li>1: LPort Tag</li>
     * <li>2: Target Protocol Address IP in String</li></ul>
     */
    FLOW_ID_FORMAT_WITHOUT_LPORT("Arp:tbl_{0}:tpa_{1}"),
    /**
     * Pool name from which group id to be generated.
     *
     * <p>Value:<b>elan.ids.pool</b>
     */
    ELAN_ID_POOL_NAME("elan.ids.pool"),
    /**
     * Name of the group id for the pool entry.
     *
     * <p>Value:<b>arp.responder.group.id</b>
     */
    ARP_RESPONDER_GROUP_ID("arp.responder.group.id"),
    /**
     * Prefix for arp check table.
     *
     * <p>Value:<b>arp.check.table.</b>
     */
    FLOWID_PREFIX_FOR_ARP_CHECK("arp.check.table."),
    /**
     * Prefix for l3 gateway mac table.
     *
     * <p>Value:<b>arp.l3.gwmac.table.</b>
     */
    FLOWID_PREFIX_FOR_MY_GW_MAC("arp.l3.gwmac.table.");

    /**
     * enum value holder.
     */
    private final String value;

    /**
     * Constructor with single argument.
     *
     * @param value String enum value
     */
    ArpResponderConstant(final String value) {
        this.value = value;
    }

    /**
     * Get value for enum.
     *
     * @return {@link #value}
     */
    public String value() {
        return this.value;
    }
}

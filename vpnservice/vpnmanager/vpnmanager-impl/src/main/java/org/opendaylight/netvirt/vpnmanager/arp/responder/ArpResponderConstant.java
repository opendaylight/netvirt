/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.arp.responder;

import java.math.BigInteger;

public enum ArpResponderConstant {

    /**
     * ARP Responder table name
     * <P>
     * Value:<b>Arp_Responder_Table</b>
     */
    TABLE_NAME("Arp_Responder_Table"),
    /**
     * ARP Responder group table name
     * <P>
     * Value:<b>Arp_Responder_Group_Flow</b>
     */
    GROUP_FLOW_NAME("Arp_Responder_Group_Flow"),
    /**
     * ARP Responder Drop Flow name
     * <P>
     * Value:<b>Arp_Responder_Drop_Flow</b>
     */
    DROP_FLOW_NAME("Arp_Responder_Drop_Flow"),
    /**
     * ARP Responder Flow ID
     * <P>
     * Value:<b>Arp:tbl_{0}:lport_{1}:gw_{2}</b>
     * <ul><li>0: Table Id</li>
     * <li>1: LPort Tag</li>
     * <li>2: Gateway IP in String</li></ul>
     */
    FLOW_ID_FORMAT("Arp:tbl_{0}:lport_{1}:gw_{2}");

    /**
     * enum value holder
     */
    private final String value;

    /**
     * Constructor with single argument
     *
     * @param value
     *            String enum value
     */
    ArpResponderConstant(final String value) {
        this.value = value;
    }

    /**
     * Get value for enum
     *
     * @return {@link #value}
     */
    public String value() {
        return this.value;
    }

    /**
     * ENUM constants that for Arp Responder Group
     *
     * @author karthik.p
     *
     */
    public enum Group {
        /**
         * Value for group id. The Group Id is set Such that it one more that
         * max of NextHopManager Group Id pool refer NexthopManager.createIdPool
         */
        ID(175001L);

        /**
         * Long value of the ID
         */
        private final long value;

        /**
         * Group constructor with single argument
         *
         * @param value
         *            Value of the enum
         */
        Group(final long value) {
            this.value = value;
        }

        /**
         * Get Value of the enum
         *
         * @return {@link #value}
         */
        public long value() {
            return this.value;
        }
    }

    /**
     * Cookies constants used by Arp Responder
     *
     */
    public enum Cookies {
        /**
         * Cookie for Drop flow
         */
        DROP_COOKIE("8220000"),
        /**
         * Cookie for ARP responder flow
         */
        ARP_RESPONDER_COOKIE("8220001");

        /**
         * value of the cookie
         */
        private final String value;

        /**
         * Constructor with single argument
         *
         * @param value
         *            value of the cookie
         */
        Cookies(final String value) {
            this.value = value;
        }

        /**
         * Get value of the cookie
         *
         * @return {@link #value}
         */
        public BigInteger value() {
            return new BigInteger(this.value, 16);
        }
    }

}

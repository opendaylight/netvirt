/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import java.math.BigInteger;

public enum GeniusProviderTestParams {
    INTERFACE_NAME("123456"),
    INTERFACE_NAME_INVALID("000000"),
    INTERFACE_NAME_NO_EXIST("111111"),
    DPN_ID(new BigInteger("1234567890")),
    DPN_ID_NO_PORTS(new BigInteger("111111111")),
    DPN_ID_NO_VXGPE_PORTS(new BigInteger("222222222")),
    DPN_ID_NO_OPTIONS(new BigInteger("333333333")),
    DPN_ID_INVALID(new BigInteger("666666666")),
    DPN_ID_NO_EXIST(new BigInteger("999999999")),
    NODE_ID("openflow:" + DPN_ID.str()),
    NODE_CONNECTOR_ID_PREFIX("openflow:" + DPN_ID.str() + ":"),
    IPV4_ADDRESS_STR("192.168.0.1"),
    OF_PORT(42L);

    private BigInteger bigValue;
    private String strValue;
    private Long longValue;

    GeniusProviderTestParams(BigInteger value) {
        this.bigValue = value;
        this.strValue = value.toString();
        this.longValue = value.longValue();
    }

    GeniusProviderTestParams(String value) {
        this.strValue = value;
        this.bigValue = null;
        this.longValue = null;
    }

    GeniusProviderTestParams(Long value) {
        this.longValue = value;
        this.bigValue = BigInteger.valueOf(value);
        this.strValue = String.valueOf(value);
    }

    public Long longValue() {
        return longValue;
    }

    public BigInteger big() {
        return bigValue;
    }

    public String str() {
        return strValue;
    }
}

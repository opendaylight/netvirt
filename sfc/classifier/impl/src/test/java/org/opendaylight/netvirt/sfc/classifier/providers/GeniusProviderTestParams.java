/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import java.math.BigInteger;

public interface GeniusProviderTestParams {
    String INTERFACE_NAME = "123456";
    String INTERFACE_NAME_INVALID = "000000";
    String INTERFACE_NAME_NO_EXIST = "111111";

    BigInteger DPN_ID = new BigInteger("1234567890");
    BigInteger DPN_ID_NO_PORTS = new BigInteger("111111111");
    BigInteger DPN_ID_NO_VXGPE_PORTS = new BigInteger("222222222");
    BigInteger DPN_ID_NO_OPTIONS = new BigInteger("333333333");
    BigInteger DPN_ID_INVALID = new BigInteger("666666666");
    BigInteger DPN_ID_NO_EXIST = new BigInteger("999999999");

    String NODE_ID = "openflow:" + DPN_ID.toString();
    String NODE_CONNECTOR_ID_PREFIX = "openflow:" + DPN_ID.toString() + ":";
    String IPV4_ADDRESS_STR = "192.168.0.1";
    long OF_PORT = 42L;
}

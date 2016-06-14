/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.utils;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;

import static org.junit.Assert.assertEquals;

/**
 * Unit test fort {@link RoutemgrUtil}
 */
public class RoutemgrUtilTest {
    private RoutemgrUtil instance;

    @Before
    public void initTest() {
         instance = RoutemgrUtil.getInstance();
    }

    /**
     *  Test getIpv6LinkLocalAddressFromMac with different MACAddress values.
     */
    @Test
    public void testgetIpv6LinkLocalAddressFromMac() {
        MacAddress mac = new MacAddress("fa:16:3e:4e:18:0c");
        Ipv6Address expectedLinkLocalAddress = new Ipv6Address("fe80:0:0:0:f816:3eff:fe4e:180c");
        assertEquals(expectedLinkLocalAddress, instance.getIpv6LinkLocalAddressFromMac(mac));

        mac = new MacAddress("fa:16:3e:4e:18:c0");
        expectedLinkLocalAddress = new Ipv6Address("fe80:0:0:0:f816:3eff:fe4e:18c0");
        assertEquals(expectedLinkLocalAddress, instance.getIpv6LinkLocalAddressFromMac(mac));

        mac = new MacAddress("50:7B:9D:78:54:F3");
        expectedLinkLocalAddress = new Ipv6Address("fe80:0:0:0:527b:9dff:fe78:54f3");
        assertEquals(expectedLinkLocalAddress, instance.getIpv6LinkLocalAddressFromMac(mac));
    }
}
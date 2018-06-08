/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;

/**
 * Unit test fort {@link Ipv6ServiceUtilsTest}.
 */
public class Ipv6ServiceUtilsTest {
    private static String IPv6_ADDR_1 = "fe80:0:0:0:f816:3eff:fe4e:180c";
    private static String IPv6_ADDR_2 = "fe80:0:0:0:f816:3eff:fe4e:18c0";
    private static String IPv6_ADDR_3 = "fe80:0:0:0:816:3ff:fe04:80c";
    private static String IPv6_ADDR_4 = "fe80:0:0:0:f600:ff:fe0f:6b";
    private static String IPv6_ADDR_5 = "fe80:0:0:0:527b:9dff:fe78:54f3";

    /**
     *  Test getIpv6LinkLocalAddressFromMac with different MACAddress values.
     */
    @Test
    public void testgetIpv6LinkLocalAddressFromMac() {
        MacAddress mac = new MacAddress("fa:16:3e:4e:18:0c");
        Ipv6Address expectedLinkLocalAddress = new Ipv6Address(IPv6_ADDR_1);
        assertEquals(expectedLinkLocalAddress, Ipv6Util.getIpv6LinkLocalAddressFromMac(mac));

        mac = new MacAddress("fa:16:3e:4e:18:c0");
        expectedLinkLocalAddress = new Ipv6Address(IPv6_ADDR_2);
        assertEquals(expectedLinkLocalAddress, Ipv6Util.getIpv6LinkLocalAddressFromMac(mac));

        mac = new MacAddress("0a:16:03:04:08:0c");
        expectedLinkLocalAddress = new Ipv6Address(IPv6_ADDR_3);
        assertEquals(expectedLinkLocalAddress, Ipv6Util.getIpv6LinkLocalAddressFromMac(mac));

        mac = new MacAddress("f4:00:00:0f:00:6b");
        expectedLinkLocalAddress = new Ipv6Address(IPv6_ADDR_4);
        assertEquals(expectedLinkLocalAddress, Ipv6Util.getIpv6LinkLocalAddressFromMac(mac));

        mac = new MacAddress("50:7B:9D:78:54:F3");
        expectedLinkLocalAddress = new Ipv6Address(IPv6_ADDR_5);
        assertEquals(expectedLinkLocalAddress, Ipv6Util.getIpv6LinkLocalAddressFromMac(mac));
    }

    /**
     *  Test getIpv6SolicitedNodeMcastAddress with different IPv6Address values.
     */
    @Test
    public void testgetIpv6SolicitedNodeMcastAddress() {
        Ipv6Address ipv6Address = new Ipv6Address(IPv6_ADDR_1);
        Ipv6Address expectedSolicitedNodeAddr = new Ipv6Address("ff02:0:0:0:0:1:ff4e:180c");
        assertEquals(expectedSolicitedNodeAddr, Ipv6Util.getIpv6SolicitedNodeMcastAddress(ipv6Address));

        ipv6Address = new Ipv6Address(IPv6_ADDR_2);
        expectedSolicitedNodeAddr = new Ipv6Address("ff02:0:0:0:0:1:ff4e:18c0");
        assertEquals(expectedSolicitedNodeAddr, Ipv6Util.getIpv6SolicitedNodeMcastAddress(ipv6Address));

        ipv6Address = new Ipv6Address(IPv6_ADDR_4);
        expectedSolicitedNodeAddr = new Ipv6Address("ff02:0:0:0:0:1:ff0f:6b");
        assertEquals(expectedSolicitedNodeAddr, Ipv6Util.getIpv6SolicitedNodeMcastAddress(ipv6Address));
    }

    /**
     *  Test getIpv6MulticastMacAddress with different IPv6Address values.
     */
    @Test
    public void testgetIpv6MulticastMacAddress() {
        Ipv6Address ipv6Address = new Ipv6Address(IPv6_ADDR_1);
        MacAddress expectedMacAddress = new MacAddress("33:33:fe:4e:18:0c");
        assertEquals(expectedMacAddress, Ipv6Util.getIpv6MulticastMacAddress(ipv6Address));

        ipv6Address = new Ipv6Address(IPv6_ADDR_2);
        expectedMacAddress = new MacAddress("33:33:fe:4e:18:c0");
        assertEquals(expectedMacAddress, Ipv6Util.getIpv6MulticastMacAddress(ipv6Address));

        ipv6Address = new Ipv6Address(IPv6_ADDR_4);
        expectedMacAddress = new MacAddress("33:33:fe:0f:00:6b");
        assertEquals(expectedMacAddress, Ipv6Util.getIpv6MulticastMacAddress(ipv6Address));
    }
}

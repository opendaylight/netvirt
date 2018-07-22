/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;

/**
 * Unit tests for AclServiceUtils.
 *
 */
public class AclServiceUtilsTest {

    @Test
    public void testExcludeMulticastAAPs() {
        List<AllowedAddressPairs> inputAAPs = new ArrayList<>();
        buildInputAAP(inputAAPs, "1.1.1.1/32");// non-multicast Ipv4Prefix
        buildInputAAP(inputAAPs, "2.2.2.2");// non-multicast Ipv4Address
        buildInputAAP(inputAAPs, "2001:db8:85a3:0:0:8a2e:370:7334"); // non-multicast Ipv6Address
        buildInputAAP(inputAAPs, "2002:db8:85a3::8a2e:370:7334/8"); // non-multicast Ipv6Prefix

        List<AllowedAddressPairs> filteredAAPs = AclServiceUtils.excludeMulticastAAPs(inputAAPs);
        assertNotNull(filteredAAPs);
        assertEquals(inputAAPs, filteredAAPs);
        inputAAPs.clear();

        buildInputAAP(inputAAPs, "224.0.0.0/24");// multicast Ipv4Prefix
        buildInputAAP(inputAAPs, "224.4.2.2");// multicast Ipv4Address
        buildInputAAP(inputAAPs, "FF01:0:0:0:0:0:0:1"); // multicast Ipv6Address
        buildInputAAP(inputAAPs, "FF01::DB8:0:0/96"); // multicast Ipv6Prefix

        filteredAAPs = AclServiceUtils.excludeMulticastAAPs(inputAAPs);
        assertNotNull(filteredAAPs);
        assertEquals(0, filteredAAPs.size());
        inputAAPs.clear();

        buildInputAAP(inputAAPs, "224.4.2.2");// multicast Ipv4Address
        buildInputAAP(inputAAPs, "3.3.3.3");// non-multicast Ipv4Address
        buildInputAAP(inputAAPs, "2001:db8:85a3:0:0:8a2e:370:7335"); // non-multicast Ipv6Address
        buildInputAAP(inputAAPs, "FF01:0:0:0:0:0:0:2"); // multicast Ipv6Address

        List<AllowedAddressPairs> tempAAPs = new ArrayList<>();
        tempAAPs.add(buildAAp("3.3.3.3"));
        tempAAPs.add(buildAAp("2001:db8:85a3:0:0:8a2e:370:7335"));

        filteredAAPs = AclServiceUtils.excludeMulticastAAPs(inputAAPs);
        assertNotNull(filteredAAPs);
        assertEquals(2, filteredAAPs.size());
        assertEquals(tempAAPs, filteredAAPs);
        inputAAPs.clear();
    }

    private static void buildInputAAP(List<AllowedAddressPairs> inputAAPs, String addr) {
        inputAAPs.add(buildAAp(addr));
    }

    private static AllowedAddressPairs buildAAp(String addr) {
        AllowedAddressPairsBuilder aapb = new AllowedAddressPairsBuilder();
        aapb.setIpAddress(IpPrefixOrAddressBuilder.getDefaultInstance(addr));
        aapb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        return aapb.build();
    }
}

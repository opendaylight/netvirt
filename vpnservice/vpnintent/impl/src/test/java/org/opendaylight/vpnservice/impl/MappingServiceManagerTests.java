/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opendaylight.nic.mapping.api.IntentMappingService;

public class MappingServiceManagerTests {

    @Test
    public void addInfo() {
        // Arrange
        String siteName = "UoR";
        String ipPrefix = "16.101.233.2/8";
        String switchPortId = "openflow:1:3";
        long mplsLabel = 10L;
        String nextHop = "16.101.233.1/8";

        Map<String, String> map = new HashMap<>();
        map.put("ip_prefix", ipPrefix);
        map.put("switch_port", switchPortId);
        map.put("mpls_label", String.valueOf(mplsLabel));
        map.put("next_hop", nextHop);

        IntentMappingService mapSvc = mock(IntentMappingService.class);
        when(mapSvc.get(any(String.class))).thenReturn(map);

        MappingServiceManager manager = new MappingServiceManager(mapSvc);

        // Act
        manager.add(siteName, ipPrefix, switchPortId, mplsLabel, nextHop);

        Map<String, String> returnedObjs = manager.get(siteName);

        // Assert
        assertEquals(ipPrefix, returnedObjs.get("ip_prefix"));
        assertEquals(switchPortId, returnedObjs.get("switch_port"));
        assertEquals(mplsLabel, Long.parseLong(returnedObjs.get("mpls_label")));
        assertEquals(nextHop, returnedObjs.get("next_hop"));
    }

    @Test
    public void removeInfo() {
        // Arrange
        String siteName = "UoR";
        String ipPrefix = "16.101.233.2/8";
        String switchPortId = "openflow:1:3";

        Map<String, String> map = new HashMap<>();
        map.put("ip_prefix", ipPrefix);
        map.put("switch_port", switchPortId);

        IntentMappingService mapSvc = mock(IntentMappingService.class);

        MappingServiceManager manager = new MappingServiceManager(mapSvc);

        // Add first to delete next
        manager.add(siteName, ipPrefix, switchPortId, null, null);

        // Act
        boolean result = manager.delete(siteName);

        // Assert
        assertTrue(result);
    }
}

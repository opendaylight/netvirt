/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;

public class IfmUtilTest {

    @Mock NodeConnectorId ncId;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDpnConversions() {
        String NodeId = IfmUtil.buildDpnNodeId(BigInteger.valueOf(101)).getValue();
        assertEquals("openflow:101", NodeId);
        when(ncId.getValue()).thenReturn("openflow:101:11");
        assertEquals("101",IfmUtil.getDpnFromNodeConnectorId(ncId));
    }

}

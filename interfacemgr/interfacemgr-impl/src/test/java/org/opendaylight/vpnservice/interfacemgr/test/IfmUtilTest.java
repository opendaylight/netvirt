package org.opendaylight.vpnservice.interfacemgr.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;

public class IfmUtilTest {

    @Mock NodeConnectorId ncId;
    MockDataChangedEvent event;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDpnConversions() {
        String NodeId = IfmUtil.buildDpnNodeId(101L).getValue();
        assertEquals("openflow:101", NodeId);
        when(ncId.getValue()).thenReturn("openflow:101:11");
        assertEquals("101",IfmUtil.getDpnFromNodeConnectorId(ncId));
    }

}

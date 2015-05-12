package org.opendaylight.vpnservice.bgpmanager.test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.bgpmanager.FibDSWriter;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;

import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)

public class BgpManagerTest extends AbstractDataBrokerTest {
     DataBroker dataBroker;
     FibDSWriter bgpFibWriter = null ;
     MockFibManager fibManager = null ;

     @Before
     public void setUp() throws Exception {
         dataBroker = getDataBroker() ;
         bgpFibWriter = new FibDSWriter(dataBroker);
         fibManager = new MockFibManager(dataBroker);
     }

    @Test
    public void testAddSinglePrefix() {
        String rd = "101";
        String prefix = "10.10.10.10/32";
        String nexthop = "100.100.100.100";
        int label = 1234;

        bgpFibWriter.addFibEntryToDS(rd, prefix, nexthop, label);
        assertEquals(1, fibManager.getDataChgCount());
    }

    @Test
    public void testAddPrefixesInRd() {
        String rd = "101";
        String prefix = "10.10.10.10/32";
        String nexthop = "100.100.100.100";
        int label = 1234;

        bgpFibWriter.addFibEntryToDS(rd, prefix, nexthop, label);
        assertEquals(1, fibManager.getDataChgCount());

        prefix = "10.10.10.11/32";
        label = 3456;
        bgpFibWriter.addFibEntryToDS(rd, prefix, nexthop, label);
        assertEquals(2, fibManager.getDataChgCount());


    }

    @Test
    public void testAddPrefixesAcrossRd() {
        String rd = "101";
        String prefix = "10.10.10.10/32";
        String nexthop = "100.100.100.100";
        int label = 1234;

        bgpFibWriter.addFibEntryToDS(rd, prefix, nexthop, label);
        assertEquals(1, fibManager.getDataChgCount());

        rd = "102";
        prefix = "10.10.10.11/32";
        nexthop = "200.200.200.200";
        label = 3456;
        bgpFibWriter.addFibEntryToDS(rd, prefix, nexthop, label);
        assertEquals(2, fibManager.getDataChgCount());

    }


    @Test
    public void testRemovePrefix() {
        String rd = "101";
        String prefix = "10.10.10.10/32";
        String nexthop = "100.100.100.100";
        int label = 1234;

        //add and then remove prefix
        bgpFibWriter.addFibEntryToDS(rd, prefix, nexthop, label);
        assertEquals(1, fibManager.getDataChgCount());
        bgpFibWriter.removeFibEntryFromDS(rd, prefix);
        assertEquals(0, fibManager.getDataChgCount());

    }

}

package org.opendaylight.netvirt.aclservice.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.netvirt.aclservice.tests.DataBrokerExtensions.put;
import static org.opendaylight.netvirt.aclservice.tests.InterfaceBuilderHelper.newInterfacePair;

import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;

public class BugReproducerTest extends AbstractDataBrokerTest {

    @Test
    public void reproduceBug6252() {
        put(getDataBroker(), CONFIGURATION, newInterfacePair("port1", true));
    }

}

package org.opendaylight.netvirt.aclservice.tests.utils.tests;

import static org.junit.Assert.assertEquals;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.TestDataBroker;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class TestDataBrokerTest {

    @Test public void simpleInTx() throws Exception {
        DataBroker db = TestDataBroker.newTestDataBroker();
        TestDataObject writtenDataObject = newDO();
        ReadWriteTransaction tx = db.newReadWriteTransaction();
        tx.put(CONFIGURATION, newID(), writtenDataObject);
        assertEquals(writtenDataObject, tx.read(CONFIGURATION, newID()).get().get());
    }

    @Test public void simpleSubmitWriteNewReadTx() throws Exception {
        DataBroker db = TestDataBroker.newTestDataBroker();
        TestDataObject writtenDataObject = newDO();
        WriteTransaction wTx = db.newWriteOnlyTransaction();
        wTx.put(CONFIGURATION, newID(), writtenDataObject);
        wTx.submit();
        ReadOnlyTransaction rTx = db.newReadOnlyTransaction();
        assertEquals(writtenDataObject, rTx.read(CONFIGURATION, newID()).get().get());
    }

    private static abstract class TestDataObject implements DataObject { }

    private InstanceIdentifier<TestDataObject> newID() {
        return InstanceIdentifier.create(TestDataObject.class);
    }

    private TestDataObject newDO() {
        return Mikito.stub(TestDataObject.class);
    }

}

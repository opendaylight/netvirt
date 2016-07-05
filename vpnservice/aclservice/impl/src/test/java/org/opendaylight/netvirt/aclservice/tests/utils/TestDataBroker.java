package org.opendaylight.netvirt.aclservice.tests.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * DataBroker useful for simple tests.
 *
 * This is MUCH faster than using AbstractDataBrokerTest, which takes a few
 * seconds for EACH @Test, just to warm up; whereas this one is instant. But it is
 * only a trivial dumb implementation, of course. Not multi thread safe, obviously.
 *
 * If this class is not sufficient for your purposes, and throws
 * NotImplementedException for operations which are non-trivial, then you'll
 * probably want to use an AbstractDataBrokerTest instead.
 *
 * This class is abstract just to save reading lines and typing keystrokes to
 * manually implement a bunch of methods we're not interested in. It is intended
 * to be used with Mikito, obtained through the newTestDataBroker() method, only.
 *
 * @author Michael Vorburger
 */
public abstract class TestDataBroker extends SimplestDatabase implements DataBroker {

    public static DataBroker newTestDataBroker() {
        TestDataBroker newTestDataBroker = Mikito.stub(TestDataBroker.class);
        newTestDataBroker.getMap(); // This initializes the SimplestDatabase
        return newTestDataBroker;
    }

    @Override
    public WriteTransaction newWriteOnlyTransaction() {
        return Mikito.stub(TestReadOnlyReadWriteTransaction.class).parent(this);
    }

    @Override
    public ReadOnlyTransaction newReadOnlyTransaction() {
        return Mikito.stub(TestReadOnlyReadWriteTransaction.class).parent(this);
    }

    @Override
    public ReadWriteTransaction newReadWriteTransaction() {
        return Mikito.stub(TestReadOnlyReadWriteTransaction.class).parent(this);
    }

    protected static abstract class TestReadOnlyReadWriteTransaction extends SimplestDatabase implements ReadOnlyTransaction, ReadWriteTransaction {

        private SimplestDatabase parent;

        @Override
        public <T extends DataObject> void put(LogicalDatastoreType store, InstanceIdentifier<T> path, T data, boolean createMissingParents) {
            put(store, path, data);
        }

        public TestReadOnlyReadWriteTransaction parent(SimplestDatabase theParent) {
            this.parent = theParent;
            return this;
        }

        @Override
        public <T extends DataObject> void put(LogicalDatastoreType store, InstanceIdentifier<T> path, T data) {
            getMap().get(store).put(path, data);
        }

        @Override
        public <T extends DataObject> CheckedFuture<Optional<T>, ReadFailedException> read(LogicalDatastoreType store, InstanceIdentifier<T> path) {
            Optional<T> o = get(store, path);
            if (!o.isPresent()) {
                o = parent.get(store, path);
            }
            return Futures.immediateCheckedFuture(o);
        }

        @Override
        public CheckedFuture<Void, TransactionCommitFailedException> submit() {
            mergeInto(parent);
            return Futures.immediateCheckedFuture(null);
        }
    }
}

package org.opendaylight.netvirt.coe.listeners;

import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Verifications {
    private static final Logger LOG = LoggerFactory.getLogger(Verifications.class);
    private static final int AWAIT_TIMEOUT = 10;
    private static final int AWAIT_INTERVAL = 1000;
    private final ConditionFactory awaiter;
    private final ManagedNewTransactionRunner txRunner;

    Verifications(final DataBroker dataBroker) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.awaiter = getAwaiter();
    }

    private ConditionFactory getAwaiter() {
        return Awaitility.await("TestableListener")
            .atMost(AWAIT_TIMEOUT, TimeUnit.SECONDS)//TODO constant
            .pollInterval(AWAIT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    <D extends Datastore> void awaitForData(Class<D> datastoreType, InstanceIdentifier<? extends DataObject> iid) {
        awaiter.with()
            .conditionEvaluationListener(condition -> LOG.info("{} ({} ms of {} s)",
                condition.getDescription(), condition.getElapsedTimeInMS(), AWAIT_TIMEOUT))
            .until(() ->
                txRunner.applyWithNewReadOnlyTransactionAndClose(datastoreType, tx -> tx.read(iid)).get().isPresent());
    }
}

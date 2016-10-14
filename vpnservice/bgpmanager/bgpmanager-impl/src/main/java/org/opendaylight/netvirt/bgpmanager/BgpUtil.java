/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.genius.utils.batching.ActionableResource;
import org.opendaylight.genius.utils.batching.ActionableResourceImpl;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.batching.ResourceHandler;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.encap_type;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpUtil {
    private static final Logger LOG = LoggerFactory.getLogger(BgpUtil.class);
    private static DataBroker dataBroker;
    private static BindingTransactionChain fibTransact;
    public static final int PERIODICITY = 500;
    private static AtomicInteger pendingWrTransaction = new AtomicInteger(0);
    public static final int BATCH_SIZE = 1000;
    public static Integer batchSize;
    public static Integer batchInterval;
    private static int txChainAttempts = 0;

    private static BlockingQueue<ActionableResource> bgpResourcesBufferQ = new LinkedBlockingQueue<>();

    // return number of pending Write Transactions with BGP-Util (no read)
    public static int getGetPendingWrTransaction() {
        return pendingWrTransaction.get();
    }

    static ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("bgp-util-mdsal-%d").build();

    static ExecutorService threadPool = Executors.newFixedThreadPool(1, namedThreadFactory);


    static synchronized BindingTransactionChain getTransactionChain() {
        return fibTransact;
    }

    static void registerWithBatchManager(ResourceHandler resourceHandler) {
        ResourceBatchingManager resBatchingManager = ResourceBatchingManager.getInstance();
        resBatchingManager.registerBatchableResource("BGP-RESOURCES", bgpResourcesBufferQ, resourceHandler);
    }

    static <T extends DataObject> void update(DataBroker broker, final LogicalDatastoreType datastoreType,
                                              final InstanceIdentifier<T> path, final T data) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.UPDATE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(data);
        bgpResourcesBufferQ.add(actResource);
    }

    public static <T extends DataObject> void write(DataBroker broker, final LogicalDatastoreType datastoreType,
                                                    final InstanceIdentifier<T> path, final T data) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.CREATE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(data);
        bgpResourcesBufferQ.add(actResource);
    }

    static <T extends DataObject> void delete(DataBroker broker, final LogicalDatastoreType datastoreType,
                                              final InstanceIdentifier<T> path) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.DELETE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(null);
        bgpResourcesBufferQ.add(actResource);
    }

    public static void setBroker(final DataBroker broker) {
        BgpUtil.dataBroker = broker;
        initTransactionChain();
    }

    static synchronized void initTransactionChain() {
        if (fibTransact != null) {
            fibTransact.close();
            LOG.error("*** TxChain Close, *** Attempts: {}", txChainAttempts);
            fibTransact = null;
        }
        BgpUtil.fibTransact = dataBroker.createTransactionChain(new BgpUtilTransactionChainListener());
        txChainAttempts++;
    }

    static class BgpUtilTransactionChainListener implements TransactionChainListener {
        @Override
        public void onTransactionChainFailed(TransactionChain<?, ?> transactionChain,
                                             AsyncTransaction<?, ?> asyncTransaction, Throwable throwable) {
            LOG.error("*** TxChain Creation Failed *** Attempts: {}", txChainAttempts);
            initTransactionChain();
        }

        @Override
        public void onTransactionChainSuccessful(TransactionChain<?, ?> transactionChain) {
            LOG.trace("TxChain Creation Success");
        }
    }

    public static DataBroker getBroker() {
        return dataBroker;
    }

    // Convert ProtocolType to thrift protocol_type
    public static protocol_type convertToThriftProtocolType(BgpControlPlaneType protocolType ) {
        // TODO: add implementation
        return protocol_type.PROTOCOL_ANY;
    }

    // Convert EncapType to thrift encap_type
    public static encap_type convertToThriftEncapType(EncapType encapType) {
        // TODO: add implementation
        return encap_type.MPLS;
    }

    static VpnInstanceOpDataEntry getVpnInstanceOpData(DataBroker broker, String rd) throws InterruptedException, ExecutionException, TimeoutException {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }
    public static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }
}


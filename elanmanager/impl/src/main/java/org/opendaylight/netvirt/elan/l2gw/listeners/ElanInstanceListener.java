/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInstanceListener extends AbstractClusteredAsyncDataTreeChangeListener<ElanInstance>
        implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceListener.class);

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final ElanClusterUtils elanClusterUtils;

    @Inject
    public ElanInstanceListener(final DataBroker db, final ElanClusterUtils elanClusterUtils,
                                final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                                final ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(db, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class),
                Executors.newListeningSingleThreadExecutor("ElanInstanceListener", LOG));
        broker = db;
        this.txRunner = new ManagedNewTransactionRunnerImpl(db);
        this.elanClusterUtils = elanClusterUtils;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(), this);
    }

    public void init() {
        registerListener();
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void registerListener() {
        super.register();
        LOG.info("Registering ElanInstanceListener");
    }

    public void deregisterListener() {
        super.close();
        LOG.info("Deregistering ElanInstanceListener");
    }

    @Override
    public void remove(final InstanceIdentifier<ElanInstance> identifier,
                          final ElanInstance del) {
        elanClusterUtils.runOnlyInOwnerNode(del.getElanInstanceName(), "delete Elan instance",
            () -> {
                LOG.info("Elan instance {} deleted from Configuration tree ", del);
                List<L2gatewayConnection> connections =
                        L2GatewayConnectionUtils.getL2GwConnectionsByElanName(
                                this.broker, del.getElanInstanceName());
                if (connections.isEmpty()) {
                    return Collections.emptyList();
                }
                ListenableFuture<?> future = txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                    tx -> {
                        for (L2gatewayConnection connection : connections) {
                            InstanceIdentifier<L2gatewayConnection> iid =
                                    InstanceIdentifier.create(Neutron.class).child(
                                            L2gatewayConnections.class).child(
                                            L2gatewayConnection.class, connection.key());
                            tx.delete(iid);
                        }
                    });
                LoggingFutures.addErrorLogging(future, LOG,
                        "Failed to delete associate L2 gateway connection while deleting network");
                return Collections.singletonList(future);
            });
    }

    @Override
    public void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {

    }

    @Override
    public void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance add) {
    }
}

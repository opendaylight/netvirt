/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.recovery.impl;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnectionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.srm.types.rev180626.NetvirtL2gwConnection;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class L2GatewayConnectionInstanceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayConnectionInstanceRecoveryHandler.class);

    private final ManagedNewTransactionRunner txRunner;
    private final DataBroker dataBroker;
    private ServiceRecoveryRegistry serviceRecoveryRegistry;
    private JobCoordinator jobCoordinator;
    private L2GatewayConnectionUtils l2GatewayConnectionUtils;
    private EntityOwnershipUtils entityOwnershipUtils;

    @Inject
    public L2GatewayConnectionInstanceRecoveryHandler(DataBroker dataBroker,
                                                      ServiceRecoveryRegistry serviceRecoveryRegistry,
                                                      JobCoordinator jobCoordinator,
                                                      L2GatewayConnectionUtils l2GatewayConnectionUtils,
                                                      EntityOwnershipUtils entityOwnershipUtils) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.dataBroker = dataBroker;
        this.serviceRecoveryRegistry = serviceRecoveryRegistry;
        this.jobCoordinator = jobCoordinator;
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
        this.entityOwnershipUtils = entityOwnershipUtils;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(),this);
    }

    @Override
    @SuppressWarnings("ForbidCertainMethod")
    public void recoverService(String entityId) {
        LOG.info("recover l2gateway connection {}", entityId);
        // Fetch the l2 gateway connection from l2 gateway connection config DS first.
        Uuid uuid = Uuid.getDefaultInstance(entityId);

        InstanceIdentifier<L2gatewayConnection> connectionInstanceIdentifier = InstanceIdentifier.create(Neutron.class)
                .child(L2gatewayConnections.class)
                .child(L2gatewayConnection.class, new L2gatewayConnectionKey(uuid));

        Optional<L2gatewayConnection> l2gatewayConnectionOptional = Optional.empty();
        try {
            l2gatewayConnectionOptional = SingleTransactionDataBroker.syncReadOptional(
                dataBroker, LogicalDatastoreType.CONFIGURATION, connectionInstanceIdentifier);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("recoverService: Exception while reading L2gatewayConnection DS", e);
        }
        //l2GatewayConnectionUtils.addL2GatewayConnection(l2gatewayConnectionOptional.get());

        L2gatewayConnection l2gatewayConnection = l2gatewayConnectionOptional.get();

        LOG.info("deleting l2 gateway connection {}",l2gatewayConnection.key());
        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
            tx -> tx.delete(connectionInstanceIdentifier));
        LOG.info("recreating l2 gateway connection {}, {}",entityId, l2gatewayConnection.key());
        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
            tx -> tx.put(connectionInstanceIdentifier,l2gatewayConnection));
    }

    public String buildServiceRegistryKey() {
        return NetvirtL2gwConnection.class.toString();
    }
}

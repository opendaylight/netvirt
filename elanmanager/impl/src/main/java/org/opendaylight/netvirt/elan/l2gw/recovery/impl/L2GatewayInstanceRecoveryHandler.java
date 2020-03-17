/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.recovery.impl;

import java.util.Optional;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.srm.types.rev180626.NetvirtL2gwNode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class L2GatewayInstanceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayInstanceRecoveryHandler.class);

    private final ManagedNewTransactionRunner managedNewTransactionRunner;
    private final DataBroker dataBroker;
    private L2GatewayConnectionUtils l2GatewayConnectionUtils;

    @Inject
    public L2GatewayInstanceRecoveryHandler(DataBroker dataBroker, L2GatewayConnectionUtils l2GatewayConnectionUtils,
                                            ServiceRecoveryRegistry serviceRecoveryRegistry) {
        this.dataBroker = dataBroker;
        this.managedNewTransactionRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    @Override
    @SuppressWarnings("ForbidCertainMethod")
    public void recoverService(String entityId) {
        LOG.info("recover l2gateway {}", entityId);
        Uuid uuid = Uuid.getDefaultInstance(entityId);

        InstanceIdentifier<L2gateway> l2gatewayInstanceIdentifier = InstanceIdentifier.create(Neutron.class)
                .child(L2gateways.class).child(L2gateway.class, new L2gatewayKey(uuid));

        Optional<L2gateway> l2gatewayOptional = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                l2gatewayInstanceIdentifier);
        L2gateway l2gateway = l2gatewayOptional.get();

        List<L2gatewayConnection> l2gatewayConnections = l2GatewayConnectionUtils.getL2GwConnectionsByL2GatewayId(uuid);
        // Do a delete of l2 gateway connection instances.
        //No null check required since l2gatewayConnections is known to be non-null.
        LOG.info("Deleting all l2 gateway connections of l2 gateway instance {}", l2gateway.key());
        for (L2gatewayConnection l2gatewayConnection: l2gatewayConnections) {
            InstanceIdentifier<L2gatewayConnection> identifier = InstanceIdentifier.create(Neutron.class)
                    .child(L2gatewayConnections.class)
                    .child(L2gatewayConnection.class, l2gatewayConnection.key());
            try {
                LOG.info("Deleting l2 gateway connection {}",l2gatewayConnection.key());
                managedNewTransactionRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    tx -> tx.delete(LogicalDatastoreType.CONFIGURATION, identifier)).get();
                LOG.info("Recreating l2 gateway connection {}",l2gatewayConnection.key());
                managedNewTransactionRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    tx -> tx.put(LogicalDatastoreType.CONFIGURATION, identifier, l2gatewayConnection)).get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Service recovery failed for l2gw {}", entityId);
            }
        }
        LOG.info("Finished recreation of all l2 gateway connections of l2 gateway instance {}", l2gateway.key());
    }

    public String buildServiceRegistryKey() {
        return NetvirtL2gwNode.class.toString();
    }
}
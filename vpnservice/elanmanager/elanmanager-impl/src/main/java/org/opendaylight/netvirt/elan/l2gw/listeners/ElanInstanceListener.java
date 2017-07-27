/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanInstanceListener extends AsyncClusteredDataTreeChangeListenerBase<ElanInstance,
        ElanInstanceListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceListener.class);

    private final DataBroker broker;
    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;
    private static final Map<String, List<Runnable>> WAITING_JOB_LIST = new ConcurrentHashMap<>();

    public ElanInstanceListener(final DataBroker db, final L2GatewayConnectionUtils l2GatewayConnectionUtils) {
        super(ElanInstance.class, ElanInstanceListener.class);
        broker = db;
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
        registerListener(LogicalDatastoreType.CONFIGURATION, db);
    }

    public void init() {
    }

    public void close() {
    }

    @Override
    protected void remove(final InstanceIdentifier<ElanInstance> identifier,
                          final ElanInstance del) {
        LOG.debug("Elan instance {} deleted from Operational tree ", del);
        List<L2gatewayConnection> l2gatewayConnections =
                L2GatewayConnectionUtils.getL2GwConnectionsByElanName(
                        this.broker, del.getElanInstanceName());
        if (l2gatewayConnections != null) {
            LOG.debug("L2Gatewconnection {} to be deleted as part of Elan Instance deletion {}",
                    l2gatewayConnections, del);
            for (L2gatewayConnection l2gatewayConnection : l2gatewayConnections) {
                l2GatewayConnectionUtils.deleteL2GatewayConnection(l2gatewayConnection);
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {

    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance add) {
        List<Runnable> runnables = WAITING_JOB_LIST.get(add.getElanInstanceName());
        if (runnables != null) {
            runnables.forEach(Runnable::run);
        }
    }

    public static void runJobAfterElanIsAvailable(String elanName, Runnable runnable) {
        WAITING_JOB_LIST.computeIfAbsent(elanName, (name) -> new ArrayList<>());
        WAITING_JOB_LIST.get(elanName).add(runnable);
    }

    @Override
    protected ElanInstanceListener getDataTreeChangeListener() {
        return ElanInstanceListener.this;
    }

    @Override
    protected InstanceIdentifier<ElanInstance> getWildCardPath() {
        return InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class);
    }

}
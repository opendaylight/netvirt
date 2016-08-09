/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpInterfaceConfigRemoveJob;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpInterfaceConfigListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DhcpInterfaceConfigListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private DataStoreJobCoordinator dataStoreJobCoordinator;

    public DhcpInterfaceConfigListener(DataBroker dataBroker, DhcpExternalTunnelManager dhcpExternalTunnelManager) {
        super(Interface.class);
        this.dataBroker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
    }

    public void init() {
        registerListener();
        dataStoreJobCoordinator = DataStoreJobCoordinator.getInstance();
    }

    private void registerListener() {
        try {
            listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), DhcpInterfaceConfigListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("DhcpInterfaceEventListener DataChange listener registration fail!", e);
            throw new IllegalStateException("DhcpInterfaceEventListener registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
        logger.info("DhcpInterfaceConfigListener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        DhcpInterfaceConfigRemoveJob job = new DhcpInterfaceConfigRemoveJob(dhcpExternalTunnelManager, dataBroker, del);
        dataStoreJobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(del.getName()), job, DHCPMConstants.RETRY_COUNT );
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        // Handled in update () DhcpInterfaceEventListener
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
        // Handled in add() DhcpInterfaceEventListener
    }
}

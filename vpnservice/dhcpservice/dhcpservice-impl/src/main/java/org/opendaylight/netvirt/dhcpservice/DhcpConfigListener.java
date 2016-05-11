/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.vpnservice.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DhcpConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.dhcp.config.Configs;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpConfigListener extends AsyncClusteredDataChangeListenerBase<DhcpConfig, DhcpConfigListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpConfigListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private DhcpManager dhcpManager;

    public DhcpConfigListener(final DataBroker db, final DhcpManager dhcpMgr) {
        super(DhcpConfig.class, DhcpConfigListener.class);
        dhcpManager = dhcpMgr;
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), DhcpConfigListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("NodeListener: DataChange listener registration fail!", e);
            throw new IllegalStateException("NodeListener: registration Listener failed.", e);
        }
    }

    protected InstanceIdentifier<DhcpConfig> getWildCardPath() {
        return InstanceIdentifier.create(DhcpConfig.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DhcpConfigListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.debug("DhcpConfig Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<DhcpConfig> identifier, DhcpConfig del) {
        LOG.trace("DhcpConfig removed: {}", del);
        updateConfig(null);
    }

    @Override
    protected void update(InstanceIdentifier<DhcpConfig> identifier, DhcpConfig original, DhcpConfig update) {
        LOG.trace("DhcpConfig changed to {}", update);
        updateConfig(update);
    }

    @Override
    protected void add(InstanceIdentifier<DhcpConfig> identifier, DhcpConfig add) {
        LOG.trace("DhcpConfig added {}", add);
        updateConfig(add);
    }

    private void updateConfig(DhcpConfig update) {
        //TODO: Update operational with actual values
        if(update == null || update.getConfigs() == null || update.getConfigs().isEmpty()) {
            dhcpManager.setLeaseDuration(DHCPMConstants.DEFAULT_LEASE_TIME);
            dhcpManager.setDefaultDomain(DHCPMConstants.DEFAULT_DOMAIN_NAME);
            return;
        }
        Configs config = update.getConfigs().get(0);
        if(config.getLeaseDuration() != null) {
            dhcpManager.setLeaseDuration(config.getLeaseDuration());
        }
        if(config.getDefaultDomain() != null) {
            dhcpManager.setDefaultDomain(config.getDefaultDomain());
            //TODO: What to do if string is ""
        }
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return DhcpConfigListener.this;
    }

    @Override
    protected DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }
}

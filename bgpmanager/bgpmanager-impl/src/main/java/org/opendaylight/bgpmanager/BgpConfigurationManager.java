/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.bgpmanager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.BgpRouter;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.BgpNeighbors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class BgpConfigurationManager {
    private static final Logger LOG = LoggerFactory.getLogger(BgpConfigurationManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;

    public BgpConfigurationManager(final DataBroker db) {
        broker = db;
        BgpRtrCfgManager rtrCfgManager = new BgpRtrCfgManager(db);
        BgpNghbrCfgManager nghbrCfgManager = new BgpNghbrCfgManager(db);
    }

    public class BgpRtrCfgManager extends AbstractDataChangeListener<BgpRouter> implements AutoCloseable {

        public BgpRtrCfgManager(final DataBroker db) {
            super(BgpRouter.class);
            registerListener(db);
        }

        private void registerListener(final DataBroker db) {
            try {
                listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), BgpRtrCfgManager.this, DataChangeScope.SUBTREE);
            } catch (final Exception e) {
                LOG.error("BGP Configuration DataChange listener registration fail!", e);
                throw new IllegalStateException("BGP Configuration registration Listener failed.", e);
            }
        }

        @Override
        protected void remove(InstanceIdentifier<BgpRouter> identifier,
                              BgpRouter del) {
            // TODO Auto-generated method stub
        }

        @Override
        protected void update(InstanceIdentifier<BgpRouter> identifier,
                              BgpRouter original, BgpRouter update) {
            // TODO Auto-generated method stub
        }

        @Override
        protected void add(InstanceIdentifier<BgpRouter> identifier,
                           BgpRouter value) {
            LOG.info("key: " + identifier + ", value=" + value);

        }

        private InstanceIdentifier<BgpRouter> getWildCardPath() {
            return InstanceIdentifier.create(BgpRouter.class);
        }

        @Override
        public void close() throws Exception {
            if (listenerRegistration != null) {
                try {
                    listenerRegistration.close();
                } catch (final Exception e) {
                    LOG.error("Error when cleaning up DataChangeListener.", e);
                }
                listenerRegistration = null;
            }
            LOG.info("Bgp Router Manager Closed");
        }

    }
    public class BgpNghbrCfgManager extends AbstractDataChangeListener<BgpNeighbors> implements AutoCloseable {

        public BgpNghbrCfgManager(final DataBroker db) {
            super(BgpNeighbors.class);
            registerListener(db);
        }

        private void registerListener(final DataBroker db) {
            try {
                listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), BgpNghbrCfgManager.this, DataChangeScope.SUBTREE);
            } catch (final Exception e) {
                LOG.error("BGP Neighbor DataChange listener registration fail!", e);
                throw new IllegalStateException("BGP Neighbor registration Listener failed.", e);
            }
        }

        @Override
        protected void remove(InstanceIdentifier<BgpNeighbors> identifier,
                              BgpNeighbors del) {
            // TODO Auto-generated method stub
        }

        @Override
        protected void update(InstanceIdentifier<BgpNeighbors> identifier,
                              BgpNeighbors original, BgpNeighbors update) {
            // TODO Auto-generated method stub
        }

        @Override
        protected void add(InstanceIdentifier<BgpNeighbors> identifier,
                           BgpNeighbors value) {
            LOG.info("key: " + identifier + ", value=" + value);

        }

        private InstanceIdentifier<?> getWildCardPath() {
            return InstanceIdentifier.create(BgpNeighbors.class);
        }

        @Override
        public void close() throws Exception {
            if (listenerRegistration != null) {
                try {
                    listenerRegistration.close();
                } catch (final Exception e) {
                    LOG.error("Error when cleaning up DataChangeListener.", e);
                }
                listenerRegistration = null;
            }
            LOG.info("Bgp Neighbor Manager Closed");
        }

    }
}

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

import org.apache.thrift.TException;
import org.opendaylight.bgpmanager.globals.BgpConfiguration;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.BgpRouter;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.BgpNeighbors;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.BgpNeighbor;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class BgpConfigurationManager {
    private static final Logger LOG = LoggerFactory.getLogger(BgpConfigurationManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private BgpConfiguration bgpConfiguration;
    private BgpManager bgpManager;
    private final DataBroker broker;
    private static final int MAX_RETRIES_BGP_COMMUNICATION = 1;
    private enum BgpOp {
        START_BGP, ADD_NGHBR, DEL_NGHBR
    }

    public BgpConfigurationManager(final DataBroker db, BgpConfiguration bgpCfg, BgpManager bgpMgr) {
        broker = db;
        bgpConfiguration = bgpCfg;
        bgpManager = bgpMgr;
        BgpRtrCfgManager rtrCfgManager = new BgpRtrCfgManager(broker);
        BgpNghbrCfgManager nghbrCfgManager = new BgpNghbrCfgManager(broker);
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

        private synchronized void removeBgpRouter(BgpRouter del)
        {
            bgpManager.disconnect();

            bgpConfiguration.setRouterId("");
            bgpConfiguration.setAsNum(0);

        }

        @Override
        protected void remove(InstanceIdentifier<BgpRouter> identifier,
                              BgpRouter del) {

            LOG.info("Bgp Router deleted in DS - " + "key: " + identifier + ", value=" + del);

            removeBgpRouter(del);

        }

        private synchronized void updateBgpRouter(BgpRouter original, BgpRouter update)
        {
            if(bgpConfiguration.getAsNum() != update.getLocalAsNumber()) {
                bgpConfiguration.setAsNum(update.getLocalAsNumber());
                bgpConfiguration.setConfigUpdated();
            }
            if(bgpConfiguration.getRouterId() != update.getLocalAsIdentifier().getIpv4Address().getValue()) {
                bgpConfiguration.setRouterId(update.getLocalAsIdentifier().getIpv4Address().getValue());
                bgpConfiguration.setConfigUpdated();
            }

            if(bgpConfiguration.isConfigUpdated()) {
                configureBgpServer(BgpOp.START_BGP);
                bgpConfiguration.unsetConfigUpdated();
            }

        }

        @Override
        protected void update(InstanceIdentifier<BgpRouter> identifier,
                              BgpRouter original, BgpRouter update) {

            LOG.info("Bgp Router Updated in DS - " + "key: " + identifier + ", original=" + original + ", update=" + update);

            updateBgpRouter(original, update);
        }

        private synchronized void addBgpRouter(BgpRouter value){
            if(value.getLocalAsNumber() != null) {
                bgpConfiguration.setAsNum(value.getLocalAsNumber());
            }
            if(value.getLocalAsIdentifier() != null) {
                bgpConfiguration.setRouterId(value.getLocalAsIdentifier().getIpv4Address().getValue());
            }

            if(value.getLocalAsNumber() == null || value.getLocalAsIdentifier() == null)
                return;

            configureBgpServer(BgpOp.START_BGP);
        }

        @Override
        protected void add(InstanceIdentifier<BgpRouter> identifier,
                           BgpRouter value) {
            LOG.info("Bgp Router added in DS - " + "key: " + identifier + ", value=" + value);
            LOG.info("Bgp Router localASNumber:" + value.getLocalAsNumber());
            LOG.info("Bgp Router localASIdentifier:" + value.getLocalAsIdentifier());

            addBgpRouter(value);
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

        private synchronized void removeBgpNeighbors(BgpNeighbors del) {
            List<BgpNeighbor> bgpNeighborList = del.getBgpNeighbor();
            BgpNeighbor gateway = bgpNeighborList.get(0);

            if(gateway != null) {
                if ((gateway.getPeerAddressType() != null) && (gateway.getPeerAddressType() instanceof org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.bgp.neighbor.peer.address.type.IpAddress)) {
                    IpAddress neighborIPAddr = ((org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.bgp.neighbor.peer.address.type.IpAddress) gateway.getPeerAddressType()).getIpAddress();
                    LOG.info("Bgp Neighbor IP Address " + neighborIPAddr.getIpv4Address().getValue());

                    configureBgpServer(BgpOp.DEL_NGHBR);

                    bgpConfiguration.setNeighbourIp("");
                    bgpConfiguration.setNeighbourAsNum(0);

                }
            }

        }

        @Override
        protected void remove(InstanceIdentifier<BgpNeighbors> identifier,
                              BgpNeighbors del) {

            LOG.info("Bgp Neighbors deleted in DS - " + "key: " + identifier + ", value=" + del);
            removeBgpNeighbors(del);
        }

        private synchronized void updateBgpNeighbors(BgpNeighbors original, BgpNeighbors update) {

            List<BgpNeighbor> bgpNeighborList = update.getBgpNeighbor();

            //handle the case where there are no neighbors configured - single neighbor entry has been deleted
            if(bgpNeighborList.isEmpty()) {
                configureBgpServer(BgpOp.DEL_NGHBR);
                return;
            }

            //We will always consider the first element of this list, since there can be just one DC Gateway
            BgpNeighbor gateway = bgpNeighborList.get(0);

            if(gateway != null) {
                if(gateway.getAsNumber() != null ||
                    ((gateway.getPeerAddressType() != null) && (gateway.getPeerAddressType() instanceof org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.bgp.neighbor.peer.address.type.IpAddress))) {
                    //there is an updated neighbor, so we need to delete the old neighbor
                    configureBgpServer(BgpOp.DEL_NGHBR);
                }
                if(gateway.getAsNumber() != null) {
                    LOG.info("Bgp Neighbor AS number " + gateway.getAsNumber());
                    if(bgpConfiguration.getNeighbourAsNum() != gateway.getAsNumber()) {
                        bgpConfiguration.setNeighbourAsNum(gateway.getAsNumber());
                        bgpConfiguration.setConfigUpdated();
                    }
                }
                if((gateway.getPeerAddressType() != null) && (gateway.getPeerAddressType() instanceof org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.bgp.neighbor.peer.address.type.IpAddress)) {
                    IpAddress neighborIPAddr = ((org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.bgp.neighbor.peer.address.type.IpAddress)gateway.getPeerAddressType()).getIpAddress();
                    LOG.info("Bgp Neighbor IP Address " + neighborIPAddr.getIpv4Address().getValue());
                    if(bgpConfiguration.getNeighbourIp() != neighborIPAddr.getIpv4Address().getValue()) {
                        bgpConfiguration.setNeighbourIp(neighborIPAddr.getIpv4Address().getValue());
                        bgpConfiguration.setConfigUpdated();
                    }

                }
            }
            if(bgpConfiguration.isConfigUpdated()) {
                //add the newly configured neighbor
                configureBgpServer(BgpOp.ADD_NGHBR);
            }
        }

        @Override
        protected void update(InstanceIdentifier<BgpNeighbors> identifier,
                              BgpNeighbors original, BgpNeighbors update) {

            LOG.info("Bgp Neighbors Updated in DS - " + "key: " + identifier + ", original=" + original + ", update=" + update);

            updateBgpNeighbors(original, update);

        }

        private synchronized void addBgpNeighbors(BgpNeighbors value) {
            List<BgpNeighbor> bgpNeighborList = value.getBgpNeighbor();

            //We will always consider the first element of this list, since there can be just one DC Gateway
            BgpNeighbor gateway = bgpNeighborList.get(0);

            if(gateway != null) {
                if(gateway.getAsNumber() != null) {
                    LOG.info("Bgp Neighbor AS number " + gateway.getAsNumber());
                    bgpConfiguration.setNeighbourAsNum(gateway.getAsNumber());
                }
                if((gateway.getPeerAddressType() != null) && (gateway.getPeerAddressType() instanceof org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.bgp.neighbor.peer.address.type.IpAddress)) {
                    IpAddress neighborIPAddr = ((org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.bgp.neighbor.peer.address.type.IpAddress)gateway.getPeerAddressType()).getIpAddress();
                    LOG.info("Bgp Neighbor IP Address " + neighborIPAddr.getIpv4Address().getValue());
                    bgpConfiguration.setNeighbourIp(neighborIPAddr.getIpv4Address().getValue());

                }
                if(bgpConfiguration.getNeighbourAsNum() != 0  && bgpConfiguration.getNeighbourIp() != null) {
                    configureBgpServer(BgpOp.ADD_NGHBR);
                }
            }

        }

        @Override
        protected void add(InstanceIdentifier<BgpNeighbors> identifier,
                           BgpNeighbors value) {
            LOG.info("key: " + identifier + ", value=" + value);
            LOG.info("Bgp Neighbor added in DS - " + "key: " + identifier + ", value=" + value);

            addBgpNeighbors(value);
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

    private void configureBgpServer(BgpOp bgpOp) {
        int retryCount = 0;
        boolean retry = false;
        do {
            try {
                switch(bgpOp) {
                    case START_BGP:
                        bgpManager.startBgpService();
                        break;
                    case ADD_NGHBR:
                        bgpManager.addNeighbor(bgpConfiguration.getNeighbourIp(), bgpConfiguration.getNeighbourAsNum());
                        break;
                    case DEL_NGHBR:
                        bgpManager.deleteNeighbor(bgpConfiguration.getNeighbourIp());
                        break;
                    default:
                        LOG.info("Invalid configuration option");
                }

                retry = false;
            } catch (TException t) {
                retry = true;
                retryCount++;
            }
        } while(retry && retryCount <= MAX_RETRIES_BGP_COMMUNICATION);
    }
}

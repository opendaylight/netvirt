/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import java.util.Map;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EvpnRdNetworkListener extends AsyncDataTreeChangeListenerBase<EvpnRdToNetwork, EvpnRdNetworkListener>
        implements AutoCloseable, ClusteredDataTreeChangeListener<EvpnRdToNetwork> {
    private final DataBroker broker;
    private final EntityOwnershipService entityOwnershipService;
    private static final Logger LOG = LoggerFactory.getLogger(EvpnRdNetworkListener.class);


    public EvpnRdNetworkListener(DataBroker dataBroker,
                                 EntityOwnershipService entityOwnershipService) {
        super(EvpnRdToNetwork.class, EvpnRdNetworkListener.class);
        this.broker = dataBroker;
        this.entityOwnershipService = entityOwnershipService;
    }

    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<EvpnRdToNetwork> getWildCardPath() {
        return InstanceIdentifier.create(EvpnRdToNetworks.class).child(EvpnRdToNetwork.class);
    }

    @Override
    protected EvpnRdNetworkListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void add(InstanceIdentifier<EvpnRdToNetwork> instanceIdentifier, EvpnRdToNetwork rdToNetwork) {
        if (BgpConfigurationManager.ignoreClusterDcnEventForFollower()) {
            return;
        }
        String rd = rdToNetwork.getVrfId();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Add for RD {} Netwrok {}", rd, elanName);
        addExternalTepstoElanInstance(rd);
    }

    @Override
    protected void update(InstanceIdentifier<EvpnRdToNetwork> instanceIdentifier, EvpnRdToNetwork rdToNetwork,
                          EvpnRdToNetwork rdToNetworkOld) {
        String rd = rdToNetwork.getVrfId();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Update for RD {} Netwrok {}", rd, elanName);
        LOG.trace("Update operation not supported");

    }

    @Override
    protected void remove(InstanceIdentifier<EvpnRdToNetwork> instanceIdentifier, EvpnRdToNetwork rdToNetwork) {
        if (BgpConfigurationManager.ignoreClusterDcnEventForFollower()) {
            return;
        }
        String rd = rdToNetwork.getVrfId();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Delete for RD {} Netwrok {}", rd, elanName);
        deleteExternalTepsfromElanInstance(rd);
    }

    public void addExternalTepstoElanInstance(String rd) {
        Map<String, Map<String, Map<String, Long>>> rt2Map = BgpConfigurationManager.getRt2TepMap();
        if (!rt2Map.isEmpty()) {
            if (rt2Map.containsKey(rd)) {
                rt2Map.get(rd).forEach((tepIp, mac) -> {
                    LOG.debug("Adding tep {} to Elan Corresponding to RD {}", tepIp, rd);
                    BgpUtil.addTepToElanInstance(broker, rd, tepIp);
                });
            } else {
                LOG.debug("No entry for rd {}", rd);
            }
        }
    }

    public void deleteExternalTepsfromElanInstance(String rd) {
        Map<String, Map<String, Map<String, Long>>> rt2Map = BgpConfigurationManager.getRt2TepMap();
        if (!rt2Map.isEmpty()) {
            if (rt2Map.containsKey(rd)) {
                rt2Map.get(rd).forEach((tepIp, mac) -> {
                    LOG.debug("Deleting tep {} to Elan Corresponding to RD {}", tepIp, rd);
                    BgpUtil.deleteTepFromElanInstance(broker, rd, tepIp);
                });
            } else {
                LOG.debug("No entry for rd {}", rd);
            }
        }
    }
}

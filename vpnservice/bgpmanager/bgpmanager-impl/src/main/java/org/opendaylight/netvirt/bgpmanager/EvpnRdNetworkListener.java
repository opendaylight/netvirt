/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EvpnRdNetworkListener extends AsyncDataTreeChangeListenerBase<EvpnRdToNetwork, EvpnRdNetworkListener>
        implements AutoCloseable, ClusteredDataTreeChangeListener<EvpnRdToNetwork> {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnRdNetworkListener.class);

    private final DataBroker broker;
    private final BgpConfigurationManager bgpConfigManager;

    public EvpnRdNetworkListener(DataBroker dataBroker,
                                 BgpConfigurationManager bgpConfigManager) {
        super(EvpnRdToNetwork.class, EvpnRdNetworkListener.class);
        this.broker = dataBroker;
        this.bgpConfigManager = bgpConfigManager;
    }

    @Override
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
        if (!bgpConfigManager.isBGPEntityOwner()) {
            return;
        }
        String rd = rdToNetwork.getRd();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Add for RD {} Netwrok {}", rd, elanName);
        addExternalTepstoElanInstance(rd);
    }

    @Override
    protected void update(InstanceIdentifier<EvpnRdToNetwork> instanceIdentifier, EvpnRdToNetwork rdToNetwork,
                          EvpnRdToNetwork rdToNetworkOld) {
        String rd = rdToNetwork.getRd();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Update for RD {} Netwrok {}", rd, elanName);
        LOG.trace("Update operation not supported");

    }

    @Override
    protected void remove(InstanceIdentifier<EvpnRdToNetwork> instanceIdentifier, EvpnRdToNetwork rdToNetwork) {
        if (!bgpConfigManager.isBGPEntityOwner()) {
            return;
        }
        String rd = rdToNetwork.getRd();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Delete for RD {} Netwrok {}", rd, elanName);
        deleteExternalTepsfromElanInstance(rd);
    }

    private void addExternalTepstoElanInstance(String rd) {
        for (String tepIp: bgpConfigManager.getTepIPs(rd)) {
            LOG.debug("Adding tep {} to Elan Corresponding to RD {}", tepIp, rd);
            BgpUtil.addTepToElanInstance(broker, rd, tepIp);
        }
    }

    private void deleteExternalTepsfromElanInstance(String rd) {
        for (String tepIp: bgpConfigManager.getTepIPs(rd)) {
            LOG.debug("Deleting tep {} to Elan Corresponding to RD {}", tepIp, rd);
            BgpUtil.deleteTepFromElanInstance(broker, rd, tepIp);
        }
    }
}

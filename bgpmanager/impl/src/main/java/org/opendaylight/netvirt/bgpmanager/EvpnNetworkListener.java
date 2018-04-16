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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.to.networks.EvpnToNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EvpnNetworkListener extends AsyncDataTreeChangeListenerBase<EvpnToNetwork, EvpnNetworkListener>
        implements ClusteredDataTreeChangeListener<EvpnToNetwork> {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnNetworkListener.class);

    private final DataBroker broker;
    private final BgpConfigurationManager bgpConfigManager;
    private final BgpUtil bgpUtil;

    public EvpnNetworkListener(DataBroker dataBroker, BgpConfigurationManager bgpConfigManager, BgpUtil bgpUtil) {
        super(EvpnToNetwork.class, EvpnNetworkListener.class);
        this.broker = dataBroker;
        this.bgpConfigManager = bgpConfigManager;
        this.bgpUtil = bgpUtil;
    }

    @Override
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<EvpnToNetwork> getWildCardPath() {
        return InstanceIdentifier.create(EvpnToNetworks.class).child(EvpnToNetwork.class);
    }

    @Override
    protected EvpnNetworkListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void add(InstanceIdentifier<EvpnToNetwork> instanceIdentifier, EvpnToNetwork rdToNetwork) {
        if (!bgpConfigManager.isBGPEntityOwner()) {
            return;
        }
        String rd = rdToNetwork.getRd();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Add for RD {} Netwrok {}", rd, elanName);
        addExternalTepstoElanInstance(rd);
    }

    @Override
    protected void update(InstanceIdentifier<EvpnToNetwork> instanceIdentifier, EvpnToNetwork rdToNetwork,
                          EvpnToNetwork rdToNetworkOld) {
        String rd = rdToNetwork.getRd();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Update for RD {} Netwrok {}", rd, elanName);
        LOG.trace("Update operation not supported");

    }

    @Override
    protected void remove(InstanceIdentifier<EvpnToNetwork> instanceIdentifier, EvpnToNetwork rdToNetwork) {
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
            bgpUtil.addTepToElanInstance(rd, tepIp);
        }
    }

    private void deleteExternalTepsfromElanInstance(String rd) {
        for (String tepIp: bgpConfigManager.getTepIPs(rd)) {
            LOG.debug("Deleting tep {} to Elan Corresponding to RD {}", tepIp, rd);
            bgpUtil.deleteTepFromElanInstance(rd, tepIp);
        }
    }
}

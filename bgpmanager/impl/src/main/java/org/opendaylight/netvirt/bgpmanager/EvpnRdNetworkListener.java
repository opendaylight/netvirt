/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EvpnRdNetworkListener extends AbstractAsyncDataTreeChangeListener<EvpnRdToNetwork>
        implements ClusteredDataTreeChangeListener<EvpnRdToNetwork> {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnRdNetworkListener.class);

    private final DataBroker broker;
    private final BgpConfigurationManager bgpConfigManager;
    private final BgpUtil bgpUtil;

    public EvpnRdNetworkListener(DataBroker dataBroker, BgpConfigurationManager bgpConfigManager, BgpUtil bgpUtil,
            final IdManagerService idManager) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class),
                Executors.newListeningSingleThreadExecutor("EvpnRdNetworkListener", LOG));
        this.broker = dataBroker;
        this.bgpConfigManager = bgpConfigManager;
        this.bgpUtil = bgpUtil;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} registered", getClass().getSimpleName());
    }

    @Override
    public void add(InstanceIdentifier<EvpnRdToNetwork> instanceIdentifier, EvpnRdToNetwork rdToNetwork) {
        if (!bgpConfigManager.isBGPEntityOwner()) {
            return;
        }
        String rd = rdToNetwork.getRd();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Add for RD {} Netwrok {}", rd, elanName);
        addExternalTepstoElanInstance(rd);
    }

    @Override
    public void update(InstanceIdentifier<EvpnRdToNetwork> instanceIdentifier, EvpnRdToNetwork rdToNetwork,
                          EvpnRdToNetwork rdToNetworkOld) {
        String rd = rdToNetwork.getRd();
        String elanName = rdToNetwork.getNetworkId();
        LOG.trace("Received EvpnRdToNetwork Update for RD {} Netwrok {}", rd, elanName);
        LOG.trace("Update operation not supported");

    }

    @Override
    public void remove(InstanceIdentifier<EvpnRdToNetwork> instanceIdentifier, EvpnRdToNetwork rdToNetwork) {
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

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }
}

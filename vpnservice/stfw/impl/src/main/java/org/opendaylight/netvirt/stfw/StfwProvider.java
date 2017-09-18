/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw;

import java.math.BigInteger;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.stfw.api.IStfwService;
import org.opendaylight.netvirt.stfw.northbound.NorthBoundConfigurationManager;
import org.opendaylight.netvirt.stfw.simulator.openflow.InventoryConfig;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsSimulator;
import org.opendaylight.netvirt.stfw.utils.CliUtils;
import org.opendaylight.netvirt.stfw.utils.ItmUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StfwProvider implements AutoCloseable, IStfwService {
    private static final Logger LOG = LoggerFactory.getLogger(StfwProvider.class);
    private final DataBroker dataBroker;
    private final OvsSimulator ovsSimulator;
    private final NorthBoundConfigurationManager nbConfigMgr;
    private final InventoryConfig inventoryConfig;

    @Inject
    public StfwProvider(final DataBroker dataBroker, final OvsSimulator ovsSimulator,
                        final NorthBoundConfigurationManager northBoundConfigurationManager,
                        final InventoryConfig inventoryConfig) {
        this.dataBroker = dataBroker;
        this.ovsSimulator = ovsSimulator;
        this.nbConfigMgr = northBoundConfigurationManager;
        this.inventoryConfig = inventoryConfig;
        LOG.info("StfwProvider Started");
    }

    @Override
    @PreDestroy
    public void close() throws Exception {
        LOG.info("StfwProvider Closed");
    }

    public DataBroker getDataBroker() {
        return this.dataBroker;
    }

    @Override
    public void createNetwork(int count, boolean createVpn) {
        nbConfigMgr.createNetwork(count, createVpn);
    }

    @Override
    public void deleteNetwork() {
        nbConfigMgr.delete();
    }

    @Override
    public void createOvsSwitches(int count, boolean autoMesh) {
        ovsSimulator.addSwitches(count);
        if (autoMesh) {
            ItmUtils.createMesh(ovsSimulator);
        }
    }

    @Override
    public void deleteOvsSwitches() {
        ovsSimulator.deleteSwitches();
        ItmUtils.deleteMesh(dataBroker);
    }

    @Override
    public void dumpSwitch(BigInteger count) {
        inventoryConfig.dumpSwitch(count);
    }

    @Override
    public void createVMs(int count) {
        nbConfigMgr.createVms(count);
    }

    @Override
    public void deleteVMs() {
        nbConfigMgr.deleteVms();
    }

    @Override
    public void displayFlowCount() {
        inventoryConfig.dumpFlowCount(ovsSimulator.getDpnToFlowCount());
    }

    @Override
    public void displayInterfacesCount() {
        CliUtils.dumpInterfaceCount(ovsSimulator.getInterfacesCount());
    }

    @Override
    public void createVpns() {
        LOG.debug("creating {} VPNs not implemented yet");
    }

    @Override
    public void deleteVpns() {
        LOG.debug("deleting {} VPNs not implemented yet");
    }
}

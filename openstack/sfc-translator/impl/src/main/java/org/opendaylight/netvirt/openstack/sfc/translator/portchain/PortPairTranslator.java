/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.sfc.translator.portchain;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.openstack.sfc.translator.INeutronSfcDataProcessor;
import org.opendaylight.netvirt.openstack.sfc.translator.NeutronMdsalHelper;
import org.opendaylight.netvirt.openstack.sfc.translator.SfcMdsalHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class will convert OpenStack Port Pair API yang models present in
 * neutron northbound project to OpenDaylight SFC yang models.
 */
public class PortPairTranslator implements INeutronSfcDataProcessor<PortPair> {
    private static final Logger LOG = LoggerFactory.getLogger(PortPairTranslator.class);

    private final DataBroker db;
    private NeutronPortPairListener neutronPortPairListener;
    private final SfcMdsalHelper sfcMdsalHelper;
    private final NeutronMdsalHelper neutronMdsalHelper;

    public PortPairTranslator(DataBroker db) {
        this.db = db;
        sfcMdsalHelper = new SfcMdsalHelper(db);
        neutronMdsalHelper = new NeutronMdsalHelper(db);
    }

    public void start() {
        LOG.info("Port Pair Translator Initialized.");
        if(neutronPortPairListener == null) {
            neutronPortPairListener = new NeutronPortPairListener(db, this);
        }
    }

    /**
     * Method removes PortPair which is identified by InstanceIdentifier.
     *
     * @param path - the whole path to PortPair
     * @param deletedPortPair        - PortPair for removing
     */
    @Override
    public void remove(InstanceIdentifier<PortPair> path, PortPair deletedPortPair) {

    }

    /**
     * Method updates the original PortPair to the update PortPair.
     * Both are identified by same InstanceIdentifier.
     *
     * @param path - the whole path to PortPair
     * @param originalPortPair   - original PortPair (for update)
     * @param updatePortPair     - changed PortPair (contain updates)
     */
    @Override
    public void update(InstanceIdentifier<PortPair> path, PortPair originalPortPair, PortPair updatePortPair) {

    }

    /**
     * Method adds the PortPair which is identified by InstanceIdentifier
     * to device.
     *
     * @param path - the whole path to new PortPair
     * @param newPortPair        - new PortPair
     */
    @Override
    public void add(InstanceIdentifier<PortPair> path, PortPair newPortPair) {

    }
}

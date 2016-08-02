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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.chains.PortChain;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class will convert OpenStack Port Chain API yang models present in
 * neutron northbound project to OpenDaylight SFC yang models.
 */
public class PortChainTranslator implements INeutronSfcDataProcessor<PortChain> {
    private static final Logger LOG = LoggerFactory.getLogger(PortChainTranslator.class);

    private final DataBroker db;
    private NeutronPortChainListener neutronPortChainListener;
    private final SfcMdsalHelper sfcMdsalHelper;
    private final NeutronMdsalHelper neutronMdsalHelper;

    public PortChainTranslator(DataBroker db) {
        this.db = db;
        sfcMdsalHelper = new SfcMdsalHelper(db);
        neutronMdsalHelper = new NeutronMdsalHelper(db);
    }

    public void start() {
        LOG.info("Port Chain Translator Initialized.");
        if(neutronPortChainListener == null) {
            neutronPortChainListener = new NeutronPortChainListener(db, this);
        }
    }

    /**
     * Method removes PortChain which is identified by InstanceIdentifier.
     *
     * @param path - the whole path to PortChain
     * @param deletedPortChain        - PortChain for removing
     */
    @Override
    public void remove(InstanceIdentifier<PortChain> path, PortChain deletedPortChain) {

    }

    /**
     * Method updates the original PortChain to the update PortChain.
     * Both are identified by same InstanceIdentifier.
     *
     * @param path - the whole path to PortChain
     * @param originalPortChain   - original PortChain (for update)
     * @param updatePortChain     - changed PortChain (contain updates)
     */
    @Override
    public void update(InstanceIdentifier<PortChain> path, PortChain originalPortChain, PortChain updatePortChain) {

    }

    /**
     * Method adds the PortChain which is identified by InstanceIdentifier
     * to device.
     *
     * @param path - the whole path to new PortChain
     * @param newPortChain        - new PortChain
     */
    @Override
    public void add(InstanceIdentifier<PortChain> path, PortChain newPortChain) {

    }
}

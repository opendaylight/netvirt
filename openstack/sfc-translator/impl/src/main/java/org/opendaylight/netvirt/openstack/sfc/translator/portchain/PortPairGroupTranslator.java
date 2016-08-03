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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pair.groups.PortPairGroup;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class will convert OpenStack Port Pair API yang models present in
 * neutron northbound project to OpenDaylight SFC yang models.
 */
public class PortPairGroupTranslator implements INeutronSfcDataProcessor<PortPairGroup> {
    private static final Logger LOG = LoggerFactory.getLogger(PortPairGroupTranslator.class);

    private final DataBroker db;
    private NeutronPortPairGroupListener neutronPortPairGroupListener;
    private final SfcMdsalHelper sfcMdsalHelper;
    private final NeutronMdsalHelper neutronMdsalHelper;

    public PortPairGroupTranslator(DataBroker db) {
        this.db = db;
        sfcMdsalHelper = new SfcMdsalHelper(db);
        neutronMdsalHelper = new NeutronMdsalHelper(db);
    }

    public void start() {
        LOG.info("Port Pair Group Translator Initialized.");
        if(neutronPortPairGroupListener == null) {
            neutronPortPairGroupListener = new NeutronPortPairGroupListener(db, this);
        }
    }

    /**
     * Method removes PortPairGroup which is identified by InstanceIdentifier.
     *
     * @param path - the whole path to PortPairGroup
     * @param deletedPortPairGroup        - PortPairGroup for removing
     */
    @Override
    public void remove(InstanceIdentifier<PortPairGroup> path, PortPairGroup deletedPortPairGroup) {

    }

    /**
     * Method updates the original PortPairGroup to the update PortPairGroup.
     * Both are identified by same InstanceIdentifier.
     *
     * @param path - the whole path to PortPairGroup
     * @param originalPortPairGroup   - original PortPairGroup (for update)
     * @param updatePortPairGroup     - changed PortPairGroup (contain updates)
     */
    @Override
    public void update(InstanceIdentifier<PortPairGroup> path,
                       PortPairGroup originalPortPairGroup,
                       PortPairGroup updatePortPairGroup) {

    }

    /**
     * Method adds the PortPairGroup which is identified by InstanceIdentifier
     * to device.
     *
     * @param path - the whole path to new PortPairGroup
     * @param newPortPairGroup        - new PortPairGroup
     */
    @Override
    public void add(InstanceIdentifier<PortPairGroup> path, PortPairGroup newPortPairGroup) {

    }
}

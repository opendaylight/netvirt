/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.l2gw.jobs.BcGroupUpdateJob;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanRefUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanExtnTepListener extends AsyncClusteredDataTreeChangeListenerBase<ExternalTeps, ElanExtnTepListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanExtnTepListener.class);

    private final DataBroker broker;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanRefUtil elanRefUtil;

    @Inject
    public ElanExtnTepListener(DataBroker dataBroker, ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                               ElanRefUtil elanRefUtil) {
        super(ExternalTeps.class, ElanExtnTepListener.class);
        this.broker = dataBroker;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanRefUtil = elanRefUtil;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    public InstanceIdentifier<ExternalTeps> getWildCardPath() {
        return InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class).child(ExternalTeps.class);
    }

    @Override
    protected void add(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep) {
        LOG.trace("ExternalTeps add received {}", instanceIdentifier);
        updateBcGroupOfElan(instanceIdentifier, tep);
    }

    @Override
    protected void update(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep, ExternalTeps t1) {
    }

    @Override
    protected void remove(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep) {
        LOG.trace("ExternalTeps remove received {}", instanceIdentifier);
        updateBcGroupOfElan(instanceIdentifier, tep);
    }

    protected void updateBcGroupOfElan(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep) {
        String elanName = instanceIdentifier.firstKeyOf(ElanInstance.class).getElanInstanceName();
        BcGroupUpdateJob.updateAllBcGroups(elanName, elanRefUtil, elanL2GatewayMulticastUtils, broker);
    }

    @Override
    protected ElanExtnTepListener getDataTreeChangeListener() {
        return this;
    }
}

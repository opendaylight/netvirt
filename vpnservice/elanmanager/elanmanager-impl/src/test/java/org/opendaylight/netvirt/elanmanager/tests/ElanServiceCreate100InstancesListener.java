/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified ElanInstanceManager.
 *
 * @author Michael Vorburger
 */
public class ElanServiceCreate100InstancesListener
        extends AsyncDataTreeChangeListenerBase<ElanInstance, ElanServiceCreate100InstancesListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceCreate100InstancesListener.class);

    private final DataBroker broker;

    @Inject
    public ElanServiceCreate100InstancesListener(DataBroker broker) {
        super();
        this.broker = broker;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
        LOG.info("registered listener");
    }

    @Override
    protected InstanceIdentifier<ElanInstance> getWildCardPath() {
        return InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class);
    }

    @Override
    protected ElanServiceCreate100InstancesListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> key, ElanInstance elanInstanceAdded) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        // ElanUtils.updateOperationalDataStore(broker, idManager, elanInstanceAdded, new ArrayList<>(), tx);
        String elanInstanceName = elanInstanceAdded.getElanInstanceName();
        Long elanTag = elanInstanceAdded.getElanTag();
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                .setDescription(elanInstanceAdded.getDescription())
                .setMacTimeout(elanInstanceAdded.getMacTimeout() == null ? ElanConstants.DEFAULT_MAC_TIME_OUT
                        : elanInstanceAdded.getMacTimeout())
                .setKey(elanInstanceAdded.getKey()).setElanTag(elanTag);
//        if (isEtreeInstance(elanInstanceAdded)) {
//            EtreeInstance etreeInstance = new EtreeInstanceBuilder()
//                    .setEtreeLeafTagVal(new EtreeLeafTag(etreeLeafTag))
//                    .build();
//            elanInstanceBuilder.addAugmentation(EtreeInstance.class, etreeInstance);
//        }
        ElanInstance elanInstanceWithTag = elanInstanceBuilder.build();
        tx.merge(LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInstanceConfigurationDataPath(elanInstanceName),
                elanInstanceWithTag, true);

        ElanUtils.waitForTransactionToComplete(tx);
    }

    @Override
    protected void update(InstanceIdentifier<ElanInstance> key, ElanInstance dataObjectModificationBefore,
            ElanInstance dataObjectModificationAfter) {
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInstance> key, ElanInstance dataObjectModification) {
    }

}

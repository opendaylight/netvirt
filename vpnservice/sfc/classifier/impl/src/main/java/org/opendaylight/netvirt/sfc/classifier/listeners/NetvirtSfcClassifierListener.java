/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.sfc.classifier.service.ClassifierService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.classifier.rev150105.Classifiers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.classifier.rev150105.classifiers.Classifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Data tree listener for Classifier.
 */
@Singleton
public class NetvirtSfcClassifierListener
    extends AsyncDataTreeChangeListenerBase<Classifier, NetvirtSfcClassifierListener>
    implements AutoCloseable {

    private final DataBroker dataBroker;
    private final ClassifierService classifierService;

    @Inject
    public NetvirtSfcClassifierListener(final DataBroker dataBroker, final ClassifierService classifierService) {
        super(Classifier.class, NetvirtSfcClassifierListener.class);

        this.dataBroker = dataBroker;
        this.classifierService = classifierService;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Classifier> getWildCardPath() {
        return InstanceIdentifier
            .create(Classifiers.class)
            .child(Classifier.class);
    }

    @Override
    protected NetvirtSfcClassifierListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void add(InstanceIdentifier<Classifier> key, Classifier classifier) {
        classifierService.updateAll();
    }

    @Override
    protected void remove(InstanceIdentifier<Classifier> key, Classifier classifier) {
        classifierService.updateAll();
    }

    @Override
    protected void update(InstanceIdentifier<Classifier> key, Classifier classifierBefore,
            Classifier classifierAfter) {
        classifierService.updateAll();
    }
}

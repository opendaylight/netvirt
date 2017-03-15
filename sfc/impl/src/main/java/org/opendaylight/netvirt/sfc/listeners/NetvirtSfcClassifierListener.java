/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.sfc.processors.NetvirtSfcDataProcessorBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.sfc.classifier.rev150105.classifiers.Classifier;

import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Data tree listener for Classifier.
 */
public class NetvirtSfcClassifierListener
  extends AsyncDataTreeChangeListenerBase<Classifier, NetvirtSfcClassifierListener>
  implements AutoCloseable {

    private final DataBroker dataBroker;
    private final NetvirtSfcDataProcessorBase<Classifier> dataProcessor;

    @Inject
    public NetvirtSfcClassifierListener(DataBroker dataBroker, NetvirtSfcDataProcessorBase<Classifier> dataProcessor) {
        super(Classifier.class, NetvirtSfcClassifierListener.class);

        this.dataBroker = dataBroker;
        this.dataProcessor = dataProcessor;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected NetvirtSfcClassifierListener getDataTreeChangeListener() {
        return null;
    }

    @Override
    protected InstanceIdentifier<Classifier> getWildCardPath() {
        return null;
    }

    @Override
    protected void add(InstanceIdentifier<Classifier> key, Classifier classifier) {
        dataProcessor.add(key, classifier);
    }

    @Override
    protected void remove(InstanceIdentifier<Classifier> key, Classifier classifier) {
        dataProcessor.remove(key, classifier);
    }

    @Override
    protected void update(InstanceIdentifier<Classifier> key, Classifier classifierBefore,
            Classifier classifierAfter) {
        dataProcessor.update(key, classifierBefore, classifierAfter);
    }
}

/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.netvirt.sfc.classifier.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.NetvirtProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.OpenFlow13Provider;
import org.opendaylight.netvirt.sfc.classifier.providers.SfcProvider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;
import org.opendaylight.netvirt.sfc.classifier.service.domain.impl.ClassifierUpdate;
import org.opendaylight.netvirt.sfc.classifier.service.domain.impl.ConfigurationClassifierImpl;
import org.opendaylight.netvirt.sfc.classifier.service.domain.impl.GeniusRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.impl.OpenflowRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.impl.OperationalClassifierImpl;
import org.opendaylight.netvirt.sfc.classifier.utils.LastTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ClassifierService {

    private final NetvirtProvider netvirtProvider;
    private final GeniusProvider geniusProvider;
    private final SfcProvider sfcProvider;
    private final DataBroker dataBroker;
    private final Executor lastTaskExecutor;
    private final OperationalClassifierImpl operationalClassifier = new OperationalClassifierImpl();
    private final List<ClassifierEntryRenderer> classifierRenderers = new ArrayList<>();
    private static final Logger LOG = LoggerFactory.getLogger(ClassifierService.class);

    @Inject
    public ClassifierService(final NetvirtProvider netvirtProvider, final GeniusProvider geniusProvider,
                             final SfcProvider sfcProvider, final OpenFlow13Provider openFlow13Provider,
                             final DataBroker dataBroker) {
        this.netvirtProvider = netvirtProvider;
        this.geniusProvider = geniusProvider;
        this.sfcProvider = sfcProvider;
        this.dataBroker = dataBroker;
        this.lastTaskExecutor = new LastTaskExecutor(
                Executors.newSingleThreadExecutor(getClass().getSimpleName(), LOG));
        classifierRenderers.add(new OpenflowRenderer(openFlow13Provider, geniusProvider, dataBroker));
        classifierRenderers.add(new GeniusRenderer(geniusProvider));
        classifierRenderers.add(operationalClassifier.getRenderer());
    }

    public void updateAll() {
        lastTaskExecutor.execute(this::doUpdateAll);
    }

    private void doUpdateAll() {

        ClassifierState configurationClassifier = new ConfigurationClassifierImpl(
                geniusProvider,
                netvirtProvider,
                sfcProvider,
                dataBroker);

        ClassifierUpdate classifierUpdate = new ClassifierUpdate(
                configurationClassifier,
                operationalClassifier,
                classifierRenderers);

        classifierUpdate.run();
    }
}

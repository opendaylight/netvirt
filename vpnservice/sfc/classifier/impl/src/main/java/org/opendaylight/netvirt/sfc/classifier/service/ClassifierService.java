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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
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

@Singleton
public class ClassifierService {

    private final NetvirtProvider netvirtProvider;
    private final GeniusProvider geniusProvider;
    private final SfcProvider sfcProvider;
    private final OpenFlow13Provider openFlow13Provider;
    private final DataBroker dataBroker;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<Runnable> lastTask = new AtomicReference<>();
    private final OperationalClassifierImpl operationalClassifier = new OperationalClassifierImpl();
    private final List<ClassifierEntryRenderer> classifierRenderers = new ArrayList<>();

    @Inject
    public ClassifierService(final NetvirtProvider netvirtProvider, final GeniusProvider geniusProvider,
                             final SfcProvider sfcProvider, final OpenFlow13Provider openFlow13Provider,
                             final DataBroker dataBroker) {
        this.netvirtProvider = netvirtProvider;
        this.geniusProvider = geniusProvider;
        this.sfcProvider = sfcProvider;
        this.openFlow13Provider = openFlow13Provider;
        this.dataBroker = dataBroker;
        classifierRenderers.add(new OpenflowRenderer(openFlow13Provider, dataBroker));
        classifierRenderers.add(new GeniusRenderer(geniusProvider));
        classifierRenderers.add(operationalClassifier.getRenderer());
    }

    public void updateAll() {
        lastTask.set(this::doUpdateAll);
        executor.execute(() -> {
            Runnable task = lastTask.getAndSet(null);
            if (task != null) {
                task.run();
            }
        });
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

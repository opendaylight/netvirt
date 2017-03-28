/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderableEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;

public class ClassifierUpdate implements Runnable {

    private final ClassifierState configurationClassifier;
    private final ClassifierState operationalClassifier;
    private final List<ClassifierEntryRenderer> classifierRenderers;

    public ClassifierUpdate(ClassifierState configurationClassifier, ClassifierState operationalClassifier,
                            List<ClassifierEntryRenderer> classifierRenderers) {
        this.configurationClassifier = configurationClassifier;
        this.operationalClassifier = operationalClassifier;
        this.classifierRenderers = classifierRenderers;
    }

    @Override
    public void run() {
        Set<ClassifierRenderableEntry> configurationEntries = configurationClassifier.getAllEntries();
        Set<ClassifierRenderableEntry> operationalEntries = operationalClassifier.getAllEntries();
        Set<ClassifierRenderableEntry> entriesToAdd = Sets.difference(configurationEntries, operationalEntries);
        Set<ClassifierRenderableEntry> entriesToRemove = Sets.difference(operationalEntries, configurationEntries);

        classifierRenderers.forEach(
            classifierRenderer -> {
                entriesToAdd.forEach(classifierRenderableEntry ->
                        classifierRenderableEntry.render(classifierRenderer));
                entriesToRemove.forEach(classifierRenderableEntry ->
                        classifierRenderableEntry.suppress(classifierRenderer));
            });
    }
}

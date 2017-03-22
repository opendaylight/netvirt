/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;

public class ClassifierUpdate implements Runnable {

    private final ClassifierState configurationClassifier;
    private final ClassifierState operationalClassifier;
    private final List<ClassifierRenderer> classifierRenderers;

    public ClassifierUpdate(ClassifierState configurationClassifier, ClassifierState operationalClassifier,
                            List<ClassifierRenderer> classifierRenderers) {
        this.configurationClassifier = configurationClassifier;
        this.operationalClassifier = operationalClassifier;
        this.classifierRenderers = classifierRenderers;
    }

    @Override
    public void run() {
        Set<ClassifierEntry> configurationEntries = configurationClassifier.getAllEntries();
        Set<ClassifierEntry> operationalEntries = operationalClassifier.getAllEntries();
        Set<ClassifierEntry> entriesToAdd = Sets.difference(configurationEntries, operationalEntries);
        Set<ClassifierEntry> entriesToRemove = Sets.difference(operationalEntries, configurationEntries);

        if (entriesToAdd.isEmpty() && entriesToRemove.isEmpty()) {
            return;
        }

        Set<NodeKey> nodesToAdd = new HashSet<>();
        Set<NodeKey> nodesToRemove = new HashSet<>();
        Set<InterfaceKey> interfacesToAdd = new HashSet<>();
        Set<InterfaceKey> interfacesToRemove = new HashSet<>();

        if (!entriesToRemove.isEmpty()) {
            diffSources(entriesToRemove, configurationEntries, nodesToRemove, interfacesToRemove);
        }

        if (!entriesToAdd.isEmpty()) {
            diffSources(entriesToAdd, operationalEntries, nodesToAdd, interfacesToAdd);
        }

        classifierRenderers.forEach(
            classifierRenderer -> {
                entriesToAdd.forEach(classifierRenderer::render);
                nodesToAdd.forEach(classifierRenderer::render);
                interfacesToAdd.forEach(classifierRenderer::render);
                entriesToRemove.forEach(classifierRenderer::supress);
                nodesToRemove.forEach(classifierRenderer::supress);
                interfacesToRemove.forEach(classifierRenderer::supress);
            });
    }

    private void diffSources(Set<ClassifierEntry> rhs,
                             Set<ClassifierEntry> lhs,
                             Set<NodeKey> nodes,
                             Set<InterfaceKey> interfaces) {
        rhs.forEach(entry -> {
            nodes.add(entry.getSourceNode());
            interfaces.add(entry.getSourceInterface());
        });
        lhs.forEach(entry -> {
            nodes.remove(entry.getSourceNode());
            interfaces.remove(entry.getSourceInterface());
        });
    }
}

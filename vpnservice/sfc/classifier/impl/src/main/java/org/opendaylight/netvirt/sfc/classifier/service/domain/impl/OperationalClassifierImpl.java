/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;

public class OperationalClassifierImpl implements ClassifierState {

    private final Set<ClassifierEntry> entries = new HashSet<>();

    @Override
    public Set<ClassifierEntry> getAllEntries() {
        return Collections.unmodifiableSet(entries);
    }

    public ClassifierRenderer getRenderer() {
        return new ClassifierRenderer() {
            @Override
            public void render(ClassifierEntry entry) {
                entries.add(entry);
            }

            @Override
            public void render(NodeKey nodeKey) {
                // noop
            }

            @Override
            public void render(InterfaceKey interfaceKey) {
                // noop
            }

            @Override
            public void supress(ClassifierEntry entry) {
                entries.remove(entry);
            }

            @Override
            public void supress(NodeKey nodeKey) {
                // noop
            }

            @Override
            public void supress(InterfaceKey interfaceKey) {
                // noop
            }
        };
    }

}

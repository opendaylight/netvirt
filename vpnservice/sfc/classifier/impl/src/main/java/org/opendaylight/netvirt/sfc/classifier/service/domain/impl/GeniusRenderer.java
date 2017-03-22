/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import org.opendaylight.netvirt.sfc.classifier.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;

public class GeniusRenderer implements ClassifierRenderer {

    private final GeniusProvider geniusProvider;

    public GeniusRenderer(GeniusProvider geniusProvider) {
        this.geniusProvider = geniusProvider;
    }

    @Override
    public void render(ClassifierEntry entry) {
        // noop
    }

    @Override
    public void render(NodeKey nodeKey) {
        // noop
    }

    @Override
    public void render(InterfaceKey interfaceKey) {
        String interfaceName = interfaceKey.getName();
        geniusProvider.bindPortOnIngressClassifier(interfaceName);
        geniusProvider.bindPortOnEgressClassifier(interfaceName);
    }

    @Override
    public void suppress(ClassifierEntry entry) {
        // noop
    }

    @Override
    public void suppress(NodeKey nodeKey) {
        // noop
    }

    @Override
    public void suppress(InterfaceKey interfaceKey) {
        String interfaceName = interfaceKey.getName();
        geniusProvider.unbindPortOnEgressClassifier(interfaceName);
        geniusProvider.unbindPortOnIngressClassifier(interfaceName);
    }
}

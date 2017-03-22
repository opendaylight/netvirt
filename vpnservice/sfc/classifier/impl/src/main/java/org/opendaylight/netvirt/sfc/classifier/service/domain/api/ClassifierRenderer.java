/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.api;

import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;

public interface ClassifierRenderer {
    void render(ClassifierEntry entry);

    void render(NodeKey nodeKey);

    void render(InterfaceKey interfaceKey);

    void supress(ClassifierEntry entry);

    void supress(NodeKey nodeKey);

    void supress(InterfaceKey interfaceKey);
}

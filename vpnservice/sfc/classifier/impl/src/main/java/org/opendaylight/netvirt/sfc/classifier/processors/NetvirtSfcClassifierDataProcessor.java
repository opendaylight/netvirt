/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.processors;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.classifier.rev150105.classifiers.Classifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NetvirtSfcClassifierDataProcessor extends NetvirtSfcDataProcessorBase<Classifier> {

    // TODO upon init, read the Classifiers from the data store and act on
    //      them as if they were just created. This is for ODL restarts

    public NetvirtSfcClassifierDataProcessor() {
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public void remove(InstanceIdentifier<Classifier> identifier, Classifier del) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

    @Override
    public void update(InstanceIdentifier<Classifier> identifier, Classifier original, Classifier update) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

    @Override
    public void add(InstanceIdentifier<Classifier> identifier, Classifier add) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }
}

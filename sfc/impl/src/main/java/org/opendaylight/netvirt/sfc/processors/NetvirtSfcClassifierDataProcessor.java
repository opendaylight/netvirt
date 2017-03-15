/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.processors;

import java.util.Optional;
import org.opendaylight.netvirt.sfc.providers.NetvirtSfcGeniusProvider;
import org.opendaylight.netvirt.sfc.providers.NetvirtSfcOpenFlow13Provider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.sfc.classifier.rev150105.classifiers.Classifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * @author ebrjohn
 *
 */
public class NetvirtSfcClassifierDataProcessor extends NetvirtSfcDataProcessorBase<Classifier> {

    private NetvirtSfcOpenFlow13Provider openFlow13Provider;
    private NetvirtSfcGeniusProvider geniusProvider;

    // TODO upon init, read the Classifiers from the data store and act on
    //      them as if they were just created. This is for ODL restarts

    public NetvirtSfcClassifierDataProcessor() {
        openFlow13Provider = null;
        geniusProvider = null;
    }

    public NetvirtSfcOpenFlow13Provider getOpenFlow13Provider() {
        return openFlow13Provider;
    }

    public void setOpenFlow13Provider(NetvirtSfcOpenFlow13Provider openFlow13Provider) {
        this.openFlow13Provider = openFlow13Provider;
    }

    public Optional<NetvirtSfcGeniusProvider> getGeniusProvider() {
        return Optional.ofNullable(geniusProvider);
    }

    public void setGeniusProvider(NetvirtSfcGeniusProvider geniusProvider) {
        this.geniusProvider = geniusProvider;
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

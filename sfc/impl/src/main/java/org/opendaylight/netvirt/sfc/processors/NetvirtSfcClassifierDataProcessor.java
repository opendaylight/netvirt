/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.processors;

import org.opendaylight.netvirt.sfc.providers.NetvirtSfcClassifierGeniusProvider;
import org.opendaylight.netvirt.sfc.providers.NetvirtSfcClassifierOpenFlow13Provider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.sfc.classifier.rev150105.classifiers.Classifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * @author ebrjohn
 *
 */
public class NetvirtSfcClassifierDataProcessor extends NetvirtSfcClassifierDataProcessorBase<Classifier> {

    private NetvirtSfcClassifierOpenFlow13Provider openFlow13Provider;
    private NetvirtSfcClassifierGeniusProvider geniusProvider;

    public NetvirtSfcClassifierOpenFlow13Provider getOpenFlow13Provider() {
        return openFlow13Provider;
    }

    public void setOpenFlow13Provider(NetvirtSfcClassifierOpenFlow13Provider openFlow13Provider) {
        this.openFlow13Provider = openFlow13Provider;
    }

    public NetvirtSfcClassifierGeniusProvider getGeniusProvider() {
        return geniusProvider;
    }

    public void setGeniusProvider(NetvirtSfcClassifierGeniusProvider geniusProvider) {
        this.geniusProvider = geniusProvider;
    }

    @Override
    public void remove(InstanceIdentifier<Classifier> identifier, Classifier del) {
        // TODO Auto-generated method stub

    }

    @Override
    public void update(InstanceIdentifier<Classifier> identifier, Classifier original, Classifier update) {
        // TODO Auto-generated method stub

    }

    @Override
    public void add(InstanceIdentifier<Classifier> identifier, Classifier add) {
        // TODO Auto-generated method stub

    }
}

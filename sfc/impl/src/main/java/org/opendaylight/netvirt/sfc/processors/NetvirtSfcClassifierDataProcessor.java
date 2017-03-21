/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.processors;

import org.opendaylight.netvirt.sfc.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.providers.NetvirtProvider;
import org.opendaylight.netvirt.sfc.providers.OpenFlow13Provider;
import org.opendaylight.netvirt.sfc.providers.SfcProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.sfc.classifier.rev150105.classifiers.Classifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * @author ebrjohn
 *
 */
public class NetvirtSfcClassifierDataProcessor extends NetvirtSfcDataProcessorBase<Classifier>
    implements AutoCloseable {

    private final GeniusProvider geniusProvider;
    private final NetvirtProvider netvirtProvider;
    private final OpenFlow13Provider openFlowProvider;
    private final SfcProvider sfcProvider;

    // TODO upon init, read the Classifiers from the data store and act on
    //      them as if they were just created. This is for ODL restarts

    public NetvirtSfcClassifierDataProcessor(GeniusProvider geniusProvider, NetvirtProvider netvirtProvider,
            OpenFlow13Provider openFlowProvider, SfcProvider sfcProvider) {
        this.geniusProvider = geniusProvider;
        this.netvirtProvider = netvirtProvider;
        this.openFlowProvider = openFlowProvider;
        this.sfcProvider = sfcProvider;
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

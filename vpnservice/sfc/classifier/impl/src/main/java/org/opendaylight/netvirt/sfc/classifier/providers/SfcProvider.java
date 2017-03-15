/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;

@Singleton
public class SfcProvider {

    private final DataBroker dataBroker;

    @Inject
    public SfcProvider(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    // TODO add necessary methods to interact with SFC

    public Optional<RenderedServicePath> getRenderedServicePath(String rspName) {
        Optional<RenderedServicePath> rsp = Optional.empty();

        return rsp;
    }

    public Optional<RenderedServicePath> getRenderedServicePathFromSfc(String sfcName) {
        Optional<RenderedServicePath> rsp = Optional.empty();

        return rsp;
    }
}

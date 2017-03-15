/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.providers;

import java.util.Optional;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;

/**
 * @author ebrjohn
 *
 */
public class SfcProvider {
    private static MdsalUtils mdsalUtils = null;

    // This is a static class that cant be instantiated
    private SfcProvider() {
    }

    public static MdsalUtils getMdsalUtils() {
        return mdsalUtils;
    }

    public static void setMdsalUtils(MdsalUtils mdsalUtils) {
        SfcProvider.mdsalUtils = mdsalUtils;
    }

    // TODO add necessary methods to interact with SFC

    public static Optional<RenderedServicePath> getRenderedServicePath(String rspName) {
        Optional<RenderedServicePath> rsp = Optional.empty();

        return rsp;
    }

    public static Optional<RenderedServicePath> getRenderedServicePathFromSfc(String sfcName) {
        Optional<RenderedServicePath> rsp = Optional.empty();

        return rsp;
    }
}

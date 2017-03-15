/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.providers;

import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;

/**
 * @author ebrjohn
 *
 */
public class OpenFlow13Provider {
    private static MdsalUtils mdsalUtils = null;

    // This is a static class that cant be instantiated
    private OpenFlow13Provider() {
    }

    public static MdsalUtils getMdsalUtils() {
        return mdsalUtils;
    }

    public static void setMdsalUtils(MdsalUtils mdsalUtils) {
        OpenFlow13Provider.mdsalUtils = mdsalUtils;
    }

    // TODO add necessary methods to write OpenFlow13 flows

    // TODO this method needs to also take the NodeId to install the flow on
    public static void writeClassifierFlow(MatchBuilder match, RenderedServicePath rsp) {
    }
}

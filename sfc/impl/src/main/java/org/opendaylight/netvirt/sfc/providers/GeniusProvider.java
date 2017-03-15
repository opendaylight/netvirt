/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.providers;

import java.util.Optional;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

/**
 * @author ebrjohn
 *
 */
public class GeniusProvider {
    private static MdsalUtils mdsalUtils = null;

    // This is a static class that cant be instantiated
    private GeniusProvider() {
    }

    public static MdsalUtils getMdsalUtils() {
        return mdsalUtils;
    }

    public static void setMdsalUtils(MdsalUtils mdsalUtils) {
        GeniusProvider.mdsalUtils = mdsalUtils;
    }

    // TODO add necessary methods to interact with Genius

    public static Optional<NodeId> getNodeIdFromNeutronPort(NeutronPort port) {
        // TODO finish this
        return Optional.empty();
    }
}

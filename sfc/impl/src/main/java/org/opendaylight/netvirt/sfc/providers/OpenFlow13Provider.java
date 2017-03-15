/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.providers;

import java.math.BigInteger;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

/**
 * @author ebrjohn
 *
 */
public class OpenFlow13Provider {
    public static final BigInteger NETVIRT_SFC_CLASSIFIER_FILTER_COOKIE = new BigInteger("F005BA1100000001", 16);
    public static final BigInteger NETVIRT_SFC_CLASSIFIER_ACL_COOKIE = new BigInteger("F005BA1100000002", 16);
    public static final int SFC_SERVICE_PRIORITY = 6;

    private final DataBroker dataBroker;
    private final MDSALUtil mdsalUtils;

    // This is a static class that cant be instantiated
    private OpenFlow13Provider(final DataBroker dataBroker, final MDSALUtil mdsalUtils) {
        this.dataBroker = dataBroker;
        this.mdsalUtils = mdsalUtils;
    }

    public static void writeClassifierFlows(NodeId node, MatchBuilder match, RenderedServicePath rsp) {
        // TODO finish this
    }

    // TODO add necessary methods to write OpenFlow13 flows

}

/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.sfc.translator;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility functions to read Neutron models (e.g network, subnet, port, sfc flow classifier
 * port pair, port group, port chain) from md-sal data store.
 */
public class NeutronMdsalHelper {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronMdsalHelper.class);

    private final DataBroker dataBroker;
    private final MdsalUtils mdsalUtils;

    public NeutronMdsalHelper(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        mdsalUtils = new MdsalUtils(this.dataBroker);
    }
}

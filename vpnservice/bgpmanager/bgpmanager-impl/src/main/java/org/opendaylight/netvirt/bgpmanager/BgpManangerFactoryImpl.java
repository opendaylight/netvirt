/*
 * Copyright (c) 2016 Inocybe and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.bgpmanager.api.BgpManagerFactory;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of BgpManagerFactory.
 *
 * @author Alexis de Talhouët
 */
public class BgpManangerFactoryImpl implements BgpManagerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(BgpManangerFactoryImpl.class);

    @Override
    public IBgpManager newInstance(final DataBroker dataBroker) {
        LOG.info("Initializing new BgpMananger.");
        return new BgpManager(dataBroker);
    }
}

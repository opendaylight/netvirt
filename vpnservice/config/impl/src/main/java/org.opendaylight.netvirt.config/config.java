/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.config;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.config.rev170410.NetvirtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class config {
    private static final Logger LOG = LoggerFactory.getLogger(config.class);

    @Inject
    public config(NetvirtConfig netvirtConfig) {
        LOG.info("shague constructor, config: {}", netvirtConfig);
    }
}

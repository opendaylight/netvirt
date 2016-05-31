/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.openstack.netvirt.api.L2RewriteProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.Southbound;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.AbstractServiceInstance;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.PipelineOrchestrator;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;

public class L2RewriteService extends AbstractServiceInstance implements L2RewriteProvider {

    public L2RewriteService(final DataBroker dataBroker,
            final PipelineOrchestrator orchestrator,
            final Southbound southbound) {
        super(Service.L2_REWRITE, dataBroker, orchestrator, southbound);
        orchestrator.registerService(Service.L2_REWRITE, this);
    }
}
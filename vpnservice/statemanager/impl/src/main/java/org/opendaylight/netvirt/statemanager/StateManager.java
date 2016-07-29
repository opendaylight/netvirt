/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.statemanager;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateManager {
    private static final Logger LOG = LoggerFactory.getLogger(StateManager.class);
    private Map<String, Pair<String, Boolean>> ready = new HashMap<>();
    private DataBroker dataBroker;

    public StateManager(DataBroker databroker) {
        LOG.info("SatteManager constructor");
        ready.put("vpnservice-impl", Pair.of("vpnservice-default", false));
    }
}

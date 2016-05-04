/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fcapsappjmx;

import java.util.HashMap;
import java.util.Map;

public class NumberOfOFPorts implements NumberOfOFPortsMBean {
    private static Map<String,String> counterCache = new HashMap<String,String>();

    @Override
    public void updateCounter(Map<String, String> map) {
        counterCache = map;
    }

    @Override
    public Map<String, String> retrieveCounterMap() {
        return counterCache;
    }
}

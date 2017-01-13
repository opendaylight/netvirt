/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.oam;

import javax.management.NotificationBroadcasterSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * Created by ECHIAPT on 9/25/2015.
 */
public class BgpCountersBroadcaster extends NotificationBroadcasterSupport implements BgpCountersBroadcasterMBean  {
    public Map<String, String> bgpCountersMap = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(BgpCountersBroadcaster.class);

    public Map<String, String> retrieveCounterMap() {
        LOGGER.trace("Polled retrieveCounterMap");
        Map<String, String> countersVal = new HashMap<>(bgpCountersMap);
        for (Map.Entry<String, String> entry : countersVal.entrySet()) {
            LOGGER.trace(entry.getKey() + ", Value from MBean= " + entry.getValue());
        }
        return countersVal;
    }

    public void setBgpCountersMap(Map fetchedCountersMap) {
        LOGGER.trace("putAll");
        bgpCountersMap.clear();
        bgpCountersMap.putAll(fetchedCountersMap);
    }
}

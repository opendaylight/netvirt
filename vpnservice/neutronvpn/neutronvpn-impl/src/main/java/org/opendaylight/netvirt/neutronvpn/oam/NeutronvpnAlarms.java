/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.oam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronvpnAlarms {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnAlarms.class);
    private static final NvpnJMXAlarmAgent ALARM_AGENT = new NvpnJMXAlarmAgent();
    private static final String ALARM_TEXT = "Neutronvpn alarm";
    private static final String ALARM_ID = "NeutronvpnControlPathFailure";
    private static final String SOURCE_START = "Neutronvpn_type=";

    public NeutronvpnAlarms() {
        ALARM_AGENT.registerMbean();
    }

    public void unregisterMbean() {
        ALARM_AGENT.unregisterMbean();
    }

    public void raiseNvpnNbrDownAlarm(String typeAlarm) {
        if ((typeAlarm == null) || (typeAlarm.isEmpty())) {
            return;
        }
        StringBuilder source = new StringBuilder().append(SOURCE_START).append(typeAlarm);
        LOG.trace("Raising neutronvpnControlPathFailure alarm. {} alarmtext {} ", source, ALARM_TEXT);
        ALARM_AGENT.invokeFMraisemethod(ALARM_ID, ALARM_TEXT, source.toString());
    }

    public void clearNvpnNbrDownAlarm(String typeAlarm) {
        if ((typeAlarm == null) || (typeAlarm.isEmpty())) {
            return;
        }
        StringBuilder source = new StringBuilder().append(SOURCE_START).append(typeAlarm);
        LOG.trace("Clearing NeutronvpnControlPathFailure alarm of source {} alarmtext {} ", source, ALARM_TEXT);
        ALARM_AGENT.invokeFMclearmethod(ALARM_ID, ALARM_TEXT, source.toString());
    }
}


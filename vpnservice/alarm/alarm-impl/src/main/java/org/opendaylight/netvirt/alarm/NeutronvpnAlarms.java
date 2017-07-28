/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.alarm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronvpnAlarms {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnAlarms.class);
    protected static NvpnJMXAlarmAgent alarmAgent = new NvpnJMXAlarmAgent();
    private static final String ALARM_TEXT = "Neutronvpn alarm";
    private static final String ALARM_ID = "NeutronvpnControlPathFailure";
    private static final String SOURCE_START = "Neutronvpn_type=";
    private static final String CUSTOM_PROPERTY = "vpnservice.bgpspeaker.host.name";

    public NeutronvpnAlarms() {
        alarmAgent.registerMbean();
    }

    public void unregisterMbean() {
        alarmAgent.unregisterMbean();
    }

    /**method to raise an alarm.
     * @param typeAlarm the type of alarm as String
     * @param detailsAlarm the details just to log more informations
     */
    public void raiseNvpnNbrDownAlarm(String typeAlarm, String detailsAlarm) {
        if (!isAlarmEnabled() || typeAlarm == null || typeAlarm.isEmpty()) {
            return;
        }
        StringBuilder source = new StringBuilder().append(SOURCE_START).append(typeAlarm);
        LOG.trace("Raising neutronvpnControlPathFailure alarm. {} alarmtext {} ", source, ALARM_TEXT);
        alarmAgent.invokeFMraisemethod(ALARM_ID, ALARM_TEXT, source.toString(), detailsAlarm);
    }

    /**method to clear an alarm from its typeAlarm.
     * @param typeAlarm the type of alarm as String
     * @param detailsAlarm the details just to log more information
     */
    public void clearNvpnNbrDownAlarm(String typeAlarm, String detailsAlarm) {
        if (!isAlarmEnabled() || typeAlarm == null || typeAlarm.isEmpty()) {
            return;
        }
        StringBuilder source = new StringBuilder().append(SOURCE_START).append(typeAlarm);
        LOG.trace("Clearing NeutronvpnControlPathFailure alarm of source {} alarmtext {} ", source, ALARM_TEXT);
        alarmAgent.invokeFMclearmethod(ALARM_ID, ALARM_TEXT, source.toString(), detailsAlarm);
    }

    /**get if this alarm is activated from a file as custom.properties.
     * @return get true if this alarm is activated, false otherwise
     */
    public boolean isAlarmEnabled() {
        final String enabledPropertyStr = System.getProperty(CUSTOM_PROPERTY, "yes");
        return enabledPropertyStr != null && enabledPropertyStr.equalsIgnoreCase("yes");
    }
}


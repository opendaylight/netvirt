/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsmanager;

public interface AlarmServiceFacade {
    /**
     * Raises the given alarm in platform environment
     *
     * @param alarmName
     *            Alarm to be raised
     * @param additionalText
     *            Additional details describing about the alarm
     * @param source
     *            Source of the alarm ex: dpnId=openflow:1
     *            the source node that caused this alarm
     */
    public void raiseAlarm(String alarmName, String additionalText, String source);

    /**
     * Clears the given alarm in platform environment
     *
     * @param alarmName
     *            Alarm to be cleared
     * @param additionalText
     *            Additional details describing about the alarm
     * @param source
     *            Source of the alarm ex:  dpnId=openflow:1
     *            the source node that caused this alarm
     */
    public void clearAlarm(String alarmName, String additionalText, String source);
}
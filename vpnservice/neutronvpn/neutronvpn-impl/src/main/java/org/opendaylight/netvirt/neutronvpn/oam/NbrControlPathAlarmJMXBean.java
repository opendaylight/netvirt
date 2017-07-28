/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.oam;

import java.util.ArrayList;

public interface NbrControlPathAlarmJMXBean {

    void clearAlarm(String alarmName, String additionalText, String source);

    void raiseAlarm(String alarmName, String additionalText, String source);

    void setClearAlarmObject(ArrayList<String> clearAlarmObject);

    void setRaiseAlarmObject(ArrayList<String> raiseAlarmObject);

}
/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.alarm;

import java.util.List;

public interface NvpnNbrControlPathAlarmMBean {

    void setRaiseAlarmObject(List<String> raiseAlarmObject);

    List<String> getRaiseAlarmObject();

    void setClearAlarmObject(List<String> clearAlarmObject);

    List<String> getClearAlarmObject();

    void raiseAlarm(String alarmName, String additionalText, String source, String detailsInfo);

    void clearAlarm(String alarmName, String additionalText, String source, String detailsInfo);

}
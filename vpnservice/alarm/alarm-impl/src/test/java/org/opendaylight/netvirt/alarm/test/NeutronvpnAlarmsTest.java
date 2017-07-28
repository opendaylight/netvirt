/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.alarm.test;

import org.opendaylight.netvirt.alarm.NeutronvpnAlarms;
import org.opendaylight.netvirt.alarm.NvpnJMXAlarmAgent;

public class NeutronvpnAlarmsTest extends NeutronvpnAlarms {

    /**
     * method to used only for unit test cases.
     *
     * @return the NvpnJMXAlarmAgent registered in MBeanServer
     */
    public NvpnJMXAlarmAgent getAlarmAgent() {
        return alarmAgent;
    }

}

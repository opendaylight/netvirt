/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.alarm.test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;


@RunWith(MockitoJUnitRunner.class)

public class AlarmTest {

    private final NeutronvpnAlarmsTest neutronvpnAlarmTest = new NeutronvpnAlarmsTest();
    private Uuid vpnId = new Uuid("ffaa8822-2222-3333-4444-ffffffffffff");

    private String typeAlarm = "for vpnId: " + vpnId + " have exceeded next hops for prefixe";
    private String detailsAlarm = "this is Test details to raise an alarm";
    private String newLine = System.getProperty("line.separator");

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testNeutronvpnMoreNextHopThanRD() {



        neutronvpnAlarmTest.raiseNvpnNbrDownAlarm(typeAlarm, detailsAlarm);
        //neutronvpnAlarmTest.raiseNvpnNbrDownAlarm(typeAlarm + "2", detailsAlarm);
        long startTime = System.currentTimeMillis();

        while (startTime + 5000 > System.currentTimeMillis()) {
            int nbreAlarm = neutronvpnAlarmTest.getAlarmAgent().alarmBean.raiseAlarmObjectMap.size();
            if (nbreAlarm > 0) {
                break;
            } else {
                pause(10L);
            }
        }


        String messageError = "neutronvpnAlarmTest.getAlarmAgent().alarmBean.raiseAlarmObjectMap.size() = "
                + neutronvpnAlarmTest.getAlarmAgent().alarmBean.raiseAlarmObjectMap.size() + newLine;

        Map<String, ArrayList<String>> map = neutronvpnAlarmTest.getAlarmAgent().alarmBean.raiseAlarmObjectMap;
        for (Entry<String, ArrayList<String>> entry : map.entrySet()) {
            messageError += "Key : " + entry.getKey() + ",,, Value : " + entry.getValue().size() + newLine;
        }

        assertEquals("waiting 1 entry => " + messageError + " this is the first method running", 1, map.size());
    }

    private void pause(long milliSec) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            return;
        }
    }

    @Test
    public void testNeutronvpnClearMoreNextHopThanRD() {

        neutronvpnAlarmTest.clearNvpnNbrDownAlarm(typeAlarm, detailsAlarm);

        long startTime = System.currentTimeMillis();
        while (startTime + 5000 > System.currentTimeMillis()) {
            int nbreAlarm = neutronvpnAlarmTest.getAlarmAgent().alarmBean.raiseAlarmObjectMap.size();
            if (nbreAlarm == 0) {
                break;
            } else {
                pause(10L);
            }
        }

        String messageError = "neutronvpnAlarmTest.getAlarmAgent().alarmBean.raiseAlarmObjectMap.size() = "
                + neutronvpnAlarmTest.getAlarmAgent().alarmBean.raiseAlarmObjectMap.size() + newLine;

        Map<String, ArrayList<String>> map = neutronvpnAlarmTest.getAlarmAgent().alarmBean.raiseAlarmObjectMap;
        for (Entry<String, ArrayList<String>> entry : map.entrySet()) {
            messageError += "Key : " + entry.getKey() + ",,, Value : " + entry.getValue().size() + newLine;
        }

        assertEquals("waiting for 0 entry => this is the second method running",0, map.size());
    }
}

/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.alarm.test;

import static org.junit.Assert.assertEquals;

import java.util.List;
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

        /***********************Step to raise alarm*/

        neutronvpnAlarmTest.raiseNvpnNbrDownAlarm(typeAlarm, detailsAlarm);
        neutronvpnAlarmTest.raiseNvpnNbrDownAlarm(typeAlarm + "_Second", detailsAlarm);
        long startTime = System.currentTimeMillis();

        while (startTime + 5000 > System.currentTimeMillis()) {
            int nbreAlarm = neutronvpnAlarmTest.getAlarmAgent().getAlarmBean().getRaiseAlarmObjectMapSize();
            if (nbreAlarm > 0) {
                break;
            } else {
                pause(10L);
            }
        }


        String messageError = "testNeutronvpnMoreNextHopThanRD: NeutronvpnAlarmTest.getAlarmAgent().alarmBean."
                + "raiseAlarmObjectMap.size() = " + neutronvpnAlarmTest.getAlarmAgent().getAlarmBean()
                .getRaiseAlarmObjectMapSize() + newLine;

        Map<String, List<String>> map = neutronvpnAlarmTest.getAlarmAgent().getAlarmBean().getRaiseAlarmObjectMapCopy();
        messageError += getDetailForMap(map);

        assertEquals(newLine + messageError + "waiting 1 entry => "
                + messageError + " this is the first method running", 2, map.size());




        /***********************Step to clear alarm*/
        messageError = newLine;

        neutronvpnAlarmTest.clearNvpnNbrDownAlarm(typeAlarm, detailsAlarm);

        neutronvpnAlarmTest.clearNvpnNbrDownAlarm(typeAlarm + "_Second", detailsAlarm);

        startTime = System.currentTimeMillis();
        while (startTime + 5000 > System.currentTimeMillis()) {
            int nbreAlarm = neutronvpnAlarmTest.getAlarmAgent().getAlarmBean().getRaiseAlarmObjectMapSize();
            if (nbreAlarm == 0) {
                break;
            } else {
                pause(10L);
            }
        }

        messageError += "testNeutronvpnClearMoreNextHopThanRD: NeutronvpnAlarmTest.getAlarmAgent().alarmBean."
                + "raiseAlarmObjectMap.size() = " + neutronvpnAlarmTest.getAlarmAgent().getAlarmBean()
                .getRaiseAlarmObjectMapSize() + newLine;

        map = neutronvpnAlarmTest.getAlarmAgent().getAlarmBean().getRaiseAlarmObjectMapCopy();
        messageError += getDetailForMap(map);

        int size = map.size();
        assertEquals(newLine + messageError + "waiting for 0 entry => this is the second method running", 0, size);
    }

    private void pause(long milliSec) {
        try {
            Thread.sleep(milliSec);
        } catch (InterruptedException e) {
            return;
        }
    }

    private String getDetailForMap(Map<String, List<String>>  map) {
        String res = "";

        res += "================================================" + newLine;
        for (Entry<String, List<String>> entry : map.entrySet()) {
            res += "Key : " + entry.getKey() + newLine;
            if (entry.getValue() != null) {
                res += "Values : ";
                List<String> list = entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    res += "value " + i + ", " + list.get(i)  + newLine;
                }
            }
            res += "================================================" + newLine;
        }
        return res;
    }
}

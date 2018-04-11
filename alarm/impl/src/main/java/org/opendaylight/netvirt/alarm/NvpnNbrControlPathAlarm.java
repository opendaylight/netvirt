/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.alarm;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NvpnNbrControlPathAlarm extends NotificationBroadcasterSupport implements NvpnNbrControlPathAlarmMBean,
    javax.management.NotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NvpnNbrControlPathAlarm.class);

    private final AtomicInteger sequenceNumber = new AtomicInteger(0);

    private final ConcurrentMap<String, List<String>> raiseAlarmObjectMap = new ConcurrentHashMap<>();

    @Override
    public void raiseAlarm(String alarmName, String additionalText, String source, String detailsInfo) {
        if (alarmName == null || source == null || detailsInfo == null) {
            LOG.error("NvpnNbrControlPathAlarm.raiseAlarm has bad argument");
            return;
        }
        ArrayList<String> raiseAlarmObject = new ArrayList<>();
        raiseAlarmObject.add(alarmName);
        raiseAlarmObject.add(additionalText);
        raiseAlarmObject.add(source);
        raiseAlarmObject.add(detailsInfo);
        sendNotification(new AttributeChangeNotification(this,
            sequenceNumber.getAndIncrement(), System.currentTimeMillis(),
            NvpnJMXAlarmAgent.OP_RAISEALARM, "raiseAlarmObject", "List",
            "", raiseAlarmObject));
    }

    @Override
    public void clearAlarm(String alarmName, String additionalText, String source, String detailsInfo) {
        if (alarmName == null || source == null || detailsInfo == null) {
            LOG.error("NvpnNbrControlPathAlarm.clearAlarm has bad argument");
            return;
        }
        ArrayList<String> clearAlarmObject = new ArrayList<>();
        clearAlarmObject.add(alarmName);
        clearAlarmObject.add(additionalText);
        clearAlarmObject.add(source);
        clearAlarmObject.add(detailsInfo);
        sendNotification(new AttributeChangeNotification(this,
                sequenceNumber.getAndIncrement(), System.currentTimeMillis(),
                NvpnJMXAlarmAgent.OP_CLEARALARM, "clearAlarmObject", "List",
                "", clearAlarmObject));
    }

    public int getRaiseAlarmObjectMapSize() {
        return raiseAlarmObjectMap.size();
    }

    public Map<String, List<String>> getRaiseAlarmObjectMapCopy() {
        return ImmutableMap.copyOf(raiseAlarmObjectMap);
    }

    @Override
    public void handleNotification(NotificationListener listener, Notification notif, Object handback) {
        AttributeChangeNotification attrib;
        try {
            attrib = (AttributeChangeNotification) notif;
        } catch (ClassCastException e) {
            /*type of notification is not expected*/
            return;
        }
        /*below you could treat this new alarm and change the behavior if needed*/
        if (notif != null && notif.getSource() != null
                && notif.getSource().getClass().equals(NvpnNbrControlPathAlarm.class)) {
            List<String> tab = (List<String>) attrib.getNewValue();
            if (tab != null && tab.size() == 4) {
                if (notif.getMessage().compareTo(NvpnJMXAlarmAgent.OP_RAISEALARM) == 0) {
                    //only the last list is stored in the map (for a unique key),
                    //ensure that you have get the old list if needed
                    //and store it in the new list if you are expecting to save it again.
                    this.raiseAlarmObjectMap.put(tab.get(2), tab);
                }
                if (notif.getMessage().compareTo(NvpnJMXAlarmAgent.OP_CLEARALARM) == 0) {
                    this.raiseAlarmObjectMap.remove(tab.get(2), tab);
                }
            }
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        handleNotification(null, notification, handback);
    }
}

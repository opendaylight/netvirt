/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.alarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.management.AttributeChangeNotification;
import javax.management.NotificationBroadcasterSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NvpnNbrControlPathAlarm extends NotificationBroadcasterSupport implements NvpnNbrControlPathAlarmMBean {


    private static final Logger LOG = LoggerFactory.getLogger(NvpnNbrControlPathAlarm.class);

    ArrayList<String> raiseAlarmObject = new ArrayList<>();
    ArrayList<String> clearAlarmObject = new ArrayList<>();
    private long sequenceNumber = 1;

    public Map<String, ArrayList<String>> raiseAlarmObjectMap = new HashMap();

    @Override
    public void setRaiseAlarmObject(ArrayList<String> raiseAlarmObject) {
        this.raiseAlarmObject = raiseAlarmObject;
        sendNotification(new AttributeChangeNotification(this,
                sequenceNumber++, System.currentTimeMillis(),
                "raise alarm object notified ", "raiseAlarmObject", "ArrayList",
                "", this.raiseAlarmObject));
    }

    @Override
    public ArrayList<String> getRaiseAlarmObject() {
        return raiseAlarmObject;
    }

    @Override
    public void setClearAlarmObject(ArrayList<String> clearAlarmObject) {
        this.clearAlarmObject = clearAlarmObject;
        sendNotification(new AttributeChangeNotification(this,
                sequenceNumber++, System.currentTimeMillis(),
                "clear alarm object notified ", "clearAlarmObject", "ArrayList",
                "", this.clearAlarmObject));
    }

    @Override
    public ArrayList<String> getClearAlarmObject() {
        return clearAlarmObject;
    }

    @Override
    public void raiseAlarm(String alarmName, String additionalText, String source, String detailsInfo) {
        //ArrayList<String> raiseAlarmObject = new ArrayList<>();
        raiseAlarmObject.add(alarmName);
        raiseAlarmObject.add(additionalText);
        raiseAlarmObject.add(source);
        raiseAlarmObject.add(detailsInfo);
        if (!raiseAlarmObjectMap.containsKey(source)) {
            raiseAlarmObjectMap.put(source, (ArrayList<String>) raiseAlarmObject.clone());
        }
        setRaiseAlarmObject(raiseAlarmObject);
        raiseAlarmObject.clear();
    }

    @Override
    public void clearAlarm(String alarmName, String additionalText, String source, String detailsInfo) {
        //ArrayList<String> clearAlarmObject = new ArrayList<>();
        clearAlarmObject.add(alarmName);
        clearAlarmObject.add(additionalText);
        clearAlarmObject.add(source);
        clearAlarmObject.add(detailsInfo);
        boolean isCleared = false;
        if (raiseAlarmObjectMap.containsKey(source)) {
            ArrayList<String> removedArray = raiseAlarmObjectMap.remove(source);
            isCleared = true;
            if (removedArray != null && removedArray.size() == 3) {
                LOG.info("Remove alarm for : alarm name : {}, alarm name : {}, source : {}",
                        removedArray.get(0), removedArray.get(2));
            } else {
                isCleared = false;
            }
        }
        if (!isCleared) {
            LOG.debug("can\'t clear an unexisting alarm from {}", source);
        }
        setClearAlarmObject(clearAlarmObject);
        clearAlarmObject.clear();
    }
}

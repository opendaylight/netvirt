/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.oam;

/**
 * Created by ECHIAPT on 7/21/2016.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.AttributeChangeNotification;
import javax.management.NotificationBroadcasterSupport;

public class BgpNbrControlPathAlarm extends NotificationBroadcasterSupport implements BgpNbrControlPathAlarmMBean {

    private final AtomicLong sequenceNumber = new AtomicLong(1);

    @Override
    public void raiseAlarm(String alarmName, String additionalText, String source) {
        List<String> alarm = new ArrayList<>();
        alarm.add(alarmName);
        alarm.add(additionalText);
        alarm.add(source);

        sendNotification(new AttributeChangeNotification(this,
                sequenceNumber.incrementAndGet(), System.currentTimeMillis(),
                "raise alarm object notified", "raiseAlarmObject", "List", "", alarm));
    }

    @Override
    public void clearAlarm(String alarmName, String additionalText, String source) {
        List<String> alarm = new ArrayList<>();
        alarm.add(alarmName);
        alarm.add(additionalText);
        alarm.add(source);

        sendNotification(new AttributeChangeNotification(this,
                sequenceNumber.incrementAndGet(), System.currentTimeMillis(),
                "clear alarm object notified", "clearAlarmObject", "List", "", alarm));
    }
}

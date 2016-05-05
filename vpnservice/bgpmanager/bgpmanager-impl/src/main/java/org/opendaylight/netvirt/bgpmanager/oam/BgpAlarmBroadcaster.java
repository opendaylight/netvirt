/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.oam;

/**
 * Created by echiapt on 7/27/2015.
 */

 import javax.management.*;

 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import java.util.ArrayList;

public class BgpAlarmBroadcaster extends NotificationBroadcasterSupport implements BgpAlarmBroadcasterMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(BgpAlarmBroadcaster.class);
    private long sequenceNumber;
    public BgpAlarmBroadcaster () {
        this.sequenceNumber = 1;
    }

    public void sendBgpAlarmInfo(String pfx, int code , int subcode) {
        Notification n;
        String alarmAddText, alarmSrc = "BGP";
        BgpAlarmErrorCodes userAlarm;
        ArrayList<String> arrayList = new ArrayList<String>();

        userAlarm = BgpAlarmErrorCodes.checkErrorSubcode(subcode);
        alarmAddText = "Peer=" + pfx;
        arrayList.clear();
        arrayList.add(userAlarm.getAlarmType());
        arrayList.add(alarmAddText);
        arrayList.add(alarmSrc);
        n = new AttributeChangeNotification(this, sequenceNumber++, System.currentTimeMillis(),
                                            "raise Alarm Object notified", "raiseAlarmObject",
                                            "ArrayList", "", arrayList);
        sendNotification(n);
        LOGGER.info("BGP: Alarm :"+ userAlarm.getAlarmType() + " has been posted.");
        return;
    }
}

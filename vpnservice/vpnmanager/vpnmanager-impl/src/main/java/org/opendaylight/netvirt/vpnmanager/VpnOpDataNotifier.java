/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnOpDataNotifier {

    // Maps a VpnName with a list of Task to be executed once the the Vpn is fully ready.
    private ConcurrentHashMap<String, List<Runnable>> vpnInstanceListenerMap = new ConcurrentHashMap<String,
                                                                                                     List<Runnable>>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    static final Logger logger = LoggerFactory.getLogger(VpnOpDataNotifier.class);


    /**
     * Blocks current thread until VpnManager decides that all the operational
     * data about a specific VPN is ready.
     *
     * @param vpnName Name of the VPN to wait for
     * @param maxWaitMillis Max time in ms to wait for
     * @return true if the thread is released before the max time wait.
     */
    public boolean waitForVpnInstanceInfo(String vpnName, long maxWaitMillis) {
        logger.debug("Waiting for VPN {} to be fully ready. Max wait: {}", vpnName, maxWaitMillis);
        boolean vpnIsReady = false;
        Runnable notifyTask = new VpnNotifyTask();
        List<Runnable> notifieeList = null;
        try {
            synchronized (vpnInstanceListenerMap) {
                notifieeList = vpnInstanceListenerMap.get(vpnName);
                if (notifieeList == null) {
                    notifieeList = new ArrayList<Runnable>();
                    vpnInstanceListenerMap.put(vpnName, notifieeList);
                }
                notifieeList.add(notifyTask);
            }

            synchronized (notifyTask) {
                try {
                    long t0 = System.nanoTime();
                    notifyTask.wait(maxWaitMillis);
                    long elapsedTimeNs = System.nanoTime() - t0;
                    if ( elapsedTimeNs < (maxWaitMillis*1000000) ) {
                        // Thread woken up before timeout
                        logger.debug("Its been reported that VPN {} is now ready", vpnName);
                        vpnIsReady = true;
                    } else {
                        // Timeout
                        logger.debug("Vpn {} OpData not ready before {}ms", vpnName, maxWaitMillis);
                        vpnIsReady = false;
                    }
                } catch ( InterruptedException e ) {
                    vpnIsReady = true;
                }
            }
        } finally {
            synchronized (vpnInstanceListenerMap) {
                notifieeList = vpnInstanceListenerMap.get(vpnName);
                if (notifieeList != null) {
                    notifieeList.remove(notifyTask);
                    if (notifieeList.isEmpty()) {
                        vpnInstanceListenerMap.remove(vpnName);
                    }
                }
            }
        }

        return vpnIsReady;
    }

    public void notifyVpnOpDataReady(String vpnName) {
        logger.debug("Reporting that vpn {} is ready", vpnName);
        synchronized (vpnInstanceListenerMap) {
            List<Runnable> notifieeList = vpnInstanceListenerMap.remove(vpnName);
            if (notifieeList == null) {
                logger.trace(" No notify tasks found for vpnName {}", vpnName);
                return;
            }
            Iterator<Runnable> notifieeIter = notifieeList.iterator();
            while (notifieeIter.hasNext()) {
                Runnable notifyTask = notifieeIter.next();
                executorService.execute(notifyTask);
                notifieeIter.remove();
            }
        }
    }
}

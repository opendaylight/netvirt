/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aims to provide a common synchronization point for all those classes that
 * want to know when certain type of Operational data is ready for a given VPN,
 * and those others that can notify that the Operational data is ready.
 */
@Singleton
public class VpnOpDataSyncer {

    static final Logger LOG = LoggerFactory.getLogger(VpnOpDataSyncer.class);

    public enum VpnOpDataType {
        vpnInstanceToId,
        vpnOpData,
    }

    // Maps VpnOpDataType to a Map of VpnName to a list of tasks to be executed once the the Vpn is fully ready.
    private final Map<VpnOpDataType, ConcurrentMap<String, List<Runnable>>> mapOfMaps =
        ImmutableMap.<VpnOpDataType, ConcurrentMap<String, List<Runnable>>>builder()
            .put(VpnOpDataType.vpnInstanceToId, new ConcurrentHashMap<>())
            .put(VpnOpDataType.vpnOpData, new ConcurrentHashMap<>())
            .build();


    private static final ThreadFactory THREAD_FACTORY =
        new ThreadFactoryBuilder().setNameFormat("NV-VpnMgr-%d").build();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(THREAD_FACTORY);


    public boolean waitForVpnDataReady(VpnOpDataType vpnOpDataType, String vpnName, long maxWaitMillis,
                                       int maxAttempts) {
        int attempts = 0;
        boolean isDataReady = false;
        do {
            attempts++;
            isDataReady = waitForVpnDataReady(vpnOpDataType, vpnName, maxWaitMillis);
        }
        while (!isDataReady && attempts < maxAttempts);

        return isDataReady;
    }

    // "Unconditional wait" and "Wait not in loop" wrt the VpnNotifyTask below - suppressing the FB violation -
    // see comments below.
    @SuppressFBWarnings({"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"})
    public boolean waitForVpnDataReady(VpnOpDataType dataType, String vpnName, long maxWaitMillis) {
        //TODO(vivek) This waiting business to be removed in carbon
        boolean dataReady = false;
        ConcurrentMap<String, List<Runnable>> listenerMap = mapOfMaps.get(dataType);
        Runnable notifyTask = new VpnNotifyTask();
        try {
            List<Runnable> notifyList = listenerMap.computeIfAbsent(vpnName,
                k -> Collections.synchronizedList(new ArrayList<>()));

            synchronized (notifyTask) {
                // Per FB's "Unconditional wait" violation, the code should really verify that the condition it intends
                // to wait for is not already satisfied before calling wait. However the VpnNotifyTask is published
                // here while holding the lock on it so this path will hit the wait before notify can be invoked.
                notifyList.add(notifyTask);

                long t0 = System.nanoTime();
                try {
                    notifyTask.wait(maxWaitMillis);
                    long elapsedTimeNs = System.nanoTime() - t0;

                    if (elapsedTimeNs < maxWaitMillis * 1000000) {
                        // Thread woken up before timeout
                        LOG.debug("Its been reported that VPN {} is now ready", vpnName);
                        dataReady = true;
                    } else {
                        // Timeout
                        LOG.debug("Vpn {} OpData not ready after {}ms", vpnName, maxWaitMillis);
                        dataReady = false;
                    }
                } catch (InterruptedException e) {
                    dataReady = true;
                }
            }
        } finally {
            List<Runnable> notifyTaskList = listenerMap.get(vpnName);
            if (notifyTaskList != null) {
                synchronized (notifyTaskList) {
                    notifyTaskList.remove(notifyTask);
                    if (notifyTaskList.isEmpty()) {
                        listenerMap.remove(vpnName);
                    }
                }
            }
        }
        return dataReady;
    }

    public void notifyVpnOpDataReady(VpnOpDataType dataType, String vpnName) {
        LOG.debug("Reporting that vpn {} is ready", vpnName);
        ConcurrentMap<String, List<Runnable>> listenerMap = mapOfMaps.get(dataType);
        List<Runnable> notifyTaskList = listenerMap.remove(vpnName);
        if (notifyTaskList == null) {
            LOG.trace(" No notify tasks found for vpnName {}", vpnName);
            return;
        }

        Runnable[] notifyTasks;
        synchronized (notifyTaskList) {
            notifyTasks = notifyTaskList.toArray(new Runnable[notifyTaskList.size()]);
            notifyTaskList.clear();
        }

        for (Runnable notifyTask : notifyTasks) {
            executorService.execute(notifyTask);
        }
    }
}

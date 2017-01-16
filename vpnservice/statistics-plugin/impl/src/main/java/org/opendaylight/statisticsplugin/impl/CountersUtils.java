/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.statisticsplugin.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opendaylight.genius.interfacemanager.globals.IfmConstants;

import jline.internal.Log;

public class CountersUtils {

    public static final String BYTE_COUNTER_NAME = "byteCount";
    public static final String BYTES_GROUP_NAME = "Bytes";
    public static final String BYTES_RECEIVED_COUNTER_NAME = "bytesReceivedCount";
    public static final String BYTES_TRANSMITTED_COUNTER_NAME = "bytesTransmittedCount";
    public static final String DURATION_GROUP_NAME = "Duration";
    public static final String DURATION_NANO_SECOND_COUNTER_NAME = "durationNanoSecondCount";
    public static final String DURATION_SECOND_COUNTER_NAME = "durationSecondCount";
    public static final String OF_DELIMITER = ":";
    public static final String PACKET_COUNTER_NAME = "packetCount";
    public static final String PACKETS_GROUP_NAME = "Packets";
    public static final String PACKETS_RECEIVED_COUNTER_NAME = "packetsReceivedCount";
    public static final String PACKETS_TRANSMITTED_COUNTER_NAME = "packetsTransmittedCount";

    public static final long CREATE_RESULT_TIMEOUT = 5;
    public static final long NODE_CONNECTOR_REPLIES_TIMOUT = 10;

    private static final List<String> UNACCUMULATED_COUNTER_GROUPS = new ArrayList<String>(Arrays.asList("Duration"));

    public static String getNodeId(BigInteger dpId) {
        return IfmConstants.OF_URI_PREFIX + dpId;
    }

    public static String getNodeConnectorId(BigInteger dpId, String portNumber) {
        return IfmConstants.OF_URI_PREFIX + dpId + OF_DELIMITER + portNumber;
    }

    public static CounterResultDataStructure aggregateCounters(CounterResultDataStructure counters,
            String aggregatedResultId) {
        CounterResultDataStructure aggregatedCounters = new CounterResultDataStructure();
        if (counters.isEmpty()) {
            return null;
        }

        Set<String> groupNames = counters.getGroupNames();
        if (groupNames == null || groupNames.isEmpty()) {
            return null;
        }

        aggregatedCounters.addCounterResult(aggregatedResultId);
        for (String groupName : groupNames) {
            Map<String, BigInteger> aggregatedGroupCounters = aggregateGroupCounters(groupName, counters);
            aggregatedCounters.addCounterGroup(aggregatedResultId, groupName, aggregatedGroupCounters);
        }

        return aggregatedCounters;
    }

    private static Map<String, BigInteger> aggregateGroupCounters(String groupName,
            CounterResultDataStructure counters) {
        Set<String> groupCounterNames = counters.getGroupCounterNames(groupName);
        if (groupCounterNames == null || groupCounterNames.isEmpty()) {
            return null;
        }

        Map<String, BigInteger> aggregatedCounters = new HashMap<>();
        for (String counterName : groupCounterNames) {
            aggregatedCounters.put(counterName, BigInteger.valueOf(0));
        }

        for (String counterResultId : counters.getResults().keySet()) {
            Map<String, BigInteger> currentResultGroup = counters.getGroups(counterResultId).get(groupName);
            for (String counterName : currentResultGroup.keySet()) {
                if (aggregatedCounters.get(counterName) != null) {
                    aggregatedCounters.put(counterName,
                            aggregatedCounters.get(counterName).add(currentResultGroup.get(counterName)));
                } else {
                    Log.warn("missing counter value for: {}", counterName);
                }
            }
            if (UNACCUMULATED_COUNTER_GROUPS.contains(groupName)) {
                break;
            }
        }
        return aggregatedCounters;
    }

}

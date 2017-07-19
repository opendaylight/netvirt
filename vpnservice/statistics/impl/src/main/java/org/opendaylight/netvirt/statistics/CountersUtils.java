/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CountersUtils {

    public static final String BYTE_COUNTER_NAME = "byteCount";
    public static final String BYTES_GROUP_NAME = "Bytes";
    public static final String BYTES_RECEIVED_COUNTER_NAME = "bytesReceivedCount";
    public static final String BYTES_TRANSMITTED_COUNTER_NAME = "bytesTransmittedCount";
    public static final String DURATION_GROUP_NAME = "Duration";
    public static final String DURATION_NANO_SECOND_COUNTER_NAME = "durationNanoSecondCount";
    public static final String DURATION_SECOND_COUNTER_NAME = "durationSecondCount";
    public static final String OF_DELIMITER = ":";
    public static final String OF_PREFIX = "openflow:";
    public static final String PACKET_COUNTER_NAME = "packetCount";
    public static final String PACKETS_GROUP_NAME = "Packets";
    public static final String PACKETS_RECEIVED_COUNTER_NAME = "packetsReceivedCount";
    public static final String PACKETS_TRANSMITTED_COUNTER_NAME = "packetsTransmittedCount";

    public static final String ELEMENT_COUNTERS_IP_FILTER_GROUP_NAME = "ipFilterGroup";
    public static final String ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME = "tcpFilterFroup";
    public static final String ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME = "udpFilterFroup";
    public static final String IP_FILTER_NAME = "ip";
    public static final String TCP_SRC_PORT_FILTER_NAME = "tcpSrcPort";
    public static final String TCP_DST_PORT_FILTER_NAME = "tcpDstPort";
    public static final String UDP_SRC_PORT_FILTER_NAME = "udpSrcPort";
    public static final String UDP_DST_PORT_FILTER_NAME = "udpDstPort";
    public static final String TCP_FILTER_NAME = "tcp";
    public static final String UDP_FILTER_NAME = "udp";

    private static final List<String> UNACCUMULATED_COUNTER_GROUPS = new ArrayList<String>(Arrays.asList("Duration"));
    protected static final Logger LOG = LoggerFactory.getLogger(CountersUtils.class);

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
                    LOG.warn("missing counter value for: {}", counterName);
                }
            }
            if (UNACCUMULATED_COUNTER_GROUPS.contains(groupName)) {
                break;
            }
        }
        return aggregatedCounters;
    }

}

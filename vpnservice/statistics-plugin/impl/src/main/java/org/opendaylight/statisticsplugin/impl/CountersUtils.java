/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.statisticsplugin.impl;

import java.math.BigInteger;

import org.opendaylight.genius.interfacemanager.globals.IfmConstants;

public class CountersUtils {
    
    private static final String OF_DELIMITER = ":";
    static final String BYTE_COUNTER_NAME = "byteCount";
    static final String PACKET_COUNTER_NAME = "packetCount";
    static final String BYTES_GROUP_NAME = "Bytes";
    static final String PACKETS_GROUP_NAME = "Packets";
    static final String DURATION_GROUP_NAME = "Duration";
    static final String BYTES_RECEIVED_COUNTER_NAME = "bytesReceivedCount";
    static final String BYTES_TRANSMITTED_COUNTER_NAME = "bytesTransmittedCount";
    static final String PACKETS_RECEIVED_COUNTER_NAME = "packetsReceivedCount";
    static final String PACKETS_TRANSMITTED_COUNTER_NAME = "packetsTransmittedCount";
    static final String DURATION_SECOND_COUNTER_NAME = "durationSecondCount";
    static final String DURATION_NANO_SECOND_COUNTER_NAME = "durationNanoSecondCount";
    
    public static String getNodeId(BigInteger dpId){
        return IfmConstants.OF_URI_PREFIX + dpId;
    }
    
    public static String getNodeConnectorId(BigInteger dpId, String portNumber){
        return IfmConstants.OF_URI_PREFIX + dpId + OF_DELIMITER + portNumber;
    }

}

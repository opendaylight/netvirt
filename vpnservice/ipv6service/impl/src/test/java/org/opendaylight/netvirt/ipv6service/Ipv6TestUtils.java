/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;


import java.util.ArrayList;
import java.util.List;

public class Ipv6TestUtils {

    public byte[] buildPacket(String... contents) {
        List<String[]> splitContents = new ArrayList<>();
        int packetLength = 0;
        for (String content : contents) {
            String[] split = content.split(" ");
            packetLength += split.length;
            splitContents.add(split);
        }
        byte[] packet = new byte[packetLength];
        int index = 0;
        for (String[] split : splitContents) {
            for (String component : split) {
                // We can't use Byte.parseByte() here, it refuses anything > 7F
                packet[index] = (byte) Integer.parseInt(component, 16);
                index++;
            }
        }
        return packet;
    }
}

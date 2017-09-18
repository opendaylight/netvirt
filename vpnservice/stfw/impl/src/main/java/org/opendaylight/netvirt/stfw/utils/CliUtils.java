/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.utils;

import java.util.Formatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CliUtils {

    private static final String INTERFACES_OUTPUT_FORMAT = "%-5s %-80s";
    private static final String INTERFACES_COUNT_FORMAT = "%-30s %-5s";

    public static void dumpInterfaceCount(ConcurrentHashMap<String, Integer> interfaceMap) {
        showInterfacesCountHeader();
        showInterfacesCount(interfaceMap);
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private static void showInterfacesCountHeader() {
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        System.out.println(fmt.format(INTERFACES_COUNT_FORMAT, "Interface Type", "Count"));
        sb.setLength(0);
        System.out.print("------------------------------------------------------------\n");
        sb.setLength(0);
        fmt.close();
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private static void showInterfacesCount(ConcurrentHashMap<String, Integer> interfacesCount) {
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        for (Map.Entry<String, Integer> entry : interfacesCount.entrySet()) {
            System.out.println(fmt.format(INTERFACES_COUNT_FORMAT, entry.getKey(), entry.getValue()));
            sb.setLength(0);
        }
        fmt.close();
    }
}

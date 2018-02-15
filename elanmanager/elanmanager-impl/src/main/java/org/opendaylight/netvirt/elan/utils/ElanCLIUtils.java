/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

public interface ElanCLIUtils {
    String HEADER_UNDERLINE = "----------------------------------------------------------"
            + "------------------------------------";
    String MAC_TABLE_CLI_FORMAT = "%-35s %-20s %-20s %-20s";
    String ELAN_CLI_FORMAT = "%-35s %-20s %-20s ";
    String ETREE_CLI_FORMAT = "%-35s %-20s %-20s %-20s";
    String ELAN_INTERFACE_CLI_FORMAT = "%-35s %-25s %-15s %-15s ";
    String ETREE_INTERFACE_CLI_FORMAT = "%-35s %-25s %-15s %-15s %-15s";
}

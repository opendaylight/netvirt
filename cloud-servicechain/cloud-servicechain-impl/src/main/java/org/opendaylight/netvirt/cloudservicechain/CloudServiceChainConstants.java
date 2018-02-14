/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.cloudservicechain;

import java.math.BigInteger;

public interface CloudServiceChainConstants {
    BigInteger COOKIE_SCF_BASE = new BigInteger("7000000", 16);
    BigInteger COOKIE_LPORT_DISPATCHER_BASE = new BigInteger("6000000", 16);
    BigInteger METADATA_MASK_SCF_WRITE = new BigInteger("000000FF00000000", 16);
    BigInteger COOKIE_L3_BASE = new BigInteger("8000000", 16);
    int DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY = 1;
    int DEFAULT_SCF_FLOW_PRIORITY = 20;
    String FLOWID_PREFIX_SCF = "SCF.";
    String FLOWID_PREFIX_L3 = "L3.";

    String FLOWID_PREFIX = "L3.";
    String L2_FLOWID_PREFIX = "L2.";
    String VPN_PSEUDO_PORT_FLOWID_PREFIX = "VpnPseudoPort.";
    String VPN_PSEUDO_VPN2SCF_FLOWID_PREFIX = "VpnPseudoPort.Vpn2Scf";
    String VPN_PSEUDO_VPN2VPN_FLOWID_PREFIX = "VpnPseudoPort.Vpn2Vpn";
    String VPN_PSEUDO_SCF2VPN_FLOWID_PREFIX = "VpnPseudoPort.Scf2Vpn";
    String ELAN_TO_SCF_L2_FLOWID_PREFIX = "ElanPseudoPort.Elan2Scf";
    String SCF_TO_ELAN_L2_FLOWID_PREFIX = "ElanPseudoPort.Scf2Elan";
    long INVALID_VPN_TAG = -1;
}

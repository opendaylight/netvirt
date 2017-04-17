/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.tests;

import java.util.HashMap;
import java.util.Map;

/**
 * The Id Helper class.
 * <p>
 * Disabling checkstyle:linelength for readability purpose.
 * </p>
 */
@SuppressWarnings("checkstyle:linelength")
public class IdHelper {
    private static Map<String, Integer> idCacheMap = new HashMap<>();

    static {
        idCacheMap.put("UDP_DESTINATION_1_0Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1001);
        idCacheMap.put("TCP_DESTINATION_1_0Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1002);
        idCacheMap.put("ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6", 1003);
        idCacheMap.put("ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1004);
        idCacheMap.put("ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1005);
        idCacheMap.put("UDP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1006);
        idCacheMap.put("UDP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1007);
        idCacheMap.put("UDP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1008);
        idCacheMap.put("TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1009);
        idCacheMap.put("TCP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1010);
        idCacheMap.put("TCP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1011);
        idCacheMap.put("ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1012);
        idCacheMap.put("ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426a22", 1013);
        idCacheMap.put("ICMP_V4_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1014);
        idCacheMap.put("ICMP_V4_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426a21", 1015);
        idCacheMap.put("ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1016);
        idCacheMap.put("ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1017);
        idCacheMap.put("UDP_DESTINATION_2000_65532Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1018);
        idCacheMap.put("TCP_DESTINATION_776_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1019);
        idCacheMap.put("TCP_DESTINATION_512_65280Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1020);
        idCacheMap.put("TCP_DESTINATION_334_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1021);
        idCacheMap.put("TCP_DESTINATION_333_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1022);
        idCacheMap.put("TCP_DESTINATION_336_65520Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1023);
        idCacheMap.put("TCP_DESTINATION_352_65504Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1024);
        idCacheMap.put("TCP_DESTINATION_384_65408Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1025);
        idCacheMap.put("TCP_DESTINATION_768_65528Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1026);
        idCacheMap.put("ETHERnull_remoteACL_id_85cc3048-abc3-43cc-89b3-377341426ac5Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 61010);
        idCacheMap.put("UDP_DESTINATION_80_65535_remoteACL_id_85cc3048-abc3-43cc-89b3-377341426ac5Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 61010);
        idCacheMap.put("ICMP_V4_DESTINATION_23__remoteACL_id_85cc3048-abc3-43cc-89b3-377341426ac5Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 61010);
        idCacheMap.put("TCP_DESTINATION_80_65535_remoteACL_id_85cc3048-abc3-43cc-89b3-377341426ac5Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 61010);
        idCacheMap.put("85cc3048-abc3-43cc-89b3-377341426ac5", 1 << 1);
        idCacheMap.put("85cc3048-abc3-43cc-89b3-377341426ac8", 2 << 1);
        idCacheMap.put("ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.100/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1027);
        idCacheMap.put("ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:A4_10.0.0.101/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1028);
    }

    public static Integer getId(String key) {
        Integer value = idCacheMap.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key);
        }
        return value;
    }
}

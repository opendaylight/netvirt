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
    private static Map<String, Integer> flowPriorityCacheMap = new HashMap<>();

    static {
        flowPriorityCacheMap.put("UDP_DESTINATION_1_0Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1001);
        flowPriorityCacheMap.put("TCP_DESTINATION_1_0Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1002);
        flowPriorityCacheMap.put("ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6", 1003);
        flowPriorityCacheMap.put("ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1004);
        flowPriorityCacheMap.put("ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1005);
        flowPriorityCacheMap.put("UDP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1006);
        flowPriorityCacheMap.put("UDP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1007);
        flowPriorityCacheMap.put("UDP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1008);
        flowPriorityCacheMap.put("TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1009);
        flowPriorityCacheMap.put("TCP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1010);
        flowPriorityCacheMap.put("TCP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1011);
        flowPriorityCacheMap.put("ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1012);
        flowPriorityCacheMap.put("ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426a22", 1013);
        flowPriorityCacheMap.put("ICMP_V4_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1014);
        flowPriorityCacheMap.put("ICMP_V4_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426a21", 1015);
        flowPriorityCacheMap.put("ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1016);
        flowPriorityCacheMap.put("ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1017);
        flowPriorityCacheMap.put("UDP_DESTINATION_2000_65532Ingress98785cc3048-abc3-43cc-89b3-377341426ac7", 1018);
        flowPriorityCacheMap.put("TCP_DESTINATION_776_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1019);
        flowPriorityCacheMap.put("TCP_DESTINATION_512_65280Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1020);
        flowPriorityCacheMap.put("TCP_DESTINATION_334_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1021);
        flowPriorityCacheMap.put("TCP_DESTINATION_333_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1022);
        flowPriorityCacheMap.put("TCP_DESTINATION_336_65520Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1023);
        flowPriorityCacheMap.put("TCP_DESTINATION_352_65504Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1024);
        flowPriorityCacheMap.put("TCP_DESTINATION_384_65408Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1025);
        flowPriorityCacheMap.put("TCP_DESTINATION_768_65528Egress98785cc3048-abc3-43cc-89b3-377341426ac6", 1026);
    }

    public static Integer getFlowPriority(String key) {
        return flowPriorityCacheMap.get(key);
    }
}

package org.opendaylight.netvirt.igmpsnooping.api;

public interface IGMPConstants {
    //IGMP Msg Types
    static final byte IGMPv1_MEMBERSHIP_REPORT = 0x12;
    static final byte IGMPv2_MEMBERSHIP_REPORT = 0x16;
    static final byte IGMPv3_MEMBERSHIP_REPORT = 0x22;
    static final byte IGMPv3_MEMBERSHIP_QUERY  = 0x11;
    static final byte IGMPv2_LEAVE_GROUP       = 0x17;
}

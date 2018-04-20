package org.opendaylight.netvirt.igmpsnooping.api;

import static org.opendaylight.netvirt.igmpsnooping.api.IGMPConstants.IGMPv2_LEAVE_GROUP;
import static org.opendaylight.netvirt.igmpsnooping.api.IGMPConstants.IGMPv2_MEMBERSHIP_REPORT;

import java.nio.ByteBuffer;
//import org.opendaylight.openflowplugin.libraries.liblldp.Packet;
//import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//public class IGMP extends Packet {
public class IGMP {
    protected static final Logger LOG = LoggerFactory
            .getLogger(IGMP.class);

    protected byte igmpType;
    protected byte resField = 0;
    protected short checksum = 0;

    public IGMP() {
    }

    public void deserialize(byte[] data, int bitOffset, int size)
    {

        //Deserialize the header
        final ByteBuffer byteBuf = ByteBuffer.wrap(data, bitOffset, size);
        byte igmpType = byteBuf.get();
        boolean isV2;
        if (igmpType == IGMPv2_MEMBERSHIP_REPORT  || igmpType == IGMPv2_LEAVE_GROUP ) {
            isV2 = true;
        } else {
            isV2 = false;
        }

        this.igmpType = igmpType;
        this.resField = byteBuf.get();
        this.checksum = byteBuf.getShort();
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("igmpType:").append(Byte.toString(igmpType))
                .append(" resField:").append(Byte.toString(resField))
                .append(" checksum:").append(Short.toString(checksum));

        return str.toString();
    }
}

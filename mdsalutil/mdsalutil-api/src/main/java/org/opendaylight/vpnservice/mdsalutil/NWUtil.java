/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import com.google.common.base.Optional;
import com.google.common.primitives.UnsignedBytes;

public class NWUtil {

    public static  long convertInetAddressToLong(InetAddress address) {
        byte[] ipAddressRaw = address.getAddress();
        return (((ipAddressRaw[0] & 0xFF) << (3 * 8))
                + ((ipAddressRaw[1] & 0xFF) << (2 * 8))
                + ((ipAddressRaw[2] & 0xFF) << (1 * 8))
                + (ipAddressRaw[3] & 0xFF))
                & 0xffffffffL;
    }

    public static byte[] parseIpAddress(String ipAddress) {
        byte cur;

        String[] addressPart = ipAddress.split(".");
        int size = addressPart.length;

        byte[] part = new byte[size];
        for (int i = 0; i < size; i++) {
            cur = UnsignedBytes.parseUnsignedByte(addressPart[i], 16);
            part[i] = cur;
        }

        return part;
    }

    public static byte[] parseMacAddress(String macAddress) {
        byte cur;

        String[] addressPart = macAddress.split(":");
        int size = addressPart.length;

        byte[] part = new byte[size];
        for (int i = 0; i < size; i++) {
            cur = UnsignedBytes.parseUnsignedByte(addressPart[i], 16);
            part[i] = cur;
        }

        return part;
    }

    public static String toStringIpAddress(byte[] ipAddress)
    {
        if (ipAddress == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(18);

        for (byte ipAddres : ipAddress) {
            sb.append(UnsignedBytes.toString(ipAddres, 10));
            sb.append(".");
        }

        sb.setLength(17);
        return sb.toString();
    }

    public static String toStringMacAddress(byte[] macAddress)
    {
        if (macAddress == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(18);

        for (byte macAddres : macAddress) {
            String tmp = UnsignedBytes.toString(macAddres, 16).toUpperCase();
            if(tmp.length() == 1 || macAddres == (byte)0) {
                sb.append("0");
            }
            sb.append(tmp);
            sb.append(":");
        }

        sb.setLength(17);
        return sb.toString();
    }

    /**
     * Returns the ids of the currently operative DPNs
     *
     * @param dataBroker
     * @return
     */
    public static List<BigInteger> getOperativeDPNs(DataBroker dataBroker) {
        List<BigInteger> result = new LinkedList<BigInteger>();
        InstanceIdentifier<Nodes> nodesInstanceIdentifier = InstanceIdentifier.builder(Nodes.class).build();
        Optional<Nodes> nodesOptional = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                                       nodesInstanceIdentifier);
        if (!nodesOptional.isPresent()) {
            return result;
        }
        Nodes nodes = nodesOptional.get();
        List<Node> nodeList = nodes.getNode();
        for (Node node : nodeList) {
            NodeId nodeId = node.getId();
            if (nodeId != null) {
                BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeId);
                result.add(dpnId);
            }
        }
        return result;
    }
}

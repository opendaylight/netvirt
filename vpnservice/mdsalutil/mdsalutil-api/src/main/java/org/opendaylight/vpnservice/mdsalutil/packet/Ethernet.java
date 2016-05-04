/*
 * Copyright (c) 2013, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.mdsalutil.packet;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.liblldp.BitBufferHelper;
import org.opendaylight.controller.liblldp.EtherTypes;
import org.opendaylight.controller.liblldp.LLDP;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.liblldp.Packet;

/**
 * Class that represents the Ethernet frame objects
 * taken from opendaylight(helium) adsal bundle
 */
public class Ethernet extends Packet {
    private static final String DMAC = "DestinationMACAddress";
    private static final String SMAC = "SourceMACAddress";
    private static final String ETHT = "EtherType";

    // TODO: This has to be outside and it should be possible for osgi
    // to add new coming packet classes
    public static final Map<Short, Class<? extends Packet>> etherTypeClassMap;
    static {
        etherTypeClassMap = new HashMap<Short, Class<? extends Packet>>();
        etherTypeClassMap.put(EtherTypes.ARP.shortValue(), ARP.class);
        etherTypeClassMap.put(EtherTypes.LLDP.shortValue(), LLDP.class);
        etherTypeClassMap.put(EtherTypes.IPv4.shortValue(), IPv4.class);
        // TODO: Add support for more classes here
        etherTypeClassMap.put(EtherTypes.VLANTAGGED.shortValue(), IEEE8021Q.class);
        // etherTypeClassMap.put(EtherTypes.OLDQINQ.shortValue(), IEEE8021Q.class);
        // etherTypeClassMap.put(EtherTypes.QINQ.shortValue(), IEEE8021Q.class);
        // etherTypeClassMap.put(EtherTypes.CISCOQINQ.shortValue(), IEEE8021Q.class);
    }
    private static Map<String, Pair<Integer, Integer>> fieldCoordinates = new LinkedHashMap<String, Pair<Integer, Integer>>() {
        private static final long serialVersionUID = 1L;
        {
            put(DMAC, new ImmutablePair<Integer, Integer>(0, 48));
            put(SMAC, new ImmutablePair<Integer, Integer>(48, 48));
            put(ETHT, new ImmutablePair<Integer, Integer>(96, 16));
        }
    };
    private final Map<String, byte[]> fieldValues;

    /**
     * Default constructor that creates and sets the HashMap
     */
    public Ethernet() {
        super();
        fieldValues = new HashMap<String, byte[]>();
        hdrFieldCoordMap = fieldCoordinates;
        hdrFieldsMap = fieldValues;
    }

    /**
     * Constructor that sets the access level for the packet and
     * creates and sets the HashMap
     * @param writeAccess boolean
     */
    public Ethernet(boolean writeAccess) {
        super(writeAccess);
        fieldValues = new HashMap<String, byte[]>();
        hdrFieldCoordMap = fieldCoordinates;
        hdrFieldsMap = fieldValues;
    }

    @Override
    public void setHeaderField(String headerField, byte[] readValue) {
        if (headerField.equals(ETHT)) {
            payloadClass = etherTypeClassMap.get(BitBufferHelper
                    .getShort(readValue));
        }
        hdrFieldsMap.put(headerField, readValue);
    }

    public byte[] getDestinationMACAddress() {
        return fieldValues.get(DMAC);
    }

    public byte[] getSourceMACAddress() {
        return fieldValues.get(SMAC);
    }

    public short getEtherType() {
        return BitBufferHelper.getShort(fieldValues.get(ETHT));
    }

    public boolean isBroadcast(){
        return NetUtils.isBroadcastMACAddr(getDestinationMACAddress());
    }

    public boolean isMulticast(){
        return NetUtils.isMulticastMACAddr(getDestinationMACAddress());
    }

    public Ethernet setDestinationMACAddress(byte[] destinationMACAddress) {
        fieldValues.put(DMAC, destinationMACAddress);
        return this;
    }

    public Ethernet setSourceMACAddress(byte[] sourceMACAddress) {
        fieldValues.put(SMAC, sourceMACAddress);
        return this;
    }

    public Ethernet setEtherType(short etherType) {
        byte[] ethType = BitBufferHelper.toByteArray(etherType);
        fieldValues.put(ETHT, ethType);
        return this;
    }

}

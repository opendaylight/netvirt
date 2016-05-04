/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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

public class IEEE8021Q extends Ethernet {
    private static final String PRIORITY = "Priority";
    private static final String CFI = "CFI";
    private static final String VLAN_ID = "VlanId";
    private static final String ETHT = "EtherType";

    private static Map<String, Pair<Integer, Integer>> fieldCoordinates = new LinkedHashMap<String, Pair<Integer, Integer>>() {
        private static final long serialVersionUID = 1L;
        {
            put(PRIORITY, new ImmutablePair<Integer, Integer>(0, 3));
            put(CFI, new ImmutablePair<Integer, Integer>(3, 1));
            put(VLAN_ID, new ImmutablePair<Integer, Integer>(4, 12));
            put(ETHT, new ImmutablePair<Integer, Integer>(16, 16));
        }
    };
    private Map<String, byte[]> fieldValues;

    /**
     * Default constructor that creates and sets the HashMap
     */
    public IEEE8021Q() {
        super();
        fieldValues = new HashMap<String, byte[]>();
        hdrFieldCoordMap = fieldCoordinates;
        hdrFieldsMap = fieldValues;
    }

    public IEEE8021Q(boolean writeAccess) {
        super(writeAccess);
        fieldValues = new HashMap<String, byte[]>();
        hdrFieldCoordMap = fieldCoordinates;
        hdrFieldsMap = fieldValues;
    }

    public short getPriority() {
        return (BitBufferHelper.getShort(fieldValues.get(PRIORITY)));
    }

    public short getCfi() {
        return (BitBufferHelper.getShort(fieldValues.get(CFI)));
    }

    public short getVlanId() {
        return (BitBufferHelper.getShort(fieldValues.get(VLAN_ID)));
    }

    @Override
    public short getEtherType() {
        return BitBufferHelper.getShort(fieldValues.get(ETHT));
    }

    public IEEE8021Q setPriority(short priority) {
        byte[] priorityByte = BitBufferHelper.toByteArray(priority);
        fieldValues.put(PRIORITY, priorityByte);
        return this;
    }

    public IEEE8021Q setCFI(short cfi) {
        byte[] cfiByte = BitBufferHelper
                .toByteArray(cfi);
        fieldValues.put(CFI, cfiByte);
        return this;
    }

    public IEEE8021Q setVlanId(short vlanId) {
        byte[] vlan = BitBufferHelper
                .toByteArray(vlanId);
        fieldValues.put(VLAN_ID, vlan);
        return this;
    }

    @Override
    public IEEE8021Q setEtherType(short etherType) {
        byte[] ethType = BitBufferHelper.toByteArray(etherType);
        fieldValues.put(ETHT, ethType);
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result
                + ((fieldValues == null) ? 0 : fieldValues.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        IEEE8021Q other = (IEEE8021Q) obj;
        if (fieldValues == null) {
            if (other.fieldValues != null) {
                return false;
            }
        } else if (!fieldValues.equals(other.fieldValues)) {
            return false;
        }
        return true;
    }
}

/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager.api;

public enum IpAddressSource {
    ExternalFixedIP(0, "ExternalFixedIP"),

    FloatingIP(1, "FloatingIP");

    java.lang.String name;
    int value;
    private static final java.util.Map<java.lang.Integer, IpAddressSource> VALUE_MAP;

    static {
        final com.google.common.collect.ImmutableMap.Builder<java.lang.Integer, IpAddressSource>
                b = com.google.common.collect.ImmutableMap.builder();
        for (IpAddressSource enumItem : IpAddressSource.values()) {
            b.put(enumItem.value, enumItem);
        }
        VALUE_MAP = b.build();
    }

    IpAddressSource(int value, java.lang.String name) {
        this.value = value;
        this.name = name;
    }

    /**
     * Returns the name of the enumeration item as it is specified in the input yang.
     *
     * @return the name of the enumeration item as it is specified in the input yang
     */
    public java.lang.String getName() {
        return name;
    }

    /**
     *  Returns the equivalent Integer value.
     * @return integer value.
     */
    public int getIntValue() {
        return value;
    }

    /**
     *  Returns the IpAddressSource
     *
     * @param valueArg integer value of the IpAddressSource.
     * @return corresponding IpAddressSource item.
     */
    public static IpAddressSource forValue(int valueArg) {
        return VALUE_MAP.get(valueArg);
    }
}

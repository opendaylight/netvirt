/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;

import java.math.BigInteger;

/**
 * Created by achuth.m on 24-06-2017.
 */
public class ArpResponderInput {

    private BigInteger dpnId;
    private String ipAddress;

    public ArpResponderInput(ArpResponderInputBuilder builder){
        this.dpnId = builder.dpnId;
        this.ipAddress = builder.ipAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public BigInteger getDpnId() {
        return dpnId;
    }

    public void setDpnId(BigInteger dpnId) {
        this.dpnId = dpnId;
    }


    public static class ArpResponderInputBuilder {
        private BigInteger dpnId;
        private String ipAddress;

        public  ArpResponderInputBuilder(){}

        public ArpResponderInputBuilder setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public ArpResponderInputBuilder setDpnId(BigInteger dpnId) {
            this.dpnId = dpnId;
            return this;
        }

        public ArpResponderInput build(){
            return new ArpResponderInput(this);
        }
    }
}

/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.factory;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;


public class VrfInputImpl implements VrfInput {
    private VrfEntry vrfEntry;
    private Long vpnId;
    private String rd;
    private short action;
    private InstanceIdentifier id;
    private VpnInstanceOpDataEntry vpnInstance;
    BigInteger dpnId;
    private DataBroker dataBroker;

    public VrfInput setVrfEntry(VrfEntry vrfEntry) {
        this.vrfEntry = vrfEntry;
        return this;
    }

    public VrfInput setVpnId(Long vpnId) {
        this.vpnId = vpnId;
        return this;
    }

    public VrfInput setRd(String rd) {
        this.rd = rd;
        return this;
    }

    public VrfInput setAction(short action) {
        this.action = action;
        return this;
    }

    public VrfInput setInstanceIdentifier (InstanceIdentifier id) {
        this.id = id;
        return this;
    }

    public VrfInput setVpnInstance(VpnInstanceOpDataEntry vpnInstance) {
        this.vpnInstance = vpnInstance;
        return this;
    }

    public VrfInput setDataBroker(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        return this;
    }

    public VrfInput setDpnId(BigInteger dpnId) {
        this.dpnId = dpnId;
        return this;
    }

    public short getAction() {
        return this.action;
    }

    public VpnInstanceOpDataEntry getVpnInstance() {
        return this.vpnInstance;
    }

    public Long getVpnId() {
        return this.vpnId;
    }

    public VrfEntry getVrfEntry() {
        return this.vrfEntry;
    }

    public String getRd() {
        return this.rd;
    }

    public BigInteger getDpnId() {
        return this.dpnId;
    }

    public DataBroker getDataBroker() {
        return  this.dataBroker;
    }
}
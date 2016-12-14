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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;


public interface VrfInput {
    short ADD_SUBNET_VRF = 0;
    short MOD_SUBNET_VRF = 1;
    short DELETE_SUBNET_VRF = 2;

    public VrfInput setVrfEntry(VrfEntry vrfEntry);
    public VrfInput setVpnId(Long vpnId);
    public VrfInput setRd(String rd);
    public VrfInput setAction(short action);
    public VrfInput setDpnId(BigInteger dpnId);
    public VrfInput setInstanceIdentifier(InstanceIdentifier id);
    public VrfInput setVpnInstance(VpnInstanceOpDataEntry vpnInstance);
    public VrfInput setDataBroker(DataBroker dataBroker);
    public short getAction();
    public BigInteger getDpnId();
    public Long getVpnId();
    public VrfEntry getVrfEntry();
    public VpnInstanceOpDataEntry getVpnInstance();
    public String getRd();
    public DataBroker getDataBroker();
}
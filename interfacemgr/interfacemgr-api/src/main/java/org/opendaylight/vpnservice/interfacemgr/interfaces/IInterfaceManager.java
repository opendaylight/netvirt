/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.interfaces;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;

import java.math.BigInteger;
import java.util.List;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;

public interface IInterfaceManager {

    public Long getPortForInterface(String ifName);
    public BigInteger getDpnForInterface(String ifName);
    public BigInteger getDpnForInterface(Interface intrf);
    public String getEndpointIpForDpn(BigInteger dpnId);
    public List<MatchInfo> getInterfaceIngressRule(String ifName);
    public List<ActionInfo> getInterfaceEgressActions(String ifName);

}
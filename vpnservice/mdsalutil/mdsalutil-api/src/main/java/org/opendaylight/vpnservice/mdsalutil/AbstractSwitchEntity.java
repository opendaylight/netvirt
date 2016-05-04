/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;

public class AbstractSwitchEntity {
    private static final long serialVersionUID = 1L;

    private BigInteger m_dpnId;


    public AbstractSwitchEntity(BigInteger dpnId) {
        m_dpnId = dpnId;
    }

    @Override
    public String toString() {
        return "AbstractSwitchEntity [m_lDpnId=" + m_dpnId + " ]";
    }


    public BigInteger getDpnId() {
        return m_dpnId;
    }

    public void setDpnId(BigInteger dpnId) {
        m_dpnId = dpnId;
    }

}

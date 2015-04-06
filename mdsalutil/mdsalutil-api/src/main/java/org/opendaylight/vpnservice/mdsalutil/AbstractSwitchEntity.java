/*
 * Copyright (c) 2013 Ericsson AB.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */

package org.opendaylight.vpnservice.mdsalutil;

public class AbstractSwitchEntity {
    private static final long serialVersionUID = 1L;

    private long m_lDpnId;
    

    public AbstractSwitchEntity(long lDpnId) {
        m_lDpnId = lDpnId;
      
    }
    
    @Override
    public String toString() {
        return "AbstractSwitchEntity [m_lDpnId=" + m_lDpnId + " ]";
    }

    
    public long getDpnId() {
        return m_lDpnId;
    }

    public void setDpnId(long lDpnId) {
        m_lDpnId = lDpnId;
    }

}

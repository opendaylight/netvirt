/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

public class IVpnTunnelLocType {
    public enum ITMTunnelLocType {
        Invalid(0), Internal(1), External(2), Hwvtep(3);

        private final int type;
        ITMTunnelLocType(int id) {
            this.type = id;
        }
        public int getValue() {
            return type;
        }
    }
}
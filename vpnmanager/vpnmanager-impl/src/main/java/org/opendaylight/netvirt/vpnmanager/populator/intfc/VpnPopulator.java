/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.intfc;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;

public interface VpnPopulator {
    void populateFib(L3vpnInput input, WriteTransaction writeCfgTxn, WriteTransaction writeOperTxn);

    Adjacency createOperationalAdjacency(L3vpnInput input);
}

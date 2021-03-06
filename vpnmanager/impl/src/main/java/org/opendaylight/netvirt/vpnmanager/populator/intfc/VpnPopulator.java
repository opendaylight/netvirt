/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.intfc;

import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;

public interface VpnPopulator {
    void populateFib(L3vpnInput input, TypedWriteTransaction<Configuration> writeCfgTxn);

    Adjacency createOperationalAdjacency(L3vpnInput input);
}

/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import java.math.BigInteger;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;

public interface IVpnFootprintService {

    /**
     * Updates the footprint that a VPN has on a given DPN by adding/removing
     * the specified interface.
     *
     * @param dpId DPN where the VPN interface belongs to
     * @param vpnName Name of the VPN whose footprint is being modified
     * @param interfaceName Name of the VPN interface to be added/removed to/from the specified DPN
     * @param add true for addition, false for removal
     */
    void updateVpnToDpnMapping(BigInteger dpId, String vpnName, String primaryRd, String interfaceName,
            ImmutablePair<IpAddresses.IpAddressSource, String> ipAddressSourceValuePair, boolean add);
}

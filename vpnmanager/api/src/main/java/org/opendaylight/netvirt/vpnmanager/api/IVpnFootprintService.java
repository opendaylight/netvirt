/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.IpAddresses;
import org.opendaylight.yangtools.yang.common.Uint64;

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
    void updateVpnToDpnMapping(Uint64 dpId, String vpnName, String primaryRd, @Nullable String interfaceName,
            ImmutablePair<IpAddresses.IpAddressSource, String> ipAddressSourceValuePair, boolean add);
}

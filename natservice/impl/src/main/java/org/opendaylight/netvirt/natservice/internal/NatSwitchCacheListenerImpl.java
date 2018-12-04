/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.natservice.api.NatSwitchCacheListener;
import org.opendaylight.netvirt.natservice.api.SwitchInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;

public class NatSwitchCacheListenerImpl implements NatSwitchCacheListener {

    private final DataBroker dataBroker;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;

    NatSwitchCacheListenerImpl(final DataBroker dataBroker,
            final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer) {
        this.dataBroker = dataBroker;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;

    }

    public void switchAddedToCache(SwitchInfo switchInfo) {
        ExternalSubnets externalSubnets = NatUtil.getExternalSubnets(dataBroker);
        if (externalSubnets != null) {
            for (Subnets externalSubnet : externalSubnets.getSubnets()) {
                Uuid externalNetwroKUuid = externalSubnet.getExternalNetworkId();
                String providerNet = NatUtil.getElanInstancePhysicalNetwok(externalNetwroKUuid.getValue(),
                        dataBroker);
                if (switchInfo.getProviderNet().contains(providerNet)) {
                    long vpnid = NatUtil.getVpnId(dataBroker, externalNetwroKUuid.getValue());
                    snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnetInDpn(externalSubnet,
                            externalNetwroKUuid.getValue(), NwConstants.ADD_FLOW, vpnid, switchInfo.getDpnId());
                }
            }
        }
    }

    public void switchRemovedFromCache(SwitchInfo switchInfo) {
        /* Do Nothing */
    }
}

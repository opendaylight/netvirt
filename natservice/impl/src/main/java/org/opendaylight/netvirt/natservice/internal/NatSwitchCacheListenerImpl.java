/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.natservice.api.NatSwitchCache;
import org.opendaylight.netvirt.natservice.api.NatSwitchCacheListener;
import org.opendaylight.netvirt.natservice.api.SwitchInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yangtools.yang.common.Uint32;

@Singleton
public class NatSwitchCacheListenerImpl implements NatSwitchCacheListener {

    private final DataBroker dataBroker;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;

    @Inject
    public NatSwitchCacheListenerImpl(final DataBroker dataBroker,
            final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer, NatSwitchCache natSwitchCache,
            final NatserviceConfig config) {
        this.dataBroker = dataBroker;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
        if (config != null && config.getNatMode().equals(NatserviceConfig.NatMode.Conntrack)) {
            natSwitchCache.register(this);
        }
    }

    public void switchAddedToCache(SwitchInfo switchInfo) {
        ExternalSubnets externalSubnets = NatUtil.getExternalSubnets(dataBroker);
        if (externalSubnets != null) {
            for (Subnets externalSubnet : externalSubnets.getSubnets()) {
                Uuid externalNetworkUuid = externalSubnet.getExternalNetworkId();
                String providerNet = NatUtil.getElanInstancePhysicalNetwok(externalNetworkUuid.getValue(),
                        dataBroker);
                if (switchInfo.getProviderNets().contains(providerNet)) {
                    Uint32 vpnid = NatUtil.getVpnId(dataBroker, externalNetworkUuid.getValue());
                    snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnetInDpn(externalSubnet,
                            externalNetworkUuid.getValue(), NwConstants.ADD_FLOW, vpnid, switchInfo.getDpnId());
                }
            }
        }
    }

    public void switchRemovedFromCache(SwitchInfo switchInfo) {
        /* Do Nothing */
    }
}

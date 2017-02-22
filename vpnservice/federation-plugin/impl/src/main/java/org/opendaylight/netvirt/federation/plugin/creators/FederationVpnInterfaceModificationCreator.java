/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.creators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationVpnInterfaceModificationCreator
        implements FederationPluginModificationCreator<VpnInterface, VpnInterfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationVpnInterfaceModificationCreator.class);

    @Inject
    public FederationVpnInterfaceModificationCreator() {
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.VPN_INTERFACE_KEY, this);
    }

    @Override
    public Collection<DataTreeModification<VpnInterface>> createDataTreeModifications(VpnInterfaces vpnInterfaces) {
        if (vpnInterfaces == null || vpnInterfaces.getVpnInterface() == null) {
            LOG.debug("No VPN interfaces found");
            return Collections.emptyList();
        }

        Collection<DataTreeModification<VpnInterface>> modifications = new ArrayList<>();
        for (VpnInterface vpnInterface : vpnInterfaces.getVpnInterface()) {
            modifications.add(new FullSyncDataTreeModification<VpnInterface>(vpnInterface));
        }

        return modifications;
    }

}

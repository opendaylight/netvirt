/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.identifiers;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class FederationVpnInterfaceIdentifier
        implements FederationPluginIdentifier<VpnInterface, VpnInterfaces, VpnInterfaces> {

    @Inject
    public FederationVpnInterfaceIdentifier() {
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.VPN_INTERFACE_KEY,
                LogicalDatastoreType.CONFIGURATION, this);
    }

    @Override
    public InstanceIdentifier<VpnInterface> getInstanceIdentifier() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    public InstanceIdentifier<VpnInterfaces> getParentInstanceIdentifier() {
        return InstanceIdentifier.create(VpnInterfaces.class);
    }

    @Override
    public InstanceIdentifier<VpnInterfaces> getSubtreeInstanceIdentifier() {
        return InstanceIdentifier.create(VpnInterfaces.class);
    }

}

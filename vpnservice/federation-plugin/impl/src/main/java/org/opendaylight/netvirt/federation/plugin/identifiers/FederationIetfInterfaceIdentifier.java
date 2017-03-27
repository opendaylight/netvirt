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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class FederationIetfInterfaceIdentifier
        implements FederationPluginIdentifier<Interface, Interfaces, Interfaces> {

    @Inject
    public FederationIetfInterfaceIdentifier() {
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.IETF_INTERFACE_KEY,
                LogicalDatastoreType.CONFIGURATION, this);
    }

    @Override
    public InstanceIdentifier<Interface> getInstanceIdentifier() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    public InstanceIdentifier<Interfaces> getParentInstanceIdentifier() {
        return InstanceIdentifier.create(Interfaces.class);
    }

    @Override
    public InstanceIdentifier<Interfaces> getSubtreeInstanceIdentifier() {
        return InstanceIdentifier.create(Interfaces.class);
    }

}

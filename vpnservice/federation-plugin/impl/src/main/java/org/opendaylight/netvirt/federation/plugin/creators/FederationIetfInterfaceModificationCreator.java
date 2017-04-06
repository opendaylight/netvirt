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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationIetfInterfaceModificationCreator
        implements FederationPluginModificationCreator<Interface, Interfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationIetfInterfaceModificationCreator.class);

    @Inject
    public FederationIetfInterfaceModificationCreator() {
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.IETF_INTERFACE_KEY, this);
    }

    @Override
    public Collection<DataTreeModification<Interface>> createDataTreeModifications(Interfaces interfaces) {
        if (interfaces == null || interfaces.getInterface() == null) {
            LOG.debug("No IETF interfaces found");
            return Collections.emptyList();
        }

        Collection<DataTreeModification<Interface>> modifications = new ArrayList<>();
        for (Interface iface : interfaces.getInterface()) {
            modifications.add(new FullSyncDataTreeModification<Interface>(iface));
        }

        return modifications;
    }

}

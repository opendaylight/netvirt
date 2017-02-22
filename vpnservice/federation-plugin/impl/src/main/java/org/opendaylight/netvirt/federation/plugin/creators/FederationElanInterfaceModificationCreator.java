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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationElanInterfaceModificationCreator
        implements FederationPluginModificationCreator<ElanInterface, ElanInterfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationElanInterfaceModificationCreator.class);

    @Inject
    public FederationElanInterfaceModificationCreator() {
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.ELAN_INTERFACE_KEY, this);
    }

    @Override
    public Collection<DataTreeModification<ElanInterface>> createDataTreeModifications(ElanInterfaces elanInterfaces) {
        if (elanInterfaces == null || elanInterfaces.getElanInterface() == null) {
            LOG.debug("No ELAN interfaces found");
            return Collections.emptyList();
        }

        Collection<DataTreeModification<ElanInterface>> modifications = new ArrayList<>();
        for (ElanInterface elanInterface : elanInterfaces.getElanInterface()) {
            modifications.add(new FullSyncDataTreeModification<ElanInterface>(elanInterface));
        }

        return modifications;
    }

}

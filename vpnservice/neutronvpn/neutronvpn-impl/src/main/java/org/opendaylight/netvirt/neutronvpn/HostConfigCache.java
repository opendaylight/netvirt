/*
 * Copyright (c) 2017 Mellanox. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.cache.DataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.Hostconfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.hostconfigs.Hostconfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.hostconfigs.HostconfigKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class HostConfigCache extends DataObjectCache<Hostconfig> {
    @Inject
    public HostConfigCache(DataBroker dataBroker, CacheProvider cacheProvider) {
        super(Hostconfig.class, dataBroker, LogicalDatastoreType.OPERATIONAL,
              InstanceIdentifier.builder(Neutron.class).child(Hostconfigs.class).child(Hostconfig.class).build(),
              cacheProvider);
    }

    public Optional<Hostconfig> get(@Nonnull String hostId) throws ReadFailedException {
        InstanceIdentifier<Hostconfig> hostConfigPath = InstanceIdentifier.builder(Neutron.class)
                                                 .child(Hostconfigs.class)
                                                 .child(Hostconfig.class, new HostconfigKey(hostId, "ODL L2"))
                                                 .build();
        return get(hostConfigPath);
    }
}

/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.providers;

import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipCandidateRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListenerRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.TableId;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sam Hague (shague@redhat.com)
 */
public class NetvirtProvidersProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetvirtProvidersProvider.class);

    private final DataBroker dataBroker;
    private final EntityOwnershipService entityOwnershipService;
    private ProviderEntityListener providerEntityListener = null;
    private static AtomicBoolean hasProviderEntityOwnership = new AtomicBoolean(false);
    private static short tableOffset;


    private NetvirtProvidersConfigImpl netvirtProvidersConfig;

    public NetvirtProvidersProvider(final DataBroker dataBroker,
                                    final EntityOwnershipService eos,
                                    final short tableOffset) {
        LOG.info("NetvirtProvidersProvider");
        this.dataBroker = dataBroker;
        this.entityOwnershipService = eos;
        setTableOffset(tableOffset);
    }

    public static boolean isMasterProviderInstance() {
        return hasProviderEntityOwnership.get();
    }

    public static void setTableOffset(short tableOffset) {
        try {
            new TableId((short) (tableOffset + Service.L2_FORWARDING.getTable()));
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid table offset: {}", tableOffset, e);
            return;
        }

        LOG.info("setTableOffset: changing from {} to {}",
                NetvirtProvidersProvider.tableOffset, tableOffset);
        NetvirtProvidersProvider.tableOffset = tableOffset;
    }

    public static short getTableOffset() {
        return tableOffset;
    }

    @Override
    public void close() throws Exception {
        LOG.info("NetvirtProvidersProvider closed");
        if (providerEntityListener != null) {
            providerEntityListener.close();
        }
    }

    public void start() {
        LOG.info("NetvirtProvidersProvider: onSessionInitiated dataBroker: {}", dataBroker);
        providerEntityListener = new ProviderEntityListener(this, entityOwnershipService);
        netvirtProvidersConfig = new NetvirtProvidersConfigImpl(dataBroker, tableOffset);
    }

    private void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        if (ownershipChange.isOwner()) {
            LOG.info("*This* instance of OVSDB netvirt provider is a MASTER instance");
            hasProviderEntityOwnership.set(true);
        } else {
            LOG.info("*This* instance of OVSDB netvirt provider is a SLAVE instance");
            hasProviderEntityOwnership.set(false);
        }
    }

    private class ProviderEntityListener implements EntityOwnershipListener {
        private NetvirtProvidersProvider provider;
        private EntityOwnershipListenerRegistration listenerRegistration;
        private EntityOwnershipCandidateRegistration candidateRegistration;

        ProviderEntityListener(NetvirtProvidersProvider provider,
                               EntityOwnershipService entityOwnershipService) {
            this.provider = provider;
            this.listenerRegistration =
                    entityOwnershipService.registerListener(Constants.NETVIRT_OWNER_ENTITY_TYPE, this);

            //register instance entity to get the ownership of the netvirt provider
            Entity instanceEntity = new Entity(
                    Constants.NETVIRT_OWNER_ENTITY_TYPE, Constants.NETVIRT_OWNER_ENTITY_TYPE);
            try {
                this.candidateRegistration = entityOwnershipService.registerCandidate(instanceEntity);
            } catch (CandidateAlreadyRegisteredException e) {
                LOG.warn("OVSDB Netvirt Provider instance entity {} was already "
                        + "registered for ownership", instanceEntity, e);
            }
        }

        public void close() {
            if (listenerRegistration != null) {
                this.listenerRegistration.close();
            }
            if (candidateRegistration != null) {
                this.candidateRegistration.close();
            }
            if (netvirtProvidersConfig != null) {
                netvirtProvidersConfig.close();
            }
        }

        @Override
        public void ownershipChanged(EntityOwnershipChange ownershipChange) {
            provider.handleOwnershipChange(ownershipChange);
        }
    }
}

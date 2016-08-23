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
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbInventoryService;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.TableId;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sam Hague (shague@redhat.com)
 */
public class NetvirtProvidersProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetvirtProvidersProvider.class);

    private final BundleContext bundleContext;
    private static DataBroker dataBroker;
    private ConfigActivator activator;
    private final EntityOwnershipService entityOwnershipService;
    private ProviderEntityListener providerEntityListener = null;
    private static AtomicBoolean hasProviderEntityOwnership = new AtomicBoolean(false);
    private static short tableOffset;
    private final NotificationProviderService notificationProviderService;
    private final PacketProcessingService packetProcessingService;
    private final SalFlowService salFlowService;
    private final OvsdbInventoryService ovsdbInventoryService;

    public NetvirtProvidersProvider(final DataBroker dataBroker,
                                    final EntityOwnershipService eos,
                                    final NotificationProviderService notificationProviderService,
                                    final PacketProcessingService packetProcessingService,
                                    final SalFlowService salFlowService,
                                    final short tableOffset,
                                    final OvsdbInventoryService ovsdbInventoryService) {
        LOG.info("NetvirtProvidersProvider");
        NetvirtProvidersProvider.dataBroker = dataBroker;
        this.notificationProviderService = notificationProviderService;
        this.entityOwnershipService = eos;
        this.bundleContext = FrameworkUtil.getBundle(NetvirtProvidersProvider.class).getBundleContext();
        this.salFlowService = salFlowService;
        this.packetProcessingService = packetProcessingService;
        setTableOffset(tableOffset);
        this.ovsdbInventoryService = ovsdbInventoryService;

        assert(ovsdbInventoryService != null);
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
        activator.stop(bundleContext);
        providerEntityListener.close();
    }

    public void start() {
        LOG.info("NetvirtProvidersProvider: onSessionInitiated dataBroker: {}", dataBroker);
        providerEntityListener = new ProviderEntityListener(this, entityOwnershipService);
        this.activator = new ConfigActivator(dataBroker, notificationProviderService, packetProcessingService, salFlowService);
        try {
            activator.start(bundleContext);
        } catch (Exception e) {
            LOG.warn("Failed to start Netvirt: ", e);
        }
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
            this.listenerRegistration.close();
            this.candidateRegistration.close();
        }

        @Override
        public void ownershipChanged(EntityOwnershipChange ownershipChange) {
            provider.handleOwnershipChange(ownershipChange);
        }
    }

    public static DataBroker getDataBroker() {
        return dataBroker;
    }
}

/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;
import static org.opendaylight.netvirt.elan.utils.ElanConstants.ELAN_EOS_DELAY;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipChange;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipListener;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.netvirt.elan.internal.ElanDpnInterfaceClusteredListener;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInstanceEntityOwnershipListener implements EntityOwnershipListener {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceEntityOwnershipListener.class);

    private final L2GatewayConnectionListener l2GatewayConnectionListener;
    private final ElanDpnInterfaceClusteredListener elanDpnInterfaceClusteredListener;
    private final Scheduler scheduler;
    private final DataBroker dataBroker;
    volatile ScheduledFuture<?> ft;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public ElanInstanceEntityOwnershipListener(L2GatewayConnectionListener l2GatewayConnectionListener,
                                        ElanDpnInterfaceClusteredListener elanDpnInterfaceClusteredListener,
                                        Scheduler scheduler, DataBroker dataBroker,
                                        EntityOwnershipService entityOwnershipService) {
        this.l2GatewayConnectionListener = l2GatewayConnectionListener;
        this.elanDpnInterfaceClusteredListener = elanDpnInterfaceClusteredListener;
        this.scheduler = scheduler;
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        entityOwnershipService.registerListener(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE, this);
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void ownershipChanged(EntityOwnershipChange ownershipChange) {
        LOG.info("Entity Ownership changed for the entity: {}" , ownershipChange);
        if (!ownershipChange.getState().isOwner()) {
            if (ft != null) {
                ft.cancel(false);
                ft = null;
            }
            return;
        }

        if (!ownershipChange.getState().wasOwner() && ownershipChange.getState().isOwner()) {
            if (ft != null) {
                ft.cancel(false);
                ft = null;
            }
            ft = scheduler.getScheduledExecutorService().schedule(() -> {
                try {
                    //check if i'm the owner
                    if (ownershipChange.getState().isOwner()) {
                        LOG.info("Elan Entity owner is: {}", ownershipChange);

                        txRunner.callWithNewReadOnlyTransactionAndClose(CONFIGURATION, tx -> {
                            l2GatewayConnectionListener.loadL2GwConnectionCache(tx);
                        });

                        InstanceIdentifier<ElanDpnInterfaces> elanDpnInterfacesInstanceIdentifier = InstanceIdentifier
                                .builder(ElanDpnInterfaces.class).build();

                        txRunner.callWithNewReadOnlyTransactionAndClose(OPERATIONAL, tx -> {
                            Optional<ElanDpnInterfaces> optional = Optional.empty();
                            try {
                                optional = tx.read(elanDpnInterfacesInstanceIdentifier).get();
                            } catch (ExecutionException | InterruptedException e) {
                                LOG.error("Exception While reading ElanDpnInterfaces", e);
                            }
                            if (optional.isPresent()
                                && optional.get().getElanDpnInterfacesList() != null) {
                                LOG.debug("Found elan dpn interfaces list");
                                optional.get().nonnullElanDpnInterfacesList().values()
                                    .forEach(elanDpnInterfacesList -> {
                                        List<DpnInterfaces> dpnInterfaces = new ArrayList<>(
                                            elanDpnInterfacesList.nonnullDpnInterfaces().values());
                                        InstanceIdentifier<ElanDpnInterfacesList> parentIid = InstanceIdentifier
                                            .builder(ElanDpnInterfaces.class)
                                            .child(ElanDpnInterfacesList.class,
                                                new ElanDpnInterfacesListKey(
                                                    elanDpnInterfacesList
                                                        .getElanInstanceName())).build();
                                        for (DpnInterfaces dpnInterface : dpnInterfaces) {
                                            LOG.debug("Found elan dpn interfaces");
                                            elanDpnInterfaceClusteredListener.add(parentIid
                                                    .child(DpnInterfaces.class, dpnInterface.key()),
                                                dpnInterface);
                                        }
                                    });
                            }
                        });

                    } else {
                        LOG.info("Not the owner for Elan entity {}", ownershipChange);
                    }
                    ft = null;
                } catch (Exception e) {
                    LOG.error("Failed to read mdsal ", e);
                }
            }, ELAN_EOS_DELAY, TimeUnit.MINUTES);
        }
    }
}

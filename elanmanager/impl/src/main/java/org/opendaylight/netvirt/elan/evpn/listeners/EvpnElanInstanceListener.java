/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnMacVrfUtils;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Singleton
public class EvpnElanInstanceListener extends AbstractAsyncDataTreeChangeListener<ElanInstance> {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnElanInstanceListener.class);
    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final EvpnUtils evpnUtils;
    private final EvpnMacVrfUtils evpnMacVrfUtils;
    private final IMdsalApiManager mdsalManager;


    @Inject
    public EvpnElanInstanceListener(final DataBroker dataBroker, final EvpnUtils evpnUtils,
                                    EvpnMacVrfUtils evpnMacVrfUtils, IMdsalApiManager mdsalApiManager) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class),
                Executors.newListeningSingleThreadExecutor("EvpnElanInstanceListener", LOG));
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.evpnUtils = evpnUtils;
        this.evpnMacVrfUtils = evpnMacVrfUtils;
        this.mdsalManager = mdsalApiManager;
    }

    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void add(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance evpnAugmentation) {
    }

    @Override
    public void remove(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance evpnAugmentation) {
    }

    @Override
    public void update(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance original,
                          ElanInstance update) {
        String elanName = update.getElanInstanceName();
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, confTx -> {
            if (evpnUtils.isWithdrawEvpnRT2Routes(original, update)) {
                evpnUtils.withdrawEvpnRT2Routes(original.augmentation(EvpnAugmentation.class), elanName);
                evpnMacVrfUtils.updateEvpnDmacFlows(original, false);
                evpnUtils.programEvpnL2vniDemuxTable(elanName,
                    (elan, interfaceName) -> evpnUtils.bindElanServiceToExternalTunnel(elanName, interfaceName),
                    (dpnId, flowEntity) -> mdsalManager.addFlow(confTx, flowEntity));
            } else if (evpnUtils.isAdvertiseEvpnRT2Routes(original, update)) {
                evpnUtils.advertiseEvpnRT2Routes(update.augmentation(EvpnAugmentation.class), elanName);
                evpnMacVrfUtils.updateEvpnDmacFlows(update, true);
                evpnUtils.programEvpnL2vniDemuxTable(elanName,
                    (elan, interfaceName) -> evpnUtils.unbindElanServiceFromExternalTunnel(elanName, interfaceName),
                    (dpnId, flowEntity) -> {
                        try {
                            mdsalManager.removeFlow(confTx, dpnId, flowEntity.getFlowId(), flowEntity.getTableId());
                        } catch (ExecutionException | InterruptedException e) {
                            LOG.error("Error removing flow", e);
                        }
                    });
            }
        }), LOG, "Error handling EVPN ELAN instance update");
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }
}

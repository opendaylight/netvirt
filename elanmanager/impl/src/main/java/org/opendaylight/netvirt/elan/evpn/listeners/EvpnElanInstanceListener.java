/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn.listeners;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnMacVrfUtils;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Singleton
public class EvpnElanInstanceListener extends AsyncDataTreeChangeListenerBase<ElanInstance, EvpnElanInstanceListener> {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnElanInstanceListener.class);
    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final EvpnUtils evpnUtils;
    private final EvpnMacVrfUtils evpnMacVrfUtils;
    private final IMdsalApiManager mdsalManager;


    @Inject
    public EvpnElanInstanceListener(final DataBroker dataBroker, final EvpnUtils evpnUtils,
                                    EvpnMacVrfUtils evpnMacVrfUtils, IMdsalApiManager mdsalApiManager) {
        super(ElanInstance.class, EvpnElanInstanceListener.class);
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.evpnUtils = evpnUtils;
        this.evpnMacVrfUtils = evpnMacVrfUtils;
        this.mdsalManager = mdsalApiManager;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<ElanInstance> getWildCardPath() {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class).build();
    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance evpnAugmentation) {
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance evpnAugmentation) {
    }

    @Override
    protected void update(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance original,
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
    protected EvpnElanInstanceListener getDataTreeChangeListener() {
        return this;
    }

}

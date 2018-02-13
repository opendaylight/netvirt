/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanLearntVpnVipToPortListener extends
        AsyncDataTreeChangeListenerBase<LearntVpnVipToPort, ElanLearntVpnVipToPortListener> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanLearntVpnVipToPortListener.class);
    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final ElanUtils elanUtils;
    private final JobCoordinator jobCoordinator;

    @Inject
    public ElanLearntVpnVipToPortListener(DataBroker broker, ElanUtils elanUtils, JobCoordinator jobCoordinator) {
        super(LearntVpnVipToPort.class, ElanLearntVpnVipToPortListener.class);
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.elanUtils = elanUtils;
        this.jobCoordinator = jobCoordinator;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    protected InstanceIdentifier<LearntVpnVipToPort> getWildCardPath() {
        return InstanceIdentifier.create(LearntVpnVipToPortData.class).child(LearntVpnVipToPort.class);
    }

    @Override
    protected void remove(InstanceIdentifier<LearntVpnVipToPort> key, LearntVpnVipToPort dataObjectModification) {
        String macAddress = dataObjectModification.getMacAddress();
        String interfaceName = dataObjectModification.getPortName();
        LOG.trace("Removing mac address {} from interface {} ", macAddress, interfaceName);
        jobCoordinator.enqueueJob(buildJobKey(macAddress, interfaceName),
                new StaticMacRemoveWorker(macAddress, interfaceName));
    }

    @Override
    protected void update(InstanceIdentifier<LearntVpnVipToPort> key, LearntVpnVipToPort dataObjectModificationBefore,
            LearntVpnVipToPort dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<LearntVpnVipToPort> key, LearntVpnVipToPort dataObjectModification) {
        String macAddress = dataObjectModification.getMacAddress();
        String interfaceName = dataObjectModification.getPortName();
        LOG.trace("Adding mac address {} to interface {} ", macAddress, interfaceName);
        jobCoordinator.enqueueJob(buildJobKey(macAddress, interfaceName),
                new StaticMacAddWorker(macAddress, interfaceName));
    }

    @Override
    protected ElanLearntVpnVipToPortListener getDataTreeChangeListener() {
        return this;
    }

    private class StaticMacAddWorker implements Callable<List<ListenableFuture<Void>>> {
        String macAddress;
        String interfaceName;

        StaticMacAddWorker(String macAddress, String interfaceName) {
            this.macAddress = macAddress;
            this.interfaceName = interfaceName;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
            if (elanInterface == null) {
                LOG.debug("ElanInterface Not present for interfaceName {} for add event", interfaceName);
                return Collections.emptyList();
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(interfaceTx -> futures.add(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        flowTx -> elanUtils.addMacEntryToDsAndSetupFlows(interfaceName, macAddress,
                                elanInterface.getElanInstanceName(), interfaceTx, flowTx,
                                ElanConstants.STATIC_MAC_TIMEOUT)))));
            return futures;
        }
    }

    private class StaticMacRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        String macAddress;
        String interfaceName;

        StaticMacRemoveWorker(String macAddress, String interfaceName) {
            this.macAddress = macAddress;
            this.interfaceName = interfaceName;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
            if (elanInterface == null) {
                LOG.debug("ElanInterface Not present for interfaceName {} for delete event", interfaceName);
                return Collections.emptyList();
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(interfaceTx -> futures.add(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        flowTx -> elanUtils.deleteMacEntryFromDsAndRemoveFlows(interfaceName, macAddress,
                                elanInterface.getElanInstanceName(), interfaceTx, flowTx)))));
            return futures;
        }
    }

    private String buildJobKey(String mac, String interfaceName) {
        return "ENTERPRISEMACJOB" + mac + interfaceName;
    }


}

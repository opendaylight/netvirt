/*
 * Copyright (c) 2018 Redhat and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnServiceElanDpnInterfacesListener
        extends AsyncDataTreeChangeListenerBase<DpnInterfaces, VpnServiceElanDpnInterfacesListener> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnServiceElanDpnInterfacesListener.class);
    private final DataBroker dataBroker;
    private final IInterfaceManager interfaceManager;
    private final IFibManager fibManager;
    private final JobCoordinator jobCoordinator;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public VpnServiceElanDpnInterfacesListener(final DataBroker dataBroker, final IInterfaceManager interfaceManager,
            final IFibManager fibManager,final JobCoordinator jobCoordinator) {
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.fibManager = fibManager;
        this.jobCoordinator = jobCoordinator;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @PostConstruct
    public void start() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    public InstanceIdentifier<DpnInterfaces> getWildCardPath() {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class)
                .child(DpnInterfaces.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces dpnInterfaces) {

    }

    @Override
    protected void update(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces original,
            DpnInterfaces update) {
        LOG.info("received Dpninterfaces update event for dpn {}", update.getDpId());
        BigInteger dpnId = update.getDpId();
        String elanInstanceName = identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
        ElanInstance elanInstance = VpnUtil.getElanInstanceByName(dataBroker, elanInstanceName);
        String vpnName = VpnUtil.getVpnNameFromElanIntanceName(dataBroker,elanInstanceName);
        if (vpnName == null) {
            return;
        }
        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
        if (elanInstance != null && !elanInstance.isExternal() && VpnUtil.isVlan(elanInstance)) {
            jobCoordinator.enqueueJob(elanInstance.getElanInstanceName(), () -> {
                ListenableFuture<Void> future = txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeConfigTxn -> {
                    List<String> addedInterfaces = getUpdatedInterfaceList(update.getInterfaces(),
                            original.getInterfaces());
                    for (String addedInterface: addedInterfaces) {
                        if (interfaceManager.isExternalInterface(addedInterface)) {
                            InstanceIdentifier<VpnToDpnList> id = VpnHelper.getVpnToDpnListIdentifier(primaryRd,
                                    dpnId);
                            Optional<VpnToDpnList> dpnInVpn = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                    LogicalDatastoreType.OPERATIONAL, id);
                            if (!dpnInVpn.isPresent() || (dpnInVpn.get().getVpnInterfaces() != null
                                    && dpnInVpn.get().getVpnInterfaces().size() != 1)) {
                                return;
                            }
                            if (!VpnUtil.shouldPopulateFibForVlan(dataBroker, vpnName, elanInstanceName,
                                    dpnId, interfaceManager)) {
                                return;
                            }
                            long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
                            fibManager.populateFibOnNewDpn(dpnId, vpnId, primaryRd, null);
                            break;
                        }
                    }
                    List<String> deletedInterfaces = getUpdatedInterfaceList(original.getInterfaces(),
                            update.getInterfaces());
                    if (!deletedInterfaces.isEmpty()) {
                        String routerPortUuid = VpnUtil.getRouterPordIdFromElanInstance(dataBroker, elanInstanceName);
                        if (update.getInterfaces().size() == 2 && update.getInterfaces().contains(routerPortUuid)) {
                            VpnUtil.removeRouterPortFromElanForVlanInDpn(vpnName, dpnId, dataBroker);
                        }
                    }
                });
                ListenableFutures.addErrorLogging(future, LOG, "Failed to process DpnInterfaces update event for"
                        + "dpn {} elan {} vpn {}", dpnId, elanInstanceName, vpnName);
                return Collections.singletonList(future);
            });
        }
    }

    @Override
    protected void add(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces dpnInterfaces) {
        BigInteger dpnId = dpnInterfaces.getDpId();
        String elanInstanceName = identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
        ElanInstance elanInstance = VpnUtil.getElanInstanceByName(dataBroker, elanInstanceName);
        if (!VpnUtil.isVlan(elanInstance)) {
            return;
        }
        String vpnName = VpnUtil.getVpnNameFromElanIntanceName(dataBroker,elanInstanceName);
        if (vpnName == null) {
            return;
        }
        VpnUtil.addRouterPortToElanForVlanInDpn(vpnName, dpnId, dataBroker);
    }

    @Override
    protected VpnServiceElanDpnInterfacesListener getDataTreeChangeListener() {
        return VpnServiceElanDpnInterfacesListener.this;
    }


    public static List<String> getUpdatedInterfaceList(List<String> updatedInterfaceList,
            List<String> currentInterfaceList) {
        if (updatedInterfaceList == null) {
            return Collections.<String>emptyList();
        }
        List<String> newInterfaceList = new ArrayList<>(updatedInterfaceList);
        if (currentInterfaceList == null) {
            return newInterfaceList;
        }
        List<String> origInterfaceList = new ArrayList<>(currentInterfaceList);
        for (Iterator<String> iterator = newInterfaceList.iterator(); iterator.hasNext();) {
            String updatedInterface = iterator.next();
            for (String currentInterface :origInterfaceList) {
                if (updatedInterface.equals(currentInterface)) {
                    iterator.remove();
                }
            }
        }
        return newInterfaceList;
    }

}

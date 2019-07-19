/*
 * Copyright (c) 2018 Redhat and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.Dpns;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnServiceElanDpnInterfacesListener extends AbstractAsyncDataTreeChangeListener<DpnInterfaces> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnServiceElanDpnInterfacesListener.class);
    private final DataBroker dataBroker;
    private final IInterfaceManager interfaceManager;
    private final IFibManager fibManager;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;

    @Inject
    public VpnServiceElanDpnInterfacesListener(final DataBroker dataBroker, final IInterfaceManager interfaceManager,
            final IFibManager fibManager,final JobCoordinator jobCoordinator, VpnUtil vpnUtil) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ElanDpnInterfaces.class)
                        .child(ElanDpnInterfacesList.class).child(DpnInterfaces.class),
                Executors.newListeningSingleThreadExecutor("VpnServiceElanDpnInterfacesListener", LOG));
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.fibManager = fibManager;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
        start();
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }


    @Override
    public void remove(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces dpnInterfaces) {

    }

    @Override
    public void update(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces original,
            DpnInterfaces update) {
        LOG.info("received Dpninterfaces update event for dpn {}", update.getDpId());
        Uint64 dpnId = update.getDpId();
        String elanInstanceName = identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
        ElanInstance elanInstance = vpnUtil.getElanInstanceByName(elanInstanceName);
        String vpnName = vpnUtil.getVpnNameFromElanIntanceName(elanInstanceName);
        if (vpnName == null) {
            return;
        }
        final String primaryRd = vpnUtil.getPrimaryRd(vpnName);
        if (elanInstance != null && !elanInstance.isExternal() && VpnUtil.isVlan(elanInstance)) {
            jobCoordinator.enqueueJob(elanInstance.getElanInstanceName(), () -> {
                List<String> addedInterfaces = getUpdatedInterfaceList(update.getInterfaces(),
                    original.getInterfaces());
                for (String addedInterface : addedInterfaces) {
                    if (interfaceManager.isExternalInterface(addedInterface)) {
                        InstanceIdentifier<Dpns> dpnElementId = VpnUtil
                                .getDpnListFromDpnOpElementsIdentifier(primaryRd, dpnId);
                        Optional<Dpns> dpnOpElement = VpnUtil.read(dataBroker,
                                LogicalDatastoreType.OPERATIONAL, dpnElementId);
                        if (!dpnOpElement.isPresent() || (dpnOpElement.get().getVpnInterfaces() != null
                                && dpnOpElement.get().getVpnInterfaces().size() != 1)) {
                            return Collections.emptyList();
                        }
                        if (!vpnUtil.shouldPopulateFibForVlan(vpnName, elanInstanceName, dpnId)) {
                            return Collections.emptyList();
                        }
                        Uint32 vpnId = vpnUtil.getVpnId(vpnName);
                        fibManager.populateFibOnNewDpn(dpnId, vpnId, primaryRd, null);
                        break;
                    }
                }
                List<String> deletedInterfaces = getUpdatedInterfaceList(original.getInterfaces(),
                    update.getInterfaces());
                if (!deletedInterfaces.isEmpty()) {
                    String routerPortUuid = vpnUtil.getRouterPordIdFromElanInstance(elanInstanceName);
                    if (update.getInterfaces().size() == 2 && update.getInterfaces().contains(routerPortUuid)) {
                        vpnUtil.removeRouterPortFromElanForVlanInDpn(vpnName, dpnId);
                    }
                }
                return Collections.emptyList();
            });
        }
    }

    @Override
    public void add(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces dpnInterfaces) {
        Uint64 dpnId = dpnInterfaces.getDpId();
        String elanInstanceName = identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
        ElanInstance elanInstance = vpnUtil.getElanInstanceByName(elanInstanceName);
        if (!VpnUtil.isVlan(elanInstance)) {
            return;
        }
        String vpnName = vpnUtil.getVpnNameFromElanIntanceName(elanInstanceName);
        if (vpnName == null) {
            return;
        }
        vpnUtil.addRouterPortToElanForVlanInDpn(vpnName, dpnId);
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

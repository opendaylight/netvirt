/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes.NetworkType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetmapChangeListener extends AbstractAsyncDataTreeChangeListener<Subnetmap> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetmapChangeListener.class);
    private final DataBroker dataBroker;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private final VpnUtil vpnUtil;
    private final IVpnManager vpnManager;
    private final ManagedNewTransactionRunner txRunner;
    private final JobCoordinator jobCoordinator;

    @Inject
    public SubnetmapChangeListener(final DataBroker dataBroker, final VpnSubnetRouteHandler vpnSubnetRouteHandler,
                                   VpnUtil vpnUtil, IVpnManager vpnManager,JobCoordinator jobCoordinator) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Subnetmaps.class).child(Subnetmap.class),
                Executors.newListeningSingleThreadExecutor("SubnetmapChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
        this.vpnUtil = vpnUtil;
        this.vpnManager = vpnManager;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.jobCoordinator = jobCoordinator;
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
    public void add(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.debug("add: subnetmap method - key: {}, value: {}", identifier, subnetmap);
        Uuid subnetId = subnetmap.getId();
        Network network = vpnUtil.getNeutronNetwork(subnetmap.getNetworkId());
        if (network == null) {
            LOG.error("add: network was not found for subnetId {}", subnetId.getValue());
            return;
        }
        if (subnetmap.getVpnId() != null) {
            if (NetworkType.VLAN.equals(subnetmap.getNetworkType())) {
                vpnUtil.addRouterPortToElanDpnListForVlaninAllDpn(subnetmap.getVpnId().getValue());
            }
        }
        if (VpnUtil.getIsExternal(network)) {
            LOG.debug("SubnetmapListener:add: provider subnetwork {} is handling in "
                      + "ExternalSubnetVpnInstanceListener", subnetId.getValue());
            return;
        }
        jobCoordinator.enqueueJob("SUBNETROUTE-" + subnetId, () -> {
            String elanInstanceName = subnetmap.getNetworkId().getValue();
            long elanTag = getElanTag(elanInstanceName);
            if (elanTag == 0L) {
                LOG.error("add: unable to fetch elantag from ElanInstance {} for subnet {}",
                        elanInstanceName, subnetId.getValue());
                return Collections.emptyList();
            }
            Uuid vpnId = subnetmap.getVpnId();
            if (vpnId != null) {
                boolean isBgpVpn = !vpnId.equals(subnetmap.getRouterId());
                LOG.info("add: subnetmap {} with elanTag {} to VPN {}", subnetmap, elanTag,
                        vpnId);
                vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmap, isBgpVpn, elanTag);
                if (isBgpVpn && subnetmap.getRouterId() == null) {
                    Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue());
                    if (!routeTargets.isEmpty()) {
                        // FIXME: separate this out somehow?
                        final ReentrantLock lock = JvmGlobalLocks.getLockForString(subnetmap.getSubnetIp());
                        lock.lock();
                        try {
                            vpnManager.updateRouteTargetsToSubnetAssociation(routeTargets, subnetmap.getSubnetIp(),
                                    vpnId.getValue());
                        } finally {
                            lock.unlock();
                        }
                    }
                }
            }
            return Collections.emptyList();
        });
    }

    @Override
    public void remove(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("remove: subnetmap method - key: {}, value: {}", identifier, subnetmap);
        jobCoordinator.enqueueJob("SUBNETROUTE-" + subnetmap.getId(), () -> {
            java.util.Optional.ofNullable(subnetmap.getPortList()).ifPresent(portList ->
                    portList.forEach(port -> vpnSubnetRouteHandler.onPortRemovedFromSubnet(subnetmap, port)));
            return Collections.emptyList();
        });
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void update(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmapOriginal, Subnetmap
            subnetmapUpdate) {
        LOG.debug("update: method - key {}, original {}, update {}", identifier,
                  subnetmapOriginal, subnetmapUpdate);
        Uuid subnetId = subnetmapUpdate.getId();
        Network network = vpnUtil.getNeutronNetwork(subnetmapUpdate.getNetworkId());
        if (network == null) {
            LOG.error("update: network was not found for subnetId {}", subnetId.getValue());
            return;
        }
        jobCoordinator.enqueueJob("SUBNETROUTE-" + subnetId, () -> {
            List<ListenableFuture<Void>> futures = Collections.emptyList();
            String elanInstanceName = subnetmapUpdate.getNetworkId().getValue();
            long elanTag = getElanTag(elanInstanceName);
            if (elanTag == 0L) {
                LOG.error("update: unable to fetch elantag from ElanInstance {} for subnetId {}",
                        elanInstanceName, subnetId);
                return futures;
            }
            updateVlanDataEntry(subnetmapOriginal.getVpnId(), subnetmapUpdate.getVpnId(), subnetmapUpdate,
                    subnetmapOriginal, elanInstanceName);
            if (VpnUtil.getIsExternal(network)) {
                LOG.debug("update: provider subnetwork {} is handling in "
                        + "ExternalSubnetVpnInstanceListener", subnetId.getValue());
                return futures;
            }
            // update on BGPVPN or InternalVPN change
            Uuid vpnIdOld = subnetmapOriginal.getVpnId();
            Uuid vpnIdNew = subnetmapUpdate.getVpnId();
            if (!Objects.equals(vpnIdOld, vpnIdNew)) {
                LOG.info("update: update subnetOpDataEntry for subnet {} imported in VPN",
                        subnetmapUpdate.getId().getValue());
                updateSubnetmapOpDataEntry(subnetmapOriginal.getVpnId(), subnetmapUpdate.getVpnId(), subnetmapUpdate,
                        subnetmapOriginal, elanTag);
            }
            // update on Internet VPN Id change
            Uuid inetVpnIdOld = subnetmapOriginal.getInternetVpnId();
            Uuid inetVpnIdNew = subnetmapUpdate.getInternetVpnId();
            if (!Objects.equals(inetVpnIdOld, inetVpnIdNew)) {
                LOG.info("update: update subnetOpDataEntry for subnet {} imported in InternetVPN",
                        subnetmapUpdate.getId().getValue());
                updateSubnetmapOpDataEntry(inetVpnIdOld, inetVpnIdNew, subnetmapUpdate, subnetmapOriginal, elanTag);
            }
            // update on PortList change
            List<Uuid> oldPortList;
            List<Uuid> newPortList;
            newPortList = subnetmapUpdate.getPortList() != null ? subnetmapUpdate.getPortList() : new ArrayList<>();
            oldPortList = subnetmapOriginal.getPortList() != null ? subnetmapOriginal.getPortList() : new ArrayList<>();
            if (newPortList.size() == oldPortList.size()) {
                return futures;
            }
            LOG.info("update: update port list for subnet {}", subnetmapUpdate.getId().getValue());
            if (newPortList.size() > oldPortList.size()) {
                for (Uuid portId : newPortList) {
                    if (!oldPortList.contains(portId)) {
                        vpnSubnetRouteHandler.onPortAddedToSubnet(subnetmapUpdate, portId);
                    }
                }
            } else {
                for (Uuid portId : oldPortList) {
                    if (!newPortList.contains(portId)) {
                        vpnSubnetRouteHandler.onPortRemovedFromSubnet(subnetmapUpdate, portId);
                    }
                }
            }
            return futures;
        });
    }

    private void updateSubnetmapOpDataEntry(Uuid vpnIdOld, Uuid vpnIdNew, Subnetmap subnetmapUpdate,
                                    Subnetmap subnetmapOriginal, Long elanTag) {

        // subnet added to VPN
        if (vpnIdNew != null && vpnIdOld == null) {
            if (vpnIdNew.equals(subnetmapUpdate.getRouterId())) {
                return;
            }
            vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmapUpdate, true, elanTag);
        }
        // subnet removed from VPN
        if (vpnIdOld != null && vpnIdNew == null) {
            if (vpnIdOld.equals(subnetmapOriginal.getRouterId())) {
                return;
            }
            vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmapOriginal, true);
        }
        // subnet updated in VPN
        if (vpnIdOld != null && vpnIdNew != null && !vpnIdNew.equals(vpnIdOld)) {
            vpnSubnetRouteHandler.onSubnetUpdatedInVpn(subnetmapUpdate, elanTag);
        }
    }

    private void updateVlanDataEntry(Uuid vpnIdOld, Uuid vpnIdNew, Subnetmap subnetmapUpdate,
            Subnetmap subnetmapOriginal, String elanInstanceName) {
        if (vpnIdNew != null && vpnIdOld == null) {
            if (elanInstanceName != null && NetworkType.VLAN.equals(subnetmapUpdate.getNetworkType())) {
                vpnUtil.addRouterPortToElanDpnListForVlaninAllDpn(vpnIdNew.getValue());
            }
        }
        if (vpnIdOld != null && vpnIdNew == null) {
            if (NetworkType.VLAN.equals(subnetmapOriginal.getNetworkType())) {
                vpnUtil.removeRouterPortFromElanDpnListForVlanInAllDpn(elanInstanceName, subnetmapOriginal
                        .getRouterInterfacePortId().getValue(), vpnIdOld.getValue());
            }
        }
    }

    @SuppressWarnings("all")
    protected long getElanTag(String elanInstanceName) {
        final long[] elanTag = {0L};

        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
                    .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
            ElanInstance elanInstance = tx.read(LogicalDatastoreType.CONFIGURATION, elanIdentifierId)
                    .get().orElse(null);
            if (elanInstance != null) {
                if (elanInstance.getElanTag() != null) {
                    elanTag[0] =elanInstance.getElanTag().longValue();
                } else {
                    LOG.error("Notification failed because of failure in fetching elanTag for ElanInstance {}",
                            elanInstanceName);
                }
            } else {
                LOG.error("Notification failed because of failure in reading ELANInstance {}", elanInstanceName);
            }
        }), LOG, "Error binding an ELAN tag for elanInstance {}", elanInstanceName);

        return elanTag[0];
    }
}

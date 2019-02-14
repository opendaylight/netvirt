/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.eos.binding.api.Entity;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipCandidateRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LearntVpnVipToPortEventProcessor
        extends AsyncClusteredDataTreeChangeListenerBase<LearntVpnVipToPortEvent, LearntVpnVipToPortEventProcessor> {
    private static final Logger LOG = LoggerFactory.getLogger(LearntVpnVipToPortEventProcessor.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IInterfaceManager interfaceManager;
    public static final String MIP_PROCESSING_JOB  = "MIP-JOB";
    private final JobCoordinator jobCoordinator;
    private final EntityOwnershipUtils entityOwnershipUtils;
    private EntityOwnershipCandidateRegistration candidateRegistration;
    private final VpnUtil vpnUtil;

    @Inject
    public LearntVpnVipToPortEventProcessor(final DataBroker dataBroker, IInterfaceManager interfaceManager,
            EntityOwnershipService entityOwnershipService, final JobCoordinator jobCoordinator, VpnUtil vpnUtil) {
        super(LearntVpnVipToPortEvent.class, LearntVpnVipToPortEventProcessor.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interfaceManager = interfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);
        this.vpnUtil = vpnUtil;
    }

    @PostConstruct
    public void start() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
        try {
            candidateRegistration = entityOwnershipUtils.getEntityOwnershipService()
                    .registerCandidate(new Entity(VpnConstants.IP_MONITORING_ENTITY,
                            VpnConstants.IP_MONITORING_ENTITY));
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("Failed to register the entity {}", VpnConstants.IP_MONITORING_ENTITY);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        super.close();
        if (candidateRegistration != null) {
            candidateRegistration.close();
        }
    }

    @Override
    protected InstanceIdentifier<LearntVpnVipToPortEvent> getWildCardPath() {
        return InstanceIdentifier.create(LearntVpnVipToPortEventData.class).child(LearntVpnVipToPortEvent.class);
    }

    @Override
    protected LearntVpnVipToPortEventProcessor getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void update(InstanceIdentifier<LearntVpnVipToPortEvent> id, LearntVpnVipToPortEvent value,
            LearntVpnVipToPortEvent dataObjectModificationAfter) {
        // Updates does not make sense on an event queue .
        // NOTE: DONOT ADD ANY CODE HERE AND MAKE A CIRCUS
    }

    @Override
    protected void add(InstanceIdentifier<LearntVpnVipToPortEvent> identifier, LearntVpnVipToPortEvent value) {
        // AFTER PROCESSING THE EVENT, REMOVE THE EVENT FROM THE QUEUE
        entityOwnershipUtils.runOnlyInOwnerNode(VpnConstants.IP_MONITORING_ENTITY, VpnConstants.IP_MONITORING_ENTITY,
            jobCoordinator, "LearntVpnVipToPortEvent-Handler", () -> {
                try {
                    String vpnName = value.getVpnName();
                    String ipAddress = value.getSrcFixedip();
                    if (value.getEventAction() == LearntVpnVipToPortEventAction.Add) {
                        jobCoordinator.enqueueJob(VpnUtil.buildIpMonitorJobKey(ipAddress, vpnName),
                                new AddMipAdjacencyWorker(value));
                    }
                    if (value.getEventAction() == LearntVpnVipToPortEventAction.Delete) {
                        jobCoordinator.enqueueJob(VpnUtil.buildIpMonitorJobKey(ipAddress, vpnName),
                                new DeleteMipAdjacencyWorker(value));
                    }
                } finally {
                    // remove the processed event
                    vpnUtil.removeLearntVpnVipToPortEvent(value.getLearntVpnVipEventId(), null);
                }
            });
    }

    @Override
    protected void remove(InstanceIdentifier<LearntVpnVipToPortEvent> key, LearntVpnVipToPortEvent value) {
        // Removals are triggered by add handling.
        // NOTE: DONOT ADD ANY CODE HERE AND MAKE A CIRCUS
    }

    private class AddMipAdjacencyWorker implements Callable<List<ListenableFuture<Void>>> {
        String vpnName;
        String interfaceName;
        String srcIpAddress;
        String destIpAddress;
        String macAddress;

        AddMipAdjacencyWorker(LearntVpnVipToPortEvent event) {
            this.vpnName = event.getVpnName();
            this.interfaceName = event.getPortName();
            this.srcIpAddress = event.getSrcFixedip();
            this.destIpAddress = event.getDestFixedip();
            this.macAddress = event.getMacAddress();
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                                                                        Datastore.OPERATIONAL, operTx -> {
                    addMipAdjacency(vpnName, interfaceName, srcIpAddress, macAddress, destIpAddress);
                    vpnUtil.createVpnPortFixedIpToPort(vpnName, srcIpAddress,
                            interfaceName, Boolean.TRUE, macAddress, null);
                    vpnUtil.createLearntVpnVipToPort(vpnName, srcIpAddress, interfaceName, macAddress, operTx);
                }));
        }

        private void addMipAdjacency(String vpnInstName, String vpnInterface, String srcPrefix, String mipMacAddress,
                                     String dstPrefix) {
            LOG.trace("Adding {} adjacency to VPN Interface {} ", srcPrefix, vpnInterface);
            InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
            try {
                synchronized (vpnInterface.intern()) {
                    Optional<VpnInterface> optVpnInterface = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.CONFIGURATION, vpnIfId);
                    if (!optVpnInterface.isPresent()) {
                        LOG.error("Config VpnInterface not found for interface={}", interfaceName);
                        return;
                    }
                    Adjacencies configAdjacencies = optVpnInterface.get().augmentation(Adjacencies.class);
                    List<Adjacency> adjacencyList =
                            configAdjacencies == null ? Lists.newArrayList() : configAdjacencies.getAdjacency();

                    String ip = VpnUtil.getIpPrefix(srcPrefix);
                    AdjacencyBuilder newAdjBuilder;
                    if (interfaceManager.isExternalInterface(vpnInterface)) {
                        String subnetId = getSubnetId(vpnInstName, dstPrefix);
                        if (subnetId == null) {
                            LOG.trace("Can't find corresponding subnet for src IP {}, src MAC {}, dst IP {},"
                                    + "  in VPN {}", srcPrefix, mipMacAddress, dstPrefix, vpnInstName);
                            return;
                        }
                        newAdjBuilder = new AdjacencyBuilder().setIpAddress(ip).withKey(new AdjacencyKey(ip))
                                .setAdjacencyType(AdjacencyType.PrimaryAdjacency).setMacAddress(mipMacAddress)
                                .setSubnetId(new Uuid(subnetId)).setPhysNetworkFunc(true);
                    } else {
                        String nextHopIp = null;
                        String nextHopMacAddress = null;
                        for (Adjacency adjacency : adjacencyList) {
                            if (adjacency.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                                if (adjacency.getIpAddress().equals(ip)) {
                                    LOG.error("The MIP {} is already present as a primary adjacency for interface {}."
                                            + "Skipping adjacency addition.", ip, interfaceName);
                                    return;
                                } else if (NWUtil.getEtherTypeFromIpPrefix(ip) == NWUtil
                                        .getEtherTypeFromIpPrefix(adjacency.getIpAddress())) {
                                    nextHopIp = adjacency.getIpAddress().split("/")[0];
                                    nextHopMacAddress = adjacency.getMacAddress();
                                    break;
                                }
                            }
                        }
                        if (nextHopIp == null) {
                            LOG.error("Next Hop IP not found for MIP={}, interface={}, vpnName {}. Skipping adjacency "
                                    + "addition.", ip, interfaceName, vpnName);
                            return;
                        }

                        String rd = vpnUtil.getVpnRd(vpnInstName);
                        long label = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME,
                                VpnUtil.getNextHopLabelKey(rd != null ? rd : vpnInstName, ip));
                        if (label == 0) {
                            LOG.error("Unable to fetch label from Id Manager. Bailing out of adding MIP adjacency {}"
                                    + " to vpn interface {} for vpn {}", ip, vpnInterface, vpnInstName);
                            return;
                        }
                        newAdjBuilder = new AdjacencyBuilder().setIpAddress(ip).withKey(new AdjacencyKey(ip))
                                .setNextHopIpList(Collections.singletonList(nextHopIp))
                                .setAdjacencyType(AdjacencyType.LearntIp);
                        if (mipMacAddress != null && !mipMacAddress.equalsIgnoreCase(nextHopMacAddress)) {
                            newAdjBuilder.setMacAddress(mipMacAddress);
                        }
                    }
                    adjacencyList.add(newAdjBuilder.build());
                    Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencyList);
                    VpnInterface newVpnIntf = new VpnInterfaceBuilder(optVpnInterface.get())
                            .addAugmentation(Adjacencies.class, aug).build();
                    SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIfId,
                            newVpnIntf, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
                    LOG.debug(" Successfully stored subnetroute Adjacency into VpnInterface {}", vpnInterface);
                }
            } catch (ReadFailedException e) {
                LOG.error("addMipAdjacency: Failed to read data store for interface {} vpn {} ip {} mac {}",
                        vpnInterface, vpnInstName, srcPrefix, mipMacAddress);
            } catch (TransactionCommitFailedException e) {
                LOG.error("addMipAdjacency: Failed to commit to data store for interface {} vpn {} ip {} mac {}",
                        vpnInterface, vpnInstName, srcPrefix, mipMacAddress);
            }
        }

        @Nullable
        private String getSubnetId(String vpnInstName, String ip) {
            // Check if this IP belongs to a router_interface
            VpnPortipToPort vpnPortipToPort =
                    vpnUtil.getNeutronPortFromVpnPortFixedIp(vpnInstName, ip);
            if (vpnPortipToPort != null && vpnPortipToPort.isSubnetIp()) {
                List<Adjacency> adjacencies =
                    vpnUtil.getAdjacenciesForVpnInterfaceFromConfig(vpnPortipToPort.getPortName());
                if (adjacencies != null) {
                    for (Adjacency adjacency : adjacencies) {
                        if (adjacency.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                            return adjacency.getSubnetId().getValue();
                        }
                    }
                }
            }

            // Check if this IP belongs to a router_gateway
            List<Uuid> routerIds = vpnUtil.getExternalNetworkRouterIds(new Uuid(vpnInstName));
            for (Uuid routerId : routerIds) {
                Uuid subnetId = vpnUtil.getSubnetFromExternalRouterByIp(routerId, ip);
                if (subnetId != null) {
                    return subnetId.getValue();
                }
            }

            // Check if this IP belongs to  external network
            String extSubnetId = vpnUtil.getAssociatedExternalSubnet(ip);
            if (extSubnetId != null) {
                LOG.info("The IP belongs to extenal subnet {} ", extSubnetId);
                return extSubnetId;
            }

            return null;
        }
    }

    private class DeleteMipAdjacencyWorker implements Callable<List<ListenableFuture<Void>>> {
        String vpnName;
        String interfaceName;
        String ipAddress;

        DeleteMipAdjacencyWorker(LearntVpnVipToPortEvent event) {
            this.vpnName = event.getVpnName();
            this.interfaceName = event.getPortName();
            this.ipAddress = event.getSrcFixedip();
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            vpnUtil.removeMipAdjAndLearntIp(vpnName, interfaceName,  ipAddress);
            return futures;
        }

    }

}


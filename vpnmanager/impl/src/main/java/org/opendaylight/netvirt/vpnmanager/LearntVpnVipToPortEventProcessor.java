/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.arputil.api.ArpConstants;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.eos.binding.api.Entity;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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


public class LearntVpnVipToPortEventProcessor
        extends AsyncClusteredDataTreeChangeListenerBase<LearntVpnVipToPortEvent, LearntVpnVipToPortEventProcessor> {
    private static final Logger LOG = LoggerFactory.getLogger(LearntVpnVipToPortEventProcessor.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService interfaceRpc;
    private final IMdsalApiManager mdsalManager;
    private final AlivenessMonitorService alivenessManager;
    private final INeutronVpnManager neutronVpnService;
    private final IInterfaceManager interfaceManager;
    private final IdManagerService idManager;
    public static final String MIP_PROCESSING_JOB  = "MIP-JOB";
    private final JobCoordinator jobCoordinator;
    private final EntityOwnershipService entityOwnershipService;
    private final EntityOwnershipUtils entityOwnershipUtils;

    public LearntVpnVipToPortEventProcessor(final DataBroker dataBroker, final OdlInterfaceRpcService interfaceRpc,
                                            IMdsalApiManager mdsalManager, AlivenessMonitorService alivenessManager,
                                            INeutronVpnManager neutronVpnService, IInterfaceManager interfaceManager,
                                            EntityOwnershipService entityOwnershipService,
                                            IdManagerService idManagerService, final JobCoordinator jobCoordinator) {
        super(LearntVpnVipToPortEvent.class, LearntVpnVipToPortEventProcessor.class);
        this.dataBroker = dataBroker;
        this.interfaceRpc = interfaceRpc;
        this.mdsalManager = mdsalManager;
        this.alivenessManager = alivenessManager;
        this.neutronVpnService = neutronVpnService;
        this.interfaceManager = interfaceManager;
        this.idManager = idManagerService;
        this.jobCoordinator = jobCoordinator;
        this.entityOwnershipService = entityOwnershipService;
        this.entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);
    }

    public void start() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
        try {
            entityOwnershipService.registerCandidate(
                    new Entity(VpnConstants.ARP_MONITORING_ENTITY, VpnConstants.ARP_MONITORING_ENTITY));
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("LearntVpnVipToPortEventProcessor: failed to register the entity "
                    + VpnConstants.ARP_MONITORING_ENTITY);
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
        entityOwnershipUtils.runOnlyInOwnerNode(VpnConstants.ARP_MONITORING_ENTITY,
                VpnConstants.ARP_MONITORING_ENTITY, jobCoordinator, "LearntVpnVipToPortEvent-Handler", () -> {
                try {
                    String vpnName = value.getVpnName();
                    String ipAddress = value.getSrcFixedip();
                    if (value.getEventAction() == LearntVpnVipToPortEventAction.Add) {
                        jobCoordinator.enqueueJob(buildJobKey(ipAddress, vpnName), new AddMipAdjacencyWorker(value));
                    }
                    if (value.getEventAction() == LearntVpnVipToPortEventAction.Delete) {
                        jobCoordinator.enqueueJob(buildJobKey(ipAddress, vpnName), new DeleteMipAdjacencyWorker(value));
                    }
                } finally {
                    // remove the processed event
                    VpnUtil.removeLearntVpnVipToPortEvent(dataBroker, value.getLearntVpnVipEventId(), null);
                }
            });
    }

    @Override
    protected void remove(InstanceIdentifier<LearntVpnVipToPortEvent> key, LearntVpnVipToPortEvent value) {
        // Removals are triggered by add handling.
        // NOTE: DONOT ADD ANY CODE HERE AND MAKE A CIRCUS
    }

    static String buildJobKey(String ip, String vpnName) {
        return new StringBuilder(ArpConstants.ARPJOB).append('-').append(vpnName).append('-').append(ip).toString();
    }

    private class AddMipAdjacencyWorker implements Callable<List<ListenableFuture<Void>>> {
        String vpnName;
        String interfaceName;
        String srcIpAddress;
        String destIpAddress;
        String macAddress;
        LearntVpnVipToPortEvent event;

        AddMipAdjacencyWorker(LearntVpnVipToPortEvent event) {
            this.vpnName = event.getVpnName();
            this.interfaceName = event.getPortName();
            this.srcIpAddress = event.getSrcFixedip();
            this.destIpAddress = event.getDestFixedip();
            this.macAddress = event.getMacAddress();
            this.event = event;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
            addMipAdjacency(vpnName, interfaceName,
                    srcIpAddress, macAddress, destIpAddress);
            VpnUtil.createLearntVpnVipToPort(dataBroker, vpnName, srcIpAddress,
                    interfaceName, macAddress, writeOperTxn);
            futures.add(writeConfigTxn.submit());
            futures.add(writeOperTxn.submit());
            return futures;
        }

        private void addMipAdjacency(String vpnInstName, String vpnInterface, String srcPrefix, String mipMacAddress,
                                     String dstPrefix) {
            LOG.trace("Adding {} adjacency to VPN Interface {} ", srcPrefix, vpnInterface);
            InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
            InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
            synchronized (vpnInterface.intern()) {
                Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
                String nextHopIpAddr = null;
                String nextHopMacAddress = null;
                String ip = srcPrefix;
                if (interfaceManager.isExternalInterface(vpnInterface)) {
                    String subnetId = getSubnetId(vpnInstName, dstPrefix);
                    if (subnetId == null) {
                        LOG.trace("Can't find corresponding subnet for src IP {}, src MAC {}, dst IP {},  in VPN {}",
                                srcPrefix, mipMacAddress, dstPrefix, vpnInstName);
                        return;
                    }
                    ip = VpnUtil.getIpPrefix(ip);
                    AdjacencyBuilder newAdjBuilder = new AdjacencyBuilder().setIpAddress(ip)
                            .setKey(new AdjacencyKey(ip)).setAdjacencyType(AdjacencyType.PrimaryAdjacency)
                            .setMacAddress(mipMacAddress).setSubnetId(new Uuid(subnetId)).setPhysNetworkFunc(true);

                    List<Adjacency> adjacencyList = adjacencies.isPresent()
                            ? adjacencies.get().getAdjacency() : new ArrayList<>();

                    adjacencyList.add(newAdjBuilder.build());

                    Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencyList);
                    Optional<VpnInterface> optionalVpnInterface =
                            VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIfId);
                    VpnInterface newVpnIntf;
                    if (optionalVpnInterface.isPresent()) {
                        newVpnIntf =
                                new VpnInterfaceBuilder(optionalVpnInterface.get())
                                        .addAugmentation(Adjacencies.class, aug)
                                        .build();
                        VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIfId, newVpnIntf);
                    }
                    LOG.debug(" Successfully stored subnetroute Adjacency into VpnInterface {}", vpnInterface);
                    return;
                }

                if (adjacencies.isPresent()) {
                    List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
                    ip = VpnUtil.getIpPrefix(ip);
                    for (Adjacency adjacs : adjacencyList) {
                        if (adjacs.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                            if (adjacs.getIpAddress().equals(ip)) {
                                LOG.error("The MIP {} is already present as a primary adjacency for interface {}"
                                        + "vpn {} Skipping adjacency addition.", ip, vpnInterface, vpnInstName);
                                return;
                            }
                            nextHopIpAddr = adjacs.getIpAddress();
                            nextHopMacAddress = adjacs.getMacAddress();
                            break;
                        }
                    }
                    if (nextHopIpAddr != null) {
                        String rd = VpnUtil.getVpnRd(dataBroker, vpnInstName);
                        long label =
                                VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                        VpnUtil.getNextHopLabelKey(rd != null ? rd : vpnInstName, ip));
                        if (label == 0) {
                            LOG.error("Unable to fetch label from Id Manager. Bailing out of adding MIP adjacency {} "
                                    + "to vpn interface {} for vpn {}", ip, vpnInterface, vpnInstName);
                            return;
                        }
                        String nextHopIp = nextHopIpAddr.split("/")[0];
                        AdjacencyBuilder newAdjBuilder =
                                new AdjacencyBuilder().setIpAddress(ip).setKey(new AdjacencyKey(ip)).setNextHopIpList(
                                        Collections.singletonList(nextHopIp)).setAdjacencyType(AdjacencyType.LearntIp);
                        if (mipMacAddress != null && !mipMacAddress.equalsIgnoreCase(nextHopMacAddress)) {
                            newAdjBuilder.setMacAddress(mipMacAddress);
                        }
                        adjacencyList.add(newAdjBuilder.build());
                        Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencyList);
                        Optional<VpnInterface> optionalVpnInterface =
                                VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIfId);
                        VpnInterface newVpnIntf;
                        if (optionalVpnInterface.isPresent()) {
                            newVpnIntf =
                                    new VpnInterfaceBuilder(optionalVpnInterface.get())
                                            .addAugmentation(Adjacencies.class, aug).build();
                            VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                    vpnIfId, newVpnIntf);
                        }
                        LOG.debug(" Successfully stored subnetroute Adjacency into VpnInterface {}", vpnInterface);
                    }
                }
            }
        }

        private String getSubnetId(String vpnInstName, String ip) {
            // Check if this IP belongs to a router_interface
            VpnPortipToPort vpnPortipToPort =
                    VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnInstName, ip);
            if (vpnPortipToPort != null && vpnPortipToPort.isSubnetIp()) {
                List<Adjacency> adjacecnyList = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker,
                        vpnPortipToPort.getPortName());
                for (Adjacency adjacency : adjacecnyList) {
                    if (adjacency.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                        return adjacency.getSubnetId().getValue();
                    }
                }
            }

            // Check if this IP belongs to a router_gateway
            List<Uuid> routerIds = VpnUtil.getExternalNetworkRouterIds(dataBroker, new Uuid(vpnInstName));
            for (Uuid routerId : routerIds) {
                Uuid subnetId = VpnUtil.getSubnetFromExternalRouterByIp(dataBroker, routerId, ip);
                if (subnetId != null) {
                    return subnetId.getValue();
                }
            }

            return null;
        }
    }

    private class DeleteMipAdjacencyWorker implements Callable<List<ListenableFuture<Void>>> {
        String vpnName;
        String interfaceName;
        String ipAddress;
        LearntVpnVipToPortEvent event;

        DeleteMipAdjacencyWorker(LearntVpnVipToPortEvent event) {
            this.vpnName = event.getVpnName();
            this.interfaceName = event.getPortName();
            this.ipAddress = event.getSrcFixedip();
            this.event = event;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            VpnUtil.removeMipAdjAndLearntIp(dataBroker, vpnName, interfaceName,  ipAddress);
            return futures;
        }

    }

}


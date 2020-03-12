/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.base.Optional;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.IntextIpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalNetworksChangeListener
        extends AsyncDataTreeChangeListenerBase<Networks, ExternalNetworksChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalNetworksChangeListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final FloatingIPListener floatingIpListener;
    private final ExternalRoutersListener externalRouterListener;
    private final OdlInterfaceRpcService interfaceManager;
    private final JobCoordinator coordinator;
    private final NatMode natMode;
    private final Map<String, ConcurrentLinkedQueue<PendingExternalNetworks>>
            pendingExternalNetworks = new ConcurrentHashMap<>();

    @Inject
    public ExternalNetworksChangeListener(final DataBroker dataBroker, final FloatingIPListener floatingIpListener,
                                          final ExternalRoutersListener externalRouterListener,
                                          final OdlInterfaceRpcService interfaceManager,
                                          final NatserviceConfig config,
                                          final JobCoordinator coordinator) {
        super(Networks.class, ExternalNetworksChangeListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.floatingIpListener = floatingIpListener;
        this.externalRouterListener = externalRouterListener;
        this.interfaceManager = interfaceManager;
        this.coordinator = coordinator;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatMode.Controller;
        }
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Networks> getWildCardPath() {
        return InstanceIdentifier.create(ExternalNetworks.class).child(Networks.class);
    }

    @Override
    protected void add(InstanceIdentifier<Networks> identifier, Networks networks) {

    }

    @Override
    protected ExternalNetworksChangeListener getDataTreeChangeListener() {
        return ExternalNetworksChangeListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<Networks> identifier, Networks networks) {
        if (identifier == null || networks == null || networks.getRouterIds() == null
                || networks.getRouterIds().isEmpty()) {
            LOG.warn("remove : returning without processing since networks/identifier is null: "
                + "identifier: {}, networks: {}", identifier, networks);
            return;
        }

        for (Uuid routerId: networks.getRouterIds()) {
            String routerName = routerId.toString();

            InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitchInstanceIdentifier =
                    NatUtil.buildNaptSwitchIdentifier(routerName);

            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, routerToNaptSwitchInstanceIdentifier);

            LOG.debug("remove : successful deletion of data in napt-switches container");
        }
        if (networks.getVpnid() != null) {
            removePendingExternalNetworks(networks.getVpnid().getValue(), networks);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Networks> identifier, Networks original, Networks update) {
        //Check for VPN disassociation
        Uuid originalVpn = original.getVpnid();
        Uuid updatedVpn = update.getVpnid();
        if (originalVpn == null && updatedVpn != null) {
            VpnInstance vpnInstance = NatUtil.getVpnIdToVpnInstance(dataBroker, updatedVpn.getValue());
            if (vpnInstance == null || vpnInstance.getVpnId() == null) {
                LOG.error("createNATFlowEntries: VPN ID Not Available for VpnInstance {}. Adding to "
                        + "UnprocessedUpdatedExternalnetwork queue", updatedVpn);
                addToPendingExternalNetworks(identifier, update);
                return;
            }
            //external network is dis-associated from L3VPN instance
            associateExternalNetworkWithVPN(update);
        } else if (originalVpn != null && updatedVpn == null) {
            removePendingExternalNetworks(originalVpn.getValue(), original);
            //external network is associated with vpn
            disassociateExternalNetworkFromVPN(update, originalVpn.getValue());
            //Remove the SNAT entries
            removeSnatEntries(original, original.getId());
        }
    }

    private void removeSnatEntries(Networks original, Uuid networkUuid) {
        if (original.getRouterIds() != null) {
            for (Uuid routerUuid : original.getRouterIds()) {
                Uint32 routerId = NatUtil.getVpnId(dataBroker, routerUuid.getValue());
                if (routerId == NatConstants.INVALID_ID) {
                    LOG.error("removeSnatEntries : Invalid routerId returned for routerName {}", routerUuid.getValue());
                    return;
                }
                Collection<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker,routerId);
                if (natMode == NatMode.Controller) {
                    coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + routerUuid.getValue(),
                        () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                            tx -> externalRouterListener.handleDisableSnatInternetVpn(routerUuid.getValue(), routerId,
                                networkUuid, externalIps, original.getVpnid().getValue(), tx))),
                        NatConstants.NAT_DJC_MAX_RETRIES);
                }
            }
        }
    }

    private void associateExternalNetworkWithVPN(Networks network) {
        if (network.getRouterIds() != null) {
            List<Uuid> routerIds = network.getRouterIds();
            for (Uuid routerId : routerIds) {
                //long router = NatUtil.getVpnId(dataBroker, routerId.getValue());

                InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerId.getValue());
                Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    routerPortsId);
                if (!optRouterPorts.isPresent()) {
                    LOG.debug("associateExternalNetworkWithVPN : Could not read Router Ports data object with id: {} "
                        + "to handle associate ext nw {}", routerId, network.getId());
                    continue;
                }
                RouterPorts routerPorts = optRouterPorts.get();
                for (Ports port : routerPorts.nonnullPorts()) {
                    String portName = port.getPortName();
                    Uint64 dpnId = NatUtil.getDpnForInterface(interfaceManager, portName);
                    if (dpnId.equals(Uint64.ZERO)) {
                        LOG.debug("associateExternalNetworkWithVPN : DPN not found for {}, "
                            + "skip handling of ext nw {} association", portName, network.getId());
                        continue;
                    }
                    for (InternalToExternalPortMap ipMap : port.nonnullInternalToExternalPortMap()) {
                        // remove all VPN related entries
                        coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + ipMap.key(),
                            () -> Collections.singletonList(
                                txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                                    tx -> floatingIpListener.createNATFlowEntries(dpnId, portName, routerId.getValue(),
                                        network.getId(), ipMap, tx))), NatConstants.NAT_DJC_MAX_RETRIES);
                    }
                }
            }

            // SNAT
            for (Uuid routerId : routerIds) {
                LOG.debug("associateExternalNetworkWithVPN() : for routerId {}", routerId);
                Uuid networkId = network.getId();
                if (networkId == null) {
                    LOG.error("associateExternalNetworkWithVPN : networkId is null for the router ID {}", routerId);
                    return;
                }
                final String vpnName = network.getVpnid().getValue();
                if (vpnName == null) {
                    LOG.error("associateExternalNetworkWithVPN : No VPN associated with ext nw {} for router {}",
                        networkId, routerId);
                    return;
                }

                Uint64 dpnId = Uint64.valueOf("0");
                InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitch =
                    NatUtil.buildNaptSwitchRouterIdentifier(routerId.getValue());
                Optional<RouterToNaptSwitch> rtrToNapt =
                    MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerToNaptSwitch);
                if (rtrToNapt.isPresent()) {
                    dpnId = rtrToNapt.get().getPrimarySwitchId();
                }
                LOG.debug("associateExternalNetworkWithVPN : got primarySwitch as dpnId{} ", dpnId);
                if (dpnId == null || dpnId.equals(Uint64.ZERO)) {
                    LOG.warn("associateExternalNetworkWithVPN : primary napt Switch not found for router {} on dpn: {}",
                        routerId, dpnId);
                    return;
                }
                final Uint64 finalDpnId = dpnId;
                coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + routerId.getValue(),
                    () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                        confTx -> {
                            Uint32 routerIdentifier = NatUtil.getVpnId(dataBroker, routerId.getValue());
                            InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
                                .rev160111.intext.ip.map.IpMapping> idBuilder =
                                InstanceIdentifier.builder(IntextIpMap.class)
                                    .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
                                            .intext.ip.map.IpMapping.class,
                                        new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
                                            .intext.ip.map.IpMappingKey(routerIdentifier));
                            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
                                .rev160111.intext.ip.map.IpMapping> id = idBuilder.build();
                            Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
                                .intext.ip.map.IpMapping> ipMapping = MDSALUtil.read(dataBroker,
                                LogicalDatastoreType.OPERATIONAL, id);
                            if (ipMapping.isPresent()) {
                                for (IpMap ipMap : ipMapping.get().nonnullIpMap()) {
                                    String externalIp = ipMap.getExternalIp();
                                    LOG.debug(
                                        "associateExternalNetworkWithVPN : Calling advToBgpAndInstallFibAndTsFlows "
                                            + "for dpnId {},vpnName {} and externalIp {}", finalDpnId, vpnName,
                                        externalIp);
                                    if (natMode == NatMode.Controller) {
                                        externalRouterListener.advToBgpAndInstallFibAndTsFlows(finalDpnId,
                                            NwConstants.INBOUND_NAPT_TABLE, vpnName, routerIdentifier,
                                            routerId.getValue(), externalIp, network.getId(),
                                            null /* external-router */, confTx);
                                    }
                                }
                            } else {
                                LOG.warn("associateExternalNetworkWithVPN: No ipMapping present fot the routerId {}",
                                    routerId);
                            }

                            Uint32 vpnId = NatUtil.getVpnId(dataBroker, vpnName);
                            // Install 47 entry to point to 21
                            if (natMode == NatMode.Controller) {
                                externalRouterListener.installNaptPfibEntriesForExternalSubnets(routerId.getValue(),
                                    finalDpnId, confTx);
                                if (vpnId.longValue() != -1) {
                                    LOG.debug("associateExternalNetworkWithVPN : Calling externalRouterListener "
                                        + "installNaptPfibEntry for dpnId {} and vpnId {}", finalDpnId, vpnId);
                                    externalRouterListener.installNaptPfibEntry(finalDpnId, vpnId, confTx);
                                }
                            }
                        })), NatConstants.NAT_DJC_MAX_RETRIES);
            }
        }
    }

    private void disassociateExternalNetworkFromVPN(Networks network, String vpnName) {
        if (network.getRouterIds() != null) {
            for (Uuid routerId : network.getRouterIds()) {
                InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerId.getValue());
                Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    routerPortsId);
                if (!optRouterPorts.isPresent()) {
                    LOG.debug(
                        "disassociateExternalNetworkFromVPN : Could not read Router Ports data object with id: {} "
                            + "to handle disassociate ext nw {}", routerId, network.getId());
                    continue;
                }
                RouterPorts routerPorts = optRouterPorts.get();
                for (Ports port : routerPorts.nonnullPorts()) {
                    String portName = port.getPortName();
                    Uint64 dpnId = NatUtil.getDpnForInterface(interfaceManager, portName);
                    if (dpnId.equals(Uint64.ZERO)) {
                        LOG.debug("disassociateExternalNetworkFromVPN : DPN not found for {},"
                            + "skip handling of ext nw {} disassociation", portName, network.getId());
                        continue;
                    }
                    for (InternalToExternalPortMap intExtPortMap : port.nonnullInternalToExternalPortMap()) {
                        coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + intExtPortMap.key(),
                            () -> Collections.singletonList(
                                txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                                    tx -> floatingIpListener.removeNATFlowEntries(dpnId, portName, vpnName,
                                        routerId.getValue(),
                                        intExtPortMap, tx))), NatConstants.NAT_DJC_MAX_RETRIES);
                    }
                }
            }
        }
    }

    private void addToPendingExternalNetworks(InstanceIdentifier<Networks> identifier, Networks network) {
        synchronized (network.getVpnid().getValue().intern()) {
            ConcurrentLinkedQueue<PendingExternalNetworks> unProcessedExternalNetwork
                    = pendingExternalNetworks.get(network.getVpnid().getValue());
            if (unProcessedExternalNetwork == null) {
                unProcessedExternalNetwork = new ConcurrentLinkedQueue<>();
            }
            unProcessedExternalNetwork.add(new PendingExternalNetworks(identifier, network));
            pendingExternalNetworks.put(network.getVpnid().getValue(), unProcessedExternalNetwork);
            LOG.debug("addToPendingExternalNetworks: Saved unhandled external network {} for vpn Instance {} ",
                    network, network.getVpnid().getValue());
        }
    }

    public void removePendingExternalNetworks(String vpnInstanceName, Networks network) {
        synchronized (vpnInstanceName.intern()) {
            if (network != null) {
                if (pendingExternalNetworks.get(vpnInstanceName) != null) {
                    pendingExternalNetworks.get(vpnInstanceName).remove(network);
                }
            } else {
                pendingExternalNetworks.remove(vpnInstanceName);
            }
        }
    }


    public void processPendingExternalNetworks(String vpnInstanceName) {
        synchronized (vpnInstanceName.intern()) {
            ConcurrentLinkedQueue<PendingExternalNetworks> externalNetworkData =
                    pendingExternalNetworks.get(vpnInstanceName);
            if (externalNetworkData != null) {
                while (!externalNetworkData.isEmpty()) {
                    PendingExternalNetworks savedInstance = externalNetworkData.poll();
                    String currVpnId = savedInstance.getNetwork().getVpnid().getValue();
                    if (!currVpnId.equals(vpnInstanceName)) {
                        continue;
                    }
                    associateExternalNetworkWithVPN(savedInstance.getNetwork());
                    LOG.debug("processPendingExternalNetworks: Handled PendingExternalNetworks {} for VPN {}",
                            savedInstance.getNetwork(), vpnInstanceName);
                }
            } else {
                LOG.debug("processPendingExternalNetworks: No ExternalNetworks in queue for VPN {}",
                        vpnInstanceName);
            }
        }
    }

    private static class PendingExternalNetworks {
        private final InstanceIdentifier<Networks> identifier;
        private final Networks network;

        PendingExternalNetworks(InstanceIdentifier<Networks> identifier, Networks network) {
            this.identifier = identifier;
            this.network = network;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            PendingExternalNetworks other = (PendingExternalNetworks) obj;
            if (identifier == null) {
                if (other.identifier != null) {
                    return false;
                }
            } else if (!identifier.equals(other.identifier)) {
                return false;
            }
            if (network == null) {
                if (other.network != null) {
                    return false;
                }
            } else if ((other.network == null) || !(network.getId().equals(other.network.getId()))) {
                return false;
            }
            return true;
        }

        public InstanceIdentifier<Networks> getIdentifier() {
            return identifier;
        }

        public Networks getNetwork() {
            return network;
        }
    }
}

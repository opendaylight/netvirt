/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronSubnetChangeListener extends AbstractAsyncDataTreeChangeListener<Subnet> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSubnetChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronExternalSubnetHandler externalSubnetHandler;
    private final NeutronvpnUtils neutronvpnUtils;
    private final IVpnManager vpnManager;

    @Inject
    public NeutronSubnetChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
            final NeutronExternalSubnetHandler externalSubnetHandler, final NeutronvpnUtils neutronvpnUtils,
                                       final IVpnManager vpnManager) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(Subnets.class).child(Subnet.class),
                Executors.newSingleThreadExecutor("NeutronSubnetChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.nvpnManager = neutronvpnManager;
        this.externalSubnetHandler = externalSubnetHandler;
        this.neutronvpnUtils = neutronvpnUtils;
        this.vpnManager = vpnManager;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void add(InstanceIdentifier<Subnet> identifier, Subnet input) {
        LOG.trace("Adding Subnet : key: {}, value={}", identifier, input);
        Uuid networkId = input.getNetworkId();
        Uuid subnetId = input.getUuid();
        Network network = neutronvpnUtils.getNeutronNetwork(networkId);
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.warn("neutron vpn received a subnet add() for a network without a provider extension augmentation "
                            + "or with an unsupported network type for the subnet {} which is part of network {}",
                    subnetId.getValue(), network);
            return;
        }
        neutronvpnUtils.addToSubnetCache(input);
        handleNeutronSubnetCreated(input, network);
        externalSubnetHandler.handleExternalSubnetAdded(network, subnetId, null);
    }

    @Override
    public void remove(InstanceIdentifier<Subnet> identifier, Subnet input) {
        LOG.trace("Removing subnet : key: {}, value={}", identifier, input);
        Uuid networkId = input.getNetworkId();
        Uuid subnetId = input.getUuid();
        Network network = neutronvpnUtils.getNeutronNetwork(networkId);
        handleNeutronSubnetDeleted(subnetId, networkId, input.getCidr().stringValue());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.warn("neutron vpn received a subnet remove() for a network without a provider extension augmentation "
                            + "or with an unsupported network type for the subnet {} which is part of network {}",
                    subnetId.getValue(), network);
        } else {
            externalSubnetHandler.handleExternalSubnetRemoved(network, subnetId);
        }
        neutronvpnUtils.removeFromSubnetCache(input);
    }

    @Override
    public void update(InstanceIdentifier<Subnet> identifier, Subnet original, Subnet update) {
        LOG.trace("Updating Subnet : key: {}, original value={}, update value={}", identifier, original, update);
        if (Objects.equals(original, update)) {
            return;
        }
        neutronvpnUtils.addToSubnetCache(update);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleNeutronSubnetCreated(Subnet subnet, Network network) {
        Uuid networkId = network.getUuid();
        Uuid subnetId = subnet.getUuid();
        ProviderTypes providerType = NeutronvpnUtils.getProviderNetworkType(network);
        String segmentationId = NeutronvpnUtils.getSegmentationIdFromNeutronNetwork(network);
        boolean isExternalNetwork = NeutronvpnUtils.getIsExternal(network);
        try {
            nvpnManager.createSubnetmapNode(subnetId, subnet.getCidr().stringValue(), subnet.getTenantId(), networkId,
                    providerType != null ? NetworkAttributes.NetworkType.valueOf(providerType.getName()) : null,
                    segmentationId != null ? Long.parseLong(segmentationId) : 0L, isExternalNetwork);
            createSubnetToNetworkMapping(subnetId, networkId);
        } catch (Exception e) {
            LOG.error("handleNeutronSubnetCreated: Failed for subnet {} with subnet IP {} and networkId {}",
                    subnetId.getValue(), subnet.getCidr().stringValue(), networkId.getValue(), e);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleNeutronSubnetDeleted(Uuid subnetId, Uuid networkId, String subnetCidr) {
        try {
            Uuid vpnId = neutronvpnUtils.getVpnForNetwork(networkId);
            if (vpnId != null) {
                Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue());
                LOG.warn("Subnet {} deleted without disassociating network {} from VPN {}. Ideally, please "
                                + "disassociate network from VPN before deleting neutron subnet.",
                        subnetId.getValue(), networkId.getValue(), vpnId.getValue());
                Subnetmap subnetmap = neutronvpnUtils.getSubnetmap(subnetId);
                if (subnetmap != null) {
                    nvpnManager.removeSubnetFromVpn(vpnId, subnetmap, null /* internet-vpn-id */);
                } else {
                    LOG.error("Subnetmap for subnet {} not found", subnetId.getValue());
                }
                if (!routeTargets.isEmpty()) {
                    vpnManager.removeRouteTargetsToSubnetAssociation(routeTargets, subnetCidr, vpnId.getValue());
                }
            }
            if (networkId != null) {
                deleteSubnetToNetworkMapping(subnetId, networkId);
            }
            nvpnManager.deleteSubnetMapNode(subnetId);
        } catch (Exception e) {
            LOG.error("handleNeutronSubnetDeleted: Failed for subnet {} with subnet IP {} and networkId {}",
                    subnetId.getValue(), subnetCidr, networkId.getValue(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void createSubnetToNetworkMapping(Uuid subnetId, Uuid networkId) {
        try {
            InstanceIdentifier<NetworkMap>  networkMapIdentifier = NeutronvpnUtils.buildNetworkMapIdentifier(networkId);
            Optional<NetworkMap> optionalNetworkMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, networkMapIdentifier);
            NetworkMapBuilder nwMapBuilder;
            if (optionalNetworkMap.isPresent()) {
                nwMapBuilder = new NetworkMapBuilder(optionalNetworkMap.get());
            } else {
                nwMapBuilder = new NetworkMapBuilder().withKey(new NetworkMapKey(networkId)).setNetworkId(networkId);
                LOG.debug("Adding a new network node in NetworkMaps DS for network {}", networkId.getValue());
            }
            List<Uuid> subnetIdList = nwMapBuilder.getSubnetIdList() != null
                    ? new ArrayList<>(nwMapBuilder.getSubnetIdList()) : new ArrayList<>();
            subnetIdList.add(subnetId);
            nwMapBuilder.setSubnetIdList(subnetIdList);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, networkMapIdentifier, nwMapBuilder
                    .build());
            LOG.debug("Created subnet-network mapping for subnet {} network {}", subnetId.getValue(),
                    networkId.getValue());
        } catch (RuntimeException | ExecutionException | InterruptedException e) {
            LOG.error("Create subnet-network mapping failed for subnet {} network {}", subnetId.getValue(),
                    networkId.getValue());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void deleteSubnetToNetworkMapping(Uuid subnetId, Uuid networkId) {
        try {
            InstanceIdentifier<NetworkMap>  networkMapIdentifier = NeutronvpnUtils.buildNetworkMapIdentifier(networkId);
            Optional<NetworkMap> optionalNetworkMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, networkMapIdentifier);
            if (optionalNetworkMap.isPresent()) {
                NetworkMapBuilder nwMapBuilder = new NetworkMapBuilder(optionalNetworkMap.get());
                List<Uuid> subnetIdList = new ArrayList<>(nwMapBuilder.getSubnetIdList());
                if (subnetIdList.remove(subnetId)) {
                    if (subnetIdList.isEmpty()) {
                        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, networkMapIdentifier);
                        LOG.debug("Deleted network node in NetworkMaps DS for subnet {} network {}",
                                subnetId.getValue(), networkId.getValue());
                    } else {
                        nwMapBuilder.setSubnetIdList(subnetIdList);
                        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, networkMapIdentifier,
                                nwMapBuilder.build());
                        LOG.debug("Deleted subnet-network mapping for subnet {} network {}", subnetId.getValue(),
                                networkId.getValue());
                    }
                } else {
                    LOG.error("Subnet {} is not mapped to network {}", subnetId.getValue(), networkId.getValue());
                }
            } else {
                LOG.error("network {} not present for subnet {} ", networkId, subnetId);
            }
        } catch (RuntimeException | ExecutionException | InterruptedException e) {
            LOG.error("Delete subnet-network mapping failed for subnet {} network {}", subnetId.getValue(),
                    networkId.getValue());
        }
    }
}


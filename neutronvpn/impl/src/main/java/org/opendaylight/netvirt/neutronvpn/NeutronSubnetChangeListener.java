/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
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
public class NeutronSubnetChangeListener extends AsyncDataTreeChangeListenerBase<Subnet, NeutronSubnetChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSubnetChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronExternalSubnetHandler externalSubnetHandler;
    private final NeutronvpnUtils neutronvpnUtils;

    @Inject
    public NeutronSubnetChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
            final NeutronExternalSubnetHandler externalSubnetHandler, final NeutronvpnUtils neutronvpnUtils) {
        super(Subnet.class, NeutronSubnetChangeListener.class);
        this.dataBroker = dataBroker;
        this.nvpnManager = neutronvpnManager;
        this.externalSubnetHandler = externalSubnetHandler;
        this.neutronvpnUtils = neutronvpnUtils;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Subnet> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Subnets.class).child(Subnet.class);
    }

    @Override
    protected NeutronSubnetChangeListener getDataTreeChangeListener() {
        return NeutronSubnetChangeListener.this;
    }


    @Override
    protected void add(InstanceIdentifier<Subnet> identifier, Subnet input) {
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
    protected void remove(InstanceIdentifier<Subnet> identifier, Subnet input) {
        LOG.trace("Removing subnet : key: {}, value={}", identifier, input);
        Uuid networkId = input.getNetworkId();
        Uuid subnetId = input.getUuid();
        Network network = neutronvpnUtils.getNeutronNetwork(networkId);
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.warn("neutron vpn received a subnet remove() for a network without a provider extension augmentation "
                            + "or with an unsupported network type for the subnet {} which is part of network {}",
                    subnetId.getValue(), network);
            return;
        }
        handleNeutronSubnetDeleted(subnetId, networkId, String.valueOf(input.getCidr().getValue()));
        externalSubnetHandler.handleExternalSubnetRemoved(network, subnetId);
        neutronvpnUtils.removeFromSubnetCache(input);
    }

    @Override
    protected void update(InstanceIdentifier<Subnet> identifier, Subnet original, Subnet update) {
        LOG.trace("Updating Subnet : key: {}, original value={}, update value={}", identifier, original, update);
        neutronvpnUtils.addToSubnetCache(update);
    }

    private void handleNeutronSubnetCreated(Subnet subnet, Network network) {
        Uuid networkId = network.getUuid();
        Uuid subnetId = subnet.getUuid();
        ProviderTypes providerType = NeutronvpnUtils.getProviderNetworkType(network);
        String segmentationId = NeutronvpnUtils.getSegmentationIdFromNeutronNetwork(network);
        nvpnManager.createSubnetmapNode(subnetId, String.valueOf(subnet.getCidr().getValue()),
                subnet.getTenantId(), networkId,
                providerType != null ? NetworkAttributes.NetworkType.valueOf(providerType.getName()) : null,
                segmentationId != null ? Long.parseLong(segmentationId) : 0L);
        createSubnetToNetworkMapping(subnetId, networkId);
    }

    private void handleNeutronSubnetDeleted(Uuid subnetId, Uuid networkId, String subnetCidr) {
        Uuid vpnId = neutronvpnUtils.getVpnForNetwork(networkId);
        if (vpnId != null) {
            LOG.warn("Subnet {} deleted without disassociating network {} from VPN {}. Ideally, please disassociate "
                    + "network from VPN before deleting neutron subnet.", subnetId.getValue(), networkId.getValue(),
                    vpnId.getValue());
            Subnetmap subnetmap = neutronvpnUtils.getSubnetmap(subnetId);
            if (subnetmap != null) {
                nvpnManager.removeSubnetFromVpn(vpnId, subnetId, null /* internet-vpn-id */);
            } else {
                LOG.error("Subnetmap for subnet {} not found", subnetId.getValue());
            }
            Set<VpnTarget> routeTargets = neutronvpnUtils.getRtListForVpn(vpnId.getValue());
            if (!routeTargets.isEmpty()) {
                neutronvpnUtils.removeRouteTargetsToSubnetAssociation(dataBroker, routeTargets,
                        subnetCidr, subnetId.getValue());
            }
        }
        if (networkId != null) {
            deleteSubnetToNetworkMapping(subnetId, networkId);
        }
        nvpnManager.deleteSubnetMapNode(subnetId);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void createSubnetToNetworkMapping(Uuid subnetId, Uuid networkId) {
        try {
            InstanceIdentifier<NetworkMap>  networkMapIdentifier = NeutronvpnUtils.buildNetworkMapIdentifier(networkId);
            Optional<NetworkMap> optionalNetworkMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, networkMapIdentifier);
            NetworkMapBuilder nwMapBuilder = null;
            if (optionalNetworkMap.isPresent()) {
                nwMapBuilder = new NetworkMapBuilder(optionalNetworkMap.get());
            } else {
                nwMapBuilder = new NetworkMapBuilder().setKey(new NetworkMapKey(networkId)).setNetworkId(networkId);
                LOG.debug("Adding a new network node in NetworkMaps DS for network {}", networkId.getValue());
            }
            List<Uuid> subnetIdList = nwMapBuilder.getSubnetIdList();
            if (subnetIdList == null) {
                subnetIdList = new ArrayList<>();
            }
            subnetIdList.add(subnetId);
            nwMapBuilder.setSubnetIdList(subnetIdList);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, networkMapIdentifier, nwMapBuilder
                    .build());
            LOG.debug("Created subnet-network mapping for subnet {} network {}", subnetId.getValue(),
                    networkId.getValue());
        } catch (ReadFailedException | RuntimeException e) {
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
                List<Uuid> subnetIdList = nwMapBuilder.getSubnetIdList();
                if (subnetIdList.remove(subnetId)) {
                    if (subnetIdList.isEmpty()) {
                        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, networkMapIdentifier);
                        LOG.debug("Deleted network node in NetworkMaps DS for network {}", subnetId.getValue(),
                                networkId.getValue());
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
        } catch (ReadFailedException | RuntimeException e) {
            LOG.error("Delete subnet-network mapping failed for subnet {} network {}", subnetId.getValue(),
                    networkId.getValue());
        }
    }
}


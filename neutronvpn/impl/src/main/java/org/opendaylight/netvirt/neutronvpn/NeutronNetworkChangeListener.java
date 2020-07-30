/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ElanSegments;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ElanSegmentsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.neutron.networks.network.Segments;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronNetworkChangeListener extends AbstractAsyncDataTreeChangeListener<Network> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronNetworkChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;
    private final IElanService elanService;
    private final NeutronvpnUtils neutronvpnUtils;

    @Inject
    public NeutronNetworkChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
                                        final NeutronvpnNatManager neutronvpnNatManager,
                                        final IElanService elanService, final NeutronvpnUtils neutronvpnUtils) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(Networks.class).child(Network.class), Executors.newSingleThreadExecutor(
                        "NeutronNetworkChangeListener", LOG));
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        nvpnNatManager = neutronvpnNatManager;
        this.elanService = elanService;
        this.neutronvpnUtils = neutronvpnUtils;
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
    public void add(InstanceIdentifier<Network> identifier, Network input) {
        LOG.trace("Adding Network : key: {}, value={}", identifier, input);
        String networkId = input.getUuid().getValue();
        if (!NeutronvpnUtils.isNetworkTypeSupported(input)) {
            LOG.error("Neutronvpn doesn't support the provider type for given network {}", networkId);
            return;
        }
        Class<? extends NetworkTypeBase> networkType = input.augmentation(NetworkProviderExtension.class)
                .getNetworkType();
        if (NeutronvpnUtils.isVlanOrVxlanNetwork(networkType)
                && NeutronUtils.getSegmentationIdFromNeutronNetwork(input, networkType) == null) {
            LOG.error("Segmentation ID is null for configured provider network {} of type {}. Abandoning any further "
                    + "processing for the network", input.getUuid().getValue(), networkType);
            return;
        }

        Collection<Segments> providerSegmentsCollection = input.augmentation(NetworkProviderExtension.class)
                .getSegments().values();
        List<Segments> providerSegments = new ArrayList<Segments>(providerSegmentsCollection != null
                ? providerSegmentsCollection : Collections.emptyList());
        if (providerSegments != null && providerSegments.size() > 0) {
            for (Segments segment: providerSegments) {
                Class<? extends NetworkTypeBase> segNetworktype = segment.getNetworkType();
                if (NeutronvpnUtils.isVlanOrVxlanNetwork(segNetworktype)
                        && segment.getSegmentationId() == null) {
                    LOG.error("Segmentation ID is null for configured network {} of Segment provider type {}. "
                                    + "Abandoning any further processing for the network", input.getUuid().getValue(),
                            segNetworktype);
                    return;
                }
            }
        }

        neutronvpnUtils.addToNetworkCache(input);
        // Create ELAN instance for this network
        ElanInstance elanInstance = createElanInstance(input);

        if (NeutronvpnUtils.getIsExternal(input)) {
            // Create ELAN interface and IETF interfaces for the physical network
            elanService.createExternalElanNetwork(elanInstance);
            ProviderTypes providerNwType = NeutronvpnUtils.getProviderNetworkType(input);
            if (providerNwType == null) {
                LOG.error("Unable to get Network Provider Type for network {}", networkId);
                return;
            }
            LOG.trace("External Network Provider Type for network {} is {}", networkId, providerNwType.getName());
            nvpnNatManager.addExternalNetwork(input);
            if (NeutronvpnUtils.isFlatOrVlanNetwork(input)) {
                nvpnManager.createL3InternalVpn(input.getUuid(), null, null, null, null, null, null, null);
                nvpnManager.createExternalVpnInterfaces(input.getUuid());
            }
        }
    }

    @Override
    public void remove(InstanceIdentifier<Network> identifier, Network input) {
        LOG.trace("Removing Network : key: {}, value={}", identifier, input);
        if (NeutronvpnUtils.getIsExternal(input)) {
            if (NeutronvpnUtils.isFlatOrVlanNetwork(input)) {
                nvpnManager.removeExternalVpnInterfaces(input.getUuid());
                nvpnManager.removeVpn(input.getUuid());
            }
            nvpnNatManager.removeExternalNetwork(input);
        }
        //Delete ELAN instance for this network
        String elanInstanceName = input.getUuid().getValue();
        ElanInstance elanInstance = elanService.getElanInstance(elanInstanceName);
        if (elanInstance != null) {
            elanService.deleteExternalElanNetwork(elanInstance);
            deleteElanInstance(elanInstanceName);
        }
        neutronvpnUtils.removeFromNetworkCache(input);
    }

    @Override
    public void update(InstanceIdentifier<Network> identifier, Network original, Network update) {
        LOG.trace("Updating Network : key: {}, original value={}, update value={}", identifier, original, update);
        if (Objects.equals(original, update)) {
            return;
        }
        neutronvpnUtils.addToNetworkCache(update);
        String elanInstanceName = original.getUuid().getValue();
        Class<? extends SegmentTypeBase> updateSegmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(update);
        String updateSegmentationId = NeutronvpnUtils.getSegmentationIdFromNeutronNetwork(update);
        String updatePhysicalNetwork = NeutronvpnUtils.getPhysicalNetworkName(update);

        NetworkProviderExtension originalProviderExtension = original.augmentation(NetworkProviderExtension.class);
        Collection<Segments> originalSegCollection = originalProviderExtension.getSegments().values();
        List<Segments> originalSegments = new ArrayList<Segments>(originalSegCollection != null ? originalSegCollection
                : Collections.emptyList());
        NetworkProviderExtension updateProviderExtension = update.augmentation(NetworkProviderExtension.class);
        Collection<Segments> updateSegCollection = updateProviderExtension.getSegments().values();
        List<Segments> updateSegments = new ArrayList<Segments>(updateSegCollection != null ? updateSegCollection
                : Collections.emptyList());

        Boolean origExternal = NeutronvpnUtils.getIsExternal(original);
        Boolean updateExternal = NeutronvpnUtils.getIsExternal(update);
        Boolean origIsFlatOrVlanNetwork = NeutronvpnUtils.isFlatOrVlanNetwork(original);
        Boolean updateIsFlatOrVlanNetwork = NeutronvpnUtils.isFlatOrVlanNetwork(update);

        if (!Objects.equals(originalSegments, updateSegments) || !Objects.equals(origExternal, updateExternal)) {
            if (origExternal && origIsFlatOrVlanNetwork && (!updateExternal || !updateIsFlatOrVlanNetwork)) {
                nvpnManager.removeExternalVpnInterfaces(original.getUuid());
                nvpnManager.removeVpn(original.getUuid());
                nvpnNatManager.removeExternalNetwork(original);
            }

            ElanInstance elanInstance = elanService.getElanInstance(elanInstanceName);
            if (elanInstance != null) {
                elanService.deleteExternalElanNetwork(elanInstance);
                elanInstance = updateElanInstance(elanInstanceName, updateSegmentType, updateSegmentationId,
                        updatePhysicalNetwork, update);
                if (updateExternal) {
                    elanService.updateExternalElanNetwork(elanInstance);
                }
            }

            if (updateExternal && updateIsFlatOrVlanNetwork && !origExternal) {
                nvpnNatManager.addExternalNetwork(update);
                nvpnManager.createL3InternalVpn(update.getUuid(), null, null, null, null, null, null, null);
                nvpnManager.createExternalVpnInterfaces(update.getUuid());
            }
        }
    }

    @NonNull
    private List<ElanSegments> buildSegments(Network input) {
        NetworkProviderExtension providerExtension = input.augmentation(NetworkProviderExtension.class);
        if (providerExtension == null || providerExtension.getSegments() == null) {
            return Collections.emptyList();
        }
        return providerExtension.getSegments().values().stream()
                .map(segment -> new ElanSegmentsBuilder()
                        .setSegmentationIndex(segment.getSegmentationIndex())
                        .setSegmentationId(getSegmentationId(input, segment))
                        .setSegmentType(elanSegmentTypeFromNetworkType(segment.getNetworkType()))
                        .setPhysicalNetworkName(segment.getPhysicalNetwork())
                        .build())
                .collect(Collectors.toList());
    }

    private Long getSegmentationId(Network network, Segments segment) {
        try {
            if (segment.getSegmentationId() != null) {
                return Long.valueOf(segment.getSegmentationId());
            }
        } catch (NumberFormatException error) {
            LOG.error("Failed to get the segment id for network {}", network);
        }
        return 0L;
    }

    @Nullable
    private Class<? extends SegmentTypeBase> elanSegmentTypeFromNetworkType(
            @Nullable Class<? extends NetworkTypeBase> networkType) {
        if (networkType == null) {
            return null;
        }
        if (networkType.isAssignableFrom(NetworkTypeVxlan.class)) {
            return SegmentTypeVxlan.class;
        } else if (networkType.isAssignableFrom(NetworkTypeVlan.class)) {
            return SegmentTypeVlan.class;
        } else if (networkType.isAssignableFrom(NetworkTypeFlat.class)) {
            return SegmentTypeFlat.class;
        }
        return null;
    }

    private ElanInstance createElanInstance(Network input) {
        String elanInstanceName = input.getUuid().getValue();
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        Optional<ElanInstance> existingElanInstance = null;
        try {
            existingElanInstance = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    id);
            if (existingElanInstance.isPresent()) {
                return existingElanInstance.get();
            }
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("createElanInstance: failed to read elanInstance {} due to exception ", elanInstanceName, e);
        }
        Class<? extends SegmentTypeBase> segmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(input);
        String segmentationId = NeutronvpnUtils.getSegmentationIdFromNeutronNetwork(input);
        String physicalNetworkName = NeutronvpnUtils.getPhysicalNetworkName(input);
        Uint32 elanTag = elanService.retrieveNewElanTag(elanInstanceName);
        ElanInstance elanInstance = createElanInstanceBuilder(elanInstanceName, segmentType, segmentationId,
                physicalNetworkName, input).setElanTag(elanTag).build();
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        LOG.debug("ELANInstance {} created with elan tag {} and segmentation ID {}", elanInstanceName, elanTag,
                segmentationId);
        return elanInstance;
    }

    private ElanInstanceBuilder createElanInstanceBuilder(String elanInstanceName, Class<? extends SegmentTypeBase>
            segmentType, String segmentationId, String physicalNetworkName, Network network) {
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        Optional<ElanInstance> optionalElan;
        try {
            optionalElan = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("createElanInstanceBuilder: Exception while reading ElanInstance DS for the elan-Instance {}, "
                            + "network {}", elanInstanceName, network.key().getUuid().getValue());
            return null;
        }
        ElanInstanceBuilder elanInstanceBuilder;
        if (optionalElan.isPresent()) {
            elanInstanceBuilder = new ElanInstanceBuilder(optionalElan.get());
        } else {
            elanInstanceBuilder = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName);
        }
        if (segmentType != null) {
            elanInstanceBuilder.setSegmentType(segmentType);
            if (segmentationId != null) {
                elanInstanceBuilder.setSegmentationId(Long.valueOf(segmentationId));
            }
            if (physicalNetworkName != null) {
                elanInstanceBuilder.setPhysicalNetworkName(physicalNetworkName);
            }
        }
        List<ElanSegments> segments = buildSegments(network);
        if (!segments.isEmpty()) {
            elanInstanceBuilder.setSegmentType(null);
            elanInstanceBuilder.setPhysicalNetworkName(null);
            elanInstanceBuilder.setSegmentationId(Uint32.ZERO);
        }

        elanInstanceBuilder.setElanSegments(segments);
        elanInstanceBuilder.setExternal(NeutronvpnUtils.getIsExternal(network));
        elanInstanceBuilder.withKey(new ElanInstanceKey(elanInstanceName));
        return elanInstanceBuilder;
    }

    private void deleteElanInstance(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        LOG.debug("ELANInstance {} deleted", elanInstanceName);
    }

    private ElanInstance updateElanInstance(String elanInstanceName, Class<? extends SegmentTypeBase> segmentType,
                                            String segmentationId, String physicalNetworkName, Network network) {

        ElanInstance elanInstance = createElanInstanceBuilder(elanInstanceName, segmentType, segmentationId,
                physicalNetworkName, network).build();
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        return elanInstance;
    }

    private InstanceIdentifier<ElanInstance> createElanInstanceIdentifier(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        return id;
    }
}

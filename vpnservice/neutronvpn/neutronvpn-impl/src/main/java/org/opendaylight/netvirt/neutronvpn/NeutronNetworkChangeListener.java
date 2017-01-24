/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ElanSegments;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ElanSegmentsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronNetworkChangeListener extends AsyncDataTreeChangeListenerBase<Network, NeutronNetworkChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronNetworkChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;
    private final IElanService elanService;
    private OdlInterfaceRpcService odlInterfaceRpcService;

    public NeutronNetworkChangeListener(final DataBroker dataBroker, final NeutronvpnManager nVpnMgr,
                                        final NeutronvpnNatManager nVpnNatMgr, final IElanService elanService,
                                        OdlInterfaceRpcService odlInterfaceRpcService) {
        super(Network.class, NeutronNetworkChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = nVpnMgr;
        nvpnNatManager = nVpnNatMgr;
        this.elanService = elanService;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Network> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Networks.class).child(Network.class);
    }

    @Override
    protected NeutronNetworkChangeListener getDataTreeChangeListener() {
        return NeutronNetworkChangeListener.this;
    }


    @Override
    protected void add(InstanceIdentifier<Network> identifier, Network input) {
        LOG.trace("Adding Network : key: {}, value={}", identifier, input);
        if (!NeutronvpnUtils.isNetworkTypeSupported(input)) {
            LOG.error("Neutronvpn doesn't support this network provider type for this network {} and uuid {}.", input.getName(), input.getUuid());
            return;
        }

        NeutronvpnUtils.addToNetworkCache(input);
        // Create ELAN instance for this network
        ElanInstance elanInstance = createElanInstance(input);
        // Create ELAN interface and IETF interfaces for the physical network
        elanService.createExternalElanNetwork(elanInstance);
        if (NeutronvpnUtils.getIsExternal(input)) {
            ProviderTypes providerNwType = NeutronvpnUtils.getProviderNetworkType(input);
            if (providerNwType == null) {
                LOG.error("Neutron Service : Unable to get Network Provider Type for network {}", input.getUuid());
                return;
            }
            LOG.trace("Neutron Service : External Network Provider Type is {}", providerNwType.getName());
            nvpnNatManager.addExternalNetwork(input);
            if (NeutronvpnUtils.isFlatOrVlanNetwork(input)) {
                nvpnManager.createL3InternalVpn(input.getUuid(), null, null, null, null, null, null, null);
                nvpnManager.createExternalVpnInterfaces(input.getUuid());
            }
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Network> identifier, Network input) {
        LOG.trace("Removing Network : key: {}, value={}", identifier, input);
        if (!NeutronvpnUtils.isNetworkTypeSupported(input)) {
            //FIXME: This should be removed when support for GRE network types is added
            LOG.error("Neutronvpn doesn't support gre network provider type for this network {}.", input);
            return;
        }
        if (NeutronvpnUtils.getIsExternal(input)) {
            if (NeutronvpnUtils.isFlatOrVlanNetwork(input)) {
                nvpnManager.removeExternalVpnInterfaces(input.getUuid());
                nvpnManager.removeL3Vpn(input.getUuid());
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
        NeutronvpnUtils.removeFromNetworkCache(input);
    }

    @Override
    protected void update(InstanceIdentifier<Network> identifier, Network original, Network update) {
        LOG.trace("Updating Network : key: {}, original value={}, update value={}", identifier, original, update);
        NeutronvpnUtils.addToNetworkCache(update);
        String elanInstanceName = original.getUuid().getValue();
        Class<? extends SegmentTypeBase> origSegmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(original);
        String origSegmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(original);
        String origPhysicalNetwork = NeutronvpnUtils.getPhysicalNetworkName(original);
        Class<? extends SegmentTypeBase> updateSegmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(update);
        String updateSegmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(update);
        String updatePhysicalNetwork = NeutronvpnUtils.getPhysicalNetworkName(update);

        if (!Objects.equals(origSegmentType, updateSegmentType)
                || !Objects.equals(origSegmentationId, updateSegmentationId)
                || !Objects.equals(origPhysicalNetwork, updatePhysicalNetwork)) {
            if (NeutronvpnUtils.getIsExternal(original) && NeutronvpnUtils.isFlatOrVlanNetwork(original)) {
                nvpnManager.removeExternalVpnInterfaces(original.getUuid());
            }
            ElanInstance elanInstance = elanService.getElanInstance(elanInstanceName);
            if (elanInstance != null) {
                elanService.deleteExternalElanNetwork(elanInstance);
                elanInstance = updateElanInstance(elanInstanceName, updateSegmentType, updateSegmentationId,
                        updatePhysicalNetwork, buildSegments(update));
                elanService.createExternalElanNetwork(elanInstance);
            }

            if (NeutronvpnUtils.getIsExternal(update) && NeutronvpnUtils.isFlatOrVlanNetwork(update)) {
                nvpnManager.createExternalVpnInterfaces(update.getUuid());
            }
        }

        QosNetworkExtension updateQos = update.getAugmentation(QosNetworkExtension.class);
        QosNetworkExtension originalQos = original.getAugmentation(QosNetworkExtension.class);
        if (originalQos == null && updateQos != null) {
            // qos policy add
            NeutronvpnUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            NeutronQosUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {
            // qos policy update
            NeutronvpnUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
            NeutronvpnUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            NeutronQosUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qos policy delete
            NeutronQosUtils.handleNeutronNetworkQosRemove(dataBroker, odlInterfaceRpcService,
                    original, originalQos.getQosPolicyId());
            NeutronvpnUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
        }
    }

    private List<ElanSegments> buildSegments(Network input) {
        Long numSegments = NeutronUtils.getNumberSegmentsFromNeutronNetwork(input);
        Long index = 0L;
        List <ElanSegments> segments = new ArrayList<>();
        while (index < numSegments) {
            index++;
            ElanSegmentsBuilder elanSegmentsBuilder = new ElanSegmentsBuilder();
            elanSegmentsBuilder.setSegmentationId(Long.valueOf(NeutronUtils.getSegmentationIdFromNeutronNetworkSegment(input, index)));
            if(NeutronUtils.isNetworkSegmentType(input, index, NetworkTypeVxlan.class)) {
                elanSegmentsBuilder.setSegmentType(SegmentTypeVxlan.class);
            } else if (NeutronUtils.isNetworkSegmentType(input, index, NetworkTypeVlan.class)) {
                elanSegmentsBuilder.setSegmentType(SegmentTypeVlan.class);
            }
            elanSegmentsBuilder.setSegmentationIndex(index);
            segments.add(elanSegmentsBuilder.build());
            LOG.debug("Added segment {} to ELANInstance{}", segments.get(Integer.valueOf(index.intValue() - 1 )));
        }
        return segments;
    }

    private ElanInstance createElanInstance(Network input) {
        String elanInstanceName = input.getUuid().getValue();
        Class<? extends SegmentTypeBase> segmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(input);
        String segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(input);
        String physicalNetworkName = NeutronvpnUtils.getPhysicalNetworkName(input);
        ElanInstance elanInstance = createElanInstance(elanInstanceName, segmentType, segmentationId,
                                                       physicalNetworkName, buildSegments(input));
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        Optional<ElanInstance> existingElanInstance = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (existingElanInstance.isPresent()) {
            return existingElanInstance.get();
        }
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        LOG.debug("ELANInstance {} created", elanInstanceName);
        return elanInstance;
    }

    private void deleteElanInstance(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        LOG.debug("ELANInstance {} deleted", elanInstanceName);
    }

    private ElanInstance updateElanInstance(String elanInstanceName, Class<? extends SegmentTypeBase> segmentType,
            String segmentationId, String physicalNetworkName, List<ElanSegments> segments) {

        ElanInstance elanInstance = createElanInstance(elanInstanceName, segmentType, segmentationId, physicalNetworkName, segments);
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        return elanInstance;
    }

    private InstanceIdentifier<ElanInstance> createElanInstanceIdentifier(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        return id;
    }

    private ElanInstance createElanInstance(String elanInstanceName, Class<? extends SegmentTypeBase> segmentType,
            String segmentationId, String physicalNetworkName, List<ElanSegments> segments) {
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName);
        if (segmentType != null) {
            elanInstanceBuilder.setSegmentType(segmentType);
            if (segmentationId != null) {
                elanInstanceBuilder.setSegmentationId(Long.valueOf(segmentationId));
            }
            if (physicalNetworkName != null) {
                elanInstanceBuilder.setPhysicalNetworkName(physicalNetworkName);
            }
        }
        if (segments != null) {
            elanInstanceBuilder.setElanSegments(segments);
        }
        elanInstanceBuilder.setKey(new ElanInstanceKey(elanInstanceName));
        return elanInstanceBuilder.build();
    }

}

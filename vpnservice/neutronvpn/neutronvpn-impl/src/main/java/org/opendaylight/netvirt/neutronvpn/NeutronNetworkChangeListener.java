/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.Objects;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronNetworkChangeListener extends AbstractDataChangeListener<Network> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronNetworkChangeListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private final NeutronvpnNatManager nvpnNatManager;
    private final IElanService elanService;

    public NeutronNetworkChangeListener(final DataBroker dataBroker, final NeutronvpnNatManager nVpnNatMgr,
                                        final IElanService elanService) {
        super(Network.class);
        this.dataBroker = dataBroker;
        nvpnNatManager = nVpnNatMgr;
        this.elanService = elanService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                getWildCardPath(), this, DataChangeScope.SUBTREE);
    }

    private InstanceIdentifier<Network> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Networks.class).child(Network.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
            listenerRegistration = null;
        }
        LOG.info("{} close", getClass().getSimpleName());
    }

    @Override
    protected void add(InstanceIdentifier<Network> identifier, Network input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Network : key: " + identifier + ", value=" + input);
        }
        if (!NeutronvpnUtils.isNetworkTypeSupported(input)) {
            //FIXME: This should be removed when support for GRE network types is added
            LOG.error("Neutronvpn doesn't support gre network provider type for this network {}.", input);
            return;
        }
        // Create ELAN instance for this network
        ElanInstance elanInstance = createElanInstance(input);
        // Create ELAN interface and IETF interfaces for the physical network
        elanService.createExternalElanNetwork(elanInstance);
        if (input.getAugmentation(NetworkL3Extension.class).isExternal()) {
            nvpnNatManager.addExternalNetwork(input);
            NeutronvpnUtils.addToNetworkCache(input);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Network> identifier, Network input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing Network : key: " + identifier + ", value=" + input);
        }
        if (!NeutronvpnUtils.isNetworkTypeSupported(input)) {
            //FIXME: This should be removed when support for GRE network types is added
            LOG.error("Neutronvpn doesn't support gre network provider type for this network {}.", input);
            return;
        }
        //Delete ELAN instance for this network
        String elanInstanceName = input.getUuid().getValue();
        ElanInstance elanInstance = elanService.getElanInstance(elanInstanceName);
        if (elanInstance != null) {
            elanService.deleteExternalElanNetwork(elanInstance);
            deleteElanInstance(elanInstanceName);
        }
        if (input.getAugmentation(NetworkL3Extension.class).isExternal()) {
            nvpnNatManager.removeExternalNetwork(input);
            NeutronvpnUtils.removeFromNetworkCache(input);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Network> identifier, Network original, Network update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Network : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }

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
            ElanInstance elanInstance = elanService.getElanInstance(elanInstanceName);
            if (elanInstance != null) {
                elanService.deleteExternalElanNetwork(elanInstance);
                elanInstance = updateElanInstance(elanInstanceName, updateSegmentType, updateSegmentationId,
                        updatePhysicalNetwork);
                elanService.createExternalElanNetwork(elanInstance);
            }
        }
    }

    private ElanInstance createElanInstance(Network input) {
        String elanInstanceName = input.getUuid().getValue();
        Class<? extends SegmentTypeBase> segmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(input);
        String segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(input);
        String physicalNetworkName = NeutronvpnUtils.getPhysicalNetworkName(input);
        ElanInstance elanInstance = createElanInstance(elanInstanceName, segmentType, segmentationId, physicalNetworkName);
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        return elanInstance;
    }

    private void deleteElanInstance(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
    }

    private ElanInstance updateElanInstance(String elanInstanceName, Class<? extends SegmentTypeBase> segmentType,
            String segmentationId, String physicalNetworkName) {
        ElanInstance elanInstance = createElanInstance(elanInstanceName, segmentType, segmentationId, physicalNetworkName);
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
            String segmentationId, String physicalNetworkName) {
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
        elanInstanceBuilder.setKey(new ElanInstanceKey(elanInstanceName));
        return elanInstanceBuilder.build();
    }

}

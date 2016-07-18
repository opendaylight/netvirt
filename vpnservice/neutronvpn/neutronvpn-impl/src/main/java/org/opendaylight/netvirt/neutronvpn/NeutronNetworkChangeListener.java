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
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance.SegmentType;
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
    private final DataBroker broker;
    private NeutronvpnManager nvpnManager;
    private NeutronvpnNatManager nvpnNatManager;


    public NeutronNetworkChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr,
                                        NeutronvpnNatManager nVpnNatMgr) {
        super(Network.class);
        broker = db;
        nvpnManager = nVpnMgr;
        nvpnNatManager = nVpnNatMgr;
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("N_Network listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).
                            child(Networks.class).child(Network.class),
                    NeutronNetworkChangeListener.this, DataChangeScope.SUBTREE);
            LOG.info("Neutron Manager Network DataChange listener registration Success!");
        } catch (final Exception e) {
            LOG.error("Neutron Manager Network DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Network DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Network> identifier, Network input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Network : key: " + identifier + ", value=" + input);
        }
        if (NeutronvpnUtils.isNetworkTypeGre(input)) {
            //FIXME: This should be removed when support for GRE network types is added
            LOG.error("Neutronvpn doesn't support gre network provider type for this network {}.", input);
            return;
        }
        // Create ELAN instance for this network
        ElanInstance elanInstance = createElanInstance(input);
        // Create ELAN interface and IETF interfaces for the physical network
        NeutronvpnServiceAccessor.getElanProvider().createExternalElanNetwork(elanInstance);
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
        if (NeutronvpnUtils.isNetworkTypeGre(input)) {
            //FIXME: This should be removed when support for GRE network types is added
            LOG.error("Neutronvpn doesn't support gre network provider type for this network {}.", input);
            return;
        }
        //Delete ELAN instance for this network
        String elanInstanceName = input.getUuid().getValue();
        ElanInstance elanInstance = NeutronvpnServiceAccessor.getElanProvider().getElanInstance(elanInstanceName);
        if (elanInstance != null) {
            NeutronvpnServiceAccessor.getElanProvider().deleteExternalElanNetwork(elanInstance);
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
        SegmentType origSegmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(original);
        String origSegmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(original);
        String origPhysicalNetwork = NeutronvpnUtils.getPhysicalNetworkName(original);
        SegmentType updateSegmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(update);
        String updateSegmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(update);
        String updatePhysicalNetwork = NeutronvpnUtils.getPhysicalNetworkName(update);

        if (!Objects.equals(origSegmentType, updateSegmentType)
                || !Objects.equals(origSegmentationId, updateSegmentationId)
                || !Objects.equals(origPhysicalNetwork, updatePhysicalNetwork)) {
            ElanInstance elanInstance = NeutronvpnServiceAccessor.getElanProvider().getElanInstance(elanInstanceName);
            if (elanInstance != null) {
                NeutronvpnServiceAccessor.getElanProvider().deleteExternalElanNetwork(elanInstance);
                elanInstance = updateElanInstance(elanInstanceName, updateSegmentType, updateSegmentationId,
                        updatePhysicalNetwork);
                NeutronvpnServiceAccessor.getElanProvider().createExternalElanNetwork(elanInstance);
            }
        }
    }

    private ElanInstance createElanInstance(Network input) {
        String elanInstanceName = input.getUuid().getValue();
        SegmentType segmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(input);
        String segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(input);
        String physicalNetworkName = NeutronvpnUtils.getPhysicalNetworkName(input);
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
        ElanInstance elanInstance = elanInstanceBuilder.build();
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        return elanInstance;
    }

    private void deleteElanInstance(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, id);
    }

    private ElanInstance updateElanInstance(String elanInstanceName, SegmentType segmentType, String segmentationId,
            String physicalNetworkName) {
        ElanInstance elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                .setSegmentType(segmentType).setSegmentationId(Long.valueOf(segmentationId))
                .setPhysicalNetworkName(physicalNetworkName).setKey(new ElanInstanceKey(elanInstanceName)).build();
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        return elanInstance;
    }

    private InstanceIdentifier<ElanInstance> createElanInstanceIdentifier(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        return id;
    }
}

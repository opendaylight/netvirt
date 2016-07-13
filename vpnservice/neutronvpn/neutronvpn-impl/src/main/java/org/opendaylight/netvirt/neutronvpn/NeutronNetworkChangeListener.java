/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elanmanager.utils.ElanPhysicalPortCacheUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanPhysicalPortCacheUtils.DpnInterface;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
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


    public NeutronNetworkChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr, NeutronvpnNatManager
            nVpnNatMgr) {
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
        if (input.getAugmentation(NetworkL3Extension.class).isExternal()) {
            nvpnNatManager.addExternalNetwork(input);
            NeutronvpnUtils.addToNetworkCache(input);
        }

        // Create ELAN instance for this network
        ElanInstance elanInstance = createElanInstance(input);
        String physicalNetworkName = NeutronvpnUtils.getPhysicalNetworkName(input);
        if (physicalNetworkName == null) {
            LOG.trace("No physical network attached to {}", input.getName());
            return;
        }
        Iterable<DpnInterface> dpnInterfaces = ElanPhysicalPortCacheUtils.getPhysnetPorts(physicalNetworkName);
        if (dpnInterfaces == null) {
            LOG.trace("No physical port attached to {}", physicalNetworkName);
            return;
        }

        for (DpnInterface dpnInterface : dpnInterfaces) {
            String patchPortName = dpnInterface.getPatchPortName();
            // If physnet is attached to external port, create the matching ietf-interface(s)
            String elanInterfaceName = createIetfInterfaces(elanInstance, patchPortName);
            // Associate created ietf-interface with the ELAN instance
            createElanInterface(elanInterfaceName, elanInstance.getElanInstanceName());
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
        deleteElanInstance(elanInstanceName);
        if (input.getAugmentation(NetworkL3Extension.class).isExternal()) {
            nvpnNatManager.removeExternalNetwork(input);
            NeutronvpnUtils.removeFromNetworkCache(input);
        }

        // TODO: delete elan-interfaces for physnet port
    }

    @Override
    protected void update(InstanceIdentifier<Network> identifier, Network original, Network update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Network : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }

        // TODO handle update?
    }

    private ElanInstance createElanInstance(Network input) {
        String elanInstanceName = input.getUuid().getValue();
        SegmentType segmentType = NeutronvpnUtils.getSegmentTypeFromNeutronNetwork(input);
        String segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(input);
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName);
        if (segmentType != null) {
            elanInstanceBuilder.setSegmentType(segmentType);
            if (segmentationId != null) {
                elanInstanceBuilder.setSegmentationId(Long.valueOf(segmentationId));
            }
        }
        elanInstanceBuilder.setKey(new ElanInstanceKey(elanInstanceName));
        ElanInstance elanInstance = elanInstanceBuilder.build();
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        return elanInstance;
    }

    private void deleteElanInstance(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, id);
    }

    /**
     * Create ietf-interfaces based on the network segment type.<br>
     * For network type flat - create transparent interface pointing to the
     * patch-port attached to the physnet port.<br>
     * For network type vlan - create trunk interface pointing to the patch-port
     * attached to the physnet port + trunk-member interface pointing to the
     * trunk interface.
     *
     * @param elanInstance
     *            ELAN instance
     * @param parentRefName
     *            parent interface name
     * @return the name of the interface to be added to the ELAN instance i.e.
     *         trunk-member name for vlan network and transparent for flat
     *         network or null otherwise
     */
    private String createIetfInterfaces(ElanInstance elanInstance, String parentRefName) {
        String interfaceName = null;
        SegmentType segmentType = elanInstance.getSegmentType();
        Long segmentationId = elanInstance.getSegmentationId();

        if (SegmentType.Flat.equals(segmentType)) {
            interfaceName = parentRefName + ":transparent";
            createInterface(interfaceName, parentRefName, IfL2vlan.L2vlanMode.Transparent, null);
        } else if (SegmentType.Vlan.equals(segmentType) && segmentationId != null) {
            String trunkName = parentRefName + ":trunk";
            createInterface(trunkName, parentRefName, IfL2vlan.L2vlanMode.Trunk, null);
            interfaceName = parentRefName + ':' + segmentationId;
            createInterface(interfaceName, trunkName, IfL2vlan.L2vlanMode.TrunkMember, segmentationId);
        }

        return interfaceName;
    }

    private void createInterface(String interfaceName, String parentRefName,
            IfL2vlan.L2vlanMode l2VlanMode, Long vlanId) {
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder();
        IfL2vlanBuilder ifL2vlanBuilder = new IfL2vlanBuilder();
        ifL2vlanBuilder.setL2vlanMode(l2VlanMode);
        if (vlanId != null) {
            ifL2vlanBuilder.setVlanId(new VlanId(vlanId.intValue()));
        }
        if (parentRefName != null) {
            ParentRefsBuilder parentRefsBuilder = new ParentRefsBuilder().setParentInterface(parentRefName);
            interfaceBuilder.addAugmentation(ParentRefs.class, parentRefsBuilder.build());
        }

        interfaceBuilder.setEnabled(true).setName(interfaceName).setType(L2vlan.class)
        .addAugmentation(IfL2vlan.class, ifL2vlanBuilder.build());
        NeutronvpnUtils.createOfPortInterface(interfaceBuilder.build(), broker);
    }

    private void createElanInterface(String interfaceName, String elanInstanceName) {
        NeutronvpnUtils.createElanInterface(interfaceName, elanInstanceName, null, broker);
    }

}

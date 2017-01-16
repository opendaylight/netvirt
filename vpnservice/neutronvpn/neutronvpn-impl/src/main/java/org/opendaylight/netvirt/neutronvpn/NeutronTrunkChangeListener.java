/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunk.attributes.SubPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunks.attributes.Trunks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunks.attributes.trunks.Trunk;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronTrunkChangeListener extends AsyncDataTreeChangeListenerBase<Trunk, NeutronTrunkChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronTrunkChangeListener.class);
    private final DataBroker dataBroker;
    private final IInterfaceManager ifMgr;

    public NeutronTrunkChangeListener(final DataBroker dataBroker, IInterfaceManager ifMgr) {
        super();
        this.dataBroker = dataBroker;
        this.ifMgr = ifMgr;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Trunk> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Trunks.class).child(Trunk.class);
    }

    @Override
    protected NeutronTrunkChangeListener getDataTreeChangeListener() {
        return NeutronTrunkChangeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<Trunk> identifier, Trunk input) {
        Preconditions.checkNotNull(input.getPortId());
        LOG.trace("Adding Trunk : key: {}, value={}", identifier, input);
        Optional.ofNullable(input.getSubPorts()).ifPresent(subPorts
            -> subPorts.parallelStream().forEach(subPort
                -> createSubPortInterface(input, subPort)));
    }

    @Override
    protected void remove(InstanceIdentifier<Trunk> identifier, Trunk input) {
        Preconditions.checkNotNull(input.getPortId());
        LOG.trace("Removing Trunk : key: {}, value={}", identifier, input);
        Optional.ofNullable(input.getSubPorts()).ifPresent(subPorts
            -> subPorts.parallelStream().forEach(subPort
                -> deleteSubPortInterface(subPort)));
    }

    @Override
    protected void update(InstanceIdentifier<Trunk> identifier, Trunk original, Trunk update) {
        List<SubPorts> added = new ArrayList<>();
        List<SubPorts> deleted = new ArrayList<>();
        Optional<List<SubPorts>> updateOptional = Optional.ofNullable(update.getSubPorts());
        Optional<List<SubPorts>> originalOptional = Optional.ofNullable(original.getSubPorts());

        updateOptional.ifPresent(subPorts -> added.addAll(subPorts));
        originalOptional.ifPresent(subPorts -> deleted.addAll(subPorts));

        originalOptional.ifPresent(subPorts -> added.removeAll(subPorts));
        updateOptional.ifPresent(subPorts -> deleted.removeAll(subPorts));
        LOG.trace("Updating Trunk : key: {}. subPortsAdded={}, subPortsDeleted={}", identifier, added, deleted);
        deleted.parallelStream().forEach(subPort -> deleteSubPortInterface(subPort));
        added.parallelStream().forEach(subPort -> createSubPortInterface(update, subPort));
    }

    private void createSubPortInterface(Trunk trunk, SubPorts subPort) {
        if (!NetworkTypeVlan.class.equals(subPort.getSegmentationType())) {
            LOG.warn("SegmentationType other than VLAN not supported for Trunk:SubPorts");
            return;
        }
        String portName = subPort.getPortId().getValue();
        String parentName = trunk.getPortId().getValue();
        InstanceIdentifier<Interface> interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(portName);
        final DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();

        // Should we use parentName?
        dataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
            Interface iface = ifMgr.getInterfaceInfoFromConfigDataStore(portName);
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            if (iface == null) {
                /*
                 * Trunk creation requires NeutronPort to be present, by this time interface
                 * should've been created. In controller restart use case Interface would already be present.
                 * Clustering consideration:
                 *      This being same shard as NeutronPort, interface creation will be triggered on the same
                 *      node as this one. Use of DSJC helps ensure the order.
                 */
                LOG.warn("Interface not present for Trunk SubPort: {}", subPort);
                return futures;
            }
            InterfaceBuilder interfaceBuilder = new InterfaceBuilder();
            IfL2vlan ifL2vlan = new IfL2vlanBuilder().setL2vlanMode(IfL2vlan.L2vlanMode.TrunkMember)
                .setVlanId(new VlanId(subPort.getSegmentationId().intValue())).build();
            ParentRefs parentRefs = new ParentRefsBuilder().setParentInterface(parentName).build();
            interfaceBuilder.setName(portName).setType(L2vlan.class).addAugmentation(IfL2vlan.class, ifL2vlan)
                .addAugmentation(ParentRefs.class, parentRefs);
            iface = interfaceBuilder.build();
            /*
             * Interface is already created for parent NeutronPort. We're updating parent refs
             * and VLAN Information
             */
            WriteTransaction txn = dataBroker.newWriteOnlyTransaction();
            txn.merge(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, iface);
            LOG.trace("Creating trunk member interface {}", iface);
            futures.add(txn.submit());
            return futures;
        });
    }

    private void deleteSubPortInterface(SubPorts subPort) {
        String portName = subPort.getPortId().getValue();
        InstanceIdentifier<Interface> interfaceIdentifier =
                        NeutronvpnUtils.buildVlanInterfaceIdentifier(subPort.getPortId().getValue());
        final DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
            Interface iface = ifMgr.getInterfaceInfoFromConfigDataStore(portName);
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            if (iface == null) {
                LOG.warn("Interface not present for SubPort {}", subPort);
                return futures;
            }
            /*
             * We'll reset interface back to way it was? Can IFM handle parentRef delete?
             */
            InterfaceBuilder interfaceBuilder = new InterfaceBuilder(iface);
            // Reset augmentations
            interfaceBuilder.removeAugmentation(IfL2vlan.class).removeAugmentation(ParentRefs.class);
            IfL2vlan ifL2vlan = new IfL2vlanBuilder().setL2vlanMode(IfL2vlan.L2vlanMode.Trunk).build();
            interfaceBuilder.addAugmentation(IfL2vlan.class, ifL2vlan);
            iface = interfaceBuilder.build();
            /*
             * There is no means to do an update to remove elements from a node.
             * Our solution is to get existing iface, remove parentRef and VlanId,
             * and do a put to replace existing entry. This works out better as put
             * has better performance than merge.
             * Only drawback is any in-flight changes might be lost, but that is a corner case
             * and this being subport delete path, don't expect any significant changes to
             * corresponding Neutron Port. Deletion of NeutronPort should follow soon enough.
             */
            WriteTransaction txn = dataBroker.newWriteOnlyTransaction();
            txn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, iface);
            LOG.trace("Resetting trunk member interface {}", iface);
            futures.add(txn.submit());
            return futures;
        });

    }
}

/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.SplitHorizon;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.SplitHorizonBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunk.attributes.SubPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunks.attributes.Trunks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunks.attributes.trunks.Trunk;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronTrunkChangeListener extends AsyncDataTreeChangeListenerBase<Trunk, NeutronTrunkChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronTrunkChangeListener.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IInterfaceManager ifMgr;
    private final JobCoordinator jobCoordinator;

    @Inject
    public NeutronTrunkChangeListener(final DataBroker dataBroker, final IInterfaceManager ifMgr,
            final JobCoordinator jobCoordinator) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.ifMgr = ifMgr;
        this.jobCoordinator = jobCoordinator;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
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
        List<SubPorts> subPorts = input.getSubPorts();
        if (subPorts != null) {
            subPorts.forEach(subPort -> createSubPortInterface(input, subPort));
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Trunk> identifier, Trunk input) {
        Preconditions.checkNotNull(input.getPortId());
        LOG.trace("Removing Trunk : key: {}, value={}", identifier, input);
        List<SubPorts> subPorts = input.getSubPorts();
        if (subPorts != null) {
            subPorts.forEach(this::deleteSubPortInterface);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Trunk> identifier, Trunk original, Trunk update) {
        List<SubPorts> updatedSubPorts = update.getSubPorts();
        if (updatedSubPorts == null) {
            updatedSubPorts = Collections.emptyList();
        }
        List<SubPorts> originalSubPorts = original.getSubPorts();
        if (originalSubPorts == null) {
            originalSubPorts = Collections.emptyList();
        }
        List<SubPorts> added = new ArrayList<>(updatedSubPorts);
        added.removeAll(originalSubPorts);
        List<SubPorts> deleted = new ArrayList<>(originalSubPorts);
        deleted.removeAll(updatedSubPorts);

        LOG.trace("Updating Trunk : key: {}. subPortsAdded={}, subPortsDeleted={}", identifier, added, deleted);
        deleted.forEach(this::deleteSubPortInterface);
        added.forEach(subPort -> createSubPortInterface(update, subPort));
    }

    private void createSubPortInterface(Trunk trunk, SubPorts subPort) {
        if (!NetworkTypeVlan.class.equals(subPort.getSegmentationType())) {
            LOG.warn("SegmentationType other than VLAN not supported for Trunk:SubPorts");
            return;
        }
        String portName = subPort.getPortId().getValue();
        String parentName = trunk.getPortId().getValue();
        InstanceIdentifier<Interface> interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(portName);

        // Should we use parentName?
        jobCoordinator.enqueueJob("PORT- " + portName,
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(confTx -> {
                Interface iface = ifMgr.getInterfaceInfoFromConfigDataStore(confTx, portName);
                if (iface == null) {
                    /*
                     * Trunk creation requires NeutronPort to be present, by this time interface
                     * should've been created. In controller restart use case Interface would already be present.
                     * Clustering consideration:
                     *      This being same shard as NeutronPort, interface creation will be triggered on the same
                     *      node as this one. Use of DSJC helps ensure the order.
                     */
                    LOG.warn("Interface not present for Trunk SubPort: {}", subPort);
                    return;
                }
                IfL2vlan ifL2vlan = new IfL2vlanBuilder().setL2vlanMode(IfL2vlan.L2vlanMode.TrunkMember)
                        .setVlanId(new VlanId(subPort.getSegmentationId().intValue())).build();
                ParentRefs parentRefs = new ParentRefsBuilder().setParentInterface(parentName).build();
                SplitHorizon splitHorizon = new SplitHorizonBuilder().setOverrideSplitHorizonProtection(true).build();
                Interface newIface = new InterfaceBuilder()
                        .setName(portName)
                        .setType(L2vlan.class)
                        .addAugmentation(IfL2vlan.class, ifL2vlan)
                        .addAugmentation(ParentRefs.class, parentRefs)
                        .addAugmentation(SplitHorizon.class, splitHorizon)
                        .build();
                /*
                 * Interface is already created for parent NeutronPort. We're updating parent refs
                 * and VLAN Information
                 */
                confTx.merge(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, newIface);
                LOG.trace("Creating trunk member interface {}", newIface);
            })));
    }

    private void deleteSubPortInterface(SubPorts subPort) {
        String portName = subPort.getPortId().getValue();
        InstanceIdentifier<Interface> interfaceIdentifier =
                        NeutronvpnUtils.buildVlanInterfaceIdentifier(subPort.getPortId().getValue());
        jobCoordinator.enqueueJob("PORT- " + portName,
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(confTx -> {
                Interface iface = ifMgr.getInterfaceInfoFromConfigDataStore(confTx, portName);
                if (iface == null) {
                    LOG.warn("Interface not present for SubPort {}", subPort);
                    return;
                }
                /*
                 * We'll reset interface back to way it was? Can IFM handle parentRef delete?
                 */
                InterfaceBuilder interfaceBuilder = new InterfaceBuilder(iface);
                // Reset augmentations
                interfaceBuilder.removeAugmentation(IfL2vlan.class).removeAugmentation(ParentRefs.class)
                        .removeAugmentation(SplitHorizon.class);
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
                confTx.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, iface);
                LOG.trace("Resetting trunk member interface {}", iface);
            })));
    }
}

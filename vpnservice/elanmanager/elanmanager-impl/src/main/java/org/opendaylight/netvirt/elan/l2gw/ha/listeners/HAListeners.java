/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LogicalSwitchesCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.PhysicalLocatorCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.SwitchesCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TerminationPointCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TunnelCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TunnelIpCmd;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalSwitchAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Switches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.Tunnels;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.ChildOf;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HAListeners implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HAListeners.class);

    private final DataBroker broker;
    private final List<HwvtepNodeDataListener> listeners = new ArrayList<>();
    private final InstanceIdentifier physicalPortIid = InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
            .child(Node.class).child(TerminationPoint.class);

    public HAListeners(DataBroker broker) {
        this.broker = broker;
    }

    public void init() {
        registerListener(LocalMcastMacs.class, new LocalMcastCmd());
        registerListener(RemoteMcastMacs.class, new RemoteMcastCmd());
        registerListener(LocalUcastMacs.class, new LocalUcastCmd());
        registerListener(RemoteUcastMacs.class, new RemoteUcastCmd());
        registerListener(LogicalSwitches.class, new LogicalSwitchesCmd());
        registerListener(Switches.class, new SwitchesCmd());

        registerSwitchListener(TunnelIps.class, new TunnelIpCmd());
        registerSwitchListener(Tunnels.class, new TunnelCmd());

        PhysicalLocatorCmd physicalLocatorCmd = new PhysicalLocatorCmd();
        TerminationPointCmd terminationPointCmd = new TerminationPointCmd();
        listeners.add(new PhysicalLocatorListener(broker, PhysicalLocatorListener.class, physicalLocatorCmd,
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, true));
        listeners.add(new PhysicalLocatorListener(broker, PhysicalLocatorListener.class, physicalLocatorCmd,
                ResourceBatchingManager.ShardResource.OPERATIONAL_TOPOLOGY, true));

        listeners.add(new PhysicalLocatorListener(broker, PhysicalLocatorListener.class, terminationPointCmd,
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, false));
        listeners.add(new PhysicalLocatorListener(broker, PhysicalLocatorListener.class, terminationPointCmd,
                ResourceBatchingManager.ShardResource.OPERATIONAL_TOPOLOGY, false));
    }

    @Override
    public void close() throws Exception {
        for (HwvtepNodeDataListener listener : listeners) {
            listener.close();
        }
    }

    private <T extends ChildOf<HwvtepGlobalAttributes>> void registerListener(Class<T> clazz,
                                                                              MergeCommand mergeCommand) {
        GlobalAugmentationListener listener = new GlobalAugmentationListener(
                broker, clazz, HwvtepNodeDataListener.class, mergeCommand,
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY);
        listener.init();
        listeners.add(listener);

        if (mergeCommand instanceof RemoteUcastCmd) {
            return;
        }
        listener = new GlobalAugmentationListener(
                broker, clazz, HwvtepNodeDataListener.class, mergeCommand,
                ResourceBatchingManager.ShardResource.OPERATIONAL_TOPOLOGY);
        listener.init();
        listeners.add(listener);
    }

    private <T extends ChildOf<HwvtepPhysicalSwitchAttributes>> void registerSwitchListener(Class<T> clazz,
                                                                              MergeCommand mergeCommand) {
        SwitchAugmentationListener listener = new SwitchAugmentationListener(
                broker, clazz, HwvtepNodeDataListener.class, mergeCommand,
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY);
        listener.init();
        listeners.add(listener);

        listener = new SwitchAugmentationListener(
                broker, clazz, HwvtepNodeDataListener.class, mergeCommand,
                ResourceBatchingManager.ShardResource.OPERATIONAL_TOPOLOGY);
        listener.init();
        listeners.add(listener);
    }

    public static class GlobalAugmentationListener<T extends DataObject
            & ChildOf<HwvtepGlobalAttributes> & Identifiable> extends HwvtepNodeDataListener<T> {

        public GlobalAugmentationListener(DataBroker broker, Class<T> clazz, Class eventClazz,
                                   MergeCommand mergeCommand,
                                   ResourceBatchingManager.ShardResource datastoreType) {
            super(broker, clazz, eventClazz, mergeCommand, datastoreType);
        }

        protected InstanceIdentifier<T> getWildCardPath() {
            return InstanceIdentifier.create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                    .child(Node.class).augmentation(HwvtepGlobalAugmentation.class).child(clazz);
        }
    }

    public static class SwitchAugmentationListener<T extends DataObject
            & ChildOf<HwvtepPhysicalSwitchAttributes> & Identifiable> extends HwvtepNodeDataListener<T> {

        public SwitchAugmentationListener(DataBroker broker, Class<T> clazz, Class eventClazz,
                                          MergeCommand mergeCommand,
                                          ResourceBatchingManager.ShardResource datastoreType) {
            super(broker, clazz, eventClazz, mergeCommand, datastoreType);
        }

        protected InstanceIdentifier<T> getWildCardPath() {
            return InstanceIdentifier.create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                    .child(Node.class).augmentation(PhysicalSwitchAugmentation.class).child(clazz);
        }
    }

    class PhysicalLocatorListener extends HwvtepNodeDataListener<TerminationPoint> {

        boolean forGlobalNode = false;
        Predicate<InstanceIdentifier> predicate = (identifier) -> {
            return forGlobalNode && isGlobalNode(identifier) || (!forGlobalNode && !isGlobalNode(identifier));
        };

        PhysicalLocatorListener(DataBroker broker, Class eventClazz,
                                MergeCommand mergeCommand, ResourceBatchingManager.ShardResource datastoreType,
                                boolean forGlobalNode) {
            super(broker, TerminationPoint.class, eventClazz, mergeCommand, datastoreType);
            this.forGlobalNode = forGlobalNode;
        }

        @Override
        protected InstanceIdentifier getWildCardPath() {
            return physicalPortIid;
        }

        @Override
        protected void remove(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint dataRemoved) {
            if (predicate.test(identifier)) {
                super.remove(identifier, dataRemoved);
            }
        }

        @Override
        protected void update(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint before,
                              TerminationPoint after) {
            if (predicate.test(identifier)) {
                super.update(identifier, before, after);
            }
        }

        @Override
        protected void add(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint added) {
            if (predicate.test(identifier)) {
                super.add(identifier, added);
            }
        }

        boolean isGlobalNode(InstanceIdentifier<TerminationPoint> identifier) {
            return !identifier.firstKeyOf(Node.class).getNodeId().getValue()
                    .contains(HwvtepSouthboundConstants.PSWITCH_URI_PREFIX);
        }
    }
}

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
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LogicalSwitchesCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.PhysicalLocatorCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteUcastCmd;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.ChildOf;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class HAListeners implements AutoCloseable {
    private static final InstanceIdentifier<TerminationPoint> PHYSICAL_PORT_IID =
        InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
            .child(Node.class).child(TerminationPoint.class);

    private final DataBroker broker;
    private final List<HwvtepNodeDataListener<?>> listeners = new ArrayList<>();

    @Inject
    public HAListeners(DataBroker broker, HwvtepNodeHACache hwvtepNodeHACache) {
        this.broker = broker;
        registerListener(LocalMcastMacs.class, new LocalMcastCmd(), hwvtepNodeHACache);
        registerListener(RemoteMcastMacs.class, new RemoteMcastCmd(), hwvtepNodeHACache);
        registerListener(LocalUcastMacs.class, new LocalUcastCmd(), hwvtepNodeHACache);
        registerListener(RemoteUcastMacs.class, new RemoteUcastCmd(), hwvtepNodeHACache);
        registerListener(LogicalSwitches.class, new LogicalSwitchesCmd(), hwvtepNodeHACache);

        PhysicalLocatorCmd physicalLocatorCmd = new PhysicalLocatorCmd();
        listeners.add(new PhysicalLocatorListener(broker, hwvtepNodeHACache, physicalLocatorCmd,
                LogicalDatastoreType.CONFIGURATION));
        listeners.add(new PhysicalLocatorListener(broker, hwvtepNodeHACache, physicalLocatorCmd,
                LogicalDatastoreType.OPERATIONAL));
    }

    @Override
    @PreDestroy
    public void close() {
        for (HwvtepNodeDataListener listener : listeners) {
            listener.close();
        }
    }

    private <T extends ChildOf<HwvtepGlobalAttributes>> void registerListener(Class<T> clazz,
            MergeCommand<T, ?, ?> mergeCommand, HwvtepNodeHACache hwvtepNodeHACache) {
        listeners.add(new GlobalAugmentationListener(broker, hwvtepNodeHACache, clazz, HwvtepNodeDataListener.class,
                mergeCommand, LogicalDatastoreType.CONFIGURATION));
        listeners.add(new GlobalAugmentationListener(broker, hwvtepNodeHACache, clazz, HwvtepNodeDataListener.class,
                mergeCommand, LogicalDatastoreType.OPERATIONAL));
    }

    private static class GlobalAugmentationListener<T extends DataObject
            & ChildOf<HwvtepGlobalAttributes>> extends HwvtepNodeDataListener<T> {

        GlobalAugmentationListener(DataBroker broker, HwvtepNodeHACache hwvtepNodeHACache,
                                   Class<T> clazz, Class<HwvtepNodeDataListener<T>> eventClazz,
                                   MergeCommand<T, ?, ?> mergeCommand,
                                   LogicalDatastoreType datastoreType) {
            super(broker, hwvtepNodeHACache, clazz, eventClazz, mergeCommand, datastoreType);
        }

        @Override
        protected InstanceIdentifier<T> getWildCardPath() {
            return InstanceIdentifier.create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                    .child(Node.class).augmentation(HwvtepGlobalAugmentation.class).child(clazz);
        }
    }

    private static class PhysicalLocatorListener extends HwvtepNodeDataListener<TerminationPoint> {

        PhysicalLocatorListener(DataBroker broker, HwvtepNodeHACache hwvtepNodeHACache,
                MergeCommand<TerminationPoint, ?, ?> mergeCommand, LogicalDatastoreType datastoreType) {
            super(broker, hwvtepNodeHACache, TerminationPoint.class, (Class)PhysicalLocatorListener.class,
                    mergeCommand, datastoreType);
        }

        @Override
        protected InstanceIdentifier<TerminationPoint> getWildCardPath() {
            return PHYSICAL_PORT_IID;
        }

        @Override
        protected void remove(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint dataRemoved) {
            if (!isGlobalNode(identifier)) {
                return;
            }
            super.remove(identifier, dataRemoved);
        }

        @Override
        protected void update(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint before,
                              TerminationPoint after) {
            if (!isGlobalNode(identifier)) {
                return;
            }
            super.update(identifier, before, after);
        }

        @Override
        protected void add(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint added) {
            if (!isGlobalNode(identifier)) {
                return;
            }
            super.add(identifier, added);
        }

        boolean isGlobalNode(InstanceIdentifier<TerminationPoint> identifier) {
            return !identifier.firstKeyOf(Node.class).getNodeId().getValue()
                    .contains(HwvtepSouthboundConstants.PSWITCH_URI_PREFIX);
        }
    }
}

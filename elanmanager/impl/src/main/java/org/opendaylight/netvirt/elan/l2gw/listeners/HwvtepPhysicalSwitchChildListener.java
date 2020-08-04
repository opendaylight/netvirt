/*
 * Copyright © 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.StaleVlanBindingsCleaner;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class HwvtepPhysicalSwitchChildListener extends
    AbstractClusteredAsyncDataTreeChangeListener<PhysicalSwitchAugmentation> {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepPhysicalSwitchChildListener.class);

    static HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    private final L2GatewayCache l2GatewayCache;
    private final ElanClusterUtils elanClusterUtils;
    private final StaleVlanBindingsCleaner staleVlanBindingsCleaner;
    private final DataBroker dataBroker;

    @Inject
    public HwvtepPhysicalSwitchChildListener(L2GatewayCache l2GatewayCache,
                                             ElanClusterUtils elanClusterUtils,
                                             StaleVlanBindingsCleaner staleVlanBindingsCleaner,
                                             DataBroker dataBroker) {
        super(dataBroker,  DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class)
                .augmentation(PhysicalSwitchAugmentation.class)),
            Executors.newListeningSingleThreadExecutor("HwvtepPhysicalSwitchChildListener", LOG));

        this.l2GatewayCache = l2GatewayCache;
        this.elanClusterUtils = elanClusterUtils;
        this.staleVlanBindingsCleaner = staleVlanBindingsCleaner;
        this.dataBroker = dataBroker;
        init();
    }

    public void init() {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
        LOG.info("Registering HwvtepPhysicalSwitchChildListener");
        super.register();
    }

    @Override
    public void remove(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
                          PhysicalSwitchAugmentation del) {
    }

    @Override
    public void update(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
                          PhysicalSwitchAugmentation original,
                          PhysicalSwitchAugmentation update) {
    }

    @Override
    public void add(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
                       PhysicalSwitchAugmentation add) {
        if (HwvtepHACache.getInstance().isHAEnabledDevice(identifier)) {
            InstanceIdentifier<Node> childGlobalNodeIid = getManagedByNodeIid(identifier);
            InstanceIdentifier<Node> globalNodeIid = hwvtepHACache.getParent(childGlobalNodeIid);


            final String psName = getPsName(identifier);
            L2GatewayDevice l2GwDevice = l2GatewayCache.get(psName);
            if (l2GwDevice != null) {
                final String physName = l2GwDevice.getDeviceName();

                elanClusterUtils.runOnlyInOwnerNode(psName, "Stale entry cleanup on hwvtep disconnect", () -> {
                    String psNodeId = globalNodeIid.firstKeyOf(Node.class).getNodeId().getValue()
                            + HwvtepHAUtil.PHYSICALSWITCH + physName;
                    InstanceIdentifier<Node> psIid = HwvtepHAUtil.convertToInstanceIdentifier(psNodeId);
                    staleVlanBindingsCleaner.scheduleStaleCleanup(physName, globalNodeIid, psIid);
                    return Collections.emptyList();
                });
            }
        }
    }

    private InstanceIdentifier<Node> getManagedByNodeIid(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        String psNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            psNodeId = psNodeId.substring(0, psNodeId.indexOf(HwvtepHAUtil.PHYSICALSWITCH));
            return identifier.firstIdentifierOf(Topology.class).child(Node.class, new NodeKey(new NodeId(psNodeId)));
        }
        return null;
    }

    private String getPsName(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        String psNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            return psNodeId.substring(psNodeId.indexOf(HwvtepHAUtil.PHYSICALSWITCH) + HwvtepHAUtil.PHYSICALSWITCH
                    .length());
        }
        return null;
    }
}

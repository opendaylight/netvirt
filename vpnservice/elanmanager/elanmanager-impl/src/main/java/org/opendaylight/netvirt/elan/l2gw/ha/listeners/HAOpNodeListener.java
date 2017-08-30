/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.HAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.IHAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.INodeCopier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.managers.ManagerOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HAOpNodeListener extends HwvtepNodeBaseListener implements DataTreeChangeListener<Node>, AutoCloseable {

    public static final Logger LOG = LoggerFactory.getLogger(HAOpNodeListener.class);

    static HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    static BiPredicate<String, InstanceIdentifier<Node>> IS_PS_CHILD_TO_GLOBAL_NODE = (globalNodeId, iid) -> {
        String psNodeId = iid.firstKeyOf(Node.class).getNodeId().getValue();
        return psNodeId.startsWith(globalNodeId) && psNodeId.contains("physicalswitch");
    };

    static Predicate<InstanceIdentifier<Node>> IS_NOT_HA_CHILD = (iid) -> hwvtepHACache.getParent(iid) == null;

    private final IHAEventHandler haEventHandler;

    private final HAOpClusteredListener haOpClusteredListener;

    private ManagerListener managerListener;
    static HAOpNodeListener instance;
    INodeCopier nodeCopier;

    public HAOpNodeListener(DataBroker db, HAEventHandler haEventHandler,
                            HAOpClusteredListener haOpClusteredListener,
                            INodeCopier nodeCopier)
            throws Exception {
        super(OPERATIONAL, db);
        this.haEventHandler = haEventHandler;
        this.haOpClusteredListener = haOpClusteredListener;
        this.nodeCopier = nodeCopier;
        //this.managerListener = new ManagerListener(Managers.class, ManagerListener.class);
        instance = this;
    }

    public static HAOpNodeListener getInstance() {
        return instance;
    }

    @Override
    public void close() throws Exception {
        super.close();
        if (managerListener != null) {
            managerListener.close();
        }
    }

    String getNodeId(InstanceIdentifier<Node> iid) {
        return iid.firstKeyOf(Node.class).getNodeId().getValue();
    }

    @Override
    void onPsNodeUpdate(InstanceIdentifier<Node> childPath,
                        Node updatedChildPSNode,
                        Node originalChildPSNode,
                        ReadWriteTransaction tx) throws ReadFailedException {
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> childPsPath,
                        Node childPsNode,
                        ReadWriteTransaction tx) throws ReadFailedException {
        //one child ps node disconnected
        //find if all child ps nodes disconnected then delete parent ps node
        haOpClusteredListener.onPsNodeDelete(childPsPath, childPsNode, tx);
        InstanceIdentifier<Node> disconnectedChildGlobalPath = HwvtepHAUtil.getGlobalNodePathFromPSNode(childPsNode);
        if (IS_NOT_HA_CHILD.test(disconnectedChildGlobalPath)) {
            LOG.info("on non ha ps child delete {} ", getNodeId(childPsPath));
            return;
        }
        InstanceIdentifier<Node> haGlobalPath = hwvtepHACache.getParent(disconnectedChildGlobalPath);
        Set<InstanceIdentifier<Node>> childPsPaths = hwvtepHACache.getChildrenForHANode(haGlobalPath).stream()
                .map((childGlobalPath) -> HwvtepHAUtil.convertPsPath(childPsNode, childGlobalPath))
                .collect(Collectors.toSet());
        //TODO validate what if this is null
        if (haOpClusteredListener.getConnected(childPsPaths).isEmpty()) {
            InstanceIdentifier<Node> haPsPath = HwvtepHAUtil.convertPsPath(childPsNode, haGlobalPath);
            LOG.info("All child deleted for ha ps node {} ", HwvtepHAUtil.getNodeIdVal(haPsPath));
            HwvtepHAUtil.deleteNodeIfPresent(tx, LogicalDatastoreType.OPERATIONAL, haPsPath);
            //HwvtepHAUtil.deleteGlobalNodeSwitches(haGlobalPath, haPsPath, LogicalDatastoreType.OPERATIONAL, tx);
        } else {
            LOG.info("not all ha ps child deleted {} connected {}", getNodeId(childPsPath),
                    haOpClusteredListener.getConnected(childPsPaths));
        }
    }

    @Override
    void onGlobalNodeDelete(InstanceIdentifier<Node> childGlobalPath,
                            Node childNode,
                            ReadWriteTransaction tx) throws InterruptedException, ExecutionException,
            ReadFailedException {
        haOpClusteredListener.onGlobalNodeDelete(childGlobalPath, childNode, tx);
        if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
            LOG.info("non ha child global delete {} ", getNodeId(childGlobalPath));
            return;
        }
        LOG.info("ha child global delete {} ", getNodeId(childGlobalPath));
        InstanceIdentifier<Node> haNodePath = hwvtepHACache.getParent(childGlobalPath);
        Set<InstanceIdentifier<Node>> children = hwvtepHACache.getChildrenForHANode(haNodePath);
        if (haOpClusteredListener.getConnected(children).isEmpty()) {
            LOG.info("All child deleted for ha node {} ", HwvtepHAUtil.getNodeIdVal(haNodePath));
            //ha ps delete is taken care by ps node delete
            //HwvtepHAUtil.deleteSwitchesManagedBy-Node(haNodePath, tx);
            HwvtepHAUtil.deleteNodeIfPresent(tx, OPERATIONAL, haNodePath);
        } else {
            LOG.info("not all child deleted {} connected {}", getNodeId(childGlobalPath),
                    haOpClusteredListener.getConnected(children));
        }
    }

    //Update on global node has been taken care by HAListeners as per perf improvement
    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> childGlobalPath,
                            Node updatedChildNode,
                            Node originalChildNode,
                            ReadWriteTransaction tx) throws ReadFailedException {

        String oldHAId = HwvtepHAUtil.getHAIdFromManagerOtherConfig(originalChildNode);
        if (!Strings.isNullOrEmpty(oldHAId)) { //was already ha child
            //InstanceIdentifier<Node> haPath = hwvtepHACache.getParent(childGlobalPath);
            //haEventHandler.copyChildGlobalOpUpdateToHAParent(updatedChildNode, originalChildNode, haPath, tx);
            return;//TODO handle unha case
        }

        HAOpClusteredListener.addToHACacheIfBecameHAChild(childGlobalPath, updatedChildNode, originalChildNode, tx);
        if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
            return;
        }
        LOG.info("{} became ha child ", updatedChildNode.getNodeId().getValue());
        onGlobalNodeAdd(childGlobalPath, updatedChildNode, tx);
    }

    @Override
    public void onGlobalNodeAdd(InstanceIdentifier<Node> childGlobalPath,
                                Node childNode,
                                ReadWriteTransaction tx) {
        //copy child global node to ha global node
        //create ha global config node if not present
        //copy ha global config node to child global config node
        LOG.trace("Node connected {} - Checking if Ha or Non-Ha enabled ", childNode.getNodeId().getValue());
        haOpClusteredListener.onGlobalNodeAdd(childGlobalPath, childNode, tx);
        if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
            return;
        }
        InstanceIdentifier<Node> haNodePath = hwvtepHACache.getParent(childGlobalPath);
        LOG.trace("Ha enabled child node connected {}", childNode.getNodeId().getValue());
        try {
            nodeCopier.copyGlobalNode(childNode, childGlobalPath, haNodePath,  LogicalDatastoreType.OPERATIONAL, tx);
            nodeCopier.copyGlobalNode(null, haNodePath, childGlobalPath,  LogicalDatastoreType.CONFIGURATION, tx);
        } catch (ReadFailedException e) {
            LOG.error("Failed to read nodes {} , {} ", childGlobalPath, haNodePath);
        }
        readAndCopyChildPsOpToParent(childGlobalPath, childNode, haNodePath, tx);
    }

    private void readAndCopyChildPsOpToParent(InstanceIdentifier<Node> childGlobalPath,
                                              Node childNode,
                                              InstanceIdentifier<Node> haNodePath,
                                              ReadWriteTransaction tx) {
        LOG.error("Inside readAndCopyChildPsOpToParent");
        String childGlobalNodeId = childNode.getNodeId().getValue();
        List<InstanceIdentifier> childPsIids = new ArrayList<>();
        HwvtepGlobalAugmentation hwvtepGlobalAugmentation = childNode.getAugmentation(HwvtepGlobalAugmentation.class);
        if (hwvtepGlobalAugmentation == null || HwvtepHAUtil.isEmpty(hwvtepGlobalAugmentation.getSwitches())) {
            haOpClusteredListener.getConnectedNodes()
                    .stream()
                    .filter((connectedIid) -> IS_PS_CHILD_TO_GLOBAL_NODE.test(childGlobalNodeId, connectedIid))
                    .forEach((connectedIid) -> childPsIids.add(connectedIid));
        } else {
            hwvtepGlobalAugmentation.getSwitches().forEach(
                (switches) -> childPsIids.add(switches.getSwitchRef().getValue()));
        }
        if (childPsIids.isEmpty()) {
            LOG.error("No child ps found for global {}", childGlobalNodeId);
        }
        LOG.error("Got child PS node of size {}",childPsIids.size());

        childPsIids.forEach((psIid) -> {
            try {
                InstanceIdentifier<Node> childPsIid = psIid;
                Optional<Node> childPsNode = tx.read(LogicalDatastoreType.OPERATIONAL, childPsIid).checkedGet();
                if (childPsNode.isPresent()) {
                    LOG.error("Child oper PS node found");
                    onPsNodeAdd(childPsIid, childPsNode.get(), tx);
                } else {
                    LOG.error("Child oper ps node not found {}", childPsIid);
                }
            } catch (ReadFailedException e) {
                LOG.error("Failed to read child ps node {}", psIid);
            }
        });
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> childPsPath,
                     Node childPsNode,
                     ReadWriteTransaction tx) throws ReadFailedException {
        //copy child ps oper node to ha ps oper node
        //copy ha ps config node to child ps config
        haOpClusteredListener.onPsNodeAdd(childPsPath, childPsNode, tx);
        InstanceIdentifier<Node> childGlobalPath = HwvtepHAUtil.getGlobalNodePathFromPSNode(childPsNode);
        if (!haOpClusteredListener.getConnectedNodes().contains(childGlobalPath)) {
            return;
        }
        if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
            return;
        }
        LOG.info("ha ps child connected {} ", getNodeId(childPsPath));
        InstanceIdentifier<Node> haGlobalPath = hwvtepHACache.getParent(childGlobalPath);
        InstanceIdentifier<Node> haPsPath = HwvtepHAUtil.convertPsPath(childPsNode, haGlobalPath);
        try {
            nodeCopier.copyPSNode(childPsNode, childPsPath, haPsPath, haGlobalPath,
                    LogicalDatastoreType.OPERATIONAL, tx);
            nodeCopier.copyPSNode(null, haPsPath, childPsPath, childGlobalPath,
                    LogicalDatastoreType.CONFIGURATION, tx);
        } catch (ReadFailedException e) {
            LOG.error("Failed to read nodes {} , {} ", childPsPath, haGlobalPath);
        }
    }

    /**
     * ManagerListeners listens to manager updated and act in case non-ha node get converted to ha node.
     */
    class ManagerListener extends AsyncDataTreeChangeListenerBase<Managers, ManagerListener> {

        ManagerListener(Class<Managers> clazz, Class<ManagerListener> eventClazz) {
            super(clazz, eventClazz);
            registerListener(LogicalDatastoreType.OPERATIONAL, db);
        }

        @Override
        protected InstanceIdentifier<Managers> getWildCardPath() {
            return InstanceIdentifier.create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node
                            .class).augmentation(HwvtepGlobalAugmentation.class).child(clazz);
        }

        @Override
        protected void remove(InstanceIdentifier<Managers> instanceIdentifier, Managers managers) {

        }

        String getHaId(Managers managers) {
            if (managers.getManagerOtherConfigs() == null) {
                return null;
            }
            for (ManagerOtherConfigs configs : managers.getManagerOtherConfigs()) {
                if (configs.getOtherConfigKey().equals(HwvtepHAUtil.HA_ID)) {
                    return configs.getOtherConfigValue();
                }
            }
            return null;
        }

        @Override
        protected void update(InstanceIdentifier<Managers> instanceIdentifier, Managers oldData, Managers newData) {
            String oldHAId = getHaId(oldData);
            if (Strings.isNullOrEmpty(oldHAId)) {
                String newHAID = getHaId(newData);
                if (!Strings.isNullOrEmpty(newHAID)) {
                    InstanceIdentifier<Node> nodeIid = instanceIdentifier.firstIdentifierOf(Node.class);
                    ReadWriteTransaction tx = getTx();
                    try {
                        Node node = tx.read(LogicalDatastoreType.OPERATIONAL, nodeIid).checkedGet().get();
                        HAOpClusteredListener.addToCacheIfHAChildNode(nodeIid, node);
                        HAJobScheduler.getInstance().submitJob(() -> {
                            try {
                                ReadWriteTransaction tx1 = getTx();
                                onGlobalNodeAdd(nodeIid, node, tx1);
                                tx.submit().checkedGet();
                            } catch (TransactionCommitFailedException e) {
                                LOG.error("Failed to handle global node add", e);
                            }
                        });
                    } catch (ReadFailedException e) {
                        LOG.error("Read failed {}",e.getMessage());
                    }
                }
            }
        }

        //The add manager will be called once a new node connects which is handled by the base
        //HA node listener . In case the node been converted from non-ha to ha or vice versa
        //it will come as an update and has been handled above.
        //Hence no functionality has been added to add function here.
        @Override
        protected void add(InstanceIdentifier<Managers> instanceIdentifier, Managers managers) {
            LOG.trace("Manager added {}", instanceIdentifier.firstKeyOf(Node.class).getNodeId().getValue());
        }

        @Override
        protected ManagerListener getDataTreeChangeListener() {
            return ManagerListener.this;
        }
    }
}

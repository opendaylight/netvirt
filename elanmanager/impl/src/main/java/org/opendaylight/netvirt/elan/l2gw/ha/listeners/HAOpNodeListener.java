/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.HAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.IHAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.NodeCopier;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HAOpNodeListener extends HwvtepNodeBaseListener<Operational> implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(HAOpNodeListener.class);

    static BiPredicate<String, InstanceIdentifier<Node>> IS_PS_CHILD_TO_GLOBAL_NODE = (globalNodeId, iid) -> {
        String psNodeId = iid.firstKeyOf(Node.class).getNodeId().getValue();
        return psNodeId.startsWith(globalNodeId) && psNodeId.contains("physicalswitch");
    };

    static Predicate<InstanceIdentifier<Node>> IS_NOT_HA_CHILD = iid -> hwvtepHACache.getParent(iid) == null;

    private final IHAEventHandler haEventHandler;
    private final HAOpClusteredListener haOpClusteredListener;
    private final NodeCopier nodeCopier;
    private final IdManagerService idManager;

    @Inject
    public HAOpNodeListener(DataBroker db, HAEventHandler haEventHandler,
                            HAOpClusteredListener haOpClusteredListener,
                            NodeCopier nodeCopier,
                            final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                            final ServiceRecoveryRegistry serviceRecoveryRegistry,
                            final IdManagerService idManager) throws Exception {
        super(OPERATIONAL, db);
        this.haEventHandler = haEventHandler;
        this.haOpClusteredListener = haOpClusteredListener;
        this.nodeCopier = nodeCopier;
        this.idManager = idManager;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(db);
    }

    @Override
    @SuppressWarnings("all")
    public void registerListener() {
        try {
            LOG.info("Registering HAOpNodeListener");
            registerListener(OPERATIONAL, getDataBroker());
        } catch (Exception e) {
            LOG.error("HA OP Node register listener error.", e);
        }
    }

    @Override
    public void deregisterListener() {
        LOG.info("Deregistering HAOpNodeListener");
        super.close();
    }

    String getNodeId(InstanceIdentifier<Node> iid) {
        return iid.firstKeyOf(Node.class).getNodeId().getValue();
    }

    @Override
    public void onGlobalNodeAdd(InstanceIdentifier<Node> childGlobalPath,
                                Node childNode,
                                TypedReadWriteTransaction<Operational> tx) {
        //copy child global node to ha global node
        //create ha global config node if not present
        //copy ha global config node to child global config node
        LOG.info("HAOpNodeListener Node connected {} - Checking if Ha or Non-Ha enabled {}",
            childNode.getNodeId().getValue(), getManagers(childNode));
        haOpClusteredListener.onGlobalNodeAdd(childGlobalPath, childNode, tx);

        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, configTx -> {
            if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
                LOG.info("HAOpNodeListener The connected node is not a HA child {}",
                    childNode.getNodeId().getValue());
                if (hwvtepHACache.isHAParentNode(childGlobalPath)) {
                    LOG.info("HAOpNodeListener this is Parent Node {}",
                        childNode.getNodeId().getValue());
                    HwvtepGlobalAugmentation globalAugmentation = childNode
                        .augmentation(HwvtepGlobalAugmentation.class);
                    String operDbVersion = globalAugmentation.getDbVersion();

                    try {
                        Optional<Node> globalConfigNodeOptional = configTx.read(childGlobalPath).get();
                        if (globalConfigNodeOptional.isPresent()) {
                            HwvtepGlobalAugmentation globalConfigAugmentation = globalConfigNodeOptional
                                .get().augmentation(HwvtepGlobalAugmentation.class);
                            String configDbVersion = globalConfigAugmentation.getDbVersion();
                            if (operDbVersion != null && !operDbVersion.equals(configDbVersion)) {
                                LOG.info("Change in Db version from {} to {} for Node {}",
                                    configDbVersion, operDbVersion, childGlobalPath);
                                HwvtepGlobalAugmentationBuilder haBuilder =
                                    new HwvtepGlobalAugmentationBuilder(globalConfigAugmentation);
                                haBuilder.setDbVersion(operDbVersion);
                                NodeBuilder nodeBuilder = new NodeBuilder(childNode);
                                nodeBuilder.addAugmentation(haBuilder.build());
                                configTx.merge(childGlobalPath, nodeBuilder.build());
                            } else {
                                LOG.debug("No Change in Db version from {} to {} for Node {}",
                                    configDbVersion, operDbVersion, childGlobalPath);
                            }
                        }
                    } catch (ExecutionException | InterruptedException ex) {
                        LOG.error("HAOpNodeListener Failed to read node {} from Config DS",
                            childGlobalPath);
                    }

                }
                return;
            }
            InstanceIdentifier<Node> haNodePath = hwvtepHACache.getParent(childGlobalPath);
            LOG.info("HAOpNodeListener Ha enabled child node connected {} create parent oper node",
                childNode.getNodeId().getValue());
            try {
                nodeCopier.copyGlobalNode(Optional.ofNullable(childNode),
                    childGlobalPath, haNodePath, OPERATIONAL, tx);

                Optional<Node> existingDstGlobalNodeOptional = tx.read(haNodePath).get();
                List<Managers> managers = HwvtepHAUtil
                    .buildManagersForHANode(Optional.ofNullable(childNode).get(),
                        existingDstGlobalNodeOptional);

                Optional<Node> globalNodeOptional = configTx.read(haNodePath).get();
                if (globalNodeOptional.isPresent()) {
                    //Also update the manager section in config which helps in cluster reboot scenarios
                    managers.stream().forEach(manager -> {
                        InstanceIdentifier<Managers> managerIid = haNodePath
                            .augmentation(HwvtepGlobalAugmentation.class)
                            .child(Managers.class, manager.key());
                        configTx.put(managerIid, manager);
                    });
                    nodeCopier.copyGlobalNode(globalNodeOptional, haNodePath, childGlobalPath,
                        CONFIGURATION, tx);
                } else {
                    NodeBuilder nodeBuilder = new NodeBuilder().setNodeId(haNodePath
                        .firstKeyOf(Node.class).getNodeId());
                    HwvtepGlobalAugmentationBuilder augBuilder = new HwvtepGlobalAugmentationBuilder();
                    augBuilder.setManagers(managers);
                    if (existingDstGlobalNodeOptional.isPresent()) {
                        HwvtepGlobalAugmentation srcGlobalAugmentation =
                            existingDstGlobalNodeOptional.get()
                                .augmentation(HwvtepGlobalAugmentation.class);
                        if (srcGlobalAugmentation != null) {
                            augBuilder.setDbVersion(srcGlobalAugmentation.getDbVersion());
                        }
                    }
                    nodeBuilder.addAugmentation(augBuilder.build());
                    configTx.put(haNodePath, nodeBuilder.build());
                }
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("HAOpNodeListener Failed to read nodes {} , {} ", childGlobalPath,
                    haNodePath);
            }
        });
        readAndCopyChildPsOpToParent(childNode, tx);
    }

    public Object getManagers(Node node) {
        if (node.augmentation(HwvtepGlobalAugmentation.class) != null
                && node.augmentation(HwvtepGlobalAugmentation.class).getManagers() != null) {
            return node.augmentation(HwvtepGlobalAugmentation.class).getManagers();
        }
        return node;
    }

    //Update on global node has been taken care by HAListeners as per perf improvement
    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> childGlobalPath,
                            Node updatedChildNode,
                            Node originalChildNode,
                            DataObjectModification<Node> mod,
                            TypedReadWriteTransaction<Operational> tx) {

        LOG.trace("Node updated {} {}", updatedChildNode, originalChildNode);

        String oldHAId = HwvtepHAUtil.getHAIdFromManagerOtherConfig(originalChildNode);
        if (!Strings.isNullOrEmpty(oldHAId)) { //was already ha child
            InstanceIdentifier<Node> haPath = hwvtepHACache.getParent(childGlobalPath);
            LOG.debug("Copy oper update from child {} to parent {}", childGlobalPath, haPath);
            ((BatchedTransaction)tx).setSrcNodeId(updatedChildNode.getNodeId());
            ((BatchedTransaction)tx).updateMetric(true);
            haEventHandler.copyChildGlobalOpUpdateToHAParent(haPath, mod, tx);
            return;//TODO handle unha case
        }

        HAOpClusteredListener.addToHACacheIfBecameHAChild(childGlobalPath, updatedChildNode, originalChildNode);
        if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
            if (!hwvtepHACache.isHAParentNode(childGlobalPath)) {
                //TODO error
                LOG.trace("Connected node is not ha child {}", updatedChildNode);
            }
            return;
        }
        LOG.info("HAOpNodeListener {} became ha child ", updatedChildNode.getNodeId().getValue());
        onGlobalNodeAdd(childGlobalPath, updatedChildNode, tx);
    }

    @Override
    void onGlobalNodeDelete(InstanceIdentifier<Node> childGlobalPath,
                            Node childNode,
                            TypedReadWriteTransaction<Operational> tx) {
        haOpClusteredListener.onGlobalNodeDelete(childGlobalPath, childNode, tx);
        if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
            LOG.info("HAOpNodeListener non ha child global delete {} ", getNodeId(childGlobalPath));
            return;
        }
        LOG.info("HAOpNodeListener ha child global delete {} ", getNodeId(childGlobalPath));
        InstanceIdentifier<Node> haNodePath = hwvtepHACache.getParent(childGlobalPath);
        Set<InstanceIdentifier<Node>> children = hwvtepHACache.getChildrenForHANode(haNodePath);
        if (haOpClusteredListener.getConnected(children).isEmpty()) {
            LOG.info("HAOpNodeListener All child deleted for ha node {} ", HwvtepHAUtil.getNodeIdVal(haNodePath));
            //ha ps delete is taken care by ps node delete
            //HwvtepHAUtil.deleteSwitchesManagedBy-Node(haNodePath, tx);
            try {
                HwvtepHAUtil.deleteNodeIfPresent(tx, haNodePath);
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("HAOpNodeListener HA Node Delete failed {}", haNodePath);
            }
        } else {
            LOG.info("HAOpNodeListener not all child deleted {} connected {}", getNodeId(childGlobalPath),
                    haOpClusteredListener.getConnected(children));
        }
    }

    @Override
    public void onPsNodeAdd(InstanceIdentifier<Node> childPsPath,
                            Node childPsNode,
                            TypedReadWriteTransaction<Operational> tx) {
        //copy child ps oper node to ha ps oper node
        //copy ha ps config node to child ps config
        haOpClusteredListener.onPsNodeAdd(childPsPath, childPsNode, tx);
        InstanceIdentifier<Node> childGlobalPath = HwvtepHAUtil
            .getGlobalNodePathFromPSNode(childPsNode);
        if (!haOpClusteredListener.getConnectedNodes().contains(childGlobalPath)) {
            LOG.error("HAOpNodeListener Ignoring ps node add as global node not found {}",
                childPsNode.getNodeId().getValue());
            return;
        }
        if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
            if (!hwvtepHACache.isHAParentNode(childGlobalPath)) {
                LOG.error("HAOpNodeListener Ignoring ps node add as the node is not ha child {}",
                    childPsNode.getNodeId().getValue());
            }
            return;
        }
        LOG.info("HAOpNodeListener Ha ps child connected {} ", getNodeId(childPsPath));
        InstanceIdentifier<Node> haGlobalPath = hwvtepHACache.getParent(childGlobalPath);
        InstanceIdentifier<Node> haPsPath = HwvtepHAUtil.convertPsPath(childPsNode, haGlobalPath);
        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, configTx -> {
            try {
                nodeCopier
                    .copyPSNode(Optional.ofNullable(childPsNode), childPsPath, haPsPath, haGlobalPath,
                        OPERATIONAL, tx);

                Optional<Node> haPsNodeOptional = configTx.read(haPsPath).get();
                if (haPsNodeOptional.isPresent()) {
                    nodeCopier.copyPSNode(haPsNodeOptional, haPsPath, childPsPath, childGlobalPath,
                        CONFIGURATION, tx);
                } else {
                    PhysicalSwitchAugmentationBuilder psBuilder = new PhysicalSwitchAugmentationBuilder();
                    PhysicalSwitchAugmentation srcPsAugmentation = childPsNode
                        .augmentation(PhysicalSwitchAugmentation.class);
                    if (srcPsAugmentation != null) {
                        psBuilder.setTunnelIps(srcPsAugmentation.getTunnelIps());
                    } else {
                        LOG.error("Physical Switch Augmentation is null for the child ps node: {}",
                            childPsNode);
                    }
                    //setting tunnel ip and termination points in the parent node
                    List<TerminationPoint> terminationPoints = getTerminationPointForConfig(
                        childPsNode);
//                for (TerminationPoint terminationPoint: terminationPoints) {
//                    HwvtepTerminationPointCache.getInstance().addTerminationPoint(haGlobalPath, terminationPoint);
//                }
                    NodeBuilder nodeBuilder = new NodeBuilder()
                        .setNodeId(haPsPath.firstKeyOf(Node.class).getNodeId());
                    nodeBuilder.addAugmentation(psBuilder.build());
                    LOG.info("HAOpNodeListener creating the HAParent PhysicalSwitch {}", haPsPath);
                    configTx.put(haPsPath, nodeBuilder
                        .setTerminationPoint(terminationPoints).build());
                }
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("Failed to read nodes {} , {} ", childPsPath, haGlobalPath);
            }
        });
    }

    private List<TerminationPoint> getTerminationPointForConfig(Node childPsNode) {
        List<TerminationPoint> configTPList = new ArrayList<>();
        if (childPsNode != null && childPsNode.getTerminationPoint() != null) {
            childPsNode.getTerminationPoint().values().forEach(operTerminationPoint -> {
                TerminationPointBuilder tpBuilder = new TerminationPointBuilder(operTerminationPoint);
                tpBuilder.removeAugmentation(HwvtepPhysicalPortAugmentation.class);
                HwvtepPhysicalPortAugmentation operPPAugmentation =
                    operTerminationPoint. augmentation(HwvtepPhysicalPortAugmentation.class);
                HwvtepPhysicalPortAugmentationBuilder tpAugmentationBuilder =
                    new HwvtepPhysicalPortAugmentationBuilder();
                tpAugmentationBuilder.setAclBindings(operPPAugmentation.getAclBindings());
                tpAugmentationBuilder
                    .setHwvtepNodeDescription(operPPAugmentation.getHwvtepNodeDescription());
                tpAugmentationBuilder.setHwvtepNodeName(operPPAugmentation.getHwvtepNodeName());
                tpAugmentationBuilder.setPhysicalPortUuid(operPPAugmentation.getPhysicalPortUuid());
                tpAugmentationBuilder.setVlanStats(operPPAugmentation.getVlanStats());
                tpAugmentationBuilder.setVlanBindings(operPPAugmentation.getVlanBindings());

                tpBuilder.addAugmentation(tpAugmentationBuilder.build());
                configTPList.add(tpBuilder.build());
            });
        }
        return configTPList;
    }

    @Override
    void onPsNodeUpdate(Node updatedChildPSNode,
                        DataObjectModification<Node> mod,
                        TypedReadWriteTransaction<Operational> tx) {
        InstanceIdentifier<Node> childGlobalPath = HwvtepHAUtil.getGlobalNodePathFromPSNode(updatedChildPSNode);
        if (IS_NOT_HA_CHILD.test(childGlobalPath)) {
            return;
        }
        //tunnel ip and termination points from child to parent
        InstanceIdentifier<Node> haGlobalPath = hwvtepHACache.getParent(childGlobalPath);
        ((BatchedTransaction)tx).setSrcNodeId(updatedChildPSNode.getNodeId());
        ((BatchedTransaction)tx).updateMetric(true);
        haEventHandler.copyChildPsOpUpdateToHAParent(updatedChildPSNode, haGlobalPath, mod, tx);
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> childPsPath,
                        Node childPsNode,
                        TypedReadWriteTransaction<Operational> tx) {
        //one child ps node disconnected
        //find if all child ps nodes disconnected then delete parent ps node
        haOpClusteredListener.onPsNodeDelete(childPsPath, childPsNode, tx);
        InstanceIdentifier<Node> disconnectedChildGlobalPath = HwvtepHAUtil.getGlobalNodePathFromPSNode(childPsNode);
        if (IS_NOT_HA_CHILD.test(disconnectedChildGlobalPath)) {
            LOG.info("HAOpNodeListener on non ha ps child delete {} ", getNodeId(childPsPath));
            return;
        }
        InstanceIdentifier<Node> haGlobalPath = hwvtepHACache.getParent(disconnectedChildGlobalPath);
        Set<InstanceIdentifier<Node>> childPsPaths = hwvtepHACache.getChildrenForHANode(haGlobalPath).stream()
                .map(childGlobalPath -> HwvtepHAUtil.convertPsPath(childPsNode, childGlobalPath))
                .collect(Collectors.toSet());
        //TODO validate what if this is null
        if (haOpClusteredListener.getConnected(childPsPaths).isEmpty()) {
            InstanceIdentifier<Node> haPsPath = HwvtepHAUtil.convertPsPath(childPsNode, haGlobalPath);
            LOG.info("HAOpNodeListener All child deleted for ha ps node {} ", HwvtepHAUtil.getNodeIdVal(haPsPath));
            try {
                HwvtepHAUtil.deleteNodeIfPresent(tx, haPsPath);
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("HAOpNodeListener Exception While Delete HA PS Node : {}", haPsPath);
            }
            //HwvtepHAUtil.deleteGlobalNodeSwitches(haGlobalPath, haPsPath, LogicalDatastoreType.OPERATIONAL, tx);
        } else {
            LOG.info("HAOpNodeListener not all ha ps child deleted {} connected {}", getNodeId(childPsPath),
                    haOpClusteredListener.getConnected(childPsPaths));
        }
    }

    private void readAndCopyChildPsOpToParent(Node childNode, TypedReadWriteTransaction<Operational> tx) {
        String childGlobalNodeId = childNode.getNodeId().getValue();
        List<InstanceIdentifier> childPsIids = new ArrayList<>();
        HwvtepGlobalAugmentation hwvtepGlobalAugmentation = childNode.augmentation(HwvtepGlobalAugmentation.class);
        if (hwvtepGlobalAugmentation == null
            || HwvtepHAUtil.isEmpty(hwvtepGlobalAugmentation.nonnullSwitches().values())) {
            haOpClusteredListener.getConnectedNodes()
                    .stream()
                    .filter(connectedIid -> IS_PS_CHILD_TO_GLOBAL_NODE.test(childGlobalNodeId, connectedIid))
                    .forEach(connectedIid -> childPsIids.add(connectedIid));
        } else {
            hwvtepGlobalAugmentation.getSwitches().values().forEach(
                switches -> childPsIids.add(switches.getSwitchRef().getValue()));
        }
        if (childPsIids.isEmpty()) {
            LOG.info("HAOpNodeListener No child ps found for global {}", childGlobalNodeId);
        }
        childPsIids.forEach(psIid -> {
            try {
                InstanceIdentifier<Node> childPsIid = psIid;
                Optional<Node> childPsNode = tx.read(childPsIid).get();
                if (childPsNode.isPresent()) {
                    LOG.debug("Child oper PS node found");
                    onPsNodeAdd(childPsIid, childPsNode.get(), tx);
                } else {
                    LOG.error("HAOpNodeListener Child oper ps node not found {}", childPsIid);
                }
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("HAOpNodeListener Failed to read child ps node {}", psIid);
            }
        });
    }
}

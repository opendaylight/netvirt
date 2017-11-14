/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.SwitchesCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.HAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.IHAEventHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Switches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class HAOpNodeListener extends HwvtepNodeBaseListener {

    private final IHAEventHandler haEventHandler;

    private final Map<String, Boolean> availableGlobalNodes = new HashMap<>();
    private final Map<String, Boolean> availablePsNodes = new HashMap<>();

    public HAOpNodeListener(DataBroker db, HAEventHandler haEventHandler) throws Exception {
        super(OPERATIONAL, db);
        this.haEventHandler = haEventHandler;
        LOG.info("Registering HwvtepDataChangeListener for operational nodes");
    }

    void clearNodeAvailability(InstanceIdentifier<Node> key) {
        String id = key.firstKeyOf(Node.class).getNodeId().getValue();
        if (id.indexOf(HwvtepHAUtil.PHYSICALSWITCH) >= 0) {
            availablePsNodes.remove(id.substring(0, id.indexOf(HwvtepHAUtil.PHYSICALSWITCH)));
        } else {
            availableGlobalNodes.remove(id);
        }
    }

    void updateNodeAvailability(InstanceIdentifier<Node> key) {
        String id = key.firstKeyOf(Node.class).getNodeId().getValue();
        if (id.indexOf(HwvtepHAUtil.PHYSICALSWITCH) >= 0) {
            availablePsNodes.put(id.substring(0, id.indexOf(HwvtepHAUtil.PHYSICALSWITCH)), Boolean.TRUE);
        } else {
            availableGlobalNodes.put(id, Boolean.TRUE);
        }
    }

    boolean areBothGlobalAndPsNodeAvailable(InstanceIdentifier<Node> key) {
        String id = key.firstKeyOf(Node.class).getNodeId().getValue();
        String globalId;

        if (id.indexOf(HwvtepHAUtil.PHYSICALSWITCH) >= 0) {
            globalId = id.substring(0, id.indexOf(HwvtepHAUtil.PHYSICALSWITCH));
        } else {
            globalId = id;
        }
        return availableGlobalNodes.containsKey(globalId) && availablePsNodes.containsKey(globalId);
    }

    @Override
    void onGlobalNodeAdd(InstanceIdentifier<Node> childPath,
                         Node childNode,
                         ReadWriteTransaction tx) {
        LOG.trace("Node connected {} - Checking if Ha or Non-Ha enabled ", childNode.getNodeId().getValue());
        //update cache
        HAOpClusteredListener.addToCacheIfHAChildNode(childPath, childNode);
        if (!hwvtepHACache.isHAEnabledDevice(childPath)) {
            LOG.trace("Non ha node connected {}", childNode.getNodeId().getValue());
            return;
        }
        hwvtepHACache.updateConnectedNodeStatus(childPath);
        LOG.trace("Ha enabled child node connected {}", childNode.getNodeId().getValue());
        InstanceIdentifier<Node> haNodePath = hwvtepHACache.getParent(childPath);
        updateNodeAvailability(childPath);
        if (areBothGlobalAndPsNodeAvailable(childPath)) {
            readHAConfigNodeAndMergeData(childPath, childNode, haNodePath, tx);
        }
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> childPath,
                        Node childNode,
                        ReadWriteTransaction tx) {
        clearNodeAvailability(childPath);
    }

    @Override
    void onGlobalNodeDelete(InstanceIdentifier<Node> childPath,
                            Node childNode,
                            ReadWriteTransaction tx) throws InterruptedException, ExecutionException,
            ReadFailedException {
        clearNodeAvailability(childPath);
        if (!hwvtepHACache.isHAEnabledDevice(childPath)) {
            return;
        }
        //If all child nodes disconnect remove parent from operational datastore
        InstanceIdentifier<Node> haNodePath = hwvtepHACache.getParent(childPath);
        if (haNodePath != null) {
            Set<InstanceIdentifier<Node>> children = hwvtepHACache.getChildrenForHANode(haNodePath);
            children.remove(childPath);
            hwvtepHACache.updateDisconnectedNodeStatus(childPath);
            if (HwvtepHAUtil.areAllChildDeleted(children, tx)) {
                LOG.info("All child deleted for ha node {} ", HwvtepHAUtil.getNodeIdVal(haNodePath));
                HwvtepHAUtil.deleteSwitchesManagedByNode(haNodePath, tx);
                HwvtepHAUtil.deleteNodeIfPresent(tx, OPERATIONAL, haNodePath);
            }
        }
    }

    //Update on global node has been taken care by HAListeners as per perf improvement
    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> childPath,
                            Node updatedChildNode,
                            Node originalChildNode,
                            ReadWriteTransaction tx) throws ReadFailedException {

        String oldHAId = HwvtepHAUtil.getHAIdFromManagerOtherConfig(originalChildNode);
        if (!Strings.isNullOrEmpty(oldHAId)) { //was already ha child
            //The following update is taken care by HAListeners
            //InstanceIdentifier<Node> haPath = hwvtepHACache.getParent(childPath);
            //haEventHandler.copyChildGlobalOpUpdateToHAParent(updatedChildNode, originalChildNode, haPath, tx);
            return;//TODO handle unha case
        }

        HAOpClusteredListener.addToHACacheIfBecameHAChild(childPath, updatedChildNode, originalChildNode, tx);
        boolean becameHAChild = hwvtepHACache.isHAEnabledDevice(childPath);
        if (becameHAChild) {
            hwvtepHACache.updateConnectedNodeStatus(childPath);
            LOG.info("{} became ha child ", updatedChildNode.getNodeId().getValue());
            onGlobalNodeAdd(childPath, updatedChildNode, tx);
        }
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> childPath,
                     Node childPsNode,
                     ReadWriteTransaction tx) throws ReadFailedException {
        updateNodeAvailability(childPath);
        if (areBothGlobalAndPsNodeAvailable(childPath)) {
            InstanceIdentifier<Node> globalPath = HwvtepHAUtil.getGlobalNodePathFromPSNode(childPsNode);
            Node globalNode = HwvtepHAUtil.readNode(tx, OPERATIONAL, globalPath);
            onGlobalNodeAdd(globalPath, globalNode, tx);
        }
    }

    @Override
    void onPsNodeUpdate(InstanceIdentifier<Node> childPath,
                        Node updatedChildPSNode,
                        Node originalChildPSNode,
                        ReadWriteTransaction tx) throws ReadFailedException {

        InstanceIdentifier<Node> globalNodePath = HwvtepHAUtil.getGlobalNodePathFromPSNode(updatedChildPSNode);
        if (hwvtepHACache.isHAEnabledDevice(globalNodePath) && areBothGlobalAndPsNodeAvailable(childPath)) {
            InstanceIdentifier<Node> haPath = hwvtepHACache.getParent(globalNodePath);
            haEventHandler.copyChildPsOpUpdateToHAParent(updatedChildPSNode, originalChildPSNode, haPath, tx);
        }
    }

    public void readHAConfigNodeAndMergeData(final InstanceIdentifier<Node> childPath,
                                             final Node childNode,
                                             final InstanceIdentifier<Node> haNodePath,
                                             final ReadWriteTransaction tx) {
        if (haNodePath == null) {
            return;
        }
        ListenableFuture<Optional<Node>> ft = tx.read(CONFIGURATION, haNodePath);
        Futures.addCallback(ft, new FutureCallback<Optional<Node>>() {
            @Override
            public void onSuccess(@Nonnull final Optional<Node> haGlobalCfg) {
                if (haGlobalCfg.isPresent()) {
                    if (childNode.getAugmentation(HwvtepGlobalAugmentation.class) != null) {
                        List<Switches> switches =
                                childNode.getAugmentation(HwvtepGlobalAugmentation.class).getSwitches();
                        if (switches != null) {
                            SwitchesCmd cmd = new SwitchesCmd();
                            for (Switches ps : switches) {
                                ReadWriteTransaction tx = getTx();
                                Switches dst = cmd.transform(haNodePath, ps);
                                ListenableFuture<Optional<Node>> ft = tx.read(CONFIGURATION,
                                        (InstanceIdentifier<Node>) dst.getSwitchRef().getValue());
                                Futures.addCallback(ft, new FutureCallback<Optional<Node>>() {
                                    @Override
                                    public void onSuccess(Optional<Node> haPSCfg) {
                                        handleNodeReConnected(childPath, childNode, haNodePath, haGlobalCfg, haPSCfg);
                                    }

                                    @Override
                                    public void onFailure(Throwable throwable) {
                                    }
                                }, MoreExecutors.directExecutor());
                                break;//TODO handle all switches instead of just one switch
                            }
                        } else {
                            Optional<Node> psNodeOptional = Optional.absent();
                            handleNodeReConnected(childPath, childNode, haNodePath, haGlobalCfg, psNodeOptional);
                        }
                    }
                } else {
                    handleNodeConnected(childPath, childNode, haNodePath);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        }, MoreExecutors.directExecutor());
    }

    void handleNodeConnected(final InstanceIdentifier<Node> childPath,
                             final Node childNode,
                             final InstanceIdentifier<Node> haNodePath) {
        HAJobScheduler.getInstance().submitJob(() -> {
            try {
                LOG.trace("Ha child connected handleNodeConnected {}", childNode.getNodeId().getValue());
                ReadWriteTransaction tx = getTx();
                haEventHandler.handleChildNodeConnected(childNode, childPath, haNodePath, tx);
                tx.submit().get();
            } catch (InterruptedException | ExecutionException | ReadFailedException e) {
                LOG.error("Failed to process ", e);
            }
        });
    }

    void handleNodeReConnected(final InstanceIdentifier<Node> childPath,
                               final Node childNode,
                               final InstanceIdentifier<Node> haNodePath,
                               final Optional<Node> haGlobalCfg,
                               final Optional<Node> haPSCfg) {
        HAJobScheduler.getInstance().submitJob(() -> {
            try {
                LOG.trace("Ha child reconnected handleNodeReConnected {}", childNode.getNodeId().getValue());
                ReadWriteTransaction tx = getTx();
                haEventHandler.handleChildNodeReConnected(childNode, childPath,
                        haNodePath, haGlobalCfg, haPSCfg, tx);
                tx.submit().get();
            } catch (InterruptedException | ExecutionException | ReadFailedException e) {
                LOG.error("Failed to process ", e);
            }
        });
    }
}

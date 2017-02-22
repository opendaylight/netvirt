/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowProperties;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FederationPluginCleaner {
    private static final Logger LOG = LoggerFactory.getLogger(FederationPluginCleaner.class);
    private static final int MAX_TRANSACTION_DELETE_RETRIES = 5;
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);

    public static synchronized void removeOldGenerationFederatedEntities(DataBroker db, final int generationNumber,
        String remoteIp) {
        boolean somethingDeleted = false;

        if (deleteVpnInterface(db, LogicalDatastoreType.OPERATIONAL, MAX_TRANSACTION_DELETE_RETRIES, vpnInterface -> {
            VpnShadowProperties vpnShadowProperties = vpnInterface.getAugmentation(VpnShadowProperties.class);
            return vpnShadowProperties != null && Boolean.TRUE.equals(vpnShadowProperties.isShadow())
                && generationNumber != vpnShadowProperties.getGenerationNumber()
                && remoteIp.equals(vpnShadowProperties.getRemoteIp());
        })) {
            somethingDeleted = true;
        }

        if (deleteVpnInterface(db, LogicalDatastoreType.CONFIGURATION, MAX_TRANSACTION_DELETE_RETRIES, vpnInterface -> {
            VpnShadowProperties vpnShadowProperties = vpnInterface.getAugmentation(VpnShadowProperties.class);
            return vpnShadowProperties != null && Boolean.TRUE.equals(vpnShadowProperties.isShadow())
                && generationNumber != vpnShadowProperties.getGenerationNumber()
                && remoteIp.equals(vpnShadowProperties.getRemoteIp());
        })) {
            somethingDeleted = true;
        }

        if (deleteElanInterfacesShadows(db, LogicalDatastoreType.CONFIGURATION, MAX_TRANSACTION_DELETE_RETRIES,
            elanInterface -> {
                ElanShadowProperties elanShadowProperties = elanInterface.getAugmentation(ElanShadowProperties.class);
                return elanShadowProperties != null && Boolean.TRUE.equals(elanShadowProperties.isShadow())
                    && generationNumber != elanShadowProperties.getGenerationNumber()
                    && remoteIp.equals(elanShadowProperties.getRemoteIp());
            })) {
            somethingDeleted = true;
        }

        sleepIfSomethingWasDeleted(somethingDeleted);

        deleteInterfacesShadows(db, LogicalDatastoreType.CONFIGURATION, MAX_TRANSACTION_DELETE_RETRIES, iface -> {
            IfShadowProperties ifShadowProperties = iface.getAugmentation(IfShadowProperties.class);
            return ifShadowProperties != null && Boolean.TRUE.equals(ifShadowProperties.isShadow())
                && generationNumber != ifShadowProperties.getGenerationNumber()
                && remoteIp.equals(ifShadowProperties.getRemoteIp());
        });

        EXECUTOR.schedule(() -> {

            deleteInventoryNodes(db, LogicalDatastoreType.OPERATIONAL, MAX_TRANSACTION_DELETE_RETRIES,
                    new IEntityDeleteDecision<Node>() {
                        @Override
                        public boolean shouldDelete(Node node) {
                            InventoryNodeShadowProperties nodeShadowProperties = node
                                    .getAugmentation(InventoryNodeShadowProperties.class);
                            return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                                    && (generationNumber != nodeShadowProperties.getGenerationNumber())
                                    && (remoteIp.equals(nodeShadowProperties.getRemoteIp()));
                        }
                    });

            deleteInventoryNodes(db, LogicalDatastoreType.CONFIGURATION, MAX_TRANSACTION_DELETE_RETRIES,
                    new IEntityDeleteDecision<Node>() {
                        @Override
                        public boolean shouldDelete(Node node) {
                            InventoryNodeShadowProperties nodeShadowProperties = node
                                    .getAugmentation(InventoryNodeShadowProperties.class);
                            return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                                    && (generationNumber != nodeShadowProperties.getGenerationNumber())
                                    && (remoteIp.equals(nodeShadowProperties.getRemoteIp()));
                        }
                    });

            deleteTopologyShadowNodes(db, LogicalDatastoreType.OPERATIONAL, MAX_TRANSACTION_DELETE_RETRIES,
                    new IEntityDeleteDecision<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology
                    .rev131021.network.topology.topology.Node>() {
                        @Override
                        public boolean shouldDelete(
                                org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021
                                .network.topology.topology.Node node) {
                            TopologyNodeShadowProperties nodeShadowProperties = node
                                    .getAugmentation(TopologyNodeShadowProperties.class);
                            return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                                    && (generationNumber != nodeShadowProperties.getGenerationNumber())
                                    && (remoteIp.equals(nodeShadowProperties.getRemoteIp()));
                        }
                    });

            deleteTopologyShadowNodes(db, LogicalDatastoreType.CONFIGURATION, MAX_TRANSACTION_DELETE_RETRIES,
                    new IEntityDeleteDecision<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology
                    .rev131021.network.topology.topology.Node>() {
                        @Override
                        public boolean shouldDelete(
                                org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021
                                .network.topology.topology.Node node) {
                            TopologyNodeShadowProperties nodeShadowProperties = node
                                    .getAugmentation(TopologyNodeShadowProperties.class);
                            return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                                    && (generationNumber > nodeShadowProperties.getGenerationNumber())
                                    && (remoteIp.equals(nodeShadowProperties.getRemoteIp()));
                        }
                    });
        } , 120, TimeUnit.SECONDS);
    }

    private static void sleepIfSomethingWasDeleted(boolean somethingRemoved) {
        if (somethingRemoved) {
            LOG.info("Sleeping 10 seconds to let Netvirt listeners process");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                LOG.warn("I can't sleep!", e);
            }
        }
    }

    private static boolean deleteVpnInterface(DataBroker db, LogicalDatastoreType type, int remainingRetries,
        IEntityDeleteDecision<VpnInterface> entityDeleteDecision) {
        InstanceIdentifier<VpnInterfaces> path = InstanceIdentifier.create(VpnInterfaces.class);
        ReadTransaction readTx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<VpnInterfaces>, ReadFailedException> future = readTx.read(type, path);
        Optional<VpnInterfaces> optional = null;

        try {
            optional = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("deleteVpnInterface failed to get data");
            return false;
        }
        if (optional.isPresent()) {
            WriteTransaction deleteTx = db.newWriteOnlyTransaction();
            VpnInterfaces vpnInterfaces = optional.get();
            for (VpnInterface iface : vpnInterfaces.getVpnInterface()) {
                if (entityDeleteDecision.shouldDelete(iface)) {
                    LOG.debug("Delete shadow vpn Interface: DataStoreType {}, interface {}", type, iface);
                    FederationPluginCounters.removed_shadow_vpn_interface.inc();
                    InstanceIdentifier<VpnInterface> iid = InstanceIdentifier.create(VpnInterfaces.class)
                        .child(VpnInterface.class, new VpnInterfaceKey(iface.getKey()));
                    deleteTx.delete(type, iid);
                }
            }
            CheckedFuture<Void, TransactionCommitFailedException> future1 = deleteTx.submit();
            try {
                future1.checkedGet();
            } catch (TransactionCommitFailedException e) {
                if (remainingRetries > 0) {
                    LOG.warn("deleteVpnInterface - Failed to delete! {} {}" + e.getMessage(), e);
                    deleteVpnInterface(db, type, --remainingRetries, entityDeleteDecision);
                } else {
                    LOG.error("deleteVpnInterface - Failed to delete - no more retries! {} {}" + e.getMessage(), e);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean deleteInterfacesShadows(DataBroker db, LogicalDatastoreType type, int remainingRetries,
        IEntityDeleteDecision<Interface> entityDeleteDecision) {
        InstanceIdentifier<Interfaces> path = InstanceIdentifier.create(Interfaces.class);
        ReadTransaction readTx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<Interfaces>, ReadFailedException> future = readTx.read(type, path);
        Optional<Interfaces> optional = null;

        try {
            optional = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("deleteInterfacesShadows failed to get data");
            return false;
        }
        if (optional.isPresent()) {
            WriteTransaction deleteTx = db.newWriteOnlyTransaction();
            Interfaces interfaces = optional.get();
            for (Interface iface : interfaces.getInterface()) {
                if (entityDeleteDecision.shouldDelete(iface)) {
                    LOG.debug("Delete shadow interfaces: DataStoreType {}, interface {}", type, iface);
                    FederationPluginCounters.removed_shadow_ietf_interface.inc();
                    InstanceIdentifier<Interface> iid = InstanceIdentifier.create(Interfaces.class)
                        .child(Interface.class, new InterfaceKey(iface.getKey()));
                    deleteTx.delete(type, iid);
                }
            }
            CheckedFuture<Void, TransactionCommitFailedException> future1 = deleteTx.submit();
            try {
                future1.checkedGet();
            } catch (TransactionCommitFailedException e) {
                if (remainingRetries > 0) {
                    LOG.warn("deleteInterfacesShadows - Failed to delete! {} {}" + e.getMessage(), e);
                    deleteInterfacesShadows(db, type, --remainingRetries, entityDeleteDecision);
                } else {
                    LOG.error("deleteInterfacesShadows - Failed to delete - no more retries! {} {}" + e.getMessage(),
                        e);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean deleteElanInterfacesShadows(DataBroker db, LogicalDatastoreType type, int remainingRetries,
        IEntityDeleteDecision<ElanInterface> entityDeleteDecision) {

        InstanceIdentifier<ElanInterfaces> path = InstanceIdentifier.create(ElanInterfaces.class);
        ReadTransaction readTx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<ElanInterfaces>, ReadFailedException> future = readTx.read(type, path);

        Optional<ElanInterfaces> optional = null;

        try {
            optional = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("deleteElanInterfacesShadows failed to get data");
            return false;
        }
        if (optional.isPresent()) {
            WriteTransaction deleteTx = db.newWriteOnlyTransaction();
            ElanInterfaces interfaces = optional.get();
            for (ElanInterface iface : interfaces.getElanInterface()) {
                if (entityDeleteDecision.shouldDelete(iface)) {
                    LOG.debug("Delete shadow elan interface: DataStoreType {}, interface {}", type, iface);
                    FederationPluginCounters.removed_shadow_elan_interface.inc();
                    InstanceIdentifier<ElanInterface> iid = InstanceIdentifier.create(ElanInterfaces.class)
                        .child(ElanInterface.class, new ElanInterfaceKey(iface.getKey()));
                    deleteTx.delete(type, iid);
                }
            }
            CheckedFuture<Void, TransactionCommitFailedException> future1 = deleteTx.submit();
            try {
                future1.checkedGet();
            } catch (TransactionCommitFailedException e) {
                if (remainingRetries > 0) {
                    LOG.warn("deleteElanInterfacesShadows - Failed to delete! {} {}" + e.getMessage(), e);
                    deleteElanInterfacesShadows(db, type, --remainingRetries, entityDeleteDecision);
                } else {
                    LOG.error(
                        "deleteElanInterfacesShadows - Failed to delete - no more retries! {} {}" + e.getMessage(), e);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean deleteTopologyShadowNodes(DataBroker db, LogicalDatastoreType type, int remainingRetries,
        IEntityDeleteDecision<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network
        .topology.topology.Node> entityDeleteDecision) {
        InstanceIdentifier<Topology> path = InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
            new TopologyKey(new TopologyId(new Uri("ovsdb:1"))));
        ReadTransaction readTx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<Topology>, ReadFailedException> future = readTx.read(type, path);

        Optional<Topology> optional = null;

        try {
            optional = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("deleteTopologyShadowNodes failed to get data");
            return false;
        }
        if (optional.isPresent()) {
            WriteTransaction deleteTx = db.newWriteOnlyTransaction();
            Topology topology = optional.get();
            for (org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology
                     .topology.Node node : topology.getNode()) {
                if (entityDeleteDecision.shouldDelete(node)) {
                    LOG.debug("Delete shadow topolog node: DataStoreType {}, node {}", type, node);
                    FederationPluginCounters.removed_shadow_topology_node.inc();
                    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology
                        .rev131021.network.topology.topology.Node> iid = InstanceIdentifier
                            .create(NetworkTopology.class)
                            .child(Topology.class, new TopologyKey(new TopologyId(new Uri("ovsdb:1"))))
                            .child(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021
                                    .network.topology.topology.Node.class, node.getKey());
                    deleteTx.delete(type, iid);
                }
            }
            CheckedFuture<Void, TransactionCommitFailedException> future1 = deleteTx.submit();
            try {
                future1.checkedGet();
            } catch (org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException e) {
                if (remainingRetries > 0) {
                    LOG.warn("deleteTopologyShadowNodes - Failed to delete! {} {}" + e.getMessage(), e);
                    deleteTopologyShadowNodes(db, type, --remainingRetries, entityDeleteDecision);
                } else {
                    LOG.error("deleteTopologyShadowNodes - Failed to delete - no more retries! {} {}" + e.getMessage(),
                        e);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static void deleteInventoryNodes(DataBroker db, LogicalDatastoreType type, int remainingRetries,
        IEntityDeleteDecision<
            org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> entityDeleteDecision) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes> path =
            InstanceIdentifier.create(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes.class);
        ReadTransaction readTx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes>,
            ReadFailedException> future = readTx.read(type, path);

        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes> optional = null;

        try {
            optional = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.info("deleteInventoryNodes failed to get data");
        }
        if (optional != null && optional.isPresent()) {
            WriteTransaction deleteTx = db.newWriteOnlyTransaction();
            org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes nodes = optional.get();
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node node : nodes.getNode()) {
                if (entityDeleteDecision.shouldDelete(node)) {
                    LOG.debug("Delete shadow inventory node: DataStoreType {}, node {}", type, node);
                    FederationPluginCounters.removed_shadow_inventory_node.inc();
                    org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey key =
                        new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey(
                            node.getId());
                    InstanceIdentifier<
                        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> iid =
                            InstanceIdentifier
                                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes.class)
                                .child(
                                    org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                                    key);
                    deleteTx.delete(type, iid);
                }
            }
            CheckedFuture<Void, TransactionCommitFailedException> future1 = deleteTx.submit();
            try {
                future1.checkedGet();
            } catch (TransactionCommitFailedException e) {
                if (remainingRetries > 0) {
                    LOG.warn("deleteInventoryNodes - Failed to delete! {} {}" + e.getMessage(), e);
                    deleteInventoryNodes(db, type, --remainingRetries, entityDeleteDecision);
                } else {
                    LOG.error("deleteInventoryNodes - Failed to delete - no more retries! {} {}" + e.getMessage(), e);
                }
            }
        }
    }
}

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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwConnectionShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnectionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
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
                ElanShadowProperties elanShadowProperties = elanInterface
                        .getAugmentation(ElanShadowProperties.class);
                return elanShadowProperties != null && Boolean.TRUE.equals(elanShadowProperties.isShadow())
                        && generationNumber != elanShadowProperties.getGenerationNumber()
                        && remoteIp.equals(elanShadowProperties.getRemoteIp());
            })) {
            somethingDeleted = true;
        }

        if (deleteL2GatewayShadows(db, LogicalDatastoreType.CONFIGURATION, MAX_TRANSACTION_DELETE_RETRIES,
            l2Gateway -> {
                L2gwShadowProperties l2GwShadowProperties = l2Gateway.getAugmentation(L2gwShadowProperties.class);
                return l2GwShadowProperties != null && Boolean.TRUE.equals(l2GwShadowProperties.isShadow())
                        && generationNumber != l2GwShadowProperties.getGenerationNumber()
                        && remoteIp.equals(l2GwShadowProperties.getRemoteIp());
            })) {
            somethingDeleted = true;
        }

        if (deleteL2GatewayConnectionShadows(db, LogicalDatastoreType.CONFIGURATION, MAX_TRANSACTION_DELETE_RETRIES,
            l2GatewayConnection -> {
                L2gwConnectionShadowProperties l2GwConnectionShadowProperties = l2GatewayConnection
                        .getAugmentation(L2gwConnectionShadowProperties.class);
                return l2GwConnectionShadowProperties != null
                        && Boolean.TRUE.equals(l2GwConnectionShadowProperties.isShadow())
                        && generationNumber != l2GwConnectionShadowProperties.getGenerationNumber()
                        && remoteIp.equals(l2GwConnectionShadowProperties.getRemoteIp());
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

            deleteInventoryNodes(db, LogicalDatastoreType.OPERATIONAL, MAX_TRANSACTION_DELETE_RETRIES, node -> {
                InventoryNodeShadowProperties nodeShadowProperties = node
                        .getAugmentation(InventoryNodeShadowProperties.class);
                return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                        && generationNumber != nodeShadowProperties.getGenerationNumber()
                        && remoteIp.equals(nodeShadowProperties.getRemoteIp());
            });

            deleteInventoryNodes(db, LogicalDatastoreType.CONFIGURATION, MAX_TRANSACTION_DELETE_RETRIES, node -> {
                InventoryNodeShadowProperties nodeShadowProperties = node
                        .getAugmentation(InventoryNodeShadowProperties.class);
                return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                        && generationNumber != nodeShadowProperties.getGenerationNumber()
                        && remoteIp.equals(nodeShadowProperties.getRemoteIp());
            });

            deleteTopologyShadowNodes(db, LogicalDatastoreType.OPERATIONAL,
                    FederationPluginConstants.OVSDB_TOPOLOGY_KEY, MAX_TRANSACTION_DELETE_RETRIES, node -> {
                    TopologyNodeShadowProperties nodeShadowProperties = node
                                .getAugmentation(TopologyNodeShadowProperties.class);
                    return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                                && generationNumber != nodeShadowProperties.getGenerationNumber()
                                && remoteIp.equals(nodeShadowProperties.getRemoteIp());
                });

            deleteTopologyShadowNodes(db, LogicalDatastoreType.CONFIGURATION,
                    FederationPluginConstants.OVSDB_TOPOLOGY_KEY, MAX_TRANSACTION_DELETE_RETRIES, node -> {
                    TopologyNodeShadowProperties nodeShadowProperties = node
                                .getAugmentation(TopologyNodeShadowProperties.class);
                    return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                                && generationNumber > nodeShadowProperties.getGenerationNumber()
                                && remoteIp.equals(nodeShadowProperties.getRemoteIp());
                });
            deleteTopologyShadowNodes(db, LogicalDatastoreType.OPERATIONAL,
                    FederationPluginConstants.HWVTEP_TOPOLOGY_KEY, MAX_TRANSACTION_DELETE_RETRIES, node -> {
                    TopologyNodeShadowProperties nodeShadowProperties = node
                                .getAugmentation(TopologyNodeShadowProperties.class);
                    return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                                && generationNumber != nodeShadowProperties.getGenerationNumber()
                                && remoteIp.equals(nodeShadowProperties.getRemoteIp());
                });

            deleteTopologyShadowNodes(db, LogicalDatastoreType.CONFIGURATION,
                    FederationPluginConstants.HWVTEP_TOPOLOGY_KEY, MAX_TRANSACTION_DELETE_RETRIES, node -> {
                    TopologyNodeShadowProperties nodeShadowProperties = node
                                .getAugmentation(TopologyNodeShadowProperties.class);
                    return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow())
                                && generationNumber > nodeShadowProperties.getGenerationNumber()
                                && remoteIp.equals(nodeShadowProperties.getRemoteIp());
                });
        }, 120, TimeUnit.SECONDS);
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

    private static boolean deleteL2GatewayShadows(DataBroker db, LogicalDatastoreType type, int remainingRetries,
            IEntityDeleteDecision<L2gateway> entityDeleteDecision) {

        InstanceIdentifier<L2gateways> path = InstanceIdentifier.create(Neutron.class)
                .child(L2gateways.class);
        ReadTransaction readTx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<L2gateways>, ReadFailedException> future = readTx.read(type, path);

        Optional<L2gateways> optional = null;

        try {
            optional = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("deleteL2GatewayShadows failed to get data");
            return false;
        }
        if (optional.isPresent()) {
            WriteTransaction deleteTx = db.newWriteOnlyTransaction();
            L2gateways l2gws = optional.get();
            for (L2gateway iface : l2gws.getL2gateway()) {
                if (entityDeleteDecision.shouldDelete(iface)) {
                    LOG.debug("Delete shadow l2 gateway: DataStoreType {}, interface {}", type, iface);
                    FederationPluginCounters.removed_shadow_l2_gateway.inc();
                    InstanceIdentifier<L2gateway> iid = InstanceIdentifier.create(Neutron.class)
                            .child(L2gateways.class).child(L2gateway.class, new L2gatewayKey(iface.getKey()));
                    deleteTx.delete(type, iid);
                }
            }
            CheckedFuture<Void, TransactionCommitFailedException> future1 = deleteTx.submit();
            try {
                future1.checkedGet();
            } catch (TransactionCommitFailedException e) {
                if (remainingRetries > 0) {
                    LOG.warn("deleteL2GatewayShadows - Failed to delete! {} {}" + e.getMessage(), e);
                    deleteL2GatewayShadows(db, type, --remainingRetries, entityDeleteDecision);
                } else {
                    LOG.error("deleteL2GatewayShadows - Failed to delete - no more retries! {} {}" + e.getMessage(), e);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean deleteL2GatewayConnectionShadows(DataBroker db, LogicalDatastoreType type,
            int remainingRetries, IEntityDeleteDecision<L2gatewayConnection> entityDeleteDecision) {

        InstanceIdentifier<L2gatewayConnections> path = InstanceIdentifier.create(Neutron.class)
                .child(L2gatewayConnections.class);
        ReadTransaction readTx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<L2gatewayConnections>, ReadFailedException> future = readTx.read(type, path);

        Optional<L2gatewayConnections> optional = null;

        try {
            optional = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("deleteL2GatewayConnectionShadows failed to get data");
            return false;
        }
        if (optional.isPresent()) {
            WriteTransaction deleteTx = db.newWriteOnlyTransaction();
            L2gatewayConnections l2gws = optional.get();
            for (L2gatewayConnection iface : l2gws.getL2gatewayConnection()) {
                if (entityDeleteDecision.shouldDelete(iface)) {
                    LOG.debug("Delete shadow l2 gateway: DataStoreType {}, interface {}", type, iface);
                    FederationPluginCounters.removed_shadow_l2_gateway.inc();
                    InstanceIdentifier<L2gatewayConnection> iid = InstanceIdentifier.create(
                            Neutron.class).child(L2gatewayConnections.class)
                            .child(L2gatewayConnection.class, new L2gatewayConnectionKey(iface.getKey()));
                    deleteTx.delete(type, iid);
                }
            }
            CheckedFuture<Void, TransactionCommitFailedException> future1 = deleteTx.submit();
            try {
                future1.checkedGet();
            } catch (TransactionCommitFailedException e) {
                if (remainingRetries > 0) {
                    LOG.warn("deleteL2GatewayShadows - Failed to delete! {} {}" + e.getMessage(), e);
                    deleteL2GatewayConnectionShadows(db, type, --remainingRetries, entityDeleteDecision);
                } else {
                    LOG.error(
                            "deleteL2GatewayShadows - Failed to delete - no more retries! {} {}" + e.getMessage(), e);
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

    private static boolean deleteTopologyShadowNodes(DataBroker db, LogicalDatastoreType type, TopologyKey topologyKey,
            int remainingRetries,
        IEntityDeleteDecision<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network
        .topology.topology.Node> entityDeleteDecision) {
        InstanceIdentifier<Topology> path = InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
                topologyKey);
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
                            .child(Topology.class, FederationPluginConstants.OVSDB_TOPOLOGY_KEY)
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
                    deleteTopologyShadowNodes(db, type, topologyKey, --remainingRetries, entityDeleteDecision);
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

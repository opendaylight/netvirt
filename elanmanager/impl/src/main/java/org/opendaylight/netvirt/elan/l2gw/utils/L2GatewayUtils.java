/*
 * Copyright (c) 2016 ,2017  Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.AddL2GwDeviceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.DeleteL2GwDeviceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class L2GatewayUtils {
    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");

    private L2GatewayUtils() { }

    public static boolean isGatewayAssociatedToL2Device(L2GatewayDevice l2GwDevice) {
        return (l2GwDevice.getL2GatewayIds().size() > 0);
    }

    public static boolean isLastL2GatewayBeingDeleted(L2GatewayDevice l2GwDevice) {
        return (l2GwDevice.getL2GatewayIds().size() == 1);
    }

    public static boolean isItmTunnelsCreatedForL2Device(L2GatewayDevice l2GwDevice) {
        return (l2GwDevice.getHwvtepNodeId() != null && l2GwDevice.getL2GatewayIds().size() > 0);
    }

    public static void deleteItmTunnels(ItmRpcService itmRpcService, String hwvtepId, String psName,
                                        IpAddress tunnelIp) {
        DeleteL2GwDeviceInputBuilder builder = new DeleteL2GwDeviceInputBuilder();
        builder.setTopologyId(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID.getValue());
        builder.setNodeId(HwvtepSouthboundUtils.createManagedNodeId(new NodeId(hwvtepId), psName).getValue());
        builder.setIpAddress(tunnelIp);
        try {
            Future<RpcResult<Void>> result = itmRpcService.deleteL2GwDevice(builder.build());
            RpcResult<Void> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                LOG.info("Deleted ITM tunnels for {}", hwvtepId);
            } else {
                LOG.error("Failed to delete ITM Tunnels: {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("RPC to delete ITM tunnels failed", e);
        }
    }

    public static void createItmTunnels(ItmRpcService itmRpcService, String hwvtepId, String psName,
                                        IpAddress tunnelIp) {
        AddL2GwDeviceInputBuilder builder = new AddL2GwDeviceInputBuilder();
        builder.setTopologyId(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID.getValue());
        builder.setNodeId(HwvtepSouthboundUtils.createManagedNodeId(new NodeId(hwvtepId), psName).getValue());
        builder.setIpAddress(tunnelIp);
        try {
            Future<RpcResult<Void>> result = itmRpcService.addL2GwDevice(builder.build());
            RpcResult<Void> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                LOG.info("Created ITM tunnels for {}", hwvtepId);
            } else {
                LOG.error("Failed to create ITM Tunnels: ", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("RPC to create ITM tunnels failed", e);
        }
    }

    //TODO Remove the method from HwvtepUtils.getDbVersion() from genius
    public static String getConfigDbVersion(DataBroker broker, NodeId nodeId) {
        Node hwvtepNode = getHwVtepNode(broker, LogicalDatastoreType.CONFIGURATION, nodeId);
        String dbVersion = "";
        if (hwvtepNode != null) {
            HwvtepGlobalAugmentation globalAugmentation = hwvtepNode.getAugmentation(HwvtepGlobalAugmentation.class);
            if (globalAugmentation != null) {
                dbVersion = globalAugmentation.getDbVersion();
            }
        } else {
            LOG.warn("HWVTEP Node missing in config topo for {}", nodeId.getValue());
        }

        return dbVersion;
    }

    public static Node getHwVtepNode(DataBroker dataBroker, LogicalDatastoreType datastoreType, NodeId nodeId) {
        return (Node) MDSALUtil.read(dataBroker, datastoreType, HwvtepSouthboundUtils.createInstanceIdentifier(nodeId))
                .orNull();
    }
}
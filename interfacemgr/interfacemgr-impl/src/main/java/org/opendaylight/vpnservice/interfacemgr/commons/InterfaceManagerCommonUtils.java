/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.commons;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdOutput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class InterfaceManagerCommonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceManagerCommonUtils.class);
    public static NodeConnector getNodeConnectorFromInventoryOperDS(NodeConnectorId nodeConnectorId,
                                                                    DataBroker dataBroker) {
        NodeId nodeId = IfmUtil.getNodeIdFromNodeConnectorId(nodeConnectorId);
        InstanceIdentifier<NodeConnector> ncIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId))
                .child(NodeConnector.class, new NodeConnectorKey(nodeConnectorId)).build();

        Optional<NodeConnector> nodeConnectorOptional = IfmUtil.read(LogicalDatastoreType.OPERATIONAL,
                ncIdentifier, dataBroker);
        if (!nodeConnectorOptional.isPresent()) {
            return null;
        }
        return nodeConnectorOptional.get();
    }

    /*public static void addInterfaceEntryToInventoryOperDS(NodeConnectorId nodeConnectorId, long lporttag, String interfaceName,
                                                          DataBroker dataBroker, WriteTransaction t) {
        NodeId nodeId = IfmUtil.getNodeIdFromNodeConnectorId(nodeConnectorId);
        TunnelInterfaceInventoryInfoKey tunnelInterfaceInventoryInfoKey = new TunnelInterfaceInventoryInfoKey(lporttag);
        InstanceIdentifier<TunnelInterfaceInventoryInfo> inventoryIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId))
                .augmentation(TunnelInterfaceNames.class)
                .child(TunnelInterfaceInventoryInfo.class, tunnelInterfaceInventoryInfoKey).build();
        TunnelInterfaceInventoryInfoBuilder builder = new TunnelInterfaceInventoryInfoBuilder().setKey(tunnelInterfaceInventoryInfoKey)
                .setTunIntfName(interfaceName);
        t.put(LogicalDatastoreType.OPERATIONAL, inventoryIdentifier, builder.build(), true);
    }

    public static void removeInterfaceEntryFromInventoryOperDS(NodeConnectorId nodeConnectorId, long lporttag,
                                                               String interfaceName, DataBroker dataBroker,
                                                               WriteTransaction t) {
        NodeId nodeId = IfmUtil.getNodeIdFromNodeConnectorId(nodeConnectorId);
        TunnelInterfaceInventoryInfoKey tunnelInterfaceInventoryInfoKey = new TunnelInterfaceInventoryInfoKey(lporttag);
        InstanceIdentifier<TunnelInterfaceInventoryInfo> inventoryIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId))
                .augmentation(TunnelInterfaceNames.class)
                .child(TunnelInterfaceInventoryInfo.class, tunnelInterfaceInventoryInfoKey).build();
        t.delete(LogicalDatastoreType.OPERATIONAL, inventoryIdentifier);
    }

    public static void removeInterfaceEntryFromInventoryOperDS(NodeConnectorId nodeConnectorId, long lporttag,
                                                               DataBroker dataBroker) {
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        NodeId nodeId = IfmUtil.getNodeIdFromNodeConnectorId(nodeConnectorId);
        TunnelInterfaceInventoryInfoKey tunnelInterfaceInventoryInfoKey = new TunnelInterfaceInventoryInfoKey(lporttag);
        InstanceIdentifier<TunnelInterfaceInventoryInfo> inventoryIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId))
                .augmentation(TunnelInterfaceNames.class)
                .child(TunnelInterfaceInventoryInfo.class, tunnelInterfaceInventoryInfoKey).build();
        t.delete(LogicalDatastoreType.OPERATIONAL, inventoryIdentifier);
        t.submit(); // This is a Best-Effort Deletion. If Node is already removed, this may fail.
    } */

    public static InstanceIdentifier<Interface> getInterfaceIdentifier(InterfaceKey interfaceKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> interfaceInstanceIdentifierBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, interfaceKey);
        return interfaceInstanceIdentifierBuilder.build();
    }

    public static Interface getInterfaceFromConfigDS(InterfaceKey interfaceKey, DataBroker dataBroker) {
        InstanceIdentifier<Interface> interfaceId = getInterfaceIdentifier(interfaceKey);
        Optional<Interface> interfaceOptional = IfmUtil.read(LogicalDatastoreType.CONFIGURATION, interfaceId, dataBroker);
        if (!interfaceOptional.isPresent()) {
            return null;
        }

        return interfaceOptional.get();
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface getInterfaceStateFromOperDS(String interfaceName, DataBroker dataBroker) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                IfmUtil.buildStateInterfaceId(interfaceName);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateOptional =
                IfmUtil.read(LogicalDatastoreType.OPERATIONAL, ifStateId, dataBroker);
        if (!ifStateOptional.isPresent()) {
            return null;
        }

        return ifStateOptional.get();
    }

    public static Integer getUniqueId(IdManager idManager, String idKey) {
        GetUniqueIdInput getIdInput = new GetUniqueIdInputBuilder()
                .setPoolName(IfmConstants.IFM_LPORT_TAG_IDPOOL_NAME)
                .setIdKey(idKey).build();

        try {
            Future<RpcResult<GetUniqueIdOutput>> result = idManager.
                    getUniqueId(getIdInput);
            RpcResult<GetUniqueIdOutput> rpcResult = result.get();
            if(rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id",e);
        }
        return 0;
    }

    public static String getJobKey(String dpId, String portName) {
        String jobKey = "";
        if (dpId != null && !"".equals(dpId)) {
            jobKey = dpId.toString() + ":";
        }
        jobKey = jobKey + portName;
        return jobKey;
    }

    public static String getJobKey(BigInteger dpId, String portName) {
        String jobKey = "";
        if (dpId != null && dpId.longValue() != 0) {
            jobKey = dpId.toString() + ":";
        }
        jobKey = jobKey + portName;
        return jobKey;
    }
}
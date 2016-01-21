/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.commons;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.VpnConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.*;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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
    public static void makeTunnelIngressFlow(List<ListenableFuture<Void>> futures, IMdsalApiManager mdsalApiManager,
                                             IfTunnel tunnel, BigInteger dpnId, long portNo, Interface iface, int addOrRemoveFlow) {
        String flowRef = InterfaceManagerCommonUtils.getTunnelInterfaceFlowRef(dpnId, NwConstants.VLAN_INTERFACE_INGRESS_TABLE, iface.getName());
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        if (NwConstants.ADD_FLOW == addOrRemoveFlow) {
            matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[] {
                    dpnId, BigInteger.valueOf(portNo) }));
            short tableId = tunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeMplsOverGre.class) ? NwConstants.L3_LFIB_TABLE :
                    tunnel.isInternal() ? NwConstants.INTERNAL_TUNNEL_TABLE : NwConstants.EXTERNAL_TUNNEL_TABLE;
            mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] {tableId}));}

        BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("8000001", 16);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.VLAN_INTERFACE_INGRESS_TABLE, flowRef,
                IfmConstants.DEFAULT_FLOW_PRIORITY, iface.getName(), 0, 0, COOKIE_VM_INGRESS_TABLE, matches, mkInstructions);
        if (NwConstants.ADD_FLOW == addOrRemoveFlow) {
            futures.add(mdsalApiManager.installFlow(dpnId, flowEntity));
        } else {
            futures.add(mdsalApiManager.removeFlow(dpnId, flowEntity));
        }
    }
    public static String getTunnelInterfaceFlowRef(BigInteger dpnId, short tableId, String ifName) {
        return new StringBuilder().append(dpnId).append(tableId).append(ifName).toString();
    }

    public static void setOpStateForInterface(DataBroker broker, String interfaceName, org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus opStatus) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceId = IfmUtil.buildStateInterfaceId(interfaceName);
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder().setKey(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey(interfaceName));
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface interfaceData = ifaceBuilder.setOperStatus(opStatus).build();
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, interfaceId, interfaceData);
    }

    public static void updateTunnelMonitorDetailsInConfigDS(DataBroker broker, String interfaceName, boolean monitorEnabled, long monitorInterval) {
        InstanceIdentifier<Interface> id = IfmUtil.buildId(interfaceName);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder ifaceBuilder = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder();
        ifaceBuilder.setKey(new InterfaceKey(interfaceName));
        IfTunnelBuilder ifTunnelBuilder = new IfTunnelBuilder();
        ifTunnelBuilder.setMonitorEnabled(monitorEnabled);
        ifTunnelBuilder.setMonitorInterval(monitorInterval);
        ifaceBuilder.addAugmentation(IfTunnel.class, ifTunnelBuilder.build());

        LOG.trace("Updating trunk interface {} in Config DS", interfaceName);
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, id, ifaceBuilder.build());
    }
}
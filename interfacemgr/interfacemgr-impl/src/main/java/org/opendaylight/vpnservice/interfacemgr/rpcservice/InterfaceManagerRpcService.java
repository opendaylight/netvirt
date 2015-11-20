/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.rpcservice;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.InterfaceChildInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.*;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

public class InterfaceManagerRpcService implements OdlInterfaceRpcService {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceManagerRpcService.class);
    DataBroker dataBroker;
    public InterfaceManagerRpcService(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public Future<RpcResult<GetDpidFromInterfaceOutput>> getDpidFromInterface(GetDpidFromInterfaceInput input) {
        String interfaceName = input.getIntfName();
        RpcResultBuilder<GetDpidFromInterfaceOutput> rpcResultBuilder;
        try {
            BigInteger dpId = null;
            InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
            Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
            if (Tunnel.class.equals(interfaceInfo.getType())) {
                ParentRefs parentRefs = interfaceInfo.getAugmentation(ParentRefs.class);
                dpId = parentRefs.getDatapathNodeIdentifier();
            } else {
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                        InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName, dataBroker);
                String lowerLayerIf = ifState.getLowerLayerIf().get(0);
                NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
                dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
            }
            GetDpidFromInterfaceOutputBuilder output = new GetDpidFromInterfaceOutputBuilder().setDpid(
                    (dpId));
            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output.build());
        } catch (Exception e) {
            LOG.error("Retrieval of datapath id for the key {} failed due to {}", interfaceName, e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetEndpointIpForDpnOutput>> getEndpointIpForDpn(GetEndpointIpForDpnInput input) {
        RpcResultBuilder<GetEndpointIpForDpnOutput> rpcResultBuilder;
        try {
            BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(input.getDpid());
            InstanceIdentifier<BridgeEntry> bridgeEntryInstanceIdentifier =
                    InterfaceMetaUtils.getBridgeEntryIdentifier(bridgeEntryKey);
            BridgeEntry bridgeEntry =
                    InterfaceMetaUtils.getBridgeEntryFromConfigDS(bridgeEntryInstanceIdentifier,
                            dataBroker);
            // local ip of any of the bridge interface entry will be the dpn end point ip
            BridgeInterfaceEntry bridgeInterfaceEntry = bridgeEntry.getBridgeInterfaceEntry().get(0);
            InterfaceKey interfaceKey = new InterfaceKey(bridgeInterfaceEntry.getInterfaceName());
            Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
            IfTunnel tunnel = interfaceInfo.getAugmentation(IfTunnel.class);
            GetEndpointIpForDpnOutputBuilder endpointIpForDpnOutput = new GetEndpointIpForDpnOutputBuilder().setLocalIps(Arrays.asList(tunnel.getTunnelSource()));
            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(endpointIpForDpnOutput.build());
        }catch(Exception e){
            LOG.error("Retrieval of endpoint of for dpn {} failed due to {}" ,input.getDpid(), e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetEgressInstructionsForInterfaceOutput>> getEgressInstructionsForInterface(GetEgressInstructionsForInterfaceInput input) {
        RpcResultBuilder<GetEgressInstructionsForInterfaceOutput> rpcResultBuilder;
        try {
            List<InstructionInfo> instructionInfo = new ArrayList<InstructionInfo>();
            List<ActionInfo> actionInfo = getEgressActionInfosForInterface(input.getIntfName());
            instructionInfo.add(new InstructionInfo(InstructionType.write_actions, actionInfo));
                    GetEgressInstructionsForInterfaceOutputBuilder output = new GetEgressInstructionsForInterfaceOutputBuilder().
                    setInstruction(buildInstructions(instructionInfo));
            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output.build());
        }catch(Exception e){
            LOG.error("Retrieval of egress actions for the key {} failed due to {}" ,input.getIntfName(), e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetEgressActionsForInterfaceOutput>> getEgressActionsForInterface(GetEgressActionsForInterfaceInput input) {
        RpcResultBuilder<GetEgressActionsForInterfaceOutput> rpcResultBuilder;
        try {
            List<Action> actionsList = getEgressActionsForInterface(input.getIntfName());
            GetEgressActionsForInterfaceOutputBuilder output = new GetEgressActionsForInterfaceOutputBuilder().
                    setAction(actionsList);
            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output.build());
        }catch(Exception e){
            LOG.error("Retrieval of egress actions for the key {} failed due to {}" ,input.getIntfName(), e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    public static InstanceIdentifier<InterfaceChildEntry> getInterfaceChildEntryIdentifier(InterfaceParentEntryKey parentEntryKey, InterfaceChildEntryKey interfaceChildEntryKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<InterfaceChildEntry> interfaceChildEntryInstanceIdentifierBuilder =
                InstanceIdentifier.builder(InterfaceChildInfo.class).child(InterfaceParentEntry.class, parentEntryKey).child(InterfaceChildEntry.class, interfaceChildEntryKey);
        return interfaceChildEntryInstanceIdentifierBuilder.build();
    }

    public static InterfaceChildEntry getInterfaceChildEntryFromConfigDS(String interfaceName,
                                                         DataBroker dataBroker) {
        InterfaceParentEntryKey parentEntryKey = new InterfaceParentEntryKey(interfaceName);
        InterfaceChildEntryKey childEntryKey = new InterfaceChildEntryKey(interfaceName);
        InstanceIdentifier<InterfaceChildEntry> interfaceChildEntryInstanceIdentifier = getInterfaceChildEntryIdentifier(parentEntryKey, childEntryKey);
        Optional<InterfaceChildEntry> interfaceChildEntryOptional =
                IfmUtil.read(LogicalDatastoreType.CONFIGURATION, interfaceChildEntryInstanceIdentifier, dataBroker);
        if (!interfaceChildEntryOptional.isPresent()) {
            return null;
        }
        return interfaceChildEntryOptional.get();
    }

    @Override
    public Future<RpcResult<GetPortFromInterfaceOutput>> getPortFromInterface(GetPortFromInterfaceInput input) {
        RpcResultBuilder<GetPortFromInterfaceOutput> rpcResultBuilder;
        String interfaceName = input.getIntfName();
        try {
            BigInteger dpId = null;
            long portNo = 0;
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                        InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName, dataBroker);
            String lowerLayerIf = ifState.getLowerLayerIf().get(0);
            NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
            dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
            portNo = Long.valueOf(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
            // FIXME Assuming portName and interfaceName are same
            GetPortFromInterfaceOutputBuilder output = new GetPortFromInterfaceOutputBuilder().setDpid(dpId).
                    setPortname(interfaceName).setPortno(Long.valueOf(portNo));
            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output.build());
        }catch(Exception e){
            LOG.error("Retrieval of lport tag for the key {} failed due to {}" ,input.getIntfName(), e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetInterfaceFromPortOutput>> getInterfaceFromPort(GetInterfaceFromPortInput input) {
        /*RpcResultBuilder<GetInterfaceFromPortOutput> rpcResultBuilder;
        try {
            Interface interfaceInfo = null;
            NodeId nodeId = IfmUtil.buildDpnNodeId(input.getDpid());
            Node node = getNodeFromInventoryOperDS(nodeId, dataBroker);
            ChildInterfaceNames childInterfaceNames = node.getAugmentation(ChildInterfaceNames.class);
            for(OfInterfaceRefInfo ofInterfaceRefInfo : childInterfaceNames.getOfInterfaceRefInfo()){
               interfaceInfo = getInterfaceFromTunnelKey(ofInterfaceRefInfo.getOfIntfName(), input.getInterfaceId(),
                       input.getInterfaceType());
            }
            GetInterfaceFromPortOutputBuilder output = new GetInterfaceFromPortOutputBuilder().
                    setInterfaceName(interfaceInfo == null ? null : interfaceInfo.getName());
            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output.build());
        }catch(Exception e){
            LOG.error("Retrieval of interface for the key {} failed due to {}" ,input.getPortno(), e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
        */
        return null;
    }

    public List<ActionInfo> getEgressActionInfosForInterface(String interfaceName) {
        Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(interfaceName),
                dataBroker);
        List<ActionInfo> listActionInfo = new ArrayList<ActionInfo>();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName, dataBroker);

        String lowerLayerIf = ifState.getLowerLayerIf().get(0);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
        String portNo = IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId);
        Class<? extends InterfaceType> ifType = interfaceInfo.getType();
        if(L2vlan.class.equals(ifType)){
            IfL2vlan vlanIface = interfaceInfo.getAugmentation(IfL2vlan.class);
            LOG.trace("L2Vlan: {}",vlanIface);
            long vlanVid = (vlanIface == null) ? 0 : vlanIface.getVlanId().getValue();
            if (vlanVid != 0) {
                listActionInfo.add(new ActionInfo(ActionType.push_vlan, new String[] {}));
                listActionInfo.add(new ActionInfo(ActionType.set_field_vlan_vid,
                        new String[] { Long.toString(vlanVid) }));
            }
            listActionInfo.add(new ActionInfo(ActionType.output, new String[] {portNo}));
        }else if(Tunnel.class.equals(ifType)){
            listActionInfo.add(new ActionInfo(ActionType.output, new String[] { portNo}));
        }
        return listActionInfo;
    }

    public List<Action> getEgressActionsForInterface(String interfaceName) {
            List<ActionInfo> listActionInfo = getEgressActionInfosForInterface(interfaceName);
            List<Action> actionsList = new ArrayList<>();
            for (ActionInfo actionInfo : listActionInfo) {
                actionsList.add(actionInfo.buildAction());
            }
            return actionsList;
    }

    protected static List<Instruction> buildInstructions(List<InstructionInfo> listInstructionInfo) {
        if (listInstructionInfo != null) {
            List<Instruction> instructions = new ArrayList<Instruction>();
            int instructionKey = 0;

            for (InstructionInfo instructionInfo : listInstructionInfo) {
                instructions.add(instructionInfo.buildInstruction(instructionKey));
                instructionKey++;
            }
            return instructions;
        }

        return null;
    }

    public static Node getNodeFromInventoryOperDS(NodeId nodeId, DataBroker dataBroker) {
        InstanceIdentifier<Node> nodeIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).build();

        Optional<Node> nodeOptional = IfmUtil.read(LogicalDatastoreType.OPERATIONAL,
                nodeIdentifier, dataBroker);
        if (!nodeOptional.isPresent()) {
            return null;
        }
        return nodeOptional.get();
    }

    public Interface getInterfaceFromTunnelKey(String interfaceName, BigInteger tunnelKey,
                                               Class<? extends TunnelTypeBase> ifType){

        /*Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(interfaceName), dataBroker);

        if(ifType.isAssignableFrom(IfL2vlan.class)){
            IfL2vlan vlanIface = interfaceInfo.getAugmentation(IfL2vlan.class);
            LOG.trace("L2Vlan: {}",vlanIface);
            long vlanVid = (vlanIface == null) ? 0 : vlanIface.getVlanId();
            if(tunnelKey.intValue() == vlanVid){
               return interfaceInfo;
            }
        }else if(ifType.isAssignableFrom(TunnelTypeBase.class)){
            IfTunnel ifTunnel = interfaceInfo.getAugmentation(IfTunnel.class);
            TunnelResources tunnelResources = ifTunnel.getTunnelResources();
            if(ifType.isAssignableFrom(TunnelTypeGre.class)) {
                IfGre ifGre = tunnelResources.getAugmentation(IfGre.class);
                if (ifGre.getGreKey() == tunnelKey) {
                    return interfaceInfo;
                }
            }else if(ifType.isAssignableFrom(TunnelTypeVxlan.class)){
                IfVxlan ifVxlan = tunnelResources.getAugmentation(IfVxlan.class);
                if(ifVxlan.getVni() == tunnelKey){
                    return interfaceInfo;
                }
            }
        }
        return null;*/
        return null;
    }
}
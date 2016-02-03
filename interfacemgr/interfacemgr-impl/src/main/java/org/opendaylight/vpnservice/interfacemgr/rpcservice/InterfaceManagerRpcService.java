/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.rpcservice;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.IfIndexesInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeMplsOverGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.*;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
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
    IMdsalApiManager mdsalMgr;
    public InterfaceManagerRpcService(DataBroker dataBroker, IMdsalApiManager mdsalMgr) {
        this.dataBroker = dataBroker;
        this.mdsalMgr = mdsalMgr;
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
    public Future<RpcResult<Void>> createTerminatingServiceActions(final CreateTerminatingServiceActionsInput input) {
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        try{
            LOG.info("create terminatingServiceAction on DpnId = {} for tunnel-key {}", input.getDpid() , input.getTunnelKey());
            Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(input.getInterfaceName()),dataBroker);
            IfTunnel tunnelInfo = interfaceInfo.getAugmentation(IfTunnel.class);
            if(tunnelInfo != null) {
                ListenableFuture<Void> installFlowResult = (tunnelInfo.getTunnelInterfaceType().isAssignableFrom(TunnelTypeMplsOverGre.class)) ?
                        makeLFIBFlow(input.getDpid(),input.getTunnelKey(), input.getInstruction(), NwConstants.ADD_FLOW) :
                        makeTerminatingServiceFlow(tunnelInfo, input.getDpid(), input.getTunnelKey(), input.getInstruction(), NwConstants.ADD_FLOW);
                Futures.addCallback(installFlowResult, new FutureCallback<Void>(){

                    @Override
                    public void onSuccess(Void aVoid) {
                        result.set(RpcResultBuilder.<Void>success().build());
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        String msg = String.format("Unable to install terminating service flow for %s", input.getInterfaceName());
                        LOG.error("create terminating service actions failed. {}. {}", msg, error);
                        result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, error).build());
                    }
                });
                result.set(RpcResultBuilder.<Void>success().build());
            } else {
                String msg = String.format("Terminating Service Actions cannot be created for a non-tunnel interface %s",input.getInterfaceName());
                LOG.error(msg);
                result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg).build());
            }
        }catch(Exception e){
            String msg = String.format("create Terminating Service Actions for %s failed",input.getInterfaceName());
            LOG.error("create Terminating Service Actions for {} failed due to {}" ,input.getDpid(), e);
            result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, e.getMessage()).build());
        }
        return result;
    }

    @Override
    public Future<RpcResult<Void>> removeTerminatingServiceActions(final RemoveTerminatingServiceActionsInput input) {
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        try{
            WriteTransaction t = dataBroker.newWriteOnlyTransaction();
            LOG.info("remove terminatingServiceAction on DpnId = {} for tunnel-key {}", input.getDpid() , input.getTunnelKey());

            Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(input.getInterfaceName()),dataBroker);
            IfTunnel tunnelInfo = interfaceInfo.getAugmentation(IfTunnel.class);
            if(tunnelInfo != null) {
                ListenableFuture<Void> removeFlowResult = (tunnelInfo.getTunnelInterfaceType().isAssignableFrom(TunnelTypeMplsOverGre.class)) ?
                        makeLFIBFlow(input.getDpid(),input.getTunnelKey(), null, NwConstants.DEL_FLOW) :
                        makeTerminatingServiceFlow(tunnelInfo, input.getDpid(), input.getTunnelKey(), null, NwConstants.DEL_FLOW);
                Futures.addCallback(removeFlowResult, new FutureCallback<Void>(){

                    @Override
                    public void onSuccess(Void aVoid) {
                        result.set(RpcResultBuilder.<Void>success().build());
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        String msg = String.format("Unable to install terminating service flow %s", input.getInterfaceName());
                        LOG.error("create terminating service actions failed. {}. {}", msg, error);
                        result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, error).build());
                    }
                });
                result.set(RpcResultBuilder.<Void>success().build());
            } else {
                String msg = String.format("Terminating Service Actions cannot be removed for a non-tunnel interface %s",
                        input.getInterfaceName());
                LOG.error(msg);
                result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg).build());
            }
        }catch(Exception e){
            LOG.error("Remove Terminating Service Actions for {} failed due to {}" ,input.getDpid(), e);
            String msg = String.format("Remove Terminating Service Actions for %d failed.", input.getDpid());
            result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, e.getMessage()).build());
        }
        return result;
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
    public Future<RpcResult<GetInterfaceTypeOutput>> getInterfaceType(GetInterfaceTypeInput input) {
        String interfaceName = input.getIntfName();
        RpcResultBuilder<GetInterfaceTypeOutput> rpcResultBuilder;
        try {
            InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
            Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);

            GetInterfaceTypeOutputBuilder output = new GetInterfaceTypeOutputBuilder().setInterfaceType(interfaceInfo.getType());
            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output.build());
        } catch (Exception e) {
            LOG.error("Retrieval of interface type for the key {} failed due to {}", interfaceName, e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetTunnelTypeOutput>> getTunnelType(GetTunnelTypeInput input) {
        String interfaceName = input.getIntfName();
        RpcResultBuilder<GetTunnelTypeOutput> rpcResultBuilder;
        try {
            InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
            Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);

            if (Tunnel.class.equals(interfaceInfo.getType())) {
                IfTunnel tnl = interfaceInfo.getAugmentation(IfTunnel.class);
                Class <? extends TunnelTypeBase> tun_type = tnl.getTunnelInterfaceType();
                GetTunnelTypeOutputBuilder output = new GetTunnelTypeOutputBuilder().setTunnelType(tun_type);
                rpcResultBuilder = RpcResultBuilder.success();
                rpcResultBuilder.withResult(output.build());
            } else {
                LOG.error("Retrieval of interface type for the key {} failed", interfaceName);
                rpcResultBuilder = RpcResultBuilder.failed();
            }
        } catch (Exception e) {
            LOG.error("Retrieval of interface type for the key {} failed due to {}", interfaceName, e);
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
    public Future<RpcResult<GetNodeconnectorIdFromInterfaceOutput>> getNodeconnectorIdFromInterface(GetNodeconnectorIdFromInterfaceInput input) {
        String interfaceName = input.getIntfName();
        RpcResultBuilder<GetNodeconnectorIdFromInterfaceOutput> rpcResultBuilder;
        try {
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                    InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName, dataBroker);
            String lowerLayerIf = ifState.getLowerLayerIf().get(0);
            NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);

            GetNodeconnectorIdFromInterfaceOutputBuilder output = new GetNodeconnectorIdFromInterfaceOutputBuilder().setNodeconnectorId(nodeConnectorId);
            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output.build());
        } catch (Exception e) {
            LOG.error("Retrieval of nodeconnector id for the key {} failed due to {}", interfaceName, e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetInterfaceFromIfIndexOutput>> getInterfaceFromIfIndex(GetInterfaceFromIfIndexInput input) {
        Integer ifIndex = input.getIfIndex();
        RpcResultBuilder<GetInterfaceFromIfIndexOutput> rpcResultBuilder = null;
        try {
            InstanceIdentifier<IfIndexInterface> id = InstanceIdentifier.builder(IfIndexesInterfaceMap.class).child(IfIndexInterface.class, new IfIndexInterfaceKey(ifIndex)).build();
            Optional<IfIndexInterface> ifIndexesInterface = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, id, dataBroker);
            if(ifIndexesInterface.isPresent()) {
                String interfaceName = ifIndexesInterface.get().getInterfaceName();
                GetInterfaceFromIfIndexOutputBuilder output = new GetInterfaceFromIfIndexOutputBuilder().setInterfaceName(interfaceName);
                rpcResultBuilder = RpcResultBuilder.success();
                rpcResultBuilder.withResult(output.build());
            }
        } catch (Exception e) {
            LOG.error("Retrieval of interfaceName for the key {} failed due to {}", ifIndex, e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
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
            long vlanVid = 0;
            boolean isVlanTransparent = false;
            if (vlanIface != null) {
                vlanVid = vlanIface.getVlanId() == null ? 0 : vlanIface.getVlanId().getValue();
                isVlanTransparent = vlanIface.getL2vlanMode() == IfL2vlan.L2vlanMode.Transparent;
            }
            if (vlanVid != 0 && !isVlanTransparent) {
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

    private ListenableFuture<Void> makeTerminatingServiceFlow(IfTunnel tunnelInfo, BigInteger dpnId, BigInteger tunnelKey, List<Instruction> instruction, int addOrRemove) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {tunnelKey}));
        short tableId = tunnelInfo.isInternal() ? NwConstants.INTERNAL_TUNNEL_TABLE :
                NwConstants.EXTERNAL_TUNNEL_TABLE;
        final String flowRef = getFlowRef(dpnId,tableId, tunnelKey);
        Flow terminatingSerFlow = MDSALUtil.buildFlowNew(tableId, flowRef,
                5, "TST Flow Entry", 0, 0,
                IfmConstants.TUNNEL_TABLE_COOKIE.add(tunnelKey), mkMatches, instruction);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            return mdsalMgr.installFlow(dpnId, terminatingSerFlow);
        }

        return mdsalMgr.removeFlow(dpnId, terminatingSerFlow);
    }

    private ListenableFuture<Void> makeLFIBFlow(BigInteger dpnId, BigInteger tunnelKey, List<Instruction> instruction, int addOrRemove) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        mkMatches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[]{0x8847L}));
        mkMatches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(tunnelKey.longValue())}));
        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, tunnelKey);

        Flow lfibFlow = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
                IfmConstants.DEFAULT_FLOW_PRIORITY, "LFIB Entry", 0, 0,
                IfmConstants.COOKIE_VM_LFIB_TABLE, mkMatches, instruction);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            return mdsalMgr.installFlow(dpnId, lfibFlow);
        }
        return mdsalMgr.removeFlow(dpnId, lfibFlow);
    }

    private String getFlowRef(BigInteger dpnId, short tableId, BigInteger tunnelKey) {
        return new StringBuffer().append(IfmConstants.TUNNEL_TABLE_FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR).append(tunnelKey).toString();
    }
}
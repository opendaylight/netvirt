/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import com.google.common.util.concurrent.SettableFuture;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.interfacemanager.IfmUtil;
import org.opendaylight.genius.interfacemanager.commons.InterfaceManagerCommonUtils;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.netvirt.elanmanager.tests.InterfaceDetails;
import org.opendaylight.netvirt.elanmanager.tests.TunnelInterfaceDetails;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressInstructionsForInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public abstract class InterfaceMgrTestImpl implements IInterfaceManager, OdlInterfaceRpcService {

    public Map<String, InterfaceInfo> interfaces = new ConcurrentHashMap<>();
    public Map<String, Boolean> externalInterfaces = new ConcurrentHashMap<>();
    private DataBroker dataBroker;

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceMgrTestImpl.class);

    public static InterfaceMgrTestImpl newInstance(DataBroker dataBroker) {
        InterfaceMgrTestImpl instance = Mockito.mock(InterfaceMgrTestImpl.class, realOrException());
        instance.interfaces = new ConcurrentHashMap<>();
        instance.externalInterfaces = new ConcurrentHashMap<>();
        instance.dataBroker = dataBroker;
        return instance;
    }

    private InstanceIdentifier<Interface> buildIid(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class).child(Interface.class,
                new InterfaceKey(interfaceName)).build();
    }

    public void addInterface(DataBroker dataBroker, InterfaceDetails interfaceDetails)
            throws TransactionCommitFailedException {
        ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, interfaceDetails.getIfaceIid(), interfaceDetails.getIface());
        tx.submit().checkedGet();
        tx = dataBroker.newReadWriteTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, interfaceDetails.getIfStateId(), interfaceDetails.getIfState());
        tx.submit().checkedGet();
        String interfaceName = interfaceDetails.getName();
        InterfaceInfo interfaceInfo = getInterfaceInfo(dataBroker, interfaceName);
        interfaces.put(interfaceName, interfaceInfo);
    }

    public void addTunnelInterface(DataBroker dataBroker, TunnelInterfaceDetails interfaceDetails)
            throws TransactionCommitFailedException {
        ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, interfaceDetails.getIfaceIid(), interfaceDetails.getIface());
        tx.submit().checkedGet();
        tx = dataBroker.newReadWriteTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, interfaceDetails.getIfStateId(), interfaceDetails.getIfState());
        tx.submit().checkedGet();
        String interfaceName = interfaceDetails.getTrunkInterfaceName();
        InterfaceInfo interfaceInfo = getInterfaceInfo(dataBroker, interfaceName);
        interfaces.put(interfaceName, interfaceInfo);
    }

    @Override
    public InterfaceInfo getInterfaceInfo(String interfaceName) {
        return interfaces.get(interfaceName);
    }

    public InterfaceInfo getInterfaceInfo(DataBroker dataBroker, String interfaceName) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .ietf.interfaces.rev140508.interfaces.state.Interface ifState = InterfaceManagerCommonUtils
                .getInterfaceState(interfaceName, dataBroker);

        if (ifState == null) {
            return null;
        }
        Interface intf = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(interfaceName),
                dataBroker);
        if (intf == null) {
            return null;
        }

        NodeConnectorId ncId = IfmUtil.getNodeConnectorIdFromInterface(intf.getName(), dataBroker);
        InterfaceInfo.InterfaceType interfaceType = IfmUtil.getInterfaceType(intf);
        InterfaceInfo interfaceInfo = new InterfaceInfo(interfaceName);
        BigInteger dpId = org.opendaylight.genius.interfacemanager.globals.IfmConstants.INVALID_DPID;
        Integer portNo = org.opendaylight.genius.interfacemanager.globals.IfmConstants.INVALID_PORT_NO;
        if (ncId != null) {
            dpId = IfmUtil.getDpnFromNodeConnectorId(ncId);
            portNo = Integer.parseInt(IfmUtil.getPortNoFromNodeConnectorId(ncId));
        }
        if (interfaceType == InterfaceInfo.InterfaceType.VLAN_INTERFACE) {
            interfaceInfo = IfmUtil.getVlanInterfaceInfo(interfaceName, intf, dpId);
        } else if (interfaceType == InterfaceInfo.InterfaceType.UNKNOWN_INTERFACE) {
            return null;
        }
        InterfaceInfo.InterfaceOpState opState;
        if (ifState.getOperStatus() == OperStatus.Up) {
            opState = InterfaceInfo.InterfaceOpState.UP;
        } else if (ifState.getOperStatus() == OperStatus.Down) {
            opState = InterfaceInfo.InterfaceOpState.DOWN;
        } else {
            opState = InterfaceInfo.InterfaceOpState.UNKNOWN;
        }
        interfaceInfo.setDpId(dpId);
        interfaceInfo.setPortNo(portNo);
        interfaceInfo.setAdminState(intf.isEnabled()
                ? InterfaceInfo.InterfaceAdminState.ENABLED : InterfaceInfo.InterfaceAdminState.DISABLED);
        interfaceInfo.setInterfaceName(interfaceName);
        Integer lportTag = ifState.getIfIndex();
        interfaceInfo.setInterfaceTag(lportTag);
        interfaceInfo.setInterfaceType(interfaceType);
        interfaceInfo.setGroupId(IfmUtil.getGroupId(lportTag, interfaceType));
        interfaceInfo.setOpState(opState);
        PhysAddress phyAddress = ifState.getPhysAddress();
        if (phyAddress != null) {
            interfaceInfo.setMacAddress(ifState.getPhysAddress().getValue());
        }
        return interfaceInfo;
    }


    String getParentName(String name) {
        return "tap" + name.substring(0, 12);
    }

    @Override
    public List<ActionInfo> getInterfaceEgressActions(String ifName) {
        return IfmUtil.getEgressActionInfosForInterface(ifName, 0, dataBroker, false);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Future<RpcResult<GetEgressActionsForInterfaceOutput>> getEgressActionsForInterface(
            GetEgressActionsForInterfaceInput input) {
        RpcResultBuilder<GetEgressActionsForInterfaceOutput> rpcResultBuilder = null;
        try {
            List<Instruction> instructions = IfmUtil.getEgressInstructionsForInterface(input.getIntfName(),
                    input.getTunnelKey(), dataBroker, false);
            List<Action> actionsList = IfmUtil.getEgressActionsForInterface(input.getIntfName(), input.getTunnelKey(),
                    input.getActionKey(), dataBroker, false);

            GetEgressInstructionsForInterfaceOutputBuilder output = new GetEgressInstructionsForInterfaceOutputBuilder()
                    .setInstruction(instructions);
            GetEgressActionsForInterfaceOutputBuilder output2 = new GetEgressActionsForInterfaceOutputBuilder()
                    .setAction(actionsList);

            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output2.build());
        } catch (Exception e) {
            String errMsg = String.format("Retrieval of egress instructions for the key {%s} failed due to %s",
                    input.getIntfName(), e.getMessage());
            rpcResultBuilder = RpcResultBuilder.<GetEgressActionsForInterfaceOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
        }
        SettableFuture ft = SettableFuture.create();
        ft.set(rpcResultBuilder.build());
        return ft;
    }

    @Override
    public Future<RpcResult<GetDpidFromInterfaceOutput>> getDpidFromInterface(
            GetDpidFromInterfaceInput getDpidFromInterfaceInput) {
        BigInteger dpnId = getDpnForInterface(getDpidFromInterfaceInput.getIntfName());
        GetDpidFromInterfaceOutput output = new GetDpidFromInterfaceOutputBuilder().setDpid(dpnId).build();
        return RpcResultBuilder.success(output).buildFuture();
    }

    @Override
    public BigInteger getDpnForInterface(String interfaceName) {
        return interfaces.get(interfaceName).getDpId();
    }

    @Override
    public BigInteger getDpnForInterface(Interface intrface) {
        return interfaces.get(intrface.getName()).getDpId();
    }


    @Override
    public InterfaceInfo getInterfaceInfoFromOperationalDataStore(
            String interfaceName, InterfaceInfo.InterfaceType interfaceType) {
        return interfaces.get(interfaceName);
    }

    @Override
    public InterfaceInfo getInterfaceInfoFromOperationalDataStore(String interfaceName) {
        return interfaces.get(interfaceName);
    }

    @Override
    public Interface getInterfaceInfoFromConfigDataStore(String interfaceName) {
        return null;//TODO
    }

    @Override
    public boolean isExternalInterface(String interfaceName) {
        return externalInterfaces.containsKey(interfaceName);
    }

    @Override
    public String getPortNameForInterface(NodeConnectorId nodeConnectorId, String interfaceName) {
        return null;
    }

    @Override
    public String getPortNameForInterface(String interfaceName, String interfaceName1) {
        return null;
    }

    @Override
    public OvsdbBridgeAugmentation getOvsdbBridgeForNodeIid(InstanceIdentifier<Node> instanceIdentifier) {
        return null;
    }
}

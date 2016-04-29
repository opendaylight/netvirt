/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.AlivenessMonitorUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OvsInterfaceStateUpdateHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceStateUpdateHelper.class);

    public static List<ListenableFuture<Void>> updateState(InstanceIdentifier<FlowCapableNodeConnector> key,
                                                           AlivenessMonitorService alivenessMonitorService,
                                                           DataBroker dataBroker, String portName,
                                                           FlowCapableNodeConnector flowCapableNodeConnectorNew,
                                                           FlowCapableNodeConnector flowCapableNodeConnectorOld) {
        LOG.debug("Update of Interface State for port: {}", portName);
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        Interface.OperStatus operStatusNew = getOpState(flowCapableNodeConnectorNew);
        MacAddress macAddressNew = flowCapableNodeConnectorNew.getHardwareAddress();

        Interface.OperStatus operStatusOld = getOpState(flowCapableNodeConnectorOld);
        MacAddress macAddressOld = flowCapableNodeConnectorOld.getHardwareAddress();

        boolean opstateModified = false;
        boolean hardwareAddressModified = false;
        if (!operStatusNew.equals(operStatusOld)) {
            opstateModified = true;
        }
        if (!macAddressNew.equals(macAddressOld)) {
            hardwareAddressModified = true;
        }

        if (!opstateModified && !hardwareAddressModified) {
            LOG.debug("If State entry for port: {} Not Modified.", portName);
            return futures;
        }

        InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
        if (hardwareAddressModified) {
            LOG.debug("Hw-Address Modified for Port: {}", portName);
            PhysAddress physAddress = new PhysAddress(macAddressNew.getValue());
            ifaceBuilder.setPhysAddress(physAddress);
        }

        NodeConnectorId nodeConnectorId = InstanceIdentifier.keyOf(key.firstIdentifierOf(NodeConnector.class)).getId();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                handleInterfaceStateUpdates(portName, nodeConnectorId,
                transaction, dataBroker, ifaceBuilder, opstateModified, operStatusNew);

        InterfaceParentEntry interfaceParentEntry =
                InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(portName, dataBroker);
        if (interfaceParentEntry != null && interfaceParentEntry.getInterfaceChildEntry() != null) {
            for (InterfaceChildEntry higherlayerChild : interfaceParentEntry.getInterfaceChildEntry()) {
                handleInterfaceStateUpdates(higherlayerChild.getChildInterface(),
                        nodeConnectorId, transaction, dataBroker, ifaceBuilder, opstateModified, operStatusNew);
                InterfaceParentEntry higherLayerParent =
                        InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(higherlayerChild.getChildInterface(), dataBroker);
                if (higherLayerParent != null && higherLayerParent.getInterfaceChildEntry() != null) {
                    for (InterfaceChildEntry interfaceChildEntry : higherLayerParent.getInterfaceChildEntry()) {
                        //FIXME: If the no. of child entries exceeds 100, perform txn updates in batches of 100.
                        handleInterfaceStateUpdates(interfaceChildEntry.getChildInterface(), nodeConnectorId,
                                transaction, dataBroker, ifaceBuilder, opstateModified, operStatusNew);
                    }
                }
            }
        }else {
            handleTunnelMonitoringUpdates(alivenessMonitorService, dataBroker, iface, operStatusNew, opstateModified);
        }
        futures.add(transaction.submit());
        return futures;
    }

    public static Interface.OperStatus getOpState(FlowCapableNodeConnector flowCapableNodeConnector){
        Interface.OperStatus operStatus =
                (flowCapableNodeConnector.getState().isLive() &&
                        !flowCapableNodeConnector.getConfiguration().isPORTDOWN())
                        ? Interface.OperStatus.Up: Interface.OperStatus.Down;
        return operStatus;
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
    handleInterfaceStateUpdates(String interfaceName, NodeConnectorId nodeConnectorId,WriteTransaction transaction,
                                DataBroker dataBroker, InterfaceBuilder ifaceBuilder, boolean opStateModified,
                                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus opState){
        LOG.debug("updating interface state entry for {}", interfaceName);
        InstanceIdentifier<Interface> ifStateId = IfmUtil.buildStateInterfaceId(interfaceName);
        ifaceBuilder.setKey(new InterfaceKey(interfaceName));
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceName, dataBroker);
        if (modifyOpState(iface, opStateModified)) {
            LOG.debug("updating interface oper status as {} for {}", opState.name(), interfaceName);
            ifaceBuilder.setOperStatus(opState);
        }
        transaction.merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build());

        // if opstate has changed, add or remove ingress flow for l2vlan interfaces accordingly
        if(modifyIngressFlow(iface, opStateModified)) {
            handleVlanIngressFlowUpdates(dataBroker, opState, transaction, iface, nodeConnectorId, ifStateId);
        }
        return iface;
    }

    public static void handleTunnelMonitoringUpdates(AlivenessMonitorService alivenessMonitorService, DataBroker dataBroker,
                                                     org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface,
                                                     Interface.OperStatus operStatus, boolean opStateModified){
        // start/stop monitoring based on opState
        if(!modifyTunnel(iface, opStateModified)){
            return;
        }

        LOG.debug("handling tunnel monitoring updates for {} due to opstate modification", iface.getName());
        if (operStatus == Interface.OperStatus.Down)
            AlivenessMonitorUtils.stopLLDPMonitoring(alivenessMonitorService, dataBroker, iface);
        else
            AlivenessMonitorUtils.startLLDPMonitoring(alivenessMonitorService, dataBroker, iface);
    }

    public static boolean modifyOpState(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface,
                                        boolean opStateModified){
        return (opStateModified && (iface == null || iface != null && iface.isEnabled()));
    }

    public static boolean modifyIngressFlow(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface,
                                        boolean opStateModified){
        return modifyOpState(iface, opStateModified) && iface != null && iface.getAugmentation(IfTunnel.class) == null;
    }

    public static boolean modifyTunnel(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface,
                                            boolean opStateModified){
        return modifyOpState(iface, opStateModified) && iface != null && iface.getAugmentation(IfTunnel.class) != null;
    }

    public static void handleVlanIngressFlowUpdates(DataBroker dataBroker, Interface.OperStatus opState, WriteTransaction transaction,
                                                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface,
                                                    NodeConnectorId nodeConnectorId, InstanceIdentifier<Interface> ifStateId){
        LOG.debug("handling vlan ingress flow updates for {}", iface.getName());
        Interface ifState = InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(ifStateId, dataBroker);
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
        if (opState == org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Up) {
            long portNo = Long.valueOf(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
            List<MatchInfo> matches = FlowBasedServicesUtils.getMatchInfoForVlanPortAtIngressTable(dpId, portNo, iface);
            FlowBasedServicesUtils.installVlanFlow(dpId, portNo, iface, transaction, matches, ifState.getIfIndex());
        } else {
            FlowBasedServicesUtils.removeIngressFlow(iface.getName(), dpId, transaction);
        }
    }
}

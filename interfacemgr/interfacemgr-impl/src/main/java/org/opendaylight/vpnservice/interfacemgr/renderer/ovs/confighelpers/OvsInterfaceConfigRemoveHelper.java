/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers;

import com.google.common.util.concurrent.ListenableFuture;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.AlivenessMonitorUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities.SouthboundUtils;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorStopInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorStopInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OvsInterfaceConfigRemoveHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceConfigRemoveHelper.class);

    public static List<ListenableFuture<Void>> removeConfiguration(DataBroker dataBroker, AlivenessMonitorService alivenessMonitorService,
                                                                   Interface interfaceOld,
                                                                   IdManagerService idManager,
                                                                   IMdsalApiManager mdsalApiManager,
                                                                   ParentRefs parentRefs) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();

        IfTunnel ifTunnel = interfaceOld.getAugmentation(IfTunnel.class);
        if (ifTunnel != null) {
            removeTunnelConfiguration(alivenessMonitorService, parentRefs, dataBroker, interfaceOld,
                    idManager, mdsalApiManager, futures);
        }else {
            removeVlanConfiguration(dataBroker, interfaceOld, t);
            futures.add(t.submit());
        }
        return futures;
    }

    private static void removeVlanConfiguration(DataBroker dataBroker, Interface interfaceOld, WriteTransaction t) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceOld.getName(), dataBroker);
        if (ifState == null) {
            return;
        }

        String ncStr = ifState.getLowerLayerIf().get(0);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ncStr);
        NodeConnector nodeConnector =
                InterfaceManagerCommonUtils.getNodeConnectorFromInventoryOperDS(nodeConnectorId, dataBroker);
        if(nodeConnector != null) {
            FlowCapableNodeConnector flowCapableNodeConnector =
                    nodeConnector.getAugmentation(FlowCapableNodeConnector.class);
            //State state = flowCapableNodeConnector.getState();
            OperStatus operStatus = flowCapableNodeConnector == null ? OperStatus.Down : OperStatus.Up;

            if (ifState.getOperStatus() != operStatus) {
                InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                        IfmUtil.buildStateInterfaceId(interfaceOld.getName());
                InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
                ifaceBuilder.setOperStatus(operStatus);
                ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(interfaceOld.getName()));
                t.merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build());
            }
        }
        IfL2vlan ifL2vlan = interfaceOld.getAugmentation(IfL2vlan.class);
        if (ifL2vlan == null) {
            return;
        }
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
        FlowBasedServicesUtils.removeIngressFlow(interfaceOld, dpId, t);
        if (ifL2vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Trunk) {
            return;
        }

        // For Vlan-Trunk Interface, remove the trunk-member operstates as well...
        InterfaceKey interfaceKey = new InterfaceKey(interfaceOld.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
        if (iface == null) {
            return;
        }

        InterfaceParentEntryKey interfaceParentEntryKey = new InterfaceParentEntryKey(iface.getName());
        InterfaceParentEntry interfaceParentEntry =
                InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(interfaceParentEntryKey, dataBroker);
        if (interfaceParentEntry == null) {
            return;
        }

        List<InterfaceChildEntry> interfaceChildEntries = interfaceParentEntry.getInterfaceChildEntry();
        if (interfaceChildEntries == null) {
            return;
        }

        //FIXME: If the no. of child entries exceeds 100, perform txn updates in batches of 100.
        for (InterfaceChildEntry interfaceChildEntry : interfaceChildEntries) {
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifChildStateId =
                    IfmUtil.buildStateInterfaceId(interfaceChildEntry.getChildInterface());
            t.delete(LogicalDatastoreType.OPERATIONAL, ifChildStateId);
            InterfaceKey childIfKey = new InterfaceKey(interfaceChildEntry.getChildInterface());
            FlowBasedServicesUtils.removeIngressFlow(InterfaceManagerCommonUtils.getInterfaceFromConfigDS(childIfKey, dataBroker), dpId, t);
        }
    }

    private static void removeTunnelConfiguration(AlivenessMonitorService alivenessMonitorService, ParentRefs parentRefs,
                                                  DataBroker dataBroker, Interface interfaceOld,
                                                  IdManagerService idManager, IMdsalApiManager mdsalApiManager,
                                                  List<ListenableFuture<Void>> futures) {

        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        BigInteger dpId = null;
        if (parentRefs != null) {
            dpId = parentRefs.getDatapathNodeIdentifier();
        }

        if (dpId == null) {
            return;
        }

        BridgeRefEntryKey bridgeRefEntryKey = new BridgeRefEntryKey(dpId);
        InstanceIdentifier<BridgeRefEntry> bridgeRefEntryIid =
                InterfaceMetaUtils.getBridgeRefEntryIdentifier(bridgeRefEntryKey);
        BridgeRefEntry bridgeRefEntry =
                InterfaceMetaUtils.getBridgeRefEntryFromOperDS(bridgeRefEntryIid, dataBroker);

        if (bridgeRefEntry != null) {
            InstanceIdentifier<?> bridgeIid = bridgeRefEntry.getBridgeReference().getValue();
            InstanceIdentifier<TerminationPoint> tpIid = SouthboundUtils.createTerminationPointInstanceIdentifier(
                    InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), interfaceOld.getName());
            t.delete(LogicalDatastoreType.CONFIGURATION, tpIid);

            // delete tunnel ingress flow
            NodeConnectorId ncId = IfmUtil.getNodeConnectorIdFromInterface(interfaceOld, dataBroker);
            long portNo = Long.valueOf(IfmUtil.getPortNoFromNodeConnectorId(ncId));
            InterfaceManagerCommonUtils.makeTunnelIngressFlow(futures, mdsalApiManager,
                    interfaceOld.getAugmentation(IfTunnel.class),
                    dpId, portNo, interfaceOld, -1,
                    NwConstants.DEL_FLOW);
        }

        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpId);
        InstanceIdentifier<BridgeEntry> bridgeEntryIid = InterfaceMetaUtils.getBridgeEntryIdentifier(bridgeEntryKey);
        BridgeEntry bridgeEntry = InterfaceMetaUtils.getBridgeEntryFromConfigDS(bridgeEntryIid, dataBroker);
        if (bridgeEntry == null) {
            return;
        }

        List<BridgeInterfaceEntry> bridgeInterfaceEntries = bridgeEntry.getBridgeInterfaceEntry();
        if (bridgeInterfaceEntries == null) {
            return;
        }

        if (bridgeInterfaceEntries.size() <= 1) {
            t.delete(LogicalDatastoreType.CONFIGURATION, bridgeEntryIid);
        } else {
            BridgeInterfaceEntryKey bridgeInterfaceEntryKey =
                    new BridgeInterfaceEntryKey(interfaceOld.getName());
            InstanceIdentifier<BridgeInterfaceEntry> bridgeInterfaceEntryIid =
                    InterfaceMetaUtils.getBridgeInterfaceEntryIdentifier(bridgeEntryKey,
                            bridgeInterfaceEntryKey);
            t.delete(LogicalDatastoreType.CONFIGURATION, bridgeInterfaceEntryIid);
        }
        futures.add(t.submit());
        // stop LLDP monitoring for the tunnel interface
        AlivenessMonitorUtils.stopLLDPMonitoring(alivenessMonitorService, dataBroker, interfaceOld);
    }
}
/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OvsInterfaceStateRemoveHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceStateRemoveHelper.class);

    public static List<ListenableFuture<Void>> removeState(IdManagerService idManager, IMdsalApiManager mdsalApiManager,
                                                           AlivenessMonitorService alivenessMonitorService,
                                                           InstanceIdentifier<FlowCapableNodeConnector> key,
                                                           DataBroker dataBroker, String portName, FlowCapableNodeConnector fcNodeConnectorOld) {
        LOG.debug("Removing interface-state for port: {}", portName);
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        InstanceIdentifier<Interface> ifStateId = IfmUtil.buildStateInterfaceId(portName);
        /* Remove entry from if-index-interface-name map and deallocate Id from Idmanager. */
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface interfaceState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(portName, dataBroker);
        if(interfaceState != null) {
            InterfaceMetaUtils.removeLportTagInterfaceMap(transaction, idManager, dataBroker, interfaceState.getName(), interfaceState.getIfIndex());
        }

        transaction.delete(LogicalDatastoreType.OPERATIONAL, ifStateId);

        // For Vlan-Trunk Interface, remove the trunk-member operstates as well...
        InterfaceKey interfaceKey = new InterfaceKey(portName);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
        if (iface == null) {
            futures.add(transaction.submit());
            return futures;
        }

        // If this interface is a tunnel interface, remove the tunnel ingress flow and stop lldp monitoring
        IfTunnel tunnel = iface.getAugmentation(IfTunnel.class);
        if(tunnel != null){
            NodeConnectorId nodeConnectorId = InstanceIdentifier.keyOf(key.firstIdentifierOf(NodeConnector.class)).getId();
            BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
            long portNo = Long.valueOf(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
            InterfaceManagerCommonUtils.makeTunnelIngressFlow(futures, mdsalApiManager, tunnel, dpId, portNo, iface,
                    NwConstants.DEL_FLOW);
            futures.add(transaction.submit());
            AlivenessMonitorUtils.stopLLDPMonitoring(alivenessMonitorService, dataBroker, iface);
            return futures;
        }

        InterfaceParentEntryKey interfaceParentEntryKey = new InterfaceParentEntryKey(iface.getName());
        InterfaceParentEntry interfaceParentEntry =
                InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(interfaceParentEntryKey, dataBroker);
        if (interfaceParentEntry == null) {
            futures.add(transaction.submit());
            return futures;
        }

        List<InterfaceChildEntry> interfaceChildEntries = interfaceParentEntry.getInterfaceChildEntry();
        if (interfaceChildEntries == null) {
            futures.add(transaction.submit());
            return futures;
        }

        //FIXME: If the no. of child entries exceeds 100, perform txn updates in batches of 100.
        for (InterfaceChildEntry interfaceChildEntry : interfaceChildEntries) {
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifChildStateId =
                    IfmUtil.buildStateInterfaceId(interfaceChildEntry.getChildInterface());
            transaction.delete(LogicalDatastoreType.OPERATIONAL, ifChildStateId);
        }

       /* Below code will be needed if we want to update the vlan-trunk in the of-port.
       NodeConnectorId nodeConnectorId = InstanceIdentifier.keyOf(key.firstIdentifierOf(NodeConnector.class)).getId();
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));

        BridgeRefEntryKey BridgeRefEntryKey = new BridgeRefEntryKey(dpId);
        InstanceIdentifier<BridgeRefEntry> dpnBridgeEntryIid =
                InterfaceMetaUtils.getBridgeRefEntryIdentifier(BridgeRefEntryKey);
        BridgeRefEntry bridgeRefEntry =
                InterfaceMetaUtils.getBridgeRefEntryFromOperDS(dpnBridgeEntryIid, dataBroker);
        if (bridgeRefEntry == null) {
            futures.add(t.submit());
            return futures;
        }

        InstanceIdentifier<?> bridgeIid = bridgeRefEntry.getBridgeReference().getValue();
        InstanceIdentifier<TerminationPoint> tpIid = SouthboundUtils.createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), interfaceOld.getName());
        t.delete(LogicalDatastoreType.CONFIGURATION, tpIid); */

        futures.add(transaction.submit());
        return futures;
    }
}
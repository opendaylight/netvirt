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
            removeVlanConfiguration(dataBroker, parentRefs, interfaceOld, t);
            futures.add(t.submit());
        }
        return futures;
    }

    private static void removeVlanConfiguration(DataBroker dataBroker, ParentRefs parentRefs, Interface interfaceOld, WriteTransaction transaction) {
        LOG.debug("removing vlan configuration for {}",interfaceOld.getName());
        IfL2vlan ifL2vlan = interfaceOld.getAugmentation(IfL2vlan.class);
        if (ifL2vlan == null || ifL2vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Trunk) {
            return;
        }
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceOld.getName(), dataBroker);
        if (ifState == null) {
            LOG.debug("could not fetch interface state corresponding to {}",interfaceOld.getName());
            return;
        }

        InterfaceManagerCommonUtils.updateOperStatus(interfaceOld.getName(), OperStatus.Down, transaction);
        NodeConnectorId ncId = new NodeConnectorId(ifState.getLowerLayerIf().get(0));
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(ncId));
        FlowBasedServicesUtils.removeIngressFlow(interfaceOld.getName(), dpId, transaction);
        // For Vlan-Trunk Interface, remove the trunk-member operstates as well...

        InterfaceParentEntryKey interfaceParentEntryKey = new InterfaceParentEntryKey(interfaceOld.getName());
        InterfaceParentEntry interfaceParentEntry =
                InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(interfaceParentEntryKey, dataBroker);
        if (interfaceParentEntry == null || interfaceParentEntry.getInterfaceChildEntry() == null) {
            return;
        }

        //FIXME: If the no. of child entries exceeds 100, perform txn updates in batches of 100.
        for (InterfaceChildEntry interfaceChildEntry : interfaceParentEntry.getInterfaceChildEntry()) {
            LOG.debug("removing interface state for  vlan trunk member {}",interfaceChildEntry.getChildInterface());
            InterfaceManagerCommonUtils.deleteStateEntry(interfaceChildEntry.getChildInterface(), transaction);
            FlowBasedServicesUtils.removeIngressFlow(interfaceChildEntry.getChildInterface(), dpId, transaction);
        }
    }

    private static void removeTunnelConfiguration(AlivenessMonitorService alivenessMonitorService, ParentRefs parentRefs,
                                                  DataBroker dataBroker, Interface interfaceOld,
                                                  IdManagerService idManager, IMdsalApiManager mdsalApiManager,
                                                  List<ListenableFuture<Void>> futures) {
        LOG.debug("removing tunnel configuration for {}",interfaceOld.getName());
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
            LOG.debug("removing termination point for {}",interfaceOld.getName());
            InstanceIdentifier<?> bridgeIid = bridgeRefEntry.getBridgeReference().getValue();
            InstanceIdentifier<TerminationPoint> tpIid = SouthboundUtils.createTerminationPointInstanceIdentifier(
                    InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), interfaceOld.getName());
            t.delete(LogicalDatastoreType.CONFIGURATION, tpIid);

            // delete tunnel ingress flow
            LOG.debug("removing tunnel ingress flow for {}",interfaceOld.getName());
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
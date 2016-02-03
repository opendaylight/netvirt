/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.xtend.lib.annotations.Data;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.AlivenessMonitorUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities.SouthboundUtils;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.params.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.start.input.ConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.*;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class OvsInterfaceConfigAddHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceConfigAddHelper.class);

    public static List<ListenableFuture<Void>> addConfiguration(DataBroker dataBroker, ParentRefs parentRefs,
                                                                Interface interfaceNew, IdManagerService idManager,
                                                                AlivenessMonitorService alivenessMonitorService,
                                                                IMdsalApiManager mdsalApiManager) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();

        IfTunnel ifTunnel = interfaceNew.getAugmentation(IfTunnel.class);
        if (ifTunnel != null) {
            addTunnelConfiguration(dataBroker, parentRefs, interfaceNew, idManager, alivenessMonitorService,
                    mdsalApiManager, futures);
            return futures;
        }

        addVlanConfiguration(interfaceNew, dataBroker, idManager, futures);
        return futures;
    }

    private static void addVlanConfiguration(Interface interfaceNew, DataBroker dataBroker, IdManagerService idManager,
                                             List<ListenableFuture<Void>> futures) {
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceNew.getName(), dataBroker);

        if (ifState == null) {
            return;
        }
        updateStateEntry(interfaceNew, transaction, ifState);

        IfL2vlan ifL2vlan = interfaceNew.getAugmentation(IfL2vlan.class);
        if (ifL2vlan == null || ifL2vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Trunk) {
            return;
        }

        InterfaceParentEntryKey interfaceParentEntryKey = new InterfaceParentEntryKey(interfaceNew.getName());
        InterfaceParentEntry interfaceParentEntry =
                InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(interfaceParentEntryKey, dataBroker);
        if (interfaceParentEntry == null) {
            return;
        }

        List<InterfaceChildEntry> interfaceChildEntries = interfaceParentEntry.getInterfaceChildEntry();
        if (interfaceChildEntries == null) {
            return;
        }

        OperStatus operStatus = ifState.getOperStatus();
        PhysAddress physAddress = ifState.getPhysAddress();
        AdminStatus adminStatus = ifState.getAdminStatus();
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ifState.getLowerLayerIf().get(0));

        //FIXME: If the no. of child entries exceeds 100, perform txn updates in batches of 100.
        for (InterfaceChildEntry interfaceChildEntry : interfaceChildEntries) {
            InterfaceKey childIfKey = new InterfaceKey(interfaceChildEntry.getChildInterface());
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface ifaceChild =
                    InterfaceManagerCommonUtils.getInterfaceFromConfigDS(childIfKey, dataBroker);

            if (!ifaceChild.isEnabled()) {
                operStatus = OperStatus.Down;
            }

            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifChildStateId =
                    IfmUtil.buildStateInterfaceId(ifaceChild.getName());
            List<String> childLowerLayerIfList = new ArrayList<>();
            childLowerLayerIfList.add(0, nodeConnectorId.getValue());
            childLowerLayerIfList.add(1, interfaceNew.getName());
            InterfaceBuilder childIfaceBuilder = new InterfaceBuilder().setAdminStatus(adminStatus)
                    .setOperStatus(operStatus).setPhysAddress(physAddress).setLowerLayerIf(childLowerLayerIfList);
            childIfaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(ifaceChild.getName()));
            transaction.put(LogicalDatastoreType.OPERATIONAL, ifChildStateId, childIfaceBuilder.build(), true);
        }
        futures.add(transaction.submit());
    }

    private static void addTunnelConfiguration(DataBroker dataBroker, ParentRefs parentRefs,
                                              Interface interfaceNew, IdManagerService idManager,
                                              AlivenessMonitorService alivenessMonitorService,
                                              IMdsalApiManager mdsalApiManager,
                                              List<ListenableFuture<Void>> futures) {
        LOG.debug("adding tunnel configuration for {}", interfaceNew.getName());
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        if (parentRefs == null) {
            LOG.warn("ParentRefs for interface: {} Not Found. " +
                    "Creation of Tunnel OF-Port not supported when dpid not provided.", interfaceNew.getName());
            return;
        }

        BigInteger dpId = parentRefs.getDatapathNodeIdentifier();
        if (dpId == null) {
            LOG.warn("dpid for interface: {} Not Found. No DPID provided. " +
                    "Creation of OF-Port not supported.", interfaceNew.getName());
            return;
        }

        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpId);
        BridgeInterfaceEntryKey bridgeInterfaceEntryKey = new BridgeInterfaceEntryKey(interfaceNew.getName());

        LOG.debug("creating bridge interfaceEntry in ConfigDS {}", bridgeEntryKey);
        InterfaceMetaUtils.createBridgeInterfaceEntryInConfigDS(bridgeEntryKey, bridgeInterfaceEntryKey,
                    interfaceNew.getName(), transaction);
        futures.add(transaction.submit());

        // create bridge on switch, if switch is connected
        BridgeRefEntryKey BridgeRefEntryKey = new BridgeRefEntryKey(dpId);
        InstanceIdentifier<BridgeRefEntry> dpnBridgeEntryIid =
                InterfaceMetaUtils.getBridgeRefEntryIdentifier(BridgeRefEntryKey);
        BridgeRefEntry bridgeRefEntry =
                InterfaceMetaUtils.getBridgeRefEntryFromOperDS(dpnBridgeEntryIid, dataBroker);
        if(bridgeRefEntry != null && bridgeRefEntry.getBridgeReference() != null) {
            LOG.debug("creating bridge interface on dpn {}", bridgeEntryKey);
            InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid =
                    (InstanceIdentifier<OvsdbBridgeAugmentation>) bridgeRefEntry.getBridgeReference().getValue();
            Optional<OvsdbBridgeAugmentation> bridgeNodeOptional =
                    IfmUtil.read(LogicalDatastoreType.OPERATIONAL, bridgeIid, dataBroker);
            if (bridgeNodeOptional.isPresent()) {
                OvsdbBridgeAugmentation ovsdbBridgeAugmentation = bridgeNodeOptional.get();
                String bridgeName = ovsdbBridgeAugmentation.getBridgeName().getValue();
                SouthboundUtils.addPortToBridge(bridgeIid, interfaceNew,
                        ovsdbBridgeAugmentation, bridgeName, interfaceNew.getName(), dataBroker, futures);

                // if TEP is already configured on switch, start LLDP monitoring and program tunnel ingress flow
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                        InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceNew.getName(), dataBroker);
                if(ifState != null){
                    NodeConnectorId ncId = IfmUtil.getNodeConnectorIdFromInterface(ifState);
                    if(ncId != null) {
                        long portNo = Long.valueOf(IfmUtil.getPortNoFromNodeConnectorId(ncId));
                        InterfaceManagerCommonUtils.makeTunnelIngressFlow(futures, mdsalApiManager, interfaceNew.getAugmentation(IfTunnel.class),
                                dpId, portNo, interfaceNew, ifState.getIfIndex(), NwConstants.ADD_FLOW);
                        // start LLDP monitoring for the tunnel interface
                        AlivenessMonitorUtils.startLLDPMonitoring(alivenessMonitorService, dataBroker, interfaceNew);
                    }
                }
            }
        }
    }

    private static void updateStateEntry(Interface interfaceNew, WriteTransaction transaction,
                                         org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                IfmUtil.buildStateInterfaceId(interfaceNew.getName());
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
        if (!interfaceNew.isEnabled() && ifState.getOperStatus() != OperStatus.Down) {
            ifaceBuilder.setOperStatus(OperStatus.Down);
            ifaceBuilder.setType(interfaceNew.getType());
            ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(interfaceNew.getName()));
            transaction.merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build());
        }
    }
}

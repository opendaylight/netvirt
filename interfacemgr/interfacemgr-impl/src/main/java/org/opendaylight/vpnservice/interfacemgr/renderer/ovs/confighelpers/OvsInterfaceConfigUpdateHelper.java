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
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.LivenessState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class OvsInterfaceConfigUpdateHelper{
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceConfigUpdateHelper.class);

    public static List<ListenableFuture<Void>> updateConfiguration(DataBroker dataBroker,  AlivenessMonitorService alivenessMonitorService,
                                                                   IdManagerService idManager, IMdsalApiManager mdsalApiManager,
                                                                   Interface interfaceNew, Interface interfaceOld) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();

        if(portAttributesModified(interfaceOld, interfaceNew)) {
            futures.addAll(OvsInterfaceConfigRemoveHelper.removeConfiguration(dataBroker, alivenessMonitorService, interfaceOld, idManager,
                    mdsalApiManager, interfaceOld.getAugmentation(ParentRefs.class)));
            futures.addAll(OvsInterfaceConfigAddHelper.addConfiguration(dataBroker,
                    interfaceNew.getAugmentation(ParentRefs.class), interfaceNew, idManager));
            return futures;
        }

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceNew.getName(), dataBroker);
        if (ifState == null) {
            futures.addAll(OvsInterfaceConfigAddHelper.addConfiguration(dataBroker,
                    interfaceNew.getAugmentation(ParentRefs.class), interfaceNew, idManager));
            return futures;
        }

        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        if (interfaceNew.isEnabled() != interfaceOld.isEnabled()) {
            OperStatus operStatus;
            if (!interfaceNew.isEnabled()) {
                operStatus = OperStatus.Down;
            } else {
                String ncStr = ifState.getLowerLayerIf().get(0);
                NodeConnectorId nodeConnectorId = new NodeConnectorId(ncStr);
                NodeConnector nodeConnector =
                        InterfaceManagerCommonUtils.getNodeConnectorFromInventoryOperDS(nodeConnectorId, dataBroker);
                FlowCapableNodeConnector flowCapableNodeConnector =
                        nodeConnector.getAugmentation(FlowCapableNodeConnector.class);
                //State state = flowCapableNodeConnector.getState();
                operStatus = flowCapableNodeConnector == null ? OperStatus.Down : OperStatus.Up;
            }

            String ifName = interfaceNew.getName();
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                    IfmUtil.buildStateInterfaceId(interfaceNew.getName());
            InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
            ifaceBuilder.setOperStatus(operStatus);
            ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(ifName));
            t.merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build());

            IfL2vlan ifL2vlan = interfaceNew.getAugmentation(IfL2vlan.class);
            if (ifL2vlan == null || ifL2vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Trunk) {
                futures.add(t.submit());
                // stop tunnel monitoring if admin state is disabled for a vxlan trunk interface
                if(!interfaceNew.isEnabled()){
                    AlivenessMonitorUtils.stopLLDPMonitoring(alivenessMonitorService, dataBroker, interfaceNew);
                }
                return futures;
            }

            InterfaceKey interfaceKey = new InterfaceKey(ifName);
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                    InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
            if (iface == null) {
                futures.add(t.submit());
                return futures;
            }

            InterfaceParentEntryKey interfaceParentEntryKey = new InterfaceParentEntryKey(iface.getName());
            InterfaceParentEntry interfaceParentEntry =
                    InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(interfaceParentEntryKey, dataBroker);
            if (interfaceParentEntry == null) {
                futures.add(t.submit());
                return futures;
            }

            List<InterfaceChildEntry> interfaceChildEntries = interfaceParentEntry.getInterfaceChildEntry();
            if (interfaceChildEntries == null) {
                futures.add(t.submit());
                return futures;
            }

            for (InterfaceChildEntry interfaceChildEntry : interfaceChildEntries) {
                InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifChildStateId =
                        IfmUtil.buildStateInterfaceId(interfaceChildEntry.getChildInterface());
                InterfaceBuilder ifaceBuilderChild = new InterfaceBuilder();
                ifaceBuilderChild.setOperStatus(operStatus);
                ifaceBuilderChild.setKey(IfmUtil.getStateInterfaceKeyFromName(interfaceChildEntry.getChildInterface()));
                t.merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilderChild.build());
            }
        }

        futures.add(t.submit());
        return futures;
    }

    private static boolean portAttributesModified(Interface interfaceOld, Interface interfaceNew) {
        ParentRefs parentRefsOld = interfaceOld.getAugmentation(ParentRefs.class);
        ParentRefs parentRefsNew = interfaceNew.getAugmentation(ParentRefs.class);
        if (checkAugmentations(parentRefsOld, parentRefsNew)) {
            return true;
        }

        IfL2vlan ifL2vlanOld = interfaceOld.getAugmentation(IfL2vlan.class);
        IfL2vlan ifL2vlanNew = interfaceNew.getAugmentation(IfL2vlan.class);
        if (checkAugmentations(ifL2vlanOld, ifL2vlanNew)) {
            return true;
        }

        IfTunnel ifTunnelOld = interfaceOld.getAugmentation(IfTunnel.class);
        IfTunnel ifTunnelNew = interfaceNew.getAugmentation(IfTunnel.class);
        if (checkAugmentations(ifTunnelOld, ifTunnelNew)) {
            return true;
        }

        return false;
    }

    private static<T> boolean checkAugmentations(T oldAug, T newAug) {
        if ((oldAug != null && newAug == null) ||
                (oldAug == null && newAug != null)) {
            return true;
        }

        if (newAug != null && oldAug != null && !newAug.equals(oldAug)) {
            return true;
        }

        return false;
    }

}
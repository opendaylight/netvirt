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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();

        Interface.OperStatus operStatusNew =
                flowCapableNodeConnectorNew.getState().isLinkDown() ? Interface.OperStatus.Down : Interface.OperStatus.Up;
        Interface.AdminStatus adminStatusNew =
                flowCapableNodeConnectorNew.getState().isBlocked() ? Interface.AdminStatus.Down : Interface.AdminStatus.Up;
        MacAddress macAddressNew = flowCapableNodeConnectorNew.getHardwareAddress();

        Interface.OperStatus operStatusOld =
                flowCapableNodeConnectorOld.getState().isLinkDown() ? Interface.OperStatus.Down : Interface.OperStatus.Up;
        Interface.AdminStatus adminStatusOld =
                flowCapableNodeConnectorOld.getState().isBlocked() ? Interface.AdminStatus.Down : Interface.AdminStatus.Up;
        MacAddress macAddressOld = flowCapableNodeConnectorOld.getHardwareAddress();

        boolean opstateModified = false;
        boolean adminStateModified = false;
        boolean hardwareAddressModified = false;
        if (!operStatusNew.equals(operStatusOld)) {
            opstateModified = true;
        }
        if (!adminStatusNew.equals(adminStatusOld)) {
            adminStateModified = true;
        }
        if (!macAddressNew.equals(macAddressOld)) {
            hardwareAddressModified = true;
        }

        if (!opstateModified && !adminStateModified && !hardwareAddressModified) {
            LOG.debug("If State entry for port: {} Not Modified.", portName);
            return futures;
        }

        InstanceIdentifier<Interface> ifStateId = IfmUtil.buildStateInterfaceId(portName);
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface = null;
        boolean modified = false;
        if (opstateModified) {
            LOG.debug("Opstate Modified for Port: {}", portName);
            InterfaceKey interfaceKey = new InterfaceKey(portName);
             iface = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);

            // If interface config admin state is disabled, set operstate of the Interface State entity to Down.
            if (iface != null && !iface.isEnabled()) {
                operStatusNew = Interface.OperStatus.Down;
            }

            ifaceBuilder.setOperStatus(operStatusNew);
            modified = true;
        }

        if (adminStateModified) {
            LOG.debug("Admin state Modified for Port: {}", portName);
            ifaceBuilder.setAdminStatus(adminStatusNew);
            modified = true;
        }

        if (hardwareAddressModified) {
            LOG.debug("Hw-Address Modified for Port: {}", portName);
            PhysAddress physAddress = new PhysAddress(macAddressNew.getValue());
            ifaceBuilder.setPhysAddress(physAddress);
            modified = true;
        }

        /* FIXME: Is there chance that lower layer node-connector info is updated.
                  Not Considering for now.
         */

        if (modified) {
            ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(portName));
            t.merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build());

            InterfaceParentEntryKey interfaceParentEntryKey = new InterfaceParentEntryKey(portName);
            InterfaceParentEntry interfaceParentEntry =
                    InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(interfaceParentEntryKey, dataBroker);
            if (interfaceParentEntry == null || interfaceParentEntry.getInterfaceChildEntry() == null) {
                futures.add(t.submit());
                // start/stop monitoring based on opState
                IfTunnel ifTunnel = iface.getAugmentation(IfTunnel.class);
                if(ifTunnel != null) {
                    if (operStatusNew == Interface.OperStatus.Down)
                        AlivenessMonitorUtils.stopLLDPMonitoring(alivenessMonitorService, dataBroker, iface);
                    else
                        AlivenessMonitorUtils.startLLDPMonitoring(alivenessMonitorService, dataBroker, iface);
                }
                return futures;
            }
            for(InterfaceChildEntry higherlayerChild : interfaceParentEntry.getInterfaceChildEntry()) {
                InstanceIdentifier<Interface> higherLayerIfChildStateId =
                        IfmUtil.buildStateInterfaceId(higherlayerChild.getChildInterface());
                t.merge(LogicalDatastoreType.OPERATIONAL, higherLayerIfChildStateId, ifaceBuilder.build());
                InterfaceParentEntryKey higherLayerParentEntryKey = new InterfaceParentEntryKey(higherlayerChild.getChildInterface());
                InterfaceParentEntry higherLayerParent =
                        InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(higherLayerParentEntryKey, dataBroker);
                if(higherLayerParent != null && higherLayerParent.getInterfaceChildEntry() != null) {
                    for (InterfaceChildEntry interfaceChildEntry : higherLayerParent.getInterfaceChildEntry()) {
                        LOG.debug("Updating if-state entries for Vlan-Trunk Members for port: {}", portName);
                        //FIXME: If the no. of child entries exceeds 100, perform txn updates in batches of 100.
                        InstanceIdentifier<Interface> ifChildStateId =
                                IfmUtil.buildStateInterfaceId(interfaceChildEntry.getChildInterface());
                        t.merge(LogicalDatastoreType.OPERATIONAL, ifChildStateId, ifaceBuilder.build());
                    }
                }
            }
        }

        futures.add(t.submit());
        return futures;
    }
}
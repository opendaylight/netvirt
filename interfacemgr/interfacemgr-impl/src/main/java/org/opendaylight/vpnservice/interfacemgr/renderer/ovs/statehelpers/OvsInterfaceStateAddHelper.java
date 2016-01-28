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
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.VpnConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.AlivenessMonitorUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * This worker is responsible for adding the openflow-interfaces/of-port-info container
 * in odl-interface-openflow yang.
 * Where applicable:
    * Create the entries in Interface-State OperDS.
    * Create the entries in Inventory OperDS.
 */

public class OvsInterfaceStateAddHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceStateAddHelper.class);

    public static List<ListenableFuture<Void>> addState(DataBroker dataBroker, IdManagerService idManager,
                                                        IMdsalApiManager mdsalApiManager,AlivenessMonitorService alivenessMonitorService,
                                                        NodeConnectorId nodeConnectorId, String portName, FlowCapableNodeConnector fcNodeConnectorNew) {
        LOG.debug("Adding Interface State to Oper DS for port: {}", portName);
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        //Retrieve PbyAddress & OperState from the DataObject
        PhysAddress physAddress = new PhysAddress(fcNodeConnectorNew.getHardwareAddress().getValue());
        /*FIXME
        State state = fcNodeConnectorNew.getState();
        Interface.OperStatus operStatus =
                fcNodeConnectorNew == null ? Interface.OperStatus.Down : Interface.OperStatus.Up;
        Interface.AdminStatus adminStatus = state.isBlocked() ? Interface.AdminStatus.Down : Interface.AdminStatus.Up;
        */
        Interface.OperStatus operStatus = Interface.OperStatus.Up;
        Interface.AdminStatus adminStatus = Interface.AdminStatus.Up;
        InterfaceKey interfaceKey = new InterfaceKey(portName);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);

        if (iface != null && !iface.isEnabled()) {
            operStatus = Interface.OperStatus.Down;
        }

        List<String> lowerLayerIfList = new ArrayList<>();
        lowerLayerIfList.add(nodeConnectorId.getValue());

        Integer ifIndex = IfmUtil.allocateId(idManager, IfmConstants.IFM_IDPOOL_NAME, portName);
        InstanceIdentifier<Interface> ifStateId = IfmUtil.buildStateInterfaceId(portName);
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder().setOperStatus(operStatus)
                .setAdminStatus(adminStatus).setPhysAddress(physAddress).setIfIndex(ifIndex).setLowerLayerIf(lowerLayerIfList)
                .setKey(IfmUtil.getStateInterfaceKeyFromName(portName));
        transaction.put(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build(), true);

        // allocate lport tag and set in if-index
        InterfaceMetaUtils.createLportTagInterfaceMap(transaction, portName, ifIndex);
        if (iface == null) {
            futures.add(transaction.submit());
            return futures;
        }

        // If this interface is a tunnel interface, create the tunnel ingress flow
        IfTunnel tunnel = iface.getAugmentation(IfTunnel.class);
        if(tunnel != null){
            BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
            long portNo = Long.valueOf(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
            InterfaceManagerCommonUtils.makeTunnelIngressFlow(futures, mdsalApiManager, tunnel,dpId, portNo, iface,
                    ifIndex, NwConstants.ADD_FLOW);
            futures.add(transaction.submit());
            AlivenessMonitorUtils.startLLDPMonitoring(alivenessMonitorService, dataBroker, iface);
            return futures;
        }

        // If this interface maps to a Vlan trunk entity, operational states of all the vlan-trunk-members
        // should also be created here.
        IfL2vlan ifL2vlan = iface.getAugmentation(IfL2vlan.class);
        if (ifL2vlan == null || ifL2vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Trunk) {
            futures.add(transaction.submit());
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
        //List<Trunks> trunks = new ArrayList<>();
        for (InterfaceChildEntry interfaceChildEntry : interfaceChildEntries) {
            InterfaceKey childIfKey = new InterfaceKey(interfaceChildEntry.getChildInterface());
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface ifaceChild =
                    InterfaceManagerCommonUtils.getInterfaceFromConfigDS(childIfKey, dataBroker);

            // IfL2vlan ifL2vlanChild = iface.getAugmentation(IfL2vlan.class);
            // trunks.add(new TrunksBuilder().setTrunk(ifL2vlanChild.getVlanId()).build());

            if (!ifaceChild.isEnabled()) {
                operStatus = Interface.OperStatus.Down;
            }

            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifChildStateId =
                    IfmUtil.buildStateInterfaceId(ifaceChild.getName());
            List<String> childLowerLayerIfList = new ArrayList<>();
            childLowerLayerIfList.add(0, nodeConnectorId.getValue());
            childLowerLayerIfList.add(1, iface.getName());
            ifIndex = IfmUtil.allocateId(idManager, IfmConstants.IFM_IDPOOL_NAME, ifaceChild.getName());
            InterfaceBuilder childIfaceBuilder = new InterfaceBuilder().setAdminStatus(adminStatus).setOperStatus(operStatus)
                    .setPhysAddress(physAddress).setLowerLayerIf(childLowerLayerIfList).setIfIndex(ifIndex);
            childIfaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(ifaceChild.getName()));
            transaction.put(LogicalDatastoreType.OPERATIONAL, ifChildStateId, childIfaceBuilder.build(), true);

            // create lportTag Interface Map
            InterfaceMetaUtils.createLportTagInterfaceMap(transaction, ifaceChild.getName(), ifIndex);
        }

        /** Below code will be needed if we want to update the vlan-trunks on the of-port
        if (trunks.isEmpty()) {
            futures.add(t.submit());
            return futures;
        }

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

        InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid =
                (InstanceIdentifier<OvsdbBridgeAugmentation>)bridgeRefEntry.getBridgeReference().getValue();
        VlanTrunkSouthboundUtils.addTerminationPointWithTrunks(bridgeIid, trunks, iface.getName(), t);
         */

        futures.add(transaction.submit());
        return futures;
    }
}
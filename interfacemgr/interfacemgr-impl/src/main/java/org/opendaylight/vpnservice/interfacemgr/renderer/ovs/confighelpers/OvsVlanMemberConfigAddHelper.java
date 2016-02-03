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
import org.opendaylight.vpnservice.VpnUtil;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OvsVlanMemberConfigAddHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsVlanMemberConfigAddHelper.class);
    public static List<ListenableFuture<Void>> addConfiguration(DataBroker dataBroker, ParentRefs parentRefs,
                                                                Interface interfaceNew, IfL2vlan ifL2vlan,
                                                                IdManagerService idManager) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();

        InterfaceParentEntryKey interfaceParentEntryKey = new InterfaceParentEntryKey(parentRefs.getParentInterface());
        createInterfaceParentEntryIfNotPresent(dataBroker, t, interfaceParentEntryKey, parentRefs.getParentInterface());
        createInterfaceChildEntry(dataBroker, idManager, t, interfaceParentEntryKey, interfaceNew.getName());

        InterfaceKey interfaceKey = new InterfaceKey(parentRefs.getParentInterface());
        Interface ifaceParent = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
        if (ifaceParent == null) {
            LOG.info("Parent Interface: {} not found when adding child interface: {}",
                    parentRefs.getParentInterface(), interfaceNew.getName());
            futures.add(t.submit());
            return futures;
        }

        IfL2vlan parentIfL2Vlan = ifaceParent.getAugmentation(IfL2vlan.class);
        if (parentIfL2Vlan == null || parentIfL2Vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Trunk) {
            LOG.error("Parent Interface: {} not of trunk Type when adding trunk-member: {}", ifaceParent, interfaceNew);
            futures.add(t.submit());
            return futures;
        }

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(parentRefs.getParentInterface(), dataBroker);
        if (ifState != null) {
            OperStatus operStatus = ifState.getOperStatus();
            AdminStatus adminStatus = ifState.getAdminStatus();
            PhysAddress physAddress = ifState.getPhysAddress();

            if (!interfaceNew.isEnabled()) {
                operStatus = OperStatus.Down;
            }

            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                    IfmUtil.buildStateInterfaceId(interfaceNew.getName());
            List<String> lowerLayerIfList = new ArrayList<>();
            lowerLayerIfList.add(ifState.getLowerLayerIf().get(0));
            lowerLayerIfList.add(parentRefs.getParentInterface());
            Integer ifIndex = IfmUtil.allocateId(idManager, IfmConstants.IFM_IDPOOL_NAME, interfaceNew.getName());
            InterfaceBuilder ifaceBuilder = new InterfaceBuilder().setAdminStatus(adminStatus).setOperStatus(operStatus)
                    .setPhysAddress(physAddress).setLowerLayerIf(lowerLayerIfList).setIfIndex(ifIndex);
            ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(interfaceNew.getName()));
            t.put(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build(), true);

            // create lportTag Interface Map
            InterfaceMetaUtils.createLportTagInterfaceMap(t, interfaceNew.getName(), ifIndex);
            //Installing vlan flow for vlan member
            NodeConnectorId nodeConnectorId = new NodeConnectorId(ifState.getLowerLayerIf().get(0));
            BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
            long portNo = Long.valueOf(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
            if (operStatus == OperStatus.Up) {
                List<MatchInfo> matches = FlowBasedServicesUtils.getMatchInfoForVlanPortAtIngressTable(dpId, portNo, interfaceNew);
                FlowBasedServicesUtils.installVlanFlow(dpId, portNo, interfaceNew, t, matches, ifIndex);
            }

            // FIXME: Maybe, add the new interface to the higher-layer if of the parent interface-state.
            // That may not serve any purpose though for interface manager.... Unless some external parties are interested in it.

            /* FIXME -- Below code is needed to add vlan-trunks to the of-port. Is this really needed.
            String lowerLayerIf = ifState.getLowerLayerIf().get(0);

            NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
            BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));

            BridgeRefEntryKey BridgeRefEntryKey = new BridgeRefEntryKey(dpId);
            InstanceIdentifier<BridgeRefEntry> dpnBridgeEntryIid =
                    InterfaceMetaUtils.getBridgeRefEntryIdentifier(BridgeRefEntryKey);
            BridgeRefEntry bridgeRefEntry =
                    InterfaceMetaUtils.getBridgeRefEntryFromOperDS(dpnBridgeEntryIid, dataBroker);
            if (bridgeRefEntry != null) {
                InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid =
                        (InstanceIdentifier<OvsdbBridgeAugmentation>)bridgeRefEntry.getBridgeReference().getValue();
                Optional<OvsdbBridgeAugmentation> bridgeNodeOptional =
                        IfmUtil.read(LogicalDatastoreType.OPERATIONAL, bridgeIid, dataBroker);
                if (bridgeNodeOptional.isPresent()) {
                    OvsdbBridgeAugmentation ovsdbBridgeAugmentation = bridgeNodeOptional.get();
                    String bridgeName = ovsdbBridgeAugmentation.getBridgeName().getValue();
                    VlanTrunkSouthboundUtils.addVlanPortToBridge(bridgeIid, ifL2vlan,
                            ovsdbBridgeAugmentation, bridgeName, parentRefs.getParentInterface(), dataBroker, t);
                }
            } */
            // FIXME: Need to add the Group here with actions: Push-Vlan, output_port. May not be needed here...
        }

        futures.add(t.submit());
        return futures;
    }

    private static void createInterfaceParentEntryIfNotPresent(DataBroker dataBroker, WriteTransaction t,
                                                               InterfaceParentEntryKey interfaceParentEntryKey,
                                                               String parentInterface){
        InstanceIdentifier<InterfaceParentEntry> interfaceParentEntryIdentifier =
                InterfaceMetaUtils.getInterfaceParentEntryIdentifier(interfaceParentEntryKey);
        InterfaceParentEntry interfaceParentEntry =
                InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(interfaceParentEntryIdentifier, dataBroker);

        if(interfaceParentEntry != null){
            LOG.info("Not Found entry for Parent Interface: {} in Vlan Trunk-Member Interface Renderer ConfigDS. " +
                    "Creating...", parentInterface);
            InterfaceParentEntryBuilder interfaceParentEntryBuilder = new InterfaceParentEntryBuilder()
                    .setKey(interfaceParentEntryKey).setParentInterface(parentInterface);
            t.put(LogicalDatastoreType.CONFIGURATION, interfaceParentEntryIdentifier,
                    interfaceParentEntryBuilder.build(), true);
        }
    }

    private static long createInterfaceChildEntry(DataBroker dataBroker, IdManagerService idManager, WriteTransaction t,
                                                InterfaceParentEntryKey interfaceParentEntryKey, String childInterface){

        long lportTag = IfmUtil.allocateId(idManager, IfmConstants.IFM_IDPOOL_NAME, childInterface);
        InterfaceChildEntryKey interfaceChildEntryKey = new InterfaceChildEntryKey(childInterface);
        InstanceIdentifier<InterfaceChildEntry> intfId =
                InterfaceMetaUtils.getInterfaceChildEntryIdentifier(interfaceParentEntryKey, interfaceChildEntryKey);
        InterfaceChildEntryBuilder entryBuilder = new InterfaceChildEntryBuilder().setKey(interfaceChildEntryKey)
              .setChildInterface(childInterface);
        t.put(LogicalDatastoreType.CONFIGURATION, intfId, entryBuilder.build(),true);
        return lportTag;
    }
}

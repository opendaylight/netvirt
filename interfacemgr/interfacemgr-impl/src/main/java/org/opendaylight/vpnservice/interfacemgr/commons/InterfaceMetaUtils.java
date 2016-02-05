/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.commons;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.xtend.lib.annotations.Data;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.BridgeInterfaceInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.IfIndexesInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.InterfaceChildInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class InterfaceMetaUtils {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceMetaUtils.class);
    public static InstanceIdentifier<BridgeRefEntry> getBridgeRefEntryIdentifier(BridgeRefEntryKey bridgeRefEntryKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<BridgeRefEntry> bridgeRefEntryInstanceIdentifierBuilder =
                InstanceIdentifier.builder(BridgeRefInfo.class)
                        .child(BridgeRefEntry.class, bridgeRefEntryKey);
        return bridgeRefEntryInstanceIdentifierBuilder.build();
    }

    public static BridgeRefEntry getBridgeRefEntryFromOperDS(InstanceIdentifier<BridgeRefEntry> dpnBridgeEntryIid,
                                                             DataBroker dataBroker) {
        Optional<BridgeRefEntry> bridgeRefEntryOptional =
                IfmUtil.read(LogicalDatastoreType.OPERATIONAL, dpnBridgeEntryIid, dataBroker);
        if (!bridgeRefEntryOptional.isPresent()) {
            return null;
        }
        return bridgeRefEntryOptional.get();
    }

    public static InstanceIdentifier<BridgeEntry> getBridgeEntryIdentifier(BridgeEntryKey bridgeEntryKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<BridgeEntry> bridgeEntryIdBuilder =
                InstanceIdentifier.builder(BridgeInterfaceInfo.class).child(BridgeEntry.class, bridgeEntryKey);
        return bridgeEntryIdBuilder.build();
    }

    public static BridgeEntry getBridgeEntryFromConfigDS(InstanceIdentifier<BridgeEntry> bridgeEntryInstanceIdentifier,
                                                         DataBroker dataBroker) {
        Optional<BridgeEntry> bridgeEntryOptional =
                IfmUtil.read(LogicalDatastoreType.CONFIGURATION, bridgeEntryInstanceIdentifier, dataBroker);
        if (!bridgeEntryOptional.isPresent()) {
            return null;
        }
        return bridgeEntryOptional.get();
    }

    public static InstanceIdentifier<BridgeInterfaceEntry> getBridgeInterfaceEntryIdentifier(BridgeEntryKey bridgeEntryKey,
                                                                                    BridgeInterfaceEntryKey bridgeInterfaceEntryKey) {
        return InstanceIdentifier.builder(BridgeInterfaceInfo.class)
                        .child(BridgeEntry.class, bridgeEntryKey)
                        .child(BridgeInterfaceEntry.class, bridgeInterfaceEntryKey).build();

    }

    public static BridgeInterfaceEntry getBridgeInterfaceEntryFromConfigDS(
            InstanceIdentifier<BridgeInterfaceEntry> bridgeInterfaceEntryInstanceIdentifier, DataBroker dataBroker) {
        Optional<BridgeInterfaceEntry> bridgeInterfaceEntryOptional =
                IfmUtil.read(LogicalDatastoreType.CONFIGURATION, bridgeInterfaceEntryInstanceIdentifier, dataBroker);
        if (!bridgeInterfaceEntryOptional.isPresent()) {
            return null;
        }
        return bridgeInterfaceEntryOptional.get();
    }


    public static void createBridgeInterfaceEntryInConfigDS(BridgeEntryKey bridgeEntryKey,
                                                             BridgeInterfaceEntryKey bridgeInterfaceEntryKey,
                                                             String childInterface,
                                                             WriteTransaction t) {
        InstanceIdentifier<BridgeInterfaceEntry> bridgeInterfaceEntryIid =
                InterfaceMetaUtils.getBridgeInterfaceEntryIdentifier(bridgeEntryKey, bridgeInterfaceEntryKey);
        BridgeInterfaceEntryBuilder entryBuilder = new BridgeInterfaceEntryBuilder().setKey(bridgeInterfaceEntryKey)
                .setInterfaceName(childInterface);
        t.put(LogicalDatastoreType.CONFIGURATION, bridgeInterfaceEntryIid, entryBuilder.build(), true);
    }

    public static InstanceIdentifier<InterfaceParentEntry> getInterfaceParentEntryIdentifier(
            InterfaceParentEntryKey interfaceParentEntryKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<InterfaceParentEntry> intfIdBuilder =
                InstanceIdentifier.builder(InterfaceChildInfo.class)
                        .child(InterfaceParentEntry.class, interfaceParentEntryKey);
        return intfIdBuilder.build();
    }

    public static InstanceIdentifier<InterfaceChildEntry> getInterfaceChildEntryIdentifier(
            InterfaceParentEntryKey interfaceParentEntryKey, InterfaceChildEntryKey interfaceChildEntryKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<InterfaceChildEntry> intfIdBuilder =
                InstanceIdentifier.builder(InterfaceChildInfo.class)
                        .child(InterfaceParentEntry.class, interfaceParentEntryKey)
                        .child(InterfaceChildEntry.class, interfaceChildEntryKey);
        return intfIdBuilder.build();
    }

    public static InterfaceParentEntry getInterfaceParentEntryFromConfigDS(
            InterfaceParentEntryKey interfaceParentEntryKey, DataBroker dataBroker) {
        InstanceIdentifier<InterfaceParentEntry> intfParentIid =
                getInterfaceParentEntryIdentifier(interfaceParentEntryKey);

        return getInterfaceParentEntryFromConfigDS(intfParentIid, dataBroker);
    }

    public static InterfaceParentEntry getInterfaceParentEntryFromConfigDS(
            InstanceIdentifier<InterfaceParentEntry> intfId, DataBroker dataBroker) {
        Optional<InterfaceParentEntry> interfaceParentEntryOptional =
                IfmUtil.read(LogicalDatastoreType.CONFIGURATION, intfId, dataBroker);
        if (!interfaceParentEntryOptional.isPresent()) {
            return null;
        }
        return interfaceParentEntryOptional.get();
    }

    public static InterfaceChildEntry getInterfaceChildEntryFromConfigDS(InterfaceParentEntryKey interfaceParentEntryKey,
                                                                         InterfaceChildEntryKey interfaceChildEntryKey,
                                                                         DataBroker dataBroker) {
        InstanceIdentifier<InterfaceChildEntry> intfChildIid =
                getInterfaceChildEntryIdentifier(interfaceParentEntryKey, interfaceChildEntryKey);

        return getInterfaceChildEntryFromConfigDS(intfChildIid, dataBroker);
    }

    public static InterfaceChildEntry getInterfaceChildEntryFromConfigDS(
            InstanceIdentifier<InterfaceChildEntry> intfChildIid, DataBroker dataBroker) {
        Optional<InterfaceChildEntry> interfaceChildEntryOptional =
                IfmUtil.read(LogicalDatastoreType.CONFIGURATION, intfChildIid, dataBroker);
        if (!interfaceChildEntryOptional.isPresent()) {
            return null;
        }
        return interfaceChildEntryOptional.get();
    }

    public static void createLportTagInterfaceMap(WriteTransaction t, String infName, Integer ifIndex) {
        LOG.debug("creating lport tag to interface map for {}",infName);
        InstanceIdentifier<IfIndexInterface> id = InstanceIdentifier.builder(IfIndexesInterfaceMap.class).child(IfIndexInterface.class, new IfIndexInterfaceKey(ifIndex)).build();
        IfIndexInterface ifIndexInterface = new IfIndexInterfaceBuilder().setIfIndex(ifIndex).setKey(new IfIndexInterfaceKey(ifIndex)).setInterfaceName(infName).build();
        t.put(LogicalDatastoreType.OPERATIONAL, id, ifIndexInterface, true);
    }

    public static void removeLportTagInterfaceMap(WriteTransaction t, IdManagerService idManager, DataBroker broker, String infName, Integer ifIndex) {
        InstanceIdentifier<IfIndexInterface> id = InstanceIdentifier.builder(IfIndexesInterfaceMap.class).child(IfIndexInterface.class, new IfIndexInterfaceKey(ifIndex)).build();
        Optional<IfIndexInterface> ifIndexesInterface = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, id, broker);
        if(ifIndexesInterface.isPresent()) {
           LOG.debug("removing lport tag to interface map for {}",infName);
           t.delete(LogicalDatastoreType.OPERATIONAL, id);
        }
        IfmUtil.releaseId(idManager, IfmConstants.IFM_IDPOOL_NAME, infName);
    }

    public static void createBridgeRefEntry(BigInteger dpnId, InstanceIdentifier<?> bridgeIid,
                                            WriteTransaction tx){
        BridgeRefEntryKey bridgeRefEntryKey = new BridgeRefEntryKey(dpnId);
        InstanceIdentifier<BridgeRefEntry> bridgeEntryId =
                InterfaceMetaUtils.getBridgeRefEntryIdentifier(bridgeRefEntryKey);
        BridgeRefEntryBuilder tunnelDpnBridgeEntryBuilder =
                new BridgeRefEntryBuilder().setKey(bridgeRefEntryKey).setDpid(dpnId)
                        .setBridgeReference(new OvsdbBridgeRef(bridgeIid));
        tx.put(LogicalDatastoreType.OPERATIONAL, bridgeEntryId, tunnelDpnBridgeEntryBuilder.build(), true);
    }
}
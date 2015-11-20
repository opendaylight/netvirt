/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.commons;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.BridgeInterfaceInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.InterfaceChildInfo;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class InterfaceMetaUtils {
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

    public static void createBridgeInterfaceEntryInConfigDS(BridgeEntryKey bridgeEntryKey,
                                                             BridgeInterfaceEntryKey bridgeInterfaceEntryKey,
                                                             String childInterface,
                                                             InstanceIdentifier<TerminationPoint> tpIid,
                                                             WriteTransaction t) {
        if (tpIid == null) {
            createBridgeInterfaceEntryInConfigDS(bridgeEntryKey, bridgeInterfaceEntryKey, childInterface, t);
            return;
        }

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
}
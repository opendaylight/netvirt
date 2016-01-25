/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OvsInterfaceTopologyStateAddHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceTopologyStateAddHelper.class);
    public static List<ListenableFuture<Void>> addPortToBridge(InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid,
                                                               OvsdbBridgeAugmentation bridgeNew, DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();

        if (bridgeNew.getDatapathId() == null) {
            LOG.warn("DataPathId found as null for Bridge Augmentation: {}... retrying...", bridgeNew);
            Optional<OvsdbBridgeAugmentation> bridgeNodeOptional = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, bridgeIid, dataBroker);
            if (bridgeNodeOptional.isPresent()) {
                bridgeNew = bridgeNodeOptional.get();
            }
            if (bridgeNew.getDatapathId() == null) {
                LOG.warn("DataPathId found as null again for Bridge Augmentation: {}. Bailing out.", bridgeNew);
                return futures;
            }
        }
        String bridgeName = bridgeNew.getBridgeName().getValue();
        BigInteger dpnId = IfmUtil.getDpnId(bridgeNew.getDatapathId());

        if (dpnId == null) {
            LOG.warn("Got Null DPID for Bridge: {}", bridgeNew);
            return futures;
        }

        // create bridge reference entry in interface meta operational DS
        InterfaceMetaUtils.createBridgeRefEntry(dpnId, bridgeIid, writeTransaction);

        // FIX for OVSDB Bug - manually copying the bridge info from topology operational DS to config DS
        SouthboundUtils.addBridge(bridgeIid, bridgeNew, dataBroker, futures);

        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpnId);
        InstanceIdentifier<BridgeEntry> bridgeEntryInstanceIdentifier =
                InterfaceMetaUtils.getBridgeEntryIdentifier(bridgeEntryKey);
        BridgeEntry bridgeEntry =
                InterfaceMetaUtils.getBridgeEntryFromConfigDS(bridgeEntryInstanceIdentifier,
                        dataBroker);
        if (bridgeEntry == null) {
            futures.add(writeTransaction.submit());
            return futures;
        }

        List<BridgeInterfaceEntry> bridgeInterfaceEntries = bridgeEntry.getBridgeInterfaceEntry();
        for (BridgeInterfaceEntry bridgeInterfaceEntry : bridgeInterfaceEntries) {
            String portName = bridgeInterfaceEntry.getInterfaceName();
            InterfaceKey interfaceKey = new InterfaceKey(portName);
            Interface iface = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
            if (iface.getAugmentation(IfTunnel.class) != null) {
                SouthboundUtils.addPortToBridge(bridgeIid, iface, bridgeNew, bridgeName, portName, dataBroker, futures);
            }
        }

        futures.add(writeTransaction.submit());
        return futures;
    }
}
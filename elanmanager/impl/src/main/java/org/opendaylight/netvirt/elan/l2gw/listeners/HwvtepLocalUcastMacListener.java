/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.Collections;
import java.util.Locale;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.hwvtep.HwvtepClusteredDataTreeChangeListener;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A listener for Ucast MAC entries that are added/removed to/from an External
 * Device (e.g., TOR).
 *
 * <p>When a Ucast MAC addr appears in the hwvtep's operational DS, that MAC must
 * be populated in DMAC tables in all Elan participating DPNs. ELAN is selected
 * according to field 'tunnel_key' of the Logical Switch to which the new MAC
 * belongs.
 */
public class HwvtepLocalUcastMacListener extends
        HwvtepClusteredDataTreeChangeListener<LocalUcastMacs, HwvtepLocalUcastMacListener> {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepLocalUcastMacListener.class);

    private final DataBroker broker;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanInstanceCache elanInstanceCache;

    public HwvtepLocalUcastMacListener(DataBroker broker, ElanL2GatewayUtils elanL2GatewayUtils,
            ElanInstanceCache elanInstanceCache, HwvtepNodeHACache hwvtepNodeHACache) {
        super(LocalUcastMacs.class, HwvtepLocalUcastMacListener.class, hwvtepNodeHACache);

        this.broker = broker;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanInstanceCache = elanInstanceCache;
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.broker);
    }

    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    protected void removed(InstanceIdentifier<LocalUcastMacs> identifier, LocalUcastMacs macRemoved) {
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macRemoved.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());

        LOG.trace("LocalUcastMacs {} removed from {}", macAddress, hwvtepNodeId);

        String elanName = getElanName(macRemoved);

        L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, hwvtepNodeId);
        if (elanL2GwDevice == null) {
            LOG.warn("Could not find L2GatewayDevice for ELAN: {}, nodeID:{} from cache", elanName, hwvtepNodeId);
            return;
        }

        // Remove MAC from cache
        elanL2GwDevice.removeUcastLocalMac(macRemoved);
        elanL2GatewayUtils.unInstallL2GwUcastMacFromL2gwDevices(elanName, elanL2GwDevice,
                Collections.singletonList(new MacAddress(macAddress.toLowerCase(Locale.getDefault()))));
        elanL2GatewayUtils.unInstallL2GwUcastMacFromElanDpns(elanInstanceCache.get(elanName).orNull(),
                elanL2GwDevice, Collections.singletonList(new MacAddress(macAddress.toLowerCase(Locale.getDefault()))));
    }

    protected String getElanName(LocalUcastMacs mac) {
        return mac.getLogicalSwitchRef().getValue()
                .firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    }

    @Override
    protected void updated(InstanceIdentifier<LocalUcastMacs> identifier, LocalUcastMacs original,
            LocalUcastMacs update) {
        // TODO (eperefr) what can change here?

    }

    @Override
    public void added(InstanceIdentifier<LocalUcastMacs> identifier, LocalUcastMacs macAdded) {
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macAdded.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());

        LOG.trace("LocalUcastMacs {} added to {}", macAddress, hwvtepNodeId);

        String elanName = getElanName(macAdded);
        ElanInstance elan = elanInstanceCache.get(elanName).orNull();
        if (elan == null) {
            LOG.warn("Could not find ELAN for mac {} being added", macAddress);
            return;
        }

        L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, hwvtepNodeId);
        if (elanL2GwDevice == null) {
            LOG.warn("Could not find L2GatewayDevice for ELAN: {}, nodeID:{} from cache", elanName, hwvtepNodeId);
            return;
        }

        // Cache MAC for furthur processing later
        elanL2GwDevice.addUcastLocalMac(macAdded);

        elanL2GatewayUtils.installL2GwUcastMacInElan(elan, elanL2GwDevice, macAddress.toLowerCase(Locale.getDefault()),
                macAdded, null);
    }

    @Override
    protected InstanceIdentifier<LocalUcastMacs> getWildCardPath() {
        return HwvtepUtils.getWildCardPathForLocalUcastMacs();
    }

    @Override
    protected HwvtepLocalUcastMacListener getDataTreeChangeListener() {
        return this;
    }
}

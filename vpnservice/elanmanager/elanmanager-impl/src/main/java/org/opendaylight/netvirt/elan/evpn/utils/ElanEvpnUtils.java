/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.evpn.utils;


import com.google.common.base.Optional;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.math.BigInteger;
import java.util.List;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by eriytal on 2/7/2017.
 */
public class ElanEvpnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ElanEvpnUtils.class);
    private final DataBroker broker;
    private final ElanUtils elanUtils;
    private final IdManagerService idManager;
    private IVpnManager vpnManager;

    public ElanEvpnUtils(DataBroker broker, ElanUtils elanUtils, IdManagerService idManager,
                         final BundleContext bundleContext) {
        this.broker = broker;
        this.elanUtils = elanUtils;
        this.idManager = idManager;

        GlobalEventExecutor.INSTANCE.execute(() -> {
            final WaitingServiceTracker<IVpnManager> tracker = WaitingServiceTracker.create(
                IVpnManager.class, bundleContext);
            vpnManager = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);
            LOG.info("ElanEvpnUtils initialized. vpnManager={}", vpnManager);
        });
    }

    public static String getEndpointIpAddressForDPN(DataBroker broker, BigInteger dpnId) {
        String nextHopIp = null;
        InstanceIdentifier<DPNTEPsInfo> tunnelInfoId =
                InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class,
                        new DPNTEPsInfoKey(dpnId)).build();
        Optional<DPNTEPsInfo> tunnelInfo = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, tunnelInfoId);

        if (tunnelInfo.isPresent()) {
            List<TunnelEndPoints> nexthopIpList = tunnelInfo.get().getTunnelEndPoints();
            if (nexthopIpList != null && !nexthopIpList.isEmpty()) {
                nextHopIp = nexthopIpList.get(0).getIpAddress().getIpv4Address().getValue();
            }
        }
        return nextHopIp;
    }

    public Optional<String> getGatewayMacAddressForInterface(String vpnName, String ifName, String ipAddress) {
        Optional<String> routerGwMac;
        VpnPortipToPort gwPort = vpnManager.getNeutronPortFromVpnPortFixedIp(broker, vpnName, ipAddress);
        routerGwMac = Optional.of((gwPort != null && gwPort.isSubnetIp())
                ? gwPort.getMacAddress() : vpnManager.getMacAddressForInterface(broker, ifName));
        return routerGwMac;
    }

    public Optional<MacTable> getMacTableFromOperationalDS(String elanName) {
        InstanceIdentifier<MacTable> macTableIid = getMacTableIidFromOpertionalDS(elanName);
        Optional<MacTable> existingMacTable = elanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, macTableIid);
        return existingMacTable;
    }

    public static InstanceIdentifier<MacTable> getMacTableIidFromOpertionalDS(String elanName) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class,
                new MacTableKey(elanName)).build();
    }

    public Long getElanTag(InstanceIdentifier<MacVrfEntry> instanceIdentifier) {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        String elanName = null;
        InstanceIdentifier<EvpnRdToNetwork> iidEvpnRdToNet =
                InstanceIdentifier.builder(EvpnRdToNetworks.class).child(EvpnRdToNetwork.class,
                        new EvpnRdToNetworkKey(rd)).build();
        try {
            Optional<EvpnRdToNetwork> evpnRdToNetwork =
                    tx.read(LogicalDatastoreType.OPERATIONAL, iidEvpnRdToNet).checkedGet();
            elanName = evpnRdToNetwork.get().getNetworkId();
        } catch (ReadFailedException e) {
            LOG.error("getElanTag: Error : tx.read throws exception e {} ", e);
        }

        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanName);
        Long elanTag = elanInstance.getElanTag();
        if (elanTag == null || elanTag == 0L) {
            elanTag = elanUtils.retrieveNewElanTag(idManager, elanName);
        }

        return elanTag;
    }

    public ElanInstance getElanInstance(InstanceIdentifier<MacVrfEntry> instanceIdentifier) {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        String elanName = null;
        InstanceIdentifier<EvpnRdToNetwork> iidEvpnRdToNet =
                InstanceIdentifier.builder(EvpnRdToNetworks.class).child(EvpnRdToNetwork.class,
                        new EvpnRdToNetworkKey(rd)).build();
        try {
            Optional<EvpnRdToNetwork> evpnRdToNetwork =
                    tx.read(LogicalDatastoreType.OPERATIONAL, iidEvpnRdToNet).checkedGet();
            elanName = evpnRdToNetwork.get().getNetworkId();
        } catch (ReadFailedException e) {
            LOG.error("getElanInstance: Error : tx.read throws exception e {} ", e);
        }

        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanName);
        return elanInstance;
    }
}

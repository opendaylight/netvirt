/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.evpn.utils;


import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.List;
import java.util.function.BiPredicate;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvpnUtils {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnUtils.class);
    private static final BiPredicate<String, String> ISNETATTACH = (var1, var2) -> ((var1 == null) && (var2 != null));
    private static final BiPredicate<String, String> ISNETDETACH = (var1, var2) -> ((var1 != null) && (var2 == null));
    private final IBgpManager bgpManager;
    private final DataBroker broker;
    private final IInterfaceManager interfaceManager;
    private final IVpnManager vpnManager;
    private final ElanUtils elanUtils;

    public EvpnUtils(DataBroker broker, IBgpManager bgpManager, IInterfaceManager interfaceManager,
                     IVpnManager vpnManager, ElanUtils elanUtils) {
        this.broker = broker;
        this.bgpManager = bgpManager;
        this.interfaceManager = interfaceManager;
        this.vpnManager = vpnManager;
        this.elanUtils = elanUtils;
    }

    public boolean isWithdrawEvpnRT2Routes(EvpnAugmentation original, EvpnAugmentation update) {
        return ISNETDETACH.test(original.getEvpnName(), update.getEvpnName());
    }

    public boolean isAdvertiseEvpnRT2Routes(EvpnAugmentation original, EvpnAugmentation update) {
        return ISNETATTACH.test(original.getEvpnName(), update.getEvpnName())
                || ISNETATTACH.test(original.getL3vpnName(), update.getL3vpnName());
    }

    public void withdrawEvpnRT2Routes(EvpnAugmentation evpnAugmentation, String elanName) {
        List<MacEntry> macEntries = elanUtils.getElanMacEntries(elanName);
        if (macEntries == null || macEntries.isEmpty()) {
            LOG.error("withdrawEvpnRT2Routes : macEntries  is null  evpnAugmentation {} "
                    + "elan name {}", evpnAugmentation, elanName);
            return;
        }
        String rd = vpnManager.getVpnRd(broker, evpnAugmentation.getEvpnName());
        if (rd == null) {
            LOG.error("withdrawEvpnRT2Routes : rd  is null  evpnAugmentation {} "
                    + "elan name {}", evpnAugmentation, elanName);
            return;
        }
        for (MacEntry macEntry : macEntries) {
            String prefix = macEntry.getIpPrefix().toString();
            if (prefix == null) {
                LOG.error("withdrawEvpnRT2Routes : prefix  is null  evpnAugmentation {} "
                        + "elan name {}", evpnAugmentation, elanName);
                return;
            }
            LOG.info("Withdrawing routes with rd {}, prefix {}", rd, prefix);
            bgpManager.withdrawPrefix(rd, prefix);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void advertiseEvpnRT2Routes(EvpnAugmentation evpnAugmentation, String elanName)  {
        List<MacEntry> macEntries = elanUtils.getElanMacEntries(elanName);
        if (macEntries == null || macEntries.isEmpty()) {
            LOG.error("advertiseEVPNRT2Route : macEntries  is null  evpnAugmentation {} "
                    + "elan name {}", evpnAugmentation, elanName);
            return;
        }
        String evpnName = evpnAugmentation.getEvpnName();
        if (evpnName == null) {
            LOG.error("advertiseEVPNRT2Route : evpnName  is null  evpnAugmentation {} "
                    + "elan name {}", evpnAugmentation, elanName);
            return;
        }
        String rd = vpnManager.getVpnRd(broker, evpnName);
        if (rd == null) {
            LOG.error("advertiseEVPNRT2Route : rd  is null  evpnAugmentation {} "
                    + "elan name {}", evpnAugmentation, elanName);
            return;
        }

        ElanInstance elanInstance = elanUtils.getElanInstanceByName(broker, elanName);
        for (MacEntry macEntry : macEntries) {
            String macAddress = macEntry.getMacAddress().toString();
            String prefix = macEntry.getIpPrefix().toString();
            String interfaceName = macEntry.getInterface();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            String nextHop = getEndpointIpAddressForDPN(broker, interfaceInfo.getDpId());
            int vpnLabel = 0;
            long l2vni = elanInstance.getSegmentationId();
            long l3vni = 0;
            String gatewayMacAddr = null;
            if (evpnAugmentation.getL3vpnName() != null) {
                VpnInstance vpnInstance = vpnManager.getVpnInstance(broker, evpnName);
                l3vni = vpnInstance.getL3vni();
                Optional<String> gatewayMac = getGatewayMacAddressForInterface(vpnInstance.getVpnInstanceName(),
                                interfaceName, prefix);
                gatewayMacAddr = gatewayMac.get();
                LOG.info("advertiseEVPNRT2Route : l3vni  is {},  gatewayMacAddr", l3vni, gatewayMacAddr);
            }
            LOG.info("Advertising routes with rd {},  macAddress {}, prefix {}, nextHop {},"
                            + " vpnLabel {}, l3vni {}, l2vni {}, gatewayMac {}", rd, macAddress, prefix, nextHop,
                    vpnLabel, l3vni, l2vni, gatewayMacAddr);

            try {
                bgpManager.advertisePrefix(rd, macAddress, prefix, nextHop,
                        VrfEntryBase.EncapType.Vxlan, vpnLabel, l3vni, l2vni, gatewayMacAddr);
            } catch (Exception e) {
                LOG.error("advertisePrefix with rd {},  macAddress {}, prefix {}, nextHop {},"
                                + "vpnLabel {}, l3vni {}, l2vni {}, gatewayMac {} throws exception {} ", rd,
                        macAddress, prefix, nextHop, vpnLabel, l3vni, l2vni, gatewayMacAddr, e);
            }
        }
    }

    public Optional<String> getGatewayMacAddressForInterface(String vpnName, String ifName, String ipAddress) {
        VpnPortipToPort gwPort = vpnManager.getNeutronPortFromVpnPortFixedIp(broker, vpnName, ipAddress);
        return Optional.of((gwPort != null && gwPort.isSubnetIp())
                ? gwPort.getMacAddress()
                : interfaceManager.getInterfaceInfoFromOperationalDataStore(ifName).getMacAddress());
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
}

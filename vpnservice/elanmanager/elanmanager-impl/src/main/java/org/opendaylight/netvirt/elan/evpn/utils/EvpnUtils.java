/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.evpn.utils;


import com.google.common.base.Optional;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvpnUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ElanEvpnUtils.class);
    private final IBgpManager bgpManager;
    private final DataBroker broker;
    private final ElanEvpnUtils elanEvpnUtils;
    private final IInterfaceManager interfaceManager;
    private final IVpnManager vpnManager;
    private final ElanUtils elanUtils;

    public EvpnUtils(DataBroker broker, IBgpManager bgpManager, ElanEvpnUtils elanEvpnUtils,
                     IInterfaceManager interfaceManager, IVpnManager vpnManager, ElanUtils elanUtils) {
        this.broker = broker;
        this.bgpManager = bgpManager;
        this.elanEvpnUtils = elanEvpnUtils;
        this.interfaceManager = interfaceManager;
        this.vpnManager = vpnManager;
        this.elanUtils = elanUtils;
    }


    public boolean isNetAttachedToEvpn(String originalEvpnName, String updateEvpnName) {
        if ((originalEvpnName == null) && (updateEvpnName != null)) {
            return true;
        }
        return false;
    }

    public boolean isNetAttachedToL3vpn(String originalL3vpnName, String updateL3vpnName) {
        if ((originalL3vpnName == null) && (updateL3vpnName != null)) {
            return true;
        }
        return false;
    }

    public boolean isNetworkDetachedFromEvpn(String originalEvpnName, String updateEvpnName) {
        if ((originalEvpnName != null) && (updateEvpnName == null)) {
            return true;
        }
        return false;
    }

    public boolean isNetDetachedFromL3vpn(String originalL3vpnName, String updatedL3vpnName) {
        if ((originalL3vpnName != null) && (updatedL3vpnName == null)) {
            return true;
        }
        return false;
    }

    public boolean isWithdrawEvpnRT2Routes(EvpnAugmentation original, EvpnAugmentation update) {
        if (isNetworkDetachedFromEvpn(original.getEvpnName(), update.getEvpnName())) {
            LOG.info("Network {} is detached from EVPN, L3VPN is already present "
                    + "Withdrawing RT2 L3vni Routes ", original);
            return true;
        } else if (isNetDetachedFromL3vpn(original.getL3vpnName(), update.getL3vpnName())) {
            LOG.info("Network {} is detached from L3VPN, L3VPN is already present "
                    + "Withdrawing RT2 L3vni Routes ", original);
            return true;
        }

        return false;
    }

    public boolean isAdvertiseEvpnRT2Routes(EvpnAugmentation original, EvpnAugmentation update) {
        if (isNetAttachedToEvpn(original.getL3vpnName(), update.getL3vpnName())) {
            LOG.info("Network {} is attached to EVPN, L3VPN is already present "
                    + "advertise RT2 L3vni routes ", update);
            return true;
        } else if (isNetAttachedToL3vpn(original.getL3vpnName(), update.getL3vpnName())) {
            LOG.info("Network {} is attached to L3VPN, EVPN is already present "
                    + "advertise RT2 L3vni routes ", update);
            return true;
        }

        return false;
    }

    public List<MacEntry> getElanMacEntries(String elanName) {
        MacTable macTable = elanUtils.getElanMacTable(elanName);
        if (macTable == null) {
            LOG.error("getElanMacEntries : macTable  is not present ");
            return null;
        }

        return macTable.getMacEntry();
    }

    public void withdrawEvpnRT2Routes(EvpnAugmentation evpnAugmentation, String elanName) {
        List<MacEntry> macEntries = getElanMacEntries(elanName);
        if (macEntries == null || macEntries.isEmpty()) {
            LOG.error("withdrawEvpnRT2Routes : macEntries  is null ");
            return;
        }

        String rd = vpnManager.getVpnRd(broker, evpnAugmentation.getEvpnName());
        if (rd == null) {
            LOG.error("withdrawEvpnRT2Routes : rd  is null ");
            return;
        }

        for (MacEntry macEntry : macEntries) {
            if (macEntry != null) {
                String prefix = macEntry.getIpPrefix().toString();
                LOG.info("Withdrawing routes with rd {}, prefix {}", rd, prefix);
                bgpManager.withdrawPrefix(rd, prefix);
            }
        }

        return;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void advertiseEvpnRT2Routes(EvpnAugmentation evpnAugmentation, String elanName)  {
        List<MacEntry> macEntries = getElanMacEntries(elanName);
        if (macEntries == null || macEntries.isEmpty()) {
            LOG.error("advertiseEVPNRT2Route : macEntries is null ");
            return;
        }

        String rd = null;
        String evpnName = evpnAugmentation.getEvpnName();
        if (evpnName != null) {
            rd = vpnManager.getVpnRd(broker, evpnName);
            if (rd == null) {
                LOG.error("advertiseEVPNRT2Route : rd is null ");
                return;
            }
        }

        ElanInstance elanInstance = elanUtils.getElanInstanceByName(broker, elanName);
        for (MacEntry macEntry : macEntries) {
            String macAddress = macEntry.getMacAddress().toString();
            String prefix = macEntry.getIpPrefix().toString();
            String interfaceName = macEntry.getInterface();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            String nextHop = elanEvpnUtils.getEndpointIpAddressForDPN(broker, interfaceInfo.getDpId());
            int vpnLabel = 0;
            long l2vni = elanInstance.getSegmentationId();
            long l3vni = 0;
            String gatewayMacAddr = null;
            if (evpnAugmentation.getL3vpnName() != null) {
                VpnInstance vpnInstance = vpnManager.getVpnInstance(broker, evpnName);
                l3vni = vpnInstance.getL3vni();
                Optional<String> gatewayMac =
                        elanEvpnUtils.getGatewayMacAddressForInterface(vpnInstance.getVpnInstanceName(),
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

        return;
    }
}

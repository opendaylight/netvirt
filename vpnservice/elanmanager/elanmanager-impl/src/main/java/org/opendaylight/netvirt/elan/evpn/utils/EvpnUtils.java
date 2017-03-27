/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by eriytal on 3/20/2017.
 */
public class EvpnUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ElanEvpnUtils.class);
    private final IBgpManager bgpManager;
    private final DataBroker broker;
    private final ElanEvpnUtils elanEvpnUtils;
    private final IInterfaceManager interfaceManager;
    private final IVpnManager vpnManager;

    public EvpnUtils(DataBroker broker, IBgpManager bgpManager, ElanEvpnUtils elanEvpnUtils,
                     IInterfaceManager interfaceManager, IVpnManager vpnManager) {
        this.broker = broker;
        this.bgpManager = bgpManager;
        this.elanEvpnUtils = elanEvpnUtils;
        this.interfaceManager = interfaceManager;
        this.vpnManager = vpnManager;
    }

    public boolean isEvpnPresent(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalEvpnName = getEvpnNameFromElan(elanOriginal);
        String updatedEvpnName = getEvpnNameFromElan(elanUpdated);
        if ((originalEvpnName != null) && (updatedEvpnName != null)) {
            return true;
        }
        return false;
    }

    public boolean isNetAttachedToEvpn(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalEvpnName = getEvpnNameFromElan(elanOriginal);
        String updatedEvpnName = getEvpnNameFromElan(elanUpdated);
        if ((originalEvpnName == null) && (updatedEvpnName != null)) {
            return true;
        }
        return false;
    }

    public boolean isNetAttachedToL3vpn(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalL3vpnName = getL3vpnNameFromElan(elanOriginal);
        String updatedL3vpnName = getL3vpnNameFromElan(elanUpdated);
        if ((originalL3vpnName == null) && (updatedL3vpnName != null)) {
            return true;
        }

        return false;
    }

    public boolean isNetworkDetachedFromEvpn(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalEvpnName = getEvpnNameFromElan(elanOriginal);
        String updatedEvpnName = getEvpnNameFromElan(elanUpdated);
        if ((originalEvpnName != null) && (updatedEvpnName == null)) {
            return true;
        }

        return false;
    }

    private String getEvpnNameFromElan(ElanInstance elanInstance) {
        EvpnAugmentation evpnAugmentation = elanInstance.getAugmentation(EvpnAugmentation.class);
        if (evpnAugmentation != null) {
            return evpnAugmentation.getEvpnName();
        }
        return null;
    }

    private String getL3vpnNameFromElan(ElanInstance elanInstance) {
        EvpnAugmentation evpnAugmentation = elanInstance.getAugmentation(EvpnAugmentation.class);
        if (evpnAugmentation != null) {
            return evpnAugmentation.getL3vpnName();
        }
        return null;
    }

    public boolean isNetDettachedFromL3vpn(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalL3vpnName = getL3vpnNameFromElan(elanOriginal);
        String updatedL3vpnName = getL3vpnNameFromElan(elanUpdated);
        if ((originalL3vpnName != null) && (updatedL3vpnName == null)) {
            return true;
        }

        return false;
    }

    public void withdrawEvpnRT2Routes(ElanInstance elanInstance) {
        Optional<MacTable> existingMacTable =
                elanEvpnUtils.getMacTableFromOperationalDS(elanInstance.getElanInstanceName());
        if (!existingMacTable.isPresent()) {
            LOG.error("withdrawEVPNRT2Routes : existingMacTable  is not present ");
            return;
        }

        List<MacEntry> macEntries = existingMacTable.get().getMacEntry();
        for (MacEntry macEntry : macEntries) {
            String evpnInstanceName = getEvpnNameFromElan(elanInstance);
            if (evpnInstanceName != null) {
                String rd = vpnManager.getVpnRd(broker, evpnInstanceName);
                String prefix = macEntry.getIpPrefix().toString();
                LOG.info("Withdrawing routes with rd {}, prefix {}", rd, prefix);
                bgpManager.withdrawPrefix(rd, prefix);
            }

        }

        return;
    }

    public void advertiseEvpnRT2Route(ElanInstance elanInstance) throws Exception {
        Optional<MacTable> existingMacTable =
                elanEvpnUtils.getMacTableFromOperationalDS(elanInstance.getElanInstanceName());
        if (!existingMacTable.isPresent()) {
            LOG.error("advertiseEVPNRT2Route : existingMacTable  is not present ");
            return;
        }

        List<MacEntry> macEntries = existingMacTable.get().getMacEntry();
        for (MacEntry macEntry : macEntries) {
            String macAddress = macEntry.getMacAddress().toString();
            String prefix = macEntry.getIpPrefix().toString();
            String evpnName = getEvpnNameFromElan(elanInstance);
            VpnInstance vpnInstance = vpnManager.getVpnInstance(broker, evpnName);
            String evpnInstanceName = getEvpnNameFromElan(elanInstance);
            String rd = null;
            if (evpnInstanceName != null) {
                rd = vpnManager.getVpnRd(broker, evpnInstanceName);
            }
            String interfaceName = macEntry.getInterface();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            String nextHop = elanEvpnUtils.getEndpointIpAddressForDPN(broker, interfaceInfo.getDpId());
            int vpnLabel = 0;
            long l3vni = 0;
            long l2vni = elanInstance.getSegmentationId();
            String gatewayMacAddr = null;

            if (getL3vpnNameFromElan(elanInstance) != null) {
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

            bgpManager.advertisePrefix(rd, macAddress, prefix, nextHop,
                    VrfEntryBase.EncapType.Vxlan, vpnLabel, l3vni, l2vni, gatewayMacAddr);

        }

        return;
    }
}

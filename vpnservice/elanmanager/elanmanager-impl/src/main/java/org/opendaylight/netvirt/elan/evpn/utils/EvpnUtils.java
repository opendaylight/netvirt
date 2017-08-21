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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetDpnEndpointIpsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetDpnEndpointIpsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvpnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(EvpnUtils.class);

    private final BiPredicate<String, String> isNetAttach = (var1, var2) -> ((var1 == null) && (var2 != null));
    private final BiPredicate<String, String> isNetDetach = (var1, var2) -> ((var1 != null) && (var2 == null));
    private final Predicate<MacEntry> isIpv4PrefixAvailable = (macEntry) -> (macEntry != null
        && macEntry.getIpPrefix() != null && macEntry.getIpPrefix().getIpv4Address() != null);
    private final DataBroker broker;
    private final IInterfaceManager interfaceManager;
    private final ElanUtils elanUtils;
    private final ItmRpcService itmRpcService;

    private volatile IBgpManager bgpManager;
    private volatile IVpnManager vpnManager;

    public EvpnUtils(DataBroker broker, IInterfaceManager interfaceManager,
                     ElanUtils elanUtils, ItmRpcService itmRpcService) {
        this.broker = broker;
        this.interfaceManager = interfaceManager;
        this.elanUtils = elanUtils;
        this.itmRpcService = itmRpcService;
    }

    public void init() {
    }

    public void close() {
    }

    public void setVpnManager(IVpnManager vpnManager) {
        this.vpnManager = vpnManager;
    }

    public void setBgpManager(IBgpManager bgpManager) {
        this.bgpManager = bgpManager;
    }

    public boolean isWithdrawEvpnRT2Routes(ElanInstance original, ElanInstance update) {
        return isNetDetach.test(getEvpnNameFromElan(original), getEvpnNameFromElan(update));
    }

    public boolean isAdvertiseEvpnRT2Routes(ElanInstance original, ElanInstance update) {
        return isNetAttach.test(getEvpnNameFromElan(original), getEvpnNameFromElan(update));
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void advertiseEvpnRT2Routes(EvpnAugmentation evpnAugmentation, String elanName)  {
        if (evpnAugmentation == null || evpnAugmentation.getEvpnName() == null) {
            return;
        }
        String evpnName = evpnAugmentation.getEvpnName();
        List<MacEntry> macEntries = elanUtils.getElanMacEntries(elanName);
        if (macEntries == null || macEntries.isEmpty()) {
            LOG.trace("advertiseEvpnRT2Routes no elan mac entries found for {}", elanName);
            return;
        }
        String rd = vpnManager.getVpnRd(broker, evpnName);
        ElanInstance elanInfo = elanUtils.getElanInstanceByName(broker, elanName);
        macEntries.stream().filter(isIpv4PrefixAvailable).forEach(macEntry -> {
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(macEntry.getInterface());
            if (interfaceInfo == null) {
                LOG.debug("advertiseEvpnRT2Routes, interfaceInfo is null for interface {}", macEntry.getInterface());
                return;
            }
            advertisePrefix(elanInfo, rd, macEntry.getMacAddress().getValue(),
                    macEntry.getIpPrefix().getIpv4Address().getValue(),
                    interfaceInfo.getInterfaceName(), interfaceInfo.getDpId());
        });
    }

    public String getEndpointIpAddressForDPN(BigInteger dpnId) {

        Future<RpcResult<GetDpnEndpointIpsOutput>> result = itmRpcService.getDpnEndpointIps(
                new GetDpnEndpointIpsInputBuilder()
                        .setSourceDpid(dpnId)
                        .build());
        RpcResult<GetDpnEndpointIpsOutput> rpcResult = null;
        try {
            rpcResult = result.get();
        } catch (InterruptedException e) {
            LOG.error("getnextHopIpFromRpcOutput : InterruptedException for dpnid {}", e, dpnId);
            return null;
        } catch (ExecutionException e) {
            LOG.error("getnextHopIpFromRpcOutput : ExecutionException for dpnid {}", e, dpnId);
            return null;
        }
        if (!rpcResult.isSuccessful()) {
            LOG.warn("RPC Call to getDpnEndpointIps returned with Errors {}", rpcResult.getErrors());
            return null;
        }

        List<IpAddress> nexthopIpList = rpcResult.getResult().getNexthopipList();
        return nexthopIpList.get(0).getIpv4Address().getValue();
    }

    public Optional<String> getGatewayMacAddressForInterface(String vpnName,
                                                                                    String ifName, String ipAddress) {
        VpnPortipToPort gwPort = vpnManager.getNeutronPortFromVpnPortFixedIp(broker, vpnName, ipAddress);
        return Optional.of((gwPort != null && gwPort.isSubnetIp())
                ? gwPort.getMacAddress()
                : interfaceManager.getInterfaceInfoFromOperationalDataStore(ifName).getMacAddress());
    }

    public String getL3vpnNameFromElan(ElanInstance elanInfo) {
        if (elanInfo == null) {
            LOG.debug("getL3vpnNameFromElan :elanInfo is NULL");
            return null;
        }
        EvpnAugmentation evpnAugmentation = elanInfo.getAugmentation(EvpnAugmentation.class);
        return evpnAugmentation != null ? evpnAugmentation.getL3vpnName() : null;
    }

    public static String getEvpnNameFromElan(ElanInstance elanInfo) {
        if (elanInfo == null) {
            LOG.debug("getEvpnNameFromElan :elanInfo is NULL");
            return null;
        }
        EvpnAugmentation evpnAugmentation = elanInfo.getAugmentation(EvpnAugmentation.class);
        return evpnAugmentation != null ? evpnAugmentation.getEvpnName() : null;
    }

    public String getEvpnRd(ElanInstance elanInfo) {
        String evpnName = getEvpnNameFromElan(elanInfo);
        if (evpnName == null) {
            LOG.debug("getEvpnRd : evpnName is NULL for elanInfo {}", elanInfo);
            return null;
        }
        return vpnManager.getVpnRd(broker, evpnName);
    }

    public void advertisePrefix(ElanInstance elanInfo, String macAddress, String prefix,
                                 String interfaceName, BigInteger dpnId) {
        String rd = getEvpnRd(elanInfo);
        advertisePrefix(elanInfo, rd, macAddress, prefix, interfaceName, dpnId);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void advertisePrefix(ElanInstance elanInfo, String rd,
                                 String macAddress, String prefix, String interfaceName, BigInteger dpnId) {
        if (rd == null) {
            LOG.debug("advertisePrefix : rd is NULL for elanInfo {}, macAddress {}", elanInfo, macAddress);
            return;
        }
        String nextHop = getEndpointIpAddressForDPN(dpnId);
        if (nextHop == null) {
            LOG.debug("Failed to get the dpn tep ip for dpn {}", dpnId);
            return;
        }
        int vpnLabel = 0;
        long l2vni = elanInfo.getSegmentationId();
        long l3vni = 0;
        String gatewayMacAddr = null;
        String l3VpName = getL3vpnNameFromElan(elanInfo);
        if (l3VpName != null) {
            VpnInstance l3VpnInstance = vpnManager.getVpnInstance(broker, l3VpName);
            l3vni = l3VpnInstance.getL3vni();
            com.google.common.base.Optional<String> gatewayMac = getGatewayMacAddressForInterface(l3VpName,
                    interfaceName, prefix);
            gatewayMacAddr = gatewayMac.isPresent() ? gatewayMac.get() : null;

        }
        LOG.info("Advertising routes with rd {},  macAddress {}, prefix {}, nextHop {},"
                        + " vpnLabel {}, l3vni {}, l2vni {}, gatewayMac {}", rd, macAddress, prefix, nextHop,
                vpnLabel, l3vni, l2vni, gatewayMacAddr);
        try {
            bgpManager.advertisePrefix(rd, macAddress, prefix, nextHop,
                    VrfEntryBase.EncapType.Vxlan, vpnLabel, l3vni, l2vni, gatewayMacAddr);
        } catch (Exception e) {
            LOG.error("Failed to advertisePrefix", e);
        }
    }

    public void advertisePrefix(ElanInstance elanInfo, MacEntry macEntry) {
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(macEntry.getInterface());
        if (interfaceInfo == null) {
            LOG.debug("advertisePrefix, interfaceInfo is null for interface {}", macEntry.getInterface());
            return;
        }

        if (!isIpv4PrefixAvailable.test(macEntry)) {
            LOG.debug("advertisePrefix macEntry does not have IPv4 prefix {}", macEntry);
            return;
        }
        advertisePrefix(elanInfo, macEntry.getMacAddress().getValue(),
                macEntry.getIpPrefix().getIpv4Address().getValue(),
                interfaceInfo.getInterfaceName(), interfaceInfo.getDpId());
    }

    public void withdrawEvpnRT2Routes(EvpnAugmentation evpnAugmentation, String elanName) {
        if (evpnAugmentation == null || evpnAugmentation.getEvpnName() == null) {
            LOG.trace("withdrawEvpnRT2Routes, evpnAugmentation is null");
            return;
        }

        String evpnName = evpnAugmentation.getEvpnName();
        String rd = vpnManager.getVpnRd(broker, evpnName);
        if (rd == null) {
            LOG.debug("withdrawEvpnRT2Routes : rd is null ", elanName);
            return;
        }
        List<MacEntry> macEntries = elanUtils.getElanMacEntries(elanName);
        if (macEntries == null || macEntries.isEmpty()) {
            LOG.debug("withdrawEvpnRT2Routes : macEntries  is empty for elan {} ", elanName);
            return;
        }
        for (MacEntry macEntry : macEntries) {
            if (!isIpv4PrefixAvailable.test(macEntry)) {
                LOG.debug("withdrawEvpnRT2Routes macEntry does not have IPv4 prefix {}", macEntry);
                continue;
            }
            String prefix = macEntry.getIpPrefix().getIpv4Address().getValue();
            LOG.info("Withdrawing routes with rd {}, prefix {}", rd, prefix);
            bgpManager.withdrawPrefix(rd, prefix);
        }
    }

    public void withdrawPrefix(ElanInstance elanInfo, String prefix) {
        String rd = getEvpnRd(elanInfo);
        if (rd == null) {
            return;
        }
        bgpManager.withdrawPrefix(rd, prefix);
    }

    public void withdrawPrefix(ElanInstance elanInfo, MacEntry macEntry) {
        if (!isIpv4PrefixAvailable.test(macEntry)) {
            LOG.debug("withdrawPrefix macEntry does not have IPv4 prefix {}", macEntry);
            return;
        }
        withdrawPrefix(elanInfo, macEntry.getIpPrefix().getIpv4Address().getValue());
    }

}

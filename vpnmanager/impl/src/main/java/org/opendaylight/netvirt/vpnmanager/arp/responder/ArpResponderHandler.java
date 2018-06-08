/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.arp.responder;

import com.google.common.base.Optional;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput.ArpReponderInputBuilder;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that is responsible for handling ARP Responder flows which involves to
 * differentiate between router and connected mac cases, identify DPNs and
 * installation and uninstallation of flows.
 *
 */
@Singleton
public class ArpResponderHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ArpResponderHandler.class);
    /**
     * MDSAL DataBroker reference.
     */
    private final DataBroker dataBroker;
    /**
     * Elan RPC service reference.
     */
    private final IElanService elanService;

    /**
     * RPC to access InterfaceManager APIs.
     */
    private final IInterfaceManager interfaceManager;

    /**
     * RPC to access ITM APIs.
     */
    private final ItmRpcService itmRpcService;

    /**
     * Vpn Utility.
     */
    private final VpnUtil vpnUtil;

    /**
     * Constructor.
     *
     * @param dataBroker
     *            {@link #dataBroker}
     * @param elanService
     *            {@link #elanService}
     * @param interfaceManager
     *            {@link #interfaceManager}
     * @param itmRpcService
     *            {@link #itmRpcService}
     * @param vpnUtil
     *            {@link #vpnUtil}
     */
    @Inject
    public ArpResponderHandler(DataBroker dataBroker, IElanService elanService, IInterfaceManager interfaceManager,
                               ItmRpcService itmRpcService, VpnUtil vpnUtil) {
        this.dataBroker = dataBroker;
        this.elanService = elanService;
        this.interfaceManager = interfaceManager;
        this.itmRpcService = itmRpcService;
        this.vpnUtil = vpnUtil;
    }

    /**
     * Add ARP Responder flow, by invoking ELan RPC service.
     *
     * @param dpnId
     *            dpn Id on which ARP responder flow to be added
     * @param lportTag
     *            lport tag of the interface
     * @param interfaceName
     *            interface to which ARP responder flow to be added
     * @param gatewayIp
     *            gateway ip of the interface
     * @param mac
     *            mac address
     */

    public void addArpResponderFlow(BigInteger dpnId, int lportTag, String interfaceName,
            String gatewayIp, String mac) {

        LOG.trace("Creating the ARP Responder flow for VPN Interface {}", interfaceName);
        ArpReponderInputBuilder builder = new ArpReponderInputBuilder();
        builder.setDpId(dpnId).setInterfaceName(interfaceName).setSpa(gatewayIp).setSha(mac).setLportTag(lportTag);
        builder.setInstructions(ArpResponderUtil
                .getInterfaceInstructions(interfaceManager, interfaceName, gatewayIp, mac, itmRpcService));
        elanService.addArpResponderFlow(builder.buildForInstallFlow());
    }

    /**
     * Remove ARP Responder flow when VM interface is removed, by invoking ELan
     * RPC service.
     *
     * @param dpId
     *            dpn Id on which ARP responder flow to be removed
     * @param lportTag
     *            lport tag of the interface
     * @param ifName
     *            interface to which ARP responder flow to be removed
     * @param gatewayIp
     *            subnet gateway ip
     * @param subnetUuid
     *            subnet Id of the interface
     */
    public void removeArpResponderFlow(BigInteger dpId, int lportTag, String ifName, String gatewayIp,
            Uuid subnetUuid) {
        if (gatewayIp == null) {
            Optional<String> gwIpOptional = vpnUtil.getVpnSubnetGatewayIp(subnetUuid);
            if (gwIpOptional.isPresent()) {
                gatewayIp = gwIpOptional.get();
            }
        }
        if (gatewayIp != null) {
            ArpReponderInputBuilder builder = new ArpReponderInputBuilder();
            builder.setDpId(dpId).setInterfaceName(ifName).setSpa(gatewayIp).setLportTag(lportTag);
            elanService.removeArpResponderFlow(builder.buildForRemoveFlow());
        }
    }

    /**
     * Get Mac address from given gateway port and interface name.
     *
     * @param gwPort
     *            gateway port
     * @param ifName
     *            interface for which gateway to be retrieved
     * @return mac address if present else optional absent value
     */
    public Optional<String> getGatewayMacAddressForInterface(VpnPortipToPort gwPort, String ifName) {
        // Check if a router gateway interface is available for the subnet gw is
        // so then use Router interface
        // else use connected interface
        return Optional.of(gwPort != null && gwPort.isSubnetIp() ? gwPort.getMacAddress()
                : InterfaceUtils.getMacAddressForInterface(dataBroker, ifName).get());
    }

}

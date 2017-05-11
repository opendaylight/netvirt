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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that is responsible for handling ARP Responder flows which involves to
 * differentiate between router and connected mac cases, identify DPNs and
 * installation and uninstallation of flows.
 *
 */
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
     * Constructor.
     *
     * @param dataBroker
     *            {@link #dataBroker}
     * @param elanService
     *            {@link #elanService}
     */
    public ArpResponderHandler(DataBroker dataBroker, IElanService elanService) {
        super();
        this.dataBroker = dataBroker;
        this.elanService = elanService;
    }

    /**
     * Enum to represent MAC types.
     *
     * @see #CONNECTED_MAC
     * @see #ROUTER_MAC
     */
    public enum MacType {
        /**
         * The mac address is from router interface.
         */
        ROUTER_MAC,
        /**
         * The mac address is from connected interface.
         */
        CONNECTED_MAC
    }

    /**
     * Check any other vpn interface is present on the given dpn belonging to
     * provided subnet.
     *
     * @param dpnId
     *            dpn on which vpn interface to be checked
     * @param ifaceName
     *            vpn interface
     * @param subnetId
     *            Id on the subnet to which interface belongs
     * @param vpnId
     *            VPN Id of the interface.
     * @return true if other vpn interfaces are present else false
     */
    private boolean anyOtherVpnIfaceOnDpnOfSubnet(final BigInteger dpnId, String ifaceName, final Uuid subnetId,
            final long vpnId) {
        final InstanceIdentifier<VpnIds> vpnIdfr = VpnUtil.getPrefixToInterfaceIdentifier(vpnId);
        final Optional<VpnIds> vpnIds = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, vpnIdfr);
        if (vpnIds.isPresent() && vpnIds.get().getPrefixes() != null) {
            LOG.trace("Number of vpn prefixes {} on DPN {}", vpnIds.get().getPrefixes().size(), dpnId);
            return vpnIds.get().getPrefixes().stream()
                    .filter(intf -> !intf.getVpnInterfaceName().contentEquals(ifaceName)
                            && intf.getDpnId().equals(dpnId)
                            && intf.getSubnetId().getValue().equals(subnetId.getValue()))
                    .findFirst().isPresent();
        } else {
            return false;
        }
    }

    /**
     * Add ARP Responder flow, by invoking ELan RPC service.
     *
     * @param dpnId
     *            dpn Id on which ARP responder flow to be added
     * @param lportTag
     *            lport tag of the interface
     * @param vpnName
     *            vpnname of the interface
     * @param vpnId
     *            vpn id that interface belongs to
     * @param interfaceName
     *            interface to which ARP responder flow to be added
     * @param subnetId
     *            subnet Id of the interface
     * @param gatewayIp
     *            gateway ip of the interface
     * @param mac
     *            mac address
     * @param macType
     *            Mac Address type {@link MacType}
     */
//  Retaining this method for future use where flow is add/removed per subnet for router case
//    public void addArpResponderFlow(final BigInteger dpnId, final int lportTag, final String vpnName,
//            final long vpnId, final String interfaceName, final Uuid subnetId, final String gatewayIp,
//            final String mac,final MacType macType) {
//
//        if (MacType.ROUTER_MAC == macType) {
//            if (!anyOtherVpnIfaceOnDpnOfSubnet(dpnId, interfaceName, subnetId, vpnId)) {
//                LOG.trace("Creating the ARP Responder flow for VPN Interface {}", interfaceName);
//                elanService.addArpResponderFlow(dpnId, interfaceName, gatewayIp, mac, java.util.Optional.empty(),
//                        false);
//            }
//        } else {
//            LOG.trace("Creating the ARP Responder flow for VPN Interface {}", interfaceName);
//            elanService.addArpResponderFlow(dpnId, interfaceName, gatewayIp, mac, java.util.Optional.of(lportTag),
//                    false);
//        }
//    }
    public void addArpResponderFlow(final BigInteger dpnId, final int lportTag, final String vpnName, final long vpnId,
            final String interfaceName, final Uuid subnetId, final String gatewayIp, final String mac,
            final MacType macType) {

        LOG.trace("Creating the ARP Responder flow for VPN Interface {}", interfaceName);
        elanService.addArpResponderFlow(dpnId, interfaceName, gatewayIp, mac, java.util.Optional.of(lportTag), false);
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
     * @param vpnName
     *            vpnname of the interface
     * @param vpnId
     *            vpn id that interface belongs to
     *
     * @param subnetUuid
     *            subnet Id of the interface
     */
//    Retaining this method for future use where flow is add/removed per subnet for router case
//    public void removeArpResponderFlow(final BigInteger dpId, final int lportTag, final String ifName,
//            final String vpnName, final long vpnId, final Uuid subnetUuid) {
//        final Optional<String> gwIp = VpnUtil.getVpnSubnetGatewayIp(dataBroker, subnetUuid);
//        if (gwIp.isPresent()) {
//
//            if (!anyOtherVpnIfaceOnDpnOfSubnet(dpId, ifName, subnetUuid, vpnId)) {
//                LOG.trace("VPN Interface {} is the last interface on the DPN, removing the ARP Responder flow",
//                        ifName, dpId);
//                elanService.removeArpResponderFlow(dpId, ifName, gwIp.get(), java.util.Optional.empty());
//            }
//            elanService.removeArpResponderFlow(dpId, ifName, gwIp.get(), java.util.Optional.of(lportTag));
//        }
//    }

    public void removeArpResponderFlow(final BigInteger dpId, final int lportTag, final String ifName,
            final String vpnName, final long vpnId, final Uuid subnetUuid) {
        final Optional<String> gwIp = VpnUtil.getVpnSubnetGatewayIp(dataBroker, subnetUuid);
        if (gwIp.isPresent()) {
            elanService.removeArpResponderFlow(dpId, ifName, gwIp.get(), java.util.Optional.of(lportTag));
        }
    }

    /**
     * Remove ARP Responder flow when router interface is removed,, by invoking
     * ELan RPC service.
     *
     * @param vpnInterface
     *            VPN Interface
     */
    public void removeArpResponderFlowOnRouterIfRemove(final VpnInterface vpnInterface) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn
                .id.VpnInstance> id = VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnInterface.getVpnInstanceName());
        Optional<VpnInstance> vpnInstance = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        Adjacencies adjacencies = vpnInterface.getAugmentation(Adjacencies.class);
        if (adjacencies != null && adjacencies.getAdjacency() != null) {
            adjacencies.getAdjacency().stream().filter(Adjacency::isPrimaryAdjacency).findFirst().ifPresent(adj -> {
                final java.util.Optional<VpnIds> vpnIds = java.util.Optional
                        .ofNullable(VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(vpnInstance.get().getVpnId())).orNull());
                vpnIds.ifPresent(vpn -> {
                    if (vpn.getPrefixes() != null && adj.getIpAddress() != null) {
                        vpn.getPrefixes().stream()
                                .filter(v -> v.getSubnetId().getValue().equals(adj.getSubnetId().getValue()))
                                .map(Prefixes::getDpnId).distinct().forEach(dpnId -> {

                                    final String gwIp = adj.getIpAddress().substring(0,
                                            adj.getIpAddress().indexOf('/'));
                                    LOG.trace("Removing ARP Responder on DPN {} with GW IP {}", dpnId, gwIp);
                                    elanService.removeArpResponderFlow(dpnId, vpnInterface.getName(), gwIp,
                                            java.util.Optional.empty());
                                });
                    }
                });
            });
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
    public Optional<String> getGatewayMacAddressForInterface(final VpnPortipToPort gwPort, String ifName) {
        Optional<String> routerGwMac = Optional.absent();
        // Check if a router gateway interface is available for the subnet gw is
        // so then use Router interface
        // else use connected interface
        routerGwMac = Optional.of((gwPort != null && gwPort.isSubnetIp()) ? gwPort.getMacAddress()
                : InterfaceUtils.getMacAddressForInterface(dataBroker, ifName).get());
        return routerGwMac;
    }

}
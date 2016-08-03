/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class ArpNotificationHandler implements OdlArputilListener {

    VpnInterfaceManager vpnIfManager;
    DataBroker broker;

    private static final Logger LOG = LoggerFactory.getLogger(ArpNotificationHandler.class);

    public ArpNotificationHandler(VpnInterfaceManager vpnIfMgr, DataBroker dataBroker) {
        vpnIfManager = vpnIfMgr;
        broker = dataBroker;
    }

    @Override
    public void onMacChanged(MacChanged notification){

    }

    @Override
    public void onArpRequestReceived(ArpRequestReceived notification){
        LOG.trace("ArpNotification Request Received from interface {} and IP {} having MAC {} target destination {}",
                notification.getInterface(), notification.getSrcIpaddress().getIpv4Address().getValue(),
                notification.getSrcMac().getValue(),notification.getDstIpaddress().getIpv4Address().getValue());
        String srcInterface = notification.getInterface();
        IpAddress srcIP = notification.getSrcIpaddress();
        PhysAddress srcMac = notification.getSrcMac();
        IpAddress targetIP = notification.getDstIpaddress();
        BigInteger metadata = notification.getMetadata();
        if (metadata != null && metadata != BigInteger.ZERO) {
            long vpnId = MetaDataUtil.getVpnIdFromMetadata(metadata);
            // Respond to ARP request only if vpnservice is configured on the interface
            if (VpnUtil.isVpnInterfaceConfigured(broker, srcInterface)) {
                LOG.info("Received ARP Request for interface {} ", srcInterface);
                InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds>
                        vpnIdsInstanceIdentifier = VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId);
                Optional<VpnIds> vpnIdsOptional
                        = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);
                if (!vpnIdsOptional.isPresent()) {
                    // Donot respond to ARP requests on unknown VPNs
                    LOG.trace("ARP NO_RESOLVE: VPN {} not configured. Ignoring responding to ARP requests on this VPN", vpnId);
                    return;
                }
                String vpnName = vpnIdsOptional.get().getVpnInstanceName();
                String ipToQuery = notification.getSrcIpaddress().getIpv4Address().getValue();
                LOG.trace("ArpRequest being processed for Source IP {}", ipToQuery);
                VpnIds vpnIds = vpnIdsOptional.get();
                VpnPortipToPort vpnPortipToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(broker, vpnIds.getVpnInstanceName(), ipToQuery);
                if (vpnPortipToPort != null) {
                    String oldPortName = vpnPortipToPort.getPortName();
                    String oldMac = vpnPortipToPort.getMacAddress();
                    if (!oldMac.equalsIgnoreCase(srcMac.getValue())) {
                        //MAC has changed for requested IP
                        LOG.trace("ARP request Source IP/MAC data etmodified for IP {} with MAC {} and Port {}", ipToQuery,
                                srcMac, srcInterface);
                        if (!vpnPortipToPort.isConfig()) {
                                synchronized ((vpnName + ipToQuery).intern()) {
                                    vpnIfManager.removeMIPAdjacency(vpnName, oldPortName, srcIP);
                                    VpnUtil.removeVpnPortFixedIpToPort(broker, vpnName, ipToQuery);
                                }
                                try {
                                    Thread.sleep(2000);
                                } catch (Exception e) {
                                }
                            } else {
                                //MAC mismatch for a Neutron learned IP
                                LOG.warn("MAC Address mismatach for Interface {} having a Mac  {},  IP {} and Arp learnt Mac {}",
                                        oldPortName, oldMac, ipToQuery, srcMac.getValue());
                                return;
                            }
                        }
                    } else {
                        synchronized ((vpnName + ipToQuery).intern()) {
                            VpnUtil.createVpnPortFixedIpToPort(broker, vpnName, ipToQuery, srcInterface, srcMac.getValue(), false, false, true);
                            vpnIfManager.addMIPAdjacency(vpnName, srcInterface, srcIP);
                        }
                    }
                    String targetIpToQuery = notification.getDstIpaddress().getIpv4Address().getValue();
                    VpnPortipToPort vpnTargetIpToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(broker,
                            vpnIds.getVpnInstanceName(), targetIpToQuery);
                    //Process and respond from Controller only for GatewayIp ARP request
                    if (vpnTargetIpToPort != null) {
                        if (vpnTargetIpToPort.isSubnetIp()) {
                            String macAddress = vpnTargetIpToPort.getMacAddress();
                            PhysAddress targetMac = new PhysAddress(macAddress);
                            vpnIfManager.processArpRequest(srcIP, srcMac, targetIP, targetMac, srcInterface);
                        }
                    } else {
                        //Respond for gateway Ips ARP requests if L3vpn configured without a router
                        if (vpnIds.isExternalVpn()) {
                            Port prt;
                            String gw = null;
                            Uuid portUuid = new Uuid(srcInterface);
                            InstanceIdentifier<Port> inst = InstanceIdentifier.create(Neutron.class)
                                    .child(Ports.class)
                                    .child(Port.class, new PortKey(portUuid));
                            Optional<Port> port = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
                            if (port.isPresent()) {
                                prt = port.get();
                                Uuid subnetUUID = prt.getFixedIps().get(0).getSubnetId();
                                LOG.trace("Subnet UUID for this VPN Interface is {}", subnetUUID);
                                SubnetKey subnetkey = new SubnetKey(subnetUUID);
                                InstanceIdentifier<Subnet> subnetidentifier = InstanceIdentifier.create(Neutron.class)
                                        .child(Subnets.class)
                                        .child(Subnet.class, subnetkey);
                                Optional<Subnet> subnet = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, subnetidentifier);
                                if (subnet.isPresent()) {
                                    gw = subnet.get().getGatewayIp().getIpv4Address().getValue();
                                    if (targetIpToQuery.equalsIgnoreCase(gw)) {
                                        LOG.trace("Target Destination matches the Gateway IP {} so respond for ARP", gw);
                                        vpnIfManager.processArpRequest(srcIP, srcMac, targetIP, null, srcInterface);
                                    }
                                }
                            }
                    } else if (VpnmanagerServiceAccessor.getElanProvider().isExternalInterface(srcInterface)) {
                        handleArpRequestFromExternalInterface(srcInterface, srcIP, srcMac, targetIP);
                    } else {
                        LOG.trace("ARP request is not on an External VPN/Interface, so ignoring the request.");
                    }
                }
            }
        }
    }

    @Override
    public void onArpResponseReceived(ArpResponseReceived notification){
        LOG.trace("ArpNotification Response Received from interface {} and IP {} having MAC {}",notification.getInterface(),
                notification.getIpaddress().getIpv4Address().getValue(), notification.getMacaddress().getValue());
        String srcInterface = notification.getInterface();
        IpAddress srcIP = notification.getIpaddress();
        PhysAddress srcMac = notification.getMacaddress();
        BigInteger metadata = notification.getMetadata();
        if (metadata != null && metadata != BigInteger.ZERO) {
            long vpnId = MetaDataUtil.getVpnIdFromMetadata(metadata);
            InstanceIdentifier<VpnIds>
                    vpnIdsInstanceIdentifier = VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId);
            Optional<VpnIds> vpnIdsOptional
                    = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);
            if (!vpnIdsOptional.isPresent()) {
                // Donot respond to ARP requests on unknown VPNs
                LOG.trace("ARP NO_RESOLVE: VPN {} not configured. Ignoring responding to ARP requests on this VPN", vpnId);
                return;
            }
            if (VpnUtil.isVpnInterfaceConfigured(broker, srcInterface)) {
                String vpnName = vpnIdsOptional.get().getVpnInstanceName();
                String ipToQuery = notification.getIpaddress().getIpv4Address().getValue();
                VpnIds vpnIds = vpnIdsOptional.get();
                VpnPortipToPort vpnPortipToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(broker, vpnIds.getVpnInstanceName(), ipToQuery);
                if (vpnPortipToPort != null) {
                    String oldMac = vpnPortipToPort.getMacAddress();
                    String oldPortName = vpnPortipToPort.getPortName();
                    if (!oldMac.equalsIgnoreCase(srcMac.getValue())) {
                        //MAC has changed for requested IP
                        LOG.trace("ARP response Source IP/MAC data modified for IP {} with MAC {} and Port {}", ipToQuery,
                                srcMac, srcInterface);
                        if (!vpnPortipToPort.isConfig()) {
                                synchronized ((vpnName + ipToQuery).intern()) {
                                    vpnIfManager.removeMIPAdjacency(vpnName, oldPortName, srcIP);
                                    VpnUtil.removeVpnPortFixedIpToPort(broker, vpnName, ipToQuery);
                                }
                                try {
                                    Thread.sleep(2000);
                                } catch (Exception e) {
                                }
                            } else {
                                //MAC mismatch for a Neutron learned IP set learnt back to false
                                LOG.warn("MAC Address mismatch for Interface {} having a Mac  {} , IP {} and Arp learnt Mac {}",
                                        srcInterface, oldMac, ipToQuery, srcMac.getValue());
                            }
                        }
                    } else {
                        synchronized ((vpnName + ipToQuery).intern()) {
                            VpnUtil.createVpnPortFixedIpToPort(broker, vpnName, ipToQuery, srcInterface, srcMac.getValue(), false, false, true);
                            vpnIfManager.addMIPAdjacency(vpnName, srcInterface, srcIP);
                        }
                    }
            }
        }
    }

    private void handleArpRequestFromExternalInterface(String srcInterface, IpAddress srcIP, PhysAddress srcMac,
            IpAddress targetIP) {
        Port port = VpnUtil.getNeutronPortForFloatingIp(broker, targetIP);
        String targetIpValue = targetIP.getIpv4Address().getValue();
        if (port == null) {
            LOG.trace("No neutron port found for with floating ip {}", targetIpValue);
            return;
        }

        MacAddress targetMac = port.getMacAddress();
        if (targetMac == null) {
            LOG.trace("No mac address found for floating ip {}", targetIpValue);
            return;
        }

        LOG.trace("Target destination matches floating IP {} so respond for ARP", targetIpValue);
        vpnIfManager.processArpRequest(srcIP, srcMac, targetIP, new PhysAddress(targetMac.getValue()), srcInterface);
    }

}

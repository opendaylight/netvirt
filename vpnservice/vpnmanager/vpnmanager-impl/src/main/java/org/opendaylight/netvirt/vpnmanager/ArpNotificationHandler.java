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
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.floatingips.attributes.Floatingips;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.floatingips.attributes.floatingips.Floatingip;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpResponseInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpResponseInputBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import java.util.concurrent.Future;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class ArpNotificationHandler implements OdlArputilListener {
    private static final Logger LOG = LoggerFactory.getLogger(ArpNotificationHandler.class);
    DataBroker dataBroker;
    VpnInterfaceManager vpnIfManager;
    IdManagerService idManager;
    OdlArputilService arpManager;
    final IElanService elanService;
    ArpMonitoringHandler arpScheduler;
    OdlInterfaceRpcService ifaceMgrRpcService;

    public ArpNotificationHandler(DataBroker dataBroker, VpnInterfaceManager vpnIfMgr,
            final IElanService elanService, IdManagerService idManager, OdlArputilService arpManager,
            ArpMonitoringHandler arpScheduler, OdlInterfaceRpcService ifaceMgrRpcService) {
        this.dataBroker = dataBroker;
        vpnIfManager = vpnIfMgr;
        this.elanService = elanService;
        this.idManager = idManager;
        this.arpManager = arpManager;
        this.arpScheduler = arpScheduler;
        this.ifaceMgrRpcService = ifaceMgrRpcService;
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
            if (VpnUtil.isVpnInterfaceConfigured(dataBroker, srcInterface)) {
                LOG.info("Received ARP Request for interface {} ", srcInterface);
                InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds>
                vpnIdsInstanceIdentifier = VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId);
                Optional<VpnIds> vpnIdsOptional
                = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);
                if (!vpnIdsOptional.isPresent()) {
                    // Donot respond to ARP requests on unknown VPNs
                    LOG.trace("ARP NO_RESOLVE: VPN {} not configured. Ignoring responding to ARP requests on this VPN", vpnId);
                    return;
                }
                String vpnName = vpnIdsOptional.get().getVpnInstanceName();
                String ipToQuery = notification.getSrcIpaddress().getIpv4Address().getValue();
                LOG.trace("ArpRequest being processed for Source IP {}", ipToQuery);
                VpnIds vpnIds = vpnIdsOptional.get();
                VpnPortipToPort vpnPortipToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnIds.getVpnInstanceName(), ipToQuery);
                if (vpnPortipToPort != null) {
                    String oldPortName = vpnPortipToPort.getPortName();
                    String oldMac = vpnPortipToPort.getMacAddress();
                    if (!oldMac.equalsIgnoreCase(srcMac.getValue())) {
                        //MAC has changed for requested IP
                        LOG.trace("ARP request Source IP/MAC data etmodified for IP {} with MAC {} and Port {}",
                                ipToQuery, srcMac, srcInterface);
                        if (!vpnPortipToPort.isConfig()) {
                            synchronized ((vpnName + ipToQuery).intern()) {
                                removeMipAdjacency(vpnName, oldPortName, srcIP);
                                VpnUtil.removeVpnPortFixedIpToPort(dataBroker, vpnName, ipToQuery);
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
                        VpnUtil.createVpnPortFixedIpToPort(dataBroker, vpnName, ipToQuery, srcInterface, srcMac.getValue(), false, false, true);
                        addMipAdjacency(vpnName, srcInterface, srcIP);
                    }
                }
                String targetIpToQuery = notification.getDstIpaddress().getIpv4Address().getValue();
                VpnPortipToPort vpnTargetIpToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker,
                        vpnIds.getVpnInstanceName(), targetIpToQuery);

                if (vpnIds.isExternalVpn()) {
                    handleArpRequestForExternalVpn(srcInterface, srcIP, srcMac, targetIP, targetIpToQuery,
                            vpnTargetIpToPort);
                    return;
                }
                if (vpnTargetIpToPort != null && vpnTargetIpToPort.isSubnetIp()) { // handle router interfaces ARP
                    handleArpRequestForSubnetIp(srcInterface, srcIP, srcMac, targetIP, vpnTargetIpToPort);
                    return;
                }
                if (elanService.isExternalInterface(srcInterface)) {
                    handleArpRequestFromExternalInterface(srcInterface, srcIP, srcMac, targetIP);
                    return;
                }
            }
        }
    }

    private void handleArpRequestForSubnetIp(String srcInterface, IpAddress srcIP, PhysAddress srcMac,
            IpAddress targetIP, VpnPortipToPort vpnTargetIpToPort) {
        String macAddress = vpnTargetIpToPort.getMacAddress();
        PhysAddress targetMac = new PhysAddress(macAddress);
        processArpRequest(srcIP, srcMac, targetIP, targetMac, srcInterface);
        return;
    }

    private void handleArpRequestForExternalVpn(String srcInterface, IpAddress srcIP, PhysAddress srcMac,
            IpAddress targetIP, String targetIpToQuery, VpnPortipToPort vpnTargetIpToPort) {
        if (vpnTargetIpToPort != null) {
            if (vpnTargetIpToPort.isSubnetIp()) {
                handleArpRequestForSubnetIp(srcInterface, srcIP, srcMac, targetIP, vpnTargetIpToPort);
            }
            return;
        }
        // Respond for gateway Ips ARP requests if L3vpn configured without a router
        Port prt;
        String gw = null;
        Uuid portUuid = new Uuid(srcInterface);
        InstanceIdentifier<Port> inst = InstanceIdentifier.create(Neutron.class).child(Ports.class)
                .child(Port.class, new PortKey(portUuid));
        Optional<Port> port = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, inst);
        if (port.isPresent()) {
            prt = port.get();
            //TODO(Gobinath): Need to fix this as assuming port will belong to only one Subnet would be incorrect"
            Uuid subnetUUID = prt.getFixedIps().get(0).getSubnetId();
            LOG.trace("Subnet UUID for this VPN Interface is {}", subnetUUID);
            SubnetKey subnetkey = new SubnetKey(subnetUUID);
            InstanceIdentifier<Subnet> subnetidentifier = InstanceIdentifier.create(Neutron.class)
                    .child(Subnets.class).child(Subnet.class, subnetkey);
            Optional<Subnet> subnet = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    subnetidentifier);
            if (subnet.isPresent()) {
                gw = subnet.get().getGatewayIp().getIpv4Address().getValue();
                if (targetIpToQuery.equalsIgnoreCase(gw)) {
                    LOG.trace("Target Destination matches the Gateway IP {} so respond for ARP", gw);
                    processArpRequest(srcIP, srcMac, targetIP, null, srcInterface);
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
            = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);
            if (!vpnIdsOptional.isPresent()) {
                // Donot respond to ARP requests on unknown VPNs
                LOG.trace("ARP NO_RESOLVE: VPN {} not configured. Ignoring responding to ARP requests on this VPN", vpnId);
                return;
            }
            if (VpnUtil.isVpnInterfaceConfigured(dataBroker, srcInterface)) {
                String vpnName = vpnIdsOptional.get().getVpnInstanceName();
                String ipToQuery = notification.getIpaddress().getIpv4Address().getValue();
                VpnIds vpnIds = vpnIdsOptional.get();
                VpnPortipToPort vpnPortipToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnIds
                        .getVpnInstanceName(), ipToQuery);
                if (vpnPortipToPort != null) {
                    String oldMac = vpnPortipToPort.getMacAddress();
                    String oldPortName = vpnPortipToPort.getPortName();
                    if (!oldMac.equalsIgnoreCase(srcMac.getValue())) {
                        //MAC has changed for requested IP
                        LOG.trace("ARP response Source IP/MAC data modified for IP {} with MAC {} and Port {}",
                                ipToQuery, srcMac, srcInterface);
                        if (!vpnPortipToPort.isConfig()) {
                            synchronized ((vpnName + ipToQuery).intern()) {
                                removeMipAdjacency(vpnName, oldPortName, srcIP);
                                VpnUtil.removeVpnPortFixedIpToPort(dataBroker, vpnName, ipToQuery);
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
                        VpnUtil.createVpnPortFixedIpToPort(dataBroker, vpnName, ipToQuery, srcInterface, srcMac.getValue(), false, false, true);
                        addMipAdjacency(vpnName, srcInterface, srcIP);
                    }
                }
            }
        }
    }

    private void handleArpRequestFromExternalInterface(String srcInterface, IpAddress srcIP, PhysAddress srcMac,
            IpAddress targetIP) {
        Port port = VpnUtil.getNeutronPortForFloatingIp(dataBroker, targetIP);
        String floatingIp = targetIP.getIpv4Address().getValue();
        if (port == null) {
            LOG.trace("No neutron port found for with floating ip {}", floatingIp);
            return;
        }

        MacAddress targetMac = port.getMacAddress();
        if (targetMac == null) {
            LOG.trace("No mac address found for floating ip {}", floatingIp);
            return;
        }

        // don't allow ARP responses if it arrives from different dpn
        String localPortInterface = getFloatingInternalInterface(floatingIp);
        if (localPortInterface != null && !localPortInterface.isEmpty()) {
            BigInteger dpnIdSrc = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, srcInterface);
            BigInteger dpnIdLocal = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, localPortInterface);
            if (!dpnIdSrc.equals(dpnIdLocal)) {
                LOG.trace("Not same dpnId, so don't respond for ARP - dpnIdSrc:{} dpnIdLocal:{}", dpnIdSrc, dpnIdLocal);
                return;
            }
        }
        LOG.trace("Target destination matches floating IP {} so respond for ARP", floatingIp);
        vpnIfManager.processArpRequest(srcIP, srcMac, targetIP, new PhysAddress(targetMac.getValue()), srcInterface);
    }

    public void processArpRequest(IpAddress srcIP, PhysAddress srcMac, IpAddress targetIP, PhysAddress targetMac,
            String srcInterface){
        //Build ARP response with ARP requests TargetIp TargetMac as the Arp Response SrcIp and SrcMac
        SendArpResponseInput input = new SendArpResponseInputBuilder().setInterface(srcInterface)
                .setDstIpaddress(srcIP).setDstMacaddress(srcMac).setSrcIpaddress(targetIP).setSrcMacaddress(targetMac).build();
        final String msgFormat = String.format("Send ARP Response on interface %s to destination %s", srcInterface, srcIP);
        Future<RpcResult<Void>> future = arpManager.sendArpResponse(input);
        Futures.addCallback(JdkFutureAdapters.listenInPoolThread(future), new FutureCallback<RpcResult<Void>>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error - {}", msgFormat, error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if(!result.isSuccessful()) {
                    LOG.warn("Rpc call to {} failed", msgFormat);
                } else {
                    LOG.debug("Successful RPC Result - {}", msgFormat);
                }
            }
        });
    }

    private void addMipAdjacency(String vpnName, String vpnInterface, IpAddress prefix){

        LOG.trace("Adding {} adjacency to VPN Interface {} ",prefix,vpnInterface);
        InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
        InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
        synchronized (vpnInterface.intern()) {
            Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
            String nextHopIpAddr = null;
            String nextHopMacAddress = null;
            String ip = prefix.getIpv4Address().getValue();
            if (adjacencies.isPresent()) {
                List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
                ip = VpnUtil.getIpPrefix(ip);
                for (Adjacency adjacs : adjacencyList) {
                    if (adjacs.getMacAddress() != null && !adjacs.getMacAddress().isEmpty()) {
                        nextHopIpAddr = adjacs.getIpAddress();
                        nextHopMacAddress = adjacs.getMacAddress();
                        break;
                    }
                }
                if (nextHopMacAddress != null && ip != null) {
                    String rd = VpnUtil.getVpnRd(dataBroker, vpnName);
                    long label =
                            VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                    VpnUtil.getNextHopLabelKey((rd != null) ? rd : vpnName, ip));
                    if (label == 0) {
                        LOG.error("Unable to fetch label from Id Manager. Bailing out of adding MIP adjacency {} "
                                + "to vpn interface {} for vpn {}", ip, vpnInterface, vpnName);
                        return;
                    }
                    String nextHopIp = nextHopIpAddr.split("/")[0];
                    Adjacency newAdj = new AdjacencyBuilder().setIpAddress(ip).setKey
                            (new AdjacencyKey(ip)).setNextHopIpList(Arrays.asList(nextHopIp)).build();
                    adjacencyList.add(newAdj);
                    Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencyList);
                    VpnInterface newVpnIntf = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(vpnInterface)).
                            setName(vpnInterface).setVpnInstanceName(vpnName).addAugmentation(Adjacencies.class, aug)
                            .build();
                    VpnUtil.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIfId, newVpnIntf);
                    LOG.debug(" Successfully stored subnetroute Adjacency into VpnInterface {}", vpnInterface);
                }
            }
        }

    }

    private void removeMipAdjacency(String vpnName, String vpnInterface, IpAddress prefix) {
        String ip = VpnUtil.getIpPrefix(prefix.getIpv4Address().getValue());
        LOG.trace("Removing {} adjacency from Old VPN Interface {} ", ip,vpnInterface);
        InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
        InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
        synchronized (vpnInterface.intern()) {
            Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
            if (adjacencies.isPresent()) {
                InstanceIdentifier<Adjacency> adjacencyIdentifier = InstanceIdentifier.builder(VpnInterfaces.class).
                        child(VpnInterface.class, new VpnInterfaceKey(vpnInterface)).augmentation(Adjacencies.class)
                        .child(Adjacency.class, new AdjacencyKey(ip)).build();
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, adjacencyIdentifier);
                LOG.trace("Successfully Deleted Adjacency into VpnInterface {}", vpnInterface);
            }
        }
    }

    public String getFloatingInternalInterface(String targetIpValue) {
        if (targetIpValue == null || targetIpValue.isEmpty()) {
            return null;
        }
        InstanceIdentifier<Floatingips> identifier = InstanceIdentifier.create(Neutron.class).child(Floatingips.class);
        Optional<Floatingips> optInterface = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, identifier);
        if (optInterface.isPresent()) {
            Floatingips fips = optInterface.get();
            if (fips != null) {
                for (Floatingip fip : fips.getFloatingip()) {
                    String ipv4Addr = fip.getFloatingIpAddress().getIpv4Address().getValue();
                    if (targetIpValue.equals(ipv4Addr)) {
                        return fip.getPortId().getValue();
                    }
                }
            }
        }
        return null;
    }
}

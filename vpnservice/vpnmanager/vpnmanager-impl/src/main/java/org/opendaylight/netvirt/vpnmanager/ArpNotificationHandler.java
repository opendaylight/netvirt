/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpResponseInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpResponseInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;


public class ArpNotificationHandler implements OdlArputilListener {
    private static final Logger LOG = LoggerFactory.getLogger(ArpNotificationHandler.class);
    // temp where Key is VPNInstance+IP and value is timestamp
    private final Cache<Pair<String, String>, BigInteger> migrateArpReqCache;

    DataBroker dataBroker;
    VpnInterfaceManager vpnIfManager;
    IdManagerService idManager;
    OdlArputilService arpManager;
    ArpMonitoringHandler arpScheduler;
    OdlInterfaceRpcService ifaceMgrRpcService;
    IInterfaceManager interfaceManager;
    private final VpnConfig config;


    public ArpNotificationHandler(DataBroker dataBroker, VpnInterfaceManager vpnIfMgr,
                                  IdManagerService idManager, OdlArputilService arpManager,
            ArpMonitoringHandler arpScheduler, OdlInterfaceRpcService ifaceMgrRpcService,
            IInterfaceManager interfaceManager, VpnConfig vpnConfig) {
        this.dataBroker = dataBroker;
        vpnIfManager = vpnIfMgr;
        this.idManager = idManager;
        this.arpManager = arpManager;
        this.arpScheduler = arpScheduler;
        this.ifaceMgrRpcService = ifaceMgrRpcService;
        this.interfaceManager = interfaceManager;
        this.config = vpnConfig;

        long duration = config.getArpLearnTimeout() * 10;
        long cacheSize = config.getArpCacheSize().longValue();
        migrateArpReqCache =
                CacheBuilder.newBuilder().maximumSize(cacheSize).expireAfterWrite(duration, TimeUnit.MILLISECONDS).build();
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
            InstanceIdentifier<VpnIds> vpnIdsInstanceIdentifier = VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId);
            Optional<VpnIds> vpnIdsOptional
                    = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);
            if (!vpnIdsOptional.isPresent()) {
                // Donot respond to ARP requests on unknown VPNs
                LOG.trace("ARP NO_RESOLVE: VPN {} not configured. Ignoring responding to ARP requests on this VPN", vpnId);
                return;
            }
            VpnIds vpnIds = vpnIdsOptional.get();
            String vpnName = vpnIds.getVpnInstanceName();
            if (VpnUtil.isInterfaceAssociatedWithVpn(dataBroker, vpnName, srcInterface)) {
                LOG.debug("Received ARP request for target IP {}, sender MAC {} and sender IP {} via interface {}",
                        targetIP.getIpv4Address().getValue(), srcMac.getValue(), srcIP.getIpv4Address().getValue(), srcInterface);
                String ipToQuery = notification.getSrcIpaddress().getIpv4Address().getValue();
                LOG.trace("ArpRequest being processed for Source IP {}", ipToQuery);
                VpnPortipToPort vpnPortipToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnName, ipToQuery);
                boolean isGarp = srcIP.equals(targetIP);
                if (isGarp && vpnPortipToPort != null) {
                    String oldPortName = vpnPortipToPort.getPortName();
                    String oldMac = vpnPortipToPort.getMacAddress();
                    if (!oldMac.equalsIgnoreCase(srcMac.getValue())) {
                        //MAC has changed for requested IP
                        LOG.trace("ARP request Source IP/MAC data modified for IP {} with MAC {} and Port {}",
                                ipToQuery, srcMac, srcInterface);
                        if (!vpnPortipToPort.isConfig()) {
                            synchronized ((vpnName + ipToQuery).intern()) {
                                removeMipAdjacency(vpnName, oldPortName, srcIP);
                                VpnUtil.removeVpnPortFixedIpToPort(dataBroker, vpnName, ipToQuery);

                                putVpnIpToMigrateArpCache(vpnName, ipToQuery, srcMac);
                            }
                        } else {
                            //MAC mismatch for a Neutron learned IP
                            LOG.warn("MAC Address mismatch for Interface {} having a Mac {},  IP {} and ARP learnt Mac {}",
                                    oldPortName, oldMac, ipToQuery, srcMac.getValue());
                            return;
                        }
                    }
                } else if (isGarp && shouldLearnMacFromArpPackets(vpnName, ipToQuery)) {
                    learnMacFromArpPackets(vpnName, srcInterface, srcIP, srcMac);
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
            String vpnName = vpnIdsOptional.get().getVpnInstanceName();
            if (VpnUtil.isInterfaceAssociatedWithVpn(dataBroker, vpnName, srcInterface)) {
                String ipToQuery = notification.getIpaddress().getIpv4Address().getValue();
                VpnPortipToPort vpnPortipToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnName, ipToQuery);
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

                                putVpnIpToMigrateArpCache(vpnName, ipToQuery, srcMac);
                            }
                        } else {
                            //MAC mismatch for a Neutron learned IP set learnt back to false
                            LOG.warn("MAC Address mismatch for Interface {} having a Mac  {} , IP {} and Arp learnt Mac {}",
                                    srcInterface, oldMac, ipToQuery, srcMac.getValue());
                        }
                    }
                } else if (shouldLearnMacFromArpPackets(vpnName, ipToQuery)) {
                    learnMacFromArpPackets(vpnName, srcInterface, srcIP, srcMac);
                }
            }
        }
    }

    private void learnMacFromArpPackets(String vpnName, String srcInterface,
                                        IpAddress srcIP, PhysAddress srcMac) {
        String ipToQuery = srcIP.getIpv4Address().getValue();
        /* Traffic coming from external interfaces should always be learnt */
        if (interfaceManager.isExternalInterface(srcInterface) ||
                !VpnUtil.isNeutronPortConfigured(dataBroker, srcInterface, srcIP)) {
            synchronized ((vpnName + ipToQuery).intern()) {
                VpnUtil.createVpnPortFixedIpToPort(dataBroker, vpnName, ipToQuery, srcInterface,
                        srcMac.getValue(), false, false, true);
                addMipAdjacency(vpnName, srcInterface, srcIP, srcMac.getValue());
            }
        }
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

    private void addMipAdjacency(String vpnName, String vpnInterface, IpAddress prefix, String mipMacAddress){

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
                    if (adjacs.isPrimaryAdjacency()) {
                        nextHopIpAddr = adjacs.getIpAddress();
                        nextHopMacAddress = adjacs.getMacAddress();
                        break;
                    }
                }
                if (nextHopIpAddr != null) {
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
                    AdjacencyBuilder newAdjBuilder = new AdjacencyBuilder().setIpAddress(ip).setKey
                            (new AdjacencyKey(ip)).setNextHopIpList(Arrays.asList(nextHopIp));
                    if (mipMacAddress != null && !mipMacAddress.equals(nextHopMacAddress)) {
                        newAdjBuilder.setMacAddress(mipMacAddress);
                    }
                    adjacencyList.add(newAdjBuilder.build());
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

    private void putVpnIpToMigrateArpCache(String vpnName, String ipToQuery, PhysAddress srcMac) {
        long cacheSize = config.getArpCacheSize().longValue();
        if (migrateArpReqCache.size() >= cacheSize) {
            LOG.debug("ARP_MIGRATE_CACHE: max size {} reached, assuming cache eviction we still put IP {}"
                    + " vpnName {} with MAC {}", cacheSize, ipToQuery, vpnName, srcMac);
        }
        LOG.debug("ARP_MIGRATE_CACHE: add to dirty cache IP {} vpnName {} with MAC {}", ipToQuery, vpnName, srcMac);
        migrateArpReqCache.put(new ImmutablePair<>(vpnName, ipToQuery),
                new BigInteger(String.valueOf(System.currentTimeMillis())));
    }

    private boolean shouldLearnMacFromArpPackets(String vpnName, String ipToQuery) {
        if (migrateArpReqCache == null || migrateArpReqCache.size() == 0) {
            return true;
        }
        Pair<String, String> keyPair = new ImmutablePair<>(vpnName, ipToQuery);
        BigInteger prevTimeStampCached = migrateArpReqCache.getIfPresent(keyPair);
        if (prevTimeStampCached == null) {
            LOG.debug("ARP_MIGRATE_CACHE: there is no IP {} vpnName {} in dirty cache, so learn it",
                    ipToQuery, vpnName);
            return true;
        }
        if (System.currentTimeMillis() > prevTimeStampCached.longValue() + config.getArpLearnTimeout()) {
            LOG.debug("ARP_MIGRATE_CACHE: older than timeout value - remove from dirty cache IP {} vpnName {}",
                    ipToQuery, vpnName);
            migrateArpReqCache.invalidate(keyPair);
            return true;
        }
        LOG.debug("ARP_MIGRATE_CACHE: younger than timeout value - ignore learning IP {} vpnName {}",
                ipToQuery, vpnName);
        return false;
    }
}

/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInterfaceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ArpNotificationHandler implements OdlArputilListener {
    private static final Logger LOG = LoggerFactory.getLogger(ArpNotificationHandler.class);
    // temp where Key is VPNInstance+IP and value is timestamp
    private final Cache<Pair<String, String>, BigInteger> migrateArpCache;

    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final IInterfaceManager interfaceManager;
    private final VpnConfig config;

    @Inject
    public ArpNotificationHandler(DataBroker dataBroker, IdManagerService idManager,
                                  IInterfaceManager interfaceManager, VpnConfig vpnConfig) {
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.interfaceManager = interfaceManager;
        this.config = vpnConfig;

        long duration = config.getArpLearnTimeout() * 10;
        long cacheSize = config.getArpCacheSize().longValue();
        migrateArpCache =
                CacheBuilder.newBuilder().maximumSize(cacheSize).expireAfterWrite(duration,
                        TimeUnit.MILLISECONDS).build();
    }

    @Override
    public void onMacChanged(MacChanged notification) {

    }

    @Override
    public void onArpRequestReceived(ArpRequestReceived notification) {
        String srcInterface = notification.getInterface();
        IpAddress srcIP = notification.getSrcIpaddress();
        PhysAddress srcMac = notification.getSrcMac();
        IpAddress targetIP = notification.getDstIpaddress();
        BigInteger metadata = notification.getMetadata();
        boolean isGarp = srcIP.equals(targetIP);
        if (!isGarp) {
            LOG.info("ArpNotification Non-Gratuitous Request Received from "
                      + "interface {} and IP {} having MAC {} target destination {}, ignoring..",
                    srcInterface, srcIP.getIpv4Address().getValue(),srcMac.getValue(),
                    targetIP.getIpv4Address().getValue());
            return;
        }
        LOG.info("ArpNotification Gratuitous Request Received from "
                  + "interface {} and IP {} having MAC {} target destination {}, learning MAC",
                  srcInterface, srcIP.getIpv4Address().getValue(),srcMac.getValue(),
                  targetIP.getIpv4Address().getValue());
        processArpLearning(srcInterface, srcIP, srcMac, metadata, targetIP);
    }

    @Override
    public void onArpResponseReceived(ArpResponseReceived notification) {
        String srcInterface = notification.getInterface();
        IpAddress srcIP = notification.getSrcIpaddress();
        PhysAddress srcMac = notification.getSrcMac();
        LOG.info("ArpNotification Response Received from interface {} and IP {} having MAC {}, learning MAC",
                srcInterface, srcIP.getIpv4Address().getValue(), srcMac.getValue());
        List<Adjacency> adjacencies = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker, srcInterface);
        if (adjacencies != null) {
            for (Adjacency adj : adjacencies) {
                String ipAddress = adj.getIpAddress();
                try {
                    if (NWUtil.isIpInSubnet(NWUtil.ipAddressToInt(srcIP.getIpv4Address().getValue()), ipAddress)) {
                        return;
                    }
                } catch (UnknownHostException e) {
                    LOG.error("Subnet string {} not convertible to InetAdddress", srcIP, e);
                }
            }
        }
        BigInteger metadata = notification.getMetadata();
        IpAddress targetIP = notification.getDstIpaddress();
        LOG.trace("ArpNotification Response Received from interface {} and IP {} having MAC {}, learning MAC",
                srcInterface, srcIP.getIpv4Address().getValue(), srcMac.getValue());
        processArpLearning(srcInterface, srcIP, srcMac, metadata, targetIP);
    }

    private void processArpLearning(String srcInterface, IpAddress srcIP, PhysAddress srcMac, BigInteger metadata,
            IpAddress dstIP) {
        if (metadata != null && !Objects.equals(metadata, BigInteger.ZERO)) {
            Optional<List<String>> vpnList = VpnUtil
                  .getVpnHandlingIpv4AssociatedWithInterface(dataBroker, srcInterface);
            if (vpnList.isPresent()) {
                for (String vpnName : vpnList.get()) {
                    LOG.info("Received ARP for sender MAC {} and sender IP {} via interface {}",
                              srcMac.getValue(), srcIP.getIpv4Address().getValue(), srcInterface);
                    String ipToQuery = srcIP.getIpv4Address().getValue();
                    LOG.info("ARP being processed for Source IP {}", ipToQuery);
                    VpnPortipToPort vpnPortipToPort =
                            VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnName, ipToQuery);
                    if (vpnPortipToPort != null) {
                        /* This is a well known neutron port and so should be ignored
                         * from being discovered
                         */
                        continue;
                    }
                    LearntVpnVipToPort learntVpnVipToPort = VpnUtil.getLearntVpnVipToPort(dataBroker,
                              vpnName, ipToQuery);
                    if (learntVpnVipToPort != null) {
                        String oldPortName = learntVpnVipToPort.getPortName();
                        String oldMac = learntVpnVipToPort.getMacAddress();
                        if (!oldMac.equalsIgnoreCase(srcMac.getValue())) {
                            //MAC has changed for requested IP
                            LOG.info("ARP Source IP/MAC data modified for IP {} with MAC {} and Port {}",
                                    ipToQuery, srcMac, srcInterface);
                            synchronized ((vpnName + ipToQuery).intern()) {
                                VpnUtil.createLearntVpnVipToPortEvent(dataBroker, vpnName, ipToQuery,
                                        oldPortName, oldMac, LearntVpnVipToPortEventAction.Delete, null);
                                putVpnIpToMigrateArpCache(vpnName, ipToQuery, srcMac);
                            }
                        }
                    } else if (!isIpInArpMigrateCache(vpnName, ipToQuery)) {
                        learnMacFromArpPackets(vpnName, srcInterface, srcIP, srcMac, dstIP);
                    }
                }
            } else {
                LOG.info("ARP NO_RESOLVE: VPN  not configured. Ignoring responding to ARP requests from this"
                        + " Interface {}.", srcInterface);
                return;

            }
        }
    }

    private void learnMacFromArpPackets(String vpnName, String srcInterface,
        IpAddress srcIP, PhysAddress srcMac, IpAddress dstIP) {
        String ipToQuery = srcIP.getIpv4Address().getValue();
        synchronized ((vpnName + ipToQuery).intern()) {
            VpnUtil.createLearntVpnVipToPortEvent(dataBroker, vpnName, ipToQuery, srcInterface,
                    srcMac.getValue(), LearntVpnVipToPortEventAction.Add, null);
        }
    }

//    private void addMipAdjacency(String vpnName, String vpnInterface, IpAddress srcPrefix, String mipMacAddress,
//            IpAddress dstPrefix) {
//        LOG.trace("Adding {} adjacency to VPN Interface {} ",srcPrefix,vpnInterface);
//        InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
//        InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
//        synchronized (vpnInterface.intern()) {
//            Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
//            String nextHopIpAddr = null;
//            String nextHopMacAddress = null;
//            String ip = srcPrefix.getIpv4Address().getValue();
//            if (interfaceManager.isExternalInterface(vpnInterface)) {
//                String subnetId = getSubnetId(vpnName, dstPrefix.getIpv4Address().getValue());
//                if (subnetId == null) {
//                    LOG.trace("Can't find corresponding subnet for src IP {}, src MAC {}, dst IP {},  in VPN {}",
//                            srcPrefix, mipMacAddress, dstPrefix, vpnName);
//                    return;
//                }
//                ip = VpnUtil.getIpPrefix(ip);
//                AdjacencyBuilder newAdjBuilder = new AdjacencyBuilder().setIpAddress(ip).setKey(new AdjacencyKey(ip))
//                        .setAdjacencyType(AdjacencyType.PrimaryAdjacency).setMacAddress(mipMacAddress)
//                        .setSubnetId(new Uuid(subnetId)).setPhysNetworkFunc(true);
//
//                List<Adjacency> adjacencyList = adjacencies.isPresent()
//                        ? adjacencies.get().getAdjacency() : new ArrayList<>();
//
//                adjacencyList.add(newAdjBuilder.build());
//
//                Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencyList);
//                Optional<VpnInterface> optionalVpnInterface =
//                    VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIfId);
//                VpnInterface newVpnIntf;
//                if (optionalVpnInterface.isPresent()) {
//                    newVpnIntf =
//                        new VpnInterfaceBuilder(optionalVpnInterface.get())
//                            .addAugmentation(Adjacencies.class, aug)
//                            .build();
//                    VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIfId, newVpnIntf);
//                }
//                LOG.debug(" Successfully stored subnetroute Adjacency into VpnInterface {}", vpnInterface);
//                return;
//            }
//
//            if (adjacencies.isPresent()) {
//                List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
//                ip = VpnUtil.getIpPrefix(ip);
//                for (Adjacency adjacs : adjacencyList) {
//                    if (adjacs.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
//                        if (adjacs.getIpAddress().equals(ip)) {
//                            LOG.error("The MIP {} is already present as a primary adjacency for interface {} vpn {}."
//                                    + "Skipping adjacency addition.", ip, vpnInterface, vpnName);
//                            return;
//                        }
//                        nextHopIpAddr = adjacs.getIpAddress();
//                        nextHopMacAddress = adjacs.getMacAddress();
//                        break;
//                    }
//                }
//                if (nextHopIpAddr != null) {
//                    String rd = VpnUtil.getVpnRd(dataBroker, vpnName);
//                    long label =
//                        VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
//                            VpnUtil.getNextHopLabelKey(rd != null ? rd : vpnName, ip));
//                    if (label == 0) {
//                        LOG.error("Unable to fetch label from Id Manager. Bailing out of adding MIP adjacency {} "
//                            + "to vpn interface {} for vpn {}", ip, vpnInterface, vpnName);
//                        return;
//                    }
//                    String nextHopIp = nextHopIpAddr.split("/")[0];
//                    AdjacencyBuilder newAdjBuilder =
//                            new AdjacencyBuilder().setIpAddress(ip).setKey(new AdjacencyKey(ip)).setNextHopIpList(
//                                    Collections.singletonList(nextHopIp)).setAdjacencyType(AdjacencyType.LearntIp);
//                    if (mipMacAddress != null && !mipMacAddress.equalsIgnoreCase(nextHopMacAddress)) {
//                        newAdjBuilder.setMacAddress(mipMacAddress);
//                    }
//                    adjacencyList.add(newAdjBuilder.build());
//                    Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencyList);
//                    Optional<VpnInterface> optionalVpnInterface =
//                        VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIfId);
//                    VpnInterface newVpnIntf;
//                    if (optionalVpnInterface.isPresent()) {
//                        newVpnIntf =
//                            new VpnInterfaceBuilder(optionalVpnInterface.get())
//                                .addAugmentation(Adjacencies.class, aug).build();
//                        VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
//                                 vpnIfId, newVpnIntf);
//                    }
//                    LOG.debug(" Successfully stored subnetroute Adjacency into VpnInterface {}", vpnInterface);
//                }
//            }
//        }
//
//    }

    private String getSubnetId(String vpnName, String ip) {
        // Check if this IP belongs to a router_interface
        VpnPortipToPort vpnPortipToPort =
                VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnName, ip);
        if (vpnPortipToPort != null && vpnPortipToPort.isSubnetIp()) {
            List<Adjacency> adjacecnyList = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker,
                    vpnPortipToPort.getPortName());
            for (Adjacency adjacency : adjacecnyList) {
                if (adjacency.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                    return adjacency.getSubnetId().getValue();
                }
            }
        }

        // Check if this IP belongs to a router_gateway
        List<Uuid> routerIds = VpnUtil.getExternalNetworkRouterIds(dataBroker, new Uuid(vpnName));
        for (Uuid routerId : routerIds) {
            Uuid subnetId = VpnUtil.getSubnetFromExternalRouterByIp(dataBroker, routerId, ip);
            if (subnetId != null) {
                return subnetId.getValue();
            }
        }

        return null;
    }

    private void putVpnIpToMigrateArpCache(String vpnName, String ipToQuery, PhysAddress srcMac) {
        long cacheSize = config.getArpCacheSize().longValue();
        if (migrateArpCache.size() >= cacheSize) {
            LOG.debug("ARP_MIGRATE_CACHE: max size {} reached, assuming cache eviction we still put IP {}"
                    + " vpnName {} with MAC {}", cacheSize, ipToQuery, vpnName, srcMac);
        }
        LOG.debug("ARP_MIGRATE_CACHE: add to dirty cache IP {} vpnName {} with MAC {}", ipToQuery, vpnName, srcMac);
        migrateArpCache.put(new ImmutablePair<>(vpnName, ipToQuery),
                new BigInteger(String.valueOf(System.currentTimeMillis())));
    }

    private boolean isIpInArpMigrateCache(String vpnName, String ipToQuery) {
        if (migrateArpCache == null || migrateArpCache.size() == 0) {
            return false;
        }
        Pair<String, String> keyPair = new ImmutablePair<>(vpnName, ipToQuery);
        BigInteger prevTimeStampCached = migrateArpCache.getIfPresent(keyPair);
        if (prevTimeStampCached == null) {
            LOG.debug("ARP_MIGRATE_CACHE: there is no IP {} vpnName {} in dirty cache, so learn it",
                    ipToQuery, vpnName);
            return false;
        }
        if (System.currentTimeMillis() > prevTimeStampCached.longValue() + config.getArpLearnTimeout()) {
            LOG.debug("ARP_MIGRATE_CACHE: older than timeout value - remove from dirty cache IP {} vpnName {}",
                    ipToQuery, vpnName);
            migrateArpCache.invalidate(keyPair);
            return false;
        }
        LOG.debug("ARP_MIGRATE_CACHE: younger than timeout value - ignore learning IP {} vpnName {}",
                ipToQuery, vpnName);
        return true;
    }
}

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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
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
    private final VpnUtil vpnUtil;

    @Inject
    public ArpNotificationHandler(DataBroker dataBroker, IdManagerService idManager,
                                  IInterfaceManager interfaceManager, VpnConfig vpnConfig,
                                  VpnUtil vpnUtil) {
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.interfaceManager = interfaceManager;
        this.config = vpnConfig;
        this.vpnUtil = vpnUtil;

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
        List<Adjacency> adjacencies = vpnUtil.getAdjacenciesForVpnInterfaceFromConfig(srcInterface);
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
            Optional<List<String>> vpnList = vpnUtil
                  .getVpnHandlingIpv4AssociatedWithInterface(srcInterface);
            if (vpnList.isPresent()) {
                for (String vpnName : vpnList.get()) {
                    LOG.info("Received ARP for sender MAC {} and sender IP {} via interface {}",
                              srcMac.getValue(), srcIP.getIpv4Address().getValue(), srcInterface);
                    String srcIpToQuery = srcIP.getIpv4Address().getValue();
                    String destIpToQuery = dstIP.getIpv4Address().getValue();
                    LOG.info("ARP being processed for Source IP {}", srcIpToQuery);
                    VpnPortipToPort vpnPortipToPort =
                            vpnUtil.getNeutronPortFromVpnPortFixedIp(vpnName, srcIpToQuery);
                    if (vpnPortipToPort != null) {
                        /* This is a well known neutron port and so should be ignored
                         * from being discovered
                         */
                        continue;
                    }
                    LearntVpnVipToPort learntVpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, srcIpToQuery);
                    if (learntVpnVipToPort != null) {
                        String oldPortName = learntVpnVipToPort.getPortName();
                        String oldMac = learntVpnVipToPort.getMacAddress();
                        if (!oldMac.equalsIgnoreCase(srcMac.getValue())) {
                            //MAC has changed for requested IP
                            LOG.info("ARP Source IP/MAC data modified for IP {} with MAC {} and Port {}",
                                    srcIpToQuery, srcMac, srcInterface);
                            synchronized ((vpnName + srcIpToQuery).intern()) {
                                vpnUtil.createLearntVpnVipToPortEvent(vpnName, srcIpToQuery, destIpToQuery,
                                        oldPortName, oldMac, LearntVpnVipToPortEventAction.Delete, null);
                                putVpnIpToMigrateArpCache(vpnName, srcIpToQuery, srcMac);
                            }
                        }
                    } else if (!isIpInArpMigrateCache(vpnName, srcIpToQuery)) {
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
        String srcIpToQuery = srcIP.getIpv4Address().getValue();
        String destIpToQuery = dstIP.getIpv4Address().getValue();
        synchronized ((vpnName + srcIpToQuery).intern()) {
            vpnUtil.createLearntVpnVipToPortEvent(vpnName, srcIpToQuery, destIpToQuery, srcInterface,
                    srcMac.getValue(), LearntVpnVipToPortEventAction.Add, null);
        }
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

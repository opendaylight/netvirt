/*
 * Copyright (c) 2018 Alten Calsoft Labs India Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.iplearn;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefixBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractIpLearnNotificationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractIpLearnNotificationHandler.class);

    // temp where Key is VPNInstance+IP and value is timestamp
    private final Cache<Pair<String, String>, Uint64> migrateIpCache;

    protected final VpnConfig config;
    protected final VpnUtil vpnUtil;
    protected final INeutronVpnManager neutronVpnManager;
    private long bootupTime = 0L;

    public AbstractIpLearnNotificationHandler(VpnConfig vpnConfig, VpnUtil vpnUtil,
            INeutronVpnManager neutronVpnManager) {
        this.config = vpnConfig;
        this.vpnUtil = vpnUtil;
        this.neutronVpnManager = neutronVpnManager;

        long duration = config.getIpLearnTimeout().toJava() * 10;
        long cacheSize = config.getMigrateIpCacheSize().longValue();
        migrateIpCache =
                CacheBuilder.newBuilder().maximumSize(cacheSize).expireAfterWrite(duration,
                        TimeUnit.MILLISECONDS).build();
        this.bootupTime = System.currentTimeMillis();
    }

    protected void validateAndProcessIpLearning(String srcInterface, IpAddress srcIP, MacAddress srcMac,
            IpAddress targetIP, Uint64 metadata) {
        List<Adjacency> adjacencies = vpnUtil.getAdjacenciesForVpnInterfaceFromConfig(srcInterface);
        IpVersionChoice srcIpVersion = VpnUtil.getIpVersionFromString(srcIP.stringValue());
        boolean isSrcIpVersionPartOfVpn = false;
        if (adjacencies != null && !adjacencies.isEmpty()) {
            for (Adjacency adj : adjacencies) {
                IpPrefix ipPrefix = IpPrefixBuilder.getDefaultInstance(adj.getIpAddress());
                // If extra/static route is configured, we should ignore for learning process
                if (NWUtil.isIpAddressInRange(srcIP, ipPrefix)) {
                    return;
                }
                IpVersionChoice currentAdjIpVersion = VpnUtil.getIpVersionFromString(adj.getIpAddress());
                if (srcIpVersion.isIpVersionChosen(currentAdjIpVersion)) {
                    isSrcIpVersionPartOfVpn = true;
                }
            }
            //If srcIP version is not part of the srcInterface VPN Adjacency, ignore IpLearning process
            if (!isSrcIpVersionPartOfVpn) {
                return;
            }
        }

        LOG.trace("ARP/NA Notification Response Received from interface {} and IP {} having MAC {}, learning MAC",
                srcInterface, srcIP.stringValue(), srcMac.getValue());
        processIpLearning(srcInterface, srcIP, srcMac, metadata, targetIP);
    }

    protected void processIpLearning(String srcInterface, IpAddress srcIP, MacAddress srcMac, Uint64 metadata,
                                     IpAddress dstIP) {

        if (!VpnUtil.isArpLearningEnabled()) {
            LOG.trace("Not handling packet as ARP Based Learning is disabled");
            return;
        }
        if (metadata == null || Objects.equals(metadata, Uint64.ZERO)) {
            return;
        }

        Optional<List<String>> vpnList = vpnUtil.getVpnHandlingIpv4AssociatedWithInterface(srcInterface);
        if (!vpnList.isPresent()) {
            LOG.info("IP LEARN NO_RESOLVE: VPN  not configured. Ignoring responding to ARP/NA requests from this"
                    + " Interface {}.", srcInterface);
            return;
        }

        String srcIpToQuery = srcIP.stringValue();
        String destIpToQuery = dstIP.stringValue();
        for (String vpnName : vpnList.get()) {
            LOG.info("Received ARP/NA for sender MAC {} and sender IP {} via interface {}", srcMac.getValue(),
                    srcIpToQuery, srcInterface);
            final ReentrantLock lock = lockFor(vpnName, srcIpToQuery);
            lock.lock();
            try {
                VpnPortipToPort vpnPortipToPort = vpnUtil.getNeutronPortFromVpnPortFixedIp(vpnName, srcIpToQuery);
                // Check if this IP belongs to  external network
                if (vpnPortipToPort == null) {
                    String extSubnetId = vpnUtil.getAssociatedExternalSubnet(srcIpToQuery);
                    if (extSubnetId != null) {
                        vpnPortipToPort =
                                vpnUtil.getNeutronPortFromVpnPortFixedIp(extSubnetId, srcIpToQuery);
                    }
                }
                if (vpnPortipToPort != null && !vpnPortipToPort.isLearntIp()) {
                    /*
                     * This is a well known neutron port and so should be ignored from being
                     * discovered...unless it is an Octavia VIP
                     */
                    String portName = vpnPortipToPort.getPortName();
                    Port neutronPort = neutronVpnManager.getNeutronPort(portName);

                    if (neutronPort == null) {
                        LOG.warn("{} should have been a neutron port but could not retrieve it. Aborting processing",
                                portName);
                        continue;
                    }

                    if (!"Octavia".equals(neutronPort.getDeviceOwner())) {
                        LOG.debug("Neutron port {} is not an Octavia port, ignoring", portName);
                        continue;
                    }
                }
                // For IPs learnt before cluster-reboot/upgrade, GARP/ArpResponse is received
                // within 300sec
                // after reboot, it would be ignored.
                if (vpnPortipToPort != null && vpnPortipToPort.isLearntIp()) {
                    if (System.currentTimeMillis()
                            < this.bootupTime + config.getBootDelayArpLearning().toJava() * 1000) {
                        LOG.trace("GARP/Arp Response not handled for IP {} vpnName {} for time {}s",
                                vpnPortipToPort.getPortFixedip(), vpnName, config.getBootDelayArpLearning());
                        continue;
                    }
                }
                LearntVpnVipToPort learntVpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, srcIpToQuery);
                if (learntVpnVipToPort != null) {
                    String oldPortName = learntVpnVipToPort.getPortName();
                    String oldMac = learntVpnVipToPort.getMacAddress();
                    if (!oldMac.equalsIgnoreCase(srcMac.getValue())) {
                        // MAC has changed for requested IP
                        LOG.info("ARP/NA Source IP/MAC data modified for IP {} with MAC {} and Port {}", srcIpToQuery,
                                srcMac, srcInterface);
                        vpnUtil.createLearntVpnVipToPortEvent(vpnName, srcIpToQuery, destIpToQuery, oldPortName, oldMac,
                                LearntVpnVipToPortEventAction.Delete, null);
                        putVpnIpToMigrateIpCache(vpnName, srcIpToQuery, srcMac);
                    }
                } else if (!isIpInMigrateCache(vpnName, srcIpToQuery)) {
                    if (vpnPortipToPort != null && !vpnPortipToPort.getPortName().equals(srcInterface)) {
                        LOG.trace(
                                "LearntIp: {} vpnName {} is already present in VpnPortIpToPort with " + "PortName {} ",
                                srcIpToQuery, vpnName, vpnPortipToPort.getPortName());
                        vpnUtil.createLearntVpnVipToPortEvent(vpnName, srcIpToQuery, destIpToQuery,
                                vpnPortipToPort.getPortName(), vpnPortipToPort.getMacAddress(),
                                LearntVpnVipToPortEventAction.Delete, null);
                        continue;
                    }
                    learnMacFromIncomingPacket(vpnName, srcInterface, srcIP, srcMac, dstIP);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void learnMacFromIncomingPacket(String vpnName, String srcInterface, IpAddress srcIP, MacAddress srcMac,
            IpAddress dstIP) {
        String srcIpToQuery = srcIP.stringValue();
        String destIpToQuery = dstIP.stringValue();
        final ReentrantLock lock = lockFor(vpnName, srcIpToQuery);
        lock.lock();
        try {
            vpnUtil.createLearntVpnVipToPortEvent(vpnName, srcIpToQuery, destIpToQuery, srcInterface,
                    srcMac.getValue(), LearntVpnVipToPortEventAction.Add, null);
        } finally {
            lock.unlock();
        }
    }

    private void putVpnIpToMigrateIpCache(String vpnName, String ipToQuery, MacAddress srcMac) {
        long cacheSize = config.getMigrateIpCacheSize().longValue();
        if (migrateIpCache.size() >= cacheSize) {
            LOG.debug("IP_MIGRATE_CACHE: max size {} reached, assuming cache eviction we still put IP {}"
                    + " vpnName {} with MAC {}", cacheSize, ipToQuery, vpnName, srcMac);
        }
        LOG.debug("IP_MIGRATE_CACHE: add to dirty cache IP {} vpnName {} with MAC {}", ipToQuery, vpnName, srcMac);
        migrateIpCache.put(new ImmutablePair<>(vpnName, ipToQuery),
                Uint64.valueOf(String.valueOf(System.currentTimeMillis())));
    }

    private boolean isIpInMigrateCache(String vpnName, String ipToQuery) {
        if (migrateIpCache == null || migrateIpCache.size() == 0) {
            return false;
        }
        Pair<String, String> keyPair = new ImmutablePair<>(vpnName, ipToQuery);
        Uint64 prevTimeStampCached = migrateIpCache.getIfPresent(keyPair);
        if (prevTimeStampCached == null) {
            LOG.debug("IP_MIGRATE_CACHE: there is no IP {} vpnName {} in dirty cache, so learn it",
                    ipToQuery, vpnName);
            return false;
        }
        if (System.currentTimeMillis() > prevTimeStampCached.longValue() + config.getIpLearnTimeout().toJava()) {
            LOG.debug("IP_MIGRATE_CACHE: older than timeout value - remove from dirty cache IP {} vpnName {}",
                    ipToQuery, vpnName);
            migrateIpCache.invalidate(keyPair);
            return false;
        }
        LOG.debug("IP_MIGRATE_CACHE: younger than timeout value - ignore learning IP {} vpnName {}",
                ipToQuery, vpnName);
        return true;
    }

    private static ReentrantLock lockFor(String vpnName, String srcIpToQuery) {
        // FIXME: form an Identifier? That would side-step string concat here
        return JvmGlobalLocks.getLockForString(vpnName + srcIpToQuery);
    }
}

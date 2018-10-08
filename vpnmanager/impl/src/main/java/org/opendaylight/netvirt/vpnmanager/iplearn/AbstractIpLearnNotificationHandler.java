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
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefixBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractIpLearnNotificationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractIpLearnNotificationHandler.class);

    // temp where Key is VPNInstance+IP and value is timestamp
    private final Cache<Pair<String, String>, BigInteger> migrateIpCache;

    protected final VpnConfig config;
    protected final VpnUtil vpnUtil;

    public AbstractIpLearnNotificationHandler(VpnConfig vpnConfig, VpnUtil vpnUtil) {
        this.config = vpnConfig;
        this.vpnUtil = vpnUtil;

        long duration = config.getIpLearnTimeout() * 10;
        long cacheSize = config.getMigrateIpCacheSize().longValue();
        migrateIpCache =
                CacheBuilder.newBuilder().maximumSize(cacheSize).expireAfterWrite(duration,
                        TimeUnit.MILLISECONDS).build();
    }

    protected void validateAndProcessIpLearning(String srcInterface, IpAddress srcIP, MacAddress srcMac,
            IpAddress targetIP, BigInteger metadata) {
        List<Adjacency> adjacencies = vpnUtil.getAdjacenciesForVpnInterfaceFromConfig(srcInterface);
        IpVersionChoice srcIpVersion = vpnUtil.getIpVersionFromString(srcIP.stringValue());
        boolean isSrcIpVersionPartOfVpn = false;
        if (adjacencies != null) {
            for (Adjacency adj : adjacencies) {
                IpPrefix ipPrefix = IpPrefixBuilder.getDefaultInstance(adj.getIpAddress());
                // If extra/static route is configured, we should ignore for learning process
                if (NWUtil.isIpAddressInRange(srcIP, ipPrefix)) {
                    return;
                }
                IpVersionChoice currentAdjIpVersion = vpnUtil.getIpVersionFromString(adj.getIpAddress());
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

    protected void processIpLearning(String srcInterface, IpAddress srcIP, MacAddress srcMac, BigInteger metadata,
                                     IpAddress dstIP) {
        if (metadata != null && !Objects.equals(metadata, BigInteger.ZERO)) {
            Optional<List<String>> vpnList = vpnUtil.getVpnHandlingIpv4AssociatedWithInterface(srcInterface);
            if (vpnList.isPresent()) {
                String srcIpToQuery = srcIP.stringValue();
                String destIpToQuery = dstIP.stringValue();
                for (String vpnName : vpnList.get()) {
                    LOG.info("Received ARP/NA for sender MAC {} and sender IP {} via interface {}",
                              srcMac.getValue(), srcIpToQuery, srcInterface);
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
                            LOG.info("ARP/NA Source IP/MAC data modified for IP {} with MAC {} and Port {}",
                                    srcIpToQuery, srcMac, srcInterface);
                            synchronized ((vpnName + srcIpToQuery).intern()) {
                                vpnUtil.createLearntVpnVipToPortEvent(vpnName, srcIpToQuery, destIpToQuery,
                                        oldPortName, oldMac, LearntVpnVipToPortEventAction.Delete, null);
                                putVpnIpToMigrateIpCache(vpnName, srcIpToQuery, srcMac);
                            }
                        }
                    } else if (!isIpInMigrateCache(vpnName, srcIpToQuery)) {
                        learnMacFromIncomingPacket(vpnName, srcInterface, srcIP, srcMac, dstIP);
                    }
                }
            } else {
                LOG.info("IP LEARN NO_RESOLVE: VPN  not configured. Ignoring responding to ARP/NA requests from this"
                        + " Interface {}.", srcInterface);
                return;

            }
        }
    }

    private void learnMacFromIncomingPacket(String vpnName, String srcInterface, IpAddress srcIP, MacAddress srcMac,
            IpAddress dstIP) {
        String srcIpToQuery = srcIP.stringValue();
        String destIpToQuery = dstIP.stringValue();
        synchronized ((vpnName + srcIpToQuery).intern()) {
            vpnUtil.createLearntVpnVipToPortEvent(vpnName, srcIpToQuery, destIpToQuery, srcInterface,
                    srcMac.getValue(), LearntVpnVipToPortEventAction.Add, null);
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
                new BigInteger(String.valueOf(System.currentTimeMillis())));
    }

    private boolean isIpInMigrateCache(String vpnName, String ipToQuery) {
        if (migrateIpCache == null || migrateIpCache.size() == 0) {
            return false;
        }
        Pair<String, String> keyPair = new ImmutablePair<>(vpnName, ipToQuery);
        BigInteger prevTimeStampCached = migrateIpCache.getIfPresent(keyPair);
        if (prevTimeStampCached == null) {
            LOG.debug("IP_MIGRATE_CACHE: there is no IP {} vpnName {} in dirty cache, so learn it",
                    ipToQuery, vpnName);
            return false;
        }
        if (System.currentTimeMillis() > prevTimeStampCached.longValue() + config.getIpLearnTimeout()) {
            LOG.debug("IP_MIGRATE_CACHE: older than timeout value - remove from dirty cache IP {} vpnName {}",
                    ipToQuery, vpnName);
            migrateIpCache.invalidate(keyPair);
            return false;
        }
        LOG.debug("IP_MIGRATE_CACHE: younger than timeout value - ignore learning IP {} vpnName {}",
                ipToQuery, vpnName);
        return true;
    }
}

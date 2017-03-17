/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api.intervpnlink;

import com.google.common.base.Optional;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.cache.CacheUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages some utility caches in order to speed (avoid) reads from MD-SAL.
 * InterVpnLink is something that rarely changes and is frequently queried.
 */
public class InterVpnLinkCache {

    // Cache that maps endpoints with their respective InterVpnLinkComposite
    public static final String ENDPOINT_2_IVPNLINK_CACHE_NAME = "EndpointToInterVpnLinkCache";

    // Cache that maps Vpn UUIDs with their respective InterVpnLinkComposite
    public static final String UUID_2_IVPNLINK_CACHE_NAME = "UuidToInterVpnLinkCache";

    // It maps InterVpnLink names with their corresponding InterVpnLinkComposite.
    public static final String IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME = "NameToInterVpnLinkCache";

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkCache.class);

    ///////////////////////////////////
    //  Initialization / Destruction  //
    ///////////////////////////////////

    public static synchronized void createInterVpnLinkCaches(DataBroker dataBroker) {
        boolean emptyCaches = true;
        if (CacheUtil.getCache(ENDPOINT_2_IVPNLINK_CACHE_NAME) == null) {
            CacheUtil.createCache(ENDPOINT_2_IVPNLINK_CACHE_NAME);
        } else {
            emptyCaches = false;
        }

        if (CacheUtil.getCache(UUID_2_IVPNLINK_CACHE_NAME) == null) {
            CacheUtil.createCache(UUID_2_IVPNLINK_CACHE_NAME);
        } else {
            emptyCaches = false;
        }

        if (CacheUtil.getCache(IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME) == null) {
            CacheUtil.createCache(IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME);
        } else {
            emptyCaches = false;
        }

        if (emptyCaches) {
            initialFeed(dataBroker);
        }
    }

    public static synchronized void destroyCaches() {
        if (CacheUtil.getCache(ENDPOINT_2_IVPNLINK_CACHE_NAME) != null) {
            CacheUtil.destroyCache(ENDPOINT_2_IVPNLINK_CACHE_NAME);
        }

        if (CacheUtil.getCache(UUID_2_IVPNLINK_CACHE_NAME) != null) {
            CacheUtil.destroyCache(UUID_2_IVPNLINK_CACHE_NAME);
        }

        if (CacheUtil.getCache(IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME) != null) {
            CacheUtil.destroyCache(IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME);
        }
    }

    /////////////////////
    //      feeding    //
    /////////////////////

    private static void initialFeed(DataBroker broker) {
        // Read all InterVpnLinks and InterVpnLinkStates from MD-SAL.
        InstanceIdentifier<InterVpnLinks> interVpnLinksIid = InstanceIdentifier.builder(InterVpnLinks.class).build();

        Optional<InterVpnLinks> optIVpnLinksOpData =
                MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, interVpnLinksIid);

        if (!optIVpnLinksOpData.isPresent()) {
            return; // Nothing to be added to cache
        }
        InterVpnLinks interVpnLinks = optIVpnLinksOpData.get();
        for (InterVpnLink interVpnLink : interVpnLinks.getInterVpnLink()) {
            addInterVpnLinkToCaches(interVpnLink);
        }

        // Now the States
        InstanceIdentifier<InterVpnLinkStates> interVpnLinkStateIid =
                InstanceIdentifier.builder(InterVpnLinkStates.class).build();

        Optional<InterVpnLinkStates> optIVpnLinkStateOpData =
                MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, interVpnLinkStateIid);
        if (!optIVpnLinkStateOpData.isPresent()) {
            return;
        }
        InterVpnLinkStates interVpnLinkStates = optIVpnLinkStateOpData.get();
        for (InterVpnLinkState interVpnLinkState : interVpnLinkStates.getInterVpnLinkState()) {
            addInterVpnLinkStateToCaches(interVpnLinkState);
        }
    }

    public static void addInterVpnLinkToCaches(InterVpnLink interVpnLink) {

        LOG.debug("Adding InterVpnLink {} with vpn1=[id={} endpoint={}] and vpn2=[id={}  endpoint={}] ]",
                interVpnLink.getName(), interVpnLink.getFirstEndpoint().getVpnUuid(),
                interVpnLink.getFirstEndpoint().getIpAddress(), interVpnLink.getSecondEndpoint().getVpnUuid(),
                interVpnLink.getSecondEndpoint().getIpAddress());

        InterVpnLinkDataComposite interVpnLinkDataComposite;

        Optional<InterVpnLinkDataComposite> optIVpnLinkComposite =
                getInterVpnLinkByName(interVpnLink.getName());

        if (optIVpnLinkComposite.isPresent()) {
            interVpnLinkDataComposite = optIVpnLinkComposite.get();
            interVpnLinkDataComposite.setInterVpnLinkConfig(interVpnLink);
        } else {
            interVpnLinkDataComposite = new InterVpnLinkDataComposite(interVpnLink);
            addToIVpnLinkNameCache(interVpnLinkDataComposite);
        }

        addToEndpointCache(interVpnLinkDataComposite);
        addToVpnUuidCache(interVpnLinkDataComposite);
    }


    public static void addInterVpnLinkStateToCaches(InterVpnLinkState interVpnLinkState) {

        LOG.debug("Adding InterVpnLinkState {} with vpn1=[{}]  and vpn2=[{}]",
                interVpnLinkState.getInterVpnLinkName(), interVpnLinkState.getFirstEndpointState(),
                interVpnLinkState.getSecondEndpointState());

        Optional<InterVpnLinkDataComposite> optIVpnLink =
                getInterVpnLinkByName(interVpnLinkState.getInterVpnLinkName());

        InterVpnLinkDataComposite interVpnLinkComposite;
        if (optIVpnLink.isPresent()) {
            interVpnLinkComposite = optIVpnLink.get();
            interVpnLinkComposite.setInterVpnLinkState(interVpnLinkState);
        } else {
            interVpnLinkComposite = new InterVpnLinkDataComposite(interVpnLinkState);
            addToIVpnLinkNameCache(interVpnLinkComposite);
        }

        addToEndpointCache(interVpnLinkComposite);
        addToVpnUuidCache(interVpnLinkComposite);
    }

    private static void addToEndpointCache(InterVpnLinkDataComposite interVpnLink) {
        ConcurrentHashMap<String, InterVpnLinkDataComposite> cache =
                (ConcurrentHashMap<String, InterVpnLinkDataComposite>) CacheUtil.getCache(
                        ENDPOINT_2_IVPNLINK_CACHE_NAME);
        if (cache == null) {
            LOG.warn("Cache {} is not ready", ENDPOINT_2_IVPNLINK_CACHE_NAME);
            return;
        }
        if (interVpnLink.getFirstEndpointIpAddr().isPresent()) {
            cache.put(interVpnLink.getFirstEndpointIpAddr().get(), interVpnLink);
        }
        if (interVpnLink.getSecondEndpointIpAddr().isPresent()) {
            cache.put(interVpnLink.getSecondEndpointIpAddr().get(), interVpnLink);
        }
    }

    private static void addToVpnUuidCache(InterVpnLinkDataComposite interVpnLink) {
        ConcurrentHashMap<String, InterVpnLinkDataComposite> cache =
                (ConcurrentHashMap<String, InterVpnLinkDataComposite>) CacheUtil.getCache(UUID_2_IVPNLINK_CACHE_NAME);
        if (cache == null) {
            LOG.warn("Cache {} is not ready", UUID_2_IVPNLINK_CACHE_NAME);
            return;
        }
        if (interVpnLink.getFirstEndpointVpnUuid().isPresent()) {
            cache.put(interVpnLink.getFirstEndpointVpnUuid().get(), interVpnLink);
        }
        if (interVpnLink.getSecondEndpointVpnUuid().isPresent()) {
            cache.put(interVpnLink.getSecondEndpointVpnUuid().get(), interVpnLink);
        }
    }

    private static void addToIVpnLinkNameCache(InterVpnLinkDataComposite interVpnLink) {
        ConcurrentHashMap<String, InterVpnLinkDataComposite> cache =
                (ConcurrentHashMap<String, InterVpnLinkDataComposite>) CacheUtil.getCache(
                        IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME);
        if (cache == null) {
            LOG.warn("Cache {} is not ready", IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME);
            return;
        }
        cache.put(interVpnLink.getInterVpnLinkName(), interVpnLink);
        if (interVpnLink.getSecondEndpointIpAddr().isPresent()) {
            cache.put(interVpnLink.getSecondEndpointIpAddr().get(), interVpnLink);
        }
    }

    public static void removeInterVpnLinkFromCache(InterVpnLink interVpnLink) {
        ConcurrentHashMap<String, InterVpnLink> cache =
                (ConcurrentHashMap<String, InterVpnLink>) CacheUtil.getCache(ENDPOINT_2_IVPNLINK_CACHE_NAME);
        if (cache != null) {
            cache.remove(interVpnLink.getFirstEndpoint().getIpAddress().getValue());
            cache.remove(interVpnLink.getSecondEndpoint().getIpAddress().getValue());
        } else {
            LOG.warn("Cache {} is not ready", ENDPOINT_2_IVPNLINK_CACHE_NAME);
        }

        ConcurrentHashMap<String, InterVpnLink> cache2 =
                (ConcurrentHashMap<String, InterVpnLink>) CacheUtil.getCache(UUID_2_IVPNLINK_CACHE_NAME);
        if (cache2 != null) {
            cache2.remove(interVpnLink.getFirstEndpoint().getVpnUuid().getValue());
            cache2.remove(interVpnLink.getSecondEndpoint().getVpnUuid().getValue());
        } else {
            LOG.warn("Cache {} is not ready", UUID_2_IVPNLINK_CACHE_NAME);
        }
    }


    public static void removeInterVpnLinkStateFromCache(InterVpnLinkState interVpnLinkState) {
        Optional<InterVpnLinkDataComposite> optIVpnLinkComposite =
                getInterVpnLinkByName(interVpnLinkState.getInterVpnLinkName());

        if (optIVpnLinkComposite.isPresent()) {
            InterVpnLinkDataComposite interVpnLinkComposite = optIVpnLinkComposite.get();
            removeFromEndpointIpAddressCache(interVpnLinkComposite);
            removeFromVpnUuidCache(interVpnLinkComposite);
            removeFromInterVpnLinkNameCache(interVpnLinkComposite);
        }
    }

    private static void removeFromInterVpnLinkNameCache(InterVpnLinkDataComposite interVpnLinkComposite) {
        ConcurrentHashMap<String, InterVpnLinkDataComposite> cache =
                (ConcurrentHashMap<String, InterVpnLinkDataComposite>) CacheUtil.getCache(
                        IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME);
        if (cache != null) {
            cache.remove(interVpnLinkComposite.getInterVpnLinkName());
        } else {
            LOG.warn("removeFromInterVpnLinkNameCache: Cache {} is not ready", IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME);
        }
    }


    private static void removeFromVpnUuidCache(InterVpnLinkDataComposite interVpnLinkComposite) {
        ConcurrentHashMap<String, InterVpnLink> cache =
                (ConcurrentHashMap<String, InterVpnLink>) CacheUtil.getCache(UUID_2_IVPNLINK_CACHE_NAME);
        if (cache == null) {
            LOG.warn("removeFromVpnUuidCache: Cache {} is not ready", UUID_2_IVPNLINK_CACHE_NAME);
            return;
        }
        Optional<String> opt1stEndpointUuid = interVpnLinkComposite.getFirstEndpointVpnUuid();
        if (opt1stEndpointUuid.isPresent()) {
            cache.remove(opt1stEndpointUuid.get());
        }
        Optional<String> opt2ndEndpointUuid = interVpnLinkComposite.getSecondEndpointVpnUuid();
        cache.remove(opt2ndEndpointUuid.get());
    }


    private static void removeFromEndpointIpAddressCache(InterVpnLinkDataComposite interVpnLinkComposite) {
        ConcurrentHashMap<String, InterVpnLink> cache =
                (ConcurrentHashMap<String, InterVpnLink>) CacheUtil.getCache(ENDPOINT_2_IVPNLINK_CACHE_NAME);
        if (cache == null) {
            LOG.warn("removeFromVpnUuidCache: Cache {} is not ready", ENDPOINT_2_IVPNLINK_CACHE_NAME);
            return;
        }
        Optional<String> opt1stEndpointIpAddr = interVpnLinkComposite.getFirstEndpointIpAddr();
        if (opt1stEndpointIpAddr.isPresent()) {
            cache.remove(opt1stEndpointIpAddr.get());
        }
        Optional<String> opt2ndEndpointIpAddr = interVpnLinkComposite.getSecondEndpointIpAddr();
        cache.remove(opt2ndEndpointIpAddr.get());
    }


    /////////////////////
    //  Cache Usage    //
    /////////////////////

    public static Optional<InterVpnLinkDataComposite> getInterVpnLinkByName(String interVpnLinkName) {
        ConcurrentHashMap<String, InterVpnLinkDataComposite> cache =
                (ConcurrentHashMap<String, InterVpnLinkDataComposite>) CacheUtil.getCache(
                        IVPNLINK_NAME_2_IVPNLINK_CACHE_NAME);
        return (cache == null) ? Optional.absent()
                : Optional.fromNullable(cache.get(interVpnLinkName));
    }

    public static Optional<InterVpnLinkDataComposite> getInterVpnLinkByEndpoint(String endpointIp) {
        LOG.trace("Checking if {} is configured as an InterVpnLink endpoint", endpointIp);
        ConcurrentHashMap<String, InterVpnLinkDataComposite> cache =
                (ConcurrentHashMap<String, InterVpnLinkDataComposite>) CacheUtil.getCache(
                        ENDPOINT_2_IVPNLINK_CACHE_NAME);
        return (cache == null) ? Optional.absent()
                : Optional.fromNullable(cache.get(endpointIp));
    }

    public static Optional<InterVpnLinkDataComposite> getInterVpnLinkByVpnId(String vpnId) {
        ConcurrentHashMap<String, InterVpnLinkDataComposite> cache =
                (ConcurrentHashMap<String, InterVpnLinkDataComposite>) CacheUtil.getCache(UUID_2_IVPNLINK_CACHE_NAME);
        return (cache == null) ? Optional.absent() : Optional.fromNullable(cache.get(vpnId));
    }

    public static List<InterVpnLinkDataComposite> getAllInterVpnLinks() {
        ConcurrentHashMap<String, InterVpnLinkDataComposite> cache =
                (ConcurrentHashMap<String, InterVpnLinkDataComposite>) CacheUtil.getCache(UUID_2_IVPNLINK_CACHE_NAME);
        return (cache == null) ? Collections.emptyList()
                : Collections.list(cache.elements());
    }

}

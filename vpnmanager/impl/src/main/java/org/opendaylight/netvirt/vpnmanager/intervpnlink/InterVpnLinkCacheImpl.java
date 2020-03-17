/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of InterVpnLinkCache.
 */
@Singleton
public class InterVpnLinkCacheImpl implements InterVpnLinkCache {
    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkCacheImpl.class);

    // Cache that maps endpoints with their respective InterVpnLinkComposite
    private final ConcurrentMap<String, InterVpnLinkDataComposite> endpointToInterVpnLinkCache =
            new ConcurrentHashMap<>();

    // Cache that maps Vpn UUIDs with their respective InterVpnLinkComposite
    private final ConcurrentMap<String, InterVpnLinkDataComposite> uuidToInterVpnLinkCache =
            new ConcurrentHashMap<>();

    // Cache that maps InterVpnLink names with their corresponding InterVpnLinkComposite.
    private final ConcurrentMap<String, InterVpnLinkDataComposite> nameToInterVpnLinkCache =
            new ConcurrentHashMap<>();

    private final DataBroker dataBroker;

    @Inject
    public InterVpnLinkCacheImpl(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @PostConstruct
    public void initialFeed() {
        // Read all InterVpnLinks and InterVpnLinkStates from MD-SAL.
        InstanceIdentifier<InterVpnLinks> interVpnLinksIid = InstanceIdentifier.builder(InterVpnLinks.class).build();

        Optional<InterVpnLinks> optIVpnLinksOpData =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, interVpnLinksIid);

        if (!optIVpnLinksOpData.isPresent()) {
            return; // Nothing to be added to cache
        }
        InterVpnLinks interVpnLinks = optIVpnLinksOpData.get();
        for (InterVpnLink interVpnLink : interVpnLinks.nonnullInterVpnLink()) {
            addInterVpnLinkToCaches(interVpnLink);
        }

        // Now the States
        InstanceIdentifier<InterVpnLinkStates> interVpnLinkStateIid =
                InstanceIdentifier.builder(InterVpnLinkStates.class).build();

        Optional<InterVpnLinkStates> optIVpnLinkStateOpData =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, interVpnLinkStateIid);
        if (!optIVpnLinkStateOpData.isPresent()) {
            return;
        }
        InterVpnLinkStates interVpnLinkStates = optIVpnLinkStateOpData.get();
        for (InterVpnLinkState interVpnLinkState : interVpnLinkStates.nonnullInterVpnLinkState()) {
            addInterVpnLinkStateToCaches(interVpnLinkState);
        }
    }

    @Override
    public void addInterVpnLinkToCaches(InterVpnLink interVpnLink) {

        String ivlName = interVpnLink.getName();
        LOG.debug("Adding InterVpnLink {} with vpn1=[id={} endpoint={}] and vpn2=[id={}  endpoint={}] ]",
                  ivlName, interVpnLink.getFirstEndpoint().getVpnUuid(),
                interVpnLink.getFirstEndpoint().getIpAddress(), interVpnLink.getSecondEndpoint().getVpnUuid(),
                interVpnLink.getSecondEndpoint().getIpAddress());

        InterVpnLinkDataComposite interVpnLinkDataComposite = getInterVpnLinkByName(ivlName).orNull();
        if (interVpnLinkDataComposite != null) {
            interVpnLinkDataComposite.setInterVpnLinkConfig(interVpnLink);
        } else {
            interVpnLinkDataComposite = new InterVpnLinkDataComposite(interVpnLink);
            addToIVpnLinkNameCache(interVpnLinkDataComposite);
        }

        addToEndpointCache(interVpnLinkDataComposite);
        addToVpnUuidCache(interVpnLinkDataComposite);
    }

    @Override
    public void addInterVpnLinkStateToCaches(InterVpnLinkState interVpnLinkState) {

        String ivlName = interVpnLinkState.getInterVpnLinkName();
        LOG.debug("Adding InterVpnLinkState {} with vpn1=[{}]  and vpn2=[{}]",
                  ivlName, interVpnLinkState.getFirstEndpointState(), interVpnLinkState.getSecondEndpointState());

        InterVpnLinkDataComposite ivl = getInterVpnLinkByName(ivlName).orNull();
        if (ivl != null) {
            ivl.setInterVpnLinkState(interVpnLinkState);
        } else {
            ivl = new InterVpnLinkDataComposite(interVpnLinkState);
            addToIVpnLinkNameCache(ivl);
        }

        addToEndpointCache(ivl);
        addToVpnUuidCache(ivl);
    }

    private void addToEndpointCache(InterVpnLinkDataComposite interVpnLink) {
        safePut(endpointToInterVpnLinkCache, interVpnLink.getFirstEndpointIpAddr().orNull(), interVpnLink);
        safePut(endpointToInterVpnLinkCache, interVpnLink.getSecondEndpointIpAddr().orNull(), interVpnLink);
    }

    private void addToVpnUuidCache(InterVpnLinkDataComposite interVpnLink) {
        safePut(uuidToInterVpnLinkCache, interVpnLink.getFirstEndpointVpnUuid().orNull(), interVpnLink);
        safePut(uuidToInterVpnLinkCache, interVpnLink.getSecondEndpointVpnUuid().orNull(), interVpnLink);
    }

    private void addToIVpnLinkNameCache(InterVpnLinkDataComposite interVpnLink) {
        safePut(nameToInterVpnLinkCache, interVpnLink.getInterVpnLinkName(), interVpnLink);
    }

    @Override
    public void removeInterVpnLinkFromCache(InterVpnLink interVpnLink) {
        safeRemove(endpointToInterVpnLinkCache, interVpnLink.getFirstEndpoint().getIpAddress().getValue());
        safeRemove(endpointToInterVpnLinkCache, interVpnLink.getSecondEndpoint().getIpAddress().getValue());

        safeRemove(uuidToInterVpnLinkCache, interVpnLink.getFirstEndpoint().getVpnUuid().getValue());
        safeRemove(uuidToInterVpnLinkCache, interVpnLink.getSecondEndpoint().getVpnUuid().getValue());
    }


    @Override
    public void removeInterVpnLinkStateFromCache(InterVpnLinkState interVpnLinkState) {
        Optional<InterVpnLinkDataComposite> optIVpnLinkComposite =
                getInterVpnLinkByName(interVpnLinkState.getInterVpnLinkName());

        if (optIVpnLinkComposite.isPresent()) {
            InterVpnLinkDataComposite interVpnLinkComposite = optIVpnLinkComposite.get();
            removeFromEndpointIpAddressCache(interVpnLinkComposite);
            removeFromVpnUuidCache(interVpnLinkComposite);
            removeFromInterVpnLinkNameCache(interVpnLinkComposite);
        }
    }

    private void removeFromInterVpnLinkNameCache(InterVpnLinkDataComposite interVpnLinkComposite) {
        safeRemove(nameToInterVpnLinkCache, interVpnLinkComposite.getInterVpnLinkName());
    }


    private void removeFromVpnUuidCache(InterVpnLinkDataComposite interVpnLinkComposite) {
        safeRemove(uuidToInterVpnLinkCache, interVpnLinkComposite.getFirstEndpointVpnUuid().orNull());
        safeRemove(uuidToInterVpnLinkCache, interVpnLinkComposite.getSecondEndpointVpnUuid().orNull());
    }


    private void removeFromEndpointIpAddressCache(InterVpnLinkDataComposite interVpnLinkComposite) {
        safeRemove(endpointToInterVpnLinkCache, interVpnLinkComposite.getFirstEndpointIpAddr().orNull());
        safeRemove(endpointToInterVpnLinkCache, interVpnLinkComposite.getSecondEndpointIpAddr().orNull());
    }

    @Override
    public Optional<InterVpnLinkDataComposite> getInterVpnLinkByName(String interVpnLinkName) {
        return Optional.fromNullable(safeGet(nameToInterVpnLinkCache, interVpnLinkName));
    }

    @Override
    public Optional<InterVpnLinkDataComposite> getInterVpnLinkByEndpoint(String endpointIp) {
        LOG.trace("Checking if {} is configured as an InterVpnLink endpoint", endpointIp);
        return Optional.fromNullable(safeGet(endpointToInterVpnLinkCache, endpointIp));
    }

    @Override
    public Optional<InterVpnLinkDataComposite> getInterVpnLinkByVpnId(String vpnId) {
        return Optional.fromNullable(safeGet(uuidToInterVpnLinkCache, vpnId));
    }

    @Override
    public List<InterVpnLinkDataComposite> getAllInterVpnLinks() {
        return ImmutableList.copyOf(nameToInterVpnLinkCache.values());
    }

    private <T> void safeRemove(ConcurrentMap<T, ?> fromMap, @Nullable T key) {
        if (key != null) {
            fromMap.remove(key);
        }
    }

    @Nullable
    private <K, V> V safeGet(ConcurrentMap<K, V> fromMap, @Nullable K key) {
        return key != null ? fromMap.get(key) : null;
    }

    private <K, V> void safePut(ConcurrentMap<K, V> toMap, @Nullable K key, V value) {
        if (key != null) {
            toMap.put(key, value);
        }
    }
}

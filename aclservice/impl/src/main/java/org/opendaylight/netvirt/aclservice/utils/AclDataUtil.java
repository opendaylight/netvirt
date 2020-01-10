/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netvirt.aclservice.api.utils.AclDataCache;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;

@Singleton
public class AclDataUtil implements AclDataCache {

    private final ConcurrentMap<String, Acl> aclMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, ConcurrentMap<String, AclInterface>> aclInterfaceMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, Set<Uuid>> ingressRemoteAclIdMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, Set<Uuid>> egressRemoteAclIdMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> aclTagMap = new ConcurrentHashMap<>();

    /**
     * Adds the acl.
     *
     * @param acl the acl
     */
    public void addAcl(Acl acl) {
        this.aclMap.put(acl.getAclName(), acl);
    }

    /**
     * Removes the acl.
     *
     * @param aclName the acl name
     * @return the acl
     */
    public Acl removeAcl(String aclName) {
        return this.aclMap.remove(aclName);
    }

    /**
     * Gets the acl.
     *
     * @param aclName the acl name
     * @return the acl
     */
    @Override
    public Acl getAcl(String aclName) {
        return this.aclMap.get(aclName);
    }

    public void addOrUpdateAclInterfaceMap(List<Uuid> aclList, AclInterface port) {
        for (Uuid acl : aclList) {
            aclInterfaceMap.computeIfAbsent(acl, key -> new ConcurrentHashMap<>()).put(port.getInterfaceId(), port);
        }
    }

    public void removeAclInterfaceMap(List<Uuid> aclList, AclInterface port) {
        for (Uuid acl : aclList) {
            removeAclInterfaceMap(acl, port);
        }
    }

    public void removeAclInterfaceMap(Uuid acl, AclInterface port) {
        ConcurrentMap<String, AclInterface> interfaceMap = aclInterfaceMap.get(acl);
        if (interfaceMap != null) {
            interfaceMap.remove(port.getInterfaceId());
        }
    }

    @Override
    @NonNull
    public Collection<AclInterface> getInterfaceList(Uuid acl) {
        final ConcurrentMap<String, AclInterface> interfaceMap = aclInterfaceMap.get(acl);
        return interfaceMap != null ? interfaceMap.values() : Collections.emptySet();
    }

    @SuppressWarnings("checkstyle:JavadocParagraph")
    /**
     * Gets set of ACL interfaces per ACL (in a map) for the specified remote ACL IDs.
     *
     * @param remoteAclIdList List of remote ACL Ids
     * @param direction the direction
     * @return set of ACL interfaces per ACL (in a map) for the specified remote ACL IDs.
     *         Format: ConcurrentMap<<Remote-ACL-ID>, Map<<ACL-ID>, Set<AclInterface>>>
     */
    public ConcurrentMap<Uuid, Map<String, Set<AclInterface>>> getRemoteAclInterfaces(List<Uuid> remoteAclIdList,
            Class<? extends DirectionBase> direction) {
        ConcurrentMap<Uuid, Map<String, Set<AclInterface>>> mapOfAclWithInterfacesList = new ConcurrentHashMap<>();
        for (Uuid remoteAclId : remoteAclIdList) {
            Map<String, Set<AclInterface>> mapOfAclWithInterfaces = getRemoteAclInterfaces(remoteAclId, direction);
            if (mapOfAclWithInterfaces != null) {
                mapOfAclWithInterfacesList.put(remoteAclId, mapOfAclWithInterfaces);
            }
        }
        return mapOfAclWithInterfacesList;
    }

    /**
     * Gets the set of ACL interfaces per ACL (in a map) which has specified
     * remote ACL ID.
     *
     * @param remoteAclId the remote acl id
     * @param direction the direction
     * @return the set of ACL interfaces per ACL (in a map) which has specified
     *         remote ACL ID.
     */
    @Nullable
    public Map<String, Set<AclInterface>> getRemoteAclInterfaces(Uuid remoteAclId,
            Class<? extends DirectionBase> direction) {
        Collection<Uuid> remoteAclList = getRemoteAcl(remoteAclId, direction);
        if (remoteAclList == null) {
            return null;
        }

        Map<String, Set<AclInterface>> mapOfAclWithInterfaces = new HashMap<>();
        for (Uuid acl : remoteAclList) {
            Collection<AclInterface> interfaces = getInterfaceList(acl);
            if (!interfaces.isEmpty()) {
                Set<AclInterface> interfaceSet = new HashSet<>(interfaces);
                mapOfAclWithInterfaces.put(acl.getValue(), interfaceSet);
            }
        }
        return mapOfAclWithInterfaces;
    }

    public void addRemoteAclId(Uuid remoteAclId, Uuid aclId, Class<? extends DirectionBase> direction) {
        getRemoteAclIdMap(direction).computeIfAbsent(remoteAclId, key -> ConcurrentHashMap.newKeySet()).add(aclId);
    }

    public void removeRemoteAclId(Uuid remoteAclId, Uuid aclId, Class<? extends DirectionBase> direction) {
        Set<Uuid> aclList = getRemoteAclIdMap(direction).get(remoteAclId);
        if (aclList != null) {
            aclList.remove(aclId);
        }
    }

    @Override
    public Collection<Uuid> getRemoteAcl(Uuid remoteAclId, Class<? extends DirectionBase> direction) {
        return getRemoteAclIdMap(direction).get(remoteAclId);
    }

    /**
     * Gets the set of ACL interfaces per ACL (in a map) which has remote ACL.
     *
     * @param direction the direction
     * @return the set of ACL interfaces per ACL (in a map) which has remote ACL.
     */
    public Map<String, Set<AclInterface>> getAllRemoteAclInterfaces(Class<? extends DirectionBase> direction) {
        Map<String, Set<AclInterface>> mapOfAclWithInterfaces = new HashMap<>();
        for (Uuid remoteAcl : getRemoteAclIdMap(direction).keySet()) {
            Map<String, Set<AclInterface>> map = getRemoteAclInterfaces(remoteAcl, direction);
            if (map != null) {
                mapOfAclWithInterfaces.putAll(map);
            }
        }

        return mapOfAclWithInterfaces;
    }

    /**
     * Adds the ACL tag to the cache.
     *
     * @param aclName the ACL name
     * @param aclTag the ACL tag
     */
    public void addAclTag(final String aclName, final Integer aclTag) {
        this.aclTagMap.put(aclName, aclTag);
    }

    /**
     * Removes the acl tag from the cache.
     *
     * @param aclName the acl name
     * @return the previous value associated with key, or null if there was no
     *         mapping for key.
     */
    public Integer removeAclTag(final String aclName) {
        return this.aclTagMap.remove(aclName);
    }

    /**
     * Gets the acl tag from the cache.
     *
     * @param aclName the acl name
     * @return the acl tag
     */
    @Override
    public Integer getAclTag(final String aclName) {
        return this.aclTagMap.get(aclName);
    }

    /**
     * Checks if DPN has acl interface associated with it.
     *
     * @param dpnId the datapath ID of DPN
     * @return true if DPN is associated with Acl interface, else false
     */
    public boolean doesDpnHaveAclInterface(BigInteger dpnId) {
        return aclInterfaceMap.values().stream().anyMatch(map -> map.values().stream()
                .anyMatch(aclInterface -> aclInterface.getDpId().equals(dpnId)));
    }

    @Override
    public Map<Uuid, Collection<AclInterface>> getAclInterfaceMap() {
        Builder<Uuid, Collection<AclInterface>> builder = ImmutableMap.builder();
        for (Entry<Uuid, ConcurrentMap<String, AclInterface>> entry: aclInterfaceMap.entrySet()) {
            builder.put(entry.getKey(), entry.getValue().values());
        }

        return builder.build();
    }

    private ConcurrentMap<Uuid, Set<Uuid>> getRemoteAclIdMap(Class<? extends DirectionBase> direction) {
        return DirectionEgress.class.equals(direction) ? egressRemoteAclIdMap : ingressRemoteAclIdMap;
    }

    @Override
    public Map<Uuid, Collection<Uuid>> getEgressRemoteAclIdMap() {
        return ImmutableMap.copyOf(egressRemoteAclIdMap);
    }

    @Override
    public Map<Uuid, Collection<Uuid>> getIngressRemoteAclIdMap() {
        return ImmutableMap.copyOf(ingressRemoteAclIdMap);
    }

    @Override
    public Map<String, Integer> getAclTagMap() {
        return ImmutableMap.copyOf(aclTagMap);
    }

    @Override
    public Map<String, Acl> getAclMap() {
        return ImmutableMap.copyOf(aclMap);
    }
}

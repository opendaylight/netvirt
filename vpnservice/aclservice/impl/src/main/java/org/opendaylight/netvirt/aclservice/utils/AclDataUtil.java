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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Singleton;
import org.opendaylight.netvirt.aclservice.api.utils.AclDataCache;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;


@Singleton
public class AclDataUtil implements AclDataCache {

    private final ConcurrentMap<Uuid, ConcurrentMap<String, AclInterface>> aclInterfaceMap = new ConcurrentHashMap<>();
    private final Map<Uuid, Set<Uuid>> remoteAclIdMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> aclTagMap = new ConcurrentHashMap<>();

    public void addAclInterfaceMap(List<Uuid> aclList, AclInterface port) {
        for (Uuid acl : aclList) {
            addAclInterface(acl, port);
        }
    }

    private void addAclInterface(Uuid acl, AclInterface port) {
        aclInterfaceMap.computeIfAbsent(acl, key -> new ConcurrentHashMap<>())
                .putIfAbsent(port.getInterfaceId(), port);
    }

    public void addOrUpdateAclInterfaceMap(List<Uuid> aclList, AclInterface port) {
        for (Uuid acl : aclList) {
            aclInterfaceMap.computeIfAbsent(acl, key -> new ConcurrentHashMap<>()).put(port.getInterfaceId(), port);
        }
    }

    public void removeAclInterfaceMap(List<Uuid> aclList, AclInterface port) {
        for (Uuid acl : aclList) {
            ConcurrentMap<String, AclInterface> interfaceMap = aclInterfaceMap.get(acl);
            if (interfaceMap != null) {
                interfaceMap.remove(port.getInterfaceId());
            }
        }
    }

    @Override
    public Collection<AclInterface> getInterfaceList(Uuid acl) {
        final ConcurrentMap<String, AclInterface> interfaceMap = aclInterfaceMap.get(acl);
        return interfaceMap != null ? interfaceMap.values() : null;
    }

    /**
     * Gets the set of ACL interfaces per ACL (in a map) which has specified
     * remote ACL ID.
     *
     * @param remoteAclId the remote acl id
     * @return the set of ACL interfaces per ACL (in a map) which has specified
     *         remote ACL ID.
     */
    public Map<String, Set<AclInterface>> getRemoteAclInterfaces(Uuid remoteAclId) {
        Collection<Uuid> remoteAclList = getRemoteAcl(remoteAclId);
        if (remoteAclList == null) {
            return null;
        }

        Map<String, Set<AclInterface>> mapOfAclWithInterfaces = new HashMap<>();
        for (Uuid acl : remoteAclList) {
            Set<AclInterface> interfaceSet = new HashSet<>();
            Collection<AclInterface> interfaces = getInterfaceList(acl);
            if (interfaces != null && !interfaces.isEmpty()) {
                interfaceSet.addAll(interfaces);
                mapOfAclWithInterfaces.put(acl.getValue(), interfaceSet);
            }
        }
        return mapOfAclWithInterfaces;
    }

    public void addRemoteAclId(Uuid remoteAclId, Uuid aclId) {
        remoteAclIdMap.computeIfAbsent(remoteAclId, key -> ConcurrentHashMap.newKeySet()).add(aclId);
    }

    public void removeRemoteAclId(Uuid remoteAclId, Uuid aclId) {
        Set<Uuid> aclList = remoteAclIdMap.get(remoteAclId);
        if (aclList != null) {
            aclList.remove(aclId);
        }
    }

    @Override
    public Collection<Uuid> getRemoteAcl(Uuid remoteAclId) {
        return remoteAclIdMap.get(remoteAclId);
    }

    /**
     * Gets the set of ACL interfaces per ACL (in a map) which has remote ACL.
     *
     * @return the set of ACL interfaces per ACL (in a map) which has remote ACL.
     */
    public Map<String, Set<AclInterface>> getAllRemoteAclInterfaces() {
        Map<String, Set<AclInterface>> mapOfAclWithInterfaces = new HashMap<>();
        for (Uuid remoteAcl : remoteAclIdMap.keySet()) {
            Map<String, Set<AclInterface>> map = getRemoteAclInterfaces(remoteAcl);
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
     * @param key the key
     * @return the previous value associated with key, or null if there was no
     *         mapping for key.
     */
    public Integer removeAclTag(final String key) {
        return this.aclTagMap.remove(key);
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

    @Override
    public Map<Uuid, Collection<Uuid>> getRemoteAclIdMap() {
        return ImmutableMap.copyOf(remoteAclIdMap);
    }

    @Override
    public Map<String, Integer> getAclTagMap() {
        return ImmutableMap.copyOf(aclTagMap);
    }
}

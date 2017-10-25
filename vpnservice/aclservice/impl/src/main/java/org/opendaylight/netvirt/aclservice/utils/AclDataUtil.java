/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Singleton;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;


@Singleton
public class AclDataUtil {

    private final ConcurrentMap<Uuid, ConcurrentMap<String, AclInterface>> aclInterfaceMap = new ConcurrentHashMap<>();
    private final Map<Uuid, Set<Uuid>> remoteAclIdMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> aclFlowPriorityMap = new ConcurrentHashMap<>();

    public void addAclInterfaceMap(List<Uuid> aclList, AclInterface port) {
        for (Uuid acl : aclList) {
            aclInterfaceMap.computeIfAbsent(acl, key -> new ConcurrentHashMap<>())
                    .putIfAbsent(port.getInterfaceId(), port);
        }
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
     * Adds the acl flow priority to the cache.
     *
     * @param aclName the acl name
     * @param flowPriority the flow priority
     */
    public void addAclFlowPriority(final String aclName, final Integer flowPriority) {
        this.aclFlowPriorityMap.put(aclName, flowPriority);
    }

    /**
     * Removes the acl flow priority from the cache.
     *
     * @param key the key
     * @return the previous value associated with key, or null if there was no
     *         mapping for key.
     */
    public Integer removeAclFlowPriority(final String key) {
        return this.aclFlowPriorityMap.remove(key);
    }

    /**
     * Gets the acl flow priority from the cache.
     *
     * @param aclName the acl name
     * @return the acl flow priority
     */
    public Integer getAclFlowPriority(final String aclName) {
        Integer priority = this.aclFlowPriorityMap.get(aclName);
        if (priority == null) {
            // Set to default value
            priority = AclConstants.PROTO_MATCH_PRIORITY;
        }
        return priority;
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
}

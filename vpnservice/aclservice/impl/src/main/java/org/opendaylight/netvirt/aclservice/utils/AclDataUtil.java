/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public final class AclDataUtil {

    private static Map<Uuid, List<AclInterface>> aclInterfaceMap = new ConcurrentHashMap<>();
    private static Map<Uuid, List<Uuid>> remoteAclIdMap = new ConcurrentHashMap<>();

    private AclDataUtil() {
    }

    public static synchronized void addAclInterfaceMap(List<Uuid> aclList, AclInterface port) {
        for (Uuid acl : aclList) {
            List<AclInterface> interfaceList = aclInterfaceMap.get(acl);
            if (interfaceList == null) {
                interfaceList = new ArrayList<>();
                interfaceList.add(port);
                aclInterfaceMap.put(acl, interfaceList);
            } else {
                interfaceList.add(port);
            }
        }
    }

    public static synchronized void removeAclInterfaceMap(List<Uuid> aclList, AclInterface port) {
        for (Uuid acl : aclList) {
            List<AclInterface> interfaceList = aclInterfaceMap.get(acl);
            if (interfaceList != null) {
                interfaceList.remove(port);
            }
        }
    }

    public static List<AclInterface> getInterfaceList(Uuid acl) {
        return aclInterfaceMap.get(acl);
    }

    public static Set<AclInterface> getRemoteAclInterfaces(Uuid remoteAclId) {
        List<Uuid> remoteAclList = getRemoteAcl(remoteAclId);
        if (remoteAclList == null) {
            return null;
        }
        Set<AclInterface> interfaceSet = new HashSet<>();
        for (Uuid acl: remoteAclList) {
            List<AclInterface> interfaces = getInterfaceList(acl);
            if (interfaces != null && !interfaces.isEmpty()) {
                interfaceSet.addAll(interfaces);
            }
        }
        return interfaceSet;
    }

    public static synchronized void addRemoteAclId(Uuid remoteAclId, Uuid aclId) {
        List<Uuid> aclList = remoteAclIdMap.get(remoteAclId);
        if (aclList == null) {
            aclList = new ArrayList<>();
            aclList.add(aclId);
            remoteAclIdMap.put(remoteAclId, aclList);
        } else {
            aclList.add(aclId);
        }
    }

    public static synchronized void removeRemoteAclId(Uuid remoteAclId, Uuid aclId) {
        List<Uuid> aclList = remoteAclIdMap.get(remoteAclId);
        if (aclList != null) {
            aclList.remove(aclId);
        }
    }

    public static List<Uuid> getRemoteAcl(Uuid remoteAclId) {
        return remoteAclIdMap.get(remoteAclId);
    }

    // ONLY FOR USE BY TESTS
    // TODO clean up code to not use static anymore, and remove this!!
    public static void clearCaches() {
        aclInterfaceMap.clear();
        remoteAclIdMap.clear();
    }
}

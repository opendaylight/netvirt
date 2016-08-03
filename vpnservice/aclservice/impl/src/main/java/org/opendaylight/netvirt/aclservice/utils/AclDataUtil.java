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
    private static Map<Uuid, Set<String>> remoteAclInterfaceMap = new ConcurrentHashMap<>();

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

    public static void updateRemoteAclInterfaceMap(Uuid remoteAcl, String port, boolean isAdd) {
        if (isAdd) {
            addRemoteAclInterfaceMap(remoteAcl, port);
        } else {
            removeRemoteAclInterfaceMap(remoteAcl, port);
        }
    }

    public static synchronized void addRemoteAclInterfaceMap(Uuid remoteAcl, String port) {
        Set<String> interfaceSet = remoteAclInterfaceMap.get(remoteAcl);
        if (interfaceSet == null) {
            interfaceSet = new HashSet<>();
            interfaceSet.add(port);
            remoteAclInterfaceMap.put(remoteAcl, interfaceSet);
        } else {
            interfaceSet.add(port);
        }
    }

    public static synchronized void removeRemoteAclInterfaceMap(Uuid remoteAcl, String port) {
        Set<String> interfaceList = remoteAclInterfaceMap.get(remoteAcl);
        if (interfaceList != null) {
            interfaceList.remove(port);
        }
    }

    public static Set<String> getRemoteAclInterfaces(Uuid remoteAcl) {
        return remoteAclInterfaceMap.get(remoteAcl);
    }

}

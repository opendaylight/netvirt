/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public final class AclDataUtil {

    private static Map<Uuid, List<Interface>> aclInterfaceMap = new ConcurrentHashMap<>();

    private AclDataUtil() {

    }

    public static void addAclInterfaceMap(List<Uuid> aclList, Interface port) {
        for (Uuid acl : aclList) {
            List<Interface> interfaceList = aclInterfaceMap.get(acl.getValue());
            if (interfaceList == null) {
                interfaceList = new ArrayList<>();
                interfaceList.add(port);
                aclInterfaceMap.put(acl, interfaceList);
            } else {
                interfaceList.add(port);
            }
        }
    }

    public static void removeAclInterfaceMap(List<Uuid> aclList, Interface port) {
        for (Uuid acl : aclList) {
            List<Interface> interfaceList = aclInterfaceMap.get(acl.getValue());
            if (interfaceList != null) {
                interfaceList.remove(port);
            }
        }
    }

    public static List<Interface> getInterfaceList(Uuid acl) {
        return aclInterfaceMap.get(acl);
    }

}

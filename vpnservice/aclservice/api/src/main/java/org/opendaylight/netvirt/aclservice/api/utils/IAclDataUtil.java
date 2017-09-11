/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.api.utils;

import java.util.List;
import java.util.Map;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public interface IAclDataUtil {


    List<AclInterface> getInterfaceList(Uuid acl);

    List<Uuid> getRemoteAcl(Uuid remoteAclId);

    Integer getAclFlowPriority(String aclName);

    Map<Uuid, List<AclInterface>> getAclInterfaceMap();

    Map<Uuid, List<Uuid>> getRemoteAclIdMap();

    Map<String, Integer> getAclFlowPriorityMap();

}

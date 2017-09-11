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
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public interface IAclDataUtil {


    public List<AclInterface> getInterfaceList(Uuid acl);

    public List<Uuid> getRemoteAcl(Uuid remoteAclId);

    public Integer getAclFlowPriority(final String aclName);

    public Map<Uuid, List<AclInterface>> getAclInterfaceMap();

    public Map<Uuid, List<Uuid>> getRemoteAclIdMap();

    public Map<String, Integer> getAclFlowPriorityMap();

}

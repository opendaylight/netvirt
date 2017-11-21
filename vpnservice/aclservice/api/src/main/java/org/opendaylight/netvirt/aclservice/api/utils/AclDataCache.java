/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.api.utils;

import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public interface AclDataCache {

    @Nullable
    Collection<AclInterface> getInterfaceList(Uuid acl);

    @Nullable
    Collection<Uuid> getRemoteAcl(Uuid remoteAclId);

    @Nonnull
    Integer getAclFlowPriority(String aclName);

    @Nonnull
    Map<Uuid, Collection<AclInterface>> getAclInterfaceMap();

    @Nonnull
    Map<Uuid, Collection<Uuid>> getRemoteAclIdMap();

    @Nonnull
    Map<String, Integer> getAclFlowPriorityMap();

}

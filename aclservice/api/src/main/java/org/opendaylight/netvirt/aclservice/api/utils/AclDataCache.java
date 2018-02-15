/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;

public interface AclDataCache {

    @Nullable
    Acl getAcl(String aclName);

    @Nullable
    Collection<AclInterface> getInterfaceList(Uuid acl);

    @Nullable
    Collection<Uuid> getRemoteAcl(Uuid remoteAclId, Class<? extends DirectionBase> direction);

    @Nullable
    Integer getAclTag(String aclId);

    @Nonnull
    Map<Uuid, Collection<AclInterface>> getAclInterfaceMap();

    @Nonnull
    Map<Uuid, Collection<Uuid>> getEgressRemoteAclIdMap();

    @Nonnull
    Map<Uuid, Collection<Uuid>> getIngressRemoteAclIdMap();

    @Nonnull
    Map<String, Integer> getAclTagMap();

    @Nonnull
    Map<String, Acl> getAclMap();

}

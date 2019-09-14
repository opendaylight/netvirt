/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.utils;

import com.google.common.collect.ImmutableBiMap;
import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class AclUtils {
    public static final String INGRESS = "ingress";
    public static final String EGRESS = "egress";
    public static final ImmutableBiMap<Class<? extends DirectionBase>, String> DIRECTION_MAP = ImmutableBiMap.of(
        DirectionIngress.class, INGRESS,
        DirectionEgress.class, EGRESS
    );

    private AclUtils() {

    }

    @Nonnull
    public static InstanceIdentifier<Acl> getAclIid(@Nonnull String aclName) {
        return InstanceIdentifier
            .builder(AccessLists.class)
            .child(Acl.class, new AclKey(aclName, Ipv4Acl.class))
            .build();
    }

    @Nonnull
    public static InstanceIdentifier<Ace> getAceIid(@Nonnull String aclName, @Nonnull String ruleName) {
        return getAclIid(aclName).builder()
            .child(AccessListEntries.class)
            .child(Ace.class, new AceKey(ruleName))
            .build();
    }

    @Nonnull
    public static String buildName(String... args) {
        return String.join("_", args);
    }
}

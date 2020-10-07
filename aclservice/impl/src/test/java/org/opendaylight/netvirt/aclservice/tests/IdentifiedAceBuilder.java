/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.PermitBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * This class is not ThreadSafe.
 */
public class IdentifiedAceBuilder implements DataTreeIdentifierDataObjectPairBuilder<Ace> {

    private String sgUuid;
    private String ruleName;
    private Matches matches;
    private Class<? extends DirectionBase> direction;
    private Optional<Uuid> optRemoteGroupId = Optional.empty();

    @Override
    public LogicalDatastoreType type() {
        return CONFIGURATION;
    }

    @Override
    public InstanceIdentifier<Ace> identifier() {
        return InstanceIdentifier
                .builder(AccessLists.class)
                .child(Acl.class, new AclKey(sgUuid, Ipv4Acl.class))
                .child(AccessListEntries.class)
                .child(Ace.class, new AceKey(ruleName))
                .build();
    }

    @Override
    public Ace dataObject() {
        return new AceBuilder()
            .withKey(new AceKey(ruleName))
            .setRuleName(ruleName)
            .setMatches(matches)
            .setActions(new ActionsBuilder()
                .setPacketHandling(new PermitBuilder()
                    .setPermit(Empty.getInstance()).build()
                ).build()
            )
            .addAugmentation(new SecurityRuleAttrBuilder()
                .setDirection(direction)
                .setRemoteGroupId(optRemoteGroupId.orElse(null)).build()
            ).build();
    }

    // TODO use Immutables.org to generate below?

    public IdentifiedAceBuilder sgUuid(String newSgUuid) {
        this.sgUuid = newSgUuid;
        return this;
    }

    public IdentifiedAceBuilder newRuleName(String newRuleName) {
        this.ruleName = newRuleName;
        return this;
    }

    public IdentifiedAceBuilder newMatches(Matches newMatches) {
        this.matches = newMatches;
        return this;
    }

    public IdentifiedAceBuilder newDirection(Class<? extends DirectionBase> newDirection) {
        this.direction = newDirection;
        return this;
    }

    public IdentifiedAceBuilder newRemoteGroupId(Uuid newRemoteGroupId) {
        this.optRemoteGroupId = Optional.of(newRemoteGroupId);
        return this;
    }

}

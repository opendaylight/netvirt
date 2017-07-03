/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.ActionsBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.PermitBuilder
import java.util.Optional
import javax.annotation.concurrent.NotThreadSafe

@NotThreadSafe
class IdentifiedAceBuilder implements DataTreeIdentifierDataObjectPairBuilder<Ace> {

    var String sgUuid
    var String newRuleName
    var Matches newMatches
    var Class<? extends DirectionBase> newDirection
    var Optional<Uuid> newRemoteGroupId = Optional.empty

    override type() {
        CONFIGURATION
    }

    override identifier() {
        InstanceIdentifier
                .builder(AccessLists)
                .child(Acl, new AclKey(sgUuid, Ipv4Acl))
                .child(AccessListEntries)
                .child(Ace, new AceKey(newRuleName))
                .build();
    }

    override dataObject() {
        new AceBuilder >> [
            key = new AceKey(newRuleName)
            ruleName = newRuleName
            matches = newMatches
            actions = new ActionsBuilder >> [
                packetHandling = new PermitBuilder >> [
                    permit = true
                ]
            ]
            addAugmentation(SecurityRuleAttr, new SecurityRuleAttrBuilder >> [
                direction = newDirection
                newRemoteGroupId.ifPresent([ uuid | remoteGroupId = uuid])
            ])
        ]
    }

    // TODO Write a new, or find an existing, Xtend active annotation @Builder, extending AccessorsProcessor, which creates setters that "return this" (and without the set* prefix)
    //   NB it will have to deal with Optional just like Immutables.org does (it above will have to be changed)
    //      as well as have addList(List<>) kind of methods

    def sgUuid(String sgUuid) {
        this.sgUuid = sgUuid
        this
    }

    def newRuleName(String newRuleName) {
        this.newRuleName = newRuleName
        this
    }

    def newMatches(Matches newMatches) {
        this.newMatches = newMatches
        this
    }

    def newDirection(Class<? extends DirectionBase> newDirection) {
        this.newDirection = newDirection
        this
    }

    def newRemoteGroupId(Uuid newRemoteGroupId) {
        this.newRemoteGroupId = Optional.of(newRemoteGroupId)
        this
    }

}

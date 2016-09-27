/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import java.util.ArrayList
import java.util.List
import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierIdentifiableBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION

import static extension org.opendaylight.netvirt.aclservice.tests.infra.BuilderExtensions.operator_doubleGreaterThan

// TODO @Builder
class IdentifiedInterfaceWithAclBuilder implements DataTreeIdentifierIdentifiableBuilder<Interface> {

    String interfaceName
    Boolean portSecurity
    // TODO auto-gen. new ArrayList by @Builder with an @EmptyCollections meta annotation
    List<Uuid> newSecurityGroups = new ArrayList
    List<AllowedAddressPairs> ifAllowedAddressPairs = new ArrayList

    // TODO auto-gen. this by @Builder
    def IdentifiedInterfaceWithAclBuilder interfaceName(String interfaceName) {
        this.interfaceName = interfaceName
        return this;
    }
    def IdentifiedInterfaceWithAclBuilder portSecurity(boolean portSecurity) {
        this.portSecurity = portSecurity
        return this;
    }
    def IdentifiedInterfaceWithAclBuilder newSecurityGroups(List<Uuid> newSecurityGroups) {
        this.newSecurityGroups = newSecurityGroups
        return this;
    }
    def IdentifiedInterfaceWithAclBuilder ifAllowedAddressPairs(List<AllowedAddressPairs> ifAllowedAddressPairs) {
        this.ifAllowedAddressPairs = ifAllowedAddressPairs
        return this;
    }

    // TODO auto-gen. this by @Builder
    def IdentifiedInterfaceWithAclBuilder addNewSecurityGroup(Uuid newSecurityGroup) {
        this.newSecurityGroups.add(newSecurityGroup)
        return this;
    }
    def IdentifiedInterfaceWithAclBuilder addIfAllowedAddressPair(AllowedAddressPairs ifAllowedAddressPair) {
        this.ifAllowedAddressPairs.add(ifAllowedAddressPair)
        return this;
    }

    override type() {
        CONFIGURATION
    }

    override identifier() {
        InstanceIdentifier.builder(Interfaces)
                    .child(Interface, new InterfaceKey(interfaceName)).build
    }

    override identifiable() {
        new InterfaceBuilder >> [
            addAugmentation(InterfaceAcl, new InterfaceAclBuilder >> [
                portSecurityEnabled = portSecurity
                securityGroups = newSecurityGroups
                allowedAddressPairs = ifAllowedAddressPairs
            ])
            name = interfaceName
        ]
    }

}

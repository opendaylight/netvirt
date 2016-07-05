/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.tests

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier

import static extension org.opendaylight.netvirt.aclservice.api.tests.BuilderExtensions.operator_doubleGreaterThan

class InterfaceBuilderHelper {

    def static Pair<InstanceIdentifier<Interface>, Interface> newInterfacePair(String interfaceName, boolean portSecurity) {
        Pair.of(newInterfaceInstanceIdentifier(interfaceName), newInterface(interfaceName, portSecurity))
    }

    def static newInterface(String ifName, boolean portSecurity) {
        new InterfaceBuilder >> [
            addAugmentation(InterfaceAcl, new InterfaceAclBuilder >> [
                portSecurityEnabled = portSecurity
                securityGroups = #[]
                allowedAddressPairs = #[]
            ])
            name = ifName
        ]
    }

    def static newInterfaceInstanceIdentifier(String interfaceName) {
        InstanceIdentifier.builder(Interfaces).child(Interface, new InterfaceKey(interfaceName)).build
    }

}

package org.opendaylight.netvirt.aclservice.api.tests

import static extension org.opendaylight.netvirt.aclservice.api.tests.BuilderExtensions.operator_doubleGreaterThan

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder

class InterfaceBuilderHelper {

    def static newInterface(String ifName, boolean portSecurity) {
        new InterfaceBuilder >> [
            addAugmentation(InterfaceAcl, new InterfaceAclBuilder >> [
                portSecurityEnabled = portSecurity
                securityGroups = #[]
            ])
            name = ifName
        ]
    }

}

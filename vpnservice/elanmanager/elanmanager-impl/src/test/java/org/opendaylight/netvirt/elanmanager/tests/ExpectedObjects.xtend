/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests

import org.opendaylight.mdsal.binding.testutils.AssertDataObjects
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstancesBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfacesBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan

/**
 * Definitions of complex objects expected in tests.
 *
 * These were originally generated {@link AssertDataObjects#assertEqualBeans}.
 */
class ExpectedObjects {

    def static createElanInstance() {
        new ElanInstancesBuilder >> [
            elanInstance = #[
                new ElanInstanceBuilder >> [
                    description = "TestElan description"
                    elanInstanceName = "TestElanName"
                    elanTag = 5000L
                    macTimeout = 12345L
                ]
            ]
        ]
    }

    def static addElanInterface() {
        new ElanInterfacesBuilder >> [
            elanInterface = #[
                new ElanInterfaceBuilder >> [
                    description = "..."
                    elanInstanceName = "TestElanName"
                    name = "TestElanInterfaceName"
                    staticMacEntries = #[
                        new PhysAddress("11:22:33:44:55:66")
                    ]
                ]
            ]
        ]
    }
}

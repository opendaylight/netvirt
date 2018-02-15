/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL
import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import javax.annotation.concurrent.NotThreadSafe
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixesBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixesKey
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixes
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.PortsSubnetIpPrefixes

import java.util.List
import java.util.ArrayList

@NotThreadSafe
class IdentifiedSubnetIpPrefixBuilder implements DataTreeIdentifierDataObjectPairBuilder<PortSubnetIpPrefixes>  {

    var String newInterfaceName
    List<IpPrefixOrAddress> newIpPrefixOrAddress = new ArrayList

    override dataObject() {
        new PortSubnetIpPrefixesBuilder >> [
            key = new PortSubnetIpPrefixesKey(newInterfaceName)
            portId = newInterfaceName
            subnetIpPrefixes = newIpPrefixOrAddress
        ]
    }

    override identifier() {
        InstanceIdentifier.builder(PortsSubnetIpPrefixes)
                    .child(PortSubnetIpPrefixes, new PortSubnetIpPrefixesKey(newInterfaceName)).build
    }

    override type() {
        OPERATIONAL
    }

    def interfaceName(String interfaceName) {
        this.newInterfaceName = interfaceName
        this
    }

    def addAllIpPrefixOrAddress(List<IpPrefixOrAddress> ipPrefixOrAddress) {
        this.newIpPrefixOrAddress.addAll(ipPrefixOrAddress)
        this
    }

}
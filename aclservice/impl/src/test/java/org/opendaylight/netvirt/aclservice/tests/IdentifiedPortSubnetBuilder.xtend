/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import java.util.ArrayList
import java.util.List
import javax.annotation.concurrent.NotThreadSafe
import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.PortSubnets
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.PortSubnet
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.PortSubnetBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.PortSubnetKey
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.port.subnet.SubnetInfo
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan

@NotThreadSafe
class IdentifiedPortSubnetBuilder implements DataTreeIdentifierDataObjectPairBuilder<PortSubnet>  {

    var String newInterfaceName
    List<SubnetInfo> subnetInfoList = new ArrayList

    override dataObject() {
        new PortSubnetBuilder >> [
            key = new PortSubnetKey(newInterfaceName)
            portId = newInterfaceName
            subnetInfo = subnetInfoList
        ]
    }

    override identifier() {
        InstanceIdentifier.builder(PortSubnets)
                    .child(PortSubnet, new PortSubnetKey(newInterfaceName)).build
    }

    override type() {
        OPERATIONAL
    }

    def interfaceName(String interfaceName) {
        this.newInterfaceName = interfaceName
        this
    }

    def addAllSubnetInfo(List<SubnetInfo> subnetInfoList) {
        this.subnetInfoList.addAll(subnetInfoList)
        this
    }

}
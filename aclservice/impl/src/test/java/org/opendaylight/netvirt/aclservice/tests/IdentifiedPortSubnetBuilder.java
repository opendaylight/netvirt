/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.NotThreadSafe;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.PortSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.PortSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.PortSubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.PortSubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.port.subnet.SubnetInfo;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@NotThreadSafe
public class IdentifiedPortSubnetBuilder implements DataTreeIdentifierDataObjectPairBuilder<PortSubnet>  {

    private String newInterfaceName;
    private final List<SubnetInfo> subnetInfoList = new ArrayList<>();

    @Override
    public PortSubnet dataObject() {
        return new PortSubnetBuilder()
            .setKey(new PortSubnetKey(newInterfaceName))
            .setPortId(newInterfaceName)
            .setSubnetInfo(subnetInfoList)
            .build();
    }

    @Override
    public InstanceIdentifier<PortSubnet> identifier() {
        return InstanceIdentifier.builder(PortSubnets.class)
                    .child(PortSubnet.class, new PortSubnetKey(newInterfaceName)).build();
    }

    @Override
    public LogicalDatastoreType type() {
        return OPERATIONAL;
    }

    public IdentifiedPortSubnetBuilder interfaceName(String interfaceName) {
        this.newInterfaceName = interfaceName;
        return this;
    }

    public IdentifiedPortSubnetBuilder addAllSubnetInfo(List<SubnetInfo> addToSubnetInfoList) {
        this.subnetInfoList.addAll(addToSubnetInfoList);
        return this;
    }

}

/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.SubnetInfo;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * This class is not ThreadSafe.
 */
public class IdentifiedInterfaceWithAclBuilder implements DataTreeIdentifierDataObjectPairBuilder<Interface> {

    private String interfaceName;
    private Boolean portSecurity;
    private final List<Uuid> newSecurityGroups = new ArrayList<>();
    private final List<AllowedAddressPairs> ifAllowedAddressPairs = new ArrayList<>();
    private final List<SubnetInfo> ifSubnetInfo = new ArrayList<>();

    @Override
    public LogicalDatastoreType type() {
        return CONFIGURATION;
    }

    @Override
    public InstanceIdentifier<Interface> identifier() {
        return InstanceIdentifier.builder(Interfaces.class)
                    .child(Interface.class, new InterfaceKey(interfaceName)).build();
    }

    @Override
    public Interface dataObject() {
        return new InterfaceBuilder()
            .addAugmentation(new InterfaceAclBuilder()
                .setPortSecurityEnabled(portSecurity)
                .setSecurityGroups(newSecurityGroups)
                .setAllowedAddressPairs(ifAllowedAddressPairs)
                .setSubnetInfo(ifSubnetInfo)
                .build())
            .setName(interfaceName)
            .setType(L2vlan.class)
        .build();
    }

    // see IdentifiedAceBuilder (@Builder)

    public IdentifiedInterfaceWithAclBuilder interfaceName(String newInterfaceName) {
        this.interfaceName = newInterfaceName;
        return this;
    }

    public IdentifiedInterfaceWithAclBuilder portSecurity(Boolean newPortSecurity) {
        this.portSecurity = newPortSecurity;
        return this;
    }

    public IdentifiedInterfaceWithAclBuilder addAllNewSecurityGroups(List<Uuid> addToNewSecurityGroups) {
        this.newSecurityGroups.addAll(addToNewSecurityGroups);
        return this;
    }

    public IdentifiedInterfaceWithAclBuilder addAllIfAllowedAddressPairs(
            List<AllowedAddressPairs> addToIfAllowedAddressPairs) {
        this.ifAllowedAddressPairs.addAll(addToIfAllowedAddressPairs);
        return this;
    }

    public IdentifiedInterfaceWithAclBuilder addAllIfSubnetInfo(List<SubnetInfo> addToIfSubnetInfo) {
        this.ifSubnetInfo.addAll(addToIfSubnetInfo);
        return this;
    }
}

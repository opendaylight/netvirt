/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static java.util.Collections.singletonList;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state._interface.StatisticsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Utility to create interfaces in tests.
 *
 * @author Michael Vorburger.ch
 */
public final class StateInterfaceBuilderHelper {
    // TODO make this like IdentifiedInterfaceWithAclBuilder

    private StateInterfaceBuilderHelper() {

    }

    public static void putNewStateInterface(DataBroker dataBroker, String interfaceName, String mac)
            throws TransactionCommitFailedException {
        InterfaceBuilder stateInterfaceBuilder = new InterfaceBuilder();
        stateInterfaceBuilder.setName(interfaceName);
        stateInterfaceBuilder.setPhysAddress(new PhysAddress(mac));
        stateInterfaceBuilder.setLowerLayerIf(singletonList("openflow:123:456"));
        stateInterfaceBuilder.setIfIndex(987);
        stateInterfaceBuilder.setOperStatus(OperStatus.Up);
        stateInterfaceBuilder.setAdminStatus(AdminStatus.Up);
        stateInterfaceBuilder.setType(L2vlan.class);
        stateInterfaceBuilder.setStatistics(new StatisticsBuilder()
                .setDiscontinuityTime(DateAndTime.getDefaultInstance("8330-42-22T79:08:74Z")).build());
        Interface stateInterface = stateInterfaceBuilder.build();
        InstanceIdentifier<Interface> id = InstanceIdentifier.builder(InterfacesState.class)
                .child(Interface.class, new InterfaceKey(interfaceName)).build();
        SingleTransactionDataBroker.syncWrite(dataBroker, OPERATIONAL, id, stateInterface);
    }

}

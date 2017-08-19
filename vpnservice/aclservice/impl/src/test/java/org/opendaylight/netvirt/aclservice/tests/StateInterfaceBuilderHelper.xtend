/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.controller.md.sal.binding.api.DataBroker
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state._interface.Statistics;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state._interface.StatisticsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL
import org.opendaylight.genius.mdsalutil.MDSALUtil
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan

class StateInterfaceBuilderHelper {
    // TODO make this like IdentifiedInterfaceWithAclBuilder

    def static putNewStateInterface(DataBroker dataBroker, String interfaceName, String mac) {
        val id = InstanceIdentifier.builder(InterfacesState)
                    .child(Interface, new InterfaceKey(interfaceName)).build
        val stateInterface = new InterfaceBuilder >> [
            name = interfaceName
            physAddress = new PhysAddress(mac)
            lowerLayerIf = #[ "openflow:123:456" ]
            ifIndex = 987
            operStatus = OperStatus.Up
            adminStatus = AdminStatus.Up
            type = L2vlan
            statistics = new StatisticsBuilder() >> [
                discontinuityTime = DateAndTime.getDefaultInstance("8330-42-22T79:08:74Z")
            ]
        ]
        MDSALUtil.syncWrite(dataBroker, OPERATIONAL, id, stateInterface);
    }

}

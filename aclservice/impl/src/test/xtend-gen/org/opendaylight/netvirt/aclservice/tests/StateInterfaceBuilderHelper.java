/**
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import java.util.Collections;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state._interface.Statistics;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state._interface.StatisticsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@SuppressWarnings("all")
public class StateInterfaceBuilderHelper {
  public static void putNewStateInterface(final DataBroker dataBroker, final String interfaceName, final String mac) {
    InstanceIdentifier.InstanceIdentifierBuilder<InterfacesState> _builder = InstanceIdentifier.<InterfacesState>builder(InterfacesState.class);
    InterfaceKey _interfaceKey = new InterfaceKey(interfaceName);
    InstanceIdentifier.InstanceIdentifierBuilder<Interface> _child = _builder.<Interface, InterfaceKey>child(Interface.class, _interfaceKey);
    final InstanceIdentifier<Interface> id = _child.build();
    InterfaceBuilder _interfaceBuilder = new InterfaceBuilder();
    final Procedure1<InterfaceBuilder> _function = (InterfaceBuilder it) -> {
      it.setName(interfaceName);
      PhysAddress _physAddress = new PhysAddress(mac);
      it.setPhysAddress(_physAddress);
      it.setLowerLayerIf(Collections.<String>unmodifiableList(CollectionLiterals.<String>newArrayList("openflow:123:456")));
      it.setIfIndex(Integer.valueOf(987));
      it.setOperStatus(Interface.OperStatus.Up);
      it.setAdminStatus(Interface.AdminStatus.Up);
      it.setType(L2vlan.class);
      StatisticsBuilder _statisticsBuilder = new StatisticsBuilder();
      final Procedure1<StatisticsBuilder> _function_1 = (StatisticsBuilder it_1) -> {
        DateAndTime _defaultInstance = DateAndTime.getDefaultInstance("8330-42-22T79:08:74Z");
        it_1.setDiscontinuityTime(_defaultInstance);
      };
      Statistics _doubleGreaterThan = XtendBuilderExtensions.<Statistics, StatisticsBuilder>operator_doubleGreaterThan(_statisticsBuilder, _function_1);
      it.setStatistics(_doubleGreaterThan);
    };
    final Interface stateInterface = XtendBuilderExtensions.<Interface, InterfaceBuilder>operator_doubleGreaterThan(_interfaceBuilder, _function);
    MDSALUtil.<Interface>syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, id, stateInterface);
  }
}

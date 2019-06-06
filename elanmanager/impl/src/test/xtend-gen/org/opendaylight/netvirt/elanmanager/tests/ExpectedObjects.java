/**
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.testutils.interfacemanager.TunnelInterfaceDetails;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
import org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;

/**
 * Definitions of complex objects expected in tests.
 *
 * These were originally generated {@link AssertDataObjects#assertEqualBeans}.
 */
@SuppressWarnings("all")
public class ExpectedObjects {
  public static String ELAN1 = "34701c04-1118-4c65-9425-78a80d49a211";

  public static Long ELAN1_SEGMENT_ID = Long.valueOf(100L);

  public static Interface newInterfaceConfig(final String interfaceName, final String parentName) {
    InterfaceBuilder _interfaceBuilder = new InterfaceBuilder();
    final Procedure1<InterfaceBuilder> _function = (InterfaceBuilder it) -> {
      it.setDescription(interfaceName);
      it.setName(interfaceName);
      it.setType(L2vlan.class);
      ParentRefsBuilder _parentRefsBuilder = new ParentRefsBuilder();
      final Procedure1<ParentRefsBuilder> _function_1 = (ParentRefsBuilder it_1) -> {
        it_1.setParentInterface(parentName);
      };
      ParentRefs _doubleGreaterThan = XtendBuilderExtensions.<ParentRefs, ParentRefsBuilder>operator_doubleGreaterThan(_parentRefsBuilder, _function_1);
      it.addAugmentation(ParentRefs.class, _doubleGreaterThan);
      IfL2vlanBuilder _ifL2vlanBuilder = new IfL2vlanBuilder();
      final Procedure1<IfL2vlanBuilder> _function_2 = (IfL2vlanBuilder it_1) -> {
        it_1.setL2vlanMode(IfL2vlan.L2vlanMode.Trunk);
        VlanId _vlanId = new VlanId(Integer.valueOf(0));
        it_1.setVlanId(_vlanId);
      };
      IfL2vlan _doubleGreaterThan_1 = XtendBuilderExtensions.<IfL2vlan, IfL2vlanBuilder>operator_doubleGreaterThan(_ifL2vlanBuilder, _function_2);
      it.addAugmentation(IfL2vlan.class, _doubleGreaterThan_1);
    };
    return XtendBuilderExtensions.<Interface, InterfaceBuilder>operator_doubleGreaterThan(_interfaceBuilder, _function);
  }

  public static Object createElanInstance() {
    throw new Error("Unresolved compilation problems:"
      + "\nElanInstancesBuilder cannot be resolved."
      + "\nThe method elanInstance(List<Object>) is undefined"
      + "\nElanInstanceBuilder cannot be resolved."
      + "\nThe method description(String) is undefined"
      + "\nThe method elanInstanceName(String) is undefined"
      + "\nThe method elanTag(long) is undefined"
      + "\nThe method macTimeout(long) is undefined"
      + "\n>> cannot be resolved"
      + "\n>> cannot be resolved");
  }

  public static Object createElanInstance(final String elan, final Long segmentId) {
    throw new Error("Unresolved compilation problems:"
      + "\nElanInstanceBuilder cannot be resolved."
      + "\nThe method elanInstanceName(String) is undefined"
      + "\nThe method description(String) is undefined"
      + "\nThe method segmentType(Object) is undefined"
      + "\nThe method or field SegmentTypeVxlan is undefined"
      + "\nThe method segmentationId(Long) is undefined"
      + "\nThe method macTimeout(long) is undefined"
      + "\n>> cannot be resolved");
  }

  public static Object createElanInstance(final String elan, final Long segmentId, final Long tag) {
    throw new Error("Unresolved compilation problems:"
      + "\nElanInstanceBuilder cannot be resolved."
      + "\nThe method elanInstanceName(String) is undefined"
      + "\nThe method description(String) is undefined"
      + "\nThe method elanTag(Long) is undefined"
      + "\nThe method segmentationId(Long) is undefined"
      + "\nThe method segmentType(Object) is undefined"
      + "\nThe method or field SegmentTypeVxlan is undefined"
      + "\nThe method macTimeout(long) is undefined"
      + "\n>> cannot be resolved");
  }

  public static Flow checkSmac(final String flowId, final InterfaceInfo interfaceInfo, final /* ElanInstance */Object elanInstance) {
    throw new Error("Unresolved compilation problems:"
      + "\ngetElanTag cannot be resolved");
  }

  public static Flow checkDmacOfSameDpn(final String flowId, final InterfaceInfo interfaceInfo, final /* ElanInstance */Object elanInstance) {
    throw new Error("Unresolved compilation problems:"
      + "\ngetElanTag cannot be resolved");
  }

  public static Flow checkDmacOfOtherDPN(final String flowId, final InterfaceInfo interfaceInfo, final TunnelInterfaceDetails tepDetails, final /* ElanInstance */Object elanInstance) {
    throw new Error("Unresolved compilation problems:"
      + "\ngetElanTag cannot be resolved");
  }

  public static Object checkEvpnAdvertiseRoute(final Long vni, final String mac, final String tepip, final String prefix, final String rd1) {
    throw new Error("Unresolved compilation problems:"
      + "\nNetworksBuilder cannot be resolved."
      + "\nThe method bgpControlPlaneType(Object) is undefined"
      + "\nThe method or field BgpControlPlaneType is undefined"
      + "\nThe method encapType(Object) is undefined"
      + "\nThe method or field EncapType is undefined"
      + "\nThe method ethtag(long) is undefined"
      + "\nThe method l2vni(Long) is undefined"
      + "\nThe method l3vni(long) is undefined"
      + "\nThe method label(long) is undefined"
      + "\nThe method macaddress(String) is undefined"
      + "\nThe method nexthop(Ipv4Address) is undefined"
      + "\nThe method prefixLen(String) is undefined"
      + "\nThe method rd(String) is undefined"
      + "\n>> cannot be resolved"
      + "\nPROTOCOLEVPN cannot be resolved"
      + "\nVXLAN cannot be resolved");
  }

  public static /* Networks */Object checkEvpnWithdrawRT2DelIntf() {
    return null;
  }
}

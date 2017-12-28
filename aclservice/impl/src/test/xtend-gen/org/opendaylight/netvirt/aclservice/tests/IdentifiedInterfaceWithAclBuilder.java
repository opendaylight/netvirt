/**
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.NotThreadSafe;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions;
import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@NotThreadSafe
@SuppressWarnings("all")
public class IdentifiedInterfaceWithAclBuilder implements DataTreeIdentifierDataObjectPairBuilder<Interface> {
  private String interfaceName;
  
  private Boolean portSecurity;
  
  private List<Uuid> newSecurityGroups = new ArrayList<Uuid>();
  
  private List<AllowedAddressPairs> ifAllowedAddressPairs = new ArrayList<AllowedAddressPairs>();
  
  @Override
  public LogicalDatastoreType type() {
    return LogicalDatastoreType.CONFIGURATION;
  }
  
  @Override
  public InstanceIdentifier<Interface> identifier() {
    InstanceIdentifier.InstanceIdentifierBuilder<Interfaces> _builder = InstanceIdentifier.<Interfaces>builder(Interfaces.class);
    InterfaceKey _interfaceKey = new InterfaceKey(this.interfaceName);
    InstanceIdentifier.InstanceIdentifierBuilder<Interface> _child = _builder.<Interface, InterfaceKey>child(Interface.class, _interfaceKey);
    return _child.build();
  }
  
  @Override
  public Interface dataObject() {
    InterfaceBuilder _interfaceBuilder = new InterfaceBuilder();
    final Procedure1<InterfaceBuilder> _function = (InterfaceBuilder it) -> {
      InterfaceAclBuilder _interfaceAclBuilder = new InterfaceAclBuilder();
      final Procedure1<InterfaceAclBuilder> _function_1 = (InterfaceAclBuilder it_1) -> {
        it_1.setPortSecurityEnabled(this.portSecurity);
        it_1.setSecurityGroups(this.newSecurityGroups);
        it_1.setAllowedAddressPairs(this.ifAllowedAddressPairs);
      };
      InterfaceAcl _doubleGreaterThan = XtendBuilderExtensions.<InterfaceAcl, InterfaceAclBuilder>operator_doubleGreaterThan(_interfaceAclBuilder, _function_1);
      it.addAugmentation(InterfaceAcl.class, _doubleGreaterThan);
      it.setName(this.interfaceName);
      it.setType(L2vlan.class);
    };
    return XtendBuilderExtensions.<Interface, InterfaceBuilder>operator_doubleGreaterThan(_interfaceBuilder, _function);
  }
  
  public IdentifiedInterfaceWithAclBuilder interfaceName(final String interfaceName) {
    IdentifiedInterfaceWithAclBuilder _xblockexpression = null;
    {
      this.interfaceName = interfaceName;
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
  
  public IdentifiedInterfaceWithAclBuilder portSecurity(final Boolean portSecurity) {
    IdentifiedInterfaceWithAclBuilder _xblockexpression = null;
    {
      this.portSecurity = portSecurity;
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
  
  public IdentifiedInterfaceWithAclBuilder addAllNewSecurityGroups(final List<Uuid> newSecurityGroups) {
    IdentifiedInterfaceWithAclBuilder _xblockexpression = null;
    {
      this.newSecurityGroups.addAll(newSecurityGroups);
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
  
  public IdentifiedInterfaceWithAclBuilder addAllIfAllowedAddressPairs(final List<AllowedAddressPairs> ifAllowedAddressPairs) {
    IdentifiedInterfaceWithAclBuilder _xblockexpression = null;
    {
      this.ifAllowedAddressPairs.addAll(ifAllowedAddressPairs);
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
}

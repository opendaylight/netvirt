/**
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.concurrent.NotThreadSafe;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions;
import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Actions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.Permit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.PermitBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@NotThreadSafe
@SuppressWarnings("all")
public class IdentifiedAceBuilder implements DataTreeIdentifierDataObjectPairBuilder<Ace> {
  private String sgUuid;
  
  private String newRuleName;
  
  private Matches newMatches;
  
  private Class<? extends DirectionBase> newDirection;
  
  private Optional<Uuid> newRemoteGroupId = Optional.<Uuid>empty();
  
  @Override
  public LogicalDatastoreType type() {
    return LogicalDatastoreType.CONFIGURATION;
  }
  
  @Override
  public InstanceIdentifier<Ace> identifier() {
    InstanceIdentifier.InstanceIdentifierBuilder<AccessLists> _builder = InstanceIdentifier.<AccessLists>builder(AccessLists.class);
    AclKey _aclKey = new AclKey(this.sgUuid, Ipv4Acl.class);
    InstanceIdentifier.InstanceIdentifierBuilder<Acl> _child = _builder.<Acl, AclKey>child(Acl.class, _aclKey);
    InstanceIdentifier.InstanceIdentifierBuilder<AccessListEntries> _child_1 = _child.<AccessListEntries>child(AccessListEntries.class);
    AceKey _aceKey = new AceKey(this.newRuleName);
    InstanceIdentifier.InstanceIdentifierBuilder<Ace> _child_2 = _child_1.<Ace, AceKey>child(Ace.class, _aceKey);
    return _child_2.build();
  }
  
  @Override
  public Ace dataObject() {
    AceBuilder _aceBuilder = new AceBuilder();
    final Procedure1<AceBuilder> _function = (AceBuilder it) -> {
      AceKey _aceKey = new AceKey(this.newRuleName);
      it.setKey(_aceKey);
      it.setRuleName(this.newRuleName);
      it.setMatches(this.newMatches);
      ActionsBuilder _actionsBuilder = new ActionsBuilder();
      final Procedure1<ActionsBuilder> _function_1 = (ActionsBuilder it_1) -> {
        PermitBuilder _permitBuilder = new PermitBuilder();
        final Procedure1<PermitBuilder> _function_2 = (PermitBuilder it_2) -> {
          it_2.setPermit(Boolean.valueOf(true));
        };
        Permit _doubleGreaterThan = XtendBuilderExtensions.<Permit, PermitBuilder>operator_doubleGreaterThan(_permitBuilder, _function_2);
        it_1.setPacketHandling(_doubleGreaterThan);
      };
      Actions _doubleGreaterThan = XtendBuilderExtensions.<Actions, ActionsBuilder>operator_doubleGreaterThan(_actionsBuilder, _function_1);
      it.setActions(_doubleGreaterThan);
      SecurityRuleAttrBuilder _securityRuleAttrBuilder = new SecurityRuleAttrBuilder();
      final Procedure1<SecurityRuleAttrBuilder> _function_2 = (SecurityRuleAttrBuilder it_1) -> {
        it_1.setDirection(this.newDirection);
        final Consumer<Uuid> _function_3 = (Uuid uuid) -> {
          it_1.setRemoteGroupId(uuid);
        };
        this.newRemoteGroupId.ifPresent(_function_3);
      };
      SecurityRuleAttr _doubleGreaterThan_1 = XtendBuilderExtensions.<SecurityRuleAttr, SecurityRuleAttrBuilder>operator_doubleGreaterThan(_securityRuleAttrBuilder, _function_2);
      it.addAugmentation(SecurityRuleAttr.class, _doubleGreaterThan_1);
    };
    return XtendBuilderExtensions.<Ace, AceBuilder>operator_doubleGreaterThan(_aceBuilder, _function);
  }
  
  public IdentifiedAceBuilder sgUuid(final String sgUuid) {
    IdentifiedAceBuilder _xblockexpression = null;
    {
      this.sgUuid = sgUuid;
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
  
  public IdentifiedAceBuilder newRuleName(final String newRuleName) {
    IdentifiedAceBuilder _xblockexpression = null;
    {
      this.newRuleName = newRuleName;
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
  
  public IdentifiedAceBuilder newMatches(final Matches newMatches) {
    IdentifiedAceBuilder _xblockexpression = null;
    {
      this.newMatches = newMatches;
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
  
  public IdentifiedAceBuilder newDirection(final Class<? extends DirectionBase> newDirection) {
    IdentifiedAceBuilder _xblockexpression = null;
    {
      this.newDirection = newDirection;
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
  
  public IdentifiedAceBuilder newRemoteGroupId(final Uuid newRemoteGroupId) {
    IdentifiedAceBuilder _xblockexpression = null;
    {
      Optional<Uuid> _of = Optional.<Uuid>of(newRemoteGroupId);
      this.newRemoteGroupId = _of;
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
}

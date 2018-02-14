/**
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.PortsSubnetIpPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.ports.subnet.ip.prefixes.PortSubnetIpPrefixesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@NotThreadSafe
@SuppressWarnings("all")
public class IdentifiedSubnetIpPrefixBuilder implements DataTreeIdentifierDataObjectPairBuilder<PortSubnetIpPrefixes> {
  private String newInterfaceName;
  
  private List<IpPrefixOrAddress> newIpPrefixOrAddress = new ArrayList<IpPrefixOrAddress>();
  
  @Override
  public PortSubnetIpPrefixes dataObject() {
    PortSubnetIpPrefixesBuilder _portSubnetIpPrefixesBuilder = new PortSubnetIpPrefixesBuilder();
    final Procedure1<PortSubnetIpPrefixesBuilder> _function = (PortSubnetIpPrefixesBuilder it) -> {
      PortSubnetIpPrefixesKey _portSubnetIpPrefixesKey = new PortSubnetIpPrefixesKey(this.newInterfaceName);
      it.setKey(_portSubnetIpPrefixesKey);
      it.setPortId(this.newInterfaceName);
      it.setSubnetIpPrefixes(this.newIpPrefixOrAddress);
    };
    return XtendBuilderExtensions.<PortSubnetIpPrefixes, PortSubnetIpPrefixesBuilder>operator_doubleGreaterThan(_portSubnetIpPrefixesBuilder, _function);
  }
  
  @Override
  public InstanceIdentifier<PortSubnetIpPrefixes> identifier() {
    InstanceIdentifier.InstanceIdentifierBuilder<PortsSubnetIpPrefixes> _builder = InstanceIdentifier.<PortsSubnetIpPrefixes>builder(PortsSubnetIpPrefixes.class);
    PortSubnetIpPrefixesKey _portSubnetIpPrefixesKey = new PortSubnetIpPrefixesKey(this.newInterfaceName);
    InstanceIdentifier.InstanceIdentifierBuilder<PortSubnetIpPrefixes> _child = _builder.<PortSubnetIpPrefixes, PortSubnetIpPrefixesKey>child(PortSubnetIpPrefixes.class, _portSubnetIpPrefixesKey);
    return _child.build();
  }
  
  @Override
  public LogicalDatastoreType type() {
    return LogicalDatastoreType.OPERATIONAL;
  }
  
  public IdentifiedSubnetIpPrefixBuilder interfaceName(final String interfaceName) {
    IdentifiedSubnetIpPrefixBuilder _xblockexpression = null;
    {
      this.newInterfaceName = interfaceName;
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
  
  public IdentifiedSubnetIpPrefixBuilder addAllIpPrefixOrAddress(final List<IpPrefixOrAddress> ipPrefixOrAddress) {
    IdentifiedSubnetIpPrefixBuilder _xblockexpression = null;
    {
      this.newIpPrefixOrAddress.addAll(ipPrefixOrAddress);
      _xblockexpression = this;
    }
    return _xblockexpression;
  }
}

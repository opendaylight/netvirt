/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fibmanager.test;

import java.math.BigInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnmanager.api.IVpnManager;
import org.opendaylight.vpnservice.fibmanager.FibManager;
import org.opendaylight.vpnservice.fibmanager.test.MockDataChangedEvent;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.GetEgressPointerOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.L3nexthopService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.RpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

@RunWith(MockitoJUnitRunner.class)
public class FibManagerTest {

  @Mock
  DataBroker dataBroker;
  @Mock
  ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
  @Mock
  ReadOnlyTransaction mockReadTx;
  @Mock
  WriteTransaction mockWriteTx;
  @Mock
  L3nexthopService l3nexthopService;
  @Mock
  IMdsalApiManager mdsalManager;
  @Mock
  IVpnManager vpnmanager;
  @Mock
  GetEgressPointerOutput adjacency;
  @Mock
  VrfTablesKey vrfTableKey;

  MockDataChangedEvent dataChangeEvent;
  FibManager fibmgr;
  private static final Long EgressPointer = 11L;
  VrfEntry vrfEntry;
  InstanceIdentifier<VrfEntry> identifier;
  VrfEntryBuilder vrfbuilder;
  private static final String rd = "routeDis";
  private static final String prefix = "0.1.2.3";
  private static final String nexthop = "1.1.1.1";
  private static final int label = 10;
  List<BigInteger> Dpns;
  private static final long vpnId = 101L;

  private void SetupMocks() {
    Dpns = new ArrayList<BigInteger>();
    Dpns.add(BigInteger.valueOf(100000L));
    identifier = buildVrfEntryId(rd, prefix);
    vrfEntry = buildVrfEntry(rd, prefix, nexthop, label);
    fibmgr.setMdsalManager(mdsalManager);
    fibmgr.setVpnmanager(vpnmanager);
    when(adjacency.getEgressPointer()).thenReturn(EgressPointer);
    when(adjacency.isLocalDestination()).thenReturn(true);
    when(vrfTableKey.getRouteDistinguisher()).thenReturn(rd);
    when(vpnmanager.getDpnsForVpn(any(Long.class))).thenReturn(Dpns);
  }

  @Before
  public void setUp() throws Exception {
    when(
        dataBroker.registerDataChangeListener(any(LogicalDatastoreType.class),
            any(InstanceIdentifier.class), any(DataChangeListener.class),
            any(DataChangeScope.class))).thenReturn(dataChangeListenerRegistration);
    dataChangeEvent = new MockDataChangedEvent();
    vrfbuilder = new VrfEntryBuilder();
    fibmgr = new FibManager(dataBroker, l3nexthopService) {
      protected GetEgressPointerOutput resolveAdjacency(final BigInteger dpId, final long vpnId,
          final VrfEntry vrfEntry) {
        return adjacency;
      }

      protected Long getVpnId(String rd) {
        return vpnId;
      }
    };
    SetupMocks();
  }

  @Test
  public void testAdd() {
    dataChangeEvent.created.put(identifier, vrfEntry);
    fibmgr.onDataChanged(dataChangeEvent);
    Mockito.verify(mdsalManager, Mockito.times(2)).installFlow(any(FlowEntity.class));
  }

  private VrfEntry buildVrfEntry(String rd, String prefix, String nexthop, int label) {
    return new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddress(nexthop)
        .setLabel((long) label).build();
  }

  public static InstanceIdentifier<VrfTables> buildVrfTableId(String rd) {
    InstanceIdentifierBuilder<VrfTables> idBuilder =
        InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
    InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
    return vrfTableId;
  }

  public static InstanceIdentifier<VrfEntry> buildVrfEntryId(String rd, String prefix) {
    InstanceIdentifierBuilder<VrfEntry> idBuilder =
        InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
            .child(VrfEntry.class, new VrfEntryKey(prefix));
    InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
    return vrfEntryId;
  }
}

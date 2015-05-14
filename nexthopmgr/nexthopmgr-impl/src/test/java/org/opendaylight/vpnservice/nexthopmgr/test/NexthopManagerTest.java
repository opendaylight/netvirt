/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others. All
 * rights reserved.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr.test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.VpnNexthopsKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.nexthopmgr.NexthopManager;
import org.opendaylight.vpnservice.nexthopmgr.VpnInterfaceChangeListener;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.VpnNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthopBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.Augmentation;
import org.opendaylight.yangtools.yang.binding.DataContainer;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;

@RunWith(MockitoJUnitRunner.class)
public class NexthopManagerTest {

  private final String ifName = "dpn1-if0";
  private final String vpnName = "vpn1";
  private final String ipAddress = "1.1.1.1";
  private final String macAddress = "11:22:33:44:55:66";
  private final int groupId = 5000;
  private final long dpId = 1L;
  private final long vpnId = 2L;

  Map<InstanceIdentifier<?>, DataObject> written = new HashMap<>();
  Map<InstanceIdentifier<?>, DataObject> updated = new HashMap<>();
  Set<InstanceIdentifier<?>> removed = new HashSet<>();

  @Mock
  DataBroker dataBroker;
  @Mock
  IdManager idManager;
  @Mock
  IInterfaceManager interfacemanager;
  @Mock
  ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
  @Mock
  ReadOnlyTransaction mockReadTx;
  @Mock
  WriteTransaction mockWriteTx;
  @Mock
  IMdsalApiManager mdsalmanager;

  MockDataChangedEvent dataChangeEvent;
  NexthopManager nhmgr;
  VpnInterfaceChangeListener vpnchglistener;
  VpnInstance vpninstance;
  VpnInterface vpninterface;
  VpnNexthops vpnnexthops;
  VpnNexthop vpnNexthop;

  InstanceIdentifier<VpnInterface> vpnInterfaceIdent;
  InstanceIdentifier<Adjacencies> adjacencies;
  Adjacencies adjacency;
  List<Adjacency> adjList = new ArrayList<Adjacency>();

  Adjacency adjacencyobject = new Adjacency() {

    @Override
    public <E extends Augmentation<Adjacency>> E getAugmentation(Class<E> augmentationType) {
      return null;
    }

    @Override
    public Class<? extends DataContainer> getImplementedInterface() {
      return null;
    }

    @Override
    public Long getNextHopId() {
      return null;
    }

    @Override
    public String getMacAddress() {
      return macAddress;
    }

    @Override
    public Long getLabel() {
      return null;
    }

    @Override
    public AdjacencyKey getKey() {
      return null;
    }

    @Override
    public String getIpAddress() {
      return ipAddress;
    }
  };

  @Before
  public void setUp() throws Exception {
    adjacency = getVpnInterfaceAugmentation(adjList);
    adjacencies =
            InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class).augmentation(Adjacencies.class);
    vpnInterfaceIdent = adjacencies.firstIdentifierOf(VpnInterface.class);
    vpninterface = getVpnInterface(ifName, vpnName, adjacency);
    adjList.add(adjacencyobject);

    when(
            dataBroker.registerDataChangeListener(any(LogicalDatastoreType.class), any(InstanceIdentifier.class),
                    any(DataChangeListener.class), any(DataChangeScope.class))).thenReturn(
            dataChangeListenerRegistration);

    dataChangeEvent = new MockDataChangedEvent();
    vpnNexthop = new VpnNexthopBuilder().setEgressPointer(10L).setIpAddress(ipAddress).build();
    nhmgr = new NexthopManager(dataBroker) {
      protected int createNextHopPointer(String nexthopKey) {
        return groupId;
      }

      protected long getVpnId(String vpnName) {
        return vpnId;
      }

      protected VpnNexthop getVpnNexthop(long vpnId, String ipAddress) {
        return vpnNexthop;
      }

      protected void addVpnNexthopToDS(long vpnId, String ipPrefix, long egressPointer) {
        return;
      }
    };

    nhmgr.setInterfaceManager(interfacemanager);
    nhmgr.setMdsalManager(mdsalmanager);
    vpnchglistener = new VpnInterfaceChangeListener(dataBroker, nhmgr);
    setupMocks();
  }

  private void setupMocks() {
    when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
    when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
  }

  @Test
  public void testAdd() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Optional<VpnInterface> vpnIf = Optional.of(vpninterface);

    doReturn(Futures.immediateCheckedFuture(vpnIf)).when(mockReadTx).read(LogicalDatastoreType.OPERATIONAL,
            vpnInterfaceIdent);
    InstanceIdentifierBuilder<VpnNexthops> idBuilder =
                    InstanceIdentifier.builder(L3nexthop.class).child(VpnNexthops.class, new VpnNexthopsKey(vpnId));
    InstanceIdentifier<VpnNexthops> id = idBuilder.build();
    doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(LogicalDatastoreType.OPERATIONAL,id);

    when(interfacemanager.getDpnForInterface(ifName)).thenReturn(dpId);
    dataChangeEvent.created.put(adjacencies, adjacency);
    vpnchglistener.onDataChanged(dataChangeEvent);

 // FIXME: Add some verifications
  }

  private Adjacencies getVpnInterfaceAugmentation(List<Adjacency> nextHops) {
    return new AdjacenciesBuilder().setAdjacency(nextHops).build();
  }

  private VpnInterface getVpnInterface(String intfName, String vpnName, Adjacencies aug) {
    return new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(intfName)).setVpnInstanceName(vpnName)
            .addAugmentation(Adjacencies.class, aug).build();
  }
}
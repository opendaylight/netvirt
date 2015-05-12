/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fibmanager;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import com.google.common.util.concurrent.FutureCallback;
import org.opendaylight.vpnservice.AbstractDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstance1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.GetEgressPointerInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.GetEgressPointerOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.L3nexthopService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.FibEntries;
import org.opendaylight.yangtools.yang.binding.RpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Optional;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FibManager extends AbstractDataChangeListener<VrfEntry> implements AutoCloseable{
  private static final Logger LOG = LoggerFactory.getLogger(FibManager.class);
  private static final String FLOWID_PREFIX = "L3.";
  private ListenerRegistration<DataChangeListener> listenerRegistration;
  private final DataBroker broker;
  private final L3nexthopService l3nexthopService;
  private IMdsalApiManager mdsalManager;

  private static final short L3_FIB_TABLE = 20;
  private static final short L3_LFIB_TABLE = 21;
  private static final BigInteger COOKIE_VM_LFIB_TABLE = new BigInteger("8000002", 16);
  private static final BigInteger COOKIE_VM_FIB_TABLE =  new BigInteger("8000003", 16);
  private static final int DEFAULT_FIB_FLOW_PRIORITY = 10;


  private static final FutureCallback<Void> DEFAULT_CALLBACK =
      new FutureCallback<Void>() {
        public void onSuccess(Void result) {
          LOG.debug("Success in Datastore write operation");
        }

        public void onFailure(Throwable error) {
          LOG.error("Error in Datastore write operation", error);
        };
      };

  public FibManager(final DataBroker db, final RpcService nextHopService) {
    super(VrfEntry.class);
    broker = db;
    l3nexthopService = (L3nexthopService)nextHopService;
    registerListener(db);
  }

  @Override
  public void close() throws Exception {
    if (listenerRegistration != null) {
      try {
        listenerRegistration.close();
      } catch (final Exception e) {
        LOG.error("Error when cleaning up DataChangeListener.", e);
      }
      listenerRegistration = null;
    }
    LOG.info("Fib Manager Closed");
  }


  public void setMdsalManager(IMdsalApiManager mdsalManager) {
    this.mdsalManager = mdsalManager;
  }

  private void registerListener(final DataBroker db) {
    try {
      listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                                                           getWildCardPath(), FibManager.this, DataChangeScope.SUBTREE);
    } catch (final Exception e) {
      LOG.error("FibManager DataChange listener registration fail!", e);
      throw new IllegalStateException("FibManager registration Listener failed.", e);
    }
  }

  @Override
  protected void add(final InstanceIdentifier<VrfEntry> identifier,
                     final VrfEntry vrfEntry) {
    LOG.trace("key: " + identifier + ", value=" + vrfEntry );
    createFibEntries(identifier, vrfEntry);
  }

  private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
                                                  InstanceIdentifier<T> path) {

    ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

    Optional<T> result = Optional.absent();
    try {
      result = tx.read(datastoreType, path).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return result;
  }

  private InstanceIdentifier<VrfEntry> getWildCardPath() {
    return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class).child(VrfEntry.class);
  }

  @Override
  protected void remove(InstanceIdentifier<VrfEntry> identifier, VrfEntry del) {
    LOG.trace("key: " + identifier + ", value=" + del );
  }

  @Override
  protected void update(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update) {
    LOG.trace("key: " + identifier + ", original=" + original + ", update=" + update );
  }

  private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                                                 InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
    WriteTransaction tx = broker.newWriteOnlyTransaction();
    tx.put(datastoreType, path, data, true);
    Futures.addCallback(tx.submit(), callback);
  }

  private void createFibEntries(final InstanceIdentifier<VrfEntry> identifier,
                                final VrfEntry vrfEntry) {
    final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
    Preconditions.checkNotNull(vrfTableKey, "VrfTablesKey cannot be null or empty!");
    Preconditions.checkNotNull(vrfEntry, "VrfEntry cannot be null or empty!");

    Long vpnId = getVpnId(vrfTableKey.getRouteDistinguisher());
    Preconditions.checkNotNull(vpnId, "Vpn Instance not available!");
    List<Long> dpns = getDpnsByVpn(vpnId);
    for (Long dpId : dpns) {
      addRouteInternal(dpId, vpnId, vrfTableKey, vrfEntry);
    }
  }

  /*
   *
   */
  private void addRouteInternal(final long dpId, final long vpnId, final VrfTablesKey vrfTableKey,
                        final VrfEntry vrfEntry) {
    String rd = vrfTableKey.getRouteDistinguisher();
    LOG.info("adding route " + vrfEntry.getDestPrefix() + " " + rd);

    String values[] = vrfEntry.getDestPrefix().split("/");
    LOG.info(String.format("Adding route to DPN. ip %s masklen %s", values[0], values[1]));
    String ipAddress = values[0];
    int prefix = Integer.parseInt(values[1]);
    InetAddress destAddress = null;
    try {
      destAddress = InetAddress.getByName(ipAddress);
    } catch (UnknownHostException e) {
      LOG.error(String.format("UnknowHostException in addRoute."
                                 + "Failed to add Route for ipPrefix %s", vrfEntry.getDestPrefix()));
      return;
    }

    GetEgressPointerOutput adjacency = resolveAdjacency(dpId, vpnId, vrfTableKey, vrfEntry);
    long groupId = -1;
    boolean isLocalRoute = false;
    if(adjacency != null) {
      groupId = adjacency.getEgressPointer();
      isLocalRoute = adjacency.isLocalDestination();
    }
    if(groupId == -1) {
      LOG.error(String.format("Could not get nexthop group id for nexthop: %s in vpn %s",
                                   vrfEntry.getNextHopAddress(), rd));
      LOG.warn(String.format("Failed to add Route: %s in vpn: %s",
                             vrfEntry.getDestPrefix(), rd));
      return;
    }

    makeConnectedRoute(dpId, destAddress, prefix, vpnId, rd, groupId, NwConstants.ADD_FLOW);

    if (isLocalRoute) {
      makeLFibTableEntry(dpId, vrfEntry.getLabel(), groupId, vrfEntry.getNextHopAddress(), NwConstants.ADD_FLOW);
    }

    LOG.info(
        "Successfully added fib entry for " + destAddress.getHostAddress() + " vpnId " + vpnId);
  }

  public void deleteRoute(InetAddress destPrefix, int prefixLength, int vpnId, String rd) {
    LOG.info("deleting route "+destPrefix.getHostAddress()+ " "+vpnId);

    List<Long> dpnIds = getDpnsByVpn(vpnId);

    for (long dpnId : dpnIds) {
      makeConnectedRoute(dpnId, destPrefix, prefixLength, vpnId, rd, 0/*groupId*/, NwConstants.DEL_FLOW);
    }
    LOG.info("Successfully delete fib entry for "+destPrefix.getHostAddress()+ " vpnId "+vpnId);
  }

  public void deleteRoute(InetAddress destPrefix, int prefixLength, int vpnId, String rd, long dpId) {
    LOG.info("deleting route "+destPrefix.getHostAddress()+ " "+vpnId);

    makeConnectedRoute(dpId, destPrefix, prefixLength, vpnId, rd, 0/*groupId*/, NwConstants.DEL_FLOW);

    LOG.info("Successfully delete fib entry for "+destPrefix.getHostAddress()+ " vpnId "+vpnId);
  }

  private long getIpAddress(byte[] rawIpAddress) {
    return (((rawIpAddress[0] & 0xFF) << (3 * 8)) + ((rawIpAddress[1] & 0xFF) << (2 * 8))
            + ((rawIpAddress[2] & 0xFF) << (1 * 8)) + (rawIpAddress[3] & 0xFF)) & 0xffffffffL;
  }

  private void makeConnectedRoute(long dpId, InetAddress destPrefix, int prefixLength, long vpnId,
                          String rd, long groupId, int addOrRemove) {

    List<MatchInfo> matches = new ArrayList<MatchInfo>();

    matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
        BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

    matches.add(new MatchInfo(MatchFieldType.eth_type,
                              new long[] { 0x0800L }));

    if(prefixLength != 0) {
      matches.add(new MatchInfo(MatchFieldType.ipv4_dst, new long[] {
          getIpAddress(destPrefix.getAddress()), prefixLength }));
    }

    List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
    List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();

    if(addOrRemove == NwConstants.ADD_FLOW) {
      actionsInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId)}));
      instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));
    }

    String flowRef = getFlowRef(dpId, L3_FIB_TABLE, rd, destPrefix);

    FlowEntity flowEntity;

    int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
    flowEntity = MDSALUtil.buildFlowEntity(dpId, L3_FIB_TABLE, flowRef,
                                           priority, flowRef, 0, 0,
                                           COOKIE_VM_FIB_TABLE, matches, instructions);

    if (addOrRemove == NwConstants.ADD_FLOW) {
      mdsalManager.installFlow(flowEntity);
    } else {
      mdsalManager.removeFlow(flowEntity);
    }
  }

  private void makeLFibTableEntry(long dpId, long label, long groupId,
                                  String nextHop, int addOrRemove) {
    List<MatchInfo> matches = new ArrayList<MatchInfo>();
    matches.add(new MatchInfo(MatchFieldType.eth_type,
                              new long[] { 0x8847L }));
    matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(label)}));

    List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
    List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
    actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{Long.toString(label)}));
    actionsInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));
    instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));

    // Install the flow entry in L3_LFIB_TABLE
    String flowRef = getFlowRef(dpId, L3_LFIB_TABLE, label, nextHop);

    FlowEntity flowEntity;
    flowEntity = MDSALUtil.buildFlowEntity(dpId, L3_LFIB_TABLE, flowRef,
                                           DEFAULT_FIB_FLOW_PRIORITY, flowRef, 0, 0,
                                           COOKIE_VM_LFIB_TABLE, matches, instructions);

    if (addOrRemove == NwConstants.ADD_FLOW) {
      mdsalManager.installFlow(flowEntity);
    } else {
      mdsalManager.removeFlow(flowEntity);
    }
    LOG.info("LFIB Entry for dpID {} : label : {} grpup {} modified successfully {}",dpId, label, groupId );
  }

  private String getFlowRef(long dpnId, short tableId, long label, String nextHop) {
    return new StringBuilder(64).append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
        .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
        .append(label).append(NwConstants.FLOWID_SEPARATOR)
        .append(nextHop).toString();
  }

  private String getFlowRef(long dpnId, short tableId, String rd, InetAddress destPrefix) {
    return new StringBuilder(64).append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
        .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
        .append(rd).append(NwConstants.FLOWID_SEPARATOR)
        .append(destPrefix.getHostAddress()).toString();
  }

  private GetEgressPointerOutput resolveAdjacency(final long dpId, final long vpnId, final VrfTablesKey vrfTableKey,
                        final VrfEntry vrfEntry) {
    GetEgressPointerOutput adjacency = null;
    try {
      Future<RpcResult<GetEgressPointerOutput>> result =
          l3nexthopService.getEgressPointer(new GetEgressPointerInputBuilder().setDpnId(dpId)
                                                .setIpPrefix(vrfEntry.getDestPrefix())
                                                .setNexthopIp(vrfEntry.getNextHopAddress())
                                                .setVpnId(vpnId)
                                                .build());
      RpcResult<GetEgressPointerOutput> rpcResult = result.get();
      if (rpcResult.isSuccessful()) {
        adjacency = rpcResult.getResult();
      } else {
        LOG.error("Next hop information not available");
      }
    } catch (NullPointerException | InterruptedException | ExecutionException e) {
      LOG.trace("", e);
    }
    return adjacency;
  }

  private List<Long> getDpnsByVpn(long vpnId) {
    // TODO: get list of dpns from vpnmanager, for all dpns List<Long>
    return new ArrayList<>();
  }

  private Long getVpnId(String rd) {
    Long vpnId = null;
    InstanceIdentifier<VpnInstances> id = InstanceIdentifier.create(VpnInstances.class);
    Optional<VpnInstances> vpnInstances = read(LogicalDatastoreType.OPERATIONAL, id);
    if(vpnInstances.isPresent()) {
      List<VpnInstance> vpns = vpnInstances.get().getVpnInstance();
      for(VpnInstance vpn : vpns) {
        if(vpn.getIpv4Family().getRouteDistinguisher().equals(rd)) {
          VpnInstance1 vpnInstanceId = vpn.getAugmentation(VpnInstance1.class);
          if (vpnInstanceId != null) {
            vpnId = vpnInstanceId.getVpnId();
            break;
          }
        }
      }
    }
    return vpnId;
  }
}

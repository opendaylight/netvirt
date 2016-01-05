/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fibmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnmanager.api.IVpnManager;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.PrefixToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibManager extends AbstractDataChangeListener<VrfEntry> implements AutoCloseable{
  private static final Logger LOG = LoggerFactory.getLogger(FibManager.class);
  private static final String FLOWID_PREFIX = "L3.";
  private ListenerRegistration<DataChangeListener> listenerRegistration;
  private final DataBroker broker;
  private IMdsalApiManager mdsalManager;
  private IVpnManager vpnmanager;
  private NexthopManager nextHopManager;
  private ItmRpcService itmManager;
  private OdlInterfaceRpcService interfaceManager;

  private static final short L3_FIB_TABLE = 21;
  private static final short L3_LFIB_TABLE = 20;
  private static final short L3_PROTOCOL_TABLE = 36;
  private static final short L3_INTERFACE_TABLE = 80;
  public static final short LPORT_DISPATCHER_TABLE = 30;
  private static final BigInteger COOKIE_VM_LFIB_TABLE = new BigInteger("8000002", 16);
  private static final BigInteger COOKIE_VM_FIB_TABLE =  new BigInteger("8000003", 16);
  private static final int DEFAULT_FIB_FLOW_PRIORITY = 10;
  private static final BigInteger METADATA_MASK_CLEAR = new BigInteger("000000FFFFFFFFFF", 16);
  private static final BigInteger CLEAR_METADATA = BigInteger.valueOf(0);


  private static final FutureCallback<Void> DEFAULT_CALLBACK =
      new FutureCallback<Void>() {
        public void onSuccess(Void result) {
          LOG.debug("Success in Datastore write operation");
        }

        public void onFailure(Throwable error) {
          LOG.error("Error in Datastore write operation", error);
        };
      };

  public FibManager(final DataBroker db) {
    super(VrfEntry.class);
    broker = db;
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

  public void setNextHopManager(NexthopManager nextHopManager) {
    this.nextHopManager = nextHopManager;
  }

  public void setMdsalManager(IMdsalApiManager mdsalManager) {
    this.mdsalManager = mdsalManager;
  }

  public void setVpnmanager(IVpnManager vpnmanager) {
    this.vpnmanager = vpnmanager;
  }

  public void setITMRpcService(ItmRpcService itmManager) {
      this.itmManager = itmManager;
  }
  
  public void setInterfaceManager(OdlInterfaceRpcService ifManager) {
      this.interfaceManager = ifManager;
  }

  private void registerListener(final DataBroker db) {
    try {
      listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                                                           getWildCardPath(), FibManager.this, DataChangeScope.SUBTREE);
    } catch (final Exception e) {
      LOG.error("FibManager DataChange listener registration fail!", e);
      throw new IllegalStateException("FibManager registration Listener failed.", e);
    }
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

  private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                                                 InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
    WriteTransaction tx = broker.newWriteOnlyTransaction();
    tx.put(datastoreType, path, data, true);
    Futures.addCallback(tx.submit(), callback);
  }

  @Override
  protected void add(final InstanceIdentifier<VrfEntry> identifier,
                     final VrfEntry vrfEntry) {
    LOG.trace("key: " + identifier + ", value=" + vrfEntry );
    createFibEntries(identifier, vrfEntry);
  }

  @Override
  protected void remove(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry) {
    LOG.trace("key: " + identifier + ", value=" + vrfEntry);
    deleteFibEntries(identifier, vrfEntry);
  }

  @Override
  protected void update(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update) {
    LOG.trace("key: " + identifier + ", original=" + original + ", update=" + update );
    createFibEntries(identifier, update);
  }

  private void createFibEntries(final InstanceIdentifier<VrfEntry> identifier,
                                final VrfEntry vrfEntry) {
    final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
    Preconditions.checkNotNull(vrfTableKey, "VrfTablesKey cannot be null or empty!");
    Preconditions.checkNotNull(vrfEntry, "VrfEntry cannot be null or empty!");

    VpnInstanceOpDataEntry vpnInstance = getVpnInstance(vrfTableKey.getRouteDistinguisher());
    Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available!");
    Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId() + "has null vpnId!");

    Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
    if (vpnToDpnList != null) {
      BigInteger localDpnId = createLocalFibEntry(vpnInstance.getVpnId(),
                          vrfTableKey.getRouteDistinguisher(), vrfEntry);
      for (VpnToDpnList curDpn : vpnToDpnList) {
        if (!curDpn.getDpnId().equals(localDpnId)) {
          createRemoteFibEntry(localDpnId, curDpn.getDpnId(), vpnInstance.getVpnId(),
                               vrfTableKey, vrfEntry);
        }
      }
    }
  }

  public BigInteger createLocalFibEntry(Long vpnId, String rd, VrfEntry vrfEntry) {
    BigInteger localDpnId = BigInteger.ZERO;
    Prefixes localNextHopInfo = getPrefixToInterface(vpnId, vrfEntry.getDestPrefix());
    boolean staticRoute = false;

    //If the vrf entry is a static/extra route, the nexthop of the entry would be a adjacency in the vpn
    if(localNextHopInfo == null) {
      localNextHopInfo = getPrefixToInterface(vpnId, vrfEntry.getNextHopAddress() + "/32");
      staticRoute = true;
    }

    if(localNextHopInfo != null) {
      localDpnId = localNextHopInfo.getDpnId();
      long groupId = nextHopManager.createLocalNextHop(vpnId, localDpnId, localNextHopInfo.getVpnInterfaceName(),
                                                        (staticRoute == true) ? vrfEntry.getNextHopAddress() + "/32" : vrfEntry.getDestPrefix());
      List<ActionInfo> actionInfos = new ArrayList<ActionInfo>();

      actionInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId)}));

      makeConnectedRoute(localDpnId, vpnId, vrfEntry, rd, actionInfos, NwConstants.ADD_FLOW);
      makeLFibTableEntry(localDpnId, vrfEntry.getLabel(), groupId, vrfEntry.getNextHopAddress(), NwConstants.ADD_FLOW);

      LOG.debug("Installing tunnel table entry on dpn {} for interface {} with label {}", 
                      localDpnId, localNextHopInfo.getVpnInterfaceName(), vrfEntry.getLabel());
      makeTunnelTableEntry(localDpnId, vrfEntry.getLabel(), groupId);

    }
    return localDpnId;
  }

  private void makeTunnelTableEntry(BigInteger dpId, long label, long groupId/*String egressInterfaceName*/) {
      List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
      actionsInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));


      createTerminatingServiceActions(dpId, (int)label, actionsInfos);

      LOG.debug("Terminating service Entry for dpID {} : label : {} egress : {} installed successfully {}",
              dpId, label, groupId);
  }

  public void createTerminatingServiceActions( BigInteger destDpId, int label, List<ActionInfo> actionsInfos) {
    // FIXME
/*      List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();

      LOG.info("create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}", destDpId , label,actionsInfos);

      // Matching metadata
      mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {
                                  MetaDataUtil.getTunnelIdWithValidVniBitAndVniSet(label),
                                  MetaDataUtil.METADA_MASK_TUNNEL_ID }));

      List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
      mkInstructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));

      FlowEntity terminatingServiceTableFlowEntity = MDSALUtil.buildFlowEntity(destDpId,ITMConstants.TERMINATING_SERVICE_TABLE,
                      getFlowRef(destDpId, ITMConstants.TERMINATING_SERVICE_TABLE,label), 5, String.format("%s:%d","TST Flow Entry ",label),
                      0, 0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(label)),mkMatches, mkInstructions);

      mdsalManager.installFlow(terminatingServiceTableFlowEntity);*/
 }

  private void removeTunnelTableEntry(BigInteger dpId, long label) {
      // FIXME
      // itmManager.removeTerminatingServiceAction(dpId, (int)label);

      // LOG.debug("Terminating service Entry for dpID {} : label : {} removed successfully {}",dpId, label);
  }

  public BigInteger deleteLocalFibEntry(Long vpnId, String rd, VrfEntry vrfEntry) {
    BigInteger localDpnId = BigInteger.ZERO;
    VpnNexthop localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, vrfEntry.getDestPrefix());
    boolean staticRoute = false;

    //If the vrf entry is a static/extra route, the nexthop of the entry would be a adjacency in the vpn
    if(localNextHopInfo == null) {
      localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, vrfEntry.getNextHopAddress() + "/32");
      staticRoute = true;
    }

    if(localNextHopInfo != null) {
      localDpnId = localNextHopInfo.getDpnId();
      if (getPrefixToInterface(vpnId, (staticRoute == true) ? vrfEntry.getNextHopAddress() + "/32" : vrfEntry.getDestPrefix()) == null) {
        makeConnectedRoute(localDpnId, vpnId, vrfEntry, rd, null /* invalid */,
                           NwConstants.DEL_FLOW);
        makeLFibTableEntry(localDpnId, vrfEntry.getLabel(), 0 /* invalid */,
                           vrfEntry.getNextHopAddress(), NwConstants.DEL_FLOW);
        removeTunnelTableEntry(localDpnId, vrfEntry.getLabel());
        deleteLocalAdjacency(localDpnId, vpnId, (staticRoute == true) ? vrfEntry.getNextHopAddress() + "/32" : vrfEntry.getDestPrefix());
      }
    }
    return localDpnId;
  }

  private InstanceIdentifier<Prefixes> getPrefixToInterfaceIdentifier(Long vpnId, String ipPrefix) {
    return InstanceIdentifier.builder(PrefixToInterface.class)
        .child(VpnIds.class, new VpnIdsKey(vpnId)).child(Prefixes.class, new PrefixesKey(ipPrefix)).build();
  }

  private Prefixes getPrefixToInterface(Long vpnId, String ipPrefix) {
    Optional<Prefixes> localNextHopInfoData =
        read(LogicalDatastoreType.OPERATIONAL, getPrefixToInterfaceIdentifier(vpnId, ipPrefix));
    return  localNextHopInfoData.isPresent() ? localNextHopInfoData.get() : null;
  }

  private void createRemoteFibEntry(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                    final long vpnId, final VrfTablesKey vrfTableKey,
                                    final VrfEntry vrfEntry) {
    String rd = vrfTableKey.getRouteDistinguisher();
    LOG.debug("adding route " + vrfEntry.getDestPrefix() + " " + rd);

    List<ActionInfo> actionInfos = resolveAdjacency(localDpnId, remoteDpnId, vpnId, vrfEntry);
    if(actionInfos == null) {
      LOG.error("Could not get nexthop group id for nexthop: {} in vpn {}",
                                   vrfEntry.getNextHopAddress(), rd);
      LOG.warn("Failed to add Route: {} in vpn: {}",
                             vrfEntry.getDestPrefix(), rd);
      return;
    }
    BigInteger dpnId = nextHopManager.getDpnForPrefix(vpnId, vrfEntry.getDestPrefix());
    if(dpnId == null) {
        //This route may be extra route... try to query with nexthop Ip
        LOG.debug("Checking for extra route to install remote fib entry {}", vrfEntry.getDestPrefix());
        dpnId = nextHopManager.getDpnForPrefix(vpnId, vrfEntry.getNextHopAddress() + "/32");
    }
    if(dpnId == null) {
        LOG.debug("Push label action for prefix {}", vrfEntry.getDestPrefix());
        actionInfos.add(new ActionInfo(ActionType.push_mpls, new String[] { null }));
        actionInfos.add(new ActionInfo(ActionType.set_field_mpls_label, new String[] { Long.toString(vrfEntry.getLabel())}));
    } else {
        int label = vrfEntry.getLabel().intValue();
        LOG.debug("adding set tunnel id action for label {}", label);
        actionInfos.add(new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[] {
                MetaDataUtil.getTunnelIdWithValidVniBitAndVniSet(label),
                MetaDataUtil.METADA_MASK_VALID_TUNNEL_ID_BIT_AND_TUNNEL_ID }));
    }

    makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, actionInfos, NwConstants.ADD_FLOW);
    LOG.debug(
        "Successfully added fib entry for " + vrfEntry.getDestPrefix() + " vpnId " + vpnId);
  }

  private void deleteFibEntries(final InstanceIdentifier<VrfEntry> identifier,
                                final VrfEntry vrfEntry) {
    final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
    Preconditions.checkNotNull(vrfTableKey, "VrfTablesKey cannot be null or empty!");
    Preconditions.checkNotNull(vrfEntry, "VrfEntry cannot be null or empty!");

    VpnInstanceOpDataEntry vpnInstance = getVpnInstance(vrfTableKey.getRouteDistinguisher());
    Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available!");
    Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
    if (vpnToDpnList != null) {
      BigInteger localDpnId = deleteLocalFibEntry(vpnInstance.getVpnId(),
                          vrfTableKey.getRouteDistinguisher(), vrfEntry);
      for (VpnToDpnList curDpn : vpnToDpnList) {
        if (!curDpn.getDpnId().equals(localDpnId)) {
          deleteRemoteRoute(localDpnId, curDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry);
        }
      }

    }
  }

  public void deleteRemoteRoute(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                final long vpnId, final VrfTablesKey vrfTableKey,
                                final VrfEntry vrfEntry) {
    LOG.debug("deleting route "+ vrfEntry.getDestPrefix() + " "+vpnId);
    String rd = vrfTableKey.getRouteDistinguisher();
    List<ActionInfo> actionInfos = resolveAdjacency(localDpnId, remoteDpnId, vpnId, vrfEntry);
    if(actionInfos == null) {
      LOG.error("Could not get nexthop group id for nexthop: {} in vpn {}",
                vrfEntry.getNextHopAddress(), rd);
      LOG.warn("Failed to delete Route: {} in vpn: {}",
               vrfEntry.getDestPrefix(), rd);
      return;
    }

    makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW);
    LOG.debug("Successfully delete fib entry for "+ vrfEntry.getDestPrefix() + " vpnId "+vpnId);
  }

  private long getIpAddress(byte[] rawIpAddress) {
    return (((rawIpAddress[0] & 0xFF) << (3 * 8)) + ((rawIpAddress[1] & 0xFF) << (2 * 8))
            + ((rawIpAddress[2] & 0xFF) << (1 * 8)) + (rawIpAddress[3] & 0xFF)) & 0xffffffffL;
  }

  private void makeConnectedRoute(BigInteger dpId, long vpnId, VrfEntry vrfEntry, String rd,
                                  List<ActionInfo> actionInfos, int addOrRemove) {
    LOG.trace("makeConnectedRoute: vrfEntry {}",vrfEntry);
    String values[] = vrfEntry.getDestPrefix().split("/");
    String ipAddress = values[0];
    int prefixLength = (values.length == 1) ? 0 : Integer.parseInt(values[1]);
    LOG.debug("Adding route to DPN. ip {} masklen {}", ipAddress, prefixLength);
    InetAddress destPrefix = null;
    try {
      destPrefix = InetAddress.getByName(ipAddress);
    } catch (UnknownHostException e) {
      LOG.error("UnknowHostException in addRoute. Failed  to add Route for ipPrefix {}", vrfEntry.getDestPrefix());
      return;
    }

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
    if(addOrRemove == NwConstants.ADD_FLOW) {
      instructions.add(new InstructionInfo(InstructionType.write_actions, actionInfos));
    }

    String flowRef = getFlowRef(dpId, L3_FIB_TABLE, rd, destPrefix);

    FlowEntity flowEntity;

    int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
    flowEntity = MDSALUtil.buildFlowEntity(dpId, L3_FIB_TABLE, flowRef,
                                           priority, flowRef, 0, 0,
                                           COOKIE_VM_FIB_TABLE, matches, instructions);

    if (addOrRemove == NwConstants.ADD_FLOW) {
      /* We need to call sync API to install flow so that 2 DS operations on the same object do not
      * happen at same time. However, MDSALManager's syncInstallFlow takes a delay time (or uses a default one) to wait
      * for or for notification that operational DS write for flows is done. We do not turn on the stats writing for flows,
      * so that notification never comes, so we do not need that wait. Sending the lowest value of wait "1 ms" since 0 wait means
      * wait indefinitely. */
      // FIXME: sync calls.
      //mdsalManager.syncInstallFlow(flowEntity, 1);
      mdsalManager.installFlow(flowEntity);
    } else {
      // FIXME: sync calls.
      // mdsalManager.syncRemoveFlow(flowEntity, 1);
      mdsalManager.removeFlow(flowEntity);
    }
  }

  private void makeLFibTableEntry(BigInteger dpId, long label, long groupId,
                                  String nextHop, int addOrRemove) {
    List<MatchInfo> matches = new ArrayList<MatchInfo>();
    matches.add(new MatchInfo(MatchFieldType.eth_type,
                              new long[] { 0x8847L }));
    matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(label)}));

    List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
    List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
    actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{}));
    actionsInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));
    instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));

    // Install the flow entry in L3_LFIB_TABLE
    String flowRef = getFlowRef(dpId, L3_LFIB_TABLE, label, nextHop);

    FlowEntity flowEntity;
    flowEntity = MDSALUtil.buildFlowEntity(dpId, L3_LFIB_TABLE, flowRef,
                                           DEFAULT_FIB_FLOW_PRIORITY, flowRef, 0, 0,
                                           COOKIE_VM_LFIB_TABLE, matches, instructions);

    if (addOrRemove == NwConstants.ADD_FLOW) {
      /* We need to call sync API to install flow so that 2 DS operations on the same object do not
      * happen at same time. However, MDSALManager's syncInstallFlow takes a delay time (or uses a default one) to wait
      * for or for notification that operational DS write for flows is done. We do not turn on the stats writing for flows,
      * so that notification never comes, so we do not need that wait. Sending the lowest value of wait "1 ms" since 0 wait means
      * wait indefinitely. */

      // FIXME:
      // mdsalManager.syncInstallFlow(flowEntity, 1);
      mdsalManager.installFlow(flowEntity);
    } else {
      // FIXME:
      // mdsalManager.syncRemoveFlow(flowEntity, 1);
      mdsalManager.removeFlow(flowEntity);
    }
    LOG.debug("LFIB Entry for dpID {} : label : {} group {} modified successfully {}",dpId, label, groupId );
  }

  private void deleteLocalAdjacency(final BigInteger dpId, final long vpnId, final String ipAddress) {
    LOG.trace("deleteLocalAdjacency called with dpid {}, vpnId{}, ipAddress {}",dpId, vpnId, ipAddress);
    try {
      nextHopManager.removeLocalNextHop(dpId, vpnId, ipAddress);
    } catch (NullPointerException e) {
      LOG.trace("", e);
    }
  }

  public void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd) {
    LOG.trace("New dpn {} for vpn {} : populateFibOnNewDpn", dpnId, rd);
    InstanceIdentifier<VrfTables> id = buildVrfId(rd);
    Optional<VrfTables> vrfTable = read(LogicalDatastoreType.OPERATIONAL, id);
    if(vrfTable.isPresent()) {
      for(VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
        // Passing null as we don't know the dpn
        // to which prefix is attached at this point
        createRemoteFibEntry(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry);
      }
    }
  }

  public void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd) {
    LOG.trace("Remove dpn {} for vpn {} : cleanUpDpnForVpn", dpnId, rd);
    InstanceIdentifier<VrfTables> id = buildVrfId(rd);
    Optional<VrfTables> vrfTable = read(LogicalDatastoreType.OPERATIONAL, id);
    if(vrfTable.isPresent()) {
      for(VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
        // Passing null as we don't know the dpn
        // to which prefix is attached at this point
        deleteRemoteRoute(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry);
      }
    }
  }

  public static InstanceIdentifier<VrfTables> buildVrfId(String rd) {
    InstanceIdentifierBuilder<VrfTables> idBuilder =
        InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
    InstanceIdentifier<VrfTables> id = idBuilder.build();
    return id;
  }

  private String getFlowRef(BigInteger dpnId, short tableId, long label, String nextHop) {
    return new StringBuilder(64).append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
        .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
        .append(label).append(NwConstants.FLOWID_SEPARATOR)
        .append(nextHop).toString();
  }

  private String getFlowRef(BigInteger dpnId, short tableId, String rd, InetAddress destPrefix) {
    return new StringBuilder(64).append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
        .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
        .append(rd).append(NwConstants.FLOWID_SEPARATOR)
        .append(destPrefix.getHostAddress()).toString();
  }

  protected List<ActionInfo> resolveAdjacency(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                              final long vpnId, final VrfEntry vrfEntry) {
    List<ActionInfo> adjacency = null;
    LOG.trace("resolveAdjacency called with localdpid{} remotedpid {}, vpnId{}, VrfEntry {}", localDpnId, remoteDpnId, vpnId, vrfEntry);;
    try {
      adjacency =
          nextHopManager.getRemoteNextHopPointer(localDpnId, remoteDpnId, vpnId,
                                                 vrfEntry.getDestPrefix(),
                                                 vrfEntry.getNextHopAddress());
    } catch (NullPointerException e) {
      LOG.trace("", e);
    }
    return adjacency;
  }

  protected VpnInstanceOpDataEntry getVpnInstance(String rd) {
    InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class).child(
        VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
    Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = read(LogicalDatastoreType.OPERATIONAL, id);
    if(vpnInstanceOpData.isPresent()) {
      return vpnInstanceOpData.get();
    }
    return null;
  }

    public void processNodeAdd(BigInteger dpnId) {
        LOG.debug("Received notification to install TableMiss entries for dpn {} ", dpnId);
        makeTableMissFlow(dpnId, NwConstants.ADD_FLOW);
        makeProtocolTableFlow(dpnId, NwConstants.ADD_FLOW);
        makeL3IntfTblMissFlow(dpnId, NwConstants.ADD_FLOW);
    }

    private void makeTableMissFlow(BigInteger dpnId, int addOrRemove) {
        final BigInteger COOKIE_TABLE_MISS = new BigInteger("1030000", 16);
        // Instruction to goto L3 InterfaceTable
        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { L3_INTERFACE_TABLE }));
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        FlowEntity flowEntityLfib = MDSALUtil.buildFlowEntity(dpnId, L3_LFIB_TABLE,
                getFlowRef(dpnId, L3_LFIB_TABLE, NwConstants.TABLE_MISS_FLOW),
                NwConstants.TABLE_MISS_PRIORITY, "Table Miss", 0, 0, COOKIE_TABLE_MISS, matches, instructions);

        FlowEntity flowEntityFib = MDSALUtil.buildFlowEntity(dpnId,L3_FIB_TABLE, getFlowRef(dpnId, L3_FIB_TABLE, NwConstants.TABLE_MISS_FLOW),
                NwConstants.TABLE_MISS_PRIORITY, "FIB Table Miss Flow", 0, 0, COOKIE_VM_FIB_TABLE,
                matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("Invoking MDSAL to install Table Miss Entries");
            mdsalManager.installFlow(flowEntityLfib);
            mdsalManager.installFlow(flowEntityFib);
        } else {
            mdsalManager.removeFlow(flowEntityLfib);
            mdsalManager.removeFlow(flowEntityFib);

        }
    }

    private String getFlowRef(BigInteger dpnId, short tableId, int tableMiss) {
        return new StringBuffer().append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR).append(tableMiss)
                .append(FLOWID_PREFIX).toString();
    }

  /*
   * Install flow entry in protocol table to forward mpls
   * coming through gre tunnel to LFIB table.
   */
  private void makeProtocolTableFlow(BigInteger dpnId, int addOrRemove) {
    final BigInteger COOKIE_PROTOCOL_TABLE = new BigInteger("1070000", 16);
    // Instruction to goto L3 InterfaceTable
    List<InstructionInfo> instructions = new ArrayList<>();
    instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] {L3_LFIB_TABLE}));
    List<MatchInfo> matches = new ArrayList<MatchInfo>();
    matches.add(new MatchInfo(MatchFieldType.eth_type,
                              new long[] { 0x8847L }));
    FlowEntity flowEntityToLfib = MDSALUtil.buildFlowEntity(dpnId, L3_PROTOCOL_TABLE,
                                                          getFlowRef(dpnId, L3_PROTOCOL_TABLE,
                                                                     L3_LFIB_TABLE),
                                                          DEFAULT_FIB_FLOW_PRIORITY,
                                                          "Protocol Table For LFIB",
                                                          0, 0,
                                                          COOKIE_PROTOCOL_TABLE,
                                                          matches, instructions);

    if (addOrRemove == NwConstants.ADD_FLOW) {
      LOG.debug("Invoking MDSAL to install Protocol Entries for dpn {}", dpnId);
      mdsalManager.installFlow(flowEntityToLfib);
    } else {
      mdsalManager.removeFlow(flowEntityToLfib);
    }
  }

  public List<String> printFibEntries() {
    List<String> result = new ArrayList<String>();
    result.add(String.format("   %-7s  %-20s  %-20s  %-7s", "RD", "Prefix", "Nexthop", "Label"));
    result.add("-------------------------------------------------------------------");
    InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);
    Optional<FibEntries> fibEntries = read(LogicalDatastoreType.OPERATIONAL, id);
    if (fibEntries.isPresent()) {
      List<VrfTables> vrfTables = fibEntries.get().getVrfTables();
      for (VrfTables vrfTable : vrfTables) {
        for (VrfEntry vrfEntry : vrfTable.getVrfEntry()) {
          result.add(String.format("   %-7s  %-20s  %-20s  %-7s", vrfTable.getRouteDistinguisher(),
                  vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddress(), vrfEntry.getLabel()));
        }
      }
    }
    return result;
  }

  private void makeL3IntfTblMissFlow(BigInteger dpnId, int addOrRemove) {
    List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
    List<MatchInfo> matches = new ArrayList<MatchInfo>();
    final BigInteger COOKIE_TABLE_MISS = new BigInteger("1030000", 16);
    // Instruction to clear metadata except SI and LportTag bits
    instructions.add(new InstructionInfo(InstructionType.write_metadata, new BigInteger[] {
                    CLEAR_METADATA, METADATA_MASK_CLEAR }));
    // Instruction to clear action
    instructions.add(new InstructionInfo(InstructionType.clear_actions));
    // Instruction to goto L3 InterfaceTable

    instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { LPORT_DISPATCHER_TABLE }));

    FlowEntity flowEntityL3Intf = MDSALUtil.buildFlowEntity(dpnId, L3_INTERFACE_TABLE,
            getFlowRef(dpnId, L3_INTERFACE_TABLE, NwConstants.TABLE_MISS_FLOW),
            NwConstants.TABLE_MISS_PRIORITY, "L3 Interface Table Miss", 0, 0, COOKIE_TABLE_MISS, matches, instructions);
    if (addOrRemove == NwConstants.ADD_FLOW) {
      LOG.info("Invoking MDSAL to install L3 interface Table Miss Entries");
      mdsalManager.installFlow(flowEntityL3Intf);
    } else {
      mdsalManager.removeFlow(flowEntityL3Intf);
    }
  }

}

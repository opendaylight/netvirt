/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PrefixToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.overlay.rev150105.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.VpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.vpn.Extraroute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.vpn.ExtrarouteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.vpn.ExtrarouteKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeMplsOverGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetTunnelTypeInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetTunnelTypeOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
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
  private IdManagerService idManager;
  private static final BigInteger COOKIE_VM_LFIB_TABLE = new BigInteger("8000002", 16);
  private static final BigInteger COOKIE_VM_FIB_TABLE =  new BigInteger("8000003", 16);
  private static final int DEFAULT_FIB_FLOW_PRIORITY = 10;
  private static final BigInteger METADATA_MASK_CLEAR = new BigInteger("000000FFFFFFFFFF", 16);
  private static final BigInteger CLEAR_METADATA = BigInteger.valueOf(0);
  public static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);


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

    public NexthopManager getNextHopManager() {
        return this.nextHopManager;
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

  public void setIdManager(IdManagerService idManager) {
      this.idManager = idManager;
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


  private InstanceIdentifier<VrfEntry> getWildCardPath() {
    return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class).child(VrfEntry.class);
  }


  @Override
  protected void add(final InstanceIdentifier<VrfEntry> identifier,
                     final VrfEntry vrfEntry) {
    LOG.trace("Add key: " + identifier + ", value=" + vrfEntry );
    createFibEntries(identifier, vrfEntry);
  }

  @Override
  protected void remove(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry) {
    LOG.trace("Remove key: " + identifier + ", value=" + vrfEntry);
    deleteFibEntries(identifier, vrfEntry);
  }

  @Override
  protected void update(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update) {
    LOG.trace("Update key: " + identifier + ", original=" + original + ", update=" + update );
    if (original.getAugmentation(SubnetRoute.class) != null && update.getAugmentation(SubnetRoute.class) == null)
        return;
    createFibEntries(identifier, update);
  }

  private void createFibEntries(final InstanceIdentifier<VrfEntry> identifier,
                                final VrfEntry vrfEntry) {
    final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
    Preconditions.checkNotNull(vrfTableKey, "VrfTablesKey cannot be null or empty!");
    Preconditions.checkNotNull(vrfEntry, "VrfEntry cannot be null or empty!");

    VpnInstanceOpDataEntry vpnInstance = getVpnInstance(vrfTableKey.getRouteDistinguisher());
    Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
    Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId() + " has null vpnId!");

    Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
    Long vpnId = vpnInstance.getVpnId();
    String rd = vrfTableKey.getRouteDistinguisher();
    SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
    if (subnetRoute != null) {
        LOG.trace("SubnetRoute augmented vrfentry found for rd {} prefix {} with elantag {}",
                rd, vrfEntry.getDestPrefix(), subnetRoute.getElantag());
        long elanTag = subnetRoute.getElantag();
        if (vpnToDpnList != null) {
            for (VpnToDpnList curDpn : vpnToDpnList) {
                installSubnetRouteInFib(curDpn.getDpnId(),elanTag, rd, vpnId.longValue(), vrfEntry);
            }
        }
        return;
    }
    BigInteger localDpnId = createLocalFibEntry(vpnInstance.getVpnId(),
            rd, vrfEntry);
    if (vpnToDpnList != null) {
        for (VpnToDpnList curDpn : vpnToDpnList) {
            if (!curDpn.getDpnId().equals(localDpnId)) {
                createRemoteFibEntry(localDpnId, curDpn.getDpnId(), vpnInstance.getVpnId(),
                        vrfTableKey, vrfEntry);
            }
        }
    }
  }

  private void installSubnetRouteInFib(BigInteger dpnId, long elanTag, String rd,
                                       long vpnId, VrfEntry vrfEntry){
      List<InstructionInfo> instructions = new ArrayList<>();

      instructions.add(new InstructionInfo(InstructionType.write_metadata,  new BigInteger[] { (BigInteger.valueOf(elanTag)).shiftLeft(24), MetaDataUtil.METADATA_MASK_SERVICE }));
      instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_SUBNET_ROUTE_TABLE }));
      makeConnectedRoute(dpnId,vpnId,vrfEntry,rd,instructions,NwConstants.ADD_FLOW);

      List<ActionInfo> actionsInfos = new ArrayList<>();
      // reinitialize instructions list for LFIB Table
      instructions = new ArrayList<>();

      actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{}));
      instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
      instructions.add(new InstructionInfo(InstructionType.write_metadata,  new BigInteger[] { (BigInteger.valueOf(elanTag)).shiftLeft(24), MetaDataUtil.METADATA_MASK_SERVICE }));
      instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_SUBNET_ROUTE_TABLE }));

      makeLFibTableEntry(dpnId,vrfEntry.getLabel(),instructions,
              vrfEntry.getNextHopAddress(),NwConstants.ADD_FLOW);
      // TODO makeTunnelTableEntry();
  }

  private  <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
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

  private void makeSubnetRouteTableMissFlow(BigInteger dpnId, int addOrRemove) {
      final BigInteger COOKIE_TABLE_MISS = new BigInteger("8000004", 16);
      List<ActionInfo> actionsInfos = new ArrayList<>();
      List<InstructionInfo> instructions = new ArrayList<>();
      actionsInfos.add(new ActionInfo(ActionType.punt_to_controller, new String[]{}));
      instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
      List<MatchInfo> matches = new ArrayList<>();
      String flowRef = getFlowRef(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, NwConstants.TABLE_MISS_FLOW);
      FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, flowRef,
              NwConstants.TABLE_MISS_PRIORITY, "Subnet Route Table Miss", 0, 0, COOKIE_TABLE_MISS, matches, instructions);

      if (addOrRemove == NwConstants.ADD_FLOW) {
          mdsalManager.installFlow(flowEntity);
      } else {
          mdsalManager.removeFlow(flowEntity);
      }
  }

  private Collection<BigInteger> getDpnsForVpn(VpnInstanceOpDataEntry vpnInstance) {
      Collection<BigInteger> dpns = new HashSet<>();
      for(VpnToDpnList dpn : vpnInstance.getVpnToDpnList()) {
          dpns.add(dpn.getDpnId());
      }

      return dpns;
  }

  public BigInteger createLocalFibEntry(Long vpnId, String rd, VrfEntry vrfEntry) {
    BigInteger localDpnId = BigInteger.ZERO;
    Prefixes localNextHopInfo = getPrefixToInterface(vpnId, vrfEntry.getDestPrefix());
    String localNextHopIP = vrfEntry.getDestPrefix();

    if(localNextHopInfo == null) {
        //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
        Extraroute extra_route = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
        if (extra_route != null) {
            localNextHopInfo = getPrefixToInterface(vpnId, extra_route.getNexthopIp() + "/32");
            localNextHopIP = extra_route.getNexthopIp() + "/32";
        }
    }

    if(localNextHopInfo != null) {
        localDpnId = localNextHopInfo.getDpnId();
        long groupId = nextHopManager.createLocalNextHop(vpnId, localDpnId, localNextHopInfo.getVpnInterfaceName(), localNextHopIP);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId)}));
        instructions.add(new InstructionInfo(InstructionType.write_actions,actionsInfos));
        makeConnectedRoute(localDpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW);

        actionsInfos= new ArrayList<>();
        instructions= new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{}));
        actionsInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));
        instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));
        makeLFibTableEntry(localDpnId, vrfEntry.getLabel(), instructions, vrfEntry.getNextHopAddress(), NwConstants.ADD_FLOW);

        LOG.debug("Installing tunnel table entry on dpn {} for interface {} with label {}",
                localDpnId, localNextHopInfo.getVpnInterfaceName(), vrfEntry.getLabel());
        makeTunnelTableEntry(localDpnId, vrfEntry.getLabel(), groupId);

    }
    return localDpnId;
  }

  private void makeTunnelTableEntry(BigInteger dpId, long label, long groupId/*String egressInterfaceName*/) {
      List<ActionInfo> actionsInfos = new ArrayList<>();
      actionsInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));


      createTerminatingServiceActions(dpId, (int)label, actionsInfos);

      LOG.debug("Terminating service Entry for dpID {} : label : {} egress : {} installed successfully",
              dpId, label, groupId);
  }

  public void createTerminatingServiceActions( BigInteger destDpId, int label, List<ActionInfo> actionsInfos) {
      List<MatchInfo> mkMatches = new ArrayList<>();

      LOG.info("create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}", destDpId , label,actionsInfos);

      // Matching metadata
      // FIXME vxlan vni bit set is not working properly with OVS.need to revisit
      mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(label)}));

      List<InstructionInfo> mkInstructions = new ArrayList<>();
      mkInstructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));

      FlowEntity terminatingServiceTableFlowEntity = MDSALUtil.buildFlowEntity(destDpId, NwConstants.INTERNAL_TUNNEL_TABLE,
                      getFlowRef(destDpId, NwConstants.INTERNAL_TUNNEL_TABLE,label), 5, String.format("%s:%d","TST Flow Entry ",label),
                      0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(label)),mkMatches, mkInstructions);

      mdsalManager.installFlow(terminatingServiceTableFlowEntity);
 }

  private void removeTunnelTableEntry(BigInteger dpId, long label) {
    FlowEntity flowEntity;
    LOG.info("remove terminatingServiceActions called with DpnId = {} and label = {}", dpId , label);
    List<MatchInfo> mkMatches = new ArrayList<>();
    // Matching metadata
    mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(label)}));
    flowEntity = MDSALUtil.buildFlowEntity(dpId,
                                           NwConstants.INTERNAL_TUNNEL_TABLE,
                                           getFlowRef(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, (int)label),
                                           5, String.format("%s:%d","TST Flow Entry ",label), 0, 0,
                                           COOKIE_TUNNEL.add(BigInteger.valueOf(label)), mkMatches, null);
    mdsalManager.removeFlow(flowEntity);
    LOG.debug("Terminating service Entry for dpID {} : label : {} removed successfully",dpId, label);
  }

  public BigInteger deleteLocalFibEntry(Long vpnId, String rd, VrfEntry vrfEntry) {
    BigInteger localDpnId = BigInteger.ZERO;
    boolean isExtraRoute = false;
    VpnNexthop localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, vrfEntry.getDestPrefix());
    String localNextHopIP = vrfEntry.getDestPrefix();

    if(localNextHopInfo == null) {
        //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
        Extraroute extra_route = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
        if (extra_route != null) {
            localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, extra_route.getNexthopIp() + "/32");
            localNextHopIP = extra_route.getNexthopIp() + "/32";
            isExtraRoute = true;
        }
    }


    if(localNextHopInfo != null) {
      localDpnId = localNextHopInfo.getDpnId();
      Prefixes prefix = getPrefixToInterface(vpnId, isExtraRoute ? localNextHopIP : vrfEntry.getDestPrefix());
        makeConnectedRoute(localDpnId, vpnId, vrfEntry, rd, null /* invalid */,
                           NwConstants.DEL_FLOW);
        makeLFibTableEntry(localDpnId, vrfEntry.getLabel(), null /* invalid */,
                           vrfEntry.getNextHopAddress(), NwConstants.DEL_FLOW);
        removeTunnelTableEntry(localDpnId, vrfEntry.getLabel());
        deleteLocalAdjacency(localDpnId, vpnId, localNextHopIP);
    }
    return localDpnId;
  }

  private InstanceIdentifier<Prefixes> getPrefixToInterfaceIdentifier(Long vpnId, String ipPrefix) {
    return InstanceIdentifier.builder(PrefixToInterface.class)
        .child(VpnIds.class, new VpnIdsKey(vpnId)).child(Prefixes.class, new PrefixesKey(ipPrefix)).build();
  }

  private Prefixes getPrefixToInterface(Long vpnId, String ipPrefix) {
    Optional<Prefixes> localNextHopInfoData =
        FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, getPrefixToInterfaceIdentifier(vpnId, ipPrefix));
    return  localNextHopInfoData.isPresent() ? localNextHopInfoData.get() : null;
  }

    private InstanceIdentifier<Extraroute> getVpnToExtrarouteIdentifier(String vrfId, String ipPrefix) {
        return InstanceIdentifier.builder(VpnToExtraroute.class)
                .child(Vpn.class, new VpnKey(vrfId)).child(Extraroute.class,
                        new ExtrarouteKey(ipPrefix)).build();
    }

    private Extraroute getVpnToExtraroute(String rd, String ipPrefix) {
        Optional<Extraroute> extraRouteInfo =
                FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, getVpnToExtrarouteIdentifier(rd, ipPrefix));
        return  extraRouteInfo.isPresent() ? extraRouteInfo.get() : null;

    }

  private Class<? extends TunnelTypeBase> getTunnelType(String ifName) {
        try {
            Future<RpcResult<GetTunnelTypeOutput>> result = interfaceManager.getTunnelType(
        		  new GetTunnelTypeInputBuilder().setIntfName(ifName).build());
          RpcResult<GetTunnelTypeOutput> rpcResult = result.get();
          if(!rpcResult.isSuccessful()) {
              LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
          } else {
              return rpcResult.getResult().getTunnelType();
          }
    	  
      } catch (InterruptedException | ExecutionException e) {
          LOG.warn("Exception when getting tunnel interface Id for tunnel type {}", e);
      }
  
  return null;

  }
  private void createRemoteFibEntry(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                    final long vpnId, final VrfTablesKey vrfTableKey,
                                    final VrfEntry vrfEntry) {
    String rd = vrfTableKey.getRouteDistinguisher();
    LOG.debug("createremotefibentry: adding route {} for rd {}", vrfEntry.getDestPrefix(), rd);
    /********************************************/
    String tunnelInterface = resolveAdjacency(localDpnId, remoteDpnId, vpnId, vrfEntry, rd);
    if(tunnelInterface == null) {
      LOG.error("Could not get interface for nexthop: {} in vpn {}",
                                   vrfEntry.getNextHopAddress(), rd);
      LOG.warn("Failed to add Route: {} in vpn: {}",
                             vrfEntry.getDestPrefix(), rd);
      return;
    }
      List<ActionInfo> actionInfos = new ArrayList<>();
	Class<? extends TunnelTypeBase> tunnel_type = getTunnelType(tunnelInterface);
    if (tunnel_type.equals(TunnelTypeMplsOverGre.class)) {
        LOG.debug("Push label action for prefix {}", vrfEntry.getDestPrefix());
        actionInfos.add(new ActionInfo(ActionType.push_mpls, new String[] { null }));
        actionInfos.add(new ActionInfo(ActionType.set_field_mpls_label, new String[] { Long.toString(vrfEntry.getLabel())}));
    } else {
        int label = vrfEntry.getLabel().intValue();
        BigInteger tunnelId;
        // FIXME vxlan vni bit set is not working properly with OVS.need to revisit
        if(tunnel_type.equals(TunnelTypeVxlan.class)) {
        	tunnelId = BigInteger.valueOf(label);
        } else {
        	tunnelId = BigInteger.valueOf(label);
        }
        LOG.debug("adding set tunnel id action for label {}", label);
        actionInfos.add(new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[]{
                tunnelId}));
    }
    actionInfos.addAll(nextHopManager.getEgressActionsForInterface(tunnelInterface));
    List<InstructionInfo> instructions= new ArrayList<>();
    instructions.add(new InstructionInfo(InstructionType.write_actions, actionInfos));
/*
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
**/
      makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW);
      LOG.debug("Successfully added fib entry for prefix {} in vpn {} ", vrfEntry.getDestPrefix(), vpnId);
  }

  private void delIntfFromDpnToVpnList(long vpnId, BigInteger dpnId, String intfName, String rd) {
      InstanceIdentifier<VpnToDpnList> id = FibUtil.getVpnToDpnListIdentifier(rd, dpnId);
      Optional<VpnToDpnList> dpnInVpn = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
      if (dpnInVpn.isPresent()) {
          List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                  .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
          org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces
                  currVpnInterface = new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder().setInterfaceName(intfName).build();

          if (vpnInterfaces.remove(currVpnInterface)) {
              if (vpnInterfaces.isEmpty()) {
                  LOG.trace("Last vpn interface {} on dpn {} for vpn {}. Clean up fib in dpn", intfName, dpnId, rd);
                  FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id);
                  cleanUpDpnForVpn(dpnId, vpnId, rd);
              } else {
                  LOG.trace("Delete vpn interface {} from dpn {} to vpn {} list.", intfName, dpnId, rd);
                  FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                          org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                                  .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                          new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey(intfName)));
              }
          }
      }
  }

  private void cleanUpOpDataForFib(Long vpnId, String rd, final VrfEntry vrfEntry) {
    /* Get interface info from prefix to interface mapping;
        Use the interface info to get the corresponding vpn interface op DS entry,
        remove the adjacency corresponding to this fib entry.
        If adjacency removed is the last adjacency, clean up the following:
         - vpn interface from dpntovpn list, dpn if last vpn interface on dpn
         - prefix to interface entry
         - vpn interface op DS
     */
      LOG.debug("Cleanup of prefix {} in VPN {}", vrfEntry.getDestPrefix(), vpnId);
      Prefixes prefixInfo = getPrefixToInterface(vpnId, vrfEntry.getDestPrefix());
      Extraroute extraRoute = null;
      if (prefixInfo == null) {
          extraRoute = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
          if(extraRoute != null) {
              prefixInfo = getPrefixToInterface(vpnId, extraRoute.getNexthopIp() + "/32");
              //clean up the vpn to extra route entry in DS
              //FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, FibUtil.getVpnToExtrarouteIdentifier(rd,
                //      vrfEntry.getDestPrefix()));
          }
      }
      if (prefixInfo == null) {
          LOG.debug("Cleanup VPN Data Failed as unable to find prefix Info for prefix {}" , vrfEntry.getDestPrefix());
          return; //Don't have any info for this prefix (shouldn't happen); need to return
      }
      String ifName = prefixInfo.getVpnInterfaceName();
      synchronized (ifName.intern()) {
          Optional<VpnInterface> optvpnInterface = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, FibUtil.getVpnInterfaceIdentifier(ifName));
          if (optvpnInterface.isPresent()) {
              long associatedVpnId = FibUtil.getVpnId(broker, optvpnInterface.get().getVpnInstanceName());
              if (vpnId != associatedVpnId) {
                  LOG.warn("Prefixes {} are associated with different vpn instance with id : {} rather than {}",
                          vrfEntry.getDestPrefix(), associatedVpnId, vpnId);
                  LOG.trace("Releasing prefix label - rd {}, prefix {}", rd, vrfEntry.getDestPrefix());
                  FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                          FibUtil.getNextHopLabelKey(rd, vrfEntry.getDestPrefix()));
                  LOG.warn("Not proceeding with Cleanup op data for prefix {}", vrfEntry.getDestPrefix());
                  return;
              } else {
                  LOG.debug("Processing cleanup of prefix {} associated with vpn {}", vrfEntry.getDestPrefix(), associatedVpnId);
              }
          }
          if (extraRoute != null) {
              FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, FibUtil.getVpnToExtrarouteIdentifier(rd, vrfEntry.getDestPrefix()));
          }
          Optional<Adjacencies> optAdjacencies = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, FibUtil.getAdjListPath(ifName));
          int numAdj = 0;
          if (optAdjacencies.isPresent()) {
              numAdj = optAdjacencies.get().getAdjacency().size();
          }

          //remove adjacency corr to prefix
          if (numAdj > 1) {
              LOG.trace("cleanUpOpDataForFib: remove adjacency for prefix: {} {}", vpnId, vrfEntry.getDestPrefix());
              FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL,
                      FibUtil.getAdjacencyIdentifier(ifName, vrfEntry.getDestPrefix()));
          }

          if ((numAdj - 1) == 0) { //there are no adjacencies left for this vpn interface, clean up
              //clean up the vpn interface from DpnToVpn list
              LOG.trace("Clean up vpn interface {} from dpn {} to vpn {} list.", ifName, prefixInfo.getDpnId(), rd);
              FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL,
                      FibUtil.getVpnInterfaceIdentifier(ifName));
          }

          FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                  FibUtil.getNextHopLabelKey(rd, vrfEntry.getDestPrefix()));
      }
  }

  private void deleteFibEntries(final InstanceIdentifier<VrfEntry> identifier,
                                final VrfEntry vrfEntry) {
    final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
    Preconditions.checkNotNull(vrfTableKey, "VrfTablesKey cannot be null or empty!");
    Preconditions.checkNotNull(vrfEntry, "VrfEntry cannot be null or empty!");

    String rd  = vrfTableKey.getRouteDistinguisher();
    VpnInstanceOpDataEntry vpnInstance = getVpnInstance(vrfTableKey.getRouteDistinguisher());
    if (vpnInstance == null) {
        LOG.debug("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
        return;
    }
    Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
    SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
    if (subnetRoute != null) {
        if (vpnToDpnList != null) {
            for (VpnToDpnList curDpn : vpnToDpnList) {
                makeConnectedRoute(curDpn.getDpnId(), vpnInstance.getVpnId(), vrfEntry, vrfTableKey
                        .getRouteDistinguisher(), null, NwConstants.DEL_FLOW);
                makeLFibTableEntry(curDpn.getDpnId(), vrfEntry.getLabel(), null,
                        vrfEntry.getNextHopAddress(), NwConstants.DEL_FLOW);
            }
        }
        FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
              FibUtil.getNextHopLabelKey(rd, vrfEntry.getDestPrefix()));
        LOG.trace("deleteFibEntries: Released subnetroute label {} for rd {} prefix {}", vrfEntry.getLabel(), rd,
                vrfEntry.getDestPrefix());
        return;
    }
    BigInteger localDpnId = deleteLocalFibEntry(vpnInstance.getVpnId(),
            vrfTableKey.getRouteDistinguisher(), vrfEntry);
    if (vpnToDpnList != null) {
        for (VpnToDpnList curDpn : vpnToDpnList) {
            if (!curDpn.getDpnId().equals(localDpnId)) {
                deleteRemoteRoute(localDpnId, curDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry);
            }
        }
    }
    //The flow/group entry has been deleted from config DS; need to clean up associated operational
    //DS entries in VPN Op DS, VpnInstanceOpData and PrefixToInterface to complete deletion
    cleanUpOpDataForFib(vpnInstance.getVpnId(), vrfTableKey.getRouteDistinguisher(), vrfEntry);
  }

  public void deleteRemoteRoute(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                final long vpnId, final VrfTablesKey vrfTableKey,
                                final VrfEntry vrfEntry) {
    LOG.debug("deleting route "+ vrfEntry.getDestPrefix() + " "+vpnId);
    String rd = vrfTableKey.getRouteDistinguisher();
    boolean isRemoteRoute = true;
    if (localDpnId == null) {
      // localDpnId is not known when clean up happens for last vm for a vpn on a dpn
      VpnNexthop localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, vrfEntry.getDestPrefix());
      if(localNextHopInfo == null) {
        //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
        Extraroute extra_route = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
        if (extra_route != null) {
          localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, extra_route.getNexthopIp());
        }
      }
      if (localNextHopInfo != null) {
        isRemoteRoute = (!remoteDpnId.equals(localNextHopInfo.getDpnId()));
      }
    }
    if (isRemoteRoute) {
      makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW);
      LOG.debug("Successfully delete fib entry for "+ vrfEntry.getDestPrefix() + " vpnId "+vpnId);
    } else{
      LOG.debug("Did not delete fib entry rd: {} =, prefix: {} as it is local to dpn {}", rd, vrfEntry.getDestPrefix(), remoteDpnId);
    }
  }

  private long getIpAddress(byte[] rawIpAddress) {
    return (((rawIpAddress[0] & 0xFF) << (3 * 8)) + ((rawIpAddress[1] & 0xFF) << (2 * 8))
            + ((rawIpAddress[2] & 0xFF) << (1 * 8)) + (rawIpAddress[3] & 0xFF)) & 0xffffffffL;
  }

  private void makeConnectedRoute(BigInteger dpId, long vpnId, VrfEntry vrfEntry, String rd,
                                  List<InstructionInfo> instructions, int addOrRemove) {    LOG.trace("makeConnectedRoute: vrfEntry {}",vrfEntry);
    String values[] = vrfEntry.getDestPrefix().split("/");
    String ipAddress = values[0];
    int prefixLength = (values.length == 1) ? 0 : Integer.parseInt(values[1]);
    if (addOrRemove == NwConstants.ADD_FLOW) {
        LOG.debug("Adding route to DPN {} for rd {} prefix {} ", dpId, rd, vrfEntry.getDestPrefix());
    } else {
        LOG.debug("Removing route from DPN {} for rd {} prefix {}", dpId, rd, vrfEntry.getDestPrefix());
    }
    InetAddress destPrefix = null;
    try {
      destPrefix = InetAddress.getByName(ipAddress);
    } catch (UnknownHostException e) {
      LOG.error("Failed to get destPrefix for prefix {} ", vrfEntry.getDestPrefix(), e);
      return;
    }

    List<MatchInfo> matches = new ArrayList<>();

    matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
        BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

    matches.add(new MatchInfo(MatchFieldType.eth_type,
                              new long[] { 0x0800L }));

    if(prefixLength != 0) {
        matches.add(new MatchInfo(MatchFieldType.ipv4_destination, new String[] {
                destPrefix.getHostAddress(), Integer.toString(prefixLength)}));
    }

    String flowRef = getFlowRef(dpId, NwConstants.L3_FIB_TABLE, rd, destPrefix);

    FlowEntity flowEntity;

    int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
    flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
                                           priority, flowRef, 0, 0,
                                           COOKIE_VM_FIB_TABLE, matches, instructions);

    if (addOrRemove == NwConstants.ADD_FLOW) {
      /* We need to call sync API to install flow so that 2 DS operations on the same object do not
      * happen at same time. However, MDSALManager's syncInstallFlow takes a delay time (or uses a default one) to wait
      * for or for notification that operational DS write for flows is done. We do not turn on the stats writing for flows,
      * so that notification never comes, so we do not need that wait. Sending the lowest value of wait "1 ms" since 0 wait means
      * wait indefinitely. */
      mdsalManager.syncInstallFlow(flowEntity, 1);
    } else {
      mdsalManager.syncRemoveFlow(flowEntity, 1);
    }
  }

  private void makeLFibTableEntry(BigInteger dpId, long label, List<InstructionInfo> instructions,
                                  String nextHop, int addOrRemove) {
    List<MatchInfo> matches = new ArrayList<>();
    matches.add(new MatchInfo(MatchFieldType.eth_type,
                              new long[] { 0x8847L }));
    matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(label)}));

    // Install the flow entry in L3_LFIB_TABLE
    String flowRef = getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, label, nextHop);

    FlowEntity flowEntity;
    flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_LFIB_TABLE, flowRef,
                                           DEFAULT_FIB_FLOW_PRIORITY, flowRef, 0, 0,
                                           COOKIE_VM_LFIB_TABLE, matches, instructions);

    if (addOrRemove == NwConstants.ADD_FLOW) {
      /* We need to call sync API to install flow so that 2 DS operations on the same object do not
      * happen at same time. However, MDSALManager's syncInstallFlow takes a delay time (or uses a default one) to wait
      * for or for notification that operational DS write for flows is done. We do not turn on the stats writing for flows,
      * so that notification never comes, so we do not need that wait. Sending the lowest value of wait "1 ms" since 0 wait means
      * wait indefinitely. */

      mdsalManager.syncInstallFlow(flowEntity, 1);
    } else {
      mdsalManager.syncRemoveFlow(flowEntity, 1);
    }
      LOG.debug("LFIB Entry for dpID {} : label : {} instructions {} modified successfully {}",dpId, label, instructions );
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
    String lockOnDpnVpn = new String(dpnId.toString()+ vpnId);
    synchronized (lockOnDpnVpn.intern()) {
      Optional<VrfTables> vrfTable = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
      if (vrfTable.isPresent()) {
        for (VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
          SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
          if (subnetRoute != null){
              long elanTag= subnetRoute.getElantag();
              installSubnetRouteInFib(dpnId, elanTag, rd, vpnId, vrfEntry);
              continue;
          }
          // Passing null as we don't know the dpn
          // to which prefix is attached at this point
          createRemoteFibEntry(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry);
        }
      }
    }
  }

  public void populateFibOnDpn(BigInteger dpnId, long vpnId, String rd, String nexthopIp) {
    LOG.trace("dpn {} for vpn {}, nexthopIp {} : populateFibOnDpn", dpnId, rd, nexthopIp);
    InstanceIdentifier<VrfTables> id = buildVrfId(rd);
    String lockOnDpnVpn = new String(dpnId.toString()+ vpnId);
    synchronized (lockOnDpnVpn.intern()) {
      Optional<VrfTables> vrfTable = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
      if (vrfTable.isPresent()) {
        for (VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
          // Passing null as we don't know the dpn
          // to which prefix is attached at this point
          if (nexthopIp == vrfEntry.getNextHopAddress()) {
            createRemoteFibEntry(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry);
          }
        }
      }
    }
  }

  public void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd) {
    LOG.trace("Remove dpn {} for vpn {} : cleanUpDpnForVpn", dpnId, rd);
    InstanceIdentifier<VrfTables> id = buildVrfId(rd);
    String lockOnDpnVpn = new String(dpnId.toString()+ vpnId);
    synchronized (lockOnDpnVpn.intern()) {
      Optional<VrfTables> vrfTable = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
      if (vrfTable.isPresent()) {
        for (VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
                        /* Handle subnet routes here */
            SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
            if (subnetRoute != null){
                LOG.trace("Cleaning subnetroute {} on dpn {} for vpn {} : cleanUpDpnForVpn", vrfEntry.getDestPrefix(),
                        dpnId, rd);
                makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW);
                makeLFibTableEntry(dpnId, vrfEntry.getLabel(), null,
                        vrfEntry.getNextHopAddress(),NwConstants.DEL_FLOW);
                LOG.trace("cleanUpDpnForVpn: Released subnetroute label {} for rd {} prefix {}", vrfEntry.getLabel(), rd,
                        vrfEntry.getDestPrefix());
                continue;
            }
          // Passing null as we don't know the dpn
          // to which prefix is attached at this point
          deleteRemoteRoute(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry);
        }
      }
    }
  }

  public void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd, String nexthopIp) {
    LOG.trace("dpn {} for vpn {}, nexthopIp {} : cleanUpDpnForVpn", dpnId, rd, nexthopIp);
    InstanceIdentifier<VrfTables> id = buildVrfId(rd);
    String lockOnDpnVpn = new String(dpnId.toString()+ vpnId);
    synchronized (lockOnDpnVpn.intern()) {
      Optional<VrfTables> vrfTable = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
      if (vrfTable.isPresent()) {
        for (VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
          // Passing null as we don't know the dpn
          // to which prefix is attached at this point
          if (nexthopIp == vrfEntry.getNextHopAddress()) {
            deleteRemoteRoute(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry);
          }
        }
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

  protected String resolveAdjacency(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                              final long vpnId, final VrfEntry vrfEntry, String rd) {
    String adjacency = null;
    boolean staticRoute = false;
    LOG.trace("resolveAdjacency called with localdpid{} remotedpid {}, vpnId{}, VrfEntry {}", localDpnId, remoteDpnId, vpnId, vrfEntry);
    try {
        Extraroute extra_route = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
        if(extra_route != null) {
            staticRoute = true;
        }

        adjacency =
          nextHopManager.getRemoteNextHopPointer(localDpnId, remoteDpnId, vpnId,
                  (staticRoute) ? extra_route.getNexthopIp() + "/32" : vrfEntry.getDestPrefix(),
                                                vrfEntry.getNextHopAddress());
    } catch (NullPointerException e) {
      LOG.trace("", e);
    }
    return adjacency;
  }

  protected VpnInstanceOpDataEntry getVpnInstance(String rd) {
    InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class).child(
        VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
    Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
    if(vpnInstanceOpData.isPresent()) {
      return vpnInstanceOpData.get();
    }
    return null;
  }

    public void processNodeAdd(BigInteger dpnId) {
        LOG.debug("Received notification to install TableMiss entries for dpn {} ", dpnId);
        makeTableMissFlow(dpnId, NwConstants.ADD_FLOW);
        makeL3IntfTblMissFlow(dpnId, NwConstants.ADD_FLOW);
        makeSubnetRouteTableMissFlow(dpnId, NwConstants.ADD_FLOW);
    }

    private void makeTableMissFlow(BigInteger dpnId, int addOrRemove) {
        final BigInteger COOKIE_TABLE_MISS = new BigInteger("1030000", 16);
        // Instruction to goto L3 InterfaceTable
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_INTERFACE_TABLE }));
        List<MatchInfo> matches = new ArrayList<>();
        FlowEntity flowEntityLfib = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_LFIB_TABLE,
                getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, NwConstants.TABLE_MISS_FLOW),
                NwConstants.TABLE_MISS_PRIORITY, "Table Miss", 0, 0, COOKIE_TABLE_MISS, matches, instructions);

        FlowEntity flowEntityFib = MDSALUtil.buildFlowEntity(dpnId,NwConstants.L3_FIB_TABLE, getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, NwConstants.TABLE_MISS_FLOW),
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
    instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] {NwConstants.L3_LFIB_TABLE}));
    List<MatchInfo> matches = new ArrayList<>();
    matches.add(new MatchInfo(MatchFieldType.eth_type,
                              new long[] { 0x8847L }));
    FlowEntity flowEntityToLfib = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_PROTOCOL_TABLE,
                                                          getFlowRef(dpnId, NwConstants.L3_PROTOCOL_TABLE,
                                                                  NwConstants.L3_LFIB_TABLE),
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
    List<String> result = new ArrayList<>();
    result.add(String.format("   %-7s  %-20s  %-20s  %-7s", "RD", "Prefix", "Nexthop", "Label"));
    result.add("-------------------------------------------------------------------");
    InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);
    Optional<FibEntries> fibEntries = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
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
    List<InstructionInfo> instructions = new ArrayList<>();
    List<MatchInfo> matches = new ArrayList<>();
    final BigInteger COOKIE_TABLE_MISS = new BigInteger("1030000", 16);
    // Instruction to clear metadata except SI and LportTag bits
    instructions.add(new InstructionInfo(InstructionType.write_metadata, new BigInteger[] {
                    CLEAR_METADATA, METADATA_MASK_CLEAR }));
    // Instruction to clear action
    instructions.add(new InstructionInfo(InstructionType.clear_actions));
    // Instruction to goto L3 InterfaceTable

    List <ActionInfo> actionsInfos = new ArrayList<>();
    actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[]{
        Short.toString(NwConstants.LPORT_DISPATCHER_TABLE)}));
    instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
    //instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.LPORT_DISPATCHER_TABLE }));

    FlowEntity flowEntityL3Intf = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_INTERFACE_TABLE,
            getFlowRef(dpnId, NwConstants.L3_INTERFACE_TABLE, NwConstants.TABLE_MISS_FLOW),
            NwConstants.TABLE_MISS_PRIORITY, "L3 Interface Table Miss", 0, 0, COOKIE_TABLE_MISS, matches, instructions);
    if (addOrRemove == NwConstants.ADD_FLOW) {
      LOG.info("Invoking MDSAL to install L3 interface Table Miss Entries");
      mdsalManager.installFlow(flowEntityL3Intf);
    } else {
      mdsalManager.removeFlow(flowEntityL3Intf);
    }
  }

}

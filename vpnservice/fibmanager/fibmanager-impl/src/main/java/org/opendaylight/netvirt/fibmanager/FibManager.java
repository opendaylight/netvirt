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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.utils.batching.ActionableResource;
import org.opendaylight.genius.utils.batching.ActionableResourceImpl;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.batching.ResourceHandler;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.LabelRouteMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthopBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PrefixToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;


import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn
        .link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn
        .links.InterVpnLink;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.overlay.rev150105.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.VpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.vpn.Extraroute;
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

public class FibManager extends AbstractDataChangeListener<VrfEntry> implements AutoCloseable, ResourceHandler {
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
  private static final int LFIB_INTERVPN_PRIORITY = 1;
  private static final BigInteger METADATA_MASK_CLEAR = new BigInteger("000000FFFFFFFFFF", 16);
  private static final BigInteger CLEAR_METADATA = BigInteger.valueOf(0);
  public static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);

    private static final int PERIODICITY = 500;
    private static Integer batchSize;
    private static Integer batchInterval;

    private static final int BATCH_SIZE = 1000;

    private static BlockingQueue<ActionableResource> vrfEntryBufferQ = new LinkedBlockingQueue<>();
    private ResourceBatchingManager resourceBatchingManager;


  public FibManager(final DataBroker db) {
      super(VrfEntry.class);
      broker = db;
      registerListener(db);
      batchSize = Integer.getInteger("batch.size");
      if (batchSize == null) {
          batchSize = BATCH_SIZE;
      }
      batchInterval = Integer.getInteger("batch.wait.time");
      if (batchInterval == null) {
          batchInterval = PERIODICITY;
      }
      resourceBatchingManager = ResourceBatchingManager.getInstance();
      resourceBatchingManager.registerBatchableResource("FIB-VRFENTRY",vrfEntryBufferQ, this);
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

  public DataBroker getResourceBroker() {
      return broker;
  }

  @Override
  protected void add(final InstanceIdentifier<VrfEntry> identifier, final VrfEntry vrfEntry) {
      Preconditions.checkNotNull(vrfEntry, "VrfEntry should not be null or empty.");
      String rd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
      LOG.info("ADD: Adding Fib Entry rd {} prefix {} nexthop {} label {}",
               rd, vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddressList(), vrfEntry.getLabel());
      if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
          createFibEntries(identifier, vrfEntry);
      } else {
          ActionableResource actResource = new ActionableResourceImpl(rd.toString() + vrfEntry.getDestPrefix());
          actResource.setAction(ActionableResource.CREATE);
          actResource.setInstanceIdentifier(identifier);
          actResource.setInstance(vrfEntry);
          vrfEntryBufferQ.add(actResource);
          leakRouteIfNeeded(identifier, vrfEntry, NwConstants.ADD_FLOW);
      }
      LOG.info("ADD: Added Fib Entry rd {} prefix {} nexthop {} label {}",
               rd, vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddressList(), vrfEntry.getLabel());
  }

  @Override
  protected void remove(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry) {
    Preconditions.checkNotNull(vrfEntry, "VrfEntry should not be null or empty.");
    String rd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
    LOG.info("REMOVE: Removing Fib Entry rd {} prefix {} nexthop {} label {}",
             rd, vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddressList(), vrfEntry.getLabel());
    if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
        deleteFibEntries(identifier, vrfEntry);
    } else {
        ActionableResource actResource = new ActionableResourceImpl(rd.toString() + vrfEntry.getDestPrefix());
        actResource.setAction(ActionableResource.DELETE);
        actResource.setInstanceIdentifier(identifier);
        actResource.setInstance(vrfEntry);
        vrfEntryBufferQ.add(actResource);
        leakRouteIfNeeded(identifier, vrfEntry, NwConstants.DEL_FLOW);
    }
    LOG.info("REMOVE: Removed Fib Entry rd {} prefix {} nexthop {} label {}",
             rd, vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddressList(), vrfEntry.getLabel());
    leakRouteIfNeeded(identifier, vrfEntry, NwConstants.DEL_FLOW);
  }

  @Override
  protected void update(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update) {
    Preconditions.checkNotNull(update, "VrfEntry should not be null or empty.");
    String rd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
    LOG.info("UPDATE: Updating Fib Entries to rd {} prefix {} nexthop {} label {}",
             rd, update.getDestPrefix(), update.getNextHopAddressList(), update.getLabel());
    if (RouteOrigin.value(update.getOrigin()) != RouteOrigin.BGP) {
        createFibEntries(identifier, update);
    } else {
        ActionableResource actResource = new ActionableResourceImpl(rd.toString() + update.getDestPrefix());
        actResource.setAction(ActionableResource.UPDATE);
        actResource.setInstanceIdentifier(identifier);
        actResource.setInstance(update);
        actResource.setOldInstance(original);
        vrfEntryBufferQ.add(actResource);
    }
    LOG.info("UPDATE: Updated Fib Entries to rd {} prefix {} nexthop {} label {}",
             rd, update.getDestPrefix(), update.getNextHopAddressList(), update.getLabel());
  }

  public void create(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier, Object vrfEntry) {
      if (vrfEntry instanceof VrfEntry) {
          createFibEntries(tx, identifier, (VrfEntry)vrfEntry);
      }
  }

  public void delete(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier, Object vrfEntry) {
      if (vrfEntry instanceof VrfEntry) {
          deleteFibEntries(tx, identifier, (VrfEntry) vrfEntry);
      }
  }

  public void update(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier, Object original,
                     Object update) {
      if ((original instanceof VrfEntry) && (update instanceof VrfEntry)) {
          createFibEntries(tx, identifier, (VrfEntry)update);
      }
  }

  public int getBatchSize() {
      return batchSize;
  }

  public int getBatchInterval() {
      return batchInterval;
  }

  public LogicalDatastoreType getDatastoreType() {
      return LogicalDatastoreType.CONFIGURATION;
  }

  private void createFibEntries(final InstanceIdentifier<VrfEntry> vrfEntryIid, final VrfEntry vrfEntry) {
      final VrfTablesKey vrfTableKey = vrfEntryIid.firstKeyOf(VrfTables.class);

      final VpnInstanceOpDataEntry vpnInstance = getVpnInstance(vrfTableKey.getRouteDistinguisher());
      Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
      Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId() + " has null vpnId!");

      final Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
      final Long vpnId = vpnInstance.getVpnId();
      final String rd = vrfTableKey.getRouteDistinguisher();
      SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
      if (subnetRoute != null) {
          final long elanTag = subnetRoute.getElantag();
          LOG.trace("SubnetRoute augmented vrfentry found for rd {} prefix {} with elantag {}",
                  rd, vrfEntry.getDestPrefix(), elanTag);
          if (vpnToDpnList != null) {
              DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
              dataStoreCoordinator.enqueueJob("FIB"+rd.toString()+vrfEntry.getDestPrefix(),
                      new Callable<List<ListenableFuture<Void>>>() {
                          @Override
                          public List<ListenableFuture<Void>> call() throws Exception {
                              WriteTransaction tx = broker.newWriteOnlyTransaction();
                              for (final VpnToDpnList curDpn : vpnToDpnList) {
                                  if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                      installSubnetRouteInFib(curDpn.getDpnId(), elanTag, rd, vpnId.longValue(), vrfEntry, tx);
                                  }
                              }
                              List<ListenableFuture<Void>> futures = new ArrayList<>();
                              futures.add(tx.submit());
                              return futures;
                          }
                      });
          }
          return;
      }

      if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.INTERVPN) {
          // When it is a leaked route, the LFIB and FIB goes a bit different.
          installInterVpnRouteInLFib(rd, vrfEntry);
          return;
      }

      final List<BigInteger> localDpnIdList = createLocalFibEntry(vpnInstance.getVpnId(), rd, vrfEntry);

      if (vpnToDpnList != null) {
          DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
          dataStoreCoordinator.enqueueJob("FIB"+rd.toString()+vrfEntry.getDestPrefix(),
                  new Callable<List<ListenableFuture<Void>>>() {
                      @Override
                      public List<ListenableFuture<Void>> call() throws Exception {
                          WriteTransaction tx = broker.newWriteOnlyTransaction();
                          for (VpnToDpnList vpnDpn : vpnToDpnList) {
                              if ( !localDpnIdList.contains(vpnDpn.getDpnId())) {
                                  if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                      createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
                                  }
                              }
                          }
                          List<ListenableFuture<Void>> futures = new ArrayList<>();
                          futures.add(tx.submit());
                          return futures;
                      }
                  });
      }

      Optional<String> vpnUuid = FibUtil.getVpnNameFromRd(broker, rd);
      if ( vpnUuid.isPresent() ) {
          Optional<InterVpnLink> interVpnLink = FibUtil.getInterVpnLinkByVpnUuid(broker, vpnUuid.get());
          if ( interVpnLink.isPresent() ) {
              String routeNexthop = vrfEntry.getNextHopAddressList().get(0);
              if ( isNexthopTheOtherVpnLinkEndpoint(routeNexthop, vpnUuid.get(), interVpnLink.get()) ) {
                  // This is an static route that points to the other endpoint of an InterVpnLink
                  // In that case, we should add another entry in FIB table pointing to LPortDispatcher table.
                  installRouteInInterVpnLink(interVpnLink.get(), rd, vrfEntry, vpnId);
              }
          }
      }
  }


  /*
    Please note that the following createFibEntries will be invoked only for BGP Imported Routes.
    The invocation of the following method is via create() callback from the MDSAL Batching Infrastructure
    provided by ResourceBatchingManager
   */
  private void createFibEntries(WriteTransaction writeTx, final InstanceIdentifier<VrfEntry> vrfEntryIid, final VrfEntry vrfEntry) {
      final VrfTablesKey vrfTableKey = vrfEntryIid.firstKeyOf(VrfTables.class);

      final VpnInstanceOpDataEntry vpnInstance = getVpnInstance(vrfTableKey.getRouteDistinguisher());
      Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
      Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId() + " has null vpnId!");

      final Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
      final String rd = vrfTableKey.getRouteDistinguisher();
      if (vpnToDpnList != null) {
          for (VpnToDpnList vpnDpn : vpnToDpnList) {
              if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                  createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry, writeTx);
              }
          }
      }
  }

    /*
     * Returns true if the specified nexthop is the other endpoint in an
     * InterVpnLink, regarding one of the VPN's point of view.
     */
    private boolean isNexthopTheOtherVpnLinkEndpoint(String nexthop, String thisVpnUuid, InterVpnLink interVpnLink) {
        return
            interVpnLink != null
            && (   (interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(thisVpnUuid)
                     && interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(nexthop))
                || (interVpnLink.getSecondEndpoint().getVpnUuid().getValue().equals(thisVpnUuid )
                     && interVpnLink.getFirstEndpoint().getIpAddress().getValue().equals(nexthop)) );
    }


    // FIXME: Refactoring needed here.
    //        This kind of logic must be taken to an 'upper' layer like BgpManager or VpnManager
    private void leakRouteIfNeeded(final InstanceIdentifier<VrfEntry> vrfEntryIid, final VrfEntry vrfEntry,
                                   int addOrRemove) {
        Preconditions.checkNotNull(vrfEntry, "VrfEntry cannot be null or empty!");
        final VrfTablesKey vrfTableKey = vrfEntryIid.firstKeyOf(VrfTables.class);

        String rd = vrfTableKey.getRouteDistinguisher();
        VpnInstanceOpDataEntry vpnInstance = getVpnInstance(rd);
        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
            if (vpnInstance == null) {
                LOG.error("Vpn Instance not available for external route with prefix {} label {} nexthop {}. Returning...", vrfEntry.getDestPrefix(), vrfEntry.getLabel(), vrfEntry.getNextHopAddressList());
                return;
            }
        } else {
            Preconditions.checkNotNull(vpnInstance,
                    "Vpn Instance not available with rd " + vrfTableKey.getRouteDistinguisher());
        }
        String vpnUuid = vpnInstance.getVpnInstanceName();
        Preconditions.checkArgument(vpnUuid != null && !vpnUuid.isEmpty(),
                                    "Could not find suitable VPN UUID for Route-Distinguisher=" + rd);

        // if the new vrfEntry has been learned by Quagga BGP, its necessary to check if it's
        // there an interVpnLink for the involved vpn in order to make learn the new route to
        // the other part of the inter-vpn-link.

        // For leaking, we need the InterVpnLink to be active. For removal, we just need a InterVpnLink.
        Optional<InterVpnLink> interVpnLink =
            (addOrRemove == NwConstants.ADD_FLOW) ? FibUtil.getActiveInterVpnLinkFromRd(broker, rd)
                                                  : FibUtil.getInterVpnLinkByRd(broker, rd);
        if ( !interVpnLink.isPresent() ) {
            LOG.debug("Could not find an InterVpnLink for Route-Distinguisher={}", rd);
            return;
        }

        // Ok, at this point everything is ready for the leaking/removal... but should it be performed?
        // For removal, we remove all leaked routes, but we only leak a route if the corresponding flag is enabled.
        boolean proceed = (addOrRemove == NwConstants.DEL_FLOW )
                          || ( RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP
                               && interVpnLink.get().isBgpRoutesLeaking() );

        if ( proceed ) {
            String theOtherVpnId = ( interVpnLink.get().getFirstEndpoint().getVpnUuid().getValue().equals(vpnUuid) )
                                     ? interVpnLink.get().getSecondEndpoint().getVpnUuid().getValue()
                                     : vpnUuid;

            String dstVpnRd = FibUtil.getVpnRd(broker, theOtherVpnId);
            String endpointIp = vrfEntry.getNextHopAddressList().get(0);

            InstanceIdentifier<VrfEntry> vrfEntryIidInOtherVpn =
                InstanceIdentifier.builder(FibEntries.class)
                                  .child(VrfTables.class, new VrfTablesKey(dstVpnRd))
                                  .child(VrfEntry.class, new VrfEntryKey(vrfEntry.getDestPrefix()))
                                  .build();
            if ( addOrRemove == NwConstants.ADD_FLOW ) {
                LOG.info("Leaking route (destination={}, nexthop={}) from Vrf={} to Vrf={}",
                         vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddressList(), rd, dstVpnRd);
                String key = rd + FibConstants.SEPARATOR + vrfEntry.getDestPrefix();
                long label = FibUtil.getUniqueId(idManager, FibConstants.VPN_IDPOOL_NAME, key);
                VrfEntry newVrfEntry = new VrfEntryBuilder(vrfEntry).setNextHopAddressList(Arrays.asList(endpointIp))
                                                                    .setLabel(label)
                                                                    .setOrigin(RouteOrigin.INTERVPN.getValue())
                                                                    .build();
                MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryIidInOtherVpn, newVrfEntry);
            } else {
                LOG.info("Removing leaked vrfEntry={}", vrfEntryIidInOtherVpn.toString());
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryIidInOtherVpn);
            }
        }
    }

    private Prefixes updateVpnReferencesInLri(LabelRouteInfo lri, String vpnInstanceName, boolean isPresentInList) {
        LOG.info("updating LRI : for label {} vpninstancename {}", lri.getLabel(), vpnInstanceName);
        PrefixesBuilder prefixBuilder = new PrefixesBuilder();
        prefixBuilder.setDpnId(lri.getDpnId());
        prefixBuilder.setVpnInterfaceName(lri.getVpnInterfaceName());
        prefixBuilder.setIpAddress(lri.getPrefix());
        // Increment the refCount here
        InstanceIdentifier<LabelRouteInfo> lriId = InstanceIdentifier.builder(LabelRouteMap.class)
                .child(LabelRouteInfo.class, new LabelRouteInfoKey((long)lri.getLabel())).build();
        LabelRouteInfoBuilder builder = new LabelRouteInfoBuilder(lri);
        if (!isPresentInList) {
            LOG.debug("vpnName {} is not present in LRI with label {}..", vpnInstanceName, lri.getLabel());
            List<String> vpnInstanceNames = lri.getVpnInstanceList();
            vpnInstanceNames.add(vpnInstanceName);
            builder.setVpnInstanceList(vpnInstanceNames);
            FibUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, lriId, builder.build(), FibUtil.DEFAULT_CALLBACK);
        } else {
            LOG.debug("vpnName {} is present in LRI with label {}..", vpnInstanceName, lri.getLabel());
        }
        return prefixBuilder.build();
    }

    private void installSubnetRouteInFib(final BigInteger dpnId, final long elanTag, final String rd,
                                         final long vpnId, final VrfEntry vrfEntry, WriteTransaction tx){
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = broker.newWriteOnlyTransaction();
        }
        synchronized (vrfEntry.getLabel().toString().intern()) {
            LabelRouteInfo lri = getLabelRouteInfo(vrfEntry.getLabel());
            if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) &&
                    vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {

                if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                    Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = FibUtil.getVpnInstanceOpData(broker, rd);
                    if (vpnInstanceOpDataEntryOptional.isPresent()) {
                        String vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                        if (!lri.getVpnInstanceList().contains(vpnInstanceName)) {
                            updateVpnReferencesInLri(lri, vpnInstanceName, false);
                        }
                    }
                }
                LOG.debug("Fetched labelRouteInfo for label {} interface {} and got dpn {}",
                        vrfEntry.getLabel(), lri.getVpnInterfaceName(), lri.getDpnId());
            }
        }
        final List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        BigInteger subnetRouteMeta =  ((BigInteger.valueOf(elanTag)).shiftLeft(32)).or((BigInteger.valueOf(vpnId)));
        instructions.add(new InstructionInfo(InstructionType.write_metadata,  new BigInteger[] { subnetRouteMeta, MetaDataUtil.METADATA_MASK_SUBNET_ROUTE }));
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_SUBNET_ROUTE_TABLE }));
        makeConnectedRoute(dpnId,vpnId,vrfEntry,rd,instructions,NwConstants.ADD_FLOW, tx);

        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
            List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
            // reinitialize instructions list for LFIB Table
            final List<InstructionInfo> LFIBinstructions = new ArrayList<InstructionInfo>();

            actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{}));
            LFIBinstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
            LFIBinstructions.add(new InstructionInfo(InstructionType.write_metadata,  new BigInteger[] { subnetRouteMeta, MetaDataUtil.METADATA_MASK_SUBNET_ROUTE }));
            LFIBinstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_SUBNET_ROUTE_TABLE }));

            makeLFibTableEntry(dpnId,vrfEntry.getLabel(), LFIBinstructions, DEFAULT_FIB_FLOW_PRIORITY, NwConstants.ADD_FLOW, tx);
        }
        if (!wrTxPresent ) {
            tx.submit();
        }
    }

    private void installInterVpnRouteInLFib(final String rd, final VrfEntry vrfEntry) {
        // INTERVPN routes are routes in a Vpn1 that have been leaked to Vpn2. In DC-GW, this Vpn2 route is pointing
        // to a list of DPNs where Vpn2's VpnLink was instantiated. In these DPNs LFIB must be programmed so that the
        // packet is commuted from Vpn2 to Vpn1.
        Optional<String> vpnNameOpc = FibUtil.getVpnNameFromRd(broker, rd);
        if ( !vpnNameOpc.isPresent() ) {
            LOG.warn("Could not find VpnInstanceName for Route-Distinguisher {}", rd);
            return;
        }

        String vpnName = vpnNameOpc.get();
        List<InterVpnLink> interVpnLinks = FibUtil.getAllInterVpnLinks(broker);
        boolean interVpnLinkFound = false;
        for ( InterVpnLink interVpnLink : interVpnLinks ) {
            boolean vpnIs1stEndpoint = interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(vpnName);
            boolean vpnIs2ndEndpoint = !vpnIs1stEndpoint
                                        && interVpnLink.getSecondEndpoint().getVpnUuid().getValue().equals(vpnName);
            if ( vpnIs1stEndpoint || vpnIs2ndEndpoint ) {
                interVpnLinkFound = true;

                Optional<InterVpnLinkState> vpnLinkState = FibUtil.getInterVpnLinkState(broker, interVpnLink.getName());
                if ( !vpnLinkState.isPresent()
                     || !vpnLinkState.get().getState().equals(InterVpnLinkState.State.Active) ) {
                    LOG.warn("InterVpnLink {}, linking VPN {} and {}, is not in Active state",
                             interVpnLink.getName(), interVpnLink.getFirstEndpoint().getVpnUuid().getValue(),
                             interVpnLink.getSecondEndpoint().getVpnUuid().getValue() );
                    return;
                }

                List<BigInteger> targetDpns =
                    ( vpnIs1stEndpoint ) ? vpnLinkState.get().getFirstEndpointState().getDpId()
                                         : vpnLinkState.get().getSecondEndpointState().getDpId();
                int lportTag =
                    ( vpnIs1stEndpoint ) ? vpnLinkState.get().getSecondEndpointState().getLportTag()
                                         : vpnLinkState.get().getFirstEndpointState().getLportTag();

                for ( BigInteger dpId : targetDpns ) {
                    List<ActionInfo> actionsInfos = Arrays.asList(new ActionInfo(ActionType.pop_mpls, new String[]{}));

                    BigInteger[] metadata = new BigInteger[] {
                        MetaDataUtil.getMetaDataForLPortDispatcher(lportTag, FibConstants.L3VPN_SERVICE_IDENTIFIER),
                        MetaDataUtil.getMetaDataMaskForLPortDispatcher()
                    };
                    List<InstructionInfo> instructions =
                        Arrays.asList(new InstructionInfo(InstructionType.apply_actions, actionsInfos),
                                      new InstructionInfo(InstructionType.write_metadata, metadata),
                                      new InstructionInfo(InstructionType.goto_table,
                                                          new long[] { NwConstants.L3_INTERFACE_TABLE }));

                    makeLFibTableEntry(dpId, vrfEntry.getLabel(), instructions, LFIB_INTERVPN_PRIORITY,
                                       NwConstants.ADD_FLOW, null);
                }

                break;
            }
        }

        if ( !interVpnLinkFound ) {
            LOG.warn("VrfEntry=[prefix={} label={} nexthop={}] for VPN {} has origin INTERVPN but no InterVpnLink could be found",
                     vrfEntry.getDestPrefix(), vrfEntry.getLabel(), vrfEntry.getNextHopAddressList(), rd);
        }
    }



    private void installRouteInInterVpnLink(final InterVpnLink interVpnLink, final String vpnUuid,
                                            final VrfEntry vrfEntry, long vpnTag) {
        Preconditions.checkNotNull(interVpnLink, "InterVpnLink cannot be null");
        Preconditions.checkArgument(vrfEntry.getNextHopAddressList() != null
                                    && vrfEntry.getNextHopAddressList().size() == 1);

        // After having received a static route, we should check if the vpn is part of an inter-vpn-link.
        // In that case, we should populate the FIB table of the VPN pointing to LPortDisptacher table
        // using as metadata the LPortTag associated to that vpn in the inter-vpn-link.
        Optional<InterVpnLinkState> interVpnLinkState = FibUtil.getInterVpnLinkState(broker, interVpnLink.getName());
        if ( !interVpnLinkState.isPresent() ) {
            LOG.warn("Could not find State for InterVpnLink {}", interVpnLink.getName());
            return;
        }
        if ( ! interVpnLinkState.get().getState().equals(InterVpnLinkState.State.Active) ) {
            LOG.warn("Route to {} with nexthop={} cannot be installed because the interVpnLink {} is not active",
                     vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddressList().get(0), interVpnLink.getName());
            return;
        }


        // Everything Ok
        boolean vpnIsFirstEndpoint = isVpnFirstEndPoint(interVpnLink, vpnUuid);
        List<BigInteger> targetDpns =
            vpnIsFirstEndpoint ? interVpnLinkState.get().getFirstEndpointState().getDpId()
                               : interVpnLinkState.get().getSecondEndpointState().getDpId();

        Integer otherEndpointlportTag =
            vpnIsFirstEndpoint ? interVpnLinkState.get().getSecondEndpointState().getLportTag()
                               : interVpnLinkState.get().getFirstEndpointState().getLportTag();

        BigInteger[] metadata = new BigInteger[] {
                        MetaDataUtil.getMetaDataForLPortDispatcher(otherEndpointlportTag,
                                                                   FibConstants.L3VPN_SERVICE_IDENTIFIER),
                        MetaDataUtil.getMetaDataMaskForLPortDispatcher()
                    };
        List<Instruction> instructions =
            Arrays.asList(new InstructionInfo(InstructionType.write_metadata, metadata).buildInstruction(0),
                          new InstructionInfo(InstructionType.goto_table,
                                              new long[] { NwConstants.L3_INTERFACE_TABLE }).buildInstruction(1));

        String values[] = vrfEntry.getDestPrefix().split("/");
        String destPrefixIpAddress = values[0];
        int prefixLength = (values.length == 1) ? 0 : Integer.parseInt(values[1]);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.metadata,
                                  new BigInteger[] { BigInteger.valueOf(vpnTag),
                                                     MetaDataUtil.METADATA_MASK_VRFID }));
        matches.add(new MatchInfo(MatchFieldType.eth_type, new long[] { NwConstants.ETHTYPE_IPV4 }));

        if (prefixLength != 0) {
            matches.add(new MatchInfo(MatchFieldType.ipv4_destination,
                                      new String[] { destPrefixIpAddress, Integer.toString(prefixLength) }));
        }

        int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        String nextHop = vrfEntry.getNextHopAddressList().get(0);
        String flowRef = getInterVpnFibFlowRef(interVpnLink.getName(), vrfEntry.getDestPrefix(), nextHop);
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef, priority, flowRef, 0, 0,
                                                 COOKIE_VM_FIB_TABLE, matches, instructions);

        for ( BigInteger dpId : targetDpns ) {
            mdsalManager.installFlow(dpId, flowEntity);
        }
    }

    private void removeRouteFromInterVpnLink(final InterVpnLink interVpnLink, final String vpnUuid,
                                             final VrfEntry vrfEntry) {

        Preconditions.checkNotNull(interVpnLink, "InterVpnLink cannot be null");
        Preconditions.checkArgument(vrfEntry.getNextHopAddressList() != null
                                    && vrfEntry.getNextHopAddressList().size() == 1);

        Optional<InterVpnLinkState> interVpnLinkState = FibUtil.getInterVpnLinkState(broker, interVpnLink.getName());
        if ( !interVpnLinkState.isPresent() ) {
            LOG.warn("Could not find State for InterVpnLink {}", interVpnLink.getName());
            return;
        }

        // Everything Ok
        boolean vpnIsFirstEndpoint = isVpnFirstEndPoint(interVpnLink, vpnUuid);
        List<BigInteger> targetDpns =
            vpnIsFirstEndpoint ? interVpnLinkState.get().getFirstEndpointState().getDpId()
                               : interVpnLinkState.get().getSecondEndpointState().getDpId();

        String nextHop = vrfEntry.getNextHopAddressList().get(0);
        String flowRef = getInterVpnFibFlowRef(interVpnLink.getName(), vrfEntry.getDestPrefix(), nextHop);
        FlowKey flowKey = new FlowKey(new FlowId(flowRef));
        Flow flow = new FlowBuilder().setKey(flowKey).setId(new FlowId(flowRef)).setTableId(NwConstants.L3_FIB_TABLE)
                                     .setFlowName(flowRef).build();

        for ( BigInteger dpId : targetDpns ) {
            mdsalManager.removeFlow(dpId, flow);
        }

    }

    private boolean isVpnFirstEndPoint(InterVpnLink interVpnLink, String vpnName) {
        return interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(vpnName);
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
        List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        actionsInfos.add(new ActionInfo(ActionType.punt_to_controller, new String[]{}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        String flowRef = getTableMissFlowRef(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, NwConstants.TABLE_MISS_FLOW);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, flowRef,
                NwConstants.TABLE_MISS_PRIORITY, "Subnet Route Table Miss", 0, 0, COOKIE_TABLE_MISS, matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.installFlow(flowEntity);
        } else {
            mdsalManager.removeFlow(flowEntity);
        }
    }

  private List<BigInteger> createLocalFibEntry(Long vpnId, String rd, VrfEntry vrfEntry) {
    List<BigInteger> returnLocalDpnId = new ArrayList<BigInteger>();
    Prefixes localNextHopInfo = getPrefixToInterface(vpnId, vrfEntry.getDestPrefix());
    String localNextHopIP = vrfEntry.getDestPrefix();

    if (localNextHopInfo == null) {
        //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
        Extraroute extraRoute = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
        if (extraRoute != null) {
            for (String nextHopIp : extraRoute.getNexthopIpList()) {
                LOG.debug("NextHop IP for destination {} is {}", vrfEntry.getDestPrefix(), nextHopIp);
                if (nextHopIp != null) {
                    localNextHopInfo = getPrefixToInterface(vpnId, nextHopIp + "/32");
                    localNextHopIP = nextHopIp + "/32";
                    BigInteger dpnId = checkCreateLocalFibEntry(localNextHopInfo, localNextHopIP, vpnId, rd, vrfEntry, vpnId);
                    returnLocalDpnId.add(dpnId);
                }
            }
        }
        if (localNextHopInfo == null) {
            /* imported routes case */
            synchronized (vrfEntry.getLabel().toString().intern()) {
                LabelRouteInfo lri = getLabelRouteInfo(vrfEntry.getLabel());
                if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) &&
                        vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {
                    if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = FibUtil.getVpnInstanceOpData(broker, rd);
                        if (vpnInstanceOpDataEntryOptional.isPresent()) {
                            String vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                            if (lri.getVpnInstanceList().contains(vpnInstanceName)) {
                                localNextHopInfo = updateVpnReferencesInLri(lri, vpnInstanceName, true);
                            } else {
                                localNextHopInfo = updateVpnReferencesInLri(lri, vpnInstanceName, false);
                            }
                        }
                    }
                    localNextHopIP = lri.getPrefix();
                    LOG.debug("Fetched labelRouteInfo for label {} interface {} and got dpn {}",
                            vrfEntry.getLabel(), localNextHopInfo.getVpnInterfaceName(), lri.getDpnId());
                    BigInteger dpnId = checkCreateLocalFibEntry(localNextHopInfo, localNextHopIP, vpnId, rd, vrfEntry, lri.getParentVpnid());
                    returnLocalDpnId.add(dpnId);
                }
            }
        }
    } else {
        BigInteger dpnId = checkCreateLocalFibEntry(localNextHopInfo, localNextHopIP, vpnId, rd, vrfEntry, vpnId);
        returnLocalDpnId.add(dpnId);
    }

     return returnLocalDpnId;
  }

   private BigInteger checkCreateLocalFibEntry(Prefixes localNextHopInfo, String localNextHopIP, final Long vpnId, final String rd,
                                               final VrfEntry vrfEntry, Long parentVpnId){
       if (localNextHopInfo != null) {
           final BigInteger dpnId = localNextHopInfo.getDpnId();
           if (!isVpnPresentInDpn(rd, dpnId)) {
               return BigInteger.ZERO;
           }

           final long groupId = nextHopManager.createLocalNextHop(parentVpnId, dpnId, localNextHopInfo.getVpnInterfaceName(), localNextHopIP);

           List<ActionInfo> actionsInfos =
               Arrays.asList(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId)}));
           final List<InstructionInfo> instructions =
               Arrays.asList(new InstructionInfo(InstructionType.write_actions, actionsInfos));
           actionsInfos = Arrays.asList(new ActionInfo(ActionType.pop_mpls, new String[]{}),
                   new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }) );
           final List<InstructionInfo> lfibinstructions = Arrays.asList(new InstructionInfo(InstructionType.write_actions, actionsInfos));
           if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
               LOG.debug("Installing tunnel table entry on dpn {} for interface {} with label {}",
                       dpnId, localNextHopInfo.getVpnInterfaceName(), vrfEntry.getLabel());
           } else {
               LOG.debug("Route with rd {} prefix {} label {} nexthop {} for vpn {} is an imported route. LFib and Terminating table entries will not be created.", rd, vrfEntry.getDestPrefix(), vrfEntry.getLabel(), vrfEntry.getNextHopAddressList(), vpnId);
           }
           DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
           dataStoreCoordinator.enqueueJob("FIB"+vpnId.toString()+dpnId.toString()+vrfEntry.getDestPrefix(),
                   new Callable<List<ListenableFuture<Void>>>() {
                       @Override
                       public List<ListenableFuture<Void>> call() throws Exception {
                           WriteTransaction tx = broker.newWriteOnlyTransaction();
                           makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW, tx);
                           if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                               makeLFibTableEntry(dpnId, vrfEntry.getLabel(), lfibinstructions , DEFAULT_FIB_FLOW_PRIORITY, NwConstants.ADD_FLOW, tx);
                               makeTunnelTableEntry(dpnId, vrfEntry.getLabel(), groupId, tx);
                           }
                           List<ListenableFuture<Void>> futures = new ArrayList<>();
                           futures.add(tx.submit());
                           return futures;
                       }
                   });
           return dpnId;
       }
       return BigInteger.ZERO;
   }

   private boolean isVpnPresentInDpn(String rd, BigInteger dpnId)  {
       InstanceIdentifier<VpnToDpnList> id = FibUtil.getVpnToDpnListIdentifier(rd, dpnId);
       Optional<VpnToDpnList> dpnInVpn = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
       if (dpnInVpn.isPresent()) {
           return true;
       }
       return false;
   }

    private LabelRouteInfo getLabelRouteInfo(Long label) {
        InstanceIdentifier<LabelRouteInfo>lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
                .child(LabelRouteInfo.class, new LabelRouteInfoKey((long)label)).build();
        Optional<LabelRouteInfo> opResult = read(broker, LogicalDatastoreType.OPERATIONAL, lriIid);
        if (opResult.isPresent()) {
            return opResult.get();
        }
        return null;
    }

    private boolean deleteLabelRouteInfo(LabelRouteInfo lri, String vpnInstanceName) {
        LOG.info("deleting LRI : for label {} vpninstancename {}", lri.getLabel(), vpnInstanceName);
        InstanceIdentifier<LabelRouteInfo> lriId = InstanceIdentifier.builder(LabelRouteMap.class)
                .child(LabelRouteInfo.class, new LabelRouteInfoKey((long) lri.getLabel())).build();
        if (lri == null) {
            return true;
        }
        List<String> vpnInstancesList = lri.getVpnInstanceList();
        if (vpnInstancesList.contains(vpnInstanceName)) {
            LOG.debug("vpninstance {} name is present", vpnInstanceName);
            vpnInstancesList.remove(vpnInstanceName);
        }
        if (vpnInstancesList.size() == 0) {
            LOG.debug("deleting LRI instance object for label {}", lri.getLabel());
            FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, lriId);
            return true;
        } else {
            LOG.debug("updating LRI instance object for label {}", lri.getLabel());
            LabelRouteInfoBuilder builder = new LabelRouteInfoBuilder(lri).setVpnInstanceList(vpnInstancesList);
            FibUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, lriId, builder.build(), FibUtil.DEFAULT_CALLBACK);
        }
        return false;
    }

    private void makeTunnelTableEntry(BigInteger dpId, long label, long groupId/*String egressInterfaceName*/,
                                      WriteTransaction tx) {
      List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
      actionsInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));


      createTerminatingServiceActions(dpId, (int)label, actionsInfos, tx);

      LOG.debug("Terminating service Entry for dpID {} : label : {} egress : {} installed successfully",
              dpId, label, groupId);
  }

  public void createTerminatingServiceActions( BigInteger destDpId, int label, List<ActionInfo> actionsInfos,
                                               WriteTransaction tx) {
      List<MatchInfo> mkMatches = new ArrayList<>();

      LOG.info("create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}", destDpId , label,actionsInfos);

      // Matching metadata
      // FIXME vxlan vni bit set is not working properly with OVS.need to revisit
      mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(label)}));

      List<InstructionInfo> mkInstructions = new ArrayList<>();
      mkInstructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));

      FlowEntity terminatingServiceTableFlowEntity = MDSALUtil.buildFlowEntity(destDpId, NwConstants.INTERNAL_TUNNEL_TABLE,
                      getTableMissFlowRef(destDpId, NwConstants.INTERNAL_TUNNEL_TABLE,label), 5, String.format("%s:%d","TST Flow Entry ",label),
                      0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(label)),mkMatches, mkInstructions);

      FlowKey flowKey = new FlowKey( new FlowId(terminatingServiceTableFlowEntity.getFlowId()) );

      FlowBuilder flowbld = terminatingServiceTableFlowEntity.getFlowBuilder();

      Node nodeDpn = buildDpnNode(terminatingServiceTableFlowEntity.getDpnId());
      InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
              .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
              .child(Table.class, new TableKey(terminatingServiceTableFlowEntity.getTableId())).child(Flow.class,flowKey).build();
      tx.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flowbld.build(),true );
 }

  private void removeTunnelTableEntry(BigInteger dpId, long label, WriteTransaction tx) {
    FlowEntity flowEntity;
    LOG.info("remove terminatingServiceActions called with DpnId = {} and label = {}", dpId , label);
    List<MatchInfo> mkMatches = new ArrayList<>();
    // Matching metadata
    mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(label)}));
    flowEntity = MDSALUtil.buildFlowEntity(dpId,
                                           NwConstants.INTERNAL_TUNNEL_TABLE,
                                           getTableMissFlowRef(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, (int)label),
                                           5, String.format("%s:%d","TST Flow Entry ",label), 0, 0,
                                           COOKIE_TUNNEL.add(BigInteger.valueOf(label)), mkMatches, null);
    Node nodeDpn = buildDpnNode(flowEntity.getDpnId());
    FlowKey flowKey = new FlowKey(new FlowId(flowEntity.getFlowId()));
    InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
      .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
      .child(Table.class, new TableKey(flowEntity.getTableId())).child(Flow.class, flowKey).build();

    tx.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
    LOG.debug("Terminating service Entry for dpID {} : label : {} removed successfully", dpId, label);
  }

    /**
     * Delete local FIB entry
     * @param vpnId
     * @param rd
     * @param vrfEntry
     * @return
     */
  public List<BigInteger> deleteLocalFibEntry(Long vpnId, String rd, VrfEntry vrfEntry) {
      List<BigInteger> returnLocalDpnId = new ArrayList<>();
      VpnNexthop localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, vrfEntry.getDestPrefix());
      String localNextHopIP = vrfEntry.getDestPrefix();

      if (localNextHopInfo == null) {
          //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
          Extraroute extra_route = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
          if (extra_route != null) {
              for (String nextHopIp : extra_route.getNexthopIpList()) {
                  LOG.debug("NextHop IP for destination {} is {}", vrfEntry.getDestPrefix(), nextHopIp);
                  if (nextHopIp != null) {
                      localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, nextHopIp + "/32");
                      localNextHopIP = nextHopIp + "/32";
                      BigInteger dpnId = checkDeleteLocalFibEntry(localNextHopInfo, localNextHopIP,
                                                                  vpnId, rd, vrfEntry, true /*isExtraRoute*/);
                      if (!dpnId.equals(BigInteger.ZERO)) {
                          returnLocalDpnId.add(dpnId);
                      }
                  }
              }
          }

          if (localNextHopInfo == null) {
              /* Imported VRF entry */
              LabelRouteInfo lri = getLabelRouteInfo(vrfEntry.getLabel());
              if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) &&
                      vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {
                  VpnNexthopBuilder vpnNexthopBuilder = new VpnNexthopBuilder();
                  vpnNexthopBuilder.setDpnId(lri.getDpnId());
                  BigInteger dpnId = checkDeleteLocalFibEntry(vpnNexthopBuilder.build(), localNextHopIP,
                          vpnId, rd, vrfEntry, false /*isExtraRoute*/);
                  if (!dpnId.equals(BigInteger.ZERO)) {
                      returnLocalDpnId.add(dpnId);
                  }
              }
          }


      } else {
          BigInteger dpnId = checkDeleteLocalFibEntry(localNextHopInfo, localNextHopIP,
                                                      vpnId, rd, vrfEntry, false /*isExtraRoute*/);
          if (!dpnId.equals(BigInteger.ZERO)) {
              returnLocalDpnId.add(dpnId);
          }
      }

      return returnLocalDpnId;
  }

    private BigInteger checkDeleteLocalFibEntry(VpnNexthop localNextHopInfo, final String localNextHopIP,
                                                final Long vpnId, final String rd,
                                                final VrfEntry vrfEntry, final boolean isExtraRoute) {
        if (localNextHopInfo != null) {
            final BigInteger dpnId = localNextHopInfo.getDpnId();;
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("FIB"+vpnId.toString()+dpnId.toString()+vrfEntry.getDestPrefix(),
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            WriteTransaction tx = broker.newWriteOnlyTransaction();
                            makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null /* instructions */,
                                    NwConstants.DEL_FLOW, tx);
                            if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                                makeLFibTableEntry(dpnId, vrfEntry.getLabel(), null /* instructions */,
                                        DEFAULT_FIB_FLOW_PRIORITY, NwConstants.DEL_FLOW, tx);
                                removeTunnelTableEntry(dpnId, vrfEntry.getLabel(), tx);
                            }
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            futures.add(tx.submit());
                            return futures;
                        }
                    });
            //TODO: verify below adjacency call need to be optimized (?)
            deleteLocalAdjacency(dpnId, vpnId, localNextHopIP);
            return dpnId;
        }
        return BigInteger.ZERO;
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
    private void createRemoteFibEntry(final BigInteger remoteDpnId, final long vpnId, final VrfTablesKey vrfTableKey,
                                      final VrfEntry vrfEntry, WriteTransaction tx) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = broker.newWriteOnlyTransaction();
        }
        String rd = vrfTableKey.getRouteDistinguisher();
        LOG.debug(  "createremotefibentry: adding route {} for rd {} with transaction {}",
                    vrfEntry.getDestPrefix(), rd, tx);
        /********************************************/
        List<String> tunnelInterfaceList = resolveAdjacency(remoteDpnId, vpnId, vrfEntry, rd);

        if (tunnelInterfaceList.isEmpty()) {
            LOG.error("Could not get interface for nexthop: {} in vpn {}",
                    vrfEntry.getNextHopAddressList(), rd);
            LOG.warn("Failed to add Route: {} in vpn: {}",
                    vrfEntry.getDestPrefix(), rd);
            return;
        }

        for (String tunnelInterface : tunnelInterfaceList) {
            List<InstructionInfo> instructions = new ArrayList<>();
            List<ActionInfo> actionInfos = new ArrayList<>();
            Class<? extends TunnelTypeBase> tunnel_type = getTunnelType(tunnelInterface);
            if (tunnel_type.equals(TunnelTypeMplsOverGre.class)) {
                LOG.debug("Push label action for prefix {}", vrfEntry.getDestPrefix());
                actionInfos.add(new ActionInfo(ActionType.push_mpls, new String[]{null}));
                actionInfos.add(new ActionInfo(ActionType.set_field_mpls_label, new String[]{Long.toString(vrfEntry.getLabel())}));
            } else {
                int label = vrfEntry.getLabel().intValue();
                BigInteger tunnelId;
                // FIXME vxlan vni bit set is not working properly with OVS.need to revisit
                if (tunnel_type.equals(TunnelTypeVxlan.class)) {
                    tunnelId = BigInteger.valueOf(label);
                } else {
                    tunnelId = BigInteger.valueOf(label);
                }

                LOG.debug("adding set tunnel id action for label {}", label);
                actionInfos.add(new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[]{tunnelId}));
            }
            List<ActionInfo> egressActions = nextHopManager.getEgressActionsForInterface(tunnelInterface);
            if(egressActions.isEmpty()){
                LOG.error("Failed to retrieve egress action for prefix {} nextHop {} interface {}. Aborting remote FIB entry creation.", vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddressList(), tunnelInterface);
                return;
            }
            actionInfos.addAll(egressActions);
            instructions.add(new InstructionInfo(InstructionType.write_actions, actionInfos));
            makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW, tx);
        }
        if(!wrTxPresent ){
            tx.submit();
        }
        LOG.debug("Successfully added FIB entry for prefix {} in vpnId {}", vrfEntry.getDestPrefix(), vpnId);
    }

    private void delIntfFromDpnToVpnList(long vpnId, BigInteger dpnId, String intfName, String rd) {
        InstanceIdentifier<VpnToDpnList> id = FibUtil.getVpnToDpnListIdentifier(rd, dpnId);
        Optional<VpnToDpnList> dpnInVpn = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (dpnInVpn.isPresent()) {
            List<VpnInterfaces> vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
            VpnInterfaces currVpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();

            if (vpnInterfaces.remove(currVpnInterface)) {
                if (vpnInterfaces.isEmpty()) {
                  LOG.trace("Last vpn interface {} on dpn {} for vpn {}. Clean up fib in dpn", intfName, dpnId, rd);
                  FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id);
                  cleanUpDpnForVpn(dpnId, vpnId, rd);
                } else {
                  LOG.trace("Delete vpn interface {} from dpn {} to vpn {} list.", intfName, dpnId, rd);
                    FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                            VpnInterfaces.class,
                            new VpnInterfacesKey(intfName)));
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
              for (String nextHopIp : extraRoute.getNexthopIpList()) {
                  LOG.debug("NextHop IP for destination {} is {}", vrfEntry.getDestPrefix(), nextHopIp);

                  if (nextHopIp != null) {
                      prefixInfo = getPrefixToInterface(vpnId, nextHopIp + "/32");
                      checkCleanUpOpDataForFib(prefixInfo, vpnId, rd, vrfEntry, extraRoute);
                  }
              }
          }
          if (prefixInfo == null) {
              LabelRouteInfo lri = getLabelRouteInfo(vrfEntry.getLabel());
              if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) &&
                      vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {
                  PrefixesBuilder prefixBuilder = new PrefixesBuilder();
                  prefixBuilder.setDpnId(lri.getDpnId());
                  prefixBuilder.setVpnInterfaceName(lri.getVpnInterfaceName());
                  prefixBuilder.setIpAddress(lri.getPrefix());
                  prefixInfo = prefixBuilder.build();
                  LOG.debug("Fetched labelRouteInfo for label {} interface {} and got dpn {}",
                          vrfEntry.getLabel(), prefixInfo.getVpnInterfaceName(), lri.getDpnId());
                  checkCleanUpOpDataForFib(prefixInfo, vpnId, rd, vrfEntry, extraRoute);
              }
          }
      } else {
          checkCleanUpOpDataForFib(prefixInfo, vpnId, rd, vrfEntry, extraRoute);
      }
  }

  private void checkCleanUpOpDataForFib(final Prefixes prefixInfo, final Long vpnId, final String rd,
                                        final VrfEntry vrfEntry, final Extraroute extraRoute) {

      if (prefixInfo == null) {
          LOG.debug("Cleanup VPN Data Failed as unable to find prefix Info for prefix {}", vrfEntry.getDestPrefix());
          return; //Don't have any info for this prefix (shouldn't happen); need to return
      }

      String ifName = prefixInfo.getVpnInterfaceName();
      DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
      dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + ifName,
              new CleanupVpnInterfaceWorker(prefixInfo, vpnId, rd, vrfEntry, extraRoute));
  }

  private class CleanupVpnInterfaceWorker implements Callable<List<ListenableFuture<Void>>> {
      Prefixes prefixInfo;
      Long vpnId;
      String rd;
      VrfEntry vrfEntry;
      Extraroute extraRoute;

      public CleanupVpnInterfaceWorker(final Prefixes prefixInfo, final Long vpnId, final String rd,
                                       final VrfEntry vrfEntry, final Extraroute extraRoute) {
          this.prefixInfo = prefixInfo;
          this.vpnId = vpnId;
          this.rd= rd;
          this.vrfEntry= vrfEntry;
          this.extraRoute = extraRoute;
      }

      @Override
      public List<ListenableFuture<Void>> call() throws Exception {
          // If another renderer(for eg : CSS) needs to be supported, check can be performed here
          // to call the respective helpers.
          String ifName = prefixInfo.getVpnInterfaceName();
          WriteTransaction writeTxn = broker.newWriteOnlyTransaction();
          Optional<VpnInterface> optvpnInterface = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                  FibUtil.getVpnInterfaceIdentifier(ifName));
          if (optvpnInterface.isPresent()) {
              long associatedVpnId = FibUtil.getVpnId(broker, optvpnInterface.get().getVpnInstanceName());
              if (vpnId != associatedVpnId) {
                  LOG.warn("Prefixes {} are associated with different vpn instance with id : {} rather than {}",
                          vrfEntry.getDestPrefix(), associatedVpnId, vpnId);
                  LOG.trace("Releasing prefix label - rd {}, prefix {}", rd, vrfEntry.getDestPrefix());
                  FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                          FibUtil.getNextHopLabelKey(rd, vrfEntry.getDestPrefix()));
                  LOG.warn("Not proceeding with Cleanup op data for prefix {}", vrfEntry.getDestPrefix());
                  return null;
              } else {
                  LOG.debug("Processing cleanup of prefix {} associated with vpn {}",
                          vrfEntry.getDestPrefix(), associatedVpnId);
              }
          }
          if (extraRoute != null) {
              FibUtil.delete(broker, LogicalDatastoreType.OPERATIONAL,
                      FibUtil.getVpnToExtrarouteIdentifier(rd, vrfEntry.getDestPrefix()));
          }
          Optional<Adjacencies> optAdjacencies = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                  FibUtil.getAdjListPath(ifName));
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

          synchronized (vrfEntry.getLabel().toString().intern()) {
              LabelRouteInfo lri = getLabelRouteInfo(vrfEntry.getLabel());
              if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) &&
                      vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {
                  Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = FibUtil.getVpnInstanceOpData(broker, rd);
                  String vpnInstanceName = "";
                  if (vpnInstanceOpDataEntryOptional.isPresent()) {
                      vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                  }
                  boolean lriRemoved = deleteLabelRouteInfo(lri, vpnInstanceName);
                  if (lriRemoved) {
                      String parentRd = lri.getParentVpnRd();
                      FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                              FibUtil.getNextHopLabelKey(parentRd, vrfEntry.getDestPrefix()));
                  }
              }
          }
          CheckedFuture<Void, TransactionCommitFailedException> futures = writeTxn.submit();
          try {
              futures.get();
          } catch (InterruptedException | ExecutionException e) {
              LOG.error("Error cleaning up interface {} on vpn {}", ifName, vpnId);
              throw new RuntimeException(e.getMessage());
          }
          return null;
      }
  }

  private void deleteFibEntries(final InstanceIdentifier<VrfEntry> identifier, final VrfEntry vrfEntry) {
    final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);

    final String rd  = vrfTableKey.getRouteDistinguisher();
    final VpnInstanceOpDataEntry vpnInstance = getVpnInstance(vrfTableKey.getRouteDistinguisher());
    if (vpnInstance == null) {
        LOG.error("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
        return;
    }
    final Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
    long elanTag = 0L;
    SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
    if (subnetRoute != null) {
        elanTag = subnetRoute.getElantag();
        LOG.trace("SubnetRoute augmented vrfentry found for rd {} prefix {} with elantag {}",
                rd, vrfEntry.getDestPrefix(), elanTag);
        if (vpnToDpnList != null) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("FIB" + rd.toString() + vrfEntry.getDestPrefix(),
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            WriteTransaction tx = broker.newWriteOnlyTransaction();

                            for (final VpnToDpnList curDpn : vpnToDpnList) {

                                makeConnectedRoute(curDpn.getDpnId(), vpnInstance.getVpnId(), vrfEntry,
                                        vrfTableKey.getRouteDistinguisher(), null, NwConstants.DEL_FLOW, tx);
                                if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                                    makeLFibTableEntry(curDpn.getDpnId(), vrfEntry.getLabel(), null,
                                            DEFAULT_FIB_FLOW_PRIORITY, NwConstants.DEL_FLOW, tx);
                                }
                            }
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            futures.add(tx.submit());
                            return futures;
                        }
                    });
        }
        synchronized (vrfEntry.getLabel().toString().intern()) {
            LabelRouteInfo lri = getLabelRouteInfo(vrfEntry.getLabel());
            if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) && vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {
                Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = FibUtil.getVpnInstanceOpData(broker, rd);
                String vpnInstanceName = "";
                if (vpnInstanceOpDataEntryOptional.isPresent()) {
                    vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                }
                boolean lriRemoved = this.deleteLabelRouteInfo(lri, vpnInstanceName);
                if (lriRemoved) {
                    String parentRd = lri.getParentVpnRd();
                    FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                            FibUtil.getNextHopLabelKey(parentRd, vrfEntry.getDestPrefix()));
                    LOG.trace("deleteFibEntries: Released subnetroute label {} for rd {} prefix {}", vrfEntry.getLabel(), rd,
                            vrfEntry.getDestPrefix());
                }
            }
        }
        return;
    }

    final List<BigInteger> localDpnIdList = deleteLocalFibEntry(vpnInstance.getVpnId(),
                                                          vrfTableKey.getRouteDistinguisher(), vrfEntry);
    if (vpnToDpnList != null) {
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("FIB" + rd.toString() + vrfEntry.getDestPrefix(),
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        WriteTransaction tx = broker.newWriteOnlyTransaction();

                        if (localDpnIdList.size() <= 0) {
                            for (VpnToDpnList curDpn : vpnToDpnList) {
                                if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                    if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                        deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
                                    }
                                } else {
                                    deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
                                }
                            }
                        } else {
                            for (BigInteger localDpnId : localDpnIdList) {
                                for (VpnToDpnList curDpn : vpnToDpnList) {
                                    if (!curDpn.getDpnId().equals(localDpnId)) {
                                        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                            if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                                deleteRemoteRoute(localDpnId, curDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
                                            }
                                        } else {
                                            deleteRemoteRoute(localDpnId, curDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
                                        }
                                    }
                                }
                            }
                        }
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        futures.add(tx.submit());
                        return futures;
                    }
                });
    }

    //The flow/group entry has been deleted from config DS; need to clean up associated operational
    //DS entries in VPN Op DS, VpnInstanceOpData and PrefixToInterface to complete deletion
    cleanUpOpDataForFib(vpnInstance.getVpnId(), vrfTableKey.getRouteDistinguisher(), vrfEntry);

    // Remove all fib entries configured due to interVpnLink, when nexthop is the opposite endPoint
    // of the interVpnLink.
    Optional<String> vpnUuid = FibUtil.getVpnNameFromRd(broker, rd);
    if ( vpnUuid.isPresent() ) {
        Optional<InterVpnLink> interVpnLink = FibUtil.getInterVpnLinkByVpnUuid(broker, vpnUuid.get());
        String routeNexthop = vrfEntry.getNextHopAddressList().get(0);

        if ( interVpnLink.isPresent()
             && ( (interVpnLink.get().getFirstEndpoint().getVpnUuid().getValue().equals(vpnUuid.get())
                   && interVpnLink.get().getSecondEndpoint().getIpAddress().getValue().equals(routeNexthop))
                  || (interVpnLink.get().getSecondEndpoint().getVpnUuid().getValue().equals(vpnUuid.get() )
                      && interVpnLink.get().getFirstEndpoint().getIpAddress().getValue().equals(routeNexthop)) ) ) {
            // This is route that points to the other endpoint of an InterVpnLink
            // In that case, we should look for the FIB table pointing to LPortDispatcher table and remove it.
            removeRouteFromInterVpnLink(interVpnLink.get(), rd, vrfEntry);
        }
    }

  }

  /*
    Please note that the following deleteFibEntries will be invoked only for BGP Imported Routes.
    The invocation of the following method is via delete() callback from the MDSAL Batching Infrastructure
    provided by ResourceBatchingManager
   */
  private void deleteFibEntries(WriteTransaction writeTx, final InstanceIdentifier<VrfEntry> identifier, final VrfEntry vrfEntry) {
      final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);

      final String rd  = vrfTableKey.getRouteDistinguisher();
      final VpnInstanceOpDataEntry vpnInstance = getVpnInstance(vrfTableKey.getRouteDistinguisher());
      if (vpnInstance == null) {
          LOG.debug("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
          return;
      }
      final Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
      if (vpnToDpnList != null) {
          for (VpnToDpnList curDpn : vpnToDpnList) {
              if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                  deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey, vrfEntry, writeTx);
              }
          }
      }
  }

  public void deleteRemoteRoute(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                final long vpnId, final VrfTablesKey vrfTableKey,
                                final VrfEntry vrfEntry, WriteTransaction tx) {

      Boolean wrTxPresent = true;
      if (tx == null) {
          wrTxPresent = false;
          tx = broker.newWriteOnlyTransaction();
      }

    LOG.debug("deleting route: prefix={}, vpnId={}", vrfEntry.getDestPrefix(), vpnId);
    String rd = vrfTableKey.getRouteDistinguisher();

      if(localDpnId != null) {
          // localDpnId is not known when clean up happens for last vm for a vpn on a dpn
          deleteFibEntry(remoteDpnId, vpnId, vrfEntry, rd, tx);
          return;
      }

      // below two reads are kept as is, until best way is found to identify dpnID
      VpnNexthop localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, vrfEntry.getDestPrefix());
      Extraroute extraRoute = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());

      if (localNextHopInfo == null && extraRoute != null) {
          // Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
          for (String nextHopIp : extraRoute.getNexthopIpList()) {
              localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, nextHopIp);
              checkDpnDeleteFibEntry(localNextHopInfo, remoteDpnId, vpnId, vrfEntry, rd, tx);
          }
      } else {
              checkDpnDeleteFibEntry(localNextHopInfo, remoteDpnId, vpnId, vrfEntry, rd, tx);
      }
      if(!wrTxPresent ){
          tx.submit();
      }
  }

    private boolean checkDpnDeleteFibEntry(VpnNexthop localNextHopInfo, BigInteger remoteDpnId, long vpnId,
                                           VrfEntry vrfEntry, String rd, WriteTransaction tx){
        boolean isRemoteRoute = true;
        if (localNextHopInfo != null) {
            isRemoteRoute = (!remoteDpnId.equals(localNextHopInfo.getDpnId()));
        }
        if (isRemoteRoute) {
            deleteFibEntry(remoteDpnId, vpnId, vrfEntry, rd, tx);
            return true;
        } else {
            LOG.debug("Did not delete FIB entry: rd={}, vrfEntry={}, as it is local to dpnId={}", rd, vrfEntry.getDestPrefix(), remoteDpnId);
            return false;
        }
    }

    private void deleteFibEntry(BigInteger remoteDpnId, long vpnId, VrfEntry vrfEntry, String rd, WriteTransaction tx){
            makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW, tx);
            LOG.debug("Successfully delete FIB entry: vrfEntry={}, vpnId={}", vrfEntry.getDestPrefix(), vpnId);
    }

  private long get
          (byte[] rawIpAddress) {
    return (((rawIpAddress[0] & 0xFF) << (3 * 8)) + ((rawIpAddress[1] & 0xFF) << (2 * 8))
            + ((rawIpAddress[2] & 0xFF) << (1 * 8)) + (rawIpAddress[3] & 0xFF)) & 0xffffffffL;
  }

  private void makeConnectedRoute(BigInteger dpId, long vpnId, VrfEntry vrfEntry, String rd,
                                  List<InstructionInfo> instructions, int addOrRemove, WriteTransaction tx) {
    Boolean wrTxPresent = true;
    if (tx == null) {
      wrTxPresent = false;
      tx = broker.newWriteOnlyTransaction();
    }

    LOG.trace("makeConnectedRoute: vrfEntry {}", vrfEntry);
    String values[] = vrfEntry.getDestPrefix().split("/");
    String ipAddress = values[0];
    int prefixLength = (values.length == 1) ? 0 : Integer.parseInt(values[1]);
    if (addOrRemove == NwConstants.ADD_FLOW) {
        LOG.debug("Adding route to DPN {} for rd {} prefix {} ", dpId, rd, vrfEntry.getDestPrefix());
    } else {
        LOG.debug("Removing route from DPN {} for rd {} prefix {}", dpId, rd, vrfEntry.getDestPrefix());
    }
    InetAddress destPrefix;
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
                              new long[] { NwConstants.ETHTYPE_IPV4 }));

    if(prefixLength != 0) {
      matches.add(new MatchInfo(MatchFieldType.ipv4_destination, new String[] {
          destPrefix.getHostAddress(), Integer.toString(prefixLength)}));
    }
    int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
    String flowRef = getFlowRef(dpId, NwConstants.L3_FIB_TABLE, rd, priority, destPrefix);
    FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef, priority, flowRef, 0, 0,
                                                      COOKIE_VM_FIB_TABLE, matches, instructions);

    Flow flow = flowEntity.getFlowBuilder().build();
    String flowId = flowEntity.getFlowId();
    FlowKey flowKey = new FlowKey( new FlowId(flowId));
    Node nodeDpn = buildDpnNode(dpId);

    InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();
    if (addOrRemove == NwConstants.ADD_FLOW) {
        tx.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId,flow, true);
    } else {
        tx.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
    }

    if(!wrTxPresent ){
      tx.submit();
    }
  }

    //TODO: How to handle the below code, its a copy paste from MDSALManager.java
    private Node buildDpnNode(BigInteger dpnId) {
        NodeId nodeId = new NodeId("openflow:" + dpnId);
        Node nodeDpn = new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();

        return nodeDpn;
    }

    private void makeLFibTableEntry(BigInteger dpId, long label, List<InstructionInfo> instructions, int priority,
                                    int addOrRemove, WriteTransaction tx) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = broker.newWriteOnlyTransaction();
        }

        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                                  new long[] { NwConstants.ETHTYPE_MPLS_UC }));
        matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(label)}));

        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, label, priority);

        FlowEntity flowEntity;
        flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_LFIB_TABLE, flowRef, priority, flowRef, 0, 0,
                                               COOKIE_VM_LFIB_TABLE, matches, instructions);
        Flow flow = flowEntity.getFlowBuilder().build();
        String flowId = flowEntity.getFlowId();
        FlowKey flowKey = new FlowKey( new FlowId(flowId));
        Node nodeDpn = buildDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();

        if (addOrRemove == NwConstants.ADD_FLOW) {
            tx.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId,flow, true);
        } else {
            tx.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
        }
        if(!wrTxPresent ){
            tx.submit();
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

  public void populateFibOnNewDpn(final BigInteger dpnId, final long vpnId, final String rd) {
      LOG.trace("New dpn {} for vpn {} : populateFibOnNewDpn", dpnId, rd);
      InstanceIdentifier<VrfTables> id = buildVrfId(rd);
      synchronized (rd.intern()) {
          final Optional<VrfTables> vrfTable = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
          if (vrfTable.isPresent()) {
              DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
              dataStoreCoordinator.enqueueJob("FIB" + vpnId + dpnId.toString(),
                      new Callable<List<ListenableFuture<Void>>>() {
                          @Override
                          public List<ListenableFuture<Void>> call() throws Exception {
                              WriteTransaction tx = broker.newWriteOnlyTransaction();
                              for (final VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {

                                  SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
                                  if (subnetRoute != null) {
                                      long elanTag = subnetRoute.getElantag();
                                      installSubnetRouteInFib(dpnId, elanTag, rd, vpnId, vrfEntry, tx);
                                      continue;
                                  }
                                  if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) { //Handle local flow creation for imports
                                      LabelRouteInfo lri = getLabelRouteInfo(vrfEntry.getLabel());
                                      if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) && vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {
                                          if (lri.getDpnId().equals(dpnId)) {
                                              createLocalFibEntry(vpnId, rd, vrfEntry);
                                              continue;
                                          }
                                      }
                                  }
                                  // Passing null as we don't know the dpn
                                  // to which prefix is attached at this point
                                  createRemoteFibEntry(dpnId, vpnId, vrfTable.get().getKey(), vrfEntry, tx);
                              }
                              //TODO: if we have 100K entries in FIB, can it fit in one Tranasaction (?)
                              List<ListenableFuture<Void>> futures = new ArrayList<>();
                              futures.add(tx.submit());
                              return futures;
                          }
                      });
          }
      }
  }

  public void populateFibOnDpn(final BigInteger dpnId, final long vpnId, final String rd, final String localNextHopIp, final String remoteNextHopIp) {
    LOG.trace(  "dpn {}, vpn {}, rd {}, localNexthopIp {} , remoteNextHopIp {} : populateFibOnDpn",
                dpnId, vpnId, rd, localNextHopIp, remoteNextHopIp);
    InstanceIdentifier<VrfTables> id = buildVrfId(rd);
    synchronized (rd.intern()) {
      final Optional<VrfTables> vrfTable = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
      if (vrfTable.isPresent()) {
          DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
          dataStoreCoordinator.enqueueJob(" FIB + on Dpn , rd "
                            + rd.toString() + "localNextHopIp "
                            + localNextHopIp + "remoteNextHopIP"
                            + remoteNextHopIp + "vpnId "
                            + vpnId + "dpnId" + dpnId,
                  new Callable<List<ListenableFuture<Void>>>() {
                      @Override
                      public List<ListenableFuture<Void>> call() throws Exception {
                          WriteTransaction writeTransaction = broker.newWriteOnlyTransaction();
                          List<ListenableFuture<Void>> futures = new ArrayList<>();
                          LOG.trace("populate FIB starts on Dpn " + dpnId
                                  + "rd  " + rd.toString()
                                  + "localNextHopIp " + localNextHopIp
                                  + "remoteNextHopIp" + remoteNextHopIp
                                  + "vpnId " + vpnId );

                          for (VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
                                LOG.trace("old vrfEntry before populate:: {}", vrfEntry);

                              if (vrfEntry.getOrigin().equals(RouteOrigin.BGP.getValue())) {
                                  if (remoteNextHopIp.trim().equals(vrfEntry.getNextHopAddressList().get(0).trim())) {
                                      LOG.trace(" creating remote FIB entry for vfEntry {}", vrfEntry);
                                      createRemoteFibEntry(dpnId, vpnId, vrfTable.get().getKey(), vrfEntry, writeTransaction);
                                  }
                              }

                              if ((vrfEntry.getOrigin().equals(RouteOrigin.CONNECTED.getValue())) ||
                                  (vrfEntry.getOrigin().equals(RouteOrigin.STATIC.getValue()))) {
                                  String destPfx = vrfEntry.getDestPrefix();
                                  BigInteger dpnIdForPrefix = nextHopManager.getDpnForPrefix(vpnId, destPfx);
                                  if (dpnIdForPrefix == null) {
                                      LOG.trace("Populate::the dpnIdForPrefix is null for prefix {}.",
                                                vrfEntry.getDestPrefix());
                                      continue;
                                  }
                                  int sameDpnId = dpnIdForPrefix.compareTo(dpnId);
                                  if (sameDpnId != 0) {
                                      LOG.trace("Populate::Different srcDpnId {} and dpnIdForPrefix {} for prefix {}",
                                              dpnId, dpnIdForPrefix, vrfEntry.getDestPrefix());
                                     continue;
                                  }

                                  // Passing null as we don't know the dpn
                                  // to which prefix is attached at this point
                                  InstanceIdentifier<VrfEntry> vrfEntryId = getVrfEntryId(rd, vrfEntry.getDestPrefix());


                                  vrfEntry.getNextHopAddressList().add(localNextHopIp);
                                  VrfEntry newVrfEntry =
                                          new VrfEntryBuilder(vrfEntry).setNextHopAddressList(vrfEntry.getNextHopAddressList()).build();
                                  // Just update the VrfEntry
                                  FibUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION,
                                          vrfEntryId, newVrfEntry);
                                  vrfEntry = getVrfEntry(broker, rd, destPfx);
                                  LOG.trace("updated vrfEntry after populate:: {}", vrfEntry);
                              }
                          }
                          futures.add(writeTransaction.submit());
                          LOG.trace("populate FIB ends on Dpn " + dpnId
                                  + "rd  " + rd.toString()
                                  + "localNextHopIp " + localNextHopIp
                                  + "remoteNextHopIp" + remoteNextHopIp
                                  + "vpnId " + vpnId );
                          return futures;
                      }
                  });
      }
    }
  }

  public void handleRemoteRoute(final boolean action, final BigInteger localDpnId, final BigInteger remoteDpnId, final long vpnId, final String  rd, final String destPrefix , final String localNextHopIP,
                                final String remoteNextHopIp) {

      DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
      dataStoreCoordinator.enqueueJob(  "FIB" + rd.toString()
                                        + "local dpid" + localDpnId
                                        + "remote dpid" + remoteDpnId
                                        + "vpnId" + vpnId
                                        + "localNHIp" + localNextHopIP
                                        + "remoteNHIp" + remoteNextHopIp,
              new Callable<List<ListenableFuture<Void>>>() {
                  @Override
                  public List<ListenableFuture<Void>> call() throws Exception {
                      List<ListenableFuture<Void>> futures = new ArrayList<>();
                      WriteTransaction writeTransaction = broker.newWriteOnlyTransaction();
                      VrfTablesKey vrfTablesKey = new VrfTablesKey(rd);
                      VrfEntry vrfEntry = getVrfEntry(broker, rd, destPrefix);
                      if (vrfEntry == null)
                          return futures;
                      LOG.trace("handleRemoteRoute :: action {}, localDpnId {}, " +
                                "remoteDpnId {} , vpnId {}, rd {}, destPfx {}",
                                action, localDpnId, remoteDpnId, vpnId, rd, destPrefix);
                      if (action == true) {
                          vrfEntry = getVrfEntry(broker, rd, destPrefix);
                          LOG.trace("handleRemoteRoute updated(add)  vrfEntry :: {}", vrfEntry);
                          createRemoteFibEntry(remoteDpnId, vpnId, vrfTablesKey, vrfEntry, writeTransaction);
                      } else {
                          vrfEntry = getVrfEntry(broker, rd, destPrefix);
                          LOG.trace("handleRemoteRoute updated(remove)  vrfEntry :: {}", vrfEntry);
                          deleteRemoteRoute(null, remoteDpnId, vpnId, vrfTablesKey, vrfEntry, writeTransaction);
                      }
                      futures.add(writeTransaction.submit());
                      return futures;
                  }
              });
  }

  public void cleanUpDpnForVpn(final BigInteger dpnId, final long vpnId, final String rd) {
      LOG.trace("Remove dpn {} for vpn {} : cleanUpDpnForVpn", dpnId, rd);
      InstanceIdentifier<VrfTables> id = buildVrfId(rd);
      synchronized (rd.intern()) {
          final Optional<VrfTables> vrfTable = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
          if (vrfTable.isPresent()) {
              DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
              dataStoreCoordinator.enqueueJob("FIB" + vpnId + dpnId.toString(),
                      new Callable<List<ListenableFuture<Void>>>() {
                          WriteTransaction tx = broker.newWriteOnlyTransaction();
                          @Override
                          public List<ListenableFuture<Void>> call() throws Exception {
                              for (final VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
                            /* Handle subnet routes here */
                                  SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
                                  if (subnetRoute != null) {
                                      LOG.trace("Cleaning subnetroute {} on dpn {} for vpn {} : cleanUpDpnForVpn", vrfEntry.getDestPrefix(),
                                              dpnId, rd);
                                      makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW, tx);
                                      makeLFibTableEntry(dpnId, vrfEntry.getLabel(), null, DEFAULT_FIB_FLOW_PRIORITY, NwConstants.DEL_FLOW, tx);
                                      LOG.trace("cleanUpDpnForVpn: Released subnetroute label {} for rd {} prefix {}", vrfEntry.getLabel(), rd,
                                              vrfEntry.getDestPrefix());
                                      continue;
                                  }
                                  // Passing null as we don't know the dpn
                                  // to which prefix is attached at this point
                                  deleteRemoteRoute(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry, tx);
                              }
                              List<ListenableFuture<Void>> futures = new ArrayList<>();
                              futures.add(tx.submit());
                              return futures;
                          }

                      });
          }

      }
  }

  public void cleanUpDpnForVpn(final BigInteger dpnId, final long vpnId, final String rd,
                               final String localNextHopIp, final String remoteNextHopIp) {
    LOG.trace(  " cleanup remote routes on dpn {} for vpn {}, rd {}, " +
                " localNexthopIp {} , remoteNexhtHopIp {} : cleanUpDpnForVpn",
                dpnId, vpnId, rd, localNextHopIp, remoteNextHopIp);
    InstanceIdentifier<VrfTables> id = buildVrfId(rd);
    synchronized (rd.intern()) {
      final Optional<VrfTables> vrfTable = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
      if (vrfTable.isPresent()) {
          DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
          dataStoreCoordinator.enqueueJob(" FIB + on Dpn " + dpnId
                                            + rd + rd.toString()
                                            + "localNextHopIp " + localNextHopIp
                                            + "remoteNextHopIP" + remoteNextHopIp
                                            + "vpnId " + vpnId
                                            + "dpnId" + dpnId,
                  new Callable<List<ListenableFuture<Void>>>() {
                      @Override
                      public List<ListenableFuture<Void>> call() throws Exception {
                          WriteTransaction writeTransaction = broker.newWriteOnlyTransaction();
                          List<ListenableFuture<Void>> futures = new ArrayList<>();
                          LOG.trace("cleanup FIB starts on Dpn " + dpnId
                                    + "rd  " + rd.toString()
                                    + "localNextHopIp " + localNextHopIp
                                    + "remoteNextHopIp" + remoteNextHopIp
                                    + "vpnId " + vpnId );

                          for (VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
                              LOG.trace("old vrfEntry before cleanup:: {}", vrfEntry);
                              if (remoteNextHopIp.trim().equals(vrfEntry.getNextHopAddressList().get(0).trim())) {
                                  LOG.trace(" deleting remote FIB entry {}", vrfEntry);
                                  deleteRemoteRoute(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry, writeTransaction);
                              }

                              if (localNextHopIp.trim().equals(vrfEntry.getNextHopAddressList().get(0).trim())) {
                                  LOG.trace("changing the nexthopip for local VM routes {} on dpn {}",
                                            vrfEntry.getDestPrefix(), dpnId);
                                  String destPfx = vrfEntry.getDestPrefix();
                                  InstanceIdentifier<VrfEntry> vrfEntryId = getVrfEntryId(rd, destPfx);
                                  List<java.lang.String> newList = vrfEntry.getNextHopAddressList();
                                  newList.remove(localNextHopIp);
                                 VrfEntry newVrfEntry =
                                          new VrfEntryBuilder(vrfEntry).setNextHopAddressList(newList).build();
                                  FibUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION,
                                          vrfEntryId, newVrfEntry);
                                  vrfEntry = getVrfEntry(broker, rd, destPfx);
                                  LOG.trace("updated vrfEntry after cleanup:: {}", vrfEntry);
                              }
                          }
                          futures.add(writeTransaction.submit());
                          LOG.trace("cleanup FIB ends on Dpn " + dpnId
                                    + "rd  " + rd.toString()
                                    + "localNextHopIp " + localNextHopIp
                                    + "remoteNextHopIp" + remoteNextHopIp
                                    + "vpnId " + vpnId );
                          return futures;
                      }
                  });

      }
    }
  }

  public static InstanceIdentifier<VrfTables> buildVrfId(String rd) {
    InstanceIdentifierBuilder<VrfTables> idBuilder =
        InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
    InstanceIdentifier<VrfTables> id = idBuilder.build();
    return id;
  }

  private String getFlowRef(BigInteger dpnId, short tableId, long label, int priority) {
    return new StringBuilder(64).append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
        .append(tableId).append(NwConstants.FLOWID_SEPARATOR).append(label).append(NwConstants.FLOWID_SEPARATOR)
        .append(priority).toString();
  }

  private String getFlowRef(BigInteger dpnId, short tableId, String rd, int priority, InetAddress destPrefix) {
    return new StringBuilder(64).append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
        .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
        .append(rd).append(NwConstants.FLOWID_SEPARATOR)
        .append(priority).append(NwConstants.FLOWID_SEPARATOR)
        .append(destPrefix.getHostAddress()).toString();
  }

  private String getInterVpnFibFlowRef(String interVpnLinkName, String prefix, String nextHop ) {
      return new StringBuilder(64).append(FLOWID_PREFIX)
                                  .append(interVpnLinkName).append(NwConstants.FLOWID_SEPARATOR)
                                  .append(prefix).append(NwConstants.FLOWID_SEPARATOR)
                                  .append(nextHop).toString();
  }

  protected List<String> resolveAdjacency(final BigInteger remoteDpnId, final long vpnId, final VrfEntry vrfEntry,
                                          String rd) {
    List<String> adjacencyList = new ArrayList<>();
    List<String> prefixIpList = new ArrayList<>();
    LOG.trace("resolveAdjacency called with remotedpid {}, vpnId{}, VrfEntry {}", remoteDpnId, vpnId, vrfEntry);
    try {
        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
            Extraroute extra_route = getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
            if (extra_route == null) {
                prefixIpList = Arrays.asList(vrfEntry.getDestPrefix());
            } else {
                prefixIpList = new ArrayList<>();
                for (String extraRouteIp : extra_route.getNexthopIpList()) {
                    prefixIpList.add(extraRouteIp + "/32");
                }
            }
        } else {
            prefixIpList = Arrays.asList(vrfEntry.getDestPrefix());
        }

        for (String prefixIp : prefixIpList) {
            for (String nextHopIp : vrfEntry.getNextHopAddressList()) {
                LOG.debug("NextHop IP for destination {} is {}", prefixIp, nextHopIp);
                String adjacency = nextHopManager.getRemoteNextHopPointer(remoteDpnId, vpnId, prefixIp, nextHopIp);
                if (adjacency != null && !adjacency.isEmpty() && !adjacencyList.contains(adjacency)) {
                    adjacencyList.add(adjacency);
                }
            }
        }
    } catch (NullPointerException e) {
      LOG.trace("", e);
    }
    return adjacencyList;
  }

  protected VpnInstanceOpDataEntry getVpnInstance(String rd) {
    InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class).child(
        VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
    Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = FibUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
    return vpnInstanceOpData.isPresent() ? vpnInstanceOpData.get() : null;
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
        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_INTERFACE_TABLE }));
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        FlowEntity flowEntityLfib = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_LFIB_TABLE,
                getTableMissFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, NwConstants.TABLE_MISS_FLOW),
                NwConstants.TABLE_MISS_PRIORITY, "Table Miss", 0, 0, COOKIE_TABLE_MISS, matches, instructions);

        FlowEntity flowEntityFib = MDSALUtil.buildFlowEntity(dpnId,NwConstants.L3_FIB_TABLE,
                                                             getTableMissFlowRef(dpnId, NwConstants.L3_FIB_TABLE,
                                                                                 NwConstants.TABLE_MISS_FLOW),
                                                             NwConstants.TABLE_MISS_PRIORITY, "FIB Table Miss Flow",
                                                             0, 0, COOKIE_VM_FIB_TABLE,
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

    private String getTableMissFlowRef(BigInteger dpnId, short tableId, int tableMiss) {
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
    List<MatchInfo> matches = new ArrayList<MatchInfo>();
    matches.add(new MatchInfo(MatchFieldType.eth_type,
                              new long[] { NwConstants.ETHTYPE_MPLS_UC }));
    FlowEntity flowEntityToLfib = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_PROTOCOL_TABLE,
                                                          getTableMissFlowRef(dpnId, NwConstants.L3_PROTOCOL_TABLE,
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
    List<String> result = new ArrayList<String>();
    result.add(String.format("   %-7s  %-20s  %-20s  %-7s  %-7s", "RD", "Prefix", "NextHop", "Label", "Origin"));
    result.add("-------------------------------------------------------------------");
    InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);
    Optional<FibEntries> fibEntries = FibUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
    if (fibEntries.isPresent()) {
        List<VrfTables> vrfTables = fibEntries.get().getVrfTables();
        for (VrfTables vrfTable : vrfTables) {
            for (VrfEntry vrfEntry : vrfTable.getVrfEntry()) {
                for (String nextHop : vrfEntry.getNextHopAddressList()) {
                    result.add(String.format("   %-7s  %-20s  %-20s  %-7s  %-7s", vrfTable.getRouteDistinguisher(),
                            vrfEntry.getDestPrefix(), nextHop, vrfEntry.getLabel(), vrfEntry.getOrigin()));
                }
                if (vrfEntry.getNextHopAddressList().isEmpty()) {
                    result.add(String.format("   %-7s  %-20s  %-20s  %-7s  %-7s", vrfTable.getRouteDistinguisher(),
                            vrfEntry.getDestPrefix(), "local", vrfEntry.getLabel(), vrfEntry.getOrigin()));
                }
            }
        }
    }
    return result;
  }

  private void makeL3IntfTblMissFlow(BigInteger dpnId, int addOrRemove) {
    List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
    List<MatchInfo> matches = new ArrayList<MatchInfo>();
    final BigInteger COOKIE_TABLE_MISS = new BigInteger("1030000", 16);
    // Instruction to goto L3 InterfaceTable

    List <ActionInfo> actionsInfos = new ArrayList <ActionInfo> ();
    actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[]{
        Short.toString(NwConstants.LPORT_DISPATCHER_TABLE)}));
    instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
    //instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.LPORT_DISPATCHER_TABLE }));

    FlowEntity flowEntityL3Intf = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_INTERFACE_TABLE,
            getTableMissFlowRef(dpnId, NwConstants.L3_INTERFACE_TABLE, NwConstants.TABLE_MISS_FLOW),
            NwConstants.TABLE_MISS_PRIORITY, "L3 Interface Table Miss", 0, 0, COOKIE_TABLE_MISS, matches, instructions);
    if (addOrRemove == NwConstants.ADD_FLOW) {
      LOG.info("Invoking MDSAL to install L3 interface Table Miss Entries");
      mdsalManager.installFlow(flowEntityL3Intf);
    } else {
      mdsalManager.removeFlow(flowEntityL3Intf);
    }
  }

    private VrfEntry getVrfEntry(DataBroker broker, String rd, String ipPrefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId =
                    InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).
                            child(VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
        Optional<VrfEntry> vrfEntry = read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        if (vrfEntry.isPresent())  {
            return (vrfEntry.get());
        }
        return null;
    }

    private InstanceIdentifier<VrfEntry> getVrfEntryId(String rd, String ipPrefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).
                        child(VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
        return vrfEntryId;
    }
}

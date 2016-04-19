/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.IpPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.vpn.rpc.rev160201.GenerateVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.vpn.rpc.rev160201.GenerateVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.vpn.rpc.rev160201.GenerateVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.vpn.rpc.rev160201.RemoveVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.vpn.rpc.rev160201.RemoveVpnLabelInputBuilder;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.BucketInfo;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.RouterIdName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.map.ip.mapping.IpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.map.ip.mapping.IpMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.map.ip.mapping.IpMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.router.id.name.RouterIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.router.id.name.RouterIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.router.id.name.RouterIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Created by EYUGSAR on 2/20/2016.
 */

public class ExternalRoutersListener extends AsyncDataTreeChangeListenerBase<Routers, ExternalRoutersListener>{

    private static final Logger LOG = LoggerFactory.getLogger( ExternalRoutersListener.class);
    private static long label;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private IMdsalApiManager mdsalManager;
    private ItmRpcService itmManager;
    private OdlInterfaceRpcService interfaceManager;
    private IdManagerService idManager;
    private NaptManager naptManager;
    private NAPTSwitchSelector naptSwitchSelector;
    private IBgpManager bgpManager;
    private VpnRpcService vpnService;
    private FibRpcService fibService;
    private SNATDefaultRouteProgrammer defaultRouteProgrammer;
    private static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);
    static final BigInteger COOKIE_VM_LFIB_TABLE = new BigInteger("8000022", 16);

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setItmManager(ItmRpcService itmManager) {
        this.itmManager = itmManager;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
        createGroupIdPool();
    }

    void setDefaultProgrammer(SNATDefaultRouteProgrammer defaultRouteProgrammer) {
        this.defaultRouteProgrammer = defaultRouteProgrammer;
    }


    public void setInterfaceManager(OdlInterfaceRpcService interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setNaptManager(NaptManager naptManager) {
        this.naptManager = naptManager;
    }

    public void setNaptSwitchSelector(NAPTSwitchSelector naptSwitchSelector) {
        this.naptSwitchSelector = naptSwitchSelector;
    }

    public void setBgpManager(IBgpManager bgpManager) {
        this.bgpManager = bgpManager;
    }

    public void setVpnService(VpnRpcService vpnService) {
        this.vpnService = vpnService;
    }

    public void setFibService(FibRpcService fibService) {
        this.fibService = fibService;
    }

    public ExternalRoutersListener(DataBroker dataBroker )
    {
        super( Routers.class, ExternalRoutersListener.class );
        this.dataBroker = dataBroker;
    }

    @Override
    protected void add(InstanceIdentifier<Routers> identifier, Routers routers) {

        LOG.info( "Add external router event for {}", routers.getRouterName() );

        LOG.info("Installing NAT default route on all dpns part of router {}", routers.getRouterName());
        addOrDelDefFibRouteToSNAT(routers.getRouterName(), true);

        if( !routers.isEnableSnat()) {
            LOG.info( "SNAT is disabled for external router {} ", routers.getRouterName());
            return;
        }

        // Populate the router-id-name container
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        RouterIds rtrs = new RouterIdsBuilder().setKey(new RouterIdsKey(routerId)).setRouterId(routerId).setRouterName(routerName).build();
        MDSALUtil.syncWrite( dataBroker, LogicalDatastoreType.CONFIGURATION, getRoutersIdentifier(routerId), rtrs);

        handleEnableSnat(routerName);
    }

    public void handleEnableSnat(String routerName){
        LOG.info("Handling SNAT for router {}", routerName);

        // Allocate Primary Napt Switch for this router
        BigInteger primarySwitchId = naptSwitchSelector.selectNewNAPTSwitch(routerName);

        LOG.debug("NAT Service : About to create and install outbound miss entry in Primary Switch {} for router {}", primarySwitchId, routerName);
        // write metadata and punt
        installOutboundMissEntry(routerName, primarySwitchId);
        // Now install entries in SNAT tables to point to Primary for each router
        List<BigInteger> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        for(BigInteger dpnId : switches) {
              // Handle switches and NAPT switches separately
              if( dpnId != primarySwitchId ) {
                   LOG.debug("NAT Service : Handle Ordinary switch");
                   handleSwitches(dpnId, routerName, primarySwitchId);
              } else {
                   LOG.debug("NAT Service : Handle NAPT switch");
                   handlePrimaryNaptSwitch(dpnId, routerName, primarySwitchId);
              }
        }

        // call registerMapping Api
        long segmentId = NatUtil.getVpnId(dataBroker, routerName);
        LOG.debug("NAT Service : Preparing to call registerMapping for routerName {} and Id {}", routerName, segmentId);

        List<Uuid> subnetList = null;
        List<String> externalIps = null;

        InstanceIdentifier<Routers> id = InstanceIdentifier
                .builder(ExtRouters.class)
                .child(Routers.class, new RoutersKey(routerName))
                .build();

        Optional<Routers> extRouters = read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);

        if(extRouters.isPresent())
        {
            LOG.debug("NAT Service : Fetching values from extRouters model");
            Routers routerEntry= extRouters.get();
            subnetList = routerEntry.getSubnetIds();
            externalIps = routerEntry.getExternalIps();
            int counter = 0;
            int extIpCounter = externalIps.size();
            LOG.debug("NAT Service : counter values before looping counter {} and extIpCounter {}", counter, extIpCounter);
            for(Uuid subnet : subnetList) {
                  LOG.debug("NAT Service : Looping internal subnets for subnet {}", subnet);
                  InstanceIdentifier<Subnetmap> subnetmapId = InstanceIdentifier
                         .builder(Subnetmaps.class)
                         .child(Subnetmap.class, new SubnetmapKey(subnet))
                         .build();
                  Optional<Subnetmap> sn = read(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetmapId);
                  if(sn.isPresent()){
                      // subnets
                      Subnetmap subnetmapEntry = sn.get();
                      String subnetString = subnetmapEntry.getSubnetIp();
                      String[] subnetSplit = subnetString.split("/");
                      String subnetIp = subnetSplit[0];
                      String subnetPrefix = "0";
                      if(subnetSplit.length ==  2) {
                          subnetPrefix = subnetSplit[1];
                      }
                      IPAddress subnetAddr = new IPAddress(subnetIp, Integer.parseInt(subnetPrefix));
                      LOG.debug("NAT Service : subnetAddr is {} and subnetPrefix is {}", subnetAddr.getIpAddress(), subnetAddr.getPrefixLength());
                      //externalIps
                      LOG.debug("NAT Service : counter values counter {} and extIpCounter {}", counter, extIpCounter);
                      if(extIpCounter != 0) {
                            if(counter < extIpCounter) {
                                   String[] IpSplit = externalIps.get(counter).split("/");
                                   String externalIp = IpSplit[0];
                                   String extPrefix = Short.toString(NatConstants.DEFAULT_PREFIX);
                                   if(IpSplit.length==2) {
                                       extPrefix = IpSplit[1];
                                   }
                                   IPAddress externalIpAddr = new IPAddress(externalIp, Integer.parseInt(extPrefix));
                                   LOG.debug("NAT Service : externalIp is {} and extPrefix  is {}", externalIpAddr.getIpAddress(), externalIpAddr.getPrefixLength());
                                   naptManager.registerMapping(segmentId, subnetAddr, externalIpAddr);
                                   LOG.debug("NAT Service : Called registerMapping for subnetIp {}, prefix {}, externalIp {}. prefix {}", subnetIp, subnetPrefix,
                                            externalIp, extPrefix);

                                   String externalIpAddrPrefix = externalIpAddr.getIpAddress() + "/" + externalIpAddr.getPrefixLength();
                                   LOG.debug("NAT Service : Calling handleSnatReverseTraffic for primarySwitchId {}, routerName {} and externalIpAddPrefix {}", primarySwitchId, routerName, externalIpAddrPrefix);
                                   handleSnatReverseTraffic(primarySwitchId, segmentId, externalIpAddrPrefix);

                            } else {
                                   counter = 0;    //Reset the counter which runs on externalIps for round-robbin effect
                                   LOG.debug("NAT Service : Counter on externalIps got reset");
                                   String[] IpSplit = externalIps.get(counter).split("/");
                                   String externalIp = IpSplit[0];
                                   String extPrefix = Short.toString(NatConstants.DEFAULT_PREFIX);
                                   if(IpSplit.length==2) {
                                       extPrefix = IpSplit[1];
                                   }
                                   IPAddress externalIpAddr = new IPAddress(externalIp, Integer.parseInt(extPrefix));
                                   LOG.debug("NAT Service : externalIp is {} and extPrefix  is {}", externalIpAddr.getIpAddress(), externalIpAddr.getPrefixLength());
                                   naptManager.registerMapping(segmentId, subnetAddr, externalIpAddr);
                                   LOG.debug("NAT Service : Called registerMapping for subnetIp {}, prefix {}, externalIp {}. prefix {}", subnetIp, subnetPrefix,
                                            externalIp, extPrefix);

                                   String externalIpAddrPrefix = externalIpAddr.getIpAddress() + "/" + externalIpAddr.getPrefixLength();
                                   LOG.debug("NAT Service : Calling handleSnatReverseTraffic for primarySwitchId {}, routerName {} and externalIpAddPrefix {}", primarySwitchId, routerName, externalIpAddrPrefix);
                                   handleSnatReverseTraffic(primarySwitchId, segmentId, externalIpAddrPrefix);

                            }
                      }
                      counter++;
                      LOG.debug("NAT Service : Counter on externalIps incremented to {}", counter);

                  } else {
                      LOG.warn("NAT Service : No internal subnets present in extRouters Model");
                  }
            }
        }

        LOG.info("NAT Service : handleEnableSnat() Exit");
    }

    private void addOrDelDefFibRouteToSNAT(String routerName, boolean create) {
        //Router ID is used as the internal VPN's name, hence the vrf-id in VpnInstance Op DataStore
        InstanceIdentifier<VpnInstanceOpDataEntry> id = NatUtil.getVpnInstanceOpDataIdentifier(routerName);
        Optional<VpnInstanceOpDataEntry> vpnInstOp = NatUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = vpnInstOp.get().getVpnToDpnList();
            if(dpnListInVpn == null) {
                LOG.debug("Current no dpns part of router {} to program default NAT route", routerName);
                return;
            }
            long vpnId = NatUtil.readVpnId(dataBroker, routerName);
            if(vpnId == NatConstants.INVALID_ID) {
                LOG.error("Could not retrieve router Id for {} to program default NAT route in FIB", routerName);
                return;
            }
            for (VpnToDpnList dpn : dpnListInVpn) {
                BigInteger dpnId = dpn.getDpnId();
                if (create == true) {
                    //installDefNATRouteInDPN(dpnId, vpnId);
                    defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnId);
                } else {
                    //removeDefNATRouteInDPN(dpnId, vpnId);
                    defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, vpnId);
                }
            }
        }
    }

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType, InstanceIdentifier<T> path)
    {
        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try
        {
            result = tx.read(datastoreType, path).get();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return result;
    }

    public void close() throws Exception
    {
        if (listenerRegistration != null)
        {
            try
            {
                listenerRegistration.close();
            }
            catch (final Exception e)
            {
                LOG.error("Error when cleaning up ExternalRoutersListener.", e);
            }

            listenerRegistration = null;
        }
        LOG.debug("ExternalRoutersListener Closed");
    }

    protected void installOutboundMissEntry(String routerName, BigInteger primarySwitchId) {
        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        LOG.debug("NAT Service : Router ID from getVpnId {}", routerId);
        if(routerId != NatConstants.INVALID_ID) {
            LOG.debug("NAT Service : Creating miss entry on primary {}, for router {}", primarySwitchId, routerId);
            createOutboundTblEntry(primarySwitchId, routerId);
        } else {
            LOG.error("NAT Service : Unable to fetch Router Id  for RouterName {}, failed to createAndInstallMissEntry", routerName);
        }
    }

    public String getFlowRefOutbound(BigInteger dpnId, short tableId, long routerID) {
        return new StringBuilder().append(NatConstants.NAPT_FLOWID_PREFIX).append(dpnId).append(NatConstants.FLOWID_SEPARATOR).
                append(tableId).append(NatConstants.FLOWID_SEPARATOR).append(routerID).toString();
    }

    public BigInteger getCookieOutboundFlow(long routerId) {
        return NatConstants.COOKIE_OUTBOUND_NAPT_TABLE.add(new BigInteger("0110001", 16)).add(
                BigInteger.valueOf(routerId));
    }

    protected FlowEntity buildOutboundFlowEntity(BigInteger dpId, long routerId) {
        LOG.debug("NAT Service : buildOutboundFlowEntity called for dpId {} and routerId{}", dpId, routerId);
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                BigInteger.valueOf(routerId), MetaDataUtil.METADATA_MASK_VRFID }));

        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
        actionsInfos.add(new ActionInfo(ActionType.punt_to_controller, new String[] {}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        instructions.add(new InstructionInfo(InstructionType.write_metadata, new BigInteger[] { BigInteger.valueOf(routerId), MetaDataUtil.METADATA_MASK_VRFID }));

        String flowRef = getFlowRefOutbound(dpId, NatConstants.OUTBOUND_NAPT_TABLE, routerId);
        BigInteger cookie = getCookieOutboundFlow(routerId);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NatConstants.OUTBOUND_NAPT_TABLE, flowRef,
                5, flowRef, 0, 0,
                cookie, matches, instructions);
        LOG.debug("NAT Service : returning flowEntity {}", flowEntity);
        return flowEntity;
    }

    public void createOutboundTblEntry(BigInteger dpnId, long routerId) {
        LOG.debug("NAT Service : createOutboundTblEntry called for dpId {} and routerId {}", dpnId, routerId);
        FlowEntity flowEntity = buildOutboundFlowEntity(dpnId, routerId);
        LOG.debug("NAT Service : Installing flow {}", flowEntity);
        mdsalManager.installFlow(flowEntity);
    }

    protected String getTunnelInterfaceName(BigInteger srcDpId, BigInteger dstDpId) {
        try {
            Future<RpcResult<GetTunnelInterfaceNameOutput>> result = itmManager.getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder()
                                                                                 .setSourceDpid(srcDpId)
                                                                                 .setDestinationDpid(dstDpId).build());
            RpcResult<GetTunnelInterfaceNameOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException | NullPointerException e) {
            LOG.warn("NAT Service : Exception when getting tunnel interface Id for tunnel between {} and  {}", srcDpId, dstDpId);
        }

        return null;
    }

    protected List<ActionInfo> getEgressActionsForInterface(String ifName, long routerId) {
        LOG.debug("NAT Service : getEgressActionsForInterface called for interface {}", ifName);
        List<ActionInfo> listActionInfo = new ArrayList<ActionInfo>();
        try {
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                interfaceManager.getEgressActionsForInterface(
                    new GetEgressActionsForInterfaceInputBuilder().setIntfName(ifName).setTunnelKey(routerId).build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}", ifName, rpcResult.getErrors());
            } else {
                List<Action> actions =
                    rpcResult.getResult().getAction();
                for (Action action : actions) {
                    org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action actionClass = action.getAction();
                    if (actionClass instanceof OutputActionCase) {
                        listActionInfo.add(new ActionInfo(ActionType.output,
                                                          new String[] {((OutputActionCase)actionClass).getOutputAction()
                                                                            .getOutputNodeConnector().getValue()}));
                    } else if (actionClass instanceof PushVlanActionCase) {
                        listActionInfo.add(new ActionInfo(ActionType.push_vlan, new String[] {}));
                    } else if (actionClass instanceof SetFieldCase) {
                        if (((SetFieldCase)actionClass).getSetField().getVlanMatch() != null) {
                            int vlanVid = ((SetFieldCase)actionClass).getSetField().getVlanMatch().getVlanId().getVlanId().getValue();
                            listActionInfo.add(new ActionInfo(ActionType.set_field_vlan_vid,
                                                              new String[] { Long.toString(vlanVid) }));
                        }
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", ifName, e);
        }
        return listActionInfo;
    }

    protected void installSnatMissEntry(BigInteger dpnId, List<BucketInfo> bucketInfo, String routerName) {
        LOG.debug("NAT Service : installSnatMissEntry called for dpnId {} with primaryBucket {} ", dpnId, bucketInfo.get(0));
        // Install the select group
        long groupId = createGroupId(getGroupIdKey(routerName));
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll, bucketInfo);
        LOG.debug("NAT Service : installing the SNAT to NAPT GroupEntity:{}", groupEntity);
        mdsalManager.installGroup(groupEntity);
        // Install miss entry pointing to group
        FlowEntity flowEntity = buildSnatFlowEntity(dpnId, routerName, groupId);
        mdsalManager.installFlow(flowEntity);
    }

    public FlowEntity buildSnatFlowEntity(BigInteger dpId, String routerName, long groupId) {

        LOG.debug("NAT Service : buildSnatFlowEntity is called for dpId {}, routerName {} and groupId {}", dpId, routerName, groupId );
        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                BigInteger.valueOf(routerId), MetaDataUtil.METADATA_MASK_VRFID }));


        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        List<ActionInfo> actionsInfo = new ArrayList<ActionInfo>();

        ActionInfo actionSetField = new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[] {
                        BigInteger.valueOf(routerId)}) ;
        actionsInfo.add(actionSetField);
        LOG.debug("NAT Service : Setting the tunnel to the list of action infos {}", actionsInfo);
        actionsInfo.add(new ActionInfo(ActionType.group, new String[] {String.valueOf(groupId)}));
        instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfo));
        String flowRef = getFlowRefSnat(dpId, NatConstants.PSNAT_TABLE, routerName);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NatConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NatConstants.COOKIE_SNAT_TABLE, matches, instructions);

        LOG.debug("NAT Service : Returning SNAT Flow Entity {}", flowEntity);
        return flowEntity;
    }

    // TODO : Replace this with ITM Rpc once its available with full functionality
    protected void installTerminatingServiceTblEntry(BigInteger dpnId, String routerName) {
        LOG.debug("NAT Service : creating entry for Terminating Service Table for switch {}, routerName {}", dpnId, routerName);
        FlowEntity flowEntity = buildTsFlowEntity(dpnId, routerName);
        mdsalManager.installFlow(flowEntity);

    }

    private FlowEntity buildTsFlowEntity(BigInteger dpId, String routerName) {

        BigInteger routerId = BigInteger.valueOf (NatUtil.getVpnId(dataBroker, routerName));
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));
        matches.add(new MatchInfo(MatchFieldType.tunnel_id, new  BigInteger[] {routerId }));

        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        instructions.add(new InstructionInfo(InstructionType.write_metadata, new BigInteger[]
                { routerId, MetaDataUtil.METADATA_MASK_VRFID }));
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[]
                { NatConstants.OUTBOUND_NAPT_TABLE }));
        String flowRef = getFlowRefTs(dpId, NatConstants.TERMINATING_SERVICE_TABLE, routerId.longValue());
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NatConstants.TERMINATING_SERVICE_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, 0, 0,
                NatConstants.COOKIE_TS_TABLE, matches, instructions);
        return flowEntity;
    }

    public String getFlowRefTs(BigInteger dpnId, short tableId, long routerID) {
        return new StringBuilder().append(NatConstants.NAPT_FLOWID_PREFIX).append(dpnId).append(NatConstants.FLOWID_SEPARATOR).
                append(tableId).append(NatConstants.FLOWID_SEPARATOR).append(routerID).toString();
    }

    public static String getFlowRefSnat(BigInteger dpnId, short tableId, String routerID) {
        return new StringBuilder().append(NatConstants.SNAT_FLOWID_PREFIX).append(dpnId).append(NatConstants.FLOWID_SEPARATOR).
            append(tableId).append(NatConstants.FLOWID_SEPARATOR).append(routerID).toString();
    }

    private String getGroupIdKey(String routerName){
        String groupIdKey = new String("snatmiss." + routerName);
        return groupIdKey;
    }

    protected long createGroupId(String groupIdKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(NatConstants.SNAT_IDPOOL_NAME).setIdKey(groupIdKey)
            .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.trace("",e);
        }
        return 0;
    }

    protected void createGroupIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(NatConstants.SNAT_IDPOOL_NAME)
            .setLow(NatConstants.SNAT_ID_LOW_VALUE)
            .setHigh(NatConstants.SNAT_ID_HIGH_VALUE)
            .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
                if ((result != null) && (result.get().isSuccessful())) {
                    LOG.debug("NAT Service : Created GroupIdPool");
                } else {
                    LOG.error("NAT Service : Unable to create GroupIdPool");
                }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create PortPool for NAPT Service",e);
        }
    }

    protected void handleSwitches (BigInteger dpnId, String routerName, BigInteger primarySwitchId) {
        LOG.debug("NAT Service : Installing SNAT miss entry in switch {}", dpnId);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = getTunnelInterfaceName( dpnId, primarySwitchId);
        List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
        long routerId = NatUtil.getVpnId(dataBroker, routerName);

        if(ifNamePrimary != null) {
            LOG.debug("NAT Service : On Non- Napt switch , Primary Tunnel interface is {}", ifNamePrimary);
            listActionInfoPrimary = getEgressActionsForInterface(ifNamePrimary, routerId);
        }
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);

        listBucketInfo.add(0, bucketPrimary);
        installSnatMissEntry(dpnId, listBucketInfo, routerName);

    }

    protected void handlePrimaryNaptSwitch (BigInteger dpnId, String routerName, BigInteger primarySwitchId) {

           /*
            * Primary NAPT Switch – bucket Should always point back to its own Outbound Table
            */

            LOG.debug("NAT Service : Installing SNAT miss entry in Primary NAPT switch {} ", dpnId);

            List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
            List<ActionInfo> listActionInfoPrimary =  new ArrayList<ActionInfo>();
            listActionInfoPrimary.add(new ActionInfo(ActionType.nx_resubmit, new String[]{String.valueOf(NatConstants.TERMINATING_SERVICE_TABLE)}));
            BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
            listBucketInfo.add(0, bucketPrimary);

            long routerId = NatUtil.getVpnId(dataBroker, routerName);

            installSnatMissEntry(dpnId, listBucketInfo, routerName);
            installTerminatingServiceTblEntry(dpnId, routerName);
            installNaptPfibEntry(dpnId, routerId);

    }

    public void installNaptPfibEntry(BigInteger dpnId, long routerId) {
        LOG.debug("NAT Service : installNaptPfibEntry called for dpnId {} and routerId {} ", dpnId, routerId);
        FlowEntity flowEntity = buildNaptPfibFlowEntity(dpnId, routerId);
        mdsalManager.installFlow(flowEntity);
    }

    public FlowEntity buildNaptPfibFlowEntity(BigInteger dpId, long routerId) {

        LOG.debug("NAT Service : buildNaptPfibFlowEntity is called for dpId {}, routerId {}", dpId, routerId );
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                BigInteger.valueOf(routerId), MetaDataUtil.METADATA_MASK_VRFID }));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionInfo(ActionType.nx_resubmit, new String[] { Integer.toString(NatConstants.L3_FIB_TABLE) }));
        instructionInfo.add(new InstructionInfo(InstructionType.apply_actions, listActionInfo));

        String flowRef = getFlowRefTs(dpId, NatConstants.NAPT_PFIB_TABLE, routerId);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NatConstants.NAPT_PFIB_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NatConstants.COOKIE_SNAT_TABLE, matches, instructionInfo);

        LOG.debug("NAT Service : Returning NaptPFib Flow Entity {}", flowEntity);
        return flowEntity;
    }

    private void handleSnatReverseTraffic(BigInteger dpnId, long routerId, String externalIp) {
        LOG.debug("NAT Service : handleSnatReverseTraffic() entry for DPN ID, routerId, externalIp : {}", dpnId, routerId, externalIp);
        Uuid networkId = NatUtil.getNetworkIdFromRouterId(dataBroker, routerId);
        if(networkId == null) {
            LOG.error("NAT Service : networkId is null for the router ID {}", routerId);
            return;
        }
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId, LOG);
        if(vpnName == null) {
            LOG.error("NAT Service : No VPN associated with ext nw {} to handle add external ip configuration {} in router {}",
                    networkId, externalIp, routerId);
            return;
        }
        advToBgpAndInstallFibAndTsFlows(dpnId, NatConstants.INBOUND_NAPT_TABLE, vpnName, routerId, externalIp, vpnService, fibService, bgpManager, dataBroker, LOG);
        LOG.debug("NAT Service : handleSnatReverseTraffic() exit for DPN ID, routerId, externalIp : {}", dpnId, routerId, externalIp);
    }

    public void advToBgpAndInstallFibAndTsFlows(final BigInteger dpnId, final short tableId, final String vpnName, final long routerId, final String externalIp,
                                                VpnRpcService vpnService, final FibRpcService fibService, final IBgpManager bgpManager, final DataBroker dataBroker,
                                                final Logger log){
        LOG.debug("NAT Service : advToBgpAndInstallFibAndTsFlows() entry for DPN ID {}, tableId {}, vpnname {} and externalIp {}", dpnId, tableId, vpnName, externalIp);
        //Generate VPN label for the external IP
        GenerateVpnLabelInput labelInput = new GenerateVpnLabelInputBuilder().setVpnName(vpnName).setIpPrefix(externalIp).build();
        Future<RpcResult<GenerateVpnLabelOutput>> labelFuture = vpnService.generateVpnLabel(labelInput);

        //On successful generation of the VPN label, advertise the route to the BGP and install the FIB routes.
        ListenableFuture<RpcResult<Void>> future = Futures.transform(JdkFutureAdapters.listenInPoolThread(labelFuture), new AsyncFunction<RpcResult<GenerateVpnLabelOutput>, RpcResult<Void>>() {

            @Override
            public ListenableFuture<RpcResult<Void>> apply(RpcResult<GenerateVpnLabelOutput> result) throws Exception {
                if (result.isSuccessful()) {
                    LOG.debug("NAT Service : inside apply with result success");
                    GenerateVpnLabelOutput output = result.getResult();
                    long label = output.getLabel();

                    //Inform BGP
                    String rd = NatUtil.getVpnRd(dataBroker, vpnName);
                    String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, dpnId);
                    NatUtil.addPrefixToBGP(bgpManager, rd, externalIp, nextHopIp, label, log);

                    //Get IPMaps from the DB for the router ID
                    List<IpMap> dbIpMaps = naptManager.getIpMapList(dataBroker, routerId);

                    for (IpMap dbIpMap : dbIpMaps) {
                        String dbExternalIp = dbIpMap.getExternalIp();
                        //Select the IPMap, whose external IP is the IP for which FIB is installed
                        if (externalIp.contains(dbExternalIp)) {
                            String dbInternalIp = dbIpMap.getInternalIp();
                            IpMapKey dbIpMapKey = dbIpMap.getKey();
                            IpMap newIpm = new IpMapBuilder().setKey(dbIpMapKey).setInternalIp(dbInternalIp).setExternalIp(dbExternalIp).setLabel(label).build();
                            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, naptManager.getIpMapIdentifier(routerId, dbInternalIp), newIpm);
                        }
                    }

                    //Install custom FIB routes
                    List<Instruction> customInstructions = new ArrayList<>();
                    customInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[]{tableId}).buildInstruction(0));
                    makeTunnelTableEntry(dpnId, label, customInstructions);
                    makeLFibTableEntry(dpnId, label, customInstructions);

                    CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName).setSourceDpid(dpnId)
                            .setIpAddress(externalIp).setServiceId(label).setInstruction(customInstructions).build();
                    Future<RpcResult<Void>> future = fibService.createFibEntry(input);
                    return JdkFutureAdapters.listenInPoolThread(future);
                } else {
                    LOG.error("NAT Service : inside apply with result failed");
                    String errMsg = String.format("Could not retrieve the label for prefix %s in VPN %s, %s", externalIp, vpnName, result.getErrors());
                    return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                }
            }
        });

            Futures.addCallback(future, new FutureCallback<RpcResult<Void>>() {

                @Override
                public void onFailure(Throwable error) {
                    log.error("NAT Service : Error in generate label or fib install process", error);
                }

                @Override
                public void onSuccess(RpcResult<Void> result) {
                    if (result.isSuccessful()) {
                        log.info("NAT Service : Successfully installed custom FIB routes for prefix {}", externalIp);
                    } else {
                        log.error("NAT Service : Error in rpc call to create custom Fib entries for prefix {} in DPN {}, {}", externalIp, dpnId, result.getErrors());
                    }
                }
            });
     }

    private void makeLFibTableEntry(BigInteger dpId, long serviceId, List<Instruction> customInstructions) {
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x8847L }));
        matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(serviceId)}));

        List<Instruction> instructions = new ArrayList<Instruction>();
        List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
        actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{}));
        Instruction writeInstruction = new InstructionInfo(InstructionType.write_actions, actionsInfos).buildInstruction(0);
        instructions.add(writeInstruction);
        instructions.addAll(customInstructions);

        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
                10, flowRef, 0, 0,
                COOKIE_VM_LFIB_TABLE, matches, instructions);

        mdsalManager.installFlow(dpId, flowEntity);

        LOG.debug("NAT Service : LFIB Entry for dpID {} : label : {} modified successfully {}",dpId, serviceId );
    }

    private void makeTunnelTableEntry(BigInteger dpnId, long serviceId, List<Instruction> customInstructions) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();

        LOG.debug("NAT Service : Create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}", dpnId , serviceId);

        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(serviceId)}));

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""), 5, String.format("%s:%d","TST Flow Entry ",serviceId),
                0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)),mkMatches, customInstructions);

        mdsalManager.installFlow(dpnId, terminatingServiceTableFlowEntity);
    }

    protected InstanceIdentifier<RouterIds> getRoutersIdentifier(long routerId) {
        InstanceIdentifier<RouterIds> id = InstanceIdentifier.builder(
                RouterIdName.class).child(RouterIds.class, new RouterIdsKey(routerId)).build();
        return id;
    }

    private String getFlowRef(BigInteger dpnId, short tableId, long id, String ipAddress) {
        return new StringBuilder(64).append(NatConstants.SNAT_FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
                .append(id).append(NwConstants.FLOWID_SEPARATOR).append(ipAddress).toString();
    }

    @Override
    protected void update(InstanceIdentifier<Routers> identifier, Routers original, Routers update) {
        boolean originalSNATEnabled = original.isEnableSnat();
        boolean updatedSNATEnabled = update.isEnableSnat();
        if(originalSNATEnabled != updatedSNATEnabled) {
            if(originalSNATEnabled) {
                //SNAT disabled for the router
                String routerName = original.getRouterName();
                Uuid networkUuid = original.getNetworkId();
                List<String> externalIps = original.getExternalIps();
                LOG.info("NAT Service : SNAT disabled for Router {}", routerName);
                handleDisableSnat(routerName, networkUuid, externalIps);
            } else {
                String routerName = original.getRouterName();
                LOG.info("NAT Service : SNAT enabled for Router {}", original.getRouterName());
                handleEnableSnat(routerName);
            }
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Routers> identifier, Routers router) {
        LOG.trace("NAT Service : Router delete method");
        {
        /*
            ROUTER DELETE SCENARIO
            1) Get the router ID from the event.
            2) Build the cookie information from the router ID.
            3) Get the primary and secondary switch DPN IDs using the router ID from the model.
            4) Build the flow with the cookie value.
            5) Delete the flows which matches the cookie information from the NAPT outbound, inbound tables.
            6) Remove the flows from the other switches which points to the primary and secondary switches for the flows related the router ID.
            7) Get the list of external IP address maintained for the router ID.
            8) Use the NaptMananager removeMapping API to remove the list of IP addresses maintained.
            9) Withdraw the corresponding routes from the BGP.
         */

            if (identifier == null || router == null) {
                LOG.info("++++++++++++++NAT Service : ExternalRoutersListener:remove:: returning without processing since routers is null");
                return;
            }

            String routerName = router.getRouterName();
            LOG.info("Removing default NAT route from FIB on all dpns part of router {} ", routerName);
            addOrDelDefFibRouteToSNAT(routerName, false);
            Uuid networkUuid = router.getNetworkId();
            List<String> externalIps = router.getExternalIps();
            handleDisableSnat(routerName, networkUuid, externalIps);
        }
    }

    public void handleDisableSnat(String routerName, Uuid networkUuid, List<String> externalIps){
        LOG.info("NAT Service : handleDisableSnat() Entry");
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);

        BigInteger naptSwitchDpnId = null;
        InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitch = NatUtil.buildNaptSwitchRouterIdentifier(routerName);
        Optional<RouterToNaptSwitch> rtrToNapt = read(dataBroker, LogicalDatastoreType.OPERATIONAL, routerToNaptSwitch );
        if(rtrToNapt.isPresent()) {
            naptSwitchDpnId = rtrToNapt.get().getPrimarySwitchId();
        }
        LOG.debug("NAT Service : got primarySwitch as dpnId{} ", naptSwitchDpnId);

        removeNaptFlowsFromActiveSwitch(routerId, routerName, naptSwitchDpnId);
        removeFlowsFromNonActiveSwitches(routerName, naptSwitchDpnId);
        advToBgpAndRemoveFibAndTsFlows(naptSwitchDpnId, routerId, networkUuid, externalIps);

        //Use the NaptMananager removeMapping API to remove the entire list of IP addresses maintained for the router ID.
        LOG.debug("NAT Service : Remove the Internal to external IP address maintained for the router ID {} in the DS", routerId);
        naptManager.removeMapping(routerId);

        LOG.info("NAT Service : handleDisableSnat() Exit");
    }

    public void removeNaptFlowsFromActiveSwitch(long routerId, String routerName, BigInteger dpnId){
        LOG.debug("NAT Service : Remove NAPT flows from Active switch");
        BigInteger cookieSnatFlow = NatUtil.getCookieNaptFlow(routerId);

        //Remove the PSNAT entry which forwards the packet to Terminating Service table
        String pSNatFlowRef = getFlowRefSnat(dpnId, NatConstants.PSNAT_TABLE, routerName);
        FlowEntity pSNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NatConstants.PSNAT_TABLE, pSNatFlowRef);

        LOG.info("NAT Service : Remove the flow in the " + NatConstants.PSNAT_TABLE + " for the active switch with the DPN ID {} and router ID {}", dpnId, routerId);
        mdsalManager.removeFlow(pSNatFlowEntity);

        //Remove the group entry which resubmits the packet to the Terminating Service table or to the out port accordingly.
        long groupId = createGroupId(getGroupIdKey(routerName));
        List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
        GroupEntity pSNatGroupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll, listBucketInfo);

        LOG.info("NAT Service : Remove the group {} for the active switch with the DPN ID {} and router ID {}", groupId, dpnId, routerId);
        mdsalManager.removeGroup(pSNatGroupEntity);

        //Remove the Terminating Service table entry which forwards the packet to Outbound NAPT Table
        String tsFlowRef = getFlowRefTs(dpnId, NatConstants.TERMINATING_SERVICE_TABLE, routerId);
        FlowEntity tsNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NatConstants.TERMINATING_SERVICE_TABLE, tsFlowRef);

        LOG.info("NAT Service : Remove the flow in the " + NatConstants.TERMINATING_SERVICE_TABLE + " for the active switch with the DPN ID {} and router ID {}", dpnId, routerId);
        mdsalManager.removeFlow(tsNatFlowEntity);

        //Remove the Outbound flow entry which forwards the packet to FIB Table
        String outboundNatFlowRef = getFlowRefOutbound(dpnId, NatConstants.OUTBOUND_NAPT_TABLE, routerId);
        FlowEntity outboundNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NatConstants.OUTBOUND_NAPT_TABLE, outboundNatFlowRef);

        LOG.info("NAT Service : Remove the flow in the " + NatConstants.OUTBOUND_NAPT_TABLE + " for the active switch with the DPN ID {} and router ID {}", dpnId, routerId);
        mdsalManager.removeFlow(outboundNatFlowEntity);

        //Remove the NAPT PFIB TABLE which forwards the packet to FIB Table
        String natPfibFlowRef = getFlowRefTs(dpnId, NatConstants.NAPT_PFIB_TABLE, routerId);
        FlowEntity natPfibFlowEntity = NatUtil.buildFlowEntity(dpnId, NatConstants.NAPT_PFIB_TABLE, natPfibFlowRef);

        LOG.info("NAT Service : Remove the flow in the " + NatConstants.NAPT_PFIB_TABLE + " for the active switch with the DPN ID {} and router ID {}", dpnId, routerId);
        mdsalManager.removeFlow(natPfibFlowEntity);

        //For the router ID get the internal IP , internal port and the corresponding external IP and external Port.
        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if(ipPortMapping == null){
            LOG.error("NAT Service : Unable to retrieve the IpPortMapping");
            return;
        }

        List<IntextIpProtocolType> intextIpProtocolTypes = ipPortMapping.getIntextIpProtocolType();
        for(IntextIpProtocolType intextIpProtocolType : intextIpProtocolTypes){
            List<IpPortMap> ipPortMaps = intextIpProtocolType.getIpPortMap();
            for(IpPortMap ipPortMap : ipPortMaps){
                String ipPortInternal = ipPortMap.getIpPortInternal();
                String[] ipPortParts = ipPortInternal.split(":");
                if(ipPortParts.length != 2) {
                    LOG.error("NAT Service : Unable to retrieve the Internal IP and port");
                    return;
                }
                String internalIp = ipPortParts[0];
                String internalPort = ipPortParts[1];

                //Build the flow for the outbound NAPT table
                String switchFlowRef = NatUtil.getNaptFlowRef(dpnId, NatConstants.OUTBOUND_NAPT_TABLE, String.valueOf(routerId), internalIp, Integer.valueOf(internalPort));
                FlowEntity outboundNaptFlowEntity = NatUtil.buildFlowEntity(dpnId, NatConstants.OUTBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                LOG.info("NAT Service : Remove the flow in the " + NatConstants.OUTBOUND_NAPT_TABLE + " for the active switch with the DPN ID {} and router ID {}", dpnId, routerId);
                mdsalManager.removeFlow(outboundNaptFlowEntity);

                IpPortExternal ipPortExternal = ipPortMap.getIpPortExternal();
                String externalIp = ipPortExternal.getIpAddress();
                int externalPort = ipPortExternal.getPortNum();

                //Build the flow for the inbound NAPT table
                switchFlowRef = NatUtil.getNaptFlowRef(dpnId, NatConstants.INBOUND_NAPT_TABLE, String.valueOf(routerId), externalIp, externalPort);
                FlowEntity inboundNaptFlowEntity = NatUtil.buildFlowEntity(dpnId, NatConstants.INBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                LOG.info("NAT Service : Remove the flow in the " + NatConstants.INBOUND_NAPT_TABLE + " for the active active switch with the DPN ID {} and router ID {}", dpnId, routerId);
                mdsalManager.removeFlow(inboundNaptFlowEntity);
            }
        }
    }

    public void removeFlowsFromNonActiveSwitches(String routerName, BigInteger naptSwitchDpnId){
        LOG.debug("NAT Service : Remove NAPT related flows from non active switches");

        //Remove the flows from the other switches which points to the primary and secondary switches for the flows related the router ID.
        List<VpnToDpnList> allSwitchList = NatUtil.getVpnToDpnList(dataBroker, routerName);
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        for (VpnToDpnList eachSwitch : allSwitchList) {
            BigInteger dpnId = eachSwitch.getDpnId();
            if (naptSwitchDpnId != dpnId) {
                LOG.info("NAT Service : Handle Ordinary switch");

                //Remove the PSNAT entry which forwards the packet to Terminating Service table
                String pSNatFlowRef = getFlowRefSnat(dpnId, NatConstants.PSNAT_TABLE, String.valueOf(routerName));
                FlowEntity pSNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NatConstants.PSNAT_TABLE, pSNatFlowRef);

                LOG.info("Remove the flow in the " + NatConstants.PSNAT_TABLE + " for the non active switch with the DPN ID {} and router ID {}", dpnId, routerId);
                mdsalManager.removeFlow(pSNatFlowEntity);

                //Remove the group entry which resubmits the packet to the Terminating Service table or to the out port accordingly.
                long groupId = createGroupId(getGroupIdKey(routerName));
                List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
                GroupEntity pSNatGroupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll, listBucketInfo);

                LOG.info("NAT Service : Remove the group {} for the non active switch with the DPN ID {} and router ID {}", groupId, dpnId, routerId);
                mdsalManager.removeGroup(pSNatGroupEntity);

            }
        }
    }

    public void advToBgpAndRemoveFibAndTsFlows(final BigInteger dpnId, Long routerId, Uuid networkUuid, List<String> externalIps){
        //Withdraw the corresponding routes from the BGP.
        //Get the network ID using the router ID.
        LOG.debug("NAT Service : Advertise to BGP and remove routes");
        if(networkUuid == null ){
            LOG.error("NAT Service : networkId is null");
            return;
        }

        //Get the VPN Name using the network ID
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkUuid, LOG);
        if (vpnName == null) {
            LOG.error("No VPN associated with ext nw {} for the router {}",
                    networkUuid, routerId);
            return;
        }

        //Inform BGP about the route removal
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        String prefix = "32";
        NatUtil.removePrefixFromBGP(bgpManager, rd, prefix, LOG);

        //Remove custom FIB routes
        //Future<RpcResult<java.lang.Void>> removeFibEntry(RemoveFibEntryInput input);
        final String externalIp = externalIps.get(0);

        //Get IPMaps from the DB for the router ID
        List<IpMap> dbIpMaps = naptManager.getIpMapList(dataBroker, routerId);
        if(dbIpMaps == null ){
            LOG.error("NAT Service : IPMaps is null");
            return;
        }

        long tempLabel = -1;
        for(IpMap dbIpMap: dbIpMaps) {
            String dbExternalIp = dbIpMap.getExternalIp();
            //Select the IPMap, whose external IP is the IP for which FIB is installed
            if (externalIp.contains(dbExternalIp)) {
                tempLabel = dbIpMap.getLabel();
                break;
            }
        }
        if(tempLabel == -1){
            LOG.error("NAT Service : Label is null");
            return;
        }

        final long label = tempLabel;
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName).setSourceDpid(dpnId).setIpAddress(externalIp + "/" +
                NatConstants.DEFAULT_PREFIX).setServiceId(label).build();
        Future<RpcResult<Void>> future = fibService.removeFibEntry(input);

        ListenableFuture<RpcResult<Void>> labelFuture = Futures.transform(JdkFutureAdapters.listenInPoolThread(future), new AsyncFunction<RpcResult<Void>, RpcResult<Void>>() {

            @Override
            public ListenableFuture<RpcResult<Void>> apply(RpcResult<Void> result) throws Exception {
                //Release label
                if (result.isSuccessful()) {
                    removeTunnelTableEntry(dpnId, label);
                    removeLFibTableEntry(dpnId, label);
                    RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder().setVpnName(vpnName).setIpPrefix(externalIp).build();
                    Future<RpcResult<Void>> labelFuture = vpnService.removeVpnLabel(labelInput);
                    return JdkFutureAdapters.listenInPoolThread(labelFuture);
                } else {
                    String errMsg = String.format("RPC call to remove custom FIB entries on dpn %s for prefix %s Failed - %s", dpnId, externalIp, result.getErrors());
                    LOG.error(errMsg);
                    return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                }
            }

        });

        Futures.addCallback(labelFuture, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("NAT Service : Error in removing the label or custom fib entries", error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    LOG.debug("NAT Service : Successfully removed the label for the prefix {} from VPN {}", externalIp, vpnName);
                } else {
                    LOG.error("NAT Service : Error in removing the label for prefix {} from VPN {}, {}", externalIp, vpnName, result.getErrors());
                }
            }
        });
    }

    private void removeTunnelTableEntry(BigInteger dpnId, long serviceId) {
        LOG.info("NAT Service : remove terminatingServiceActions called with DpnId = {} and label = {}", dpnId , serviceId);
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(serviceId)}));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""),
                5, String.format("%s:%d","TST Flow Entry ",serviceId), 0, 0,
                COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)), mkMatches, null);
        mdsalManager.removeFlow(dpnId, flowEntity);
        LOG.debug("NAT Service : Terminating service Entry for dpID {} : label : {} removed successfully {}",dpnId, serviceId);
    }

    private void removeLFibTableEntry(BigInteger dpnId, long serviceId) {
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x8847L }));
        matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(serviceId)}));

        String flowRef = getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        LOG.debug("NAT Service : removing LFib entry with flow ref {}", flowRef);

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
                10, flowRef, 0, 0,
                COOKIE_VM_LFIB_TABLE, matches, null);

        mdsalManager.removeFlow(dpnId, flowEntity);

        LOG.debug("NAT Service : LFIB Entry for dpID : {} label : {} removed successfully {}",dpnId, serviceId);
    }

    public static GroupEntity buildGroupEntity(BigInteger dpnId, long groupId) {
        GroupEntity groupEntity = new GroupEntity(dpnId);
        groupEntity.setGroupId(groupId);
        return groupEntity;
    }

    protected InstanceIdentifier<Routers> getWildCardPath()
    {
        return InstanceIdentifier.create(ExtRouters.class).child(Routers.class);
    }

    @Override
    protected ExternalRoutersListener getDataTreeChangeListener()
    {
        return ExternalRoutersListener.this;
    }

}

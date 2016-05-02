/*
 * Copyright (c) 2015, 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.impl;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.CheckedFuture;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.itm.confighelpers.HwVtep;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.TunnelMonitorEnabled;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.TunnelMonitorInterval;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.VtepConfigSchemas;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.VtepIpPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchemaKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.ip.pools.VtepIpPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.ip.pools.VtepIpPoolKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.interfaces._interface.NodeIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.interfaces._interface.NodeIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.interfaces._interface.NodeIdentifierKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.DpnEndpointsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPointsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPointsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnels_state.StateTunnelListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.vtep.config.schema.DpnIds ;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice._interface.service.rev150602._interface.service.info.ServiceInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.vtep.config.schema.DpnIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.vtep.config.schema.DpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class ItmUtils {

    public static final String DUMMY_IP_ADDRESS = "0.0.0.0";
    public static final String TUNNEL_TYPE_VXLAN = "VXLAN";
    public static final String TUNNEL_TYPE_GRE = "GRE";
    public static final String TUNNEL = "TUNNEL";
    public static ItmCache itmCache = new ItmCache();

    private static final Logger LOG = LoggerFactory.getLogger(ItmUtils.class);

    public static final FutureCallback<Void> DEFAULT_CALLBACK = new FutureCallback<Void>() {
        public void onSuccess(Void result) {
            LOG.debug("Success in Datastore write operation");
        }

        public void onFailure(Throwable error) {
            LOG.error("Error in Datastore write operation", error);
        }
    };

    public static <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, DataBroker broker) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public static <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, T data, DataBroker broker, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    public static <T extends DataObject> void asyncUpdate(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, T data, DataBroker broker, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    public static <T extends DataObject> void asyncDelete(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, DataBroker broker, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), callback);
    }
    public static <T extends DataObject> void asyncBulkRemove(final DataBroker broker,final LogicalDatastoreType datastoreType,
            List<InstanceIdentifier<T>> pathList, FutureCallback<Void> callback) {
    if (!pathList.isEmpty()) {
      WriteTransaction tx = broker.newWriteOnlyTransaction();
      for (InstanceIdentifier<T> path : pathList) {
         tx.delete(datastoreType, path);
      }
      Futures.addCallback(tx.submit(), callback);
    }
    }

    public static String getInterfaceName(final BigInteger datapathid, final String portName, final Integer vlanId) {
        return String.format("%s:%s:%s", datapathid, portName, vlanId);
    }

    public static BigInteger getDpnIdFromInterfaceName(String interfaceName) {
        String[] dpnStr = interfaceName.split(":");
        BigInteger dpnId = new BigInteger(dpnStr[0]);
        return dpnId;
    }

    public static String getTrunkInterfaceName(IdManagerService idManager, String parentInterfaceName,
                                               String localHostName, String remoteHostName, String tunnelType) {
        String tunnelTypeStr;
        if(tunnelType.contains("TunnelTypeGre")) {
            tunnelTypeStr = ITMConstants.TUNNEL_TYPE_GRE;
        } else {
            tunnelTypeStr = ITMConstants.TUNNEL_TYPE_VXLAN;
        }
        String trunkInterfaceName = String.format(  "%s:%s:%s:%s", parentInterfaceName, localHostName,
                                                    remoteHostName, tunnelTypeStr);
        LOG.trace("trunk interface name is {}", trunkInterfaceName);
        trunkInterfaceName = String.format("%s:%s", TUNNEL, getUniqueId(idManager, trunkInterfaceName));
        return trunkInterfaceName;
    }

    public static void releaseIdForTrunkInterfaceName(IdManagerService idManager, String parentInterfaceName, String localHostName, String remoteHostName, String tunnelType) {
        String tunnelTypeStr;
        if(tunnelType.contains("TunnelTypeGre")) {
            tunnelTypeStr = ITMConstants.TUNNEL_TYPE_GRE;
        } else {
            tunnelTypeStr = ITMConstants.TUNNEL_TYPE_VXLAN;
        }
        String trunkInterfaceName = String.format("%s:%s:%s:%s", parentInterfaceName, localHostName, remoteHostName, tunnelTypeStr);
        LOG.trace("Releasing Id for trunkInterface - {}", trunkInterfaceName );
        releaseId(idManager, trunkInterfaceName) ;
    }

    public static InetAddress getInetAddressFromIpAddress(IpAddress ip) {
        return InetAddresses.forString(ip.getIpv4Address().getValue());
    }

    public static InstanceIdentifier<DPNTEPsInfo> getDPNTEPInstance(BigInteger dpIdKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<DPNTEPsInfo> dpnTepInfoBuilder =
                        InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class,
                                        new DPNTEPsInfoKey(dpIdKey));
        InstanceIdentifier<DPNTEPsInfo> dpnInfo = dpnTepInfoBuilder.build();
        return dpnInfo;
    }

    public static DPNTEPsInfo createDPNTepInfo(BigInteger dpId, List<TunnelEndPoints> endpoints) {

        return new DPNTEPsInfoBuilder().setKey(new DPNTEPsInfoKey(dpId)).setTunnelEndPoints(endpoints).build();
    }

    public static TunnelEndPoints createTunnelEndPoints(BigInteger dpnId, IpAddress ipAddress, String portName, int vlanId,
                    IpPrefix prefix, IpAddress gwAddress, String zoneName, Class<? extends TunnelTypeBase>  tunnel_type) {
        // when Interface Mgr provides support to take in Dpn Id
        return new TunnelEndPointsBuilder().setKey(new TunnelEndPointsKey(ipAddress, portName,tunnel_type, vlanId))
                        .setSubnetMask(prefix).setGwIpAddress(gwAddress).setTransportZone(zoneName)
                        .setInterfaceName(ItmUtils.getInterfaceName(dpnId, portName, vlanId)).setTunnelType(tunnel_type).build();
    }

    public static DpnEndpoints createDpnEndpoints(List<DPNTEPsInfo> dpnTepInfo) {
        return new DpnEndpointsBuilder().setDPNTEPsInfo(dpnTepInfo).build();
    }

    public static InstanceIdentifier<Interface> buildId(String interfaceName) {
        InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        return id;
    }

    public static Interface buildTunnelInterface(BigInteger dpn, String ifName, String desc, boolean enabled, Class<? extends TunnelTypeBase> tunType,
                    IpAddress localIp, IpAddress remoteIp, IpAddress gatewayIp,Integer vlanId, boolean internal, Boolean monitorEnabled, Integer monitorInterval) {
       InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(ifName)).setName(ifName)
       .setDescription(desc).setEnabled(enabled).setType(Tunnel.class);
       ParentRefs parentRefs = new ParentRefsBuilder().setDatapathNodeIdentifier(dpn).build();
       builder.addAugmentation(ParentRefs.class, parentRefs);
       if( vlanId > 0) {
           IfL2vlan l2vlan = new IfL2vlanBuilder().setVlanId(new VlanId(vlanId)).build();
           builder.addAugmentation(IfL2vlan.class, l2vlan);
        }
        Long monitoringInterval = (long) ITMConstants.DEFAULT_MONITOR_INTERVAL;
        Boolean monitoringEnabled = true;
        if(monitorInterval!= null)
            monitoringInterval = monitorInterval.longValue();
        if(monitorEnabled!=null  )
            monitoringEnabled = monitorEnabled;
        IfTunnel tunnel = new IfTunnelBuilder().setTunnelDestination(remoteIp).setTunnelGateway(
                        gatewayIp).setTunnelSource(localIp)
                        .setTunnelInterfaceType(tunType).setInternal(internal).setMonitorEnabled(
                                        monitoringEnabled).setMonitorInterval(monitoringInterval).build();
       builder.addAugmentation(IfTunnel.class, tunnel);
       return builder.build();
    }

    public static Interface buildHwTunnelInterface(String tunnelIfName, String desc, boolean enabled, String topo_id,
                    String node_id, Class<? extends TunnelTypeBase> tunType, IpAddress srcIp, IpAddress destIp,
                    IpAddress gWIp, Boolean monitor_enabled, Integer monitor_interval){
        InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(tunnelIfName)).setName(
                        tunnelIfName).setDescription(desc).
                setEnabled(enabled).setType(Tunnel.class);
        List<NodeIdentifier> nodeIds = new ArrayList<NodeIdentifier>();
        NodeIdentifier hWnode = new NodeIdentifierBuilder().setKey(new NodeIdentifierKey(topo_id)).setTopologyId(
                        topo_id).
                setNodeId(node_id).build();
        nodeIds.add(hWnode);
        ParentRefs parent = new ParentRefsBuilder().setNodeIdentifier(nodeIds).build();
        builder.addAugmentation(ParentRefs.class , parent);
        Long monitoringInterval = (long) ITMConstants.DEFAULT_MONITOR_INTERVAL;
        Boolean monitoringEnabled = true;
        if(monitor_interval!= null)
            monitoringInterval = monitor_interval.longValue();
        if(monitor_enabled!=null  )
            monitoringEnabled = monitor_enabled;
        IfTunnel tunnel = new IfTunnelBuilder().setTunnelDestination(destIp).setTunnelGateway(gWIp).setTunnelSource(
                        srcIp).setMonitorEnabled(monitoringEnabled).setMonitorInterval(100L).
                        setTunnelInterfaceType(tunType).setInternal(false).build();
        builder.addAugmentation(IfTunnel.class, tunnel);
        LOG.trace("iftunnel {} built from hwvtep {} ",tunnel,node_id);
        return builder.build();
    }


    public static InternalTunnel buildInternalTunnel( BigInteger srcDpnId, BigInteger dstDpnId,
                                                      Class<? extends TunnelTypeBase> tunType,
                                                      String trunkInterfaceName) {
        InternalTunnel tnl = new InternalTunnelBuilder().setKey(new InternalTunnelKey(dstDpnId, srcDpnId, tunType)).setDestinationDPN(dstDpnId)
                        .setSourceDPN(srcDpnId).setTransportType(tunType)
            .setTunnelInterfaceName(trunkInterfaceName).build();
        return tnl ;
    }

    public static ExternalTunnel buildExternalTunnel(String srcNode, String dstNode,
                                                     Class<? extends TunnelTypeBase> tunType,
                                                     String trunkInterfaceName) {
        ExternalTunnel extTnl = new ExternalTunnelBuilder().setKey(
                        new ExternalTunnelKey(dstNode, srcNode, tunType))
                                .setSourceDevice(srcNode).setDestinationDevice(dstNode)
                                .setTunnelInterfaceName(trunkInterfaceName)
                                .setTransportType(tunType).build();
        return extTnl ;
    }

    public static List<DPNTEPsInfo> getTunnelMeshInfo(DataBroker dataBroker) {
        List<DPNTEPsInfo> dpnTEPs= null ;

        // Read the EndPoint Info from the operational database
        InstanceIdentifierBuilder<DpnEndpoints> depBuilder = InstanceIdentifier.builder( DpnEndpoints.class) ;
        InstanceIdentifier<DpnEndpoints> deps = depBuilder.build();
        Optional<DpnEndpoints> dpnEps = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, deps, dataBroker);
        if (dpnEps.isPresent()) {
           DpnEndpoints tn= dpnEps.get() ;
           dpnTEPs = tn.getDPNTEPsInfo();
            LOG.debug( "Read from CONFIGURATION datastore - No. of Dpns " , dpnTEPs.size() );
        }else
            LOG.debug( "No Dpn information in CONFIGURATION datastore "  );
         return dpnTEPs ;
    }

    public static int getUniqueId(IdManagerService idManager, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(ITMConstants.ITM_IDPOOL_NAME)
            .setIdKey(idKey).build();

        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if(rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id",e);
        }
        return 0;
    }

    public static void releaseId(IdManagerService idManager, String idKey) {
        ReleaseIdInput idInput =
                        new ReleaseIdInputBuilder().setPoolName(ITMConstants.ITM_IDPOOL_NAME).setIdKey(idKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id for key {}", idKey, e);
        }
    }

    public static List<DPNTEPsInfo> getDPNTEPListFromDPNId(DataBroker dataBroker, List<BigInteger> dpnIds) {
        List<DPNTEPsInfo> meshedDpnList = getTunnelMeshInfo(dataBroker) ;
        List<DPNTEPsInfo> cfgDpnList = new ArrayList<DPNTEPsInfo>();
        if( null != meshedDpnList) {
           for(BigInteger dpnId : dpnIds) {
              for( DPNTEPsInfo teps : meshedDpnList ) {
                 if( dpnId.equals(teps.getDPNID()))
                 cfgDpnList.add( teps) ;
              }
            }
        }
        return cfgDpnList;
    }

    public static void setUpOrRemoveTerminatingServiceTable(BigInteger dpnId, IMdsalApiManager mdsalManager, boolean addFlag) {
        String logmsg = ( addFlag == true) ? "Installing" : "Removing";
        LOG.trace( logmsg + " PUNT to Controller flow in DPN {} ", dpnId );
        long dpId;
        List<ActionInfo> listActionInfo = new ArrayList<ActionInfo>();
        listActionInfo.add(new ActionInfo(ActionType.punt_to_controller,
                new String[] {}));

        try {
            List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();

            mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {
                    BigInteger.valueOf(ITMConstants.LLDP_SERVICE_ID) }));

            List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
            mkInstructions.add(new InstructionInfo(InstructionType.apply_actions,
                   listActionInfo));

            FlowEntity terminatingServiceTableFlowEntity = MDSALUtil
                    .buildFlowEntity(
                             dpnId,
                             NwConstants.INTERNAL_TUNNEL_TABLE,
                            getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE,
                                   ITMConstants.LLDP_SERVICE_ID), 5, String.format("%s:%d","ITM Flow Entry ",ITMConstants.LLDP_SERVICE_ID),
                            0, 0, ITMConstants.COOKIE_ITM
                                    .add(BigInteger.valueOf(ITMConstants.LLDP_SERVICE_ID)),
                                    mkMatches, mkInstructions);
            if(addFlag)
                mdsalManager.installFlow(terminatingServiceTableFlowEntity);
            else
                mdsalManager.removeFlow(terminatingServiceTableFlowEntity);
        } catch (Exception e) {
            LOG.error("Error while setting up Table 36 for {}", dpnId, e);
        }
    }

    private static String getFlowRef(long termSvcTable, int svcId) {
        return new StringBuffer().append(termSvcTable).append(svcId).toString();
    }
    public static InstanceIdentifier<VtepConfigSchema> getVtepConfigSchemaIdentifier(String schemaName) {
        return InstanceIdentifier.builder(VtepConfigSchemas.class)
                        .child(VtepConfigSchema.class, new VtepConfigSchemaKey(schemaName)).build();
    }

    public static InstanceIdentifier<VtepConfigSchema> getVtepConfigSchemaIdentifier() {
        return InstanceIdentifier.builder(VtepConfigSchemas.class).child(VtepConfigSchema.class).build();
    }
    public static InstanceIdentifier<VtepConfigSchemas> getVtepConfigSchemasIdentifier() {
        return InstanceIdentifier.builder(VtepConfigSchemas.class).build();
    }
    public static InstanceIdentifier<VtepIpPool> getVtepIpPoolIdentifier(String subnetCidr) {
        return InstanceIdentifier.builder(VtepIpPools.class).child(VtepIpPool.class, new VtepIpPoolKey(subnetCidr))
                .build();
    }
    public static VtepConfigSchema validateForAddVtepConfigSchema(VtepConfigSchema schema,
            List<VtepConfigSchema> existingSchemas) {
        VtepConfigSchema validSchema = validateVtepConfigSchema(schema);
        for (VtepConfigSchema existingSchema : emptyIfNull(existingSchemas)) {
            if (!StringUtils.equalsIgnoreCase(schema.getSchemaName(), existingSchema.getSchemaName())
                    && schema.getSubnet().equals(existingSchema.getSubnet())) {
                String subnetCidr = getSubnetCidrAsString(schema.getSubnet());
                Preconditions.checkArgument(false, new StringBuilder("VTEP schema with subnet [").append(subnetCidr)
                        .append("] already exists. Multiple VTEP schemas with same subnet is not allowed.").toString());
            }
        }
        if (isNotEmpty(getDpnIdList(validSchema.getDpnIds()))) {
            String tzone = validSchema.getTransportZoneName();
            List<BigInteger> lstDpns = getConflictingDpnsAlreadyConfiguredWithTz(validSchema.getSchemaName(), tzone,
            		getDpnIdList(validSchema.getDpnIds()), existingSchemas);
            if (!lstDpns.isEmpty()) {
                Preconditions.checkArgument(false,
                        new StringBuilder("DPN's ").append(lstDpns).append(" already configured for transport zone ")
                                .append(tzone).append(". Only one end point per transport Zone per Dpn is allowed.")
                                .toString());
            }
            if (schema.getTunnelType().equals(TunnelTypeGre.class)){
                validateForSingleGreTep(validSchema.getSchemaName(), getDpnIdList(validSchema.getDpnIds()), existingSchemas);
            }
        }
        return validSchema;
    }
    private static void validateForSingleGreTep(String schemaName, List<BigInteger> lstDpnsForAdd,
            List<VtepConfigSchema> existingSchemas) {
        for (VtepConfigSchema existingSchema : emptyIfNull(existingSchemas)) {
            if ((TunnelTypeGre.class).equals(existingSchema.getTunnelType())
                    && !StringUtils.equalsIgnoreCase(schemaName, existingSchema.getSchemaName())) {
                List<BigInteger> lstConflictingDpns = new ArrayList<>(getDpnIdList(existingSchema.getDpnIds()));
                lstConflictingDpns.retainAll(emptyIfNull(lstDpnsForAdd));
                if (!lstConflictingDpns.isEmpty()) {
                    String errMsg = new StringBuilder("DPN's ").append(lstConflictingDpns)
                            .append(" already configured with GRE TEP. Mutiple GRE TEP's on a single DPN are not allowed.")
                            .toString();
                    Preconditions.checkArgument(false, errMsg);
                }
            }
        }
    }
    public static VtepConfigSchema validateVtepConfigSchema(VtepConfigSchema schema) {
        Preconditions.checkNotNull(schema);
        Preconditions.checkArgument(StringUtils.isNotBlank(schema.getSchemaName()));
        Preconditions.checkArgument(StringUtils.isNotBlank(schema.getPortName()));
        Preconditions.checkArgument((schema.getVlanId() >= 0 && schema.getVlanId() < 4095),
                "Invalid VLAN ID, range (0-4094)");
        Preconditions.checkArgument(StringUtils.isNotBlank(schema.getTransportZoneName()));
        Preconditions.checkNotNull(schema.getSubnet());
        String subnetCidr = getSubnetCidrAsString(schema.getSubnet());
        SubnetUtils subnetUtils = new SubnetUtils(subnetCidr);
        IpAddress gatewayIp = schema.getGatewayIp();
        if (gatewayIp != null) {
            String strGatewayIp = String.valueOf(gatewayIp.getValue());
            if (!strGatewayIp.equals(ITMConstants.DUMMY_IP_ADDRESS) && !subnetUtils.getInfo().isInRange(strGatewayIp)) {
                Preconditions.checkArgument(false, new StringBuilder("Gateway IP address ").append(strGatewayIp)
                        .append(" is not in subnet range ").append(subnetCidr).toString());
            }
        }
        ItmUtils.getExcludeIpAddresses(schema.getExcludeIpFilter(), subnetUtils.getInfo());
        return new VtepConfigSchemaBuilder(schema).setTunnelType(schema.getTunnelType()).build();
    }
    public static String validateTunnelType(String tunnelType) {
        if (tunnelType == null) {
            tunnelType = ITMConstants.TUNNEL_TYPE_VXLAN;
        } else {
            tunnelType = StringUtils.upperCase(tunnelType);
            String error = new StringBuilder("Invalid tunnel type. Valid values: ")
                    .append(ITMConstants.TUNNEL_TYPE_VXLAN).append(" | ").append(ITMConstants.TUNNEL_TYPE_GRE)
                    .toString();
            Preconditions.checkArgument(ITMConstants.TUNNEL_TYPE_VXLAN.equals(tunnelType)
                    || ITMConstants.TUNNEL_TYPE_GRE.equals(tunnelType), error);
        }
        return tunnelType;
    }
    private static List<BigInteger> getConflictingDpnsAlreadyConfiguredWithTz(String schemaName, String tzone,
            List<BigInteger> lstDpns, List<VtepConfigSchema> existingSchemas) {
        List<BigInteger> lstConflictingDpns = new ArrayList<>();
        for (VtepConfigSchema schema : emptyIfNull(existingSchemas)) {
            if (!StringUtils.equalsIgnoreCase(schemaName, schema.getSchemaName())
                    && StringUtils.equals(schema.getTransportZoneName(), tzone)) {
                lstConflictingDpns = new ArrayList<>(getDpnIdList(schema.getDpnIds()));
                lstConflictingDpns.retainAll(lstDpns);
                if (!lstConflictingDpns.isEmpty()) {
                    break;
                }
            }
        }
        return lstConflictingDpns;
    }
    public static VtepConfigSchema constructVtepConfigSchema(String schemaName, String portName, Integer vlanId,
            String subnetMask, String gatewayIp, String transportZone,String tunnelType, List<BigInteger> dpnIds,
            String excludeIpFilter) {
        IpAddress gatewayIpObj = StringUtils.isBlank(gatewayIp) ? null : new IpAddress(gatewayIp.toCharArray());
        IpPrefix subnet = StringUtils.isBlank(subnetMask) ? null : new IpPrefix(subnetMask.toCharArray());
        Class<? extends TunnelTypeBase> tunType ;
        if( tunnelType.equals(ITMConstants.TUNNEL_TYPE_VXLAN))
            tunType = TunnelTypeVxlan.class ;
        else
            tunType = TunnelTypeGre.class ;
        VtepConfigSchemaBuilder schemaBuilder = new VtepConfigSchemaBuilder().setSchemaName(schemaName)
                .setPortName(portName).setVlanId(vlanId).setSubnet(subnet).setGatewayIp(gatewayIpObj)
                .setTransportZoneName(transportZone).setTunnelType(tunType).setDpnIds(getDpnIdsListFromBigInt(dpnIds))
                .setExcludeIpFilter(excludeIpFilter);
        return schemaBuilder.build();
    }
    public static List<IpAddress> getExcludeIpAddresses(String excludeIpFilter, SubnetInfo subnetInfo) {
        final List<IpAddress> lstIpAddress = new ArrayList<>();
        if (StringUtils.isBlank(excludeIpFilter)) {
            return lstIpAddress;
        }
        final String[] arrIps = StringUtils.split(excludeIpFilter, ',');
        for (String ip : arrIps) {
            if (StringUtils.countMatches(ip, "-") == 1) {
                final String[] arrIpRange = StringUtils.split(ip, '-');
                String strStartIp = StringUtils.trim(arrIpRange[0]);
                String strEndIp = StringUtils.trim(arrIpRange[1]);
                Preconditions.checkArgument(InetAddresses.isInetAddress(strStartIp),
                        new StringBuilder("Invalid exclude IP filter: invalid IP address value ").append(strStartIp)
                                .toString());
                Preconditions.checkArgument(InetAddresses.isInetAddress(strEndIp),
                        new StringBuilder("Invalid exclude IP filter: invalid IP address value ").append(strEndIp)
                                .toString());
                Preconditions.checkArgument(subnetInfo.isInRange(strStartIp),
                        new StringBuilder("Invalid exclude IP filter: IP address [").append(strStartIp)
                                .append("] not in subnet range ").append(subnetInfo.getCidrSignature()).toString());
                Preconditions.checkArgument(subnetInfo.isInRange(strEndIp),
                        new StringBuilder("Invalid exclude IP filter: IP address [").append(strEndIp)
                                .append("] not in subnet range ").append(subnetInfo.getCidrSignature()).toString());
                int startIp = subnetInfo.asInteger(strStartIp);
                int endIp = subnetInfo.asInteger(strEndIp);

                Preconditions.checkArgument(startIp < endIp,
                        new StringBuilder("Invalid exclude IP filter: Invalid range [").append(ip).append("] ")
                                .toString());
                for (int i = startIp; i <= endIp; i++) {
                    String ipAddress = ipFormat(toIpArray(i));
                    validateAndAddIpAddressToList(subnetInfo, lstIpAddress, ipAddress);
                }
            } else {
                validateAndAddIpAddressToList(subnetInfo, lstIpAddress, ip);
            }
        }
        return lstIpAddress;
    }
    private static void validateAndAddIpAddressToList(SubnetInfo subnetInfo, final List<IpAddress> lstIpAddress,
            String ipAddress) {
        String ip = StringUtils.trim(ipAddress);
        Preconditions.checkArgument(InetAddresses.isInetAddress(ip),
                new StringBuilder("Invalid exclude IP filter: invalid IP address value ").append(ip).toString());
        Preconditions.checkArgument(subnetInfo.isInRange(ip),
                new StringBuilder("Invalid exclude IP filter: IP address [").append(ip).append("] not in subnet range ")
                        .append(subnetInfo.getCidrSignature()).toString());
        lstIpAddress.add(new IpAddress(ip.toCharArray()));
    }
    private static int[] toIpArray(int val) {
        int[] ret = new int[4];
        for (int j = 3; j >= 0; --j) {
            ret[j] |= ((val >>> 8 * (3 - j)) & (0xff));
        }
        return ret;
    }
    private static String ipFormat(int[] octets) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < octets.length; ++i) {
            str.append(octets[i]);
            if (i != octets.length - 1) {
                str.append(".");
            }
        }
        return str.toString();
    }
    public static VtepConfigSchema validateForUpdateVtepSchema(String schemaName, List<BigInteger> lstDpnsForAdd,
            List<BigInteger> lstDpnsForDelete, IITMProvider itmProvider) {
        Preconditions.checkArgument(StringUtils.isNotBlank(schemaName));
        if ((lstDpnsForAdd == null || lstDpnsForAdd.isEmpty())
                && (lstDpnsForDelete == null || lstDpnsForDelete.isEmpty())) {
            Preconditions.checkArgument(false,
                    new StringBuilder("DPN ID list for add | delete is null or empty in schema ").append(schemaName)
                            .toString());
        }
        VtepConfigSchema schema = itmProvider.getVtepConfigSchema(schemaName);
        if (schema == null) {
            Preconditions.checkArgument(false, new StringBuilder("Specified VTEP Schema [").append(schemaName)
                    .append("] doesn't exists!").toString());
        }
        List<BigInteger> existingDpnIds = getDpnIdList(schema.getDpnIds());
        if (isNotEmpty(lstDpnsForAdd)) {
            if (isNotEmpty(existingDpnIds)) {
                List<BigInteger> lstAlreadyExistingDpns = new ArrayList<>(existingDpnIds);
                lstAlreadyExistingDpns.retainAll(lstDpnsForAdd);
                Preconditions.checkArgument(lstAlreadyExistingDpns.isEmpty(),
                        new StringBuilder("DPN ID's ").append(lstAlreadyExistingDpns)
                                .append(" already exists in VTEP schema [").append(schemaName).append("]").toString());
            }
            if (schema.getTunnelType().equals(TunnelTypeGre.class)) {
                validateForSingleGreTep(schema.getSchemaName(), lstDpnsForAdd, itmProvider.getAllVtepConfigSchemas());
            }
        }
        if (isNotEmpty(lstDpnsForDelete)) {
            if (existingDpnIds == null || existingDpnIds.isEmpty()) {
                StringBuilder builder = new StringBuilder("DPN ID's ").append(lstDpnsForDelete)
                        .append(" specified for delete from VTEP schema [").append(schemaName)
                        .append("] are not configured in the schema.");
                Preconditions.checkArgument(false, builder.toString());
            } else if (!existingDpnIds.containsAll(lstDpnsForDelete)) {
                List<BigInteger> lstConflictingDpns = new ArrayList<>(lstDpnsForDelete);
                lstConflictingDpns.removeAll(existingDpnIds);
                StringBuilder builder = new StringBuilder("DPN ID's ").append(lstConflictingDpns)
                        .append(" specified for delete from VTEP schema [").append(schemaName)
                        .append("] are not configured in the schema.");
                Preconditions.checkArgument(false, builder.toString());
            }
        }
        return schema;
    }
    public static String getSubnetCidrAsString(IpPrefix subnet) {
        return (subnet == null) ? StringUtils.EMPTY : String.valueOf(subnet.getValue());
    }
    public static <T> List<T> emptyIfNull(List<T> list) {
        return (list == null) ? Collections.<T> emptyList() : list;
    }
    public static <T> boolean isEmpty(Collection<T> collection) {
        return (collection == null || collection.isEmpty()) ? true : false;
    }
    public static <T> boolean isNotEmpty(Collection<T> collection) {
        return !isEmpty(collection);
    }
    public static HwVtep createHwVtepObject(String topo_id, String node_id, IpAddress ipAddress, IpPrefix ipPrefix, IpAddress gatewayIP, int vlanID, Class<? extends TunnelTypeBase> tunnel_type, TransportZone transportZone) {
        HwVtep hwVtep = new HwVtep();
        hwVtep.setGatewayIP(gatewayIP);
        hwVtep.setHwIp(ipAddress);
        hwVtep.setIpPrefix(ipPrefix);
        hwVtep.setNode_id(node_id);
        hwVtep.setTopo_id(topo_id);
        hwVtep.setTransportZone(transportZone.getZoneName());
        hwVtep.setTunnel_type(tunnel_type);
        hwVtep.setVlanID(vlanID);
        return hwVtep;
    }

    public static String getHwParentIf(String topo_id, String srcNodeid) {
        return String.format("%s:%s", topo_id, srcNodeid);
    }

    public static <T extends DataObject> void syncWrite(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, T data, DataBroker broker) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("ITMUtils:SyncWrite , Error writing to datastore (path, data) : ({}, {})", path, data);
            throw new RuntimeException(e.getMessage());
        }
    }

    public static List<BigInteger> getDpnIdList( List<DpnIds> dpnIds ) {
        List<BigInteger> dpnList = new ArrayList<BigInteger>() ;
        for( DpnIds dpn : dpnIds) {
           dpnList.add(dpn.getDPN()) ;
        }
        return dpnList ;
    }

    public static List<DpnIds> getDpnIdsListFromBigInt( List<BigInteger> dpnIds) {
        List<DpnIds> dpnIdList = new ArrayList<DpnIds>() ;
        DpnIdsBuilder builder = new DpnIdsBuilder() ;
        for( BigInteger dpnId : dpnIds) {
              dpnIdList.add(builder.setKey(new DpnIdsKey(dpnId)).setDPN(dpnId).build() );
        }
        return dpnIdList;
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.class,
                                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey(
                                                interfaceName));
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> id = idBuilder.build();
        return id;
}
    public static Boolean readMonitoringStateFromDS(DataBroker dataBroker) {
        InstanceIdentifier<TunnelMonitorEnabled> iid = InstanceIdentifier.create(TunnelMonitorEnabled.class);
        Optional<TunnelMonitorEnabled> tunnelMonitorEnabledOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION,
                        iid, dataBroker);
        if(tunnelMonitorEnabledOptional.isPresent())
            return tunnelMonitorEnabledOptional.get().isEnabled();
        return null;
    }

    public static Integer readMonitorIntervalfromDS(DataBroker dataBroker) {
        InstanceIdentifier<TunnelMonitorInterval> iid = InstanceIdentifier.create(TunnelMonitorInterval.class);
        Optional<TunnelMonitorInterval> tunnelMonitorIOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, iid, dataBroker);
        if(tunnelMonitorIOptional.isPresent())
            return tunnelMonitorIOptional.get().getInterval();
        return null;
    }
    public static List<String> getTunnelsofTzone(List<HwVtep> hwVteps, String tzone, DataBroker dataBroker, Boolean hwVtepsExist) {
        List<String> tunnels = new ArrayList<String>();
        InstanceIdentifier<TransportZone> path = InstanceIdentifier.builder(TransportZones.class).
                        child(TransportZone.class, new TransportZoneKey(tzone)).build();
        Optional<TransportZone> tZoneOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, dataBroker);
        Class<? extends TunnelTypeBase> tunType = tZoneOptional.get().getTunnelType();
        if (tZoneOptional.isPresent()) {
            if (tZoneOptional.get().getSubnets() != null && !tZoneOptional.get().getSubnets().isEmpty()) {
                for (Subnets sub : tZoneOptional.get().getSubnets()) {
                    if (sub.getVteps() != null && !sub.getVteps().isEmpty()) {
                        for (Vteps vtepLocal : sub.getVteps()) {
                            for (Vteps vtepRemote : sub.getVteps()) {
                                if (!vtepLocal.equals(vtepRemote)) {
                                    InternalTunnelKey key = new InternalTunnelKey(vtepRemote.getDpnId(), vtepLocal.getDpnId(), tunType);
                                    InstanceIdentifier<InternalTunnel> intIID =
                                                    InstanceIdentifier.builder(TunnelList.class).
                                                                    child(InternalTunnel.class, key).build();
                                    Optional<InternalTunnel> TunnelsOptional =
                                                    ItmUtils.read(LogicalDatastoreType.CONFIGURATION, intIID, dataBroker);
                                    if (TunnelsOptional.isPresent()) {
                                        LOG.trace("Internal Tunnel added {}",TunnelsOptional.get().getTunnelInterfaceName());
                                        tunnels.add(TunnelsOptional.get().getTunnelInterfaceName());
                                    }
                                }
                            }
                            if(hwVteps!= null && !hwVteps.isEmpty()) {
                                for (HwVtep hwVtep : hwVteps) {
                                    tunnels.add(getExtTunnel(hwVtep.getNode_id(), vtepLocal.getDpnId().toString(),
                                                    tunType, dataBroker));
                                    tunnels.add(getExtTunnel(vtepLocal.getDpnId().toString(), hwVtep.getNode_id(),
                                                    tunType, dataBroker));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (hwVtepsExist) {
            for (HwVtep hwVtep : hwVteps) {
                for (HwVtep hwVtepOther : hwVteps) {
                    if (!hwVtep.getHwIp().equals(hwVtepOther.getHwIp())) {
                        tunnels.add(getExtTunnel(hwVtep.getNode_id(), hwVtepOther.getNode_id(), tunType, dataBroker));
                        tunnels.add(getExtTunnel(hwVtepOther.getNode_id(), hwVtep.getNode_id(), tunType, dataBroker));
                    }
                }
            }
        }
        return tunnels;
    }
    public static List<String> getInternalTunnelsofTzone(String tzone, DataBroker dataBroker) {
        List<String> tunnels = new ArrayList<String>();
        LOG.trace("Getting internal tunnels of {}",tzone);
        InstanceIdentifier<TransportZone> path = InstanceIdentifier.builder(TransportZones.class).
                        child(TransportZone.class, new TransportZoneKey(tzone)).build();
        Optional<TransportZone> tZoneOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, dataBroker);
        if (tZoneOptional.isPresent()) {
            if (tZoneOptional.get().getSubnets() != null && !tZoneOptional.get().getSubnets().isEmpty()) {
                for (Subnets sub : tZoneOptional.get().getSubnets()) {
                    if (sub.getVteps() != null && !sub.getVteps().isEmpty()) {
                        for (Vteps vtepLocal : sub.getVteps()) {
                            for (Vteps vtepRemote : sub.getVteps()) {
                                if (!vtepLocal.equals(vtepRemote)) {
                                    InternalTunnelKey key = new InternalTunnelKey(vtepRemote.getDpnId(), vtepLocal.getDpnId(), tZoneOptional.get().getTunnelType());
                                    InstanceIdentifier<InternalTunnel> intIID =
                                                    InstanceIdentifier.builder(TunnelList.class).
                                                                    child(InternalTunnel.class, key).build();
                                    Optional<InternalTunnel> TunnelsOptional =
                                                    ItmUtils.read(LogicalDatastoreType.CONFIGURATION, intIID, dataBroker);
                                    if (TunnelsOptional.isPresent()) {
                                        LOG.trace("Internal Tunnel added {}",
                                                        TunnelsOptional.get().getTunnelInterfaceName());
                                        tunnels.add(TunnelsOptional.get().getTunnelInterfaceName());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return tunnels;
    }
    private static String getExtTunnel(String node_id, String dpId,Class<? extends TunnelTypeBase> tunType, DataBroker dataBroker) {
        LOG.trace("getting ext tunnel for {} and dpId {}",node_id,dpId);
        ExternalTunnelKey key = getExternalTunnelKey(dpId,node_id, tunType);
        InstanceIdentifier<ExternalTunnel> intIID = InstanceIdentifier.builder(ExternalTunnelList.class).
                        child(ExternalTunnel.class, key).build();
        Optional<ExternalTunnel> TunnelsOptional =
                        ItmUtils.read(LogicalDatastoreType.CONFIGURATION, intIID, dataBroker);
        if (TunnelsOptional.isPresent()) {
            LOG.trace("ext tunnel returned {} ",TunnelsOptional.get().getTunnelInterfaceName());
            return TunnelsOptional.get().getTunnelInterfaceName();
        }
        return null;
    }
    public static ExternalTunnelKey getExternalTunnelKey(String dst , String src, Class<? extends TunnelTypeBase> tunType) {
        if (src.indexOf("physicalswitch") > 0) {
            src = src.substring(0, src.indexOf("physicalswitch") - 1);
        }
        if (dst.indexOf("physicalswitch") > 0) {
            dst = dst.substring(0, dst.indexOf("physicalswitch") - 1);
        }
        return new ExternalTunnelKey(dst, src, tunType);
    }
    public static List<TunnelEndPoints> getTEPsForDpn( BigInteger srcDpn, List<DPNTEPsInfo> dpnList) {
        for (DPNTEPsInfo dpn : dpnList) {
            if( dpn.getDPNID().equals(srcDpn)) {
                return dpn.getTunnelEndPoints() ;
            }
        }
        return null ;
    }
    public static TunnelList getAllInternalTunnels(DataBroker broker) {
        InstanceIdentifier<TunnelList> tunnelListInstanceIdentifier = InstanceIdentifier.builder(TunnelList.class).build();
        Optional<TunnelList> tunnelList = read(LogicalDatastoreType.CONFIGURATION, tunnelListInstanceIdentifier, broker);
        if (tunnelList.isPresent()) {
            return tunnelList.get();
        }
        return null;
    }
    public static InternalTunnel getInternalTunnel(String interfaceName, DataBroker broker) {
        InternalTunnel internalTunnel = null;
        TunnelList tunnelList = getAllInternalTunnels(broker);
        if (tunnelList != null && tunnelList.getInternalTunnel() != null) {
            List<InternalTunnel> internalTunnels = tunnelList.getInternalTunnel();
            for (InternalTunnel tunnel : internalTunnels) {
                if (tunnel.getTunnelInterfaceName().equalsIgnoreCase(interfaceName)) {
                    internalTunnel = tunnel;
                    break;
                }
            }
        }
        return internalTunnel;
    }
    public static ExternalTunnel getExternalTunnel(String interfaceName, DataBroker broker) {
        ExternalTunnel externalTunnel = null;
        List<ExternalTunnel> externalTunnels = getAllExternalTunnels(broker);
        for (ExternalTunnel tunnel : externalTunnels) {
            if (StringUtils.equalsIgnoreCase(interfaceName, tunnel.getTunnelInterfaceName())) {
                externalTunnel = tunnel;
                break;
            }
        }
        return externalTunnel;
    }
    public static List<ExternalTunnel> getAllExternalTunnels(DataBroker broker) {
        List<ExternalTunnel> result = null;
        InstanceIdentifier<ExternalTunnelList> id = InstanceIdentifier.builder(ExternalTunnelList.class).build();
        Optional<ExternalTunnelList> tunnelList = read(LogicalDatastoreType.CONFIGURATION, id, broker);
        if (tunnelList.isPresent()) {
            result = tunnelList.get().getExternalTunnel();
        }
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }
    public static String convertTunnelTypetoString(Class<? extends TunnelTypeBase> tunType ) {
        String tunnelType = ITMConstants.TUNNEL_TYPE_VXLAN;
        if( tunType.equals(TunnelTypeVxlan.class))
            tunnelType = ITMConstants.TUNNEL_TYPE_VXLAN ;
        else if( tunType.equals(TunnelTypeGre.class) )
            tunnelType = ITMConstants.TUNNEL_TYPE_GRE ;
        else if (tunnelType.equals(TunnelTypeMplsOverGre.class))
            tunnelType = ITMConstants.TUNNEL_TYPE_MPLS_OVER_GRE;
        return tunnelType ;
    }
    public static boolean isItmIfType(Class<? extends InterfaceType> ifType) {
        if( (ifType != null) && (ifType.isAssignableFrom(Tunnel.class)) ) {
            return true;
        }
        return false;
    }
    public static StateTunnelListKey getTunnelStateKey( org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface iface) {
        StateTunnelListKey key = null;
        if(isItmIfType(iface.getType())) {
            key = new StateTunnelListKey(iface.getName());
        }
        return key;
    }
    public static void updateTunnelsCache(DataBroker broker) {
        List<InternalTunnel> internalTunnels = getAllInternalTunnels(broker, LogicalDatastoreType.CONFIGURATION);
        for (InternalTunnel tunnel : internalTunnels) {
            itmCache.addInternalTunnel(tunnel);
        }
        List<ExternalTunnel> externalTunnels = getAllExternalTunnels(broker, LogicalDatastoreType.CONFIGURATION);
        for (ExternalTunnel tunnel : externalTunnels) {
            itmCache.addExternalTunnel(tunnel);
        }
    }
    public static List<ExternalTunnel> getAllExternalTunnels(DataBroker dataBroker, LogicalDatastoreType datastoreType) {
        List<ExternalTunnel> result = null;
        InstanceIdentifier<ExternalTunnelList> iid = InstanceIdentifier.builder(ExternalTunnelList.class).build();
        Optional<ExternalTunnelList> tunnelList = read(LogicalDatastoreType.CONFIGURATION, iid, dataBroker);
        if (tunnelList.isPresent()) {
            result = tunnelList.get().getExternalTunnel();
        }
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }
    public static List<InternalTunnel> getAllInternalTunnels(DataBroker dataBroker, LogicalDatastoreType datastoreType) {
        List<InternalTunnel> result = null;
        InstanceIdentifier<TunnelList> iid = InstanceIdentifier.builder(TunnelList.class).build();
        Optional<TunnelList> tunnelList = read(LogicalDatastoreType.CONFIGURATION, iid, dataBroker);
        if (tunnelList.isPresent()) {
            result = tunnelList.get().getInternalTunnel();
        }
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }
    public static Interface getInterface(
                    String name, DataBroker broker) {
        Interface result = itmCache.getInterface(name);
        if (result == null) {
            InstanceIdentifier<Interface> iid =
                            InstanceIdentifier.builder(Interfaces.class)
                            .child(Interface.class, new InterfaceKey(name)).build();
            Optional<Interface> optInterface = read(LogicalDatastoreType.CONFIGURATION, iid, broker);
            if (optInterface.isPresent()) {
                result = optInterface.get();
                itmCache.addInterface(result);
            }
        }
        return result;
    }
}

/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.natservice.internal;

import com.google.common.base.Optional;
import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.map.IpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.map.IpMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.map.ip.mapping.IpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.IpPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.IpPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NaptSwitchHA {
    private static final Logger LOG = LoggerFactory.getLogger(NaptSwitchHA.class);
    private final DataBroker dataBroker;
    private IMdsalApiManager mdsalManager;
    private ItmRpcService itmManager;
    private OdlInterfaceRpcService interfaceManager;
    private IdManagerService idManager;
    private NAPTSwitchSelector naptSwitchSelector;
    private ExternalRoutersListener externalRouterListener;
    private IBgpManager bgpManager;
    private VpnRpcService vpnService;
    private FibRpcService fibService;

    public NaptSwitchHA(DataBroker broker,NAPTSwitchSelector selector){
        dataBroker = broker;
        naptSwitchSelector = selector;
    }

    public void setItmManager(ItmRpcService itmManager) {
        this.itmManager = itmManager;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setInterfaceManager(OdlInterfaceRpcService interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    void setExternalRoutersListener(ExternalRoutersListener externalRoutersListener) {
        this.externalRouterListener = externalRoutersListener;
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

    /* This method checks the switch that gone down is a NaptSwitch for a router.
       If it is a NaptSwitch
          1) selects new NAPT switch
          2) installs nat flows in new NAPT switch
          table 21(FIB)->26(PSNAT)->group(resubmit/napttunnel)->36(Terminating)->46(outbound)->47(resubmit)->21
          3) modify the group and miss entry flow in other vSwitches pointing to newNaptSwitch
          4) Remove nat flows in oldNaptSwitch
     */
    public void handleNaptSwitchDown(BigInteger dpnId){

        LOG.debug("handleNaptSwitchDown method is called with dpnId {}",dpnId);
        BigInteger naptSwitch;
        try {
            NaptSwitches naptSwitches = NatUtil.getNaptSwitch(dataBroker);
            if (naptSwitches == null || naptSwitches.getRouterToNaptSwitch() == null || naptSwitches.getRouterToNaptSwitch().isEmpty()) {
                LOG.debug("NaptSwitchDown: NaptSwitch is not allocated for none of the routers");
                return;
            }
            for (RouterToNaptSwitch routerToNaptSwitch : naptSwitches.getRouterToNaptSwitch()) {
                String routerName = routerToNaptSwitch.getRouterName();
                naptSwitch = routerToNaptSwitch.getPrimarySwitchId();
                boolean naptStatus = isNaptSwitchDown(routerName,dpnId,naptSwitch);
                if (!naptStatus) {
                    LOG.debug("NaptSwitchDown: Switch with DpnId {} is not naptSwitch for router {}",
                            dpnId, routerName);
                } else {
                    removeSnatFlowsInOldNaptSwitch(routerName,naptSwitch);
                    return;
                }
            }
        } catch (Exception ex) {
            LOG.error("Exception in handleNaptSwitchDown method {}",ex);
        }
    }

    private void removeSnatFlowsInOldNaptSwitch(String routerName, BigInteger naptSwitch) {
        //remove SNAT flows in old NAPT SWITCH
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("Invalid routerId returned for routerName {}",routerName);
            return;
        }
        BigInteger cookieSnatFlow = NatUtil.getCookieSnatFlow(routerId);

        //Build and remove flows in outbound NAPT table
        try {
            FlowEntity outboundNaptFlowEntity = NatUtil.buildFlowEntity(naptSwitch, NatConstants.OUTBOUND_NAPT_TABLE, cookieSnatFlow);
            mdsalManager.removeFlow(outboundNaptFlowEntity);
            LOG.info("Removed all flows for router {} in the table {} for oldNaptswitch {}"
                    ,routerName, NatConstants.OUTBOUND_NAPT_TABLE, naptSwitch);
        } catch (Exception ex) {
            LOG.info("Failed to remove all flows for router {} in the table {} for oldNaptswitch {}"
                    ,routerName, NatConstants.OUTBOUND_NAPT_TABLE, naptSwitch);
        }

        //Build and remove flows in inbound NAPT table
        try {
            FlowEntity inboundNaptFlowEntity = NatUtil.buildFlowEntity(naptSwitch, NatConstants.INBOUND_NAPT_TABLE,
                    cookieSnatFlow);
            mdsalManager.removeFlow(inboundNaptFlowEntity);
            LOG.info("Removed all flows for router {} in the table {} for oldNaptswitch {}"
                    ,routerName, NatConstants.INBOUND_NAPT_TABLE, naptSwitch);
        } catch (Exception ex) {
            LOG.info("Failed to remove all flows for router {} in the table {} for oldNaptswitch {}"
                    ,routerName, NatConstants.INBOUND_NAPT_TABLE, naptSwitch);
        }

        //Remove the Terminating Service table entry which forwards the packet to Outbound NAPT Table
        String tsFlowRef = externalRouterListener.getFlowRefTs(naptSwitch, NatConstants.TERMINATING_SERVICE_TABLE, routerId);
        FlowEntity tsNatFlowEntity = NatUtil.buildFlowEntity(naptSwitch, NatConstants.TERMINATING_SERVICE_TABLE, tsFlowRef);

        LOG.info("Remove the flow in table {} for the active switch with the DPN ID {} and router ID {}"
                ,NatConstants.TERMINATING_SERVICE_TABLE, naptSwitch, routerId);
        mdsalManager.removeFlow(tsNatFlowEntity);

        //Remove the Outbound flow entry which forwards the packet to Outbound NAPT Table
        String outboundNatFlowRef = externalRouterListener.getFlowRefOutbound(naptSwitch, NatConstants.OUTBOUND_NAPT_TABLE, routerId);
        FlowEntity outboundNatFlowEntity = NatUtil.buildFlowEntity(naptSwitch,
                NatConstants.OUTBOUND_NAPT_TABLE, outboundNatFlowRef);
        LOG.info("Remove the flow in the for the active switch with the DPN ID {} and router ID {}"
                ,NatConstants.OUTBOUND_NAPT_TABLE, naptSwitch, routerId);
        mdsalManager.removeFlow(outboundNatFlowEntity);

        //Remove the NAPT_PFIB_TABLE(47) flow entry forwards the packet to Fib Table
        String naptPFibflowRef = externalRouterListener.getFlowRefTs(naptSwitch, NatConstants.NAPT_PFIB_TABLE, routerId);
        FlowEntity naptPFibFlowEntity = NatUtil.buildFlowEntity(naptSwitch, NatConstants.NAPT_PFIB_TABLE,naptPFibflowRef);
        LOG.info("Remove the flow in the for the active switch with the DPN ID {} and router ID {}",
                NatConstants.NAPT_PFIB_TABLE, naptSwitch, routerId);
        mdsalManager.removeFlow(naptPFibFlowEntity);

        //Remove Fib entries and 36-> 44
        Uuid networkId = NatUtil.getNetworkIdFromRouterId(dataBroker, routerId);
        if (networkId == null) {
            LOG.debug("network is not associated to router {}", routerId);
        }
        Optional<Routers> routerData = NatUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                NatUtil.buildRouterIdentifier(routerName));
        if(routerData.isPresent()){
            List<String> externalIps = routerData.get().getExternalIps();
            if (externalIps != null) {
                externalRouterListener.advToBgpAndRemoveFibAndTsFlows(naptSwitch, routerId, networkId, externalIps);
                LOG.debug("Successfully removed fib entries in naptswitch {} for router {} with external IP {}", naptSwitch,
                        routerId, externalIps);
            } else {
                LOG.debug("ExternalIps not found for router {} with networkId {}",routerName,networkId);
            }
        }
    }

    public boolean isNaptSwitchDown(String routerName, BigInteger dpnId , BigInteger naptSwitch) {
        if (!naptSwitch.equals(dpnId)) {
            LOG.debug("DpnId {} is not a naptSwitch {} for Router {}",dpnId, naptSwitch, routerName);
            return false;
        }
        LOG.debug("NaptSwitch {} is down for Router {}", naptSwitch, routerName);
        //elect a new NaptSwitch
        naptSwitch = naptSwitchSelector.selectNewNAPTSwitch(routerName);
        if (naptSwitch.equals("0")) {
            LOG.info("No napt switch is elected since all the switches for router {} are down",routerName);
            return true;
        }
        //checking elected switch health status
        if (!getSwitchStatus(naptSwitch)) {
            LOG.error("Newly elected Napt switch {} for router {} is down", naptSwitch, routerName);
            return true;
        }
        LOG.debug("New NaptSwitch {} is up for Router {} and can proceed for flow installation",naptSwitch, routerName);
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("Invalid routerId returned for routerName {}", routerName);
            return true;
        }
        //update napt model for new napt switch
        boolean naptUpdated = updateNaptSwitch(routerName, naptSwitch);
        if (naptUpdated) {
            //update group of naptswitch point to table36/ordinary switch point to naptswitchtunnelport
            updateNaptSwitchBucketStatus(routerName, naptSwitch);
        } else {
            LOG.error("Failed to update naptSwitch model for newNaptSwitch {} for router {}",naptSwitch, routerName);
        }
        //36 -> 46 ..Install flow going to 46 from table36
        externalRouterListener.installTerminatingServiceTblEntry(naptSwitch, routerName);

        //Install default flows punting to controller in table 46(OutBoundNapt table)
        externalRouterListener.installOutboundMissEntry(routerName, naptSwitch);

        //Table 47 point to table 21 for inbound traffic
        LOG.debug("installNaptPfibEntry for dpnId {} and routerId {}", naptSwitch, routerId);
        externalRouterListener.installNaptPfibEntry(naptSwitch, routerId);

        //Table 47 point to table 21 for outbound traffic
        String vpnName = getVpnName(routerId);
        if(vpnName != null) {
            long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
            if(vpnId > 0) {
                LOG.debug("installNaptPfibEntry for dpnId {} and vpnId {}", naptSwitch, vpnId);
                externalRouterListener.installNaptPfibEntry(naptSwitch, vpnId);
            } else {
                LOG.debug("Associated vpnId not found for router {}",routerId);
            }
        } else {
            LOG.debug("Associated vpnName not found for router {}",routerId);
        }

        //Install Fib entries for ExternalIps & program 36 -> 44

        Optional<IpMapping> ipMappingOptional = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                getIpMappingBuilder(routerId));
        if (vpnName != null) {
            if (ipMappingOptional.isPresent()) {
                List<IpMap> ipMaps = ipMappingOptional.get().getIpMap();
                for (IpMap ipMap : ipMaps) {
                    String externalIp = ipMap.getExternalIp();
                    LOG.debug("advToBgpAndInstallFibAndTsFlows for naptswitch {}, vpnName {} and externalIp {}",
                            naptSwitch, vpnName, externalIp);
                    externalRouterListener.advToBgpAndInstallFibAndTsFlows(naptSwitch, NatConstants.INBOUND_NAPT_TABLE,
                            vpnName, routerId, externalIp, vpnService, fibService, bgpManager, dataBroker, LOG);
                    LOG.debug("Successfully added fib entries in naptswitch {} for router {} with external IP {}", naptSwitch,
                            routerId, externalIp);
                }
            }
        } else {
            LOG.debug("Vpn is not associated to the network of router {}",routerName);
        }

        boolean flowInstalledStatus = handleFlowsInNewNaptSwitch(routerId, dpnId, naptSwitch);
        if (flowInstalledStatus) {
            LOG.debug("Installed all activesession flows in newNaptSwitch {} for routerName {}", routerName);
        } else {
            LOG.error("Failed to install flows in newNaptSwitch {} for routerId {}", naptSwitch, routerId);
        }
        return true;
    }

    private InstanceIdentifier<IpMapping> getIpMappingBuilder(Long routerId) {
        InstanceIdentifier<IpMapping> idBuilder = InstanceIdentifier.builder(IntextIpMap.class)
                .child(IpMapping.class, new IpMappingKey(routerId)).build();
        return idBuilder;
    }

    private String getVpnName(long routerId) {
        Uuid networkId = NatUtil.getNetworkIdFromRouterId(dataBroker, routerId);
        if(networkId == null) {
            LOG.error("networkId is null for the router ID {}", routerId);
        } else {
            final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId, LOG);
            if (vpnName != null) {
                LOG.debug("retreived vpnname {} associated with ext nw {} in router {}",
                        vpnName,networkId,routerId);
                return vpnName;
            } else {
                LOG.error("No VPN associated with ext nw {} belonging to routerId {}",
                        networkId, routerId);
            }
        }
        return null;
    }

    public void updateNaptSwitchBucketStatus(String routerName, BigInteger naptSwitch) {
        LOG.debug("updateNaptSwitchBucketStatus method is called");

        List<BigInteger> dpnList = getDpnListForRouter(routerName);
        for (BigInteger dpn : dpnList) {
            if (dpn.equals(naptSwitch)) {
                LOG.debug("Updating SNAT_TABLE missentry for DpnId {} which is naptSwitch for router {}",dpn,routerName);
                List<BucketInfo> bucketInfoList = handleGroupInPrimarySwitch();
                modifySnatGroupEntry(naptSwitch, bucketInfoList, routerName);
            } else {
                LOG.debug("Updating SNAT_TABLE missentry for DpnId {} which is not naptSwitch for router {}"
                        , dpn, routerName);
                List<BucketInfo> bucketInfoList = handleGroupInNeighborSwitches(dpn, routerName, naptSwitch);
                if (bucketInfoList == null) {
                    LOG.debug("bucketInfo is not populated for orinaryswitch {} whose naptSwitch {} with router {} ",
                            dpn,routerName,naptSwitch);
                    return;
                }
                modifySnatGroupEntry(naptSwitch, bucketInfoList, routerName);
            }
        }
    }

    private boolean handleFlowsInNewNaptSwitch(Long routerId,BigInteger oldNaptSwitch, BigInteger newNaptSwitch) {

        LOG.debug("Proceeding to install flows in newNaptSwitch {} for routerId {}", routerId);
        IpPortMapping ipPortMapping = getIpPortMapping(routerId);
        if (ipPortMapping == null || ipPortMapping.getIntextIpProtocolType() == null || ipPortMapping.getIntextIpProtocolType().isEmpty()) {
            LOG.debug("No Internal Ip Port mapping associated to router {}, no flows need to be installed in" +
                    "newNaptSwitch ", routerId, newNaptSwitch);
            return true;
        }
        //getvpnId
        Long vpnId = null;
        try {
            vpnId = getVpnIdForRouter(routerId);
        }catch (Exception ex) {
            LOG.error("Failed to retreive vpnID for router {} : {}", routerId,ex);
            return false;
        }
        for (IntextIpProtocolType protocolType : ipPortMapping.getIntextIpProtocolType()) {
            if (protocolType.getIpPortMap() == null || protocolType.getIpPortMap().isEmpty()) {
                LOG.debug("No {} session associated to router {}", protocolType.getProtocol(), routerId);
                return true;
            }
            for (IpPortMap intIpPortMap : protocolType.getIpPortMap()) {
                String internalIpAddress = intIpPortMap.getIpPortInternal().split(":")[0];
                String intportnum = intIpPortMap.getIpPortInternal().split(":")[1];

                //Get the external IP address and the port from the model
                NAPTEntryEvent.Protocol proto = protocolType.getProtocol().toString().equals(ProtocolTypes.TCP.toString())
                        ? NAPTEntryEvent.Protocol.TCP : NAPTEntryEvent.Protocol.UDP;
                IpPortExternal ipPortExternal = NatUtil.getExternalIpPortMap(dataBroker, routerId,
                        internalIpAddress, intportnum, proto);
                if (ipPortExternal == null) {
                    LOG.debug("External Ipport mapping is not found for internalIp {} with port {}", internalIpAddress, intportnum);
                    continue;
                }
                String externalIpAddress = ipPortExternal.getIpAddress();
                Integer extportNumber = ipPortExternal.getPortNum();
                LOG.debug("ExternalIPport {}:{} mapping for internal ipport {}:{}",externalIpAddress,extportNumber,
                        internalIpAddress,intportnum);

                SessionAddress sourceAddress = new SessionAddress(internalIpAddress,Integer.valueOf(intportnum));
                SessionAddress externalAddress = new SessionAddress(externalIpAddress,extportNumber);

                //checking naptSwitch status before installing flows
                if(getSwitchStatus(newNaptSwitch)) {
                    //Install the flow in newNaptSwitch Outbound NAPT table.
                    try {
                        NaptEventHandler.buildAndInstallNatFlows(newNaptSwitch, NatConstants.OUTBOUND_NAPT_TABLE,
                                vpnId,  routerId, sourceAddress, externalAddress, proto);
                    } catch (Exception ex) {
                        LOG.error("Failed to add flow in OUTBOUND_NAPT_TABLE for routerid {} dpnId {} ipport {}:{} proto {}" +
                                "extIpport {}:{}", routerId, newNaptSwitch, internalIpAddress
                                , intportnum, proto, externalAddress, extportNumber);
                        return false;
                    }
                    LOG.debug("Succesfully installed a flow in SecondarySwitch {} Outbound NAPT table for router {} " +
                            "ipport {}:{} proto {} extIpport {}:{}", newNaptSwitch,routerId, internalIpAddress
                            , intportnum, proto, externalAddress, extportNumber);
                    //Install the flow in newNaptSwitch Inbound NAPT table.
                    try {
                        NaptEventHandler.buildAndInstallNatFlows(newNaptSwitch, NatConstants.INBOUND_NAPT_TABLE,
                                vpnId, routerId, externalAddress, sourceAddress, proto);
                    } catch (Exception ex) {
                        LOG.error("Failed to add flow in INBOUND_NAPT_TABLE for routerid {} dpnId {} extIpport{}:{} proto {} ipport {}:{}",
                                routerId, newNaptSwitch, externalAddress, extportNumber,
                                proto, internalIpAddress, intportnum);
                        return false;
                    }
                    LOG.debug("Succesfully installed a flow in SecondarySwitch {} Inbound NAPT table for router {} " +
                            "ipport {}:{} proto {} extIpport {}:{}", newNaptSwitch,routerId, internalIpAddress
                            , intportnum, proto, externalAddress, extportNumber);

                } else {
                    LOG.error("NewNaptSwitch {} gone down while installing flows from oldNaptswitch {}",
                            newNaptSwitch,oldNaptSwitch);
                    return false;
                }
            }
        }
        return true;
    }

    private Long getVpnIdForRouter(Long routerId) {
        try {
            //getvpnId
            Uuid networkId = NatUtil.getNetworkIdFromRouterId(dataBroker, routerId);
            if (networkId == null) {
                LOG.debug("network is not associated to router {}", routerId);
            } else {
                Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
                if (vpnUuid == null) {
                    LOG.debug("vpn is not associated for network {} in router {}", networkId, routerId);
                } else {
                    Long vpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
                    if (vpnId != null) {
                        LOG.debug("retrieved vpnId {} for router {}",vpnId,routerId);
                        return vpnId;
                    } else {
                        LOG.debug("retrieved invalid vpn Id");
                    }
                }
            }
        } catch (Exception ex){
            LOG.debug("Exception while retreiving vpnId for router {} - {}", routerId, ex);
        }
        return  null;
    }

    private List<BigInteger> getDpnListForRouter(String routerName) {
        List<BigInteger> dpnList = new ArrayList<BigInteger>();
        List<VpnToDpnList> vpnDpnList = NatUtil.getVpnToDpnList(dataBroker, routerName);
        for (VpnToDpnList vpnToDpn : vpnDpnList) {
            dpnList.add(vpnToDpn.getDpnId());
        }
        return dpnList;
    }

    public boolean getSwitchStatus(BigInteger switchId){
        NodeId nodeId = new NodeId("openflow:" + switchId);
        LOG.debug("Querying switch with dpnId {} is up/down", nodeId);
        InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).build();
        Optional<Node> nodeOptional = NatUtil.read(dataBroker,LogicalDatastoreType.OPERATIONAL,nodeInstanceId);
        if (nodeOptional.isPresent()) {
            LOG.debug("Switch {} is up", nodeId);
            return true;
        }
        LOG.debug("Switch {} is down", nodeId);
        return false;
    }

    public List<BucketInfo> handleGroupInPrimarySwitch() {
        List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
        List<ActionInfo> listActionInfoPrimary = new ArrayList<ActionInfo>();
        listActionInfoPrimary.add(new ActionInfo(ActionType.nx_resubmit,
                new String[]{String.valueOf(NatConstants.TERMINATING_SERVICE_TABLE)}));
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        listBucketInfo.add(bucketPrimary);
        return listBucketInfo;
    }

    public List<BucketInfo> handleGroupInNeighborSwitches(BigInteger dpnId, String routerName, BigInteger naptSwitch) {
        List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
        String ifNamePrimary;
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("Invalid routerId returned for routerName {}",routerName);
            return listBucketInfo;
        }
        ifNamePrimary = getTunnelInterfaceName(dpnId, naptSwitch);
        if (ifNamePrimary != null) {
            LOG.debug("TunnelInterface {} between ordinary switch {} and naptSwitch {}",ifNamePrimary,dpnId,naptSwitch);
            List<ActionInfo> listActionInfoPrimary = getEgressActionsForInterface(ifNamePrimary, routerId);
            BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
            listBucketInfo.add(bucketPrimary);
        } else {
            LOG.debug("No TunnelInterface between ordinary switch {} and naptSwitch {}",dpnId,naptSwitch);
        }
        return listBucketInfo;
    }

    protected void installSnatGroupEntry(BigInteger dpnId, List<BucketInfo> bucketInfo, String routerName) {
        GroupEntity groupEntity = null;
        try {
            long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(routerName), idManager);
            LOG.debug("install SnatMissEntry for groupId {} for dpnId {} for router {}", groupId, dpnId,routerName);
            groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName,
                    GroupTypes.GroupAll, bucketInfo);
            mdsalManager.installGroup(groupEntity);
            LOG.debug("installed the SNAT to NAPT GroupEntity:{}", groupEntity);
        } catch (Exception ex) {
            LOG.error("Failed to install group for groupEntity {} : {}",groupEntity,ex);
        }
    }

    private void modifySnatGroupEntry(BigInteger dpnId, List<BucketInfo> bucketInfo, String routerName) {
        installSnatGroupEntry(dpnId,bucketInfo,routerName);
        LOG.debug("modified SnatMissEntry for dpnId {} of router {}",dpnId,routerName);
    }

    protected String getTunnelInterfaceName(BigInteger srcDpId, BigInteger dstDpId) {
        try {
            Future<RpcResult<GetTunnelInterfaceNameOutput>> result = itmManager.getTunnelInterfaceName(
                    new GetTunnelInterfaceNameInputBuilder().setSourceDpid(srcDpId).setDestinationDpid(dstDpId).build());
            RpcResult<GetTunnelInterfaceNameOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting tunnel interface Id for tunnel between {} and  {} : {}",
                    srcDpId, dstDpId, e);
        }

        return null;
    }

    protected List<ActionInfo> getEgressActionsForInterface(String ifName, long routerId) {
        LOG.debug("getEgressActionsForInterface called for interface {}", ifName);
        List<ActionInfo> listActionInfo = new ArrayList<ActionInfo>();
        try {
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                    interfaceManager.getEgressActionsForInterface(
                            new GetEgressActionsForInterfaceInputBuilder().setIntfName(ifName).setTunnelKey(routerId).build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}"
                        , ifName, rpcResult.getErrors());
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
                            int vlanVid = ((SetFieldCase)actionClass).getSetField().getVlanMatch()
                                    .getVlanId().getVlanId().getValue();
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

    private IpPortMapping getIpPortMapping(Long routerId) {
        Optional<IpPortMapping> ipPortMapData = NatUtil.read(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                buildIpToPortMapIdentifier(routerId));
        if (ipPortMapData.isPresent()) {
            return ipPortMapData.get();
        }
        return null;
    }

    public boolean updateNaptSwitch(String routerName, BigInteger naptSwitchId) {
        RouterToNaptSwitch naptSwitch = new RouterToNaptSwitchBuilder().setKey(new RouterToNaptSwitchKey(routerName))
                .setPrimarySwitchId(naptSwitchId).build();
        try {
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    NatUtil.buildNaptSwitchRouterIdentifier(routerName), naptSwitch);
        } catch (Exception ex) {
            LOG.error("Failed to write naptSwitch {} for router {} in ds",
                    naptSwitchId,routerName);
            return false;
        }
        LOG.debug("Successfully updated naptSwitch {} for router {} in ds",
                naptSwitchId,routerName);
        return true;
    }

    private InstanceIdentifier<IpPortMapping> buildIpToPortMapIdentifier(Long routerId) {
        InstanceIdentifier<IpPortMapping> ipPortMapId = InstanceIdentifier.builder(IntextIpPortMap.class).child
                (IpPortMapping.class, new IpPortMappingKey(routerId)).build();
        return ipPortMapId;
    }

    public FlowEntity buildSnatFlowEntity(BigInteger dpId, String routerName, long groupId, int addordel) {

        FlowEntity flowEntity = null;
        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("Invalid routerId returned for routerName {}",routerName);
            return flowEntity;
        }
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[]{ 0x0800L }));
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                BigInteger.valueOf(routerId), MetaDataUtil.METADATA_MASK_VRFID }));

        String flowRef = getFlowRefSnat(dpId, NatConstants.PSNAT_TABLE, routerName);

        if (addordel == NatConstants.ADD_FLOW) {
            List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
            List<ActionInfo> actionsInfo = new ArrayList<ActionInfo>();

            ActionInfo actionSetField = new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[] {
                    BigInteger.valueOf(routerId)}) ;
            actionsInfo.add(actionSetField);
            LOG.debug("Setting the tunnel to the list of action infos {}", actionsInfo);
            actionsInfo.add(new ActionInfo(ActionType.group, new String[] {String.valueOf(groupId)}));
            instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfo));

            flowEntity = MDSALUtil.buildFlowEntity(dpId, NatConstants.PSNAT_TABLE, flowRef,
                    NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                    NatConstants.COOKIE_SNAT_TABLE, matches, instructions);
        } else {
            flowEntity = MDSALUtil.buildFlowEntity(dpId, NatConstants.PSNAT_TABLE, flowRef,
                    NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                    NatConstants.COOKIE_SNAT_TABLE, matches, null);
        }
        return flowEntity;
    }

    private String getFlowRefSnat(BigInteger dpnId, short tableId, String routerID) {
        return new StringBuilder().append(NatConstants.SNAT_FLOWID_PREFIX).append(dpnId).append(NatConstants.FLOWID_SEPARATOR).
                append(tableId).append(NatConstants.FLOWID_SEPARATOR).append(routerID).toString();
    }
}
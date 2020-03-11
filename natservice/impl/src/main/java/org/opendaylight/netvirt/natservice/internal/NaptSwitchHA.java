/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProtocolTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolTypeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NaptSwitchHA {
    private static final Logger LOG = LoggerFactory.getLogger(NaptSwitchHA.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final ItmRpcService itmManager;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final IdManagerService idManager;
    private final NAPTSwitchSelector naptSwitchSelector;
    private final ExternalRoutersListener externalRouterListener;
    private final NaptEventHandler naptEventHandler;
    private final IFibManager fibManager;
    private final IElanService elanManager;
    private final EvpnNaptSwitchHA evpnNaptSwitchHA;
    private final SnatServiceManager natServiceManager;
    private final NatMode natMode;
    private final IInterfaceManager interfaceManager;
    private final NatOverVxlanUtil natOverVxlanUtil;

    private volatile Collection<String> externalIpsCache;

    @Inject
    public NaptSwitchHA(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                        final ExternalRoutersListener externalRouterListener,
                        final ItmRpcService itmManager,
                        final OdlInterfaceRpcService odlInterfaceRpcService,
                        final IdManagerService idManager,
                        final NAPTSwitchSelector naptSwitchSelector,
                        final IFibManager fibManager,
                        final EvpnNaptSwitchHA evpnNaptSwitchHA,
                        final IElanService elanManager,
                        final SnatServiceManager natServiceManager,
                        final NatserviceConfig config,
                        final NaptEventHandler naptEventHandler,
                        final IInterfaceManager interfaceManager,
                        final NatOverVxlanUtil natOverVxlanUtil) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.externalRouterListener = externalRouterListener;
        this.itmManager = itmManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.idManager = idManager;
        this.naptSwitchSelector = naptSwitchSelector;
        this.naptEventHandler = naptEventHandler;
        this.fibManager = fibManager;
        this.evpnNaptSwitchHA = evpnNaptSwitchHA;
        this.elanManager = elanManager;
        this.natServiceManager = natServiceManager;
        this.interfaceManager = interfaceManager;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatMode.Controller;
        }
        this.natOverVxlanUtil = natOverVxlanUtil;
    }

    protected void removeSnatFlowsInOldNaptSwitch(Routers extRouter, Uint32 routerId, Uint64 naptSwitch,
                                                  @Nullable Map<String, Uint32> externalIpmap, String externalVpnName,
                                                  TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {

        //remove SNAT flows in old NAPT SWITCH
        String routerName = extRouter.getRouterName();
        Uuid networkId = NatUtil.getNetworkIdFromRouterName(dataBroker, routerName);
        String vpnName = getExtNetworkVpnName(routerName, networkId);
        if (vpnName == null) {
            LOG.error("removeSnatFlowsInOldNaptSwitch : Vpn is not associated to externalN/w of router {}",
                routerName);
            return;
        }
        ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName, networkId);
        if (extNwProvType == null) {
            LOG.error("removeSnatFlowsInOldNaptSwitch : Unable to retrieve the External Network Provider Type "
                + "for Router {}", routerName);
            return;
        }
        if (extNwProvType == ProviderTypes.VXLAN) {
            evpnNaptSwitchHA.evpnRemoveSnatFlowsInOldNaptSwitch(routerName, routerId, vpnName, naptSwitch, confTx);
        } else {
            //Remove the Terminating Service table entry which forwards the packet to Outbound NAPT Table
            Uint64 tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, natOverVxlanUtil, elanManager,
                    idManager, routerId, routerName);
            String tsFlowRef = externalRouterListener.getFlowRefTs(naptSwitch, NwConstants.INTERNAL_TUNNEL_TABLE,
                Uint32.valueOf(tunnelId));
            FlowEntity tsNatFlowEntity = NatUtil.buildFlowEntity(naptSwitch, NwConstants.INTERNAL_TUNNEL_TABLE,
                tsFlowRef);

            LOG.info("removeSnatFlowsInOldNaptSwitch : Remove the flow in table {} for the old napt switch "
                + "with the DPN ID {} and router ID {}", NwConstants.INTERNAL_TUNNEL_TABLE, naptSwitch, routerId);
            mdsalManager.removeFlow(confTx, tsNatFlowEntity);
        }
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            //Remove the flow table 25->44 If there is no FIP Match on table 25 (PDNAT_TABLE)
            NatUtil.removePreDnatToSnatTableEntry(confTx, mdsalManager, naptSwitch);
        }
        //Remove the Outbound flow entry which forwards the packet to Outbound NAPT Table
        LOG.info("Remove the flow in table {} for the old napt switch with the DPN ID {} and router ID {}",
            NwConstants.OUTBOUND_NAPT_TABLE, naptSwitch, routerId);

        String outboundTcpNatFlowRef = externalRouterListener.getFlowRefOutbound(naptSwitch,
            NwConstants.OUTBOUND_NAPT_TABLE, routerId, NwConstants.IP_PROT_TCP);
        FlowEntity outboundTcpNatFlowEntity = NatUtil.buildFlowEntity(naptSwitch,
            NwConstants.OUTBOUND_NAPT_TABLE, outboundTcpNatFlowRef);
        mdsalManager.removeFlow(confTx, outboundTcpNatFlowEntity);

        String outboundUdpNatFlowRef = externalRouterListener.getFlowRefOutbound(naptSwitch,
            NwConstants.OUTBOUND_NAPT_TABLE, routerId, NwConstants.IP_PROT_UDP);
        FlowEntity outboundUdpNatFlowEntity = NatUtil.buildFlowEntity(naptSwitch,
            NwConstants.OUTBOUND_NAPT_TABLE, outboundUdpNatFlowRef);
        mdsalManager.removeFlow(confTx, outboundUdpNatFlowEntity);

        String icmpDropFlowRef = externalRouterListener.getFlowRefOutbound(naptSwitch,
            NwConstants.OUTBOUND_NAPT_TABLE, routerId, NwConstants.IP_PROT_ICMP);
        FlowEntity icmpDropFlowEntity = NatUtil.buildFlowEntity(naptSwitch, NwConstants.OUTBOUND_NAPT_TABLE,
            icmpDropFlowRef);
        mdsalManager.removeFlow(confTx, icmpDropFlowEntity);

        //Remove the NAPT PFIB TABLE (47->21) which forwards the incoming packet to FIB Table matching on the
        // External Subnet Vpn Id.
        Collection<Uuid> externalSubnetIdsForRouter = NatUtil.getExternalSubnetIdsForRouter(dataBroker,
            routerName);
        for (Uuid externalSubnetId : externalSubnetIdsForRouter) {
            Uint32 subnetVpnId = NatUtil.getVpnId(dataBroker, externalSubnetId.getValue());
            if (subnetVpnId != NatConstants.INVALID_ID && !NatUtil.checkForRoutersWithSameExtSubnetAndNaptSwitch(
                dataBroker, externalSubnetId, routerName, naptSwitch)) {
                String natPfibSubnetFlowRef = externalRouterListener.getFlowRefTs(naptSwitch,
                    NwConstants.NAPT_PFIB_TABLE, subnetVpnId);
                FlowEntity natPfibFlowEntity = NatUtil.buildFlowEntity(naptSwitch, NwConstants.NAPT_PFIB_TABLE,
                    natPfibSubnetFlowRef);
                mdsalManager.removeFlow(confTx, natPfibFlowEntity);
                LOG.debug("removeSnatFlowsInOldNaptSwitch : Removed the flow in table {} with external subnet "
                          + "Vpn Id {} as metadata on Napt Switch {}", NwConstants.NAPT_PFIB_TABLE,
                    subnetVpnId, naptSwitch);
            }
        }

        // Remove the NAPT_PFIB_TABLE(47) flow entry forwards the packet to Fib Table for inbound traffic
        // matching on the router ID.
        String naptPFibflowRef =
            externalRouterListener.getFlowRefTs(naptSwitch, NwConstants.NAPT_PFIB_TABLE, routerId);
        FlowEntity naptPFibFlowEntity =
            NatUtil.buildFlowEntity(naptSwitch, NwConstants.NAPT_PFIB_TABLE, naptPFibflowRef);
        LOG.info("removeSnatFlowsInOldNaptSwitch : Remove the flow in table {} for the old napt switch "
            + "with the DPN ID {} and router ID {}", NwConstants.NAPT_PFIB_TABLE, naptSwitch, routerId);
        mdsalManager.removeFlow(confTx, naptPFibFlowEntity);

        // Remove the NAPT_PFIB_TABLE(47) flow entry forwards the packet to Fib Table for outbound traffic
        // matching on the vpn ID.
        boolean switchSharedByRouters = false;
        Uuid extNetworkId = extRouter.getNetworkId();
        if (extNetworkId != null && !NatUtil.checkForRoutersWithSameExtNetAndNaptSwitch(
            dataBroker, extNetworkId, routerName, naptSwitch)) {
            List<String> routerNamesAssociated = getRouterIdsForExtNetwork(extNetworkId);
            for (String routerNameAssociated : routerNamesAssociated) {
                if (!routerNameAssociated.equals(routerName)) {
                    Uint32 routerIdAssociated = NatUtil.getVpnId(dataBroker, routerNameAssociated);
                    Uint64 naptDpn = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerNameAssociated);
                    if (naptDpn != null && naptDpn.equals(naptSwitch)) {
                        LOG.debug("removeSnatFlowsInOldNaptSwitch : Napt switch {} is also acting as primary "
                            + "for router {}", naptSwitch, routerIdAssociated);
                        switchSharedByRouters = true;
                        break;
                    }
                }
            }
            if (!switchSharedByRouters) {
                Uint32 vpnId = NatUtil.getVpnId(dataBroker,externalVpnName);
                if (vpnId != NatConstants.INVALID_ID) {
                    String naptFibflowRef =
                        externalRouterListener.getFlowRefTs(naptSwitch, NwConstants.NAPT_PFIB_TABLE, vpnId);
                    FlowEntity naptFibFlowEntity =
                        NatUtil.buildFlowEntity(naptSwitch, NwConstants.NAPT_PFIB_TABLE, naptFibflowRef);
                    LOG.info("removeSnatFlowsInOldNaptSwitch : Remove the flow in table {} for the old napt switch"
                        + " with the DPN ID {} and vpnId {}", NwConstants.NAPT_PFIB_TABLE, naptSwitch, vpnId);
                    mdsalManager.removeFlow(confTx, naptFibFlowEntity);
                } else {
                    LOG.error("removeSnatFlowsInOldNaptSwitch : Invalid vpnId retrieved for routerId {}",
                        routerId);
                    return;
                }
            }
        }

        //Remove Fib entries,tables 20->44 ,36-> 44
        String gwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routerName);
        if (externalIpmap != null && !externalIpmap.isEmpty()) {
            for (Entry<String, Uint32> entry : externalIpmap.entrySet()) {
                String externalIp = entry.getKey();
                Uint32 label = entry.getValue();
                externalRouterListener.delFibTsAndReverseTraffic(naptSwitch, routerName, routerId, externalIp, vpnName,
                    extNetworkId, label, gwMacAddress, true, confTx);
                LOG.debug("removeSnatFlowsInOldNaptSwitch : Successfully removed fib entries in old naptswitch {} "
                    + "for router {} and externalIps {} label {}", naptSwitch, routerId, externalIp, label);
            }
        } else {
            List<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerName);
            if (extNetworkId != null) {
                externalRouterListener.clearFibTsAndReverseTraffic(naptSwitch, routerId, extNetworkId,
                    externalIps, null, gwMacAddress, confTx);
                LOG.debug(
                    "removeSnatFlowsInOldNaptSwitch : Successfully removed fib entries in old naptswitch {} for "
                        + "router {} with networkId {} and externalIps {}", naptSwitch, routerId, extNetworkId,
                    externalIps);
            } else {
                LOG.debug("removeSnatFlowsInOldNaptSwitch : External network not associated to router {}",
                    routerId);
            }
            externalRouterListener.removeNaptFibExternalOutputFlows(routerId, naptSwitch, extNetworkId,
                externalIps, confTx);
        }

        //For the router ID get the internal IP , internal port and the corresponding external IP and external Port.
        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null || ipPortMapping.getIntextIpProtocolType() == null
            || ipPortMapping.getIntextIpProtocolType().isEmpty()) {
            LOG.warn("removeSnatFlowsInOldNaptSwitch : No Internal Ip Port mapping associated to router {}, "
                + "no flows need to be removed in oldNaptSwitch {}", routerId, naptSwitch);
            return;
        }
        Uint64 cookieSnatFlow = NatUtil.getCookieNaptFlow(routerId);
        Map<IntextIpProtocolTypeKey, IntextIpProtocolType> keyIntextIpProtocolTypeMap
                = ipPortMapping.nonnullIntextIpProtocolType();
        for (IntextIpProtocolType intextIpProtocolType : keyIntextIpProtocolTypeMap.values()) {
            if (intextIpProtocolType.getIpPortMap() == null || intextIpProtocolType.getIpPortMap().isEmpty()) {
                LOG.debug("removeSnatFlowsInOldNaptSwitch : No {} session associated to router {},"
                        + "no flows need to be removed in oldNaptSwitch {}",
                    intextIpProtocolType.getProtocol(), routerId, naptSwitch);
                continue;
            }
            String protocol = intextIpProtocolType.getProtocol().name();
            Map<IpPortMapKey, IpPortMap> keyIpPortMapMap = intextIpProtocolType.nonnullIpPortMap();
            for (IpPortMap ipPortMap : keyIpPortMapMap.values()) {
                String ipPortInternal = ipPortMap.getIpPortInternal();
                String[] ipPortParts = ipPortInternal.split(":");
                if (ipPortParts.length != 2) {
                    LOG.error("removeSnatFlowsInOldNaptSwitch : Unable to retrieve the Internal IP and port");
                    continue;
                }
                String internalIp = ipPortParts[0];
                String internalPort = ipPortParts[1];

                //Build and remove flow in outbound NAPT table
                String switchFlowRef =
                    NatUtil.getNaptFlowRef(naptSwitch, NwConstants.OUTBOUND_NAPT_TABLE, String.valueOf(routerId),
                        internalIp, Integer.parseInt(internalPort), protocol);
                FlowEntity outboundNaptFlowEntity =
                    NatUtil.buildFlowEntity(naptSwitch, NwConstants.OUTBOUND_NAPT_TABLE,
                        cookieSnatFlow, switchFlowRef);

                LOG.info("removeSnatFlowsInOldNaptSwitch : Remove the flow in table {} for old napt switch "
                    + "with the DPN ID {} and router ID {}", NwConstants.OUTBOUND_NAPT_TABLE, naptSwitch, routerId);
                mdsalManager.removeFlow(confTx, outboundNaptFlowEntity);

                //Build and remove flow in  inbound NAPT table
                switchFlowRef =
                    NatUtil.getNaptFlowRef(naptSwitch, NwConstants.INBOUND_NAPT_TABLE, String.valueOf(routerId),
                        internalIp, Integer.parseInt(internalPort), protocol);
                FlowEntity inboundNaptFlowEntity =
                    NatUtil.buildFlowEntity(naptSwitch, NwConstants.INBOUND_NAPT_TABLE,
                        cookieSnatFlow, switchFlowRef);

                LOG.info(
                    "removeSnatFlowsInOldNaptSwitch : Remove the flow in table {} for old napt switch with the "
                        + "DPN ID {} and router ID {}", NwConstants.INBOUND_NAPT_TABLE, naptSwitch, routerId);
                mdsalManager.removeFlow(confTx, inboundNaptFlowEntity);
            }
        }
    }

    @NonNull
    private List<String> getRouterIdsForExtNetwork(Uuid extNetworkId) {
        List<String> routerUuidsAsString = new ArrayList<>();
        InstanceIdentifier<Networks> extNetwork = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(extNetworkId)).build();
        Optional<Networks> extNetworkData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, extNetwork);
        if (extNetworkData.isPresent()) {
            List<Uuid> routerUuids = extNetworkData.get().getRouterIds();
            if (routerUuids != null) {
                for (Uuid routerUuid : routerUuids) {
                    routerUuidsAsString.add(routerUuid.getValue());
                }
            }
        }
        return routerUuidsAsString;
    }

    public boolean isNaptSwitchDown(Routers extRouter, Uint32 routerId, Uint64 dpnId, Uint64 naptSwitch,
                                    Uint32 routerVpnId, Collection<String> externalIpCache,
                                    TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        return isNaptSwitchDown(extRouter, routerId, dpnId, naptSwitch, routerVpnId, externalIpCache, true,
                confTx);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public boolean isNaptSwitchDown(Routers extRouter, Uint32 routerId, Uint64 dpnId, Uint64 naptSwitch,
                                    Uint32 routerVpnId, Collection<String> externalIpCache, boolean isClearBgpRts,
                                    TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        externalIpsCache = externalIpCache;
        String routerName = extRouter.getRouterName();
        if (!naptSwitch.equals(dpnId)) {
            LOG.debug("isNaptSwitchDown : DpnId {} is not a naptSwitch {} for Router {}",
                    dpnId, naptSwitch, routerName);
            return false;
        }
        LOG.debug("NaptSwitch {} is down for Router {}", naptSwitch, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("isNaptSwitchDown : Invalid routerId returned for routerName {}", routerName);
            return true;
        }
        Uuid networkId = extRouter.getNetworkId();
        String vpnName = getExtNetworkVpnName(routerName, networkId);
        //elect a new NaptSwitch
        naptSwitch = naptSwitchSelector.selectNewNAPTSwitch(routerName, Arrays.asList(naptSwitch));
        if (natMode == NatMode.Conntrack) {
            Routers extRouters = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
            natServiceManager.notify(confTx, extRouters, null, dpnId, dpnId,
                    SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_DISBL);
            if (extRouters.isEnableSnat()) {
                natServiceManager.notify(confTx, extRouters, null, dpnId, dpnId,
                        SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
            }
            natServiceManager.notify(confTx, extRouters, null, naptSwitch, naptSwitch,
                    SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_ENBL);
            if (extRouters.isEnableSnat()) {
                natServiceManager.notify(confTx, extRouters, null, naptSwitch, naptSwitch,
                        SnatServiceManager.Action.SNAT_ALL_SWITCH_ENBL);
            }
        } else {
            if (naptSwitch.equals(Uint64.ZERO)) {
                LOG.warn("isNaptSwitchDown : No napt switch is elected since all the switches for router {}"
                        + " are down. SNAT IS NOT SUPPORTED FOR ROUTER {}", routerName, routerName);
                boolean naptUpdatedStatus = updateNaptSwitch(routerName, naptSwitch);
                if (!naptUpdatedStatus) {
                    LOG.debug("isNaptSwitchDown : Failed to update naptSwitch {} for router {} in ds",
                            naptSwitch, routerName);
                }
                //clearBgpRoutes
                if (externalIpsCache != null) {
                    if (vpnName != null) {
                        //List<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
                        //if (externalIps != null) {
                        if (isClearBgpRts) {
                            LOG.debug("isNaptSwitchDown : Clearing both FIB entries and the BGP routes");
                            for (String externalIp : externalIpsCache) {
                                externalRouterListener.clearBgpRoutes(externalIp, vpnName);
                            }
                        } else {
                            LOG.debug("isNaptSwitchDown : Clearing the FIB entries but not the BGP routes");
                            String rd = NatUtil.getVpnRd(dataBroker, vpnName);
                            for (String externalIp : externalIpsCache) {
                                LOG.debug("isNaptSwitchDown : Removing Fib entry rd {} prefix {}", rd, externalIp);
                                fibManager.removeFibEntry(rd, externalIp, null, null,
                                        null);
                            }
                        }
                    } else {
                        LOG.debug("isNaptSwitchDown : vpn is not associated to extn/w for router {}", routerName);
                    }
                } else {
                    LOG.debug("isNaptSwitchDown : No ExternalIps found for subnets under router {}, "
                            + "no bgp routes need to be cleared", routerName);
                }
                return true;
            }
            //checking elected switch health status
            if (!NatUtil.getSwitchStatus(dataBroker, naptSwitch)) {
                LOG.error("isNaptSwitchDown : Newly elected Napt switch {} for router {} is down",
                        naptSwitch, routerName);
                return true;
            }
            LOG.debug("isNaptSwitchDown : New NaptSwitch {} is up for Router {} and can proceed for flow installation",
                    naptSwitch, routerName);
            //update napt model for new napt switch
            boolean naptUpdated = updateNaptSwitch(routerName, naptSwitch);
            if (naptUpdated) {
                //update group of ordinary switch point to naptSwitch tunnel port
                updateNaptSwitchBucketStatus(routerName, routerId, naptSwitch);
            } else {
                LOG.error("isNaptSwitchDown : Failed to update naptSwitch model for newNaptSwitch {} for router {}",
                        naptSwitch, routerName);
            }

            //update table26 forward packets to table46(outbound napt table)
            FlowEntity flowEntity =
                    buildSnatFlowEntityForNaptSwitch(naptSwitch, routerName, routerVpnId, NatConstants.ADD_FLOW);
            if (flowEntity == null) {
                LOG.error("isNaptSwitchDown : Failed to populate flowentity for router {} in naptSwitch {}",
                        routerName, naptSwitch);
            } else {
                LOG.debug("isNaptSwitchDown : Successfully installed flow in naptSwitch {} for router {}",
                        naptSwitch, routerName);
                mdsalManager.addFlow(confTx, flowEntity);
            }

            installSnatFlows(routerName, routerId, naptSwitch, routerVpnId, networkId, vpnName, confTx);

            boolean flowInstalledStatus = handleNatFlowsInNewNaptSwitch(routerName, routerId, dpnId, naptSwitch,
                    routerVpnId, networkId);
            if (flowInstalledStatus) {
                LOG.debug("isNaptSwitchDown :Installed all active session flows in newNaptSwitch {} for routerName {}",
                        naptSwitch, routerName);
            } else {
                LOG.error("isNaptSwitchDown : Failed to install flows in newNaptSwitch {} for routerId {}",
                        naptSwitch, routerId);
            }

            //remove group in new naptswitch, coz this switch acted previously as ordinary switch
            Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME,
                NatUtil.getGroupIdKey(routerName));
            if (groupId != NatConstants.INVALID_ID) {
                try {
                    LOG.info("isNaptSwitchDown : Removing NAPT Group in new naptSwitch {}",
                        naptSwitch);
                    mdsalManager.removeGroup(confTx, naptSwitch, groupId.longValue());
                } catch (Exception ex) {
                    LOG.error("isNaptSwitchDown : Failed to remove group in new naptSwitch {}",
                        naptSwitch, ex);
                }
            } else {
                LOG.error("NAT Service : Unable to obtain groupId for router:{}", routerName);
            }
        }
        return true;
    }

    @Nullable
    private String getExtNetworkVpnName(String routerName, Uuid networkId) {
        if (networkId == null) {
            LOG.error("getExtNetworkVpnName : networkId is null for the router ID {}", routerName);
        } else {
            final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId);
            if (vpnName != null) {
                LOG.debug("getExtNetworkVpnName : retrieved vpn name {} associated with ext nw {} in router {}",
                    vpnName, networkId, routerName);
                return vpnName;
            } else {
                LOG.error("getExtNetworkVpnName : No VPN associated with ext nw {} belonging to routerId {}",
                    networkId, routerName);
            }
        }
        LOG.error("getExtNetworkVpnName : External Network VPN not found for router : {}", routerName);
        return null;
    }

    public void updateNaptSwitchBucketStatus(String routerName, Uint32 routerId, Uint64 naptSwitch) {
        LOG.debug("updateNaptSwitchBucketStatus : called");

        List<Uint64> dpnList = naptSwitchSelector.getDpnsForVpn(routerName);
        //List<BigInteger> dpnList = getDpnListForRouter(routerName);
        if (dpnList.isEmpty()) {
            LOG.warn("updateNaptSwitchBucketStatus : No switches found for router {}", routerName);
            return;
        }
        for (Uint64 dpn : dpnList) {
            if (!dpn.equals(naptSwitch)) {
                LOG.debug("updateNaptSwitchBucketStatus : Updating SNAT_TABLE missentry for DpnId {} "
                        + "which is not naptSwitch for router {}", dpn, routerName);
                List<BucketInfo> bucketInfoList = handleGroupInNeighborSwitches(dpn, routerName, routerId, naptSwitch);
                if (bucketInfoList.isEmpty()) {
                    LOG.error("Failed to populate bucketInfo for non-napt switch {} whose naptSwitch:{} for router:{}",
                        dpn,naptSwitch,routerName);
                    continue;
                }
                modifySnatGroupEntry(dpn, bucketInfoList, routerName);
                externalRouterListener.installSnatMissEntry(dpn, bucketInfoList, routerName, routerId);
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean handleNatFlowsInNewNaptSwitch(String routerName, Uint32 routerId, Uint64 oldNaptSwitch,
                                                    Uint64 newNaptSwitch, Uint32 routerVpnId, Uuid networkId) {
        LOG.debug("handleNatFlowsInNewNaptSwitch : Proceeding to install flows in newNaptSwitch {} for routerId {}",
                newNaptSwitch, routerId);
        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null || ipPortMapping.getIntextIpProtocolType() == null
            || ipPortMapping.getIntextIpProtocolType().isEmpty()) {
            LOG.debug("handleNatFlowsInNewNaptSwitch : No Internal Ip Port mapping associated to router {},"
                    + "no flows need to be installed in newNaptSwitch {}", routerId, newNaptSwitch);
            return true;
        }
        //getvpnId
        Uint32 vpnId = getVpnIdForRouter(routerId, networkId);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("handleNatFlowsInNewNaptSwitch : Invalid vpnId for routerId {}", routerId);
            return false;
        }
        Uint32 bgpVpnId;
        if (routerId.equals(routerVpnId)) {
            bgpVpnId = NatConstants.INVALID_ID;
        } else {
            bgpVpnId = routerVpnId;
        }
        LOG.debug("handleNatFlowsInNewNaptSwitch : retrieved bgpVpnId {} for router {}", bgpVpnId, routerId);
        // Get the External Gateway MAC Address
        String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routerName);
        if (extGwMacAddress != null) {
            LOG.debug("handleNatFlowsInNewNaptSwitch :External Gateway MAC address {} found for External Router ID {}",
                    extGwMacAddress, routerId);
        } else {
            LOG.error("handleNatFlowsInNewNaptSwitch : No External Gateway MAC address found for External Router ID {}",
                    routerId);
            return false;
        }
        for (IntextIpProtocolType protocolType : ipPortMapping.nonnullIntextIpProtocolType().values()) {
            if (protocolType.getIpPortMap() == null || protocolType.getIpPortMap().isEmpty()) {
                LOG.debug("handleNatFlowsInNewNaptSwitch : No {} session associated to router {}",
                        protocolType.getProtocol(), routerId);
                return true;
            }
            for (IpPortMap intIpPortMap : protocolType.nonnullIpPortMap().values()) {
                String internalIpAddress = intIpPortMap.getIpPortInternal().split(":")[0];
                String intportnum = intIpPortMap.getIpPortInternal().split(":")[1];
                LOG.debug("handleNatFlowsInNewNaptSwitch : Found Internal IP Address {} and Port Number {}",
                    internalIpAddress, intportnum);
                //Get the external IP address and the port from the model
                NAPTEntryEvent.Protocol proto =
                    protocolType.getProtocol().toString().equals(ProtocolTypes.TCP.toString())
                    ? NAPTEntryEvent.Protocol.TCP : NAPTEntryEvent.Protocol.UDP;
                IpPortExternal ipPortExternal = NatUtil.getExternalIpPortMap(dataBroker, routerId,
                    internalIpAddress, intportnum, proto);
                if (ipPortExternal == null) {
                    LOG.debug("handleNatFlowsInNewNaptSwitch : External Ipport mapping is not found for internalIp {} "
                            + "with port {}", internalIpAddress, intportnum);
                    continue;
                }
                String externalIpAddress = ipPortExternal.getIpAddress();
                Integer extportNumber = ipPortExternal.getPortNum().toJava();
                LOG.debug("handleNatFlowsInNewNaptSwitch : ExternalIPport {}:{} mapping for internal ipport {}:{}",
                        externalIpAddress, extportNumber, internalIpAddress, intportnum);

                SessionAddress sourceAddress = new SessionAddress(internalIpAddress, Integer.parseInt(intportnum));
                SessionAddress externalAddress = new SessionAddress(externalIpAddress, extportNumber);

                //checking naptSwitch status before installing flows
                if (NatUtil.getSwitchStatus(dataBroker, newNaptSwitch)) {
                    //Install the flow in newNaptSwitch Inbound NAPT table.
                    try {
                        naptEventHandler.buildAndInstallNatFlows(newNaptSwitch, NwConstants.INBOUND_NAPT_TABLE,
                            vpnId, routerId, bgpVpnId, externalAddress, sourceAddress, proto, extGwMacAddress);
                    } catch (RuntimeException ex) {
                        LOG.error("handleNatFlowsInNewNaptSwitch : Failed to add flow in INBOUND_NAPT_TABLE for "
                                + "routerid {} dpnId {} extIpport{}:{} proto {} ipport {}:{} BgpVpnId {}",
                            routerId, newNaptSwitch, externalAddress, extportNumber, proto,
                            internalIpAddress, intportnum, bgpVpnId);
                        return false;
                    }
                    LOG.debug("handleNatFlowsInNewNaptSwitch : Successfully installed a flow in Primary switch {} "
                            + "Inbound NAPT table for router {} ipport {}:{} proto {} extIpport {}:{} BgpVpnId {}",
                        newNaptSwitch, routerId, internalIpAddress,
                        intportnum, proto, externalAddress, extportNumber, bgpVpnId);
                    //Install the flow in newNaptSwitch Outbound NAPT table.
                    try {
                        naptEventHandler.buildAndInstallNatFlows(newNaptSwitch, NwConstants.OUTBOUND_NAPT_TABLE,
                            vpnId, routerId, bgpVpnId, sourceAddress, externalAddress, proto, extGwMacAddress);
                    } catch (RuntimeException ex) {
                        LOG.error("handleNatFlowsInNewNaptSwitch : Failed to add flow in OUTBOUND_NAPT_TABLE for "
                                + "routerid {} dpnId {} ipport {}:{} proto {} extIpport {}:{} BgpVpnId {}",
                            routerId, newNaptSwitch, internalIpAddress,
                            intportnum, proto, externalAddress, extportNumber, bgpVpnId, ex);
                        return false;
                    }
                    LOG.debug("handleNatFlowsInNewNaptSwitch : Successfully installed a flow in Primary switch {} "
                            + "Outbound NAPT table for router {} ipport {}:{} proto {} extIpport {}:{} BgpVpnId {}",
                        newNaptSwitch, routerId, internalIpAddress,
                        intportnum, proto, externalAddress, extportNumber, bgpVpnId);
                } else {
                    LOG.error("handleNatFlowsInNewNaptSwitch : NewNaptSwitch {} gone down while installing flows "
                            + "from oldNaptswitch {}", newNaptSwitch, oldNaptSwitch);
                    return false;
                }
            }
        }
        return true;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private Uint32 getVpnIdForRouter(Uint32 routerId, Uuid networkId) {
        try {
            //getvpnId
            if (networkId == null) {
                LOG.debug("getVpnIdForRouter : network is not associated to router {}", routerId);
            } else {
                Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
                if (vpnUuid == null) {
                    LOG.debug("getVpnIdForRouter : vpn is not associated for network {} in router {}",
                            networkId, routerId);
                } else {
                    Uint32 vpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
                    if (vpnId.longValue() > 0) {
                        LOG.debug("getVpnIdForRouter : retrieved vpnId {} for router {}", vpnId, routerId);
                        return vpnId;
                    } else {
                        LOG.debug("getVpnIdForRouter : retrieved invalid vpn Id");
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("getVpnIdForRouter : Exception while retrieving vpnId for router {}", routerId, ex);
        }
        return NatConstants.INVALID_ID;
    }

    @NonNull
    public List<BucketInfo> handleGroupInNeighborSwitches(Uint64 dpnId, String routerName, Uint32 routerId,
            Uint64 naptSwitch) {
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        String ifNamePrimary;
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("handleGroupInNeighborSwitches : Invalid routerId returned for routerName {}", routerName);
            return listBucketInfo;
        }
        ifNamePrimary = getTunnelInterfaceName(dpnId, naptSwitch);
        if (ifNamePrimary != null) {
            LOG.debug("handleGroupInNeighborSwitches : TunnelInterface {} between ordinary switch {} and naptSwitch {}",
                ifNamePrimary, dpnId, naptSwitch);
            List<ActionInfo> listActionInfoPrimary =
                NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager, interfaceManager,
                        ifNamePrimary, routerId, true);
            BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
            listBucketInfo.add(bucketPrimary);
        } else {
            LOG.debug("handleGroupInNeighborSwitches : No TunnelInterface between ordinary switch {} and naptSwitch {}",
                    dpnId, naptSwitch);
        }
        return listBucketInfo;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void installSnatGroupEntry(Uint64 dpnId, List<BucketInfo> bucketInfo, String routerName) {
        GroupEntity groupEntity = null;
        try {
            Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME,
                NatUtil.getGroupIdKey(routerName));
            if (groupId != NatConstants.INVALID_ID) {
                LOG.debug(
                    "installSnatGroupEntry : install SnatMissEntry for groupId {} for dpnId {} for router {}",
                    groupId, dpnId, routerName);
                groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId.longValue(), routerName,
                    GroupTypes.GroupAll, bucketInfo);
                mdsalManager.syncInstallGroup(groupEntity);
                LOG.debug("installSnatGroupEntry : installed the SNAT to NAPT GroupEntity:{}",
                    groupEntity);
            } else {
                LOG.error("installSnatGroupEntry: Unable to obtain groupId for router:{}", routerName);
            }
        } catch (Exception ex) {
            LOG.error("installSnatGroupEntry : Failed to install group for groupEntity {}", groupEntity, ex);
        }
    }

    private void modifySnatGroupEntry(Uint64 dpnId, List<BucketInfo> bucketInfo, String routerName) {
        installSnatGroupEntry(dpnId, bucketInfo, routerName);
        LOG.debug("modifySnatGroupEntry : modified SnatMissEntry for dpnId {} of router {}", dpnId, routerName);
    }

    @Nullable
    protected String getTunnelInterfaceName(Uint64 srcDpId, Uint64 dstDpId) {
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        RpcResult<GetTunnelInterfaceNameOutput> rpcResult;

        try {
            Future<RpcResult<GetTunnelInterfaceNameOutput>> result = itmManager.getTunnelInterfaceName(
                new GetTunnelInterfaceNameInputBuilder().setSourceDpid(srcDpId).setDestinationDpid(dstDpId)
                    .setTunnelType(tunType).build());
            rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                tunType = TunnelTypeGre.class;
                result = itmManager.getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder()
                    .setSourceDpid(srcDpId)
                    .setDestinationDpid(dstDpId)
                    .setTunnelType(tunType)
                    .build());
                rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.warn("getTunnelInterfaceName : RPC Call to getTunnelInterfaceId returned with Errors {}",
                            rpcResult.getErrors());
                } else {
                    return rpcResult.getResult().getInterfaceName();
                }
                LOG.warn("getTunnelInterfaceName : RPC Call to getTunnelInterfaceId returned with Errors {}",
                        rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getTunnelInterfaceName :Exception when getting tunnel interface Id for tunnel between {} and {}",
                srcDpId, dstDpId, e);
        }
        LOG.error("getTunnelInterfaceName : Tunnel missing between dpn {}:{}", srcDpId, dstDpId);
        return null;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public boolean updateNaptSwitch(String routerName, Uint64 naptSwitchId) {
        RouterToNaptSwitch naptSwitch = new RouterToNaptSwitchBuilder().withKey(new RouterToNaptSwitchKey(routerName))
            .setPrimarySwitchId(naptSwitchId).build();
        try {
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                NatUtil.buildNaptSwitchRouterIdentifier(routerName), naptSwitch);
        } catch (Exception ex) {
            LOG.error("updateNaptSwitch : Failed to write naptSwitch {} for router {} in ds",
                naptSwitchId, routerName);
            return false;
        }
        LOG.debug("updateNaptSwitch : Successfully updated naptSwitch {} for router {} in ds",
            naptSwitchId, routerName);
        return true;
    }

    public FlowEntity buildSnatFlowEntity(Uint64 dpId, String routerName, long groupId,
                                          Uint32 routerVpnId, int addordel) {
        FlowEntity flowEntity;
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        String flowRef = getFlowRefSnat(dpId, NwConstants.PSNAT_TABLE, routerName);

        if (addordel == NatConstants.ADD_FLOW) {
            List<ActionInfo> actionsInfo = new ArrayList<>();
            Uint64 tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, natOverVxlanUtil, elanManager,
                    idManager, routerVpnId, routerName);
            actionsInfo.add(new ActionSetFieldTunnelId(tunnelId));
            LOG.debug("buildSnatFlowEntity : Setting the tunnel to the list of action infos {}", actionsInfo);
            actionsInfo.add(new ActionGroup(groupId));
            List<InstructionInfo> instructions = new ArrayList<>();
            instructions.add(new InstructionApplyActions(actionsInfo));

            flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                    NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                    NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
        } else {
            flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_SNAT_TABLE, matches, null);
        }
        return flowEntity;
    }

    public FlowEntity buildSnatFlowEntityForNaptSwitch(Uint64 dpId, String routerName,
                                                       Uint32 routerVpnId, int addordel) {
        FlowEntity flowEntity;
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        String flowRef = getFlowRefSnat(dpId, NwConstants.PSNAT_TABLE, routerName);

        if (addordel == NatConstants.ADD_FLOW) {
            List<InstructionInfo> instructions = new ArrayList<>();

            instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));

            flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
        } else {
            flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_SNAT_TABLE, matches, null);
        }
        return flowEntity;
    }

    private String getFlowRefSnat(Uint64 dpnId, short tableId, String routerID) {
        return NatConstants.SNAT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID;
    }

    protected void installSnatFlows(String routerName, Uint32 routerId, Uint64 naptSwitch, Uint32 routerVpnId,
        Uuid networkId, String vpnName, TypedReadWriteTransaction<Configuration> confTx) {

        if (routerId.equals(routerVpnId)) {
            LOG.debug("installSnatFlows : Installing flows for router with internalvpnId");
            //36 -> 46 ..Install flow forwarding packet to table46 from table36
            LOG.debug("installSnatFlows : installTerminatingServiceTblEntry in naptswitch with dpnId {} for "
                + "routerName {} with routerId {}", naptSwitch, routerName, routerId);
            externalRouterListener.installTerminatingServiceTblEntry(naptSwitch, routerName, routerId, confTx);

            //Install default flows punting to controller in table 46(OutBoundNapt table)
            LOG.debug("installSnatFlows : installOutboundMissEntry in naptswitch with dpnId {} for "
                + "routerName {} with routerId {}", naptSwitch, routerName, routerId);
            externalRouterListener.createOutboundTblEntry(naptSwitch, routerId, confTx);

            //Table 47 point to table 21 for inbound traffic
            LOG.debug("installSnatFlows : installNaptPfibEntry in naptswitch with dpnId {} for router {}",
                naptSwitch, routerId);
            externalRouterListener.installNaptPfibEntry(naptSwitch, routerId, confTx);

            //Table 47 point to group
            LOG.debug("installSnatFlows : installNaptPfibExternalOutputFlow in naptswitch with dpnId {} for router {}",
                naptSwitch, routerId);
            externalRouterListener.installNaptPfibExternalOutputFlow(routerName, routerId, naptSwitch, confTx);
        } else {
            Uuid extNetworkUuid = NatUtil.getNetworkIdFromRouterName(dataBroker, routerName);
            if (extNetworkUuid == null) {
                LOG.error("onRouterAssociatedToVpn : Unable to retrieve external network Uuid for router {}",
                        routerName);
                return;
            }
            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName,
                    extNetworkUuid);
            if (extNwProvType == null) {
                LOG.error("onRouterAssociatedToVpn : External Network Provider Type missing");
                return;
            }
            //36 -> 46 ..Install flow forwarding packet to table46 from table36
            LOG.debug("installSnatFlows : installTerminatingServiceTblEntry in naptswitch with dpnId {} for "
                + "routerName {} with BgpVpnId {}", naptSwitch, routerName, routerVpnId);
            externalRouterListener
                .installTerminatingServiceTblEntryWithUpdatedVpnId(naptSwitch, routerName, routerId,
                        routerVpnId, confTx, extNwProvType);

            //Install default flows punting to controller in table 46(OutBoundNapt table)
            LOG.debug("installSnatFlows : installOutboundMissEntry in naptswitch with dpnId {} for "
                + "routerName {} with BgpVpnId {}", naptSwitch, routerName, routerVpnId);
            externalRouterListener.createOutboundTblEntryWithBgpVpn(naptSwitch, routerId, routerVpnId, confTx);

            //Table 47 point to table 21 for inbound traffic
            LOG.debug("installSnatFlows : installNaptPfibEntry in naptswitch with dpnId {} for router {} "
                    + "with BgpVpnId {}", naptSwitch, routerId, routerVpnId);
            externalRouterListener.installNaptPfibEntryWithBgpVpn(naptSwitch, routerId, routerVpnId, confTx);
        }

        if (vpnName != null) {
            //NAPT PFIB point to FIB table for outbound traffic
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                .instance.to.vpn.id.VpnInstance vpnInstance = NatUtil.getVpnIdToVpnInstance(dataBroker, vpnName);
            if (vpnInstance == null) {
                LOG.error("NAT Service : installNaptPfibEntry vpnInstance not found for {}", vpnName);
                return;
            }
            Uint32 vpnId = vpnInstance.getVpnId();
            if (vpnName.equals(networkId.getValue())) {
                // below condition valid only for flat/vlan use-case
                boolean shouldInstallNaptPfibWithExtNetworkVpnId = true;
                Collection<Uuid> externalSubnetIds = NatUtil
                    .getExternalSubnetIdsForRouter(dataBroker, routerName);
                if (!externalSubnetIds.isEmpty()) {
                    //NAPT PFIB point to FIB table for outbound traffic - using external subnetID as vpnID.
                    for (Uuid externalSubnetId : externalSubnetIds) {
                        Uint32 externalSubnetVpnId = NatUtil
                            .getExternalSubnetVpnId(dataBroker, externalSubnetId);
                        if (externalSubnetVpnId != NatConstants.INVALID_ID) {
                            shouldInstallNaptPfibWithExtNetworkVpnId = false;
                            LOG.debug(
                                "installSnatFlows : installNaptPfibEntry fin naptswitch with dpnId {} for "
                                    + "BgpVpnId {}", naptSwitch, externalSubnetVpnId);
                            externalRouterListener
                                .installNaptPfibEntry(naptSwitch, externalSubnetVpnId, confTx);
                        }
                    }
                }
                if (vpnId != NatConstants.INVALID_ID && shouldInstallNaptPfibWithExtNetworkVpnId) {
                    //NAPT PFIB table point to FIB table for outbound traffic - using external networkID as vpnID.
                    LOG.debug(
                        "installSnatFlows : installNaptPfibEntry fin naptswitch with dpnId {} for "
                            + "BgpVpnId {}", naptSwitch, vpnId);
                    externalRouterListener.installNaptPfibEntry(naptSwitch, vpnId, confTx);
                } else if (vpnId != NatConstants.INVALID_ID) {
                    LOG.debug("installSnatFlows : Associated BgpvpnId not found for router {}",
                        routerId);
                }
            }
            //Install Fib entries for ExternalIps & program 36 -> 44
            Collection<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
            String rd = vpnInstance.getVrfId();
            for (String externalIp : externalIps) {
                removeFibEntry(rd, externalIp);
                LOG.debug("installSnatFlows : advToBgpAndInstallFibAndTsFlows in naptswitch id {} "
                    + "with vpnName {} and externalIp {}", naptSwitch, vpnName, externalIp);
                externalRouterListener.advToBgpAndInstallFibAndTsFlows(naptSwitch, NwConstants.INBOUND_NAPT_TABLE,
                    vpnName, routerId, routerName, externalIp, networkId, null /* external-router */, confTx);
                LOG.debug("installSnatFlows : Successfully added fib entries in naptswitch {} for "
                    + "router {} with external IP {}", naptSwitch, routerId, externalIp);
            }
        } else {
            LOG.debug("installSnatFlows : Associated vpnName not found for router {}", routerId);
        }
    }

    protected void bestEffortDeletion(Uint32 routerId, String routerName, Map<String, Uint32> externalIpLabel,
                                      TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        Collection<String> newExternalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
        if (externalIpsCache != null) {
            Set<String> removedExternalIps = new HashSet<>(externalIpsCache);
            removedExternalIps.removeAll(newExternalIps);
            if (removedExternalIps.isEmpty()) {
                LOG.info("bestEffortDeletion : No external Ip needed to be removed in bestEffortDeletion "
                        + "method for router {}", routerName);
                return;
            }
            Uuid networkId = NatUtil.getNetworkIdFromRouterName(dataBroker, routerName);
            String vpnName = getExtNetworkVpnName(routerName, networkId);
            if (vpnName == null) {
                LOG.error("bestEffortDeletion : Vpn is not associated to externalN/w of router {}", routerName);
                return;
            }
            Uint64 naptSwitch = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
            if (naptSwitch == null || naptSwitch.equals(Uint64.ZERO)) {
                LOG.error("bestEffortDeletion : No naptSwitch is selected for router {}", routerName);
                return;
            }
            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,routerName, networkId);
            if (extNwProvType == null) {
                return;
            }
            String gwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routerName);
            if (gwMacAddress != null) {
                LOG.debug("bestEffortDeletion : External Gateway MAC address {} found for External Router ID {}",
                        gwMacAddress, routerId);
            } else {
                LOG.error("bestEffortDeletion : No External Gateway MAC address found for External Router ID {}",
                        routerId);
                return;
            }
            if (extNwProvType == ProviderTypes.VXLAN) {
                for (String externalIp : removedExternalIps) {
                    externalRouterListener.clearBgpRoutes(externalIp, vpnName);
                    externalRouterListener.delFibTsAndReverseTraffic(naptSwitch, routerName, routerId, externalIp,
                        vpnName, networkId, NatConstants.DEFAULT_LABEL_VALUE, gwMacAddress, true, confTx);
                    LOG.debug("bestEffortDeletion : Successfully removed fib entry for externalIp {} for routerId {} "
                                    + "on NAPT switch {} ", externalIp, routerId, naptSwitch);
                }
            } else {
                if (externalIpLabel == null || externalIpLabel.isEmpty()) {
                    LOG.error("bestEffortDeletion : ExternalIpLabel map is empty for router {}", routerName);
                    return;
                }
                Uint32 label;
                for (String externalIp : removedExternalIps) {
                    if (externalIpLabel.containsKey(externalIp)) {
                        label = externalIpLabel.get(externalIp);
                        LOG.debug("bestEffortDeletion : Label {} for ExternalIp {} for router {}",
                                label, externalIp, routerName);
                    } else {
                        LOG.debug("bestEffortDeletion : Label for ExternalIp {} is not found for router {}",
                                externalIp, routerName);
                        continue;
                    }
                    externalRouterListener.clearBgpRoutes(externalIp, vpnName);
                    externalRouterListener.delFibTsAndReverseTraffic(naptSwitch, routerName, routerId, externalIp,
                            vpnName, networkId, label, gwMacAddress, true, confTx);
                    LOG.debug("bestEffortDeletion : Successfully removed fib entries in switch {} for router {} "
                            + "and externalIps {}", naptSwitch, routerId, externalIp);
                }
            }
        } else {
            LOG.debug("bestEffortDeletion : No external IP found for router {}", routerId);
        }
    }

    private void removeFibEntry(String rd, String prefix) {
        InstanceIdentifier.InstanceIdentifierBuilder<VrfEntry> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
                .child(VrfEntry.class, new VrfEntryKey(prefix));
        InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
        Optional<VrfEntry> ent;
        try {
            ent = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("removeFibEntry: Exception while reading the VrfEntry for the prefix {} rd {}", prefix, rd, e);
            return;
        }
        if (ent.isPresent()) {
            LOG.debug("removeFibEntry : Removing Fib entry rd {} prefix {}", rd, prefix);
            fibManager.removeFibEntry(rd, prefix, null, null, null);
        }
    }

    protected void subnetRegisterMapping(Routers routerEntry, Uint32 segmentId) {
        externalRouterListener.subnetRegisterMapping(routerEntry, segmentId);
    }
}

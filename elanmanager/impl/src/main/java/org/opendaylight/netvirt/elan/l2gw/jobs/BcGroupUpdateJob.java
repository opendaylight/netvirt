/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.tuple.Pair;

import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchInPort;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.elan.cache.ElanInstanceDpnsCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanRefUtil;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BcGroupUpdateJob extends DataStoreJob {

    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");

    static final Map<BigInteger, Boolean> INSTALLED_DEFAULT_FLOW = new ConcurrentHashMap<>();
    static final Map<Pair<BigInteger, IpAddress>, Boolean> INSTALLED_FLOW_FOR_TUNNEL = new ConcurrentHashMap<>();

    private final String elanName;
    private final boolean add;
    private BigInteger addedDpn;
    private L2GatewayDevice addedL2gw;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanRefUtil elanRefUtil;

    private IInterfaceManager interfaceManager;
    private IMdsalApiManager mdsalApiManager;
    private ElanInstanceDpnsCache elanInstanceDpnsCache;
    private ElanItmUtils elanItmUtils;

    public BcGroupUpdateJob(String elanName,
                            boolean add,
                            BigInteger addedDpn,
                            L2GatewayDevice addedL2gw,
                            ElanRefUtil elanRefUtil,
                            ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                            IMdsalApiManager mdsalApiManager,
                            ElanInstanceDpnsCache elanInstanceDpnsCache,
                            ElanItmUtils elanItmUtils) {
        super(ElanUtils.getBcGroupUpdateKey(elanName),
                elanRefUtil.getScheduler(), elanRefUtil.getJobCoordinator());
        this.elanName = elanName;
        this.add = add;
        this.addedDpn = addedDpn;
        this.addedL2gw = addedL2gw;
        this.elanRefUtil = elanRefUtil;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.interfaceManager = elanL2GatewayMulticastUtils.getInterfaceManager();
        this.mdsalApiManager = mdsalApiManager;
        this.elanInstanceDpnsCache = elanInstanceDpnsCache;
        this.elanItmUtils = elanItmUtils;
    }

    public void submit() {
        elanRefUtil.getElanClusterUtils().runOnlyInOwnerNode(super.jobKey, "BC Group Update Job", this);
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        Optional<ElanInstance> elanInstanceOptional =  elanRefUtil.getElanInstanceCache().get(elanName);
        if (elanInstanceOptional.isPresent()) {
            elanL2GatewayMulticastUtils.updateRemoteBroadcastGroupForAllElanDpns(elanInstanceOptional.get(), add,
                    addedDpn);
        }
        if (addedDpn != null && add) {
            installDpnDefaultFlows(elanName, addedDpn);
        } else if (addedL2gw != null && add) {
            installDpnDefaultFlows(elanName, addedL2gw);
        }
        return null;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void installDpnDefaultFlows(String elan, L2GatewayDevice device) {
        try {
            elanInstanceDpnsCache.getElanDpns().getOrDefault(elan, Collections.emptyMap()).values().forEach(dpn -> {
                installDpnDefaultFlows(elan, dpn.getDpId(), device);
            });
        } catch (NullPointerException e) {
            LOG.error("Runtime exception: Unable to install default dpn flows for elan {} and l2gateway device {}",
                    elan, device);
        } catch (Exception e) {
            LOG.error("Unable to install default dpn flows for elan {} and l2gateway device {}", elan, device);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void installDpnDefaultFlows(String elan, BigInteger dpnId) {
        try {
            ElanL2GwCacheUtils.getInvolvedL2GwDevices(elan).values().forEach(device -> {
                installDpnDefaultFlows(elan, dpnId, device);
            });
        } catch (NullPointerException e) {
            LOG.error("Runtime exception: Unable to install default dpn flows for elan {} and dpnId {}", elan, dpnId);
        } catch (Exception e) {
            LOG.error("Unable to install default dpn flows for elan {} and dpnId {}", elan, dpnId);
        }
    }

    public void installDpnDefaultFlows(String elan, BigInteger dpnId, L2GatewayDevice device) {
        String interfaceName = elanItmUtils.getExternalTunnelInterfaceName(String.valueOf(dpnId),
                device.getHwvtepNodeId());
        if (interfaceName == null) {
            return;
        }
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        if (interfaceInfo == null) {
            return;
        }
        if (INSTALLED_FLOW_FOR_TUNNEL.putIfAbsent(Pair.of(dpnId, device.getTunnelIp()), Boolean.TRUE) == null) {
            makeTunnelIngressFlow(dpnId, interfaceInfo.getPortNo(), interfaceName, interfaceInfo.getInterfaceTag());
        }
        if (INSTALLED_DEFAULT_FLOW.putIfAbsent(dpnId, Boolean.TRUE) == null) {
            setupTableMissForHandlingExternalTunnel(dpnId);
        }
        LOG.info("Installed default flows on DPN {} for TOR {} for elan {}",
            dpnId, device.getHwvtepNodeId(), elan);
    }

    private void setupTableMissForHandlingExternalTunnel(BigInteger dpId) {

        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.EXTERNAL_TUNNEL_TABLE));
        LOG.debug("mk instructions {}", mkInstructions);

        FlowEntity flowEntity = new FlowEntityBuilder()
                .setDpnId(dpId)
                .setTableId(NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL)
                .setFlowId("DHCPTableMissFlowForExternalTunnel")
                .setPriority(0)
                .setFlowName("DHCP Table Miss Flow For External Tunnel")
                .setIdleTimeOut(0)
                .setHardTimeOut(0)
                .setCookie(DhcpMConstants.COOKIE_DHCP_BASE)
                .setMatchInfoList(matches)
                .setInstructionInfoList(mkInstructions)
                .build();

        mdsalApiManager.batchedAddFlow(dpId, flowEntity);
    }

    public static String getTunnelInterfaceFlowRef(BigInteger dpnId, short tableId, String ifName) {
        return String.valueOf(dpnId) + tableId + ifName;
    }

    public void makeTunnelIngressFlow(BigInteger dpnId, long portNo, String interfaceName, int ifIndex) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        matches.add(new MatchInPort(dpnId, portNo));
        mkInstructions.add(new InstructionWriteMetadata(MetaDataUtil.getLportTagMetaData(ifIndex).or(BigInteger.ONE),
                MetaDataUtil.METADATA_MASK_LPORT_TAG_SH_FLAG));
        short tableId = NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL;
        mkInstructions.add(new InstructionGotoTable(tableId));

        String flowRef = getTunnelInterfaceFlowRef(dpnId,
                NwConstants.VLAN_INTERFACE_INGRESS_TABLE, interfaceName);
        LOG.debug("Flow ref {}", flowRef);

        FlowEntity flowEntity = new FlowEntityBuilder()
                .setDpnId(dpnId)
                .setTableId(NwConstants.VLAN_INTERFACE_INGRESS_TABLE)
                .setFlowId(flowRef)
                .setPriority(ITMConstants.DEFAULT_FLOW_PRIORITY)
                .setFlowName(interfaceName)
                .setIdleTimeOut(0)
                .setHardTimeOut(0)
                .setCookie(NwConstants.COOKIE_VM_INGRESS_TABLE)
                .setMatchInfoList(matches)
                .setInstructionInfoList(mkInstructions)
                .build();


        mdsalApiManager.batchedAddFlow(dpnId, flowEntity);
    }

    public static void updateAllBcGroups(String elanName,
                                         boolean add,
                                         BigInteger addedDpn,
                                         L2GatewayDevice addedL2gw,
                                         ElanRefUtil elanRefUtil,
                                         ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                         IMdsalApiManager mdsalApiManager,
                                         ElanInstanceDpnsCache elanInstanceDpnsCache,
                                         ElanItmUtils elanItmUtils) {
        new BcGroupUpdateJob(elanName, add, addedDpn, addedL2gw, elanRefUtil, elanL2GatewayMulticastUtils,
                mdsalApiManager, elanInstanceDpnsCache, elanItmUtils).submit();
    }
}

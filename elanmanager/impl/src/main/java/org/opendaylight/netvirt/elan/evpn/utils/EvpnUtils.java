/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.evpn.utils;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.SettableFutureCallback;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.DcGatewayIpList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.dc.gateway.ip.list.DcGatewayIp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetDpnEndpointIpsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetDpnEndpointIpsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(EvpnUtils.class);

    private final BiPredicate<String, String> isNetAttach = (var1, var2) -> (var1 == null && var2 != null);
    private final BiPredicate<String, String> isNetDetach = (var1, var2) -> (var1 != null && var2 == null);
    private final Predicate<MacEntry> isIpv4PrefixAvailable = (macEntry) -> (macEntry != null
        && macEntry.getIpPrefix() != null && macEntry.getIpPrefix().getIpv4Address() != null);
    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IInterfaceManager interfaceManager;
    private final ElanUtils elanUtils;
    private final ItmRpcService itmRpcService;
    private final JobCoordinator jobCoordinator;
    private final IBgpManager bgpManager;
    private final IVpnManager vpnManager;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public EvpnUtils(DataBroker broker, IInterfaceManager interfaceManager, ElanUtils elanUtils,
            ItmRpcService itmRpcService, IVpnManager vpnManager, IBgpManager bgpManager,
            JobCoordinator jobCoordinator, ElanInstanceCache elanInstanceCache) {
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.interfaceManager = interfaceManager;
        this.elanUtils = elanUtils;
        this.itmRpcService = itmRpcService;
        this.vpnManager = vpnManager;
        this.bgpManager = bgpManager;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
    }

    public boolean isWithdrawEvpnRT2Routes(ElanInstance original, ElanInstance update) {
        return isNetDetach.test(getEvpnNameFromElan(original), getEvpnNameFromElan(update));
    }

    public boolean isAdvertiseEvpnRT2Routes(ElanInstance original, ElanInstance update) {
        return isNetAttach.test(getEvpnNameFromElan(original), getEvpnNameFromElan(update));
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void advertiseEvpnRT2Routes(EvpnAugmentation evpnAugmentation, String elanName)  {
        if (evpnAugmentation == null || evpnAugmentation.getEvpnName() == null) {
            return;
        }
        String evpnName = evpnAugmentation.getEvpnName();
        List<MacEntry> macEntries = elanUtils.getElanMacEntries(elanName);
        if (macEntries == null || macEntries.isEmpty()) {
            LOG.trace("advertiseEvpnRT2Routes no elan mac entries found for {}", elanName);
            return;
        }
        String rd = vpnManager.getVpnRd(broker, evpnName);
        ElanInstance elanInfo = elanInstanceCache.get(elanName).orElse(null);
        macEntries.stream().filter(isIpv4PrefixAvailable).forEach(macEntry -> {
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(macEntry.getInterface());
            if (interfaceInfo == null) {
                LOG.debug("advertiseEvpnRT2Routes, interfaceInfo is null for interface {}", macEntry.getInterface());
                return;
            }
            advertisePrefix(elanInfo, rd, macEntry.getMacAddress().getValue(),
                    macEntry.getIpPrefix().getIpv4Address().getValue(),
                    interfaceInfo.getInterfaceName(), interfaceInfo.getDpId());
        });
    }

    @Nullable
    public String getEndpointIpAddressForDPN(Uint64 dpnId) {

        Future<RpcResult<GetDpnEndpointIpsOutput>> result = itmRpcService.getDpnEndpointIps(
                new GetDpnEndpointIpsInputBuilder()
                        .setSourceDpid(dpnId)
                        .build());
        RpcResult<GetDpnEndpointIpsOutput> rpcResult = null;
        try {
            rpcResult = result.get();
        } catch (InterruptedException e) {
            LOG.error("getnextHopIpFromRpcOutput : InterruptedException for dpnid {}", dpnId, e);
            return null;
        } catch (ExecutionException e) {
            LOG.error("getnextHopIpFromRpcOutput : ExecutionException for dpnid {}", dpnId, e);
            return null;
        }
        if (!rpcResult.isSuccessful()) {
            LOG.warn("RPC Call to getDpnEndpointIps returned with Errors {}", rpcResult.getErrors());
            return null;
        }

        List<IpAddress> nexthopIpList = rpcResult.getResult().getNexthopipList();
        return nexthopIpList.get(0).getIpv4Address().getValue();
    }

    public Optional<String> getGatewayMacAddressForInterface(String vpnName,
                                                                                    String ifName, String ipAddress) {
        VpnPortipToPort gwPort = vpnManager.getNeutronPortFromVpnPortFixedIp(broker, vpnName, ipAddress);
        return Optional.of(gwPort != null && gwPort.isSubnetIp()
                ? gwPort.getMacAddress()
                : interfaceManager.getInterfaceInfoFromOperationalDataStore(ifName).getMacAddress());
    }

    @Nullable
    public String getL3vpnNameFromElan(ElanInstance elanInfo) {
        if (elanInfo == null) {
            LOG.debug("getL3vpnNameFromElan :elanInfo is NULL");
            return null;
        }
        EvpnAugmentation evpnAugmentation = elanInfo.augmentation(EvpnAugmentation.class);
        return evpnAugmentation != null ? evpnAugmentation.getL3vpnName() : null;
    }

    @Nullable
    public static String getEvpnNameFromElan(ElanInstance elanInfo) {
        if (elanInfo == null) {
            LOG.debug("getEvpnNameFromElan :elanInfo is NULL");
            return null;
        }
        EvpnAugmentation evpnAugmentation = elanInfo.augmentation(EvpnAugmentation.class);
        return evpnAugmentation != null ? evpnAugmentation.getEvpnName() : null;
    }

    @Nullable
    public String getEvpnRd(ElanInstance elanInfo) {
        String evpnName = getEvpnNameFromElan(elanInfo);
        if (evpnName == null) {
            LOG.debug("getEvpnRd : evpnName is NULL for elanInfo {}", elanInfo);
            return null;
        }
        return vpnManager.getVpnRd(broker, evpnName);
    }

    public void advertisePrefix(ElanInstance elanInfo, String macAddress, String prefix,
                                 String interfaceName, Uint64 dpnId) {
        String rd = getEvpnRd(elanInfo);
        advertisePrefix(elanInfo, rd, macAddress, prefix, interfaceName, dpnId);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void advertisePrefix(ElanInstance elanInfo, String rd,
                                 String macAddress, String prefix, String interfaceName, Uint64 dpnId) {
        if (rd == null) {
            LOG.debug("advertisePrefix : rd is NULL for elanInfo {}, macAddress {}", elanInfo, macAddress);
            return;
        }
        String nextHop = getEndpointIpAddressForDPN(dpnId);
        if (nextHop == null) {
            LOG.debug("Failed to get the dpn tep ip for dpn {}", dpnId);
            return;
        }
        Uint32 vpnLabel = Uint32.ZERO;
        Uint32 l2vni = ElanUtils.getVxlanSegmentationId(elanInfo);
        Uint32 l3vni = Uint32.ZERO;
        String gatewayMacAddr = null;
        String l3VpName = getL3vpnNameFromElan(elanInfo);
        if (l3VpName != null) {
            VpnInstance l3VpnInstance = vpnManager.getVpnInstance(broker, l3VpName);
            l3vni = l3VpnInstance.getL3vni();
            Optional<String> gatewayMac = getGatewayMacAddressForInterface(l3VpName,
                    interfaceName, prefix);
            gatewayMacAddr = gatewayMac.isPresent() ? gatewayMac.get() : null;

        }
        LOG.info("Advertising routes with rd {},  macAddress {}, prefix {}, nextHop {},"
                        + " vpnLabel {}, l3vni {}, l2vni {}, gatewayMac {}", rd, macAddress, prefix, nextHop,
                vpnLabel, l3vni, l2vni, gatewayMacAddr);
        try {
            bgpManager.advertisePrefix(rd, macAddress, prefix, nextHop,
                    VrfEntryBase.EncapType.Vxlan, vpnLabel, l3vni, l2vni, gatewayMacAddr);
        } catch (Exception e) {
            LOG.error("Failed to advertisePrefix", e);
        }
    }

    public void advertisePrefix(ElanInstance elanInfo, MacEntry macEntry) {
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(macEntry.getInterface());
        if (interfaceInfo == null) {
            LOG.debug("advertisePrefix, interfaceInfo is null for interface {}", macEntry.getInterface());
            return;
        }

        if (!isIpv4PrefixAvailable.test(macEntry)) {
            LOG.debug("advertisePrefix macEntry does not have IPv4 prefix {}", macEntry);
            return;
        }
        advertisePrefix(elanInfo, macEntry.getMacAddress().getValue(),
                macEntry.getIpPrefix().getIpv4Address().getValue(),
                interfaceInfo.getInterfaceName(), interfaceInfo.getDpId());
    }

    public void withdrawEvpnRT2Routes(EvpnAugmentation evpnAugmentation, String elanName) {
        if (evpnAugmentation == null || evpnAugmentation.getEvpnName() == null) {
            LOG.trace("withdrawEvpnRT2Routes, evpnAugmentation is null");
            return;
        }

        String evpnName = evpnAugmentation.getEvpnName();
        String rd = vpnManager.getVpnRd(broker, evpnName);
        if (rd == null) {
            LOG.debug("withdrawEvpnRT2Routes : rd is null for {}", elanName);
            return;
        }
        List<MacEntry> macEntries = elanUtils.getElanMacEntries(elanName);
        if (macEntries == null || macEntries.isEmpty()) {
            LOG.debug("withdrawEvpnRT2Routes : macEntries  is empty for elan {} ", elanName);
            return;
        }
        for (MacEntry macEntry : macEntries) {
            if (!isIpv4PrefixAvailable.test(macEntry)) {
                LOG.debug("withdrawEvpnRT2Routes macEntry does not have IPv4 prefix {}", macEntry);
                continue;
            }
            String prefix = macEntry.getIpPrefix().getIpv4Address().getValue();
            LOG.info("Withdrawing routes with rd {}, prefix {}", rd, prefix);
            bgpManager.withdrawPrefix(rd, prefix);
        }
    }

    public void withdrawPrefix(ElanInstance elanInfo, String prefix) {
        String rd = getEvpnRd(elanInfo);
        if (rd == null) {
            return;
        }
        bgpManager.withdrawPrefix(rd, prefix);
    }

    public void withdrawPrefix(ElanInstance elanInfo, MacEntry macEntry) {
        if (!isIpv4PrefixAvailable.test(macEntry)) {
            LOG.debug("withdrawPrefix macEntry does not have IPv4 prefix {}", macEntry);
            return;
        }
        withdrawPrefix(elanInfo, macEntry.getIpPrefix().getIpv4Address().getValue());
    }

    public static InstanceIdentifier<ExternalTunnelList> getExternaTunnelListIdentifier() {
        return InstanceIdentifier
                .builder(ExternalTunnelList.class).build();
    }

    public Optional<ExternalTunnelList> getExternalTunnelList() {
        InstanceIdentifier<ExternalTunnelList> externalTunnelListId = getExternaTunnelListIdentifier();
        ExternalTunnelList externalTunnelList = null;
        try {
            externalTunnelList = elanUtils.read2(LogicalDatastoreType.CONFIGURATION,
                    externalTunnelListId).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getExternalTunnelList: unable to read ExternalTunnelList, exception ", e);
        }
        return Optional.ofNullable(externalTunnelList);
    }

    public static InstanceIdentifier<DcGatewayIpList> getDcGatewayIpListIdentifier() {
        return InstanceIdentifier
                .builder(DcGatewayIpList.class).build();
    }

    public Optional<DcGatewayIpList> getDcGatewayIpList() {
        InstanceIdentifier<DcGatewayIpList> dcGatewayIpListInstanceIdentifier = getDcGatewayIpListIdentifier();
        DcGatewayIpList dcGatewayIpListConfig = null;
        try {
            dcGatewayIpListConfig = elanUtils.read2(LogicalDatastoreType.CONFIGURATION,
                    dcGatewayIpListInstanceIdentifier).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getDcGatewayTunnelInterfaceNameList: unable to read DcGatewayTunnelList, exception ", e);
        }
        return Optional.ofNullable(dcGatewayIpListConfig);
    }

    public List<String> getDcGatewayTunnelInterfaceNameList() {
        final List<String> tunnelInterfaceNameList = new ArrayList<>();
        Optional<DcGatewayIpList> dcGatewayIpListOptional = getDcGatewayIpList();
        if (!dcGatewayIpListOptional.isPresent()) {
            LOG.info("No DC gateways configured while programming the l2vni table.");
            return tunnelInterfaceNameList;
        }
        List<DcGatewayIp> dcGatewayIps = dcGatewayIpListOptional.get().nonnullDcGatewayIp();

        Optional<ExternalTunnelList> externalTunnelListOptional = getExternalTunnelList();
        if (!externalTunnelListOptional.isPresent()) {
            LOG.info("No External Tunnel Configured while programming the l2vni table.");
            return tunnelInterfaceNameList;
        }
        List<ExternalTunnel> externalTunnels = externalTunnelListOptional.get().nonnullExternalTunnel();

        dcGatewayIps.forEach(dcIp -> externalTunnels
                .stream()
                .filter(externalTunnel -> externalTunnel.getDestinationDevice()
                        .contains(dcIp.getIpAddress().getIpv4Address().toString()))
                .forEach(externalTunnel -> tunnelInterfaceNameList.add(externalTunnel.getTunnelInterfaceName())));

        return tunnelInterfaceNameList;
    }

    public void bindElanServiceToExternalTunnel(String elanName, String interfaceName) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            int instructionKey = 0;
            LOG.trace("Binding external interface {} elan {}", interfaceName, elanName);
            List<Instruction> instructions = new ArrayList<>();
            instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(
                    NwConstants.L2VNI_EXTERNAL_TUNNEL_DEMUX_TABLE, ++instructionKey));
            short elanServiceIndex =
                    ServiceIndex.getIndex(NwConstants.ELAN_SERVICE_NAME, NwConstants.ELAN_SERVICE_INDEX);
            BoundServices serviceInfo = ElanUtils.getBoundServices(
                    ElanUtils.getElanServiceName(elanName, interfaceName), elanServiceIndex,
                    NwConstants.ELAN_SERVICE_INDEX, NwConstants.COOKIE_ELAN_INGRESS_TABLE, instructions);
            InstanceIdentifier<BoundServices> bindServiceId = ElanUtils.buildServiceId(interfaceName, elanServiceIndex);
            if (!tx.read(bindServiceId).get().isPresent()) {
                tx.put(bindServiceId, serviceInfo, WriteTransaction.CREATE_MISSING_PARENTS);
            }
        }), LOG, "Error binding an ELAN service to an external tunnel");
    }

    public void unbindElanServiceFromExternalTunnel(String elanName, String interfaceName) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            LOG.trace("UnBinding external interface {} elan {}", interfaceManager, elanName);
            short elanServiceIndex =
                    ServiceIndex.getIndex(NwConstants.ELAN_SERVICE_NAME, NwConstants.ELAN_SERVICE_INDEX);
            InstanceIdentifier<BoundServices> bindServiceId = ElanUtils.buildServiceId(interfaceName, elanServiceIndex);
            if (tx.read(bindServiceId).get().isPresent()) {
                tx.delete(bindServiceId);
            }
        }), LOG, "Error binding an ELAN service to an external tunnel");
    }

    private static List<InstructionInfo> getInstructionsForExtTunnelTable(Uint32 elanTag) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionWriteMetadata(ElanUtils.getElanMetadataLabel(elanTag.longValue(), false),
                ElanHelper.getElanMetadataMask()));
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_DMAC_TABLE));
        return mkInstructions;
    }

    private static String getFlowRef(long tableId, long elanTag, Uint64 dpnId) {
        return new StringBuilder().append(tableId).append(elanTag).append(dpnId.toString()).toString();
    }

    private void programEvpnL2vniFlow(ElanInstance elanInfo, BiConsumer<Uint64, FlowEntity> flowHandler) {
        Uint32 elanTag = elanInfo.getElanTag();
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(ElanUtils.getVxlanSegmentationId(elanInfo).longValue())));
        try {
            NWUtil.getOperativeDPNs(broker).forEach(dpnId -> {
                LOG.debug("Updating tunnel flow to dpnid {}", dpnId);
                List<InstructionInfo> instructions = getInstructionsForExtTunnelTable(elanTag);
                String flowRef = getFlowRef(NwConstants.L2VNI_EXTERNAL_TUNNEL_DEMUX_TABLE, elanTag.longValue(), dpnId);
                FlowEntity flowEntity = MDSALUtil.buildFlowEntity(
                        dpnId,
                        NwConstants.L2VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                        flowRef,
                        5, // prio
                        elanInfo.getElanInstanceName(), // flowName
                        0, // idleTimeout
                        0, // hardTimeout
                        Uint64.valueOf(ITMConstants.COOKIE_ITM_EXTERNAL.longValue() + elanTag.longValue()),
                        mkMatches,
                        instructions);
                flowHandler.accept(dpnId, flowEntity);
            });
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("programEvpnL2vniFlow: Exception while programming Evpn L2vni flow for elanInstance {}",
                    elanInfo, e);
        }
    }

    public void programEvpnL2vniDemuxTable(String elanName, final BiConsumer<String, String> serviceHandler,
                                           BiConsumer<Uint64, FlowEntity> flowHandler) {
        ElanInstance elanInfo = elanInstanceCache.get(elanName).orElse(null);
        List<String> tunnelInterfaceNameList = getDcGatewayTunnelInterfaceNameList();
        if (tunnelInterfaceNameList.isEmpty()) {
            LOG.info("No DC gateways tunnels while programming l2vni table for elan {}.", elanName);
            return;
        }

        tunnelInterfaceNameList.forEach(tunnelInterfaceName -> serviceHandler.accept(elanName, tunnelInterfaceName));
        programEvpnL2vniFlow(elanInfo, flowHandler);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends DataObject> void asyncReadAndExecute(final LogicalDatastoreType datastoreType,
            final InstanceIdentifier<T> iid, final String jobKey, final Function<Optional<T>, Void> function) {
        jobCoordinator.enqueueJob(jobKey, () -> {
            SettableFuture<Optional<T>> settableFuture = SettableFuture.create();
            List futures = Collections.singletonList(settableFuture);

            try (ReadTransaction tx = broker.newReadOnlyTransaction()) {
                Futures.addCallback(tx.read(datastoreType, iid),
                        new SettableFutureCallback<Optional<T>>(settableFuture) {
                            @Override
                            public void onSuccess(Optional<T> data) {
                                function.apply(data);
                                super.onSuccess(data);
                            }
                        }, MoreExecutors.directExecutor());

                return futures;
            }
        }, ElanConstants.JOB_MAX_RETRIES);
    }
}

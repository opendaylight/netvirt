/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CleanupDpnForVpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CleanupDpnForVpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CleanupDpnForVpnOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.PopulateFibOnDpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.PopulateFibOnDpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.PopulateFibOnDpnOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FibRpcServiceImpl implements FibRpcService {
    private static final Logger LOG = LoggerFactory.getLogger(FibRpcServiceImpl.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IFibManager fibManager;
    private final IVpnFootprintService vpnFootprintService;

    @Inject
    public FibRpcServiceImpl(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                             final IFibManager fibManager, final IVpnFootprintService vpnFootprintService) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.fibManager = fibManager;
        this.vpnFootprintService = vpnFootprintService;
    }

    /**
     * To install FIB routes on specified dpn with given instructions.
     */
    @Override
    public ListenableFuture<RpcResult<CreateFibEntryOutput>> createFibEntry(CreateFibEntryInput input) {

        Uint64 dpnId = input.getSourceDpid();
        String vpnName = input.getVpnName();
        String vpnRd = getVpnRd(dataBroker, vpnName);
        String ipAddress = input.getIpAddress();
        LOG.info("Create custom FIB entry - {} on dpn {} for VPN {} ", ipAddress, dpnId, vpnName);
        Map<InstructionKey, Instruction> instructionMap = input.nonnullInstruction();
        LOG.info("ADD: Adding Custom Fib Entry rd {} prefix {} label {}", vpnRd, ipAddress, input.getServiceId());
        IpAddresses.IpAddressSource ipAddressSource = IpAddresses.IpAddressSource
                .forValue(input.getIpAddressSource().getIntValue());
        vpnFootprintService.updateVpnToDpnMapping(dpnId, vpnName, vpnRd, null /* interfaceName*/,
                new ImmutablePair<>(ipAddressSource, ipAddress), true /*add*/);

        Uint32 vpnId = getVpnId(dataBroker, vpnName);
        makeLocalFibEntry(vpnId, dpnId, ipAddress, new ArrayList<Instruction>(instructionMap.values()));
        LOG.info("ADD: Added Custom Fib Entry rd {} prefix {} label {}", vpnRd, ipAddress, input.getServiceId());
        return RpcResultBuilder.success(new CreateFibEntryOutputBuilder().build()).buildFuture();
    }

    /**
     * To remove FIB/LFIB/TST routes from specified dpn.
     */
    @Override
    public ListenableFuture<RpcResult<RemoveFibEntryOutput>> removeFibEntry(RemoveFibEntryInput input) {
        Uint64 dpnId = input.getSourceDpid();
        String vpnName = input.getVpnName();
        Uint32 vpnId = getVpnId(dataBroker, vpnName);
        String vpnRd = getVpnRd(dataBroker, vpnName);

        String ipAddress = input.getIpAddress();

        LOG.info("Delete custom FIB entry - {} on dpn {} for VPN {} ", ipAddress, dpnId, vpnName);
        LOG.info("REMOVE: Removing Custom Fib Entry rd {} prefix {} label {}", vpnRd, ipAddress, input.getServiceId());
        removeLocalFibEntry(dpnId, vpnId, ipAddress);
        IpAddresses.IpAddressSource ipAddressSource = IpAddresses.IpAddressSource
                .forValue(input.getIpAddressSource().getIntValue());
        vpnFootprintService.updateVpnToDpnMapping(dpnId, vpnName, vpnRd, null /* interfaceName*/,
                new ImmutablePair<>(ipAddressSource, ipAddress), false /*add*/);
        LOG.info("REMOVE: Removed Custom Fib Entry rd {} prefix {} label {}", vpnRd, ipAddress, input.getServiceId());
        return RpcResultBuilder.success(new RemoveFibEntryOutputBuilder().build()).buildFuture();
    }


    @Override
    public ListenableFuture<RpcResult<PopulateFibOnDpnOutput>> populateFibOnDpn(PopulateFibOnDpnInput input) {
        fibManager.populateFibOnNewDpn(input.getDpid(), input.getVpnId(), input.getRd(), null);
        return RpcResultBuilder.success(new PopulateFibOnDpnOutputBuilder().build()).buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<CleanupDpnForVpnOutput>> cleanupDpnForVpn(CleanupDpnForVpnInput input) {
        fibManager.cleanUpDpnForVpn(input.getDpid(), input.getVpnId(), input.getRd(), null);
        return RpcResultBuilder.success(new CleanupDpnForVpnOutputBuilder().build()).buildFuture();
    }

    private void removeLocalFibEntry(Uint64 dpnId, Uint32 vpnId, String ipPrefix) {
        String[] values = ipPrefix.split("/");
        String ipAddress = values[0];
        int prefixLength = values.length == 1 ? 0 : Integer.parseInt(values[1]);
        LOG.debug("Removing route from DPN. ip {} masklen {}", ipAddress, prefixLength);
        InetAddress destPrefix = null;
        try {
            destPrefix = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            LOG.error("UnknowHostException in removeRoute. Failed  to remove Route for ipPrefix {} DPN {} Vpn {}",
                    ipAddress, dpnId, vpnId, e);
            return;
        }
        List<MatchInfo> matches = new ArrayList<>();

        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
            MetaDataUtil.METADATA_MASK_VRFID));

        matches.add(MatchEthernetType.IPV4);

        if (prefixLength != 0) {
            matches.add(new MatchIpv4Destination(destPrefix.getHostAddress(), Integer.toString(prefixLength)));
        }

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, vpnId, ipAddress);


        int priority = FibConstants.DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef,
            priority, flowRef, 0, 0,
            NwConstants.COOKIE_VM_FIB_TABLE, matches, null);

        mdsalManager.removeFlow(dpnId, flowEntity);

        LOG.info("FIB entry for prefix {} on dpn {} vpn {} removed successfully", ipAddress, dpnId,  vpnId);
    }

    private void makeLocalFibEntry(Uint32 vpnId, Uint64 dpnId, String ipPrefix,
                                   List<Instruction> customInstructions) {
        String[] values = ipPrefix.split("/");
        String ipAddress = values[0];
        int prefixLength = values.length == 1 ? 0 : Integer.parseInt(values[1]);
        LOG.debug("Adding route to DPN. ip {} masklen {}", ipAddress, prefixLength);
        InetAddress destPrefix = null;
        try {
            destPrefix = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            LOG.error("UnknowHostException in addRoute. Failed  to add Route for ipPrefix {} VpnId {} DPN{}",
                    ipAddress, vpnId, dpnId, e);
            return;
        }
        List<MatchInfo> matches = new ArrayList<>();

        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
            MetaDataUtil.METADATA_MASK_VRFID));

        matches.add(MatchEthernetType.IPV4);

        if (prefixLength != 0) {
            matches.add(new MatchIpv4Destination(destPrefix.getHostAddress(), Integer.toString(prefixLength)));
        }

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, vpnId, ipAddress);


        int priority = FibConstants.DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        Map<InstructionKey, Instruction> customInstructionsMap = new HashMap<InstructionKey, Instruction>();
        int instructionKey = 0;
        for (Instruction instructionObj : customInstructions) {
            customInstructionsMap.put(new InstructionKey(++instructionKey), instructionObj);
        }
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef,
            priority, flowRef, 0, 0,
            NwConstants.COOKIE_VM_FIB_TABLE, matches, customInstructionsMap);
        mdsalManager.installFlow(dpnId, flowEntity);

        LOG.debug("FIB entry for route {} on dpn {} installed successfully - flow {}", ipAddress, dpnId, flowEntity);
    }

    private String getFlowRef(Uint64 dpnId, short tableId, Uint32 id, String ipAddress) {
        String suffixToUse = "";
        if (tableId == NwConstants.INTERNAL_TUNNEL_TABLE) {
            suffixToUse = FibConstants.TST_FLOW_ID_SUFFIX;
        }
        return FibConstants.FLOWID_PREFIX + suffixToUse + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + id + NwConstants.FLOWID_SEPARATOR + ipAddress;
    }

    //TODO: Below Util methods to be removed once VpnUtil methods are exposed in api bundle
    @Nullable
    public static String getVpnRd(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        try {
            return SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION, id)
                    .map(VpnInstance::getVrfId).orElse(null);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getVpnRd: Exception while reading VpnInstance DS for the vpn {}", vpnName, e);
        }
        return null;
    }

    static InstanceIdentifier<VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }


    static Uint32 getVpnId(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        try {
            return SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION, id)
                    .map(VpnInstance::getVpnId).orElse(Uint32.ZERO);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getVpnId: Exception while reading VpnInstance DS for the vpn {}", vpnName, e);
        }
        return Uint32.ZERO;
    }
}

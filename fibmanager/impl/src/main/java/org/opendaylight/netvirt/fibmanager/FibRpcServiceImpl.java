/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static org.opendaylight.netvirt.fibmanager.FibConstants.DEFAULT_FIB_FLOW_PRIORITY;
import static org.opendaylight.netvirt.fibmanager.FibConstants.FLOWID_PREFIX;

import com.google.common.util.concurrent.Futures;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CleanupDpnForVpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.PopulateFibOnDpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
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
    public Future<RpcResult<Void>> createFibEntry(CreateFibEntryInput input) {

        BigInteger dpnId = input.getSourceDpid();
        String vpnName = input.getVpnName();
        long vpnId = getVpnId(dataBroker, vpnName);
        String vpnRd = getVpnRd(dataBroker, vpnName);
        String ipAddress = input.getIpAddress();
        LOG.info("Create custom FIB entry - {} on dpn {} for VPN {} ", ipAddress, dpnId, vpnName);
        List<Instruction> instructions = input.getInstruction();
        LOG.info("ADD: Adding Custom Fib Entry rd {} prefix {} label {}", vpnRd, ipAddress, input.getServiceId());
        makeLocalFibEntry(vpnId, dpnId, ipAddress, instructions);
        IpAddresses.IpAddressSource ipAddressSource = IpAddresses.IpAddressSource
                .forValue(input.getIpAddressSource().getIntValue());
        vpnFootprintService.updateVpnToDpnMapping(dpnId, vpnName, vpnRd, null /* interfaceName*/,
                new ImmutablePair<>(ipAddressSource, ipAddress), true /*add*/);
        LOG.info("ADD: Added Custom Fib Entry rd {} prefix {} label {}", vpnRd, ipAddress, input.getServiceId());
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    /**
     * To remove FIB/LFIB/TST routes from specified dpn.
     */
    @Override
    public Future<RpcResult<Void>> removeFibEntry(RemoveFibEntryInput input) {
        BigInteger dpnId = input.getSourceDpid();
        String vpnName = input.getVpnName();
        long vpnId = getVpnId(dataBroker, vpnName);
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
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }


    @Override
    public Future<RpcResult<Void>> populateFibOnDpn(PopulateFibOnDpnInput input) {
        fibManager.populateFibOnNewDpn(input.getDpid(), input.getVpnId(), input.getVpnInstanceName(),
                input.getRd(), null);
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    @Override
    public Future<RpcResult<Void>> cleanupDpnForVpn(CleanupDpnForVpnInput input) {
        fibManager.cleanUpDpnForVpn(input.getDpid(), input.getVpnId(), input.getVpnInstanceName(),
                input.getRd(), null);
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    private void removeLocalFibEntry(BigInteger dpnId, long vpnId, String ipPrefix) {
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

        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));

        matches.add(MatchEthernetType.IPV4);

        if (prefixLength != 0) {
            matches.add(new MatchIpv4Destination(destPrefix.getHostAddress(), Integer.toString(prefixLength)));
        }

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, vpnId, ipAddress);


        int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef,
            priority, flowRef, 0, 0,
            NwConstants.COOKIE_VM_FIB_TABLE, matches, null);

        mdsalManager.removeFlow(dpnId, flowEntity);

        LOG.info("FIB entry for prefix {} on dpn {} vpn {} removed successfully", ipAddress, dpnId,  vpnId);
    }

    private void makeLocalFibEntry(long vpnId, BigInteger dpnId, String ipPrefix,
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

        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));

        matches.add(MatchEthernetType.IPV4);

        if (prefixLength != 0) {
            matches.add(new MatchIpv4Destination(destPrefix.getHostAddress(), Integer.toString(prefixLength)));
        }

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, vpnId, ipAddress);


        int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef,
            priority, flowRef, 0, 0,
            NwConstants.COOKIE_VM_FIB_TABLE, matches, customInstructions);
        mdsalManager.installFlow(dpnId, flowEntity);

        LOG.debug("FIB entry for route {} on dpn {} installed successfully - flow {}", ipAddress, dpnId, flowEntity);
    }

    private String getFlowRef(BigInteger dpnId, short tableId, long id, String ipAddress) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + id
                + NwConstants.FLOWID_SEPARATOR + ipAddress;
    }

    //TODO: Below Util methods to be removed once VpnUtil methods are exposed in api bundle
    public static String getVpnRd(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                VpnInstance::getVrfId).orElse(null);
    }

    static InstanceIdentifier<VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }


    static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, BigInteger dpnId) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd))
            .child(VpnToDpnList.class, new VpnToDpnListKey(dpnId)).build();
    }

    static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }

    static VpnInstanceOpDataEntry getVpnInstanceOpData(String rd, long vpnId, String vpnName) {
        return new VpnInstanceOpDataEntryBuilder().setVrfId(rd).setVpnId(vpnId).setVpnInstanceName(vpnName).build();
    }

    static long getVpnId(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                VpnInstance::getVpnId).orElse(-1L);
    }
}

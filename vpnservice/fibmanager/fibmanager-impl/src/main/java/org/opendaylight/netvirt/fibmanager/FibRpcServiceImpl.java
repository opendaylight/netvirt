/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static org.opendaylight.netvirt.fibmanager.FibConstants.COOKIE_VM_FIB_TABLE;
import static org.opendaylight.netvirt.fibmanager.FibConstants.DEFAULT_FIB_FLOW_PRIORITY;
import static org.opendaylight.netvirt.fibmanager.FibConstants.FLOWID_PREFIX;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibRpcServiceImpl implements FibRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(FibRpcServiceImpl.class);
    private final IMdsalApiManager mdsalManager;
    private final DataBroker broker;
    private final IFibManager fibManager;

    public FibRpcServiceImpl(DataBroker broker, IMdsalApiManager mdsalManager, IFibManager fibManager) {
        this.broker = broker;
        this.mdsalManager = mdsalManager;
        this.fibManager = fibManager;
    }

    /**
     * to install FIB routes on specified dpn with given instructions
     *
     */
    public Future<RpcResult<Void>> createFibEntry(CreateFibEntryInput input) {

        BigInteger dpnId = input.getSourceDpid();
        String vpnName = input.getVpnName();
        long vpnId = getVpnId(broker, vpnName);
        String ipAddress = input.getIpAddress();
        LOG.info("Create custom FIB entry - {} on dpn {} for VPN {} ", ipAddress, dpnId, vpnName);
        List<Instruction> instructions = input.getInstruction();

        makeLocalFibEntry(vpnId, dpnId, ipAddress, instructions);
        updateVpnToDpnAssociation(vpnId, dpnId, ipAddress, vpnName);

        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    /**
     * to remove FIB/LFIB/TST routes from specified dpn
     *
     */
    public Future<RpcResult<Void>> removeFibEntry(RemoveFibEntryInput input) {

        BigInteger dpnId = input.getSourceDpid();
        String vpnName = input.getVpnName();
        long vpnId = getVpnId(broker, vpnName);
        String ipAddress = input.getIpAddress();

        LOG.info("Delete custom FIB entry - {} on dpn {} for VPN {} ", ipAddress, dpnId, vpnName);

        removeLocalFibEntry(dpnId, vpnId, ipAddress);
        //removeLFibTableEntry(dpnId, serviceId);
        //removeTunnelTableEntry(dpnId, serviceId);
        removeFromVpnDpnAssociation(vpnId, dpnId, ipAddress, vpnName);

        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    private void removeLocalFibEntry(BigInteger dpnId, long vpnId, String ipPrefix) {
        String values[] = ipPrefix.split("/");
        String ipAddress = values[0];
        int prefixLength = (values.length == 1) ? 0 : Integer.parseInt(values[1]);
        LOG.debug("Removing route from DPN. ip {} masklen {}", ipAddress, prefixLength);
        InetAddress destPrefix = null;
        try {
          destPrefix = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
          LOG.error("UnknowHostException in removeRoute. Failed  to remove Route for ipPrefix {}", ipAddress);
          return;
        }
        List<MatchInfo> matches = new ArrayList<>();

        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
            BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        matches.add(new MatchInfo(MatchFieldType.eth_type,
                                  new long[] { 0x0800L }));

        if(prefixLength != 0) {
            matches.add(new MatchInfo(MatchFieldType.ipv4_destination, new String[] {
                    destPrefix.getHostAddress(), Integer.toString(prefixLength) }));
        }

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, vpnId, ipAddress);


        int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef,
                                               priority, flowRef, 0, 0,
                                               COOKIE_VM_FIB_TABLE, matches, null);

        mdsalManager.removeFlow(dpnId, flowEntity);

        LOG.debug("FIB entry for route {} on dpn {} removed successfully", ipAddress, dpnId);
    }

    private void makeLocalFibEntry(long vpnId, BigInteger dpnId, String ipPrefix, List<Instruction> customInstructions) {
        String values[] = ipPrefix.split("/");
        String ipAddress = values[0];
        int prefixLength = (values.length == 1) ? 0 : Integer.parseInt(values[1]);
        LOG.debug("Adding route to DPN. ip {} masklen {}", ipAddress, prefixLength);
        InetAddress destPrefix = null;
        try {
          destPrefix = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
          LOG.error("UnknowHostException in addRoute. Failed  to add Route for ipPrefix {}", ipAddress);
          return;
        }
        List<MatchInfo> matches = new ArrayList<>();

        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
            BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        matches.add(new MatchInfo(MatchFieldType.eth_type,
                                  new long[] { 0x0800L }));

        if(prefixLength != 0) {
          matches.add(new MatchInfo(MatchFieldType.ipv4_destination, new String[] {
             destPrefix.getHostAddress(), Integer.toString(prefixLength) }));
        }

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, vpnId, ipAddress);


        int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef,
                                               priority, flowRef, 0, 0,
                                               COOKIE_VM_FIB_TABLE, matches, customInstructions);

        mdsalManager.installFlow(dpnId, flowEntity);

        LOG.debug("FIB entry for route {} on dpn {} installed successfully", ipAddress, dpnId);
    }


    private String getFlowRef(BigInteger dpnId, short tableId, long id, String ipAddress) {
        return new StringBuilder(64).append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
            .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
            .append(id).append(NwConstants.FLOWID_SEPARATOR).append(ipAddress).toString();
    }

    private synchronized void updateVpnToDpnAssociation(long vpnId, BigInteger dpnId, String ipAddr, String vpnName) {
        LOG.debug("Updating VPN to DPN list for dpn : {} for VPN: {} with ip: {}",
                dpnId, vpnName, ipAddr);
        String routeDistinguisher = getVpnRd(broker, vpnName);
        String rd = (routeDistinguisher == null) ? vpnName : routeDistinguisher;
        InstanceIdentifier<VpnToDpnList> id = getVpnToDpnListIdentifier(rd, dpnId);
        Optional<VpnToDpnList> dpnInVpn = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses
            ipAddress = new IpAddressesBuilder().setIpAddress(ipAddr).build();

        if (dpnInVpn.isPresent()) {
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance
                    .op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses.class,
                    new IpAddressesKey(ipAddr)), ipAddress);
        } else {
            MDSALUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                                    getVpnInstanceOpDataIdentifier(rd),
                                    getVpnInstanceOpData(rd, vpnId));
            VpnToDpnListBuilder vpnToDpnList = new VpnToDpnListBuilder().setDpnId(dpnId);
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                .vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses> ipAddresses =  new ArrayList<>();
            ipAddresses.add(ipAddress);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id,
                              vpnToDpnList.setIpAddresses(ipAddresses).build());
            LOG.debug("populate FIB on new dpn {} for VPN {}", dpnId, vpnName);
            fibManager.populateFibOnNewDpn(dpnId, vpnId, rd);
        }
    }

    private synchronized void removeFromVpnDpnAssociation(long vpnId, BigInteger dpnId, String ipAddr, String vpnName) {
        LOG.debug("Removing association of VPN to DPN list for dpn : {} for VPN: {} with ip: {}",
                dpnId, vpnName, ipAddr);
        String routeDistinguisher = getVpnRd(broker, vpnName);
        String rd = (routeDistinguisher == null) ? vpnName : routeDistinguisher;
        InstanceIdentifier<VpnToDpnList> id = getVpnToDpnListIdentifier(rd, dpnId);
        Optional<VpnToDpnList> dpnInVpn = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (dpnInVpn.isPresent()) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                .vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses> ipAddresses = dpnInVpn.get().getIpAddresses();
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses
                    ipAddress = new IpAddressesBuilder().setIpAddress(ipAddr).build();

            if (ipAddresses != null && ipAddresses.remove(ipAddress)) {
                if (ipAddresses.isEmpty()) {
                    List<VpnInterfaces> vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
                    if(vpnInterfaces ==null || vpnInterfaces.isEmpty()) {
                        //Clean up the dpn
                        LOG.debug("Cleaning up dpn {} from VPN {}", dpnId, vpnName);
                        MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, id);
                        fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd);
                    }
                } else {
                    delete(broker, LogicalDatastoreType.OPERATIONAL, id.child(
                        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                            .vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses.class,
                            new IpAddressesKey(ipAddr)));
                }
            }
        }
    }

    //TODO: Below Util methods to be removed once VpnUtil methods are exposed in api bundle
    public static String getVpnRd(DataBroker broker, String vpnName) {

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> id
                = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnInstance
                = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);

        String rd = null;
        if(vpnInstance.isPresent()) {
            rd = vpnInstance.get().getVrfId();
        }
        return rd;
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance>
                                                                                         getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance.class,
                        new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey(vpnName)).build();
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

    static VpnInstanceOpDataEntry getVpnInstanceOpData(String rd, long vpnId) {
        return new VpnInstanceOpDataEntryBuilder().setVrfId(rd).setVpnId(vpnId).build();
    }

    static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        tx.submit();
    }

    static long getVpnId(DataBroker broker, String vpnName) {

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> id
            = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnInstance
            = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);

        long vpnId = -1;
        if(vpnInstance.isPresent()) {
            vpnId = vpnInstance.get().getVpnId();
        }
        return vpnId;
    }

}

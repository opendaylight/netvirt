/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.DhcpAllocationPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.network.AllocationPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class DhcpAllocationPoolManager implements AutoCloseable, EventListener {
    private static final Logger LOG = LoggerFactory.getLogger(DhcpAllocationPoolManager.class);

    private DhcpAllocationPoolListener dhcpAllocationPoolListener;

    private final DataBroker dataBroker;

    private final IMdsalApiManager mdsalUtil;

    private final IdManagerService idManager;

    public DhcpAllocationPoolManager(final IMdsalApiManager mdsalApiManager, final DataBroker dataBroker,
            final IdManagerService idManager) {
        this.mdsalUtil = mdsalApiManager;
        this.dataBroker = dataBroker;
        this.idManager = idManager;
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
        dhcpAllocationPoolListener.close();
    }

    public static IpAddress convertIntToIp(int ipn) {
        String[] array = IntStream.of(24, 16, 8, 0) //
                .map(x -> (ipn >> x) & 0xFF).boxed() //
                .map(x -> String.valueOf(x)) //
                .toArray(x -> new String[x]);
        return new IpAddress(String.join(".", array).toCharArray());
    }

    public static IpAddress convertLongToIp(long ip) {
        String[] array = LongStream.of(24, 16, 8, 0) //
                .map(x -> (ip >> x) & 0xFF).boxed() //
                .map(x -> String.valueOf(x)) //
                .toArray(x -> new String[x]);
        return new IpAddress(String.join(".", array).toCharArray());
    }

    private int convertIpToInt(IpAddress ipa) {
        String[] split = String.valueOf(ipa.getValue()).split("\\.");
        Integer[] power = { 24, 16, 8, 0 };
        return IntStream.range(0, 4)//
                .map(x -> (Integer.valueOf(split[x]) << power[x]))//
                .reduce((xx, yy) -> xx ^ yy)//
                .getAsInt();
    }

    private static long convertIpToLong(IpAddress ipa) {
        String[] splitIp = String.valueOf(ipa.getValue()).split("\\.");
        long result = 0;
        for (String part: splitIp) {
            result <<= 8;
            result |= Integer.parseInt(part);
        }
        return result;
    }

    public void init() {
        dhcpAllocationPoolListener = new DhcpAllocationPoolListener(this, dataBroker);
        LOG.info("DHCP Allocation-Pool Service initialized");
    }

    public void installDhcpEntries(BigInteger dpnId, WriteTransaction tx) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, NwConstants.DHCP_TABLE, NwConstants.ADD_FLOW, mdsalUtil, tx);
    }

    public IpAddress getIpAllocation(String networkId, AllocationPool pool, String macAddress, IpAddress requestedIp) {
        String poolIdKey = getPoolKeyIdByAllocationPool(networkId, pool);
        long allocatedIp = createIdAllocation(poolIdKey, macAddress);
        if (allocatedIp != 0) {
            return convertLongToIp(allocatedIp);
        } else {
            return null;
        }
    }

    public Optional<AllocationPool> getAllocationPoolByNetwork(String networkId) {
        InstanceIdentifier<Network> network = InstanceIdentifier.builder(DhcpAllocationPool.class)
                .child(Network.class, new NetworkKey(networkId)).build();
        Optional<Network> optionalNetworkConfData = MDSALDataStoreUtils.read(dataBroker,
                LogicalDatastoreType.CONFIGURATION, network);
        if (!optionalNetworkConfData.isPresent()) {
            LOG.info("No network configuration data for network {}", networkId);
            return Optional.absent();
        }
        Network NetworkConfData = optionalNetworkConfData.get();
        List<AllocationPool> allocationPoolList = NetworkConfData.getAllocationPool();
        if (allocationPoolList != null) {
            // if network has allocation pool list - get the first element
            // as we have no info about a specific subnet
            return Optional.of(allocationPoolList.get(0));
        } else {
            return Optional.absent();
        }
    }

    public Map<BigInteger, List<String>> getElanDpnInterfacesByName(DataBroker broker, String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnIfacesIid = InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
        Optional<ElanDpnInterfacesList> elanDpnIfacesOpc = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                elanDpnIfacesIid);
        if (!elanDpnIfacesOpc.isPresent()) {
            LOG.warn("Could not find DpnInterface for elan {}", elanInstanceName);
            return null;
        }

        return elanDpnIfacesOpc.get().getDpnInterfaces().stream()
                .collect(Collectors.toMap(x -> x.getDpId(), x -> x.getInterfaces()));
    }

    public Optional<AllocationPool> getAllocationPoolByPort(String portUuid) {
        String elanInstanceName = getNetworkByPort(portUuid);
        if (elanInstanceName != null) {
            return getAllocationPoolByNetwork(elanInstanceName);
        } else {
            return Optional.absent();
        }
    }

    public String getNetworkByPort(String portUuid) {
        InstanceIdentifier<ElanInterface> elanInterfaceName = InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(portUuid)).build();
        Optional<ElanInterface> optionalElanInterface = MDSALDataStoreUtils.read(dataBroker,
                LogicalDatastoreType.CONFIGURATION, elanInterfaceName);
        if (!optionalElanInterface.isPresent()) {
            LOG.info("No elan interface data for port {}", portUuid);
            return null;
        }
        ElanInterface elanInterface = optionalElanInterface.get();
        String elanInstanceName = elanInterface.getElanInstanceName();
        return elanInstanceName;
    }

    protected long createIdAllocation(String groupIdKey, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(groupIdKey).setIdKey(idKey)
            .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.trace("Failed to allocate id for DHCP Allocation Pool Service", e);
        }
        return 0;
    }

    public void createIdAllocationPool(String networkId, AllocationPool pool) {
        String poolName = getPoolKeyIdByAllocationPool(networkId, pool);
        long low = convertIpToLong(pool.getAllocateFrom());
        long high = convertIpToLong(pool.getAllocateTo());
        
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(poolName).setLow(low).setHigh(high).build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if ((result != null) && (result.get().isSuccessful())) {
                LOG.debug("DHCP Allocation Pool Service : Created GroupIdPool");
            } else {
                LOG.error("DHCP Allocation Pool Service : Unable to create GroupIdPool");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create Pool for DHCP Allocation Pool Service", e);
        }
    }

    private String getPoolKeyIdByAllocationPool(String networkId, AllocationPool pool) {
        String poolName = "dhcpAllocationPool." + networkId + "." + String.valueOf(pool.getSubnet().getValue());
        return poolName;
    }

}

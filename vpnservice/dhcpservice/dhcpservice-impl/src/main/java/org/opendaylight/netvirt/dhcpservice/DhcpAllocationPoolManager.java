/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.DhcpAllocationPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.network.AllocationPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpAllocationPoolManager implements AutoCloseable, EventListener {
    private static final Logger LOG = LoggerFactory.getLogger(DhcpAllocationPoolManager.class);

    private DhcpAllocationPoolListener dhcpAllocationPoolListener;

    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final DhcpserviceConfig config;
    private final JobCoordinator jobCoordinator;

    @Inject
    public DhcpAllocationPoolManager(final DataBroker dataBroker, final IdManagerService idManager,
            final DhcpserviceConfig config, final JobCoordinator jobCoordinator) {
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.config = config;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void init() {
        if (config.isDhcpDynamicAllocationPoolEnabled()) {
            dhcpAllocationPoolListener = new DhcpAllocationPoolListener(this, dataBroker, jobCoordinator);
            LOG.info("DHCP Allocation Pool Service initialized");
        }
    }

    @Override
    @PreDestroy
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
        if (dhcpAllocationPoolListener != null) {
            dhcpAllocationPoolListener.close();
        }
    }

    public IpAddress getIpAllocation(String networkId, AllocationPool pool, String macAddress) {
        String poolIdKey = getPoolKeyIdByAllocationPool(networkId, pool);
        long allocatedIpLong = createIdAllocation(poolIdKey, macAddress);
        LOG.debug("allocated id {} for mac {}, from pool {}", allocatedIpLong, macAddress, poolIdKey);
        IpAddress allocatedIpAddress = allocatedIpLong != 0 ? DhcpServiceUtils.convertLongToIp(allocatedIpLong)
                : null;
        return allocatedIpAddress;
    }

    public void releaseIpAllocation(String networkId, AllocationPool pool, String macAddress) {
        String poolIdKey = getPoolKeyIdByAllocationPool(networkId, pool);
        LOG.debug("going to release id for mac {}, from pool {}", macAddress, poolIdKey);
        releaseIdAllocation(poolIdKey, macAddress);
    }

    public AllocationPool getAllocationPoolByNetwork(String networkId) {
        InstanceIdentifier<Network> network = InstanceIdentifier.builder(DhcpAllocationPool.class)
                .child(Network.class, new NetworkKey(networkId)).build();
        Optional<Network> optionalNetworkConfData = MDSALDataStoreUtils.read(dataBroker,
                LogicalDatastoreType.CONFIGURATION, network);
        if (!optionalNetworkConfData.isPresent()) {
            LOG.info("No network configuration data for network {}", networkId);
            return null;
        }
        Network networkConfData = optionalNetworkConfData.get();
        List<AllocationPool> allocationPoolList = networkConfData.getAllocationPool();
        // if network has allocation pool list - get the first element
        // as we have no info about a specific subnet
        if (allocationPoolList != null && !allocationPoolList.isEmpty()) {
            return allocationPoolList.get(0);
        } else {
            LOG.warn("No allocation pools for network {}", networkId);
            return null;
        }
    }

    public Map<BigInteger, List<String>> getElanDpnInterfacesByName(DataBroker broker, String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnIfacesIid = InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
        Optional<ElanDpnInterfacesList> elanDpnIfacesOpc = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                elanDpnIfacesIid);
        if (!elanDpnIfacesOpc.isPresent()) {
            LOG.warn("Could not find DpnInterfaces for elan {}", elanInstanceName);
            return null;
        }

        return elanDpnIfacesOpc.get().getDpnInterfaces().stream()
                .collect(Collectors.toMap(DpnInterfaces::getDpId, DpnInterfaces::getInterfaces));
    }

    public AllocationPool getAllocationPoolByPort(String portUuid) {
        String elanInstanceName = getNetworkByPort(portUuid);
        AllocationPool allocPool = elanInstanceName != null ? getAllocationPoolByNetwork(elanInstanceName) : null;
        return allocPool;
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
        return elanInterface.getElanInstanceName();
    }

    private String getPoolKeyIdByAllocationPool(String networkId, AllocationPool pool) {
        return "dhcpAllocationPool." + networkId + "." + String.valueOf(pool.getSubnet().getValue());
    }

    private long createIdAllocation(String groupIdKey, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(groupIdKey).setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.trace("Failed to allocate id for DHCP Allocation Pool Service", e);
        }
        return 0;
    }

    private void releaseIdAllocation(String groupIdKey, String idKey) {
        ReleaseIdInput getIdInput = new ReleaseIdInputBuilder().setPoolName(groupIdKey).setIdKey(idKey).build();
        idManager.releaseId(getIdInput);
    }

    protected void createIdAllocationPool(String networkId, AllocationPool pool) {
        String poolName = getPoolKeyIdByAllocationPool(networkId, pool);
        long low = DhcpServiceUtils.convertIpToLong(pool.getAllocateFrom());
        long high = DhcpServiceUtils.convertIpToLong(pool.getAllocateTo());
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder().setPoolName(poolName).setLow(low).setHigh(high)
                .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.info("DHCP Allocation Pool Service : Created IdPool name {}", poolName);
            } else {
                LOG.error("DHCP Allocation Pool Service : Unable to create IdPool name {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create Pool for DHCP Allocation Pool Service", e);
        }
    }

    protected void releaseIdAllocationPool(String networkId, AllocationPool pool) {
        String poolName = getPoolKeyIdByAllocationPool(networkId, pool);
        DeleteIdPoolInput deletePool = new DeleteIdPoolInputBuilder().setPoolName(poolName).build();
        try {
            Future<RpcResult<Void>> result = idManager.deleteIdPool(deletePool);
            if (result != null && result.get().isSuccessful()) {
                LOG.info("DHCP Allocation Pool Service : Deleted IdPool name {}", poolName);
            } else {
                LOG.error("DHCP Allocation Pool Service : Unable to delete IdPool name {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to delete Pool for DHCP Allocation Pool Service", e);
        }
    }
}

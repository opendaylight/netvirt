/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpAllocationPoolAddJob;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpAllocationPoolRemoveJob;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.DhcpAllocationPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.network.AllocationPool;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpAllocationPoolListener extends AbstractAsyncDataTreeChangeListener<AllocationPool> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpAllocationPoolListener.class);

    private final DhcpAllocationPoolManager dhcpAllocationPoolManager;
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final JobCoordinator jobCoordinator;

    public DhcpAllocationPoolListener(final DhcpAllocationPoolManager dhcpAllocationPoolManager,
            final DataBroker dataBroker, final JobCoordinator jobCoordinator) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(DhcpAllocationPool.class)
                .child(Network.class).child(AllocationPool.class),
                Executors.newListeningSingleThreadExecutor("DhcpAllocationPoolListener", LOG));
        this.dhcpAllocationPoolManager = dhcpAllocationPoolManager;
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.jobCoordinator = jobCoordinator;
        LOG.info("DhcpAllocationPoolListener initialized");
    }

    @Override
    public void add(InstanceIdentifier<AllocationPool> key, AllocationPool dataObjectModification) {
        String networkId = key.firstKeyOf(Network.class).getNetworkId();
        dhcpAllocationPoolManager.createIdAllocationPool(networkId, dataObjectModification);
        Map<Uint64, List<String>> elanDpnInterfacesByName =
            dhcpAllocationPoolManager.getElanDpnInterfacesByName(dataBroker, networkId);
        for (Entry<Uint64, List<String>> entry : elanDpnInterfacesByName.entrySet()) {
            BigInteger dpnId = entry.getKey().toJava();
            for (String interfaceName : entry.getValue()) {
                LOG.debug("Install Dhcp Entries for dpId: {} interface : {}", dpnId, interfaceName);
                DhcpAllocationPoolAddJob job = new DhcpAllocationPoolAddJob(txRunner, interfaceName);
                jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job,
                        DhcpMConstants.RETRY_COUNT);
            }
        }
    }

    @Override
    public void remove(InstanceIdentifier<AllocationPool> key, AllocationPool dataObjectModification) {
        String networkId = key.firstKeyOf(Network.class).getNetworkId();
        dhcpAllocationPoolManager.releaseIdAllocationPool(networkId, dataObjectModification);
        Map<Uint64, List<String>> elanDpnInterfacesByName =
            dhcpAllocationPoolManager.getElanDpnInterfacesByName(dataBroker, networkId);
        elanDpnInterfacesByName.values().forEach(interfaceNames -> interfaceNames.forEach(interfaceName -> {
            DhcpAllocationPoolRemoveJob job = new DhcpAllocationPoolRemoveJob(txRunner, interfaceName);
            jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job,
                    DhcpMConstants.RETRY_COUNT);
        }));
    }

    @Override
    public void update(InstanceIdentifier<AllocationPool> key, AllocationPool dataObjectModificationBefore,
            AllocationPool dataObjectModificationAfter) {
        // TODO Auto-generated method stub

    }
}

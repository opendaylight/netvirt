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
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpAllocationPoolAddJob;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpAllocationPoolRemoveJob;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.DhcpAllocationPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.network.AllocationPool;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpAllocationPoolListener
        extends AsyncDataTreeChangeListenerBase<AllocationPool, DhcpAllocationPoolListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpAllocationPoolListener.class);

    private final DhcpAllocationPoolManager dhcpAllocationPoolManager;
    private final DataBroker dataBroker;
    private final JobCoordinator jobCoordinator;

    public DhcpAllocationPoolListener(final DhcpAllocationPoolManager dhcpAllocationPoolManager,
            final DataBroker dataBroker, final JobCoordinator jobCoordinator) {
        super(AllocationPool.class, DhcpAllocationPoolListener.class);
        this.dhcpAllocationPoolManager = dhcpAllocationPoolManager;
        this.dataBroker = dataBroker;
        this.jobCoordinator = jobCoordinator;
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        LOG.info("DhcpAllocationPoolListener initialized");
    }

    @Override
    protected void add(InstanceIdentifier<AllocationPool> key, AllocationPool dataObjectModification) {
        String networkId = key.firstKeyOf(Network.class).getNetworkId();
        dhcpAllocationPoolManager.createIdAllocationPool(networkId, dataObjectModification);
        Map<BigInteger, List<String>> elanDpnInterfacesByName = getDpnInterfacesByNetwork(networkId);
        for (BigInteger dpnId : elanDpnInterfacesByName.keySet()) {
            for (String interfaceName : elanDpnInterfacesByName.get(dpnId)) {
                LOG.debug("Install Dhcp Entries for dpId: {} interface : {}", dpnId, interfaceName);
                DhcpAllocationPoolAddJob job = new DhcpAllocationPoolAddJob(dataBroker,
                        interfaceName);
                jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job,
                        DhcpMConstants.RETRY_COUNT);
            }
        }
    }

    @Override
    protected DhcpAllocationPoolListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected InstanceIdentifier<AllocationPool> getWildCardPath() {
        return InstanceIdentifier.builder(DhcpAllocationPool.class)//
                .child(Network.class).child(AllocationPool.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<AllocationPool> key, AllocationPool dataObjectModification) {
        String networkId = key.firstKeyOf(Network.class).getNetworkId();
        dhcpAllocationPoolManager.releaseIdAllocationPool(networkId, dataObjectModification);
        Map<BigInteger, List<String>> elanDpnInterfacesByName = getDpnInterfacesByNetwork(networkId);
        for (BigInteger dpnId : elanDpnInterfacesByName.keySet()) {
            for (String interfaceName : elanDpnInterfacesByName.get(dpnId)) {
                DhcpAllocationPoolRemoveJob job = new DhcpAllocationPoolRemoveJob(dataBroker,
                        interfaceName);
                jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job,
                        DhcpMConstants.RETRY_COUNT);
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<AllocationPool> key, AllocationPool dataObjectModificationBefore,
            AllocationPool dataObjectModificationAfter) {
        // TODO Auto-generated method stub

    }

    private Map<BigInteger, List<String>> getDpnInterfacesByNetwork(String networkId) {
        Map<BigInteger, List<String>> elanDpnInterfacesByName = dhcpAllocationPoolManager
                .getElanDpnInterfacesByName(dataBroker, networkId);
        return elanDpnInterfacesByName;
    }
}

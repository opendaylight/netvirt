/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.l2gw;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The listener class for ITM transport zone updates.
 */
@Singleton
public class L2GwTransportZoneListener extends AbstractAsyncDataTreeChangeListener<TransportZone> {
    private static final Logger LOG = LoggerFactory.getLogger(L2GwTransportZoneListener.class);
    private final DataBroker dataBroker;
    private final ItmRpcService itmRpcService;
    private final JobCoordinator jobCoordinator;
    private final L2GatewayCache l2GatewayCache;

    /**
     * Instantiates a new l2 gw transport zone listener.
     *
     * @param dataBroker the data broker
     * @param itmRpcService the itm rpc service
     */
    @Inject
    public L2GwTransportZoneListener(final DataBroker dataBroker, final ItmRpcService itmRpcService,
            final JobCoordinator jobCoordinator, final L2GatewayCache l2GatewayCache) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(TransportZones.class)
                .child(TransportZone.class), Executors.newSingleThreadExecutor("L2GwTransportZoneListener", LOG));
        this.dataBroker = dataBroker;
        this.itmRpcService = itmRpcService;
        this.jobCoordinator = jobCoordinator;
        this.l2GatewayCache = l2GatewayCache;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#remove(org.opendaylight.yangtools.yang.
     * binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    public void remove(InstanceIdentifier<TransportZone> key, TransportZone dataObjectModification) {
        // do nothing
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#update(org.opendaylight.yangtools.yang.
     * binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    public void update(InstanceIdentifier<TransportZone> key, TransportZone dataObjectModificationBefore,
                          TransportZone dataObjectModificationAfter) {
        // do nothing
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#add(org.opendaylight.yangtools.yang.
     * binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    public void add(InstanceIdentifier<TransportZone> key, TransportZone tzNew) {
        LOG.trace("Received Transport Zone Add Event: {}", tzNew);
        if (TunnelTypeVxlan.class.equals(tzNew.getTunnelType())) {
            AddL2GwDevicesToTransportZoneJob job =
                    new AddL2GwDevicesToTransportZoneJob(itmRpcService, tzNew, l2GatewayCache);
            jobCoordinator.enqueueJob(job.getJobKey(), job);
        }
    }

}

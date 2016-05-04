/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.listeners;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.itm.confighelpers.HwVtep;
import org.opendaylight.vpnservice.itm.confighelpers.ItmMonitorIntervalWorker;
import org.opendaylight.vpnservice.itm.confighelpers.ItmMonitorToggleWorker;
import org.opendaylight.vpnservice.itm.confighelpers.ItmTepAddWorker;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.TunnelMonitorEnabled;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.TunnelMonitorInterval;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.DeviceVteps;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TunnelMonitorIntervalListener  extends AsyncDataTreeChangeListenerBase<TunnelMonitorInterval, TunnelMonitorIntervalListener>
                implements  AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelMonitorIntervalListener.class);
    private ListenerRegistration<DataChangeListener> monitorIntervalListenerRegistration;
    private final DataBroker broker;

    public TunnelMonitorIntervalListener(DataBroker db) {
        super(TunnelMonitorInterval.class, TunnelMonitorIntervalListener.class);
        broker = db;
    }

    @Override protected InstanceIdentifier<TunnelMonitorInterval> getWildCardPath() {
        return InstanceIdentifier.create(TunnelMonitorInterval.class);
    }

    @Override
    protected void remove(InstanceIdentifier<TunnelMonitorInterval> key, TunnelMonitorInterval dataObjectModification) {
        LOG.debug("remove TunnelMonitorIntervalListener called with {}",dataObjectModification.getInterval());
        List<HwVtep> hwVteps = new ArrayList<HwVtep>();
        Boolean hwVtepsExist = false;
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InstanceIdentifier<TransportZones> path = InstanceIdentifier.builder(TransportZones.class).build();
        Optional<TransportZones> tZonesOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, broker);
        if (tZonesOptional.isPresent()) {
            TransportZones tZones = tZonesOptional.get();
            for (TransportZone tzone : tZones.getTransportZone()) {
              /*  hwVtepsExist = false;
                hwVteps = new ArrayList<HwVtep>();
                if (tzone.getSubnets() != null && !tzone.getSubnets().isEmpty()) {
                    for (Subnets sub : tzone.getSubnets()) {
                        if (sub.getDeviceVteps() != null && !sub.getDeviceVteps().isEmpty()) {
                            hwVtepsExist = true;
                            LOG.debug("Remove:Calling TunnelMonitorIntervalWorker with tzone = {} and hwtepExist",tzone.getZoneName());
                            for (DeviceVteps deviceVtep : sub.getDeviceVteps()) {
                                HwVtep hwVtep = ItmUtils.createHwVtepObject(deviceVtep.getTopologyId(), deviceVtep.getNodeId(),
                                                deviceVtep.getIpAddress(), sub.getPrefix(), sub.getGatewayIp(), sub.getVlanId(),
                                                tzone.getTunnelType(), tzone);
                                hwVteps.add(hwVtep);
                            }
                        }
                    }
                }*/
                //if you remove configuration, the last configured interval is only set i.e no change
                LOG.debug("Remove:Calling TunnelMonitorIntervalWorker with tzone = {} and {}",tzone.getZoneName(),dataObjectModification.getInterval());
                ItmMonitorIntervalWorker toggleWorker = new ItmMonitorIntervalWorker(hwVteps, tzone.getZoneName(),
                                dataObjectModification.getInterval(), broker, hwVtepsExist);
                coordinator.enqueueJob(tzone.getZoneName(), toggleWorker);
            }
        }
    }

    @Override protected void update(InstanceIdentifier<TunnelMonitorInterval> key,
                    TunnelMonitorInterval dataObjectModificationBefore,
                    TunnelMonitorInterval dataObjectModificationAfter) {
        LOG.debug("update TunnelMonitorIntervalListener called with {}",dataObjectModificationAfter.getInterval());
        List<HwVtep> hwVteps = new ArrayList<HwVtep>();
        Boolean hwVtepsExist = false;
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InstanceIdentifier<TransportZones> path = InstanceIdentifier.builder(TransportZones.class).build();
        Optional<TransportZones> tZonesOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, broker);
        if (tZonesOptional.isPresent()) {
            TransportZones tZones = tZonesOptional.get();
            for (TransportZone tzone : tZones.getTransportZone()) {
                /*hwVtepsExist = false;
                hwVteps = new ArrayList<HwVtep>();
                if (tzone.getSubnets() != null && !tzone.getSubnets().isEmpty()) {
                    for (Subnets sub : tzone.getSubnets()) {
                        if (sub.getDeviceVteps() != null && !sub.getDeviceVteps().isEmpty()) {
                            hwVtepsExist = true;//gets set to true only if this particular tzone has
                            LOG.debug("Update:Calling TunnelMonitorIntervalWorker with tzone = {} and hwtepExist",tzone.getZoneName());
                            for (DeviceVteps deviceVtep : sub.getDeviceVteps()) {
                                HwVtep hwVtep = ItmUtils.createHwVtepObject(deviceVtep.getTopologyId(), deviceVtep.getNodeId(),
                                                deviceVtep.getIpAddress(), sub.getPrefix(), sub.getGatewayIp(), sub.getVlanId(),
                                                tzone.getTunnelType(), tzone);
                                hwVteps.add(hwVtep);
                            }
                        }
                    }
                }*/
                LOG.debug("Update:Calling TunnelMonitorIntervalWorker with tzone = {} and {}",tzone.getZoneName(),dataObjectModificationAfter.getInterval());
                ItmMonitorIntervalWorker intervalWorker = new ItmMonitorIntervalWorker(hwVteps, tzone.getZoneName(),
                                dataObjectModificationAfter.getInterval(), broker, hwVtepsExist);
                coordinator.enqueueJob(tzone.getZoneName(), intervalWorker);
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<TunnelMonitorInterval> key, TunnelMonitorInterval dataObjectModification) {
        LOG.debug("Add TunnelMonitorIntervalListener called with {}",dataObjectModification.getInterval());
        List<HwVtep> hwVteps = new ArrayList<HwVtep>();
        Boolean hwVtepsExist = false;
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InstanceIdentifier<TransportZones> path = InstanceIdentifier.builder(TransportZones.class).build();
        Optional<TransportZones> tZonesOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, broker);
        if (tZonesOptional.isPresent()) {
            TransportZones tZones = tZonesOptional.get();
            for (TransportZone tzone : tZones.getTransportZone()) {
                /*hwVtepsExist = false;
                hwVteps = new ArrayList<HwVtep>();
                if (tzone.getSubnets() != null && !tzone.getSubnets().isEmpty()) {
                    for (Subnets sub : tzone.getSubnets()) {
                        if (sub.getDeviceVteps() != null && !sub.getDeviceVteps().isEmpty()) {
                            hwVtepsExist = true;
                            LOG.debug("Add:Calling TunnelMonitorIntervalWorker with tzone = {} and hwtepExist",tzone.getZoneName());
                            for (DeviceVteps deviceVtep : sub.getDeviceVteps()) {
                                HwVtep hwVtep = ItmUtils.createHwVtepObject(deviceVtep.getTopologyId(), deviceVtep.getNodeId(),
                                                deviceVtep.getIpAddress(), sub.getPrefix(), sub.getGatewayIp(), sub.getVlanId(),
                                                tzone.getTunnelType(), tzone);
                                hwVteps.add(hwVtep);
                            }
                        }
                    }
                }*/
                LOG.debug("Add:Calling TunnelMonitorIntervalWorker with tzone = {} and {}",tzone.getZoneName(),dataObjectModification.getInterval());
                ItmMonitorIntervalWorker intervalWorker = new ItmMonitorIntervalWorker(hwVteps, tzone.getZoneName(),
                                dataObjectModification.getInterval(), broker, hwVtepsExist);
                //conversion to milliseconds done while writing to i/f-mgr config DS
                coordinator.enqueueJob(tzone.getZoneName(), intervalWorker);
            }
        }
    }

    @Override protected TunnelMonitorIntervalListener getDataTreeChangeListener() {
        return TunnelMonitorIntervalListener.this;
    }
}

/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.itm.listeners;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZonesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.itm.impl.ITMManager;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.vpnservice.itm.confighelpers.ItmTepAddWorker ;
import org.opendaylight.vpnservice.itm.confighelpers.ItmTepRemoveWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class listens for interface creation/removal/update in Configuration DS.
 * This is used to handle interfaces for base of-ports.
 */
public class TransportZoneListener extends AsyncDataTreeChangeListenerBase<TransportZone, TransportZoneListener> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(TransportZoneListener.class);
    private DataBroker dataBroker;
    private IdManagerService idManagerService;
    private IMdsalApiManager mdsalManager;
    private ITMManager itmManager;

    public TransportZoneListener(final DataBroker dataBroker, final IdManagerService idManagerService) {
        super(TransportZone.class, TransportZoneListener.class);
        this.dataBroker = dataBroker;
        this.idManagerService = idManagerService;
        initializeTZNode(dataBroker);
    }

    public void setItmManager(ITMManager itmManager) {
        this.itmManager = itmManager;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    private void initializeTZNode(DataBroker db) {
        ReadWriteTransaction transaction = db.newReadWriteTransaction();
        InstanceIdentifier<TransportZones> path = InstanceIdentifier.create(TransportZones.class);
        CheckedFuture<Optional<TransportZones>, ReadFailedException> tzones =
                        transaction.read(LogicalDatastoreType.CONFIGURATION,path);
        try {
            if (!tzones.get().isPresent()) {
                TransportZonesBuilder tzb = new TransportZonesBuilder();
                transaction.put(LogicalDatastoreType.CONFIGURATION,path,tzb.build());
                transaction.submit();
            } else {
                transaction.cancel();
            }
        } catch (Exception e) {
            LOG.error("Error initializing TransportZones {}",e);
        }
    }

    @Override
    public void close() throws Exception {
        LOG.info("tzChangeListener Closed");
    }
    @Override
    protected InstanceIdentifier<TransportZone> getWildCardPath() {
        return InstanceIdentifier.create(TransportZones.class).child(TransportZone.class);
    }

    @Override
    protected TransportZoneListener getDataTreeChangeListener() {
        return TransportZoneListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<TransportZone> key, TransportZone tzOld) {
        LOG.debug("Received Transport Zone Remove Event: {}, {}", key, tzOld);
        List<DPNTEPsInfo> opDpnList = createDPNTepInfo(tzOld);
        LOG.trace("Delete: Invoking deleteTunnels in ItmManager with DpnList {}", opDpnList);
        if(opDpnList.size()>0) {
            LOG.trace("Delete: Invoking ItmManager");
           // itmManager.deleteTunnels(opDpnList);
            DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
            ItmTepRemoveWorker removeWorker = new ItmTepRemoveWorker(opDpnList, dataBroker, idManagerService, mdsalManager);
            coordinator.enqueueJob(tzOld.getZoneName(), removeWorker);
        }
    }

    @Override
    protected void update(InstanceIdentifier<TransportZone> key, TransportZone tzOld, TransportZone tzNew) {
        LOG.debug("Received Transport Zone Update Event: {}, {}, {}", key, tzOld, tzNew);
        if( !(tzOld.equals(tzNew))) {
           add(key, tzNew);
        }
    }

    @Override
    protected void add(InstanceIdentifier<TransportZone> key, TransportZone tzNew) {
        LOG.debug("Received Transport Zone Add Event: {}, {}", key, tzNew);
        List<DPNTEPsInfo> opDpnList = createDPNTepInfo(tzNew);
        LOG.trace("Add: Operational dpnTepInfo - Before invoking ItmManager {}", opDpnList);
        if(opDpnList.size()>0) {
          LOG.trace("Add: Invoking ItmManager with DPN List {} " , opDpnList);
          //itmManager.build_all_tunnels(opDpnList);
          DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
          ItmTepAddWorker addWorker = new ItmTepAddWorker(opDpnList,dataBroker, idManagerService, mdsalManager);
          coordinator.enqueueJob(tzNew.getZoneName(), addWorker);
      }
    }

    private List<DPNTEPsInfo> createDPNTepInfo(TransportZone transportZone){

        Map<BigInteger, List<TunnelEndPoints>> mapDPNToTunnelEndpt = new ConcurrentHashMap<>();
        List<DPNTEPsInfo> dpnTepInfo = new ArrayList<DPNTEPsInfo>();
       // List<TransportZone> transportZoneList = transportZones.getTransportZone();
       // for(TransportZone transportZone : transportZoneList) {
            String zone_name = transportZone.getZoneName();
            Class<? extends TunnelTypeBase> tunnel_type = transportZone.getTunnelType();
            LOG.trace("Transport Zone_name: {}", zone_name);
            List<Subnets> subnetsList = transportZone.getSubnets();
            if(subnetsList!=null){
                for (Subnets subnet : subnetsList) {
                    IpPrefix ipPrefix = subnet.getPrefix();
                    IpAddress gatewayIP = subnet.getGatewayIp();
                    int vlanID = subnet.getVlanId();
                    LOG.trace("IpPrefix: {}, gatewayIP: {}, vlanID: {} ", ipPrefix, gatewayIP, vlanID);
                    List<Vteps> vtepsList = subnet.getVteps();
                    for (Vteps vteps : vtepsList) {
                        BigInteger dpnID = vteps.getDpnId();
                        String port = vteps.getPortname();
                        IpAddress ipAddress = vteps.getIpAddress();
                        LOG.trace("DpnID: {}, port: {}, ipAddress: {}", dpnID, port, ipAddress);
                    TunnelEndPoints tunnelEndPoints = ItmUtils.createTunnelEndPoints(dpnID, ipAddress, port, vlanID, ipPrefix, gatewayIP, zone_name, tunnel_type);
                        List<TunnelEndPoints> tunnelEndPointsList = mapDPNToTunnelEndpt.get(dpnID);
                        if (tunnelEndPointsList != null) {
                            LOG.trace("Existing DPN info list in the Map: {} ", dpnID);
                            tunnelEndPointsList.add(tunnelEndPoints);
                        } else {
                            LOG.trace("Adding new DPN info list to the Map: {} ", dpnID);
                            tunnelEndPointsList = new ArrayList<TunnelEndPoints>();
                            tunnelEndPointsList.add(tunnelEndPoints);
                            mapDPNToTunnelEndpt.put(dpnID, tunnelEndPointsList);
                        }
                    }
                }
            }
        //}
        if(mapDPNToTunnelEndpt.size()>0){
            Set<BigInteger> keys = mapDPNToTunnelEndpt.keySet();
            LOG.trace("List of dpns in the Map: {} ", keys);
            for(BigInteger key: keys){
                DPNTEPsInfo newDpnTepsInfo = ItmUtils.createDPNTepInfo(key, mapDPNToTunnelEndpt.get(key));
                dpnTepInfo.add(newDpnTepsInfo);
            }
        }
        return dpnTepInfo;
    }
}
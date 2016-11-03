/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeHwvtep;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelOperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.DpnToVtepMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vtep.map.DpnToVtep;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vtep.map.DpnToVtepBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vtep.map.DpnToVtepKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TunnelInterfaceStateListener extends AsyncDataTreeChangeListenerBase<StateTunnelList,
        TunnelInterfaceStateListener> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(TunnelInterfaceStateListener.class);
    private final DataBroker dataBroker;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final ItmRpcService itmRpcService;
    private OdlInterfaceRpcService intfRpcService;
    private VpnInterfaceManager vpnInterfaceManager;
    private VpnSubnetRouteHandler vpnSubnetRouteHandler;
    protected enum UpdateRouteAction {
        ADVERTISE_ROUTE, WITHDRAW_ROUTE
    }

    protected  enum TunnelAction {
        TUNNEL_EP_ADD,
        TUNNEL_EP_DELETE,
        TUNNEL_EP_UPDATE
    }
    /**
     * Responsible for listening to tunnel interface state change
     *
     * @param dataBroker dataBroker
     * @param bgpManager bgpManager
     * @param fibManager fibManager
     * @param itmRpcService itmRpcService
     */
    public TunnelInterfaceStateListener(final DataBroker dataBroker,
                                        final IBgpManager bgpManager,
                                        final IFibManager fibManager,
                                        final ItmRpcService itmRpcService,
                                        final OdlInterfaceRpcService ifaceMgrRpcService,
                                        final VpnInterfaceManager vpnInterfaceManager,
                                        final VpnSubnetRouteHandler vpnSubnetRouteHandler) {
        super(StateTunnelList.class, TunnelInterfaceStateListener.class);
        this.dataBroker = dataBroker;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.itmRpcService = itmRpcService;
        this.intfRpcService = ifaceMgrRpcService;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<StateTunnelList> getWildCardPath() {
        return InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class);
    }

    @Override
    protected TunnelInterfaceStateListener getDataTreeChangeListener() {
        return TunnelInterfaceStateListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList del) {
        LOG.trace("Tunnel deletion---- {}", del);
        handleTunnelEventForDPN(del, UpdateRouteAction.WITHDRAW_ROUTE , TunnelAction.TUNNEL_EP_DELETE);
    }

    @Override
    protected void update(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList original, StateTunnelList update) {
        LOG.trace("Tunnel updation---- {}", update);
        LOG.trace("ITM Tunnel {} of type {} state event changed from :{} to :{}",
                update.getTunnelInterfaceName(),
                fibManager.getTransportTypeStr(update.getTransportType().toString()),
                original.getOperState(), update.getOperState());
        //withdraw all prefixes in all vpns for this dpn
        TunnelOperStatus tunOpStatus = update.getOperState();
        if ((tunOpStatus != TunnelOperStatus.Down) && (tunOpStatus != TunnelOperStatus.Up)) {
            LOG.trace("Returning from unsupported tunnelOperStatus {}", tunOpStatus);
            return;
        }
        boolean isTunnelUp = (tunOpStatus == TunnelOperStatus.Up);
        handleTunnelEventForDPN(update,
                                isTunnelUp ? UpdateRouteAction.ADVERTISE_ROUTE : UpdateRouteAction.WITHDRAW_ROUTE,
                                TunnelAction.TUNNEL_EP_UPDATE);
    }

    @Override
    protected void add(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList add) {
        LOG.trace("Tunnel addition---- {}", add);
        TunnelOperStatus tunOpStatus = add.getOperState();
        if ((tunOpStatus != TunnelOperStatus.Down) && (tunOpStatus != TunnelOperStatus.Up)) {
            LOG.trace("Returning from unsupported tunnelOperStatus {}", tunOpStatus);
            return;
        }
        boolean isTunnelUp = (tunOpStatus == TunnelOperStatus.Up);
        if (!isTunnelUp) {
            LOG.trace("Tunnel {} is not yet UP.",
                    add.getTunnelInterfaceName());
            return;
        } else {
            LOG.trace("ITM Tunnel ,type {} ,State is UP b/w src: {} and dest: {}",
                    fibManager.getTransportTypeStr(add.getTransportType().toString()),
                    add.getSrcInfo().getTepDeviceId(), add.getDstInfo().getTepDeviceId());
            handleTunnelEventForDPN(add, UpdateRouteAction.ADVERTISE_ROUTE, TunnelAction.TUNNEL_EP_ADD);
        }
    }

    private void handleTunnelEventForDPN(StateTunnelList stateTunnelList, UpdateRouteAction action, TunnelAction tunnelAction) {
        final BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        final String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());
        LOG.trace("Handle tunnel event for srcDpn {} SrcTepIp {} DestTepIp {} ", srcDpnId, srcTepIp, destTepIp);
        int tunTypeVal = getTunnelType(stateTunnelList);

        LOG.trace("tunTypeVal is {}", tunTypeVal);

        try {
            if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                // Maintain a Dpn-To-Vtep Op DS to avoid repeatedly calling updateVpnInterfaceOnTepAdd
                // method for every tunnel-add event sent by ITM when TEP is added.
                // Since TEP is common for all the (N-1) tunnels for a given Dpn , no need to update
                // the VpnInterface Adj NextHop for every tunnel event.
                InstanceIdentifier<DpnToVtep> DpnToVtepId =
                        InstanceIdentifier.builder(DpnToVtepMap.class).child(DpnToVtep.class, new DpnToVtepKey(srcDpnId)).build();
                Optional<DpnToVtep> entry = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, DpnToVtepId);

                if (entry.isPresent()) {
                    String vtepIp = entry.get().getVtepip();
                    if (!vtepIp.equals(srcTepIp)) {
                        // Entry found for DpnId. Unlikely to hit this code.
                        LOG.error (" Tunnel ADD event already received for Dpn {} with a changed VTEP IP ()", srcDpnId, srcTepIp);
                        return;
                    }
                } else {
                    LOG.trace(" Tunnel ADD event received for Dpn {} VTEP Ip {} ", srcDpnId, srcTepIp);
                    // Entry not found. Create the entry for the DpnId
                    DpnToVtepBuilder dpnToVtepBuilder = new DpnToVtepBuilder().setKey(new DpnToVtepKey(srcDpnId)).setVtepip(srcTepIp);
                    VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, DpnToVtepId, dpnToVtepBuilder.build());
                }
            } else if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE) {

                // When tunnel EP is deleted on a DPN , VPN gets two deletion event.
                // One for a DPN on which tunnel EP was deleted and another for other-end DPN.
                // Handle only the DPN on which it was deleted , ignore other event.
                // DPN on which TEP is deleted , endpoint IP will be null.
                String endpointIpForDPN = null;
                try {
                    endpointIpForDPN = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, srcDpnId);
                } catch (Exception e) {
                    /* this dpn does not have the VTEP */
                    endpointIpForDPN = null;
                }

                if (endpointIpForDPN != null) {
                    LOG.trace("Ignore Tunnel DELETE event received for Dpn {} VTEP Ip {}", srcDpnId, srcTepIp);
                    return;
                }

                InstanceIdentifier<DpnToVtep> DpnToVtepId =
                        InstanceIdentifier.builder(DpnToVtepMap.class).child(DpnToVtep.class, new DpnToVtepKey(srcDpnId)).build();
                Optional<DpnToVtep> entry = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, DpnToVtepId);
                if (entry.isPresent()) {
                    String vtepIp = entry.get().getVtepip();
                    if (vtepIp.equals(srcTepIp)) {
                        LOG.trace(" Tunnel DELETE event received for Dpn {} VTEP Ip {} ", srcDpnId, srcTepIp);
                        // Entry found for DpnId. Delete the entry.
                        VpnUtil.delete(dataBroker, LogicalDatastoreType.OPERATIONAL, DpnToVtepId);
                    } else {
                        // Unlikely to hit this condition
                        LOG.error(" Tunnel DELETE event received for Dpn {} with different VTEP Ip {} than expected VTEP Ip {}", srcDpnId, srcTepIp, vtepIp);
                        return;
                    }
                } else {
                    LOG.trace(" Tunnel DELETE event already received for Dpn {} VTEP Ip {} ", srcDpnId, srcTepIp);
                }
            }

            // Get the list of VpnInterfaces from Intf Mgr for a given DPN.
            Future<RpcResult<GetDpnInterfaceListOutput>> result;
            List<String> interfacelist = new ArrayList<>();
            try {
                result = intfRpcService.getDpnInterfaceList(new GetDpnInterfaceListInputBuilder().setDpid(srcDpnId).build());
                RpcResult<GetDpnInterfaceListOutput> rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.warn("RPC Call to GetDpnInterfaceList for dpnid {} returned with Errors {}", srcDpnId, rpcResult.getErrors());
                } else {
                    interfacelist = rpcResult.getResult().getInterfacesList();
                }
            } catch (Exception e) {
                LOG.warn("Exception {} when querying for GetDpnInterfaceList for dpnid {}, trace {}", e, srcDpnId, e.getStackTrace());
            }

            /*
             * Iterate over the list of VpnInterface for a given Dpn and read the adj.
             * Update the adjacencies with the updated nexthop.
             */
            Iterator<String> interfacelistIter = interfacelist.iterator();
            String intfName = null;
            List<Uuid> subnetList = new ArrayList<Uuid>();
            Map<Long, String> vpnIdRdMap = new HashMap<Long, String>();

            while (interfacelistIter.hasNext()) {
                intfName = interfacelistIter.next();
                final VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(dataBroker, intfName);
                if (vpnInterface != null) {

                    DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                    dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + intfName,
                            new UpdateVpnInterfaceOnTunnelEvent(dataBroker,
                                    vpnInterfaceManager,
                                    tunTypeVal,
                                    tunnelAction,
                                    action,
                                    vpnInterface,
                                    stateTunnelList));

                    // Populate the List of subnets
                    InstanceIdentifier<PortOpDataEntry> portOpIdentifier = InstanceIdentifier.builder(PortOpData.class).
                            child(PortOpDataEntry.class, new PortOpDataEntryKey(intfName)).build();
                    Optional<PortOpDataEntry> optionalPortOp = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                    if (optionalPortOp.isPresent()) {
                        Uuid subnetId = optionalPortOp.get().getSubnetId();
                        if (!subnetList.contains(subnetId)) {
                            subnetList.add(subnetId);
                        }
                    }

                    //Populate the map for VpnId-to-Rd
                    long vpnId = VpnUtil.getVpnId(dataBroker, vpnInterface.getVpnInstanceName());
                    String rd = VpnUtil.getVpnRd(dataBroker, vpnInterface.getVpnInstanceName());
                    vpnIdRdMap.put(vpnId, rd);
                }
            }

            //Iterate over the VpnId-to-Rd map.
            Iterator<Map.Entry<Long, String>> entries = vpnIdRdMap.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<Long, String> entry = entries.next();
                Long vpnId = entry.getKey();
                String rd = entry.getValue();
                if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                    if(tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                        fibManager.populateInternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                    }
                    if(tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) {
                        fibManager.populateExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                    }
                } else if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE) {
                    if(tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                        fibManager.cleanUpInternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                    }
                    if(tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) {
                        fibManager.cleanUpExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                    }
                }
            }

            if (action == UpdateRouteAction.ADVERTISE_ROUTE) {
                for (Uuid subnetId : subnetList) {
                    // Populate the List of subnets
                    vpnSubnetRouteHandler.updateSubnetRouteOnTunnelUpEvent(subnetId, srcDpnId);
                }
            }

            if (action == UpdateRouteAction.WITHDRAW_ROUTE) {
                for (Uuid subnetId : subnetList) {
                    // Populate the List of subnets
                    vpnSubnetRouteHandler.updateSubnetRouteOnTunnelDownEvent(subnetId, srcDpnId);
                }
            }
        } catch (Exception e) {
            LOG.error("Unable to handle the tunnel event.", e);
            return;
        }

    }


    private class UpdateVpnInterfaceOnTunnelEvent implements Callable {
        private int tunTypeVal;
        private VpnInterface vpnInterface;
        private StateTunnelList stateTunnelList;
        private TunnelInterfaceStateListener.UpdateRouteAction action;
        private VpnInterfaceManager vpnInterfaceManager;
        private  DataBroker broker;
        private TunnelAction tunnelAction;

        UpdateVpnInterfaceOnTunnelEvent(DataBroker broker,
                                        VpnInterfaceManager vpnInterfaceManager,
                                        int tunTypeVal,
                                        TunnelAction tunnelAction,
                                        TunnelInterfaceStateListener.UpdateRouteAction action,
                                        VpnInterface vpnInterface,
                                        StateTunnelList stateTunnelList) {
            this.broker = broker;
            this.vpnInterfaceManager = vpnInterfaceManager;
            this.tunTypeVal = tunTypeVal;
            this.stateTunnelList = stateTunnelList;
            this.vpnInterface = vpnInterface;
            this.action = action;
            this.tunnelAction = tunnelAction;
        }

        public List<ListenableFuture<Void>> call() throws Exception {
            WriteTransaction writeOperTxn = broker.newWriteOnlyTransaction();
            WriteTransaction writeConfigTxn = broker.newWriteOnlyTransaction();

            if(tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                vpnInterfaceManager.updateVpnInterfaceOnTepAdd(vpnInterface, stateTunnelList, writeOperTxn, writeConfigTxn);
            }

            if(tunnelAction == TunnelAction.TUNNEL_EP_DELETE) {
                vpnInterfaceManager.updateVpnInterfaceOnTepDelete(vpnInterface, writeOperTxn, writeConfigTxn);
            }

            List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
            futures.add(writeOperTxn.submit());
            futures.add(writeConfigTxn.submit());
            return futures;
        }
    }

    private int getTunnelType (StateTunnelList stateTunnelList) {
        int tunTypeVal = 0;
        if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeInternal.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Internal.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeExternal.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.External.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeHwvtep.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Hwvtep.getValue();
        } else {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Invalid.getValue();
        }
        return tunTypeVal;
    }
}

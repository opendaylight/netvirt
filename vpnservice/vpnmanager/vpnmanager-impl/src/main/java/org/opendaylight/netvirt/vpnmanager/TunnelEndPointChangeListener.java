/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TunnelEndPointChangeListener
    extends AsyncDataTreeChangeListenerBase<TunnelEndPoints, TunnelEndPointChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelEndPointChangeListener.class);

    private final DataBroker broker;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final JobCoordinator jobCoordinator;
    private VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private OdlInterfaceRpcService intfRpcService;

    enum TepAction {
        TUNNEL_EP_ADD,
        TUNNEL_EP_DELETE,
    }

    @Inject
    public TunnelEndPointChangeListener(final DataBroker broker, final VpnInterfaceManager vpnInterfaceManager,
            final JobCoordinator jobCoordinator, final VpnSubnetRouteHandler vpnSubnetRouteHandler,
                                        final OdlInterfaceRpcService intfRpcService) {
        super(TunnelEndPoints.class, TunnelEndPointChangeListener.class);
        this.broker = broker;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
        this.intfRpcService = intfRpcService;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<TunnelEndPoints> getWildCardPath() {
        return InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class).child(TunnelEndPoints.class)
            .build();
    }

    @Override
    protected void remove(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints tep) {
        BigInteger dpnId = key.firstIdentifierOf(DPNTEPsInfo.class).firstKeyOf(DPNTEPsInfo.class).getDPNID();
        handleTepEventForDPN(tep, dpnId, TepAction.TUNNEL_EP_DELETE);
        LOG.trace("remove: TEP {} deleted on DPN {} for tunnel-type {}",
                tep.getIpAddress(), dpnId, tep.getTunnelType());
    }

    @Override
    protected void update(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints origTep,
        TunnelEndPoints updatedTep) {
    }

    @Override
    protected void add(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints tep) {
        BigInteger dpnId = key.firstIdentifierOf(DPNTEPsInfo.class).firstKeyOf(DPNTEPsInfo.class).getDPNID();
        handleTepEventForDPN(tep, dpnId, TepAction.TUNNEL_EP_ADD);
        LOG.trace("add: TEP {} added on DPN {} for tunnel-type {}", tep.getIpAddress(), dpnId, tep.getTunnelType());
    }

    @Override
    protected TunnelEndPointChangeListener getDataTreeChangeListener() {
        return this;
    }

    private void handleTepEventForDPN(TunnelEndPoints tep, BigInteger dpnId, TepAction action) {
        LOG.debug("handleTepEventForDPN: tep {} action {}", tep, action);

        try {
            // Get the list of VpnInterfaces from Intf Mgr for a DPN on which TEP is added/deleted
            Future<RpcResult<GetDpnInterfaceListOutput>> result;
            List<String> srcDpninterfacelist = VpnUtil.getDpnInterfaceList(intfRpcService, dpnId);

            /*
             * Iterate over the list of VpnInterface for a SrcDpn on which TEP is added or deleted and read the adj.
             * Update the adjacencies with the updated nexthop.
             */
            Iterator<String> interfacelistIter = srcDpninterfacelist.iterator();
            String intfName = null;
            List<Uuid> subnetList = new ArrayList<>();

            while (interfacelistIter.hasNext()) {
                intfName = interfacelistIter.next();
                VpnInterface vpnInterface =
                        VpnUtil.getConfiguredVpnInterface(broker, intfName);
                if (vpnInterface != null) {
                    handleTepEventForDPNVpn(broker, vpnInterface, dpnId, tep, action, subnetList);
                }
            }
            /*Subnet Route re-election needs to be triggered if nextHop DPN is not set*/
            manageSubnetRoutes(subnetList, dpnId, action);
        } catch (Exception e) {
            LOG.error("handleTepEventForDPN: Unable to handle the TEP event for dpnId {} TepIp {}",
                    dpnId, tep.getIpAddress());
            return;
        }
    }

    private void manageSubnetRoutes(List<Uuid> subnetList, BigInteger srcDpnId, TepAction tepAction) {
        LOG.debug("manageSubnetRoutes: srcDpnId {} action {} subnetList {}", srcDpnId, tepAction, subnetList);
        switch (tepAction) {
            case TUNNEL_EP_ADD:
                for (Uuid subnetId : subnetList) {
                    vpnSubnetRouteHandler.updateSubnetRouteOnTepAddEvent(subnetId, srcDpnId);
                }
                break;

            case TUNNEL_EP_DELETE:
                for (Uuid subnetId : subnetList) {
                    vpnSubnetRouteHandler.updateSubnetRouteOnTepDelEvent(subnetId, srcDpnId);
                }
                break;

            default:
                break;
        }
    }

    private class UpdateVpnInterfaceOnTepEvent implements Callable {
        private VpnInterfaceOpDataEntry vpnInterface;
        private VpnInterfaceManager vpnInterfaceManager;
        private DataBroker broker;
        private TepAction tepAction;
        private TunnelEndPoints tunnelEndPoints;
        private BigInteger srcDpnId;

        UpdateVpnInterfaceOnTepEvent(DataBroker broker,
                                     VpnInterfaceManager vpnInterfaceManager,
                                     TepAction tepAction,
                                     VpnInterfaceOpDataEntry vpnInterface,
                                     BigInteger dpnId,
                                     TunnelEndPoints tunnelEndPoints) {
            this.broker = broker;
            this.vpnInterfaceManager = vpnInterfaceManager;
            this.vpnInterface = vpnInterface;
            this.tepAction = tepAction;
            this.tunnelEndPoints = tunnelEndPoints;
            this.srcDpnId = dpnId;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            WriteTransaction writeConfigTxn = broker.newWriteOnlyTransaction();
            WriteTransaction writeOperTxn = broker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();

            switch (tepAction) {
                case TUNNEL_EP_ADD:
                    vpnInterfaceManager.updateVpnInterfaceOnTepAdd(vpnInterface, srcDpnId, tunnelEndPoints,
                            writeConfigTxn, writeOperTxn);
                    break;

                case TUNNEL_EP_DELETE:
                    vpnInterfaceManager.updateVpnInterfaceOnTepDelete(vpnInterface, srcDpnId, tunnelEndPoints,
                            writeConfigTxn, writeOperTxn);
                    break;

                default:
                    break;
            }
            futures.add(writeOperTxn.submit());
            futures.add(writeConfigTxn.submit());
            return futures;
        }
    }

    private void handleTepEventForDPNVpn(DataBroker broker,
                                         VpnInterface cfgVpnInterface,
                                         BigInteger srcDpnId,
                                         TunnelEndPoints tep,
                                         TepAction tepAction,
                                         List<Uuid> subnetList) {
        String intfName = cfgVpnInterface.getName();

        if (cfgVpnInterface.getVpnInstanceNames() == null) {
            LOG.warn("handleTepEventForDPNVpn: no vpnName found for interface {}", intfName);
            return;
        }
        for (VpnInstanceNames vpnInstance : cfgVpnInterface.getVpnInstanceNames()) {
            String vpnName = vpnInstance.getVpnName();

            Optional<VpnInterfaceOpDataEntry> opVpnInterface = VpnUtil
                    .getVpnInterfaceOpDataEntry(broker, intfName, vpnName);
            if (opVpnInterface.isPresent()) {
                VpnInterfaceOpDataEntry vpnInterface = opVpnInterface.get();
                jobCoordinator.enqueueJob("VPNINTERFACE-" + intfName,
                        new UpdateVpnInterfaceOnTepEvent(broker,
                                vpnInterfaceManager,
                                tepAction,
                                vpnInterface,
                                srcDpnId,
                                tep));

                // Populate the List of subnets
                InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
                        InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                                new PortOpDataEntryKey(intfName)).build();
                Optional<PortOpDataEntry> optionalPortOp =
                        VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                if (optionalPortOp.isPresent()) {
                    List<Uuid> subnetIdList = optionalPortOp.get().getSubnetIds();
                    if (subnetIdList != null) {
                        for (Uuid subnetId : subnetIdList) {
                            if (!subnetList.contains(subnetId)) {
                                subnetList.add(subnetId);
                            }
                        }
                    }
                }
            }
        }
    }
}

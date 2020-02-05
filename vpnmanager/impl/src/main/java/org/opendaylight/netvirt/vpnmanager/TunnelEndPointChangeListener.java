/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.get.dpn._interface.list.output.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TunnelEndPointChangeListener
    extends AsyncDataTreeChangeListenerBase<TunnelEndPoints, TunnelEndPointChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelEndPointChangeListener.class);

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;
    private OdlInterfaceRpcService intfRpcService;
    private VpnSubnetRouteHandler vpnSubnetRouteHandler;

    enum TepAction {
        TUNNEL_EP_ADD,
        TUNNEL_EP_DELETE
    }

    @Inject
    public TunnelEndPointChangeListener(final DataBroker broker, final VpnInterfaceManager vpnInterfaceManager,
                                        final JobCoordinator jobCoordinator, VpnUtil vpnUtil,
                                        final VpnSubnetRouteHandler vpnSubnetRouteHandler,
                                        final OdlInterfaceRpcService intfRpcService) {
        super(TunnelEndPoints.class, TunnelEndPointChangeListener.class);
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
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
        Uint64 dpnId = key.firstIdentifierOf(DPNTEPsInfo.class).firstKeyOf(DPNTEPsInfo.class).getDPNID();
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
        Uint64 dpnId = key.firstIdentifierOf(DPNTEPsInfo.class).firstKeyOf(DPNTEPsInfo.class).getDPNID();
        handleTepEventForDPN(tep, dpnId, TepAction.TUNNEL_EP_ADD);
        LOG.trace("add: TEP {} added on DPN {} for tunnel-type {}", tep.getIpAddress(), dpnId, tep.getTunnelType());
    }

    @Override
    protected TunnelEndPointChangeListener getDataTreeChangeListener() {
        return this;
    }

    private void handleTepEventForDPN(TunnelEndPoints tep, Uint64 dpnId, TepAction action) {
        LOG.debug("handleTepEventForDPN: tep {} action {}", tep, action);

        try {
            // Get the list of VpnInterfaces from Intf Mgr for a DPN on which TEP is added/deleted
            Future<RpcResult<GetDpnInterfaceListOutput>> result;
            List<Interfaces> srcDpninterfacelist = VpnUtil.getDpnInterfaceList(intfRpcService, dpnId);

            /*
             * Iterate over the list of VpnInterface for a SrcDpn on which TEP is added or deleted and read the adj.
             * Update the adjacencies with the updated nexthop.
             */
            Iterator<Interfaces> interfacelistIter = srcDpninterfacelist.iterator();
            Interfaces interfaces = null;
            String intfName = null;
            HashSet<Uuid> subnetList = new HashSet<>();
            if (!srcDpninterfacelist.isEmpty()) {
                while (interfacelistIter.hasNext()) {
                    interfaces = interfacelistIter.next();
                    if (!L2vlan.class.equals(interfaces.getInterfaceType())) {
                        LOG.info("handleTepEventForDPN: Interface {} not of type L2Vlan",
                                interfaces.getInterfaceName());
                        continue;
                    }
                    intfName = interfaces.getInterfaceName();
                    VpnInterface cfgVpnInterface = vpnUtil.getConfiguredVpnInterface(intfName);
                    if (cfgVpnInterface != null) {
                        for (VpnInstanceNames vpnInstanceNames : cfgVpnInterface.getVpnInstanceNames()) {
                            final Optional<VpnInterfaceOpDataEntry> operVpnInterface =
                                    vpnUtil.getVpnInterfaceOpDataEntry(intfName, vpnInstanceNames.getVpnName());
                            if (operVpnInterface.isPresent()) {
                                jobCoordinator.enqueueJob("VPNINTERFACE-" + intfName,
                                        new UpdateVpnInterfaceOnTepEvent(vpnInterfaceManager,
                                                action,
                                                operVpnInterface.get(),
                                                dpnId,
                                                tep));

                                // Populate the List of subnets
                                InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
                                        InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                                                new PortOpDataEntryKey(intfName)).build();
                                Optional<PortOpDataEntry> optionalPortOp =
                                        SingleTransactionDataBroker.syncReadOptional(broker,
                                                LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                                if (optionalPortOp.isPresent()) {
                                    List<Uuid> subnetIdList = optionalPortOp.get().getSubnetIds();
                                    subnetList.addAll(subnetIdList);
                                }
                            }
                        }
                    }
                }

                /*Subnet Route re-election needs to be triggered if nextHop DPN is not set*/
                manageSubnetRoutes(subnetList, dpnId, action);
            } else {
                LOG.debug("handleTepEventForDPN: No interfaces found on Dpn {}", dpnId);
            }
        } catch (ReadFailedException e) {
            LOG.error("handleTepEventForDPN: Unable to handle the TEP event for dpnId {} TepIp {}",
                    dpnId, tep.getIpAddress(), e);
            return;
        }
    }

    private void manageSubnetRoutes(HashSet<Uuid> subnetList, Uint64 srcDpnId, TepAction tepAction) {
        LOG.debug("manageSubnetRoutes: srcDpnId {} action {} subnetList {}", srcDpnId, tepAction, subnetList);
        switch (tepAction) {
            case TUNNEL_EP_ADD:
                for (Uuid subnetId : subnetList) {
                    vpnSubnetRouteHandler.updateSubnetRouteOnTunnelUpEvent(subnetId, srcDpnId);
                }
                break;

            case TUNNEL_EP_DELETE:
                for (Uuid subnetId : subnetList) {
                    vpnSubnetRouteHandler.updateSubnetRouteOnTunnelDownEvent(subnetId, srcDpnId);
                }
                break;

            default:
                break;
        }
    }

    private class UpdateVpnInterfaceOnTepEvent implements Callable {
        private VpnInterfaceOpDataEntry vpnInterface;
        private VpnInterfaceManager vpnInterfaceManager;
        private TepAction tepAction;
        private TunnelEndPoints tunnelEndPoints;
        private Uint64 srcDpnId;

        UpdateVpnInterfaceOnTepEvent(VpnInterfaceManager vpnInterfaceManager,
                                     TepAction tepAction,
                                     VpnInterfaceOpDataEntry vpnInterface,
                                     Uint64 dpnId,
                                     TunnelEndPoints tunnelEndPoints) {
            this.vpnInterfaceManager = vpnInterfaceManager;
            this.vpnInterface = vpnInterface;
            this.tepAction = tepAction;
            this.tunnelEndPoints = tunnelEndPoints;
            this.srcDpnId = dpnId;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();

            switch (tepAction) {
                case TUNNEL_EP_ADD:
                    futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                        writeConfigTxn -> futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                            writeOperTxn -> vpnInterfaceManager.updateVpnInterfaceOnTepAdd(vpnInterface,
                                            srcDpnId, tunnelEndPoints, writeConfigTxn, writeOperTxn)))));
                    break;

                case TUNNEL_EP_DELETE:
                    futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                        writeConfigTxn -> futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                            writeOperTxn -> vpnInterfaceManager.updateVpnInterfaceOnTepDelete(vpnInterface,
                                            srcDpnId, tunnelEndPoints, writeConfigTxn, writeOperTxn)))));
                    break;

                default:
                    break;
            }
            return futures;
        }
    }
}

/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.fibmanager.factory.IVrfHandler;
import org.opendaylight.netvirt.fibmanager.factory.VrfInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import static org.opendaylight.netvirt.fibmanager.FibConstants.DEFAULT_FIB_FLOW_PRIORITY;


public class SubnetRouteVrfHandler implements IVrfHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetRouteVrfHandler.class);
    private VrfInput vrfInput;
    private DataBroker dataBroker;

//    public SubnetRouteVrfHandler(final DataBroker dataBroker) {
//        this.dataBroker = dataBroker;
//    }

    public SubnetRouteVrfHandler(VrfInput input) {
        this.vrfInput = input;
    }

    @Override
    public void doProcessing() {

        VrfEntry vrfEntry = vrfInput.getVrfEntry();
        String rd = vrfInput.getRd();
        Long vpnId = vrfInput.getVpnId();
        dataBroker = vrfInput.getDataBroker();
        SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
        long elanTag = subnetRoute.getElantag();
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vrfInput.getVpnInstance();
        Collection<VpnToDpnList> vpnToDpnList = vpnInstanceOpDataEntry.getVpnToDpnList();

        LOG.trace("SubnetRoute Processing action {} subnetroute {} rd {} vpnId {}", vrfInput.getAction(),
                vrfEntry.getDestPrefix(), rd, vpnId, elanTag);

        if (vrfInput.getAction() == VrfInput.ADD_SUBNET_VRF) {
            if (vpnToDpnList != null) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("FIB-" + rd.toString() + vrfEntry.getDestPrefix(),
                        new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                                for (final VpnToDpnList curDpn : vpnToDpnList) {
                                    if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                        installSubnetRouteFlows(curDpn.getDpnId(), elanTag, rd, vpnId.longValue(), vrfEntry, tx, dataBroker);
                                    }
                                }
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                futures.add(tx.submit());
                                return futures;
                            }
                        });
            }
            return;
        }

        if (vrfInput.getAction() == VrfInput.DELETE_SUBNET_VRF) {
            if (vpnToDpnList != null) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("FIB-" + rd.toString() + "-" + vrfEntry.getDestPrefix(),
                        new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                                for (final VpnToDpnList curDpn : vpnToDpnList) {
                                    removeSubnetRouteFlows(curDpn.getDpnId(), rd, vpnId, vrfEntry, tx, dataBroker);
                                }
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                futures.add(tx.submit());
                                return futures;
                            }
                        });
            }
        }
    }

    public void installSubnetRouteFlows (final BigInteger dpnId, final long elanTag, final String rd,
                                         final long vpnId, final VrfEntry vrfEntry, WriteTransaction tx, DataBroker dataBroker){
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }

        final List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        BigInteger subnetRouteMeta =  ((BigInteger.valueOf(elanTag)).shiftLeft(32)).or((BigInteger.valueOf(vpnId).shiftLeft(1)));
        instructions.add(new InstructionInfo(InstructionType.write_metadata,  new BigInteger[] { subnetRouteMeta, MetaDataUtil.METADATA_MASK_SUBNET_ROUTE }));
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_SUBNET_ROUTE_TABLE }));
        FibUtil.makeConnectedRoute(dpnId,vpnId,vrfEntry,rd,instructions,NwConstants.ADD_FLOW, tx, dataBroker);

        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
            List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
            // reinitialize instructions list for LFIB Table
            final List<InstructionInfo> LFIBinstructions = new ArrayList<InstructionInfo>();

            actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{}));
            LFIBinstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
            LFIBinstructions.add(new InstructionInfo(InstructionType.write_metadata,  new BigInteger[] { subnetRouteMeta, MetaDataUtil.METADATA_MASK_SUBNET_ROUTE }));
            LFIBinstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_SUBNET_ROUTE_TABLE }));

            FibUtil.makeLFibTableEntry(dpnId,vrfEntry.getLabel(), LFIBinstructions, DEFAULT_FIB_FLOW_PRIORITY, NwConstants.ADD_FLOW, tx, dataBroker);
        }
        if (!wrTxPresent ) {
            tx.submit();
        }
    }

    public void removeSubnetRouteFlows (final BigInteger dpnId,
                                        final String rd,
                                        final long vpnId,
                                        final VrfEntry vrfEntry,
                                        WriteTransaction tx,
                                        DataBroker dataBroker){

        FibUtil.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd , null, NwConstants.DEL_FLOW, tx, dataBroker);
        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
            FibUtil.makeLFibTableEntry(dpnId, vrfEntry.getLabel(), null, DEFAULT_FIB_FLOW_PRIORITY, NwConstants.DEL_FLOW, tx, dataBroker);
        }
    }

}
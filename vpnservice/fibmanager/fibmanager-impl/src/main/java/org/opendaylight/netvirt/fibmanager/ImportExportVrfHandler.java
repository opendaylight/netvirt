/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.LabelRouteMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.crypto.Data;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;


public class ImportExportVrfHandler implements IVrfHandler{
    private static final Logger LOG = LoggerFactory.getLogger(ImportExportVrfHandler.class);
    private VrfInput vrfInput;

    public ImportExportVrfHandler(VrfInput input) {
        this.vrfInput = input;
    }

    @Override
    public void doProcessing() {

        if (vrfInput.getAction() == VrfInput.ADD_SUBNET_VRF) {
            VrfEntry vrfEntry = vrfInput.getVrfEntry();
            String rd = vrfInput.getRd();
            DataBroker dataBroker = vrfInput.getDataBroker();
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vrfInput.getVpnInstance();
            Collection<VpnToDpnList> vpnToDpnList = vpnInstanceOpDataEntry.getVpnToDpnList();
            if (vpnToDpnList != null) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("FIB-" + rd.toString() + "-" + vrfEntry.getDestPrefix(),
                        new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                synchronized (vrfEntry.getLabel().toString().intern()) {
                                    LabelRouteInfo lri = FibUtil.getLabelRouteInfo(dataBroker, vrfEntry.getLabel());
                                    if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) &&
                                            vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {
                                        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                                            Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = FibUtil.getVpnInstanceOpData(dataBroker, rd);
                                            if (vpnInstanceOpDataEntryOptional.isPresent()) {
                                                String vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                                                if (!lri.getVpnInstanceList().contains(vpnInstanceName)) {
                                                    updateVpnReferencesInLri(dataBroker, lri, vpnInstanceName, false);
                                                }
                                            }
                                        }
                                        LOG.debug("Fetched labelRouteInfo for label {} interface {} and got dpn {}",
                                                vrfEntry.getLabel(), lri.getVpnInterfaceName(), lri.getDpnId());
                                    }
                                }
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                return futures;
                            }
                        });
            }
            return;
        }

        if (vrfInput.getAction() == VrfInput.DELETE_SUBNET_VRF) {
            VrfEntry vrfEntry = vrfInput.getVrfEntry();
            DataBroker dataBroker = vrfInput.getDataBroker();
            String rd = vrfInput.getRd();
            removeVpnReferenceInLri(dataBroker, vrfEntry, rd);
            return;
        }



    }

    private Prefixes updateVpnReferencesInLri(DataBroker dataBroker, LabelRouteInfo lri, String vpnInstanceName, boolean isPresentInList) {
        LOG.debug("updating LRI : for label {} vpninstancename {}", lri.getLabel(), vpnInstanceName);
        PrefixesBuilder prefixBuilder = new PrefixesBuilder();
        prefixBuilder.setDpnId(lri.getDpnId());
        prefixBuilder.setVpnInterfaceName(lri.getVpnInterfaceName());
        prefixBuilder.setIpAddress(lri.getPrefix());
        // Increment the refCount here
        InstanceIdentifier<LabelRouteInfo> lriId = InstanceIdentifier.builder(LabelRouteMap.class)
                .child(LabelRouteInfo.class, new LabelRouteInfoKey((long)lri.getLabel())).build();
        LabelRouteInfoBuilder builder = new LabelRouteInfoBuilder(lri);
        if (!isPresentInList) {
            LOG.debug("vpnName {} is not present in LRI with label {}..", vpnInstanceName, lri.getLabel());
            List<String> vpnInstanceNames = lri.getVpnInstanceList();
            vpnInstanceNames.add(vpnInstanceName);
            builder.setVpnInstanceList(vpnInstanceNames);
            FibUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, lriId, builder.build(), FibUtil.DEFAULT_CALLBACK);
        } else {
            LOG.debug("vpnName {} is present in LRI with label {}..", vpnInstanceName, lri.getLabel());
        }
        return prefixBuilder.build();
    }

    private void removeVpnReferenceInLri (DataBroker dataBroker, VrfEntry vrfEntry, String rd) {
        synchronized (vrfEntry.getLabel().toString().intern()) {
            LabelRouteInfo lri = FibUtil.getLabelRouteInfo(dataBroker, vrfEntry.getLabel());
            if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) && vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {
                Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = FibUtil.getVpnInstanceOpData(dataBroker, rd);
                String vpnInstanceName = "";
                if (vpnInstanceOpDataEntryOptional.isPresent()) {
                    vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                }
                boolean lriRemoved = deleteLabelRouteInfo(dataBroker, lri, vpnInstanceName);
                if (lriRemoved) {
                    String parentRd = lri.getParentVpnRd();
                    FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                            FibUtil.getNextHopLabelKey(parentRd, vrfEntry.getDestPrefix()));
                    LOG.trace("deleteFibEntries: Released subnetroute label {} for rd {} prefix {} as labelRouteInfo cleared", vrfEntry.getLabel(), rd,
                            vrfEntry.getDestPrefix());
                }
            } else {
                FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                        FibUtil.getNextHopLabelKey(rd, vrfEntry.getDestPrefix()));
                LOG.trace("deleteFibEntries: Released subnetroute label {} for rd {} prefix {}", vrfEntry.getLabel(), rd,
                        vrfEntry.getDestPrefix());
            }
        }
    }

    private boolean deleteLabelRouteInfo(DataBroker dataBroker, LabelRouteInfo lri, String vpnInstanceName) {
        LOG.debug("deleting LRI : for label {} vpninstancename {}", lri.getLabel(), vpnInstanceName);
        InstanceIdentifier<LabelRouteInfo> lriId = InstanceIdentifier.builder(LabelRouteMap.class)
                .child(LabelRouteInfo.class, new LabelRouteInfoKey((long) lri.getLabel())).build();
        if (lri == null) {
            return true;
        }
        List<String> vpnInstancesList = lri.getVpnInstanceList() != null ? lri.getVpnInstanceList() : new ArrayList<String>();
        if (vpnInstancesList.contains(vpnInstanceName)) {
            LOG.debug("vpninstance {} name is present", vpnInstanceName);
            vpnInstancesList.remove(vpnInstanceName);
        }
        if (vpnInstancesList.size() == 0) {
            LOG.debug("deleting LRI instance object for label {}", lri.getLabel());
            FibUtil.delete(dataBroker, LogicalDatastoreType.OPERATIONAL, lriId);
            return true;
        } else {
            LOG.debug("updating LRI instance object for label {}", lri.getLabel());
            LabelRouteInfoBuilder builder = new LabelRouteInfoBuilder(lri).setVpnInstanceList(vpnInstancesList);
            FibUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, lriId, builder.build(), FibUtil.DEFAULT_CALLBACK);
        }
        return false;
    }

}
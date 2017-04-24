/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.evpn.utils.ElanEvpnFlowUtils;
import org.opendaylight.netvirt.elan.evpn.utils.ElanEvpnUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * When RT2 route (advertise of withdraw) is received from peer side.
 * BGPManager will receive the RT2 msg.
 * It will check if the EVPN is configured and Network is attached to EVPN or not.
 * BGPManager will write in path (FibEntries.class).child(VrfTables.class).child(MacVrfEntry.class)
 * which MacVrfEntryListener is listening to.
 * When RT2 advertise route is received: add method of MacVrfEntryListener will install DMAC flows for the
 * received dest MAC in all the DPN's (with this network footprint).
 * When RT2 withdraw route is received: remove method of MacVrfEntryListener will remove DMAC flows for the
 * received dest MAC in all the DPN's (with this network footprint).
 */
@Singleton
public class MacVrfEntryListener extends AsyncDataTreeChangeListenerBase<MacVrfEntry,
        MacVrfEntryListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MacVrfEntryListener.class);
    private final DataBroker broker;
    private final ElanUtils elanUtils;
    private final IMdsalApiManager mdsalManager;
    private final ElanEvpnUtils elanEvpnUtils;
    private final ElanEvpnFlowUtils elanEvpnFlowUtils;

    @Inject
    public MacVrfEntryListener(DataBroker broker, ElanUtils elanUtils, IMdsalApiManager mdsalManager,
                               ElanEvpnUtils elanEvpnUtils, ElanEvpnFlowUtils elanEvpnFlowUtils) {
        this.broker = broker;
        this.elanUtils = elanUtils;
        this.mdsalManager = mdsalManager;
        this.elanEvpnUtils = elanEvpnUtils;
        this.elanEvpnFlowUtils = elanEvpnFlowUtils;

    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    public void close() {
    }

    @Override
    protected InstanceIdentifier<MacVrfEntry> getWildCardPath() {
        return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class).child(MacVrfEntry.class);
    }

    @Override
    protected MacVrfEntryListener getDataTreeChangeListener() {
        return MacVrfEntryListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry) {
        LOG.info("ADD: Adding DMAC Entry for MACVrfEntry {} ", macVrfEntry);
        String elanName = elanEvpnUtils.getElanNameByMacvrfiid(instanceIdentifier);
        if (elanName == null) {
            LOG.error("ADD: Error : elanName is null for iid {}", instanceIdentifier);
            return;
        }
        List<DpnInterfaces> dpnInterfaceLists = elanUtils.getInvolvedDpnsInElan(elanName);
        if (dpnInterfaceLists == null) {
            /*TODO(Riyaz) : Will this case ever hit?*/
            LOG.error("ADD: Error : dpnInterfaceLists is null for elan {}", elanName);
            return;
        }

        //TODO(Riyaz) : Check if accessing first nexthop address is right solution
        String nexthopIP = macVrfEntry.getRoutePaths().get(0).getNexthopAddress();
        Long elanTag = elanEvpnUtils.getElanTagByMacvrfiid(instanceIdentifier);
        String dstMacAddress = macVrfEntry.getMac();
        long vni = macVrfEntry.getL2vni();
        for (DpnInterfaces dpnInterfaces : dpnInterfaceLists) {
            BigInteger dpId = dpnInterfaces.getDpId();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            try {
                LOG.info("ADD: Build DMAC flow with dpId {}, nexthopIP {}, elanTag {}, \n"
                                + "vni {}, dstMacAddress {}, displayName {} ",
                        dpId, nexthopIP, elanTag, vni, dstMacAddress, elanName);

                Flow flow = elanEvpnFlowUtils.evpnBuildDmacFlowForExternalRemoteMac(dpId, nexthopIP, elanTag,
                        vni, dstMacAddress, elanName);
                futures.add(mdsalManager.installFlow(dpId, flow));
            } catch (ElanException e) {
                LOG.error("ADD: Error : unable to build DMAC flow for elan {}, exception ", elanName, e);
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry, MacVrfEntry t1) {

    }

    @Override
    protected void remove(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry) {
        LOG.info("REMOVE: Removing DMAC Entry for MACVrfEntry {} ", macVrfEntry);

        String elanName = elanEvpnUtils.getElanNameByMacvrfiid(instanceIdentifier);
        if (elanName == null) {
            LOG.error("REMOVE: Error : elanName is null for iid {}", instanceIdentifier);
            return;
        }
        List<DpnInterfaces> dpnInterfaceLists = elanUtils.getInvolvedDpnsInElan(elanName);
        if (dpnInterfaceLists == null) {
            /*TODO(Riyaz) : Will this case ever hit?*/
            LOG.error("DELETE: Error : dpnInterfaceLists is null for elan {}", elanName);
            return;
        }

        //TODO(Riyaz) : Check if accessing first nexthop address is right
        String nexthopIP = macVrfEntry.getRoutePaths().get(0).getNexthopAddress();
        Long elanTag = elanEvpnUtils.getElanTagByMacvrfiid(instanceIdentifier);
        String macToRemove = macVrfEntry.getMac();
        for (DpnInterfaces dpnInterfaces : dpnInterfaceLists) {
            BigInteger dpId = dpnInterfaces.getDpId();
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            LOG.info("REMOVE: Deleting DMAC Flows to external MAC. elanTag {}, dpId {},\n"
                    + "nexthopIP {}, macToRemove {}", elanTag, dpId, nexthopIP, macToRemove);

            elanEvpnFlowUtils.evpnDeleteDmacFlowsToExternalMac(elanTag, dpId, nexthopIP, macToRemove);
            return;
        }
    }

}

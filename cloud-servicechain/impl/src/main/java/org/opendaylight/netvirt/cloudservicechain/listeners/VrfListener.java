/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnPseudoPortCache;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for VrfEntry creations or removal with the purpose of including the
 * new label in the LFIB (or removing it) pointing to the VpnPseudoPort.
 *
 */
@Singleton
public class VrfListener extends AsyncDataTreeChangeListenerBase<VrfEntry, VrfListener> {

    private static final Logger LOG = LoggerFactory.getLogger(VrfListener.class);
    private final DataBroker broker;
    private final IMdsalApiManager mdsalMgr;
    private final VpnPseudoPortCache vpnPseudoPortCache;

    @Inject
    public VrfListener(DataBroker broker, IMdsalApiManager mdsalMgr, VpnPseudoPortCache vpnPseudoPortCache) {
        this.broker = broker;
        this.mdsalMgr = mdsalMgr;
        this.vpnPseudoPortCache = vpnPseudoPortCache;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<VrfEntry> getWildCardPath() {
        return InstanceIdentifier.create(FibEntries.class)
                .child(VpnInstanceNames.class)
                .child(VrfTables.class).child(VrfEntry.class);
    }


    @Override
    protected void remove(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntryDeleted) {
        LOG.debug("VrfEntry removed: id={}  vrfEntry=[ destination={}, route-paths=[{}]]",
                  identifier, vrfEntryDeleted.getDestPrefix(), vrfEntryDeleted.getRoutePaths());
        String vpnName = identifier.firstKeyOf(VpnInstanceNames.class).getVpnInstanceName();
        String vpnRd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        programLabelInAllVpnDpns(vpnName, vpnRd, vrfEntryDeleted, NwConstants.DEL_FLOW);
    }

    @Override
    protected void update(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update) {
        LOG.debug("VrfEntry updated: id={}  vrfEntry=[ destination={}, route-paths=[{}]]",
                  identifier, update.getDestPrefix(), update.getRoutePaths());
        List<Long> originalLabels = getUniqueLabelList(original);
        List<Long> updateLabels = getUniqueLabelList(update);
        if (!updateLabels.equals(originalLabels)) {
            remove(identifier, original);
            add(identifier, update);
        }
    }

    @Override
    protected void add(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntryAdded) {
        LOG.debug("VrfEntry added: id={}  vrfEntry=[ destination={}, route-paths=[{}]]",
                  identifier, vrfEntryAdded.getDestPrefix(), vrfEntryAdded.getRoutePaths());
        String vpnName = identifier.firstKeyOf(VpnInstanceNames.class).getVpnInstanceName();
        String vpnRd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        programLabelInAllVpnDpns(vpnName, vpnRd, vrfEntryAdded, NwConstants.ADD_FLOW);
    }

    /**
     * Adds or Removes a VPN's route in all the DPNs where the VPN has footprint.
     *
     * @param vpnRd Route-Distinguisher of the VPN
     * @param vrfEntry The route to add or remove
     * @param addOrRemove States if the route must be added or removed
     */
    protected void programLabelInAllVpnDpns(String vpnName, String vpnRd, VrfEntry vrfEntry, int addOrRemove) {
        Long vpnPseudoLPortTag = vpnPseudoPortCache.get(vpnRd);
        if (vpnPseudoLPortTag == null) {
            LOG.debug("Vpn with rd={} not related to any VpnPseudoPort", vpnRd);
            return;
        }

        Optional<VpnInstanceOpDataEntry> vpnOpData = VpnServiceChainUtils.getVpnInstanceOpData(broker, vpnName);
        if (! vpnOpData.isPresent()) {
            if (addOrRemove == NwConstants.ADD_FLOW) {
                LOG.error("VrfEntry added: Could not find operational data for VPN with RD={}", vpnRd);
            } else {
                LOG.warn("VrfEntry removed: No Operational data found for VPN with RD={}. No further action", vpnRd);
            }

            return;
        }

        Collection<VpnToDpnList> vpnToDpnList = vpnOpData.get().getVpnToDpnList();
        if (vpnToDpnList == null || vpnToDpnList.isEmpty()) {
            LOG.warn("Empty VpnToDpnlist found in Operational for VPN with RD={}. No label will be {}",
                     vpnRd, addOrRemove == NwConstants.ADD_FLOW ? "programmed" : "cleaned");
            return;
        }

        for (VpnToDpnList dpnInVpn : vpnToDpnList) {
            BigInteger dpnId = dpnInVpn.getDpnId();
            VpnServiceChainUtils.programLFibEntriesForSCF(mdsalMgr, dpnId, Collections.singletonList(vrfEntry),
                                                          (int) vpnPseudoLPortTag.longValue(), addOrRemove);
        }
    }

    private List<Long> getUniqueLabelList(VrfEntry original) {
        List<RoutePaths> vrfRoutePaths = original.getRoutePaths();
        if (vrfRoutePaths == null || vrfRoutePaths.isEmpty()) {
            return Collections.emptyList();
        }

        return vrfRoutePaths.stream()
                            .filter(rPath -> rPath.getLabel() != null).map(RoutePaths::getLabel)
                            .distinct().sorted().collect(Collectors.toList());
    }

    @Override
    protected VrfListener getDataTreeChangeListener() {
        return VrfListener.this;
    }
}

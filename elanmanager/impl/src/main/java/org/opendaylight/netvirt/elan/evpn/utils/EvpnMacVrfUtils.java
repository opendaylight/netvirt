/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.evpn.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnMacVrfUtils {

    private static final Logger LOG = LoggerFactory.getLogger(EvpnMacVrfUtils.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IdManagerService idManager;
    private final ElanEvpnFlowUtils elanEvpnFlowUtils;
    private final IMdsalApiManager mdsalManager;
    private final EvpnUtils evpnUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanUtils elanUtils;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public EvpnMacVrfUtils(final DataBroker dataBroker, final IdManagerService idManager,
            final ElanEvpnFlowUtils elanEvpnFlowUtils, final IMdsalApiManager mdsalManager, final EvpnUtils evpnUtils,
            final JobCoordinator jobCoordinator, final ElanUtils elanUtils, final ElanInstanceCache elanInstanceCache) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = idManager;
        this.elanEvpnFlowUtils = elanEvpnFlowUtils;
        this.mdsalManager = mdsalManager;
        this.evpnUtils = evpnUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanUtils = elanUtils;
        this.elanInstanceCache = elanInstanceCache;
    }

    @Nullable
    private Uint32 getElanTagByMacvrfiid(InstanceIdentifier<MacVrfEntry> macVrfEntryIid) {
        String elanName = getElanNameByMacvrfiid(macVrfEntryIid);
        if (elanName == null) {
            LOG.error("getElanTag: elanName is NULL for iid = {}", macVrfEntryIid);
        }
        ElanInstance elanInstance = elanInstanceCache.get(elanName).orElse(null);
        if (elanInstance == null) {
            return null;
        }

        Uint32 elanTag = elanInstance.getElanTag();
        if (elanTag == null || elanTag.longValue() == 0L) {
            elanTag = ElanUtils.retrieveNewElanTag(idManager, elanName);
        }
        return elanTag;
    }

    public String getElanNameByMacvrfiid(InstanceIdentifier<MacVrfEntry> instanceIdentifier) {
        try (ReadTransaction tx = dataBroker.newReadOnlyTransaction()) {
            String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
            String elanName = null;
            InstanceIdentifier<EvpnRdToNetwork> iidEvpnRdToNet =
                    InstanceIdentifier.builder(EvpnRdToNetworks.class).child(EvpnRdToNetwork.class,
                            new EvpnRdToNetworkKey(rd)).build();
            try {
                Optional<EvpnRdToNetwork> evpnRdToNetwork =
                        tx.read(LogicalDatastoreType.CONFIGURATION, iidEvpnRdToNet).get();
                if (evpnRdToNetwork.isPresent()) {
                    elanName = evpnRdToNetwork.get().getNetworkId();
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("getElanName: unable to read elanName, exception ", e);
            }
            return elanName;
        }
    }

    public InstanceIdentifier<MacVrfEntry> getMacVrfEntryIid(String rd, MacVrfEntry macVrfEntry) {
        return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
                .child(MacVrfEntry.class, macVrfEntry.key());
    }

    public void updateEvpnDmacFlows(final ElanInstance elanInstance, final boolean install) {
        String rd = evpnUtils.getEvpnRd(elanInstance);
        if (rd == null) {
            return;
        }
        final InstanceIdentifier<VrfTables> iid = InstanceIdentifier.create(FibEntries.class)
                .child(VrfTables.class, new VrfTablesKey(rd));
        evpnUtils.asyncReadAndExecute(LogicalDatastoreType.CONFIGURATION, iid,
                new StringBuilder(elanInstance.getElanInstanceName()).append(":").append(rd).toString(),
            (vrfTablesOptional) -> {
                if (!vrfTablesOptional.isPresent()) {
                    return null;
                }
                List<MacVrfEntry> macVrfEntries = vrfTablesOptional.get().getMacVrfEntry();
                if (macVrfEntries == null || macVrfEntries.isEmpty()) {
                    return null;
                }
                for (MacVrfEntry macVrfEntry : macVrfEntries) {
                    InstanceIdentifier<MacVrfEntry> macVrfEntryIid = getMacVrfEntryIid(rd, macVrfEntry);
                    if (install) {
                        addEvpnDmacFlowOnAttach(macVrfEntryIid, macVrfEntry, elanInstance);
                    } else {
                        removeEvpnDmacFlowOnDetach(macVrfEntryIid, macVrfEntry, elanInstance);
                    }
                }
                return null;
            });
    }

    public boolean checkEvpnAttachedToNet(String elanName) {
        ElanInstance elanInfo = elanInstanceCache.get(elanName).orElse(null);
        String evpnName = EvpnUtils.getEvpnNameFromElan(elanInfo);
        if (evpnName == null) {
            LOG.error("Error : evpnName is null for elanName {}", elanName);
            return false;
        }
        return true;
    }

    public void addEvpnDmacFlow(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry) {
        String elanName = getElanNameByMacvrfiid(instanceIdentifier);
        if (elanName == null) {
            LOG.error("Error : elanName is null for iid {}", instanceIdentifier);
            return;
        }

        List<DpnInterfaces> dpnInterfaceLists = elanUtils.getElanDPNByName(elanName);
        if (checkEvpnAttachedToNet(elanName)) {
            //TODO(Riyaz) : Check if accessing first nexthop address is right solution
            String nexthopIP = macVrfEntry.getRoutePaths().get(0).getNexthopAddress();
            IpAddress ipAddress = new IpAddress(new Ipv4Address(nexthopIP));
            Uint32 elanTag = getElanTagByMacvrfiid(instanceIdentifier);
            if (elanTag == null) {
                return;
            }

            String dstMacAddress = macVrfEntry.getMac();
            long vni = macVrfEntry.getL2vni().toJava();
            jobCoordinator.enqueueJob(dstMacAddress, () -> Collections.singletonList(
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION,
                    tx -> dpnInterfaceLists.forEach(dpnInterfaces -> {
                        Uint64 dpId = dpnInterfaces.getDpId();
                        LOG.info("ADD: Build DMAC flow with dpId {}, nexthopIP {}, elanTag {},"
                                + "vni {}, dstMacAddress {}, elanName {} ",
                            dpId, nexthopIP, elanTag, vni, dstMacAddress, elanName);
                        ElanEvpnFlowUtils.EvpnDmacFlowBuilder dmacFlowBuilder =
                            new ElanEvpnFlowUtils.EvpnDmacFlowBuilder();
                        dmacFlowBuilder.setDpId(dpId).setNexthopIP(ipAddress.toString()).setElanTag(elanTag.longValue())
                            .setVni(vni).setDstMacAddress(dstMacAddress).setElanName(elanName);
                        Flow flow =
                            elanEvpnFlowUtils.evpnBuildDmacFlowForExternalRemoteMac(dmacFlowBuilder.build());

                        mdsalManager.addFlow(tx, dpId, flow);
                    }))), ElanConstants.JOB_MAX_RETRIES);
        }
    }

    public void removeEvpnDmacFlow(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry) {
        String elanName = getElanNameByMacvrfiid(instanceIdentifier);
        if (elanName == null) {
            LOG.error("Error : elanName is null for iid {}", instanceIdentifier);
            return;
        }
        List<DpnInterfaces> dpnInterfaceLists = elanUtils.getElanDPNByName(elanName);

        //if (checkEvpnAttachedToNet(elanName)) {
        //TODO(Riyaz) : Check if accessing first nexthop address is right
        String nexthopIP = macVrfEntry.getRoutePaths().get(0).getNexthopAddress();
        IpAddress ipAddress = new IpAddress(new Ipv4Address(nexthopIP));
        Uint32 elanTag = getElanTagByMacvrfiid(instanceIdentifier);
        if (elanTag == null) {
            return;
        }

        String macToRemove = macVrfEntry.getMac();
        jobCoordinator.enqueueJob(macToRemove, () -> {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            dpnInterfaceLists.forEach(dpnInterfaces -> {
                Uint64 dpId = dpnInterfaces.getDpId();
                ElanEvpnFlowUtils.EvpnDmacFlowBuilder dmacFlowBuilder = new ElanEvpnFlowUtils.EvpnDmacFlowBuilder();
                dmacFlowBuilder.setDpId(dpId).setNexthopIP(ipAddress.toString()).setElanTag(elanTag.longValue())
                        .setDstMacAddress(macToRemove);
                LOG.info("REMOVE: Deleting DMAC Flows for external MAC. elanTag {}, dpId {},"
                        + "nexthopIP {}, macToRemove {}", elanTag, dpId, nexthopIP, macToRemove);
                futures.addAll(elanEvpnFlowUtils.evpnDeleteDmacFlowsToExternalMac(dmacFlowBuilder.build()));
            });
            return futures;
        }, ElanConstants.JOB_MAX_RETRIES);
    }

    public void addEvpnDmacFlowOnAttach(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry,
                                        ElanInstance elanInstance) {
        //String elanName = getElanNameByMacvrfiid(instanceIdentifier);
        if (elanInstance == null) {
            LOG.error("Error : elanName is null for iid {}", instanceIdentifier);
            return;
        }

        String elanName = elanInstance.getElanInstanceName();
        List<DpnInterfaces> dpnInterfaceLists = elanUtils.getElanDPNByName(elanName);

        if (checkEvpnAttachedToNet(elanName)) {
            String nexthopIP = getRoutePathNexthopIp(macVrfEntry);
            if (nexthopIP == null) {
                LOG.debug("nexthopIP is null for iid {}", instanceIdentifier);
                return;
            }
            IpAddress ipAddress = new IpAddress(new Ipv4Address(nexthopIP));
            Uint32 elanTag = elanInstance.getElanTag();
            String dstMacAddress = macVrfEntry.getMac();
            long vni = macVrfEntry.getL2vni().toJava();
            jobCoordinator.enqueueJob(dstMacAddress, () -> Collections.singletonList(
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION,
                    tx -> dpnInterfaceLists.forEach(dpnInterfaces -> {
                        Uint64 dpId = dpnInterfaces.getDpId();
                        LOG.info("ADD: Build DMAC flow with dpId {}, nexthopIP {}, elanTag {},"
                                + "vni {}, dstMacAddress {}, elanName {} ",
                            dpId, nexthopIP, elanTag, vni, dstMacAddress, elanName);
                        ElanEvpnFlowUtils.EvpnDmacFlowBuilder dmacFlowBuilder =
                            new ElanEvpnFlowUtils.EvpnDmacFlowBuilder();
                        dmacFlowBuilder.setDpId(dpId).setNexthopIP(ipAddress.toString()).setElanTag(elanTag.longValue())
                            .setVni(vni).setDstMacAddress(dstMacAddress).setElanName(elanName);
                        Flow flow = elanEvpnFlowUtils.evpnBuildDmacFlowForExternalRemoteMac(dmacFlowBuilder.build());
                        mdsalManager.addFlow(tx, dpId, flow);
                    }))), ElanConstants.JOB_MAX_RETRIES);
        }
    }

    @Nullable
    public String getRoutePathNexthopIp(MacVrfEntry macVrfEntry) {
        if (macVrfEntry.getRoutePaths() == null || macVrfEntry.getRoutePaths().isEmpty()) {
            LOG.debug("RoutePaths is null or empty for macvrfentry {}", macVrfEntry);
            return null;
        }
        return macVrfEntry.getRoutePaths().get(0).getNexthopAddress();
    }

    public void removeEvpnDmacFlowOnDetach(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry,
                                           ElanInstance elanInstance) {
        //String elanName = getElanNameByMacvrfiid(instanceIdentifier);
        if (elanInstance == null) {
            LOG.error("Error : elanInstance is null for iid {}", instanceIdentifier);
            return;
        }
        List<DpnInterfaces> dpnInterfaceLists = elanUtils.getElanDPNByName(elanInstance.getElanInstanceName());

        //if (checkEvpnAttachedToNet(elanName)) {
        String nexthopIP = getRoutePathNexthopIp(macVrfEntry);
        if (nexthopIP == null) {
            LOG.debug("nexthopIP is null for iid {}", instanceIdentifier);
            return;
        }
        IpAddress ipAddress = new IpAddress(new Ipv4Address(nexthopIP));
        Uint32 elanTag = elanInstance.getElanTag();
        String macToRemove = macVrfEntry.getMac();
        jobCoordinator.enqueueJob(macToRemove, () -> {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            dpnInterfaceLists.forEach(dpnInterfaces -> {
                Uint64 dpId = dpnInterfaces.getDpId();
                ElanEvpnFlowUtils.EvpnDmacFlowBuilder dmacFlowBuilder = new ElanEvpnFlowUtils.EvpnDmacFlowBuilder();
                dmacFlowBuilder.setDpId(dpId).setNexthopIP(ipAddress.toString()).setElanTag(elanTag.longValue())
                        .setDstMacAddress(macToRemove);
                LOG.info("REMOVE: Deleting DMAC Flows for external MAC. elanTag {}, dpId {},"
                        + "nexthopIP {}, macToRemove {}", elanTag, dpId, nexthopIP, macToRemove);
                elanEvpnFlowUtils.evpnDeleteDmacFlowsToExternalMac(dmacFlowBuilder.build());
            });
            return futures;
        }, ElanConstants.JOB_MAX_RETRIES);
    }
}

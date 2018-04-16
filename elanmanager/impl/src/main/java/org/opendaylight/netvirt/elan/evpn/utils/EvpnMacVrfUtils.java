/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.evpn.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNamesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.to.networks.EvpnToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.to.networks.EvpnToNetworkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnMacVrfUtils {

    private static final Logger LOG = LoggerFactory.getLogger(EvpnMacVrfUtils.class);
    private final DataBroker dataBroker;
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
        this.idManager = idManager;
        this.elanEvpnFlowUtils = elanEvpnFlowUtils;
        this.mdsalManager = mdsalManager;
        this.evpnUtils = evpnUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanUtils = elanUtils;
        this.elanInstanceCache = elanInstanceCache;
    }

    @Nullable
    private Long getElanTagByMacvrfiid(InstanceIdentifier<MacVrfEntry> macVrfEntryIid) {
        String elanName = getElanNameByMacvrfiid(macVrfEntryIid);
        if (elanName == null) {
            LOG.error("getElanTag: elanName is NULL for iid = {}", macVrfEntryIid);
        }
        ElanInstance elanInstance = elanInstanceCache.get(elanName).orNull();
        if (elanInstance == null) {
            return null;
        }

        Long elanTag = elanInstance.getElanTag();
        if (elanTag == null || elanTag == 0L) {
            elanTag = ElanUtils.retrieveNewElanTag(idManager, elanName);
        }
        return elanTag;
    }

    public String getElanNameByMacvrfiid(InstanceIdentifier<MacVrfEntry> instanceIdentifier) {
        try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
            String vpnName = instanceIdentifier.firstKeyOf(VpnInstanceNames.class).getVpnInstanceName();
            String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
            String elanName = null;
            InstanceIdentifier<EvpnToNetwork> iidEvpnRdToNet =
                    InstanceIdentifier.builder(EvpnToNetworks.class).child(EvpnToNetwork.class,
                            new EvpnToNetworkKey(vpnName)).build();
            try {
                Optional<EvpnToNetwork> evpnRdToNetwork =
                        tx.read(LogicalDatastoreType.CONFIGURATION, iidEvpnRdToNet).checkedGet();
                if (evpnRdToNetwork.isPresent()) {
                    elanName = evpnRdToNetwork.get().getNetworkId();
                }
            } catch (ReadFailedException e) {
                LOG.error("getElanName: unable to read elanName, exception ", e);
            }
            return elanName;
        }
    }

    public InstanceIdentifier<MacVrfEntry> getMacVrfEntryIid(String vpnName, String rd, MacVrfEntry macVrfEntry) {
        return InstanceIdentifier.create(FibEntries.class)
                .child(VpnInstanceNames.class, new VpnInstanceNamesKey(vpnName))
                .child(VrfTables.class, new VrfTablesKey(rd))
                .child(MacVrfEntry.class, macVrfEntry.getKey());
    }

    public void updateEvpnDmacFlows(final ElanInstance elanInstance, final boolean install) {
        String vpnName = evpnUtils.getEvpnNameFromElan(elanInstance);
        String rd = evpnUtils.getEvpnRd(elanInstance);
        if (rd == null) {
            return;
        }
        final InstanceIdentifier<VrfTables> iid = InstanceIdentifier.create(FibEntries.class)
                .child(VpnInstanceNames.class, new VpnInstanceNamesKey(vpnName))
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
                    InstanceIdentifier<MacVrfEntry> macVrfEntryIid = getMacVrfEntryIid(vpnName, rd, macVrfEntry);
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
        ElanInstance elanInfo = elanInstanceCache.get(elanName).orNull();
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
            Long elanTag = getElanTagByMacvrfiid(instanceIdentifier);
            if (elanTag == null) {
                return;
            }

            String dstMacAddress = macVrfEntry.getMac();
            long vni = macVrfEntry.getL2vni();
            jobCoordinator.enqueueJob(dstMacAddress, () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                dpnInterfaceLists.forEach(dpnInterfaces -> {
                    BigInteger dpId = dpnInterfaces.getDpId();
                    LOG.info("ADD: Build DMAC flow with dpId {}, nexthopIP {}, elanTag {},"
                                    + "vni {}, dstMacAddress {}, elanName {} ",
                            dpId, nexthopIP, elanTag, vni, dstMacAddress, elanName);
                    ElanEvpnFlowUtils.EvpnDmacFlowBuilder dmacFlowBuilder = new ElanEvpnFlowUtils.EvpnDmacFlowBuilder();
                    dmacFlowBuilder.setDpId(dpId).setNexthopIP(ipAddress.toString()).setElanTag(elanTag).setVni(vni)
                            .setDstMacAddress(dstMacAddress).setElanName(elanName);
                    Flow flow = elanEvpnFlowUtils.evpnBuildDmacFlowForExternalRemoteMac(dmacFlowBuilder.build());

                    futures.add(mdsalManager.installFlow(dpId, flow));
                });
                return futures;
            }, ElanConstants.JOB_MAX_RETRIES);
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
        Long elanTag = getElanTagByMacvrfiid(instanceIdentifier);
        if (elanTag == null) {
            return;
        }

        String macToRemove = macVrfEntry.getMac();
        jobCoordinator.enqueueJob(macToRemove, () -> {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            dpnInterfaceLists.forEach(dpnInterfaces -> {
                BigInteger dpId = dpnInterfaces.getDpId();
                ElanEvpnFlowUtils.EvpnDmacFlowBuilder dmacFlowBuilder = new ElanEvpnFlowUtils.EvpnDmacFlowBuilder();
                dmacFlowBuilder.setDpId(dpId).setNexthopIP(ipAddress.toString()).setElanTag(elanTag)
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
            Long elanTag = elanInstance.getElanTag();
            String dstMacAddress = macVrfEntry.getMac();
            long vni = macVrfEntry.getL2vni();
            jobCoordinator.enqueueJob(dstMacAddress, () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                dpnInterfaceLists.forEach(dpnInterfaces -> {
                    BigInteger dpId = dpnInterfaces.getDpId();
                    LOG.info("ADD: Build DMAC flow with dpId {}, nexthopIP {}, elanTag {},"
                                    + "vni {}, dstMacAddress {}, elanName {} ",
                            dpId, nexthopIP, elanTag, vni, dstMacAddress, elanName);
                    ElanEvpnFlowUtils.EvpnDmacFlowBuilder dmacFlowBuilder = new ElanEvpnFlowUtils.EvpnDmacFlowBuilder();
                    dmacFlowBuilder.setDpId(dpId).setNexthopIP(ipAddress.toString()).setElanTag(elanTag).setVni(vni)
                            .setDstMacAddress(dstMacAddress).setElanName(elanName);
                    Flow flow = elanEvpnFlowUtils.evpnBuildDmacFlowForExternalRemoteMac(dmacFlowBuilder.build());
                    futures.add(mdsalManager.installFlow(dpId, flow));
                });
                return futures;
            }, ElanConstants.JOB_MAX_RETRIES);
        }
    }

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
        Long elanTag = elanInstance.getElanTag();
        String macToRemove = macVrfEntry.getMac();
        jobCoordinator.enqueueJob(macToRemove, () -> {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            dpnInterfaceLists.forEach(dpnInterfaces -> {
                BigInteger dpId = dpnInterfaces.getDpId();
                ElanEvpnFlowUtils.EvpnDmacFlowBuilder dmacFlowBuilder = new ElanEvpnFlowUtils.EvpnDmacFlowBuilder();
                dmacFlowBuilder.setDpId(dpId).setNexthopIP(ipAddress.toString()).setElanTag(elanTag)
                        .setDstMacAddress(macToRemove);
                LOG.info("REMOVE: Deleting DMAC Flows for external MAC. elanTag {}, dpId {},"
                        + "nexthopIP {}, macToRemove {}", elanTag, dpId, nexthopIP, macToRemove);
                elanEvpnFlowUtils.evpnDeleteDmacFlowsToExternalMac(dmacFlowBuilder.build());
            });
            return futures;
        }, ElanConstants.JOB_MAX_RETRIES);
    }
}

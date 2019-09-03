/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanDmacUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DpnDmacJob extends DataStoreJob {
    private static final Logger LOG = LoggerFactory.getLogger(DpnDmacJob.class);
    private String elanName;
    private DpnInterfaces dpnInterfaces;
    private ElanL2GatewayUtils elanL2GatewayUtils;
    private ElanClusterUtils elanClusterUtils;
    private ElanInstanceCache elanInstanceCache;
    private ElanDmacUtils elanDmacUtils;
    private Scheduler scheduler;
    private JobCoordinator jobCoordinator;
    private String nodeId;
    private boolean added;

    public DpnDmacJob(String elanName,
                      DpnInterfaces dpnInterfaces,
                      String nodeId,
                      boolean added,
                      ElanL2GatewayUtils elanL2GatewayUtils, ElanClusterUtils elanClusterUtils,
                      ElanInstanceCache elanInstanceCache, ElanDmacUtils elanDmacUtils,
                      Scheduler scheduler, JobCoordinator jobCoordinator) {
        super(elanName + ":l2gwdmac:" + dpnInterfaces.getDpId().toString() + ":" + nodeId,
                scheduler, jobCoordinator);
        this.elanName = elanName;
        this.dpnInterfaces = dpnInterfaces;
        this.nodeId = nodeId;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.elanInstanceCache = elanInstanceCache;
        this.elanDmacUtils = elanDmacUtils;
        this.scheduler = scheduler;
        this.jobCoordinator = jobCoordinator;
        this.added = added;
    }

    public void submit() {
        elanClusterUtils.runOnlyInOwnerNode(super.jobKey,"Dpn Dmac Job", this);
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        ElanInstance elan = elanInstanceCache.get(elanName).orNull();
        if (elan == null) {
            LOG.error("failed.elan.not.found." + jobKey);
            return null;
        }
        L2GatewayDevice device = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, nodeId);
        List<ListenableFuture<Void>> fts = new ArrayList<>();
        ElanL2GatewayUtils ucastUtils = elanL2GatewayUtils;
        if (added) {
            fts = ucastUtils.installDmacFlowsOnDpn(dpnInterfaces.getDpId(), device, elan,
                    dpnInterfaces.getInterfaces().get(0));
        } else {
            List<MacAddress> localMacs = ucastUtils.getL2GwDeviceLocalMacs(elan.getElanInstanceName(), device);
            if (localMacs != null && !localMacs.isEmpty()) {
                for (MacAddress mac : localMacs) {
                    fts.addAll(elanDmacUtils.deleteDmacFlowsToExternalMac(elan.getElanTag(), dpnInterfaces.getDpId(),
                            nodeId, mac.getValue().toLowerCase(Locale.getDefault())));
                }
            }
        }
        if (!fts.isEmpty()) {
            processResult(fts.get(0));
        }
        return null;
    }

    public static void uninstallDmacFromL2gws(String elanName,
                                              DpnInterfaces dpnInterfaces,
                                              ElanL2GatewayUtils elanL2GatewayUtils,
                                              ElanClusterUtils elanClusterUtils,
                                              ElanInstanceCache elanInstanceCache,
                                              ElanDmacUtils elanDmacUtils,
                                              Scheduler scheduler,
                                              JobCoordinator jobCoordinator) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).forEach(l2GatewayDevice -> {
            new DpnDmacJob(elanName, dpnInterfaces, l2GatewayDevice.getHwvtepNodeId(), false, elanL2GatewayUtils,
                    elanClusterUtils, elanInstanceCache, elanDmacUtils, scheduler, jobCoordinator).submit();
        });
    }

    public static void installDmacFromL2gws(String elanName,
                                            DpnInterfaces dpnInterfaces,
                                            ElanL2GatewayUtils elanL2GatewayUtils,
                                            ElanClusterUtils elanClusterUtils,
                                            ElanInstanceCache elanInstanceCache,
                                            ElanDmacUtils elanDmacUtils,
                                            Scheduler scheduler,
                                            JobCoordinator jobCoordinator) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).forEach(l2GatewayDevice -> {
            new DpnDmacJob(elanName, dpnInterfaces, l2GatewayDevice.getHwvtepNodeId(), true, elanL2GatewayUtils,
                    elanClusterUtils, elanInstanceCache, elanDmacUtils, scheduler, jobCoordinator).submit();
        });
    }
}
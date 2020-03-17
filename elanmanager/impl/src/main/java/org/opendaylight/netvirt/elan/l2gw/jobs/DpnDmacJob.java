/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanRefUtil;
import org.opendaylight.netvirt.elan.utils.ElanDmacUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DpnDmacJob implements Callable<List<? extends ListenableFuture<?>>> {
    private static final Logger LOG = LoggerFactory.getLogger(DpnDmacJob.class);

    private String elanName;
    private DpnInterfaces dpnInterfaces;
    private ElanL2GatewayUtils elanL2GatewayUtils;
    private ElanDmacUtils elanDmacUtils;
    private final ElanRefUtil elanRefUtil;
    private String nodeId;
    private boolean added;
    protected String jobKey;

    public DpnDmacJob(String elanName,
                      DpnInterfaces dpnInterfaces,
                      String nodeId,
                      boolean added,
                      ElanL2GatewayUtils elanL2GatewayUtils, ElanRefUtil elanRefUtil,
                      ElanDmacUtils elanDmacUtils) {
        this.jobKey = ElanUtils.getBcGroupUpdateKey(elanName);
        this.elanName = elanName;
        this.dpnInterfaces = dpnInterfaces;
        this.nodeId = nodeId;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanRefUtil = elanRefUtil;
        this.elanDmacUtils = elanDmacUtils;
        this.added = added;
    }

    public void submit() {
        elanRefUtil.getElanClusterUtils().runOnlyInOwnerNode(this.jobKey,"Dpn Dmac Job", this);
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        ElanInstance elan = elanRefUtil.getElanInstanceCache().get(elanName).orElse(null);
        if (elan == null) {
            LOG.error("failed.elan.not.found.{}", jobKey);
            return null;
        }
        List<ListenableFuture<Void>> result = new ArrayList<>();
        L2GatewayDevice device = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, nodeId);
        if (added) {
            result.addAll(elanL2GatewayUtils.installDmacFlowsOnDpn(dpnInterfaces.getDpId(), device, elan,
                    dpnInterfaces.getInterfaces().get(0)));
        } else {
            Collection<MacAddress> localMacs = elanL2GatewayUtils.getL2GwDeviceLocalMacs(
                    elan.getElanInstanceName(), device);
            if (localMacs != null && !localMacs.isEmpty()) {
                for (MacAddress mac : localMacs) {
                    result.addAll(elanDmacUtils.deleteDmacFlowsToExternalMac(elan.getElanTag().toJava(),
                            dpnInterfaces.getDpId(), nodeId, mac.getValue().toLowerCase(Locale.getDefault())));
                }
            }
        }
        return result;
    }

    public static void uninstallDmacFromL2gws(String elanName,
                                              DpnInterfaces dpnInterfaces,
                                              ElanL2GatewayUtils elanL2GatewayUtils,
                                              ElanRefUtil elanRefUtil,
                                              ElanDmacUtils elanDmacUtils) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).forEach(l2GatewayDevice -> {
            new DpnDmacJob(elanName, dpnInterfaces, l2GatewayDevice.getHwvtepNodeId(), false, elanL2GatewayUtils,
                    elanRefUtil, elanDmacUtils).submit();
        });
    }

    public static void installDmacFromL2gws(String elanName,
                                            DpnInterfaces dpnInterfaces,
                                            ElanL2GatewayUtils elanL2GatewayUtils,
                                            ElanRefUtil elanRefUtil,
                                            ElanDmacUtils elanDmacUtils) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).forEach(l2GatewayDevice -> {
            new DpnDmacJob(elanName, dpnInterfaces, l2GatewayDevice.getHwvtepNodeId(), true, elanL2GatewayUtils,
                    elanRefUtil, elanDmacUtils).submit();
        });
    }
}

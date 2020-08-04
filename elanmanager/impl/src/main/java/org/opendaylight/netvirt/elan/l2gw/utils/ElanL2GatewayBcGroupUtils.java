/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elan.cache.ElanInstanceDpnsCache;
import org.opendaylight.netvirt.elan.l2gw.jobs.BcGroupUpdateJob;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The utility class to handle ELAN L2 Gateway related to multicast.
 */
@Singleton
public class ElanL2GatewayBcGroupUtils {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ElanL2GatewayBcGroupUtils.class);

    private ElanRefUtil elanRefUtil;
    private ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private IMdsalApiManager mdsalApiManager;
    private ElanInstanceDpnsCache elanInstanceDpnsCache;
    private ElanItmUtils elanItmUtils;

    @Inject
    public ElanL2GatewayBcGroupUtils(ElanRefUtil elanRefUtil,
                                     ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                     IMdsalApiManager mdsalApiManager,
                                     ElanInstanceDpnsCache elanInstanceDpnsCache,
                                     ElanItmUtils elanItmUtils) {
        this.elanRefUtil = elanRefUtil;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.mdsalApiManager = mdsalApiManager;
        this.elanInstanceDpnsCache = elanInstanceDpnsCache;
        this.elanItmUtils = elanItmUtils;
    }

    public List<ListenableFuture<Void>> updateBcGroupForAllDpns(String elanName,
                                                                L2GatewayDevice device,
                                                                boolean createCase) {
        BcGroupUpdateJob.updateAllBcGroups(elanName, createCase, null, device, elanRefUtil,
                elanL2GatewayMulticastUtils, mdsalApiManager, elanInstanceDpnsCache, elanItmUtils);
        //new BcGroupUpdateJob(elanName, createCase, null, device, elanRefUtil, elanL2GatewayMulticastUtils,
        //        mdsalApiManager, elanInstanceDpnsCache, elanItmUtils).submit();

        return Collections.emptyList();
    }

    public void updateRemoteBroadcastGroupForAllElanDpns(ElanInstance elanInfo) {
        List<DpnInterfaces> dpns = elanRefUtil.getElanUtils()
                .getInvolvedDpnsInElan(elanInfo.getElanInstanceName());
        LOG.debug("Invoking method ELAN Broadcast Groups for ELAN {}", elanInfo);
        for (DpnInterfaces dpn : dpns) {
            elanL2GatewayMulticastUtils.setupElanBroadcastGroups(elanInfo, dpn.getDpId());
        }
    }

}

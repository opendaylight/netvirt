/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanRefUtil;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BcGroupUpdateJob implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");

    private final String elanName;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanRefUtil elanRefUtil;
    private final ManagedNewTransactionRunner txRunner;
    protected String jobKey;
    private final boolean createCase;

    public BcGroupUpdateJob(String elanName,
                            ElanRefUtil elanRefUtil,
                            ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                            DataBroker dataBroker, boolean createCase) {
        this.jobKey = ElanUtils.getBcGroupUpdateKey(elanName);
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.elanName = elanName;
        this.elanRefUtil = elanRefUtil;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.createCase = createCase;
    }

    public void submit() {
        elanRefUtil.getElanClusterUtils().runOnlyInOwnerNode(this.jobKey, "BC Group Update Job", this);
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        Optional<ElanInstance> elanInstanceOptional =  elanRefUtil.getElanInstanceCache().get(elanName);
        if (elanInstanceOptional.isPresent()) {
            return Lists.newArrayList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                confTx -> elanL2GatewayMulticastUtils.updateRemoteBroadcastGroupForAllElanDpns(
                        elanInstanceOptional.get(), createCase, confTx)));
        }
        return null;
    }

    public static void updateAllBcGroups(String elanName,
                                         ElanRefUtil elanRefUtil,
                                         ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                         DataBroker dataBroker, boolean createCase) {
        new BcGroupUpdateJob(elanName, elanRefUtil, elanL2GatewayMulticastUtils, dataBroker, createCase).submit();
    }
}

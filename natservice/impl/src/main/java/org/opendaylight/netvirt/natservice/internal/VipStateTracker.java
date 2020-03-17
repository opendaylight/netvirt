/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.util.concurrent.FluentFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.cache.DataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NeutronVipStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.neutron.vip.states.VipState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.neutron.vip.states.VipStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.neutron.vip.states.VipStateKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;

@Singleton
public class VipStateTracker extends DataObjectCache<String, VipState> {

    private ManagedNewTransactionRunner txRunner;

    @Inject
    public VipStateTracker(DataBroker dataBroker, CacheProvider cacheProvider) {
        super(VipState.class,
            dataBroker,
            LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.builder(NeutronVipStates.class).child(VipState.class).build(),
            cacheProvider,
            (iid, vipState) -> vipState.key().getIp(),
            ip -> InstanceIdentifier.builder(NeutronVipStates.class).child(VipState.class,
                new VipStateKey(ip)).build());
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    public VipState buildVipState(String ip, Uint64 dpnId, String ifcName) {
        return new VipStateBuilder().setIp(ip).setDpnId(dpnId).setIfcName(ifcName).build();
    }

    public FluentFuture<Void> writeVipState(VipState vipState) {
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
            tx.put(InstanceIdentifier.builder(NeutronVipStates.class)
                            .child(VipState.class, vipState.key()).build(),
                    vipState, true);
        });
    }
}

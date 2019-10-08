/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.api;

import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yangtools.yang.common.Uint64;

public interface SnatServiceManager {

    enum Action {
        SNAT_ALL_SWITCH_ENBL,
        SNAT_ALL_SWITCH_DISBL,
        SNAT_ROUTER_ENBL,
        SNAT_ROUTER_DISBL,
        SNAT_ROUTER_UPDATE,
        CNT_ROUTER_ALL_SWITCH_ENBL,
        CNT_ROUTER_ALL_SWITCH_DISBL,
        CNT_ROUTER_ENBL,
        CNT_ROUTER_DISBL
    }

    void notify(TypedReadWriteTransaction<Configuration> confTx, Routers router, @Nullable Routers oldRouter,
                Uint64 primarySwitchId, @Nullable Uint64 dpnId, Action action)
        throws ExecutionException, InterruptedException;

}

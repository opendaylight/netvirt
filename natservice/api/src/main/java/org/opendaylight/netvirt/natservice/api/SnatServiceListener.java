/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.api;

import java.math.BigInteger;

import java.util.concurrent.ExecutionException;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;

public interface SnatServiceListener {

    /**
     * Adds snat flows for all dpns having ports on the router subnet.
     *
     * @param confTx The transaction to use.
     * @param routers the router.
     * @param primarySwitchId the primaryswitchId
     * @return returns success/failure.
     */
    boolean addSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        BigInteger primarySwitchId);

    /**
     * Adds snat flows for the dpnId.
     *
     * @param confTx The transaction to use.
     * @param routers the router.
     * @param primarySwitchId the primaryswitchId.
     * @param dpnId the dpnId for which the flows needs to be added/removed.
     * @return returns success/failure.
     */
    boolean addSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers, BigInteger primarySwitchId,
        BigInteger dpnId);

    /**
     * Removes snat flows for all dpns having ports on the router subnet.
     *
     * @param confTx The transaction to use.
     * @param routers the router.
     * @param primarySwitchId the primaryswitchId
     * @return returns success/failure.
     */
    boolean removeSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        BigInteger primarySwitchId) throws ExecutionException, InterruptedException;

    /**
     * Removes snat flows for the dpnId.
     *
     * @param confTx The transaction to use.
     * @param routers the router.
     * @param primarySwitchId the primaryswitchId.
     * @param dpnId the dpnId for which the flows needs to be added/removed.
     * @return returns success/failure.
     */
    boolean removeSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers, BigInteger primarySwitchId,
        BigInteger dpnId) throws ExecutionException, InterruptedException;

}

/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.scaleinservice.api;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;

import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;

public interface TombstonedNodeManager {

    /**
     * Tells if the supplied dpn is getting scaled in or not.
     * @param dpnId dpn id
     * @return true if the supllied dpn is getting scaled in
     * @throws ReadFailedException throws read failed exception
     */
    boolean isDpnTombstoned(BigInteger dpnId) throws ReadFailedException;

    /**
     * Add the listener callback which will be invoked upon recovery of scaled in dpn.
     * @param callback callback to be invoked on recovery
     */
    void addOnRecoveryCallback(Function<BigInteger, Void> callback);

    /**
     * Filters the list of dpns which are not scaled in.
     * @param dpns the input list of dpns
     * @return filtered list of dpns which are not scaled in
     * @throws ReadFailedException throws read failed exception
     */
    List<BigInteger> filterTombStoned(List<BigInteger> dpns) throws ReadFailedException;
}

/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import java.math.BigInteger;

public enum AclConntrackClassifierType {
    CONNTRACK_SUPPORTED(BigInteger.ZERO),
    NON_CONNTRACK_SUPPORTED(BigInteger.ONE);

    private final BigInteger conntrackClassifierFlag;

    AclConntrackClassifierType(BigInteger conntrackClassifierFlag) {
        this.conntrackClassifierFlag = conntrackClassifierFlag;
    }

    public BigInteger getValue() {
        return this.conntrackClassifierFlag;
    }
}

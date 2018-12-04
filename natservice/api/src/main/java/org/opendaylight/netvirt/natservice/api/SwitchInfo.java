/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.api;

import java.math.BigInteger;
import java.util.Set;

public final class SwitchInfo {

    BigInteger dpnId;

    Set<String> providerNet;

    public BigInteger getDpnId() {
        return dpnId;
    }

    public void setDpnId(BigInteger dpnId) {
        this.dpnId = dpnId;
    }

    public Set<String> getProviderNet() {
        return providerNet;
    }

    public void setProviderNet(Set<String> providerNet) {
        this.providerNet = providerNet;
    }


}

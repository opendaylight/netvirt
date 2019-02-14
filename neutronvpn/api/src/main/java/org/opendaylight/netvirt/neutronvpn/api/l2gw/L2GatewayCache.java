/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.api.l2gw;

import java.util.Collection;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public interface L2GatewayCache {
    @NonNull
    L2GatewayDevice addOrGet(@NonNull String deviceName);

    @Nullable
    L2GatewayDevice remove(String deviceName);

    @Nullable
    L2GatewayDevice get(String deviceName);

    @NonNull
    Collection<L2GatewayDevice> getAll();
}

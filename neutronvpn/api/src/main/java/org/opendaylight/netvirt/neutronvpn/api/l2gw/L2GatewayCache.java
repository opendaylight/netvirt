/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.api.l2gw;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;


public interface L2GatewayCache {
    @NonNull
    L2GatewayDevice addOrGet(@NonNull String deviceName);

    void add(@NonNull String deviceName, L2GatewayDevice l2GatewayDevice);

    @Nullable
    L2GatewayDevice remove(String deviceName);

    @Nullable
    L2GatewayDevice get(String deviceName);

    @Nullable
    L2GatewayDevice getByNodeId(String nodeId);

    @NonNull
    Collection<L2GatewayDevice> getAll();

    @Nullable
    L2GatewayDevice updateL2GatewayCache(String psName, Uuid l2gwUuid);

    @Nullable
    L2GatewayDevice updateL2GatewayCache(String psName, String hwvtepNodeId, List<TunnelIps> tunnelIps);

    @NonNull
    ConcurrentMap<String, L2GatewayDevice> getCache();
}

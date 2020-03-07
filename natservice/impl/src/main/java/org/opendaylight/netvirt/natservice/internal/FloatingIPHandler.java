/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;

public interface FloatingIPHandler {

    void onAddFloatingIp(BigInteger dpnId, String routerUuid, long routerId, Uuid networkId, String interfaceName,
                         InternalToExternalPortMap mapping, TypedReadWriteTransaction<Configuration> confTx);

    void onRemoveFloatingIp(BigInteger dpnId, String routerUuid, long routerId, Uuid networkId,
                            InternalToExternalPortMap mapping, long label,
                            TypedReadWriteTransaction<Configuration> confTx);

    void cleanupFibEntries(BigInteger dpnId, String vpnName, String externalIp, long label,
                           TypedReadWriteTransaction<Configuration> confTx, ProviderTypes provType);
}

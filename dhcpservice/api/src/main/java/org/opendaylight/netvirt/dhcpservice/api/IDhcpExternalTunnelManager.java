/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice.api;

import java.math.BigInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;

public interface IDhcpExternalTunnelManager {

    ConcurrentMap<BigInteger, Set<Pair<IpAddress, String>>> getDesignatedDpnsToTunnelIpElanNameCache();

    ConcurrentMap<Pair<IpAddress, String>, Set<String>> getTunnelIpElanNameToVmMacCache();

    ConcurrentMap<Pair<IpAddress, String>, Set<String>> getAvailableVMCache();

    ConcurrentMap<Pair<BigInteger, String>, Port> getVniMacAddressToPortCache();
}

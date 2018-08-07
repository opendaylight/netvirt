/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.api;

import java.util.Optional;
import org.immutables.value.Value;
import org.opendaylight.genius.infra.OpenDaylightImmutableStyle;

@Value.Immutable
@OpenDaylightImmutableStyle
public interface SouthboundInterfaceInfo {

    Optional<String> getInterfaceName();


    Optional<String> getMacAddress();


    Optional<String> getNodeIp();

    Optional<Boolean> isServiceGateway();
}

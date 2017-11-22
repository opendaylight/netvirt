/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service.api;

import java.util.List;

public interface ElementCache {
    List<IVirtualPort> getInterfaceCache();

    List<IVirtualNetwork> getNetworkCache();

    List<IVirtualSubnet> getSubnetCache();

    List<IVirtualRouter> getRouterCache();
}

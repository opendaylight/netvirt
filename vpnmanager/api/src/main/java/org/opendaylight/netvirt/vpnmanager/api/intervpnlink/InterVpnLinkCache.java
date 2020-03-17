/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.api.intervpnlink;

import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;

/**
 * Manages some utility caches in order to speed (avoid) reads from MD-SAL.
 * InterVpnLink is something that rarely changes and is frequently queried.
 *
 * @author Thomas Pantelis
 */
public interface InterVpnLinkCache {

    void addInterVpnLinkToCaches(InterVpnLink interVpnLink);

    void addInterVpnLinkStateToCaches(InterVpnLinkState interVpnLinkState);

    void removeInterVpnLinkFromCache(InterVpnLink interVpnLink);

    void removeInterVpnLinkStateFromCache(InterVpnLinkState interVpnLinkState);

    Optional<InterVpnLinkDataComposite> getInterVpnLinkByName(String interVpnLinkName);

    Optional<InterVpnLinkDataComposite> getInterVpnLinkByEndpoint(String endpointIp);

    Optional<InterVpnLinkDataComposite> getInterVpnLinkByVpnId(String vpnId);

    @NonNull
    List<InterVpnLinkDataComposite> getAllInterVpnLinks();
}

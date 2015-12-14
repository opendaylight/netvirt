/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.impl;

import java.util.HashMap;
import java.util.Map;

import org.opendaylight.nic.mapping.api.IntentMappingService;

import com.google.common.base.Preconditions;

public class MappingServiceManager {

    private IntentMappingService intentMappingService;
    private String IP_PREFIX_PROPERTY = "ip_prefix";
    private String SWITCH_PORT_ID_PROPERTY = "switch_port";
    private String MPLS_LABEL_PROPERTY = "mpls_label";
    private String NEXT_HOP_PROPERTY = "next_hop";

    public MappingServiceManager(IntentMappingService intentMappingService) {
        Preconditions.checkNotNull(intentMappingService);
        this.intentMappingService = intentMappingService;
    }

    /**
     * @param siteName
     *            Name of the member
     * @param ipPrefix
     *            Ip prefix of the member
     * @param switchPortId
     *            Switch ID and port ID (i.e. openflow:1:2)
     * @param mplsLabel
     *            MPLS label, if needed
     * @param nextHop
     *            Next hop in the route
     */
    public void add(final String siteName, final String ipPrefix, final String switchPortId, final Long mplsLabel,
            final String nextHop) {
        Preconditions.checkNotNull(siteName);
        Preconditions.checkNotNull(ipPrefix);
        Preconditions.checkNotNull(switchPortId);

        Map<String, String> objs = new HashMap<>();
        objs.put(IP_PREFIX_PROPERTY, ipPrefix);
        objs.put(SWITCH_PORT_ID_PROPERTY, switchPortId);

        if (mplsLabel != null)
            objs.put(MPLS_LABEL_PROPERTY, String.valueOf(mplsLabel));
        if (nextHop != null)
            objs.put(NEXT_HOP_PROPERTY, nextHop);

        intentMappingService.add(siteName, objs);
    }

    /**
     * @param siteName
     *            Name of the member
     * @return Map of parameters related to the member
     */
    public Map<String, String> get(String siteName) {
        return intentMappingService.get(siteName);
    }

    /**
     * @param siteName
     *            Name of the member
     * @return Return true if transaction succeed, otherwise false
     */
    public boolean delete(String siteName) {
        try {
            // TODO: Implement delete() in mapping service
            // By now, it's going to overwrite data for this key
            intentMappingService.add(siteName, null);
            return true;
        } catch (Exception e) {
            throw e;
        }
    }
}

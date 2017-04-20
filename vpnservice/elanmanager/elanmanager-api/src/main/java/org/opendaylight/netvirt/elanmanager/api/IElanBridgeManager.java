/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.api;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public interface IElanBridgeManager {

    /**
     * Get integration bridge node with dpId.
     *
     * @param dpId
     *            datapath id
     * @return integration bridge {@code Node} or null if not found
     */
    Node getBridgeNode(BigInteger dpId);

    /**
     * Extract OpenvSwitch other-config to key value map.
     *
     * @param node
     *            OVSDB node
     * @param key
     *            key to extract from other-config
     * @return key-value Map or empty map if key was not found
     */
    Map<String, String> getOpenvswitchOtherConfigMap(Node node, String key);

    /**
     * Extract multi key-value into Map.
     *
     * @param multiKeyValueStr
     *            multi key-value formatted using colon key-value
     *            separator and comma multi-value separator
     * @return Map containing key value pairs or empty map if no key value pairs
     *         where found
     */
    Map<String, String> getMultiValueMap(String multiKeyValueStr);

    /**
     * Get the integration bridge DPN id from the manager node UUID.
     *
     * @param managerNodeId
     *            node-id of the OVSDB node managing br-int
     * @return Optional containing the dp-id or empty Optional if not found
     */
    Optional<BigInteger> getDpIdFromManagerNodeId(String managerNodeId);

}

/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import java.util.ArrayList;
import java.util.List;

public class FederatedPayload {
    public List<FederatedNetworkPair> networkPairs = new ArrayList<>();
    public List<FederatedAclPair> secGroupsPairs = new ArrayList<>();

    public FederatedPayload(List<FederatedNetworkPair> networkPairs,
        List<FederatedAclPair> secGroupsPairs) {
        this.networkPairs = networkPairs;
        this.secGroupsPairs = secGroupsPairs;
    }
}

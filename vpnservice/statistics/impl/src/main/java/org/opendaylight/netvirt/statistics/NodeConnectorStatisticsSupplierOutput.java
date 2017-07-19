/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yangtools.yang.common.RpcResult;

@SuppressWarnings("deprecation")
public class NodeConnectorStatisticsSupplierOutput {

    RpcResult<GetNodeConnectorStatisticsOutput> nodeConnectorStatisticsOutput;
    NodeConnectorId nodeConnectorId;

    public NodeConnectorStatisticsSupplierOutput(
            RpcResult<GetNodeConnectorStatisticsOutput> nodeConnectorStatisticsOutput,
            NodeConnectorId nodeConnectorId) {
        this.nodeConnectorStatisticsOutput = nodeConnectorStatisticsOutput;
        this.nodeConnectorId = nodeConnectorId;
    }

    public RpcResult<GetNodeConnectorStatisticsOutput> getNodeConnectorStatisticsOutput() {
        return nodeConnectorStatisticsOutput;
    }

    public NodeConnectorId getNodeConnectrId() {
        return nodeConnectorId;
    }
}

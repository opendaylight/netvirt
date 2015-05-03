package org.opendaylight.vpnservice.interfacemgr;

import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

public class IfmUtil { 

    static String getDpnFromNodeConnectorId(NodeConnectorId portId) {
        /*
         * NodeConnectorId is of form 'openflow:dpnid:portnum'
         */
        String[] split = portId.getValue().split(IfmConstants.OF_URI_SEPARATOR);
        return split[1];
    }


    static NodeId buildDpnNodeId(long dpnId) {
        return new NodeId(IfmConstants.OF_URI_PREFIX + dpnId);
    }
}

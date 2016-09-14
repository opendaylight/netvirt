/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha;

import static org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID;

import java.util.List;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HwvtepHAUtil {

    static Logger LOG = LoggerFactory.getLogger(HwvtepHAUtil.class);

    //TODO reuse HWvtepSouthboundConstants
    public static final String TEP_PREFIX = "vxlan_over_ipv4:";


    public static Uuid getUUid(String key) {
        return new Uuid(java.util.UUID.nameUUIDFromBytes(key.getBytes()).toString());
    }

    public static HwvtepPhysicalLocatorRef buildLocatorRef(InstanceIdentifier<Node> nodeIid, String tepIp ) {
        InstanceIdentifier<TerminationPoint> tepId = buildTpId(nodeIid, tepIp);
        return new HwvtepPhysicalLocatorRef(tepId);
    }

    public static InstanceIdentifier<TerminationPoint> buildTpId(InstanceIdentifier<Node> nodeIid,String tepIp ) {
        String tpKeyStr = TEP_PREFIX + tepIp;
        TerminationPointKey tpKey = new TerminationPointKey(new TpId(tpKeyStr));
        InstanceIdentifier<TerminationPoint> plIid = nodeIid.child(TerminationPoint.class, tpKey);
        return plIid;
    }

    public static boolean isEmptyList(List list) {
        if (list == null || list.size() == 0) {
            return true;
        }
        return false;
    }

    public static InstanceIdentifier<Node> convertToInstanceIdentifier(String nodeIdString) {
        NodeId nodeId = new NodeId(new Uri(nodeIdString));
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(HWVTEP_TOPOLOGY_ID);
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(Node.class, nodeKey)
                .build();
    }

    public static HwvtepLogicalSwitchRef convertLogicalSwitchRef(HwvtepLogicalSwitchRef src,
                                                                 InstanceIdentifier<Node> nodePath) {
        InstanceIdentifier<LogicalSwitches> srcId = (InstanceIdentifier<LogicalSwitches>)src.getValue();
        HwvtepNodeName switchName = srcId.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName();
        InstanceIdentifier<LogicalSwitches> iid = nodePath.augmentation(HwvtepGlobalAugmentation.class)
                .child(LogicalSwitches.class, new LogicalSwitchesKey(switchName));
        HwvtepLogicalSwitchRef ref = new HwvtepLogicalSwitchRef(iid);
        return ref;
    }

    public static String getTepIpVal(HwvtepPhysicalLocatorRef locatorRef) {
        InstanceIdentifier<TerminationPoint> tpId = (InstanceIdentifier<TerminationPoint>) locatorRef.getValue();
        return tpId.firstKeyOf(TerminationPoint.class).getTpId().getValue().substring("vxlan_over_ipv4:".length());
    }

    public static HwvtepPhysicalLocatorRef convertLocatorRef(HwvtepPhysicalLocatorRef src,
                                                             InstanceIdentifier<Node> nodePath) {
        InstanceIdentifier<TerminationPoint> srcTepPath = (InstanceIdentifier<TerminationPoint>)src.getValue();
        TpId tpId = srcTepPath.firstKeyOf(TerminationPoint.class).getTpId();
        InstanceIdentifier<TerminationPoint> tpPath =
                nodePath.child(TerminationPoint.class, new TerminationPointKey(tpId));
        HwvtepPhysicalLocatorRef locatorRef = new HwvtepPhysicalLocatorRef(tpPath);
        return locatorRef;
    }
}

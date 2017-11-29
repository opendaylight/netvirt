/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.commands;

import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.LoggerFactory;

public class LogicalSwitchesCmd extends MergeCommand<LogicalSwitches,
        HwvtepGlobalAugmentationBuilder, HwvtepGlobalAugmentation> {

    private DataBroker dataBroker;

    public LogicalSwitchesCmd() {
        LOG = LoggerFactory.getLogger(LogicalSwitchesCmd.class);
    }

    public LogicalSwitchesCmd(DataBroker dataBroker) {
        LOG = LoggerFactory.getLogger(LogicalSwitchesCmd.class);
        this.dataBroker = dataBroker;
    }

    @Override
    public List<LogicalSwitches> getData(HwvtepGlobalAugmentation node) {
        if (node != null) {
            return node.getLogicalSwitches();
        }
        return null;
    }

    @Override
    public void setData(HwvtepGlobalAugmentationBuilder builder, List<LogicalSwitches> data) {
        builder.setLogicalSwitches(data);
    }

    @Override
    public InstanceIdentifier<LogicalSwitches> generateId(InstanceIdentifier<Node> id, LogicalSwitches node) {
        return id.augmentation(HwvtepGlobalAugmentation.class).child(LogicalSwitches.class, node.getKey());
    }

    @Override
    public LogicalSwitches transform(InstanceIdentifier<Node> nodePath, LogicalSwitches src) {
        LogicalSwitchesBuilder logicalSwitchesBuilder = new LogicalSwitchesBuilder(src);
        logicalSwitchesBuilder.setLogicalSwitchUuid(HwvtepHAUtil.getUUid(src.getHwvtepNodeName().getValue()));
        setReplicationMode(nodePath, logicalSwitchesBuilder);
        return logicalSwitchesBuilder.build();
    }

    @Override
    public Identifier getKey(LogicalSwitches data) {
        return data.getKey();
    }

    @Override
    public String getDescription() {
        return "LogicalSwitches";
    }

    @Override
    public boolean areEqual(LogicalSwitches updated, LogicalSwitches orig) {
        return updated.getHwvtepNodeName().getValue().equals(orig.getHwvtepNodeName().getValue());
    }

    @Override
    public LogicalSwitches withoutUuid(LogicalSwitches data) {
        return new LogicalSwitchesBuilder(data).setHwvtepNodeName(null).build();
    }

    private void setReplicationMode(InstanceIdentifier<Node> nodePath, LogicalSwitchesBuilder dstBuilder) {
        if (dataBroker == null) {
            // No need to set the replication mode if not set a DataBroker.
            return;
        }
        if (!HwvtepHACache.getInstance().isHAEnabledDevice(nodePath)) {
            // No need to set the replication mode if not a child node.
            return;
        }
        NodeId hwvtepNodeId = nodePath.firstKeyOf(Node.class).getNodeId();
        String dbVersion = HwvtepUtils.getDbVersion(dataBroker, hwvtepNodeId);
        if (SouthboundUtils.compareDbVersionToMinVersion(dbVersion, "1.6.0")) {
            dstBuilder.setReplicationMode("source_node");
        }
    }
}

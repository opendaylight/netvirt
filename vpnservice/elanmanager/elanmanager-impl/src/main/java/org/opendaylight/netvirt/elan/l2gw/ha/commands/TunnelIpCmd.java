/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.commands;

import java.util.List;
import java.util.Objects;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.LoggerFactory;

public class TunnelIpCmd extends
        MergeCommand<TunnelIps, PhysicalSwitchAugmentationBuilder, PhysicalSwitchAugmentation> {

    public TunnelIpCmd() {
        LOG = LoggerFactory.getLogger(TunnelIpCmd.class);
    }

    @Override
    public List<TunnelIps> getData(PhysicalSwitchAugmentation node) {
        if (node != null) {
            return node.getTunnelIps();
        }
        return null;
    }

    @Override
    public void setData(PhysicalSwitchAugmentationBuilder builder, List<TunnelIps> data) {
        builder.setTunnelIps(data);
    }

    @Override
    public InstanceIdentifier<TunnelIps> generateId(InstanceIdentifier<Node> id, TunnelIps src) {
        return id.augmentation(PhysicalSwitchAugmentation.class).child(TunnelIps.class, src.getKey());
    }

    @Override
    public TunnelIps transform(InstanceIdentifier<Node> nodePath, TunnelIps src) {
        return src;
    }

    @Override
    public Identifier getKey(TunnelIps data) {
        return data.getKey();
    }

    @Override
    public String getDescription() {
        return "TunnelIps";
    }

    @Override
    public boolean areEqual(TunnelIps updated, TunnelIps orig) {
        return Objects.equals(updated, orig);
    }

    @Override
    public TunnelIps withoutUuid(TunnelIps data) {
        return data;
    }
}

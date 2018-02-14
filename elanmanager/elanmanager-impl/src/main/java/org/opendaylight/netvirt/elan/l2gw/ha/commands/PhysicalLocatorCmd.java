/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.commands;

import java.util.List;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class PhysicalLocatorCmd extends MergeCommand<TerminationPoint, NodeBuilder, Node> {

    public PhysicalLocatorCmd() {
    }

    @Override
    public List<TerminationPoint> getData(Node node) {
        if (node != null) {
            return node.getTerminationPoint();
        }
        return null;
    }

    @Override
    public void setData(NodeBuilder builder, List<TerminationPoint> data) {
        builder.setTerminationPoint(data);
    }

    @Override
    public InstanceIdentifier<TerminationPoint> generateId(InstanceIdentifier<Node> id, TerminationPoint node) {
        return id.child(TerminationPoint.class, node.getKey());
    }

    @Override
    public TerminationPoint transform(InstanceIdentifier<Node> nodePath, TerminationPoint src) {
        return src;
    }

    @Override
    public Identifier getKey(TerminationPoint data) {
        return data.getKey();
    }

    @Override
    public String getDescription() {
        return "PhysicalLocator";
    }

    @Override
    public boolean areEqual(TerminationPoint updated, TerminationPoint orig) {
        HwvtepPhysicalLocatorAugmentation updatedPhysicalLocator =
                updated.getAugmentation(HwvtepPhysicalLocatorAugmentation.class);
        HwvtepPhysicalLocatorAugmentation origPhysicalLocator =
                orig.getAugmentation(HwvtepPhysicalLocatorAugmentation.class);
        return updatedPhysicalLocator.getDstIp().equals(origPhysicalLocator.getDstIp())
                && updatedPhysicalLocator.getEncapsulationType() == origPhysicalLocator.getEncapsulationType();
    }

    @Override
    public TerminationPoint withoutUuid(TerminationPoint data) {
        return data;
    }
}

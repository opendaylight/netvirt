/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.commands;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindingsBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminationPointCmd extends MergeCommand<TerminationPoint, NodeBuilder, Node> {

    private static final Logger LOG = LoggerFactory.getLogger(TerminationPointCmd.class);

    public TerminationPointCmd() {
    }

    @Override
    @Nullable
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
        return id.child(TerminationPoint.class, node.key());
    }

    @Override
    public TerminationPoint transform(InstanceIdentifier<Node> nodePath, TerminationPoint src) {
        HwvtepPhysicalPortAugmentation augmentation = src.augmentation(HwvtepPhysicalPortAugmentation.class);
        if (augmentation == null) {
            return new TerminationPointBuilder(src).build();
        }
        String nodeIdVal = nodePath.firstKeyOf(Node.class).getNodeId().getValue();
        int idx = nodeIdVal.indexOf("/physicalswitch");
        if (idx > 0) {
            nodeIdVal = nodeIdVal.substring(0, idx);
            nodePath = HwvtepHAUtil.convertToInstanceIdentifier(nodeIdVal);
        }
        final InstanceIdentifier<Node> path = nodePath;
        TerminationPointBuilder tpBuilder = new TerminationPointBuilder(src);
        tpBuilder.removeAugmentation(HwvtepPhysicalPortAugmentation.class);
        HwvtepPhysicalPortAugmentationBuilder tpAugmentationBuilder =
                new HwvtepPhysicalPortAugmentationBuilder(augmentation);

        if (augmentation.getVlanBindings() != null && augmentation.getVlanBindings().size() > 0) {
            tpAugmentationBuilder.setVlanBindings(augmentation.getVlanBindings().stream().map(
                vlanBindings -> {
                    if (vlanBindings.getLogicalSwitchRef() == null) {
                        LOG.error("Failed to get logical switch ref for vlan binding {} {} ", path, src);
                    }
                    VlanBindingsBuilder vlanBindingsBuilder = new VlanBindingsBuilder(vlanBindings);
                    vlanBindingsBuilder.setLogicalSwitchRef(
                            HwvtepHAUtil.convertLogicalSwitchRef(vlanBindings.getLogicalSwitchRef(), path));
                    return vlanBindingsBuilder.build();
                }).collect(Collectors.toList()));
        }

        tpBuilder.addAugmentation(HwvtepPhysicalPortAugmentation.class, tpAugmentationBuilder.build());
        return tpBuilder.build();
    }

    @Override
    public Identifier getKey(TerminationPoint data) {
        return data.key();
    }

    @Override
    public String getDescription() {
        return "vlanbindings";
    }

    @Override
    public TerminationPoint withoutUuid(TerminationPoint data) {
        return data;
    }

    @Override
    public boolean areEqual(TerminationPoint updated, TerminationPoint orig) {
        if (!updated.key().equals(orig.key())) {
            return false;
        }
        HwvtepPhysicalPortAugmentation updatedAugmentation = updated
                .augmentation(HwvtepPhysicalPortAugmentation.class);
        HwvtepPhysicalPortAugmentation origAugmentation = orig.augmentation(HwvtepPhysicalPortAugmentation.class);

        List<VlanBindings> up = updatedAugmentation != null ? updatedAugmentation.getVlanBindings() : null;
        List<VlanBindings> or = origAugmentation != null ? origAugmentation.getVlanBindings() : null;
        if (!areSameSize(up, or)) {
            return false;
        }
        List<VlanBindings> added = diffOf(up, or, bindingsComparator);
        if (added.size() != 0) {
            return false;
        }
        List<VlanBindings> removed = diffOf(or, up, bindingsComparator);
        if (removed.size() != 0) {
            return false;
        }
        return true;
    }

    static BindingsComparator bindingsComparator = new BindingsComparator();

    static class BindingsComparator implements Comparator<VlanBindings>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(VlanBindings updated, VlanBindings orig) {
            if (updated == null && orig == null) {
                return 0;
            }
            if (updated == null) {
                return 1;
            }
            if (orig == null) {
                return 1;
            }
            if (updated.key().equals(orig.key())) {
                return 0;
            }
            updated.key();
            return 1;
        }
    }
/*
    @Override
    public void transformUpdate(List<TerminationPoint> existing,
                                List<TerminationPoint> updated,
                                List<TerminationPoint> orig,
                                InstanceIdentifier<Node> nodePath,
                                LogicalDatastoreType datastoreType,
                                ReadWriteTransaction tx) {

        if (updated == null) {
            updated = new ArrayList<>();
        }
        if (orig == null) {
            orig = new ArrayList<>();
        }
        List<TerminationPoint> added   = new ArrayList<>(updated);

        added = diffOf(added, orig);
        added = diffOf(added, existing);//do not add the existing data again
        if (added != null && added.size() > 0) {
            for (TerminationPoint addedItem : added) {
                InstanceIdentifier<TerminationPoint> transformedId = generateId(nodePath, addedItem);
                TerminationPoint transformedItem = transform(nodePath, addedItem);
                LOG.trace("adding vlanbindings id {} ", transformedId
                                    .firstKeyOf(TerminationPoint.class).getTpId().getValue());
                tx.put(datastoreType, transformedId, transformedItem, true);
            }
        }
        for (TerminationPoint origItem : orig) {
            boolean found = false;
            for (TerminationPoint newItem : updated) {
                if (newItem.key().equals(origItem.key())) {
                    found = true;
                }
            }
            if (!found) {
                boolean existsInConfig = false;
                for (TerminationPoint existingItem : existing) {
                    if (existingItem.key().equals(origItem.key())) {
                        existsInConfig = true;
                    }
                }
                if (existsInConfig) {
                    InstanceIdentifier<TerminationPoint> transformedId = generateId(nodePath, origItem);
                    LOG.trace("deleting vlanbindings id {} ", transformedId.firstKeyOf(TerminationPoint.class)
                                                                                .getTpId().getValue());
                    tx.delete(datastoreType, transformedId);
                }
            }
        }
    }
*/

}

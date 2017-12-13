/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.merge;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.DataUpdates;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public abstract class MergeCommandsAggregator<BuilderTypeT extends Builder, AugTypeT extends DataObject> {

    protected MergeCommandsAggregator() {
    }

    List<MergeCommand> commands = new ArrayList<>();

    public void mergeOperationalData(BuilderTypeT builder,
                                     AugTypeT existingData,
                                     AugTypeT src,
                                     InstanceIdentifier<Node> dstPath) {
        for (MergeCommand cmd : commands) {
            cmd.mergeOperationalData(builder, existingData, src, dstPath);
        }
    }

    public void mergeConfigData(BuilderTypeT builder,
                                AugTypeT src,
                                InstanceIdentifier<Node> dstPath) {
        for (MergeCommand cmd : commands) {
            cmd.mergeConfigData(builder, src, dstPath);
        }
    }


    public void mergeConfigUpdate(InstanceIdentifier<Node> dstPath,
                                  DataUpdates dataUpdates,
                                  ReadWriteTransaction tx) {
        for (MergeCommand cmd : commands) {
            cmd.mergeConfigUpdate(dstPath, dataUpdates, tx);
        }
    }

    public void mergeOpUpdate(InstanceIdentifier<Node> dstPath,
                              DataUpdates dataUpdates,
                              ReadWriteTransaction tx) {
        for (MergeCommand cmd : commands) {
            cmd.mergeOpUpdate(dstPath, dataUpdates, tx);
        }
    }
}

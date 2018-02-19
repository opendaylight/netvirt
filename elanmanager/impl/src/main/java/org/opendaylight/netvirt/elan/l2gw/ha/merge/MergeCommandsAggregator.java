/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.merge;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Optional;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.utils.SuperTypeUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteUcastCmd;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MergeCommandsAggregator<BuilderTypeT extends Builder, AugTypeT extends DataObject> {

    public static final Logger LOG = LoggerFactory.getLogger(MergeCommandsAggregator.class);

    protected Map<Class<?>, MergeCommand> commands = new HashMap<>();

    private final BiPredicate<LogicalDatastoreType, Class> skipCopy = (dsType, cmdType) -> {
        return (dsType == CONFIGURATION ? commands.get(cmdType) instanceof LocalUcastCmd :
                commands.get(cmdType) instanceof RemoteUcastCmd);
    };

    protected MergeCommandsAggregator() {
    }

    protected void addCommand(MergeCommand mergeCommand) {
        commands.put(SuperTypeUtil.getTypeParameter(mergeCommand.getClass(), 0), mergeCommand);
    }

    public void mergeOperationalData(BuilderTypeT builder,
                                     AugTypeT existingData,
                                     AugTypeT src,
                                     InstanceIdentifier<Node> dstPath) {
        for (MergeCommand cmd : commands.values()) {
            if (skipCopy.negate().test(OPERATIONAL, cmd.getClass())) {
                cmd.mergeOperationalData(builder, existingData, src, dstPath);
            }
        }
    }

    public void mergeConfigData(BuilderTypeT builder,
                                AugTypeT src,
                                InstanceIdentifier<Node> dstPath) {
        for (MergeCommand cmd : commands.values()) {
            if (skipCopy.negate().test(CONFIGURATION, cmd.getClass())) {
                cmd.mergeConfigData(builder, src, dstPath);
            }
        }
    }


    public void mergeConfigUpdate(InstanceIdentifier<Node> dstPath,
                                  DataObjectModification mod,
                                  ReadWriteTransaction tx) {
        mergeUpdate(dstPath, mod, CONFIGURATION, tx);
    }

    public void mergeOpUpdate(InstanceIdentifier<Node> dstPath,
                              DataObjectModification mod,
                              ReadWriteTransaction tx) {
        mergeUpdate(dstPath, mod, OPERATIONAL, tx);
    }

    public void mergeUpdate(InstanceIdentifier<Node> dstPath,
                            DataObjectModification mod,
                            LogicalDatastoreType datastoreType,
                            ReadWriteTransaction tx) {
        if (mod == null) {
            return;
        }
        Collection<DataObjectModification> modifications = mod.getModifiedChildren();
        modifications.stream()
            .filter(modification -> skipCopy.negate().test(datastoreType, modification.getDataType()))
            .filter(modification -> commands.get(modification.getDataType()) != null)
            .peek(modification -> LOG.debug("Received {} modification {} copy/delete to {}",
                    datastoreType, modification, dstPath))
            .forEach(modification -> {
                MergeCommand mergeCommand = commands.get(modification.getDataType());
                boolean create = modification.getDataAfter() != null;
                DataObject data = create ? modification.getDataAfter() : modification.getDataBefore();
                InstanceIdentifier<DataObject> transformedId = mergeCommand.generateId(dstPath, data);
                DataObject transformedItem = mergeCommand.transform(dstPath, data);

                Optional<DataObject> existingDataOptional = null;
                try {
                    existingDataOptional = tx.read(datastoreType, transformedId).checkedGet();
                } catch (ReadFailedException ex) {
                    LOG.error("Failed to read data {} from {}", transformedId, datastoreType);
                    return;
                }

                String destination = datastoreType == CONFIGURATION ? "child" : "parent";
                if (create) {
                    if (isDataUpdated(existingDataOptional, data)) {
                        LOG.debug("Copy to {} {} {}", destination, datastoreType, transformedId);
                        tx.put(datastoreType, transformedId, data, true);
                    } else {
                        LOG.debug("Data not updated skip copy to {}", transformedId);
                    }
                } else {
                    if (existingDataOptional.isPresent()) {
                        LOG.debug("Delete from {} {} {}", destination, datastoreType, transformedId);
                        tx.delete(datastoreType, transformedId);
                    } else {
                        LOG.debug("Delete skipped for {}", transformedId);
                    }
                }
            });
    }

    boolean isDataUpdated(Optional<DataObject> existingDataOptional, DataObject newData) {
        return !existingDataOptional.isPresent() || !Objects.equals(existingDataOptional.get(), newData);
    }
}

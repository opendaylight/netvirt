/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.merge;

import static org.opendaylight.mdsal.binding.api.WriteTransaction.CREATE_MISSING_PARENTS;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.Optional;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.utils.SuperTypeUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.PhysicalLocatorCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TerminationPointCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TunnelCmd;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.Tunnels;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MergeCommandsAggregator<BuilderTypeT extends Builder, AugTypeT extends DataObject> {

    private static final Logger LOG = LoggerFactory.getLogger(MergeCommandsAggregator.class);

    protected Map<Class<?>, MergeCommand> commands = new HashMap<>();

    private final Map<Class, Boolean> operSkipCommands = new HashMap<>();
    private final Map<Class, Boolean> configSkipCommands = new HashMap<>();

    private final BiPredicate<Class<? extends Datastore>, Class> skipCopy =
        (dsType, cmdType) -> (Configuration.class.equals(dsType) ? configSkipCommands.containsKey(cmdType)
                : operSkipCommands.containsKey(cmdType));

    protected MergeCommandsAggregator() {
        operSkipCommands.put(RemoteUcastCmd.class, Boolean.TRUE);
        operSkipCommands.put(RemoteMcastCmd.class, Boolean.TRUE);
        operSkipCommands.put(TerminationPointCmd.class, Boolean.TRUE);
        operSkipCommands.put(LocalMcastCmd.class, Boolean.TRUE);
        operSkipCommands.put(PhysicalLocatorCmd.class, Boolean.TRUE);
        operSkipCommands.put(TunnelCmd.class, Boolean.TRUE);

        operSkipCommands.put(RemoteMcastMacs.class, Boolean.TRUE);
        operSkipCommands.put(RemoteUcastMacs.class, Boolean.TRUE);
        operSkipCommands.put(LocalMcastMacs.class, Boolean.TRUE);
        operSkipCommands.put(TerminationPoint.class, Boolean.TRUE);
        operSkipCommands.put(Tunnels.class, Boolean.TRUE);

        configSkipCommands.put(LocalUcastCmd.class, Boolean.TRUE);
        configSkipCommands.put(LocalUcastMacs.class, Boolean.TRUE);
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
                                  TypedReadWriteTransaction<Configuration> tx) {
        mergeUpdate(dstPath, mod, CONFIGURATION, tx);
    }

    public void mergeOpUpdate(InstanceIdentifier<Node> dstPath,
                              DataObjectModification mod,
                              TypedReadWriteTransaction<Operational> tx) {
        mergeUpdate(dstPath, mod, OPERATIONAL, tx);
    }

    public <D extends Datastore> void mergeUpdate(InstanceIdentifier<Node> dstPath,
                            DataObjectModification mod,
                            Class<D> datastoreType,
                            TypedReadWriteTransaction<D> tx) {
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
                DataObject dataAfter = modification.getDataAfter();
                boolean create = dataAfter != null;
                DataObject data = create ? dataAfter : modification.getDataBefore();
                InstanceIdentifier<DataObject> transformedId = mergeCommand.generateId(dstPath, data);
                DataObject transformedItem = mergeCommand.transform(dstPath, data);

                Optional<DataObject> existingDataOptional = null;
                try {
                    existingDataOptional = tx.read(transformedId).get();
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.error("Failed to read data {} from {}", transformedId, datastoreType);
                    return;
                }

                String destination = Configuration.class.equals(datastoreType) ? "child" : "parent";
                if (create) {
                    if (isDataUpdated(existingDataOptional, transformedItem)) {
                        LOG.debug("Copy to {} {} {}", destination, datastoreType, transformedId);
                        tx.put(transformedId, transformedItem, CREATE_MISSING_PARENTS);
                    } else {
                        LOG.debug("Data not updated skip copy to {}", transformedId);
                    }
                } else {
                    if (existingDataOptional.isPresent()) {
                        LOG.debug("Delete from {} {} {}", destination, datastoreType, transformedId);
                        tx.delete(transformedId);
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

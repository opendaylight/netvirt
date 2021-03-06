/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.merge;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import org.opendaylight.genius.utils.SuperTypeUtil;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.PhysicalLocatorCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TerminationPointCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TunnelCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TunnelIpCmd;
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

    private final Cache<InstanceIdentifier, Boolean> deleteInProgressIids = CacheBuilder.newBuilder()
            .initialCapacity(50000)
            .expireAfterWrite(600, TimeUnit.SECONDS)
            .build();

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
                                  TypedReadWriteTransaction<Configuration> tx, ManagedNewTransactionRunner txRunner) {
        mergeUpdate(dstPath, mod, CONFIGURATION, tx, txRunner);
    }

    public void mergeOpUpdate(InstanceIdentifier<Node> dstPath,
                              DataObjectModification mod,
                              TypedReadWriteTransaction<Operational> tx, ManagedNewTransactionRunner txRunner) {
        mergeUpdate(dstPath, mod, OPERATIONAL, tx, txRunner);
    }

    @SuppressWarnings("illegalcatch")
    public <D extends Datastore> void mergeUpdate(InstanceIdentifier<Node> dstPath,
                            DataObjectModification mod,
                            Class<D> datastoreType,
                            TypedReadWriteTransaction<D> transaction,
                            ManagedNewTransactionRunner txRunner) {
        BatchedTransaction tx = null;
        if (mod == null || mod.getModifiedChildren() == null) {
            return;
        }
        if (!(transaction instanceof BatchedTransaction)) {
            return;
        }
        else {
            tx = (BatchedTransaction)transaction;
        }
        final BatchedTransaction transaction1 = tx;
        String srcNodeId = transaction1.getSrcNodeId().getValue();
        String dstNodeId = dstPath.firstKeyOf(Node.class).getNodeId().getValue();
        Collection<DataObjectModification> modifications = mod.getModifiedChildren();
        modifications.stream()
                .filter(modification -> skipCopy.negate().test(datastoreType, modification.getDataType()))
                .filter(modification -> commands.get(modification.getDataType()) != null)
                .peek(modification -> LOG.debug("Received {} modification {} copy/delete to {}",
                        datastoreType, modification, dstPath))
                .forEach(modification -> {
                    try {
                        copyModification(dstPath, datastoreType, transaction1,
                            srcNodeId, dstNodeId, modification, txRunner);
                    } catch (Exception e) {
                        LOG.error("Failed to copy mod from {} to {} {} {} id  {}",
                            srcNodeId, dstNodeId, modification.getDataType().getSimpleName(),
                            modification, modification.getIdentifier(), e);
                    }
                });
    }

    private <D extends Datastore> void copyModification(InstanceIdentifier<Node> dstPath, Class<D> datastoreType,
                                  BatchedTransaction tx, String srcNodeId, String dstNodeId,
                                  DataObjectModification modification, ManagedNewTransactionRunner txRunner) {
        DataObjectModification.ModificationType type = getModificationType(modification);
        if (type == null) {
            return;
        }
        String src = datastoreType == OPERATIONAL ? "child" : "parent";
        MergeCommand mergeCommand = commands.get(modification.getDataType());
        boolean create = false;
        switch (type) {
            case WRITE:
            case SUBTREE_MODIFIED:
                DataObject dataAfter = modification.getDataAfter();
                if (dataAfter == null) {
                    return;
                }
                DataObject before = modification.getDataBefore();
                if (Objects.equals(dataAfter, before)) {
                    LOG.warn("Ha updated skip not modified {}", src);
                    return;
                }

                create = true;
                break;
            case DELETE:
                DataObject dataBefore = modification.getDataBefore();
                if (dataBefore == null) {
                    LOG.warn("Ha updated skip delete {}", src);
                    return;
                }
                break;
            default:
                return;
        }
        DataObject data = create ? modification.getDataAfter() : modification.getDataBefore();
        InstanceIdentifier<DataObject> transformedId = mergeCommand.generateId(dstPath, data);
        if (tx.updateMetric()) {
            LOG.info("Ha updated processing {}", src);
        }
        if (create) {
            DataObject transformedItem = mergeCommand.transform(dstPath, modification.getDataAfter());
            tx.put(transformedId, transformedItem);
            //if tunnel ip command do this for
            if (mergeCommand.getClass() == TunnelIpCmd.class) {
                if (Operational.class.equals(datastoreType)) {
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, configTx -> {
                        configTx.put(transformedId, transformedItem);
                    });

                }
            }
        } else {
            if (deleteInProgressIids.getIfPresent(transformedId) == null) {
                // TODO uncomment this code
                /*if (isLocalMacMoved(mergeCommand, transformedId, tx, srcNodeId, txRunner)) {
                    return;
                }*/
                tx.delete(transformedId);
                if (mergeCommand.getClass() == TunnelIpCmd.class) {
                    if (Operational.class.equals(datastoreType)) {
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, configTx -> {
                            tx.delete(transformedId);
                        });
                    }
                }
                deleteInProgressIids.put(transformedId, Boolean.TRUE);
            } else {
                return;
            }
        }
        String created = create ? "created" : "deleted";
        Futures.addCallback(tx.getFt(transformedId), new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void voidResult) {
                LOG.info("Ha updated skip not modified {}", mergeCommand.getDescription());
                deleteInProgressIids.invalidate(transformedId);
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("Ha failed {}", mergeCommand.getDescription());
                deleteInProgressIids.invalidate(transformedId);
            }
        }, MoreExecutors.directExecutor());
    }

    /*private boolean isLocalMacMoved(MergeCommand mergeCommand,
                                    InstanceIdentifier<DataObject> localUcastIid,
                                    BatchedTransaction tx,
                                    String parentId, ManagedNewTransactionRunner txRunner) {
        if (mergeCommand.getClass() != LocalUcastCmd.class) {
            return false;
        }
        final Optional<DataObject> existingMacOptional = Optional.empty();
            txRunner.callWithNewReadOnlyTransactionAndClose(OPERATIONAL, operTx -> {
                Optional<DataObject> temp = operTx.read(localUcastIid).get();

            });
                if (!existingMacOptional.isPresent() || existingMacOptional.get() == null) {
                    return false;
                }
                LocalUcastMacs existingMac  = (LocalUcastMacs) existingMacOptional.get();
                if (existingMac.augmentation(SrcnodeAugmentation.class) != null) {
                    if (!Objects.equals(existingMac.augmentation(SrcnodeAugmentation.class).getSrcTorNodeid(),
                        parentId)) {
                        LOG.error("MergeCommandAggregator mac movement within tor {} {}",
                            existingMac.augmentation(SrcnodeAugmentation.class).getSrcTorNodeid(), parentId);
                        return true;
                    }
                }

        return false;
    }*/

    private DataObjectModification.ModificationType getModificationType(
            DataObjectModification<? extends DataObject> mod) {
        try {
            return mod.getModificationType();
        } catch (IllegalStateException e) {
            //not sure why this getter throws this exception, could be some mdsal bug
            LOG.trace("Failed to get the modification type for mod {}", mod);
        }
        return null;
    }

    boolean isDataUpdated(Optional<DataObject> existingDataOptional, DataObject newData) {
        return !existingDataOptional.isPresent() || !Objects.equals(existingDataOptional.get(), newData);
    }
}

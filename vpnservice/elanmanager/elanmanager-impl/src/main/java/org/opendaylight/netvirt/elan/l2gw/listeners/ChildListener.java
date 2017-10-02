/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.TaskRetryLooper;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ChildListener<P extends DataObject, C extends DataObject, G>
        implements DataTreeChangeListener<P>, AutoCloseable {

    public static final Logger LOG = LoggerFactory.getLogger(ChildListener.class);
    private static final long STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;

    protected final DataBroker dataBroker;
    private ListenerRegistration<ChildListener> registration;
    private final boolean processParentDeletes;

    public ChildListener(DataBroker dataBroker, boolean processParentDeletes) {
        this.dataBroker = dataBroker;
        this.processParentDeletes = processParentDeletes;
    }

    public void init() throws Exception {
        registration = registerListener(LogicalDatastoreType.OPERATIONAL, getParentWildCardPath());
    }

    public ListenerRegistration registerListener(final LogicalDatastoreType dsType,
                                                 final InstanceIdentifier wildCard) throws Exception {
        DataTreeIdentifier<P> treeId = new DataTreeIdentifier<>(dsType, wildCard);
        TaskRetryLooper looper = new TaskRetryLooper(STARTUP_LOOP_TICK, STARTUP_LOOP_MAX_RETRIES);
        return looper.loopUntilNoException(() -> dataBroker.registerDataTreeChangeListener(treeId, this));
    }

    protected abstract void onParentAdded(DataTreeModification<P> parent);

    protected abstract void onParentRemoved(InstanceIdentifier<P> parent);

    protected abstract boolean proceed(InstanceIdentifier<P> parent);

    protected abstract G getGroup(InstanceIdentifier<C> childIid, C child);

    protected abstract void onUpdate(
            Map<G, Map<InstanceIdentifier, C>> updatedMacsGrouped,
            Map<G, Map<InstanceIdentifier, C>> deletedMacsGrouped);

    protected abstract Map<InstanceIdentifier<C>, DataObjectModification<C>> getChildMod(
            InstanceIdentifier<P> parentIid,
            DataObjectModification<P> mod);

    protected abstract InstanceIdentifier<Node> getParentWildCardPath();

    @Override
    public void close() throws Exception {
        if (registration != null) {
            registration.close();
        }
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<P>> changes) {
        Map<G, Map<InstanceIdentifier, C>> updatedData = new HashMap<>();
        Map<G, Map<InstanceIdentifier, C>> deletedData = new HashMap<>();
        extractUpdatedAndDeletedMacs(changes, updatedData, deletedData);
        onUpdate(updatedData, deletedData);
    }

    void extractUpdatedAndDeletedMacs(Collection<DataTreeModification<P>> changes,
                                      Map<G, Map<InstanceIdentifier, C>> updatedMacsGrouped,
                                      Map<G, Map<InstanceIdentifier, C>> deletedMacsGrouped) {
        changes.stream()
                .filter(change -> proceed(change.getRootPath().getRootIdentifier()))
                .forEach((change) -> {
                    InstanceIdentifier<P> iid = change.getRootPath().getRootIdentifier();
                    DataObjectModification<P> modification = change.getRootNode();
                    switch (getModificationType(modification)) {
                        case WRITE:
                            if (modification.getDataBefore() == null) {
                                onParentAdded(change);
                            } else {
                                LOG.info("Unexpected write to parent before {}", modification.getDataBefore());
                                LOG.info("Unexpected write to parent after {}", modification.getDataAfter());
                            }
                            extractDataChanged(iid, modification, updatedMacsGrouped, deletedMacsGrouped);
                            break;
                        case SUBTREE_MODIFIED:
                            extractDataChanged(iid, modification, updatedMacsGrouped, deletedMacsGrouped);
                            break;
                        case DELETE:
                            if (processParentDeletes) {
                                extractDataChanged(iid, modification, updatedMacsGrouped, deletedMacsGrouped);
                            }
                            onParentRemoved(iid);
                            //Do not process the disconnected nodes
                            break;
                        default:
                            break;
                    }
                });
    }

    private void extractDataChanged(final InstanceIdentifier<P> key,
                                    final DataObjectModification<P> parentModification,
                                    final Map<G, Map<InstanceIdentifier, C>> updatedMacsGrouped,
                                    final Map<G, Map<InstanceIdentifier, C>> deletedMacsGrouped) {

        Map<InstanceIdentifier<C>, DataObjectModification<C>> children = getChildMod(key, parentModification);
        for (Map.Entry<InstanceIdentifier<C>, DataObjectModification<C>> entry : children.entrySet()) {
            DataObjectModification<C> childMod = entry.getValue();
            InstanceIdentifier childIid = entry.getKey();
            DataObjectModification.ModificationType modificationType = getModificationType(childMod);
            Class<? extends Identifiable> childClass = (Class<? extends Identifiable>) childMod.getDataType();
            G group;
            C dataBefore = childMod.getDataBefore();
            C dataAfter = childMod.getDataAfter();
            switch (modificationType) {
                case WRITE:
                case SUBTREE_MODIFIED:
                    group = (G)getGroup(childIid, dataAfter);
                    updatedMacsGrouped.computeIfAbsent(group, (grp) -> new ConcurrentHashMap<>());
                    updatedMacsGrouped.get(group).put(childIid, dataAfter);
                    break;
                case DELETE:
                    group = (G)getGroup(childIid, dataBefore);
                    deletedMacsGrouped.computeIfAbsent(group, (grp) -> new ConcurrentHashMap<>());
                    deletedMacsGrouped.get(group).put(childIid, dataBefore);
                    break;
                default:
                    break;
            }
        }
    }

    protected DataObjectModification.ModificationType getModificationType(
            final DataObjectModification<? extends DataObject> mod) {
        try {
            return mod.getModificationType();
        } catch (IllegalStateException e) {
            //LOG.warn("Failed to get the modification type for mod {}", mod);
        }
        return null;
    }
}

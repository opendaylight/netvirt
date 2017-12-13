/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.commands;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil.isEmptyList;

import com.google.common.base.Optional;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public abstract class MergeCommand<T extends DataObject, Y extends Builder, Z extends DataObject>
        extends BaseCommand<T> implements IMergeCommand<T, Y, Z> {

    private static final Logger LOG = LoggerFactory.getLogger(MergeCommand.class);

    Type cmdType;

    public MergeCommand() {
        cmdType = getCmdType();
    }

    protected Type getCmdType() {
        Type type = getClass().getGenericSuperclass();
        Type classType = ((ParameterizedType)type).getActualTypeArguments()[0];
        return classType;
    }

    public List<T> transformOpData(List<T> existingData, List<T> src, InstanceIdentifier<Node> nodePath) {
        if (isEmptyList(src)) {
            return new ArrayList<>();
        }
        List<T> added = diffOf(src, existingData);//do not add existing data again
        return transform(nodePath, added);
    }

    public List<T> transformConfigData(List<T> updatedSrc, InstanceIdentifier<Node> nodePath) {
        if (isEmptyList(updatedSrc)) {
            return new ArrayList<>();//what difference returning null makes ?
        }
        return transform(nodePath, updatedSrc);
    }

    @Nonnull
    public List<T> diffByKey(List<T> updated, final List<T> original) {
        if (updated == null) {
            return new ArrayList<>();
        }
        if (original == null) {
            return new ArrayList<>(updated);
        }

        List<T> result = new ArrayList<>();
        for (T ele : updated) {
            boolean present = false;
            for (T orig : original) {
                if (Objects.equals(getKey(ele), getKey(orig))) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                result.add(ele);
            }
        }
        return result;
    }

    //TODO validate the perf of the following against direct setting of the data in dst node
    public void transformUpdate(List<T> existing,
                                List<T> updated,
                                List<T> orig,
                                InstanceIdentifier<Node> nodePath,
                                LogicalDatastoreType datastoreType,
                                ReadWriteTransaction readWriteTransaction) {

        BatchedTransaction tx = (BatchedTransaction) readWriteTransaction;
        Map<Class<? extends Identifiable>, List<Identifiable>> updatedData = tx.getUpdatedData();
        Map<Class<? extends Identifiable>, List<Identifiable>> deletedData = tx.getDeletedData();

        List<Identifiable> added   = updatedData.get(cmdType);
        if (added != null && added.size() > 0) {
            for (Identifiable addedItem2 : added) {
                T addedItem = (T)addedItem2;
                InstanceIdentifier<T> transformedId = generateId(nodePath, addedItem);
                T transformedItem = transform(nodePath, addedItem);
                String nodeId = transformedId.firstKeyOf(Node.class).getNodeId().getValue();
                LOG.debug("Adding {} {} {}", getDescription(), nodeId, getKey(transformedItem));
                writeToMdsal(true, tx, transformedItem, transformedId, datastoreType, 10);
            }
        }
        List<Identifiable> removed   = deletedData.get(cmdType);
        if (removed != null && removed.size() > 0) {
            for (Identifiable removedItem2 : removed) {
                T removedItem = (T)removedItem2;
                InstanceIdentifier<T> transformedId = generateId(nodePath, removedItem);
                String nodeId = transformedId.firstKeyOf(Node.class).getNodeId().getValue();
                LOG.debug("Removing {} {} {}",getDescription(), nodeId, getKey(removedItem));
                writeToMdsal(false, tx, null, transformedId, datastoreType, 10);
            }
        }
    }

    public List<T> transform(InstanceIdentifier<Node> nodePath, List<T> list) {
        return list.stream().map(t -> transform(nodePath, t)).collect(Collectors.toList());
    }

    public abstract T transform(InstanceIdentifier<Node> nodePath, T objT);

    @Override
    public void mergeOperationalData(Y dst,
                                     Z existingData,
                                     Z src,
                                     InstanceIdentifier<Node> nodePath) {
        List<T> origDstData = getData(existingData);
        List<T> srcData = getData(src);
        List<T> data = transformOpData(origDstData, srcData, nodePath);
        setData(dst, data);
        if (!isEmptyList(data)) {
            String nodeId = nodePath.firstKeyOf(Node.class).getNodeId().getValue();
            LOG.trace("merging op {} to {} size {}",getDescription(), nodeId, data.size());
        }
    }

    @Override
    public void mergeConfigData(Y dst,
                                Z src,
                                InstanceIdentifier<Node> nodePath) {
        List<T> data        = getData(src);
        List<T> transformed = transformConfigData(data, nodePath);
        setData(dst, transformed);
        if (!isEmptyList(data)) {
            String nodeId = nodePath.firstKeyOf(Node.class).getNodeId().getValue();
            LOG.trace("copying config {} to {} size {}",getDescription(), nodeId, data.size());
        }
    }

    @Override
    public void mergeConfigUpdate(Z existing,
                                  Z updated,
                                  Z orig,
                                  InstanceIdentifier<Node> nodePath,
                                  ReadWriteTransaction tx) {
        List<T> updatedData     = getData(updated);
        List<T> origData        = getData(orig);
        List<T> existingData    = getData(existing);
        transformUpdate(existingData, updatedData, origData, nodePath, CONFIGURATION, tx);
    }

    @Override
    public void mergeOpUpdate(Z origDst,
                              Z updatedSrc,
                              Z origSrc,
                              InstanceIdentifier<Node> nodePath,
                              ReadWriteTransaction tx) {
        List<T> updatedData     = getData(updatedSrc);
        List<T> origData        = getData(origSrc);
        List<T> existingData    = getData(origDst);
        transformUpdate(existingData, updatedData, origData, nodePath, OPERATIONAL, tx);
    }

    boolean areSameSize(List objA, List objB) {
        if (HwvtepHAUtil.isEmptyList(objA) && HwvtepHAUtil.isEmptyList(objB)) {
            return true;
        }
        if (!HwvtepHAUtil.isEmptyList(objA) && !HwvtepHAUtil.isEmptyList(objB)) {
            return objA.size() == objB.size();
        }
        return false;
    }


    static LocatorSetComparator locatorSetComparator = new LocatorSetComparator();

    static class LocatorSetComparator implements Comparator<LocatorSet>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(final LocatorSet updatedLocatorSet, final LocatorSet origLocatorSet) {
            InstanceIdentifier<?> updatedLocatorRefIndentifier = updatedLocatorSet.getLocatorRef().getValue();
            TpId updatedLocatorSetTpId = updatedLocatorRefIndentifier.firstKeyOf(TerminationPoint.class).getTpId();

            InstanceIdentifier<?> origLocatorRefIndentifier = origLocatorSet.getLocatorRef().getValue();
            TpId origLocatorSetTpId = origLocatorRefIndentifier.firstKeyOf(TerminationPoint.class).getTpId();

            if (updatedLocatorSetTpId.equals(origLocatorSetTpId)) {
                return 0;
            }
            return 1;
        }
    }

    private void writeToMdsal(final boolean create,
                              final ReadWriteTransaction tx,
                              final T data,
                              final InstanceIdentifier<T> identifier,
                              final LogicalDatastoreType datastoreType,
                              final int trialNo) {
        final boolean copyToChild = datastoreType == CONFIGURATION;
        String destination = copyToChild ? "child" : "parent";
        String nodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (trialNo == 0) {
            LOG.error("All trials exceed for tx iid {}", identifier);
            return;
        }
        final int trial = trialNo - 1;
        if (BatchedTransaction.isInProgress(datastoreType, identifier)) {
            if (BatchedTransaction.addCallbackIfInProgress(datastoreType, identifier,
                () -> writeToMdsal(create, tx, data, identifier, datastoreType, trial))) {
                return;
            }
        }
        Optional<T> existingDataOptional = null;
        try {
            existingDataOptional = tx.read(datastoreType, identifier).checkedGet();
        } catch (ReadFailedException ex) {
            LOG.error("Failed to read data {} from {}", identifier, datastoreType);
            return;
        }
        if (create) {
            if (isDataUpdated(existingDataOptional, data)) {
                tx.put(datastoreType, identifier, data);
            } else {
                LOG.info("ha copy skipped for {} destination with nodei id {} "
                        + " dataTypeName {} " , destination, nodeId, getCmdType());
            }
        } else {
            if (existingDataOptional.isPresent()) {
                tx.delete(datastoreType, identifier);
            } else {
                LOG.info("ha delete skipped for {} destination with nodei id {} "
                        + " dataTypeName {} " , destination, nodeId, getCmdType());
            }
        }
    }

    <T extends DataObject> boolean isDataUpdated(Optional<T> existingDataOptional, T newData) {
        return !existingDataOptional.isPresent() || !Objects.equals(existingDataOptional.get(), newData);
    }


    public abstract List<T> getData(Z node);

    public abstract void setData(Y builder, List<T> data);

    public abstract InstanceIdentifier<T> generateId(InstanceIdentifier<Node> id, T node);

    public abstract Identifier getKey(T data);

    public abstract String getDescription();

    public abstract T withoutUuid(T data);
}

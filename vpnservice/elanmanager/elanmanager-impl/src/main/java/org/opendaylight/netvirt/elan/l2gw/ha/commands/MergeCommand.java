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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
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

    static Logger LOG = LoggerFactory.getLogger(MergeCommand.class);

    Class<? extends Identifiable> classType = getType();

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

    Class<? extends Identifiable> getType() {
        Type type = getClass().getGenericSuperclass();
        return (Class<? extends Identifiable>)((ParameterizedType) type).getActualTypeArguments()[0];

    }

    private void writeToMdsal(final boolean create,
                              final ReadWriteTransaction tx,
                              final T data,
                              final InstanceIdentifier<T> identifier,
                              final boolean copyToChild,
                              final LogicalDatastoreType datastoreType,
                              final int trialNo) {
        BatchedTransaction batchedTransaction = (BatchedTransaction) tx;
        String destination = copyToChild ? "child" : "parent";
        String nodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (BatchedTransaction.isInProgress(datastoreType, identifier)) {
            if (BatchedTransaction.addCallbackIfInProgress(
                    datastoreType, identifier,
                () -> writeToMdsal(create, tx, data, identifier, copyToChild, datastoreType, trialNo - 1))) {
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
    }

    <T extends DataObject> boolean isDataUpdated(Optional<T> existingDataOptional, T newData) {
        return !existingDataOptional.isPresent() || !Objects.equals(existingDataOptional.get(), newData);
    }

    //TODO validate the perf of the following against direct setting of the data in dst node
    public void transformUpdate(List<T> existing,
                                List<T> updated,
                                List<T> orig,
                                InstanceIdentifier<Node> nodePath,
                                LogicalDatastoreType datastoreType,
                                ReadWriteTransaction tx) {

        if (classType == RemoteUcastMacs.class && datastoreType == OPERATIONAL) {
            return;
        }

        BatchedTransaction batchedTransaction = (BatchedTransaction) tx;
        List<Identifiable> added = batchedTransaction.getUpdatedData().get(classType);
        List<Identifiable> removed = batchedTransaction.getDeletedData().get(classType);

        if (added == null && removed == null) {
            return;
        }
        boolean copyToChild = HwvtepHACache.getInstance().isHAEnabledDevice(nodePath);
        if (added != null) {
            for (Identifiable add : added) {
                T addedElement = (T) add;
                InstanceIdentifier<T> transformedId = generateId(nodePath, addedElement);
                T transformedItem = transform(nodePath, addedElement);
                writeToMdsal(true, tx, transformedItem, transformedId, copyToChild, datastoreType, 50);
            }
        }

        if (removed != null) {
            for (Identifiable remove : removed) {
                T removedItem = (T) remove;
                InstanceIdentifier<T> transformedId = generateId(nodePath, removedItem);
                T transformedItem = transform(nodePath, removedItem);
                writeToMdsal(false, tx, transformedItem, transformedId, copyToChild, datastoreType, 50);
            }
        }
    }

    public List<T> transform(InstanceIdentifier<Node> nodePath, List<T> list) {
        if (list != null) {
            return list.stream().map(t -> transform(nodePath, t)).collect(Collectors.toList());
        }
        return null;
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
        if (classType == RemoteUcastMacs.class) {
            return;
        }
        setData(dst, data);
        if (!isEmptyList(data)) {
            String nodeId = nodePath.firstKeyOf(Node.class).getNodeId().getValue();
            LOG.trace("merging op {} to {} size {} {}",getDescription(), nodeId, data.size(), classType);
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

    static class LocatorSetComparator implements Comparator<LocatorSet> {
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

    public abstract List<T> getData(Z node);

    public abstract void setData(Y builder, List<T> data);

    public abstract InstanceIdentifier<T> generateId(InstanceIdentifier<Node> id, T node);

    public abstract Identifier getKey(T data);

    public abstract String getDescription();

    public abstract T withoutUuid(T data);
}

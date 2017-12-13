/*
 * Copyright (c) 2016 ,2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.DataUpdates;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LogicalSwitchesCmd;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class LogicalSwitchesCmdTest extends AbstractConcurrentDataBrokerTest {

    // Uncomment this to keep running this test indefinitely
    // This is very useful to detect concurrency issues, respectively prove
    // that the use of AbstractConcurrentDataBrokerTest instead of AbstractDataBrokerTest
    // does NOT cause any concurrency issues and make this test flaky...
    // public static @ClassRule RunUntilFailureClassRule classRepeater = new RunUntilFailureClassRule();
    // public @Rule RunUntilFailureRule repeater = new RunUntilFailureRule(classRepeater);

    DataBroker dataBroker;
    BatchedTransaction tx;
    DataUpdates dataUpdates;
    LogicalSwitchesCmd cmd = new LogicalSwitchesCmd();

    HwvtepGlobalAugmentationBuilder dstBuilder = new HwvtepGlobalAugmentationBuilder();

    HwvtepGlobalAugmentation existingData = null;//nodata
    HwvtepGlobalAugmentation srcData = null;

    HwvtepGlobalAugmentation updatedData = null;
    HwvtepGlobalAugmentation originalData = null;

    InstanceIdentifier<Node> haNodePath = HwvtepHAUtil.convertToInstanceIdentifier("ha");
    InstanceIdentifier<Node> d1NodePath = HwvtepHAUtil.convertToInstanceIdentifier("d1");
    InstanceIdentifier<Node> d2NodePath = HwvtepHAUtil.convertToInstanceIdentifier("d2");

    LogicalSwitches[] logicalSwitches = new LogicalSwitches[4];
    LogicalSwitches[] parentLogicalSwitches = new LogicalSwitches[4];
    InstanceIdentifier<LogicalSwitches>[] ids = new InstanceIdentifier[4];
    Map<LogicalSwitches, InstanceIdentifier<LogicalSwitches>> iids = new HashMap<>();

    String[][] data = new String[][] {
            {"ls1", "100"},
            {"ls2", "200"},
            {"ls3", "300"},
            {"ls4", "400"}
    };

    private List<Identifiable> updatedLogicalSwitches = null;
    private List<Identifiable> deletedData = null;

    @Before
    public void setupForHANode() {
        dataBroker = getDataBroker();
        tx = Mockito.spy(new BatchedTransaction(dataBroker));
        for (int i = 0 ; i < 4; i++) {
            logicalSwitches[i] = buildData(data[i][0], data[i][1]);
            ids[i] = haNodePath.augmentation(HwvtepGlobalAugmentation.class).child(LogicalSwitches.class,
                    new LogicalSwitchesKey(new HwvtepNodeName(data[i][0])));
            iids.put(logicalSwitches[i], ids[i]);
            parentLogicalSwitches[i] = new LogicalSwitchesBuilder(logicalSwitches[i])
                    .setLogicalSwitchUuid(HwvtepHAUtil.getUUid(logicalSwitches[i].getHwvtepNodeName().getValue()))
                    .build();
        }
        updatedLogicalSwitches = new ArrayList<>();
        deletedData = new ArrayList<>();
        Map<Class<? extends Identifiable>, List<Identifiable>> updateMap = new HashMap<>();
        Map<Class<? extends Identifiable>, List<Identifiable>> deleteMap = new HashMap<>();
        dataUpdates = new DataUpdates(updateMap, deleteMap);
        updateMap.put(LogicalSwitches.class, updatedLogicalSwitches);
        deleteMap.put(LogicalSwitches.class, deletedData);
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(dataBroker);
    }

    @After
    public void teardown() {
        for (ResourceBatchingManager.ShardResource i : ResourceBatchingManager.ShardResource.values()) {
            ResourceBatchingManager.getInstance().deregisterBatchableResource(i.name());
        }
    }


    @Test
    public void testD1Connect() throws Exception {
        srcData = getData(new LogicalSwitches[] {logicalSwitches[0], logicalSwitches[1]});
        cmd.mergeOperationalData(dstBuilder, existingData, srcData, haNodePath);
        assertEquals("should copy the logical switches ", 2, dstBuilder.getLogicalSwitches().size());
    }

    @Test
    public void testD2Connect() throws Exception {
        existingData = getData(new LogicalSwitches[] {logicalSwitches[0], logicalSwitches[1]});
        srcData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1],
                logicalSwitches[2], logicalSwitches[3]});
        cmd.mergeOperationalData(dstBuilder, existingData, srcData, haNodePath);
        assertEquals("should copy the logical switches ", 2, dstBuilder.getLogicalSwitches().size());
    }

    void addToUpdated(List<LogicalSwitches> added) {
        updatedLogicalSwitches.addAll(getData(added.toArray(new LogicalSwitches[]{})).getLogicalSwitches());
    }

    void addToDeleted(List<LogicalSwitches> deleted) throws TransactionCommitFailedException {
        for (LogicalSwitches deletedLs : deleted) {
            ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
            tx.put(LogicalDatastoreType.OPERATIONAL, iids.get(deletedLs), deletedLs, true);
            tx.submit().checkedGet();
        }
        deletedData.addAll(getData(deleted.toArray(new LogicalSwitches[]{})).getLogicalSwitches());
    }

    @Test
    public void testOneLogicalSwitchAddedUpdate() throws Exception {
        existingData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        originalData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        updatedData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1], logicalSwitches[2]});
        addToUpdated(Lists.newArrayList(logicalSwitches[2]));
        cmd.mergeOpUpdate(haNodePath, dataUpdates, tx);
        Mockito.verify(tx).put(LogicalDatastoreType.OPERATIONAL, ids[2], parentLogicalSwitches[2],
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    @Test
    public void testTwoLogicalSwitchesAddedUpdate() throws Exception {
        existingData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        originalData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        updatedData = getData(new LogicalSwitches[]{logicalSwitches[0],
                logicalSwitches[1], logicalSwitches[2], logicalSwitches[3]});
        addToUpdated(Lists.newArrayList(logicalSwitches[2], logicalSwitches[3]));
        cmd.mergeOpUpdate(haNodePath, dataUpdates, tx);
        Mockito.verify(tx).put(LogicalDatastoreType.OPERATIONAL, ids[2], parentLogicalSwitches[2],
                WriteTransaction.CREATE_MISSING_PARENTS);
        Mockito.verify(tx).put(LogicalDatastoreType.OPERATIONAL, ids[3], parentLogicalSwitches[3],
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    @Test
    public void testLogicalSwitchDeletedUpdate() throws Exception {
        existingData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1], logicalSwitches[2]});
        originalData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1], logicalSwitches[2]});
        updatedData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        addToDeleted(Lists.newArrayList(logicalSwitches[2]));
        cmd.mergeOpUpdate(haNodePath, dataUpdates, tx);
        Mockito.verify(tx).delete(LogicalDatastoreType.OPERATIONAL, ids[2]);
    }


    @Test
    public void testTwoLogicalSwitchesDeletedUpdate() throws Exception {
        existingData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1],
                logicalSwitches[2], logicalSwitches[3]});
        originalData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1],
                logicalSwitches[2], logicalSwitches[3]});
        updatedData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        addToDeleted(Lists.newArrayList(logicalSwitches[2], logicalSwitches[3]));
        cmd.mergeOpUpdate(haNodePath, dataUpdates, tx);
        Mockito.verify(tx).delete(LogicalDatastoreType.OPERATIONAL, ids[2]);
        Mockito.verify(tx).delete(LogicalDatastoreType.OPERATIONAL, ids[3]);
    }

    @Test
    public void testTwoAddTwoDeletedLogicalSwitchesUpdate() throws Exception {
        existingData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        originalData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        updatedData = getData(new LogicalSwitches[]{logicalSwitches[2], logicalSwitches[3]});
        addToUpdated(Lists.newArrayList(logicalSwitches[2], logicalSwitches[3]));
        addToDeleted(Lists.newArrayList(logicalSwitches[0], logicalSwitches[1]));
        cmd.mergeOpUpdate(haNodePath, dataUpdates, tx);
        Mockito.verify(tx).put(LogicalDatastoreType.OPERATIONAL, ids[2], parentLogicalSwitches[2],
                WriteTransaction.CREATE_MISSING_PARENTS);
        Mockito.verify(tx).put(LogicalDatastoreType.OPERATIONAL, ids[3], parentLogicalSwitches[3],
                WriteTransaction.CREATE_MISSING_PARENTS);
        Mockito.verify(tx).delete(LogicalDatastoreType.OPERATIONAL, ids[0]);
        Mockito.verify(tx).delete(LogicalDatastoreType.OPERATIONAL, ids[1]);
    }

    @Test
    public void testAllDeleteUpdate() throws Exception {
        existingData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        originalData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        updatedData = getData(new LogicalSwitches[]{});
        addToDeleted(Lists.newArrayList(logicalSwitches[0], logicalSwitches[1]));
        cmd.mergeOpUpdate(haNodePath, dataUpdates, tx);
        Mockito.verify(tx).delete(LogicalDatastoreType.OPERATIONAL, ids[0]);
        Mockito.verify(tx).delete(LogicalDatastoreType.OPERATIONAL, ids[1]);
    }

    @Test
    public void testNoUpdate() throws Exception {
        existingData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        originalData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        updatedData = getData(new LogicalSwitches[]{logicalSwitches[0], logicalSwitches[1]});
        cmd.mergeOpUpdate(haNodePath, dataUpdates, tx);
        Mockito.verify(dataUpdates).getUpdatedData();
        Mockito.verify(dataUpdates).getDeletedData();
        Mockito.verifyNoMoreInteractions(tx);
    }

    LogicalSwitches buildData(String name, String tunnelKey) {
        LogicalSwitchesBuilder logicalSwitchesBuilder = new LogicalSwitchesBuilder();
        logicalSwitchesBuilder.setKey(new LogicalSwitchesKey(new HwvtepNodeName(name)));
        logicalSwitchesBuilder.setTunnelKey(tunnelKey);
        logicalSwitchesBuilder.setHwvtepNodeName(new HwvtepNodeName(name));
        return logicalSwitchesBuilder.build();
    }

    HwvtepGlobalAugmentation getData(LogicalSwitches[] elements) {
        HwvtepGlobalAugmentationBuilder newDataBuilder = new HwvtepGlobalAugmentationBuilder();
        List<LogicalSwitches> ls = new ArrayList<>();
        for (LogicalSwitches ele : elements) {
            ls.add(ele);
        }
        newDataBuilder.setLogicalSwitches(ls);
        return newDataBuilder.build();
    }
}

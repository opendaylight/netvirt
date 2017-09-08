/*
 * Copyright (C) 2016, 2017 Red Hat Inc., and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import com.google.common.base.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.testutils.AsyncEventsWaiter;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.evpn.listeners.ElanMacEntryListener;
import org.opendaylight.netvirt.elan.evpn.listeners.EvpnElanInstanceListener;
import org.opendaylight.netvirt.elan.evpn.listeners.MacVrfEntryListener;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.tests.utils.InterfaceMgrTestImpl;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * End-to-end test of IElanService.
 *
 * @author Michael Vorburger
 */
public class ElanServiceTest extends  ElanServiceTestBase {

    static final Logger LOG = LoggerFactory.getLogger(InterfaceMgrTestImpl.class);

    // TODO as-is, this test is flaky; as uncommenting will show
    // Uncomment this to keep running this test indefinitely
    // This is very useful to detect concurrency issues (such as https://bugs.opendaylight.org/show_bug.cgi?id=7538)
    // public static @ClassRule RunUntilFailureClassRule classRepeater = new RunUntilFailureClassRule();
    // public @Rule RunUntilFailureRule repeater = new RunUntilFailureRule(classRepeater);

    public @Rule LogRule logRule = new LogRule();
    public @Rule MethodRule guice = new GuiceRule(
            ElanServiceTestModule.class, TestableDataTreeChangeListenerModule.class);

    private @Inject IElanService elanService;
    private @Inject AsyncEventsWaiter asyncEventsWaiter;
    private @Inject IdManagerService idManager;
    private @Inject EvpnElanInstanceListener evpnElanInstanceListener;
    private @Inject ElanMacEntryListener elanMacEntryListener;
    private @Inject MacVrfEntryListener macVrfEntryListener;
    private @Inject IBgpManager bgpManager;
    private @Inject IVpnManager vpnManager;
    private @Inject EvpnUtils evpnUtils;

    private SingleTransactionDataBroker singleTxdataBroker;

    @Before public void before() {
        singleTxdataBroker = new SingleTransactionDataBroker(dataBroker);
        evpnUtils.setBgpManager(bgpManager);
        evpnUtils.setVpnManager(vpnManager);

        try {
            setupItm();
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to setup itm");
        }
    }

    @Test public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to first test the ElanServiceTestModule
    }

    @Test public void checkSMAC() throws Exception {
        /*Create Elan instance*/
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        /*Add Elan interface*/
        InterfaceDetails interfaceDetails = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceDetails);

        /*Read Elan instance*/
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        /*Read and Compare SMAC flow*/
        String flowId =  new StringBuffer()
                .append(NwConstants.ELAN_SMAC_TABLE)
                .append(actualElanInstances.getElanTag())
                .append(DPN1_ID)
                .append(interfaceDetails.getLportTag())
                .append(interfaceDetails.getMac())
                .toString();
        InstanceIdentifier<Flow> flowInstanceIidSrc = getFlowIid(NwConstants.ELAN_SMAC_TABLE,
                new FlowId(flowId), DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidSrc);

        Flow flowSrc = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidSrc);
        flowSrc = getFlowWithoutCookie(flowSrc);

        Flow expected = ExpectedObjects.checkSmac(flowId, interfaceDetails, actualElanInstances);
        AssertDataObjects.assertEqualBeans(expected, flowSrc);
    }

    private void awaitForElanTag(String elanName) {
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanName)).build();
        getAwaiter().until(() -> {
            Optional<ElanInstance> elanInstance = MDSALUtil.read(dataBroker, CONFIGURATION, elanInstanceIid);
            return elanInstance.isPresent() && elanInstance.get().getElanTag() != null;
        });
    }

    @Test public void checkDmacSameDPN() throws Exception {
        /*Create Elan instance*/
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        /*Add Elan interface in DPN1*/
        InterfaceDetails interfaceDetails = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceDetails);

        /*Read Elan instance*/
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        /*Read DMAC Flow in DPN1*/
        String flowId =  new StringBuffer()
                .append(NwConstants.ELAN_DMAC_TABLE)
                .append(actualElanInstances.getElanTag())
                .append(DPN1_ID)
                .append(interfaceDetails.getLportTag())
                .append(interfaceDetails.getMac())
                .toString();
        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE,
                new FlowId(flowId), DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);

        Flow flowDst = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidDst);
        flowDst = getFlowWithoutCookie(flowDst);

        Flow expected = ExpectedObjects.checkDmacOfSameDpn(flowId, interfaceDetails, actualElanInstances);
        AssertDataObjects.assertEqualBeans(expected, flowDst);
    }

    @Test public void checkDmacOfOtherDPN() throws Exception {
        /*Create Elan instance*/
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        /*Add Elan MAC1 in DPN1 */
        InterfaceDetails interfaceDetails = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceDetails);

        /*Add Elan MAC1 in DPN2 */
        interfaceDetails = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceDetails);

        /*Read Elan Instance*/
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        /*Read and Compare DMAC flow in DPN1 for MAC1 of DPN2*/
        FlowId flowId = new FlowId(
                ElanUtils.getKnownDynamicmacFlowRef((short)51,
                        DPN1_ID,
                        DPN2_ID,
                        interfaceDetails.getMac(),
                        actualElanInstances.getElanTag()));
        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE, flowId, DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);

        Flow flowDst = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidDst);
        flowDst = getFlowWithoutCookie(flowDst);

        TunnelInterfaceDetails tepDetails = EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN2_ID_STR);
        Flow expected = ExpectedObjects.checkDmacOfOtherDPN(flowId.getValue(), interfaceDetails, tepDetails,
                actualElanInstances);
        AssertDataObjects.assertEqualBeans(expected, flowDst);
    }

}

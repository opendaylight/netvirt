/*
 * Copyright (C) 2016 Red Hat Inc., and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.tests.utils.InterfaceMgrTestImpl;
import org.opendaylight.netvirt.elanmanager.tests.utils.ItmRpcTestImpl;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesBuilder;
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
    private static final String TEST_ELAN_NAME = "TestElanName";
    private static final String TEST_INTERFACE_NAME = "TestElanInterfaceName";

    // TODO as-is, this test is flaky; as uncommenting will show
    // Uncomment this to keep running this test indefinitely
    // This is very useful to detect concurrency issues (such as https://bugs.opendaylight.org/show_bug.cgi?id=7538)
    // public static @ClassRule RunUntilFailureClassRule classRepeater = new RunUntilFailureClassRule();
    // public @Rule RunUntilFailureRule repeater = new RunUntilFailureRule(classRepeater);

    public @Rule LogRule logRule = new LogRule();
    public @Rule MethodRule guice = new GuiceRule(
            ElanServiceTestModule.class, TestableDataTreeChangeListenerModule.class);

    private @Inject DataBroker dataBroker;
    private @Inject IElanService elanService;
    private @Inject AsyncEventsWaiter asyncEventsWaiter;
    private @Inject InterfaceMgrTestImpl interfaceMgr;
    private @Inject ItmRpcTestImpl itmRpc;
    private @Inject IdManagerService idManager;
    private @Inject ElanInstanceManager elanInstanceManager;
    private @Inject EvpnElanInstanceListener evpnElanInstanceListener;
    private @Inject ElanMacEntryListener elanMacEntryListener;
    private @Inject MacVrfEntryListener macVrfEntryListener;
    private @Inject IBgpManager bgpManager;
    private @Inject IVpnManager vpnManager;
    private @Inject EvpnUtils evpnUtils;

    private SingleTransactionDataBroker singleTxdataBroker;

    @Before public void before() {
        singleTxdataBroker = new SingleTransactionDataBroker(dataBroker);
    }

    @Test public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to first test the ElanServiceTestModule
    }


    @Test public void checkSMAC() throws Exception {
        db = dataBroker;
        evpnUtils.setBgpManager(bgpManager);
        evpnUtils.setVpnManager(vpnManager);

        /*Create Elan instance*/
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);

        /*Add tap port and tunnel ports in DPN1 and DPN2*/
        interfaceMgr.addInterface(dataBroker, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1));
        interfaceMgr.addInterface(dataBroker, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2));

        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);
        itmRpc.addInterface(DPN1_ID,
                DPN2_TEPIP, EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN2_ID_STR).getTrunkInterfaceName());

        /*Add Elan interface*/
        InterfaceDetails interfaceDetails = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceDetails);

        Thread.sleep(5000);

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

        //TunnelInterfaceDetails tepDetails = EXTN_INTFS.get(DPN1_ID_STR+":"+DPN2_ID_STR);
        Flow expected = ExpectedObjects.checkSmac(flowId, interfaceDetails, actualElanInstances);
        AssertDataObjects.assertEqualBeans(expected, flowSrc);
        LOG.error("Written flow is {}", flowSrc);

    }

    @Test public void checkDmacSameDPN() throws Exception {
        db = dataBroker;
        evpnUtils.setBgpManager(bgpManager);
        evpnUtils.setVpnManager(vpnManager);

        /*Create Elan instance*/
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);

        /*Add tap port in DPN1*/
        interfaceMgr.addInterface(dataBroker, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1));
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);

        /*Add Elan interface in DPN1*/
        InterfaceDetails interfaceDetails = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceDetails);

        Thread.sleep(5000);

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
        LOG.error("Written flow is {}", flowDst);

    }

    @Test public void checkDmacOfOtherDPN() throws Exception {
        db = dataBroker;
        evpnUtils.setBgpManager(bgpManager);
        evpnUtils.setVpnManager(vpnManager);

        /*Create Elan instance*/
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);


        /*Add tap port and tunnel ports in DPN1 and DPN2*/
        interfaceMgr.addInterface(dataBroker, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1));
        interfaceMgr.addInterface(dataBroker, ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1));

        interfaceMgr.addTunnelInterface(dataBroker, EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN2_ID_STR));
        interfaceMgr.addTunnelInterface(dataBroker, EXTN_INTFS.get(DPN2_ID_STR + ":" + DPN1_ID_STR));

        /*Add DPN1 and DPN2 TEPs*/
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);
        itmRpc.addDpn(DPN2_ID, DPN2_TEPIP);

        /*add external interface*/
        itmRpc.addInterface(DPN1_ID,
                DPN2_TEPIP, EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN2_ID_STR).getTrunkInterfaceName());
        itmRpc.addInterface(DPN2_ID,
                DPN1_TEPIP, EXTN_INTFS.get(DPN2_ID_STR + ":" + DPN1_ID_STR).getTrunkInterfaceName());


        /*Add Elan MAC1 in DPN1 */
        InterfaceDetails interfaceDetails = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceDetails);

        /*Add Elan MAC1 in DPN2 */
        interfaceDetails = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceDetails);

        Thread.sleep(5000);

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
        LOG.error("Written flow is {}", flowDst);

    }


    public void addElanInterface(String elanInstanceName, InterfaceDetails interfaceDetails) {
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        String interfaceName = interfaceDetails.getName();

        if (existingElanInstance != null) {
            ElanInterfaceBuilder elanInterfaceBuilder = new ElanInterfaceBuilder()
                    .setElanInstanceName(elanInstanceName)
                    .setName(interfaceName)
                    .setKey(new ElanInterfaceKey(interfaceName));

            StaticMacEntriesBuilder staticMacEntriesBuilder = new StaticMacEntriesBuilder();
            List<StaticMacEntries> staticMacEntries = new ArrayList<>();
            List<PhysAddress> physAddressList = Collections.singletonList(new PhysAddress(interfaceDetails.getMac()));
            for (PhysAddress physAddress : physAddressList) {
                staticMacEntries.add(staticMacEntriesBuilder.setMacAddress(physAddress)
                        .setIpPrefix(new IpAddress(new Ipv4Address(interfaceDetails.getPrefix()))).build());
            }
            elanInterfaceBuilder.setStaticMacEntries(staticMacEntries);
            ElanInterface elanInterface = elanInterfaceBuilder.build();

            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
        }
    }

    public void deleteElanInterface(InterfaceDetails interfaceDetails) {
        String interfaceName = interfaceDetails.getName();
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));

    }

}

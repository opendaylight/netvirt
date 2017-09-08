/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.testutils.InterfaceDetails;
import org.opendaylight.genius.testutils.InterfaceMgrTestImpl;
import org.opendaylight.genius.testutils.ItmRpcTestImpl;
import org.opendaylight.genius.testutils.TunnelInterfaceDetails;
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Instructions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class ElanServiceTestBase {

    protected  @Inject DataBroker dataBroker;
    protected  @Inject InterfaceMgrTestImpl interfaceMgr;
    protected  @Inject ItmRpcTestImpl itmRpc;
    protected  @Inject ElanInstanceManager elanInstanceManager;

    public static BigInteger DPN1_ID = new BigInteger("1");
    public static BigInteger DPN2_ID = new BigInteger("2");

    public static String DPN1_ID_STR = "1";
    public static String DPN2_ID_STR = "2";

    public static String DPN1_TEPIP = "192.168.56.30";
    public static String DPN2_TEPIP = "192.168.56.40";
    public static String TOR1_TEPIP = "192.168.56.50";
    public static String DCGW_TEPIP = "192.168.56.60";

    public static String ELAN1 = "34701c04-1118-4c65-9425-78a80d49a211";
    public static Long ELAN1_SEGMENT_ID = 100L;


    static String DPN1MAC1 = "10:00:00:00:00:01";
    static String DPN1MAC2 = "10:00:00:00:00:02";
    static String DPN2MAC1 = "10:00:00:00:00:03";
    static String DPN2MAC2 = "10:00:00:00:00:04";
    static String TOR1MAC1 = "10:00:00:00:00:05";
    static String TOR1MAC2 = "10:00:00:00:00:06";

    static String EVPNRECVMAC1 = "10:00:00:00:00:51";
    static String EVPNRECVMAC2 = "10:00:00:00:00:52";

    static String EVPNRECVIP1 = "192.168.122.51";
    static String EVPNRECVIP2 = "192.168.122.52";

    static String TOR1NODEID = "hwvtep://uuid/34701c04-1118-4c65-9425-78a80d49a211";
    static String DCGWID = DCGW_TEPIP;

    public static String RD = "100:1";
    public static String EVPN1 = "evpn1";

    static Map<String, InterfaceDetails> ELAN_INTERFACES = new HashMap<>();

    static {
        /*ELAN1+":"+DPN1MAC1 ->
        new(String elan, String name, BigInteger dpId, int portno, String mac, String prefix, int lportTag)*/
        ELAN_INTERFACES.put(ELAN1 + ":" + DPN1MAC1 , new InterfaceDetails(ELAN1, "23701c04-1118-4c65-9425-78a80d49a211",
                DPN1_ID, 1, DPN1MAC1, "192.168.122.10", 10));
        ELAN_INTERFACES.put(ELAN1 + ":" + DPN1MAC2 , new InterfaceDetails(ELAN1, "23701c04-1218-4c65-9425-78a80d49a211",
                DPN1_ID, 2, DPN1MAC2, "192.168.122.11", 11));
        ELAN_INTERFACES.put(ELAN1 + ":" + DPN2MAC1 , new InterfaceDetails(ELAN1, "23701c04-2118-4c65-9425-78a80d49a211",
                DPN2_ID, 3, DPN2MAC1, "192.168.122.12", 12));
        ELAN_INTERFACES.put(ELAN1 + ":" + DPN2MAC2 , new InterfaceDetails(ELAN1, "23701c04-2218-4c65-9425-78a80d49a211",
                DPN2_ID, 4, DPN2MAC2, "192.168.122.13", 13));
    }

    static Map<String, TunnelInterfaceDetails> EXTN_INTFS = new HashMap<>();

    static {
        //new(String elan, String name, BigInteger dpId, int portno, String mac, String prefix, int lportTag) {
        EXTN_INTFS.put(DPN1_ID_STR + ":" + DPN2_ID_STR, new TunnelInterfaceDetails(DPN1_ID_STR, 5,  DPN2_ID_STR,
                DPN1_TEPIP, DPN2_TEPIP, 14));
        EXTN_INTFS.put(DPN1_ID_STR + ":" + TOR1NODEID, new TunnelInterfaceDetails(DPN1_ID_STR, 6, TOR1NODEID,
                DPN1_TEPIP, TOR1_TEPIP, 15));
        EXTN_INTFS.put(DPN2_ID_STR + ":" + DPN1_ID_STR, new TunnelInterfaceDetails(DPN1_ID_STR, 7, DPN1_ID_STR,
                DPN2_TEPIP, DPN1_TEPIP, 16));
        EXTN_INTFS.put(DPN2_ID_STR + ":" + TOR1NODEID, new TunnelInterfaceDetails(DPN1_ID_STR, 8, TOR1NODEID,
                DPN2_TEPIP, TOR1_TEPIP, 17));
        EXTN_INTFS.put(DPN1_ID_STR + ":" + DCGWID, new TunnelInterfaceDetails(DPN1_ID_STR, 9, DCGWID,
                DPN1_TEPIP, DCGW_TEPIP, 18));
        EXTN_INTFS.put(DPN2_ID_STR + ":" + DCGWID, new TunnelInterfaceDetails(DPN1_ID_STR, 10, DCGWID,
                DPN2_TEPIP, DCGW_TEPIP, 19));
    }

    protected ConditionFactory getAwaiter() {
        return Awaitility.await("TestableListener")
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS);
    }

    void awaitForData(LogicalDatastoreType dsType, InstanceIdentifier<? extends DataObject> iid) {
        getAwaiter().until(() -> MDSALUtil.read(dataBroker, dsType, iid).isPresent());
    }

    void awaitForDataDelete(LogicalDatastoreType dsType, InstanceIdentifier<? extends DataObject> iid) {
        getAwaiter().until(() -> !MDSALUtil.read(dataBroker, dsType, iid).isPresent());
    }

    Flow getFlowWithoutCookie(Flow flow) {
        FlowBuilder flowBuilder = new FlowBuilder(flow);
        return flowBuilder.setCookie(null).build();
    }

    Flow getSortedActions(Flow flow) {
        FlowBuilder flowBuilder = new FlowBuilder(flow);
        Instructions instructions = flowBuilder.getInstructions();
        InstructionsBuilder builder = new InstructionsBuilder();
        InstructionBuilder instructionBuilder = new InstructionBuilder(instructions.getInstruction().get(0));
        instructionBuilder.setInstruction(sortActions(instructionBuilder.getInstruction()));
        builder.setInstruction(Lists.newArrayList(instructionBuilder.build()));
        return flowBuilder.setInstructions(builder.build()).build();
    }

    org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.Instruction
        sortActions(org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.Instruction input) {
        if (input instanceof  ApplyActionsCase) {
            List<Action> action = new ArrayList<>(((ApplyActionsCase)input).getApplyActions().getAction());
            Collections.sort(action, new Comparator<Action>() {
                @Override
                public int compare(Action o1, Action o2) {
                    return o1.getOrder().compareTo(o2.getOrder());
                }
            });

            ApplyActions actions = new ApplyActionsBuilder().setAction(action).build();
            return new ApplyActionsCaseBuilder().setApplyActions(actions).build();
        }
        return null;
    }

    protected void setupItm() throws TransactionCommitFailedException {
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
    }

    protected InstanceIdentifier<Flow> getFlowIid(short tableId, FlowId flowid, BigInteger dpnId) {

        FlowKey flowKey = new FlowKey(new FlowId(flowid));
        NodeId nodeId =
                new NodeId("openflow:" + dpnId);
        Node nodeDpn =
                new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();
        return InstanceIdentifier.builder(Nodes.class)
                .child(Node.class,
                        nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(tableId)).child(Flow.class, flowKey).build();
    }


    protected void createElanInstance(String elan1, Long elan1SegmentId) {
        ElanInstance elanInstance = ExpectedObjects.createElanInstance(elan1, elan1SegmentId);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                ElanHelper.getElanInstanceConfigurationDataPath(elan1), elanInstance);
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
}

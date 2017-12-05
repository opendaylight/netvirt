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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.testutils.TestInterfaceManager;

import org.opendaylight.genius.testutils.interfacemanager.TunnelInterfaceDetails;
import org.opendaylight.genius.testutils.itm.ItmRpcTestImpl;
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.elanmanager.tests.utils.InterfaceHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class ElanServiceTestBase {

    protected  @Inject DataBroker dataBroker;
    protected  @Inject TestInterfaceManager interfaceMgr;
    protected  @Inject ItmRpcTestImpl itmRpc;
    protected  @Inject ElanInstanceManager elanInstanceManager;
    protected @Inject SingleTransactionDataBroker singleTxdataBroker;
    protected @Inject OdlInterfaceRpcService odlInterfaceRpcService;
    public static final String ELAN1 = "34701c04-1118-4c65-9425-78a80d49a211";
    public static final Long ELAN1_SEGMENT_ID = 100L;

    protected static final BigInteger DPN1_ID = new BigInteger("1");
    protected static final BigInteger DPN2_ID = new BigInteger("2");
    protected static final BigInteger DPN3_ID = new BigInteger("3");

    protected static final String DPN1_ID_STR = "1";
    protected static final String DPN2_ID_STR = "2";
    protected static final String DPN3_ID_STR = "3";

    protected static final String DPN1_TEPIP = "192.168.56.30";
    protected static final String DPN2_TEPIP = "192.168.56.40";
    protected static final String DPN3_TEPIP = "192.168.56.50";
    protected static final String TOR1_TEPIP = "192.168.56.60";
    protected static final String TOR2_TEPIP = "192.168.56.70";
    public static final String DCGW_TEPIP = "192.168.56.80";

    protected static final String DPN1MAC1 = "10:00:00:00:00:01";
    protected static final String DPN1MAC2 = "10:00:00:00:00:02";

    protected static final String DPN2MAC1 = "10:00:00:00:00:03";
    protected static final String DPN2MAC2 = "10:00:00:00:00:04";

    protected static final String DPN3MAC1 = "10:00:00:00:00:05";
    protected static final String DPN3MAC2 = "10:00:00:00:00:06";

    protected static final String TOR1MAC1 = "10:00:00:00:00:07";
    protected static final String TOR1MAC2 = "10:00:00:00:00:08";

    protected static final String TOR2MAC1 = "10:00:00:00:00:09";
    protected static final String TOR2MAC2 = "10:00:00:00:00:10";

    protected static final String DPN1TODPN2TNLMAC = "91:00:00:00:00:01";
    protected static final String DPN1TOTOR1TNLMAC = "91:00:00:00:00:02";
    protected static final String DPN1TODCGWTNLMAC = "91:00:00:00:00:03";
    protected static final String DPN1TODPN3TNLMAC = "91:00:00:00:00:04";

    protected static final String DPN2TODPN1TNLMAC = "92:00:00:00:00:01";
    protected static final String DPN2TOTOR1TNLMAC = "92:00:00:00:00:02";
    protected static final String DPN2TODCGWTNLMAC = "92:00:00:00:00:03";
    protected static final String DPN2TODPN3TNLMAC = "92:00:00:00:00:04";

    protected static final String DPN3TODPN1TNLMAC = "93:00:00:00:00:01";
    protected static final String DPN3TODPN2TNLMAC = "93:00:00:00:00:02";
    protected static final String DPN3TOTOR1TNLMAC = "93:00:00:00:00:03";

    protected static final String DPN1TOTOR2TNLMAC = "94:00:00:00:00:01";
    protected static final String DPN2TOTOR2TNLMAC = "94:00:00:00:00:02";
    protected static final String DPN3TOTOR2TNLMAC = "94:00:00:00:00:03";


    protected static final String DPN1IP1 = "10.0.0.11";
    protected static final String DPN1IP2 = "10.0.0.12";
    protected static final String DPN2IP1 = "10.0.0.13";
    protected static final String DPN2IP2 = "10.0.0.14";
    protected static final String DPN3IP1 = "10.0.0.15";
    protected static final String DPN3IP2 = "10.0.0.16";

    protected static final String EVPNRECVMAC1 = "10:00:00:00:00:51";
    protected static final String EVPNRECVMAC2 = "10:00:00:00:00:52";

    protected static final String EVPNRECVIP1 = "192.168.122.51";
    protected static final String EVPNRECVIP2 = "192.168.122.52";

    protected static final String TOR1NODEID = "hwvtep://uuid/34701c04-1118-4c65-9425-78a80d49a211";
    protected static final String TOR2NODEID = "hwvtep://uuid/34701c04-1118-4c65-9425-78a80d49a212";

    protected static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.tbd.params
            .xml.ns.yang.network.topology.rev131021.network.topology.topology.Node>
            tor1NodeIid = createInstanceIdentifier(TOR1NODEID);
    protected static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.tbd.params
            .xml.ns.yang.network.topology.rev131021.network.topology.topology.Node>
            tor2NodeIid = createInstanceIdentifier(TOR2NODEID);

    protected static final String DCGWID = DCGW_TEPIP;

    public static final String RD = "100:1";
    public static final String EVPN1 = "evpn1";

    protected static Map<String, Pair<InterfaceInfo, String>> ELAN_INTERFACES = new HashMap<>();
    protected static Map<String, TunnelInterfaceDetails> EXTN_INTFS = new HashMap<>();

    static {
        //Adding elan dpn macs
        /*ELAN1+":"+DPN1MAC1 ->
        (vlanInterfaceInfo(String interfaceName, BigInteger dpId, int portNo, int lportTag, String mac), vmPrefix)*/
        ELAN_INTERFACES.put(ELAN1 + ":" + DPN1MAC1 ,
                new ImmutablePair(InterfaceHelper
                        .buildVlanInterfaceInfo("23701c04-1118-4c65-9425-78a80d49a211",
                DPN1_ID, 1, 10, DPN1MAC1), DPN1IP1));

        ELAN_INTERFACES.put(ELAN1 + ":" + DPN1MAC2 ,
                new ImmutablePair(InterfaceHelper
                        .buildVlanInterfaceInfo("23701c04-1218-4c65-9425-78a80d49a211",
                DPN1_ID, 2, 11, DPN1MAC2), DPN1IP2));

        ELAN_INTERFACES.put(ELAN1 + ":" + DPN2MAC1 ,
                new ImmutablePair(InterfaceHelper
                        .buildVlanInterfaceInfo("23701c04-2118-4c65-9425-78a80d49a211",
                        DPN2_ID, 3, 12, DPN2MAC1), DPN2IP1));

        ELAN_INTERFACES.put(ELAN1 + ":" + DPN2MAC2 ,
                new ImmutablePair(InterfaceHelper
                        .buildVlanInterfaceInfo("23701c04-2218-4c65-9425-78a80d49a211",
                        DPN2_ID, 4, 13, DPN2MAC2), DPN2IP2));

        ELAN_INTERFACES.put(ELAN1 + ":" + DPN3MAC1 ,
                new ImmutablePair(InterfaceHelper
                        .buildVlanInterfaceInfo("23701c04-3118-4c65-9425-78a80d49a211",
                        DPN3_ID, 5, 14, DPN3MAC1), DPN3IP1));

        ELAN_INTERFACES.put(ELAN1 + ":" + DPN3MAC2 ,
                new ImmutablePair(InterfaceHelper
                        .buildVlanInterfaceInfo("23701c04-3218-4c65-9425-78a80d49a211",
                        DPN3_ID, 6, 15, DPN3MAC2), DPN3IP2));

        //Adding the external tunnel interfaces
        EXTN_INTFS.put(DPN1_ID_STR + ":" + DPN2_ID_STR, new TunnelInterfaceDetails(DPN1_TEPIP, DPN2_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-10", DPN1_ID, 5, 14, DPN1TODPN2TNLMAC)));

        EXTN_INTFS.put(DPN1_ID_STR + ":" + TOR1NODEID, new TunnelInterfaceDetails(DPN1_TEPIP, TOR1_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-12", DPN1_ID, 6, 15, DPN1TOTOR1TNLMAC)));
        EXTN_INTFS.put(DPN1_ID_STR + ":" + TOR1_TEPIP, EXTN_INTFS.get(DPN1_ID_STR + ":" + TOR1NODEID));

        EXTN_INTFS.put(DPN2_ID_STR + ":" + DPN1_ID_STR, new TunnelInterfaceDetails(DPN2_TEPIP, DPN1_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-11", DPN2_ID, 7, 16, DPN2TODPN1TNLMAC)));

        EXTN_INTFS.put(DPN2_ID_STR + ":" + TOR1NODEID, new TunnelInterfaceDetails(DPN2_TEPIP, TOR1_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-13", DPN2_ID, 8, 17, DPN2TOTOR1TNLMAC)));
        EXTN_INTFS.put(DPN2_ID_STR + ":" + TOR1_TEPIP, EXTN_INTFS.get(DPN2_ID_STR + ":" + TOR1NODEID));

        EXTN_INTFS.put(DPN1_ID_STR + ":" + DPN3_ID_STR, new TunnelInterfaceDetails(DPN1_TEPIP, DPN3_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-14", DPN1_ID, 9, 18, DPN1TODPN3TNLMAC)));

        EXTN_INTFS.put(DPN3_ID_STR + ":" + DPN1_ID_STR, new TunnelInterfaceDetails(DPN3_TEPIP, DPN1_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-15", DPN3_ID, 10, 19, DPN3TODPN1TNLMAC)));

        EXTN_INTFS.put(DPN3_ID_STR + ":" + DPN2_ID_STR, new TunnelInterfaceDetails(DPN3_TEPIP, DPN2_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-16", DPN3_ID, 11, 20, DPN3TODPN2TNLMAC)));

        EXTN_INTFS.put(DPN2_ID_STR + ":" + DPN3_ID_STR, new TunnelInterfaceDetails(DPN2_TEPIP, DPN3_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-17", DPN2_ID, 12, 21, DPN2TODPN3TNLMAC)));

        EXTN_INTFS.put(DPN3_ID_STR + ":" + TOR1NODEID, new TunnelInterfaceDetails(DPN3_TEPIP, TOR1_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-18", DPN3_ID, 13, 22, DPN3TOTOR1TNLMAC)));
        EXTN_INTFS.put(DPN3_ID_STR + ":" + TOR1_TEPIP, EXTN_INTFS.get(DPN3_ID_STR + ":" + TOR1NODEID));

        EXTN_INTFS.put(DPN1_ID_STR + ":" + TOR2NODEID, new TunnelInterfaceDetails(DPN1_TEPIP, TOR2_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-19", DPN1_ID, 14, 23, DPN1TOTOR2TNLMAC)));
        EXTN_INTFS.put(DPN1_ID_STR + ":" + TOR2_TEPIP, EXTN_INTFS.get(DPN1_ID_STR + ":" + TOR2NODEID));

        EXTN_INTFS.put(DPN2_ID_STR + ":" + TOR2NODEID, new TunnelInterfaceDetails(DPN2_TEPIP, TOR2_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-20", DPN2_ID, 15, 24, DPN2TOTOR2TNLMAC)));
        EXTN_INTFS.put(DPN2_ID_STR + ":" + TOR2_TEPIP, EXTN_INTFS.get(DPN2_ID_STR + ":" + TOR2NODEID));

        EXTN_INTFS.put(DPN3_ID_STR + ":" + TOR2NODEID, new TunnelInterfaceDetails(DPN3_TEPIP, TOR2_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-21", DPN3_ID, 16, 25, DPN3TOTOR2TNLMAC)));
        EXTN_INTFS.put(DPN3_ID_STR + ":" + TOR2_TEPIP, EXTN_INTFS.get(DPN3_ID_STR + ":" + TOR2NODEID));

        EXTN_INTFS.put(DPN1_ID_STR + ":" + DCGWID, new TunnelInterfaceDetails(DPN1_TEPIP, DCGW_TEPIP, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-22", DPN1_ID, 17, 26, DPN1TODCGWTNLMAC)));

        EXTN_INTFS.put(DPN2_ID_STR + ":" + DCGWID, new TunnelInterfaceDetails(DPN2_TEPIP, DCGWID, true,
                InterfaceHelper.buildVxlanInterfaceInfo("tun23701c04-23", DPN2_ID, 18, 27, DPN2TODCGWTNLMAC)));

    }

    protected ConditionFactory getAwaiter() {
        return Awaitility.await("TestableListener")
                .atMost(30, TimeUnit.SECONDS)//TODO constant
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

    protected static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology
            .rev131021.network.topology.topology.Node> createInstanceIdentifier(String nodeIdString) {
        org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId nodeId
                = new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network
                .topology.rev131021.NodeId(new Uri(nodeIdString));
        org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology
                .topology.NodeKey nodeKey = new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang
                .network.topology.rev131021.network.topology.topology.NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(new TopologyId(new Uri("hwvtep:1")));
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang
                        .network.topology.rev131021.network.topology.topology.Node.class, nodeKey)
                .build();
    }


    protected void setupItm() throws TransactionCommitFailedException {
        /*Add tap port and tunnel ports in DPN1 and DPN2*/
        interfaceMgr.addInterfaceInfo(ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft());
        interfaceMgr.addInterfaceInfo(ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft());
        interfaceMgr.addInterfaceInfo(ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft());
        interfaceMgr.addInterfaceInfo(ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft());
        interfaceMgr.addInterfaceInfo(ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft());
        interfaceMgr.addInterfaceInfo(ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft());

        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN2_ID_STR));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN2_ID_STR + ":" + DPN1_ID_STR));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN3_ID_STR));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN3_ID_STR + ":" + DPN1_ID_STR));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN2_ID_STR + ":" + DPN3_ID_STR));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN3_ID_STR + ":" + DPN2_ID_STR));

        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN1_ID_STR + ":" + TOR1NODEID));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN2_ID_STR + ":" + TOR1NODEID));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN3_ID_STR + ":" + TOR1NODEID));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN1_ID_STR + ":" + TOR2NODEID));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN2_ID_STR + ":" + TOR2NODEID));
        interfaceMgr.addTunnelInterface(EXTN_INTFS.get(DPN3_ID_STR + ":" + TOR2NODEID));

        /*Add DPN1 and DPN2 TEPs*/
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);
        itmRpc.addDpn(DPN2_ID, DPN2_TEPIP);
        itmRpc.addDpn(DPN3_ID, DPN3_TEPIP);

        /*add external interface*/
        itmRpc.addInterface(DPN1_ID,
                DPN2_TEPIP, EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN2_ID_STR).getInterfaceInfo().getInterfaceName());
        itmRpc.addInterface(DPN2_ID,
                DPN1_TEPIP, EXTN_INTFS.get(DPN2_ID_STR + ":" + DPN1_ID_STR).getInterfaceInfo().getInterfaceName());
        itmRpc.addInterface(DPN1_ID,
                DPN3_TEPIP, EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN3_ID_STR).getInterfaceInfo().getInterfaceName());
        itmRpc.addInterface(DPN3_ID,
                DPN1_TEPIP, EXTN_INTFS.get(DPN3_ID_STR + ":" + DPN1_ID_STR).getInterfaceInfo().getInterfaceName());
        itmRpc.addInterface(DPN2_ID,
                DPN3_TEPIP, EXTN_INTFS.get(DPN2_ID_STR + ":" + DPN3_ID_STR).getInterfaceInfo().getInterfaceName());
        itmRpc.addInterface(DPN3_ID,
                DPN2_TEPIP, EXTN_INTFS.get(DPN3_ID_STR + ":" + DPN2_ID_STR).getInterfaceInfo().getInterfaceName());


        itmRpc.addL2GwInterface(DPN1_ID,
                DCGWID, EXTN_INTFS.get(DPN1_ID_STR + ":" + DCGWID).getInterfaceInfo().getInterfaceName());
        itmRpc.addL2GwInterface(DPN1_ID,
                TOR1NODEID, EXTN_INTFS.get(DPN1_ID_STR + ":" + TOR1NODEID).getInterfaceInfo().getInterfaceName());
        itmRpc.addL2GwInterface(DPN2_ID,
                TOR1NODEID, EXTN_INTFS.get(DPN2_ID_STR + ":" + TOR1NODEID).getInterfaceInfo().getInterfaceName());
        itmRpc.addL2GwInterface(DPN3_ID,
                TOR1NODEID, EXTN_INTFS.get(DPN3_ID_STR + ":" + TOR1NODEID).getInterfaceInfo().getInterfaceName());
        itmRpc.addL2GwInterface(DPN1_ID,
                TOR2NODEID, EXTN_INTFS.get(DPN1_ID_STR + ":" + TOR2NODEID).getInterfaceInfo().getInterfaceName());
        itmRpc.addL2GwInterface(DPN2_ID,
                TOR2NODEID, EXTN_INTFS.get(DPN2_ID_STR + ":" + TOR2NODEID).getInterfaceInfo().getInterfaceName());
        itmRpc.addL2GwInterface(DPN3_ID,
                TOR2NODEID, EXTN_INTFS.get(DPN3_ID_STR + ":" + TOR2NODEID).getInterfaceInfo().getInterfaceName());


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

    public void addElanInterface(String elanInstanceName, InterfaceInfo interfaceInfo, String prefix) {
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        String interfaceName = interfaceInfo.getInterfaceName();

        if (existingElanInstance != null) {
            ElanInterfaceBuilder elanInterfaceBuilder = new ElanInterfaceBuilder()
                    .setElanInstanceName(elanInstanceName)
                    .setName(interfaceName)
                    .setKey(new ElanInterfaceKey(interfaceName));

            StaticMacEntriesBuilder staticMacEntriesBuilder = new StaticMacEntriesBuilder();
            List<StaticMacEntries> staticMacEntries = new ArrayList<>();
            List<PhysAddress> physAddressList = Collections.singletonList(
                    new PhysAddress(interfaceInfo.getMacAddress()));
            for (PhysAddress physAddress : physAddressList) {
                staticMacEntries.add(staticMacEntriesBuilder.setMacAddress(physAddress)
                        .setIpPrefix(new IpAddress(new Ipv4Address(prefix))).build());
            }
            elanInterfaceBuilder.setStaticMacEntries(staticMacEntries);
            ElanInterface elanInterface = elanInterfaceBuilder.build();

            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
        }
    }

    public void deleteElanInterface(InterfaceInfo interfaceInfo) throws TransactionCommitFailedException {
        String interfaceName = interfaceInfo.getInterfaceName();
        singleTxdataBroker.syncDelete(LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));

    }
}

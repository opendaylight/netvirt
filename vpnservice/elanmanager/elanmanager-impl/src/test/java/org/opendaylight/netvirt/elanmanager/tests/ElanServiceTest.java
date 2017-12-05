/*
 * Copyright (C) 2016, 2017 Red Hat Inc., and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.netvirt.elan.l2gw.nodehandlertest.NodeConnectedHandlerUtils.getUUid;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorTestModule;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.testutils.interfacemanager.TunnelInterfaceDetails;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.jobcoordinator.internal.JobCoordinatorImpl;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.evpn.listeners.ElanMacEntryListener;
import org.opendaylight.netvirt.elan.evpn.listeners.EvpnElanInstanceListener;
import org.opendaylight.netvirt.elan.evpn.listeners.MacVrfEntryListener;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.netvirt.elan.internal.ElanDpnInterfaceClusteredListener;
import org.opendaylight.netvirt.elan.internal.ElanInterfaceManager;
import org.opendaylight.netvirt.elan.l2gw.listeners.HwvtepLocalUcastMacListener;
import org.opendaylight.netvirt.elan.l2gw.listeners.HwvtepPhysicalSwitchListener;
import org.opendaylight.netvirt.elan.l2gw.listeners.L2GatewayConnectionListener;
import org.opendaylight.netvirt.elan.l2gw.nodehandlertest.DataProvider;
import org.opendaylight.netvirt.elan.l2gw.nodehandlertest.GlobalAugmentationHelper;
import org.opendaylight.netvirt.elan.l2gw.nodehandlertest.TestBuilders;
import org.opendaylight.netvirt.elan.l2gw.nodehandlertest.TestUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.api.IL2gwService;
import org.opendaylight.netvirt.elanmanager.tests.utils.EvpnTestHelper;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.netvirt.neutronvpn.l2gw.L2GatewayListener;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.DevicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.InterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnectionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnectionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end test of IElanService.
 *
 * @author Michael Vorburger
 * @author Riyazahmed Talikoti
 */
public class ElanServiceTest extends  ElanServiceTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceTest.class);

    // TODO as-is, this test is flaky; as uncommenting will show
    // Uncomment this to keep running this test indefinitely
    // This is very useful to detect concurrency issues (such as https://bugs.opendaylight.org/show_bug.cgi?id=7538)
    // public static @ClassRule RunUntilFailureClassRule classRepeater = new RunUntilFailureClassRule();
    // public @Rule RunUntilFailureRule repeater = new RunUntilFailureRule(classRepeater);

    public @Rule LogRule logRule = new LogRule();
    public @Rule MethodRule guice = new GuiceRule(ElanServiceTestModule.class, JobCoordinatorTestModule.class);
    // TODO re-enable after we can await completion of listeners and DJC:
    // Otherwise this too frequently causes spurious test failures, e.g. due to error
    // logs Caused by: java.lang.RuntimeException: java.util.concurrent.ExecutionException: Operation was interrupted
    // public @Rule LogCaptureRule logCaptureRule = new LogCaptureRule();
    private @Inject IElanService elanService;
    private @Inject IdManagerService idManager;
    private @Inject EvpnElanInstanceListener evpnElanInstanceListener;
    private @Inject ElanMacEntryListener elanMacEntryListener;
    private @Inject MacVrfEntryListener macVrfEntryListener;
    private @Inject EvpnUtils evpnUtils;
    private @Inject IBgpManager bgpManager;
    private @Inject IVpnManager vpnManager;
    private @Inject EvpnTestHelper evpnTestHelper;
    private @Inject OdlInterfaceRpcService odlInterfaceRpcService;
    private @Inject ElanL2GatewayUtils elanL2GatewayUtils;
    private @Inject ElanInterfaceManager elanInterfaceManager;
    private @Inject HwvtepPhysicalSwitchListener hwvtepPhysicalSwitchListener;
    private @Inject L2GatewayConnectionListener l2GatewayConnectionListener;
    private @Inject HwvtepLocalUcastMacListener hwvtepLocalUcastMacListener;
    private @Inject ElanDpnInterfaceClusteredListener elanDpnInterfaceClusteredListener;
    private @Inject EntityOwnershipService mockedEntityOwnershipService;
    private L2GatewayListener l2gwListener;

    private SingleTransactionDataBroker singleTxdataBroker;

    @Before public void before() {

        for (ResourceBatchingManager.ShardResource i : ResourceBatchingManager.ShardResource.values()) {
            try {
                ResourceBatchingManager.getInstance().deregisterBatchableResource(i.name());
            } catch (NullPointerException e) {
                LOG.error("Exception {}", e);
            }
        }
        L2GatewayCacheUtils.removeL2DeviceFromCache("s3");
        L2GatewayCacheUtils.removeL2DeviceFromCache("s4");

        ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(TOR1NODEID);
        //ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(TOR2NODEID);

        ElanL2GwCacheUtils.removeL2GatewayDeviceFromCache(ExpectedObjects.ELAN1, TOR1NODEID);
        //ElanL2GwCacheUtils.removeL2GatewayDeviceFromCache(ExpectedObjects.ELAN1, TOR2NODEID);

        Set<DpnInterfaces> dpns =  ElanUtils.getElanInvolvedDPNsFromCache(ExpectedObjects.ELAN1);
        if (dpns != null) {
            dpns.clear();
        }
        singleTxdataBroker = new SingleTransactionDataBroker(dataBroker);

       // Entity mockEntity = Mockito.mock(Entity.class);

        JobCoordinator jobCoordinator = new JobCoordinatorImpl();

        l2gwListener = new L2GatewayListener(dataBroker, mockedEntityOwnershipService,
                Mockito.mock(ItmRpcService.class), Mockito.mock(IL2gwService.class), jobCoordinator);
        l2gwListener.init();
        try {
            setupItm();
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to setup itm");
        }
    }

    @Test public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to first test the ElanServiceTestModule
    }

    private InstanceIdentifier<Node> createInstanceIdentifier(String nodeIdString) {
        NodeId nodeId = new NodeId(new Uri(nodeIdString));
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(new TopologyId(new Uri("hwvtep:1")));
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(Node.class, nodeKey)
                .build();
    }

    private InstanceIdentifier<Group> createGroupIid(Group group, BigInteger dpId) {
        long groupId = group.getGroupId().getValue().longValue();
        return buildGroupInstanceIdentifier(groupId, this.buildDpnNode(dpId));
    }


    private List<Bucket> getRemoteBCGroupTunnelBuckets(ElanInstance elanInfo,
                                                       List<BigInteger> otherDpns, List<String> otherTors,
                                                       BigInteger dpnId, int bucketId,
                                                       long elanTagOrVni)
            throws ExecutionException, InterruptedException {
        try {
            List<Bucket> listBucketInfo = new ArrayList<>();
            if (otherDpns != null) {
                for (BigInteger otherDpn : otherDpns) {
                    GetEgressActionsForInterfaceInput getEgressActInput = new GetEgressActionsForInterfaceInputBuilder()
                            .setIntfName(EXTN_INTFS.get(dpnId + ":" + otherDpn).getInterfaceInfo().getInterfaceName())
                            .setTunnelKey(elanInfo.getElanTag()).build();
                    List<Action> actionsList =
                            odlInterfaceRpcService.getEgressActionsForInterface(getEgressActInput).get().getResult()
                                    .getAction();
                    listBucketInfo.add(MDSALUtil.buildBucket(actionsList, MDSALUtil.GROUP_WEIGHT, bucketId,
                            MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));

                    bucketId++;
                }
            }

            if (otherTors != null) {
                for (String otherTor : otherTors) {
                    GetEgressActionsForInterfaceInput getEgressActInput = new GetEgressActionsForInterfaceInputBuilder()
                            .setIntfName(EXTN_INTFS.get(dpnId + ":" + otherTor).getInterfaceInfo().getInterfaceName())
                            .setTunnelKey(elanInfo.getSegmentationId()).build();
                    List<Action> actionsList =
                            odlInterfaceRpcService.getEgressActionsForInterface(getEgressActInput).get().getResult()
                                    .getAction();
                    listBucketInfo.add(MDSALUtil.buildBucket(actionsList, MDSALUtil.GROUP_WEIGHT, bucketId,
                            MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));

                    bucketId++;
                }
            }
            return listBucketInfo;
        } catch (NullPointerException e) {
            LOG.error("Exception {}", e);

        }
        return new ArrayList<>();
    }

    private InstanceIdentifier<Group>
        buildGroupInstanceIdentifier(long groupId,
                                     org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node
                                             nodeDpn) {
        InstanceIdentifier groupInstanceId =
                InstanceIdentifier.builder(Nodes.class)
                        .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                                nodeDpn.getKey()).augmentation(FlowCapableNode.class).child(Group.class,
                        new GroupKey(new GroupId(Long.valueOf(groupId)))).build();
        return groupInstanceId;
    }

    private Group setupStandardElanBroadcastGroups(ElanInstance elanInfo, BigInteger dpnId, List<BigInteger> otherdpns,
                                                   List<String> tepIps)
            throws ExecutionException, InterruptedException {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        int actionKey = 0;
        Long elanTag = elanInfo.getElanTag();
        List<Action> listAction = new ArrayList<>();
        listAction.add(new ActionGroup(ElanUtils.getElanLocalBCGId(elanTag)).buildAction(++actionKey));
        listBucket.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP));
        bucketId++;
        List<Bucket> listBucketInfoRemote = getRemoteBCGroupBuckets(elanInfo, dpnId, otherdpns, tepIps, bucketId);
        listBucket.addAll(listBucketInfoRemote);
        long groupId = ElanUtils.getElanRemoteBCGId(elanTag);
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBucket));
        return group;
    }

    private List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo,
                                                 BigInteger dpnId,
                                                 List<BigInteger> dpns,
                                                 List<String> tepIps,
                                                 int bucketId) throws ExecutionException, InterruptedException {
        List<Bucket> listBucketInfo = new ArrayList<>();
        listBucketInfo.addAll(getRemoteBCGroupTunnelBuckets(elanInfo, dpns, tepIps, dpnId, bucketId,
                elanInfo.getSegmentationId()));
        return listBucketInfo;
    }

    private InstanceIdentifier<L2gateway> l2gatewayId(String l2gwName) {
        String l2gwNameUuid = UUID.nameUUIDFromBytes(l2gwName.getBytes()).toString();
        return InstanceIdentifier.create(Neutron.class).child(L2gateways.class)
                .child(L2gateway.class, new L2gatewayKey(toUuid(l2gwNameUuid)));
    }

    private InstanceIdentifier<L2gatewayConnection> l2gatewayConnectionId(String connectionName) {
        String l2gwConnectionNameUuid = UUID.nameUUIDFromBytes(connectionName.getBytes()).toString();
        return InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class)
                .child(L2gatewayConnection.class, new L2gatewayConnectionKey(toUuid(l2gwConnectionNameUuid)));
    }

    private L2gateway l2gatewayBuilder(String l2gwName, String deviceName, List<String> intfNames) {
        final L2gatewayBuilder l2gatewayBuilder = new L2gatewayBuilder();
        String uuid = UUID.nameUUIDFromBytes(l2gwName.getBytes()).toString();
        //String tenantUuid = UUID.fromString(ELAN1).toString();
        l2gatewayBuilder.setL2gatewayName(l2gwName);
        l2gatewayBuilder.setUuid(new Uuid(uuid));
        l2gatewayBuilder.setTenantId(new Uuid(ExpectedObjects.ELAN1));

        final List<Devices> devices = new ArrayList<>();
        final DevicesBuilder deviceBuilder = new DevicesBuilder();
        final List<Interfaces> interfaces = new ArrayList<>();
        for (String intfName : intfNames) {
            final InterfacesBuilder interfacesBuilder = new InterfacesBuilder();
            interfacesBuilder.setInterfaceName(intfName);
            interfacesBuilder.setSegmentationIds(new ArrayList<>());
            interfaces.add(interfacesBuilder.build());
        }
        deviceBuilder.setDeviceName(deviceName);
        deviceBuilder.setUuid(new Uuid(uuid));
        deviceBuilder.setInterfaces(interfaces);

        devices.add(deviceBuilder.build());
        l2gatewayBuilder.setDevices(devices);
        return l2gatewayBuilder.build();
    }

    private L2gatewayConnection l2gatewayConnectionBuilder(String connectionName, String l2gwName, String elan,
                                                           Integer segmentationId) {

        final L2gatewayConnectionBuilder l2gatewayConnectionBuilder = new L2gatewayConnectionBuilder();

        String uuidConnectionName = UUID.nameUUIDFromBytes(connectionName.getBytes()).toString();
        l2gatewayConnectionBuilder.setUuid(new Uuid(uuidConnectionName));

        String uuidL2gwName = UUID.nameUUIDFromBytes(l2gwName.getBytes()).toString();
        l2gatewayConnectionBuilder.setL2gatewayId(new Uuid(uuidL2gwName));
        l2gatewayConnectionBuilder.setNetworkId(new Uuid(elan));
        l2gatewayConnectionBuilder.setSegmentId(segmentationId);
        l2gatewayConnectionBuilder.setTenantId(new Uuid(ExpectedObjects.ELAN1));

        String portName = "port";
        String uuidPort = UUID.nameUUIDFromBytes(portName.getBytes()).toString();
        l2gatewayConnectionBuilder.setPortId(new Uuid(uuidPort));
        return l2gatewayConnectionBuilder.build();
    }

    private Uuid toUuid(String name) {
        return new Uuid(UUID.fromString(name).toString());
    }

    private  InstanceIdentifier<Node>  create_tor_node(String torNodeId, String deviceName, String tepIp)
            throws Exception {

        InstanceIdentifier<Node> nodePath =
                createInstanceIdentifier(torNodeId);
        InstanceIdentifier<Node> psNodePath =
                createInstanceIdentifier(torNodeId + "/physicalswitch/" + deviceName);

        // Create PS node
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(psNodePath.firstKeyOf(Node.class).getNodeId());
        PhysicalSwitchAugmentationBuilder physicalSwitchAugmentationBuilder = new PhysicalSwitchAugmentationBuilder();
        physicalSwitchAugmentationBuilder.setManagedBy(new HwvtepGlobalRef(nodePath));
        physicalSwitchAugmentationBuilder.setPhysicalSwitchUuid(new Uuid(UUID.nameUUIDFromBytes(deviceName.getBytes())
                .toString()));
        physicalSwitchAugmentationBuilder.setHwvtepNodeName(new HwvtepNodeName(deviceName));
        physicalSwitchAugmentationBuilder.setHwvtepNodeDescription("torNode");
        List<TunnelIps> tunnelIps = new ArrayList<>();
        IpAddress ip = new IpAddress(tepIp.toCharArray());
        tunnelIps.add(new TunnelIpsBuilder().setKey(new TunnelIpsKey(ip)).setTunnelIpsKey(ip).build());
        physicalSwitchAugmentationBuilder.setTunnelIps(tunnelIps);
        nodeBuilder.addAugmentation(PhysicalSwitchAugmentation.class, physicalSwitchAugmentationBuilder.build());
        ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, psNodePath, nodeBuilder.build(),
                WriteTransaction.CREATE_MISSING_PARENTS);
        tx.submit();

        //Create Global node
        tx = this.dataBroker.newReadWriteTransaction();
        nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(nodePath.firstKeyOf(Node.class).getNodeId());
        HwvtepGlobalAugmentationBuilder builder = new HwvtepGlobalAugmentationBuilder();
        builder.setDbVersion("1.6.0");
        builder.setManagers(TestBuilders.buildManagers1());
        GlobalAugmentationHelper.addSwitches(builder, psNodePath);
        nodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, builder.build());
        tx.put(LogicalDatastoreType.OPERATIONAL, nodePath, nodeBuilder.build(),
                WriteTransaction.CREATE_MISSING_PARENTS);
        tx.submit();

        //Check node creation success
        Thread.sleep(1000);
        ReadOnlyTransaction tx1 = this.dataBroker.newReadOnlyTransaction();
        Optional<Node> globalOpNode = TestUtil.readNode(OPERATIONAL, nodePath, tx1);
        Optional<Node> psOpNode = TestUtil.readNode(OPERATIONAL, psNodePath, tx1);
        tx1.close();

        if (!globalOpNode.isPresent()) {
            return null;
        } else if (!psOpNode.isPresent()) {
            return null;
        }
        return nodePath;
    }

    private void createL2gwL2gwconn(InstanceIdentifier<Node> nodePath, String l2gwName, String deviceName,
                                    List<String> ports, String connectionName) throws InterruptedException {

        //Create l2gw
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                l2gatewayId(l2gwName), l2gatewayBuilder(l2gwName, deviceName, ports));
        awaitForData(LogicalDatastoreType.CONFIGURATION,l2gatewayId(l2gwName));

        //Create l2gwconn
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                l2gatewayConnectionId(connectionName), l2gatewayConnectionBuilder(connectionName,
                        l2gwName, ExpectedObjects.ELAN1, 100));
        awaitForData(LogicalDatastoreType.CONFIGURATION,l2gatewayConnectionId(connectionName));

        //check for Config Logical Switch creation
        InstanceIdentifier logicalSwitchesInstanceIdentifier =
                HwvtepSouthboundUtils.createLogicalSwitchesInstanceIdentifier(
                        nodePath.firstKeyOf(Node.class).getNodeId(),
                        new HwvtepNodeName(ExpectedObjects.ELAN1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, logicalSwitchesInstanceIdentifier);

        //create operational logical switch
        Optional<LogicalSwitches> logicalSwitchesOptional =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, logicalSwitchesInstanceIdentifier);
        LogicalSwitches logicalSwitches = logicalSwitchesOptional.isPresent() ? logicalSwitchesOptional.get() : null ;
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                logicalSwitchesInstanceIdentifier, logicalSwitches);
        awaitForData(LogicalDatastoreType.OPERATIONAL, logicalSwitchesInstanceIdentifier);
    }

    private void deletel2GWConnection(String connectionName) {

        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                l2gatewayConnectionId(connectionName));
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION,l2gatewayConnectionId(connectionName));
    }


    private LocalUcastMacs createLocalUcastMac(InstanceIdentifier<Node> nodeId, String mac, String ipAddr,
                                               String tepIp) {

        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(nodeId.firstKeyOf(Node.class).getNodeId());
        final List<LocalUcastMacs> localUcastMacses = new ArrayList<>();
        LocalUcastMacsBuilder localUcastMacsBuilder = new LocalUcastMacsBuilder();
        localUcastMacsBuilder.setIpaddr(new IpAddress(ipAddr.toCharArray()));
        localUcastMacsBuilder.setMacEntryKey(new MacAddress(mac));
        localUcastMacsBuilder.setMacEntryUuid(getUUid(mac));
        localUcastMacsBuilder.setLocatorRef(TestBuilders.buildLocatorRef(nodeId, tepIp));
        localUcastMacsBuilder.setLogicalSwitchRef(TestBuilders.buildLogicalSwitchesRef(nodeId, ExpectedObjects.ELAN1));
        LocalUcastMacs localUcastMacs = localUcastMacsBuilder.build();
        localUcastMacses.add(localUcastMacs);
        HwvtepGlobalAugmentationBuilder builder1 =
                new HwvtepGlobalAugmentationBuilder().setLocalUcastMacs(localUcastMacses) ;
        nodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, builder1.build());
        ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
        tx.merge(LogicalDatastoreType.OPERATIONAL, nodeId, nodeBuilder.build());
        tx.submit();
        InstanceIdentifier<LocalUcastMacs> localUcastMacsId = getMacIid(nodeId, localUcastMacs);
        awaitForData(LogicalDatastoreType.OPERATIONAL,localUcastMacsId);
        return localUcastMacs;
    }

    private InstanceIdentifier<LocalUcastMacs> getMacIid(InstanceIdentifier<Node> nodeIid, LocalUcastMacs mac) {
        return nodeIid.augmentation(HwvtepGlobalAugmentation.class).child(LocalUcastMacs.class, mac.getKey());
    }

    private void validateDPNGroup(BigInteger dpnId, List<BigInteger> otherdpns, List<String> othertors,
                                  boolean existFlag)
            throws InterruptedException, ReadFailedException, TransactionCommitFailedException {

        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);
        InstanceIdentifier<Group> grpIid =
                buildGroupInstanceIdentifier(ElanUtils.getElanRemoteBCGId(actualElanInstances.getElanTag()),
                        this.buildDpnNode(dpnId));
        if (existFlag) {
            awaitForData(LogicalDatastoreType.CONFIGURATION, grpIid);
            validateGroup(actualElanInstances, dpnId, otherdpns, othertors);
        } else {
            awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, grpIid);
        }
    }

    private void checkForRemoteMcastMac(InstanceIdentifier<Node> torNodeId, String tepIp, boolean existFlag)
            throws ReadFailedException {
        getAwaiter().until(() -> checkForRemoteMcastMac(torNodeId, tepIp, existFlag, false));
    }

    private boolean checkForRemoteMcastMac(InstanceIdentifier<Node> torNodeId, String tepIp, boolean existFlag,
                                           boolean printError) {
        try {
            ReadOnlyTransaction tx = this.dataBroker.newReadOnlyTransaction();
            Optional<Node> node = tx.read(LogicalDatastoreType.CONFIGURATION, torNodeId).checkedGet();
            HwvtepGlobalAugmentation augmentation = node.get().getAugmentation(HwvtepGlobalAugmentation.class);
            if (augmentation == null || augmentation.getRemoteMcastMacs() == null
                    || augmentation.getRemoteMcastMacs().isEmpty()) {
                if (existFlag) {
                    return false;
                } else {
                    return true;
                }
            }
            boolean remoteMcastFoundFlag = false;
            for (RemoteMcastMacs remoteMcastMacs : augmentation.getRemoteMcastMacs()) {
                for (LocatorSet locatorSet : remoteMcastMacs.getLocatorSet()) {
                    TpId tpId = locatorSet.getLocatorRef().getValue().firstKeyOf(TerminationPoint.class).getTpId();
                    if (tpId.getValue().contains(tepIp)) {
                        remoteMcastFoundFlag = true;
                        break;
                    }
                }
            }
            if (existFlag) {
                return (remoteMcastFoundFlag == true);
            } else {
                return (remoteMcastFoundFlag == false);
            }
        } catch (ReadFailedException e) {
            return false;
        }
    }

    private void checkForRemoteUcastMac(InstanceIdentifier<Node> torNodeId, String dpnMac, boolean existFlag)
            throws ReadFailedException {
        getAwaiter().until(() -> checkForRemoteUcastMac(torNodeId, dpnMac, existFlag, false));
    }

    private boolean checkForRemoteUcastMac(InstanceIdentifier<Node> torNodeId, String dpnMac, boolean existFlag,
                                           boolean printError)
            throws ReadFailedException {

        try {
            ReadOnlyTransaction tx = this.dataBroker.newReadOnlyTransaction();

            Optional<Node> node = tx.read(LogicalDatastoreType.CONFIGURATION, torNodeId).checkedGet();
            HwvtepGlobalAugmentation augmentation = node.get().getAugmentation(HwvtepGlobalAugmentation.class);
            if (augmentation == null || augmentation.getRemoteUcastMacs() == null
                    || augmentation.getRemoteUcastMacs().isEmpty()) {
                if (existFlag) {
                    return false;
                } else {
                    return true;
                }
            }
            boolean remoteUcastFoundFlag = false;
            for (RemoteUcastMacs remoteUcastMacs : augmentation.getRemoteUcastMacs()) {
                String mac = remoteUcastMacs.getMacEntryKey().getValue();
                if (mac.equals(dpnMac)) {
                    remoteUcastFoundFlag = true;
                    break;
                }
            }
            if (existFlag) {
                return (remoteUcastFoundFlag == true);
            } else {
                return (remoteUcastFoundFlag == false);
            }
        } catch (ReadFailedException e) {
            return false;
        }
    }

    private void  verifyDmacFlowOfOtherDPN(BigInteger srcDpnId, BigInteger dpnId, String dpnMac,
                                           boolean createFlag)
            throws ReadFailedException, InterruptedException {
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);
        FlowId flowId = new FlowId(
                ElanUtils.getKnownDynamicmacFlowRef((short)51,
                        srcDpnId,
                        dpnId,
                        dpnMac,
                        actualElanInstances.getElanTag()));
        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE, flowId, srcDpnId);

        if (createFlag) {
            awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);
        } else {
            awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);
        }
    }

    private void verifyDmacFlowOfTOR(BigInteger srcDpnId, String torNodeId, String mac, boolean createFlag)
            throws ReadFailedException,InterruptedException {

        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);
        FlowId flowId = new FlowId(
                ElanUtils.getKnownDynamicmacFlowRef((short)51,
                        srcDpnId,
                        torNodeId,
                        mac,
                        actualElanInstances.getElanTag(),
                        false));

        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE, flowId, srcDpnId);

        if (createFlag) {
            awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);
        } else {
            awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);
        }
    }

    private void validateGroup(ElanInstance actualElanInstances, BigInteger dpnId, List<BigInteger> otherdpns,
                               List<String> torips)
            throws org.opendaylight.controller.md.sal.common.api.data.ReadFailedException,
            TransactionCommitFailedException {
        getAwaiter().conditionEvaluationListener((conditionEvaluationListener) -> {
            LOG.info("##### Condition {} {} {}", conditionEvaluationListener.getValue(),
                    conditionEvaluationListener.isSatisfied(),
                    conditionEvaluationListener.getRemainingTimeInMS());
        }).until(() -> validateGroup(actualElanInstances, dpnId, otherdpns, torips, false));
    }

    private boolean validateGroup(ElanInstance actualElanInstances, BigInteger dpnId, List<BigInteger> otherdpns,
                                  List<String> torips, boolean printError)
            throws org.opendaylight.controller.md.sal.common.api.data.ReadFailedException,
            TransactionCommitFailedException, ExecutionException, InterruptedException {
        Group expected = setupStandardElanBroadcastGroups(actualElanInstances, dpnId, otherdpns, torips);
        InstanceIdentifier<Group> grpIid = createGroupIid(expected, dpnId);
        final Group actual = singleTxdataBroker.syncRead(CONFIGURATION, grpIid);
        singleTxdataBroker.syncWrite(CONFIGURATION, grpIid, expected);
        expected = singleTxdataBroker.syncRead(CONFIGURATION, grpIid);

        LOG.info("Expected group FOR DPN {} {}", dpnId, expected);
        LOG.info("Actual group  FOR DPN {} {}", dpnId, actual);

        if (!(actual.getBuckets().getBucket().size() == expected.getBuckets().getBucket().size())
                || !Objects.equals(expected, actual)) {
            if (!printError) {
                return false;
            }
            AssertDataObjects.assertEqualBeans(expected, actual);
        }
        if (!(actual.getBuckets().getBucket().containsAll(expected.getBuckets().getBucket()))
                || !Objects.equals(expected, actual)) {
            if (!printError) {
                return false;
            }
            AssertDataObjects.assertEqualBeans(expected, actual);
        }
        return true;
    }

    private org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node
        buildDpnNode(BigInteger dpnId) {

        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId nodeId =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("openflow:" + dpnId);
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node nodeDpn =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder().setId(nodeId)
                        .setKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes
                                .NodeKey(nodeId)).build();
        return nodeDpn;
    }

    @Test public void checkSMAC() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add Elan interface
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);

        // Read Elan instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // Read and Compare SMAC flow
        String flowId =  new StringBuffer()
                .append(NwConstants.ELAN_SMAC_TABLE)
                .append(actualElanInstances.getElanTag())
                .append(DPN1_ID)
                .append(interfaceInfo.getInterfaceTag())
                .append(interfaceInfo.getMacAddress())
                .toString();
        InstanceIdentifier<Flow> flowInstanceIidSrc = getFlowIid(NwConstants.ELAN_SMAC_TABLE,
                new FlowId(flowId), DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidSrc);

        Flow flowSrc = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidSrc);
        flowSrc = getFlowWithoutCookie(flowSrc);

        Flow expected = ExpectedObjects.checkSmac(flowId, interfaceInfo, actualElanInstances);
        AssertDataObjects.assertEqualBeans(expected, flowSrc);
    }

    @Test public void checkDmacSameDPN() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add Elan interface in DPN1
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);

        // Read Elan instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // Read DMAC Flow in DPN1
        String flowId =  new StringBuffer()
                .append(NwConstants.ELAN_DMAC_TABLE)
                .append(actualElanInstances.getElanTag())
                .append(DPN1_ID)
                .append(interfaceInfo.getInterfaceTag())
                .append(interfaceInfo.getMacAddress())
                .toString();
        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE,
                new FlowId(flowId), DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);

        Flow flowDst = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidDst);
        flowDst = getFlowWithoutCookie(flowDst);

        Flow expected = ExpectedObjects.checkDmacOfSameDpn(flowId, interfaceInfo, actualElanInstances);
        AssertDataObjects.assertEqualBeans(expected, flowDst);
    }

    @Test public void checkDmacOfOtherDPN() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);

        // Read Elan instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);

        // Read and Compare DMAC flow in DPN1 for MAC1 of DPN2
        String flowId = ElanUtils.getKnownDynamicmacFlowRef((short)51,
                        DPN1_ID,
                        DPN2_ID,
                        interfaceInfo.getMacAddress().toString(),
                        actualElanInstances.getElanTag());

        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE,
                new FlowId(flowId), DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);

        Flow flowDst = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidDst);
        flowDst = getFlowWithoutCookie(flowDst);

        TunnelInterfaceDetails tepDetails = EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN2_ID_STR);
        Flow expected = ExpectedObjects.checkDmacOfOtherDPN(flowId, interfaceInfo, tepDetails,
                actualElanInstances);
        AssertDataObjects.assertEqualBeans(getSortedActions(expected), getSortedActions(flowDst));
    }

    @Test public void checkEvpnAdvRT2() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();


        AssertDataObjects.assertEqualBeans(
                ExpectedObjects.checkEvpnAdvertiseRoute(ELAN1_SEGMENT_ID, DPN1MAC1, DPN1_TEPIP, DPN1IP1, RD),
                readBgpNetworkFromDS(DPN1IP1));
    }

    @Test public void checkEvpnAdvRT2NewInterface() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft(), DPN1IP2);

        AssertDataObjects.assertEqualBeans(
                ExpectedObjects.checkEvpnAdvertiseRoute(ELAN1_SEGMENT_ID, DPN1MAC2, DPN1_TEPIP, DPN1IP2, RD),
                readBgpNetworkFromDS(DPN1IP2));
    }

    @Test public void checkEvpnWithdrawRT2DelIntf() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();

        InstanceIdentifier<Networks> iid = evpnTestHelper.buildBgpNetworkIid(DPN1IP1);
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        evpnTestHelper.deleteRdtoNetworks();

        deleteElanInterface(ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft());
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, iid);
    }

    @Test public void checkEvpnWithdrawRouteDetachEvpn() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft(), DPN1IP2);

        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP2));

        evpnTestHelper.detachEvpnToNetwork(ExpectedObjects.ELAN1);

        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP1));
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP2));
    }

    @Test public void checkEvpnInstalDmacFlow() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft(), DPN1IP2);

        // Verify advertise RT2 route success for both MAC's
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP2));

        // RT2 received from Peer
        evpnTestHelper.handleEvpnRt2Recvd(EVPNRECVMAC1, EVPNRECVIP1);
        evpnTestHelper.handleEvpnRt2Recvd(EVPNRECVMAC2, EVPNRECVIP2);

        // verify successful installation of DMAC flow for recvd rt2
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC2));
    }

    @Test public void checkEvpnUnInstalDmacFlow() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft(), DPN1IP2);

        // Verify advertise RT2 route success for both MAC's
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP2));

        // RT2 received from Peer
        evpnTestHelper.handleEvpnRt2Recvd(EVPNRECVMAC1, EVPNRECVIP1);
        evpnTestHelper.handleEvpnRt2Recvd(EVPNRECVMAC2, EVPNRECVIP2);

        // verify successful installation of DMAC flow for recvd rt2
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC2));

        // withdraw RT2 received from Peer
        evpnTestHelper.deleteMacVrfEntryToDS(RD, EVPNRECVMAC1);
        evpnTestHelper.deleteMacVrfEntryToDS(RD, EVPNRECVMAC2);

        // verify successful un-installation of DMAC flow for recvd rt2
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC1));
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC2));
    }

    @Test
    public void checkDmacOfOtherDPNandTOR() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //TOR Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId,"l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //TOR Node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId,"l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //create 4 localUcastMacs in TOR Nodes(2 in TOR1, 2 in TOR2)
        createLocalUcastMac(tor1NodeId,TOR1MAC1,"10.0.0.1", "192.168.122.30");
        createLocalUcastMac(tor1NodeId,TOR1MAC2,"10.0.0.2", "192.168.122.40");

        verifyDmacFlowOfTOR(DPN1_ID, TOR1NODEID, TOR1MAC1, true);
        createLocalUcastMac(tor2NodeId,TOR2MAC1,"10.0.0.3", "192.168.122.50");
        createLocalUcastMac(tor2NodeId,TOR2MAC2,"10.0.0.4", "192.168.122.60");

        //Add Elan MAC1, MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);

        //Read and Compare DMAC flows in DPN3 against Local ucast macs of TOR1(MAC1, MAC2), TOR2 (MAC3, MAC4)
        verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC1, true);
        verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC2, true);
        verifyDmacFlowOfTOR(DPN3_ID, TOR2NODEID, TOR2MAC1, true);
        verifyDmacFlowOfTOR(DPN3_ID, TOR2NODEID, TOR2MAC2,true);

        //Read and Compare DMAC flows in DPN3 against DPN1(MAC1, MAC2), DPN2(MAC1, MAC2)
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN1_ID, DPN1MAC1,true);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN1_ID, DPN1MAC2,true);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN2_ID, DPN2MAC1,true);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN2_ID, DPN2MAC2,true);

    }

    @Test
    public void checkRemoteMcastMacOfTORs() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1, MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);

        // TOR Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //TOR Node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //create 4 localUcastMacs (2 in TOR1, 2 in TOR2)
        createLocalUcastMac(tor1NodeId, TOR1MAC1, "10.0.0.1", "192.168.122.30");
        createLocalUcastMac(tor1NodeId, TOR1MAC2, "10.0.0.2", "192.168.122.40");
        createLocalUcastMac(tor2NodeId, TOR2MAC1, "10.0.0.3", "192.168.122.50");
        createLocalUcastMac(tor2NodeId, TOR2MAC2, "10.0.0.4", "192.168.122.60");

        //check for remote mcast mac(s) in tor1 against macs of dpn1, dpn2 and dpn3, tor2)
        checkForRemoteMcastMac(tor1NodeId, DPN1_TEPIP, true);
        checkForRemoteMcastMac(tor1NodeId, DPN2_TEPIP, true);
        checkForRemoteMcastMac(tor1NodeId, DPN3_TEPIP, true);
        Thread.sleep(1000);
        checkForRemoteMcastMac(tor1NodeId, TOR2_TEPIP, true);

        //check for remote mcast mac in tor2 against macs of dpn1, dpn2 and dpn3, tor1)
        checkForRemoteMcastMac(tor2NodeId, DPN1_TEPIP, true);
        checkForRemoteMcastMac(tor2NodeId, DPN2_TEPIP, true);
        checkForRemoteMcastMac(tor2NodeId, DPN3_TEPIP, true);
        Thread.sleep(1000);
        checkForRemoteMcastMac(tor2NodeId, TOR1_TEPIP, true);
    }

    @Test
    public void checkGroupsOfDPNs() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1, MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);

        // Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //create 2 localUcastMacs in TOR1, TOR2
        createLocalUcastMac(tor1NodeId, TOR1MAC1, "10.0.0.1", "192.168.122.30");
        createLocalUcastMac(tor1NodeId, TOR1MAC2, "10.0.0.2", "192.168.122.40");
        createLocalUcastMac(tor2NodeId, TOR2MAC1, "10.0.0.3", "192.168.122.50");
        createLocalUcastMac(tor2NodeId, TOR2MAC2, "10.0.0.4", "192.168.122.60");

        //check groups in DPNs
        validateDPNGroup(DPN1_ID, Lists.newArrayList(DPN3_ID, DPN2_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                true);
        validateDPNGroup(DPN2_ID, Lists.newArrayList(DPN3_ID, DPN1_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                true);
        validateDPNGroup(DPN3_ID, Lists.newArrayList(DPN2_ID, DPN1_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                true);
    }

    @Test
    public void deleteOnlyMacInDpnAndVerify() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);

        // Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //create 4 localUcastMacs : 2 in TOR1, 2 in TOR2
        createLocalUcastMac(tor1NodeId, TOR1MAC1, "10.0.0.1", "192.168.122.30");
        createLocalUcastMac(tor1NodeId, TOR1MAC2, "10.0.0.2", "192.168.122.40");
        createLocalUcastMac(tor2NodeId, TOR2MAC1, "10.0.0.3", "192.168.122.50");
        createLocalUcastMac(tor2NodeId, TOR2MAC2, "10.0.0.4", "192.168.122.60");

        verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC1, true);
        verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC2, true);
        verifyDmacFlowOfTOR(DPN3_ID, TOR2NODEID, TOR2MAC1, true);
        verifyDmacFlowOfTOR(DPN3_ID, TOR2NODEID, TOR2MAC2, true);

        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN1_ID, DPN1MAC1, true);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN1_ID, DPN1MAC2, true);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN2_ID, DPN2MAC1, true);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN2_ID, DPN2MAC2, true);

        //remove DPN3 ELAN MAC1
        deleteElanInterface(interfaceInfo);

        /*Read and verify whether DMAC flows got deleted in DPN3
          against Local ucast macs of TOR1(MAC1, MAC2), TOR2 (MAC3, MAC4)*/
        verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC1, false);
        verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC2, false);
        verifyDmacFlowOfTOR(DPN3_ID, TOR2NODEID, TOR2MAC1, false);
        verifyDmacFlowOfTOR(DPN3_ID, TOR2NODEID, TOR2MAC2, false);

        //Read and verify whether DMAC flows got deleted in DPN3 against DPN1(MAC1, MAC2), DPN2(MAC1, MAC2)
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN1_ID, DPN1MAC1, false);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN1_ID, DPN1MAC2, false);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN2_ID, DPN2MAC1, false);
        verifyDmacFlowOfOtherDPN(DPN3_ID, DPN2_ID, DPN2MAC2, false);

        //check groups in DPNs
        validateDPNGroup(DPN1_ID, Lists.newArrayList(DPN2_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                true);
        validateDPNGroup(DPN2_ID, Lists.newArrayList(DPN1_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                true);
        validateDPNGroup(DPN3_ID, Lists.newArrayList(DPN2_ID, DPN1_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                false);

        checkForRemoteMcastMac(tor1NodeId, DPN3_TEPIP, false);
        checkForRemoteMcastMac(tor2NodeId, DPN3_TEPIP, false);
    }

    @Test
    public void addThreeDpnsAndTwoTorsAndVerify() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1, MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);

        // Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //create 2 localUcastMacs in TOR1
        createLocalUcastMac(tor1NodeId, TOR1MAC1, "10.0.0.1", "192.168.122.30");
        createLocalUcastMac(tor1NodeId, TOR1MAC2, "10.0.0.2", "192.168.122.40");

        //node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //check for remote ucast mac(s) in tor2 against macs of dpn1, dpn2 and dpn3, tor1)
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC2, true);
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor2NodeId, TOR1MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, TOR1MAC2, true);

        //check for remote mcast mac(s) in tor1 against macs of  dpn1, dpn2 and dpn3, tor2)
        checkForRemoteMcastMac(tor1NodeId, DPN1_TEPIP, true);
        checkForRemoteMcastMac(tor1NodeId, DPN2_TEPIP, true);
        checkForRemoteMcastMac(tor1NodeId, DPN3_TEPIP, true);
        Thread.sleep(1000);
        checkForRemoteMcastMac(tor1NodeId, TOR2_TEPIP, true);

        //check for remote mcast mac(s) in tor2 against macs of dpn1, dpn2 and dpn3, tor1)
        checkForRemoteMcastMac(tor2NodeId, DPN1_TEPIP, true);
        checkForRemoteMcastMac(tor2NodeId, DPN2_TEPIP, true);
        checkForRemoteMcastMac(tor2NodeId, DPN3_TEPIP, true);
        Thread.sleep(1000);
        checkForRemoteMcastMac(tor2NodeId, TOR1_TEPIP, true);

        //check groups in DPNs
        validateDPNGroup(DPN1_ID, Lists.newArrayList(DPN3_ID, DPN2_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                true);
        validateDPNGroup(DPN2_ID, Lists.newArrayList(DPN3_ID, DPN1_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                true);
        validateDPNGroup(DPN3_ID, Lists.newArrayList(DPN2_ID, DPN1_ID), Lists.newArrayList(TOR2_TEPIP, TOR1_TEPIP),
                true);
    }

    @Test
    public void addDpnMacAndVerifyInTORs() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);

        // Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //check for remote ucast mac(s) in tor1 against dpn1, dpn2 and dpn3, tor2)
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor1NodeId, DPN1MAC1, true);
        checkForRemoteUcastMac(tor1NodeId, DPN1MAC2, true);
        checkForRemoteUcastMac(tor1NodeId, DPN2MAC1, true);
        checkForRemoteUcastMac(tor1NodeId, DPN2MAC2, true);
        checkForRemoteUcastMac(tor1NodeId, DPN3MAC1, true);

        //check for remote ucast mac(s) in tor2 against dpn1, dpn2 and dpn3, tor1)
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC1, true);

        //Add Elan MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);

        //check for remote ucast mac(s) in tor1, tor2 against dpn3 mac2
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor1NodeId, DPN3MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC2, true);
    }

    @Test
    public void deleteDpnMacAndVerifyInTORs() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1, MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);


        // Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //check for remote ucast mac(s) in tor1 against dpn1, dpn2 and dpn3, tor2)
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor1NodeId, DPN1MAC1, true);
        checkForRemoteUcastMac(tor1NodeId, DPN1MAC2, true);
        checkForRemoteUcastMac(tor1NodeId, DPN2MAC1, true);
        checkForRemoteUcastMac(tor1NodeId, DPN2MAC2, true);
        checkForRemoteUcastMac(tor1NodeId, DPN3MAC1, true);
        checkForRemoteUcastMac(tor1NodeId, DPN3MAC2, true);

        //check for remote ucast mac(s) in tor2 against dpn1, dpn2 and dpn3, tor1)
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC2, true);

        // DELETE DPN3 MAC2
        deleteElanInterface(interfaceInfo);

        //check for remote ucast mac DPN3MAC2 deletion in tor1, tor2
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor1NodeId, DPN3MAC2, false);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC2, false);
    }

    @Test
    public void addUcastMacsAndVerifyInDPNsAndTORs() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1, MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);

        // Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //check for remote ucast mac(s) in tor2 against macs of dpn1, dpn2 and dpn3)
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC2, true);

        //create 2 localUcastMacs in TOR1
        createLocalUcastMac(tor1NodeId, TOR1MAC1, "10.0.0.1", "192.168.122.30");
        createLocalUcastMac(tor1NodeId, TOR1MAC2, "10.0.0.2", "192.168.122.40");

        //check for remote ucast mac(s) in tor2 against macs of tor1)
        checkForRemoteUcastMac(tor2NodeId, TOR1MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, TOR1MAC2, true);

        //Read and Compare DMAC flows in DPN1 against Local ucast mac(s) of TOR1 (MAC1, MAC2)
        verifyDmacFlowOfTOR(DPN1_ID, TOR1NODEID, TOR1MAC1, true);
        verifyDmacFlowOfTOR(DPN1_ID, TOR1NODEID, TOR1MAC2, true);

        //Read and Compare DMAC flows in DPN2 against Local ucast mac(s) of TOR1 (MAC1, MAC2)
        verifyDmacFlowOfTOR(DPN2_ID, TOR1NODEID, TOR1MAC1, true);
        verifyDmacFlowOfTOR(DPN2_ID, TOR1NODEID, TOR1MAC2, true);

        //Read and Compare DMAC flows in DPN3 against Local ucast mac(s) of TOR1 (MAC1, MAC2)
        verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC1, true);
        verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC2, true);
    }

    @Test
    public void deleteUcastMacAndVerifyInTOR() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1, MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);

        // Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //create 2 localUcastMacs in TOR1
        final LocalUcastMacs localUcastMacs1 = createLocalUcastMac(tor1NodeId, TOR1MAC1, "10.0.0.1",
                "192.168.122.30");
        final LocalUcastMacs localUcastMacs2 = createLocalUcastMac(tor1NodeId, TOR1MAC2, "10.0.0.2",
                "192.168.122.40");

        //node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //check for remote ucast mac(s) in tor2 against dpn1, dpn2 and dpn3, tor1)
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN1MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN2MAC2, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, DPN3MAC2, true);
        Thread.sleep(1000);
        checkForRemoteUcastMac(tor2NodeId, TOR1MAC1, true);
        checkForRemoteUcastMac(tor2NodeId, TOR1MAC2, true);

        //Read and Compare DMAC flows in DPN1 against Local ucast mac of TOR1 MAC1
        verifyDmacFlowOfTOR(DPN1_ID, TOR1NODEID, TOR1MAC1, true);
        verifyDmacFlowOfTOR(DPN1_ID, TOR1NODEID, TOR1MAC2, true);

        //delete local ucast TOR1 mac1
        InstanceIdentifier<LocalUcastMacs> localUcastMacsId = getMacIid(tor1NodeId, localUcastMacs1);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, localUcastMacsId);
        verifyDmacFlowOfTOR(DPN1_ID, TOR1NODEID, TOR1MAC1, false);

        //delete local ucast TOR1 mac2
        localUcastMacsId = getMacIid(tor1NodeId, localUcastMacs2);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, localUcastMacsId);
        verifyDmacFlowOfTOR(DPN1_ID, TOR1NODEID, TOR1MAC2, false);
    }

    @Test
    public void deleteGWConnAndVerify() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        //Add Elan MAC1, MAC2 in DPN3
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP2);

        // TOR Node 1 creation
        InstanceIdentifier<Node> tor1NodeId = create_tor_node(TOR1NODEID, "s3", TOR1_TEPIP);
        createL2gwL2gwconn(tor1NodeId, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        //create 2 localUcastMacs in TOR NODE1
        createLocalUcastMac(tor1NodeId, TOR1MAC1, "10.0.0.1", "192.168.122.30");
        createLocalUcastMac(tor1NodeId, TOR1MAC2, "10.0.0.2", "192.168.122.40");

        // TOR Node 2 creation
        InstanceIdentifier<Node> tor2NodeId = create_tor_node(TOR2NODEID, "s4", TOR2_TEPIP);
        createL2gwL2gwconn(tor2NodeId, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");

        //check for remote mcast mac in tor2 against TEPs of dpn1, dpn2 and dpn3, tor1)
        checkForRemoteMcastMac(tor2NodeId, DPN1_TEPIP, true);
        checkForRemoteMcastMac(tor2NodeId, DPN2_TEPIP, true);
        checkForRemoteMcastMac(tor2NodeId, DPN3_TEPIP, true);
        checkForRemoteMcastMac(tor2NodeId, TOR1_TEPIP, true);

        //delete node 2 (tor2) l2gw connection
        deletel2GWConnection("l2gwConnection2");

        //check for remote mcast mac in tor2 against TEPs of dpn1, dpn2 and dpn3, tor1 - they should be deleted)
        Thread.sleep(1000);
        checkForRemoteMcastMac(tor2NodeId, DPN1_TEPIP, false);
        checkForRemoteMcastMac(tor2NodeId, DPN2_TEPIP, false);
        checkForRemoteMcastMac(tor2NodeId, DPN3_TEPIP, false);
        Thread.sleep(1000);
        checkForRemoteMcastMac(tor2NodeId, TOR1_TEPIP, false);
    }

    public void createElanInstanceAndInterfaceAndAttachEvpn() throws ReadFailedException,
            TransactionCommitFailedException {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Read Elan Instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance elanInstance = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft(), DPN1IP1);

        // Attach EVPN to networks
        evpnTestHelper.attachEvpnToNetwork(elanInstance);
    }

    public Networks readBgpNetworkFromDS(String prefix) throws ReadFailedException {
        InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(prefix, RD))
                .build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        return singleTxdataBroker.syncRead(CONFIGURATION, iid);
    }

    private void awaitForElanTag(String elanName) {
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanName)).build();
        getAwaiter().until(() -> {
            Optional<ElanInstance> elanInstance = MDSALUtil.read(dataBroker, CONFIGURATION, elanInstanceIid);
            return elanInstance.isPresent() && elanInstance.get().getElanTag() != null;
        });
    }
}

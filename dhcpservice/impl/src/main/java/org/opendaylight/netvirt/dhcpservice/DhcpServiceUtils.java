/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;


import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.ExpectedDataObjectNotFoundException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchArpOp;
import org.opendaylight.genius.mdsalutil.matches.MatchArpTpa;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionV4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.InterfaceNameMacAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.SubnetDhcpPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddressKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.subnet.dhcp.port.data.SubnetToDhcpPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.subnet.dhcp.port.data.SubnetToDhcpPortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.subnet.dhcp.port.data.SubnetToDhcpPortKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DhcpServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(DhcpServiceUtils.class);

    private static List<Uint64> connectedDpnIds = new CopyOnWriteArrayList<>();

    private DhcpServiceUtils() {

    }

    public static void setupDhcpFlowEntry(@Nullable Uint64 dpId, short tableId, @Nullable String vmMacAddress,
                                          int addOrRemove,
                                          IMdsalApiManager mdsalUtil, DhcpServiceCounters dhcpServiceCounters,
                                          TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        if (dpId == null || dpId.equals(DhcpMConstants.INVALID_DPID) || vmMacAddress == null) {
            return;
        }
        List<MatchInfo> matches = getDhcpMatch(vmMacAddress);
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();

        // Punt to controller
        actionsInfos.add(new ActionPuntToController());
        instructions.add(new InstructionApplyActions(actionsInfos));
        if (addOrRemove == NwConstants.DEL_FLOW) {
            LOG.trace("Removing DHCP Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            dhcpServiceCounters.removeDhcpFlow();
            mdsalUtil.removeFlow(tx, dpId, getDhcpFlowRef(dpId, tableId, vmMacAddress), tableId);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress), DhcpMConstants.DEFAULT_DHCP_FLOW_PRIORITY,
                    "DHCP", 0, 0, DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
            LOG.trace("Installing DHCP Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            dhcpServiceCounters.installDhcpFlow();
            mdsalUtil.addFlow(tx, flowEntity);
        }
    }

    private static String getDhcpFlowRef(Uint64 dpId, long tableId, String vmMacAddress) {
        return new StringBuilder().append(DhcpMConstants.FLOWID_PREFIX)
                .append(dpId.toString()).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
                .append(vmMacAddress).toString();
    }

    private static String getDhcpArpFlowRef(Uint64 dpId, long tableId, long lportTag, String ipAddress) {
        return new StringBuilder().append(DhcpMConstants.FLOWID_PREFIX)
                .append(dpId.toString()).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
                .append(lportTag).append(NwConstants.FLOWID_SEPARATOR)
                .append(ipAddress).toString();
    }

    public static void setupDhcpDropAction(Uint64 dpId, short tableId, String vmMacAddress, int addOrRemove,
                                           IMdsalApiManager mdsalUtil, DhcpServiceCounters dhcpServiceCounters,
                                           TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        if (dpId == null || dpId.equals(DhcpMConstants.INVALID_DPID) || vmMacAddress == null) {
            return;
        }
        List<MatchInfo> matches = getDhcpMatch(vmMacAddress);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        // Drop Action
        actionsInfos.add(new ActionDrop());
        if (addOrRemove == NwConstants.DEL_FLOW) {
            LOG.trace("Removing DHCP Drop Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            dhcpServiceCounters.removeDhcpDropFlow();
            mdsalUtil.removeFlow(tx, dpId, getDhcpFlowRef(dpId, tableId, vmMacAddress), tableId);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress), DhcpMConstants.DEFAULT_DHCP_FLOW_PRIORITY,
                    "DHCP", 0, 0, DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
            LOG.trace("Installing DHCP Drop Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            dhcpServiceCounters.installDhcpDropFlow();
            mdsalUtil.addFlow(tx, flowEntity);
        }
    }

    public static void setupDhcpArpRequest(Uint64 dpId, short tableId, Uint64 vni, String dhcpIpAddress,
                                           int lportTag, @Nullable Long elanTag, boolean add,
                                           IMdsalApiManager mdsalUtil, TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        List<MatchInfo> matches = getDhcpArpMatch(vni, dhcpIpAddress);
        if (add) {
            Flow flow = MDSALUtil.buildFlowNew(tableId, getDhcpArpFlowRef(dpId, tableId, lportTag, dhcpIpAddress),
                    DhcpMConstants.DEFAULT_DHCP_ARP_FLOW_PRIORITY, "DHCPArp", 0, 0,
                    generateDhcpArpCookie(lportTag, dhcpIpAddress), matches, null);
            LOG.trace("Removing DHCP ARP Flow DpId {}, DHCP Port IpAddress {}", dpId, dhcpIpAddress);
            mdsalUtil.removeFlow(tx, dpId, flow);
        } else {
            Flow flow = MDSALUtil.buildFlowNew(tableId, getDhcpArpFlowRef(dpId, tableId, lportTag, dhcpIpAddress),
                    DhcpMConstants.DEFAULT_DHCP_ARP_FLOW_PRIORITY, "DHCPArp", 0, 0,
                    generateDhcpArpCookie(lportTag, dhcpIpAddress), matches,
                    getDhcpArpInstructions(elanTag, lportTag));
            LOG.trace("Adding DHCP ARP Flow DpId {}, DHCPPort IpAddress {}", dpId, dhcpIpAddress);
            mdsalUtil.addFlow(tx, dpId, flow);
        }
    }

    public static List<MatchInfo> getDhcpMatch() {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(MatchIpProtocol.UDP);
        matches.add(new MatchUdpSourcePort(DhcpMConstants.DHCP_CLIENT_PORT));
        matches.add(new MatchUdpDestinationPort(DhcpMConstants.DHCP_SERVER_PORT));
        return matches;
    }

    public static List<MatchInfo> getDhcpMatch(String vmMacAddress) {
        List<MatchInfo> matches = getDhcpMatch();
        matches.add(new MatchEthernetSource(new MacAddress(vmMacAddress)));
        return matches;
    }

    private static List<MatchInfo> getDhcpArpMatch(Uint64 vni, String ipAddress) {
        return Arrays.asList(MatchEthernetType.ARP, MatchArpOp.REQUEST, new MatchTunnelId(vni),
                new MatchArpTpa(ipAddress, "32"));
    }

    private static Map<InstructionKey, Instruction> getDhcpArpInstructions(Long elanTag, int lportTag) {
        Map<InstructionKey, Instruction> mkInstructions = new HashMap<>();
        int instructionKey = 0;
        mkInstructions.put(new InstructionKey(++instructionKey), MDSALUtil.buildAndGetWriteMetadaInstruction(
                ElanHelper.getElanMetadataLabel(elanTag, lportTag), ElanHelper.getElanMetadataMask(),
                instructionKey));
        mkInstructions.put(new InstructionKey(++instructionKey), MDSALUtil
                .buildAndGetGotoTableInstruction(NwConstants.ARP_RESPONDER_TABLE, instructionKey));
        return mkInstructions;
    }

    private static Uint64 generateDhcpArpCookie(int lportTag, String ipAddress) {
        try {
            return Uint64.valueOf(NwConstants.TUNNEL_TABLE_COOKIE.toJava().add(BigInteger.valueOf(255))
                    .add(BigInteger.valueOf(NWUtil.convertInetAddressToLong(InetAddress.getByName(ipAddress))))
                    .add(BigInteger.valueOf(lportTag)));
        } catch (UnknownHostException e) {
            return Uint64.valueOf(NwConstants.TUNNEL_TABLE_COOKIE.toJava().add(BigInteger.valueOf(lportTag)));
        }
    }

    public static List<Uint64> getListOfDpns(DataBroker broker) {
        if (!connectedDpnIds.isEmpty()) {
            return connectedDpnIds;
        }
        try {
            return extractDpnsFromNodes(MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                    InstanceIdentifier.builder(Nodes.class).build()));
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getListOfDpns: Exception while reading getListOfDpns DS", e);
            return Collections.emptyList();
        }
    }

    @NonNull
    private static List<Uint64> extractDpnsFromNodes(Optional<Nodes> optionalNodes) {
        return optionalNodes.map(
            nodes -> nodes.nonnullNode().values().stream().map(Node::getId).filter(Objects::nonNull).map(
                    MDSALUtil::getDpnIdFromNodeName).collect(
                    Collectors.toList())).orElse(Collections.emptyList());
    }

    @NonNull
    public static List<Uint64> getDpnsForElan(String elanInstanceName, DataBroker broker) {
        List<Uint64> elanDpns = new LinkedList<>();
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInstanceIdentifier =
                InstanceIdentifier.builder(ElanDpnInterfaces.class)
                        .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
        Optional<ElanDpnInterfacesList> elanDpnOptional;
        try {
            elanDpnOptional = SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.OPERATIONAL,
                    elanDpnInstanceIdentifier);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getDpnsForElan: Exception while reading ElanDpnInterfacesList DS for the elanInstanceName {}",
                    elanInstanceName, e);
            return Collections.emptyList();
        }
        if (elanDpnOptional.isPresent()) {
            Map<DpnInterfacesKey, DpnInterfaces> dpnInterfacesMap = elanDpnOptional.get().nonnullDpnInterfaces();
            for (DpnInterfaces dpnInterfaces : dpnInterfacesMap.values()) {
                elanDpns.add(dpnInterfaces.getDpId());
            }
        }
        return elanDpns;
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .@Nullable Interface getInterfaceFromOperationalDS(String interfaceName, DataBroker dataBroker) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                .state.InterfaceKey interfaceKey =
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                        .state.InterfaceKey(interfaceName);
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                .interfaces.state.Interface> interfaceId = InstanceIdentifier.builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                        .interfaces.state.Interface.class, interfaceKey).build();
        return MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, interfaceId, dataBroker).orElse(null);
    }


    @Nullable
    public static String getSegmentationId(Uuid networkId, DataBroker broker) {
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class)
                .child(Networks.class).child(Network.class, new NetworkKey(networkId));
        Optional<Network> optionalNetwork;
        try {
            optionalNetwork = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getSegmentationId: Exception while reading Network DS for the Network {}",
                    networkId.getValue(), e);
            return null;
        }
        if (!optionalNetwork.isPresent()) {
            return null;
        }
        Network network = optionalNetwork.get();
        String segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(network, NetworkTypeVxlan.class);
        return segmentationId;
    }

    public static String getJobKey(String interfaceName) {
        return new StringBuilder().append(DhcpMConstants.DHCP_JOB_KEY_PREFIX).append(interfaceName).toString();
    }

    public static void bindDhcpService(String interfaceName, short tableId, TypedWriteTransaction<Configuration> tx) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(tableId, ++instructionKey));
        short serviceIndex = ServiceIndex.getIndex(NwConstants.DHCP_SERVICE_NAME, NwConstants.DHCP_SERVICE_INDEX);
        BoundServices
                serviceInfo =
                getBoundServices(String.format("%s.%s", "dhcp", interfaceName),
                        serviceIndex, DhcpMConstants.DEFAULT_FLOW_PRIORITY,
                        DhcpMConstants.COOKIE_VM_INGRESS_TABLE, instructions);
        tx.mergeParentStructurePut(buildServiceId(interfaceName, serviceIndex), serviceInfo);
    }

    public static void unbindDhcpService(String interfaceName, TypedWriteTransaction<Configuration> tx) {
        short serviceIndex = ServiceIndex.getIndex(NwConstants.DHCP_SERVICE_NAME, NwConstants.DHCP_SERVICE_INDEX);
        tx.delete(buildServiceId(interfaceName, serviceIndex));
    }

    private static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName,
                                                             short dhcpServicePriority) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(dhcpServicePriority)).build();
    }

    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                          Uint64 cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie)
                .setFlowPriority(flowPriority).setInstruction(instructions);
        return new BoundServicesBuilder().withKey(new BoundServicesKey(servicePriority))
                .setServiceName(serviceName).setServicePriority(servicePriority)
                .setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(augBuilder.build()).build();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected static void createSubnetDhcpPortData(Port port,
            BiConsumer<InstanceIdentifier<SubnetToDhcpPort>, SubnetToDhcpPort> consumer) {
        java.util.Optional<String> ip4Address = getIpV4Address(port);
        java.util.Optional<String> subnetId = getNeutronSubnetId(port);
        if ((!ip4Address.isPresent() || !subnetId.isPresent())) {
            return;
        }
        LOG.trace("Adding SubnetPortData entry for subnet {}", subnetId.get());
        InstanceIdentifier<SubnetToDhcpPort> identifier = buildSubnetToDhcpPort(subnetId.get());
        SubnetToDhcpPort subnetToDhcpPort = getSubnetToDhcpPort(port, subnetId.get(), ip4Address.get());
        try {
            LOG.trace("Adding to SubnetToDhcpPort subnet {}  mac {}.", subnetId.get(),
                    port.getMacAddress().getValue());
            consumer.accept(identifier, subnetToDhcpPort);
        } catch (Exception e) {
            LOG.error("Failure while creating SubnetToDhcpPort map for network {}.", port.getNetworkId(), e);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected static void removeSubnetDhcpPortData(Port port, Consumer<InstanceIdentifier<SubnetToDhcpPort>> consumer) {
        String subnetId = port.getDeviceId().substring("OpenDaylight".length() + 1);
        LOG.trace("Removing NetworkPortData entry for Subnet {}", subnetId);
        InstanceIdentifier<SubnetToDhcpPort> identifier = buildSubnetToDhcpPort(subnetId);
        try {
            consumer.accept(identifier);
            LOG.trace("Deleted SubnetDhcpPort for Subnet {}", subnetId);
        } catch (Exception e) {
            LOG.error("Failure while removing SubnetToDhcpPort for subnet {}.", subnetId, e);
        }

    }

    static InstanceIdentifier<SubnetToDhcpPort> buildSubnetToDhcpPort(String subnetId) {
        return InstanceIdentifier.builder(SubnetDhcpPortData.class)
                .child(SubnetToDhcpPort.class, new SubnetToDhcpPortKey(subnetId)).build();
    }

    public static java.util.Optional<SubnetToDhcpPort> getSubnetDhcpPortData(DataBroker broker, String subnetId) {
        InstanceIdentifier<SubnetToDhcpPort> id = buildSubnetToDhcpPort(subnetId);
        try {
            return java.util.Optional
                    .ofNullable(SingleTransactionDataBroker.syncRead(broker, LogicalDatastoreType.CONFIGURATION, id));
        } catch (ExpectedDataObjectNotFoundException e) {
            LOG.warn("Failed to read SubnetToDhcpPort for DS due to error {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }

    static IpAddress convertLongToIp(long ip) {
        String[] array = LongStream.of(24, 16, 8, 0) //
                .map(x -> ip >> x & 0xFF).boxed() //
                .map(String::valueOf) //
                .toArray(String[]::new);
        return IpAddressBuilder.getDefaultInstance(String.join(".", array));
    }

    static long convertIpToLong(IpAddress ipa) {
        String[] splitIp = ipa.stringValue().split("\\.");
        long result = 0;
        for (String part : splitIp) {
            result <<= 8;
            result |= Integer.parseInt(part);
        }
        return result;
    }


    static SubnetToDhcpPort getSubnetToDhcpPort(Port port, String subnetId, String ipAddress) {
        return new SubnetToDhcpPortBuilder()
                .withKey(new SubnetToDhcpPortKey(subnetId))
                .setSubnetId(subnetId).setPortName(port.getUuid().getValue())
                .setPortMacaddress(port.getMacAddress().getValue()).setPortFixedip(ipAddress).build();
    }

    static InterfaceInfo getInterfaceInfo(IInterfaceManager interfaceManager, String interfaceName) {
        return interfaceManager.getInterfaceInfoFromOperationalDataStore(interfaceName);
    }

    public static java.util.Optional<String> getIpV4Address(Port port) {
        if (port.getFixedIps() == null) {
            return java.util.Optional.empty();
        }
        return port.getFixedIps().values().stream().filter(DhcpServiceUtils::isIpV4AddressAvailable)
                .map(v -> v.getIpAddress().getIpv4Address().getValue()).findFirst();
    }

    public static java.util.Optional<String> getNeutronSubnetId(Port port) {
        if (port.getFixedIps() == null) {
            return java.util.Optional.empty();
        }
        return port.getFixedIps().values().stream().filter(DhcpServiceUtils::isIpV4AddressAvailable)
                .map(v -> v.getSubnetId().getValue()).findFirst();
    }

    public static boolean isIpV4AddressAvailable(FixedIps fixedIp) {
        return fixedIp != null && fixedIp.getIpAddress() != null && fixedIp.getIpAddress().getIpv4Address() != null;
    }

    @Nullable
    public static String getAndUpdateVmMacAddress(TypedReadWriteTransaction<Operational> tx, String interfaceName,
            DhcpManager dhcpManager) throws ExecutionException, InterruptedException {
        InstanceIdentifier<InterfaceNameMacAddress> instanceIdentifier =
                InstanceIdentifier.builder(InterfaceNameMacAddresses.class)
                        .child(InterfaceNameMacAddress.class, new InterfaceNameMacAddressKey(interfaceName)).build();
        Optional<InterfaceNameMacAddress> existingEntry = tx.read(instanceIdentifier).get();
        if (!existingEntry.isPresent()) {
            LOG.trace("Entry for interface {} missing in InterfaceNameVmMacAddress map", interfaceName);
            String vmMacAddress = getNeutronMacAddress(interfaceName, dhcpManager);
            if (vmMacAddress == null || vmMacAddress.isEmpty()) {
                return null;
            }
            LOG.trace("Updating InterfaceNameVmMacAddress map with {}, {}", interfaceName,vmMacAddress);
            InterfaceNameMacAddress interfaceNameMacAddress =
                    new InterfaceNameMacAddressBuilder()
                            .withKey(new InterfaceNameMacAddressKey(interfaceName))
                            .setInterfaceName(interfaceName).setMacAddress(vmMacAddress).build();
            tx.mergeParentStructureMerge(instanceIdentifier, interfaceNameMacAddress);
            return vmMacAddress;
        }
        return existingEntry.get().getMacAddress();
    }

    @Nullable
    private static String getNeutronMacAddress(String interfaceName, DhcpManager dhcpManager) {
        Port port = dhcpManager.getNeutronPort(interfaceName);
        if (port != null) {
            LOG.trace("Port found in neutron. Interface Name {}, port {}", interfaceName, port);
            return port.getMacAddress().getValue();
        }
        return null;
    }

    @NonNull
    public static List<Uuid> getSubnetIdsFromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier id = buildNetworkMapIdentifier(networkId);
        Optional<NetworkMap> optionalNetworkMap;
        try {
            optionalNetworkMap = SingleTransactionDataBroker.syncReadOptional(broker,
                    LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getSubnetIdsFromNetworkId: Exception while reading NetworkMap DS for the network {}",
                    networkId.getValue(), e);
            return Collections.emptyList();
        }
        if (optionalNetworkMap.isPresent()) {
            @Nullable List<Uuid> subnetIdList = optionalNetworkMap.get().getSubnetIdList();
            if (subnetIdList != null) {
                return subnetIdList;
            }
        }
        return Collections.emptyList();
    }

    static InstanceIdentifier<NetworkMap> buildNetworkMapIdentifier(Uuid networkId) {
        return InstanceIdentifier.builder(NetworkMaps.class).child(NetworkMap.class,
            new NetworkMapKey(networkId)).build();
    }

    public static boolean isIpv4Subnet(DataBroker broker, Uuid subnetUuid) {
        final SubnetKey subnetkey = new SubnetKey(subnetUuid);
        final InstanceIdentifier<Subnet> subnetidentifier = InstanceIdentifier.create(Neutron.class)
                .child(Subnets.class).child(Subnet.class, subnetkey);
        final Optional<Subnet> subnet;
        try {
            subnet = SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION,
                    subnetidentifier);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("isIpv4Subnet: Exception while reading Subnet DS for the Subnet {}",
                    subnetUuid.getValue(), e);
            return false;
        }
        if (subnet.isPresent()) {
            Class<? extends IpVersionBase> ipVersionBase = subnet.get().getIpVersion();
            return IpVersionV4.class.equals(ipVersionBase);
        }
        return false;
    }

    public static void addToDpnIdCache(Uint64 dpnId) {
        if (!connectedDpnIds.contains(dpnId)) {
            connectedDpnIds.add(dpnId);
        }
    }

    public static void removeFromDpnIdCache(Uint64 dpnId) {
        connectedDpnIds.remove(dpnId);
    }

    public static Uint64 getDpnIdFromNodeConnectorId(NodeConnectorId nodeConnectorId) {
        Long dpIdLong = MDSALUtil.getDpnIdFromPortName(nodeConnectorId);
        return dpIdLong < 0 ? Uint64.ZERO : Uint64.valueOf(dpIdLong);
    }
}


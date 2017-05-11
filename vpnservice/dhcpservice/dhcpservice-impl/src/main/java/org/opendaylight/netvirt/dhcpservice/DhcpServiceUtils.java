/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.netvirt.dhcpservice.api.DHCPConstants;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.NetworkDhcpportData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.network.dhcpport.data.NetworkToDhcpport;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.network.dhcpport.data.NetworkToDhcpportBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.network.dhcpport.data.NetworkToDhcpportKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpServiceUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpServiceUtils.class);
    private static String OF_URI_SEPARATOR = ":";


    public static void setupDhcpFlowEntry(BigInteger dpId, short tableId, String vmMacAddress, int addOrRemove,
                                          IMdsalApiManager mdsalUtil, WriteTransaction tx) {
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
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress),
                    DhcpMConstants.DEFAULT_DHCP_FLOW_PRIORITY, "DHCP", 0, 0,
                    DhcpMConstants.COOKIE_DHCP_BASE, matches, null);
            LOG.trace("Removing DHCP Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            DhcpServiceCounters.remove_dhcp_flow.inc();
            mdsalUtil.removeFlowToTx(flowEntity, tx);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress), DhcpMConstants.DEFAULT_DHCP_FLOW_PRIORITY,
                    "DHCP", 0, 0, DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
            LOG.trace("Installing DHCP Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            DhcpServiceCounters.install_dhcp_flow.inc();
            mdsalUtil.addFlowToTx(flowEntity, tx);
        }
    }

    private static String getDhcpFlowRef(BigInteger dpId, long tableId, String vmMacAddress) {
        return new StringBuffer().append(DhcpMConstants.FLOWID_PREFIX)
                .append(dpId).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
                .append(vmMacAddress).toString();
    }

    public static void setupDhcpDropAction(BigInteger dpId, short tableId, String vmMacAddress, int addOrRemove,
                                           IMdsalApiManager mdsalUtil, WriteTransaction tx) {
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
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress),
                    DhcpMConstants.DEFAULT_DHCP_FLOW_PRIORITY, "DHCP", 0, 0,
                    DhcpMConstants.COOKIE_DHCP_BASE, matches, null);
            LOG.trace("Removing DHCP Drop Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            DhcpServiceCounters.remove_dhcp_drop_flow.inc();
            mdsalUtil.removeFlowToTx(flowEntity, tx);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress), DhcpMConstants.DEFAULT_DHCP_FLOW_PRIORITY,
                    "DHCP", 0, 0, DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
            LOG.trace("Installing DHCP Drop Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            DhcpServiceCounters.install_dhcp_drop_flow.inc();
            mdsalUtil.addFlowToTx(flowEntity, tx);
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

    public static List<BigInteger> getListOfDpns(DataBroker broker) {
        List<BigInteger> dpnsList = new LinkedList<>();
        InstanceIdentifier<Nodes> nodesInstanceIdentifier = InstanceIdentifier.builder(Nodes.class).build();
        Optional<Nodes> nodesOptional =
                MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, nodesInstanceIdentifier);
        if (!nodesOptional.isPresent()) {
            return dpnsList;
        }
        Nodes nodes = nodesOptional.get();
        List<Node> nodeList = nodes.getNode();
        for (Node node : nodeList) {
            NodeId nodeId = node.getId();
            if (nodeId == null) {
                continue;
            }
            BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeId);
            dpnsList.add(dpnId);
        }
        return dpnsList;
    }

    public static List<BigInteger> getDpnsForElan(String elanInstanceName, DataBroker broker) {
        List<BigInteger> elanDpns = new LinkedList<>();
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInstanceIdentifier =
                InstanceIdentifier.builder(ElanDpnInterfaces.class)
                        .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
        Optional<ElanDpnInterfacesList> elanDpnOptional =
                MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInstanceIdentifier);
        if (elanDpnOptional.isPresent()) {
            List<DpnInterfaces> dpns = elanDpnOptional.get().getDpnInterfaces();
            for (DpnInterfaces dpnInterfaces : dpns) {
                elanDpns.add(dpnInterfaces.getDpId());
            }
        }
        return elanDpns;
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .state.Interface getInterfaceFromOperationalDS(String interfaceName, DataBroker dataBroker) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                .state.InterfaceKey interfaceKey =
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                        .state.InterfaceKey(interfaceName);
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                .interfaces.state.Interface> interfaceId = InstanceIdentifier.builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                        .interfaces.state.Interface.class, interfaceKey).build();
        return MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, interfaceId, dataBroker).orNull();
    }


    public static String getSegmentationId(Uuid networkId, DataBroker broker) {
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class)
                .child(Networks.class).child(Network.class, new NetworkKey(networkId));
        Optional<Network> optionalNetwork = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (!optionalNetwork.isPresent()) {
            return null;
        }
        Network network = optionalNetwork.get();
        String segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(network, NetworkTypeVxlan.class);
        return segmentationId;
    }

    public static String getNodeIdFromDpnId(BigInteger dpnId) {
        return MDSALUtil.NODE_PREFIX + MDSALUtil.SEPARATOR + dpnId.toString();
    }

    public static String getTrunkPortMacAddress(String parentRefName,
            DataBroker broker) {
        InstanceIdentifier<Port> portInstanceIdentifier =
                InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
        Optional<Port> trunkPort = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, portInstanceIdentifier);
        if (!trunkPort.isPresent()) {
            LOG.warn("Trunk port {} not available for sub-port", parentRefName);
            return null;
        }
        return trunkPort.get().getMacAddress().getValue();
    }

    public static String getJobKey(String interfaceName) {
        return new StringBuilder().append(DhcpMConstants.DHCP_JOB_KEY_PREFIX).append(interfaceName).toString();
    }

    public static void submitTransaction(WriteTransaction tx) {
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore tx {} error {}", tx, e.getMessage());
        }
    }

    public static void bindDhcpService(String interfaceName, short tableId, WriteTransaction tx) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(tableId, ++instructionKey));
        short serviceIndex = ServiceIndex.getIndex(NwConstants.DHCP_SERVICE_NAME, NwConstants.DHCP_SERVICE_INDEX);
        BoundServices
                serviceInfo =
                getBoundServices(String.format("%s.%s", "dhcp", interfaceName),
                        serviceIndex, DhcpMConstants.DEFAULT_FLOW_PRIORITY,
                        DhcpMConstants.COOKIE_VM_INGRESS_TABLE, instructions);
        tx.put(LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, serviceIndex), serviceInfo, true);
    }

    public static void unbindDhcpService(String interfaceName, WriteTransaction tx) {
        short serviceIndex = ServiceIndex.getIndex(NwConstants.DHCP_SERVICE_NAME, NwConstants.DHCP_SERVICE_INDEX);
        tx.delete(LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, serviceIndex));
    }

    private static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName,
                                                             short dhcpServicePriority) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(dhcpServicePriority)).build();
    }

    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                          BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie)
                .setFlowPriority(flowPriority).setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority))
                .setServiceName(serviceName).setServicePriority(servicePriority)
                .setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static boolean anyOtherInterfacceOnDpn(DataBroker broker, BigInteger dpId, String elanInstanceName,
            final String interfaceName) {

        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = MDSALUtil.read(broker,
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        LOG.trace("Elan DPN Interfaces present? {}", existingElanDpnInterfaces.isPresent() ? "yes" : "no");
        if (!existingElanDpnInterfaces.isPresent()) {
            return false;
        }
        return existingElanDpnInterfaces.get().getDpnInterfaces().stream().filter(v -> v.getDpId().equals(dpId))
                .flatMap(v -> v.getInterfaces().stream()).anyMatch(v -> v != null && !v.equals(interfaceName));
    }

    public static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();

    }

    public static InstanceIdentifier<DpnInterfaces> getDpnInterfacesOperationDataPath(String elanInstanceName,
            BigInteger dpnId) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName))
                .child(DpnInterfaces.class, new DpnInterfacesKey(dpnId)).build();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected static void createNetworkDhcpPortData(DataBroker broker,Port port, WriteTransaction tx) {
        LOG.trace("Adding NetworkPortData entry for network {}", port.getNetworkId().getValue());
        InstanceIdentifier<NetworkToDhcpport> identifier = buildNetworktoDhcpPort(port.getNetworkId().getValue());
        List<FixedIps> fixedIps = port.getFixedIps();
        fixedIps.stream().filter(v -> v.getIpAddress().getIpv4Address() != null).findFirst()
                .map(v -> v.getIpAddress().getIpv4Address().getValue()).ifPresent(ip -> {
                    NetworkToDhcpport networkToDhcpport = getNetworkToDhcpPort(port, ip);
                    try {
                        if (tx != null) {
                            tx.put(LogicalDatastoreType.CONFIGURATION, identifier, networkToDhcpport);
                        } else {
                            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, identifier,
                                    networkToDhcpport);
                        }
                        LOG.trace("Adding to NetworktoDhcpPort network {}  mac {}.", port.getNetworkId().getValue(),
                                port.getMacAddress().getValue());
                    } catch (Exception e) {
                        LOG.error("Failure while creating NetworkToDhcpPort map for network {}.", port.getNetworkId(),
                                e);
                    }
                });

    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected static void removeNetworkDhcpPortData(DataBroker broker, Port port, WriteTransaction tx) {
        LOG.trace("Removing NetworkPortData entry for Network {}", port.getNetworkId().getValue());
        InstanceIdentifier<NetworkToDhcpport> identifier = buildNetworktoDhcpPort(port.getNetworkId().getValue());
        try {
            if (tx != null) {
                tx.delete(LogicalDatastoreType.CONFIGURATION, identifier);
            } else {
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, identifier);
            }
            LOG.trace("Deleted NetworkDhcpPort for Network {}",port.getNetworkId());
        } catch (Exception e) {
            LOG.error("Failure while removing NetworkToDhcpPort for network {}.", port.getNetworkId(),e);
        }
    }

    static InstanceIdentifier<NetworkToDhcpport> buildNetworktoDhcpPort(String networkId) {
        InstanceIdentifier<NetworkToDhcpport> id = InstanceIdentifier.builder(NetworkDhcpportData.class)
                .child(NetworkToDhcpport.class, new NetworkToDhcpportKey(networkId)).build();
        return id;
    }

    public static java.util.Optional<NetworkToDhcpport> getNetworkDhcpPortData(DataBroker broker, String networkId) {
        InstanceIdentifier<NetworkToDhcpport> id = buildNetworktoDhcpPort(networkId);
        return java.util.Optional.ofNullable(MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).orNull());
    }

    static IpAddress convertIntToIp(int ipn) {
        String[] array = IntStream.of(24, 16, 8, 0) //
                .map(x -> (ipn >> x) & 0xFF).boxed() //
                .map(x -> String.valueOf(x)) //
                .toArray(x -> new String[x]);
        return new IpAddress(String.join(".", array).toCharArray());
    }

    static IpAddress convertLongToIp(long ip) {
        String[] array = LongStream.of(24, 16, 8, 0) //
                .map(x -> (ip >> x) & 0xFF).boxed() //
                .map(x -> String.valueOf(x)) //
                .toArray(x -> new String[x]);
        return new IpAddress(String.join(".", array).toCharArray());
    }

    static long convertIpToLong(IpAddress ipa) {
        String[] splitIp = String.valueOf(ipa.getValue()).split("\\.");
        long result = 0;
        for (String part : splitIp) {
            result <<= 8;
            result |= Integer.parseInt(part);
        }
        return result;
    }

    public static java.util.Optional<String> getAvailableDpnInterface(DataBroker broker, BigInteger dpId,
            String elanInstanceName) {
        InstanceIdentifier<DpnInterfaces> identifier = getDpnInterfacesOperationDataPath(elanInstanceName, dpId);
        Optional<DpnInterfaces> optional = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, identifier);
        if (optional.isPresent()) {
            return optional.get().getInterfaces().stream().findFirst();

        }
        return java.util.Optional.empty();
    }

    static List<String> getDpnInterfacesInElanInstance(DataBroker broker, String elanInstanceName) {
        List<String> interfaces = new ArrayList<>();
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = MDSALUtil.read(broker,
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if (!existingElanDpnInterfaces.isPresent()) {
            return interfaces;
        }
        return existingElanDpnInterfaces.get().getDpnInterfaces().stream().flatMap(v->v.getInterfaces().stream())
                .collect(Collectors.toList());
    }

    static NetworkToDhcpport getNetworkToDhcpPort(Port port, String ipAddress) {
        NetworkToDhcpportBuilder builder = new NetworkToDhcpportBuilder()
                .setKey(new NetworkToDhcpportKey(port.getNetworkId().getValue()))
                .setPortNetworkid(port.getNetworkId().getValue()).setPortName(port.getUuid().getValue())
                .setMacAddress(port.getMacAddress().getValue()).setPortFixedip(ipAddress);
        return builder.build();
    }

    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .Interface getInterfaceStateFromOperDS(
            DataBroker dataBroker, String interfaceName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
                .rev140508.interfaces.state.Interface>
                ifStateId =
                buildStateInterfaceId(interfaceName);
        Optional<Interface> ifStateOptional =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, ifStateId);
        if (ifStateOptional.isPresent()) {
            return ifStateOptional.get();
        }

        return null;
    }

    private static InstanceIdentifier<Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                        .child(
                                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                                        .state.Interface.class,
                                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                                        .state.InterfaceKey(
                                        interfaceName));
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
                .rev140508.interfaces.state.Interface>
                id = idBuilder.build();
        return id;
    }

    static BigInteger getDpIdFromInterface(
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                    ifState) {
        String lowerLayerIf = ifState.getLowerLayerIf().get(0);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
        return new BigInteger(getDpnFromNodeConnectorId(nodeConnectorId));
    }

    private static String getDpnFromNodeConnectorId(NodeConnectorId portId) {
        /*
         * NodeConnectorId is of form 'openflow:dpnid:portnum'
         */
        String[] split = portId.getValue().split(OF_URI_SEPARATOR);
        return split[1];
    }

    public static final Predicate<Port> DHCP_PORT = (port) -> {
        return port != null && port.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_DHCP);
    };

    static final Predicate<Port> ODL_DHCP_PORT = (port) -> {
        return port != null && port.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_DHCP)
                && port.getDeviceId() != null && port.getDeviceId().startsWith("OpenDaylight");
    };


}
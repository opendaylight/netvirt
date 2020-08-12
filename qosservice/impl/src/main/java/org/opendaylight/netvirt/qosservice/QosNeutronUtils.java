/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.felix.service.command.CommandSession;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldDscp;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.KeyedLocks;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeInterfaceInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.QosPolicyPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.qos.policy.port.map.QosPolicyList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.qos.policy.port.map.QosPolicyListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.qos.policy.port.map.qos.policy.list.NodeList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.qos.policy.port.map.qos.policy.list.NodeListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.qos.policy.port.map.qos.policy.list.node.list.PortList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.qos.policy.port.map.qos.policy.list.node.list.PortListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.qos.policy.port.map.qos.policy.list.node.list.PortListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.DscpmarkingRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.QosTypeEgressPolicer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.QosEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.QosEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.QosEntriesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.qos.entries.QosOtherConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.qos.entries.QosOtherConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.ConfigureTerminationPointWithQosInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.ConfigureTerminationPointWithQosOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.SouthBoundRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.configure.termination.point.with.qos.input.EgressQos;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.configure.termination.point.with.qos.input.EgressQosBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.configure.termination.point.with.qos.input.IngressQos;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.configure.termination.point.with.qos.input.IngressQosBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNeutronUtils {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronUtils.class);

    private final ConcurrentMap<Uuid, QosPolicy> qosPolicyMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, ConcurrentMap<Uuid, Port>> qosPortsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, ConcurrentMap<Uuid, Network>> qosNetworksMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<Uuid> qosServiceConfiguredPorts = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<Uuid, Port> neutronPortMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Uuid, Network> neutronNetworkMap = new ConcurrentHashMap<>();
    private final QosEosHandler qosEosHandler;
    private final INeutronVpnManager neutronVpnManager;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalUtils;
    private final JobCoordinator jobCoordinator;
    private final SouthBoundRpcService southBoundRpcService;
    private final KeyedLocks<String> keyedLocks = new KeyedLocks<String>();

    @Inject
    public QosNeutronUtils(final QosEosHandler qosEosHandler, final INeutronVpnManager neutronVpnManager,
                           final OdlInterfaceRpcService odlInterfaceRpcService, final DataBroker dataBroker,
                           final IMdsalApiManager mdsalUtils, final JobCoordinator jobCoordinator,
                           final SouthBoundRpcService southBoundRpcService) {
        this.qosEosHandler = qosEosHandler;
        this.neutronVpnManager = neutronVpnManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.dataBroker = dataBroker;
        this.mdsalUtils = mdsalUtils;
        this.jobCoordinator = jobCoordinator;
        this.southBoundRpcService = southBoundRpcService;
    }

    public void addToQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.put(qosPolicy.getUuid(), qosPolicy);
    }

    public void removeFromQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.remove(qosPolicy.getUuid());
    }

    public Map<Uuid, QosPolicy> getQosPolicyMap() {
        return qosPolicyMap;
    }

    public Collection<Port> getQosPorts(Uuid qosUuid) {
        final ConcurrentMap<Uuid, Port> portMap = qosPortsMap.get(qosUuid);
        return portMap != null ? portMap.values() : Collections.emptyList();
    }

    public void addToQosPortsCache(Uuid qosUuid, Port port) {
        qosPortsMap.computeIfAbsent(qosUuid, key -> new ConcurrentHashMap<>()).putIfAbsent(port.getUuid(), port);
    }

    public void removeFromQosPortsCache(Uuid qosUuid, Port port) {
        if (qosPortsMap.containsKey(qosUuid) && qosPortsMap.get(qosUuid).containsKey(port.getUuid())) {
            qosPortsMap.get(qosUuid).remove(port.getUuid(), port);
        }
    }

    public void addToQosNetworksCache(Uuid qosUuid, Network network) {
        qosNetworksMap.computeIfAbsent(qosUuid, key -> new ConcurrentHashMap<>()).putIfAbsent(network.getUuid(),
                network);
    }

    public void removeFromQosNetworksCache(Uuid qosUuid, Network network) {
        if (qosNetworksMap.containsKey(qosUuid) && qosNetworksMap.get(qosUuid).containsKey(network.getUuid())) {
            qosNetworksMap.get(qosUuid).remove(network.getUuid(), network);
        }
    }

    public void displayQosConfig(CommandSession session) {

        Map<String, Object> properties = new HashMap<>(1);
        properties.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonGeneratorFactory jsonGeneratorFactory = Json.createGeneratorFactory(properties);
        StringWriter stringWriter = new StringWriter();

        session.getConsole().println("QosClusterOwner: " + qosEosHandler.isQosClusterOwner());
        if (qosPolicyMap.isEmpty() && qosPortsMap.isEmpty() && qosNetworksMap.isEmpty()) {
            session.getConsole().println("No cache found");
            return;
        }
        if (!(qosPolicyMap.isEmpty())) {
            displayQosPolicyMap(session, jsonGeneratorFactory, stringWriter);
        }
        if (!(qosPortsMap.isEmpty())) {
            displayQosPortsMap(session, jsonGeneratorFactory, stringWriter);
        }
        if (!(qosNetworksMap.isEmpty())) {
            displayQosNetworksMap(session, jsonGeneratorFactory, stringWriter);
        }
    }

    private void displayQosPolicyMap(CommandSession session, JsonGeneratorFactory jsonGeneratorFactory,
                                     StringWriter stringWriter) {
        session.getConsole().println("\nQOS Policy Map");
        String uuid;
        String policyName;
        String dscpUuid;
        String bandwidthUuid;
        Long maxRate;
        Long maxBurstRate;
        Short dscpValue;
        Uuid policyUuid;
        JsonGenerator generator = jsonGeneratorFactory.createGenerator(stringWriter);
        stringWriter.getBuffer().setLength(0);
        generator.writeStartArray();

        for (ConcurrentMap.Entry<Uuid, QosPolicy> policyEntry : qosPolicyMap.entrySet()) {
            dscpUuid = "null";
            bandwidthUuid = "null";
            maxRate = 0L;
            maxBurstRate = 0L;
            dscpValue = 0;
            policyUuid = policyEntry.getKey();
            uuid = policyEntry.getKey().getValue();
            policyName = qosPolicyMap.get(policyUuid).getName();
            if (qosPolicyMap.get(policyUuid).getBandwidthLimitRules() != null
                    && !(qosPolicyMap.get(policyUuid).getBandwidthLimitRules().isEmpty())) {
                BandwidthLimitRules bandwidthLimitRules = qosPolicyMap.get(policyUuid).getBandwidthLimitRules().get(0);
                bandwidthUuid = bandwidthLimitRules.getUuid().getValue();
                maxRate = bandwidthLimitRules.getMaxKbps().longValue();
                maxBurstRate = bandwidthLimitRules.getMaxBurstKbps().longValue();
            }
            if (qosPolicyMap.get(policyUuid).getDscpmarkingRules() != null
                    && !(qosPolicyMap.get(policyUuid).getDscpmarkingRules().isEmpty())) {
                dscpUuid = qosPolicyMap.get(policyUuid).getDscpmarkingRules().get(0).getUuid().getValue();
                dscpValue = qosPolicyMap.get(policyUuid).getDscpmarkingRules().get(0).getDscpMark();
            }
            generator.writeStartObject().write("Policy uuid", uuid)
                    .write("Policy Name", policyName)
                    .write("Bandwidth uuid", bandwidthUuid)
                    .write("max_kbps", maxRate)
                    .write("max_burst_kbps", maxBurstRate)
                    .write("Dscp uuid", dscpUuid)
                    .write("Dscp Value", dscpValue).writeEnd();
        }
        generator.writeEnd();
        generator.close();
        session.getConsole().println(stringWriter.toString());
    }

    private void displayQosPortsMap(CommandSession session, JsonGeneratorFactory jsonGeneratorFactory,
                                    StringWriter stringWriter) {
        session.getConsole().println("\nQOS Ports Map");
        String policyId;
        String policyName;
        String portUuid;
        String portName;
        String portDetails;
        Uuid policyUuid;
        Uuid portId;
        JsonGenerator generator = jsonGeneratorFactory.createGenerator(stringWriter);
        JsonGenerator arrayGenerator;
        stringWriter.getBuffer().setLength(0);
        generator.writeStartArray();

        for (ConcurrentMap.Entry<Uuid, ConcurrentMap<Uuid, Port>> policyEntry : qosPortsMap.entrySet()) {
            policyUuid = policyEntry.getKey();
            policyId = policyUuid.getValue();
            policyName = qosPolicyMap.get(policyUuid).getName();
            generator.writeStartObject().write("Policy Uuid", policyId).write("PolicyName", policyName);
            arrayGenerator = generator.writeStartArray("Port Details");
            ConcurrentMap<Uuid, Port> portInnerMap = qosPortsMap.get(policyUuid);
            for (ConcurrentMap.Entry<Uuid, Port> portEntry : portInnerMap.entrySet()) {
                portId = portEntry.getKey();
                if (portId != null) {
                    portUuid = portInnerMap.get(portId).getUuid().getValue();
                    portName = portInnerMap.get(portId).getName();
                    if (portName == null) {
                        portName = "null";
                    }
                    portDetails = portUuid + " : " + portName;
                    arrayGenerator.write(portDetails);
                }
            }
            arrayGenerator.writeEnd();
            generator.writeEnd();
        }
        generator.writeEnd();
        generator.close();
        session.getConsole().println(stringWriter.toString());
    }

    private void displayQosNetworksMap(CommandSession session, JsonGeneratorFactory jsonGeneratorFactory,
                                       StringWriter stringWriter) {
        session.getConsole().println("\nQos Networks Map");
        String policyId;
        String policyName;
        String networkId;
        String networkName;
        String networkDetails;
        Uuid policyUuid;
        Uuid networkUuid;
        JsonGenerator generator = jsonGeneratorFactory.createGenerator(stringWriter);
        JsonGenerator arrayGenerator;
        stringWriter.getBuffer().setLength(0);
        generator.writeStartArray();

        for (ConcurrentMap.Entry<Uuid, ConcurrentMap<Uuid, Network>> policyEntry : qosNetworksMap.entrySet()) {
            policyUuid = policyEntry.getKey();
            policyId = policyUuid.getValue();
            policyName = qosPolicyMap.get(policyUuid).getName();
            generator.writeStartObject().write("Policy Uuid", policyId).write("PolicyName", policyName);
            arrayGenerator = generator.writeStartArray("Network Details");
            ConcurrentMap<Uuid, Network> networkInnerMap = qosNetworksMap.get(policyUuid);
            for (ConcurrentMap.Entry<Uuid, Network> networkEntry : networkInnerMap.entrySet()) {
                networkUuid = networkEntry.getKey();
                if (networkUuid != null) {
                    networkId = networkInnerMap.get(networkUuid).getUuid().getValue();
                    networkName = networkInnerMap.get(networkUuid).getName();
                    if (networkName == null) {
                        networkName = "null";
                    }
                    networkDetails = networkId + " : " + networkName;
                    arrayGenerator.write(networkDetails);
                }
            }
            arrayGenerator.writeEnd();
            generator.writeEnd();
        }
        generator.writeEnd();
        generator.close();
        session.getConsole().println(stringWriter.toString());
    }

    @Nonnull
    public Collection<Network> getQosNetworks(Uuid qosUuid) {
        final ConcurrentMap<Uuid, Network> networkMap = qosNetworksMap.get(qosUuid);
        return networkMap != null ? networkMap.values() : Collections.emptyList();
    }

    @Nonnull
    protected List<Uuid> getPortIdsFromSubnetId(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetMapId = InstanceIdentifier
                .builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        Optional<Subnetmap> optionalSubnetmap = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION,
                subnetMapId, dataBroker);
        return optionalSubnetmap.isPresent() ? optionalSubnetmap.get().getPortList() : Collections.emptyList();
    }

    public void handleNeutronPortQosAdd(Port port, Uuid qosUuid) {
        QosConstants.EVENT_LOGGER.info("QoS - add: port {}, policy: {}", port.getUuid().getValue(),
                qosUuid.getValue());
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);

        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            // handle Bandwidth Limit Rules update
            if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                    && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                setPortBandwidthLimits(port, qosUuid, qosPolicy.getBandwidthLimitRules());
            }
            // handle DSCP Mark Rules update
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                setPortDscpMarking(port, qosPolicy.getDscpmarkingRules().get(0));
            }
            return Collections.emptyList();
        });
    }


    public Future configureTerminationPoint(List<IngressQos> ingressQosListDetails,
                                            List<EgressQos> egressQosListDetails,
                                            String  terminationPoint,
                                            InstanceIdentifier<Node> nodeIid) {

        LOG.trace("configureTerminationPoint called with ingressQosListDetails={} egressQosListDetails={}"
                + " tp={} nodeIid={}", ingressQosListDetails, egressQosListDetails, terminationPoint, nodeIid);
        ConfigureTerminationPointWithQosInputBuilder builder = new ConfigureTerminationPointWithQosInputBuilder();
        builder.setNode(nodeIid);
        builder.setTerminationPointName(terminationPoint);

        if (ingressQosListDetails != null) {
            builder.setIngressQos(ingressQosListDetails);
        }
        if (egressQosListDetails != null) {
            builder.setEgressQos(egressQosListDetails);
        }
        Future<RpcResult<ConfigureTerminationPointWithQosOutput>> qosConfigureFuture =
                southBoundRpcService.configureTerminationPointWithQos(builder.build());
        try {
            RpcResult<ConfigureTerminationPointWithQosOutput> result = qosConfigureFuture.get();
            if (result.isSuccessful()) {
                LOG.debug("configureTerminationPoint: SouthBound RPC call to Interface {}  got successful",
                        terminationPoint);
            } else {
                LOG.error("configureTerminationPoint: SouthBound RPC call to Interface {}  got failed",
                        terminationPoint,result.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("configureTerminationPoint: Error while configuring QOS using OVSDB RPC");
        }
        return qosConfigureFuture;
    }

    public void handleQosInterfaceAdd(Port port, Uuid qosUuid) {
        QosConstants.EVENT_LOGGER.info("QoS - add Intf {}, policy: {}", port.getUuid().getValue(),
                qosUuid.getValue());
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);

        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            // handle Bandwidth Limit Rules update
            if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                    && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                setPortBandwidthLimits(port, qosUuid, qosPolicy.getBandwidthLimitRules());
            }
            // handle DSCP Mark Rules update
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                setPortDscpMarking(port, qosPolicy.getDscpmarkingRules().get(0));
            }
            return Collections.emptyList();
        });
    }

    public void handleNeutronPortQosUpdate(Port port, Uuid qosUuidNew, Uuid qosUuidOld) {
        QosConstants.EVENT_LOGGER.info("QoS - update port: {}, policy: {}", port.getUuid().getValue(),
                qosUuidNew.getValue());

        QosPolicy qosPolicyNew = qosPolicyMap.get(qosUuidNew);
        QosPolicy qosPolicyOld = qosPolicyMap.get(qosUuidOld);

        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            // handle Bandwidth Limit Rules update
            if (qosPolicyOld != null && qosPolicyOld.getBandwidthLimitRules() != null) {
                unsetPortBandwidthLimits(port, qosPolicyOld.getUuid(), qosPolicyOld.getBandwidthLimitRules());
            }
            if (qosPolicyNew != null && qosPolicyNew.getBandwidthLimitRules() != null) {
                setPortBandwidthLimits(port, qosPolicyNew.getUuid(), qosPolicyNew.getBandwidthLimitRules());
            }

            //handle DSCP Mark Rules update
            LOG.debug("qosPolicyNew:{}", qosPolicyNew.toString());
            if (qosPolicyNew != null && qosPolicyNew.getDscpmarkingRules() != null
                    && !qosPolicyNew.getDscpmarkingRules().isEmpty()) {
                setPortDscpMarking(port, qosPolicyNew.getDscpmarkingRules().get(0));
            } else if (qosPolicyOld != null && qosPolicyOld.getDscpmarkingRules() != null
                    && !qosPolicyOld.getDscpmarkingRules().isEmpty()) {
                unsetPortDscpMark(port);
            }
            return Collections.emptyList();
        });
    }

    public void handleNeutronPortQosRemove(Port port, Uuid qosUuid) {
        QosConstants.EVENT_LOGGER.info("QoS - remove port {}, policy {}", port.getUuid().getValue(),
                qosUuid.getValue());

        // check for network qosservice to apply
        Network network = neutronVpnManager.getNeutronNetwork(port.getNetworkId());
        if (network != null && network.getAugmentation(QosNetworkExtension.class) != null) {
            Uuid networkQosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
            if (networkQosUuid != null) {
                handleNeutronPortQosUpdate(port, networkQosUuid, qosUuid);
            }
        } else {
            QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);
            jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
                // handle Bandwidth Limit Rules removal
                if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null) {
                    unsetPortBandwidthLimits(port, qosPolicy.getUuid(), qosPolicy.getBandwidthLimitRules());
                }
                // handle DSCP MArk Rules removal
                if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null) {
                    unsetPortDscpMark(port);
                }
                return Collections.emptyList();
            });
        }
    }

    public void handleNeutronPortRemove(Port port, Uuid qosUuid) {
        LOG.debug("Handling Port removal and Qos associated: port: {} qos: {}", port.getUuid().getValue(),
                qosUuid.getValue());
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);
        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            //check if any DSCP rule in the policy
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                unsetPortDscpMark(port);
            }
            return Collections.emptyList();
        });
    }

    public void handleNeutronPortRemove(Port port, Uuid qosUuid, Interface intrf) {
        QosConstants.EVENT_LOGGER.info("Qos - remove Intf {}, policy: {}", port.getUuid().getValue(),
                qosUuid.getValue());
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);
        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                unsetPortDscpMark(port, intrf);
            }
            return Collections.emptyList();
        });
    }

    public void handleNeutronNetworkQosAdd(Network network, Uuid qosUuid) {
        LOG.debug("Handling Network QoS Add: net: {} qosservice: {}", network.getUuid().getValue(), qosUuid.getValue());
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);
        if (qosPolicy == null || (qosPolicy.getBandwidthLimitRules() == null
                || qosPolicy.getBandwidthLimitRules().isEmpty())
                && (qosPolicy.getDscpmarkingRules() == null
                || qosPolicy.getDscpmarkingRules().isEmpty())) {
            return;
        }
        List<Uuid> subnetIds = NeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());
        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                        || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                        List<BandwidthLimitRules> bwRuleList = qosPolicy.getBandwidthLimitRules();
                        if (bwRuleList != null && !bwRuleList.isEmpty()) {
                            setPortBandwidthLimits(port, qosUuid, bwRuleList);
                        }
                        if (qosPolicy.getDscpmarkingRules() != null && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                            setPortDscpMarking(port, qosPolicy.getDscpmarkingRules().get(0));
                        }
                        return Collections.emptyList();
                    });
                }
            }
        }
    }


    public void handleNeutronNetworkQosUpdate(Network network, Uuid origQosUuid, Uuid updQosUuid) {
        QosPolicy origQosPolicy = qosPolicyMap.get(origQosUuid);
        QosPolicy updQosPolicy = qosPolicyMap.get(updQosUuid);
        LOG.debug("Handling Network QoS update: Network = {} origQosUuid = {} updatedQosUuid = {}",
                network.getUuid().getValue(), origQosUuid, updQosUuid);
        List<Uuid> subnetIds = NeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());
        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                        || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                        if (origQosPolicy != null && origQosPolicy.getBandwidthLimitRules() != null) {
                            unsetPortBandwidthLimits(port, origQosUuid, origQosPolicy.getBandwidthLimitRules());
                        }
                        if (updQosPolicy != null && updQosPolicy.getBandwidthLimitRules() != null) {
                            setPortBandwidthLimits(port, updQosUuid, updQosPolicy.getBandwidthLimitRules());
                        }
                        //handle DSCP Mark Rules update
                        if (updQosPolicy != null && updQosPolicy.getDscpmarkingRules() != null
                                && !updQosPolicy.getDscpmarkingRules().isEmpty()) {
                            setPortDscpMarking(port, updQosPolicy.getDscpmarkingRules().get(0));
                        } else if (origQosPolicy != null && origQosPolicy.getDscpmarkingRules() != null
                                && !origQosPolicy.getDscpmarkingRules().isEmpty()) {
                            unsetPortDscpMark(port);
                        }
                        return Collections.emptyList();
                    });
                }
            }
        }
    }

    public void handleNeutronNetworkQosRemove(Network network, Uuid qosUuid) {
        LOG.debug("Handling Network QoS removal: net: {} qosservice: {}", network.getUuid().getValue(),
                qosUuid.getValue());
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);

        List<Uuid> subnetIds = NeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());
        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                        || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                        if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null) {
                            unsetPortBandwidthLimits(port, qosPolicy.getUuid(), qosPolicy.getBandwidthLimitRules());
                        }
                        if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                                && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                            unsetPortDscpMark(port);
                        }
                        return Collections.emptyList();
                    });
                }
            }
        }
    }

    public void handleNeutronNetworkQosBwRuleRemove(Network network, Uuid qosUuid, BandwidthLimitRules bwRule) {
        LOG.debug("Handling Qos Bandwidth Rule Remove, net: {}", network.getUuid().getValue());

        List<Uuid> subnetIds = NeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());

        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                        || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                        unsetPortBandwidthLimits(port, qosUuid, Collections.singletonList(bwRule));
                        return Collections.emptyList();
                    });
                }
            }
        }
    }

    public List<IngressQos> getQosIngressParamsList(BandwidthLimitRules bwLimit) {
        List<IngressQos> ingressQosList = new ArrayList<>();
        ingressQosList.add(new IngressQosBuilder().setIngressQosParam(QosConstants.INGRESS_POLICING_RATE)
                .setIngressQosParamValue(bwLimit.getMaxKbps().toString()).build());
        ingressQosList.add(new IngressQosBuilder().setIngressQosParam(QosConstants.INGRESS_POLICING_BURST)
                .setIngressQosParamValue(bwLimit.getMaxBurstKbps().toString()).build());
        return ingressQosList;
    }


    public List<EgressQos> getQosEgressParamsList(BandwidthLimitRules bwLimit, Uuid portUuid,
                                                  InstanceIdentifier<Node> nodeIid, Uuid qosPolicyId, String tpName) {
        List<EgressQos> egressQosList = new ArrayList<>();
        NodeKey nodeKey = nodeIid.firstKeyOf(Node.class);
        String nodeId = nodeKey.getNodeId().getValue();
        nodeId = nodeId.substring(0, nodeId.indexOf(QosConstants.BRIDGE_PREFIX));
        InstanceIdentifier<QosEntries> qosEntryIid = getQosEntriesIdentifier(nodeId, qosPolicyId);
        Optional<QosEntries> qosEntriesCfg = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION,
                qosEntryIid, dataBroker);
        Optional<QosEntries> qosEntriesOpt = MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, qosEntryIid, dataBroker);
        // check whether the Qos Policy is present in oper topo
        boolean isRuleModified = false;
        if (qosEntriesOpt.isPresent() && qosEntriesCfg.isPresent()) {
            // if present call RPC to set the qos by getting uuid from oper topo
            QosEntries qosEntry = qosEntriesOpt.get();
            isRuleModified = isBandwidthLimitRuleChanged(bwLimit, qosEntry);
            if (!isRuleModified) {
                egressQosList.add(new EgressQosBuilder().setEgressQosParam("qos")
                        .setEgressQosParamValue(qosEntry.getQosUuid().getValue()).build());
                addPortToQosPolicyPortMapDS(qosPolicyId, nodeId, portUuid.getValue(), tpName);
                return egressQosList;
            }
        }

        LOG.error("getQosEgressParamsList: Creating qosEntry in config DS as it's not "
                + "found in operational DS for policy {} ", qosPolicyId);
        List<PortList> portList = null;
        if (!isRuleModified) {
            try {
                keyedLocks.lock(qosPolicyId.getValue() + nodeId);
                /* lets see if some other port is creating this entry */
                portList = getPortListFromQosPolicyPortMapDS(qosPolicyId.getValue(), nodeId);
                addPortToQosPolicyPortMapDS(qosPolicyId, nodeId, portUuid.getValue(), tpName);
            } finally {
                keyedLocks.unlock(qosPolicyId.getValue() + nodeId);
            }
        }
        // If I am the first port for this qospolicy and nodeId
        if ((portList == null && !qosEntriesCfg.isPresent()) || isRuleModified) {
            QosOtherConfig cbs = new QosOtherConfigBuilder().setOtherConfigKey(QosConstants.COMMITTED_BURST_SIZE)
                    .setOtherConfigValue((bwLimit.getMaxBurstKbps().multiply(QosConstants.KILOBITS_TO_BYTES_MULTIPLIER))
                            .toString()).build();
            QosOtherConfig cir = new QosOtherConfigBuilder().setOtherConfigKey(QosConstants.COMMITTED_INFORMATION_RATE)
                    .setOtherConfigValue((bwLimit.getMaxKbps().multiply(QosConstants.KILOBITS_TO_BYTES_MULTIPLIER))
                            .toString()).build();
            List<QosOtherConfig> list = new ArrayList<>();
            list.add(cbs);
            list.add(cir);
            QosEntries addQosEntries = new QosEntriesBuilder()
                    .setKey(new QosEntriesKey(new Uri(QosConstants.QOS_URI_PREFIX + qosPolicyId)))
                    .setQosOtherConfig(list).setQosType(QosTypeEgressPolicer.class).build();
            LOG.debug("getQosEgressParamsList: Adding qosOvsdbEntry with qosPolicyId:{} nodeId:{}",
                    qosPolicyId, nodeId);
            try {
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, qosEntryIid,
                        addQosEntries);
            } catch (TransactionCommitFailedException ex) {
                LOG.error("Unable to create Entry for qosId {} nodeId {}", qosPolicyId.getValue(), nodeId);
            }
            LOG.debug("getQosEgressParamsList: Entry for qosPolicyId {} added to config DS successfully", qosPolicyId);
        }
        return egressQosList;
    }

    String getCbs(QosEntries qosEntry) {
        java.util.Optional<QosOtherConfig> cbsOpt = qosEntry.getQosOtherConfig().stream()
                .filter(qosOtherConfig -> qosOtherConfig.getOtherConfigKey().equals(QosConstants.COMMITTED_BURST_SIZE))
                .findFirst();
        return cbsOpt.map(QosOtherConfig::getOtherConfigValue).orElse(null);
    }

    String getCir(QosEntries qosEntry) {
        java.util.Optional<QosOtherConfig> cirOpt = qosEntry.getQosOtherConfig().stream()
                .filter(qosOtherConfig -> qosOtherConfig.getOtherConfigKey()
                        .equals(QosConstants.COMMITTED_INFORMATION_RATE))
                .findFirst();
        return cirOpt.map(QosOtherConfig::getOtherConfigValue).orElse(null);
    }

    Boolean isBandwidthLimitRuleChanged(BandwidthLimitRules bwLimit, QosEntries qosEntry) {
        String cbs = getCbs(qosEntry);
        String cir = getCir(qosEntry);
        String  maxBurstKbps = (bwLimit.getMaxBurstKbps().multiply(QosConstants.KILOBITS_TO_BYTES_MULTIPLIER))
                .toString();
        String  maxKbps = (bwLimit.getMaxBurstKbps().multiply(QosConstants.KILOBITS_TO_BYTES_MULTIPLIER))
                .toString();
        return  !(Objects.equals(cbs, maxBurstKbps)) && !(Objects.equals(cir, maxKbps));
    }

    Pair<TerminationPoint, InstanceIdentifier<Node>> getTerminationPointAndNodeIdentifier(Port port) {
        BigInteger dpId = getDpnForInterface(port.getUuid().getValue());
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.error("getTerminationPointAndNodeIdentifier: DPN ID for interface {} not found",
                    port.getUuid().getValue());
            return null;
        }

        OvsdbBridgeRef bridgeRefEntry = getBridgeRefEntryFromOperDS(dpId);
        Optional<Node> bridgeNode = MDSALUtil.read(LogicalDatastoreType.OPERATIONAL,
                bridgeRefEntry.getValue().firstIdentifierOf(Node.class), dataBroker);
        if (!bridgeNode.isPresent()) {
            LOG.error("setPortBandwidthLimits: bridge not found for dpn {} port {} in operational datastore",
                    dpId, port.getUuid().getValue());
            return null;
        }
        LOG.trace("getTerminationPointAndNodeIdentifier: bridgeNode {}", bridgeNode.get().getNodeId().getValue());

        TerminationPoint tp = SouthboundUtils.getTerminationPointByExternalId(bridgeNode.get(),
                port.getUuid().getValue());
        if (tp == null) {
            LOG.debug("Skipping setting of bandwidth limit rules for subport {}", port.getUuid().getValue());
            return null;
        }
        LOG.debug("tp: {}", tp.getTpId().getValue());

        InstanceIdentifier<Node> nodeIid = bridgeRefEntry.getValue().firstIdentifierOf(Node.class);
        LOG.error("nodeIid {}", nodeIid.toString());
        return Pair.of(tp, nodeIid);
    }

    public void handleNeutronNetworkQosDscpRuleRemove(Network network) {
        LOG.debug("Handling Qos Dscp Rule Remove, net: {}", network.getUuid().getValue());

        List<Uuid> subnetIds = NeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());

        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                        || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                        WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        unsetPortDscpMark(port);
                        futures.add(wrtConfigTxn.submit());
                        return futures;
                    });
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void setPortBandwidthLimits(Port port, Uuid qosPolicyId,
                                       List<BandwidthLimitRules> bwRules) {
        LOG.trace("setPortBandwidthLimits:get called with port = {} qos-policy = {} BW Limit Rules = {}",
                port.getUuid().getValue(), qosPolicyId.getValue(), bwRules);
        if (!qosEosHandler.isQosClusterOwner()) {
            LOG.trace("setPortBandwidthLimits: Not Qos Cluster Owner. Ignoring setting bandwidth limits");
            return;
        }
        if (bwRules == null || bwRules.isEmpty()) {
            LOG.debug("setPortBandwidthLimits: BandwidthLimit rule is not present for qosPolicy{}",
                    qosPolicyId.getValue());
            return;
        }
        Pair<TerminationPoint, InstanceIdentifier<Node>> tpAndNodeIid = getTerminationPointAndNodeIdentifier(port);
        if (tpAndNodeIid == null) {
            LOG.debug("setPortBandwidthLimits: tpAndNodeIid is NULL for port = {}", port.getUuid().getValue());
            return;
        }
        String tpName = tpAndNodeIid.getLeft().getAugmentation(OvsdbTerminationPointAugmentation.class).getName();
        InstanceIdentifier<Node> nodeIid = tpAndNodeIid.getRight();
        List<IngressQos> ingressQosList = Collections.emptyList();
        List<EgressQos> egressQosList = Collections.emptyList();

        LOG.debug("setPortBandwidthLimits:port = {} qosPolicyId = {} bwRules = {} toName = {}",port.toString(),
                qosPolicyId, bwRules.toString(), tpName);
        for (BandwidthLimitRules bwLimit : bwRules) {
            if (bwLimit.getDirection() == null || bwLimit.getDirection() == DirectionEgress.class) {
                ingressQosList = getQosIngressParamsList(bwLimit);
            } else if (bwLimit.getDirection() == DirectionIngress.class) {
                egressQosList = getQosEgressParamsList(bwLimit, port.getUuid(),
                        nodeIid, qosPolicyId, tpName);
            }
        }
        LOG.error("setPortBandwidthLimits: Calling OVSDB RPC to directly set the QoS on Interface {}", tpName);
        if (!ingressQosList.isEmpty() || !egressQosList.isEmpty()) {
            configureTerminationPoint(ingressQosList, egressQosList, tpName, nodeIid);
        }
    }


    public void unsetPortBandwidthLimits(Port port, Uuid qosPolicyId, List<BandwidthLimitRules> bwRules) {

        LOG.trace("unsetPortBandwidthLimits:get called with port = {} qos-policy = {} BW Limit Rules = {}",
                port.getUuid().getValue(), qosPolicyId.getValue(), bwRules);
        if (!qosEosHandler.isQosClusterOwner()) {
            LOG.trace("unsetPortBandwidthLimits: Not Qos Cluster Owner. Ignoring setting bandwidth limits");
            return;
        }
        if (bwRules == null || bwRules.isEmpty()) {
            LOG.debug("unsetPortBandwidthLimits: BandwidthLimit rule is not present for qosPolicy{}",
                    qosPolicyId.getValue());
            return;
        }
        Pair<TerminationPoint, InstanceIdentifier<Node>> tpAndNodeIid = getTerminationPointAndNodeIdentifier(port);
        if (tpAndNodeIid == null) {
            LOG.debug("unsetPortBandwidthLimits: tpAndNodeIid is NULL for port = {}", port.getUuid().getValue());
            return;
        }

        String tpName = tpAndNodeIid.getLeft().getAugmentation(OvsdbTerminationPointAugmentation.class).getName();
        List<IngressQos> ingressQosList = new ArrayList<>();
        List<EgressQos> egressQosList = new ArrayList<>();
        for (BandwidthLimitRules bwLimit: bwRules) {
            if (bwLimit.getDirection() == null || bwLimit.getDirection() == DirectionEgress.class) {
                ingressQosList.add(new IngressQosBuilder().setIngressQosParam(QosConstants.INGRESS_POLICING_RATE)
                        .setIngressQosParamValue("0").build());
                ingressQosList.add(new IngressQosBuilder().setIngressQosParam(QosConstants.INGRESS_POLICING_BURST)
                        .setIngressQosParamValue("0").build());
            } else if (bwLimit.getDirection() == DirectionIngress.class) {
                egressQosList.add(new EgressQosBuilder().setEgressQosParam("qos").setEgressQosParamValue("").build());
            }
        }
        LOG.error("unsetPortBandwidthLimits: Calling OVSDB RPC to directly set the QoS on Interface {}", tpName);
        configureTerminationPoint(ingressQosList, egressQosList, tpName, tpAndNodeIid.getRight());

        if (!egressQosList.isEmpty()) {
            /* Clear entry from DS entry */
            String nodeId = tpAndNodeIid.getRight().firstKeyOf(Node.class).getNodeId().getValue();
            nodeId = nodeId.substring(0, nodeId.indexOf(QosConstants.BRIDGE_PREFIX));
            try {
                keyedLocks.lock(qosPolicyId.getValue() + nodeId);
                delPortFromQosPolicyPortMapDS(qosPolicyId, nodeId, port.getUuid().getValue());
            } finally {
                keyedLocks.unlock(qosPolicyId.getValue() + nodeId);
            }
        }
    }

    public InstanceIdentifier<QosEntries> getQosEntriesIdentifier(String nodeId, Uuid qosPolicyId) {
        return InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                .child(Node.class,new NodeKey(new NodeId(nodeId)))
                .augmentation(OvsdbNodeAugmentation.class)
                .child(QosEntries.class, new QosEntriesKey(new Uri(
                        QosConstants.QOS_URI_PREFIX + qosPolicyId)));
    }

    public void setPortDscpMarking(Port port, DscpmarkingRules dscpMark) {
        BigInteger dpnId = getDpnForInterface(port.getUuid().getValue());
        String ifName = port.getUuid().getValue();
        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.error("DPN ID for interface {} not found. Cannot set dscp value {} on port {}",
                    port.getUuid().getValue(), dscpMark, port.getUuid().getValue());
            return;
        }
        if (!qosEosHandler.isQosClusterOwner()) {
            qosServiceConfiguredPorts.add(port.getUuid());
            LOG.trace("Not Qos Cluster Owner. Ignoring setting DSCP marking");
            return;
        } else {
            QosConstants.EVENT_LOGGER.info("QoS setting dscp marking on Port {} value {}",
                    port.getUuid().getValue(), dscpMark.getDscpMark());
            Interface ifState = getInterfaceStateFromOperDS(ifName);
            Short dscpValue = dscpMark.getDscpMark();
            int ipVersions = getIpVersions(port);
            //1. OF rules
            if (hasIpv4Addr(ipVersions)) {
                LOG.trace("setting ipv4 flow for port: {}, dscp: {}", ifName, dscpValue);
                addFlow(dpnId, dscpValue, ifName, NwConstants.ETHTYPE_IPV4, ifState);
            }
            if (hasIpv6Addr(ipVersions)) {
                LOG.trace("setting ipv6 flow for port: {}, dscp: {}", ifName, dscpValue);
                addFlow(dpnId, dscpValue, ifName, NwConstants.ETHTYPE_IPV6, ifState);
            }
            if (qosServiceConfiguredPorts.add(port.getUuid())) {
                // bind qos service to interface
                bindservice(ifName);
            }
        }
    }

    public void unsetPortDscpMark(Port port) {
        BigInteger dpnId = getDpnForInterface(port.getUuid().getValue());
        String ifName = port.getUuid().getValue();
        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.error("DPN ID for port {} not found. Cannot unset dscp value", port.getUuid().getValue());
            return;
        }
        if (!qosEosHandler.isQosClusterOwner()) {
            qosServiceConfiguredPorts.remove(port.getUuid());
            LOG.debug("Not Qos Cluster Owner. Ignoring unsetting DSCP marking");
            return;
        } else {
            QosConstants.EVENT_LOGGER.info("QoS unsetting dscp marking on Port {}", port.getUuid().getValue());
            LOG.trace("Removing dscp marking rule from Port {}", port.getUuid().getValue());
            Interface intf = getInterfaceStateFromOperDS(ifName);
            //unbind service from interface
            unbindservice(ifName);
            // 1. OF
            int ipVersions = getIpVersions(port);
            if (hasIpv4Addr(ipVersions)) {
                removeFlow(dpnId, ifName, NwConstants.ETHTYPE_IPV4, intf);
            }
            if (hasIpv6Addr(ipVersions)) {
                removeFlow(dpnId, ifName, NwConstants.ETHTYPE_IPV6, intf);
            }
            qosServiceConfiguredPorts.remove(port.getUuid());
        }
    }

    public void unsetPortDscpMark(Port port, Interface intrf) {
        BigInteger dpnId = getDpIdFromInterface(intrf);
        String ifName = port.getUuid().getValue();
        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.error("Unable to retrieve DPN Id for interface {}. Cannot unset dscp value on port", ifName);
            return;
        }
        if (!qosEosHandler.isQosClusterOwner()) {
            qosServiceConfiguredPorts.remove(port.getUuid());
            return;
        } else {
            LOG.trace("Removing dscp marking rule from Port {}", port.getUuid().getValue());
            QosConstants.EVENT_LOGGER.info("QoS unsetting dscp marking on interface {}", port.getUuid().getValue());
            unbindservice(ifName);
            int ipVersions = getIpVersions(port);
            if (hasIpv4Addr(ipVersions)) {
                removeFlow(dpnId, ifName, NwConstants.ETHTYPE_IPV4, intrf);
            }
            if (hasIpv6Addr(ipVersions)) {
                removeFlow(dpnId, ifName, NwConstants.ETHTYPE_IPV6, intrf);
            }
            qosServiceConfiguredPorts.remove(port.getUuid());
        }
    }

    private static BigInteger getDpIdFromInterface(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                                                           .interfaces.rev140508.interfaces.state.Interface ifState) {
        String lowerLayerIf = ifState.getLowerLayerIf().get(0);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
        return BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
    }

    public BigInteger getDpnForInterface(String ifName) {
        BigInteger nodeId = BigInteger.ZERO;
        try {
            GetDpidFromInterfaceInput
                    dpIdInput = new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>>
                    dpIdOutput = odlInterfaceRpcService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if (dpIdResult.isSuccessful()) {
                nodeId = dpIdResult.getResult().getDpid();
            } else {
                LOG.error("Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exception when getting DPN for interface {} exception ", ifName, e);
            } else {
                LOG.error("Could not retrieve DPN for interface {}", ifName);
            }
        }
        return nodeId;
    }

    @Nullable
    private BridgeEntry getBridgeEntryFromConfigDS(BigInteger dpnId) {
        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpnId);
        InstanceIdentifier<BridgeEntry> bridgeEntryInstanceIdentifier = getBridgeEntryIdentifier(bridgeEntryKey);
        LOG.debug("Trying to retrieve bridge entry from config for Id: {}", bridgeEntryInstanceIdentifier);
        return getBridgeEntryFromConfigDS(bridgeEntryInstanceIdentifier);
    }

    @Nullable
    private BridgeEntry getBridgeEntryFromConfigDS(InstanceIdentifier<BridgeEntry> bridgeEntryInstanceIdentifier) {
        return MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, bridgeEntryInstanceIdentifier, dataBroker).orNull();
    }

    @Nullable
    private BridgeRefEntry getBridgeRefEntryFromOperDS(InstanceIdentifier<BridgeRefEntry> dpnBridgeEntryIid) {
        return MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, dpnBridgeEntryIid, dataBroker).orNull();
    }

    @Nullable
    private OvsdbBridgeRef getBridgeRefEntryFromOperDS(BigInteger dpId) {
        BridgeRefEntryKey bridgeRefEntryKey = new BridgeRefEntryKey(dpId);
        InstanceIdentifier<BridgeRefEntry> bridgeRefEntryIid = getBridgeRefEntryIdentifier(bridgeRefEntryKey);
        BridgeRefEntry bridgeRefEntry = getBridgeRefEntryFromOperDS(bridgeRefEntryIid);
        if (bridgeRefEntry == null) {
            // bridge ref entry will be null if the bridge is disconnected from controller.
            // In that case, fetch bridge reference from bridge interface entry config DS
            BridgeEntry bridgeEntry = getBridgeEntryFromConfigDS(dpId);
            if (bridgeEntry == null) {
                return null;
            }
            return bridgeEntry.getBridgeReference();
        }
        return bridgeRefEntry.getBridgeReference();
    }

    @Nonnull
    private static InstanceIdentifier<BridgeRefEntry> getBridgeRefEntryIdentifier(BridgeRefEntryKey bridgeRefEntryKey) {
        return InstanceIdentifier.builder(BridgeRefInfo.class).child(BridgeRefEntry.class, bridgeRefEntryKey).build();
    }

    @Nonnull
    private static InstanceIdentifier<BridgeEntry> getBridgeEntryIdentifier(BridgeEntryKey bridgeEntryKey) {
        return InstanceIdentifier.builder(BridgeInterfaceInfo.class).child(BridgeEntry.class, bridgeEntryKey).build();
    }

    public void removeStaleFlowEntry(Interface intrf, int ethType) {
        List<MatchInfo> matches = new ArrayList<>();

        BigInteger dpnId = getDpIdFromInterface(intrf);

        Integer ifIndex = intrf.getIfIndex();
        matches.add(new MatchMetadata(MetaDataUtil.getLportTagMetaData(ifIndex), MetaDataUtil.METADATA_MASK_LPORT_TAG));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE,
                getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex, ethType),
                QosConstants.QOS_DEFAULT_FLOW_PRIORITY, "QoSRemoveFlow", 0, 0, NwConstants.COOKIE_QOS_TABLE,
                matches, null);
        mdsalUtils.removeFlow(flowEntity);
    }

    public void addFlow(BigInteger dpnId, Short dscpValue, String ifName, int ethType, Interface ifState) {
        if (ifState == null) {
            LOG.debug("Could not find the ifState for interface {}", ifName);
            return;
        }
        Integer ifIndex = ifState.getIfIndex();

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchEthernetType(ethType));
        matches.add(new MatchMetadata(MetaDataUtil.getLportTagMetaData(ifIndex), MetaDataUtil.METADATA_MASK_LPORT_TAG));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionSetFieldDscp(dscpValue));
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));

        List<InstructionInfo> instructions = Collections.singletonList(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE,
                getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex, ethType),
                QosConstants.QOS_DEFAULT_FLOW_PRIORITY, "QoSConfigFlow", 0, 0, NwConstants.COOKIE_QOS_TABLE,
                matches, instructions);
        mdsalUtils.installFlow(flowEntity);
    }

    public void removeFlow(BigInteger dpnId, String ifName, int ethType, Interface ifState) {
        if (ifState == null) {
            LOG.debug("Could not find the ifState for interface {}", ifName);
            return;
        }
        Integer ifIndex = ifState.getIfIndex();

        mdsalUtils.removeFlow(dpnId, NwConstants.QOS_DSCP_TABLE,
                new FlowId(getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex, ethType)));
    }

    @Nullable
    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface getInterfaceStateFromOperDS(
            String interfaceName) {
        return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                createInterfaceStateInstanceIdentifier(interfaceName)).orNull();
    }

    @Nonnull
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> createInterfaceStateInstanceIdentifier(
            String interfaceName) {
        return InstanceIdentifier
                .builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                                .ietf.interfaces.rev140508.interfaces.state.Interface.class,
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                                .ietf.interfaces.rev140508.interfaces.state.InterfaceKey(
                                interfaceName))
                .build();
    }

    public void bindservice(String ifName) {
        int priority = QosConstants.QOS_DEFAULT_FLOW_PRIORITY;
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.QOS_DSCP_TABLE, ++instructionKey));
        short qosServiceIndex = ServiceIndex.getIndex(NwConstants.QOS_SERVICE_NAME, NwConstants.QOS_SERVICE_INDEX);

        BoundServices serviceInfo = getBoundServices(
                String.format("%s.%s", "qos", ifName), qosServiceIndex,
                priority, NwConstants.COOKIE_QOS_TABLE, instructions);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(ifName, qosServiceIndex),
                serviceInfo);
    }

    public void unbindservice(String ifName) {
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, buildServiceId(ifName,
                ServiceIndex.getIndex(NwConstants.QOS_SERVICE_NAME, NwConstants.QOS_SERVICE_INDEX)));
    }

    private static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName, short qosServiceIndex) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(qosServiceIndex)).build();
    }

    private static BoundServices getBoundServices(String serviceName, short qosServiceIndex, int priority,
                                                  BigInteger cookieQosTable, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookieQosTable)
                .setFlowPriority(priority).setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(qosServiceIndex)).setServiceName(serviceName)
                .setServicePriority(qosServiceIndex).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    @Nonnull
    public static String getQosFlowId(short tableId, BigInteger dpId, int lportTag, int ethType) {
        return new StringBuilder().append(tableId).append(NwConstants.FLOWID_SEPARATOR).append(dpId)
                .append(NwConstants.FLOWID_SEPARATOR).append(lportTag)
                .append(NwConstants.FLOWID_SEPARATOR).append(ethType).toString();
    }

    public boolean portHasQosPolicy(Port port) {
        LOG.trace("checking qos policy for port: {}", port.getUuid().getValue());

        boolean isQosPolicy = port.getAugmentation(QosPortExtension.class) != null
                && port.getAugmentation(QosPortExtension.class).getQosPolicyId() != null;

        LOG.trace("portHasQosPolicy for  port: {} return value {}", port.getUuid().getValue(), isQosPolicy);
        return isQosPolicy;
    }

    @Nullable
    public QosPolicy getQosPolicy(Port port) {
        Uuid qosUuid = null;
        QosPolicy qosPolicy = null;

        if (port.getAugmentation(QosPortExtension.class) != null) {
            qosUuid = port.getAugmentation(QosPortExtension.class).getQosPolicyId();
        } else {
            Network network = neutronVpnManager.getNeutronNetwork(port.getNetworkId());
            if (network != null && network.getAugmentation(QosNetworkExtension.class) != null) {
                qosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
            }
        }
        if (qosUuid != null) {
            qosPolicy = qosPolicyMap.get(qosUuid);
        }
        return qosPolicy;
    }

    public boolean hasDscpMarkingRule(QosPolicy qosPolicy) {
        if (qosPolicy != null) {
            return qosPolicy.getDscpmarkingRules() != null && !qosPolicy.getDscpmarkingRules().isEmpty();
        }
        return false;
    }

    public void addToPortCache(Port port) {
        neutronPortMap.put(port.getUuid(), port);
    }

    public void removeOvsdbQosEntry(String nodeId, Uuid qosPolicyId) {
        InstanceIdentifier<QosEntries> qosEntryIid = getQosEntriesIdentifier(nodeId, qosPolicyId);
        try {
            LOG.debug("removeOvsdbQosEntry: Trying to delete OvsdbQosEntry nodeId {} qosPolicyId {} qosEntryIid {}",
                    nodeId, qosPolicyId, qosEntryIid);
            LOG.debug("Trying to delete the entry form config datastore");
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, qosEntryIid);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("Unable to delete Entry for qosId {} nodeId {}", qosPolicyId.getValue(), nodeId);
        }
    }

    public void delQosPolicyFromQosPolicyPortMapDS(Uuid qosId) {
        InstanceIdentifier<QosPolicyList> qosPolicyIid = getQosPolicyIdentifier(qosId);
        try {
            LOG.debug("delQosPolicyFromQosPolicyPortMapDS: Trying to delete OvsdbQosEntry qosId {} qosEntryIid {}",
                    qosId, qosPolicyIid);
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, qosPolicyIid);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("Unable to delete Entry for qosId {}", qosId.getValue());
        }
    }

    public void delPortFromQosPolicyPortMapDS(Uuid qosId, String nodeId, String portId) {
        InstanceIdentifier<NodeList> nodeListIid = getQosPolicyNodeIdentifier(qosId, nodeId);
        NodeList nodeList;
        try {
            nodeList = SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.OPERATIONAL, nodeListIid);
        } catch (ReadFailedException e) {
            LOG.error("delPortFromQosPolicyPortMapDS: Failed to read NodeList with error {}", e.getMessage());
            return;
        }
        if (nodeList == null) {
            LOG.error("delPortFromQosPolicyPortMapDS: empty nodeList for qos {} on dpn {}", qosId, nodeId);
            return;
        }
        LOG.trace("delPortFromQosPolicyPortMapDS: nodeList = {}", nodeList);
        if (nodeList.getPortList().stream().map(PortList::getPortId).anyMatch(portId::equals)) {
            if (nodeList.getPortList().size() == 1) {
                try {
                    SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            nodeListIid);
                    /* OvsdbQosEntry doesn't need /bridge/br-int in node id */
                    removeOvsdbQosEntry(nodeId, qosId);
                } catch (TransactionCommitFailedException ex) {
                    LOG.error("delPortFromQosPolicyPortMapDS: Unable to delete Entry for qosId {} nodeId {}",
                            qosId, nodeId);
                }
            } else {
                InstanceIdentifier<PortList> portIid = getQosPolicyNodePortIdentifier(qosId, nodeId, portId);
                try {
                    SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, portIid);
                } catch (TransactionCommitFailedException ex) {
                    LOG.error("delPortFromQosPolicyPortMapDS: Unable to delete Entry for qosId {} nodeId {} portId {}",
                            qosId, nodeId, portId);
                }
            }
        }
    }

    private InstanceIdentifier<PortList> getQosPolicyNodePortIdentifier(Uuid qosId, String nodeId,
                                                                               String portId) {
        return InstanceIdentifier.builder(QosPolicyPortMap.class).child(QosPolicyList.class,
        new QosPolicyListKey(qosId)).child(NodeList.class, new NodeListKey(nodeId)).child(
                PortList.class, new PortListKey(portId)).build();
    }

    private InstanceIdentifier<NodeList> getQosPolicyNodeIdentifier(Uuid qosId, String nodeId) {
        return InstanceIdentifier.builder(QosPolicyPortMap.class).child(QosPolicyList.class,
                new QosPolicyListKey(qosId)).child(NodeList.class,
                new NodeListKey(nodeId)).build();
    }

    private InstanceIdentifier<QosPolicyList> getQosPolicyIdentifier(Uuid qosId) {
        return InstanceIdentifier.builder(QosPolicyPortMap.class).child(QosPolicyList.class,
                new QosPolicyListKey(qosId)).build();
    }

    public void addPortToQosPolicyPortMapDS(Uuid qosId, String nodeId, String portId, String tpName) {
        InstanceIdentifier<PortList> portIid = getQosPolicyNodePortIdentifier(qosId, nodeId, portId);
        PortListBuilder plBuilder = new PortListBuilder().setPortId(portId).setTpName(tpName);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, portIid,
                    plBuilder.build());
        } catch (TransactionCommitFailedException ex) {
            LOG.error("addPortToQosPolicyPortMapDS: Failed to create Entry for qosId {} nodeId {} portId {}",
                    qosId, nodeId, portId);
        }
        LOG.trace("addPortToQosPolicyPortMapDS: Created entry for qosId {} nodeId {} portId {}", qosId, nodeId,
                portId);
    }

    List<PortList>  getPortListFromQosPolicyPortMapDS(String qosId, String nodeId) {
        InstanceIdentifier<NodeList> nodeListIid = getQosPolicyNodeIdentifier(new Uuid(qosId), nodeId);
        NodeList nodeList;
        try {
            nodeList = SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.OPERATIONAL, nodeListIid);
        } catch (ReadFailedException e) {
            LOG.warn("getPortListFromQosPolicyPortMapDS: nodeList is not present");
            return null;
        }
        if (nodeList != null) {
            LOG.trace("getPortListFromQosPolicyPortMapDS: nodeList = {}", nodeList);
            return nodeList.getPortList();
        }
        return null;
    }

    public void removeFromPortCache(Port port) {
        neutronPortMap.remove(port.getUuid());
    }

    public Port getNeutronPort(Uuid portUuid) {
        return neutronPortMap.get(portUuid);
    }

    public Port getNeutronPort(String portName) {
        if (portName != null) {
            return getNeutronPort(new Uuid(portName));
        } else {
            return null;
        }
    }

    public void addToNetworkCache(Network network) {
        neutronNetworkMap.put(network.getUuid(), network);
    }

    public void removeFromNetworkCache(Network network) {
        neutronNetworkMap.remove(network.getUuid());
    }

    public Network getNeutronNetwork(Uuid networkUuid) {
        return neutronNetworkMap.get(networkUuid);
    }

    public static BigInteger getDpnIdFromLowerLayerIf(String lowerLayerIf) {
        try {
            return new BigInteger(lowerLayerIf.substring(lowerLayerIf.indexOf(":") + 1, lowerLayerIf.lastIndexOf(":")));
        } catch (NullPointerException e) {
            return null;
        }
    }

    public static String getPortNumberFromLowerLayerIf(String lowerLayerIf) {
        try {
            return (lowerLayerIf.substring(lowerLayerIf.lastIndexOf(":") + 1));
        } catch (NullPointerException e) {
            return null;
        }
    }

    public int getIpVersions(Port port) {
        int versions = 0;
        for (FixedIps fixedIp : port.getFixedIps()) {
            if (fixedIp.getIpAddress().getIpv4Address() != null) {
                versions |= (1 << QosConstants.IPV4_ADDR_MASK_BIT);
            } else if (fixedIp.getIpAddress().getIpv6Address() != null) {
                versions |= (1 << QosConstants.IPV6_ADDR_MASK_BIT);
            }
        }
        return versions;
    }

    public boolean hasIpv4Addr(int versions) {
        if ((versions & (1 << QosConstants.IPV4_ADDR_MASK_BIT)) != 0) {
            return true;
        }
        return false;
    }

    public boolean hasIpv6Addr(int versions) {
        if ((versions & (1 << QosConstants.IPV6_ADDR_MASK_BIT)) != 0) {
            return true;
        }
        return false;
    }

    public boolean isBindServiceDone(Optional<Uuid> uuid) {
        if (uuid != null) {
            return qosServiceConfiguredPorts.contains(uuid.get());
        }
        return false;
    }

    public void removeInterfaceInQosConfiguredPorts(Optional<Uuid> uuid) {
        if (uuid != null) {
            qosServiceConfiguredPorts.remove(uuid.get());
        }
    }
}
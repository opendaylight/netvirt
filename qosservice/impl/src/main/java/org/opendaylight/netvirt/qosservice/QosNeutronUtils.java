/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
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
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRulesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.DscpmarkingRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
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
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalUtils;
    private final JobCoordinator jobCoordinator;

    @Inject
    public QosNeutronUtils(final QosEosHandler qosEosHandler, final INeutronVpnManager neutronVpnManager,
            final OdlInterfaceRpcService odlInterfaceRpcService, final DataBroker dataBroker,
            final IMdsalApiManager mdsalUtils, final JobCoordinator jobCoordinator) {
        this.qosEosHandler = qosEosHandler;
        this.neutronVpnManager = neutronVpnManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalUtils = mdsalUtils;
        this.jobCoordinator = jobCoordinator;
    }

    public void addToQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.put(qosPolicy.getUuid(),qosPolicy);
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

    @Nonnull
    public Collection<Network> getQosNetworks(Uuid qosUuid) {
        final ConcurrentMap<Uuid, Network> networkMap = qosNetworksMap.get(qosUuid);
        return networkMap != null ? networkMap.values() : Collections.emptyList();
    }

    @Nonnull
    public List<Uuid> getSubnetIdsFromNetworkId(Uuid networkId) {
        InstanceIdentifier<NetworkMap> networkMapId = InstanceIdentifier.builder(NetworkMaps.class)
                .child(NetworkMap.class, new NetworkMapKey(networkId)).build();
        Optional<NetworkMap> optionalNetworkMap = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION,
                networkMapId, dataBroker);
        return optionalNetworkMap.isPresent() ? optionalNetworkMap.get().getSubnetIdList() : Collections.emptyList();
    }

    @Nonnull
    protected List<Uuid> getPortIdsFromSubnetId(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetMapId = InstanceIdentifier
                .builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        Optional<Subnetmap> optionalSubnetmap = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION,
                subnetMapId,dataBroker);
        return optionalSubnetmap.isPresent() ? optionalSubnetmap.get().getPortList() : Collections.emptyList();
    }

    public void handleNeutronPortQosAdd(Port port, Uuid qosUuid) {
        LOG.debug("Handling Port add and QoS associated: port: {} qos: {}", port.getUuid(), qosUuid);

        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);

        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(),
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                // handle Bandwidth Limit Rules update
                if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                        && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                    setPortBandwidthLimits(port, qosPolicy.getBandwidthLimitRules().get(0), tx);
                }
                // handle DSCP Mark Rules update
                if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                        && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                    setPortDscpMarking(port, qosPolicy.getDscpmarkingRules().get(0));
                }
            })));
    }

    public void handleQosInterfaceAdd(Port port, Uuid qosUuid) {
        LOG.debug("Handling Port add and QoS associated: port: {} qos: {}", port.getUuid(), qosUuid);

        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);

        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () ->
                Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    // handle DSCP Mark Rules update
                    if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                            && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                        setPortDscpMarking(port, qosPolicy.getDscpmarkingRules().get(0));
                    }
                })));
    }

    public void handleNeutronPortQosUpdate(Port port, Uuid qosUuidNew, Uuid qosUuidOld) {
        LOG.debug("Handling Port QoS update: port: {} qosservice: {}", port.getUuid(), qosUuidNew);

        QosPolicy qosPolicyNew = qosPolicyMap.get(qosUuidNew);
        QosPolicy qosPolicyOld = qosPolicyMap.get(qosUuidOld);

        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(),
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                // handle Bandwidth Limit Rules update
                if (qosPolicyNew != null && qosPolicyNew.getBandwidthLimitRules() != null
                        && !qosPolicyNew.getBandwidthLimitRules().isEmpty()) {
                    setPortBandwidthLimits(port, qosPolicyNew.getBandwidthLimitRules().get(0), tx);
                } else {
                    if (qosPolicyOld != null && qosPolicyOld.getBandwidthLimitRules() != null
                            && !qosPolicyOld.getBandwidthLimitRules().isEmpty()) {
                        BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
                        setPortBandwidthLimits(port, bwLimitBuilder
                                .setMaxBurstKbps(BigInteger.ZERO)
                                .setMaxKbps(BigInteger.ZERO).build(), tx);
                    }
                }
                //handle DSCP Mark Rules update
                if (qosPolicyNew != null && qosPolicyNew.getDscpmarkingRules() != null
                        && !qosPolicyNew.getDscpmarkingRules().isEmpty()) {
                    setPortDscpMarking(port, qosPolicyNew.getDscpmarkingRules().get(0));
                } else {
                    if (qosPolicyOld != null && qosPolicyOld.getDscpmarkingRules() != null
                            && !qosPolicyOld.getDscpmarkingRules().isEmpty()) {
                        unsetPortDscpMark(port);
                    }
                }
            })));
    }

    public void handleNeutronPortQosRemove(Port port, Uuid qosUuid) {
        LOG.debug("Handling Port QoS removal: port: {} qosservice: {}", port.getUuid(), qosUuid);

        // check for network qosservice to apply
        Network network =  neutronVpnManager.getNeutronNetwork(port.getNetworkId());
        if (network != null && network.augmentation(QosNetworkExtension.class) != null) {
            Uuid networkQosUuid = network.augmentation(QosNetworkExtension.class).getQosPolicyId();
            if (networkQosUuid != null) {
                handleNeutronPortQosUpdate(port, networkQosUuid, qosUuid);
            }
        } else {
            QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);

            jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    // handle Bandwidth Limit Rules removal
                    if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                            && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                        BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
                        setPortBandwidthLimits(port, bwLimitBuilder
                                .setMaxBurstKbps(BigInteger.ZERO)
                                .setMaxKbps(BigInteger.ZERO).build(), tx);
                    }
                    // handle DSCP MArk Rules removal
                    if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                            && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                        unsetPortDscpMark(port);
                    }
                })));
        }
    }

    public void handleNeutronPortRemove(Port port, Uuid qosUuid) {
        LOG.debug("Handling Port removal and Qos associated: port: {} qos: {}", port.getUuid(), qosUuid);
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
        LOG.debug("Handling Port removal and Qos associated: port: {} qos: {}", port.getUuid(), qosUuid);
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);

        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                unsetPortDscpMark(port, intrf);
            }
            return Collections.emptyList();
        });
    }


    public void handleNeutronNetworkQosUpdate(Network network, Uuid qosUuid) {
        LOG.debug("Handling Network QoS update: net: {} qosservice: {}", network.getUuid(), qosUuid);
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);
        if (qosPolicy == null || (qosPolicy.getBandwidthLimitRules() == null
                || qosPolicy.getBandwidthLimitRules().isEmpty())
                && (qosPolicy.getDscpmarkingRules() == null
                || qosPolicy.getDscpmarkingRules().isEmpty())) {
            return;
        }
        List<Uuid> subnetIds = getSubnetIdsFromNetworkId(network.getUuid());
        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.augmentation(QosPortExtension.class) == null
                        || port.augmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(),
                        () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                            if (qosPolicy.getBandwidthLimitRules() != null
                                    && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                                setPortBandwidthLimits(port, qosPolicy.getBandwidthLimitRules().get(0), tx);
                            }
                            if (qosPolicy.getDscpmarkingRules() != null
                                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                                setPortDscpMarking(port, qosPolicy.getDscpmarkingRules().get(0));
                            }
                        })));
                }
            }
        }
    }

    public void handleNeutronNetworkQosRemove(Network network, Uuid qosUuid) {
        LOG.debug("Handling Network QoS removal: net: {} qosservice: {}", network.getUuid(), qosUuid);
        QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);

        List<Uuid> subnetIds = getSubnetIdsFromNetworkId(network.getUuid());
        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.augmentation(QosPortExtension.class) == null
                        || port.augmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(),
                        () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                            if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                                    && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                                BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
                                setPortBandwidthLimits(port, bwLimitBuilder
                                        .setMaxBurstKbps(BigInteger.ZERO)
                                        .setMaxKbps(BigInteger.ZERO).build(), tx);
                            }
                            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                                unsetPortDscpMark(port);
                            }
                        })));
                }
            }
        }
    }

    public void handleNeutronNetworkQosBwRuleRemove(Network network, BandwidthLimitRules zeroBwLimitRule) {
        LOG.debug("Handling Qos Bandwidth Rule Remove, net: {}", network.getUuid());

        List<Uuid> subnetIds = getSubnetIdsFromNetworkId(network.getUuid());

        for (Uuid subnetId: subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.augmentation(QosPortExtension.class) == null
                        || port.augmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> Collections.singletonList(
                            txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                                tx -> setPortBandwidthLimits(port, zeroBwLimitRule, tx))));
                }
            }
        }
    }

    public void handleNeutronNetworkQosDscpRuleRemove(Network network) {
        LOG.debug("Handling Qos Dscp Rule Remove, net: {}", network.getUuid());

        List<Uuid> subnetIds = getSubnetIdsFromNetworkId(network.getUuid());

        for (Uuid subnetId: subnetIds) {
            List<Uuid> portIds = getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = getNeutronPort(portId);
                if (port != null && (port.augmentation(QosPortExtension.class) == null
                        || port.augmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    jobCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                        unsetPortDscpMark(port);
                        return Collections.emptyList();
                    });
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void setPortBandwidthLimits(Port port, BandwidthLimitRules bwLimit, WriteTransaction writeConfigTxn) {
        if (!qosEosHandler.isQosClusterOwner()) {
            LOG.debug("Not Qos Cluster Owner. Ignoring setting bandwidth limits");
            return;
        }
        LOG.trace("Setting bandwidth limits {} on Port {}", port, bwLimit);

        BigInteger dpId = getDpnForInterface(port.getUuid().getValue());
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.info("DPN ID for interface {} not found", port.getUuid().getValue());
            return;
        }

        OvsdbBridgeRef bridgeRefEntry = getBridgeRefEntryFromOperDS(dpId);
        Optional<Node> bridgeNode = MDSALUtil.read(LogicalDatastoreType.OPERATIONAL,
                bridgeRefEntry.getValue().firstIdentifierOf(Node.class), dataBroker);


        TerminationPoint tp = SouthboundUtils.getTerminationPointByExternalId(bridgeNode.get(),
                port.getUuid().getValue());
        OvsdbTerminationPointAugmentation ovsdbTp = tp.augmentation(OvsdbTerminationPointAugmentation.class);

        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();
        tpAugmentationBuilder.setName(ovsdbTp.getName());
        tpAugmentationBuilder.setIngressPolicingRate(bwLimit.getMaxKbps().longValue());
        tpAugmentationBuilder.setIngressPolicingBurst(bwLimit.getMaxBurstKbps().longValue());

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.withKey(tp.key());
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());
        try {
            if (writeConfigTxn != null) {
                writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                        .create(NetworkTopology.class)
                        .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                        .child(Node.class, bridgeNode.get().key())
                        .child(TerminationPoint.class, new TerminationPointKey(tp.key())), tpBuilder.build(), true);
            } else {
                MDSALUtil.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                        .create(NetworkTopology.class)
                        .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                        .child(Node.class, bridgeNode.get().key())
                        .child(TerminationPoint.class, new TerminationPointKey(tp.key())), tpBuilder.build());
            }
        } catch (Exception e) {
            LOG.error("Failure while setting BwLimitRule {} to port {}", bwLimit, port);
            LOG.debug("Failure while setting BwLimitRule {} to port {} exception ", bwLimit, port, e);
        }

    }

    public void setPortDscpMarking(Port port, DscpmarkingRules dscpMark) {
        if (!qosEosHandler.isQosClusterOwner()) {
            LOG.debug("Not Qos Cluster Owner. Ignoring setting DSCP marking");
            return;
        }

        BigInteger dpnId = getDpnForInterface(port.getUuid().getValue());
        String ifName = port.getUuid().getValue();
        IpAddress ipAddress = port.getFixedIps().get(0).getIpAddress();
        Short dscpValue = dscpMark.getDscpMark();

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.info("DPN ID for interface {} not found. Cannot set dscp value {} on port {}",
                    port.getUuid().getValue(), dscpMark, port);
            return;
        }

        LOG.trace("Setting DSCP value {} on Port {}", dscpMark, port);
        //1. OF rules
        addFlow(dpnId, dscpValue, ifName, ipAddress, getInterfaceStateFromOperDS(ifName));
        if (qosServiceConfiguredPorts.add(port.getUuid())) {
            // bind qos service to interface
            bindservice(ifName);
        }
    }

    public void unsetPortDscpMark(Port port) {
        if (!qosEosHandler.isQosClusterOwner()) {
            LOG.debug("Not Qos Cluster Owner. Ignoring unsetting DSCP marking");
            return;
        }

        BigInteger dpnId = getDpnForInterface(port.getUuid().getValue());
        String ifName = port.getUuid().getValue();

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.trace("DPN ID for port {} not found. Cannot unset dscp value", port);
            return;
        }
        LOG.trace("Removing dscp marking rule from Port {}", port);

        //unbind service from interface
        unbindservice(ifName);
        // 1. OF
        removeFlow(dpnId, ifName, getInterfaceStateFromOperDS(ifName));
        qosServiceConfiguredPorts.remove(port.getUuid());
    }

    public void unsetPortDscpMark(Port port, Interface intrf) {

        BigInteger dpnId = getDpIdFromInterface(intrf);
        String ifName = port.getUuid().getValue();

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.error("Unable to retrieve DPN Id for interface {}. Cannot unset dscp value on port", ifName);
            return;
        }
        LOG.trace("Removing dscp marking rule from Port {}", port);
        unbindservice(ifName);
        removeFlow(dpnId, ifName, intrf);
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
            LOG.error("Could not retrieve DPN for interface {}", ifName);
            LOG.debug("Exception when getting DPN for interface {} exception ", ifName, e);
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
            return  bridgeEntry.getBridgeReference();
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

    public void removeStaleFlowEntry(Interface intrf) {
        List<MatchInfo> matches = new ArrayList<>();

        BigInteger dpnId = getDpIdFromInterface(intrf);

        Integer ifIndex = intrf.getIfIndex();
        matches.add(new MatchMetadata(MetaDataUtil.getLportTagMetaData(ifIndex), MetaDataUtil.METADATA_MASK_LPORT_TAG));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE,
                getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex),
                QosConstants.QOS_DEFAULT_FLOW_PRIORITY, "QoSRemoveFlow", 0, 0, NwConstants.COOKIE_QOS_TABLE,
                matches, null);
        mdsalUtils.removeFlow(flowEntity);
    }

    private void addFlow(BigInteger dpnId, Short dscpValue, String ifName, IpAddress ipAddress, Interface ifState) {
        if (ifState == null) {
            LOG.debug("Could not find the ifState for interface {}", ifName);
            return;
        }
        Integer ifIndex = ifState.getIfIndex();

        List<MatchInfo> matches = new ArrayList<>();
        if (ipAddress.getIpv4Address() != null) {
            matches.add(new MatchEthernetType(NwConstants.ETHTYPE_IPV4));
        } else {
            matches.add(new MatchEthernetType(NwConstants.ETHTYPE_IPV6));
        }
        matches.add(new MatchMetadata(MetaDataUtil.getLportTagMetaData(ifIndex), MetaDataUtil.METADATA_MASK_LPORT_TAG));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionSetFieldDscp(dscpValue));
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));

        List<InstructionInfo> instructions = Collections.singletonList(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE,
                getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex),
                QosConstants.QOS_DEFAULT_FLOW_PRIORITY, "QoSConfigFlow", 0, 0, NwConstants.COOKIE_QOS_TABLE,
                matches, instructions);
        mdsalUtils.installFlow(flowEntity);
    }

    private void removeFlow(BigInteger dpnId, String ifName, Interface ifState) {
        if (ifState == null) {
            LOG.debug("Could not find the ifState for interface {}", ifName);
            return;
        }
        Integer ifIndex = ifState.getIfIndex();

        mdsalUtils.removeFlow(dpnId, NwConstants.QOS_DSCP_TABLE,
                new FlowId(getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex)));
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
        return new BoundServicesBuilder().withKey(new BoundServicesKey(qosServiceIndex)).setServiceName(serviceName)
                .setServicePriority(qosServiceIndex).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    @Nonnull
    public static String getQosFlowId(short tableId, BigInteger dpId, int lportTag) {
        return String.valueOf(tableId) + dpId + lportTag;
    }

    public boolean portHasQosPolicy(Port port) {
        LOG.trace("checking qos policy for port: {}", port.getUuid());

        boolean isQosPolicy = port.augmentation(QosPortExtension.class) != null
                && port.augmentation(QosPortExtension.class).getQosPolicyId() != null;

        LOG.trace("portHasQosPolicy for  port: {} return value {}", port.getUuid(), isQosPolicy);
        return isQosPolicy;
    }

    @Nullable
    public QosPolicy getQosPolicy(Port port) {
        Uuid qosUuid = null;
        QosPolicy qosPolicy = null;

        if (port.augmentation(QosPortExtension.class) != null) {
            qosUuid = port.augmentation(QosPortExtension.class).getQosPolicyId();
        } else {
            Network network = neutronVpnManager.getNeutronNetwork(port.getNetworkId());

            if (network.augmentation(QosNetworkExtension.class) != null) {
                qosUuid = network.augmentation(QosNetworkExtension.class).getQosPolicyId();
            }
        }

        if (qosUuid != null) {
            qosPolicy = qosPolicyMap.get(qosUuid);
        }

        return qosPolicy;
    }

    public void addToPortCache(Port port) {
        neutronPortMap.put(port.getUuid(), port);
    }

    public void removeFromPortCache(Port port) {
        neutronPortMap.remove(port.getUuid());
    }

    public Port getNeutronPort(Uuid portUuid) {
        return neutronPortMap.get(portUuid);
    }

    public Port getNeutronPort(String portName) {
        return getNeutronPort(new Uuid(portName));
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

}

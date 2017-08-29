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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
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
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceOutput;
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
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class QosNeutronUtils {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronUtils.class);
    private static final String EXTERNAL_ID_INTERFACE_ID = "iface-id";
    public static ConcurrentHashMap<Uuid, QosPolicy> qosPolicyMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, ConcurrentHashMap<Uuid, Port>> qosPortsMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, ConcurrentHashMap<Uuid, Network>> qosNetworksMap = new ConcurrentHashMap<>();
    public static CopyOnWriteArraySet<Uuid> qosServiceConfiguredPorts = new CopyOnWriteArraySet<>();

    public static void addToQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.put(qosPolicy.getUuid(),qosPolicy);
    }

    public static void removeFromQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.remove(qosPolicy.getUuid());
    }

    public static void addToQosPortsCache(Uuid qosUuid, Port port) {
        if (qosPortsMap.containsKey(qosUuid)) {
            if (!qosPortsMap.get(qosUuid).containsKey(port.getUuid())) {
                qosPortsMap.get(qosUuid).put(port.getUuid(), port);
            }
        } else {
            ConcurrentHashMap<Uuid, Port> portMap = new ConcurrentHashMap<>();
            portMap.put(port.getUuid(), port);
            qosPortsMap.put(qosUuid, portMap);
        }
    }

    public static void removeFromQosPortsCache(Uuid qosUuid, Port port) {
        if (qosPortsMap.containsKey(qosUuid) && qosPortsMap.get(qosUuid).containsKey(port.getUuid())) {
            qosPortsMap.get(qosUuid).remove(port.getUuid(), port);
        }
    }

    public static void addToQosNetworksCache(Uuid qosUuid, Network network) {
        if (qosNetworksMap.containsKey(qosUuid)) {
            if (!qosNetworksMap.get(qosUuid).containsKey(network.getUuid())) {
                qosNetworksMap.get(qosUuid).put(network.getUuid(), network);
            }
        } else {

            ConcurrentHashMap<Uuid, Network> networkMap = new ConcurrentHashMap<>();
            networkMap.put(network.getUuid(), network);
            qosNetworksMap.put(qosUuid, networkMap);
        }
    }

    public static void removeFromQosNetworksCache(Uuid qosUuid, Network network) {
        if (qosNetworksMap.containsKey(qosUuid) && qosNetworksMap.get(qosUuid).containsKey(network.getUuid())) {
            qosNetworksMap.get(qosUuid).remove(network.getUuid(), network);
        }
    }

    public static List<Uuid> getSubnetIdsFromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<NetworkMap> networkMapId = InstanceIdentifier.builder(NetworkMaps.class)
                .child(NetworkMap.class, new NetworkMapKey(networkId)).build();
        Optional<NetworkMap> optionalNetworkMap = read(broker, LogicalDatastoreType.CONFIGURATION, networkMapId);
        return optionalNetworkMap.isPresent() ? optionalNetworkMap.get().getSubnetIdList() : null;
    }

    protected static List<Uuid> getPortIdsFromSubnetId(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetMapId = InstanceIdentifier
                .builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        Optional<Subnetmap> optionalSubnetmap = read(broker, LogicalDatastoreType.CONFIGURATION, subnetMapId);
        return optionalSubnetmap.isPresent() ? optionalSubnetmap.get().getPortList() : null;
    }

    public static void handleNeutronPortQosAdd(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                               IMdsalApiManager mdsalUtils, Port port, Uuid qosUuid) {
        LOG.trace("Handling Port add and QoS associated: port: {} qos: {}", port.getUuid(), qosUuid);

        QosPolicy qosPolicy = QosNeutronUtils.qosPolicyMap.get(qosUuid);

        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            WriteTransaction wrtConfigTxn = db.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            // handle Bandwidth Limit Rules update
            if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                    && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                setPortBandwidthLimits(db, odlInterfaceRpcService, port,
                        qosPolicy.getBandwidthLimitRules().get(0), wrtConfigTxn);
            }
            // handle DSCP Mark Rules update
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                setPortDscpMarking(db, odlInterfaceRpcService, mdsalUtils,
                        port, qosPolicy.getDscpmarkingRules().get(0));
            }
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    public static void handleNeutronPortQosUpdate(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                                  IMdsalApiManager mdsalUtils, Port port, Uuid qosUuidNew,
                                                  Uuid qosUuidOld) {
        LOG.trace("Handling Port QoS update: port: {} qosservice: {}", port.getUuid(), qosUuidNew);

        QosPolicy qosPolicyNew = QosNeutronUtils.qosPolicyMap.get(qosUuidNew);
        QosPolicy qosPolicyOld = QosNeutronUtils.qosPolicyMap.get(qosUuidOld);

        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            WriteTransaction wrtConfigTxn = db.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            // handle Bandwidth Limit Rules update
            if (qosPolicyNew != null && qosPolicyNew.getBandwidthLimitRules() != null
                    && !qosPolicyNew.getBandwidthLimitRules().isEmpty()) {
                setPortBandwidthLimits(db, odlInterfaceRpcService, port,
                        qosPolicyNew.getBandwidthLimitRules().get(0), wrtConfigTxn);
            } else {
                if (qosPolicyOld != null && qosPolicyOld.getBandwidthLimitRules() != null
                        && !qosPolicyOld.getBandwidthLimitRules().isEmpty()) {
                    BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
                    setPortBandwidthLimits(db, odlInterfaceRpcService, port, bwLimitBuilder
                            .setMaxBurstKbps(BigInteger.ZERO)
                            .setMaxKbps(BigInteger.ZERO).build(), wrtConfigTxn);
                }
            }
            //handle DSCP Mark Rules update
            if (qosPolicyNew != null && qosPolicyNew.getDscpmarkingRules() != null
                    && !qosPolicyNew.getDscpmarkingRules().isEmpty()) {
                setPortDscpMarking(db, odlInterfaceRpcService, mdsalUtils,
                        port, qosPolicyNew.getDscpmarkingRules().get(0));
            } else {
                if (qosPolicyOld != null && qosPolicyOld.getDscpmarkingRules() != null
                        && !qosPolicyOld.getDscpmarkingRules().isEmpty()) {
                    unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port);
                }
            }
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    public static void handleNeutronPortQosRemove(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                                  INeutronVpnManager neutronVpnManager, IMdsalApiManager mdsalUtils,
                                                  Port port, Uuid qosUuid) {
        LOG.trace("Handling Port QoS removal: port: {} qosservice: {}", port.getUuid(), qosUuid);

        // check for network qosservice to apply
        Network network =  neutronVpnManager.getNeutronNetwork(port.getNetworkId());
        if (network != null && network.getAugmentation(QosNetworkExtension.class) != null) {
            Uuid networkQosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
            if (networkQosUuid != null) {
                handleNeutronPortQosUpdate(db, odlInterfaceRpcService, mdsalUtils, port, networkQosUuid, qosUuid);
            }
        } else {
            QosPolicy qosPolicy = QosNeutronUtils.qosPolicyMap.get(qosUuid);

            final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            portDataStoreCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
                WriteTransaction wrtConfigTxn = db.newWriteOnlyTransaction();
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                // handle Bandwidth Limit Rules removal
                if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                        && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                    BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
                    setPortBandwidthLimits(db, odlInterfaceRpcService, port, bwLimitBuilder
                            .setMaxBurstKbps(BigInteger.ZERO)
                            .setMaxKbps(BigInteger.ZERO).build(), wrtConfigTxn);
                }
                // handle DSCP MArk Rules removal
                if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                        && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                    unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port);
                }
                futures.add(wrtConfigTxn.submit());
                return futures;
            });
        }
    }

    public static void handleNeutronPortRemove(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                               IMdsalApiManager mdsalUtils, Port port, Uuid qosUuid) {
        LOG.trace("Handling Port removal and Qos associated: port: {} qos: {}", port.getUuid(), qosUuid);
        QosPolicy qosPolicy = QosNeutronUtils.qosPolicyMap.get(qosUuid);

        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            WriteTransaction wrtConfigTxn = db.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            //check if any DSCP rule in the policy
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port);
            }
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    public static void handleNeutronPortRemove(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                               IMdsalApiManager mdsalUtils, Port port, Uuid qosUuid, Interface intrf) {
        LOG.trace("Handling Port removal and Qos associated: port: {} qos: {}", port.getUuid(), qosUuid);
        QosPolicy qosPolicy = QosNeutronUtils.qosPolicyMap.get(qosUuid);

        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            WriteTransaction wrtConfigTxn = db.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port, intrf);
            }
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }


    public static void handleNeutronNetworkQosUpdate(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                                     INeutronVpnManager neutronVpnManager,
                                                     IMdsalApiManager mdsalUtils,
                                                     Network network, Uuid qosUuid) {
        LOG.trace("Handling Network QoS update: net: {} qosservice: {}", network.getUuid(), qosUuid);
        QosPolicy qosPolicy = QosNeutronUtils.qosPolicyMap.get(qosUuid);
        if (qosPolicy == null || (qosPolicy.getBandwidthLimitRules() == null
                || qosPolicy.getBandwidthLimitRules().isEmpty())
                && (qosPolicy.getDscpmarkingRules() == null
                || qosPolicy.getDscpmarkingRules().isEmpty())) {
            return;
        }
        List<Uuid> subnetIds = QosNeutronUtils.getSubnetIdsFromNetworkId(db, network.getUuid());
        if (subnetIds != null) {
            for (Uuid subnetId : subnetIds) {
                List<Uuid> portIds = QosNeutronUtils.getPortIdsFromSubnetId(db, subnetId);
                if (portIds != null) {
                    for (Uuid portId : portIds) {
                        Port port = neutronVpnManager.getNeutronPort(portId);
                        if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                                || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                            final DataStoreJobCoordinator portDataStoreCoordinator =
                                    DataStoreJobCoordinator.getInstance();
                            portDataStoreCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                                WriteTransaction wrtConfigTxn = db.newWriteOnlyTransaction();
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                                        && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                                    setPortBandwidthLimits(db, odlInterfaceRpcService, port,
                                            qosPolicy.getBandwidthLimitRules().get(0), wrtConfigTxn);
                                }
                                if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                                        && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                                    setPortDscpMarking(db, odlInterfaceRpcService, mdsalUtils,
                                            port, qosPolicy.getDscpmarkingRules().get(0));
                                }
                                futures.add(wrtConfigTxn.submit());
                                return futures;
                            });
                        }
                    }
                }
            }
        }
    }

    public static void handleNeutronNetworkQosRemove(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                                     INeutronVpnManager neutronVpnManager,
                                                     IMdsalApiManager mdsalUtils,
                                                     Network network, Uuid qosUuid) {
        LOG.trace("Handling Network QoS removal: net: {} qosservice: {}", network.getUuid(), qosUuid);
        QosPolicy qosPolicy = QosNeutronUtils.qosPolicyMap.get(qosUuid);

        List<Uuid> subnetIds = QosNeutronUtils.getSubnetIdsFromNetworkId(db, network.getUuid());
        if (subnetIds != null) {
            for (Uuid subnetId : subnetIds) {
                List<Uuid> portIds = QosNeutronUtils.getPortIdsFromSubnetId(db, subnetId);
                if (portIds != null) {
                    for (Uuid portId : portIds) {
                        Port port = neutronVpnManager.getNeutronPort(portId);
                        if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                                || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                            final DataStoreJobCoordinator portDataStoreCoordinator =
                                    DataStoreJobCoordinator.getInstance();
                            portDataStoreCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                                WriteTransaction wrtConfigTxn = db.newWriteOnlyTransaction();
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                                        && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                                    BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
                                    setPortBandwidthLimits(db, odlInterfaceRpcService, port, bwLimitBuilder
                                            .setMaxBurstKbps(BigInteger.ZERO)
                                            .setMaxKbps(BigInteger.ZERO).build(), null);
                                }
                                if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                                        && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                                    unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port);
                                }
                                futures.add(wrtConfigTxn.submit());
                                return futures;
                            });
                        }
                    }
                }
            }
        }
    }

    public static void handleNeutronNetworkQosBwRuleRemove(DataBroker dataBroker,
                                                           OdlInterfaceRpcService odlInterfaceRpcService,
                                                           INeutronVpnManager neutronVpnManager,
                                                           Network network,
                                                           BandwidthLimitRules zeroBwLimitRule) {
        LOG.trace("Handling Qos Bandwidth Rule Remove, net: {}", network.getUuid());

        List<Uuid> subnetIds = QosNeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());

        for (Uuid subnetId: subnetIds) {
            List<Uuid> portIds = QosNeutronUtils.getPortIdsFromSubnetId(dataBroker, subnetId);
            for (Uuid portId : portIds) {
                Port port = neutronVpnManager.getNeutronPort(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                        || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    final DataStoreJobCoordinator portDataStoreCoordinator =
                            DataStoreJobCoordinator.getInstance();
                    portDataStoreCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                        WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        setPortBandwidthLimits(dataBroker, odlInterfaceRpcService, port,
                                zeroBwLimitRule, wrtConfigTxn);
                        futures.add(wrtConfigTxn.submit());
                        return futures;
                    });
                }
            }
        }
    }

    public static void handleNeutronNetworkQosDscpRuleRemove(DataBroker dataBroker,
                                                             OdlInterfaceRpcService odlInterfaceRpcService,
                                                             INeutronVpnManager neutronVpnManager,
                                                             IMdsalApiManager mdsalUtils,
                                                             Network network) {
        LOG.trace("Handling Qos Dscp Rule Remove, net: {}", network.getUuid());

        List<Uuid> subnetIds = QosNeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());

        for (Uuid subnetId: subnetIds) {
            List<Uuid> portIds = QosNeutronUtils.getPortIdsFromSubnetId(dataBroker, subnetId);
            for (Uuid portId : portIds) {
                Port port = neutronVpnManager.getNeutronPort(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                        || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    final DataStoreJobCoordinator portDataStoreCoordinator =
                            DataStoreJobCoordinator.getInstance();
                    portDataStoreCoordinator.enqueueJob("QosPort-" + portId.getValue(), () -> {
                        WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        unsetPortDscpMark(dataBroker, odlInterfaceRpcService, mdsalUtils, port);
                        futures.add(wrtConfigTxn.submit());
                        return futures;
                    });
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void setPortBandwidthLimits(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                              Port port, BandwidthLimitRules bwLimit,
                                              WriteTransaction writeConfigTxn) {
        LOG.trace("Setting bandwidth limits {} on Port {}", port, bwLimit);

        BigInteger dpId = getDpnForInterface(odlInterfaceRpcService, port.getUuid().getValue());
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.info("DPN ID for interface {} not found", port.getUuid().getValue());
            return;
        }
        if (!QosAlertEosHandler.isQosClusterOwner()) {
            LOG.trace("Not Qos Cluster Owner. Ignoring setting bandwidth limits");
            return;
        }

        OvsdbBridgeRef bridgeRefEntry = getBridgeRefEntryFromOperDS(dpId, db);
        Optional<Node> bridgeNode = MDSALUtil.read(LogicalDatastoreType.OPERATIONAL,
                bridgeRefEntry.getValue().firstIdentifierOf(Node.class), db);


        TerminationPoint tp = SouthboundUtils.getTerminationPointByExternalId(bridgeNode.get(),
                port.getUuid().getValue());
        OvsdbTerminationPointAugmentation ovsdbTp = tp.getAugmentation(OvsdbTerminationPointAugmentation.class);

        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();
        tpAugmentationBuilder.setName(ovsdbTp.getName());
        tpAugmentationBuilder.setIngressPolicingRate(bwLimit.getMaxKbps().longValue());
        tpAugmentationBuilder.setIngressPolicingBurst(bwLimit.getMaxBurstKbps().longValue());

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setKey(tp.getKey());
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());
        try {
            if (writeConfigTxn != null) {
                writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                        .create(NetworkTopology.class)
                        .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                        .child(Node.class, bridgeNode.get().getKey())
                        .child(TerminationPoint.class, new TerminationPointKey(tp.getKey())), tpBuilder.build());
            } else {
                MDSALUtil.syncUpdate(db, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                        .create(NetworkTopology.class)
                        .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                        .child(Node.class, bridgeNode.get().getKey())
                        .child(TerminationPoint.class, new TerminationPointKey(tp.getKey())), tpBuilder.build());
            }
        } catch (Exception e) {
            LOG.error("Failure while setting BwLimitRule{} to port{}", bwLimit, port, e);
        }

    }

    public static void setPortDscpMarking(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                          IMdsalApiManager mdsalUtils,
                                          Port port, DscpmarkingRules dscpMark) {

        LOG.trace("Setting DSCP value {} on Port {}", port, dscpMark);

        BigInteger dpnId = getDpnForInterface(odlInterfaceRpcService, port.getUuid().getValue());
        String ifName = port.getUuid().getValue();
        IpAddress ipAddress = port.getFixedIps().get(0).getIpAddress();
        Short dscpValue = dscpMark.getDscpMark();

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.info("DPN ID for interface {} not found", port.getUuid().getValue());
            return;
        }
        if (!QosAlertEosHandler.isQosClusterOwner()) {
            LOG.trace("Not Qos Cluster Owner. Ignoring setting DSCP marking");
            return;
        }

        //1. OF rules
        syncFlow(db, dpnId, NwConstants.ADD_FLOW, mdsalUtils, dscpValue, ifName, ipAddress);
        if (qosServiceConfiguredPorts.add(port.getUuid())) {
            // bind qos service to interface
            bindservice(db, ifName);
        }
    }




    public static void unsetPortDscpMark(DataBroker dataBroker, OdlInterfaceRpcService odlInterfaceRpcService,
                                         IMdsalApiManager mdsalUtils, Port port) {
        LOG.trace("Removing dscp marking rule from Port {}", port);

        BigInteger dpnId = getDpnForInterface(odlInterfaceRpcService, port.getUuid().getValue());
        String ifName = port.getUuid().getValue();

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.info("DPN ID for port {} not found", port);
            return;
        }
        if (!QosAlertEosHandler.isQosClusterOwner()) {
            LOG.trace("Not Qos Cluster Owner. Ignoring unsetting DSCP marking");
            return;
        }
        IpAddress ipAddress = port.getFixedIps().get(0).getIpAddress();

        //unbind service from interface
        unbindservice(dataBroker, ifName);
        // 1. OF
        syncFlow(dataBroker, dpnId, NwConstants.DEL_FLOW, mdsalUtils, (short) 0, ifName, ipAddress);
        qosServiceConfiguredPorts.remove(port.getUuid());
    }

    public static void unsetPortDscpMark(DataBroker dataBroker, OdlInterfaceRpcService odlInterfaceRpcService,
                                         IMdsalApiManager mdsalUtils, Port port, Interface intrf) {
        LOG.trace("Removing dscp marking rule from Port {}", port);

        BigInteger dpnId = getDpIdFromInterface(intrf);
        String ifName = port.getUuid().getValue();

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.error("Unable to retrieve DPN Id for interface {}", ifName);
            return;
        }
        IpAddress ipAddress = port.getFixedIps().get(0).getIpAddress();
        unbindservice(dataBroker, ifName);
        syncFlow(dataBroker, dpnId, NwConstants.DEL_FLOW, mdsalUtils, (short) 0, ifName, ipAddress, intrf);
    }

    private static BigInteger getDpIdFromInterface(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                                                           .interfaces.rev140508.interfaces.state.Interface ifState) {
        String lowerLayerIf = ifState.getLowerLayerIf().get(0);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
        return BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
    }

    public static BigInteger getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        BigInteger nodeId = BigInteger.ZERO;
        try {
            GetDpidFromInterfaceInput
                    dpIdInput = new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>>
                    dpIdOutput = interfaceManagerRpcService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if (dpIdResult.isSuccessful()) {
                nodeId = dpIdResult.getResult().getDpid();
            } else {
                LOG.error("Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting dpn for interface {}", ifName,  e);
        }
        return nodeId;
    }

    private static BridgeEntry getBridgeEntryFromConfigDS(BigInteger dpnId,
                                                          DataBroker dataBroker) {
        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpnId);
        InstanceIdentifier<BridgeEntry> bridgeEntryInstanceIdentifier = getBridgeEntryIdentifier(bridgeEntryKey);
        LOG.debug("Trying to retrieve bridge entry from config for Id: {}", bridgeEntryInstanceIdentifier);
        return getBridgeEntryFromConfigDS(bridgeEntryInstanceIdentifier,
                dataBroker);
    }

    private static BridgeEntry getBridgeEntryFromConfigDS(InstanceIdentifier<BridgeEntry> bridgeEntryInstanceIdentifier,
                                                          DataBroker dataBroker) {
        Optional<BridgeEntry> bridgeEntryOptional =
                read(LogicalDatastoreType.CONFIGURATION, bridgeEntryInstanceIdentifier, dataBroker);
        if (!bridgeEntryOptional.isPresent()) {
            return null;
        }
        return bridgeEntryOptional.get();
    }

    private static BridgeRefEntry getBridgeRefEntryFromOperDS(InstanceIdentifier<BridgeRefEntry> dpnBridgeEntryIid,
                                                              DataBroker dataBroker) {
        Optional<BridgeRefEntry> bridgeRefEntryOptional =
                read(LogicalDatastoreType.OPERATIONAL, dpnBridgeEntryIid, dataBroker);
        if (!bridgeRefEntryOptional.isPresent()) {
            return null;
        }
        return bridgeRefEntryOptional.get();
    }

    private static OvsdbBridgeRef getBridgeRefEntryFromOperDS(BigInteger dpId,
                                                              DataBroker dataBroker) {
        BridgeRefEntryKey bridgeRefEntryKey = new BridgeRefEntryKey(dpId);
        InstanceIdentifier<BridgeRefEntry> bridgeRefEntryIid = getBridgeRefEntryIdentifier(bridgeRefEntryKey);
        BridgeRefEntry bridgeRefEntry = getBridgeRefEntryFromOperDS(bridgeRefEntryIid, dataBroker);
        if (bridgeRefEntry == null) {
            // bridge ref entry will be null if the bridge is disconnected from controller.
            // In that case, fetch bridge reference from bridge interface entry config DS
            BridgeEntry bridgeEntry = getBridgeEntryFromConfigDS(dpId, dataBroker);
            if (bridgeEntry == null) {
                return null;
            }
            return  bridgeEntry.getBridgeReference();
        }
        return bridgeRefEntry.getBridgeReference();
    }

    private static InstanceIdentifier<BridgeRefEntry> getBridgeRefEntryIdentifier(BridgeRefEntryKey bridgeRefEntryKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<BridgeRefEntry> bridgeRefEntryInstanceIdentifierBuilder =
                InstanceIdentifier.builder(BridgeRefInfo.class)
                        .child(BridgeRefEntry.class, bridgeRefEntryKey);
        return bridgeRefEntryInstanceIdentifierBuilder.build();
    }

    private static InstanceIdentifier<BridgeEntry> getBridgeEntryIdentifier(BridgeEntryKey bridgeEntryKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<BridgeEntry> bridgeEntryIdBuilder =
                InstanceIdentifier.builder(BridgeInterfaceInfo.class).child(BridgeEntry.class, bridgeEntryKey);
        return bridgeEntryIdBuilder.build();
    }

    public  static  void removeStaleFlowEntry(DataBroker db, IMdsalApiManager mdsalUtils,
                                                OdlInterfaceRpcService odlInterfaceRpcService, Interface intrf) {
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

    private static void syncFlow(DataBroker db, BigInteger dpnId, int addOrRemove,
                                 IMdsalApiManager mdsalUtils, Short dscpValue,
                                 String ifName, IpAddress ipAddress) {
        Interface ifState = getInterfaceStateFromOperDS(ifName, db);
        syncFlow(db, dpnId, addOrRemove, mdsalUtils, dscpValue, ifName, ipAddress, ifState);
    }

    private static void syncFlow(DataBroker db, BigInteger dpnId, int addOrRemove,
                                 IMdsalApiManager mdsalUtils, Short dscpValue,
                                 String ifName, IpAddress ipAddress, Interface ifState) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();

        if (ifState == null) {
            LOG.trace("Could not find the ifState for interface {}", ifName);
            return;
        }
        Integer ifIndex = ifState.getIfIndex();

        if (ipAddress.getIpv4Address() != null) {
            matches.add(new MatchEthernetType(NwConstants.ETHTYPE_IPV4));
        } else {
            matches.add(new MatchEthernetType(NwConstants.ETHTYPE_IPV6));
        }
        matches.add(new MatchMetadata(MetaDataUtil.getLportTagMetaData(ifIndex), MetaDataUtil.METADATA_MASK_LPORT_TAG));


        if (addOrRemove == NwConstants.ADD_FLOW) {
            actionsInfos.add(new ActionSetFieldDscp(dscpValue));
            actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));

            instructions.add(new InstructionApplyActions(actionsInfos));
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE,
                    getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex),
                    QosConstants.QOS_DEFAULT_FLOW_PRIORITY, "QoSConfigFlow", 0, 0, NwConstants.COOKIE_QOS_TABLE,
                    matches, instructions);
            mdsalUtils.installFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE,
                    getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex),
                    QosConstants.QOS_DEFAULT_FLOW_PRIORITY, "QoSRemoveFlow", 0, 0, NwConstants.COOKIE_QOS_TABLE,
                    matches, null);
            mdsalUtils.removeFlow(flowEntity);
        }
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface getInterfaceStateFromOperDS(
            String interfaceName, DataBroker dataBroker) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                createInterfaceStateInstanceIdentifier(interfaceName);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .ietf.interfaces.rev140508.interfaces.state.Interface> ifStateOptional = MDSALUtil
                .read(dataBroker, LogicalDatastoreType.OPERATIONAL, ifStateId);
        if (ifStateOptional.isPresent()) {
            return ifStateOptional.get();
        }
        return null;
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> createInterfaceStateInstanceIdentifier(
            String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder = InstanceIdentifier
                .builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                                .ietf.interfaces.rev140508.interfaces.state.Interface.class,
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                                .ietf.interfaces.rev140508.interfaces.state.InterfaceKey(
                                interfaceName));
        return idBuilder.build();
    }

    public static void bindservice(DataBroker dataBroker, String ifName) {
        int priority = QosConstants.QOS_DEFAULT_FLOW_PRIORITY;
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.QOS_DSCP_TABLE, ++instructionKey));
        short qosServiceIndex = ServiceIndex.getIndex(NwConstants.QOS_SERVICE_NAME, NwConstants.QOS_SERVICE_INDEX);

        BoundServices serviceInfo = QosNeutronUtils.getBoundServices(
                String.format("%s.%s", "qos", ifName), qosServiceIndex,
                priority, NwConstants.COOKIE_QOS_TABLE, instructions);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                QosNeutronUtils.buildServiceId(ifName, qosServiceIndex),
                serviceInfo);
    }

    public static void unbindservice(DataBroker dataBroker, String ifName) {
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, QosNeutronUtils.buildServiceId(ifName,
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

    public static String getQosFlowId(short tableId, BigInteger dpId, int lportTag) {
        return new StringBuffer().append(tableId).append(dpId).append(lportTag).toString();
    }

    public static String getPortNumberForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        GetPortFromInterfaceInput portNumberInput = new GetPortFromInterfaceInputBuilder().setIntfName(ifName).build();
        Future<RpcResult<GetPortFromInterfaceOutput>> portNumberOutput =
                interfaceManagerRpcService.getPortFromInterface(portNumberInput);
        try {
            RpcResult<GetPortFromInterfaceOutput> portResult = portNumberOutput.get();
            if (portResult.isSuccessful()) {
                return portResult.getResult().getPortno().toString();
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting port for interface", e);
        }
        return null;
    }

    public static boolean portHasQosPolicy(INeutronVpnManager neutronVpnManager, Port port) {
        Uuid qosUuid = null;
        boolean isQosPolicy = false;

        LOG.trace("checking qos policy for port: {}", port.getUuid());

        if (port.getAugmentation(QosPortExtension.class) != null) {
            qosUuid = port.getAugmentation(QosPortExtension.class).getQosPolicyId();
        }
        if (qosUuid != null) {
            isQosPolicy = true;
        }

        LOG.trace("portHasQosPolicy for  port: {} return value {}", port.getUuid(), isQosPolicy);
        return (isQosPolicy);
    }

    public static boolean hasBandwidthLimitRule(INeutronVpnManager neutronVpnManager, Port port) {
        Uuid qosUuid = null;
        boolean bwLimitRule = false;

        LOG.trace("checking bandwidth limit rule for  port: {}", port.getUuid());

        if (port.getAugmentation(QosPortExtension.class) != null) {
            qosUuid = port.getAugmentation(QosPortExtension.class).getQosPolicyId();
        } else {
            Network network = neutronVpnManager.getNeutronNetwork(port.getNetworkId());

            if (network.getAugmentation(QosNetworkExtension.class) != null) {
                qosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
            }
        }

        if (qosUuid != null) {
            QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);
            if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                    && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                bwLimitRule = true;
            }
        }

        LOG.trace("Bandwidth limit rule for  port: {} return value {}", port.getUuid(), bwLimitRule);
        return (bwLimitRule);
    }

    public static boolean hasBandwidthLimitRule(Network network) {
        boolean bwLimitRule = false;

        LOG.trace("checking bandwidth limit rule for  network: {}", network.getUuid());

        if (network.getAugmentation(QosNetworkExtension.class) != null) {
            Uuid qosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();

            if (qosUuid != null) {
                QosPolicy qosPolicy = qosPolicyMap.get(qosUuid);
                if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                        && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                    bwLimitRule = true;
                }
            }
        }

        LOG.trace("Bandwidth limit rule for  network: {} return value {}", network.getUuid(), bwLimitRule);
        return (bwLimitRule);
    }

    public static QosPolicy getQosPolicy(INeutronVpnManager neutronVpnManager, Port port) {
        Uuid qosUuid = null;
        QosPolicy qosPolicy = null;

        if (port.getAugmentation(QosPortExtension.class) != null) {
            qosUuid = port.getAugmentation(QosPortExtension.class).getQosPolicyId();
        } else {
            Network network = neutronVpnManager.getNeutronNetwork(port.getNetworkId());

            if (network.getAugmentation(QosNetworkExtension.class) != null) {
                qosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
            }
        }

        if (qosUuid != null) {
            qosPolicy = qosPolicyMap.get(qosUuid);
        }

        return (qosPolicy);
    }



    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        try {
            return tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private static <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
                                                           InstanceIdentifier<T> path, DataBroker broker) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

}

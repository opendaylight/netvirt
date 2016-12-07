/*
 * Copyright (c) 2016 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.*;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldDscp;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.ServiceIndex;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceExternalIds;
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


public class NeutronQosUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronQosUtils.class);
    private static final String EXTERNAL_ID_INTERFACE_ID = "iface-id";

    public static void handleNeutronPortQosAdd(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                               IMdsalApiManager mdsalUtils, Port port, Uuid qosUuid) {
        LOG.trace("Handling Port add and QoS associated: port: {} qos: {}", port.getUuid(), qosUuid);

        QosPolicy qosPolicy = NeutronvpnUtils.qosPolicyMap.get(qosUuid);

        // handle Bandwidth Limit Rules update
        if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
            setPortBandwidthLimits(db, odlInterfaceRpcService, port,
                    qosPolicy.getBandwidthLimitRules().get(0));
        }
        // handle DSCP Mark Rules update
        if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                && !qosPolicy.getDscpmarkingRules().isEmpty()) {
            setPortDscpMarking(db, odlInterfaceRpcService, mdsalUtils,
                    port, qosPolicy.getDscpmarkingRules().get(0));
        }

    }

    public static void handleNeutronPortQosUpdate(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                                  IMdsalApiManager mdsalUtils, Port port, Uuid qosUuidNew,
                                                  Uuid qosUuidOld) {
        LOG.trace("Handling Port QoS update: port: {} qos: {}", port.getUuid(), qosUuidNew);

        QosPolicy qosPolicyNew = NeutronvpnUtils.qosPolicyMap.get(qosUuidNew);
        QosPolicy qosPolicyOld = NeutronvpnUtils.qosPolicyMap.get(qosUuidOld);

        // handle Bandwidth Limit Rules update
        if (qosPolicyNew != null && qosPolicyNew.getBandwidthLimitRules() != null
                && !qosPolicyNew.getBandwidthLimitRules().isEmpty()) {
            setPortBandwidthLimits(db, odlInterfaceRpcService, port,
                    qosPolicyNew.getBandwidthLimitRules().get(0));
        }

        // handle DSCP Mark Rules update
        if (qosPolicyNew != null && qosPolicyNew.getDscpmarkingRules() != null
                && !qosPolicyNew.getDscpmarkingRules().isEmpty()) {
            setPortDscpMarking(db, odlInterfaceRpcService, mdsalUtils,
                    port, qosPolicyNew.getDscpmarkingRules().get(0));
        } else {
            if (qosPolicyOld != null && qosPolicyOld.getDscpmarkingRules() != null
                    && !qosPolicyOld.getDscpmarkingRules().isEmpty()){
                unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port);
            }
        }
    }

    public static void handleNeutronPortQosRemove(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                                  IMdsalApiManager mdsalUtils, Port port, Uuid qosUuid) {
        LOG.trace("Handling Port QoS removal: port: {} qos: {}", port.getUuid(), qosUuid);

        // handle Bandwidth Limit Rules removal
        QosPolicy qosPolicy = NeutronvpnUtils.qosPolicyMap.get(qosUuid);
        if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
            BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
            setPortBandwidthLimits(db, odlInterfaceRpcService, port,
                    bwLimitBuilder.setMaxBurstKbps(BigInteger.ZERO).setMaxKbps(BigInteger.ZERO).build());
        }

        // check for network qos to apply
        Network network = NeutronvpnUtils.getNeutronNetwork(db, port.getNetworkId());
        if (network != null && network.getAugmentation(QosNetworkExtension.class) != null) {
            Uuid networkQosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
            if (networkQosUuid != null) {
                handleNeutronPortQosUpdate(db, odlInterfaceRpcService, mdsalUtils, port, networkQosUuid, qosUuid);
            }
        } else {
            // handle DSCP MArk Rules removal
            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port);
            }
        }
    }

    public static void handleNeutronPortRemove(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                               IMdsalApiManager mdsalUtils, Port port, Uuid qosUuid) {
        LOG.trace("Handling Port removal and Qos associated: port: {} qos: {}", port.getUuid(), qosUuid);
        QosPolicy qosPolicy = NeutronvpnUtils.qosPolicyMap.get(qosUuid);

        //check if any DSCP rule in the policy
        if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                && !qosPolicy.getDscpmarkingRules().isEmpty()) {
            unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port);
        }
    }

    public static void handleNeutronNetworkQosUpdate(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                                     IMdsalApiManager mdsalUtils, Network network, Uuid qosUuid) {
        LOG.trace("Handling Network QoS update: net: {} qos: {}", network.getUuid(), qosUuid);
        QosPolicy qosPolicy = NeutronvpnUtils.qosPolicyMap.get(qosUuid);
        if (qosPolicy == null || ((qosPolicy.getBandwidthLimitRules() == null
                || qosPolicy.getBandwidthLimitRules().isEmpty()) &&
                (qosPolicy.getDscpmarkingRules() == null ||
                        qosPolicy.getDscpmarkingRules().isEmpty()))) {
            return;
        }

        List<Uuid> subnetIds = NeutronvpnUtils.getSubnetIdsFromNetworkId(db, network.getUuid());
        if (subnetIds != null) {
            for (Uuid subnetId : subnetIds) {
                List<Uuid> portIds = NeutronvpnUtils.getPortIdsFromSubnetId(db, subnetId);
                if (portIds != null) {
                    for (Uuid portId : portIds) {
                        Port port = NeutronvpnUtils.portMap.get(portId);
                        if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                                || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                            if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                                    && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                                setPortBandwidthLimits(db, odlInterfaceRpcService, port,
                                        qosPolicy.getBandwidthLimitRules().get(0));
                            }
                            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                                setPortDscpMarking(db, odlInterfaceRpcService, mdsalUtils,
                                        port, qosPolicy.getDscpmarkingRules().get(0));
                            }
                        }
                    }
                }
            }
        }
    }

    public static void handleNeutronNetworkQosRemove(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                                     IMdsalApiManager mdsalUtils, Network network, Uuid qosUuid) {
        LOG.trace("Handling Network QoS removal: net: {} qos: {}", network.getUuid(), qosUuid);
        QosPolicy qosPolicy = NeutronvpnUtils.qosPolicyMap.get(qosUuid);

        List<Uuid> subnetIds = NeutronvpnUtils.getSubnetIdsFromNetworkId(db, network.getUuid());
        if (subnetIds != null) {
            for (Uuid subnetId : subnetIds) {
                List<Uuid> portIds = NeutronvpnUtils.getPortIdsFromSubnetId(db, subnetId);
                if (portIds != null) {
                    for (Uuid portId : portIds) {
                        Port port = NeutronvpnUtils.portMap.get(portId);
                        if (port != null && (port.getAugmentation(QosPortExtension.class) == null
                                || port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                            BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
                            if (qosPolicy != null && qosPolicy.getBandwidthLimitRules() != null
                                    && !qosPolicy.getBandwidthLimitRules().isEmpty()) {
                                setPortBandwidthLimits(db, odlInterfaceRpcService, port,
                                        bwLimitBuilder.setMaxBurstKbps(BigInteger.ZERO)
                                                .setMaxKbps(BigInteger.ZERO).build());
                            }
                            if (qosPolicy != null && qosPolicy.getDscpmarkingRules() != null
                                    && !qosPolicy.getDscpmarkingRules().isEmpty()) {
                                unsetPortDscpMark(db, odlInterfaceRpcService, mdsalUtils, port);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void handleNeutronNetworkQosBwRuleRemove(DataBroker dataBroker,
                                                           OdlInterfaceRpcService odlInterfaceRpcService,
                                                           Network network,
                                                           BandwidthLimitRules zeroBwLimitRule) {
        LOG.trace("Handling Qos Bandwidth Rule Remove, net: {}", network.getUuid());

        List<Uuid> subnetIds = NeutronvpnUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());

        for (Uuid subnetId: subnetIds) {
            List<Uuid> portIds = NeutronvpnUtils.getPortIdsFromSubnetId(dataBroker, subnetId);
            for (Uuid portId : portIds) {
                Port port = NeutronvpnUtils.portMap.get(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null ||
                        port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    setPortBandwidthLimits(dataBroker, odlInterfaceRpcService, port, zeroBwLimitRule);
                }
            }
        }
    }

    public static void handleNeutronNetworkQosDscpRuleRemove(DataBroker dataBroker,
                                                              OdlInterfaceRpcService odlInterfaceRpcService,
                                                              IMdsalApiManager mdsalUtils,
                                                              Network network) {
        LOG.trace("Handling Qos Dscp Rule Remove, net: {}", network.getUuid());

        List<Uuid> subnetIds = NeutronvpnUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());

        for (Uuid subnetId: subnetIds) {
            List<Uuid> portIds = NeutronvpnUtils.getPortIdsFromSubnetId(dataBroker, subnetId);
            for (Uuid portId : portIds) {
                Port port = NeutronvpnUtils.portMap.get(portId);
                if (port != null && (port.getAugmentation(QosPortExtension.class) == null ||
                        port.getAugmentation(QosPortExtension.class).getQosPolicyId() == null)) {
                    unsetPortDscpMark(dataBroker, odlInterfaceRpcService, mdsalUtils, port);
                }
            }
        }
    }

    public static void setPortBandwidthLimits(DataBroker db, OdlInterfaceRpcService odlInterfaceRpcService,
                                              Port port, BandwidthLimitRules bwLimit) {
        LOG.trace("Setting bandwidth limits {} on Port {}", port, bwLimit);

        BigInteger dpId = getDpnForInterface(odlInterfaceRpcService, port.getUuid().getValue());
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.info("DPN ID for interface {} not found", port.getUuid().getValue());
            return;
        }

        OvsdbBridgeRef bridgeRefEntry = getBridgeRefEntryFromOperDS(dpId, db);
        Optional<Node> bridgeNode = MDSALUtil.read(LogicalDatastoreType.OPERATIONAL,
                bridgeRefEntry.getValue().firstIdentifierOf(Node.class), db);


        TerminationPoint tp = getTerminationPoint(bridgeNode.get(), port.getUuid().getValue());
        OvsdbTerminationPointAugmentation ovsdbTp = tp.getAugmentation(OvsdbTerminationPointAugmentation.class);

        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();
        tpAugmentationBuilder.setName(ovsdbTp.getName());
        tpAugmentationBuilder.setIngressPolicingRate(bwLimit.getMaxKbps().longValue());
        tpAugmentationBuilder.setIngressPolicingBurst(bwLimit.getMaxBurstKbps().longValue());

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setKey(tp.getKey());
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());
        MDSALUtil.syncUpdate(db, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                .child(Node.class, bridgeNode.get().getKey())
                .child(TerminationPoint.class, new TerminationPointKey(tp.getKey())), tpBuilder.build());
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

        //1. OF rules
        syncFlow(db, dpnId, NwConstants.ADD_FLOW, mdsalUtils, dscpValue, ifName, ipAddress);
        // /bind qos service to interface
        bindservice(db, ifName);
    }

    public static void unsetPortDscpMark(DataBroker dataBroker,
                                         OdlInterfaceRpcService odlInterfaceRpcService,
                                         IMdsalApiManager mdsalUtils,
                                         Port port) {
        LOG.trace("Removing dscp marking rule from Port {}", port);

        BigInteger dpnId = getDpnForInterface(odlInterfaceRpcService, port.getUuid().getValue());
        String ifName = port.getUuid().getValue();
        IpAddress ipAddress = port.getFixedIps().get(0).getIpAddress();

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.info("DPN ID for port {} not found", port);
            return;
        }

        //unbind service from interface
        unbindservice(dataBroker, ifName);
        // 1. OF
        syncFlow(dataBroker, dpnId, NwConstants.DEL_FLOW, mdsalUtils, (short) 0, ifName, ipAddress);
    }

    private static TerminationPoint getTerminationPoint(Node bridgeNode, String interfaceName) {
        for (TerminationPoint tp : bridgeNode.getTerminationPoint()) {
            Boolean found = false;
            OvsdbTerminationPointAugmentation ovsdbTp = tp.getAugmentation(OvsdbTerminationPointAugmentation.class);
            if (ovsdbTp.getInterfaceExternalIds() != null
                    && !ovsdbTp.getInterfaceExternalIds().isEmpty()) {
                for (InterfaceExternalIds entry : ovsdbTp.getInterfaceExternalIds()) {
                    if (entry.getExternalIdKey().equals(EXTERNAL_ID_INTERFACE_ID)
                            && entry.getExternalIdValue().equals(interfaceName)) {
                        found = true;
                        continue;
                    }
                }
            }
            if (found) {
                return tp;
            }
        }
        return null;
    }


    private static BigInteger getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
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

    private static void syncFlow(DataBroker db, BigInteger dpnId, int addOrRemove,
                          IMdsalApiManager mdsalUtils, Short dscpValue,
                          String ifName, IpAddress ipAddress) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();

        Interface ifState = getInterfaceStateFromOperDS(ifName, db);
        if (ifState == null) {
            LOG.trace("Could not find the ifState for interface {}", ifName);
            return;
        }
        Integer ifIndex = ifState.getIfIndex();

        if (ipAddress.getIpv4Address() != null) {
            matches.add(new MatchInfo(MatchFieldType.eth_type, new long[]{NwConstants.ETHTYPE_IPV4}));
        } else {
            matches.add(new MatchInfo(MatchFieldType.eth_type, new long[]{NwConstants.ETHTYPE_IPV6}));
        }
        matches.add(new MatchInfo(MatchFieldType.metadata,
               new BigInteger[] { MetaDataUtil.getLportTagMetaData(ifIndex), MetaDataUtil.METADATA_MASK_LPORT_TAG }));


        if (addOrRemove == NwConstants.ADD_FLOW) {
            actionsInfos.add(new ActionSetFieldDscp(dscpValue));
            actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[]{
                    Short.toString(NwConstants.LPORT_DISPATCHER_TABLE)}));

            instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE,
                    getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex),
                    NeutronQosConstants.QOS_DEFAULT_FLOW_PRIORITY, "QoSConfigFlow", 0, 0, NwConstants.COOKIE_QOS_TABLE,
                    matches, instructions);
            mdsalUtils.installFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE,
                    getQosFlowId(NwConstants.QOS_DSCP_TABLE, dpnId, ifIndex),
                    NeutronQosConstants.QOS_DEFAULT_FLOW_PRIORITY, "QoSRemoveFlow", 0, 0, NwConstants.COOKIE_QOS_TABLE,
                    matches, null);
            mdsalUtils.removeFlow(flowEntity);
        }
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface getInterfaceStateFromOperDS(
            String interfaceName, DataBroker dataBroker) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId = createInterfaceStateInstanceIdentifier(
                interfaceName);
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
        int priority = NeutronQosConstants.QOS_DEFAULT_FLOW_PRIORITY;
        int instructionKey =0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.QOS_DSCP_TABLE, ++instructionKey));
        short qosServiceIndex = ServiceIndex.getIndex(NwConstants.QOS_SERVICE_NAME, NwConstants.QOS_SERVICE_INDEX);

        BoundServices serviceInfo = NeutronQosUtils.getBoundServices(
                String.format("%s.%s", "qos", ifName), qosServiceIndex,
                priority, NwConstants.COOKIE_QOS_TABLE, instructions);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, NeutronQosUtils.buildServiceId(ifName, qosServiceIndex),
                serviceInfo );
    }

    public static void unbindservice(DataBroker dataBroker, String ifName) {
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, NeutronQosUtils.buildServiceId(ifName,
                ServiceIndex.getIndex(NwConstants.QOS_SERVICE_NAME, NwConstants.QOS_SERVICE_INDEX)));
    }

    private static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName, short qosServiceIndex) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(qosServiceIndex)).build();
    }

    private static BoundServices getBoundServices(String serviceName, short qosServiceIndex, int priority,
                                                  BigInteger cookieQosTable, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookieQosTable).setFlowPriority(priority)
                .setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(qosServiceIndex)).setServiceName(serviceName)
                .setServicePriority(qosServiceIndex).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static String getQosFlowId(short tableId, BigInteger dpId, int LportTag)
    {
        return new StringBuffer().append(tableId).append(dpId).append(LportTag).toString();
    }

}

/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypePatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceExternalIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.configure.termination.point.with.qos.input.EgressQos;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.configure.termination.point.with.qos.input.IngressQos;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosTerminationPointListener extends
        AsyncClusteredDataTreeChangeListenerBase<OvsdbTerminationPointAugmentation, QosTerminationPointListener> {
    private static final Logger LOG = LoggerFactory.getLogger(QosTerminationPointListener.class);
    private static final String EXTERNAL_ID_INTERFACE_ID = "iface-id";
    private final DataBroker dataBroker;
    private final QosNeutronUtils qosNeutronUtils;
    private final QosEosHandler qosEosHandler;
    private final QosAlertManager qosAlertManager;
    private final JobCoordinator jobCoordinator;

    private static final List<Class<? extends InterfaceTypeBase>> SKIP_IFACE_FOR_QOSPROCESSING = new ArrayList<>();

    static {
        SKIP_IFACE_FOR_QOSPROCESSING.add(InterfaceTypeVxlan.class);
        SKIP_IFACE_FOR_QOSPROCESSING.add(InterfaceTypeGre.class);
        SKIP_IFACE_FOR_QOSPROCESSING.add(InterfaceTypePatch.class);
    }

    @Inject
    public QosTerminationPointListener(final DataBroker dataBroker,
                                       final QosNeutronUtils qosNeutronUtils,
                                       final QosEosHandler qosEosHandler,
                                       final QosAlertManager qosAlertManager,
                                       final JobCoordinator jobCoordinator) {
        super(OvsdbTerminationPointAugmentation.class, QosTerminationPointListener.class);
        this.dataBroker = dataBroker;
        this.qosNeutronUtils = qosNeutronUtils;
        this.qosEosHandler = qosEosHandler;
        this.qosAlertManager = qosAlertManager;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<OvsdbTerminationPointAugmentation> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                .child(Node.class).child(TerminationPoint.class)
                .augmentation(OvsdbTerminationPointAugmentation.class);
    }

    @Override
    protected void remove(InstanceIdentifier<OvsdbTerminationPointAugmentation> instanceIdentifier,
                          OvsdbTerminationPointAugmentation tp) {

        if (tp.getInterfaceType() != null && SKIP_IFACE_FOR_QOSPROCESSING.contains(tp.getInterfaceType())) {
            // ignore processing event for InterfaceTypeVxlan, InterfaceTypeGre, InterfaceTypePatch type ports
            LOG.trace("Ignore remove evevnt for {} of type {}", tp.getName(), tp.getInterfaceType());
            return;
        }
        if (isBandwidthRuleApplied(tp)) {
            String ifaceId = getIfaceId(tp);
            if (ifaceId != null) {
                qosAlertManager.removeInterfaceIdFromQosAlertCache(ifaceId);
            }
        }
        Port port = qosNeutronUtils.getNeutronPort(getIfaceId(tp));
        if (port == null) {
            LOG.error("Neutron port not found for tp  {}", tp.getName());
            return;
        }
        if (tp.getQosEntry() == null || tp.getQosEntry().isEmpty()) {
            LOG.debug("Port {} has no qosEntries associated with it", port.getUuid().getValue());
            return;
        }
        QosPolicy qosPolicy = qosNeutronUtils.getQosPolicy(port);
        if (qosPolicy == null || qosPolicy.getBandwidthLimitRules() == null
                || qosPolicy.getBandwidthLimitRules().isEmpty()) {
            return;
        }
        InstanceIdentifier<Node> nodeIid = instanceIdentifier.firstIdentifierOf(Node.class);
        NodeKey nodeKey = nodeIid.firstKeyOf(Node.class);
        String nodeId = nodeKey.getNodeId().getValue();
        nodeId = nodeId.substring(0, nodeId.indexOf(QosConstants.BRIDGE_PREFIX));
        LOG.debug("Trying to delete qospolciy {} nodeid {} portname {} portid {}", qosPolicy.getUuid(),
                nodeId, port.getName(), port.getUuid().getValue());
        qosNeutronUtils.delPortFromQosPolicyPortMapDS(qosPolicy.getUuid(), nodeId, port.getUuid().getValue());
        // Update Cache map to remove the port.
    }

    private boolean isBandwidthRuleCleared(OvsdbTerminationPointAugmentation original,
                                         OvsdbTerminationPointAugmentation update) {
        return (update.getIngressPolicingRate() == 0 && update.getIngressPolicingBurst() == 0)
                && (original.getIngressPolicingRate() != 0 || original.getIngressPolicingBurst() != 0);
    }

    private boolean isBandwidthRuleApplied(OvsdbTerminationPointAugmentation tp) {
        return tp.getIngressPolicingRate() != 0 || tp.getIngressPolicingBurst() != 0;
    }

    private boolean isBandwidthRuleApplied(OvsdbTerminationPointAugmentation original,
                                           OvsdbTerminationPointAugmentation update) {
        return (original.getIngressPolicingRate() == 0 && original.getIngressPolicingBurst() == 0)
                && (update.getIngressPolicingRate() != 0 || update.getIngressPolicingBurst() != 0);
    }

    @Override
    protected void update(InstanceIdentifier<OvsdbTerminationPointAugmentation> instanceIdentifier,
                          OvsdbTerminationPointAugmentation original,
                          OvsdbTerminationPointAugmentation update) {
        if (update.getInterfaceType() != null && SKIP_IFACE_FOR_QOSPROCESSING.contains(update.getInterfaceType())) {
            // ignore processing event for InterfaceTypeVxlan, InterfaceTypeGre, InterfaceTypePatch type ports
            LOG.trace("Ignore update evevnt for {} of type {}", update.getName(), update.getInterfaceType());
            return;
        }
        String ifaceId = getIfaceId(update);
        if (ifaceId != null) {
            if (isBandwidthRuleCleared(original, update)) {
                qosAlertManager.removeInterfaceIdFromQosAlertCache(ifaceId);
            } else if (isBandwidthRuleApplied(original, update)) {
                qosAlertManager.addInterfaceIdInQoSAlertCache(ifaceId);
            }
        }
        if (!qosEosHandler.isQosClusterOwner()) {
            return;
        }
        // switch restart scenario with openstack newton onwards results in deletion and addition
        // of vhu ports with ovs-dpdk and as a side effect of that, qos parameters for rate limiting
        // get cleared from the port.
        // To resolve the issue, in TP update event, check is done to see if old port configuration
        // has qos parameters set but cleared in updated port configuration and qos policy with
        // bandwidth rules is present for the port, then reapply the qos policy configuration.

        if (ifaceId != null && isBandwidthRuleCleared(original, update)) {
            LOG.debug("update tp augment: iface-id: {}, name: {}, old bw rate, burst = {}, {}, "
                            + "updated bw rate, burst = {}, {}", ifaceId, update.getName(),
                    original.getIngressPolicingRate(), original.getIngressPolicingBurst(),
                    update.getIngressPolicingRate(), update.getIngressPolicingBurst());
            Port port = qosNeutronUtils.getNeutronPort(ifaceId);
            if (port != null) {
                setPortBandwidthRule(instanceIdentifier, update, port);
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<OvsdbTerminationPointAugmentation> instanceIdentifier,
                       OvsdbTerminationPointAugmentation tpAugment) {

        if (tpAugment.getInterfaceType() != null
            && SKIP_IFACE_FOR_QOSPROCESSING.contains(tpAugment.getInterfaceType())) {
            // ignore processing event for InterfaceTypeVxlan, InterfaceTypeGre, InterfaceTypePatch type ports
            LOG.trace("Ignore add evevnt for {} of type {}", tpAugment.getName(), tpAugment.getInterfaceType());
            return;
        }
        String ifaceId = getIfaceId(tpAugment);
        if ((ifaceId != null) && isBandwidthRuleApplied(tpAugment)) {
            qosAlertManager.addInterfaceIdInQoSAlertCache(ifaceId);
        }
        if ((ifaceId != null) && qosEosHandler.isQosClusterOwner()) {
            Port port = qosNeutronUtils.getNeutronPort(ifaceId);
            if (port != null) {
                LOG.debug("add tp augmentation: iface-id: {}, name: {} ", ifaceId, tpAugment.getName());
                setPortBandwidthRule(instanceIdentifier, tpAugment, port);
            }
        }
    }

    @Override
    protected QosTerminationPointListener getDataTreeChangeListener() {
        return QosTerminationPointListener.this;
    }

    private void setPortBandwidthRule(InstanceIdentifier<OvsdbTerminationPointAugmentation> identifier,
                                      OvsdbTerminationPointAugmentation update, Port port) {
        QosPolicy qosPolicy = qosNeutronUtils.getQosPolicy(port);
        if (qosPolicy == null || qosPolicy.getBandwidthLimitRules() == null
                || qosPolicy.getBandwidthLimitRules().isEmpty()) {
            return;
        }
        LOG.debug("setting bandwidth rule for port: {}, tp {}, qos policy uuid: {}",
                port.getUuid(), update.getName(), qosPolicy.getUuid().getValue());
        jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
            List<IngressQos> ingressQosList = Collections.emptyList();
            List<EgressQos> egressQosList = Collections.emptyList();
            InstanceIdentifier<Node> nodeIid = identifier.firstIdentifierOf(Node.class);
            List<BandwidthLimitRules> bwRuleList = qosPolicy.getBandwidthLimitRules();
            for (BandwidthLimitRules bwRule : bwRuleList) {
                if (bwRule.getDirection() == null || bwRule.getDirection() == DirectionEgress.class) {
                    ingressQosList = qosNeutronUtils.getQosIngressParamsList(bwRule);
                } else if (bwRule.getDirection() == DirectionIngress.class) {
                    egressQosList = qosNeutronUtils.getQosEgressParamsList(bwRule, port.getUuid(),
                            nodeIid, qosPolicy.getUuid(), update.getName());
                }
            }
            if (!ingressQosList.isEmpty() || !egressQosList.isEmpty()) {
                qosNeutronUtils.configureTerminationPoint(ingressQosList, egressQosList,
                        update.getName(), nodeIid);
            }
            return Collections.emptyList();
        });
    }

    private String getIfaceId(OvsdbTerminationPointAugmentation tpAugmentation) {
        if (tpAugmentation.getInterfaceExternalIds() != null) {
            for (InterfaceExternalIds entry: tpAugmentation.getInterfaceExternalIds()) {
                if (entry.getExternalIdKey().equals(EXTERNAL_ID_INTERFACE_ID)) {
                    return entry.getExternalIdValue();
                }
            }
        }
        return null;
    }
}

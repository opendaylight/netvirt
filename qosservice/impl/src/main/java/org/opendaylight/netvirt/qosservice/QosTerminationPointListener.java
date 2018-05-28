/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.util.Collections;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceExternalIds;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
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
    private final ManagedNewTransactionRunner txRunner;
    private final JobCoordinator jobCoordinator;

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
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
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
        if (isBandwidthRuleApplied(tp)) {
            String ifaceId = getIfaceId(tp);
            if (ifaceId != null) {
                qosAlertManager.removeInterfaceIdFromQosAlertCache(ifaceId);
            }
        }
    }

    private boolean isBandwidthRuleCleared(OvsdbTerminationPointAugmentation original,
                                         OvsdbTerminationPointAugmentation update) {
        if ((update.getIngressPolicingRate() == 0 && update.getIngressPolicingBurst() == 0)
                && (original.getIngressPolicingRate() != 0 || original.getIngressPolicingBurst() != 0)) {
            return true;
        }
        return false;
    }

    private boolean isBandwidthRuleApplied(OvsdbTerminationPointAugmentation tp) {
        if (tp.getIngressPolicingRate() != 0 || tp.getIngressPolicingBurst() != 0) {
            return true;
        }
        return false;
    }

    private boolean isBandwidthRuleApplied(OvsdbTerminationPointAugmentation original,
                                           OvsdbTerminationPointAugmentation update) {
        if ((original.getIngressPolicingRate() == 0 && original.getIngressPolicingBurst() == 0)
                && (update.getIngressPolicingRate() != 0 || update.getIngressPolicingBurst() != 0)) {
            return true;
        }
        return false;
    }

    @Override
    protected void update(InstanceIdentifier<OvsdbTerminationPointAugmentation> instanceIdentifier,
                          OvsdbTerminationPointAugmentation original,
                          OvsdbTerminationPointAugmentation update) {
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
            LOG.trace("update tp augment: iface-id: {}, name: {}, old bw rate, burst = {}, {}, "
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
        String ifaceId = getIfaceId(tpAugment);
        if ((ifaceId != null) && isBandwidthRuleApplied(tpAugment)) {
            qosAlertManager.addInterfaceIdInQoSAlertCache(ifaceId);
        }
        if ((ifaceId != null) && qosEosHandler.isQosClusterOwner()) {
            Port port = qosNeutronUtils.getNeutronPort(ifaceId);
            if (port != null) {
                LOG.trace("add tp augmentation: iface-id: {}, name: {} ", ifaceId, tpAugment.getName());
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
        LOG.trace("setting bandwidth rule for port: {}, {}, qos policy: {}",
                port.getUuid(), update.getName(), qosPolicy.getName());

        jobCoordinator.enqueueJob("QosPort-" + port.getUuid(), () ->
                Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    BandwidthLimitRules bwRule = qosPolicy.getBandwidthLimitRules().get(0);
                    OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder =
                            new OvsdbTerminationPointAugmentationBuilder();
                    tpAugmentationBuilder.setName(update.getName());
                    tpAugmentationBuilder.setIngressPolicingRate(bwRule.getMaxKbps().longValue());
                    tpAugmentationBuilder.setIngressPolicingBurst(bwRule.getMaxBurstKbps().longValue());

                    tx.merge(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(NetworkTopology.class)
                            .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                            .child(Node.class, identifier.firstKeyOf(Node.class))
                            .child(TerminationPoint.class, identifier.firstKeyOf(TerminationPoint.class))
                            .augmentation(OvsdbTerminationPointAugmentation.class),
                            tpAugmentationBuilder.build(), true);

                })));
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

/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.neutronvpn.api.utils.ChangeUtils;
import org.opendaylight.netvirt.qosservice.recovery.QosServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.QosPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.QosRuleTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.QosRuleTypesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRulesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.DscpmarkingRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.rule.types.RuleTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.rule.types.RuleTypesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosPolicyChangeListener extends AsyncClusteredDataTreeChangeListenerBase<QosPolicy,
                                                QosPolicyChangeListener> implements RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(QosPolicyChangeListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final QosNeutronUtils qosNeutronUtils;
    private final JobCoordinator jobCoordinator;

    @Inject
    public QosPolicyChangeListener(final DataBroker dataBroker,
                                   final QosNeutronUtils qosNeutronUtils, final JobCoordinator jobCoordinator,
                                   final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                   final QosServiceRecoveryHandler qosServiceRecoveryHandler) {
        super(QosPolicy.class, QosPolicyChangeListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.qosNeutronUtils = qosNeutronUtils;
        this.jobCoordinator = jobCoordinator;
        serviceRecoveryRegistry.addRecoverableListener(qosServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        LOG.debug("{} created",  getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        registerListener();
        supportedQoSRuleTypes();
        LOG.debug("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    public void registerListener() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<QosPolicy> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(QosPolicies.class).child(QosPolicy.class);
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<QosPolicy>> changes) {
        handleQosPolicyChanges(changes);
        handleBandwidthLimitRulesChanges(changes);
        handleDscpMarkingRulesChanges(changes);
    }

    @Override
    protected QosPolicyChangeListener getDataTreeChangeListener() {
        return QosPolicyChangeListener.this;
    }

    private void handleQosPolicyChanges(Collection<DataTreeModification<QosPolicy>> changes) {
        Map<InstanceIdentifier<QosPolicy>, QosPolicy> qosPolicyOriginalMap =
                ChangeUtils.extractOriginal(changes, QosPolicy.class);

        for (Entry<InstanceIdentifier<QosPolicy>, QosPolicy> qosPolicyMapEntry :
                ChangeUtils.extractCreated(changes, QosPolicy.class).entrySet()) {
            add(qosPolicyMapEntry.getKey(), qosPolicyMapEntry.getValue());
        }
        for (Entry<InstanceIdentifier<QosPolicy>, QosPolicy> qosPolicyMapEntry :
                ChangeUtils.extractUpdated(changes, QosPolicy.class).entrySet()) {
            update(qosPolicyMapEntry.getKey(), qosPolicyOriginalMap.get(qosPolicyMapEntry.getKey()),
                    qosPolicyMapEntry.getValue());
        }
        for (InstanceIdentifier<QosPolicy> qosPolicyIid : ChangeUtils.extractRemoved(changes, QosPolicy.class)) {
            remove(qosPolicyIid, qosPolicyOriginalMap.get(qosPolicyIid));
        }
    }

    private void handleBandwidthLimitRulesChanges(Collection<DataTreeModification<QosPolicy>> changes) {
        Map<InstanceIdentifier<BandwidthLimitRules>, BandwidthLimitRules> bwLimitOriginalMap =
                ChangeUtils.extractOriginal(changes, BandwidthLimitRules.class);

        for (Entry<InstanceIdentifier<BandwidthLimitRules>, BandwidthLimitRules> bwLimitMapEntry :
                ChangeUtils.extractCreated(changes, BandwidthLimitRules.class).entrySet()) {
            add(bwLimitMapEntry.getKey(), bwLimitMapEntry.getValue());
        }
        for (Entry<InstanceIdentifier<BandwidthLimitRules>, BandwidthLimitRules> bwLimitMapEntry :
                ChangeUtils.extractUpdated(changes, BandwidthLimitRules.class).entrySet()) {
            update(bwLimitMapEntry.getKey(), bwLimitOriginalMap.get(bwLimitMapEntry.getKey()),
                    bwLimitMapEntry.getValue());
        }
        for (InstanceIdentifier<BandwidthLimitRules> bwLimitIid :
                ChangeUtils.extractRemoved(changes, BandwidthLimitRules.class)) {
            remove(bwLimitIid, bwLimitOriginalMap.get(bwLimitIid));
        }
    }

    private void handleDscpMarkingRulesChanges(Collection<DataTreeModification<QosPolicy>> changes) {
        Map<InstanceIdentifier<DscpmarkingRules>, DscpmarkingRules> dscpMarkOriginalMap =
                ChangeUtils.extractOriginal(changes, DscpmarkingRules.class);

        for (Entry<InstanceIdentifier<DscpmarkingRules>, DscpmarkingRules> dscpMarkMapEntry :
                ChangeUtils.extractCreated(changes, DscpmarkingRules.class).entrySet()) {
            add(dscpMarkMapEntry.getKey(), dscpMarkMapEntry.getValue());
        }
        for (Entry<InstanceIdentifier<DscpmarkingRules>, DscpmarkingRules> dscpMarkMapEntry :
                ChangeUtils.extractUpdated(changes, DscpmarkingRules.class).entrySet()) {
            update(dscpMarkMapEntry.getKey(), dscpMarkOriginalMap.get(dscpMarkMapEntry.getKey()),
                    dscpMarkMapEntry.getValue());
        }
        for (InstanceIdentifier<DscpmarkingRules> dscpMarkIid :
                ChangeUtils.extractRemoved(changes, DscpmarkingRules.class)) {
            remove(dscpMarkIid, dscpMarkOriginalMap.get(dscpMarkIid));
        }
    }

    @Override
    protected void add(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        LOG.trace("Adding  QosPolicy : key: {}, value={}", identifier, input);
        qosNeutronUtils.addToQosPolicyCache(input);
    }

    protected void add(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules input) {
        LOG.trace("Adding BandwidthlimitRules : key: {}, value={}", identifier, input);

        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        for (Network network : qosNeutronUtils.getQosNetworks(qosUuid)) {
            qosNeutronUtils.handleNeutronNetworkQosUpdate(network, qosUuid);
        }

        for (Port port : qosNeutronUtils.getQosPorts(qosUuid)) {
            jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> Collections.singletonList(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        tx -> qosNeutronUtils.setPortBandwidthLimits(port, input, tx))));
        }
    }

    private void add(InstanceIdentifier<DscpmarkingRules> identifier, DscpmarkingRules input) {
        LOG.trace("Adding DscpMarkingRules : key: {}, value={}", identifier, input);

        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();

        for (Network network : qosNeutronUtils.getQosNetworks(qosUuid)) {
            qosNeutronUtils.handleNeutronNetworkQosUpdate(network, qosUuid);
        }

        for (Port port : qosNeutronUtils.getQosPorts(qosUuid)) {
            jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
                qosNeutronUtils.setPortDscpMarking(port, input);
                return Collections.emptyList();
            });
        }
    }

    @Override
    protected void remove(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        LOG.trace("Removing QosPolicy : key: {}, value={}", identifier, input);
        qosNeutronUtils.removeFromQosPolicyCache(input);
    }

    private void remove(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules input) {
        LOG.trace("Removing BandwidthLimitRules : key: {}, value={}", identifier, input);

        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
        BandwidthLimitRules zeroBwLimitRule =
                bwLimitBuilder.setMaxBurstKbps(BigInteger.ZERO).setMaxKbps(BigInteger.ZERO).build();

        for (Network network : qosNeutronUtils.getQosNetworks(qosUuid)) {
            qosNeutronUtils.handleNeutronNetworkQosBwRuleRemove(network, zeroBwLimitRule);
        }

        for (Port port : qosNeutronUtils.getQosPorts(qosUuid)) {
            jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> Collections.singletonList(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        tx -> qosNeutronUtils.setPortBandwidthLimits(port, zeroBwLimitRule, tx))));
        }
    }

    private void remove(InstanceIdentifier<DscpmarkingRules> identifier,DscpmarkingRules input) {
        LOG.trace("Removing DscpMarkingRules : key: {}, value={}", identifier, input);

        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();

        for (Network network : qosNeutronUtils.getQosNetworks(qosUuid)) {
            qosNeutronUtils.handleNeutronNetworkQosDscpRuleRemove(network);
        }

        for (Port port : qosNeutronUtils.getQosPorts(qosUuid)) {
            jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
                qosNeutronUtils.unsetPortDscpMark(port);
                return Collections.emptyList();
            });
        }
    }

    public void reapplyPolicy(String entityid) {
        Uuid policyUuid = Uuid.getDefaultInstance(entityid);
        if (qosNeutronUtils.getQosPolicyMap().get(policyUuid) == null) {
            LOG.debug("Policy with Uuid: {} does not exist", entityid);
            return;
        }

        if (!qosNeutronUtils.getQosPolicyMap().get(policyUuid).getBandwidthLimitRules().isEmpty()) {
            BandwidthLimitRules bandwidthLimitRules =
                    qosNeutronUtils.getQosPolicyMap().get(policyUuid).getBandwidthLimitRules().get(0);
            update(policyUuid, bandwidthLimitRules);
        }

        if (!qosNeutronUtils.getQosPolicyMap().get(policyUuid).getDscpmarkingRules().isEmpty()) {
            DscpmarkingRules dscpmarkingRules =
                    qosNeutronUtils.getQosPolicyMap().get(policyUuid).getDscpmarkingRules().get(0);
            update(policyUuid, dscpmarkingRules);
        }
    }

    @Override
    protected void update(InstanceIdentifier<QosPolicy> identifier, QosPolicy original, QosPolicy update) {
        LOG.trace("Updating QosPolicy : key: {}, original value={}, update value={}", identifier, original, update);
        qosNeutronUtils.addToQosPolicyCache(update);
    }

    private void update(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules original,
                        BandwidthLimitRules update) {
        LOG.trace("Updating BandwidthLimitRules : key: {}, original value={}, update value={}", identifier, original,
                update);
        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        update(qosUuid, update);
    }

    private void update(Uuid qosUuid, BandwidthLimitRules update) {
        for (Network network : qosNeutronUtils.getQosNetworks(qosUuid)) {
            qosNeutronUtils.handleNeutronNetworkQosUpdate(network, qosUuid);
        }

        for (Port port : qosNeutronUtils.getQosPorts(qosUuid)) {
            jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> Collections.singletonList(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        tx -> qosNeutronUtils.setPortBandwidthLimits(port, update, tx))));
        }
    }

    private void update(InstanceIdentifier<DscpmarkingRules> identifier, DscpmarkingRules original,
                        DscpmarkingRules update) {
        LOG.trace("Updating DscpMarkingRules : key: {}, original value={}, update value={}", identifier, original,
                update);
        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        update(qosUuid, update);
    }

    private void update(Uuid qosUuid, DscpmarkingRules update) {
        for (Network network : qosNeutronUtils.getQosNetworks(qosUuid)) {
            qosNeutronUtils.handleNeutronNetworkQosUpdate(network, qosUuid);
        }

        for (Port port : qosNeutronUtils.getQosPorts(qosUuid)) {
            jobCoordinator.enqueueJob("QosPort-" + port.getUuid().getValue(), () -> {
                qosNeutronUtils.setPortDscpMarking(port, update);
                return Collections.emptyList();
            });
        }
    }

    private void supportedQoSRuleTypes() {
        QosRuleTypesBuilder qrtBuilder = new QosRuleTypesBuilder();
        List<RuleTypes> value = new ArrayList<>();

        value.add(getRuleTypes("bandwidth_limit_rules"));
        value.add(getRuleTypes("dscp_marking_rules"));

        qrtBuilder.setRuleTypes(value);

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
            InstanceIdentifier instanceIdentifier = InstanceIdentifier.create(Neutron.class).child(QosRuleTypes.class);
            tx.merge(LogicalDatastoreType.OPERATIONAL, instanceIdentifier, qrtBuilder.build());
        }), LOG, "Error setting up supported QoS rule types");
    }

    private RuleTypes getRuleTypes(String ruleType) {
        RuleTypesBuilder rtBuilder = new RuleTypesBuilder();
        rtBuilder.setRuleType(ruleType);
        return rtBuilder.build();
    }
}

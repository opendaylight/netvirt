/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.ChangeUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.QosPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRulesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

public class QosPolicyChangeListener implements ClusteredDataTreeChangeListener<QosPolicy>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(QosPolicyChangeListener.class);
    private ListenerRegistration<DataTreeChangeListener<QosPolicy>> listenerRegistration;
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;


    public QosPolicyChangeListener(final DataBroker dataBroker,
                                   final OdlInterfaceRpcService odlInterfaceRpcService) {
        this.dataBroker = dataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        DataTreeIdentifier<QosPolicy> dataTreeIdentifier =
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,
                        InstanceIdentifier.create(Neutron.class).child(QosPolicies.class).child(QosPolicy.class));
        LOG.info("Qos Policy DataChange listener registration {}", dataTreeIdentifier);
        listenerRegistration = dataBroker.registerDataTreeChangeListener(dataTreeIdentifier,
                QosPolicyChangeListener.this);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration.close();
        }
        LOG.info("Qos Policy listener Closed");
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<QosPolicy>> changes) {
        handleQosPolicyChanges(changes);
        handleBandwidthLimitRulesChanges(changes);
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

    private void add(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        LOG.trace("Adding  QosPolicy : key: {}, value={}", identifier, input);
        QosNeutronMappings.addToQosPolicyCache(input);
    }

    private void add(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules input) {
        LOG.trace("Adding BandwidthlimitRules : key: {}, value={}", identifier, input);

        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        if (QosNeutronMappings.qosNetworksMap.get(qosUuid) != null
                && !QosNeutronMappings.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : QosNeutronMappings.qosNetworksMap.get(qosUuid).values()) {
                QosUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (QosNeutronMappings.qosPortsMap.get(qosUuid) != null
                && !QosNeutronMappings.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : QosNeutronMappings.qosPortsMap.get(qosUuid).values()) {
                QosUtils.setPortBandwidthLimits(dataBroker, odlInterfaceRpcService, port, input);
            }
        }
    }

    private void remove(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        LOG.trace("Removing QosPolicy : key: {}, value={}", identifier, input);
        QosNeutronMappings.removeFromQosPolicyCache(input);
    }

    private void remove(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules input) {
        LOG.trace("Removing BandwidthLimitRules : key: {}, value={}", identifier, input);

        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
        BandwidthLimitRules zeroBwLimitRule =
                bwLimitBuilder.setMaxBurstKbps(BigInteger.ZERO).setMaxKbps(BigInteger.ZERO).build();

        if (QosNeutronMappings.qosNetworksMap.get(qosUuid) != null
                && !QosNeutronMappings.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : QosNeutronMappings.qosNetworksMap.get(qosUuid).values()) {
                QosUtils.handleNeutronNetworkQosRemove(dataBroker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (QosNeutronMappings.qosPortsMap.get(qosUuid) != null
                && !QosNeutronMappings.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : QosNeutronMappings.qosPortsMap.get(qosUuid).values()) {
                QosUtils.setPortBandwidthLimits(dataBroker, odlInterfaceRpcService, port, zeroBwLimitRule);
            }
        }
    }

    private void update(InstanceIdentifier<QosPolicy> identifier, QosPolicy original, QosPolicy update) {
        LOG.trace("Updating QosPolicy : key: {}, original value={}, update value={}", identifier, original, update);
        QosNeutronMappings.addToQosPolicyCache(update);
    }

    private void update(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules original,
                        BandwidthLimitRules update) {
        LOG.trace("Updating BandwidthLimitRules : key: {}, original value={}, update value={}", identifier, original,
                update);
        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        if (QosNeutronMappings.qosNetworksMap.get(qosUuid) != null
                && !QosNeutronMappings.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : QosNeutronMappings.qosNetworksMap.get(qosUuid).values()) {
                QosUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (QosNeutronMappings.qosPortsMap.get(qosUuid) != null
                && !QosNeutronMappings.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : QosNeutronMappings.qosPortsMap.get(qosUuid).values()) {
                QosUtils.setPortBandwidthLimits(dataBroker, odlInterfaceRpcService, port, update);
            }
        }
    }
}

/*
 * Copyright (c) 2016 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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


public class NeutronQosPolicyChangeListener implements ClusteredDataTreeChangeListener<QosPolicy>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronQosPolicyChangeListener.class);
    private final ListenerRegistration<DataTreeChangeListener<QosPolicy>> listenerRegistration;
    private final DataBroker broker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;


    public NeutronQosPolicyChangeListener(final DataBroker db, final OdlInterfaceRpcService odlInterfaceRpcService) {
        broker = db;
        listenerRegistration = registerListener(db);
        this.odlInterfaceRpcService = odlInterfaceRpcService;
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration.close();
        }
        LOG.info("N_Qos Policy listener Closed");
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<QosPolicy>> changes) {
        LOG.info("onDataTreeChanged: {}", changes);

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

    private ListenerRegistration<DataTreeChangeListener<QosPolicy>> registerListener(final DataBroker db) {
        try {
            DataTreeIdentifier<QosPolicy> dataTreeIdentifier =
                    new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,
                            InstanceIdentifier.create(Neutron.class).child(QosPolicies.class).child(QosPolicy.class));
            LOG.info("Neutron Manager Qos Policy DataChange listener registration {}", dataTreeIdentifier);
            return db.registerDataTreeChangeListener(dataTreeIdentifier, NeutronQosPolicyChangeListener.this);
        } catch (final Exception e) {
            LOG.error("Neutron Manager Qos Policy DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Qos DataChange listener registration failed.", e);
        }
    }

    private void add(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding  QosPolicy : key: " + identifier + ", value=" + input);
        }
        NeutronvpnUtils.addToQosPolicyCache(input);

        handleNeutronQosPolicyCreated(input);
    }

    private void add(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules input) {
        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding BandwidthlimitRules : key: " + identifier + ", value=" + input);
        }
        LOG.info("Adding BandwidthlimitRule RKEY: {} VALUE: {} QosPolicy QKEY: {}", identifier, input, qosUuid);

        if (NeutronvpnUtils.qosNetworksMap.get(qosUuid) != null
                && !NeutronvpnUtils.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : NeutronvpnUtils.qosNetworksMap.get(qosUuid).values()) {
                NeutronQosUtils.handleNeutronNetworkQosUpdate(broker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (NeutronvpnUtils.qosPortsMap.get(qosUuid) != null
                && !NeutronvpnUtils.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : NeutronvpnUtils.qosPortsMap.get(qosUuid).values()) {
                NeutronQosUtils.setPortBandwidthLimits(broker, odlInterfaceRpcService, port, input);
            }
        }
    }

    private void remove(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing QosPolicy : key: " + identifier + ", value=" + input);
        }
        handleNeutronQosPolicyDeleted(input);
        NeutronvpnUtils.removeFromQosPolicyCache(input);
    }

    private void remove(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules input) {
        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing BandwidthLimitRules : key: " + identifier + ", value=" + input);
        }
        LOG.info("Deleting BandwidthlimitRule RKEY: {} VALUE: {} QosPolicy QKEY: {}", identifier, input, qosUuid);

        BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
        BandwidthLimitRules zeroBwLimitRule =
                bwLimitBuilder.setMaxBurstKbps(BigInteger.ZERO).setMaxKbps(BigInteger.ZERO).build();

        if (NeutronvpnUtils.qosNetworksMap.get(qosUuid) != null
                && !NeutronvpnUtils.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : NeutronvpnUtils.qosNetworksMap.get(qosUuid).values()) {
                NeutronQosUtils.handleNeutronNetworkQosRemove(broker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (NeutronvpnUtils.qosPortsMap.get(qosUuid) != null
                && !NeutronvpnUtils.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : NeutronvpnUtils.qosPortsMap.get(qosUuid).values()) {
                NeutronQosUtils.setPortBandwidthLimits(broker, odlInterfaceRpcService, port, zeroBwLimitRule);
            }
        }
    }

    private void update(InstanceIdentifier<QosPolicy> identifier, QosPolicy original, QosPolicy update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating QosPolicy : key: " + identifier + ", original value=" + original + ", update value="
                    + update);
        }

        NeutronvpnUtils.addToQosPolicyCache(update);

        handleNeutronQosPolicyUpdated(original, update);
    }

    private void update(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules original,
            BandwidthLimitRules update) {
        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating BandwidthLimitRules : key: " + identifier + ", original value=" + original
                    + ", update value=" + update);
        }
        LOG.info("Updating BandwidthlimitRule RKEY: {} VALUE: {} QosPolicy QKEY: {}", identifier, update, qosUuid);

        if (NeutronvpnUtils.qosNetworksMap.get(qosUuid) != null
                && !NeutronvpnUtils.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : NeutronvpnUtils.qosNetworksMap.get(qosUuid).values()) {
                NeutronQosUtils.handleNeutronNetworkQosUpdate(broker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (NeutronvpnUtils.qosPortsMap.get(qosUuid) != null
                && !NeutronvpnUtils.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : NeutronvpnUtils.qosPortsMap.get(qosUuid).values()) {
                NeutronQosUtils.setPortBandwidthLimits(broker, odlInterfaceRpcService, port, update);
            }
        }
    }

    private void handleNeutronQosPolicyCreated(QosPolicy qosPolicy) {
        LOG.info("Handling QosPolicy Creation: {}", qosPolicy);
    }

    private void handleNeutronQosPolicyDeleted(QosPolicy qosPolicy) {
        LOG.info("Handling QosPolicy Deletion: {}", qosPolicy);
    }

    private void handleNeutronQosPolicyUpdated(QosPolicy qosoriginal, QosPolicy qosupdate) {
        LOG.info("Handling QosPolicy Update: {} to {}", qosoriginal, qosupdate);
    }
}

/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;


import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.neutronvpn.api.utils.ChangeUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.QosPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.qos.policy.BandwidthLimitRulesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QosPolicyChangeListener extends AsyncDataTreeChangeListenerBase<QosPolicy, QosPolicyChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(QosPolicyChangeListener.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;


    public QosPolicyChangeListener(final DataBroker dataBroker,
                                   final OdlInterfaceRpcService odlInterfaceRpcService) {
        super(QosPolicy.class, QosPolicyChangeListener.class);
        this.dataBroker = dataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
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

    protected void add(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        LOG.trace("Adding  QosPolicy : key: {}, value={}", identifier, input);
        QosNeutronUtils.addToQosPolicyCache(input);
    }

    protected void add(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules input) {
        LOG.trace("Adding BandwidthlimitRules : key: {}, value={}", identifier, input);

        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        if (QosNeutronUtils.qosNetworksMap.get(qosUuid) != null
                && !QosNeutronUtils.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : QosNeutronUtils.qosNetworksMap.get(qosUuid).values()) {
                QosNeutronUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (QosNeutronUtils.qosPortsMap.get(qosUuid) != null
                && !QosNeutronUtils.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : QosNeutronUtils.qosPortsMap.get(qosUuid).values()) {
                final DataStoreJobCoordinator portDataStoreCoordinator =
                        DataStoreJobCoordinator.getInstance();
                portDataStoreCoordinator.enqueueJob("QosPolicyPortAdd-" + port.getUuid().getValue(), () -> {
                    WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    QosNeutronUtils.setPortBandwidthLimits(dataBroker, odlInterfaceRpcService, port, input,
                            wrtConfigTxn);
                    futures.add(wrtConfigTxn.submit());
                    return futures;
                });
            }
        }
    }

    protected void remove(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        LOG.trace("Removing QosPolicy : key: {}, value={}", identifier, input);
        QosNeutronUtils.removeFromQosPolicyCache(input);
    }

    private void remove(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules input) {
        LOG.trace("Removing BandwidthLimitRules : key: {}, value={}", identifier, input);

        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        BandwidthLimitRulesBuilder bwLimitBuilder = new BandwidthLimitRulesBuilder();
        BandwidthLimitRules zeroBwLimitRule =
                bwLimitBuilder.setMaxBurstKbps(BigInteger.ZERO).setMaxKbps(BigInteger.ZERO).build();

        if (QosNeutronUtils.qosNetworksMap.get(qosUuid) != null
                && !QosNeutronUtils.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : QosNeutronUtils.qosNetworksMap.get(qosUuid).values()) {
                QosNeutronUtils.handleNeutronNetworkQosRemove(dataBroker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (QosNeutronUtils.qosPortsMap.get(qosUuid) != null
                && !QosNeutronUtils.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : QosNeutronUtils.qosPortsMap.get(qosUuid).values()) {
                final DataStoreJobCoordinator portDataStoreCoordinator =
                        DataStoreJobCoordinator.getInstance();
                portDataStoreCoordinator.enqueueJob("QosPolicyPortRemove-" + port.getUuid().getValue(), () -> {
                    WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    QosNeutronUtils.setPortBandwidthLimits(dataBroker, odlInterfaceRpcService, port, zeroBwLimitRule,
                            wrtConfigTxn);
                    futures.add(wrtConfigTxn.submit());
                    return futures;
                });
            }
        }
    }

    protected void update(InstanceIdentifier<QosPolicy> identifier, QosPolicy original, QosPolicy update) {
        LOG.trace("Updating QosPolicy : key: {}, original value={}, update value={}", identifier, original, update);
        QosNeutronUtils.addToQosPolicyCache(update);
    }

    private void update(InstanceIdentifier<BandwidthLimitRules> identifier, BandwidthLimitRules original,
                        BandwidthLimitRules update) {
        LOG.trace("Updating BandwidthLimitRules : key: {}, original value={}, update value={}", identifier, original,
                update);
        Uuid qosUuid = identifier.firstKeyOf(QosPolicy.class).getUuid();
        if (QosNeutronUtils.qosNetworksMap.get(qosUuid) != null
                && !QosNeutronUtils.qosNetworksMap.get(qosUuid).isEmpty()) {
            for (Network network : QosNeutronUtils.qosNetworksMap.get(qosUuid).values()) {
                QosNeutronUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService, network, qosUuid);
            }
        }

        if (QosNeutronUtils.qosPortsMap.get(qosUuid) != null
                && !QosNeutronUtils.qosPortsMap.get(qosUuid).isEmpty()) {
            for (Port port : QosNeutronUtils.qosPortsMap.get(qosUuid).values()) {
                final DataStoreJobCoordinator portDataStoreCoordinator =
                        DataStoreJobCoordinator.getInstance();
                portDataStoreCoordinator.enqueueJob("QosPolicyPortUpdate-" + port.getUuid().getValue(), () -> {
                    WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    QosNeutronUtils.setPortBandwidthLimits(dataBroker, odlInterfaceRpcService, port, update,
                            wrtConfigTxn);
                    futures.add(wrtConfigTxn.submit());
                    return futures;
                });
            }
        }
    }
}

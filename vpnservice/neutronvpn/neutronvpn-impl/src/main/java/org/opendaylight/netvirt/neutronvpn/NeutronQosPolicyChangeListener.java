/*
 * Copyright (c) 2016 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.QosPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NeutronQosPolicyChangeListener implements ClusteredDataTreeChangeListener<QosPolicy>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronQosPolicyChangeListener.class);
    private final ListenerRegistration<DataTreeChangeListener<QosPolicy>> listenerRegistration;
    private final DataBroker broker;


    public NeutronQosPolicyChangeListener(final DataBroker db) {
        broker = db;
        listenerRegistration = registerListener(db);
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
        //handleBandwidthLimitRulesChanges(changes);

    }

    private void handleQosPolicyChanges(Collection<DataTreeModification<QosPolicy>> changes) {
        Map<InstanceIdentifier<QosPolicy>, QosPolicy> qosPolicyOriginalMap = ChangeUtils.extractOriginal(changes, QosPolicy.class);

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
            LOG.trace("Adding QosPolicy : key: " + identifier + ", value=" + input);
        }
        NeutronvpnUtils.addToQosPolicyCache(input);

        handleNeutronQosPolicyCreated(input);
    }

    private void remove(InstanceIdentifier<QosPolicy> identifier, QosPolicy input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing QosPolicy : key: " + identifier + ", value=" + input);
        }
        NeutronvpnUtils.removeFromQosPolicyCache(input);

        handleNeutronQosPolicyDeleted(input);
    }

    private void update(InstanceIdentifier<QosPolicy> identifier, QosPolicy original, QosPolicy update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating QosPolicy : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }

        NeutronvpnUtils.addToQosPolicyCache(update);

        handleNeutronQosPolicyUpdated(original, update);
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

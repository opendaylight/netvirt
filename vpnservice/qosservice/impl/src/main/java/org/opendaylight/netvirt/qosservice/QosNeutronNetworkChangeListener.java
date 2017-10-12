/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNeutronNetworkChangeListener extends AsyncClusteredDataTreeChangeListenerBase<Network,
        QosNeutronNetworkChangeListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronNetworkChangeListener.class);
    private final DataBroker dataBroker;
    private final QosAlertManager qosAlertManager;
    private final QosNeutronUtils qosNeutronUtils;

    @Inject
    public QosNeutronNetworkChangeListener(final DataBroker dataBroker, final QosAlertManager qosAlertManager,
            final QosNeutronUtils qosNeutronUtils) {
        super(Network.class, QosNeutronNetworkChangeListener.class);
        this.dataBroker = dataBroker;
        this.qosAlertManager = qosAlertManager;
        this.qosNeutronUtils = qosNeutronUtils;
        LOG.debug("{} created",  getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        LOG.debug("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    protected InstanceIdentifier<Network> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Networks.class).child(Network.class);
    }

    @Override
    protected QosNeutronNetworkChangeListener getDataTreeChangeListener() {
        return QosNeutronNetworkChangeListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<Network> instanceIdentifier, Network network) {
        if (qosNeutronUtils.hasBandwidthLimitRule(network)) {
            qosAlertManager.removeFromQosAlertCache(network);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Network> instanceIdentifier, Network original, Network update) {
        QosNetworkExtension updateQos = update.getAugmentation(QosNetworkExtension.class);
        QosNetworkExtension originalQos = original.getAugmentation(QosNetworkExtension.class);
        if (originalQos == null && updateQos != null) {
            // qosservice policy add
            qosNeutronUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            qosNeutronUtils.handleNeutronNetworkQosUpdate(update, updateQos.getQosPolicyId());
            if (qosNeutronUtils.hasBandwidthLimitRule(update)) {
                qosAlertManager.addToQosAlertCache(update);
            }
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {

            // qosservice policy update

            qosNeutronUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
            qosNeutronUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            qosNeutronUtils.handleNeutronNetworkQosUpdate(update, updateQos.getQosPolicyId());

            if (qosNeutronUtils.hasBandwidthLimitRule(original)
                                             && !qosNeutronUtils.hasBandwidthLimitRule(update)) {
                qosAlertManager.removeFromQosAlertCache(original);
            } else if (!qosNeutronUtils.hasBandwidthLimitRule(original)
                                              && qosNeutronUtils.hasBandwidthLimitRule(update)) {
                qosAlertManager.addToQosAlertCache(update);
            }

        } else if (originalQos != null && updateQos == null) {
            // qosservice policy delete
            if (qosNeutronUtils.hasBandwidthLimitRule(original)) {
                qosAlertManager.removeFromQosAlertCache(original);
            }
            qosNeutronUtils.handleNeutronNetworkQosRemove(original, originalQos.getQosPolicyId());
            qosNeutronUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Network> instanceIdentifier, Network network) {
        QosNetworkExtension networkQos = network.getAugmentation(QosNetworkExtension.class);
        if (networkQos != null) {
            qosNeutronUtils.addToQosNetworksCache(networkQos.getQosPolicyId(), network);
            qosNeutronUtils.handleNeutronNetworkQosUpdate(network, networkQos.getQosPolicyId());
            if (qosNeutronUtils.hasBandwidthLimitRule(network)) {
                qosAlertManager.addToQosAlertCache(network);
            }

        }
    }
}


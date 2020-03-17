/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.qosservice.recovery.QosServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNeutronNetworkChangeListener extends AbstractClusteredAsyncDataTreeChangeListener<Network>
        implements RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronNetworkChangeListener.class);
    private final DataBroker dataBroker;
    private final QosNeutronUtils qosNeutronUtils;

    @Inject
    public QosNeutronNetworkChangeListener(final DataBroker dataBroker,
                                           final QosNeutronUtils qosNeutronUtils,
                                           final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                           final QosServiceRecoveryHandler qosServiceRecoveryHandler) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(Networks.class).child(Network.class),
                Executors.newListeningSingleThreadExecutor("QosNeutronNetworkChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.qosNeutronUtils = qosNeutronUtils;
        serviceRecoveryRegistry.addRecoverableListener(qosServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        LOG.trace("{} created",  getClass().getSimpleName());
    }

    public void init() {
        LOG.trace("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    public void registerListener() {
    }

    @Override
    public void deregisterListener() {
    }

    @Override
    public void remove(InstanceIdentifier<Network> instanceIdentifier, Network network) {
        qosNeutronUtils.removeFromNetworkCache(network);
    }

    @Override
    public void update(InstanceIdentifier<Network> instanceIdentifier, Network original, Network update) {
        qosNeutronUtils.addToNetworkCache(update);

        QosNetworkExtension updateQos = update.augmentation(QosNetworkExtension.class);
        QosNetworkExtension originalQos = original.augmentation(QosNetworkExtension.class);
        if (originalQos == null && updateQos != null) {
            // qosservice policy add
            qosNeutronUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            qosNeutronUtils.handleNeutronNetworkQosUpdate(update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !Objects.equals(originalQos.getQosPolicyId(), updateQos.getQosPolicyId())) {
            // qosservice policy update
            qosNeutronUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
            qosNeutronUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            qosNeutronUtils.handleNeutronNetworkQosUpdate(update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qosservice policy delete
            qosNeutronUtils.handleNeutronNetworkQosRemove(original, originalQos.getQosPolicyId());
            qosNeutronUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
        }
    }

    @Override
    public void add(InstanceIdentifier<Network> instanceIdentifier, Network network) {
        qosNeutronUtils.addToNetworkCache(network);

        QosNetworkExtension networkQos = network.augmentation(QosNetworkExtension.class);
        if (networkQos != null) {
            qosNeutronUtils.addToQosNetworksCache(networkQos.getQosPolicyId(), network);
            qosNeutronUtils.handleNeutronNetworkQosUpdate(network, networkQos.getQosPolicyId());
        }
    }
}


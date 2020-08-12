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
import org.opendaylight.netvirt.qosservice.recovery.QosServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNeutronNetworkChangeListener extends AsyncClusteredDataTreeChangeListenerBase<Network,
        QosNeutronNetworkChangeListener> implements RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronNetworkChangeListener.class);
    private final DataBroker dataBroker;
    private final QosNeutronUtils qosNeutronUtils;
    private  final QosEosHandler qosEosHandler;

    @Inject
    public QosNeutronNetworkChangeListener(final DataBroker dataBroker,
                                           final QosNeutronUtils qosNeutronUtils,
                                           final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                           final QosServiceRecoveryHandler qosServiceRecoveryHandler,
                                           final IdManagerService idManager,final  QosEosHandler qosEosHandler) {
        super(Network.class, QosNeutronNetworkChangeListener.class);
        this.dataBroker = dataBroker;
        this.qosNeutronUtils = qosNeutronUtils;
        this.qosEosHandler = qosEosHandler;
        serviceRecoveryRegistry.addRecoverableListener(qosServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        LOG.trace("{} created",  getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        registerListener();
        LOG.trace("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    public void registerListener() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
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
        qosNeutronUtils.removeFromNetworkCache(network);
    }

    @Override
    protected void update(InstanceIdentifier<Network> instanceIdentifier, Network original, Network update) {
        qosNeutronUtils.addToNetworkCache(update);

        QosNetworkExtension updateQos = update.getAugmentation(QosNetworkExtension.class);
        QosNetworkExtension originalQos = original.getAugmentation(QosNetworkExtension.class);
        if (originalQos == null && updateQos != null) {
            // qosservice policy add
            LOG.trace("New Qos policy applied on the Network {}", update.getUuid().getValue());
            qosNeutronUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            qosNeutronUtils.handleNeutronNetworkQosAdd(update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {
            // qosservice policy update
            LOG.trace("Qos policy updated on the Network {}", update.getUuid().getValue());
            qosNeutronUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
            qosNeutronUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            qosNeutronUtils.handleNeutronNetworkQosUpdate(update, originalQos.getQosPolicyId(),
                    updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qosservice policy delete
            LOG.trace("Qos policy removed on the Network {}", update.getUuid().getValue());
            qosNeutronUtils.handleNeutronNetworkQosRemove(original, originalQos.getQosPolicyId());
            qosNeutronUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Network> instanceIdentifier, Network network) {
        qosNeutronUtils.addToNetworkCache(network);

        QosNetworkExtension networkQos = network.getAugmentation(QosNetworkExtension.class);
        if (networkQos != null) {
            qosNeutronUtils.addToQosNetworksCache(networkQos.getQosPolicyId(), network);
            qosNeutronUtils.handleNeutronNetworkQosAdd(network, networkQos.getQosPolicyId());
        }
    }
}


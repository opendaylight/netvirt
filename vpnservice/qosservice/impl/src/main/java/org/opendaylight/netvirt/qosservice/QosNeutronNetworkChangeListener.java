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
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNeutronNetworkChangeListener extends AsyncDataTreeChangeListenerBase<Network,
        QosNeutronNetworkChangeListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronNetworkChangeListener.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final INeutronVpnManager neutronVpnManager;
    private final IMdsalApiManager mdsalUtils;


    @Inject
    public QosNeutronNetworkChangeListener(final DataBroker dataBroker,
                                           final INeutronVpnManager neutronVpnManager,
                                           final OdlInterfaceRpcService odlInterfaceRpcService,
                                           final IMdsalApiManager mdsalUtils) {
        super(Network.class, QosNeutronNetworkChangeListener.class);
        this.dataBroker = dataBroker;
        this.neutronVpnManager = neutronVpnManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.mdsalUtils = mdsalUtils;
        LOG.info("{} created",  getClass().getSimpleName());
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        LOG.info("{} init and registerListener done", getClass().getSimpleName());
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
        if (QosNeutronUtils.networkHasBandwidthLimitRule(network)) {
            QosAlertManager.removeNetworkFromQosAlertCache(network);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Network> instanceIdentifier, Network original, Network update) {
        QosNetworkExtension updateQos = update.getAugmentation(QosNetworkExtension.class);
        QosNetworkExtension originalQos = original.getAugmentation(QosNetworkExtension.class);
        if (originalQos == null && updateQos != null) {
            // qosservice policy add
            QosNeutronUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            QosNeutronUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService,
                    neutronVpnManager, mdsalUtils, update, updateQos.getQosPolicyId());
            if (QosNeutronUtils.networkHasBandwidthLimitRule(update)) {
                QosAlertManager.addNetworkToQosAlertCache(update);
            }
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {

            // qosservice policy update

            QosNeutronUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
            QosNeutronUtils.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            QosNeutronUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService,
                    neutronVpnManager, mdsalUtils, update, updateQos.getQosPolicyId());

            if (QosNeutronUtils.networkHasBandwidthLimitRule(original)
                                             && !QosNeutronUtils.networkHasBandwidthLimitRule(update)) {
                QosAlertManager.removeNetworkFromQosAlertCache(original);
            } else if (!QosNeutronUtils.networkHasBandwidthLimitRule(original)
                                              && QosNeutronUtils.networkHasBandwidthLimitRule(update)) {
                QosAlertManager.addNetworkToQosAlertCache(update);
            }

        } else if (originalQos != null && updateQos == null) {
            // qosservice policy delete
            if (QosNeutronUtils.networkHasBandwidthLimitRule(original)) {
                QosAlertManager.removeNetworkFromQosAlertCache(original);
            }
            QosNeutronUtils.handleNeutronNetworkQosRemove(dataBroker, odlInterfaceRpcService,
                    neutronVpnManager, mdsalUtils, original, originalQos.getQosPolicyId());
            QosNeutronUtils.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Network> instanceIdentifier, Network network) {
        QosNetworkExtension networkQos = network.getAugmentation(QosNetworkExtension.class);
        if (networkQos != null) {
            QosNeutronUtils.addToQosNetworksCache(networkQos.getQosPolicyId(), network);
            QosNeutronUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService,
                    neutronVpnManager, mdsalUtils, network, networkQos.getQosPolicyId());
            if (QosNeutronUtils.networkHasBandwidthLimitRule(network)) {
                QosAlertManager.addNetworkToQosAlertCache(network);
            }

        }
    }
}


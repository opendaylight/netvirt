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
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNeutronPortChangeListener extends AsyncClusteredDataTreeChangeListenerBase<Port,
                                             QosNeutronPortChangeListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final INeutronVpnManager neutronVpnManager;
    private  final IMdsalApiManager mdsalUtils;

    @Inject
    public QosNeutronPortChangeListener(final DataBroker dataBroker,
                                        final INeutronVpnManager neutronVpnManager,
                                        final OdlInterfaceRpcService odlInterfaceRpcService,
                                        final IMdsalApiManager mdsalUtils) {
        super(Port.class, QosNeutronPortChangeListener.class);
        this.dataBroker = dataBroker;
        this.neutronVpnManager = neutronVpnManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.mdsalUtils = mdsalUtils;
        LOG.debug("{} created",  getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        LOG.debug("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    protected QosNeutronPortChangeListener getDataTreeChangeListener() {
        return QosNeutronPortChangeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<Port> instanceIdentifier, Port port) {
        if (QosNeutronUtils.hasBandwidthLimitRule(neutronVpnManager, port)) {
            QosAlertManager.addToQosAlertCache(port);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Port> instanceIdentifier, Port port) {
        if (QosNeutronUtils.hasBandwidthLimitRule(neutronVpnManager, port)) {
            QosAlertManager.removeFromQosAlertCache(port);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Port> instanceIdentifier, Port original, Port update) {
        // check for QoS updates
        QosPortExtension updateQos = update.getAugmentation(QosPortExtension.class);
        QosPortExtension originalQos = original.getAugmentation(QosPortExtension.class);

        if (originalQos == null && updateQos != null) {
            // qosservice policy add
            QosNeutronUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            QosNeutronUtils.handleNeutronPortQosAdd(dataBroker, odlInterfaceRpcService, mdsalUtils,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {

            // qosservice policy update
            QosNeutronUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
            QosNeutronUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            QosNeutronUtils.handleNeutronPortQosUpdate(dataBroker, odlInterfaceRpcService, mdsalUtils,
                    update, updateQos.getQosPolicyId(), originalQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qosservice policy delete
            QosNeutronUtils.handleNeutronPortQosRemove(dataBroker, odlInterfaceRpcService, neutronVpnManager,
                    mdsalUtils, original, originalQos.getQosPolicyId());
            QosNeutronUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
        }

        if (QosNeutronUtils.hasBandwidthLimitRule(neutronVpnManager, original)
                                        && !QosNeutronUtils.hasBandwidthLimitRule(neutronVpnManager, update)) {
            QosAlertManager.removeFromQosAlertCache(original);
        } else if (!QosNeutronUtils.hasBandwidthLimitRule(neutronVpnManager, original)
                                          && QosNeutronUtils.hasBandwidthLimitRule(neutronVpnManager,update)) {
            QosAlertManager.addToQosAlertCache(update);
        }
    }
}

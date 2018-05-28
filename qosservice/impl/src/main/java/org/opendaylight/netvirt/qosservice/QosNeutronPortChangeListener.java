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
import org.opendaylight.genius.srm.RecoverableListener;
import org.opendaylight.genius.srm.ServiceRecoveryRegistry;
import org.opendaylight.netvirt.qosservice.recovery.QosServiceRecoveryHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNeutronPortChangeListener extends AsyncClusteredDataTreeChangeListenerBase<Port,
                                             QosNeutronPortChangeListener> implements RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final QosNeutronUtils qosNeutronUtils;

    @Inject
    public QosNeutronPortChangeListener(final DataBroker dataBroker,
            final QosNeutronUtils qosNeutronUtils, final QosServiceRecoveryHandler qosServiceRecoveryHandler,
                                        final ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(Port.class, QosNeutronPortChangeListener.class);
        this.dataBroker = dataBroker;
        this.qosNeutronUtils = qosNeutronUtils;
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
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    public void registerListener() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected QosNeutronPortChangeListener getDataTreeChangeListener() {
        return QosNeutronPortChangeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<Port> instanceIdentifier, Port port) {
        qosNeutronUtils.addToPortCache(port);
    }

    @Override
    protected void remove(InstanceIdentifier<Port> instanceIdentifier, Port port) {
        qosNeutronUtils.removeFromPortCache(port);
    }

    @Override
    protected void update(InstanceIdentifier<Port> instanceIdentifier, Port original, Port update) {
        qosNeutronUtils.addToPortCache(update);
        // check for QoS updates
        QosPortExtension updateQos = update.augmentation(QosPortExtension.class);
        QosPortExtension originalQos = original.augmentation(QosPortExtension.class);

        if (originalQos == null && updateQos != null) {
            // qosservice policy add
            qosNeutronUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            qosNeutronUtils.handleNeutronPortQosAdd(update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {

            // qosservice policy update
            qosNeutronUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
            qosNeutronUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            qosNeutronUtils.handleNeutronPortQosUpdate(update, updateQos.getQosPolicyId(),
                    originalQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qosservice policy delete
            qosNeutronUtils.handleNeutronPortQosRemove(original, originalQos.getQosPolicyId());
            qosNeutronUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
        }

    }
}

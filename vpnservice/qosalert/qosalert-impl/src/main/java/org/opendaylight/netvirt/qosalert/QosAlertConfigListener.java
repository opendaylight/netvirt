/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosalert;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.qosalert.config.rev161205.QosalertConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class QosAlertConfigListener  extends
        AsyncClusteredDataTreeChangeListenerBase<QosalertConfig, QosAlertConfigListener>  implements AutoCloseable   {

    private static final Logger LOG = LoggerFactory.getLogger(QosAlertConfigListener.class);
    private final DataBroker broker;
    private final QosAlertManager manager;
    private static QosAlertConfigListener instance;

    private QosAlertConfigListener(final DataBroker broker, final QosAlertManager manager) {
        super(QosalertConfig.class, QosAlertConfigListener.class);
        this.broker = broker;
        this.manager = manager;
        LOG.info("QosConfig Listener created");
    }

    public static QosAlertConfigListener getInstance(final DataBroker broker, final QosAlertManager manager) {
        LOG.info("QosConfig Listener getInstance");
        if (instance == null) {
            instance = new QosAlertConfigListener(broker, manager);
        }
        return (instance);
    }

    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
        LOG.info("Registration success");
    }


    @Override
    protected InstanceIdentifier<QosalertConfig> getWildCardPath() {
        return InstanceIdentifier.create(QosalertConfig.class);
    }

    @Override
    public void close() {
        super.close();
        LOG.info("QosConfig Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<QosalertConfig> identifier, QosalertConfig del) {
        LOG.info("QosalertConfig removed: {}", del);
        manager.restoreDefaultConfig();
    }

    @Override
    protected void update(InstanceIdentifier<QosalertConfig> identifier, QosalertConfig original,
                                                                                QosalertConfig update) {
        LOG.info("QosalertConfig changed to {}", update);
        manager.setQosalertConfig(update);
    }

    @Override
    protected void add(InstanceIdentifier<QosalertConfig> identifier, QosalertConfig add) {
        LOG.info("QosalertConfig added {}", add);
        manager.setQosalertConfig(add);
    }

    @Override
    protected QosAlertConfigListener getDataTreeChangeListener() {
        return this;
    }

}
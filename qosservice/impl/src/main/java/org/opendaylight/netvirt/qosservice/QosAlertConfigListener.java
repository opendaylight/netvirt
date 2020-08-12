/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qosalert.config.rev170301.QosalertConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class QosAlertConfigListener extends
    AbstractClusteredAsyncDataTreeChangeListener<QosalertConfig> {

    private static final Logger LOG = LoggerFactory.getLogger(QosAlertConfigListener.class);
    private final DataBroker dataBroker;
    private final QosAlertManager qosAlertManager;
    private final QosEosHandler qosEosHandler;

    @Inject
    public QosAlertConfigListener(final DataBroker dataBroker,
        final QosAlertManager qosAlertManager, final QosEosHandler qosEosHandler) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(QosalertConfig.class),
            Executors.newListeningSingleThreadExecutor("QosAlertConfigListener", LOG));
        this.dataBroker = dataBroker;
        this.qosAlertManager = qosAlertManager;
        this.qosEosHandler = qosEosHandler;
        LOG.trace("{} created",  getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        LOG.trace("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }
    @Override
    public void remove(InstanceIdentifier<QosalertConfig> identifier, QosalertConfig del) {
        LOG.debug("QosalertConfig removed: {}", del);
        qosAlertManager.restoreDefaultConfig();
    }

    @Override
    public void update(InstanceIdentifier<QosalertConfig> identifier, QosalertConfig original,
                                                                                QosalertConfig update) {
        LOG.debug("QosalertConfig changed to {}", update);
        qosAlertManager.setQosalertConfig(update);
    }

    @Override
    public void add(InstanceIdentifier<QosalertConfig> identifier, QosalertConfig add) {
        LOG.debug("QosalertConfig added {}", add);
        qosAlertManager.setQosalertConfig(add);
    }
}

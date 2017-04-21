/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.config.rev170410.NetvirtConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class QosAlertConfigListener  extends
        AsyncClusteredDataTreeChangeListenerBase<NetvirtConfig, QosAlertConfigListener>  implements AutoCloseable   {

    private static final Logger LOG = LoggerFactory.getLogger(QosAlertConfigListener.class);
    private final DataBroker dataBroker;
    private final QosAlertManager qosAlertManager;

    @Inject
    public QosAlertConfigListener(final DataBroker dataBroker, final QosAlertManager qosAlertManager) {
        super(NetvirtConfig.class, QosAlertConfigListener.class);
        this.dataBroker = dataBroker;
        this.qosAlertManager = qosAlertManager;
        LOG.info("{} created",  getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        LOG.info("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    protected InstanceIdentifier<NetvirtConfig> getWildCardPath() {
        return InstanceIdentifier.create(NetvirtConfig.class);
    }

    @Override
    protected void remove(InstanceIdentifier<NetvirtConfig> identifier, NetvirtConfig del) {
        LOG.info("QosalertConfig removed: {}", del);
        qosAlertManager.restoreDefaultConfig();
    }

    @Override
    protected void update(InstanceIdentifier<NetvirtConfig> identifier, NetvirtConfig original,
                          NetvirtConfig update) {
        LOG.info("QosalertConfig changed to {}", update);
        qosAlertManager.setQosalertConfig(update);
    }

    @Override
    protected void add(InstanceIdentifier<NetvirtConfig> identifier, NetvirtConfig add) {
        LOG.info("QosalertConfig added {}", add);
        qosAlertManager.setQosalertConfig(add);
    }

    @Override
    protected QosAlertConfigListener getDataTreeChangeListener() {
        return this;
    }

}

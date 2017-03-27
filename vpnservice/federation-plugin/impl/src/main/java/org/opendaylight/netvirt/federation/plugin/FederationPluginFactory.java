/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.federation.plugin.spi.IFederationPluginEgress;
import org.opendaylight.federation.plugin.spi.IPluginFactory;
import org.opendaylight.federation.service.api.IFederationProducerMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationPluginFactory implements IPluginFactory {
    private static final Logger LOG = LoggerFactory.getLogger(FederationPluginFactory.class);

    private final IFederationProducerMgr producerMgr;

    @Inject
    public FederationPluginFactory(final IFederationProducerMgr producerMgr) {
        this.producerMgr = producerMgr;
    }

    @PostConstruct
    public void init() {
        LOG.info("init");
        producerMgr.attachPluginFactory(FederationPluginConstants.PLUGIN_TYPE, this);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public IFederationPluginEgress createEgressPlugin(Object payload, String queueName, String contextId) {
        if (payload instanceof List) {
            List payloadList = (List) payload;
            for (Object pair : payloadList) {
                if (!(pair instanceof FederatedNetworkPair)) {
                    throw new IllegalArgumentException(
                            "payload expected to be List<FederatedNetworkPair> but was something else: "
                                    + pair.getClass().getName());
                }
            }
            return new FederationPluginEgress(producerMgr, (List<FederatedNetworkPair>) payload, queueName, contextId);
        } else {
            throw new IllegalArgumentException("payload expected to be List<FederatedNetworkPair>"
                    + " but was something else: " + payload.getClass().getName());
        }
    }
}

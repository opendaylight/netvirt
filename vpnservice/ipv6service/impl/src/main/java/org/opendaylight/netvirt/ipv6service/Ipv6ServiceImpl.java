/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6ServiceImpl {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceImpl.class);
    private final PacketProcessingService pktProcessingService;

    @Inject
    public Ipv6ServiceImpl(final PacketProcessingService pktProcessingService) {
        this.pktProcessingService = pktProcessingService;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        Ipv6RouterAdvt.setPacketProcessingService(pktProcessingService);
        Ipv6NeighborSolicitation.setPacketProcessingService(pktProcessingService);
    }
}

/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn.listeners;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class EvpnElanInstanceManager extends AsyncDataTreeChangeListenerBase<ElanInstance, EvpnElanInstanceManager>
        implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EvpnElanInstanceManager.class);

    private final DataBroker broker;
    private final EvpnUtils evpnUtils;

    @Inject
    public EvpnElanInstanceManager(final DataBroker dataBroker, EvpnUtils evpnUtils) {
        super(ElanInstance.class, EvpnElanInstanceManager.class);
        this.broker = dataBroker;
        this.evpnUtils = evpnUtils;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<ElanInstance> getWildCardPath() {
        return InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class);
    }

    @Override
    protected EvpnElanInstanceManager getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance elanInstanceAdded) {
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInstance> identifier, ElanInstance deletedElan) {
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {

        if (evpnUtils.isNetworkDetachedFromEvpn(original, update)) {
            LOG.info("Network {} is detached from EVPN, Withdrawing Routes ", original);
            evpnUtils.withdrawEvpnRT2Routes(original);
            return;
        }

        if ((evpnUtils.isNetDettachedFromL3vpn(original, update))
                && (evpnUtils.isEvpnPresent(original, update))) {
            LOG.info("Network {} is detached from L3vpn, Evpn is still present "
                    + "advertise RT2 bridging routes ", update);
            try {
                evpnUtils.advertiseEvpnRT2Route(update);
            } catch (Exception e) {
                LOG.error("isNetAttachedToL3vpn advertise route thors exception e {} ", e);
            }
            return;
        }

        if (evpnUtils.isNetAttachedToEvpn(original, update)) {
            LOG.info("Network {} is attached to either EVPN, advertise RT2 routes ", update);
            try {
                evpnUtils.advertiseEvpnRT2Route(update);
            } catch (Exception e) {
                LOG.error("isNetAttachedToL3vpn advertise route thors exception e {} ", e);
            }
            return;
        }

        if ((evpnUtils.isNetAttachedToL3vpn(original, update))
                && (evpnUtils.isEvpnPresent(original, update))) {
            LOG.info("Network {} is attached to either EVPN, advertise RT2 routes ", update);
            try {
                evpnUtils.advertiseEvpnRT2Route(update);
            } catch (Exception e) {
                LOG.error("isNetAttachedToL3vpn advertise route thors exception e {} ", e);
            }
            return;
        }

    }
}

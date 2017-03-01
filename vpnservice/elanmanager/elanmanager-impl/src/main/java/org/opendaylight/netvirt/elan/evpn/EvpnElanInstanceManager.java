/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.utils.ElanEvpnUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EvpnElanInstanceManager extends AsyncDataTreeChangeListenerBase<ElanInstance, EvpnElanInstanceManager>
        implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EvpnElanInstanceManager.class);

    private final DataBroker broker;
    private final ElanEvpnUtils elanEvpnUtils;

    @Inject
    public EvpnElanInstanceManager(final DataBroker dataBroker, ElanEvpnUtils elanEvpnUtils) {
        super(ElanInstance.class, EvpnElanInstanceManager.class);
        this.broker = dataBroker;
        this.elanEvpnUtils = elanEvpnUtils;
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
    protected void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {
        LOG.info("Evpn {} updated into Operational tree ", update);

        /*
        if ( evpn dettached ) {
            withdrawRoutes() // irrespective of l3vpn withdraw routes (A)
            return;
        }

        if (l3vpn detached && evpn is present ) {
            advertiseRoutes() rt2 bridging routes
            return;
        }
        if (evpn attached && l3vpn is present) || (l3vpn attached && evpn is present){
            advertiseRoutes() rt2 l3vni routes
            return;
        }

        */

        if (elanEvpnUtils.isNetworkDetachedFromEvpn(original, update)) {
            LOG.info("Network {} is detached from EVPN, Withdrawing Routes ", original);
            elanEvpnUtils.withdrawEVPNRT2Routes(original);
            return;
        }

        if ((elanEvpnUtils.isNetDettachedFromL3vpn(original, update))
                && (elanEvpnUtils.isEvpnPresent(original, update))){
            /*
            TODO confirm this behavior
            if (elanEvpnOriginal.getEvpn() != null) {
                withdrawEVPNRT2Routes(elanEvpnUpdated);
            }
            */
            LOG.info("Network {} is detached from L3vpn, Evpn is still present " +
                    "advertise RT2 bridging routes ", update);
            try {
                elanEvpnUtils.advertiseEVPNRT2Route(update);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (elanEvpnUtils.isNetAttachedToEvpn(original, update)){
            LOG.info("Network {} is attached to either EVPN, advertise RT2 routes ", update);
            try {
                elanEvpnUtils.advertiseEVPNRT2Route(update);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if ((elanEvpnUtils.isNetAttachedToL3vpn(original, update)) &&
                (elanEvpnUtils.isEvpnPresent(original, update))){
            LOG.info("Network {} is attached to either EVPN, advertise RT2 routes ", update);
            try {
                elanEvpnUtils.advertiseEVPNRT2Route(update);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

    }
}

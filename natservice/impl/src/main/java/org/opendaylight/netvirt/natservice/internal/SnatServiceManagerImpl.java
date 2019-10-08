/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.netvirt.natservice.api.SnatServiceListener;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SnatServiceManagerImpl implements SnatServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(SnatServiceManagerImpl.class);

    private final List<SnatServiceListener> snatServiceListeners = new CopyOnWriteArrayList<>();

    @Inject
    public SnatServiceManagerImpl(final SnatServiceImplFactory factory) {
        AbstractSnatService flatVlaSnatServiceImpl = factory.createFlatVlanSnatServiceImpl();
        if (flatVlaSnatServiceImpl != null) {
            addNatServiceListener(flatVlaSnatServiceImpl);
        }
        addNatServiceListener(factory.createFlatVlanIpv6ServiceImpl());
        AbstractSnatService vxlanGreSnatServiceImpl = factory.createVxlanGreSnatServiceImpl();
        if (vxlanGreSnatServiceImpl != null) {
            addNatServiceListener(vxlanGreSnatServiceImpl);
        }
    }

    private void addNatServiceListener(SnatServiceListener natServiceListner) {
        snatServiceListeners.add(natServiceListner);
    }

    @Override
    public void notify(TypedReadWriteTransaction<Datastore.Configuration> confTx,
                       Routers router, Routers oldRouter, Uint64 primarySwitchId, Uint64 dpnId, Action action)
            throws ExecutionException, InterruptedException {
        for (SnatServiceListener snatServiceListener : snatServiceListeners) {
            boolean result = false;
            switch (action) {
                //Enables or disables SNAT specific flows in the NAPT and NON-NAPT switches
                case SNAT_ALL_SWITCH_ENBL:
                    result = snatServiceListener.addSnatAllSwitch(confTx, router, primarySwitchId);
                    break;

                case SNAT_ALL_SWITCH_DISBL:
                    result = snatServiceListener.removeSnatAllSwitch(confTx, router, primarySwitchId);
                    break;

                case SNAT_ROUTER_ENBL:
                    result = snatServiceListener.addSnat(confTx, router, primarySwitchId, dpnId);
                    break;

                case SNAT_ROUTER_DISBL:
                    result = snatServiceListener.removeSnat(confTx, router, primarySwitchId, dpnId);
                    break;

                case SNAT_ROUTER_UPDATE:
                    result = snatServiceListener.handleRouterUpdate(confTx, oldRouter, router);
                    break;

                //Enables or disables flows to send the traffic to the NAT tables in NAPT switch and
                //the flows to send the traffic to the NAPT switch from a NON-NAPT switch.
                case CNT_ROUTER_ALL_SWITCH_ENBL:
                    result = snatServiceListener.addCentralizedRouterAllSwitch(confTx, router, primarySwitchId);
                    break;

                case CNT_ROUTER_ALL_SWITCH_DISBL:
                    result = snatServiceListener.removeCentralizedRouterAllSwitch(confTx, router, primarySwitchId);
                    break;

                case CNT_ROUTER_ENBL:
                    result = snatServiceListener.addCentralizedRouter(confTx, router, primarySwitchId, dpnId);
                    break;

                case CNT_ROUTER_DISBL:
                    result = snatServiceListener.removeCentralizedRouter(confTx, router, primarySwitchId, dpnId);
                    break;


                default:
                    break;
            }

            if (result) {
                LOG.debug("notify : Nat action {} invoking listener {} succeeded", action,
                    snatServiceListener.getClass().getName());
            } else {
                LOG.warn("notify : Nat action {} invoking listener {} failed",
                        action, snatServiceListener.getClass().getName());
            }
        }
    }

}

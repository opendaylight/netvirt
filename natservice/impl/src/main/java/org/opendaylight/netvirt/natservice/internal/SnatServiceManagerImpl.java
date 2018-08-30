/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.natservice.api.SnatServiceListener;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
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
        AbstractSnatService vxlanGreSnatServiceImpl = factory.createVxlanGreSnatServiceImpl();
        if (vxlanGreSnatServiceImpl != null) {
            addNatServiceListener(vxlanGreSnatServiceImpl);
        }
    }

    @Override
    public void addNatServiceListener(SnatServiceListener natServiceListner) {
        snatServiceListeners.add(natServiceListner);
    }

    @Override
    public void removeNatServiceListener(SnatServiceListener natServiceListner) {
        snatServiceListeners.remove(natServiceListner);
    }

    @Override
    public void notify(Routers router, Routers oldRouter, BigInteger primarySwitchId, BigInteger dpnId, Action action) {
        for (SnatServiceListener snatServiceListener : snatServiceListeners) {
            boolean result = false;
            switch (action) {
                //Enables or disables SNAT specific flows in the NAPT and NON-NAPT switches
                case SNAT_ALL_SWITCH_ENBL:
                    result = snatServiceListener.handleSnatAllSwitch(router, primarySwitchId, NwConstants.ADD_FLOW);
                    break;

                case SNAT_ALL_SWITCH_DISBL:
                    result = snatServiceListener.handleSnatAllSwitch(router, primarySwitchId, NwConstants.DEL_FLOW);
                    break;

                case SNAT_ROUTER_ENBL:
                    result = snatServiceListener.handleSnat(router, primarySwitchId, dpnId,  NwConstants.ADD_FLOW);
                    break;

                case SNAT_ROUTER_DISBL:
                    result = snatServiceListener.handleSnat(router, primarySwitchId, dpnId, NwConstants.DEL_FLOW);
                    break;

                //Enables or disables flows to send the traffic to the NAT tables in NAPT switch and
                //the flows to send the traffic to the NAPT switch from a NON-NAPT switch.
                case CNT_ROUTER_ALL_SWITCH_ENBL:
                    result = snatServiceListener.handleCentralizedRouterAllSwitch(router, primarySwitchId,
                            NwConstants.ADD_FLOW);
                    break;

                case CNT_ROUTER_ALL_SWITCH_DISBL:
                    result = snatServiceListener.handleCentralizedRouterAllSwitch(router, primarySwitchId,
                            NwConstants.DEL_FLOW);
                    break;

                case CNT_ROUTER_ENBL:
                    result = snatServiceListener.handleCentralizedRouter(router, primarySwitchId, dpnId,
                            NwConstants.ADD_FLOW);
                    break;

                case CNT_ROUTER_DISBL:
                    result = snatServiceListener.handleCentralizedRouter(router, primarySwitchId, dpnId,
                            NwConstants.DEL_FLOW);
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

/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.networkutils.VniUtils;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatOverVxlanUtil {

    private static final Logger LOG = LoggerFactory.getLogger(NatOverVxlanUtil.class);

    private VniUtils vniUtils;

    @Inject
    public NatOverVxlanUtil(final VniUtils vniUtils) {
        this.vniUtils = vniUtils;
    }

    public Uint32 getInternetVpnVni(String vpnUuid, Uint32 vpnid) {
        Uint32 internetVpnVni = Uint32.valueOf(getVNI(vpnUuid).longValue());
        if (internetVpnVni.longValue() == -1) {
            LOG.warn("getInternetVpnVni : Unable to obtain Internet Vpn VNI from VNI POOL for Vpn {}."
                    + "Will use tunnel_id {} as Internet VNI", vpnUuid, vpnid);
            return vpnid;
        }

        return internetVpnVni;
    }

    public Uint64 getRouterVni(String routerName, Uint32 routerId) {
        Uint64 routerVni = getVNI(routerName);
        if (routerVni.longValue() == -1) {
            LOG.warn("getRouterVni : Unable to obtain Router VNI from VNI POOL for router {}."
                    + "Router ID will be used as tun_id", routerName);
            return Uint64.valueOf(routerId);
        }
        return routerVni;
    }

    public void releaseVNI(String vniKey) {
        try {
            vniUtils.releaseVNI(vniKey);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("releaseVNI : Exception in release VNI for Key {}", vniKey, e);
        }
    }

    private Uint64 getVNI(String vniKey) {
        try {
            return vniUtils.getVNI(vniKey);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getVNI : Exception in get VNI for key {}", vniKey, e);
        }
        return Uint64.valueOf(-1);
    }
}

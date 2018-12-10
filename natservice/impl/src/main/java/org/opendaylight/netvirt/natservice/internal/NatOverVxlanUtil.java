/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.networkutils.VniUtils;
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

    public BigInteger getInternetVpnVni(String vpnUuid, long vpnid) {
        BigInteger internetVpnVni = getVNI(vpnUuid);
        if (internetVpnVni.longValue() == -1) {
            LOG.warn("getInternetVpnVni : Unable to obtain Internet Vpn VNI from VNI POOL for Vpn {}."
                    + "Will use tunnel_id {} as Internet VNI", vpnUuid, vpnid);
            return BigInteger.valueOf(vpnid);
        }

        return internetVpnVni;
    }

    public BigInteger getRouterVni(String routerName, long routerId) {
        BigInteger routerVni = getVNI(routerName);
        if (routerVni.longValue() == -1) {
            LOG.warn("getRouterVni : Unable to obtain Router VNI from VNI POOL for router {}."
                    + "Router ID will be used as tun_id", routerName);
            return BigInteger.valueOf(routerId);
        }
        return routerVni;
    }

    public void releaseVNI(String vniKey) {
        try {
            vniUtils.releaseVNI(vniKey);
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("releaseVNI : Exception in release VNI for Key {}", vniKey, e);
        }
    }

    private BigInteger getVNI(String vniKey) {
        try {
            vniUtils.getVNI(vniKey);
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("getVNI : Exception in get VNI for key {}", vniKey, e);
        }
        return BigInteger.valueOf(-1);
    }
}

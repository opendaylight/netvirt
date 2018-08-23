/*
 * Copyright (c) 2018 Alten Calsoft Labs India Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.iplearn.ipv6;

import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.AbstractIpLearnNotificationHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.Ipv6NdUtilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.NaReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6NaNotificationHandler extends AbstractIpLearnNotificationHandler implements Ipv6NdUtilListener {

    private static final Logger LOG = LoggerFactory.getLogger(Ipv6NaNotificationHandler.class);

    @Inject
    public Ipv6NaNotificationHandler(VpnConfig vpnConfig, VpnUtil vpnUtil) {
        super(vpnConfig, vpnUtil);
    }

    @Override
    public void onNaReceived(NaReceived naPacket) {
        String srcInterface = naPacket.getInterface();
        IpAddress srcIP = new IpAddress(naPacket.getSourceIpv6());
        MacAddress srcMac = naPacket.getSourceMac();
        IpAddress targetIP = new IpAddress(naPacket.getTargetAddress());
        BigInteger metadata = naPacket.getMetadata();
        LOG.debug("NA notification received from interface {} and IP {} having MAC {}, targetIP={}", srcInterface,
                srcIP.stringValue(), srcMac.getValue(), targetIP.stringValue());

        validateAndProcessIpLearning(srcInterface, srcIP, srcMac, targetIP, metadata);
    }
}

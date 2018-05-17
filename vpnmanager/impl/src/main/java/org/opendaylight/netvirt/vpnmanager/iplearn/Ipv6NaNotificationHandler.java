/*
 * Copyright (c) 2018 Alten Calsoft Labs India Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.iplearn;

import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.ipv6util.rev170210.Ipv6NdutilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.ipv6util.rev170210.NaReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6NaNotificationHandler extends AbstractIpLearnNotificationHandler implements Ipv6NdutilListener {

    private static final Logger LOG = LoggerFactory.getLogger(Ipv6NaNotificationHandler.class);

    @Inject
    public Ipv6NaNotificationHandler(DataBroker dataBroker, IdManagerService idManager, IInterfaceManager interfaceManager,
            VpnConfig vpnConfig) {
        super(dataBroker, idManager, interfaceManager, vpnConfig);
    }

    @Override
    public void onNaReceived(NaReceived naPacket) {
        String srcInterface = naPacket.getInterface();
        IpAddress srcIP = new IpAddress(naPacket.getSourceIpv6());
        MacAddress srcMac = naPacket.getSourceMac();
        IpAddress targetIP = new IpAddress(naPacket.getTargetAddress());
        BigInteger metadata = naPacket.getMetadata();
        LOG.debug("NA notification received from interface {} and IP {} having MAC {}, targetIP={}", srcInterface,
                String.valueOf(srcIP.getValue()), srcMac.getValue(), String.valueOf(targetIP.getValue()));

        validateAndProcessIpLearning(srcInterface, srcIP, srcMac, targetIP, metadata);
    }
}

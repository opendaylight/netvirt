/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn.ipv4;

import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.AbstractIpLearnNotificationHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ArpNotificationHandler extends AbstractIpLearnNotificationHandler implements OdlArputilListener {

    private static final Logger LOG = LoggerFactory.getLogger(ArpNotificationHandler.class);

    @Inject
    public ArpNotificationHandler(VpnConfig vpnConfig, VpnUtil vpnUtil, INeutronVpnManager neutronVpnManager) {
        super(vpnConfig, vpnUtil, neutronVpnManager);
    }

    @Override
    public void onMacChanged(MacChanged notification) {

    }

    @Override
    public void onArpRequestReceived(ArpRequestReceived notification) {
        String srcInterface = notification.getInterface();
        IpAddress srcIP = notification.getSrcIpaddress();
        MacAddress srcMac = MacAddress.getDefaultInstance(notification.getSrcMac().getValue());
        IpAddress targetIP = notification.getDstIpaddress();
        BigInteger metadata = notification.getMetadata().toJava();
        boolean isGarp = srcIP.equals(targetIP);
        if (!isGarp) {
            LOG.info(
                    "ArpNotification Non-Gratuitous Request Received from "
                            + "interface {} and IP {} having MAC {} target destination {}, ignoring..",
                    srcInterface, srcIP.stringValue(), srcMac.getValue(), targetIP.stringValue());
            return;
        }
        LOG.info(
                "ArpNotification Gratuitous Request Received from interface {} and IP {} having MAC {} "
                        + "target destination {}, learning MAC",
                srcInterface, srcIP.stringValue(), srcMac.getValue(), targetIP.stringValue());

        processIpLearning(srcInterface, srcIP, srcMac, metadata, targetIP);
    }

    @Override
    public void onArpResponseReceived(ArpResponseReceived notification) {
        String srcInterface = notification.getInterface();
        IpAddress srcIP = notification.getSrcIpaddress();
        MacAddress srcMac = MacAddress.getDefaultInstance(notification.getSrcMac().getValue());
        BigInteger metadata = notification.getMetadata().toJava();
        IpAddress targetIP = notification.getDstIpaddress();
        LOG.info("ArpNotification Response Received from interface {} and IP {} having MAC {}, learning MAC",
                srcInterface, srcIP.stringValue(), srcMac.getValue());

        validateAndProcessIpLearning(srcInterface, srcIP, srcMac, targetIP, metadata);
    }
}

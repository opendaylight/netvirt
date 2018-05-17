/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ArpNotificationHandler extends AbstractIpLearnNotificationHandler implements OdlArputilListener {
    private static final Logger LOG = LoggerFactory.getLogger(ArpNotificationHandler.class);

    @Inject
    public ArpNotificationHandler(DataBroker dataBroker, IdManagerService idManager,
                                  IInterfaceManager interfaceManager, VpnConfig vpnConfig) {
        super(dataBroker, idManager, interfaceManager, vpnConfig);
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
        BigInteger metadata = notification.getMetadata();
        boolean isGarp = srcIP.equals(targetIP);
        if (!isGarp) {
            LOG.info("ArpNotification Non-Gratuitous Request Received from "
                      + "interface {} and IP {} having MAC {} target destination {}, ignoring..",
                    srcInterface, srcIP.getIpv4Address().getValue(),srcMac.getValue(),
                    targetIP.getIpv4Address().getValue());
            return;
        }
        LOG.info("ArpNotification Gratuitous Request Received from "
                  + "interface {} and IP {} having MAC {} target destination {}, learning MAC",
                  srcInterface, srcIP.getIpv4Address().getValue(),srcMac.getValue(),
                  targetIP.getIpv4Address().getValue());
        processIpLearning(srcInterface, srcIP, srcMac, metadata, targetIP);
    }

    @Override
    public void onArpResponseReceived(ArpResponseReceived notification) {
        String srcInterface = notification.getInterface();
        IpAddress srcIP = notification.getSrcIpaddress();
        MacAddress srcMac = MacAddress.getDefaultInstance(notification.getSrcMac().getValue());
        LOG.info("ArpNotification Response Received from interface {} and IP {} having MAC {}, learning MAC",
                srcInterface, srcIP.getIpv4Address().getValue(), srcMac.getValue());
        List<Adjacency> adjacencies = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker, srcInterface);
        if (adjacencies != null) {
            for (Adjacency adj : adjacencies) {
                String ipAddress = adj.getIpAddress();
                try {
                    if (NWUtil.isIpInSubnet(NWUtil.ipAddressToInt(srcIP.getIpv4Address().getValue()), ipAddress)) {
                        return;
                    }
                } catch (UnknownHostException e) {
                    LOG.error("Subnet string {} not convertible to InetAdddress", srcIP, e);
                }
            }
        }
        BigInteger metadata = notification.getMetadata();
        IpAddress targetIP = notification.getDstIpaddress();
        LOG.trace("ArpNotification Response Received from interface {} and IP {} having MAC {}, learning MAC",
                srcInterface, srcIP.getIpv4Address().getValue(), srcMac.getValue());
        processIpLearning(srcInterface, srcIP, srcMac, metadata, targetIP);
    }
}

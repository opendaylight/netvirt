/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpNotificationHandler implements OdlArputilListener {

    VpnInterfaceManager vpnIfManager;
    DataBroker broker;

    private static final Logger LOG = LoggerFactory.getLogger(ArpNotificationHandler.class);

    public ArpNotificationHandler(VpnInterfaceManager vpnIfMgr, DataBroker dataBroker) {
        vpnIfManager = vpnIfMgr;
        broker = dataBroker;
    }
    
    public void onMacChanged(MacChanged notification){

    }

    public void onArpRequestReceived(ArpRequestReceived notification){
        String srcInterface = notification.getInterface();
        IpAddress srcIP = notification.getSrcIpaddress();
        PhysAddress srcMac = notification.getSrcMac();
        IpAddress targetIP = notification.getDstIpaddress();

        // Respond to ARP request only if vpnservice is configured on the interface
        if(VpnUtil.isVpnInterfaceConfigured(broker, srcInterface)) {
            LOG.info("Received ARP Request for interface {} ", srcInterface);
            vpnIfManager.processArpRequest(srcIP, srcMac, targetIP, srcInterface);
        }
    }
     
    public void onArpResponseReceived(ArpResponseReceived notification){

    }

}

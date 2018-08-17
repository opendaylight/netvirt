package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;

@Singleton
public class NatArpNotificationHandler implements OdlArputilListener {

    private static final Logger LOG = LoggerFactory.getLogger(NatArpNotificationHandler.class);

    private final IElanService elanService;

    @Inject
    public NatArpNotificationHandler(final IElanService elanService) {
        LOG.warn("JOSH NatArp creation");

        this.elanService = elanService;
    }

    @Override
    public void onArpResponseReceived(ArpResponseReceived notification) {
        LOG.warn("JOSH NatArp Response (?) {}", notification);
        ElanInterface elanInterface = elanService.getElanInterfaceByElanInterfaceName(notification.getInterface());
        LOG.warn("JOSH elanInterface is {}", elanInterface);
        /*
        1. Get the port that sent the arp
        2. Retrieve that port's elan
        3. Get all ports in that elan
        4. Loop through to get the one that has that IP
        5. Get the DPN.
        6. Configure NAT
         */
    }

    @Override
    public void onMacChanged(MacChanged notification) {

    }

    @Override
    public void onArpRequestReceived(ArpRequestReceived notification) {
        LOG.warn("JOSH NatArp Request (?) {}", notification);

        IpAddress srcIp = notification.getSrcIpaddress();
        if (srcIp == null || !Objects.equals(srcIp, notification.getDstIpaddress())) {
            LOG.debug("NatArpNotificationHandler: ignoring ARP packet, not gratuitous {}", notification);
            return;
        }

        ElanInterface arpSenderIfc = elanService.getElanInterfaceByElanInterfaceName(notification.getInterface());
        if (ipBelongsToElanInterface(arpSenderIfc, srcIp)) {
            LOG.debug("NatArpNotificationHandler: ignoring GARP packet. No need to NAT a port's static IP. {}",
                    notification);
            return;
        }

        LOG.warn("JOSH elanInterface is {}", arpSenderIfc);
        ElanInterface targetIfc = null;
        for(String ifcName : elanService.getElanInterfaces(arpSenderIfc.getElanInstanceName())) {
             ElanInterface elanInterface = elanService.getElanInterfaceByElanInterfaceName(ifcName);
             if (ipBelongsToElanInterface(elanInterface, srcIp)) {
                 targetIfc = elanInterface;
                 break;
             }
        }

        if (null == targetIfc) {
            LOG.warn("NatArpNotificationHandler: GARP does not correspond to an interface in this elan {}",
                     notification);
            return;
        }

    }

    private boolean ipBelongsToElanInterface(ElanInterface elanInterface, IpAddress ip) {
        for (StaticMacEntries staticMacEntries :  elanInterface.getStaticMacEntries()) {
            if (Objects.equals(staticMacEntries.getIpPrefix(), ip)) {
                return true;
            }
        }
        return false;
    }
}

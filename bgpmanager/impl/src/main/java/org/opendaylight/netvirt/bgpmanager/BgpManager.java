/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.bgpmanager.oam.BgpAlarmErrorCodes;
import org.opendaylight.netvirt.bgpmanager.oam.BgpConstants;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BgpManager implements AutoCloseable, IBgpManager {
    private static final Logger LOG = LoggerFactory.getLogger(BgpManager.class);
    private final BgpConfigurationManager bcm;

    private final FibDSWriter fibDSWriter;
    private volatile long qbgprestartTS = 0;

    @Inject
    public BgpManager(final BgpConfigurationManager bcm, final FibDSWriter fibDSWriter) {
        this.bcm = bcm;
        this.fibDSWriter = fibDSWriter;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    @Override
    public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts,
            AddressFamily addressFamily) {
        bcm.addVrf(rd, new ArrayList<>(importRts), new ArrayList<>(exportRts),  addressFamily);
    }

    @Override
      public void deleteVrf(String rd, boolean removeFibTable, AddressFamily addressFamily) {
        if (removeFibTable) {
            LOG.info("deleteVrf: suppressing FIB from rd {} with {}", rd, addressFamily);
            fibDSWriter.removeVrfSubFamilyFromDS(rd, addressFamily);
        }
        if (bcm.delVrf(rd, addressFamily) && removeFibTable) {
            fibDSWriter.removeVrfFromDS(rd);
        }
    }

    @Override
    public void addPrefix(String rd, String macAddress, String prefix, List<String> nextHopList,
                          VrfEntry.EncapType encapType, int vpnLabel, long l3vni,
                          String gatewayMac, RouteOrigin origin) {
        fibDSWriter.addFibEntryToDS(rd, prefix, nextHopList,
                encapType, vpnLabel, l3vni, gatewayMac, origin);
        bcm.addPrefix(rd, macAddress, prefix, nextHopList,
                encapType, vpnLabel, l3vni, 0 /*l2vni*/, gatewayMac);
    }

    @Override
    public void addPrefix(String rd, String macAddress, String prefix, String nextHop, VrfEntry.EncapType encapType,
                          int vpnLabel, long l3vni, String gatewayMac, RouteOrigin origin) {
        addPrefix(rd, macAddress, prefix, Collections.singletonList(nextHop), encapType, vpnLabel, l3vni,
                gatewayMac, origin);
    }

    @Override
    public void deletePrefix(String rd, String prefix) {
        fibDSWriter.removeFibEntryFromDS(rd, prefix);
        bcm.delPrefix(rd, prefix);
    }

    @Override
    public void advertisePrefix(String rd, String macAddress, String prefix, List<String> nextHopList,
                                VrfEntry.EncapType encapType, long vpnLabel, long l3vni, long l2vni,
                                String gatewayMac) {
        LOG.info("Advertise Prefix: Adding Prefix rd {} prefix {} label {} l3vni {} l2vni {}",
                rd, prefix, vpnLabel, l3vni, l2vni);
        bcm.addPrefix(rd, macAddress, prefix, nextHopList,
                encapType, vpnLabel, l3vni, l2vni, gatewayMac);
        LOG.info("Advertise Prefix: Added Prefix rd {} prefix {} label {} l3vni {} l2vni {}",
                rd, prefix, vpnLabel, l3vni, l2vni);
    }

    @Override
    public void advertisePrefix(String rd, String macAddress, String prefix, String nextHop,
                                VrfEntry.EncapType encapType, long vpnLabel, long l3vni, long l2vni,
                                String gatewayMac) {
        LOG.info("ADVERTISE: Adding Prefix rd {} prefix {} nexthop {} label {} l3vni {} l2vni {}",
                rd, prefix, nextHop, vpnLabel, l3vni, l2vni);
        bcm.addPrefix(rd, macAddress, prefix, Collections.singletonList(nextHop), encapType,
                vpnLabel, l3vni, l2vni, gatewayMac);
        LOG.info("ADVERTISE: Added Prefix rd {} prefix {} nexthop {} label {} l3vni {} l2vni {}",
                rd, prefix, nextHop, vpnLabel, l3vni, l2vni);
    }

    @Override
    public void withdrawPrefix(String rd, String prefix) {
        LOG.info("WITHDRAW: Removing Prefix rd {} prefix {} afi {}", rd, prefix);
        bcm.delPrefix(rd, prefix);
        LOG.info("WITHDRAW: Removed Prefix rd {} prefix {} afi {}", rd, prefix);
    }

    @Override
    public String getDCGwIP() {
        Bgp conf = bcm.getConfig();
        if (conf == null) {
            return null;
        }
        List<Neighbors> nbrs = conf.getNeighbors();
        if (nbrs == null) {
            return null;
        }
        return nbrs.get(0).getAddress().getValue();
    }

    @Override
    // This method doesn't actually do any real work currently but may at some point so suppress FindBugs violation.
    @SuppressFBWarnings("UC_USELESS_VOID_METHOD")
    public synchronized void sendNotificationEvent(int code, int subcode) {
        if (code != BgpConstants.BGP_NOTIFY_CEASE_CODE) {
            // CEASE Notifications alone have to be reported to the CBA.
            // Silently return here. No need to log because tons
            // of non-alarm notifications will be sent to the SDNc.
            return;
        }
        BgpAlarmErrorCodes errorSubCode = BgpAlarmErrorCodes.checkErrorSubcode(subcode);
        if (errorSubCode == BgpAlarmErrorCodes.ERROR_IGNORE) {
            // Need to report only those subcodes, defined in
            // BgpAlarmErrorCodes enum class.
            return;
        }
    }

    @Override
    public void bgpRestarted() {
        bcm.bgpRestarted();
    }

    public long getQbgprestartTS() {
        return qbgprestartTS;
    }

    @Override
    public void setQbgprestartTS(long qbgprestartTS) {
        this.qbgprestartTS = qbgprestartTS;
    }
}

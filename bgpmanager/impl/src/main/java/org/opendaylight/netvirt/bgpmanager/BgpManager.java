/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.bgpmanager.oam.BgpAlarmErrorCodes;
import org.opendaylight.netvirt.bgpmanager.oam.BgpConstants;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.TcpMd5SignaturePasswordType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BgpManager implements AutoCloseable, IBgpManager {
    private static final Logger LOG = LoggerFactory.getLogger(BgpManager.class);
    private final BgpConfigurationManager bcm;

    private final FibDSWriter fibDSWriter;
    private final DataBroker dataBroker;
    private volatile long qbgprestartTS = 0;

    @Inject
    public BgpManager(final BgpConfigurationManager bcm, final FibDSWriter fibDSWriter, final DataBroker dataBroker) {
        this.bcm = bcm;
        this.fibDSWriter = fibDSWriter;
        this.dataBroker = dataBroker;
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

    public BgpConfigurationManager getBgpConfigurationManager() {
        return bcm;
    }

    public void configureGR(int stalepathTime) {
        bcm.addGracefulRestart(stalepathTime);
    }

    public void delGracefulRestart() {
        bcm.delGracefulRestart();
    }

    public void addNeighbor(String ipAddress, long asNum,
            @Nullable final TcpMd5SignaturePasswordType md5Password) {
        bcm.addNeighbor(ipAddress, asNum, md5Password);
    }

    public void addEbgpMultihop(String ipAddress, int nhops) {
        bcm.addEbgpMultihop(ipAddress, nhops);
    }

    public void addUpdateSource(String ipAddress, String srcIp) {
        bcm.addUpdateSource(ipAddress, srcIp);
    }

    public void addAddressFamily(String ipAddress, af_afi afi, af_safi safi) {
        bcm.addAddressFamily(ipAddress, afi.getValue(), safi.getValue());
    }

    public void deleteNeighbor(String ipAddress) {
        bcm.delNeighbor(ipAddress);
    }

    @Override
    public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts,
            AddressFamily addressFamily) {
        bcm.addVrf(rd, new ArrayList<>(importRts), new ArrayList<>(exportRts),  addressFamily);
    }

    @Override
      public void deleteVrf(String rd, boolean removeFibTable, AddressFamily addressFamily) {
        boolean ret = false;
        if (removeFibTable) {
            LOG.info("deleteVrf: suppressing FIB from rd {} with {}", rd, addressFamily);
            fibDSWriter.removeVrfSubFamilyFromDS(rd, addressFamily);
        }
        ret = bcm.delVrf(rd, addressFamily);
        if (ret && removeFibTable) {
            fibDSWriter.removeVrfFromDS(rd);
        }
    }

    @Override
    public void deleteVrfwithPrefix(String rd) {
        Bgp config = getConfig();
        if (config == null) {
            LOG.info("deleteVrfwithPrefix for RD {}: No BGP configs present", rd);
            return;
        }
        List<Networks> ln = config.getNetworks();
        if (ln != null) {
            for (Networks net : ln) {
                if (net.getRd().equals(rd)) {
                    bcm.delPrefix(rd, net.getPrefixLen());
                    LOG.info("deleteVrfwithPrefix: Removed Prefix rd {} prefix {}",
                            rd, net.getPrefixLen());
                }
            }
        }
        bcm.delVrf(rd);
        LOG.info("deleteVrfwithPrefix: Removed vrf {}", rd);
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
        LOG.info("WITHDRAW: Removing Prefix rd {} prefix {}", rd, prefix);
        bcm.delPrefix(rd, prefix);
        LOG.info("WITHDRAW: Removed Prefix rd {} prefix {}", rd, prefix);
    }

    @Override
    public void withdrawPrefixIfPresent(String rd, String prefix) {
        InstanceIdentifier<Networks> networksId = InstanceIdentifier.builder(Bgp.class).child(Networks.class,
                new NetworksKey(rd, prefix)).build();
        try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
            Futures.addCallback(tx.read(LogicalDatastoreType.CONFIGURATION, networksId),
                new FutureCallback<Optional<Networks>>() {
                    @Override
                    public void onSuccess(@Nullable Optional<Networks> networks) {
                        if (networks != null && networks.isPresent()) {
                            LOG.info("withdrawPrefixIfPresent: ebgp networks present for rd {} prefix {}"
                                    + ". Withdrawing..", networks.get().getRd(), networks.get().getPrefixLen());
                            withdrawPrefix(rd, prefix);
                        } else {
                            LOG.error("withdrawPrefixIfPresent: ebgp networks not found for rd {} prefix {}",
                                    rd, prefix);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        LOG.error("withdrwaPrefixIfPresent: Failed to retrieve ebgp networks for rd {} prefix {}",
                                rd, prefix, throwable);
                    }
                });
        }
    }

    @Override
    public void setQbgpLog(String fileName, String debugLevel) {
        bcm.addLogging(fileName, debugLevel);
    }

    public void delLogging() {
        bcm.delLogging();
    }

    public void startBgp(long asn, String routerId, int spt, boolean fbit) {
        bcm.startBgp(asn, routerId, spt, fbit);
    }

    public void stopBgp() {
        bcm.stopBgp();
    }

    public void startConfig(String host, int port) {
        bcm.startConfig(host, port);
    }

    public void stopConfig() {
        bcm.stopConfig();
    }

    public Bgp getConfig() {
        return bcm.getConfig();
    }


    public void enableMultipath(af_afi afi, af_safi safi) {
        bcm.setMultipathStatus(afi, safi,true);
    }

    public void disableMultipath(af_afi afi, af_safi safi) {
        bcm.setMultipathStatus(afi, safi, false);
    }

    public void multipaths(String rd, int maxpath) {
        bcm.multipaths(rd, maxpath);
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

    public FibDSWriter getFibWriter() {
        return fibDSWriter;
    }

    public String getConfigHost() {
        return bcm.getConfigHost();
    }

    public int getConfigPort() {
        return bcm.getConfigPort();
    }


    @Override
    public void bgpRestarted() {
        bcm.bgpRestarted();
    }

    public BgpManager getBgpManager() {
        return this;
    }

    public boolean isBgpConnected() {
        return bcm.isBgpConnected();
    }

    public long getLastConnectedTS() {
        return bcm.getLastConnectedTS();
    }

    public long getConnectTS() {
        return bcm.getConnectTS();
    }

    public long getStartTS() {
        return bcm.getStartTS();
    }

    public long getQbgprestartTS() {
        return qbgprestartTS;
    }

    @Override
    public void setQbgprestartTS(long qbgprestartTS) {
        this.qbgprestartTS = qbgprestartTS;
    }

    public long getStaleStartTime() {
        return bcm.getStaleStartTime();
    }

    public long getStaleEndTime() {
        return bcm.getStaleEndTime();
    }

    public long getCfgReplayStartTime() {
        return bcm.getCfgReplayStartTime();
    }

    public long getCfgReplayEndTime() {
        return bcm.getCfgReplayEndTime();
    }

    public long getStaleCleanupTime() {
        return bcm.getStaleCleanupTime();
    }


}

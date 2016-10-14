/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import org.apache.thrift.TException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.bgpmanager.oam.BgpAlarmBroadcaster;
import org.opendaylight.netvirt.bgpmanager.oam.BgpAlarmErrorCodes;
import org.opendaylight.netvirt.bgpmanager.oam.BgpAlarms;
import org.opendaylight.netvirt.bgpmanager.oam.BgpConstants;
import org.opendaylight.netvirt.bgpmanager.oam.BgpCounters;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.LayerType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpManager implements AutoCloseable, IBgpManager {
    private static final Logger LOG = LoggerFactory.getLogger(BgpManager.class);
    private final DataBroker dataBroker;
    private final BgpConfigurationManager bcm;
    private final BgpAlarmBroadcaster qbgpAlarmProducer;
    private final FibDSWriter fibDSWriter;
    private long qbgprestartTS = 0;
    public Timer bgpAlarmsTimer;
    public BgpAlarms bgpAlarms;
    public BgpCounters bgpCounters;

    public BgpManager(final DataBroker dataBroker,
            final BgpConfigurationManager bcm,
            final BgpAlarmBroadcaster bgpAlarmProducer,
            final FibDSWriter fibDSWriter) {
        this.dataBroker = dataBroker;
        this.bcm = bcm;
        this.qbgpAlarmProducer = bgpAlarmProducer;
        this.fibDSWriter = fibDSWriter;
    }

    public void init() {
        BgpUtil.setBroker(dataBroker);
        ConfigureBgpCli.setBgpManager(this);
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public void configureGR(int stalepathTime) throws TException {
        bcm.addGracefulRestart(stalepathTime);
    }

    public void delGracefulRestart() throws Exception {
        bcm.delGracefulRestart();
    }

    public void addNeighbor(String ipAddress, long asNum) throws TException {
        bcm.addNeighbor(ipAddress, asNum);
    }

    public void addEbgpMultihop(String ipAddress, int nhops) throws TException {
        bcm.addEbgpMultihop(ipAddress, nhops);
    }

    public void addUpdateSource(String ipAddress, String srcIp) throws TException {
        bcm.addUpdateSource(ipAddress, srcIp);
    }

    public void addAddressFamily(String ipAddress, af_afi afi, af_safi safi) throws TException {
        bcm.addAddressFamily(ipAddress, afi.getValue(), safi.getValue());
    }

    public void deleteNeighbor(String ipAddress) throws TException {
        bcm.delNeighbor(ipAddress);
    }

    @Override
    public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts,
                       LayerType layerType) throws Exception {
        bcm.addVrf(rd, new ArrayList<>(importRts), new ArrayList<>(exportRts), layerType);
    }

    @Override
    public void deleteVrf(String rd, boolean removeFibTable) {
        if (removeFibTable) {
            fibDSWriter.removeVrfFromDS(rd);
        }
        bcm.delVrf(rd);
    }

    @Override
    public void addPrefix(String rd, String macAddress, String prefix, List<String> nextHopList,
                          VrfEntry.EncapType encapType, int vpnLabel, long l3vni,
                          String gatewayMac, RouteOrigin origin)
            throws Exception {
        fibDSWriter.addFibEntryToDS(rd, macAddress, prefix, nextHopList,
                encapType, vpnLabel, l3vni, gatewayMac, origin);
        bcm.addPrefix(rd, macAddress, prefix, nextHopList,
                encapType, vpnLabel, l3vni, gatewayMac);
    }

    @Override
    public void addPrefix(String rd, String macAddress, String prefix, String nextHop, VrfEntry.EncapType encapType,
                          int vpnLabel, long l3vni, String gatewayMac, RouteOrigin origin) throws Exception {
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
                                VrfEntry.EncapType encapType, int vpnLabel, long l3vni,
                                String gatewayMac) throws Exception {
        bcm.addPrefix(rd, macAddress, prefix, nextHopList,
                encapType, vpnLabel, l3vni, gatewayMac);
    }

    @Override
    public void advertisePrefix(String rd, String macAddress, String prefix, String nextHop,
                                VrfEntry.EncapType encapType, int vpnLabel, long l3vni,
                                String gatewayMac) throws Exception {
        LOG.info("ADVERTISE: Adding Prefix rd {} prefix {} nexthop {} label {}", rd, prefix, nextHop, vpnLabel);
        bcm.addPrefix(rd, macAddress, prefix, Collections.singletonList(nextHop), encapType,
                vpnLabel, l3vni, gatewayMac);
        LOG.info("ADVERTISE: Added Prefix rd {} prefix {} nexthop {} label {}", rd, prefix, nextHop, vpnLabel);
    }

    @Override
    public void withdrawPrefix(String rd, String prefix) {
        LOG.info("WITHDRAW: Removing Prefix rd {} prefix {}", rd, prefix);
        bcm.delPrefix(rd, prefix);
        LOG.info("WITHDRAW: Removed Prefix rd {} prefix {}", rd, prefix);
    }

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

    public synchronized void sendNotificationEvent(String pfx, int code, int subcode) {
        BgpAlarmErrorCodes errorSubCode;
        if (code != BgpConstants.BGP_NOTIFY_CEASE_CODE) {
            // CEASE Notifications alone have to be reported to the CBA.
            // Silently return here. No need to log because tons
            // of non-alarm notifications will be sent to the SDNc.
            return;
        }
        errorSubCode = BgpAlarmErrorCodes.checkErrorSubcode(subcode);
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
        return BgpConfigurationManager.getConfigHost();
    }

    public int getConfigPort() {
        return BgpConfigurationManager.getConfigPort();
    }


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

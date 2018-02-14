/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.oam;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpAlarms implements Runnable, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(BgpAlarms.class);
    private static final String ALARM_TEXT = "Bgp Neighbor TCP connection is down";

    private final BgpJMXAlarmAgent alarmAgent = new BgpJMXAlarmAgent();
    private final BgpConfigurationManager bgpMgr;
    private final Map<String, BgpAlarmStatus> neighborsRaisedAlarmStatusMap = new ConcurrentHashMap<>();

    public BgpAlarms(BgpConfigurationManager bgpManager) {
        bgpMgr = Objects.requireNonNull(bgpManager);
    }

    public void init() {
        alarmAgent.registerMbean();

        if (bgpMgr.getConfig() != null) {
            List<Neighbors> nbrs = bgpMgr.getConfig().getNeighbors();
            if (nbrs != null) {
                for (Neighbors nbr : nbrs) {
                    LOG.trace("Clearing Neighbor DOWN alarm at the startup for Neighbor {}",
                            nbr.getAddress().getValue());
                    clearBgpNbrDownAlarm(nbr.getAddress().getValue());
                }
            }
        }
    }

    @Override
    public void close() {
        alarmAgent.unregisterMbean();
    }

    @Override
    public void run() {
        List<Neighbors> nbrList = null;
        try {
            LOG.debug("Fetching neighbor status' from BGP");
            BgpCounters.resetFile(BgpCounters.BGP_VPNV4_SUMMARY_FILE);
            BgpCounters.resetFile(BgpCounters.BGP_VPNV6_SUMMARY_FILE);
            BgpCounters.resetFile(BgpCounters.BGP_EVPN_SUMMARY_FILE);
            Map<String, String> neighborStatusMap = new HashMap<>();

            if (bgpMgr.getBgpCounters() != null) {
                bgpMgr.getBgpCounters().fetchCmdOutputs(BgpCounters.BGP_VPNV4_SUMMARY_FILE,
                        "show ip bgp vpnv4 all summary");
                if (bgpMgr.getConfig() != null) {
                    nbrList = bgpMgr.getConfig().getNeighbors();
                }
                BgpCounters.parseIpBgpVpnv4AllSummary(neighborStatusMap);

                bgpMgr.getBgpCounters().fetchCmdOutputs(BgpCounters.BGP_VPNV6_SUMMARY_FILE,
                        "show ip bgp vpnv6 all summary");

                BgpCounters.parseIpBgpVpnv6AllSummary(neighborStatusMap);

                bgpMgr.getBgpCounters().fetchCmdOutputs(BgpCounters.BGP_EVPN_SUMMARY_FILE,
                        "show bgp l2vpn evpn all summary");

                BgpCounters.parseBgpL2vpnEvpnAllSummary(neighborStatusMap);

                processNeighborStatusMap(neighborStatusMap, nbrList);
            }
            LOG.debug("Finished getting the status of BGP neighbors");
        } catch (IOException e) {
            LOG.error("Failed to publish bgp counters ", e);
        }
    }

    private void processNeighborStatusMap(Map<String, String> nbrStatusMap, List<Neighbors> nbrs) {
        if (nbrs == null || nbrs.isEmpty()) {
            LOG.trace("No BGP neighbors configured.");
            return;
        }

        for (Neighbors nbr : nbrs) {
            boolean alarmToRaise = true;
            if (nbrStatusMap != null && nbrStatusMap.containsKey(nbr.getAddress().getValue())) {
                String nbrshipStatus = nbrStatusMap.get(nbr.getAddress().getValue());
                LOG.trace("nbr {} status {}",
                        nbr.getAddress().getValue(),
                        nbrshipStatus);
                try {
                    Integer.parseInt(nbrshipStatus);
                    alarmToRaise = false;
                } catch (NumberFormatException e) {
                    LOG.trace("Exception thrown in parsing the integers. {}", e);
                }

                final BgpAlarmStatus alarmStatus = neighborsRaisedAlarmStatusMap.get(nbr.getAddress().getValue());
                if (alarmToRaise) {
                    if (alarmStatus == null || alarmStatus != BgpAlarmStatus.RAISED) {
                        LOG.trace("alarm raised for {}.", nbr.getAddress().getValue());
                        raiseBgpNbrDownAlarm(nbr.getAddress().getValue());
                    } else {
                        LOG.trace("alarm raised already for {}", nbr.getAddress().getValue());
                    }
                } else {
                    if (alarmStatus == null || alarmStatus != BgpAlarmStatus.CLEARED) {
                        clearBgpNbrDownAlarm(nbr.getAddress().getValue());
                        LOG.trace("alarm cleared for {}", nbr.getAddress().getValue());
                    } else {
                        LOG.trace("alarm cleared already for {}", nbr.getAddress().getValue());
                    }
                }
            }
        }
    }

    private void raiseBgpNbrDownAlarm(String nbrIp) {

        StringBuilder source = new StringBuilder();
        source.append("BGP_Neighbor=").append(nbrIp);
        if (nbrIp == null || nbrIp.isEmpty()) {
            return;
        }
        LOG.trace("Raising BgpControlPathFailure alarm. {} alarmtext {} ", source, ALARM_TEXT);
        //Invokes JMX raiseAlarm method
        alarmAgent.invokeFMraisemethod("BgpControlPathFailure", ALARM_TEXT, source.toString());

        neighborsRaisedAlarmStatusMap.put(nbrIp, BgpAlarmStatus.RAISED);
    }

    public void clearBgpNbrDownAlarm(String nbrIp) {
        StringBuilder source = new StringBuilder();
        source.append("BGP_Neighbor=").append(nbrIp);
        if (nbrIp == null || nbrIp.isEmpty()) {
            return;
        }
        LOG.trace("Clearing BgpControlPathFailure alarm of source {} alarmtext {} ", source, ALARM_TEXT);
        //Invokes JMX clearAlarm method
        alarmAgent.invokeFMclearmethod("BgpControlPathFailure", ALARM_TEXT, source.toString());

        neighborsRaisedAlarmStatusMap.put(nbrIp, BgpAlarmStatus.CLEARED);
    }
}

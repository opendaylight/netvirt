/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.oam;

import static org.opendaylight.netvirt.bgpmanager.oam.BgpCounters.parseIpBgpVpnv4AllSummary;
import static org.opendaylight.netvirt.bgpmanager.oam.BgpCounters.resetFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpAlarms extends TimerTask {
    private static final Logger LOG = LoggerFactory.getLogger(BgpAlarms.class);
    private static final BgpJMXAlarmAgent ALARM_AGENT = new BgpJMXAlarmAgent();
    private static Map<String, String> neighborStatusMap = new HashMap<>();
    private BgpConfigurationManager bgpMgr;

    private static Map<String, BgpAlarmStatus> neighborsRaisedAlarmStatusMap = new HashMap<>();
    private final String alarmText = "Bgp Neighbor TCP connection is down";

    @Override
    public void run() {
        List<Neighbors> nbrList = null;
        try {
            LOG.debug("Fetching neighbor status' from BGP");
            resetFile("cmd_ip_bgp_vpnv4_all_summary.txt");
            neighborStatusMap.clear();

            if (bgpMgr != null && bgpMgr.getBgpCounters() != null) {
                bgpMgr.getBgpCounters().fetchCmdOutputs("cmd_ip_bgp_vpnv4_all_summary.txt",
                        "show ip bgp vpnv4 all summary");
                if (bgpMgr.getConfig() != null) {
                    nbrList = bgpMgr.getConfig().getNeighbors();
                }
                parseIpBgpVpnv4AllSummary(neighborStatusMap);
                processNeighborStatusMap(neighborStatusMap, nbrList, neighborsRaisedAlarmStatusMap);
            }

            LOG.debug("Finished getting the status of BGP neighbors");
        } catch (IOException e) {
            LOG.error("Failed to publish bgp counters ", e);
        }
    }

    public BgpAlarms(BgpConfigurationManager bgpManager) {
        bgpMgr = bgpManager;
        ALARM_AGENT.registerMbean();
        if (bgpMgr != null && bgpMgr.getConfig() != null) {
            List<Neighbors> nbrs = bgpMgr.getConfig().getNeighbors();
            if (nbrs != null) {
                for (Neighbors nbr : nbrs) {
                    LOG.trace("Clearing Neighbor DOWN alarm at the startup for Neighbor {}",
                            nbr.getAddress().getValue());
                    clearBgpNbrDownAlarm(nbr.getAddress().getValue());
                    neighborsRaisedAlarmStatusMap.put(nbr.getAddress().getValue(),
                            BgpAlarmStatus.CLEARED);
                }
            }
        }
    }

    private void processNeighborStatusMap(Map<String, String> nbrStatusMap,
            List<Neighbors> nbrs, Map<String, BgpAlarmStatus>
            nbrsRaisedAlarmStatusMap) {
        boolean alarmToRaise;
        String nbrshipStatus;
        if ((nbrs == null) || (nbrs.size() == 0)) {
            LOG.trace("No BGP neighbors configured.");
            return;
        }
        for (Neighbors nbr : nbrs) {
            alarmToRaise = true;
            if ((nbrStatusMap != null) && nbrStatusMap.containsKey(nbr.getAddress().getValue())) {
                nbrshipStatus = nbrStatusMap.get(nbr.getAddress().getValue());
                LOG.trace("nbr {} status {}",
                        nbr.getAddress().getValue(),
                        nbrshipStatus);
                try {
                    Integer.parseInt(nbrshipStatus);
                    alarmToRaise = false;
                } catch (NumberFormatException e) {
                    LOG.trace("Exception thrown in parsing the integers. {}", e);
                }
                if (alarmToRaise) {
                    if ((!nbrsRaisedAlarmStatusMap.containsKey(
                            nbr.getAddress().getValue())) || (nbrsRaisedAlarmStatusMap.get(
                            nbr.getAddress().getValue()) != BgpAlarmStatus.RAISED)) {
                        LOG.trace("alarm raised for {}.", nbr.getAddress().getValue());
                        raiseBgpNbrDownAlarm(nbr.getAddress().getValue());
                        nbrsRaisedAlarmStatusMap.put(nbr.getAddress().getValue(), BgpAlarmStatus.RAISED);
                    } else {
                        LOG.trace("alarm raised already for {}", nbr.getAddress().getValue());
                    }
                } else {
                    if ((!nbrsRaisedAlarmStatusMap.containsKey(
                            nbr.getAddress().getValue())) || (nbrsRaisedAlarmStatusMap.get(
                            nbr.getAddress().getValue()) != BgpAlarmStatus.CLEARED)) {
                        clearBgpNbrDownAlarm(nbr.getAddress().getValue());
                        LOG.trace("alarm cleared for {}", nbr.getAddress().getValue());
                        nbrsRaisedAlarmStatusMap.put(nbr.getAddress().getValue(), BgpAlarmStatus.CLEARED);
                    } else {
                        LOG.trace("alarm cleared already for {}", nbr.getAddress().getValue());
                    }
                }
            }
        }
    }

    public void raiseBgpNbrDownAlarm(String nbrIp) {

        StringBuilder source = new StringBuilder();
        source.append("BGP_Neighbor=").append(nbrIp);
        if ((nbrIp == null) || (nbrIp.isEmpty())) {
            return;
        }
        LOG.trace("Raising BgpControlPathFailure alarm. {} alarmtext {} ", source, alarmText);
        //Invokes JMX raiseAlarm method
        ALARM_AGENT.invokeFMraisemethod("BgpControlPathFailure", alarmText, source.toString());
    }

    public void clearBgpNbrDownAlarm(String nbrIp) {
        StringBuilder source = new StringBuilder();
        source.append("BGP_Neighbor=").append(nbrIp);
        if ((nbrIp == null) || (nbrIp.isEmpty())) {
            return;
        }
        LOG.trace("Clearing BgpControlPathFailure alarm of source {} alarmtext {} ", source, alarmText);
        //Invokes JMX clearAlarm method
        ALARM_AGENT.invokeFMclearmethod("BgpControlPathFailure", alarmText, source.toString());
    }
}

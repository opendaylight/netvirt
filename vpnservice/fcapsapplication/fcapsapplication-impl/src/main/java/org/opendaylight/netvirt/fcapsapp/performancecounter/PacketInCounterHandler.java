/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsapp.performancecounter;

import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PacketInCounterHandler implements PacketProcessingListener {
    private static final Logger LOG = LoggerFactory.getLogger(PacketInCounterHandler.class);
    private static ConcurrentHashMap<String,AtomicLong> ingressPacketMap = new ConcurrentHashMap<>();
    private static HashMap<String,String> packetInMap = new HashMap<>();
    private static final Integer FIRST_VALUE = 1;
    private static final PMAgent pmAgent =new PMAgent();

    @Override
    public void onPacketReceived(PacketReceived notification) {
        String dpnId = null;
        String nodeListEgressStr, nodekey;
        LOG.debug("Ingress packet notification received");
        try {
            if (notification.getIngress() == null) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("invalid PacketReceived notification");
                }
                return;
            }
            dpnId = getDpnId(notification.getIngress().getValue().toString());
        } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("DPN Parsing failed in onPacketReceived");
            }
        }
        if (dpnId != null) {
            nodeListEgressStr = "dpnId_" + dpnId + "_InjectedOFMessagesSent";
            nodekey = "InjectedOFMessagesSent:" + nodeListEgressStr;
            if (ingressPacketMap.containsKey(dpnId)) {
                ingressPacketMap.put(dpnId,new AtomicLong(ingressPacketMap.get(dpnId).incrementAndGet()));
                packetInMap.put(nodekey,""+ingressPacketMap.get(dpnId));
            } else {
                ingressPacketMap.put(dpnId, new AtomicLong(FIRST_VALUE));
                packetInMap.put(nodekey,""+FIRST_VALUE);
            }
            connectToPMAgent();
        } else {
            LOG.error("DpnId is null");
        }
    }
    private void connectToPMAgent(){
        pmAgent.sendPacketInCounterUpdate(packetInMap);
    }
    /*
     * Method to extract DpnId
     */
    public static String getDpnId(String id) {
        String[] nodeNo = id.split(":");
        String[] dpnId = nodeNo[1].split("]");
        return dpnId[0];
    }

    public void nodeRemovedNotification(String dpnId){
        String nodeListEgressStr, nodekey;
        if (dpnId != null) {
            dpnId = dpnId.split(":")[1];
            LOG.debug("Dpnvalue Id {}",dpnId);
            if (ingressPacketMap.containsKey(dpnId)) {
                nodeListEgressStr = "dpnId_" + dpnId + "_InjectedOFMessagesSent";
                nodekey = "InjectedOFMessagesSent:" + nodeListEgressStr;
                synchronized (this) {
                    ingressPacketMap.remove(dpnId);
                    packetInMap.remove(nodekey);
                    connectToPMAgent();
                }
                LOG.debug("Node {} Removed for PacketIn counter", dpnId);
            }
        } else {
            LOG.error("DpnId is null upon nodeRemovedNotification");
        }
    }
}
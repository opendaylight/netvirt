/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fcapsapp.performancecounter;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.Integer;
import java.lang.String;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class NodeUpdateCounter {

    private static final Logger LOG = LoggerFactory.getLogger(NodeUpdateCounter.class);
    private String nodeListEFSCountStr;
    private static HashSet<String> dpnList = new HashSet<String>();
    public static final PMAgent pmagent = new PMAgent();
    Map<String, String> counter_map = new HashMap<String, String>();

    public NodeUpdateCounter() {
    }

    public void nodeAddedNotification(String sNode,String hostName) {
        dpnList.add(sNode);
        sendNodeUpdation(dpnList.size(),hostName);
    }

    public void nodeRemovedNotification(String sNode,String hostName) {
        dpnList.remove(sNode);
        sendNodeUpdation(dpnList.size(), hostName);
    }

    private void sendNodeUpdation(Integer count,String hostName) {

        if (hostName != null) {
            nodeListEFSCountStr = "Node_" + hostName + "_NumberOfEFS";
            LOG.debug("NumberOfEFS:" + nodeListEFSCountStr + " dpnList.size " + count);

            counter_map.put("NumberOfEFS:" + nodeListEFSCountStr, "" + count);
            pmagent.connectToPMAgent(counter_map);
        } else
            LOG.error("Hostname is null upon NumberOfEFS counter");
    }

    public boolean isDpnConnectedLocal(String sNode) {
        if (dpnList.contains(sNode))
            return true;
        return false;
    }
}
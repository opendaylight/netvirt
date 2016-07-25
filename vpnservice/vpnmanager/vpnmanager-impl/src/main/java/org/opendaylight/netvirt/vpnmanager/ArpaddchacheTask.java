/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class ArpaddchacheTask implements Callable<List<ListenableFuture<Void>>> {
    InetAddress srcInetAddr;
    MacAddress srcMacAddress;
    String vpnName;
    String interfaceName;
    DelayQueue<MacEntry> macEntryQueue;
    private static final Logger LOG = LoggerFactory.getLogger(ArpaddchacheTask.class);

    public ArpaddchacheTask(InetAddress srcInetAddr, MacAddress srcMacAddress, String vpnName, String interfaceName,
                            DelayQueue<MacEntry> macEntryQueue) {
        super();
        this.srcInetAddr = srcInetAddr;
        this.srcMacAddress = srcMacAddress;
        this.vpnName = vpnName;
        this.interfaceName = interfaceName;
        this.macEntryQueue = macEntryQueue;
    }



    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        addOrUpdateMacEntryToQueue(vpnName,srcMacAddress, srcInetAddr, interfaceName);
        return futures;
    }

    public  void addOrUpdateMacEntryToQueue(String vpnName, MacAddress macAddress,InetAddress InetAddress, String interfaceName) {
        MacEntry newMacEntry = new MacEntry(ArpConstants.arpCacheTimeout,vpnName,macAddress, InetAddress,interfaceName );
        if (!macEntryQueue.contains(newMacEntry)) {
            LOG.info("Adding ARP cache");
            macEntryQueue.offer(newMacEntry);
        }
        else{
            macEntryQueue.remove(newMacEntry);
            macEntryQueue.offer(newMacEntry);
        }
    }
}

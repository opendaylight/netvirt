/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.cli.l2gw;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.genius.utils.cache.CacheUtil;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Command(scope = "l2gw", name = "show-cache", description = "display l2gateways cache")
public class L2GwUtilsCacheCli extends OsgiCommandSupport {
    private static final String L2GATEWAY_CACHE_NAME = "L2GW";
    private static final String DEMARCATION = "=================================";

    @Option(name = "-cache", aliases = {"--cache"}, description = "cache name",
        required = false, multiValued = false)
    String cacheName;

    @Option(name = "-elan", aliases = {"--elan"}, description = "elan name",
        required = false, multiValued = false)
    String elanName;

    private final L2GatewayCache l2GatewayCache;

    public L2GwUtilsCacheCli(L2GatewayCache l2GatewayCache) {
        this.l2GatewayCache = l2GatewayCache;
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    @Override
    protected Object doExecute() throws IOException {
        if (cacheName == null) {
            System.out.println("Available caches");
            System.out.println(ElanL2GwCacheUtils.L2GATEWAY_CONN_CACHE_NAME);
            System.out.println(L2GATEWAY_CACHE_NAME);
            System.out.println("HA");
            System.out.println("HA_EVENTS");
            return null;
        }
        switch (cacheName) {
            case ElanL2GwCacheUtils.L2GATEWAY_CONN_CACHE_NAME:
                dumpElanL2GwCache();
                break;
            case L2GATEWAY_CACHE_NAME:
                dumpL2GwCache();
                break;
            case "HA":
                dumpHACache(System.out);
                break;
            case "HA_EVENTS":
                dumpHACacheEvents();
                break;
            default:
                break;
        }
        return null;
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    private void dumpHACacheEvents() throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(new File("hwvtep.events.txt"))) {
            System.out.println("Dumping to file hwvtep.events.txt");
            PrintStream fos = new PrintStream(fileOutputStream);
            dumpHACache(fos);
            fos.close();
            System.out.println("Dumped to file " + new File("hwvtep.events.txt").getAbsolutePath());
        }
    }

    private void dumpHACache(PrintStream printStream) {

        printStream.println("HA enabled nodes");
        for (InstanceIdentifier<Node> id : HwvtepHACache.getInstance().getHAChildNodes()) {
            String nodeId = id.firstKeyOf(Node.class).getNodeId().getValue();
            printStream.println(nodeId);
        }

        printStream.println("HA parent nodes");
        for (InstanceIdentifier<Node> id : HwvtepHACache.getInstance().getHAParentNodes()) {
            String nodeId = id.firstKeyOf(Node.class).getNodeId().getValue();
            printStream.println(nodeId);
            for (InstanceIdentifier<Node> childId : HwvtepHACache.getInstance().getChildrenForHANode(id)) {
                nodeId = childId.firstKeyOf(Node.class).getNodeId().getValue();
                printStream.println("    " + nodeId);
            }
        }

        printStream.println("Connected Nodes");
        Map<String, Boolean> nodes = HwvtepHACache.getInstance().getConnectedNodes();
        for (Entry<String, Boolean> entry : nodes.entrySet()) {
            printStream.print(entry.getKey());
            printStream.print("    : connected : ");
            printStream.println(entry.getValue());
        }
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void dumpL2GwCache() {
        Collection<L2GatewayDevice> devices = l2GatewayCache.getAll();
        if (devices.isEmpty()) {
            System.out.println("no devices are present in cache");
            return;
        }
        for (L2GatewayDevice device : devices) {
            System.out.println("device " + device);
        }
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void dumpElanL2GwCache() {
        if (elanName == null) {
            ConcurrentMap<String, ConcurrentMap<String, L2GatewayDevice>> cache =
                (ConcurrentMap<String, ConcurrentMap<String, L2GatewayDevice>>) CacheUtil.getCache(
                    cacheName);
            if (cache == null) {
                System.out.println("no devices are present in elan cache");
            } else {
                for (Entry<String, ConcurrentMap<String, L2GatewayDevice>> entry : cache.entrySet()) {
                    print(entry.getKey(), entry.getValue());
                    System.out.println(DEMARCATION);
                    System.out.println(DEMARCATION);
                }
            }
            return;
        }
        ConcurrentMap<String, L2GatewayDevice> elanDevices = ElanL2GwCacheUtils
            .getInvolvedL2GwDevices(elanName);
        print(elanName, elanDevices);
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void print(String elan, ConcurrentMap<String, L2GatewayDevice> devices) {
        System.out.println("Elan name : " + elan);
        System.out.println("No of devices in elan " + devices.keySet().size());
        for (L2GatewayDevice device : devices.values()) {
            System.out.println("device " + device);
        }
    }
}

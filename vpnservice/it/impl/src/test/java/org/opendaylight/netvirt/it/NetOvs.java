/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import java.io.IOException;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public interface NetOvs {
    String createNetwork(String networkName, String segId, String ipPfx);

    String createRouter(String routerName);

    String createPort(int ovsInstance, Node bridgeNode, String networkName) throws
            InterruptedException, IOException;

    boolean setPortMac(int ovsInstance, String portName) throws InterruptedException, IOException;

    String createRouterInterface(String routerName, String networkName);

    PortInfo getPortInfo(String portName);

    void deletePort(String uuid);

    void destroy();

    void preparePortForPing(String portName) throws InterruptedException, IOException;

    int ping(String fromPort, String toPort) throws InterruptedException, IOException;

    void logState(int dockerInstance, String logText) throws IOException, InterruptedException;

    String getInstanceIp(int ovsInstance) throws InterruptedException, IOException;
}

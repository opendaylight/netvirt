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
    String createPort(Node bridgeNode) throws InterruptedException, IOException;
    void deletePort(String uuid);
    void destroy();
    void preparePortForPing(String portName) throws InterruptedException, IOException;
    void ping(String fromPort, String toPort) throws InterruptedException, IOException;
    void logState(int dockerInstance, String logText) throws IOException, InterruptedException;
}

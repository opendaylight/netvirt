/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import java.io.IOException;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.DockerOvs;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class RealNetOvsImpl extends AbstractNetOvs {
    RealNetOvsImpl(final DockerOvs dockerOvs, final Boolean isUserSpace, final MdsalUtils mdsalUtils,
                   final Neutron neutron, SouthboundUtils southboundUtils) {
        super(dockerOvs, isUserSpace, mdsalUtils, neutron, southboundUtils);
    }

    @Override
    public String createPort(Node bridgeNode) throws InterruptedException, IOException {
        PortInfo portInfo = buildPortInfo();

        neutron.createPort(portInfo, "compute:None");
        addTerminationPoint(portInfo, bridgeNode, "internal");
        portInfoByName.put(portInfo.name, portInfo);

        return portInfo.name;
    }
}

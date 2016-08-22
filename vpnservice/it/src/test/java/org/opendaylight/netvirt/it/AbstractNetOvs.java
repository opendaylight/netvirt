/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.ovsdb.it.utils.DockerOvs;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class AbstractNetOvs implements NetOvs {
    protected final DockerOvs dockerOvs;
    protected final Boolean isUserSpace;
    protected final MdsalUtils mdsalUtils;
    public final Neutron neutron;
    private final SouthboundUtils southboundUtils;
    protected static final int DEFAULT_WAIT = 30 * 1000;
    protected Map<String, PortInfo> portInfoByName = new HashMap<>();

    AbstractNetOvs(final DockerOvs dockerOvs, final Boolean isUserSpace, final MdsalUtils mdsalUtils,
                   final Neutron neutron, SouthboundUtils southboundUtils) {
        this.dockerOvs = dockerOvs;
        this.isUserSpace = isUserSpace;
        this.mdsalUtils = mdsalUtils;
        this.neutron = neutron;
        this.southboundUtils = southboundUtils;
    }

    protected PortInfo buildPortInfo() {
        long idx = portInfoByName.size() + 1;
        return new PortInfo(idx);
    }

    @Override
    public String createPort(Node bridgeNode) throws InterruptedException, IOException {
        return null;
    }

    @Override
    public void deletePort(String uuid) {
        if (uuid == null) {
            return;
        }

        Port port = new PortBuilder()
                .setUuid(new Uuid(uuid))
                .build();

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Ports.class).child(Port.class, port.getKey()));
    }

    @Override
    public void destroy() {
        for (PortInfo portInfo : portInfoByName.values()) {
            deletePort(portInfo.id);
        }
        portInfoByName.clear();

        neutron.deleteSubnet();
        neutron.deleteNetwork();
    }

    @Override
    public void preparePortForPing(String portName) throws InterruptedException, IOException {
    }

    @Override
    public void ping(String fromPort, String toPort) throws InterruptedException, IOException {
    }

    protected void addTerminationPoint(PortInfo portInfo, Node bridge, String portType) {
        Map<String, String> externalIds = Maps.newHashMap();
        externalIds.put("attached-mac", portInfo.mac);
        externalIds.put("iface-id", portInfo.id);
        southboundUtils.addTerminationPoint(bridge, portInfo.name, portType, null, externalIds, portInfo.ofPort);
    }

    @Override
    public void logState(int dockerInstance, String logText) throws IOException, InterruptedException {
    }
}
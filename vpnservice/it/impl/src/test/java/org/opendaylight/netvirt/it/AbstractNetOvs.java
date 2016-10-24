/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractNetOvs implements NetOvs {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetOvs.class);
    protected final DockerOvs dockerOvs;
    protected final Boolean isUserSpace;
    protected final MdsalUtils mdsalUtils;
    private final SouthboundUtils southboundUtils;
    protected static final int DEFAULT_WAIT = 30 * 1000;
    protected Map<String, PortInfo> portInfoByName = new HashMap<>();
    protected Map<String, NeutronNetwork> neutronNetworkByName = new HashMap<>();

    AbstractNetOvs(final DockerOvs dockerOvs, final Boolean isUserSpace, final MdsalUtils mdsalUtils,
                   SouthboundUtils southboundUtils) {
        this.dockerOvs = dockerOvs;
        this.isUserSpace = isUserSpace;
        this.mdsalUtils = mdsalUtils;
        this.southboundUtils = southboundUtils;
        LOG.info("{} isUserSpace: {}, usingExternalDocker: {}",
                getClass().getSimpleName(), isUserSpace, dockerOvs.usingExternalDocker());
    }

    public String createNetwork(String networkName, String segId, String ipPfx) {
        NeutronNetwork neutronNetwork = new NeutronNetwork(mdsalUtils, segId, ipPfx);
        neutronNetwork.createNetwork(networkName);
        neutronNetwork.createSubnet(networkName + "subnet");
        putNeutronNetwork(networkName, neutronNetwork);
        return networkName;
    }

    public void putNeutronNetwork(String networkName, NeutronNetwork neutronNetwork) {
        neutronNetworkByName.put(networkName, neutronNetwork);
    }

    protected NeutronNetwork getNeutronNetwork(String networkName) {
        return neutronNetworkByName.get(networkName);
    }

    protected String getNetworkId(String name) {
        return neutronNetworkByName.get(name).getNetworkId();
    }

    protected String getSubnetId(String name) {
        return neutronNetworkByName.get(name).getSubnetId();
    }

    protected PortInfo buildPortInfo(int ovsInstance) {
        long idx = portInfoByName.size() + 1;
        return new PortInfo(ovsInstance, idx);
    }

    protected void putPortInfo(PortInfo portInfo) {
        portInfoByName.put(portInfo.name, portInfo);
    }

    @Override
    public String createPort(int ovsInstance, Node bridgeNode, String networkName)
            throws InterruptedException, IOException {
        return null;
    }

    @Override
    public PortInfo getPortInfo(String portName) {
        return portInfoByName.get(portName);
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

        for (NeutronNetwork neutronNetwork : neutronNetworkByName.values()) {
            neutronNetwork.deleteSubnet();
            neutronNetwork.deleteNetwork();
        }
        neutronNetworkByName.clear();
    }

    @Override
    public void preparePortForPing(String portName) throws InterruptedException, IOException {
    }

    @Override
    public int ping(String fromPort, String toPort) throws InterruptedException, IOException {
        return 0;
    }

    protected void addTerminationPoint(PortInfo portInfo, Node bridge, String portType) {
        Map<String, String> externalIds = new HashMap<>();
        externalIds.put("attached-mac", portInfo.mac);
        externalIds.put("iface-id", portInfo.id);
        southboundUtils.addTerminationPoint(bridge, portInfo.name, portType, null, externalIds, portInfo.ofPort);
    }

    @Override
    public void logState(int dockerInstance, String logText) throws InterruptedException, IOException {
    }

    @Override
    public String getInstanceIp(int ovsInstance) throws InterruptedException, IOException {
        return null;
    }

    protected PortInfo getPortInfoByOvsInstance(int ovsInstance) {
        PortInfo portInfoFound = null;
        for (PortInfo portInfo : portInfoByName.values()) {
            if (ovsInstance == portInfo.ovsInstance) {
                portInfoFound = portInfo;
            }
        }
        return portInfoFound;
    }
}
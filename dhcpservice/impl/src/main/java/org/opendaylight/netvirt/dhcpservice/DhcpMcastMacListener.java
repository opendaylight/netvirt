/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpMcastMacListener
        extends AbstractAsyncDataTreeChangeListener<RemoteMcastMacs> {

    private static final long TIMEOUT_FOR_SHUTDOWN = 30;

    private static final Logger LOG = LoggerFactory.getLogger(DhcpMcastMacListener.class);

    private final DataBroker dataBroker;
    private final DhcpExternalTunnelManager externalTunnelManager;
    private final DhcpL2GwUtil dhcpL2GwUtil;
    private final DhcpserviceConfig config;

    @Inject
    public DhcpMcastMacListener(DhcpExternalTunnelManager dhcpManager, DhcpL2GwUtil dhcpL2GwUtil, DataBroker dataBroker,
                                final DhcpserviceConfig config, HwvtepNodeHACache hwvtepNodeHACache) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
              InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
                                     new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class)
                      .augmentation(HwvtepGlobalAugmentation.class).child(RemoteMcastMacs.class),
              Executors.newSingleThreadExecutor("IdPoolListener", LOG));
        this.externalTunnelManager = dhcpManager;
        this.dataBroker = dataBroker;
        this.dhcpL2GwUtil = dhcpL2GwUtil;
        this.config = config;
    }

    @Override
    public void update(@Nonnull InstanceIdentifier<RemoteMcastMacs> identifier, @Nonnull RemoteMcastMacs original,
                       @Nonnull RemoteMcastMacs update) {
        // NOOP
    }

    @Override
    public void add(@Nonnull InstanceIdentifier<RemoteMcastMacs> identifier, @Nonnull RemoteMcastMacs remoteMcastMacs) {
        String elanInstanceName = getElanName(remoteMcastMacs);
        IpAddress tunnelIp = dhcpL2GwUtil.getHwvtepNodeTunnelIp(identifier.firstIdentifierOf(Node.class));
        if (tunnelIp == null) {
            LOG.error("Could not find tunnelIp for {}", identifier);
            return;
        }
        List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(dataBroker);
        BigInteger designatedDpnId = externalTunnelManager.designateDpnId(tunnelIp, elanInstanceName, dpns);
        if (designatedDpnId == null || designatedDpnId.equals(DhcpMConstants.INVALID_DPID)) {
            LOG.error("Unable to designate a DPN for {}", identifier);
        }
    }

    @Override
    public void remove(@Nonnull InstanceIdentifier<RemoteMcastMacs> identifier,
                       @Nonnull RemoteMcastMacs remoteMcastMacs) {
        String elanInstanceName = getElanName(remoteMcastMacs);
        IpAddress tunnelIp = dhcpL2GwUtil.getHwvtepNodeTunnelIp(identifier.firstIdentifierOf(Node.class));
        if (tunnelIp == null) {
            LOG.error("Could not find tunnelIp for {}", identifier);
            return;
        }
        BigInteger designatedDpnId = externalTunnelManager.readDesignatedSwitchesForExternalTunnel(
                tunnelIp, elanInstanceName);
        if (designatedDpnId == null) {
            LOG.error("Could not find designated DPN ID elanInstanceName {}, tunnelIp {}", elanInstanceName, tunnelIp);
            return;
        }
        externalTunnelManager.removeDesignatedSwitchForExternalTunnel(designatedDpnId, tunnelIp, elanInstanceName);
    }

    @Override
    @PreDestroy
    public void close() {
        if (config.isControllerDhcpEnabled()) {
            super.close();
        }
        MoreExecutors.shutdownAndAwaitTermination(getExecutorService(), TIMEOUT_FOR_SHUTDOWN, TimeUnit.SECONDS);
    }

    private String getElanName(RemoteMcastMacs mac) {
        InstanceIdentifier<LogicalSwitches> logicalSwitchIid = (InstanceIdentifier<LogicalSwitches>)
                mac.getLogicalSwitchRef().getValue();
        return logicalSwitchIid.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    }
}

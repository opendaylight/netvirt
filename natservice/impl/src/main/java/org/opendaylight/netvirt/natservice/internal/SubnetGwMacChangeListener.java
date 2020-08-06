/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetGwMacChangeListener extends AbstractAsyncDataTreeChangeListener<VpnPortipToPort> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetGwMacChangeListener.class);

    private final DataBroker broker;
    private final INeutronVpnManager nvpnManager;
    private final ExternalNetworkGroupInstaller extNetworkInstaller;

    @Inject
    public SubnetGwMacChangeListener(final DataBroker broker, final INeutronVpnManager nvpnManager,
                                     final ExternalNetworkGroupInstaller extNetworkInstaller) {
        super(broker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(NeutronVpnPortipPortData.class)
                .child(VpnPortipToPort.class),
                Executors.newListeningSingleThreadExecutor("SubnetGwMacChangeListener", LOG));
        this.broker = broker;
        this.nvpnManager = nvpnManager;
        this.extNetworkInstaller = extNetworkInstaller;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void remove(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort vpnPortipToPort) {
    }

    @Override
    public void update(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort origLearntVpnVipToPort,
                       VpnPortipToPort updatedLearntVpnVipToPort) {
        if (!updatedLearntVpnVipToPort.isLearntIp()) {
            return;
        }
        handleSubnetGwIpChange(updatedLearntVpnVipToPort);
    }

    @Override
    public void add(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort learntVpnVipToPort) {
        if (!learntVpnVipToPort.isLearntIp()) {
            return;
        }
        LOG.info("ADD: Learnt IP {} discovered for VPN {} MAC {}", learntVpnVipToPort.getPortFixedip(),
                learntVpnVipToPort.getMacAddress(), learntVpnVipToPort.getVpnName());
        handleSubnetGwIpChange(learntVpnVipToPort);
    }

    private void handleSubnetGwIpChange(VpnPortipToPort learntVpnVipToPort) {
        String macAddress = learntVpnVipToPort.getMacAddress();
        if (macAddress == null) {
            LOG.error("handleSubnetGwIpChange : Mac address is null for LearntVpnVipToPort for vpn {} prefix {}",
                learntVpnVipToPort.getVpnName(), learntVpnVipToPort.getPortFixedip());
            return;
        }

        String fixedIp = learntVpnVipToPort.getPortFixedip();
        if (fixedIp == null) {
            LOG.error("handleSubnetGwIpChange : Fixed ip is null for LearntVpnVipToPort for vpn {}",
                learntVpnVipToPort.getVpnName());
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(fixedIp);
            if (address instanceof Inet6Address) {
                // TODO: Revisit when IPv6 North-South communication support is added.
                LOG.debug("handleSubnetGwIpChange : Skipping ipv6 address {}.", address);
                return;
            }
        } catch (UnknownHostException e) {
            LOG.warn("handleSubnetGwIpChange : Invalid ip address {}", fixedIp, e);
            return;
        }

        for (Uuid subnetId : nvpnManager.getSubnetIdsForGatewayIp(new IpAddress(new Ipv4Address(fixedIp)))) {
            LOG.trace("handleSubnetGwIpChange : Updating MAC resolution on vpn {} for GW ip {} to {}",
                    learntVpnVipToPort.getVpnName(), fixedIp, macAddress);
            extNetworkInstaller.installExtNetGroupEntries(subnetId, macAddress);
        }
    }
}

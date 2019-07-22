/*
 * Copyright (c) 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;

@Singleton
public class NeutronvpnManagerImpl implements INeutronVpnManager {

    private final NeutronvpnManager nvManager;
    private final NeutronvpnUtils neutronvpnUtils;

    @Inject
    public NeutronvpnManagerImpl(final NeutronvpnManager neutronvpnManager, final NeutronvpnUtils neutronvpnUtils) {
        this.nvManager = neutronvpnManager;
        this.neutronvpnUtils = neutronvpnUtils;
    }

    @Override
    public List<String> showNeutronPortsCLI() throws ReadFailedException {
        return nvManager.showNeutronPortsCLI();
    }

    @Override
    public Network getNeutronNetwork(Uuid networkId) {
        return nvManager.getNeutronNetwork(networkId);
    }

    @Override
    public List<String> showVpnConfigCLI(Uuid vuuid) throws InterruptedException, ExecutionException {
        return nvManager.showVpnConfigCLI(vuuid);
    }

    @Override
    public Uuid getNetworkForSubnet(Uuid subnetId) {
        return nvManager.getNetworkForSubnet(subnetId);
    }

    @Override
    public List<Uuid> getNetworksForVpn(Uuid vpnId) {
        return nvManager.getNetworksForVpn(vpnId);
    }

    @Override
    public Port getNeutronPort(String name) {
        return nvManager.getNeutronPort(name);
    }

    @Override
    public Subnet getNeutronSubnet(Uuid subnetId) {
        return nvManager.getNeutronSubnet(subnetId);
    }

    @Override
    public Collection<Uuid> getSubnetIdsForGatewayIp(IpAddress ipAddress) {
        return neutronvpnUtils.getSubnetIdsForGatewayIp(ipAddress);
    }

    @Override
    public void programV6InternetFallbackFlow(Uuid routerId, Uuid internetVpnId, int addOrRemove) {
        nvManager.programV6InternetFallbackFlow(routerId, internetVpnId, addOrRemove);
    }

    @Override
    public Optional<String> getSubnetGatewayIpAddressIfV4Subnet(Uuid subnetId) {
        //Get V4 subnet-gw-ip from subnetMap for both Network(s) to VPN association and router to VPN association cases
        Subnetmap subnetMap = neutronvpnUtils.getSubnetmap(subnetId);
        if (subnetMap != null) {
            if (subnetMap.getSubnetType().getName().equals(Subnetmap.SubnetType.IPV4.getName())) {
                return Optional.of(subnetMap.getGatewayIp());
            }
        }
        return Optional.absent();
    }

    @Override
    public Optional<String> getSubnetGatewayIpAddress(Uuid subnetId) {
        //Get subnet-gw-ip from subnetMap for both Network(s) to VPN association and router to VPN association cases
        Subnetmap subnetMap = neutronvpnUtils.getSubnetmap(subnetId);
        if (subnetMap != null) {
            return Optional.of(subnetMap.getGatewayIp());
        }
        return Optional.absent();
    }
}

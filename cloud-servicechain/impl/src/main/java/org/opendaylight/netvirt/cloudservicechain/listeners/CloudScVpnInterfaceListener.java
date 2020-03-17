/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.cloudservicechain.VPNServiceChainHandler;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames.AssociatedSubnetType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CloudScVpnInterfaceListener
        extends AsyncDataTreeChangeListenerBase<VpnInterface, CloudScVpnInterfaceListener> {

    private static final Logger LOG = LoggerFactory.getLogger(CloudScVpnInterfaceListener.class);
    private final DataBroker dataBroker;
    private final VPNServiceChainHandler vpnScHandler;

    @Inject
    public CloudScVpnInterfaceListener(final DataBroker dataBroker, final VPNServiceChainHandler vpnScHandler) {
        super(VpnInterface.class, CloudScVpnInterfaceListener.class);

        this.dataBroker = dataBroker;
        this.vpnScHandler = vpnScHandler;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected CloudScVpnInterfaceListener getDataTreeChangeListener() {
        return CloudScVpnInterfaceListener.this;
    }

    @Override
    protected InstanceIdentifier<VpnInterface> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInterface> key, VpnInterface vpnIfaceRemoved) {
        for (VpnInstanceNames vpnInstance : vpnIfaceRemoved.getVpnInstanceNames()) {
            String vpnName = vpnInstance.getVpnName();
            if (!vpnInstance.getAssociatedSubnetType().equals(AssociatedSubnetType.V4AndV6Subnets)
                && !vpnInstance.getAssociatedSubnetType().equals(AssociatedSubnetType.V4Subnet)) {
                continue;
            }
            try {
                Optional<VpnToPseudoPortData> optScfInfoForVpn = vpnScHandler.getScfInfoForVpn(vpnName);
                if (!optScfInfoForVpn.isPresent()) {
                    LOG.trace("Vpn {} is not related to ServiceChaining. No further action", vpnName);
                    return;
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error reading the ServiceChaining information for VPN {}", vpnName, e);
            }
            break;
        }
        vpnScHandler.unbindScfOnVpnInterface(vpnIfaceRemoved.key().getName());
    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> key, VpnInterface dataObjectModificationBefore,
                          VpnInterface dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<VpnInterface> key, VpnInterface vpnIfaceAdded) {
        for (VpnInstanceNames vpnInstance : vpnIfaceAdded.getVpnInstanceNames()) {
            String vpnName = vpnInstance.getVpnName();
            if (!vpnInstance.getAssociatedSubnetType().equals(AssociatedSubnetType.V4AndV6Subnets)
                && !vpnInstance.getAssociatedSubnetType().equals(AssociatedSubnetType.V4Subnet)) {
                continue;
            }
            try {
                Optional<VpnToPseudoPortData> optScfInfoForVpn = vpnScHandler.getScfInfoForVpn(vpnName);
                if (!optScfInfoForVpn.isPresent()) {
                    LOG.trace("Vpn {} is not related to ServiceChaining. No further action", vpnName);
                    return;
                }
                vpnScHandler.bindScfOnVpnInterface(vpnIfaceAdded.key().getName(),
                        optScfInfoForVpn.get().getScfTag());
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error reading the ServiceChaining information for VPN {}", vpnName, e);
            }
        }
    }

}

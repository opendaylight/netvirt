/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.cloudservicechain.VPNServiceChainHandler;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudScVpnInterfaceListener
    extends AsyncDataTreeChangeListenerBase<VpnInterface, CloudScVpnInterfaceListener>
    implements AutoCloseable {

    static final Logger LOG = LoggerFactory.getLogger(CloudScVpnInterfaceListener.class);

    private final DataBroker dataBroker;
    private final VPNServiceChainHandler vpnScHandler;

    public CloudScVpnInterfaceListener(final DataBroker dataBroker, final VPNServiceChainHandler vpnScHandler) {
        super(VpnInterface.class, CloudScVpnInterfaceListener.class);

        this.dataBroker = dataBroker;
        this.vpnScHandler = vpnScHandler;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
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
        String vpnName = vpnIfaceRemoved.getVpnInstanceName();
        Optional<VpnToPseudoPortData> optScfInfoForVpn = vpnScHandler.getScfInfoForVpn(vpnName);
        if (!optScfInfoForVpn.isPresent()) {
            LOG.trace("Vpn {} is not related to ServiceChaining. No further action", vpnName);
            return;
        }

        vpnScHandler.unbindScfOnVpnInterface(vpnIfaceRemoved.getKey().getName());
    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> key, VpnInterface dataObjectModificationBefore,
                          VpnInterface dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<VpnInterface> key, VpnInterface vpnIfaceAdded) {
        String vpnName = vpnIfaceAdded.getVpnInstanceName();
        Optional<VpnToPseudoPortData> optScfInfoForVpn = vpnScHandler.getScfInfoForVpn(vpnName);
        if (!optScfInfoForVpn.isPresent()) {
            LOG.trace("Vpn {} is not related to ServiceChaining. No further action", vpnName);
            return;
        }

        vpnScHandler.bindScfOnVpnInterface(vpnIfaceAdded.getKey().getName(), optScfInfoForVpn.get().getScfTag());
    }

}

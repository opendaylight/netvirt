/*
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class VpnInstanceToVpnIdListener extends AsyncDataTreeChangeListenerBase<VpnInstance,
        VpnInstanceToVpnIdListener> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnInstanceToVpnIdListener.class);

    private final DataBroker dataBroker;
    private final ExternalNetworksChangeListener externalNetworksChangeListener;
    private final ExternalRoutersListener externalRoutersListener;

    @Inject
    public VpnInstanceToVpnIdListener(DataBroker dataBroker,
                                      ExternalNetworksChangeListener externalNetworksChangeListener,
                                      ExternalRoutersListener externalRoutersListener) {
        super(VpnInstance.class, VpnInstanceToVpnIdListener.class);
        this.dataBroker = dataBroker;
        this.externalNetworksChangeListener = externalNetworksChangeListener;
        this.externalRoutersListener = externalRoutersListener;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, this.dataBroker);
    }

    @Override
    protected VpnInstanceToVpnIdListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected InstanceIdentifier<VpnInstance> getWildCardPath() {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class).child(VpnInstance.class).build();
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstance> identifier, final VpnInstance vpnInstance) {
        String vpnInstanceName = vpnInstance.getVpnInstanceName();
        externalNetworksChangeListener.processPendingExternalNetworks(vpnInstanceName);
        externalRoutersListener.processPendingExternalRouters(vpnInstanceName, vpnInstance.getVpnId());
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstance> identifier, final VpnInstance vpnInstance) {
        String vpnInstanceName = vpnInstance.getVpnInstanceName();
        externalNetworksChangeListener.removePendingExternalNetworks(vpnInstanceName, null);
        externalRoutersListener.removePendingExternalRouters(vpnInstanceName);
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> identifier, VpnInstance original, VpnInstance update) {
    }
}

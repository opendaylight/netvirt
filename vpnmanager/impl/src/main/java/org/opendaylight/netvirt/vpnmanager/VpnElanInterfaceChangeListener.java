/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames.AssociatedSubnetType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnElanInterfaceChangeListener extends AbstractAsyncDataTreeChangeListener<ElanInterface> {
    private static final Logger LOG = LoggerFactory.getLogger(VpnElanInterfaceChangeListener.class);

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IElanService elanService;
    private final VpnUtil vpnUtil;

    @Inject
    public VpnElanInterfaceChangeListener(final DataBroker broker, final IElanService elanService,
                                          final VpnUtil vpnUtil) {
        super(broker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class),
                Executors.newListeningSingleThreadExecutor("VpnElanInterfaceChangeListener", LOG));
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.elanService = elanService;
        this.vpnUtil = vpnUtil;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<ElanInterface> key, ElanInterface elanInterface) {
        String interfaceName = elanInterface.getName();
        if (!elanService.isExternalInterface(interfaceName)) {
            LOG.debug("remove: Interface {} is not external. Ignoring interface removal", interfaceName);
            return;
        }

        if (!vpnUtil.isVpnInterfaceConfigured(interfaceName)) {
            LOG.debug("remove: VpnInterface was never configured for {}. Ignoring interface removal", interfaceName);
            return;
        }

        InstanceIdentifier<VpnInterface> vpnInterfaceIdentifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx ->
            tx.delete(vpnInterfaceIdentifier)), LOG, "Error removing VPN interface {}", vpnInterfaceIdentifier);
        LOG.info("remove: Removed VPN interface {}", interfaceName);
    }

    @Override
    public void update(InstanceIdentifier<ElanInterface> key, ElanInterface origElanInterface,
        ElanInterface updatedElanInterface) {

    }

    @Override
    public void add(InstanceIdentifier<ElanInterface> key, ElanInterface elanInterface) {
        String interfaceName = elanInterface.getName();
        if (!elanService.isExternalInterface(interfaceName)) {
            LOG.debug("add: Interface {} is not external. Ignoring", interfaceName);
            return;
        }

        Uuid networkId;
        try {
            networkId = new Uuid(elanInterface.getElanInstanceName());
        } catch (IllegalArgumentException e) {
            LOG.debug("add: ELAN instance {} for interface {} is not Uuid", elanInterface.getElanInstanceName(),
                    elanInterface.getName());
            return;
        }

        Uuid vpnId = vpnUtil.getExternalNetworkVpnId(networkId);
        if (vpnId == null) {
            LOG.debug("add: Network {} is not external or vpn-id missing. Ignoring interface {} on elan {}",
                    networkId.getValue(), elanInterface.getName(), elanInterface.getElanInstanceName());
            return;
        }
        VpnInstanceNames vpnInstance = VpnHelper
            .getVpnInterfaceVpnInstanceNames(vpnId.getValue(), AssociatedSubnetType.V4AndV6Subnets);
        List<VpnInstanceNames> listVpn = new ArrayList<>();
        listVpn.add(vpnInstance);
        VpnInterface vpnInterface = new VpnInterfaceBuilder().withKey(new VpnInterfaceKey(interfaceName))
            .setVpnInstanceNames(listVpn)
            .build();
        InstanceIdentifier<VpnInterface> vpnInterfaceIdentifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        vpnUtil.syncWrite(LogicalDatastoreType.CONFIGURATION, vpnInterfaceIdentifier, vpnInterface);
        LOG.info("add: Added VPN interface {} with VPN-id {} elanInstance {}", interfaceName, vpnId.getValue(),
                elanInterface.getElanInstanceName());
    }

}

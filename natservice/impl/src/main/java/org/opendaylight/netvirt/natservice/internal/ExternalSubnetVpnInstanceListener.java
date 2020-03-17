/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalSubnetVpnInstanceListener extends AbstractAsyncDataTreeChangeListener<VpnInstance> {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetVpnInstanceListener.class);
    private final DataBroker dataBroker;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;
    private final IElanService elanService;
    private final IVpnManager vpnManager;

    @Inject
    public ExternalSubnetVpnInstanceListener(final DataBroker dataBroker,
                     final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
                     final IElanService elanService, final IVpnManager vpnManager) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(VpnInstanceToVpnId.class)
                .child(VpnInstance.class),
                Executors.newListeningSingleThreadExecutor("ExternalSubnetVpnInstanceListener", LOG));
        this.dataBroker = dataBroker;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
        this.elanService = elanService;
        this.vpnManager = vpnManager;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstance) {
        LOG.trace("remove : External Subnet VPN Instance remove mapping method - key:{}. value={}",
                vpnInstance.key(), vpnInstance);
        String possibleExtSubnetUuid = vpnInstance.getVpnInstanceName();
        Optional<Subnets> optionalSubnets = NatUtil.getOptionalExternalSubnets(dataBroker,
                new Uuid(possibleExtSubnetUuid));
        if (optionalSubnets.isPresent()) {
            addOrDelDefaultFibRouteToSNATFlow(vpnInstance, optionalSubnets.get(), NwConstants.DEL_FLOW);
            invokeSubnetDeletedFromVpn(possibleExtSubnetUuid);
        }
    }

    @Override
    public void update(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstanceOrig,
            VpnInstance vpnInstanceNew) {
        LOG.trace("update : External Subnet VPN Instance update mapping method - key:{} original:{} new:{}",
                vpnInstanceNew.key(), vpnInstanceOrig, vpnInstanceNew);
    }

    @Override
    public void add(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstance) {
        LOG.trace("add : External Subnet VPN Instance OP Data Entry add mapping method - key:{}. value={}",
                vpnInstance.key(), vpnInstance);
        String possibleExtSubnetUuid = vpnInstance.getVpnInstanceName();
        Optional<Subnets> optionalSubnets = NatUtil.getOptionalExternalSubnets(dataBroker,
                new Uuid(possibleExtSubnetUuid));
        if (optionalSubnets.isPresent()) {
            LOG.debug("add : VpnInstance {} for external subnet {}.", possibleExtSubnetUuid,
                    optionalSubnets.get());
            addOrDelDefaultFibRouteToSNATFlow(vpnInstance, optionalSubnets.get(), NwConstants.ADD_FLOW);
            invokeSubnetAddedToVpn(possibleExtSubnetUuid);
        }
    }

    private void invokeSubnetAddedToVpn(String externalSubnetId) {
        Uuid externalSubnetUuid = new Uuid(externalSubnetId);
        Subnetmap subnetMap = NatUtil.getSubnetMap(dataBroker, externalSubnetUuid);
        if (subnetMap == null) {
            LOG.error("invokeSubnetAddedToVpn : Cannot invoke onSubnetAddedToVpn for subnet-id {} in vpn-id {}"
                    + " due to this subnet missing in Subnetmap model", externalSubnetUuid, externalSubnetId);
            return;
        }
        ElanInstance elanInstance = elanService.getElanInstance(subnetMap.getNetworkId().getValue());
        vpnManager.onSubnetAddedToVpn(subnetMap, false, elanInstance.getElanTag().toJava());

    }

    private void invokeSubnetDeletedFromVpn(String externalSubnetId) {
        Uuid externalSubnetUuid = new Uuid(externalSubnetId);
        Subnetmap subnetMap = NatUtil.getSubnetMap(dataBroker, externalSubnetUuid);
        if (subnetMap == null) {
            LOG.error("invokeSubnetDeletedFromVpn : Cannot invoke invokeSubnetDeletedFromVpn for subnet-id {} in "
                    + "vpn-id {} due to this subnet missing in Subnetmap model", externalSubnetUuid, externalSubnetId);
            return;
        }
        vpnManager.onSubnetDeletedFromVpn(subnetMap, false);
    }

    private void addOrDelDefaultFibRouteToSNATFlow(VpnInstance vpnInstance, Subnets subnet, int flowAction) {
        String vpnInstanceName = vpnInstance.getVpnInstanceName();
        LOG.debug("addOrDelDefaultFibRouteToSNATFlow : VpnInstance {} for external subnet {}.",
                vpnInstanceName, subnet);
        snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnet(subnet,
                subnet.getExternalNetworkId().getValue(), flowAction, vpnInstance.getVpnId());
    }
}

/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
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
public class ExternalSubnetVpnInstanceListener extends AsyncDataTreeChangeListenerBase<VpnInstance,
    ExternalSubnetVpnInstanceListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetVpnInstanceListener.class);
    private final DataBroker dataBroker;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;
    private final IElanService elanService;
    private final IVpnManager vpnManager;

    @Inject
    public ExternalSubnetVpnInstanceListener(final DataBroker dataBroker,
            final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
            final IElanService elanService, final IVpnManager vpnManager) {
        this.dataBroker = dataBroker;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
        this.elanService = elanService;
        this.vpnManager = vpnManager;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInstance> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstanceToVpnId.class).child(VpnInstance.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstance) {
        LOG.trace("NAT Service : External Subnet VPN Instance remove mapping method - key:{}. value={}",
                key, vpnInstance);
        String possibleExtSubnetUuid = vpnInstance.getVpnInstanceName();
        Optional<Subnets> optionalSubnets = NatUtil.getOptionalExternalSubnets(dataBroker,
                new Uuid(possibleExtSubnetUuid));
        if (optionalSubnets.isPresent()) {
            addOrDelDefaultFibRouteToSNATFlow(vpnInstance, optionalSubnets.get(), NwConstants.DEL_FLOW);
            // for all subnetmaps with externalvpnid ==  vpnInstance.getVpnId();
            //   addOrDelDefaultFibRouteToExternalFlow(vpnInstance, subnetmap, optionalSubnets.get().getExternalNetworkId().getValue(), NwConstants.DEL_FLOW);
            invokeSubnetDeletedFromVpn(possibleExtSubnetUuid);
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstanceOrig,
            VpnInstance vpnInstanceNew) {
        LOG.trace("NAT Service : External Subnet VPN Instance update mapping method - key:{}. original={}, new={}",
                key, vpnInstanceOrig, vpnInstanceNew);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstance> key, VpnInstance vpnInstance) {
        LOG.trace("NAT Service : External Subnet VPN Instance OP Data Entry add mapping method - key:{}. value={}",
                key, vpnInstance);
        String possibleExtSubnetUuid = vpnInstance.getVpnInstanceName();
        Optional<Subnets> optionalSubnets = NatUtil.getOptionalExternalSubnets(dataBroker,
                new Uuid(possibleExtSubnetUuid));
        if (optionalSubnets.isPresent()) {
            LOG.debug("NAT Service : VpnInstance {} for external subnet {}.", possibleExtSubnetUuid,
                    optionalSubnets.get());
            addOrDelDefaultFibRouteToSNATFlow(vpnInstance, optionalSubnets.get(), NwConstants.ADD_FLOW);
            // for all subnetmaps with externalvpnid ==  vpnInstance.getVpnId();
            //   addOrDelDefaultFibRouteToExternalFlow(vpnInstance, subnetmap, optionalSubnets.get().getExternalNetworkId().getValue(), NwConstants.ADD_FLOW);
            invokeSubnetAddedToVpn(possibleExtSubnetUuid);
        }
    }

    private void invokeSubnetAddedToVpn(String externalSubnetId) {
        Uuid externalSubnetUuid = new Uuid(externalSubnetId);
        Subnetmap subnetMap = NatUtil.getSubnetMap(dataBroker, externalSubnetUuid);
        if (subnetMap == null) {
            LOG.error("Cannot invoke onSubnetAddedToVpn for subnet-id {} in vpn-id {}"
                    + " due to this subnet missing in Subnetmap model", externalSubnetUuid, externalSubnetId);
            return;
        }
        ElanInstance elanInstance = elanService.getElanInstance(subnetMap.getNetworkId().getValue());
        vpnManager.onSubnetAddedToVpn(subnetMap, false, elanInstance.getElanTag());

    }

    private void invokeSubnetDeletedFromVpn(String externalSubnetId) {
        Uuid externalSubnetUuid = new Uuid(externalSubnetId);
        Subnetmap subnetMap = NatUtil.getSubnetMap(dataBroker, externalSubnetUuid);
        vpnManager.onSubnetDeletedFromVpn(subnetMap, false);
    }

    static FlowEntity buildDefaultIPv6FlowEntityForExternalSubnet(BigInteger dpId, long vpnId, String subnetId,
            IdManagerService idManager) {
        InetAddress defaultIP = null;
        try {
            defaultIP = InetAddress.getByName("0.0.0.0");
        } catch (UnknownHostException e) {
            LOG.error("NAT Service : UnknowHostException in buildDefNATFlowEntityForExternalSubnet. "
                + "Failed to build FIB Table Flow for Default Route to NAT.");
            return null;
        }
	// see code 

        return flowEntity;
    }

    void addOrDelDefaultFibRouteToExternalVpnForSubnetMap(Subnetmap subnetmap, String networkId, int flowAction, long vpnId) {
        String subnet = subnetmap.getSubnet().getValue();
        String subnetId = subnet.getId().getValue();
        InstanceIdentifier<VpnInstanceOpDataEntry> networkVpnInstanceIdentifier =
            NatUtil.getVpnInstanceOpDataIdentifier(networkId);
        Optional<VpnInstanceOpDataEntry> networkVpnInstanceOp = NatUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, networkVpnInstanceIdentifier);
        if (networkVpnInstanceOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = networkVpnInstanceOp.get().getVpnToDpnList();
            if (dpnListInVpn != null) {
                for (VpnToDpnList dpn : dpnListInVpn) {
                    FlowEntity flowEntity = buildDefaultIPv6FlowEntityForExternalSubnet(dpn.getDpnId(),
                            vpnId, subnetId, idManager);
                    if (flowAction == NwConstants.ADD_FLOW || flowAction == NwConstants.MOD_FLOW) {
                        LOG.info("IPv6 Service : Installing flow {} for subnetId {}, vpnId {} on dpn {}",
                                flowEntity, subnetId, vpnId, dpn.getDpnId());
                        mdsalManager.installFlow(flowEntity);
                    } else {
                        LOG.info("IPv6 Service : Removing flow for subnetId {}, vpnId {} with dpn {}",
                                subnetId, vpnId, dpn);
                        mdsalManager.removeFlow(flowEntity);
                    }
                }
            } else {
                LOG.debug("Will not add/remove default NAT flow for subnet {} no dpn set for vpn instance {}",
                    subnetId, networkVpnInstanceOp.get());
            }
        } else {
            LOG.debug("Cannot create/remove default FIB route to SNAT flow for subnet  {} "
                + "vpn-instance-op-data entry for network {} does not exist",
                subnetId, networkId);
        }
    }

    private void addOrDelDefaultFibRouteToExternalFlow(VpnInstance vpnInstance, Subnetmap subnet, String networkId, int flowAction) {
        String vpnInstanceName = vpnInstance.getVpnInstanceName();
        LOG.debug("NAT Service : VpnInstance {} for external subnet {}.", vpnInstanceName, subnet);
        Long vpnId = vpnInstance.getVpnId();
        addOrDelDefaultFibRouteToExternalVpnForSubnetMap(subnetmap, networkId, flowAction, vpnId);
    }

    private void addOrDelDefaultFibRouteToSNATFlow(VpnInstance vpnInstance, Subnets subnet, int flowAction) {
        String vpnInstanceName = vpnInstance.getVpnInstanceName();
        LOG.debug("NAT Service : VpnInstance {} for external subnet {}.", vpnInstanceName, subnet);
        Long vpnId = vpnInstance.getVpnId();
        snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnet(subnet,
                subnet.getExternalNetworkId().getValue(), flowAction, vpnId);
    }

    @Override
    protected ExternalSubnetVpnInstanceListener getDataTreeChangeListener() {
        return ExternalSubnetVpnInstanceListener.this;
    }
}

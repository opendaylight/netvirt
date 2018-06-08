/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.ExternalGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.external_gateway_info.ExternalFixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronvpnNatManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnNatManager.class);
    private static final int EXTERNAL_NO_CHANGE = 0;
    private static final int EXTERNAL_ADDED = 1;
    private static final int EXTERNAL_REMOVED = 2;
    private static final int EXTERNAL_CHANGED = 3;

    private final DataBroker dataBroker;
    private final NeutronvpnUtils neutronvpnUtils;
    private final NeutronvpnManager nvpnManager;
    private final IElanService elanService;

    @Inject
    public NeutronvpnNatManager(final DataBroker dataBroker, final NeutronvpnUtils neutronvpnUtils,
                                  final NeutronvpnManager neutronvpnManager, final IElanService elanService) {
        this.dataBroker = dataBroker;
        this.neutronvpnUtils = neutronvpnUtils;
        this.nvpnManager = neutronvpnManager;
        this.elanService = elanService;
    }

    @Override
    @PreDestroy
    public void close() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public void handleExternalNetworkForRouter(Router original, Router update) {
        Uuid routerId = update.getUuid();
        Uuid origExtNetId = null;
        Uuid updExtNetId = null;
        List<ExternalFixedIps> origExtFixedIps;

        LOG.trace("handleExternalNetwork for router {}", routerId);
        int extNetChanged = externalNetworkChanged(original, update);
        if (extNetChanged != EXTERNAL_NO_CHANGE) {
            if (extNetChanged == EXTERNAL_ADDED) {
                updExtNetId = update.getExternalGatewayInfo().getExternalNetworkId();
                LOG.trace("External Network {} addition detected for router {}", updExtNetId.getValue(),
                        routerId.getValue());
                addExternalNetworkToRouter(update);
                return;
            }
            if (extNetChanged == EXTERNAL_REMOVED) {
                origExtNetId = original.getExternalGatewayInfo().getExternalNetworkId();
                origExtFixedIps = original.getExternalGatewayInfo().getExternalFixedIps();
                LOG.trace("External Network removal detected for router {}", routerId.getValue());
                removeExternalNetworkFromRouter(origExtNetId, update, origExtFixedIps);
                //gateway mac unset handled as part of gateway clear deleting top-level routers node
                return;
            }

            origExtNetId = original.getExternalGatewayInfo().getExternalNetworkId();
            origExtFixedIps = original.getExternalGatewayInfo().getExternalFixedIps();
            updExtNetId = update.getExternalGatewayInfo().getExternalNetworkId();
            LOG.trace("External Network changed from {} to {} for router {}",
                origExtNetId.getValue(), updExtNetId.getValue(), routerId.getValue());
            removeExternalNetworkFromRouter(origExtNetId, update, origExtFixedIps);
            addExternalNetworkToRouter(update);
            return;
        }

        if (snatSettingChanged(original, update)) {
            LOG.trace("SNAT settings on gateway changed for router {}", routerId.getValue());
            handleSnatSettingChangeForRouter(update);
        }

        if (externalFixedIpsChanged(original, update)) {
            LOG.trace("External Fixed IPs changed for router {}", routerId.getValue());
            handleExternalFixedIpsForRouter(update);
        }
    }

    private int externalNetworkChanged(Router original, Router update) {
        String origExtNet = null;
        String newExtNet = null;
        if (original != null && original.getExternalGatewayInfo() != null) {
            origExtNet = original.getExternalGatewayInfo().getExternalNetworkId().getValue();
        }

        if (update != null && update.getExternalGatewayInfo() != null) {
            newExtNet = update.getExternalGatewayInfo().getExternalNetworkId().getValue();
        }

        if (origExtNet == null) {
            if (newExtNet == null) {
                return EXTERNAL_NO_CHANGE;
            }
            return EXTERNAL_ADDED;
        } else {
            if (newExtNet == null) {
                return EXTERNAL_REMOVED;
            }
            if (!origExtNet.equals(newExtNet)) {
                return EXTERNAL_CHANGED;
            }
            return EXTERNAL_NO_CHANGE;
        }
    }

    private boolean snatSettingChanged(Router orig, Router update) {
        ExternalGatewayInfo origExtGw = null;
        ExternalGatewayInfo newExtGw = null;
        if (orig != null && orig.getExternalGatewayInfo() != null) {
            origExtGw = orig.getExternalGatewayInfo();
        }

        if (update != null && update.getExternalGatewayInfo() != null) {
            newExtGw = update.getExternalGatewayInfo();
        }

        if (origExtGw == null) {
            if (newExtGw != null) {
                return true;
            }
        } else if (newExtGw == null || !Objects.equals(origExtGw.isEnableSnat(), newExtGw.isEnableSnat())) {
            return true;
        }
        return false;
    }

    private boolean externalFixedIpsChanged(Router orig, Router update) {
        ExternalGatewayInfo origExtGw = null;
        ExternalGatewayInfo newExtGw = null;
        if (orig != null && orig.getExternalGatewayInfo() != null) {
            origExtGw = orig.getExternalGatewayInfo();
        }

        if (update != null && update.getExternalGatewayInfo() != null) {
            newExtGw = update.getExternalGatewayInfo();
        }

        if (origExtGw == null && newExtGw != null && newExtGw.getExternalFixedIps() != null && !newExtGw
                .getExternalFixedIps().isEmpty()) {
            return true;
        }

        if (newExtGw == null && origExtGw != null && origExtGw.getExternalFixedIps() != null && !origExtGw
                .getExternalFixedIps().isEmpty()) {
            return true;
        }

        if (origExtGw != null && newExtGw != null) {
            if (origExtGw.getExternalFixedIps() != null) {
                if (!origExtGw.getExternalFixedIps().isEmpty()) {
                    if (newExtGw.getExternalFixedIps() != null && !newExtGw.getExternalFixedIps().isEmpty()) {
                        List<ExternalFixedIps> origExtFixedIps = origExtGw.getExternalFixedIps();
                        HashSet<String> origFixedIpSet = new HashSet<>();
                        for (ExternalFixedIps fixedIps : origExtFixedIps) {
                            origFixedIpSet.add(String.valueOf(fixedIps.getIpAddress().getValue()));
                        }
                        List<ExternalFixedIps> newExtFixedIps = newExtGw.getExternalFixedIps();
                        HashSet<String> updFixedIpSet = new HashSet<>();
                        for (ExternalFixedIps fixedIps : newExtFixedIps) {
                            updFixedIpSet.add(String.valueOf(fixedIps.getIpAddress().getValue()));
                        }
                        // returns true if external subnets have changed
                        return !origFixedIpSet.equals(updFixedIpSet) ? true : false;
                    }
                    return true;
                } else if (newExtGw.getExternalFixedIps() != null && !newExtGw.getExternalFixedIps().isEmpty()) {
                    return true;
                }
            } else if (newExtGw.getExternalFixedIps() != null && !newExtGw.getExternalFixedIps().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void addExternalNetwork(Network net) {
        Uuid extNetId = net.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            LOG.trace(" Creating/Updating a new Networks node {}", extNetId.getValue());
            Optional<Networks> optionalNets =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            netsIdentifier);
            if (optionalNets.isPresent()) {
                LOG.error("External Network {} already detected to be present", extNetId.getValue());
                return;
            }
            ProviderTypes provType = NeutronvpnUtils.getProviderNetworkType(net);
            if (provType == null) {
                LOG.error("Unable to get Network Provider Type for network {}", extNetId);
                return;
            }
            NetworksBuilder builder = null;
            builder = new NetworksBuilder().withKey(new NetworksKey(extNetId)).setId(extNetId);
            builder.setVpnid(neutronvpnUtils.getVpnForNetwork(extNetId));
            builder.setRouterIds(new ArrayList<>());
            builder.setProviderNetworkType(provType);

            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            LOG.trace("Creating externalnetworks {}", networkss);
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier,
                    networkss);
            LOG.trace("Wrote externalnetwork successfully to CONFIG Datastore");
        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("Creation of External Network {} failed", extNetId.getValue(), ex);
        }
    }

    public void removeExternalNetwork(Network net) {
        Uuid extNetId = net.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalNets =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            netsIdentifier);
            LOG.trace("Removing Networks node {}", extNetId.getValue());
            if (!optionalNets.isPresent()) {
                LOG.error("External Network {} not available in the datastore", extNetId.getValue());
                return;
            }
            // Delete Networks object from the ExternalNetworks list
            LOG.trace("Deleting External Network {}", extNetId.getValue());
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier);
            LOG.trace("Deleted External Network {} successfully from CONFIG Datastore", extNetId.getValue());

        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("Deletion of External Network {} failed", extNetId.getValue(), ex);
        }
    }

    private void addExternalNetworkToRouter(Router update) {
        Uuid routerId = update.getUuid();
        Uuid extNetId = update.getExternalGatewayInfo().getExternalNetworkId();
        List<ExternalFixedIps> externalFixedIps = update.getExternalGatewayInfo().getExternalFixedIps();

        try {
            Network input = neutronvpnUtils.getNeutronNetwork(extNetId);
            ProviderTypes providerNwType = NeutronvpnUtils.getProviderNetworkType(input);
            if (providerNwType == null) {
                LOG.error("Unable to get Network Provider Type for network {}", input.getUuid().getValue());
                return;
            }
            // Add this router to the ExtRouters list
            addExternalRouter(update);

            // Update External Subnets for this router
            updateExternalSubnetsForRouter(routerId, extNetId, externalFixedIps);

            // Create and add Networks object for this External Network to the ExternalNetworks list
            InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
                .child(Networks.class, new NetworksKey(extNetId)).build();

            Optional<Networks> optionalNets =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            netsIdentifier);
            if (!optionalNets.isPresent()) {
                LOG.error("External Network {} not present in the NVPN datamodel", extNetId.getValue());
                return;
            }
            NetworksBuilder builder = new NetworksBuilder(optionalNets.get());
            List<Uuid> rtrList = builder.getRouterIds();
            if (rtrList == null) {
                rtrList = new ArrayList<>();
            }
            rtrList.add(routerId);
            builder.setRouterIds(rtrList);
            if (NeutronvpnUtils.isFlatOrVlanNetwork(input)) {
                builder.setVpnid(extNetId);
            }

            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            LOG.trace("Updating externalnetworks {}", networkss);
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier,
                    networkss);
            LOG.trace("Updated externalnetworks successfully to CONFIG Datastore");
            //get vpn external form this network external to setup vpnInternet for ipv6
            Uuid vpnExternal = neutronvpnUtils.getVpnForNetwork(extNetId);
            if (vpnExternal == null) {
                LOG.debug("addExternalNetworkToRouter : no vpnExternal for Network {}", extNetId);
            }
            LOG.debug("addExternalNetworkToRouter : the vpnExternal {}", vpnExternal);
            //get subnetmap associate to the router, any subnetmap "external" could be existing
            List<Subnetmap> snList = neutronvpnUtils.getNeutronRouterSubnetMaps(routerId);
            LOG.debug("addExternalNetworkToRouter : the vpnExternal {} subnetmap to be set with vpnInternet {}",
                    vpnExternal, snList);
            for (Subnetmap sn : snList) {
                if (sn.getInternetVpnId() == null) {
                    continue;
                }
                IpVersionChoice ipVers = neutronvpnUtils.getIpVersionFromString(sn.getSubnetIp());
                if (ipVers == IpVersionChoice.IPV6) {
                    LOG.debug("addExternalNetworkToRouter : setup vpnInternet IPv6 for vpnExternal {} subnetmap {}",
                            vpnExternal, sn);
                    nvpnManager.updateVpnInternetForSubnet(sn, vpnExternal, true);
                }
            }
        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("Creation of externalnetworks failed for {}",
                extNetId.getValue(), ex);
        }
    }

    public void removeExternalNetworkFromRouter(Uuid origExtNetId, Router update,
            List<ExternalFixedIps> origExtFixedIps) {
        Uuid routerId = update.getUuid();

        // Remove the router to the ExtRouters list
        removeExternalRouter(update);

        //Remove router entry from floating-ip-info list
        removeRouterFromFloatingIpInfo(update, dataBroker);

        // Remove the router from External Subnets
        removeRouterFromExternalSubnets(routerId, origExtNetId, origExtFixedIps);

        // Remove the router from the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(origExtNetId)).build();
        Optional<Networks> optionalNets = null;
        try {
            optionalNets = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                            LogicalDatastoreType.CONFIGURATION, netsIdentifier);
        } catch (ReadFailedException ex) {
            LOG.error("removeExternalNetworkFromRouter: Failed to remove provider network {} from router {}",
                      origExtNetId.getValue(), routerId.getValue(), ex);
            return;
        }
        if (!optionalNets.isPresent()) {
            LOG.error("removeExternalNetworkFromRouter: Provider Network {} not present in the NVPN datamodel",
                      origExtNetId.getValue());
            return;
        }
        Networks nets = optionalNets.get();
        try {
            NetworksBuilder builder = new NetworksBuilder(nets);
            List<Uuid> rtrList = builder.getRouterIds();
            if (rtrList != null) {
                rtrList.remove(routerId);
                builder.setRouterIds(rtrList);
                Networks networkss = builder.build();
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        netsIdentifier, networkss);
                LOG.trace("removeExternalNetworkFromRouter: Remove router {} from External Networks node {}",
                          routerId, origExtNetId.getValue());
            }
        } catch (TransactionCommitFailedException ex) {
            LOG.error("removeExternalNetworkFromRouter: Failed to remove provider network {} from router {}",
                      origExtNetId.getValue(), routerId.getValue(), ex);
        }

        // Remove the vpnInternetId fromSubnetmap
        Network net = neutronvpnUtils.getNeutronNetwork(nets.getId());
        List<Uuid> submapIds = neutronvpnUtils.getPrivateSubnetsToExport(net);
        for (Uuid snId : submapIds) {
            Subnetmap subnetMap = neutronvpnUtils.getSubnetmap(snId);
            if ((subnetMap == null) || (subnetMap.getInternetVpnId() == null)) {
                LOG.error("removeExternalNetworkFromRouter: Can not find Subnetmap for SubnetId {} in ConfigDS",
                          snId.getValue());
                continue;
            }
            LOG.trace("removeExternalNetworkFromRouter: Remove Internet VPN Id {} from SubnetMap {}",
                      subnetMap.getInternetVpnId(), subnetMap.getId());
            IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(subnetMap.getSubnetIp());
            if (ipVers == IpVersionChoice.IPV6) {
                nvpnManager.updateVpnInternetForSubnet(subnetMap, subnetMap.getInternetVpnId(), false);
                LOG.debug("removeExternalNetworkFromRouter: Withdraw IPv6 routes from VPN {}",
                          subnetMap.getInternetVpnId());
            }
        }
    }

    public void addExternalRouter(Router update) {
        Uuid routerId = update.getUuid();
        Uuid extNetId = update.getExternalGatewayInfo().getExternalNetworkId();
        Uuid gatewayPortId = update.getGatewayPortId();
        // Create and add Routers object for this Router to the ExtRouters list

        // Create a Routers object
        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Network input = neutronvpnUtils.getNeutronNetwork(extNetId);
            ProviderTypes providerNwType = NeutronvpnUtils.getProviderNetworkType(input);
            if (providerNwType == null) {
                LOG.error("Unable to get Network Provider Type for network {}", input.getUuid().getValue());
                return;
            }
            Optional<Routers> optionalRouters =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            routersIdentifier);
            LOG.trace("Creating/Updating a new Routers node: {}", routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                builder = new RoutersBuilder().withKey(new RoutersKey(routerId.getValue()));
            }
            builder.setRouterName(routerId.getValue());
            builder.setNetworkId(extNetId);
            builder.setEnableSnat(update.getExternalGatewayInfo().isEnableSnat());

            ArrayList<ExternalIps> externalIps = new ArrayList<>();
            for (ExternalFixedIps fixedIps : update.getExternalGatewayInfo().getExternalFixedIps()) {
                addExternalFixedIpToExternalIpsList(externalIps, fixedIps);
            }
            builder.setExternalIps(externalIps);

            if (gatewayPortId != null) {
                LOG.trace("Setting/Updating gateway Mac for router {}", routerId.getValue());
                Port port = neutronvpnUtils.getNeutronPort(gatewayPortId);
                if (port != null && port.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_GATEWAY_INF)) {
                    builder.setExtGwMacAddress(port.getMacAddress().getValue());
                }
            }
            List<Uuid> subList = neutronvpnUtils.getNeutronRouterSubnetIds(routerId);
            builder.setSubnetIds(subList);
            Routers routers = builder.build();
            // Add Routers object to the ExtRouters list
            LOG.trace("Creating extrouters {}", routers);
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, routersIdentifier,
                    builder.build());
            LOG.trace("Wrote successfully Routers to CONFIG Datastore");

        } catch (ReadFailedException | TransactionCommitFailedException ex) {
            LOG.error("Creation of extrouters failed for router {} failed",
                routerId.getValue(), ex);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeExternalRouter(Router update) {
        Uuid routerId = update.getUuid();

        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Optional<Routers> optionalRouters =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            routersIdentifier);
            LOG.trace(" Removing Routers node {}", routerId.getValue());
            if (optionalRouters.isPresent()) {
                RoutersBuilder builder = new RoutersBuilder(optionalRouters.get());
                builder.setExternalIps(null);
                builder.setSubnetIds(null);
                SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        routersIdentifier);
                LOG.trace("Removed router {} from extrouters", routerId.getValue());
            }
        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("Removing extrouter {} from extrouters failed", routerId.getValue(), ex);
        }
    }

    private void removeRouterFromFloatingIpInfo(Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();
        InstanceIdentifier.InstanceIdentifierBuilder<RouterPorts> routerPortsIdentifierBuilder = InstanceIdentifier
                .builder(FloatingIpInfo.class).child(RouterPorts.class, new RouterPortsKey(routerId.getValue()));
        try {
            Optional<RouterPorts> optionalRouterPorts =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            routerPortsIdentifierBuilder.build());
            if (optionalRouterPorts.isPresent()) {
                SingleTransactionDataBroker.syncDelete(broker, LogicalDatastoreType.CONFIGURATION,
                        routerPortsIdentifierBuilder.build());
            }
        } catch (ReadFailedException | TransactionCommitFailedException e) {
            LOG.error("Failed to read from FloatingIpInfo DS for routerid {}", routerId, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleExternalFixedIpsForRouter(Router update) {
        Uuid routerId = update.getUuid();
        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);
        try {
            Optional<Routers> optionalRouters =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            routersIdentifier);
            LOG.trace("Updating External Fixed IPs Routers node {}", routerId.getValue());
            if (optionalRouters.isPresent()) {
                RoutersBuilder builder = new RoutersBuilder(optionalRouters.get());
                List<ExternalIps> externalIps = new ArrayList<>();
                for (ExternalFixedIps fixedIps : update.getExternalGatewayInfo().getExternalFixedIps()) {
                    addExternalFixedIpToExternalIpsList(externalIps, fixedIps);
                }

                builder.setExternalIps(externalIps);

                updateExternalSubnetsForRouter(routerId, update.getExternalGatewayInfo().getExternalNetworkId(),
                        update.getExternalGatewayInfo().getExternalFixedIps());
                Routers routerss = builder.build();
                LOG.trace("Updating external fixed ips for router {} with value {}", routerId.getValue(), routerss);
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, routersIdentifier,
                        routerss);
                LOG.trace("Added External Fixed IPs successfully for Routers to CONFIG Datastore");
            }
        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("Updating extfixedips for {} in extrouters failed", routerId.getValue(), ex);
        }
    }

    public void handleSubnetsForExternalRouter(Uuid routerId) {
        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Optional<Routers> optionalRouters =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            routersIdentifier);
            LOG.trace("Updating Internal subnets for Routers node: {}", routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                LOG.debug("No Routers element found for router {}", routerId.getValue());
                return;
            }
            List<Uuid> subList = neutronvpnUtils.getNeutronRouterSubnetIds(routerId);
            builder.setSubnetIds(subList);
            Routers routerss = builder.build();
            // Add Routers object to the ExtRouters list
            LOG.trace("Updating extrouters {}", routerss);
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier, routerss);
            LOG.trace("Updated successfully Routers to CONFIG Datastore");
        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("Updation of internal subnets for extrouters failed for router {}",
                routerId.getValue(), ex);
        }
    }

    private void handleSnatSettingChangeForRouter(Router update) {
        Uuid routerId = update.getUuid();

        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Optional<Routers> optionalRouters =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            routersIdentifier);
            LOG.trace("Updating Internal subnets for Routers node: {}", routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                LOG.trace("No Routers element found for router name {}", routerId.getValue());
                return;
            }
            builder.setEnableSnat(update.getExternalGatewayInfo().isEnableSnat());
            Routers routerss = builder.build();
            // Add Routers object to the ExtRouters list
            LOG.trace("Updating extrouters for snat change {}", routerss);
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier, routerss);
            LOG.trace("Updated successfully Routers to CONFIG Datastore");

        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("Updation of snat for extrouters failed for router {}", routerId.getValue(), ex);
        }
    }

    public void updateOrAddExternalSubnet(Uuid networkId, Uuid subnetId, List<Uuid> routerIds) {
        Optional<Subnets> optionalExternalSubnets = neutronvpnUtils.getOptionalExternalSubnets(subnetId);
        if (optionalExternalSubnets.isPresent()) {
            LOG.trace("Will update external subnet {} with networkId {} and routerIds {}",
                    subnetId, networkId, routerIds);
            updateExternalSubnet(networkId, subnetId, routerIds);
        } else {
            LOG.trace("Will add external subnet {} with networkId {} and routerIds {}",
                    subnetId, networkId, routerIds);
            addExternalSubnet(networkId, subnetId, routerIds);
        }
    }

    public void addExternalSubnet(Uuid networkId, Uuid subnetId, List<Uuid> routerIds) {
        InstanceIdentifier<Subnets> subnetsIdentifier = InstanceIdentifier.builder(ExternalSubnets.class)
                .child(Subnets.class, new SubnetsKey(subnetId)).build();
        try {
            Subnets newExternalSubnets = createSubnets(subnetId, networkId, routerIds);
            LOG.debug("Creating external subnet {}", newExternalSubnets);
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetsIdentifier,
                    newExternalSubnets);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("Creation of External Subnets {} failed", subnetId, ex);
        }
    }

    public void updateExternalSubnet(Uuid networkId, Uuid subnetId, List<Uuid> routerIds) {
        InstanceIdentifier<Subnets> subnetsIdentifier = InstanceIdentifier.builder(ExternalSubnets.class)
                .child(Subnets.class, new SubnetsKey(subnetId)).build();
        try {
            Subnets newExternalSubnets = createSubnets(subnetId, networkId, routerIds);
            LOG.debug("Updating external subnet {}", newExternalSubnets);
            SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetsIdentifier,
                    newExternalSubnets);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("Update of External Subnets {} failed", subnetId, ex);
        }
    }

    public void removeExternalSubnet(Uuid networkId, Uuid subnetId) {
        removeAdjacencyAndLearnedEntriesforExternalSubnet(networkId, subnetId);
        InstanceIdentifier<Subnets> subnetsIdentifier = InstanceIdentifier.builder(ExternalSubnets.class)
                .child(Subnets.class, new SubnetsKey(subnetId)).build();
        try {
            LOG.debug("Removing external subnet {}", subnetId);
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetsIdentifier);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("Deletion of External Subnets {} failed", subnetId, ex);
        }
    }

    public void addRouterIdToExternalSubnet(Uuid networkId, Uuid subnetId, Uuid routerId) {
        Optional<Subnets> optionalExternalSubnets = neutronvpnUtils.getOptionalExternalSubnets(subnetId);
        if (optionalExternalSubnets.isPresent()) {
            Subnets subnets = optionalExternalSubnets.get();
            List<Uuid> routerIds;
            if (subnets.getRouterIds() != null) {
                routerIds = subnets.getRouterIds();
            } else {
                routerIds = new ArrayList<>();
            }

            if (subnets.getExternalNetworkId() != null
                    && subnets.getExternalNetworkId().equals(networkId) && !routerIds.contains(routerId)) {
                LOG.debug("Will add routerID {} for external subnet {}.", routerId, subnetId);
                routerIds.add(routerId);
                updateExternalSubnet(networkId, subnetId, routerIds);
            }
        }
    }

    private Subnets createSubnets(Uuid subnetId, Uuid networkId, List<Uuid> routerIds) {
        SubnetsBuilder subnetsBuilder = new SubnetsBuilder();
        subnetsBuilder.withKey(new SubnetsKey(subnetId));
        subnetsBuilder.setId(subnetId);
        subnetsBuilder.setVpnId(subnetId);
        subnetsBuilder.setExternalNetworkId(networkId);
        if (routerIds != null) {
            subnetsBuilder.setRouterIds(routerIds);
        }

        return subnetsBuilder.build();
    }

    private void updateExternalSubnetsForRouter(Uuid routerId, Uuid externalNetworkId,
            List<ExternalFixedIps> externalFixedIps) {
        LOG.debug("Updating external subnets for router {} for external network ID {}",
                routerId, externalNetworkId);
        Set<Uuid> subnetsUuidsSet = getExternalSubnetsUuidsSetForFixedIps(externalFixedIps);
        for (Uuid subnetId : subnetsUuidsSet) {
            addRouterIdToExternalSubnet(externalNetworkId, subnetId, routerId);
        }
    }

    private void removeRouterFromExternalSubnets(Uuid routerId, Uuid externalNetworkId,
            List<ExternalFixedIps> externalFixedIps) {
        LOG.debug("Removing routerID {} from external subnets of external network{}",
                routerId, externalNetworkId);

        List<Subnets> fixedIpsSubnets = getSubnets(getExternalSubnetsUuidsSetForFixedIps(externalFixedIps));
        for (Subnets subnets : fixedIpsSubnets) {
            Uuid subnetId = subnets.getId();
            List<Uuid> routerIds = subnets.getRouterIds();
            if (routerIds != null) {
                if (subnets.getExternalNetworkId() != null
                        && subnets.getExternalNetworkId().equals(externalNetworkId)
                        && routerIds.contains(routerId)) {
                    routerIds.remove(routerId);
                    LOG.debug("Will remove routerIDs {} from external subnet {} router ID {}",
                        routerIds, subnetId, routerId);
                    addExternalSubnet(externalNetworkId, subnetId, routerIds);
                }
            }
        }
    }

    private Set<Uuid> getExternalSubnetsUuidsSetForFixedIps(List<ExternalFixedIps> externalFixedIps) {
        Set<Uuid> subnetsUuidsSet = new HashSet<>();
        for (ExternalFixedIps externalFixedIp : externalFixedIps) {
            subnetsUuidsSet.add(externalFixedIp.getSubnetId());
        }

        return subnetsUuidsSet;
    }

    private List<Subnets> getSubnets(Set<Uuid> subnetsUuidsSet) {
        List<Subnets> subnetsList = new ArrayList<>();
        for (Uuid subnetId : subnetsUuidsSet) {
            Optional<Subnets> optionalSubnets = neutronvpnUtils.getOptionalExternalSubnets(subnetId);
            if (optionalSubnets.isPresent()) {
                subnetsList.add(optionalSubnets.get());
            }
        }

        return subnetsList;
    }

    private void addExternalFixedIpToExternalIpsList(List<ExternalIps> externalIps, ExternalFixedIps fixedIps) {
        Uuid subnetId = fixedIps.getSubnetId();
        String ip = String.valueOf(fixedIps.getIpAddress().getValue());
        ExternalIpsBuilder externalIpsBuilder = new ExternalIpsBuilder();
        externalIpsBuilder.withKey(new ExternalIpsKey(ip, subnetId));
        externalIpsBuilder.setIpAddress(ip);
        externalIpsBuilder.setSubnetId(subnetId);
        externalIps.add(externalIpsBuilder.build());
    }

    private void removeAdjacencyAndLearnedEntriesforExternalSubnet(Uuid extNetId, Uuid extSubnetId) {
        Collection<String> extElanInterfaces = elanService.getExternalElanInterfaces(extNetId.getValue());
        if (extElanInterfaces == null || extElanInterfaces.isEmpty()) {
            LOG.error("No external ports attached to external network {}", extNetId.getValue());
            return;
        }

        for (String infName : extElanInterfaces) {
            InstanceIdentifier<VpnInterface> vpnIfIdentifier = InstanceIdentifier.builder(
                VpnInterfaces.class).child(VpnInterface.class, new VpnInterfaceKey(infName)).build();
            InstanceIdentifier<Adjacencies> adjacenciesIdentifier = vpnIfIdentifier.augmentation(Adjacencies.class);
            try {
                // Looking for existing prefix in MDSAL database
                Optional<Adjacencies> optionalAdjacencies = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, adjacenciesIdentifier);
                if (optionalAdjacencies.isPresent()) {
                    List<Adjacency> adjacencies = optionalAdjacencies.get().getAdjacency();
                    Iterator<Adjacency> adjacencyIter = adjacencies.iterator();
                    while (adjacencyIter.hasNext()) {
                        Adjacency adjacency = adjacencyIter.next();
                        if (!adjacency.getSubnetId().equals(extSubnetId)) {
                            continue;
                        }
                        InstanceIdentifier<Adjacency> adjacencyIdentifier =
                            adjacenciesIdentifier.child(Adjacency.class, new AdjacencyKey(adjacency.getIpAddress()));
                        SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            adjacencyIdentifier);
                        LOG.trace("Removed Adjacency for fixedIP {} for port {} on external subnet {} ",
                            adjacency.getIpAddress(), infName, extSubnetId);
                        String extNetVpnName = extNetId.getValue();
                        String learnedSrcIp = adjacency.getIpAddress().split("/")[0];
                        InstanceIdentifier<LearntVpnVipToPort> id =
                            NeutronvpnUtils.buildLearntVpnVipToPortIdentifier(extNetVpnName, learnedSrcIp);
                        Optional<LearntVpnVipToPort> optionalLearntVpnVipToPort = SingleTransactionDataBroker
                            .syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
                        if (optionalLearntVpnVipToPort.isPresent()) {
                            neutronvpnUtils.removeLearntVpnVipToPort(extNetVpnName, learnedSrcIp);
                            LOG.trace("Removed Learnt Entry for fixedIP {} for port {}",
                                adjacency.getIpAddress(), infName);
                        }
                    }
                }
            } catch (TransactionCommitFailedException | ReadFailedException e) {
                LOG.error("exception in removeAdjacencyAndLearnedEntriesforExternalSubnet for interface {}",
                    infName, e);
            }
        }
    }
}

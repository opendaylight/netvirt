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
import java.util.HashSet;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.ExternalGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.external_gateway_info.ExternalFixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronvpnNatManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnNatManager.class);
    private final DataBroker dataBroker;
    private static final int EXTERNAL_NO_CHANGE = 0;
    private static final int EXTERNAL_ADDED = 1;
    private static final int EXTERNAL_REMOVED = 2;
    private static final int EXTERNAL_CHANGED = 3;

    public NeutronvpnNatManager(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public void handleExternalNetworkForRouter(Router original, Router update) {

        Uuid routerId = update.getUuid();
        Uuid origExtNetId = null;
        Uuid updExtNetId = null;

        LOG.trace("handleExternalNetwork for router " +  routerId);
        int extNetChanged = externalNetworkChanged(original, update);
        if (extNetChanged != EXTERNAL_NO_CHANGE) {
            if (extNetChanged == EXTERNAL_ADDED) {
                updExtNetId = update.getExternalGatewayInfo().getExternalNetworkId();
                LOG.trace("External Network " + updExtNetId.getValue()
                    + " addition detected for router " +  routerId.getValue());
                addExternalNetworkToRouter(update);
                return;
            }
            if (extNetChanged == EXTERNAL_REMOVED) {
                origExtNetId = original.getExternalGatewayInfo().getExternalNetworkId();
                LOG.trace("External Network removal detected for router {}", routerId.getValue());
                removeExternalNetworkFromRouter(origExtNetId, update);
                //gateway mac unset handled as part of gateway clear deleting top-level routers node
                return;
            }
            origExtNetId = original.getExternalGatewayInfo().getExternalNetworkId();
            updExtNetId = update.getExternalGatewayInfo().getExternalNetworkId();
            LOG.trace("External Network changed from {} to {} for router {}",
                origExtNetId.getValue(), updExtNetId.getValue(), routerId.getValue());
            removeExternalNetworkFromRouter(origExtNetId, update);
            addExternalNetworkToRouter(update);
            return;
        }

        if (snatSettingChanged(original, update)) {
            LOG.trace("SNAT settings on gateway changed for router {}", routerId.getValue());
            handleSnatSettingChangeForRouter(update, dataBroker);
        }

        if (externalFixedIpsChanged(original, update)) {
            LOG.trace("External Fixed IPs changed for router {}", routerId.getValue());
            handleExternalFixedIpsForRouter(update, dataBroker);
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
        } else if (newExtGw == null || origExtGw.isEnableSnat() != newExtGw.isEnableSnat()) {
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
                            origFixedIpSet.add(fixedIps.getIpAddress().getIpv4Address().getValue());
                        }
                        List<ExternalFixedIps> newExtFixedIps = newExtGw.getExternalFixedIps();
                        HashSet<String> updFixedIpSet = new HashSet<>();
                        for (ExternalFixedIps fixedIps : newExtFixedIps) {
                            updFixedIpSet.add(fixedIps.getIpAddress().getIpv4Address().getValue());
                        }
                        // returns true if external subnets have changed
                        return (!origFixedIpSet.equals(updFixedIpSet)) ? true : false;
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

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void addExternalNetwork(Network net) {
        Uuid extNetId = net.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            LOG.trace(" Creating/Updating a new Networks node: " +  extNetId.getValue());
            Optional<Networks> optionalNets = NeutronvpnUtils.read(dataBroker,
                    LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            if (optionalNets.isPresent()) {
                LOG.error("External Network {} already detected to be present", extNetId.getValue());
                return;
            }
            ProviderTypes provType = NeutronvpnUtils.getProviderNetworkType(net);
            if (provType == null) {
                LOG.error("Unable to get Network Provider Type for network {}", net.getUuid());
                return;
            }
            NetworksBuilder builder = null;
            builder = new NetworksBuilder().setKey(new NetworksKey(extNetId)).setId(extNetId);
            builder.setVpnid(NeutronvpnUtils.getVpnForNetwork(dataBroker, extNetId));
            builder.setRouterIds(new ArrayList<>());
            builder.setProviderNetworkType(provType);

            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            LOG.trace("Creating externalnetworks " + networkss);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
            LOG.trace("Wrote externalnetwork successfully to CONFIG Datastore");
        } catch (Exception ex) {
            LOG.error("Creation of External Network {} failed: {}", extNetId.getValue(), ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void removeExternalNetwork(Network net) {
        Uuid extNetId = net.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(dataBroker,
                    LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            LOG.trace("Removing Networks node: " +  extNetId.getValue());
            if (!optionalNets.isPresent()) {
                LOG.info("External Network {} not available in the datastore", extNetId.getValue());
                return;
            }
            // Delete Networks object from the ExternalNetworks list
            LOG.trace("Deleting External Network " + extNetId.getValue());
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier);
            LOG.trace("Deleted External Network {} successfully from CONFIG Datastore", extNetId.getValue());

        } catch (Exception ex) {
            LOG.error("Deletion of External Network {} failed: {}", extNetId.getValue(), ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addExternalNetworkToRouter(Router update) {
        Uuid routerId = update.getUuid();
        Uuid extNetId = update.getExternalGatewayInfo().getExternalNetworkId();

        try {
            Network input = NeutronvpnUtils.getNeutronNetwork(dataBroker, extNetId);
            ProviderTypes providerNwType = NeutronvpnUtils.getProviderNetworkType(input);
            if (providerNwType == null) {
                LOG.error("Unable to get Network Provider Type for network {} and uuid {}",
                    input.getName(), input.getUuid());
                return;
            }
            // Add this router to the ExtRouters list
            addExternalRouter(update, dataBroker);

            // Create and add Networks object for this External Network to the ExternalNetworks list
            InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
                .child(Networks.class, new NetworksKey(extNetId)).build();

            Optional<Networks> optionalNets = NeutronvpnUtils.read(dataBroker,
                    LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            NetworksBuilder builder = null;
            if (optionalNets.isPresent()) {
                builder = new NetworksBuilder(optionalNets.get());
            } else {
                LOG.error("External Network {} not present in the NVPN datamodel", extNetId.getValue());
                return;
            }
            List<Uuid> rtrList = builder.getRouterIds();
            if (rtrList == null) {
                rtrList = new ArrayList<>();
            }
            rtrList.add(routerId);
            builder.setRouterIds(rtrList);
            if (providerNwType != ProviderTypes.GRE) {
                builder.setVpnid(extNetId);
            }

            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            LOG.trace("Updating externalnetworks " + networkss);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
            LOG.trace("Updated externalnetworks successfully to CONFIG Datastore");
        } catch (Exception ex) {
            LOG.error("Creation of externalnetworks failed for {} with exception {}",
                extNetId.getValue(), ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void removeExternalNetworkFromRouter(Uuid origExtNetId, Router update) {
        Uuid routerId = update.getUuid();

        // Remove the router to the ExtRouters list
        removeExternalRouter(origExtNetId, update, dataBroker);

        // Remove the router from the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(origExtNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(dataBroker,
                    LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            LOG.trace("Removing a router from External Networks node: {}", origExtNetId.getValue());
            if (optionalNets.isPresent()) {
                NetworksBuilder builder = new NetworksBuilder(optionalNets.get());
                List<Uuid> rtrList = builder.getRouterIds();
                if (rtrList != null) {
                    rtrList.remove(routerId);
                    builder.setRouterIds(rtrList);
                    Networks networkss = builder.build();
                    MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
                    LOG.trace("Removed router {} from externalnetworks {}", routerId, origExtNetId.getValue());
                }
            }
        } catch (Exception ex) {
            LOG.error("Removing externalnetwork {} from router {} failed {}",
                origExtNetId.getValue(), routerId, ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void addExternalNetworkToVpn(Network network, Uuid vpnId) {
        Uuid extNetId = network.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            LOG.trace("Adding vpn-id into Networks node: " +  extNetId.getValue());
            NetworksBuilder builder = null;
            if (optionalNets.isPresent()) {
                builder = new NetworksBuilder(optionalNets.get());
            } else {
                LOG.error("External Network {} not present in the NVPN datamodel", extNetId.getValue());
                return;
            }
            builder.setVpnid(vpnId);
            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            LOG.trace("Setting VPN-ID for externalnetworks " + networkss);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
            LOG.trace("Wrote with VPN-ID successfully to CONFIG Datastore");

        } catch (Exception ex) {
            LOG.error("Attaching VPN-ID to externalnetwork {} failed with {}", extNetId.getValue(), ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void removeExternalNetworkFromVpn(Network network) {
        Uuid extNetId = network.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            LOG.trace("Removing vpn-id from Networks node: " +  extNetId.getValue());
            NetworksBuilder builder = null;
            if (optionalNets.isPresent()) {
                builder = new NetworksBuilder(optionalNets.get());
            } else {
                LOG.error("External Network " + extNetId.getValue() + " not present in the NVPN datamodel");
                return;
            }

            builder.setVpnid(null);
            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            LOG.trace("Remove vpn-id for externalnetwork " + networkss);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
            LOG.trace("Updated extnetworks successfully to CONFIG Datastore");

        } catch (Exception ex) {
            LOG.error("Removing VPN-ID from externalnetworks {} failed with {}", extNetId.getValue(), ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void addExternalRouter(Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();
        Uuid extNetId = update.getExternalGatewayInfo().getExternalNetworkId();
        Uuid gatewayPortId = update.getGatewayPortId();

        // Create and add Routers object for this Router to the ExtRouters list

        // Create a Routers object
        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Network input = NeutronvpnUtils.getNeutronNetwork(dataBroker, extNetId);
            ProviderTypes providerNwType = NeutronvpnUtils.getProviderNetworkType(input);
            if (providerNwType == null) {
                LOG.error("Unable to get Network Provider Type for network {} and uuid{}",
                    input.getName(), input.getUuid());
                return;
            }
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            LOG.trace("Creating/Updating a new Routers node: " + routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                builder = new RoutersBuilder().setKey(new RoutersKey(routerId.getValue()));
            }
            builder.setRouterName(routerId.getValue());
            builder.setNetworkId(extNetId);
            builder.setEnableSnat(update.getExternalGatewayInfo().isEnableSnat());

            ArrayList<String> extFixedIps = new ArrayList<>();
            for (ExternalFixedIps fixedIps : update.getExternalGatewayInfo().getExternalFixedIps()) {
                extFixedIps.add(fixedIps.getIpAddress().getIpv4Address().getValue());
            }
            builder.setExternalIps(extFixedIps);

            if (gatewayPortId != null) {
                LOG.trace("Setting/Updating gateway Mac for router {}", routerId.getValue());
                Port port = NeutronvpnUtils.getNeutronPort(broker, gatewayPortId);
                if (port != null && port.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_GATEWAY_INF)) {
                    builder.setExtGwMacAddress(port.getMacAddress().getValue());
                }
            }
            List<Uuid> subList = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);
            builder.setSubnetIds(subList);
            Routers routers = builder.build();
            // Add Routers object to the ExtRouters list
            LOG.trace("Creating extrouters " + routers);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier, builder.build());
            LOG.trace("Wrote successfully Routers to CONFIG Datastore");

        } catch (Exception ex) {
            LOG.error("Creation of extrouters failed for router {} failed with {}",
                routerId.getValue(),  ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeExternalRouter(Uuid extNetId, Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();

        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            LOG.trace(" Removing Routers node: " +  routerId.getValue());
            if (optionalRouters.isPresent()) {
                RoutersBuilder builder = new RoutersBuilder(optionalRouters.get());
                builder.setExternalIps(null);
                builder.setSubnetIds(null);
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier);
                LOG.trace("Removed router " + routerId.getValue() + " from extrouters ");
            }
        } catch (Exception ex) {
            LOG.error("Removing extrouter {} from extrouters failed with {}", routerId.getValue(), ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleExternalFixedIpsForRouter(Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();

        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            LOG.trace("Updating External Fixed IPs Routers node: " +  routerId.getValue());
            if (optionalRouters.isPresent()) {
                RoutersBuilder builder = new RoutersBuilder(optionalRouters.get());
                if (builder != null) {
                    ArrayList<String> extFixedIps = new ArrayList<>();
                    for (ExternalFixedIps fixedIps : update.getExternalGatewayInfo().getExternalFixedIps()) {
                        extFixedIps.add(fixedIps.getIpAddress().getIpv4Address().getValue());
                    }
                    builder.setExternalIps(extFixedIps);
                }
                Routers routerss = builder.build();
                LOG.trace("Updating external fixed ips for router {} with value {}", routerId.getValue(), routerss);
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier, routerss);
                LOG.trace("Added External Fixed IPs successfully for Routers to CONFIG Datastore");
            }
        } catch (Exception ex) {
            LOG.error("Updating extfixedips for {} in extrouters failed with {}", routerId.getValue(), ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handleSubnetsForExternalRouter(Uuid routerId, DataBroker broker) {
        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            LOG.trace("Updating Internal subnets for Routers node: {}", routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                LOG.info("No Routers element found for router name " + routerId.getValue());
                return;
            }
            List<Uuid> subList = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);
            builder.setSubnetIds(subList);
            Routers routerss = builder.build();
            // Add Routers object to the ExtRouters list
            LOG.trace("Updating extrouters " + routerss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier, routerss);
            LOG.trace("Updated successfully Routers to CONFIG Datastore");

        } catch (Exception ex) {
            LOG.error("Updation of internal subnets for extrouters failed for router {} with {}",
                routerId.getValue(), ex.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleSnatSettingChangeForRouter(Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();

        InstanceIdentifier<Routers> routersIdentifier = NeutronvpnUtils.buildExtRoutersIdentifier(routerId);

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            LOG.trace("Updating Internal subnets for Routers node: {}", routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                LOG.trace("No Routers element found for router name " + routerId.getValue());
                return;
            }
            builder.setEnableSnat(update.getExternalGatewayInfo().isEnableSnat());
            Routers routerss = builder.build();
            // Add Routers object to the ExtRouters list
            LOG.trace("Updating extrouters for snat change " + routerss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier, routerss);
            LOG.trace("Updated successfully Routers to CONFIG Datastore");

        } catch (Exception ex) {
            LOG.error("Updation of snat for extrouters failed for router {} with {}",
                routerId.getValue(), ex.getMessage());
        }
    }
}

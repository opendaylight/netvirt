/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.ExternalGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.external_gateway_info.ExternalFixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class NeutronvpnNatManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(NeutronvpnNatManager.class);

    private final DataBroker broker;
    IMdsalApiManager mdsalUtil;

    private static final int EXTERNAL_NO_CHANGE = 0;
    private static final int EXTERNAL_ADDED = 1;
    private static final int EXTERNAL_REMOVED = 2;
    private static final int EXTERNAL_CHANGED = 3;

    /**
     * @param db           - dataBroker reference
     * @param mdsalManager - MDSAL Util API access
     */
    public NeutronvpnNatManager(final DataBroker db, IMdsalApiManager mdsalManager) {
        broker = db;
        mdsalUtil = mdsalManager;
    }

    @Override
    public void close() throws Exception {
        logger.info("Neutron VPN Nat Manager Closed.");
    }

    public void handleExternalNetworkForRouter(Router original, Router update) {

        Uuid routerId = update.getUuid();
        Uuid origExtNetId = null;
        Uuid updExtNetId = null;

        logger.trace("handleExternalNetwork for router " +  routerId);
        int ext_net_changed = externalNetworkChanged(original, update);
        if (ext_net_changed != EXTERNAL_NO_CHANGE) {
            if (ext_net_changed == EXTERNAL_ADDED) {
                updExtNetId = update.getExternalGatewayInfo().getExternalNetworkId();
                logger.trace("External Network " + updExtNetId.getValue() +
                        " addition detected for router " +  routerId.getValue());
                addExternalNetworkToRouter(update);
                return;
            }
            if (ext_net_changed == EXTERNAL_REMOVED) {
                origExtNetId = original.getExternalGatewayInfo().getExternalNetworkId();
                logger.trace("External Network removal detected " +
                        "for router " +  routerId.getValue());
                removeExternalNetworkFromRouter(origExtNetId, update);
                return;
            }
            origExtNetId = original.getExternalGatewayInfo().getExternalNetworkId();
            updExtNetId = update.getExternalGatewayInfo().getExternalNetworkId();
            logger.trace("External Network changed from "+ origExtNetId.getValue() + " to "
                    + updExtNetId.getValue() + " for router " +  routerId.getValue());
            removeExternalNetworkFromRouter(origExtNetId, update);
            addExternalNetworkToRouter(update);
            return;
        }

        if (snatSettingChanged(original, update)) {
            logger.trace("SNAT settings on gateway changed  " +
                    "for router " + routerId.getValue());
            handleSnatSettingChangeForRouter(update, broker);
        }

        if (externalFixedIpsChanged(original, update)) {
            logger.trace("External Fixed IPs changed " +
                    "for router " + routerId.getValue());
            handleExternalFixedIpsForRouter(update, broker);
        }
    }

    private int externalNetworkChanged(Router original, Router update) {
        String orig_ext_net = null;
        String new_ext_net = null;
        if (original.getExternalGatewayInfo() != null) {
            orig_ext_net = original.getExternalGatewayInfo().getExternalNetworkId().getValue();
        }

        if (update.getExternalGatewayInfo() != null) {
            new_ext_net = update.getExternalGatewayInfo().getExternalNetworkId().getValue();
        }

        if (orig_ext_net == null) {
            if (new_ext_net == null) {
                return EXTERNAL_NO_CHANGE;
            }
            return EXTERNAL_ADDED;
        }else{
            if (new_ext_net == null) {
                return EXTERNAL_REMOVED;
            }
            if (!orig_ext_net.equals(new_ext_net)) {
                return EXTERNAL_CHANGED;
            }
            return EXTERNAL_NO_CHANGE;
        }
    }

    private boolean snatSettingChanged(Router orig, Router update) {
        ExternalGatewayInfo orig_ext_gw = null;
        ExternalGatewayInfo new_ext_gw = null;
        if (orig.getExternalGatewayInfo() != null) {
            orig_ext_gw = orig.getExternalGatewayInfo();
        }

        if (update.getExternalGatewayInfo() != null) {
            new_ext_gw = update.getExternalGatewayInfo();
        }

        if ((orig_ext_gw != null) && (new_ext_gw != null)) {
            if (orig_ext_gw.isEnableSnat() != new_ext_gw.isEnableSnat()) {
                return true;
            }
        }

        return false;
    }

    private boolean externalFixedIpsChanged(Router orig, Router update) {
        ExternalGatewayInfo orig_ext_gw = null;
        ExternalGatewayInfo new_ext_gw = null;
        if (orig.getExternalGatewayInfo() != null) {
            orig_ext_gw = orig.getExternalGatewayInfo();
        }

        if (update.getExternalGatewayInfo() != null) {
            new_ext_gw = update.getExternalGatewayInfo();
        }

        if ((orig_ext_gw != null) && (new_ext_gw != null)) {
            List<ExternalFixedIps> orig_ext_fixed_ips = orig_ext_gw.getExternalFixedIps();
            HashSet<String> orig_fixed_ip_set = new HashSet<String>();
            for (ExternalFixedIps fixed_ip: orig_ext_fixed_ips) {
                orig_fixed_ip_set.add(fixed_ip.getIpAddress().toString());
            }
            HashSet<String> upd_fixed_ip_set = new HashSet<String>();
            List<ExternalFixedIps> new_ext_fixed_ips = new_ext_gw.getExternalFixedIps();
            for (ExternalFixedIps fixed_ip: new_ext_fixed_ips) {
                upd_fixed_ip_set.add(fixed_ip.getIpAddress().toString());
            }

            if (!orig_fixed_ip_set.equals(upd_fixed_ip_set)) {
                // External Subnets have changed
                return true;
            }
        }
        return false;
    }

    public void addExternalNetwork(Network net) {
        Uuid extNetId = net.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class).
                child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            logger.trace(" Creating/Updating a new Networks node: " +  extNetId.getValue());
            Optional<Networks> optionalNets = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            if (optionalNets.isPresent()) {
                logger.error("External Network " + extNetId.getValue() +
                        " already detected to be present");
                return;
            }
            NetworksBuilder builder = null;
            builder = new NetworksBuilder().setKey(new NetworksKey(extNetId)).setId(extNetId);
            builder.setVpnid(NeutronvpnUtils.getVpnForNetwork(broker, extNetId));
            builder.setRouterIds(new ArrayList<Uuid>());

            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            //isLockAcquired = NeutronvpnUtils.lock(lockManager, extNetId.getValue());
            logger.trace("Creating externalnetworks " + networkss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
            logger.trace("Wrote externalnetwork successfully to CONFIG Datastore");
        } catch (Exception ex) {
            logger.error("Creation of External Network " +
                    extNetId.getValue() + " failed " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    public void removeExternalNetwork(Network net) {
        Uuid extNetId = net.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class).
                child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            logger.trace("Removing Networks node: " +  extNetId.getValue());
            if (!optionalNets.isPresent()) {
                logger.info("External Network " + extNetId.getValue() +
                        " not available in the datastore");
                return;
            }
            // Delete Networks object from the ExternalNetworks list
            //isLockAcquired = NeutronvpnUtils.lock(lockManager, extNetId.getValue());
            logger.trace("Deleting External Network " + extNetId.getValue());
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, netsIdentifier);
            logger.trace("Deleted External Network " + extNetId.getValue() +
                    " successfully from CONFIG Datastore");

        } catch (Exception ex) {
            logger.error("Deletion of External Network " + extNetId.getValue() +
                    " failed " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    private void addExternalNetworkToRouter(Router update) {
        Uuid routerId = update.getUuid();
        Uuid extNetId = update.getExternalGatewayInfo().getExternalNetworkId();

        // Add this router to the ExtRouters list
        addExternalRouter(update, broker);

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class).
                child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            NetworksBuilder builder = null;
            if (optionalNets.isPresent()) {
                builder = new NetworksBuilder(optionalNets.get());
            } else {
                logger.error("External Network " + extNetId.getValue() +
                        " not present in the NVPN datamodel");
                return;
            }
            List<Uuid> rtrList = builder.getRouterIds();
            if (rtrList == null) {
                rtrList = new ArrayList<Uuid>();
            }
            rtrList.add(routerId);
            builder.setRouterIds(rtrList);

            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            //isLockAcquired = NeutronvpnUtils.lock(lockManager, extNetId.getValue());
            logger.trace("Updating externalnetworks " + networkss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
            logger.trace("Updated externalnetworks successfully to CONFIG Datastore");
        } catch (Exception ex) {
            logger.error("Creation of externalnetworks failed for " + extNetId.getValue() + "with exception " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    public void removeExternalNetworkFromRouter(Uuid origExtNetId, Router update) {
        Uuid routerId = update.getUuid();

        // Remove the router to the ExtRouters list
        removeExternalRouter(origExtNetId, update, broker);

        // Remove the router from the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class).
                child(Networks.class, new NetworksKey(origExtNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            logger.trace("Removing a router from External Networks node: " +
                    origExtNetId.getValue());
            if (optionalNets.isPresent()) {
                NetworksBuilder builder = new NetworksBuilder(optionalNets.get());
                List<Uuid> rtrList = builder.getRouterIds();
                if (rtrList != null) {
                    rtrList.remove(routerId);
                    builder.setRouterIds(rtrList);
                    Networks networkss = builder.build();
                    MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
                    logger.trace("Removed router " + routerId + " from externalnetworks " + origExtNetId.getValue());
                }
            }
        } catch (Exception ex) {
            logger.error("Removing externalnetwork " + origExtNetId.getValue() +
                    " from router " + routerId + " failed " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    public void addExternalNetworkToVpn(Network network, Uuid vpnId) {
        Uuid extNetId = network.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class).
                child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            logger.trace("Adding vpn-id into Networks node: " +  extNetId.getValue());
            NetworksBuilder builder = null;
            if (optionalNets.isPresent()) {
                builder = new NetworksBuilder(optionalNets.get());
            } else {
                logger.error("External Network " + extNetId.getValue() +
                        " not present in the NVPN datamodel");
                return;
            }
            builder.setVpnid(vpnId);
            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            //isLockAcquired = NeutronvpnUtils.lock(lockManager, extNetId.getValue());
            logger.trace("Setting VPN-ID for externalnetworks " + networkss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
            logger.trace("Wrote with VPN-ID successfully to CONFIG Datastore");

        } catch (Exception ex) {
            logger.error("Attaching VPN-ID to externalnetwork"+ extNetId.getValue() +" failed with " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    public void removeExternalNetworkFromVpn(Network network) {
        Uuid extNetId = network.getUuid();

        // Create and add Networks object for this External Network to the ExternalNetworks list
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class).
                child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalNets = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    netsIdentifier);
            logger.trace("Removing vpn-id from Networks node: " +  extNetId.getValue());
            NetworksBuilder builder = null;
            if (optionalNets.isPresent()) {
                builder = new NetworksBuilder(optionalNets.get());
            } else {
                logger.error("External Network " + extNetId.getValue() + " not present in the NVPN datamodel");
                return;
            }

            builder.setVpnid(null);
            Networks networkss = builder.build();
            // Add Networks object to the ExternalNetworks list
            //isLockAcquired = NeutronvpnUtils.lock(lockManager, extNetId.getValue());
            logger.trace("Remove vpn-id for externalnetwork " + networkss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, netsIdentifier, networkss);
            logger.trace("Updated extnetworks successfully to CONFIG Datastore");

        } catch (Exception ex) {
            logger.error("Removing VPN-ID from externalnetworks " + extNetId.getValue() + "failed with " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    private void addExternalRouter(Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();
        Uuid extNetId = update.getExternalGatewayInfo().getExternalNetworkId();

        // Create and add Routers object for this Router to the ExtRouters list

        // Create a Routers object
        InstanceIdentifier<Routers> routersIdentifier = InstanceIdentifier.builder(ExtRouters.class).
                child(Routers.class, new RoutersKey(routerId.getValue())).build();

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            logger.trace("Creating/Updating a new Routers node: " + routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                builder = new RoutersBuilder().setKey(new RoutersKey(routerId.getValue()));
            }
            if (builder != null) {
                builder.setRouterName(routerId.getValue());
            }
            if (builder != null) {
                builder.setNetworkId(extNetId);
            }
            if (builder != null) {
                builder.setEnableSnat(update.getExternalGatewayInfo().isEnableSnat());
            }
            if (builder != null) {
                ArrayList<String> ext_fixed_ips = new ArrayList<String>();
                for (ExternalFixedIps fixed_ips : update.getExternalGatewayInfo().getExternalFixedIps()) {
                    ext_fixed_ips.add(fixed_ips.getIpAddress().toString());
                }
                builder.setExternalIps(ext_fixed_ips);
            }
            List<Uuid> subList = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);
            builder.setSubnetIds(subList);
            Routers routerss = builder.build();
            // Add Routers object to the ExtRouters list
            //isLockAcquired = NeutronvpnUtils.lock(lockManager, extNetId.getValue());
            logger.trace("Creating extrouters " + routerss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier, builder.build());
            logger.trace("Wrote successfully Routers to CONFIG Datastore");

        } catch (Exception ex) {
            logger.error("Creation of extrouters failed for router " + routerId.getValue() + " failed with " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    private void removeExternalRouter(Uuid extNetId, Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();

        InstanceIdentifier<Routers> routersIdentifier = InstanceIdentifier.builder(ExtRouters.class).
                child(Routers.class, new RoutersKey(routerId.getValue())).build();

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            logger.trace(" Removing Routers node: " +  routerId.getValue());
            if (optionalRouters.isPresent()) {
                RoutersBuilder builder = new RoutersBuilder(optionalRouters.get());
                builder.setExternalIps(null);
                builder.setSubnetIds(null);
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier);
                logger.trace("Removed router " + routerId.getValue() + " from extrouters ");
            }
        } catch (Exception ex) {
            logger.error("Removing extrouter " + routerId.getValue() + " from extrouters " + "failed with " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    private void handleExternalFixedIpsForRouter(Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();

        InstanceIdentifier<Routers> routersIdentifier = InstanceIdentifier.builder(ExtRouters.class).
                child(Routers.class, new RoutersKey(routerId.getValue())).build();

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            logger.trace("Updating External Fixed IPs Routers node: " +  routerId.getValue());
            if (optionalRouters.isPresent()) {
                RoutersBuilder builder = new RoutersBuilder(optionalRouters.get());
                if (builder != null) {
                    ArrayList<String> ext_fixed_ips = new ArrayList<String>();
                    for (ExternalFixedIps fixed_ips : update.getExternalGatewayInfo().getExternalFixedIps()) {
                        ext_fixed_ips.add(fixed_ips.getIpAddress().toString());
                    }
                    builder.setExternalIps(ext_fixed_ips);
                }
                Routers routerss = builder.build();
                logger.trace("Updating external fixed ips for router " +
                        routerId.getValue() +  " with value " + routerss);
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier, routerss);
                logger.trace("Added External Fixed IPs successfully for Routers to CONFIG Datastore");
            }
        } catch (Exception ex) {
            logger.error("Updating extfixedips for " + routerId.getValue() + " in extrouters failed with " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    public void handleSubnetsForExternalRouter(Uuid routerId, DataBroker broker) {

        InstanceIdentifier<Routers> routersIdentifier = InstanceIdentifier.builder(ExtRouters.class).
                child(Routers.class, new RoutersKey(routerId.getValue())).build();

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            logger.trace("Updating Internal subnets for Routers node: " +
                    routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                logger.info("No Routers element found for router name " + routerId.getValue());
                return;
            }
            List<Uuid> subList = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);
            builder.setSubnetIds(subList);
            Routers routerss = builder.build();
            // Add Routers object to the ExtRouters list
            //isLockAcquired = NeutronvpnUtils.lock(lockManager, extNetId.getValue());
            logger.trace("Updating extrouters " + routerss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier, routerss);
            logger.trace("Updated successfully Routers to CONFIG Datastore");

        } catch (Exception ex) {
            logger.error("Updation of internal subnets for extrouters failed for router " + routerId.getValue() + " with " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }

    private void handleSnatSettingChangeForRouter(Router update, DataBroker broker) {
        Uuid routerId = update.getUuid();

        InstanceIdentifier<Routers> routersIdentifier = InstanceIdentifier.builder(ExtRouters.class).
                child(Routers.class, new RoutersKey(routerId.getValue())).build();

        try {
            Optional<Routers> optionalRouters = NeutronvpnUtils.read(broker,
                    LogicalDatastoreType.CONFIGURATION,
                    routersIdentifier);
            logger.trace("Updating Internal subnets for Routers node: " +
                    routerId.getValue());
            RoutersBuilder builder = null;
            if (optionalRouters.isPresent()) {
                builder = new RoutersBuilder(optionalRouters.get());
            } else {
                logger.trace("No Routers element found for router name " + routerId.getValue());
                return;
            }
            builder.setEnableSnat(update.getExternalGatewayInfo().isEnableSnat());
            Routers routerss = builder.build();
            // Add Routers object to the ExtRouters list
            //isLockAcquired = NeutronvpnUtils.lock(lockManager, extNetId.getValue());
            logger.trace("Updating extrouters for snat change " + routerss);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier, routerss);
            logger.trace("Updated successfully Routers to CONFIG Datastore");

        } catch (Exception ex) {
            logger.error("Updation of snat for extrouters failed for router " + routerId.getValue() + " with " + ex.getMessage());
        } finally {
            //if (isLockAcquired) {
            //    NeutronvpnUtils.unlock(lockManager, extNetId.getValue());
            //}
        }
    }
}

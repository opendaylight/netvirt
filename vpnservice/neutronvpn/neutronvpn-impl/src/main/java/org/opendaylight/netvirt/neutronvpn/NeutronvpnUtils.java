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
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.portsecurity.rev150712.PortSecurityExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.TimeUnits;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.TryLockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.TryLockInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.UnlockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.UnlockInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.port.data.PortFixedipToPortName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.port.data.PortFixedipToPortNameKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NeutronvpnUtils {

    private static final Logger logger = LoggerFactory.getLogger(NeutronvpnUtils.class);
    public static ConcurrentHashMap<Uuid, Network> networkMap = new ConcurrentHashMap<Uuid, Network>();
    public static ConcurrentHashMap<Uuid, Router> routerMap = new ConcurrentHashMap<Uuid, Router>();
    public static ConcurrentHashMap<Uuid, Port> portMap = new ConcurrentHashMap<Uuid, Port>();
    public static ConcurrentHashMap<Uuid, Subnet> subnetMap = new ConcurrentHashMap<Uuid, Subnet>();

    private NeutronvpnUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    protected static Subnetmap getSubnetmap(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier id = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> sn = read(broker, LogicalDatastoreType.CONFIGURATION, id);

        if (sn.isPresent()) {
            return sn.get();
        }
        return null;
    }

    protected static VpnMap getVpnMap(DataBroker broker, Uuid id) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(id)).build();
        Optional<VpnMap> optionalVpnMap = read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            return optionalVpnMap.get();
        }
        logger.error("getVpnMap failed, VPN {} not present", id.getValue());
        return null;
    }

    protected static Uuid getVpnForNetwork(DataBroker broker, Uuid network) {
        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optionalVpnMaps = read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapsIdentifier);
        if (optionalVpnMaps.isPresent() && optionalVpnMaps.get().getVpnMap() != null) {
            List<VpnMap> allMaps = optionalVpnMaps.get().getVpnMap();
            for (VpnMap vpnMap : allMaps) {
                List<Uuid> netIds = vpnMap.getNetworkIds();
                if ((netIds != null) && (netIds.contains(network))) {
                    return vpnMap.getVpnId();
                }
            }
        }
        return null;
    }

    protected static Uuid getVpnForSubnet(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapIdentifier = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> optionalSubnetMap = read(broker, LogicalDatastoreType.CONFIGURATION, subnetmapIdentifier);
        if (optionalSubnetMap.isPresent()) {
            return optionalSubnetMap.get().getVpnId();
        }
        return null;
    }

    // @param external vpn - true if external vpn being fetched, false for internal vpn
    protected static Uuid getVpnForRouter(DataBroker broker, Uuid routerId, Boolean externalVpn) {
        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optionalVpnMaps = read(broker, LogicalDatastoreType.CONFIGURATION,
                vpnMapsIdentifier);
        if (optionalVpnMaps.isPresent() && optionalVpnMaps.get().getVpnMap() != null) {
            List<VpnMap> allMaps = optionalVpnMaps.get().getVpnMap();
            if (routerId != null) {
                for (VpnMap vpnMap : allMaps) {
                    if (routerId.equals(vpnMap.getRouterId())) {
                        if (externalVpn) {
                            if (!routerId.equals(vpnMap.getVpnId())) {
                                return vpnMap.getVpnId();
                            }
                        } else {
                            if (routerId.equals(vpnMap.getVpnId())) {
                                return vpnMap.getVpnId();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    protected static Uuid getRouterforVpn(DataBroker broker, Uuid vpnId) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId)).build();
        Optional<VpnMap> optionalVpnMap = read(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            return vpnMap.getRouterId();
        }
        return null;
    }

    protected static String getNeutronPortNamefromPortFixedIp(DataBroker broker, String fixedIp) {
        InstanceIdentifier id = buildFixedIpToPortNameIdentifier(fixedIp);
        Optional<PortFixedipToPortName> portFixedipToPortNameData = read(broker, LogicalDatastoreType.CONFIGURATION,
                id);
        if (portFixedipToPortNameData.isPresent()) {
            return portFixedipToPortNameData.get().getPortName();
        }
        return null;
    }

    protected static List<Uuid> getSubnetIdsFromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier id = buildNetworkMapIdentifier(networkId);
        Optional<NetworkMap> optionalNetworkMap = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (optionalNetworkMap.isPresent()) {
            return optionalNetworkMap.get().getSubnetIdList();
        }
        return null;
    }

    protected static Router getNeutronRouter(DataBroker broker, Uuid routerId) {
        Router router = null;
        router = routerMap.get(routerId);
        if (router != null) {
            return router;
        }
        InstanceIdentifier<Router> inst = InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router
                .class, new RouterKey(routerId));
        Optional<Router> rtr = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (rtr.isPresent()) {
            router = rtr.get();
        }
        return router;
    }

    protected static Network getNeutronNetwork(DataBroker broker, Uuid networkId) {
        Network network = null;
        network = networkMap.get(networkId);
        if (network != null) {
            return network;
        }
        logger.debug("getNeutronNetwork for {}", networkId.getValue());
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class).child(Networks.class).child
                (Network.class, new NetworkKey(networkId));
        Optional<Network> net = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (net.isPresent()) {
            network = net.get();
        }
        return network;
    }

    protected static List<Uuid> getNeutronRouterSubnetIds(DataBroker broker, Uuid routerId) {
        logger.info("getNeutronRouterSubnetIds for {}", routerId.getValue());

        List<Uuid> subnetIdList = new ArrayList<>();
        Ports ports = getNeutrounPorts(broker);
        if (ports != null && ports.getPort() != null) {
            for (Port port: ports.getPort()) {
                if ((port.getDeviceOwner() != null) && (port.getDeviceId() != null)) {
                    if (port.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF) &&
                            port.getDeviceId().equals(routerId.getValue())) {
                        for (FixedIps portIp: port.getFixedIps()) {
                            subnetIdList.add(portIp.getSubnetId());
                        }
                    }
                }
            }
        }
        /*Router router = getNeutronRouter(broker, routerId);
        if (router != null) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router
                    .Interfaces> interfacesList = router.getInterfaces();
            if (interfacesList != null) {
                for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers
                        .router.Interfaces interfaces : interfacesList) {
                    subnetIdList.add(interfaces.getSubnetId());
                }
            }
        }*/
        logger.info("returning from getNeutronRouterSubnetIds for {}", routerId.getValue());
        return subnetIdList;
    }

    protected static Ports getNeutrounPorts(DataBroker broker) {
        InstanceIdentifier<Ports> inst = InstanceIdentifier.create(Neutron.class).child(Ports.class);
        Optional<Ports> ports = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (ports.isPresent()) {
            return ports.get();
        }
        return null;
    }

    protected static Port getNeutronPort(DataBroker broker, Uuid portId) {
        Port prt = null;
        prt = portMap.get(portId);
        if (prt != null) {
            return prt;
        }
        logger.debug("getNeutronPort for {}", portId.getValue());
        InstanceIdentifier<Port> inst = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class,
                new PortKey(portId));
        Optional<Port> port = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (port.isPresent()) {
            prt = port.get();
        }
        return prt;
    }
    public static boolean isPortSecurityEnabled(Port port) {
        PortSecurityExtension portSecurity = port.getAugmentation(PortSecurityExtension.class);
        return (portSecurity != null && portSecurity.isPortSecurityEnabled() != null);
    }

    public static Boolean getPortSecurityEnabled(Port port) {
        PortSecurityExtension portSecurity = port.getAugmentation(PortSecurityExtension.class);
        if (portSecurity != null) {
            return portSecurity.isPortSecurityEnabled();
        }
        return null;
    }

    protected static Interface getOfPortInterface(DataBroker broker, Port port) {
        String name = port.getUuid().getValue();
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(name);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (optionalInf.isPresent()) {
                return optionalInf.get();
            } else {
                logger.error("Interface {} is not present", name);
            }
        } catch (Exception e) {
            logger.error("Failed to get interface {} due to the exception {}", name, e.getMessage());
        }
        return null;
    }

    protected static Subnet getNeutronSubnet(DataBroker broker, Uuid subnetId) {
        Subnet subnet = null;
        subnet = subnetMap.get(subnetId);
        if (subnet != null) {
            return subnet;
        }
        InstanceIdentifier<Subnet> inst = InstanceIdentifier.create(Neutron.class).
                child(Subnets.class).child(Subnet.class, new SubnetKey(subnetId));
        Optional<Subnet> sn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, inst);

        if (sn.isPresent()) {
            subnet = sn.get();
        }
        return subnet;
    }

    protected static String getVifPortName(Port port) {
        if (port == null || port.getUuid() == null) {
            logger.warn("Invalid Neutron port {}", port);
            return null;
        }
        String tapId = port.getUuid().getValue().substring(0, 11);
        String portNamePrefix = getPortNamePrefix(port);
        if (portNamePrefix != null) {
            return new StringBuilder().append(portNamePrefix).append(tapId).toString();
        }
        logger.debug("Failed to get prefix for port {}", port.getUuid());
        return null;
    }

    protected static String getPortNamePrefix(Port port) {
        PortBindingExtension portBinding = port.getAugmentation(PortBindingExtension.class);
        if (portBinding == null || portBinding.getVifType() == null) {
            return null;
        }
        switch(portBinding.getVifType()) {
            case NeutronConstants.VIF_TYPE_VHOSTUSER:
                return NeutronConstants.PREFIX_VHOSTUSER;
            case NeutronConstants.VIF_TYPE_OVS:
            case NeutronConstants.VIF_TYPE_DISTRIBUTED:
            case NeutronConstants.VIF_TYPE_BRIDGE:
            case NeutronConstants.VIF_TYPE_OTHER:
            case NeutronConstants.VIF_TYPE_MACVTAP:
                return NeutronConstants.PREFIX_TAP;
            case NeutronConstants.VIF_TYPE_UNBOUND:
            case NeutronConstants.VIF_TYPE_BINDING_FAILED:
            default:
                return null;
        }
    }

    protected static boolean isPortVifTypeUpdated(Port original, Port updated) {
        return ((getPortNamePrefix(original) == null) && (getPortNamePrefix(updated) != null));
    }

    protected static boolean lock(LockManagerService lockManager, String lockName) {
        TryLockInput input = new TryLockInputBuilder().setLockName(lockName).setTime(5L).setTimeUnit
                (TimeUnits.Milliseconds).build();
        boolean islockAcquired = false;
        try {
            Future<RpcResult<Void>> result = lockManager.tryLock(input);
            if ((result != null) && (result.get().isSuccessful())) {
                logger.debug("Acquired lock for {}", lockName);
                islockAcquired = true;
            } else {
                logger.error("Unable to acquire lock for  {}", lockName);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Unable to acquire lock for  {}", lockName);
            throw new RuntimeException(String.format("Unable to acquire lock for %s", lockName), e.getCause());
        }
        return islockAcquired;
    }

    protected static boolean unlock(LockManagerService lockManager, String lockName) {
        UnlockInput input = new UnlockInputBuilder().setLockName(lockName).build();
        boolean islockAcquired = false;
        try {
            Future<RpcResult<Void>> result = lockManager.unlock(input);
            if ((result != null) && (result.get().isSuccessful())) {
                logger.debug("Unlocked {}", lockName);
                islockAcquired = true;
            } else {
                logger.error("Unable to unlock {}", lockName);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Unable to unlock {}", lockName);
            throw new RuntimeException(String.format("Unable to unlock %s", lockName), e.getCause());
        }
        return islockAcquired;
    }

    protected static Short getIPPrefixFromPort(DataBroker broker, Port port) {
        Short prefix = new Short((short) 0);
        String cidr = "";
        try {
            Uuid subnetUUID = port.getFixedIps().get(0).getSubnetId();

            SubnetKey subnetkey = new SubnetKey(subnetUUID);
            InstanceIdentifier<Subnet> subnetidentifier = InstanceIdentifier.create(Neutron.class).child(Subnets
                    .class).child(Subnet.class, subnetkey);
            Optional<Subnet> subnet = read(broker, LogicalDatastoreType.CONFIGURATION,subnetidentifier);
            if (subnet.isPresent()) {
                cidr = String.valueOf(subnet.get().getCidr().getValue());
                // Extract the prefix length from cidr
                String[] parts = cidr.split("/");
                if ((parts.length == 2)) {
                    prefix = Short.valueOf(parts[1]);
                    return prefix;
                } else {
                    logger.trace("Could not retrieve prefix from subnet CIDR");
                    System.out.println("Could not retrieve prefix from subnet CIDR");
                }
            } else {
                logger.trace("Unable to read on subnet datastore");
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve IP prefix from port : ", e);
            System.out.println("Failed to retrieve IP prefix from port : " + e.getMessage());
        }
        return null;
    }

    public static void addToNetworkCache(Network network) {
        networkMap.put(network.getUuid(),network);
    }

    public static void removeFromNetworkCache(Network network) {
        networkMap.remove(network.getUuid());
    }

    public static void addToRouterCache(Router router) {
        routerMap.put(router.getUuid(),router);
    }

    public static void removeFromRouterCache(Router router) {
        routerMap.remove(router.getUuid());
    }

    public static void addToPortCache(Port port) {
        portMap.put(port.getUuid(),port);
    }

    public static void removeFromPortCache(Port port) {
        portMap.remove(port.getUuid());
    }

    public static void addToSubnetCache(Subnet subnet) {
        subnetMap.put(subnet.getUuid(),subnet);
    }

    public static void removeFromSubnetCache(Subnet subnet) {
        subnetMap.remove(subnet.getUuid());
    }

    static InstanceIdentifier<PortFixedipToPortName> buildFixedIpToPortNameIdentifier(String fixedIp) {
        InstanceIdentifier<PortFixedipToPortName> id = InstanceIdentifier.builder(NeutronPortData.class).child
                (PortFixedipToPortName.class, new PortFixedipToPortNameKey(fixedIp)).build();
        return id;
    }

    static InstanceIdentifier<NetworkMap> buildNetworkMapIdentifier(Uuid networkId) {
        InstanceIdentifier<NetworkMap> id = InstanceIdentifier.builder(NetworkMaps.class).child(NetworkMap.class, new
                NetworkMapKey(networkId)).build();
        return id;
    }

    static InstanceIdentifier<VpnInterface> buildVpnInterfaceIdentifier(String ifName) {
        InstanceIdentifier<VpnInterface> id = InstanceIdentifier.builder(VpnInterfaces.class).
                child(VpnInterface.class, new VpnInterfaceKey(ifName)).build();
        return id;
    }

    static InstanceIdentifier<Subnetmap> buildSubnetMapIdentifier(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class, new
                SubnetmapKey(subnetId)).build();
        return id;
    }

    static InstanceIdentifier<Interface> buildVlanInterfaceIdentifier(String interfaceName) {
        InstanceIdentifier<Interface> id = InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new
                InterfaceKey(interfaceName)).build();
        return id;
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext
            .routers.Routers> buildExtRoutersIdentifier(Uuid routerId) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers
                .Routers> id = InstanceIdentifier.builder(ExtRouters.class).child(org.opendaylight.yang.gen.v1.urn
                .opendaylight.netvirt.natservice.rev160111.ext.routers.Routers.class,
                new RoutersKey(routerId.getValue())).build();
        return id;
    }

    static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                   InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    static boolean isNetworkTypeVlanOrGre(Network network) {
        NetworkProviderExtension npe = network.getAugmentation(NetworkProviderExtension.class);
        if (npe != null && npe.getNetworkType() != null) {
            if (npe.getNetworkType().isAssignableFrom(NetworkTypeVlan.class) ||
                    npe.getNetworkType().isAssignableFrom(NetworkTypeGre.class)) {
                logger.trace("Network is of type {}", npe.getNetworkType());
                return true;
            }
        }
        return false;
    }

    protected static Integer getUniqueRDId(IdManagerService idManager, String poolName, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                logger.debug("RPC Call to Get Unique Id returned with Errors", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.debug("Exception when getting Unique Id", e);
        }
        return null;
    }

    protected static void releaseRDId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                logger.debug("RPC Call to Get Unique Id returned with Errors", rpcResult.getErrors());
            } else {
                logger.info("ID for RD " + idKey + " released successfully");
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.debug("Exception when trying to release ID into the pool", idKey, e);
        }
    }

}

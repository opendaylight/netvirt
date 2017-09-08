/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import static org.opendaylight.netvirt.neutronvpn.NeutronvpnUtils.buildfloatingIpIdToPortMappingIdentifier;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronPortChangeListener extends AsyncDataTreeChangeListenerBase<Port, NeutronPortChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;
    private final NeutronSubnetGwMacResolver gwMacResolver;
    private final IElanService elanService;

    @Inject
    public NeutronPortChangeListener(final DataBroker dataBroker,
                                     final NeutronvpnManager neutronvpnManager,
                                     final NeutronvpnNatManager neutronvpnNatManager,
                                     final NeutronSubnetGwMacResolver gwMacResolver,
                                     final IElanService elanService) {
        super(Port.class, NeutronPortChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        nvpnNatManager = neutronvpnNatManager;
        this.gwMacResolver = gwMacResolver;
        this.elanService = elanService;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    protected NeutronPortChangeListener getDataTreeChangeListener() {
        return NeutronPortChangeListener.this;
    }


    @Override
    protected void add(InstanceIdentifier<Port> identifier, Port input) {
        String portName = input.getUuid().getValue();
        LOG.trace("Adding Port : key: {}, value={}", identifier, input);
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, input.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.warn("neutron vpn received a port add() for a network without a provider extension augmentation "
                            + "or with an unsupported network type for the port {} which is part of network {}",
                    portName, network);
            return;
        }
        NeutronvpnUtils.addToPortCache(input);
        String portStatus = NeutronUtils.PORT_STATUS_DOWN;
        if (!Strings.isNullOrEmpty(input.getDeviceOwner()) && !Strings.isNullOrEmpty(input.getDeviceId())) {
            if (input.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceAdded(input);
                NeutronUtils.createPortStatus(input.getUuid().getValue(), NeutronUtils.PORT_STATUS_ACTIVE, dataBroker);
                return;
            }
            if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(input.getDeviceOwner())) {
                handleRouterGatewayUpdated(input);
                portStatus = NeutronUtils.PORT_STATUS_ACTIVE;
            } else if (NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(input.getDeviceOwner())) {
                handleFloatingIpPortUpdated(null, input);
                portStatus = NeutronUtils.PORT_STATUS_ACTIVE;
            }
        }
        if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
            handleNeutronPortCreated(input);
        }
        NeutronUtils.createPortStatus(input.getUuid().getValue(), portStatus, dataBroker);
    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port input) {
        LOG.trace("Removing Port : key: {}, value={}", identifier, input);
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, input.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            String portName = input.getUuid().getValue();
            LOG.warn("neutron vpn received a port remove() for a network without a provider extension augmentation "
                            + "or with an unsupported network type for the port {} which is part of network {}",
                    portName, network);
            return;
        }
        NeutronvpnUtils.removeFromPortCache(input);
        NeutronUtils.deletePortStatus(input.getUuid().getValue(), dataBroker);

        if (!Strings.isNullOrEmpty(input.getDeviceOwner()) && !Strings.isNullOrEmpty(input.getDeviceId())) {
            if (input.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceRemoved(input);
                /* nothing else to do here */
                return;
            } else if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(input.getDeviceOwner())
                    || NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(input.getDeviceOwner())) {
                elanService.handleKnownL3DmacAddress(input.getMacAddress().getValue(), input.getNetworkId().getValue(),
                        NwConstants.DEL_FLOW);
            }
        }
        if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
            handleNeutronPortDeleted(input);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        final String portName = update.getUuid().getValue();
        LOG.info("Update port {} from network {}", portName, update.getNetworkId().toString());
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, update.getNetworkId());
        LOG.info("Update port {} from network {}", portName, update.getNetworkId().toString());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.error("neutron vpn received a port update() for a network without a provider extension augmentation "
                    + "or with an unsupported network type for the port {} which is part of network {}",
                    portName, network);
            return;
        }
        NeutronvpnUtils.addToPortCache(update);

        if ((Strings.isNullOrEmpty(original.getDeviceOwner()) || Strings.isNullOrEmpty(original.getDeviceId())
                || NeutronConstants.FLOATING_IP_DEVICE_ID_PENDING.equalsIgnoreCase(original.getDeviceId()))
                && !Strings.isNullOrEmpty(update.getDeviceOwner()) && !Strings.isNullOrEmpty(update.getDeviceId())) {
            if (update.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceAdded(update);
                return;
            }
            if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(update.getDeviceOwner())) {
                handleRouterGatewayUpdated(update);
            } else if (NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(update.getDeviceOwner())) {
                handleFloatingIpPortUpdated(original, update);
            }
        } else {
            Set<FixedIps> oldIPs = getFixedIpSet(original.getFixedIps());
            Set<FixedIps> newIPs = getFixedIpSet(update.getFixedIps());
            if (!oldIPs.equals(newIPs)) {
                handleNeutronPortUpdated(original, update);
            }
        }

        // check if port security enabled/disabled as part of port update
        boolean origSecurityEnabled = NeutronvpnUtils.getPortSecurityEnabled(original);
        boolean updatedSecurityEnabled = NeutronvpnUtils.getPortSecurityEnabled(update);

        if (origSecurityEnabled || updatedSecurityEnabled) {
            InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(portName);
            final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            portDataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
                WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                try {
                    Optional<Interface> optionalInf =
                            SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                    interfaceIdentifier);
                    if (optionalInf.isPresent()) {
                        InterfaceBuilder interfaceBuilder = new InterfaceBuilder(optionalInf.get());
                        InterfaceAcl infAcl = handlePortSecurityUpdated(dataBroker, original, update,
                                origSecurityEnabled, updatedSecurityEnabled, interfaceBuilder).build();
                        interfaceBuilder.addAugmentation(InterfaceAcl.class, infAcl);
                        LOG.info("update: Of-port-interface updation for port {}", portName);
                        // Update OFPort interface for this neutron port
                        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier,
                                interfaceBuilder.build());
                    } else {
                        LOG.warn("update: Interface {} is not present", portName);
                    }
                } catch (ReadFailedException e) {
                    LOG.error("update: Failed to update interface {}", portName, e);
                }
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(wrtConfigTxn.submit());
                return futures;
            });
        }
    }

    private void handleFloatingIpPortUpdated(Port original, Port update) {
        if (((original == null) || (original.getDeviceId().equals(NeutronConstants.FLOATING_IP_DEVICE_ID_PENDING)))
            && !update.getDeviceId().equals(NeutronConstants.FLOATING_IP_DEVICE_ID_PENDING)) {
            // populate floating-ip uuid and floating-ip port attributes (uuid, mac and subnet id for the ONLY
            // fixed IP) to be used by NAT, depopulated in NATService once mac is retrieved in the removal path
            addToFloatingIpPortInfo(new Uuid(update.getDeviceId()), update.getUuid(), update.getFixedIps().get(0)
                    .getSubnetId(), update.getMacAddress().getValue());
            elanService.handleKnownL3DmacAddress(update.getMacAddress().getValue(), update.getNetworkId().getValue(),
                    NwConstants.ADD_FLOW);
        }
    }

    private void handleRouterInterfaceAdded(Port routerPort) {
        if (routerPort.getDeviceId() != null) {
            Uuid routerId = new Uuid(routerPort.getDeviceId());
            Uuid infNetworkId = routerPort.getNetworkId();
            Uuid existingVpnId = NeutronvpnUtils.getVpnForNetwork(dataBroker, infNetworkId);

            elanService.handleKnownL3DmacAddress(routerPort.getMacAddress().getValue(), infNetworkId.getValue(),
                    NwConstants.ADD_FLOW);
            if (existingVpnId == null) {
                Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
                if (vpnId == null) {
                    vpnId = routerId;
                }
                List<Subnetmap> subnetMapList = new ArrayList<>();
                List<FixedIps> portIps = routerPort.getFixedIps();
                for (FixedIps portIP : portIps) {
                    // NOTE:  Please donot change the order of calls to updateSubnetNodeWithFixedIP
                    // and addSubnetToVpn here
                    String ipValue = String.valueOf(portIP.getIpAddress().getValue());
                    Uuid subnetId = portIP.getSubnetId();
                    nvpnManager.updateSubnetNodeWithFixedIp(subnetId, routerId,
                            routerPort.getUuid(), ipValue, routerPort.getMacAddress().getValue());
                    Subnetmap sn = NeutronvpnUtils.getSubnetmap(dataBroker, subnetId);
                    subnetMapList.add(sn);
                }
                if (! subnetMapList.isEmpty()) {
                    nvpnManager.createVpnInterface(Collections.singletonList(vpnId), routerPort, null);
                }
                for (FixedIps portIP : routerPort.getFixedIps()) {
                    String ipValue = String.valueOf(portIP.getIpAddress().getValue());
                    if (NeutronvpnUtils.shouldVpnHandleIpVersionChangeToRemove(dataBroker,
                                NeutronvpnUtils.getSubnetmap(dataBroker, portIP.getSubnetId()), vpnId)) {
                        NeutronvpnUtils.updateVpnInstanceWithIpFamily(dataBroker, vpnId.getValue(),
                               NeutronvpnUtils.getIpVersionFromString(ipValue), true);
                    }
                    nvpnManager.addSubnetToVpn(vpnId, portIP.getSubnetId());
                    LOG.trace("NeutronPortChangeListener Add Subnet Gateway IP {} MAC {} Interface {} VPN {}",
                            ipValue, routerPort.getMacAddress(),
                            routerPort.getUuid().getValue(), vpnId.getValue());
                }
                nvpnManager.addToNeutronRouterInterfacesMap(routerId, routerPort.getUuid().getValue());
                nvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);
                WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                String portInterfaceName = createOfPortInterface(routerPort, wrtConfigTxn);
                createElanInterface(routerPort, portInterfaceName, wrtConfigTxn);
                wrtConfigTxn.submit();
                PhysAddress mac = new PhysAddress(routerPort.getMacAddress().getValue());
            } else {
                LOG.error("Neutron subnetwork {} corresponding to router interface port {} for neutron router {}"
                    + " already associated to VPN {}", infNetworkId.getValue(), routerPort.getUuid().getValue(),
                    routerId.getValue(), existingVpnId.getValue());
            }
        }
    }

    private void handleRouterInterfaceRemoved(Port routerPort) {
        if (routerPort.getDeviceId() != null) {
            Uuid routerId = new Uuid(routerPort.getDeviceId());
            Uuid infNetworkId = routerPort.getNetworkId();
            elanService.handleKnownL3DmacAddress(routerPort.getMacAddress().getValue(), infNetworkId.getValue(),
                    NwConstants.DEL_FLOW);
            Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
            if (vpnId == null) {
                vpnId = routerId;
            }
            /* Remove ping responder for router interfaces
             *  A router interface reference in a VPN will have to be removed before the host interface references
             * for that subnet in the VPN are removed. This is to ensure that the FIB Entry of the router interface
             *  is not the last entry to be removed for that subnet in the VPN.
             *  If router interface FIB entry is the last to be removed for a subnet in a VPN , then all the host
             *  interface references in the vpn will already have been cleared, which will cause failures in
             *  cleanup of router interface flows*/
            nvpnManager.deleteVpnInterface(routerPort.getUuid().getValue(), null);
            // update RouterInterfaces map
            List<FixedIps> portIps = routerPort.getFixedIps();
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            boolean vpnInstanceIpVersionRemoved = false;
            IpVersionChoice vpnInstanceIpVersionToRemove = IpVersionChoice.UNDEFINED;
            for (FixedIps portIP : portIps) {
                Subnetmap sn = NeutronvpnUtils.getSubnetmap(dataBroker, portIP.getSubnetId());
                // router Port have either IPv4 or IPv6, never both
                if (NeutronvpnUtils.shouldVpnHandleIpVersionChangeToRemove(dataBroker, sn, vpnId)) {
                    vpnInstanceIpVersionRemoved = true;
                    vpnInstanceIpVersionToRemove = NeutronvpnUtils.getIpVersionFromString(sn.getSubnetIp());
                }
                String ipValue = String.valueOf(portIP.getIpAddress().getValue());
                NeutronvpnUtils.removeVpnPortFixedIpToPort(dataBroker, vpnId.getValue(), ipValue,
                    wrtConfigTxn);
                // NOTE:  Please donot change the order of calls to removeSubnetFromVpn and
                // and updateSubnetNodeWithFixedIP
                nvpnManager.removeSubnetFromVpn(vpnId, portIP.getSubnetId());
                nvpnManager.updateSubnetNodeWithFixedIp(portIP.getSubnetId(), null, null, null, null);
            }
            nvpnManager.removeFromNeutronRouterInterfacesMap(routerId, routerPort.getUuid().getValue());
            deleteElanInterface(routerPort.getUuid().getValue(), wrtConfigTxn);
            deleteOfPortInterface(routerPort, wrtConfigTxn);
            wrtConfigTxn.submit();
            nvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);
            for (FixedIps portIP : portIps) {
                String ipValue = String.valueOf(portIP.getIpAddress().getValue());
                NeutronvpnUtils.removeVpnPortFixedIpToPort(dataBroker, vpnId.getValue(), ipValue,
                    null /*writeTransaction*/);
            }
            if (vpnInstanceIpVersionRemoved) {
                NeutronvpnUtils.updateVpnInstanceWithIpFamily(dataBroker, vpnId.getValue(),
                          vpnInstanceIpVersionToRemove, false);
            }
        }
    }

    private void handleRouterGatewayUpdated(Port routerGwPort) {
        Uuid routerId = new Uuid(routerGwPort.getDeviceId());
        Uuid networkId = routerGwPort.getNetworkId();
        elanService.handleKnownL3DmacAddress(routerGwPort.getMacAddress().getValue(), networkId.getValue(),
                NwConstants.ADD_FLOW);

        Router router = NeutronvpnUtils.getNeutronRouter(dataBroker, routerId);
        if (router == null) {
            LOG.warn("No router found for router GW port {} for router {}", routerGwPort.getUuid().getValue(),
                    routerId.getValue());
            return;
        }
        gwMacResolver.sendArpRequestsToExtGateways(router);
    }

    private void handleNeutronPortCreated(final Port port) {
        final String portName = port.getUuid().getValue();
        final Uuid portId = port.getUuid();
        final List<FixedIps> portIpAddrsList = port.getFixedIps();
        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        if (NeutronConstants.IS_ODL_DHCP_PORT.test(port)) {
            return;
        }
        portDataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            // add direct port to subnetMaps config DS
            if (!NeutronUtils.isPortVnicTypeNormal(port)) {
                for (FixedIps ip: portIpAddrsList) {
                    nvpnManager.updateSubnetmapNodeWithPorts(ip.getSubnetId(), null, portId);
                }
                LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created", portName);
                return futures;
            }
            LOG.info("Of-port-interface creation for port {}", portName);
            // Create of-port interface for this neutron port
            String portInterfaceName = createOfPortInterface(port, wrtConfigTxn);
            LOG.debug("Creating ELAN Interface for port {}", portName);
            createElanInterface(port, portInterfaceName, wrtConfigTxn);
            List<Uuid> vpnIdList =  new ArrayList<>();
            for (FixedIps ip: portIpAddrsList) {
                Subnetmap subnetMap = nvpnManager.updateSubnetmapNodeWithPorts(ip.getSubnetId(), portId, null);
                if (subnetMap != null && subnetMap.getVpnId() != null) {
                    // can't use NeutronvpnUtils.getVpnForNetwork to optimise here, because it gives BGPVPN id
                    // obtained subnetMaps belongs to one network => vpnId must be the same for each port Ip
                    Uuid vpnId = subnetMap.getVpnId();
                    if (vpnId != null && !vpnIdList.contains(vpnId)) {
                        vpnIdList.add(vpnId);
                    }
                }
            }
            if (vpnIdList != null && !vpnIdList.isEmpty()) {
                // create new vpn-interface for neutron port
                LOG.debug("handleNeutronPortCreated: Adding VPN Interface for port {} from network {}", portName,
                           port.getNetworkId().toString());
                nvpnManager.createVpnInterface(vpnIdList, port, wrtConfigTxn);
            }
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    private void handleNeutronPortDeleted(final Port port) {
        final String portName = port.getUuid().getValue();
        final Uuid portId = port.getUuid();
        final List<FixedIps> portIpsList = port.getFixedIps();
        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            if (!NeutronUtils.isPortVnicTypeNormal(port)) {
                for (FixedIps ip: portIpsList) {
                    // remove direct port from subnetMaps config DS
                    nvpnManager.removePortsFromSubnetmapNode(ip.getSubnetId(), null, portId);
                }
                LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created", portName);
                return futures;
            }
            Uuid vpnId = null;
            for (FixedIps ip: portIpsList) {
                Subnetmap subnetMap = nvpnManager.removePortsFromSubnetmapNode(ip.getSubnetId(), portId, null);
                if (subnetMap != null && subnetMap.getVpnId() != null) {
                    // can't use NeutronvpnUtils.getVpnForNetwork to optimise here, because it gives BGPVPN id
                    // obtained subnetMaps belongs to one network => vpnId must be the same for each port Ip
                    vpnId = subnetMap.getVpnId();
                }
            }
            if (vpnId != null) {
                // remove vpn-interface for this neutron port
                LOG.debug("removing VPN Interface for port {}", portName);
                nvpnManager.deleteVpnInterface(portName, wrtConfigTxn);
            }
            // Remove of-port interface for this neutron port
            // ELAN interface is also implicitly deleted as part of this operation
            LOG.debug("Of-port-interface removal for port {}", portName);
            deleteOfPortInterface(port, wrtConfigTxn);
            //dissociate fixedIP from floatingIP if associated
            nvpnManager.dissociatefixedIPFromFloatingIP(port.getUuid().getValue());
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }


    private void handleNeutronPortUpdated(final Port portoriginal, final Port portupdate) {
        final List<FixedIps> portoriginalIps = portoriginal.getFixedIps();
        final List<FixedIps> portupdateIps = portupdate.getFixedIps();
        LOG.trace("PORT ORIGINAL: {} from network {} with fixed IPs: {}; "
                    + "PORT UPDATE: {} from network {} with fixed IPs: {}",
                    portoriginal.getName(), portoriginal.getNetworkId().toString(), portoriginalIps.toString(),
                    portupdate.getName(), portupdate.getNetworkId().toString(), portupdateIps.toString());
        if (portoriginalIps == null || portoriginalIps.isEmpty()) {
            handleNeutronPortCreated(portupdate);
            return;
        }

        if (portupdateIps == null || portupdateIps.isEmpty()) {
            LOG.info("Ignoring portUpdate (fixed_ip removal) for port {} as this case is handled "
                      + "during subnet deletion event.", portupdate.getUuid().getValue());
            return;
        }
        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("PORT- " + portupdate.getUuid().getValue(), () -> {
            final List<Uuid> originalSnMapsIds = portoriginalIps.stream().map(ip -> ip.getSubnetId())
                    .collect(Collectors.toList());
            final List<Uuid> updateSnMapsIds = portupdateIps.stream().map(ip -> ip.getSubnetId())
                    .collect(Collectors.toList());
            List<Uuid> oldVpnIds = new ArrayList();
            for (Uuid snId: originalSnMapsIds) {
                if (!updateSnMapsIds.remove(snId)) {
                    // snId was present in originalSnMapsIds, but not in updateSnMapsIds
                    Subnetmap subnetMapOld = nvpnManager.removePortsFromSubnetmapNode(snId, portoriginal.getUuid(),
                                                null);
                    if ((subnetMapOld != null) &&  subnetMapOld.getVpnId() != null
                        && !oldVpnIds.contains(subnetMapOld.getVpnId())) {
                        oldVpnIds.add(subnetMapOld.getVpnId());
                    }
                }
            }
            List<Uuid> newVpnId = new ArrayList();
            for (Uuid snId: updateSnMapsIds) {
                Subnetmap subnetMapNew = nvpnManager.updateSubnetmapNodeWithPorts(snId, portupdate.getUuid(), null);
                if (subnetMapNew != null) {
                    if (subnetMapNew.getVpnId() != null && !newVpnId.contains(subnetMapNew.getVpnId())) {
                        newVpnId.add(subnetMapNew.getVpnId());
                    }
                }
            }
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            if (oldVpnIds != null && oldVpnIds.size() > 0) {
                LOG.info("removing VPN Interface for port {}", portoriginal.getUuid().getValue());
                nvpnManager.deleteVpnInterface(portoriginal.getUuid().getValue(), wrtConfigTxn);
            }
            if (newVpnId != null && newVpnId.size() > 0) {
                LOG.info("Adding VPN Interface for port {}", portupdate.getUuid().getValue());
                nvpnManager.createVpnInterface(newVpnId, portupdate, wrtConfigTxn);
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    private static InterfaceAclBuilder handlePortSecurityUpdated(DataBroker dataBroker, Port portOriginal,
            Port portUpdated, boolean origSecurityEnabled, boolean updatedSecurityEnabled,
            InterfaceBuilder interfaceBuilder) {
        String interfaceName = portUpdated.getUuid().getValue();
        InterfaceAclBuilder interfaceAclBuilder = null;
        if (origSecurityEnabled != updatedSecurityEnabled) {
            interfaceAclBuilder = new InterfaceAclBuilder();
            interfaceAclBuilder.setPortSecurityEnabled(updatedSecurityEnabled);
            if (updatedSecurityEnabled) {
                // Handle security group enabled
                NeutronvpnUtils.populateInterfaceAclBuilder(interfaceAclBuilder, portUpdated);
            } else {
                // Handle security group disabled
                interfaceAclBuilder.setSecurityGroups(new ArrayList<>());
                interfaceAclBuilder.setAllowedAddressPairs(new ArrayList<>());
            }
        } else {
            if (updatedSecurityEnabled) {
                // handle SG add/delete delta
                InterfaceAcl interfaceAcl = interfaceBuilder.getAugmentation(InterfaceAcl.class);
                interfaceAclBuilder = new InterfaceAclBuilder(interfaceAcl);
                interfaceAclBuilder.setSecurityGroups(
                        NeutronvpnUtils.getUpdatedSecurityGroups(interfaceAcl.getSecurityGroups(),
                                portOriginal.getSecurityGroups(), portUpdated.getSecurityGroups()));
                List<AllowedAddressPairs> updatedAddressPairs = NeutronvpnUtils.getUpdatedAllowedAddressPairs(
                        interfaceAcl.getAllowedAddressPairs(), portOriginal.getAllowedAddressPairs(),
                        portUpdated.getAllowedAddressPairs());
                interfaceAclBuilder.setAllowedAddressPairs(NeutronvpnUtils.getAllowedAddressPairsForFixedIps(
                        updatedAddressPairs, portOriginal.getMacAddress(), portOriginal.getFixedIps(),
                        portUpdated.getFixedIps()));
            }
        }
        return interfaceAclBuilder;
    }

    private String createOfPortInterface(Port port, WriteTransaction wrtConfigTxn) {
        Interface inf = createInterface(port);
        String infName = inf.getName();

        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
        try {
            Optional<Interface> optionalInf =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            interfaceIdentifier);
            if (!optionalInf.isPresent()) {
                wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
            } else {
                LOG.warn("Interface {} is already present", infName);
            }
        } catch (ReadFailedException e) {
            LOG.error("failed to create interface {}", infName, e);
        }
        return infName;
    }

    private Interface createInterface(Port port) {
        String interfaceName = port.getUuid().getValue();
        IfL2vlan.L2vlanMode l2VlanMode = IfL2vlan.L2vlanMode.Trunk;
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder();
        IfL2vlanBuilder ifL2vlanBuilder = new IfL2vlanBuilder();
        ifL2vlanBuilder.setL2vlanMode(l2VlanMode);

        interfaceBuilder.setEnabled(true).setName(interfaceName).setType(L2vlan.class)
                .addAugmentation(IfL2vlan.class, ifL2vlanBuilder.build());

        if (NeutronvpnUtils.getPortSecurityEnabled(port)) {
            InterfaceAclBuilder interfaceAclBuilder = new InterfaceAclBuilder();
            interfaceAclBuilder.setPortSecurityEnabled(true);
            NeutronvpnUtils.populateInterfaceAclBuilder(interfaceAclBuilder, port);
            interfaceBuilder.addAugmentation(InterfaceAcl.class, interfaceAclBuilder.build());
            NeutronvpnUtils.populateSubnetIpPrefixes(dataBroker, port);
        }
        return interfaceBuilder.build();
    }

    private void deleteOfPortInterface(Port port, WriteTransaction wrtConfigTxn) {
        String name = port.getUuid().getValue();
        LOG.debug("Removing OFPort Interface {}", name);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(name);
        try {
            Optional<Interface> optionalInf =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            interfaceIdentifier);
            if (optionalInf.isPresent()) {
                wrtConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier);
            } else {
                LOG.warn("deleteOfPortInterface: Interface {} is not present", name);
            }
        } catch (ReadFailedException e) {
            LOG.error("deleteOfPortInterface: Failed to delete interface {}", name, e);
        }
    }

    private void createElanInterface(Port port, String name, WriteTransaction wrtConfigTxn) {
        String elanInstanceName = port.getNetworkId().getValue();
        List<StaticMacEntries> staticMacEntries = NeutronvpnUtils.buildStaticMacEntry(port);

        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(name).setStaticMacEntries(staticMacEntries).setKey(new ElanInterfaceKey(name)).build();
        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, id, elanInterface);
        LOG.debug("Creating new ELan Interface {}", elanInterface);
    }

    private void deleteElanInterface(String name, WriteTransaction wrtConfigTxn) {
        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        wrtConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, id);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addToFloatingIpPortInfo(Uuid floatingIpId, Uuid floatingIpPortId, Uuid floatingIpPortSubnetId, String
                                         floatingIpPortMacAddress) {
        InstanceIdentifier id = buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        try {
            FloatingIpIdToPortMappingBuilder floatingipIdToPortMacMappingBuilder = new
                FloatingIpIdToPortMappingBuilder().setKey(new FloatingIpIdToPortMappingKey(floatingIpId))
                .setFloatingIpId(floatingIpId).setFloatingIpPortId(floatingIpPortId)
                .setFloatingIpPortSubnetId(floatingIpPortSubnetId)
                .setFloatingIpPortMacAddress(floatingIpPortMacAddress);
            LOG.debug("Creating floating IP UUID {} to Floating IP neutron port {} mapping in Floating IP"
                + " Port Info Config DS", floatingIpId.getValue(), floatingIpPortId.getValue());
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id,
                floatingipIdToPortMacMappingBuilder.build());
        } catch (Exception e) {
            LOG.error("Creating floating IP UUID {} to Floating IP neutron port {} mapping in Floating IP"
                + " Port Info Config DS failed", floatingIpId.getValue(), floatingIpPortId.getValue(), e);
        }
    }

    private Set<FixedIps> getFixedIpSet(List<FixedIps> fixedIps) {
        return fixedIps != null ? new HashSet<>(fixedIps) : Collections.emptySet();
    }
}

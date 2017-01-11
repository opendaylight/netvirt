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
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.AddInterfaceToBridgeDomainInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.CloneVirtualBridgeDomainOnNodesInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.CreateInterfaceOnNodeInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.CreateVirtualBridgeDomainOnNodesInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.DelInterfaceFromBridgeDomainInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.DeleteInterfaceFromNodeInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.DeleteVirtualBridgeDomainFromNodesInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.VppAdapterService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.VxlanVniType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_adapter.rev161201.bridge.domain.attributes.tunnel.type.VxlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_renderer.rev160425._interface.attributes._interface.type.choice.TapCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_renderer.rev160425._interface.attributes._interface.type.choice.VhostUserCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.vpp_renderer.rev160425.bridge.domain.base.attributes.PhysicalLocationRefBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
//import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronPortChangeListener extends AsyncDataTreeChangeListenerBase<Port, NeutronPortChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;
    private final NotificationPublishService notificationPublishService;
    private final NeutronSubnetGwMacResolver gwMacResolver;
    private OdlInterfaceRpcService odlInterfaceRpcService;
    private final IElanService elanService;

    // VPP port creation logic ----- Start
    private final VppAdapterService vppAdapterRpcService;
    private Map<String, ConcurrentHashMap<String, HashSet<String>>>
                          vbdToVppNodesToInterfaces = new ConcurrentHashMap<>();
    //Constants for VPP interfaces
    private static final String COMPUTE_OWNER = "compute";
    private static final String DHCP_OWNER = "dhcp";
    private static final String ROUTER_OWNER = "network:router_interface";
    private static final String[] SUPPORTED_DEVICE_OWNERS = {COMPUTE_OWNER, DHCP_OWNER, ROUTER_OWNER};
    private static final String VHOST_USER = "vhostuser";
    private static final String VPP_INTERFACE_NAME_PREFIX = "neutron_port_";
    private static final String TAP_PORT_NAME_PREFIX = "tap";
    private static final String RT_PORT_NAME_PREFIX = "qr-";
    // VPP port creation logic ----- End

    public NeutronPortChangeListener(final DataBroker dataBroker,
                                     final NeutronvpnManager neutronvpnManager,
                                     final NeutronvpnNatManager neutronvpnNatManager,
                                     final NotificationPublishService notiPublishService,
                                     final NeutronSubnetGwMacResolver gwMacResolver,
                                     final OdlInterfaceRpcService odlInterfaceRpcService,
                                     final IElanService elanService,
                                     final VppAdapterService vppAdapterRpcService) {
        super(Port.class, NeutronPortChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        nvpnNatManager = neutronvpnNatManager;
        notificationPublishService = notiPublishService;
        this.gwMacResolver = gwMacResolver;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.elanService = elanService;
        // VPP port creation logic ----- Start
        this.vppAdapterRpcService = vppAdapterRpcService;
        // VPP port creation logic ----- End
    }


    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
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
            //FIXME: This should be removed when support for VLAN and GRE network types is added
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} which is part of "
                + "network {}.", portName, network);
            return;
        }
        NeutronvpnUtils.addToPortCache(input);

        /* check if router interface has been created */
        if ((input.getDeviceOwner() != null) && (input.getDeviceId() != null)) {
            if (input.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceAdded(input);
                /* nothing else to do here */
                return;
            }
            if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(input.getDeviceOwner())) {
                handleRouterGatewayUpdated(input);
            } else if (NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(input.getDeviceOwner())) {

                // populate floating-ip uuid and floating-ip port attributes (uuid, mac and subnet id for the ONLY
                // fixed IP) to be used by NAT, depopulated in NATService once mac is retrieved in the removal path
                addToFloatingIpPortInfo(new Uuid(input.getDeviceId()), input.getUuid(), input.getFixedIps().get(0)
                                .getSubnetId(), input.getMacAddress().getValue());

                elanService.handleKnownL3DmacAddress(input.getMacAddress().getValue(), input.getNetworkId().getValue(),
                        NwConstants.ADD_FLOW);
            }
        }
        if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
            handleNeutronPortCreated(input);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port input) {
        LOG.trace("Removing Port : key: {}, value={}", identifier, input);
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, input.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            //FIXME: This should be removed when support for VLAN and GRE network types is added
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} which is part of "
                + "network {}.", input.getUuid().getValue(), network);
            return;
        }
        NeutronvpnUtils.removeFromPortCache(input);

        if ((input.getDeviceOwner() != null) && (input.getDeviceId() != null)) {
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
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        final String portName = update.getUuid().getValue();
        LOG.trace("Updating Port : key: {}, original value={}, update value={}", identifier, original, update);
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, update.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} which is part of "
                + "network {}. Skipping the processing of Port update DCN", portName, network);
            return;
        }
        NeutronvpnUtils.addToPortCache(update);

        /* check if router interface has been updated */
        if ((update.getDeviceOwner() != null) && (update.getDeviceId() != null)) {
            if (update.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceAdded(update);
                /* nothing else to do here */
                return;
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
                    Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType
                            .CONFIGURATION, interfaceIdentifier);
                    if (optionalInf.isPresent()) {
                        InterfaceBuilder interfaceBuilder = new InterfaceBuilder(optionalInf.get());
                        if (origSecurityEnabled || updatedSecurityEnabled) {
                            InterfaceAcl infAcl = handlePortSecurityUpdated(original, update,
                                    origSecurityEnabled, updatedSecurityEnabled, interfaceBuilder).build();
                            interfaceBuilder.addAugmentation(InterfaceAcl.class, infAcl);
                        }
                        LOG.info("Of-port-interface updation for port {}", portName);
                        // Update OFPort interface for this neutron port
                        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier,
                                interfaceBuilder.build());
                    } else {
                        LOG.error("Interface {} is not present", portName);
                    }
                } catch (Exception e) {
                    LOG.error("Failed to update interface {} due to the exception {}", portName, e);
                }
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(wrtConfigTxn.submit());
                return futures;
            });
        }
        List<FixedIps> oldIPs = (original.getFixedIps() != null) ? original.getFixedIps() : new ArrayList<>();
        List<FixedIps> newIPs = (update.getFixedIps() != null) ? update.getFixedIps() : new ArrayList<>();
        if (!oldIPs.equals(newIPs)) {
            newIPs.removeIf(oldIPs::remove);
            handleNeutronPortUpdated(original, update);
        }

        // VPP port update logic ----- Start
        if (isValidVPPVhostUser(update)) {
            LOG.debug("handleNeutronPortUpdate for a VPP port type: {}", update);
            String oldPortBindingHost = original.getAugmentation(PortBindingExtension.class).getHostId();
            String newPortBindingHost = update.getAugmentation(PortBindingExtension.class).getHostId();
            if ((oldPortBindingHost == null) || (oldPortBindingHost.isEmpty())) {
                if ((newPortBindingHost != null) && (!newPortBindingHost.isEmpty())) {
                    LOG.debug("handleNeutronPortUpdate: portBindingHostId has been updated for VPP port: {}", update);
                    handleNeutronPortCreated(update);
                }
            } else {
                //Check if VPP interface is created for this port already, if not, invoke handleNeutronPortCreated
                boolean newPort = true;
                synchronized (vbdToVppNodesToInterfaces) {
                    if (vbdToVppNodesToInterfaces.get(update.getNetworkId().getValue()) != null) {
                        HashSet<String> vppInterfacesInHost = vbdToVppNodesToInterfaces.get(
                                                      update.getNetworkId().getValue()).get(newPortBindingHost);
                        if ((vppInterfacesInHost != null) && (vppInterfacesInHost.contains(
                                  update.getUuid().getValue()))) {
                            newPort = false;
                        }
                    }
                }
                if (newPort) {
                    LOG.debug("handleNeutronPortUpdate: perform VPP interface creation"
                              + " operations for port: {}", update);
                    handleNeutronPortCreated(update);
                }
            }
        }
        // VPP port update logic ----- End

        if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(update.getDeviceOwner())) {
            handleRouterGatewayUpdated(update);
        } else if (NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(update.getDeviceOwner())) {
            elanService.handleKnownL3DmacAddress(update.getMacAddress().getValue(), update.getNetworkId().getValue(),
                    NwConstants.ADD_FLOW);
        }
        // check for QoS updates
        QosPortExtension updateQos = update.getAugmentation(QosPortExtension.class);
        QosPortExtension originalQos = original.getAugmentation(QosPortExtension.class);
        if (originalQos == null && updateQos != null) {
            // qos policy add
            NeutronvpnUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            NeutronQosUtils.handleNeutronPortQosUpdate(dataBroker, odlInterfaceRpcService,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {
            // qos policy update
            NeutronvpnUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
            NeutronvpnUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            NeutronQosUtils.handleNeutronPortQosUpdate(dataBroker, odlInterfaceRpcService,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qos policy delete
            NeutronQosUtils.handleNeutronPortQosRemove(dataBroker, odlInterfaceRpcService,
                    original, originalQos.getQosPolicyId());
            NeutronvpnUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
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
                for (FixedIps portIP : routerPort.getFixedIps()) {
                    Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
                    if (vpnId == null) {
                        vpnId = routerId;
                    }
                    // NOTE:  Please donot change the order of calls to updateSubnetNodeWithFixedIPs
                    // and addSubnetToVpn here
                    String ipValue = String.valueOf(portIP.getIpAddress().getValue());
                    nvpnManager.updateSubnetNodeWithFixedIps(portIP.getSubnetId(), routerId,
                            routerPort.getUuid(), ipValue, routerPort.getMacAddress().getValue());
                    nvpnManager.addSubnetToVpn(vpnId, portIP.getSubnetId());
                    nvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);
                    PhysAddress mac = new PhysAddress(routerPort.getMacAddress().getValue());
                    LOG.trace("NeutronPortChangeListener Add Subnet Gateway IP {} MAC {} Interface {} VPN {}",
                            ipValue, routerPort.getMacAddress(),
                            routerPort.getUuid().getValue(), vpnId.getValue());
                    // ping responder for router interfaces
                    nvpnManager.createVpnInterface(vpnId, routerId, routerPort, null);
                }
            } else {
                LOG.error("Neutron network {} corresponding to router interface port {} for neutron router {} already"
                    + " associated to VPN {}", infNetworkId.getValue(), routerPort.getUuid().getValue(),
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
            for (FixedIps portIP : routerPort.getFixedIps()) {
                Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
                if (vpnId == null) {
                    vpnId = routerId;
                }
                // NOTE:  Please donot change the order of calls to removeSubnetFromVpn and
                // and updateSubnetNodeWithFixedIPs
                nvpnManager.removeSubnetFromVpn(vpnId, portIP.getSubnetId());
                nvpnManager.updateSubnetNodeWithFixedIps(portIP.getSubnetId(), null,
                        null, null, null);
                nvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);
                String ipValue = String.valueOf(portIP.getIpAddress().getValue());
                NeutronvpnUtils.removeVpnPortFixedIpToPort(dataBroker, vpnId.getValue(),
                        ipValue, null /*writeTransaction*/);
                // ping responder for router interfaces
                nvpnManager.deleteVpnInterface(vpnId, routerId, routerPort, null);
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
            LOG.warn("No router found for router GW port {} router id {}", routerGwPort.getUuid(), routerId.getValue());
            return;
        }
        gwMacResolver.sendArpRequestsToExtGateways(router);
    }

    private void handleNeutronPortCreated(final Port port) {
        final String portName = port.getUuid().getValue();
        final Uuid portId = port.getUuid();
        final Uuid subnetId = port.getFixedIps().get(0).getSubnetId();
        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            // add direct port to subnetMaps config DS
            if (!NeutronUtils.isPortVnicTypeNormal(port)) {
                nvpnManager.updateSubnetmapNodeWithPorts(subnetId, null, portId);
                LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created", portName);
                futures.add(wrtConfigTxn.submit());
                return futures;
            }
            // VPP port creation logic ----- Start
            if (isValidVPPVhostUser(port)) {
                LOG.debug("handleNeutronPortCreated for a VPP port type: {}", port);
                String portBindingHost = port.getAugmentation(PortBindingExtension.class).getHostId();
                if (portBindingHost == null) {
                    LOG.warn("Cannot create VPP bridge domain."
                                 + "binding:host_id not specified in neutron port: {}", port);
                    futures.add(wrtConfigTxn.submit());
                    return futures;
                }
                Node node = getVPPNode(portBindingHost, dataBroker);
                if (node == null) {
                    LOG.warn("Cannot create VPP bridge domain. Unable to find binding:host_id in topology: {}", port);
                    futures.add(wrtConfigTxn.submit());
                    return futures;
                }
                String networkId = port.getNetworkId().getValue();
                //Check if bridge domain existing for this network and if not, create
                synchronized (vbdToVppNodesToInterfaces) {
                    ConcurrentHashMap<String, HashSet<String>> vppNodesInVbd = vbdToVppNodesToInterfaces.get(networkId);
                    if (vppNodesInVbd == null) {
                        if (createVPPBridgeDomainOnNode(port, node, dataBroker)) {
                            vppNodesInVbd = new ConcurrentHashMap<String,HashSet<String>>();
                            vppNodesInVbd.put(portBindingHost, new HashSet<String>());
                            vbdToVppNodesToInterfaces.put(networkId, vppNodesInVbd);
                        } else {
                            futures.add(wrtConfigTxn.submit());
                            return futures;
                        }
                    }
                    //Check if portBindingHost is already part of bridge domain.
                    //If not, clone the bridge domain on that host
                    if (!vppNodesInVbd.containsKey(portBindingHost)) {
                        if (cloneVPPBridgeDomainOnNode(networkId, node)) {
                            vppNodesInVbd.put(portBindingHost, new HashSet<String>());
                            vbdToVppNodesToInterfaces.put(networkId, vppNodesInVbd);
                        } else {
                            futures.add(wrtConfigTxn.submit());
                            return futures;
                        }
                    }
                    //Check if VPP interface is already created for this port
                    if (vppNodesInVbd.get(portBindingHost).contains(port.getUuid().getValue())) {
                        LOG.debug("VPP port {} is already part of a VPP bridge domain on host {}",
                                port, portBindingHost);
                        futures.add(wrtConfigTxn.submit());
                        return futures;
                    }
                }
                //Create VPP interface (vhostuser or tap) on portBindingHost
                createVPPInterfaceOnNode(port, node);
                //Add VPP interface on portBindingHost to bridge domain
                addVPPInterfaceToBridgeDomain(port, node, networkId);
                //Update global map
                vbdToVppNodesToInterfaces.get(networkId).get(portBindingHost).add(port.getUuid().getValue());

                futures.add(wrtConfigTxn.submit());
                return futures;
            }
            // VPP port creation logic ----- End
            LOG.info("Of-port-interface creation for port {}", portName);
            // Create of-port interface for this neutron port
            String portInterfaceName = createOfPortInterface(port, wrtConfigTxn);
            LOG.debug("Creating ELAN Interface for port {}", portName);
            createElanInterface(port, portInterfaceName, wrtConfigTxn);

            Subnetmap subnetMap = nvpnManager.updateSubnetmapNodeWithPorts(subnetId, portId, null);
            Uuid vpnId = (subnetMap != null) ? subnetMap.getVpnId() : null;
            Uuid routerId = (subnetMap != null) ? subnetMap.getRouterId() : null;
            if (vpnId != null) {
                // create vpn-interface on this neutron port
                LOG.debug("Adding VPN Interface for port {}", portName);
                nvpnManager.createVpnInterface(vpnId, routerId, port, wrtConfigTxn);
            }
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    // VPP port creation logic ----- Start
    private boolean isValidVPPVhostUser(Port port) {
        PortBindingExtension portBindingExt = port.getAugmentation(PortBindingExtension.class);
        if (portBindingExt != null) {
            String vifType = portBindingExt.getVifType();
            String deviceOwner = port.getDeviceOwner();
            if (vifType != null && deviceOwner != null) {
                if (vifType.contains(VHOST_USER)) {
                    for (String supportedDeviceOwner : SUPPORTED_DEVICE_OWNERS) {
                        if (deviceOwner.contains(supportedDeviceOwner)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    InstanceIdentifier<Topology> getVPPTopologyIId() {
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId("topology-netconf")))
                .build();
    }

    Node getVPPNode(String portBindingHost, DataBroker dataBroker) throws Exception {
        ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        List<Node> nodes = tx.read(LogicalDatastoreType.CONFIGURATION, getVPPTopologyIId())
                .checkedGet()
                .get()
                .getNode();
        return nodes.stream().filter(n -> n.getNodeId().getValue().equals(portBindingHost)).findFirst().get();
    }

    private String createPortName(Uuid portUuid) {
        String tapPortName;
        String uuid = portUuid.getValue();
        if (uuid != null && uuid.length() >= 12) {
            tapPortName = TAP_PORT_NAME_PREFIX + uuid.substring(0, 11);
        } else {
            tapPortName = TAP_PORT_NAME_PREFIX + uuid;
        }
        return tapPortName;
    }

    private boolean createVPPBridgeDomainOnNode(Port port, Node host, DataBroker dataBroker) {
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, port.getNetworkId());
        NetworkProviderExtension providerAug = network.getAugmentation(NetworkProviderExtension.class);
        if (providerAug == null || providerAug.getSegmentationId() == null) {
            LOG.warn("Cannot create VPP bridge domain. Segmentation ID not specified in neutron network: {}", network);
            return false;
        }
        PhysicalLocationRefBuilder location = new PhysicalLocationRefBuilder();
        location.setNodeId(host.getNodeId());
        VxlanBuilder tunneltypeBuilder = new VxlanBuilder().setVni(new VxlanVniType(
                Long.parseLong(providerAug.getSegmentationId())));
        CreateVirtualBridgeDomainOnNodesInputBuilder vbdInputBuilder =
                new CreateVirtualBridgeDomainOnNodesInputBuilder()
                .setId(port.getNetworkId().getValue())
                .setDescription("Neutron Network")
                .setTunnelType(tunneltypeBuilder.build())
                .setUnknownUnicastFlood(true)
                .setPhysicalLocationRef(Arrays.asList(location.build()));
        try {
            vppAdapterRpcService.createVirtualBridgeDomainOnNodes(vbdInputBuilder.build()).get(); //Future get()
        } catch (ExecutionException e) {
            LOG.error("failed to create vpp bridge domain for network {} on host {} due to the exception {} ",
                  network, host, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            LOG.error("failed to create vpp bridge domain for network {} on host {} due to the exception {} ",
                  network, host, e.getMessage());
            return false;
        }

        LOG.debug("VPP bridge domain created for network: {} on host {}", network, host);
        return true;
    }

    private boolean cloneVPPBridgeDomainOnNode(String bridgeDomainId, Node host) {
        CloneVirtualBridgeDomainOnNodesInputBuilder vbdInputBuilder = new CloneVirtualBridgeDomainOnNodesInputBuilder()
                .setBridgeDomainId(bridgeDomainId)
                .setBridgeDomainNode(Arrays.asList(host.getNodeId()));
        try {
            vppAdapterRpcService.cloneVirtualBridgeDomainOnNodes(vbdInputBuilder.build()).get(); //Future get()
        } catch (ExecutionException e) {
            LOG.error("failed to clone vpp bridge domain {} on host {} due to the exception {} ",
                   bridgeDomainId, host, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            LOG.error("failed to clone vpp bridge domain {} on host {} due to the exception {} ",
                   bridgeDomainId, host, e.getMessage());
            return false;
        }
        LOG.debug("VPP bridge domain {} cloned on host: {}", bridgeDomainId, host);
        return true;
    }

    private boolean createVPPInterfaceOnNode(Port port, Node host) {
        CreateInterfaceOnNodeInputBuilder vbdIfInputBuilder = new CreateInterfaceOnNodeInputBuilder()
                .setVppInterfaceName(VPP_INTERFACE_NAME_PREFIX + port.getUuid().getValue())
                .setVppNodeId(host.getNodeId())
                .setDescription("Neutron Port");
        if (port.getDeviceOwner().contains(COMPUTE_OWNER)) {
            String vhostuserSocket = null;
            if (port.getAugmentation(PortBindingExtension.class).getVifDetails() != null) {
                vhostuserSocket = port.getAugmentation(PortBindingExtension.class).getVifDetails()
                        .stream()
                        .filter(v -> v.getDetailsKey().equals("vhostuser_socket"))
                        .map(v -> v.getValue())
                        .findFirst()
                        .get();
            }
            if (vhostuserSocket == null) {
                vhostuserSocket = "/tmp" + "socket_" + port.getUuid().getValue();
            }
            vbdIfInputBuilder.setInterfaceTypeChoice(new VhostUserCaseBuilder().setSocket(vhostuserSocket).build());
        } else if (port.getDeviceOwner().contains(DHCP_OWNER) && port.getMacAddress() != null) {
            TapCaseBuilder tapIfBuilder = new TapCaseBuilder()
                    .setPhysicalAddress(new PhysAddress(port.getMacAddress().getValue()))
                    .setName(createPortName(port.getUuid()));
            vbdIfInputBuilder.setInterfaceTypeChoice(tapIfBuilder.build());
        }
        try {
            vppAdapterRpcService.createInterfaceOnNode(vbdIfInputBuilder.build()).get(); //Future get()
        } catch (ExecutionException e) {
            LOG.error("failed to create vpp interface for port {} on host {} due to the exception {} ",
                   port, host, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            LOG.error("failed to create vpp interface for port {} on host {} due to the exception {} ",
                   port, host, e.getMessage());
            return false;
        }
        LOG.debug("VPP interface created for port: {} on {}", port, host);
        return true;
    }

    private boolean addVPPInterfaceToBridgeDomain(Port port, Node host, String bridgeDomainId) {
        AddInterfaceToBridgeDomainInputBuilder vbdIfInputBuilder = new AddInterfaceToBridgeDomainInputBuilder()
                .setVppInterfaceName(VPP_INTERFACE_NAME_PREFIX + port.getUuid().getValue())
                .setVppNodeId(host.getNodeId())
                .setBridgeDomainId(bridgeDomainId);
        try {
            vppAdapterRpcService.addInterfaceToBridgeDomain(vbdIfInputBuilder.build()).get(); //Future get()
        } catch (ExecutionException e) {
            LOG.error("failed to add vpp interface for port {} to bridge domain {} due to the exception {} ",
                    port, bridgeDomainId, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            LOG.error("failed to add vpp interface for port {} to bridge domain {} due to the exception {} ",
                    port, bridgeDomainId, e.getMessage());
            return false;
        }
        LOG.debug("VPP interface for port: {} added to VPP bridge domain {}", port, bridgeDomainId);
        return true;
    }

    private boolean deleteVPPInterfaceFromBridgeDomain(Port port, Node host, String bridgeDomainId) {
        DelInterfaceFromBridgeDomainInputBuilder vbdIfInputBuilder = new DelInterfaceFromBridgeDomainInputBuilder()
                .setVppInterfaceName(VPP_INTERFACE_NAME_PREFIX + port.getUuid().getValue())
                .setVppNodeId(host.getNodeId());
        try {
            vppAdapterRpcService.delInterfaceFromBridgeDomain(vbdIfInputBuilder.build()).get();
        } catch (ExecutionException e) {
            LOG.error("failed to delete vpp interface for port {} from bridge domain {} due to the exception {} ",
                          port, bridgeDomainId, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            LOG.error("failed to delete vpp interface for port {} from bridge domain {} due to the exception {} ",
                          port, bridgeDomainId, e.getMessage());
            return false;
        }
        LOG.debug("VPP interface for port: {} deleted from VPP node {}", port, host);
        return true;
    }

    private boolean deleteVPPInterfaceFromNode(Port port, Node host) {
        DeleteInterfaceFromNodeInputBuilder vbdIfInputBuilder = new DeleteInterfaceFromNodeInputBuilder()
                .setVppInterfaceName(VPP_INTERFACE_NAME_PREFIX + port.getUuid().getValue())
                .setVppNodeId(host.getNodeId());
        try {
            vppAdapterRpcService.deleteInterfaceFromNode(vbdIfInputBuilder.build()).get();
        } catch (ExecutionException e) {
            LOG.error("failed to delete vpp interface for port {} on node  {} due to the exception {} ",
                          port, host, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            LOG.error("failed to delete vpp interface for port {} on node  {} due to the exception {} ",
                          port, host, e.getMessage());
            return false;
        }
        LOG.debug("VPP interface delete for port: {} on {}", port, host);
        return true;
    }

    private boolean deleteVPPBridgeDomainFromNode(String bridgeDomainId, Node host) {
        DeleteVirtualBridgeDomainFromNodesInputBuilder vbdInputBuilder =
                new DeleteVirtualBridgeDomainFromNodesInputBuilder()
                .setBridgeDomainId(bridgeDomainId)
                .setBridgeDomainNode(Arrays.asList(host.getNodeId()));
        try {
            vppAdapterRpcService.deleteVirtualBridgeDomainFromNodes(vbdInputBuilder.build()).get();
        } catch (ExecutionException e) {
            LOG.error("failed to delete vpp bridge domain {} on node  {} due to the exception {} ",
                       bridgeDomainId, host, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            LOG.error("failed to delete vpp bridge domain {} on node  {} due to the exception {} ",
                       bridgeDomainId, host, e.getMessage());
            return false;
        }
        LOG.debug("VPP bridge domain {} deleted on host: {}", bridgeDomainId, host);
        return true;
    }
    // VPP port creation logic ----- End

    private void handleNeutronPortDeleted(final Port port) {
        final String portName = port.getUuid().getValue();
        final Uuid portId = port.getUuid();
        final Uuid subnetId = port.getFixedIps().get(0).getSubnetId();
        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            // remove direct port from subnetMaps config DS
            if (!NeutronUtils.isPortVnicTypeNormal(port)) {
                nvpnManager.removePortsFromSubnetmapNode(subnetId, null, portId);
                LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created", portName);
                futures.add(wrtConfigTxn.submit());
                return futures;
            }
            Subnetmap subnetMap = nvpnManager.removePortsFromSubnetmapNode(subnetId, portId, null);
            Uuid vpnId = (subnetMap != null) ? subnetMap.getVpnId() : null;
            Uuid routerId = (subnetMap != null) ? subnetMap.getRouterId() : null;
            if (vpnId != null) {
                // remove vpn-interface for this neutron port
                LOG.debug("removing VPN Interface for port {}", portName);
                nvpnManager.deleteVpnInterface(vpnId, routerId, port, wrtConfigTxn);
            }
            // VPP port creation logic ----- Start
            if (isValidVPPVhostUser(port)) {
                LOG.debug("handleNeutronPortDeleted for a VPP port type: {}", port);

                //Check if bridge domain existing for this network and if not, create
                String networkId = port.getNetworkId().getValue();
                synchronized (vbdToVppNodesToInterfaces) {
                    if (vbdToVppNodesToInterfaces.get(networkId) == null) {
                        LOG.warn("Cannot delete VPP port. Unable to find corresponding bridge domain: {}", port);
                        futures.add(wrtConfigTxn.submit());
                        return futures;
                    }

                    String portBindingHost = port.getAugmentation(PortBindingExtension.class).getHostId();
                    if (portBindingHost == null) {
                        LOG.warn("Cannot delete VPP bridge domain. binding:host_id not specified in neutron port: {}",
                                port);
                        futures.add(wrtConfigTxn.submit());
                        return futures;
                    }
                    Node node = getVPPNode(portBindingHost, dataBroker);
                    if (node == null) {
                        LOG.warn("Cannot delete VPP bridge domain. Unable to find binding:host_id in topology: {}",
                                port);
                        futures.add(wrtConfigTxn.submit());
                        return futures;
                    }
                    HashSet<String> vppInterfacesInHost = vbdToVppNodesToInterfaces
                            .get(networkId)
                            .get(portBindingHost);
                    if ((vppInterfacesInHost == null) || (!vppInterfacesInHost.contains(port.getUuid().getValue()))) {
                        LOG.warn("Cannot delete VPP port. Unable to find corresponding interface {} on vpp node: {}",
                                port, portBindingHost);
                        futures.add(wrtConfigTxn.submit());
                        return futures;
                    }

                    //Delete VPP interface on portBindingHost from bridge domain
                    deleteVPPInterfaceFromBridgeDomain(port, node, networkId);
                    deleteVPPInterfaceFromNode(port, node);

                    //Update the global map
                    vppInterfacesInHost.remove(port.getUuid().getValue());
                    if (vppInterfacesInHost.isEmpty()) {
                        //No more VPP interfaces on the node for this bridge domain.
                        //Delete the bridge domain from nodes
                        deleteVPPBridgeDomainFromNode(networkId, node);
                        vbdToVppNodesToInterfaces.get(networkId).remove(portBindingHost);
                    } else {
                        vbdToVppNodesToInterfaces.get(networkId).put(portBindingHost, vppInterfacesInHost);
                    }
                }

                futures.add(wrtConfigTxn.submit());
                return futures;
            }
            // VPP port creation logic ----- End
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
        if (portoriginal.getFixedIps() == null || portoriginal.getFixedIps().isEmpty()) {
            handleNeutronPortCreated(portupdate);
            return;
        }

        if (portupdate.getFixedIps() == null || portupdate.getFixedIps().isEmpty()) {
            LOG.debug("Ignoring portUpdate (fixed_ip removal) for port {} as this case is handled "
                      + "during subnet deletion event.", portupdate.getUuid().getValue());
            return;
        }

        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("PORT- " + portupdate.getUuid().getValue(), () -> {
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            Uuid vpnIdNew = null;
            final Uuid subnetIdOr = portupdate.getFixedIps().get(0).getSubnetId();
            final Uuid subnetIdUp = portupdate.getFixedIps().get(0).getSubnetId();
            // check if subnet UUID has changed upon change in fixedIP
            final Boolean subnetUpdated = subnetIdUp.equals(subnetIdOr) ? false : true;

            if (subnetUpdated) {
                Subnetmap subnetMapOld = nvpnManager.removePortsFromSubnetmapNode(subnetIdOr, portoriginal
                        .getUuid(), null);
                Uuid vpnIdOld = (subnetMapOld != null) ? subnetMapOld.getVpnId() : null;
                Subnetmap subnetMapNew = nvpnManager.updateSubnetmapNodeWithPorts(subnetIdUp, portupdate
                                .getUuid(), null);
                vpnIdNew = (subnetMapNew != null) ? subnetMapNew.getVpnId() : null;
            }
            if (!subnetUpdated) {
                Subnetmap subnetmap = NeutronvpnUtils.getSubnetmap(dataBroker, subnetIdUp);
                vpnIdNew = subnetmap != null ? subnetmap.getVpnId() : null;
            }
            if (vpnIdNew != null) {
                // remove vpn-interface for this neutron port
                LOG.debug("removing VPN Interface for port {}", portupdate.getUuid().getValue());
                nvpnManager.deleteVpnInterface(vpnIdNew, null, portupdate, wrtConfigTxn);
                // create vpn-interface on this neutron port
                LOG.debug("Adding VPN Interface for port {}", portupdate.getUuid().getValue());
                nvpnManager.createVpnInterface(vpnIdNew, null, portupdate, wrtConfigTxn);
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    private static InterfaceAclBuilder handlePortSecurityUpdated(Port portOriginal, Port portUpdated, boolean
            origSecurityEnabled, boolean updatedSecurityEnabled, InterfaceBuilder interfaceBuilder) {
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

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private String createOfPortInterface(Port port, WriteTransaction wrtConfigTxn) {
        Interface inf = createInterface(port);
        String infName = inf.getName();

        LOG.debug("Creating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (!optionalInf.isPresent()) {
                wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
            } else {
                LOG.warn("Interface {} is already present", infName);
            }
        } catch (Exception e) {
            LOG.error("failed to create interface {} due to the exception {} ", infName, e.getMessage());
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
        }
        return interfaceBuilder.build();
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void deleteOfPortInterface(Port port, WriteTransaction wrtConfigTxn) {
        String name = port.getUuid().getValue();
        LOG.debug("Removing OFPort Interface {}", name);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(name);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (optionalInf.isPresent()) {
                wrtConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier);
            } else {
                LOG.error("Interface {} is not present", name);
            }
        } catch (Exception e) {
            LOG.error("Failed to delete interface {} due to the exception {}", name, e.getMessage());
        }
    }

    private void createElanInterface(Port port, String name, WriteTransaction wrtConfigTxn) {
        String elanInstanceName = port.getNetworkId().getValue();
        List<PhysAddress> physAddresses = new ArrayList<>();
        physAddresses.add(new PhysAddress(port.getMacAddress().getValue()));

        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(name).setStaticMacEntries(physAddresses).setKey(new ElanInterfaceKey(name)).build();
        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, id, elanInterface);
        LOG.debug("Creating new ELan Interface {}", elanInterface);
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
                + " Port Info Config DS failed with exception {}",
                floatingIpId.getValue(), floatingIpPortId.getValue(), e);
        }
    }
}

/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.commons.InterfaceManagerCommonUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.InterfaceDeviceTypeCompute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.InterfaceDeviceTypeDhcp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.InterfaceVifTypeVhostuser;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class in charge of handling creations, modifications and removals of
 * ElanInterfaces in VPP data plane nodes.
 *
 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface
 */
@Singleton
@SuppressWarnings("deprecation")
public class ElanInterfaceVppRenderer extends AsyncDataTreeChangeListenerBase<ElanInterface, ElanInterfaceVppRenderer>
        implements AutoCloseable {

    //Constants for VPP interfaces
    //TODO, this constant is also repeated in NeutronPortChangeListener in neutron-vpn module
    //Move it to a common place
    public static final TopologyId NETCONF_TOPOLOGY_ID = new TopologyId("topology-netconf");
    public static final List<Class<?>> SUPPORTED_DEVICE_OWNERS = new ArrayList<>(
            Arrays.asList(InterfaceDeviceTypeCompute.class, InterfaceDeviceTypeDhcp.class));
    private final VppAdapterService vppAdapterRpcService;
    private final Map<String, ConcurrentHashMap<String, HashSet<String>>>
                          vbdToVppNodesToInterfaces = new ConcurrentHashMap<>();
    private static final String VPP_INTERFACE_NAME_PREFIX = "neutron_port_";
    private static final String TAP_PORT_NAME_PREFIX = "tap";
    private static final String RT_PORT_NAME_PREFIX = "qr-";

    private final DataBroker broker;

    private final Map<String, ConcurrentLinkedQueue<ElanInterface>>
        unProcessedElanInterfaces = new ConcurrentHashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceVppRenderer.class);

    @Inject
    public ElanInterfaceVppRenderer(final DataBroker dataBroker,
            final VppAdapterService vppAdapterRpcService) {
        super(ElanInterface.class, ElanInterfaceVppRenderer.class);
        this.broker = dataBroker;
        this.vppAdapterRpcService = vppAdapterRpcService;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<ElanInterface> getWildCardPath() {
        return InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class);
    }

    @Override
    protected void add(InstanceIdentifier<ElanInterface> identifier, ElanInterface elanInterfaceAdded) {
        LOG.debug("handle ElanInterface {} add... Checking if it is of VPP type", elanInterfaceAdded.getName());
        handleInterfaceAdded(elanInterfaceAdded);
    }

    @Override
    protected void update(InstanceIdentifier<ElanInterface> identifier, ElanInterface original, ElanInterface update) {
        LOG.debug("handle ElanInterface {} update... Checking if it is of VPP type", update.getName());
        handleInterfaceAdded(update);
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInterface> identifier, ElanInterface del) {
        LOG.debug("handle ElanInterface {} remove... Checking if it is of VPP type", del.getName());
        String interfaceName = del.getName();
        Interface intf = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(interfaceName), broker);
        if (intf == null) {
            LOG.error("Interface {} doesn't exist in config datastore", interfaceName);
            return;
        }
        handleInterfaceDeleted(del, intf);
    }

    protected void handleInterfaceAdded(ElanInterface elanInterfaceAdded) {
        String interfaceName = elanInterfaceAdded.getName();
        Interface intf = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(interfaceName), broker);
        if (intf == null) {
            LOG.error("Interface {} doesn't exist in config datastore", interfaceName);
            return;
        }

        String elanInstanceName = elanInterfaceAdded.getElanInstanceName();
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanInstanceName);
        if (elanInstance == null) {
            LOG.warn("ElanInterface add notification before ElanInstance creation for "
                    + "interface {}...Can this happen?", interfaceName);
            return;
        }

        if (!isVPPInterface(intf)) {
            LOG.debug("Interface {} is not of VPP type, so aborting the handling", interfaceName);
            return;
        }

        LOG.debug("handle ElanInterface {} add of VPP type", interfaceName);

        ParentRefs parentRefs = intf.getAugmentation(ParentRefs.class);
        if (parentRefs == null || parentRefs.getNodeIdentifier() == null) {
            LOG.debug("Unable to find binding:host_id for interface: {}", interfaceName);
            return;
        }
        String portBindingHost = parentRefs.getNodeIdentifier().get(0).getNodeId();

        Node node = getVPPNode(portBindingHost, broker);
        if (node == null) {
            LOG.debug("Unable to find binding:host_id in topology for interface: {}", interfaceName);
            return;
        }

        //Check if bridge domain existing for this network and if not, create
        synchronized (vbdToVppNodesToInterfaces) {
            ConcurrentHashMap<String, HashSet<String>> vppNodesInVbd = vbdToVppNodesToInterfaces.get(elanInstanceName);
            if (vppNodesInVbd == null) {
                if (createVPPBridgeDomainOnNode(elanInstance, node, broker)) {
                    vppNodesInVbd = new ConcurrentHashMap<>();
                    vppNodesInVbd.put(portBindingHost, new HashSet<String>());
                    vbdToVppNodesToInterfaces.put(elanInstanceName, vppNodesInVbd);
                } else {
                    return;
                }
            }
            //Check if portBindingHost is already part of bridge domain.
            //If not, clone the bridge domain on that host
            if (!vppNodesInVbd.containsKey(portBindingHost)) {
                if (cloneVPPBridgeDomainOnNode(elanInstanceName, node)) {
                    vppNodesInVbd.put(portBindingHost, new HashSet<String>());
                    vbdToVppNodesToInterfaces.put(elanInstanceName, vppNodesInVbd);
                } else {
                    return;
                }
            }
            //Check if VPP interface is already created for this port
            if (vppNodesInVbd.get(portBindingHost).contains(interfaceName)) {
                LOG.debug("VPP interface {} is already part of a VPP bridge domain on host {}",
                        interfaceName, portBindingHost);
                return;
            }
            //Create VPP interface (vhostuser or tap) on portBindingHost
            createVPPInterfaceOnNode(intf, elanInterfaceAdded, node);
            //Add VPP interface on portBindingHost to bridge domain
            addVPPInterfaceToBridgeDomain(intf, node, elanInstanceName);
            //Update global map
            vbdToVppNodesToInterfaces.get(elanInstanceName).get(portBindingHost).add(interfaceName);
        }
    }

    protected void handleInterfaceDeleted(ElanInterface del, Interface intf) {
        String interfaceName = del.getName();

        if (!isVPPInterface(intf)) {
            LOG.debug("Interface {} is not of VPP type, so aborting the handling", interfaceName);
            return;
        }

        LOG.debug("handle ElanInterface {} delete of VPP type", interfaceName);

        ParentRefs parentRefs = intf.getAugmentation(ParentRefs.class);
        if (parentRefs == null || parentRefs.getNodeIdentifier() == null) {
            LOG.warn("Cannot delete VPP interface {}.. binding:host_id not available", interfaceName);
            return;
        }
        String portBindingHost = parentRefs.getNodeIdentifier().get(0).getNodeId();

        Node node = getVPPNode(portBindingHost, broker);
        if (node == null) {
            LOG.warn("Cannot delete VPP interface {}.. Unable to find the Node in topology for "
                    + "binding:host_id {}", interfaceName, portBindingHost);
            return;
        }

        String elanInstanceName = del.getElanInstanceName();

        synchronized (vbdToVppNodesToInterfaces) {
            if (vbdToVppNodesToInterfaces.get(elanInstanceName) == null) {
                LOG.warn("Cannot delete VPP interface {}. Unable to find corresponding bridge domain", interfaceName);
                return;
            }

            HashSet<String> vppInterfacesInHost = vbdToVppNodesToInterfaces
                    .get(elanInstanceName)
                    .get(portBindingHost);
            if (vppInterfacesInHost == null || !vppInterfacesInHost.contains(del.getName())) {
                LOG.warn("Cannot delete VPP interface {}. Unable to find on vpp node: {}",
                        interfaceName, portBindingHost);
                return;
            }

            //Delete VPP interface on portBindingHost from bridge domain
            deleteVPPInterfaceFromBridgeDomain(intf, node, elanInstanceName);
            deleteVPPInterfaceFromNode(intf, node);

            //Update the global map
            vppInterfacesInHost.remove(del.getName());
            if (vppInterfacesInHost.isEmpty()) {
                //No more VPP interfaces on the node for this bridge domain.
                //Delete the bridge domain from nodes
                deleteVPPBridgeDomainFromNode(elanInstanceName, node);
                vbdToVppNodesToInterfaces.get(elanInstanceName).remove(portBindingHost);
            } else {
                vbdToVppNodesToInterfaces.get(elanInstanceName).put(portBindingHost, vppInterfacesInHost);
            }
        }
    }

    private boolean isVPPInterface(Interface intf) {
        ParentRefs parentRefs = intf.getAugmentation(ParentRefs.class);
        if (parentRefs != null && parentRefs.getDeviceOwner() != null
                && parentRefs.getVifType() != null
                && parentRefs.getNodeIdentifier() != null) {
            if ((parentRefs.getNodeIdentifier().get(0)
                    .getTopologyId().equals(NETCONF_TOPOLOGY_ID.getValue()))) {
                //if (parentRefs.getVifType().equals(InterfaceVifTypeVhostuser.class)) {
                if (InterfaceVifTypeVhostuser.class.isAssignableFrom(parentRefs.getVifType())) {
                    if (InterfaceDeviceTypeCompute.class.isAssignableFrom(parentRefs.getDeviceOwner())
                        || InterfaceDeviceTypeDhcp.class.isAssignableFrom(parentRefs.getDeviceOwner())) {
                    //if (SUPPORTED_DEVICE_OWNERS.contains(parentRefs.getDeviceOwner())) {
                        return true;
                    } else {
                        LOG.trace("isVPPInterface: unsupported deviceOwnerType {}",parentRefs.getDeviceOwner());
                    }
                } else {
                    LOG.trace("isVPPInterface: vifType {} is not vhostuser", parentRefs.getVifType());
                }
            } else {
                LOG.trace("isVPPInterface: topologyId is not of topology-netconf");
            }
        } else {
            LOG.trace("isVPPInterface: Not all parentRefs fields are populated");
        }
        return false;
    }

    InstanceIdentifier<Topology> getNetconfTopologyIId() {
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(NETCONF_TOPOLOGY_ID))
                .build();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    Node getVPPNode(String portBindingHost, DataBroker dataBroker) {
        Node node = null;
        try {
            ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
            List<Node> nodes = tx
                    .read(LogicalDatastoreType.CONFIGURATION, getNetconfTopologyIId())
                    .checkedGet()
                    .get()
                    .getNode();
            node = nodes.stream().filter(n -> n.getNodeId().getValue().equals(portBindingHost)).findFirst().get();
        } catch (Exception e) {
            LOG.error("getVPPNode", e);
        }
        return node;
    }

    private String createPortName(String uuid) {
        String tapPortName;
        if (uuid != null && uuid.length() >= 12) {
            tapPortName = TAP_PORT_NAME_PREFIX + uuid.substring(0, 11);
        } else {
            tapPortName = TAP_PORT_NAME_PREFIX + uuid;
        }
        return tapPortName;
    }

    private boolean createVPPBridgeDomainOnNode(ElanInstance elan, Node host, DataBroker dataBroker) {
        PhysicalLocationRefBuilder location = new PhysicalLocationRefBuilder();
        location.setNodeId(host.getNodeId());
        VxlanBuilder tunneltypeBuilder = new VxlanBuilder().setVni(new VxlanVniType(
                elan.getSegmentationId()));
        CreateVirtualBridgeDomainOnNodesInputBuilder vbdInputBuilder =
                new CreateVirtualBridgeDomainOnNodesInputBuilder()
                .setId(elan.getElanInstanceName())
                .setDescription("Neutron Network")
                .setTunnelType(tunneltypeBuilder.build())
                .setUnknownUnicastFlood(true)
                .setPhysicalLocationRef(Arrays.asList(location.build()));
        try {
            vppAdapterRpcService.createVirtualBridgeDomainOnNodes(vbdInputBuilder.build()).get(); //Future get()
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("failed to create vpp bridge domain for network {} on host {} ",
                    elan.getElanInstanceName(), host, e);
            return false;
        }

        LOG.debug("VPP bridge domain created for network: {} on host {}", elan.getElanInstanceName(), host);
        return true;
    }

    private boolean cloneVPPBridgeDomainOnNode(String bridgeDomainId, Node host) {
        CloneVirtualBridgeDomainOnNodesInputBuilder vbdInputBuilder = new CloneVirtualBridgeDomainOnNodesInputBuilder()
                .setBridgeDomainId(bridgeDomainId)
                .setBridgeDomainNode(Arrays.asList(host.getNodeId()));
        try {
            vppAdapterRpcService.cloneVirtualBridgeDomainOnNodes(vbdInputBuilder.build()).get(); //Future get()
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("failed to clone vpp bridge domain {} on host {}",
                   bridgeDomainId, host, e);
            return false;
        }
        LOG.debug("VPP bridge domain {} cloned on host: {}", bridgeDomainId, host);
        return true;
    }

    private boolean createVPPInterfaceOnNode(Interface intf, ElanInterface elanIntf, Node host) {
        CreateInterfaceOnNodeInputBuilder vbdIfInputBuilder = new CreateInterfaceOnNodeInputBuilder()
                .setVppInterfaceName(VPP_INTERFACE_NAME_PREFIX + intf.getName())
                .setVppNodeId(host.getNodeId())
                .setDescription("Neutron Port");
        ParentRefs parentRefs = intf.getAugmentation(ParentRefs.class);
        if (parentRefs.getDeviceOwner().equals(InterfaceDeviceTypeCompute.class)) {
            String vhostuserSocket = null;
            if (parentRefs.getVifDetails() != null) {
                vhostuserSocket = parentRefs.getVifDetails()
                        .stream()
                        .filter(v -> v.getVifDetailsKey().equals("vhostuser_socket"))
                        .map(v -> v.getVifDetailsValue())
                        .findFirst()
                        .get();
            }
            if (vhostuserSocket == null) {
                vhostuserSocket = "/tmp" + "socket_" + intf.getName();
            }
            vbdIfInputBuilder.setInterfaceTypeChoice(new VhostUserCaseBuilder().setSocket(vhostuserSocket).build());
        } else if (parentRefs.getDeviceOwner().equals(InterfaceDeviceTypeDhcp.class)
                && elanIntf.getStaticMacEntries() != null) {
            TapCaseBuilder tapIfBuilder = new TapCaseBuilder()
                    .setPhysicalAddress(elanIntf.getStaticMacEntries().get(0).getMacAddress())
                    .setName(createPortName(intf.getName()));
            vbdIfInputBuilder.setInterfaceTypeChoice(tapIfBuilder.build());
        }
        try {
            vppAdapterRpcService.createInterfaceOnNode(vbdIfInputBuilder.build()).get(); //Future get()
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("failed to create vpp interface for {} on host {}",
                    intf.getName(), host, e);
            return false;
        }
        LOG.debug("VPP interface created for {} on {}", intf.getName(), host);
        return true;
    }

    private boolean addVPPInterfaceToBridgeDomain(Interface intf, Node host, String bridgeDomainId) {
        AddInterfaceToBridgeDomainInputBuilder vbdIfInputBuilder = new AddInterfaceToBridgeDomainInputBuilder()
                .setVppInterfaceName(VPP_INTERFACE_NAME_PREFIX + intf.getName())
                .setVppNodeId(host.getNodeId())
                .setBridgeDomainId(bridgeDomainId);
        try {
            vppAdapterRpcService.addInterfaceToBridgeDomain(vbdIfInputBuilder.build()).get(); //Future get()
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("failed to add vpp interface for {} to bridge domain {}",
                    intf.getName(), bridgeDomainId, e);
            return false;
        }
        LOG.debug("VPP interface for port: {} added to VPP bridge domain {}", intf.getName(), bridgeDomainId);
        return true;
    }

    private boolean deleteVPPInterfaceFromBridgeDomain(Interface intf, Node host, String bridgeDomainId) {
        DelInterfaceFromBridgeDomainInputBuilder vbdIfInputBuilder = new DelInterfaceFromBridgeDomainInputBuilder()
                .setVppInterfaceName(VPP_INTERFACE_NAME_PREFIX + intf.getName())
                .setVppNodeId(host.getNodeId());
        try {
            vppAdapterRpcService.delInterfaceFromBridgeDomain(vbdIfInputBuilder.build()).get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("failed to delete vpp interface for {} from bridge domain {}",
                    intf.getName(), bridgeDomainId, e);
            return false;
        }
        LOG.debug("VPP interface for port: {} deleted from VPP node {}", intf.getName(), host);
        return true;
    }

    private boolean deleteVPPInterfaceFromNode(Interface intf, Node host) {
        DeleteInterfaceFromNodeInputBuilder vbdIfInputBuilder = new DeleteInterfaceFromNodeInputBuilder()
                .setVppInterfaceName(VPP_INTERFACE_NAME_PREFIX + intf.getName())
                .setVppNodeId(host.getNodeId());
        try {
            vppAdapterRpcService.deleteInterfaceFromNode(vbdIfInputBuilder.build()).get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("failed to delete vpp interface for {} on node  {}",
                    intf.getName(), host, e);
            return false;
        }
        LOG.debug("VPP interface for {} deleted on host {}", intf.getName(), host);
        return true;
    }

    private boolean deleteVPPBridgeDomainFromNode(String bridgeDomainId, Node host) {
        DeleteVirtualBridgeDomainFromNodesInputBuilder vbdInputBuilder =
                new DeleteVirtualBridgeDomainFromNodesInputBuilder()
                .setBridgeDomainId(bridgeDomainId)
                .setBridgeDomainNode(Arrays.asList(host.getNodeId()));
        try {
            vppAdapterRpcService.deleteVirtualBridgeDomainFromNodes(vbdInputBuilder.build()).get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("failed to delete vpp bridge domain {} on node  {}",
                       bridgeDomainId, host, e);
            return false;
        }
        LOG.debug("VPP bridge domain {} deleted on host: {}", bridgeDomainId, host);
        return true;
    }

    @Override
    protected ElanInterfaceVppRenderer getDataTreeChangeListener() {
        return this;
    }
}

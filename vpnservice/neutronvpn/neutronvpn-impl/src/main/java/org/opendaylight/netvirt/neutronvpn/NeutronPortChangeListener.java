/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;


import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortAddedToSubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortRemovedFromSubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.port.data.PortFixedipToPortNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NeutronPortChangeListener extends AbstractDataChangeListener<Port> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private NeutronvpnManager nvpnManager;
    private NeutronvpnNatManager nvpnNatManager;
    private LockManagerService lockManager;
    private NotificationPublishService notificationPublishService;
    private NotificationService notificationService;


    public NeutronPortChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr,NeutronvpnNatManager nVpnNatMgr,
                                     NotificationPublishService notiPublishService, NotificationService notiService) {
        super(Port.class);
        broker = db;
        nvpnManager = nVpnMgr;
        nvpnNatManager = nVpnNatMgr;
        notificationPublishService = notiPublishService;
        notificationService = notiService;
        registerListener(db);
    }

    public void setLockManager(LockManagerService lockManager) {
        this.lockManager = lockManager;
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("N_Port listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class),
                    NeutronPortChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Neutron Manager Port DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Port DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Port> identifier, Port input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Port : key: " + identifier + ", value=" + input);
        }
        Network network = NeutronvpnUtils.getNeutronNetwork(broker, input.getNetworkId());
        if (network == null || NeutronvpnUtils.isNetworkTypeVlanOrGre(network)) {
            //FIXME: This should be removed when support for VLAN and GRE network types is added
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} which is part of network {}.",
                    input.getName(), network);
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
        }
        if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
            handleNeutronPortCreated(input);
        }

    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing Port : key: " + identifier + ", value=" + input);
        }
        Network network = NeutronvpnUtils.getNeutronNetwork(broker, input.getNetworkId());
        if (network == null || NeutronvpnUtils.isNetworkTypeVlanOrGre(network)) {
            //FIXME: This should be removed when support for VLAN and GRE network types is added
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} which is part of network {}.",
                    input.getName(), network);
            return;
        }
        NeutronvpnUtils.removeFromPortCache(input);

        if ((input.getDeviceOwner() != null) && (input.getDeviceId() != null)) {
            if (input.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceRemoved(input);
                /* nothing else to do here */
                return;
            }
        }
        if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
            handleNeutronPortDeleted(input);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Port : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }

        Network network = NeutronvpnUtils.getNeutronNetwork(broker, update.getNetworkId());
        if (network == null || NeutronvpnUtils.isNetworkTypeVlanOrGre(network)) {
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} which is part of network {}."
                    + " Skipping the processing of Port update DCN", update.getName(), network);
            return;
        }
        List<FixedIps> oldIPs = (original.getFixedIps() != null) ? original.getFixedIps() : new ArrayList<FixedIps>();
        List<FixedIps> newIPs = (update.getFixedIps() != null) ? update.getFixedIps() : new ArrayList<FixedIps>();

        /* check if VIF type updated as part of port binding */
        if (NeutronvpnUtils.isPortVifTypeUpdated(original, update)) {
            updateOfPortInterface(original, update);
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

        if (!oldIPs.equals(newIPs)) {
            Iterator<FixedIps> iterator = newIPs.iterator();
            while (iterator.hasNext()) {
                FixedIps ip = iterator.next();
                if (oldIPs.remove(ip)) {
                    iterator.remove();
                }
            }
            handleNeutronPortUpdated(original, update);
        }
        handlePortSecurityUpdated(original, update);
    }

    private void handleRouterInterfaceAdded(Port routerPort) {
        if (routerPort.getDeviceId() != null) {
            Uuid routerId = new Uuid(routerPort.getDeviceId());
            Uuid infNetworkId = routerPort.getNetworkId();
            Uuid existingVpnId = NeutronvpnUtils.getVpnForNetwork(broker, infNetworkId);
            if (existingVpnId == null) {
                for (FixedIps portIP : routerPort.getFixedIps()) {
                    if (portIP.getIpAddress().getIpv4Address() != null) {
                        Uuid vpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId, true);
                        if (vpnId == null) {
                            vpnId = routerId;
                        }
                        nvpnManager.addSubnetToVpn(vpnId, portIP.getSubnetId());
                        nvpnNatManager.handleSubnetsForExternalRouter(routerId, broker);
                    }
                }
            } else {
                LOG.error("Neutron network {} corresponding to router interface port {} for neutron router {} already" +
                        " associated to VPN {}", infNetworkId.getValue(), routerPort.getUuid().getValue(), routerId
                        .getValue(), existingVpnId.getValue());
            }
        }
    }

    private void handleRouterInterfaceRemoved(Port routerPort) {
        if (routerPort.getDeviceId() != null) {
            Uuid routerId = new Uuid(routerPort.getDeviceId());
            for (FixedIps portIP : routerPort.getFixedIps()) {
                if (portIP.getIpAddress().getIpv4Address() != null) {
                    Uuid vpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId, true);
                    if(vpnId == null) {
                        vpnId = routerId;
                    }
                    nvpnManager.removeSubnetFromVpn(vpnId, portIP.getSubnetId());
                    nvpnNatManager.handleSubnetsForExternalRouter(routerId, broker);
                }
            }
        }
    }

    private void handleNeutronPortCreated(Port port) {
        if (!NeutronUtils.isPortVnicTypeNormal(port)) {
            nvpnManager.updateSubnetmapNodeWithPorts(port.getFixedIps().get(0).getSubnetId(), null, port.getUuid());
            LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created",
                    port.getUuid().getValue());
            return;
        }
        LOG.info("Of-port-interface creation");
        // Create of-port interface for this neutron port
        String portInterfaceName = createOfPortInterface(port);
        LOG.debug("Creating ELAN Interface");
        createElanInterface(port, portInterfaceName);
        LOG.debug("Add port to subnet");
        // add port to local Subnets DS
        Uuid vpnId = addPortToSubnets(port);

        if (vpnId != null) {
            // create vpn-interface on this neutron port
            LOG.debug("Adding VPN Interface");
            nvpnManager.createVpnInterface(vpnId, port);
            Uuid routerId = NeutronvpnUtils.getVpnMap(broker, vpnId).getRouterId();
            if (routerId != null) {
                nvpnManager.addToNeutronRouterInterfacesMap(routerId, port.getUuid().getValue());
            }
        }
    }

    private void handleNeutronPortDeleted(Port port) {
        if (!NeutronUtils.isPortVnicTypeNormal(port)) {
            nvpnManager.removePortsFromSubnetmapNode(port.getFixedIps().get(0).getSubnetId(), null, port.getUuid());
            LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created",
                    port.getUuid().getValue());
            return;
        }
        //dissociate fixedIP from floatingIP if associated
        nvpnManager.dissociatefixedIPFromFloatingIP(port.getUuid().getValue());
        LOG.debug("Remove port from subnet");
        // remove port from local Subnets DS
        Uuid vpnId = removePortFromSubnets(port);

        if (vpnId != null) {
            // remove vpn-interface for this neutron port
            LOG.debug("removing VPN Interface");
            nvpnManager.deleteVpnInterface(port);
        }
        // Remove of-port interface for this neutron port
        // ELAN interface is also implicitly deleted as part of this operation
        LOG.debug("Of-port-interface removal", port);
        deleteOfPortInterface(port);
        if (vpnId != null) {
            Uuid routerId = NeutronvpnUtils.getVpnMap(broker, vpnId).getRouterId();
            if (routerId != null) {
                nvpnManager.removeFromNeutronRouterInterfacesMap(routerId, port.getUuid().getValue());
            }
        }
    }

    private void handleNeutronPortUpdated(Port portoriginal, Port portupdate) {
        if (portoriginal.getFixedIps() == null || portoriginal.getFixedIps().isEmpty()) {
            handleNeutronPortCreated(portupdate);
            return;
        }
        LOG.debug("Add port to subnet");
        // add port FixedIP to local Subnets DS
        Uuid vpnIdup = addPortToSubnets(portupdate);

        if (vpnIdup != null) {
            nvpnManager.createVpnInterface(vpnIdup, portupdate);
            Uuid routerId = NeutronvpnUtils.getVpnMap(broker, vpnIdup).getRouterId();
            if(routerId != null) {
                nvpnManager.addToNeutronRouterInterfacesMap(routerId, portupdate.getUuid().getValue());
            }
        }

        // remove port FixedIP from local Subnets DS
        Uuid vpnIdor = removePortFromSubnets(portoriginal);

        if (vpnIdor != null) {
            nvpnManager.deleteVpnInterface(portoriginal);
            Uuid routerId = NeutronvpnUtils.getVpnMap(broker, vpnIdor).getRouterId();
            if(routerId != null) {
                nvpnManager.removeFromNeutronRouterInterfacesMap(routerId, portoriginal.getUuid().getValue());
            }
        }
    }

    private void handlePortSecurityUpdated(Port portOriginal, Port portUpdated) {
        Boolean origSecurityEnabled = NeutronvpnUtils.getPortSecurityEnabled(portOriginal);
        Boolean updatedSecurityEnabled = NeutronvpnUtils.getPortSecurityEnabled(portUpdated);
        String interfaceName = portUpdated.getUuid().getValue();
        Interface portInterface = NeutronvpnUtils.getOfPortInterface(broker, portUpdated);
        if (portInterface != null) {
            InterfaceAclBuilder interfaceAclBuilder = null;
            if (origSecurityEnabled != updatedSecurityEnabled) {
                interfaceAclBuilder = new InterfaceAclBuilder();
                interfaceAclBuilder.setPortSecurityEnabled(updatedSecurityEnabled);
                if (updatedSecurityEnabled) {
                    // Handle security group enabled
                    List<Uuid> securityGroups = portUpdated.getSecurityGroups();
                    if (securityGroups != null) {
                        interfaceAclBuilder.setSecurityGroups(securityGroups);
                    }
                    List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs> portAllowedAddressPairs =
                            portUpdated.getAllowedAddressPairs();
                    if (portAllowedAddressPairs != null) {
                        interfaceAclBuilder
                                .setAllowedAddressPairs(getAllowedAddressPairsForAclService(portAllowedAddressPairs));
                    }
                } else {
                    // Handle security group disabled
                    interfaceAclBuilder.setSecurityGroups(Lists.newArrayList());
                    interfaceAclBuilder.setAllowedAddressPairs(Lists.newArrayList());
                }
            } else {
                if (updatedSecurityEnabled) {
                    // handle SG add/delete delta
                    InterfaceAcl interfaceAcl = portInterface.getAugmentation(InterfaceAcl.class);
                    interfaceAclBuilder = new InterfaceAclBuilder(interfaceAcl);
                    List<Uuid> addedGroups = getsecurityGroupChanged(portUpdated.getSecurityGroups(),
                            portOriginal.getSecurityGroups());
                    List<Uuid> deletedGroups = getsecurityGroupChanged(portOriginal.getSecurityGroups(),
                            portUpdated.getSecurityGroups());
                    List<Uuid> securityGroups = interfaceAcl.getSecurityGroups();
                    List<Uuid> updatedSecurityGroups =
                            (securityGroups != null) ? new ArrayList<>(securityGroups) : new ArrayList<>();
                    if (addedGroups != null) {
                        updatedSecurityGroups.addAll(addedGroups);
                    }
                    if (deletedGroups != null) {
                        updatedSecurityGroups.removeAll(deletedGroups);
                    }
                    interfaceAclBuilder.setSecurityGroups(updatedSecurityGroups);

                    List<AllowedAddressPairs> addedAllowedAddressPairs = getAllowedAddressPairsChanged(
                            portUpdated.getAllowedAddressPairs(), portOriginal.getAllowedAddressPairs());
                    List<AllowedAddressPairs> deletedAllowedAddressPairs = getAllowedAddressPairsChanged(
                            portOriginal.getAllowedAddressPairs(), portUpdated.getAllowedAddressPairs());
                    List<AllowedAddressPairs> allowedAddressPairs = interfaceAcl.getAllowedAddressPairs();
                    List<AllowedAddressPairs> updatedAllowedAddressPairs =
                            (allowedAddressPairs != null) ? new ArrayList<>(allowedAddressPairs) : new ArrayList<>();
                    if (addedAllowedAddressPairs != null) {
                        updatedAllowedAddressPairs.addAll(addedAllowedAddressPairs);
                    }
                    if (deletedAllowedAddressPairs != null) {
                        updatedAllowedAddressPairs.removeAll(deletedAllowedAddressPairs);
                    }
                    interfaceAclBuilder.setAllowedAddressPairs(updatedAllowedAddressPairs);
                }
            }

            if (interfaceAclBuilder != null) {
                InterfaceBuilder builder = new InterfaceBuilder(portInterface).addAugmentation(InterfaceAcl.class,
                        interfaceAclBuilder.build());
                InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(interfaceName);
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, builder.build());
            }
        } else {
            LOG.error("Interface {} is not present", interfaceName);
        }
    }

    private List<Uuid> getsecurityGroupChanged(List<Uuid> port1SecurityGroups, List<Uuid> port2SecurityGroups) {
        if (port1SecurityGroups == null) {
            return null;
        }

        if (port2SecurityGroups == null) {
            return port1SecurityGroups;
        }

        List<Uuid> list1 = new ArrayList<>(port1SecurityGroups);
        List<Uuid> list2 = new ArrayList<>(port2SecurityGroups);
        for (Iterator<Uuid> iterator = list1.iterator(); iterator.hasNext();) {
            Uuid securityGroup1 = iterator.next();
            for (Uuid securityGroup2 : list2) {
                if (securityGroup1.getValue().equals(securityGroup2.getValue())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return list1;
    }

    private List<AllowedAddressPairs> getAllowedAddressPairsChanged(
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs> port1AllowedAddressPairs,
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs> port2AllowedAddressPairs) {
        if (port1AllowedAddressPairs == null) {
            return null;
        }

        if (port2AllowedAddressPairs == null) {
            return getAllowedAddressPairsForAclService(port1AllowedAddressPairs);
        }

        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs> list1 =
                new ArrayList<>(port1AllowedAddressPairs);
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs> list2 =
                new ArrayList<>(port2AllowedAddressPairs);
        for (Iterator<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs> iterator =
                list1.iterator(); iterator.hasNext();) {
            org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs allowedAddressPair1 =
                    iterator.next();
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs allowedAddressPair2 : list2) {
                if (allowedAddressPair1.getKey().equals(allowedAddressPair2.getKey())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return getAllowedAddressPairsForAclService(list1);
    }

    private List<AllowedAddressPairs> getAllowedAddressPairsForAclService(
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs> portAllowedAddressPairs) {
        List<AllowedAddressPairs> aclAllowedAddressPairs = new ArrayList<>();
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs portAllowedAddressPair : portAllowedAddressPairs) {
            AllowedAddressPairsBuilder aclAllowedAdressPairBuilder = new AllowedAddressPairsBuilder();
            org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress ipAddress =
                    portAllowedAddressPair.getIpAddress();
            if (ipAddress != null && ipAddress.getValue() != null) {
                if (ipAddress.getIpPrefix() != null) {
                    aclAllowedAdressPairBuilder.setIpAddress(new IpPrefixOrAddress(ipAddress.getIpPrefix()));
                } else {
                    aclAllowedAdressPairBuilder.setIpAddress(new IpPrefixOrAddress(ipAddress.getIpAddress()));
                }
            }

            aclAllowedAdressPairBuilder.setMacAddress(portAllowedAddressPair.getMacAddress());
            aclAllowedAddressPairs.add(aclAllowedAdressPairBuilder.build());
        }
        return aclAllowedAddressPairs;
    }

    private String createOfPortInterface(Port port) {
        Interface inf = createInterface(port);
        String infName = inf.getName();

        LOG.debug("Creating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (!optionalInf.isPresent()) {
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
            } else {
                LOG.error("Interface {} is already present", infName);
            }
        } catch (Exception e) {
            LOG.error("failed to create interface {} due to the exception {} ", infName, e.getMessage());
        }
        return infName;
    }

    private Interface createInterface(Port port) {

        String parentRefName = NeutronvpnUtils.getVifPortName(port);
        String interfaceName = port.getUuid().getValue();
        IfL2vlan.L2vlanMode l2VlanMode = IfL2vlan.L2vlanMode.Trunk;
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder();
        IfL2vlanBuilder ifL2vlanBuilder = new IfL2vlanBuilder();
        ifL2vlanBuilder.setL2vlanMode(l2VlanMode);
        if (parentRefName != null) {
            ParentRefsBuilder parentRefsBuilder = new ParentRefsBuilder().setParentInterface(parentRefName);
            interfaceBuilder.addAugmentation(ParentRefs.class, parentRefsBuilder.build());
        }

        if (NeutronvpnUtils.isPortSecurityEnabled(port)) {
            InterfaceAclBuilder interfaceAclBuilder = new InterfaceAclBuilder();
            interfaceAclBuilder.setPortSecurityEnabled(true);
            List<Uuid> securityGroups = port.getSecurityGroups();
            if (securityGroups != null) {
                interfaceAclBuilder.setSecurityGroups(securityGroups);
            }

            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs> portAllowedAddressPairs =
                    port.getAllowedAddressPairs();
            if (portAllowedAddressPairs != null) {
                interfaceAclBuilder
                        .setAllowedAddressPairs(getAllowedAddressPairsForAclService(portAllowedAddressPairs));
            }
            interfaceBuilder.addAugmentation(InterfaceAcl.class, interfaceAclBuilder.build());
        }

        interfaceBuilder.setEnabled(true).setName(interfaceName).setType(L2vlan.class)
                .addAugmentation(IfL2vlan.class, ifL2vlanBuilder.build());
        return interfaceBuilder.build();
    }

    private void deleteOfPortInterface(Port port) {
        String name = port.getUuid().getValue();
        LOG.debug("Removing OFPort Interface {}", name);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(name);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (optionalInf.isPresent()) {
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, interfaceIdentifier);
            } else {
                LOG.error("Interface {} is not present", name);
            }
        } catch (Exception e) {
            LOG.error("Failed to delete interface {} due to the exception {}", name, e.getMessage());
        }
    }

    private Interface updateInterface(Port original, Port update) {
        String parentRefName = NeutronvpnUtils.getVifPortName(update);
        String interfaceName = original.getUuid().getValue();
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder();

        if (parentRefName != null) {
            ParentRefsBuilder parentRefsBuilder = new ParentRefsBuilder().setParentInterface(parentRefName);
            interfaceBuilder.addAugmentation(ParentRefs.class, parentRefsBuilder.build());
        }

        interfaceBuilder.setName(interfaceName);
        return interfaceBuilder.build();
    }

    private String updateOfPortInterface(Port original, Port updated) {
        Interface inf = updateInterface(original, updated);
        String infName = inf.getName();

        LOG.debug("Updating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (optionalInf.isPresent()) {
                MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
            } else {
                LOG.error("Interface {} doesn't exist", infName);
            }
        } catch (Exception e) {
            LOG.error("failed to update interface {} due to the exception {} ", infName, e);
        }

        return infName;
    }

    private void createElanInterface(Port port, String name) {
        String elanInstanceName = port.getNetworkId().getValue();
        List<PhysAddress> physAddresses = new ArrayList<>();
        physAddresses.add(new PhysAddress(port.getMacAddress().getValue()));

        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(name).setStaticMacEntries(physAddresses).setKey(new ElanInterfaceKey(name)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, elanInterface);
        LOG.debug("Creating new ELan Interface {}", elanInterface);
    }

    // adds port to subnet list and creates vpnInterface
    private Uuid addPortToSubnets(Port port) {
        Uuid subnetId = null;
        Uuid vpnId = null;
        Subnetmap subnetmap = null;
        String infName = port.getUuid().getValue();
        boolean isLockAcquired = false;
        String lockName = port.getUuid().getValue();

        // find the subnet to which this port is associated
        if(port.getFixedIps() == null || port.getFixedIps().isEmpty()) {
            LOG.debug("port {} doesn't have ip", port.getName());
            return null;
        }
        FixedIps ip = port.getFixedIps().get(0);
        String ipValue = (ip.getIpAddress().getIpv4Address() != null ) ? ip.getIpAddress().getIpv4Address().getValue() :
            ip.getIpAddress().getIpv6Address().getValue();
        InstanceIdentifier id = NeutronvpnUtils.buildFixedIpToPortNameIdentifier(ipValue);
        PortFixedipToPortNameBuilder builder = new PortFixedipToPortNameBuilder().setPortFixedip(ipValue)
                .setPortName(infName);
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, builder.build());
        LOG.debug("fixedIp-name map for neutron port with fixedIp: {}, name: {} added to NeutronPortData DS",
                ipValue, infName);
        subnetId = ip.getSubnetId();
        subnetmap = nvpnManager.updateSubnetmapNodeWithPorts(subnetId, port.getUuid(), null);
        if (subnetmap != null) {
            vpnId = subnetmap.getVpnId();
        }
        if(vpnId != null) {
            try {
                isLockAcquired = NeutronvpnUtils.lock(lockManager, lockName);
                checkAndPublishPortAddNotification(subnetmap.getSubnetIp(), subnetId, port.getUuid());
                LOG.debug("Port added to subnet notification sent");
            } catch (Exception e) {
                LOG.error("Port added to subnet notification failed", e);
            } finally {
                if (isLockAcquired) {
                    NeutronvpnUtils.unlock(lockManager, lockName);
                }
            }
        }
        return vpnId;
    }

    private Uuid removePortFromSubnets(Port port) {
        Uuid subnetId = null;
        Uuid vpnId = null;
        Subnetmap subnetmap = null;
        boolean isLockAcquired = false;
        String lockName = port.getUuid().getValue();

        // find the subnet to which this port is associated
        FixedIps ip = port.getFixedIps().get(0);
        String ipValue = ip.getIpAddress().getIpv4Address().getValue();
        InstanceIdentifier id = NeutronvpnUtils.buildFixedIpToPortNameIdentifier(ipValue);
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, id);
        LOG.debug("fixedIp-name map for neutron port with fixedIp: {} deleted from NeutronPortData DS", ipValue);
        subnetId = ip.getSubnetId();
        subnetmap = nvpnManager.removePortsFromSubnetmapNode(subnetId, port.getUuid(), null);
        if (subnetmap != null) {
            vpnId = subnetmap.getVpnId();
        }
        if(vpnId != null) {
            try {
                isLockAcquired = NeutronvpnUtils.lock(lockManager, lockName);
                checkAndPublishPortRemoveNotification(subnetmap.getSubnetIp(), subnetId, port.getUuid());
                LOG.debug("Port removed from subnet notification sent");
            } catch (Exception e) {
                LOG.error("Port removed from subnet notification failed", e);
            } finally {
                if (isLockAcquired) {
                    NeutronvpnUtils.unlock(lockManager, lockName);
                }
            }
        }
        return vpnId;
    }

    private void checkAndPublishPortAddNotification(String subnetIp, Uuid subnetId, Uuid portId)throws InterruptedException{
        PortAddedToSubnetBuilder builder = new PortAddedToSubnetBuilder();

        LOG.info("publish notification called");

        builder.setSubnetIp(subnetIp);
        builder.setSubnetId(subnetId);
        builder.setPortId(portId);

        notificationPublishService.putNotification(builder.build());
    }

    private void checkAndPublishPortRemoveNotification(String subnetIp, Uuid subnetId, Uuid portId)throws InterruptedException{
        PortRemovedFromSubnetBuilder builder = new PortRemovedFromSubnetBuilder();

        LOG.info("publish notification called");

        builder.setPortId(portId);
        builder.setSubnetIp(subnetIp);
        builder.setSubnetId(subnetId);

        notificationPublishService.putNotification(builder.build());
    }
}

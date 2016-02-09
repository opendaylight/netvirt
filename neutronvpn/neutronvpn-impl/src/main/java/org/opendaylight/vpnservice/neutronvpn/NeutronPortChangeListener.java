/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.neutronvpn;


import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.neutron.port.data
        .PortFixedipToPortNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.neutron.port.data
        .PortNameToPortUuidBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class NeutronPortChangeListener extends AbstractDataChangeListener<Port> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private NeutronvpnManager nvpnManager;


    public NeutronPortChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr) {
        super(Port.class);
        broker = db;
        nvpnManager = nVpnMgr;
        registerListener(db);
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
        handleNeutronPortCreated(input);

    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing Port : key: " + identifier + ", value=" + input);
        }
        handleNeutronPortDeleted(input);

    }

    @Override
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Port : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }
        List<FixedIps> oldIPs = (original.getFixedIps() != null) ? original.getFixedIps() : new ArrayList<FixedIps>();
        List<FixedIps> newIPs = (update.getFixedIps() != null) ? update.getFixedIps() : new ArrayList<FixedIps>();

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
    }

    private void handleNeutronPortCreated(Port port) {
        LOG.info("Of-port-interface creation");
        int portVlanId = NeutronvpnUtils.getVlanFromNeutronPort(port);
        // Create of-port interface for this neutron port
        createOfPortInterface(port, portVlanId);
        LOG.debug("Creating ELAN Interface");
        createElanInterface(port, portVlanId);
        LOG.debug("Add port to subnet");
        // add port to local Subnets DS
        Uuid vpnId = addPortToSubnets(port);

        if (vpnId != null) {
            // create vpn-interface on this neutron port
            LOG.debug("Adding VPN Interface");
            nvpnManager.createVpnInterface(vpnId, port);
        }
    }

    private void handleNeutronPortDeleted(Port port) {
        LOG.debug("Of-port-interface removal");
        LOG.debug("Remove port from subnet");
        // remove port from local Subnets DS
        Uuid vpnId = removePortFromSubnets(port);

        if (vpnId != null) {
            // remove vpn-interface for this neutron port
            LOG.debug("removing VPN Interface");
            nvpnManager.deleteVpnInterface(port);
        }
        int portVlanId = NeutronvpnUtils.getVlanFromNeutronPort(port);
        // Remove of-port interface for this neutron port
        // ELAN interface is also implicitly deleted as part of this operation
        deleteOfPortInterface(port, portVlanId);

    }

    private void handleNeutronPortUpdated(Port portoriginal, Port portupdate) {
        LOG.debug("Add port to subnet");
        // add port FixedIPs to local Subnets DS
        Uuid vpnIdup = addPortToSubnets(portupdate);

        if (vpnIdup != null) {
            nvpnManager.createVpnInterface(vpnIdup, portupdate);
        }

        // remove port FixedIPs from local Subnets DS
        Uuid vpnIdor = removePortFromSubnets(portoriginal);

        if (vpnIdor != null) {
            nvpnManager.deleteVpnInterface(portoriginal);
        }
    }

    private void createOfPortInterface(Port port, int portVlanId) {
        String name = NeutronvpnUtils.uuidToTapPortName(port.getUuid());
        //String ifname = new StringBuilder(name).append(":").append(Integer.toString(portVlanId)).toString();
        //Network network = NeutronvpnUtils.getNeutronNetwork(broker, port.getNetworkId());
        //Boolean isVlanTransparent = network.isVlanTransparent();

        LOG.debug("Creating OFPort Interface {}", name);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(name);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (!optionalInf.isPresent()) {
                // handle these for trunkport extensions : portVlanId, isVlanTransparent
                IfL2vlan l2vlan = new IfL2vlanBuilder().setL2vlanMode(IfL2vlan.L2vlanMode.Trunk).build();
                ParentRefs parentRefs = new ParentRefsBuilder().setParentInterface(name).build();
                Interface inf = new InterfaceBuilder().setEnabled(true).setName(name).setType(L2vlan.class).
                        addAugmentation(IfL2vlan.class, l2vlan).addAugmentation(ParentRefs.class, parentRefs).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
            } else {
                LOG.error("Interface {} is already present", name);
            }
        } catch (Exception e) {
            LOG.error("failed to create interface {} due to the exception {} ", name, e.getMessage());
        }

        InstanceIdentifier portIdentifier = NeutronvpnUtils.buildPortNameToPortUuidIdentifier(name);
        PortNameToPortUuidBuilder builder = new PortNameToPortUuidBuilder().setPortName(name).setPortId(port.getUuid());
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, portIdentifier, builder.build());
        LOG.debug("name-uuid map for port with name: {}, uuid: {} added to NeutronPortData DS", name, port.getUuid());
    }

    private void deleteOfPortInterface(Port port, int portVlanId) {
        String name = NeutronvpnUtils.uuidToTapPortName(port.getUuid());
        //String ifname = new StringBuilder(name).append(":").append(Integer.toString(portVlanId)).toString();
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

        InstanceIdentifier portIdentifier = NeutronvpnUtils.buildPortNameToPortUuidIdentifier(name);
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, portIdentifier);
        LOG.debug("name-uuid map for port with name: {}, uuid: {} deleted from NeutronPortData DS", name, port
                .getUuid());
    }

    private void createElanInterface(Port port, int portVlanId) {
        String name = NeutronvpnUtils.uuidToTapPortName(port.getUuid());
        String elanInstanceName = port.getNetworkId().getValue();
        List<PhysAddress> physAddresses = new ArrayList<>();
        physAddresses.add(new PhysAddress(port.getMacAddress()));

        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(name).setStaticMacEntries(physAddresses).
                        setKey(new ElanInterfaceKey(name)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, elanInterface);
        LOG.debug("Creating new ELan Interface {}", elanInterface);
    }

    // adds port to subnet list and creates vpnInterface
    private Uuid addPortToSubnets(Port port) {
        Uuid subnetId = null;
        Uuid vpnId = null;
        String name = NeutronvpnUtils.uuidToTapPortName(port.getUuid());

        // find all subnets to which this port is associated
        List<FixedIps> ips = port.getFixedIps();
        for (FixedIps ip : ips) {
            String ipValue = ip.getIpAddress().getIpv4Address().getValue();

            InstanceIdentifier id = NeutronvpnUtils.buildFixedIpToPortNameIdentifier(ipValue);
            PortFixedipToPortNameBuilder builder = new PortFixedipToPortNameBuilder().setPortFixedip(ipValue)
                    .setPortName(name);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, builder.build());
            LOG.debug("fixedIp-name map for neutron port with fixedIp: {}, name: {} added to NeutronPortData DS",
                    ipValue, name);

            subnetId = ip.getSubnetId();
            Subnetmap subnetmap = nvpnManager.updateSubnetNode(subnetId, null, null, null, null, port.getUuid());
            if (vpnId == null && subnetmap != null) {
                vpnId = subnetmap.getVpnId();
            }
        }
        return vpnId;
    }

    private Uuid removePortFromSubnets(Port port) {
        Uuid subnetId = null;
        Uuid vpnId = null;

        // find all Subnets to which this port is associated
        List<FixedIps> ips = port.getFixedIps();
        for (FixedIps ip : ips) {
            String ipValue = ip.getIpAddress().getIpv4Address().getValue();

            InstanceIdentifier id = NeutronvpnUtils.buildFixedIpToPortNameIdentifier(ipValue);
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, id);
            LOG.debug("fixedIp-name map for neutron port with fixedIp: {} deleted from NeutronPortData DS", ipValue);

            subnetId = ip.getSubnetId();
            Subnetmap subnetmap = nvpnManager.removeFromSubnetNode(subnetId, null, null, null, port.getUuid());
            if (vpnId == null && subnetmap != null) {
                vpnId = subnetmap.getVpnId();
            }
        }
        return vpnId;
    }
}

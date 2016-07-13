/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.InterfaceNameMacAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddressKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;

public class DhcpInterfaceEventListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DhcpInterfaceEventListener.class);
    private DhcpManager dhcpManager;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private DataBroker dataBroker;
    private static final FutureCallback<Void> DEFAULT_CALLBACK = new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
            logger.debug("Success in Datastore write operation");
        }

        @Override
        public void onFailure(Throwable error) {
            logger.error("Error in Datastore write operation", error);
        }
    };
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;

    public DhcpInterfaceEventListener(DhcpManager dhcpManager, DataBroker dataBroker, DhcpExternalTunnelManager dhcpExternalTunnelManager) {
        super(Interface.class);
        this.dhcpManager = dhcpManager;
        this.dataBroker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        registerListener();
    }

    private void registerListener() {
        try {
            listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                getWildCardPath(), DhcpInterfaceEventListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("DhcpInterfaceEventListener DataChange listener registration fail!", e);
            throw new IllegalStateException("DhcpInterfaceEventListener registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        logger.info("Interface Manager Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        List<String> ofportIds = del.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty()) {
            return;
        }
        String interfaceName = del.getName();
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                DhcpServiceUtils.getInterfaceFromConfigDS(interfaceName, dataBroker);
        if (iface != null) {
            IfTunnel tunnelInterface = iface.getAugmentation(IfTunnel.class);
            if (tunnelInterface != null && !tunnelInterface.isInternal()) {
                IpAddress tunnelIp = tunnelInterface.getTunnelDestination();
                List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(dataBroker);
                if (dpns.contains(dpId)) {
                    dhcpExternalTunnelManager.handleTunnelStateDown(tunnelIp, dpId);
                }
                return;
            }
        }
        logger.trace("Received remove DCN for interface {} dpId {}", interfaceName, dpId);
        unInstallDhcpEntries(interfaceName, dpId);
        dhcpManager.removeInterfaceCache(interfaceName);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
        if (update.getType() == null) {
            logger.trace("Interface type for interface {} is null", update);
            return;
        }
        if ((original.getOperStatus().getIntValue() ^ update.getOperStatus().getIntValue()) == 0) {
            logger.trace("Interface operstatus {} is same", update.getOperStatus());
            return;
        }
        List<String> ofportIds = update.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty()) {
            return;
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        String interfaceName = update.getName();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                DhcpServiceUtils.getInterfaceFromConfigDS(interfaceName, dataBroker);
        if (iface == null) {
            logger.trace("Interface {} is not present in the config DS", interfaceName);
            return;
        }
        if (Tunnel.class.equals(update.getType())) {
            IfTunnel tunnelInterface = iface.getAugmentation(IfTunnel.class);
            if (tunnelInterface != null && !tunnelInterface.isInternal()) {
                IpAddress tunnelIp = tunnelInterface.getTunnelDestination();
                List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(dataBroker);
                if (dpns.contains(dpId)) {
                    if (update.getOperStatus() == OperStatus.Down) {
                        dhcpExternalTunnelManager.handleTunnelStateDown(tunnelIp, dpId);
                    } else if (update.getOperStatus() == OperStatus.Up) {
                        dhcpExternalTunnelManager.handleTunnelStateUp(tunnelIp, dpId);
                    }
                }
            }
            return;
        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
        String interfaceName = add.getName();
        List<String> ofportIds = add.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty()) {
            return;
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        logger.trace("Received add DCN for interface {}, dpid {}", interfaceName, dpId);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                DhcpServiceUtils.getInterfaceFromConfigDS(add.getName(), dataBroker);
        if (iface != null) {
            IfTunnel tunnelInterface = iface.getAugmentation(IfTunnel.class);
            if (tunnelInterface != null && !tunnelInterface.isInternal()) {
                IpAddress tunnelIp = tunnelInterface.getTunnelDestination();
                List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(dataBroker);
                if (dpns.contains(dpId)) {
                    dhcpExternalTunnelManager.handleTunnelStateUp(tunnelIp, dpId);
                    dhcpManager.bindDhcpService(interfaceName, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL);
                }
                return;
            }
        }
        if (!dpId.equals(DHCPMConstants.INVALID_DPID)) {
            Port port = dhcpManager.getNeutronPort(interfaceName);
            Subnet subnet = dhcpManager.getNeutronSubnet(port);
            if (null != subnet && subnet.isEnableDhcp()) {
                logger.info("DhcpInterfaceEventListener add isEnableDhcp" + subnet.isEnableDhcp());
                installDhcpEntries(interfaceName, dpId);
                dhcpManager.updateInterfaceCache(interfaceName, new ImmutablePair<>(dpId,
                                                    add.getPhysAddress().getValue()));
            }
        }
    }

    private String getNeutronMacAddress(String interfaceName) {
        Port port = dhcpManager.getNeutronPort(interfaceName);
        if (port!=null) {
            logger.trace("Port found in neutron. Interface Name {}, port {}", interfaceName, port);
            return port.getMacAddress().getValue();
        }
        return null;
    }

    private void unInstallDhcpEntries(String interfaceName, BigInteger dpId) {
        String vmMacAddress = getAndRemoveVmMacAddress(interfaceName);
        dhcpManager.unbindDhcpService(interfaceName);
        dhcpManager.unInstallDhcpEntries(dpId, vmMacAddress);
    }

    private void installDhcpEntries(String interfaceName, BigInteger dpId) {
        String vmMacAddress = getAndUpdateVmMacAddress(interfaceName);
        dhcpManager.bindDhcpService(interfaceName, DHCPMConstants.DHCP_TABLE);
        dhcpManager.installDhcpEntries(dpId, vmMacAddress);
    }

    private String getAndUpdateVmMacAddress(String interfaceName) {
        InstanceIdentifier<InterfaceNameMacAddress> instanceIdentifier = InstanceIdentifier.builder(InterfaceNameMacAddresses.class).child(InterfaceNameMacAddress.class, new InterfaceNameMacAddressKey(interfaceName)).build();
        Optional<InterfaceNameMacAddress> existingEntry = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, instanceIdentifier);
        if (!existingEntry.isPresent()) {
            logger.trace("Entry for interface {} missing in InterfaceNameVmMacAddress map", interfaceName);
            String vmMacAddress = getNeutronMacAddress(interfaceName);
            if (vmMacAddress==null || vmMacAddress.isEmpty()) {
                return null;
            }
            logger.trace("Updating InterfaceNameVmMacAddress map with {}, {}", interfaceName,vmMacAddress);
            InterfaceNameMacAddress interfaceNameMacAddress = new InterfaceNameMacAddressBuilder().setKey(new InterfaceNameMacAddressKey(interfaceName)).setInterfaceName(interfaceName).setMacAddress(vmMacAddress).build();
            MDSALUtil.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL, instanceIdentifier, interfaceNameMacAddress);
            return vmMacAddress;
        }
        return existingEntry.get().getMacAddress();
    }

    private String getAndRemoveVmMacAddress(String interfaceName) {
        InstanceIdentifier<InterfaceNameMacAddress> instanceIdentifier = InstanceIdentifier.builder(InterfaceNameMacAddresses.class).child(InterfaceNameMacAddress.class, new InterfaceNameMacAddressKey(interfaceName)).build();
        Optional<InterfaceNameMacAddress> existingEntry = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, instanceIdentifier);
        if (existingEntry.isPresent()) {
            String vmMacAddress = existingEntry.get().getMacAddress();
            logger.trace("Entry for interface found in InterfaceNameVmMacAddress map {}, {}", interfaceName, vmMacAddress);
            MDSALDataStoreUtils.asyncRemove(dataBroker, LogicalDatastoreType.OPERATIONAL, instanceIdentifier, DEFAULT_CALLBACK);
            return vmMacAddress;
        }
        logger.trace("Entry for interface {} missing in InterfaceNameVmMacAddress map", interfaceName);
        return null;
    }
}

/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.dhcpservice;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.InterfaceNameMacAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddressKey;
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
        public void onSuccess(Void result) {
            logger.debug("Success in Datastore write operation");
        }

        public void onFailure(Throwable error) {
            logger.error("Error in Datastore write operation", error);
        }
    };

    public DhcpInterfaceEventListener(DhcpManager dhcpManager, DataBroker dataBroker) {
        super(Interface.class);
        this.dhcpManager = dhcpManager;
        this.dataBroker = dataBroker;
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
    protected void remove(InstanceIdentifier<Interface> identifier,
            Interface del) {
        String interfaceName = del.getName();
        List<String> ofportIds = del.getLowerLayerIf();
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        logger.trace("Received remove DCN for interface {} dpId {}", interfaceName, dpId);
        unInstallDhcpEntries(interfaceName, dpId);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
        String interfaceName = add.getName();
        List<String> ofportIds = add.getLowerLayerIf();
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        logger.trace("Received add DCN for interface {}, dpid{}", interfaceName, dpId);
        installDhcpEntries(interfaceName, dpId);
    }

    private String getNeutronMacAddress(String interfaceName) {
        Port port = dhcpManager.getNeutronPort(interfaceName);
        if (port!=null) {
            logger.trace("Port found in neutron. Interface Name {}, port {}", interfaceName, port);
            return port.getMacAddress();
        }
        logger.trace("Port not found in neutron. Interface Name {}, vlanId {}", interfaceName);
        return null;
    }

    private void unInstallDhcpEntries(String interfaceName, BigInteger dpId) {
        String vmMacAddress = getAndRemoveVmMacAddress(interfaceName);
        dhcpManager.unInstallDhcpEntries(dpId, vmMacAddress);
    }

    private void installDhcpEntries(String interfaceName, BigInteger dpId) {
        String vmMacAddress = getAndUpdateVmMacAddress(interfaceName);
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

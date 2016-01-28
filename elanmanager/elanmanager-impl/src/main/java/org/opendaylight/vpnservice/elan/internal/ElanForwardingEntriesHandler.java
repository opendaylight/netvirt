/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.internal;


import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.elan.utils.ElanUtils;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ElanForwardingEntriesHandler extends AbstractDataChangeListener<ElanInterface> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ElanForwardingEntriesHandler.class);
    private DataBroker broker;
    private ListenerRegistration<DataChangeListener> listenerRegistration;

    public ElanForwardingEntriesHandler(DataBroker db){
        super(ElanInterface.class);
        this.broker = db;
    }

    private InstanceIdentifier<?> getWildCardPath() {
        return InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class);
    }

    public void updateElanInterfaceForwardingTablesList(String elanInstanceName, String interfaceName, String existingInterfaceName, MacEntry mac) {
        if(existingInterfaceName == interfaceName) {
            logger.error(String.format("Static MAC address %s has already been added for the same ElanInstance %s on the same Logical Interface Port %s."
                    + " No operation will be done.", mac.getMacAddress().toString(), elanInstanceName, interfaceName));
        } else {
            logger.warn(String.format("Static MAC address %s had already been added for ElanInstance %s on Logical Interface Port %s. "
                    + "This would be considered as MAC movement scenario and old static mac will be removed and new static MAC will be added"
                    + "for ElanInstance %s on Logical Interface Port %s", mac.getMacAddress().toString(), elanInstanceName, interfaceName, elanInstanceName, interfaceName));
            //Update the  ElanInterface Forwarding Container & ElanForwarding Container
            deleteElanInterfaceForwardingTablesList(existingInterfaceName, mac);
            createElanInterfaceForwardingTablesList(interfaceName, mac);
            updateElanForwardingTablesList(elanInstanceName, interfaceName, mac);
        }

    }

    public void addElanInterfaceForwardingTableList(ElanInstance elanInstance, String interfaceName, PhysAddress physAddress) {
        MacEntry macEntry = new MacEntryBuilder().setIsStaticAddress(true).setMacAddress(physAddress).setInterface(interfaceName).setKey(new MacEntryKey(physAddress)).build();
        createElanForwardingTablesList(elanInstance.getElanInstanceName(), macEntry);
        createElanInterfaceForwardingTablesList(interfaceName, macEntry);
    }

    public void deleteElanInterfaceForwardingTablesList(String interfaceName, MacEntry mac) {
        InstanceIdentifier<MacEntry> existingMacEntryId = ElanUtils.getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, mac.getMacAddress());
        MacEntry existingInterfaceMacEntry = ElanUtils.getInterfaceMacEntriesOperationalDataPathFromId(existingMacEntryId);
        if(existingInterfaceMacEntry != null) {
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, existingMacEntryId);
        }
    }

    public void createElanInterfaceForwardingTablesList(String interfaceName, MacEntry mac) {
        InstanceIdentifier<MacEntry> existingMacEntryId = ElanUtils.getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, mac.getMacAddress());
        MacEntry existingInterfaceMacEntry = ElanUtils.getInterfaceMacEntriesOperationalDataPathFromId(existingMacEntryId);
        if(existingInterfaceMacEntry == null) {
            MacEntry macEntry = new MacEntryBuilder().setMacAddress(mac.getMacAddress()).setInterface(interfaceName).setIsStaticAddress(true).setKey(new MacEntryKey(mac.getMacAddress())).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, existingMacEntryId, macEntry);

        }
    }

    public void updateElanForwardingTablesList(String elanName, String interfaceName, MacEntry mac) {
        InstanceIdentifier<MacEntry> macEntryId =  ElanUtils.getMacEntryOperationalDataPath(elanName, mac.getMacAddress());
        MacEntry existingMacEntry = ElanUtils.getMacEntryFromElanMacId(macEntryId);
        if(existingMacEntry != null) {
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, macEntryId);
            MacEntry newMacEntry = new MacEntryBuilder().setInterface(interfaceName).setIsStaticAddress(true).setMacAddress(mac.getMacAddress()).setKey(new MacEntryKey(mac.getMacAddress())).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, macEntryId, newMacEntry);
        }
    }

    private void createElanForwardingTablesList(String elanName, MacEntry macEntry) {
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils.getMacEntryOperationalDataPath(elanName, macEntry.getMacAddress());
        Optional<MacEntry> existingMacEntry = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, macEntryId);
        if(!existingMacEntry.isPresent()) {
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, macEntryId, macEntry);
        }
    }

    public void deleteElanInterfaceForwardingEntries(ElanInstance elanInfo, InterfaceInfo interfaceInfo, MacEntry macEntry) {
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils.getMacEntryOperationalDataPath(elanInfo.getElanInstanceName(), macEntry.getMacAddress());
        ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, macEntryId);
        ElanUtils.deleteMacFlows(elanInfo, interfaceInfo, macEntry);
    }

    public void deleteElanInterfaceMacForwardingEntries(String interfaceName, PhysAddress physAddress) {
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils.getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
        ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, macEntryId);
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInterface> identifier, ElanInterface del) {

    }

    @Override
    protected void update(InstanceIdentifier<ElanInterface> identifier, ElanInterface original, ElanInterface update) {

    }

    @Override
    protected void add(InstanceIdentifier<ElanInterface> identifier, ElanInterface add) {

    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up Elan DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
    }
}

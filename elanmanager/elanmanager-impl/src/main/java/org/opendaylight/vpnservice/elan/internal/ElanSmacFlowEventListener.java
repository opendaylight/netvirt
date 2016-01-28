/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.elan.utils.ElanConstants;
import org.opendaylight.vpnservice.elan.utils.ElanUtils;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterface;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class ElanSmacFlowEventListener implements SalFlowListener {
    private final DataBroker broker;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;
    private static final Logger logger = LoggerFactory.getLogger(ElanSmacFlowEventListener.class);

    public ElanSmacFlowEventListener(DataBroker dataBroker) {
        broker = dataBroker;
    }
    private SalFlowService salFlowService;

    public SalFlowService getSalFlowService() {
        return this.salFlowService;
    }

    public void setSalFlowService(final SalFlowService salFlowService) {
        this.salFlowService = salFlowService;
    }
    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }


    public void setMdSalApiManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }
    @Override
    public void onFlowAdded(FlowAdded arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onFlowRemoved(FlowRemoved arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onFlowUpdated(FlowUpdated arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onNodeErrorNotification(NodeErrorNotification arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onNodeExperimenterErrorNotification(NodeExperimenterErrorNotification arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onSwitchFlowRemoved(SwitchFlowRemoved switchFlowRemoved) {
        short tableId = switchFlowRemoved.getTableId();
        if (tableId == ElanConstants.ELAN_SMAC_TABLE) {
            BigInteger metadata = switchFlowRemoved.getMatch().getMetadata().getMetadata();
            long elanTag = MetaDataUtil.getElanTagFromMetadata(metadata);
            ElanTagName elanTagInfo = ElanUtils.getElanInfoByElanTag(elanTag);
            if (elanTagInfo == null) {
                return;
            }
            String srcMacAddress = switchFlowRemoved.getMatch().getEthernetMatch()
                    .getEthernetSource().getAddress().getValue().toUpperCase();
            int portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
            if (portTag == 0) {
                logger.debug(String.format("Flow removed event on SMAC flow entry. But having port Tag as 0 "));
                return;
            }
            IfIndexInterface existingInterfaceInfo = ElanUtils.getInterfaceInfoByInterfaceTag(portTag);
            String interfaceName = existingInterfaceInfo.getInterfaceName();
            PhysAddress physAddress = new PhysAddress(srcMacAddress);
            if (interfaceName == null) {
                logger.error(String.format("LPort record not found for tag %d", portTag));
                return;
            }
            MacEntry macEntry = ElanUtils.getInterfaceMacEntriesOperationalDataPath(interfaceName, physAddress);
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            if(macEntry != null && interfaceInfo != null) {
                ElanUtils.deleteMacFlows(ElanUtils.getElanInstanceByName(elanTagInfo.getName()), interfaceInfo, macEntry);
            }
            InstanceIdentifier<MacEntry> macEntryId =  ElanUtils.getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, macEntryId);
        }
    }

}

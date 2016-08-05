/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclInterfaceListener extends AsyncDataTreeChangeListenerBase<Interface, AclInterfaceListener>
        implements ClusteredDataTreeChangeListener<Interface> {
    private static final Logger LOG = LoggerFactory.getLogger(AclInterfaceListener.class);

    private final AclServiceManager aclServiceManager;
    private final DataBroker dataBroker;

    public AclInterfaceListener(final AclServiceManager aclServiceManager, final DataBroker dataBroker) {
        super(Interface.class, AclInterfaceListener.class);
        this.aclServiceManager = aclServiceManager;
        this.dataBroker = dataBroker;
    }

    public void start() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier
                .create(Interfaces.class)
                .child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface port) {
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface portBefore, Interface portAfter) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, portAfter.getName());
        if (interfaceState == null || !interfaceState.getOperStatus().equals(org.opendaylight.yang.gen.v1.urn.ietf
                .params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Up)) {
            return;
        }

        InterfaceAcl aclInPortAfter = portAfter.getAugmentation(InterfaceAcl.class);
        InterfaceAcl aclInPortBefore = portBefore.getAugmentation(InterfaceAcl.class);
        if ((aclInPortAfter != null && aclInPortAfter.isPortSecurityEnabled())
                || (aclInPortBefore != null && aclInPortBefore.isPortSecurityEnabled())) {
            String interfaceId = portAfter.getName();
            AclInterface aclInterface = null;
            if (aclInPortBefore == null) {
                aclInterface = addAclInterfaceToCache(interfaceId, aclInPortAfter);
            } else {
                aclInterface = updateAclInterfaceInCache(interfaceId, aclInPortAfter);
            }
            AclInterface oldAclInterface = getOldAclInterfaceObject(aclInterface, aclInPortBefore);

            if (aclInterface.isPortSecurityEnabled()) {
                processAclUpdate(aclInterface, oldAclInterface.getSecurityGroups(), aclInterface.getSecurityGroups());
            }
            aclServiceManager.notify(aclInterface, oldAclInterface, AclServiceManager.Action.UPDATE);
        }
    }

    private AclInterface updateAclInterfaceInCache(String interfaceId, InterfaceAcl aclInPortAfter) {
        AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(interfaceId);
        if (aclInterface != null) {
            boolean portSecurityEnabled = aclInPortAfter.isPortSecurityEnabled();
            aclInterface.setPortSecurityEnabled(portSecurityEnabled);
            if (portSecurityEnabled) {
                aclInterface.setSecurityGroups(aclInPortAfter.getSecurityGroups());
                aclInterface.setAllowedAddressPairs(aclInPortAfter.getAllowedAddressPairs());
            }
        } else {
            // Control should not come here
            LOG.error("Unable to find Acl Interface details for {}", interfaceId);
        }
        return aclInterface;
    }

    private AclInterface getOldAclInterfaceObject(AclInterface aclInterface, InterfaceAcl aclInPortBefore) {
        AclInterface oldAclInterface = new AclInterface();
        if (aclInPortBefore == null) {
            oldAclInterface.setPortSecurityEnabled(false);
        } else {
            oldAclInterface.setInterfaceId(aclInterface.getInterfaceId());
            oldAclInterface.setDpId(aclInterface.getDpId());
            oldAclInterface.setLPortTag(aclInterface.getLPortTag());

            oldAclInterface.setPortSecurityEnabled(aclInPortBefore.isPortSecurityEnabled());
            oldAclInterface.setAllowedAddressPairs(aclInPortBefore.getAllowedAddressPairs());
            oldAclInterface.setSecurityGroups(aclInPortBefore.getSecurityGroups());
        }
        return oldAclInterface;
    }

    private void processAclUpdate(AclInterface port, List<Uuid> aclListBefore, List<Uuid> aclListAfter) {
        List<Uuid> addedAclList = AclServiceUtils.getUpdatedAclList(aclListAfter, aclListBefore);
        List<Uuid> deletedAclList = AclServiceUtils.getUpdatedAclList(aclListBefore, aclListAfter);
        if (addedAclList != null && !addedAclList.isEmpty()) {
            AclDataUtil.addAclInterfaceMap(addedAclList, port);
        }
        if (deletedAclList != null && !deletedAclList.isEmpty()) {
            AclDataUtil.removeAclInterfaceMap(deletedAclList, port);
        }
    }


    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface port) {
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort != null && aclInPort.isPortSecurityEnabled()) {
            addAclInterfaceToCache(port.getName(), aclInPort);
        }
    }

    private AclInterface addAclInterfaceToCache(String interfaceId, InterfaceAcl aclInPort) {
        AclInterface aclInterface = new AclInterface();
        aclInterface.setInterfaceId(interfaceId);
        aclInterface.setPortSecurityEnabled(aclInPort.isPortSecurityEnabled());
        aclInterface.setSecurityGroups(aclInPort.getSecurityGroups());
        aclInterface.setAllowedAddressPairs(aclInPort.getAllowedAddressPairs());
        AclInterfaceCacheUtil.addAclInterfaceToCache(interfaceId, aclInterface);
        return aclInterface;
    }

    @Override
    protected AclInterfaceListener getDataTreeChangeListener() {
        return this;
    }
}

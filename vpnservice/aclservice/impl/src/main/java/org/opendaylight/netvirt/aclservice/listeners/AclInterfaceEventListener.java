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
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.AclServiceUtils;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class AclInterfaceEventListener extends AsyncDataTreeChangeListenerBase<Interface, AclInterfaceEventListener>
        implements ClusteredDataTreeChangeListener<Interface> {

    private AclServiceManager aclServiceManager;
    private DataBroker dataBroker;

    public AclInterfaceEventListener(final AclServiceManager aclServiceManager, final DataBroker dataBroker) {
        super(Interface.class, AclInterfaceEventListener.class);
        this.aclServiceManager = aclServiceManager;
        this.dataBroker = dataBroker;
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
        if (AclServiceUtils.isPortSecurityEnabled(portAfter)) {
            processAclUpdate(portBefore, portAfter);
        }
        aclServiceManager.notify(portAfter, AclServiceManager.Action.UPDATE, portBefore);
    }

    private void processAclUpdate(Interface portBefore, Interface portAfter) {
        List<Uuid> addedAclList = AclServiceUtils.getUpdatedAclList(portAfter, portBefore);
        List<Uuid> deletedAclList = AclServiceUtils.getUpdatedAclList(portBefore, portAfter);
        if (addedAclList != null && !addedAclList.isEmpty()) {
            AclDataUtil.addAclInterfaceMap(addedAclList, portAfter);
        }
        if (deletedAclList != null && !deletedAclList.isEmpty()) {
            AclDataUtil.removeAclInterfaceMap(deletedAclList, portAfter);
        }
    }


    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface dataObjectModification) {

    }

    @Override
    protected AclInterfaceEventListener getDataTreeChangeListener() {
        return this;
    }

}

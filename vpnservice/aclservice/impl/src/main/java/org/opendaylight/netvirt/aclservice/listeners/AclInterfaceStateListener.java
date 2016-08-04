/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclInterfaceStateListener extends AsyncDataTreeChangeListenerBase<Interface,
        AclInterfaceStateListener> implements ClusteredDataTreeChangeListener<Interface>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AclInterfaceStateListener.class);

    /** Our registration. */
    public static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));
    public static final String EXTERNAL_ID_INTERFACE_ID = "iface-id";
    public final AclServiceManager aclServiceManger;

    /**
     * Initialize the member variables.
     * @param aclServiceManger the AclServiceManager instance.
     */
    public AclInterfaceStateListener(AclServiceManager aclServiceManger) {
        super(Interface.class, AclInterfaceStateListener.class);
        this.aclServiceManger = aclServiceManger;
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface dataObjectModification) {
        String interfaceId = dataObjectModification.getName();
        AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(interfaceId);
        if (aclInterface != null && aclInterface.getPortSecurityEnabled() != null
                && aclInterface.isPortSecurityEnabled()) {
            aclServiceManger.notify(aclInterface, null, Action.REMOVE);
            List<Uuid> aclList = aclInterface.getSecurityGroups();
            if (aclList != null) {
                AclDataUtil.removeAclInterfaceMap(aclList, aclInterface);
            }
            AclInterfaceCacheUtil.removeAclInterfaceFromCache(interfaceId);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface dataObjectModificationBefore,
                          Interface dataObjectModificationAfter) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface dataObjectModification) {
        AclInterface aclInterface = updateAclInterfaceCache(dataObjectModification);
        if (aclInterface != null && aclInterface.getPortSecurityEnabled() != null
                && aclInterface.isPortSecurityEnabled()) {
            List<Uuid> aclList = aclInterface.getSecurityGroups();
            if (aclList != null) {
                AclDataUtil.addAclInterfaceMap(aclList, aclInterface);
            }
            aclServiceManger.notify(aclInterface, null, Action.ADD);
        }
    }

    @Override
    protected AclInterfaceStateListener getDataTreeChangeListener() {
        return AclInterfaceStateListener.this;
    }

    private AclInterface updateAclInterfaceCache(Interface dataObjectModification) {
        String interfaceId = dataObjectModification.getName();
        AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(interfaceId);
        if (aclInterface == null) {
            aclInterface = new AclInterface();
            AclInterfaceCacheUtil.addAclInterfaceToCache(interfaceId, aclInterface);
        }
        aclInterface.setDpId(AclServiceUtils.getDpIdFromIterfaceState(dataObjectModification));
        aclInterface.setLPortTag(dataObjectModification.getIfIndex());
        return aclInterface;
    }
}

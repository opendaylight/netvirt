/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanInterfaceStateChangeListener
        extends AsyncDataTreeChangeListenerBase<Interface, ElanInterfaceStateChangeListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceStateChangeListener.class);

    private final DataBroker broker;
    private final ElanInterfaceManager elanInterfaceManager;
    private final IInterfaceManager interfaceManager;
    private final IElanService elanService;

    public ElanInterfaceStateChangeListener(final DataBroker db, final ElanInterfaceManager ifManager,
                                            final IInterfaceManager interfaceManager,
                                            final IElanService elanService) {
        super(Interface.class, ElanInterfaceStateChangeListener.class);
        broker = db;
        elanInterfaceManager = ifManager;
        this.interfaceManager = interfaceManager;
        this.elanService = elanService;
    }

    @Override
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface delIf) {
        LOG.trace("Received interface {} Down event", delIf);
        String interfaceName = delIf.getName();
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        if (elanInterface == null) {
            LOG.debug("No Elan Interface is created for the interface:{} ", interfaceName);
            return;
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(delIf.getLowerLayerIf().get(0));
        BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        InterfaceInfo interfaceInfo = new InterfaceInfo(dpId, nodeConnectorId.getValue());
        interfaceInfo.setInterfaceName(interfaceName);
        interfaceInfo.setInterfaceType(InterfaceInfo.InterfaceType.VLAN_INTERFACE);
        interfaceInfo.setInterfaceTag(delIf.getIfIndex());
        String elanInstanceName = elanInterface.getElanInstanceName();
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanInstanceName);
        if (elanInstance == null) {
            LOG.debug("No Elan instance is available for the interface:{} ", interfaceName);
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InterfaceRemoveWorkerOnElan removeWorker = new InterfaceRemoveWorkerOnElan(elanInstanceName, elanInstance,
                interfaceName, interfaceInfo, true, elanInterfaceManager);
        coordinator.enqueueJob(elanInstanceName, removeWorker, ElanConstants.JOB_MAX_RETRIES);

        // trigger deletion of vlan provider intf if this is the last VM on this DPN in the ELAN
        if (!elanInstance.isExternal() && ElanUtils.isVlan(elanInstance)) {
            coordinator.enqueueJob(dpId.toString(), () -> {
                DpnInterfaces dpnInterfaces = elanService.getElanDpnInterfaces(elanInstanceName, dpId);
                List<String> interfaces = dpnInterfaces.getInterfaces();
                int count = 0;
                for (String intf : interfaces) {
                    if (!interfaceManager.isExternalInterface(intf)) {
                        count++;
                    }
                }
                // this VM intf is the last intf on the DPN
                if (count == 1) {
                    LOG.debug("deleting vlan prv intf for elan {}, dpn {}", elanInstanceName, dpId);
                    elanService.deleteExternalElanNetwork(elanInstance, dpId);
                }
                return Collections.emptyList();
            });
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        LOG.trace("Operation Interface update event - Old: {}, New: {}", original, update);
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("Received interface {} up event", intrf);
        String interfaceName =  intrf.getName();
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        if (elanInterface == null) {
            return;
        }
        InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils
                .getElanInterfaceConfigurationDataPathId(interfaceName);
        elanInterfaceManager.add(elanInterfaceId, elanInterface);

        // create vlan member interface on patch port for internal VLAN provider network
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanInterface.getElanInstanceName());
        if (!elanInstance.isExternal() && ElanUtils.isVlan(elanInstance)) {
            BigInteger dpId = interfaceManager.getDpnForInterface(interfaceName);
            DataStoreJobCoordinator.getInstance().enqueueJob(dpId.toString(), () -> {
                LOG.debug("creating vlan member intf for network {}, dpn {}",
                        elanInstance.getPhysicalNetworkName(), dpId);
                elanService.createExternalElanNetwork(elanInstance, dpId);
                return Collections.emptyList();
            });
        }
    }

    @Override
    public void close() {

    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }


    @Override
    protected ElanInterfaceStateChangeListener getDataTreeChangeListener() {
        return this;
    }

}

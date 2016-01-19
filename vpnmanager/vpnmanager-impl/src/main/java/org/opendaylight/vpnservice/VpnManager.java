/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstanceToVpnIdBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnRouteList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstanceOpDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class VpnManager extends AbstractDataChangeListener<VpnInstance> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration, fibListenerRegistration, opListenerRegistration;
    private ConcurrentMap<String, Runnable> vpnOpMap = new ConcurrentHashMap<String, Runnable>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private IdManagerService idManager;
    private VpnInterfaceManager vpnInterfaceManager;
    private final FibEntriesListener fibListener;
    private final VpnInstanceOpListener vpnInstOpListener;

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                public void onSuccess(Void result) {
                    LOG.debug("Success in Datastore operation");
                }

                public void onFailure(Throwable error) {
                    LOG.error("Error in Datastore operation", error);
                };
            };

    /**
     * Listens for data change related to VPN Instance
     * Informs the BGP about VRF information
     *
     * @param db - dataBroker reference
     */
    public VpnManager(final DataBroker db, final IBgpManager bgpManager) {
        super(VpnInstance.class);
        broker = db;
        this.bgpManager = bgpManager;
        this.fibListener = new FibEntriesListener();
        this.vpnInstOpListener = new VpnInstanceOpListener();
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), VpnManager.this, DataChangeScope.SUBTREE);
            fibListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getFibEntryListenerPath(), fibListener, DataChangeScope.BASE);
            opListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getVpnInstanceOpListenerPath(), vpnInstOpListener, DataChangeScope.SUBTREE);

        } catch (final Exception e) {
            LOG.error("VPN Service DataChange listener registration fail !", e);
            throw new IllegalStateException("VPN Service registration Listener failed.", e);
        }
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    public void setVpnInterfaceManager(VpnInterfaceManager vpnInterfaceManager) {
        this.vpnInterfaceManager = vpnInterfaceManager;
    }

    private void waitForOpDataRemoval(String id) {
        //wait till DCN for removal of all DPNs in VPN arrivaes
        Runnable notifyTask = new VpnNotifyTask();
        synchronized (id.intern()) {
            vpnOpMap.put(id, notifyTask);
            synchronized (notifyTask) {
                try {
                    notifyTask.wait(VpnConstants.WAIT_TIME_IN_MILLISECONDS);
                } catch (InterruptedException e) {
                }
            }
        }

    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstance> identifier, VpnInstance del) {
        LOG.trace("Remove VPN event - Key: {}, value: {}", identifier, del);
        String vpnName = del.getVpnInstanceName();

        //Clean up vpn Interface
        InstanceIdentifier<VpnInterfaces> vpnInterfacesId = InstanceIdentifier.builder(VpnInterfaces.class).build();
        Optional<VpnInterfaces> optionalVpnInterfaces = read(LogicalDatastoreType.OPERATIONAL, vpnInterfacesId);

        if(optionalVpnInterfaces.isPresent()) {
            List<VpnInterface> vpnInterfaces = optionalVpnInterfaces.get().getVpnInterface();
            for(VpnInterface vpnInterface : vpnInterfaces) {
                if(vpnInterface.getVpnInstanceName().equals(vpnName)) {
                    LOG.debug("VpnInterface {} will be removed from VPN {}", vpnInterface.getName(), vpnName);
                    vpnInterfaceManager.remove(
                            VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()), vpnInterface);
                }
            }
        }
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance>
            vpnIdentifier = VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnName);
        delete(LogicalDatastoreType.CONFIGURATION, vpnIdentifier);

        VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME, vpnName);
        String rd = del.getIpv4Family().getRouteDistinguisher();

        if (rd !=null) {

            try {
                bgpManager.deleteVrf(rd);
            } catch (Exception e) {
                LOG.error("Exception when removing VRF from BGP", e);
            }
            waitForOpDataRemoval(rd);
            delete(LogicalDatastoreType.OPERATIONAL, VpnUtil.getVpnInstanceOpDataIdentifier(rd));
        } else {
            waitForOpDataRemoval(vpnName);
            delete(LogicalDatastoreType.OPERATIONAL, VpnUtil.getVpnInstanceOpDataIdentifier(vpnName));
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> identifier,
            VpnInstance original, VpnInstance update) {
        LOG.trace("Update event - Key: {}, value: {}", identifier, update);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstance> identifier,
            VpnInstance value) {
        LOG.trace("VPN Instance key: {}, value: {}", identifier, value);
        VpnAfConfig config = value.getIpv4Family();
        String rd = config.getRouteDistinguisher();

        long vpnId = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, value.getVpnInstanceName());
        LOG.trace("VPN instance to ID generated.");
        org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
            vpnInstanceToVpnId = VpnUtil.getVpnInstanceToVpnId(value.getVpnInstanceName(), vpnId,
                                                                    (rd != null) ? rd : value.getVpnInstanceName());

        syncWrite(LogicalDatastoreType.CONFIGURATION,
                   VpnUtil.getVpnInstanceToVpnIdIdentifier(value.getVpnInstanceName()),
                   vpnInstanceToVpnId, DEFAULT_CALLBACK);


        if(rd == null) {
            syncWrite(LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(value.getVpnInstanceName()),
                    VpnUtil.getVpnInstanceOpDataBuilder(value.getVpnInstanceName(), vpnId), DEFAULT_CALLBACK);

        } else {
            syncWrite(LogicalDatastoreType.OPERATIONAL,
                       VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                       VpnUtil.getVpnInstanceOpDataBuilder(rd, vpnId), DEFAULT_CALLBACK);

            List<VpnTarget> vpnTargetList = config.getVpnTargets().getVpnTarget();

            List<String> ertList = new ArrayList<String>();
            List<String> irtList = new ArrayList<String>();

            for (VpnTarget vpnTarget : vpnTargetList) {
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ExportExtcommunity) {
                    ertList.add(vpnTarget.getVrfRTValue());
                }
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ImportExtcommunity) {
                    irtList.add(vpnTarget.getVrfRTValue());
                }
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.Both) {
                    ertList.add(vpnTarget.getVrfRTValue());
                    irtList.add(vpnTarget.getVrfRTValue());
                }
            }

            try {
                bgpManager.addVrf(rd, irtList, ertList);
            } catch(Exception e) {
                LOG.error("Exception when adding VRF to BGP", e);
            }
        }
        //Try to add up vpn Interfaces if already in Operational Datastore
        LOG.trace("Trying to add the vpn interfaces  -1.");
        InstanceIdentifier<VpnInterfaces> vpnInterfacesId = InstanceIdentifier.builder(VpnInterfaces.class).build();
        Optional<VpnInterfaces> optionalVpnInterfaces = read(LogicalDatastoreType.CONFIGURATION, vpnInterfacesId);

        if(optionalVpnInterfaces.isPresent()) {
            List<VpnInterface> vpnInterfaces = optionalVpnInterfaces.get().getVpnInterface();
            for(VpnInterface vpnInterface : vpnInterfaces) {
                if(vpnInterface.getVpnInstanceName().equals(value.getVpnInstanceName())) {
                    LOG.debug("VpnInterface {} will be added from VPN {}", vpnInterface.getName(), value.getVpnInstanceName());
                    vpnInterfaceManager.add(
                                VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()), vpnInterface);

                }
            }
        }
    }

    private InstanceIdentifier<?> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstances.class).child(VpnInstance.class);
    }

    private InstanceIdentifier<?> getFibEntryListenerPath() {
        return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class)
                .child(VrfEntry.class);
    }

    private InstanceIdentifier<?> getVpnInstanceOpListenerPath() {
        return InstanceIdentifier.create(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class);

    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up Vpn DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        if (fibListenerRegistration != null) {
            try {
                fibListenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up Fib entries DataChangeListener.", e);
            }
            fibListenerRegistration = null;
        }
        if (opListenerRegistration != null) {
            try {
                opListenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up VPN Instance Operational entries DataChangeListener.", e);
            }
            opListenerRegistration = null;
        }

        LOG.trace("VPN Manager Closed");
    }

    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private <T extends DataObject> void syncWrite(LogicalDatastoreType datastoreType,
                                                   InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing VPN instance to ID info to datastore (path, data) : ({}, {})", path, data);
            throw new RuntimeException(e.getMessage());
        }
    }

    protected VpnInstanceOpDataEntry getVpnInstanceOpData(String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = read(LogicalDatastoreType.OPERATIONAL, id);
        if(vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    private <T extends DataObject> void delete(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    private class FibEntriesListener extends AbstractDataChangeListener<VrfEntry>  {

        public FibEntriesListener() {
            super(VrfEntry.class);
        }

        @Override
        protected void remove(InstanceIdentifier<VrfEntry> identifier,
                VrfEntry del) {
            LOG.trace("Remove Fib event - Key : {}, value : {} ", identifier, del);
            final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
            String rd = key.getRouteDistinguisher();
            Long label = del.getLabel();
            VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(rd);
            if(vpnInstanceOpData != null) {
                List<Long> routeIds = vpnInstanceOpData.getRouteEntryId();
                if(routeIds == null) {
                    LOG.debug("Fib Route entry is empty.");
                    return;
                }
                LOG.debug("Removing label from vpn info - {}", label);
                routeIds.remove(label);
                asyncWrite(LogicalDatastoreType.OPERATIONAL, VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                           new VpnInstanceOpDataEntryBuilder(vpnInstanceOpData).setRouteEntryId(routeIds).build(), DEFAULT_CALLBACK);
            } else {
                LOG.warn("No VPN Instance found for RD: {}", rd);
            }
        }

        @Override
        protected void update(InstanceIdentifier<VrfEntry> identifier,
                VrfEntry original, VrfEntry update) {
            // TODO Auto-generated method stub

        }

        @Override
        protected void add(InstanceIdentifier<VrfEntry> identifier,
                           VrfEntry add) {
            LOG.trace("Add Vrf Entry event - Key : {}, value : {}", identifier, add);
            final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
            String rd = key.getRouteDistinguisher();
            Long label = add.getLabel();
            VpnInstanceOpDataEntry vpn = getVpnInstanceOpData(rd);
            if(vpn != null) {
                List<Long> routeIds = vpn.getRouteEntryId();
                if(routeIds == null) {
                    routeIds = new ArrayList<>();
                }
                LOG.debug("Adding label to vpn info - {}", label);
                routeIds.add(label);
                asyncWrite(LogicalDatastoreType.OPERATIONAL, VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                           new VpnInstanceOpDataEntryBuilder(vpn).setRouteEntryId(routeIds).build(), DEFAULT_CALLBACK);
            } else {
                LOG.warn("No VPN Instance found for RD: {}", rd);
            }
        }
    }

    class VpnInstanceOpListener extends org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener<VpnInstanceOpDataEntry> {

        public VpnInstanceOpListener() {
            super(VpnInstanceOpDataEntry.class);
        }

        @Override
        protected void remove(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry del) {

        }

        @Override
        protected void update(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
            final VpnInstanceOpDataEntryKey key = identifier.firstKeyOf(VpnInstanceOpDataEntry.class, VpnInstanceOpDataEntryKey.class);
            String vpnName = key.getVrfId();

            LOG.trace("VpnInstanceOpListener update: vpn name {} interface count in Old VpnOp Instance {} in New VpnOp Instance {}" ,
                            vpnName, original.getVpnInterfaceCount(), update.getVpnInterfaceCount() );

            //if((original.getVpnToDpnList().size() != update.getVpnToDpnList().size()) && (update.getVpnToDpnList().size() == 0)) {
            if((original.getVpnInterfaceCount() != update.getVpnInterfaceCount()) && (update.getVpnInterfaceCount() == 0)) {
                notifyTaskIfRequired(vpnName);
            }
        }

        private void notifyTaskIfRequired(String vpnName) {
            Runnable notifyTask = vpnOpMap.remove(vpnName);
            if (notifyTask == null) {
                return;
            }
            executorService.execute(notifyTask);
        }

        @Override
        protected void add(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry add) {
        }
    }
}

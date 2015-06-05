/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstance1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstance1Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class VpnManager extends AbstractDataChangeListener<VpnInstance> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration, fibListenerRegistration;
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private IdManagerService idManager;
    private VpnInterfaceManager vpnInterfaceManager;
    private final FibEntriesListener fibListener;

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
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), VpnManager.this, DataChangeScope.SUBTREE);
            fibListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getFibEntryListenerPath(), fibListener, DataChangeScope.BASE);
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

    @Override
    protected void remove(InstanceIdentifier<VpnInstance> identifier, VpnInstance del) {
        LOG.trace("Remove VPN event - Key: {}, value: {}", identifier, del);
        String vpnName = del.getVpnInstanceName();
        InstanceIdentifier<VpnInstance> vpnIdentifier = VpnUtil.getVpnInstanceIdentifier(vpnName);
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

        delete(LogicalDatastoreType.OPERATIONAL, vpnIdentifier);

        String rd = del.getIpv4Family().getRouteDistinguisher();
        try {
            bgpManager.deleteVrf(rd);
        } catch(Exception e) {
            LOG.error("Exception when removing VRF from BGP", e);
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
        LOG.trace("key: {}, value: {}", identifier, value);

        long vpnId = getUniqueId(value.getVpnInstanceName());

        VpnInstance opValue = new VpnInstanceBuilder(value).
                 addAugmentation(VpnInstance1.class, new VpnInstance1Builder().setVpnId(vpnId).build()).build();

        asyncWrite(LogicalDatastoreType.OPERATIONAL, identifier, opValue, DEFAULT_CALLBACK);

        //public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts)
        VpnAfConfig config = value.getIpv4Family();
        String rd = config.getRouteDistinguisher();
        List<String> importRts = Arrays.asList(config.getImportRoutePolicy().split(","));
        List<String> exportRts = Arrays.asList(config.getExportRoutePolicy().split(","));
        try {
            bgpManager.addVrf(rd, importRts, exportRts);
        } catch(Exception e) {
            LOG.error("Exception when adding VRF to BGP", e);
        }
    }

    private InstanceIdentifier<?> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstances.class).child(VpnInstance.class);
    }

    private InstanceIdentifier<?> getFibEntryListenerPath() {
        return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class)
                .child(VrfEntry.class);
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

    private VpnInstance getVpnForRD(String rd) {
        InstanceIdentifier<VpnInstances> id = InstanceIdentifier.create(VpnInstances.class);
        Optional<VpnInstances> vpnInstances = read(LogicalDatastoreType.OPERATIONAL, id);
        if(vpnInstances.isPresent()) {
            List<VpnInstance> vpns = vpnInstances.get().getVpnInstance();
            for(VpnInstance vpn : vpns) {
                if(vpn.getIpv4Family().getRouteDistinguisher().equals(rd)) {
                    return vpn;
                }
            }
        }
        return null;
    }

    private Integer getUniqueId(String idKey) {
        GetUniqueIdInput getIdInput = new GetUniqueIdInputBuilder()
                                           .setPoolName(VpnConstants.VPN_IDPOOL_NAME)
                                           .setIdKey(idKey).build();

        try {
            Future<RpcResult<GetUniqueIdOutput>> result = idManager.getUniqueId(getIdInput);
            RpcResult<GetUniqueIdOutput> rpcResult = result.get();
            if(rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id",e);
        }
        return 0;
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
            VpnInstance vpn = getVpnForRD(rd);
            if(vpn != null) {
                InstanceIdentifier<VpnInstance> id = VpnUtil.getVpnInstanceIdentifier(vpn.getVpnInstanceName());
                InstanceIdentifier<VpnInstance1> augId = id.augmentation(VpnInstance1.class);
                Optional<VpnInstance1> vpnAugmenation = read(LogicalDatastoreType.OPERATIONAL, augId);
                if(vpnAugmenation.isPresent()) {
                    VpnInstance1 vpnAug = vpnAugmenation.get();
                    List<Long> routeIds = vpnAug.getRouteEntryId();
                    if(routeIds == null) {
                        LOG.debug("Fib Route entry is empty.");
                        return;
                    }
                    LOG.debug("Removing label from vpn info - {}", label);
                    routeIds.remove(label);
                    asyncWrite(LogicalDatastoreType.OPERATIONAL, augId,
                            new VpnInstance1Builder(vpnAug).setRouteEntryId(routeIds).build(), DEFAULT_CALLBACK);
                } else {
                    LOG.warn("VPN Augmentation not found for vpn instance {}", vpn.getVpnInstanceName());
                }
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
            VpnInstance vpn = getVpnForRD(rd);
            if(vpn != null) {
                InstanceIdentifier<VpnInstance> id = VpnUtil.getVpnInstanceIdentifier(vpn.getVpnInstanceName());
                InstanceIdentifier<VpnInstance1> augId = id.augmentation(VpnInstance1.class);
                Optional<VpnInstance1> vpnAugmenation = read(LogicalDatastoreType.OPERATIONAL, augId);
                if(vpnAugmenation.isPresent()) {
                    VpnInstance1 vpnAug = vpnAugmenation.get();
                    List<Long> routeIds = vpnAug.getRouteEntryId();
                    if(routeIds == null) {
                        routeIds = new ArrayList<>();
                    }
                    LOG.debug("Adding label to vpn info - {}", label);
                    routeIds.add(label);
                    asyncWrite(LogicalDatastoreType.OPERATIONAL, augId,
                            new VpnInstance1Builder(vpnAug).setRouteEntryId(routeIds).build(), DEFAULT_CALLBACK);
                } else {
                    LOG.warn("VPN Augmentation not found for vpn instance {}", vpn.getVpnInstanceName());
                }
            } else {
                LOG.warn("No VPN Instance found for RD: {}", rd);
            }
        }
    }
}

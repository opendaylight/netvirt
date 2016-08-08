/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInterfaceOpListener extends AbstractDataChangeListener<VpnInterface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceOpListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;
    private ConcurrentHashMap<String, Runnable> vpnIntfMap = new ConcurrentHashMap<String, Runnable>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    /*public VpnInterfaceOpListener(final DataBroker dataBroker) {
        super(VpnInterface.class);
        this.dataBroker = dataBroker;
    }*/

    public VpnInterfaceOpListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager) {
        super(VpnInterface.class);
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                getWildCardPath(), this, AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    private InstanceIdentifier<VpnInterface> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
            listenerRegistration = null;
        }
        LOG.info("{} close", getClass().getSimpleName());
    }

    @Override
    protected void remove(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface del) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();
        final String vpnName = del.getVpnInstanceName();
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                        postProcessVpnInterfaceRemoval(identifier, del, writeOperTxn);
                        CheckedFuture<Void, TransactionCommitFailedException> futures = writeOperTxn.submit();
                        try {
                            futures.get();
                        } catch (InterruptedException | ExecutionException e) {
                            LOG.error("Error handling removal of oper data for interface {} from vpn {}", interfaceName,
                                    vpnName);
                            throw new RuntimeException(e.getMessage());
                        }
                        return null;
                    }
                });
    }

    private void postProcessVpnInterfaceRemoval(InstanceIdentifier<VpnInterface> identifier, VpnInterface del,
                                                WriteTransaction writeOperTxn) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();
        String vpnName = del.getVpnInstanceName();

        LOG.trace("VpnInterfaceOpListener removed: interface name {} vpnName {}", interfaceName, vpnName);
        //decrement the vpn interface count in Vpn Instance Op Data
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to
                .vpn.id.VpnInstance>
                id = VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<VpnInstance> vpnInstance
                = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);

        if (vpnInstance.isPresent()) {
            String rd = null;
            rd = vpnInstance.get().getVrfId();
            //String rd = getRouteDistinguisher(del.getVpnInstanceName());

            VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(dataBroker, rd);
            LOG.trace("VpnInterfaceOpListener removed: interface name {} rd {} vpnName {} in Vpn Op Instance {}",
                    interfaceName, rd, vpnName, vpnInstOp);

            if (vpnInstOp != null) {
                // Vpn Interface removed => No more adjacencies from it.
                // Hence clean up interface from vpn-dpn-interface list.
                Adjacency adjacency = del.getAugmentation(Adjacencies.class).getAdjacency().get(0);
                List<Prefixes> prefixToInterface = new ArrayList<>();
                Optional<Prefixes> prefix = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                if (prefix.isPresent()) {
                    prefixToInterface.add(prefix.get());
                }
                if (prefixToInterface.isEmpty()) {
                    for (String nh : adjacency.getNextHopIpList()) {
                        prefix = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                        VpnUtil.getIpPrefix(nh)));
                        if (prefix.isPresent())
                            prefixToInterface.add(prefix.get());
                    }
                }
                for (Prefixes pref : prefixToInterface) {
                    if (writeOperTxn != null) {
                        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(), pref.getIpAddress()));
                    } else {
                        VpnUtil.delete(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(), pref.getIpAddress()),
                                VpnUtil.DEFAULT_CALLBACK);
                    }
                    vpnInterfaceManager.updateVpnToDpnMapping(pref.getDpnId(), del.getVpnInstanceName(),
                            interfaceName, false /* delete */);
                }
            }
        } else {
            LOG.error("rd not retrievable as vpninstancetovpnid for vpn {} is absent, trying rd as ", vpnName, vpnName);
        }
        notifyTaskIfRequired(interfaceName);
    }

    private void notifyTaskIfRequired(String intfName) {
        Runnable notifyTask = vpnIntfMap.remove(intfName);
        if (notifyTask == null) {
            LOG.trace("VpnInterfaceOpListener update: No Notify Task queued for vpnInterface {}", intfName);
            return;
        }
        executorService.execute(notifyTask);
    }

    @Override
    protected void update(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface original,
                          final VpnInterface update) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();

        if (original.getVpnInstanceName().equals(update.getVpnInstanceName())) {
            return;
        }

        final String vpnName = update.getVpnInstanceName();
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        postProcessVpnInterfaceUpdate(identifier, original, update);
                        return null;
                    }
                });
    }

    private void postProcessVpnInterfaceUpdate(InstanceIdentifier<VpnInterface> identifier, VpnInterface original,
                                               VpnInterface update) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();

        //increment the vpn interface count in Vpn Instance Op Data
        VpnInstanceOpDataEntry vpnInstOp = null;
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to
                .vpn.id.VpnInstance>
                origId = VpnUtil.getVpnInstanceToVpnIdIdentifier(original.getVpnInstanceName());
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                .VpnInstance> origVpnInstance
                = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, origId);

        if (origVpnInstance.isPresent()) {
            String rd = null;
            rd = origVpnInstance.get().getVrfId();

            vpnInstOp = VpnUtil.getVpnInstanceOpData(dataBroker, rd);
            LOG.trace("VpnInterfaceOpListener updated: interface name {} original rd {} original vpnName {}",
                    interfaceName, rd, original.getVpnInstanceName());

            if (vpnInstOp != null) {
                Adjacency adjacency = original.getAugmentation(Adjacencies.class).getAdjacency().get(0);
                List<Prefixes> prefixToInterfaceList = new ArrayList<>();
                Optional<Prefixes> prefixToInterface = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                if (prefixToInterface.isPresent()) {
                    prefixToInterfaceList.add(prefixToInterface.get());
                } else {
                    for (String adj : adjacency.getNextHopIpList()) {
                        prefixToInterface = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                        VpnUtil.getIpPrefix(adj)));
                        if (prefixToInterface.isPresent()) {
                            prefixToInterfaceList.add(prefixToInterface.get());
                        }
                    }
                }
                for (Prefixes prefix : prefixToInterfaceList) {
                    vpnInterfaceManager.updateVpnToDpnMapping(prefix.getDpnId(), original.getVpnInstanceName(),
                            interfaceName, false /* delete */);
                }
            }
        }
        notifyTaskIfRequired(interfaceName);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInterface> identifier, VpnInterface add) {
    }
}

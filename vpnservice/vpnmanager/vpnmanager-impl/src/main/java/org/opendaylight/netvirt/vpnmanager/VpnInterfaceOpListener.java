/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInterfaceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInterfaceOpListener extends AsyncDataTreeChangeListenerBase<VpnInterfaceOpDataEntry,
                           VpnInterfaceOpListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceOpListener.class);
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnFootprintService vpnFootprintService;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public VpnInterfaceOpListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager,
        final VpnFootprintService vpnFootprintService) {
        super(VpnInterfaceOpDataEntry.class, VpnInterfaceOpListener.class);
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnFootprintService = vpnFootprintService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInterfaceOpDataEntry> getWildCardPath() {
        InstanceIdentifier<VpnInterfaceOpDataEntry> id = InstanceIdentifier.create(VpnInterfaceOpData.class
                ).child(VpnInterfaceOpDataEntry.class);
        return id;
    }

    @Override
    protected VpnInterfaceOpListener getDataTreeChangeListener() {
        return VpnInterfaceOpListener.this;
    }


    @Override
    protected void remove(final InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
            final VpnInterfaceOpDataEntry del) {
        final VpnInterfaceOpDataEntryKey key = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class,
                VpnInterfaceOpDataEntryKey.class);
        final String interfaceName = key.getName();
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
            () -> {
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                postProcessVpnInterfaceRemoval(identifier, del, writeOperTxn);
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(writeOperTxn.submit());
                LOG.info("remove: Removed vpn operational data for interface {} on dpn {} vpn {}", del.getName(),
                        del.getDpnId(), del.getVpnInstanceName());
                return futures;
            });
    }

    private void postProcessVpnInterfaceRemoval(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
            VpnInterfaceOpDataEntry del, WriteTransaction writeOperTxn) {
        final VpnInterfaceOpDataEntryKey key = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class,
                VpnInterfaceOpDataEntryKey.class);
        String interfaceName = key.getName();
        String vpnName = del.getVpnInstanceName();

        LOG.info("postProcessVpnInterfaceRemoval: interface name {} vpnName {} dpn {}", interfaceName, vpnName,
                del.getDpnId());
        //decrement the vpn interface count in Vpn Instance Op Data
        Optional<VpnInstance> vpnInstance = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                                         VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName));

        if (vpnInstance.isPresent()) {
            String rd = null;
            rd = vpnInstance.get().getVrfId();

            VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(dataBroker, rd);

            AdjacenciesOp adjs = del.getAugmentation(AdjacenciesOp.class);
            List<Adjacency> adjList = (adjs != null) ? adjs.getAdjacency() : null;

            if (vpnInstOp != null && adjList != null && adjList.size() > 0) {
                // Vpn Interface removed => No more adjacencies from it.
                // Hence clean up interface from vpn-dpn-interface list.
                Adjacency adjacency = adjs.getAdjacency().get(0);
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
                        if (prefix.isPresent()) {
                            prefixToInterface.add(prefix.get());
                        }
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
                    vpnFootprintService.updateVpnToDpnMapping(pref.getDpnId(), del.getVpnInstanceName(), rd,
                        interfaceName, null /*ipAddressSourceValuePair*/, false /* delete */);
                }
            }
            LOG.info("postProcessVpnInterfaceRemoval: Removed vpn operational data and updated vpn footprint"
                    + " for interface {} on dpn {} vpn {}", interfaceName, del.getDpnId(), vpnName);
        } else {
            LOG.error("postProcessVpnInterfaceRemoval: rd not retrievable as vpninstancetovpnid for vpn {} is absent,"
                    + " trying rd as {}. interface {} dpn {}", vpnName, vpnName, interfaceName,
                    del.getDpnId());
        }
        notifyTaskIfRequired(interfaceName);
    }

    private void notifyTaskIfRequired(String intfName) {
        Runnable notifyTask = vpnInterfaceManager.isNotifyTaskQueued(intfName);
        if (notifyTask == null) {
            LOG.debug("notifyTaskIfRequired: No tasks queued to wait for deletion of vpnInterface {}", intfName);
            return;
        }
        executorService.execute(notifyTask);
    }

    @Override
    protected void update(final InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
            final VpnInterfaceOpDataEntry original, final VpnInterfaceOpDataEntry update) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();

        LOG.info("VpnInterfaceOpListener updated: original {} updated {}", original, update);
        if (original.getVpnInstanceName().equals(update.getVpnInstanceName())) {
            return;
        }

        final String vpnName = update.getVpnInstanceName();
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
            () -> {
                postProcessVpnInterfaceUpdate(identifier, original, update);
                LOG.info("update: vpn interface {} on dpn {} vpn {} processed successfully", update.getName(),
                     update.getDpnId(), update.getVpnInstanceName());
                return null;
            });
    }

    private void postProcessVpnInterfaceUpdate(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
            VpnInterfaceOpDataEntry original, VpnInterfaceOpDataEntry update) {
        final VpnInterfaceKey keyOld = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final VpnInterfaceOpDataEntryKey key = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class,
                VpnInterfaceOpDataEntryKey.class);
        String interfaceName = key.getName();
        VpnInterface vpnInterface = VpnUtil.getVpnInterface(dataBroker, original.getVpnInstanceName());

        LOG.info("postProcessVpnInterfaceUpdate: interface name {} vpnName {} dpn {}", interfaceName,
                update.getVpnInstanceName(), update.getDpnId());
        //increment the vpn interface count in Vpn Instance Op Data
        VpnInstanceOpDataEntry vpnInstOp = null;
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                .VpnInstance> origVpnInstance =
            VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                         VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(original.getVpnInstanceName()));

        if (origVpnInstance.isPresent()) {
            String rd = origVpnInstance.get().getVrfId();
            vpnInstOp = VpnUtil.getVpnInstanceOpData(dataBroker, rd);

            AdjacenciesOp adjs = update.getAugmentation(AdjacenciesOp.class);
            List<Adjacency> adjList = (adjs != null) ? adjs.getAdjacency() : null;
            if (vpnInstOp != null && adjList != null && adjList.size() > 0) {
                Adjacency adjacency = adjs.getAdjacency().get(0);
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
                    vpnFootprintService.updateVpnToDpnMapping(prefix.getDpnId(),
                        original.getVpnInstanceName(), rd,
                        interfaceName, null /*ipAddressSourceValuePair*/, false /* delete */);
                }
            }
            LOG.info("postProcessVpnInterfaceUpdate: Updated vpn operational data and vpn footprint"
                    + " for interface {} on dpn {} vpn {}", interfaceName, update.getDpnId(),
                    update.getVpnInstanceName());
        } else {
            LOG.error("postProcessVpnInterfaceUpdate: rd not retrievable as vpninstancetovpnid for vpn {} is absent,"
                    + " trying rd as {}. interface {} dpn {}", update.getVpnInstanceName(), update.getVpnInstanceName(),
                    interfaceName, update.getDpnId());
        }
        notifyTaskIfRequired(interfaceName);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, VpnInterfaceOpDataEntry add) {
    }
}

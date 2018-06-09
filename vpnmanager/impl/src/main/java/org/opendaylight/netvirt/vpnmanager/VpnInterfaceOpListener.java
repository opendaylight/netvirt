/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
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

@Singleton
public class VpnInterfaceOpListener extends AsyncDataTreeChangeListenerBase<VpnInterfaceOpDataEntry,
                                                                     VpnInterfaceOpListener> {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceOpListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnFootprintService vpnFootprintService;
    private final JobCoordinator jobCoordinator;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /*public VpnInterfaceOpListener(final DataBroker dataBroker) {
        super(VpnInterface.class);
        this.dataBroker = dataBroker;
    }*/

    @Inject
    public VpnInterfaceOpListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager,
        final VpnFootprintService vpnFootprintService, final JobCoordinator jobCoordinator) {
        super(VpnInterfaceOpDataEntry.class, VpnInterfaceOpListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnFootprintService = vpnFootprintService;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
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
        jobCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                postProcessVpnInterfaceRemoval(identifier, del, tx);
                LOG.info("remove: Removed vpn operational data for interface {} on dpn {} vpn {}", del.getName(),
                        del.getDpnId(), del.getVpnInstanceName());
            })));
    }

    private void postProcessVpnInterfaceRemoval(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
            VpnInterfaceOpDataEntry del, WriteTransaction writeOperTxn) {
            if (writeOperTxn == null) {
                ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx ->
                                postProcessVpnInterfaceRemoval(identifier, del, tx)), LOG,
                        "Error post-processing VPN interface removal");
                return;
            }
            final VpnInterfaceOpDataEntryKey key = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class,
                    VpnInterfaceOpDataEntryKey.class);
            String interfaceName = key.getName();
            String vpnName = del.getVpnInstanceName();
        try {
            LOG.info("postProcessVpnInterfaceRemoval: interface name {} vpnName {} dpn {}", interfaceName, vpnName,
                    del.getDpnId());
            //decrement the vpn interface count in Vpn Instance Op Data
            Optional<VpnInstance> vpnInstance = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName));

            if (vpnInstance.isPresent()) {
                String rd = vpnInstance.get().getVrfId();

                VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(dataBroker, rd);

                AdjacenciesOp adjs = del.augmentation(AdjacenciesOp.class);
                List<Adjacency> adjList = adjs != null ? adjs.getAdjacency() : null;

                if (vpnInstOp != null && adjList != null && adjList.size() > 0) {
                /*
                 * When a VPN Interface is removed by FibManager (aka VrfEntryListener and its cohorts),
                 * one adjacency for that VPN Interface will be hanging around along with that
                 * VPN Interface.   That adjacency could be primary (or) non-primary.
                 * If its a primary adjacency, then a prefix-to-interface entry will be available for the
                 * same.  If its a non-primary adjacency, then a prefix-to-interface entry will not be
                 * available for the same, instead we will have vpn-to-extraroutes filled in for them.
                 *
                 * Here we try to remove prefix-to-interface entry for pending adjacency in the deleted
                 * vpnInterface.   More importantly, we also update the vpnInstanceOpData by removing this
                 * vpnInterface from it.
                 */
                    List<Prefixes> prefixToInterface = new ArrayList<>();
                    for (Adjacency adjacency : adjs.getAdjacency()) {
                        List<Prefixes> prefixToInterfaceLocal = new ArrayList<>();
                        Optional<Prefixes> prefix = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                LogicalDatastoreType.OPERATIONAL,
                                VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                        VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                        if (prefix.isPresent()) {
                            prefixToInterfaceLocal.add(prefix.get());
                        }
                        if (prefixToInterfaceLocal.isEmpty()) {
                            for (String nh : adjacency.getNextHopIpList()) {
                                prefix = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                        LogicalDatastoreType.OPERATIONAL, VpnUtil.getPrefixToInterfaceIdentifier(
                                                vpnInstOp.getVpnId(), VpnUtil.getIpPrefix(nh)));
                                if (prefix.isPresent()) {
                                    prefixToInterfaceLocal.add(prefix.get());
                                }
                            }
                        }
                        if (!prefixToInterfaceLocal.isEmpty()) {
                            prefixToInterface.addAll(prefixToInterfaceLocal);
                        }
                    }
                /*
                 * In VPN Migration scenarios, there is a race condition where we use the new DPNID
                 * for the migrated VM instead of old DPNID because when we read prefix-to-interface to cleanup
                 * old DPNID, we actually get the new DPNID.
                 *
                 * More dangerously, we tend to alter the new prefix-to-interface which should be retained intac
                 * for the migration to succeed in L3VPN.  As a workaround, here we are going to use the dpnId in
                 * the deleted vpnInterface itself instead of tinkering with the prefix-to-interface.  Further we
                 * will tinker prefix-to-interface only when are damn sure if its value matches our
                 * deleted vpnInterface.
                 *
                 */
                    for (Prefixes pref : prefixToInterface) {
                        if (VpnUtil.isMatchedPrefixToInterface(pref, del)) {
                            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                                    VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(), pref.getIpAddress()));
                        }
                    }
                }
                if (del.getDpnId() != null) {
                    vpnFootprintService.updateVpnToDpnMapping(del.getDpnId(), del.getVpnInstanceName(), rd,
                            interfaceName, null /*ipAddressSourceValuePair*/,
                            false /* do delete */);
                }
                LOG.info("postProcessVpnInterfaceRemoval: Removed vpn operational data and updated vpn footprint"
                        + " for interface {} on dpn {} vpn {}", interfaceName, del.getDpnId(), vpnName);
            } else {
                LOG.error("postProcessVpnInterfaceRemoval: rd not retrievable as vpninstancetovpnid for vpn {}"
                        + " is absent, trying rd as {}. interface {} dpn {}", vpnName, vpnName, interfaceName,
                        del.getDpnId());
            }
            notifyTaskIfRequired(interfaceName);
        } catch (ReadFailedException e) {
            LOG.error("postProcessVpnInterfaceRemoval: Failed to read data store for interface {} vpn {}",
                    interfaceName, vpnName);
        }
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
        LOG.info("update: interface {} vpn {}. Ignoring", original.getName(), original.getVpnInstanceName());
    }

    @Override
    protected void add(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, VpnInterfaceOpDataEntry add) {
        LOG.info("add: interface {} vpn {}. Ignoring", add.getName(), add.getVpnInstanceName());
    }
}

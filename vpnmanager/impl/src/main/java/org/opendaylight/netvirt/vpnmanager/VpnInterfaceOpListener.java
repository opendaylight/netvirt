/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadTransaction;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInterfaceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnInterfaceOpListener extends AbstractAsyncDataTreeChangeListener<VpnInterfaceOpDataEntry> {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceOpListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnFootprintService vpnFootprintService;
    private final JobCoordinator jobCoordinator;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final VpnUtil vpnUtil;

    /*public VpnInterfaceOpListener(final DataBroker dataBroker) {
        super(VpnInterface.class);
        this.dataBroker = dataBroker;
    }*/

    @Inject
    public VpnInterfaceOpListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager,
        final VpnFootprintService vpnFootprintService, final JobCoordinator jobCoordinator,
                                  final VpnUtil vpnUtil) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(VpnInterfaceOpData.class)
                        .child(VpnInterfaceOpDataEntry.class),
                org.opendaylight.infrautils.utils.concurrent.Executors
                        .newListeningSingleThreadExecutor("VpnInterfaceOpListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnFootprintService = vpnFootprintService;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
        start();
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        org.opendaylight.infrautils.utils.concurrent.Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void remove(final InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
            final VpnInterfaceOpDataEntry del) {
        final VpnInterfaceOpDataEntryKey key = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class);
        final String interfaceName = key.getName();
        jobCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
                postProcessVpnInterfaceRemoval(identifier, del, tx, null);
                LOG.info("remove: Removed vpn operational data for interface {} on dpn {} vpn {}", del.getName(),
                        del.getDpnId(), del.getVpnInstanceName());
            })));
    }

    private void postProcessVpnInterfaceRemoval(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
            VpnInterfaceOpDataEntry del, @Nullable TypedReadWriteTransaction<Operational> operTx,
            @Nullable TypedReadTransaction<Configuration> confTx) throws InterruptedException {
        if (confTx == null) {
            txRunner.callWithNewReadOnlyTransactionAndClose(CONFIGURATION,
                tx -> postProcessVpnInterfaceRemoval(identifier, del, operTx, tx));
            return;
        }
        if (operTx == null) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL,
                tx -> postProcessVpnInterfaceRemoval(identifier, del, tx, confTx)), LOG,
                "Error post-processing VPN interface removal");
            return;
        }
        final VpnInterfaceOpDataEntryKey key = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class);
        String interfaceName = key.getName();
        String vpnName = del.getVpnInstanceName();
        try {
            LOG.info("postProcessVpnInterfaceRemoval: interface name {} vpnName {} dpn {}", interfaceName, vpnName,
                    del.getDpnId());
            //decrement the vpn interface count in Vpn Instance Op Data
            Optional<VpnInstance> vpnInstance =
                confTx.read(VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName)).get();

            if (vpnInstance.isPresent()) {
                String rd = vpnInstance.get().getVrfId();

                VpnInstanceOpDataEntry vpnInstOp = vpnUtil.getVpnInstanceOpData(rd);

                AdjacenciesOp adjs = del.augmentation(AdjacenciesOp.class);
                Map<AdjacencyKey, Adjacency> adjMap = adjs != null ? adjs.getAdjacency() : null;

                if (vpnInstOp != null && adjMap != null && adjMap.size() > 0) {
                /*
                 * When a VPN Interface is removed by FibManager (aka VrfEntryListener and its cohorts),
                 * one adjacency or two adjacency (in case of dual-stack)
                 * for that VPN Interface will be hanging around along with that
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
                    for (Adjacency adjacency : adjs.getAdjacency().values()) {
                        List<Prefixes> prefixToInterfaceLocal = new ArrayList<>();
                        Optional<Prefixes> prefix = operTx.read(
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                VpnUtil.getIpPrefix(adjacency.getIpAddress()))).get();
                        if (prefix.isPresent()) {
                            prefixToInterfaceLocal.add(prefix.get());
                        }
                        if (prefixToInterfaceLocal.isEmpty() && adjacency.getNextHopIpList() != null) {
                            for (String nh : adjacency.getNextHopIpList()) {
                                prefix = operTx.read(VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                    VpnUtil.getIpPrefix(nh))).get();
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
                            operTx.delete(
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
        } catch (InterruptedException | ExecutionException e) {
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
    public void update(final InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
            final VpnInterfaceOpDataEntry original, final VpnInterfaceOpDataEntry update) {
        LOG.info("update: interface {} vpn {}", original.getName(), original.getVpnInstanceName());
    }

    @Override
    public void add(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, VpnInterfaceOpDataEntry add) {
        LOG.info("add: interface {} vpn {}. Ignoring", add.getName(), add.getVpnInstanceName());
    }
}

/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddInterfaceToDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddInterfaceToDpnOnVpnEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveInterfaceFromDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveInterfaceFromDpnOnVpnEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add._interface.to.dpn.on.vpn.event.AddInterfaceEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add._interface.to.dpn.on.vpn.event.AddInterfaceEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.Dpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.DpnsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.IpAddressesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.IpAddressesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.VpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove._interface.from.dpn.on.vpn.event.RemoveInterfaceEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove._interface.from.dpn.on.vpn.event.RemoveInterfaceEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnFootprintService implements IVpnFootprintService {

    private static final Logger LOG = LoggerFactory.getLogger(VpnFootprintService.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IFibManager fibManager;
    private final VpnOpDataSyncer vpnOpDataSyncer;
    private final NotificationPublishService notificationPublishService;
    private final IInterfaceManager interfaceManager;
    private final VpnUtil vpnUtil;

    @Inject
    public VpnFootprintService(final DataBroker dataBroker, final IFibManager fibManager,
            final NotificationPublishService notificationPublishService, final VpnOpDataSyncer vpnOpDataSyncer,
            final IInterfaceManager interfaceManager, VpnUtil vpnUtil) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.fibManager = fibManager;
        this.vpnOpDataSyncer = vpnOpDataSyncer;
        this.notificationPublishService = notificationPublishService;
        this.interfaceManager = interfaceManager;
        this.vpnUtil = vpnUtil;
    }

    @Override
    public void updateVpnToDpnMapping(BigInteger dpId, String vpnName, String primaryRd, @Nullable String interfaceName,
            @Nullable ImmutablePair<IpAddresses.IpAddressSource, String> ipAddressSourceValuePair, boolean add) {
        long vpnId = vpnUtil.getVpnId(vpnName);
        if (!dpId.equals(BigInteger.ZERO)) {
            if (add) {
                // Considering the possibility of VpnInstanceOpData not being ready yet cause
                // the VPN is
                // still in its creation process
                if (vpnId == VpnConstants.INVALID_ID) {
                    LOG.error("updateVpnToDpnMapping: Operational data  for vpn not ready. Waiting to update vpn"
                            + " footprint for vpn {} on dpn {} interface {}", vpnName, dpId, interfaceName);
                    vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataSyncer.VpnOpDataType.vpnInstanceToId, vpnName,
                            VpnConstants.PER_VPN_INSTANCE_OPDATA_MAX_WAIT_TIME_IN_MILLISECONDS);
                    vpnId = vpnUtil.getVpnId(vpnName);
                }
                if (interfaceName != null) {
                    createOrUpdateVpnToDpnListForInterfaceName(vpnId, primaryRd, dpId, interfaceName, vpnName);
                    publishInterfaceAddedToVpnNotification(interfaceName, dpId, vpnName, vpnId);
                } else {
                    createOrUpdateVpnToDpnListForIPAddress(vpnId, primaryRd, dpId, ipAddressSourceValuePair, vpnName);
                }
            } else {
                if (interfaceName != null) {
                    removeOrUpdateVpnToDpnListForInterfaceName(vpnId, primaryRd, dpId, interfaceName, vpnName);
                    publishInterfaceRemovedFromVpnNotification(interfaceName, dpId, vpnName, vpnId);
                } else {
                    removeOrUpdateVpnToDpnListForIpAddress(vpnId, primaryRd, dpId, ipAddressSourceValuePair, vpnName);
                }
            }
        }
    }

    private void createOrUpdateVpnToDpnListForInterfaceName(long vpnId, String primaryRd, BigInteger dpnId,
            String intfName, String vpnName) {
        AtomicBoolean newDpnOnVpn = new AtomicBoolean(false);
        final String rd = (VpnUtil.getVpnRd(dataBroker, vpnName) == null)
                ? vpnName : VpnUtil.getVpnRd(dataBroker, vpnName);
        /* Starts synchronized block. This ensures only one reader/writer get access to vpn-dpn-list
         * The future.get ensures that the write to the datastore is complete before leaving the synchronized block.
         */
        // FIXME: separate this out somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName);
        lock.lock();
        try {
            ListenableFuture<Void> future = txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                InstanceIdentifier<VpnToDpnList> id = VpnHelper.getVpnToDpnListIdentifier(primaryRd, dpnId);
                VpnInterfaces vpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();
                Optional<VpnToDpnList> dpnInVpn = tx.read(LogicalDatastoreType.OPERATIONAL, id).checkedGet();
                InstanceIdentifier<Dpns> dpnOpElementId = VpnUtil.getDpnListFromDpnOpElementsIdentifier(rd, dpnId);
                Optional<Dpns> dpnOpElement = VpnUtil.read(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, dpnOpElementId);
                if (dpnInVpn.isPresent()) {
                    VpnToDpnList vpnToDpnList = dpnInVpn.get();
                    Dpns dpnElement = dpnOpElement.get();
                    List<VpnInterfaces> vpnInterfaces = dpnElement.getVpnInterfaces();
                    if (vpnInterfaces == null) {
                        vpnInterfaces = new ArrayList<>();
                    }
                    vpnInterfaces.add(vpnInterface);
                    VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder(vpnToDpnList);
                    vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active);
                    DpnsBuilder dpnsBuilder = new DpnsBuilder(dpnElement);
                    dpnsBuilder.setVpnInterfaces(vpnInterfaces);

                    tx.put(LogicalDatastoreType.OPERATIONAL, id, vpnToDpnListBuilder.build(),
                        WriteTransaction.CREATE_MISSING_PARENTS);
                    tx.put(LogicalDatastoreType.OPERATIONAL, dpnOpElementId, dpnsBuilder.build(), true);

                    /*
                     * If earlier state was inactive, it is considered new DPN coming back to the
                     * same VPN
                     */
                    if (vpnToDpnList.getDpnState() == VpnToDpnList.DpnState.Inactive) {
                        newDpnOnVpn.set(true);
                    }
                    LOG.debug("createOrUpdateVpnToDpnList: Updating vpn footprint for vpn {} vpnId {} interface {}"
                            + " on dpn {}", vpnName, vpnId, intfName, dpnId);
                } else {
                    List<VpnInterfaces> vpnInterfaces = new ArrayList<>();
                    vpnInterfaces.add(vpnInterface);
                    VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder().setDpnId(dpnId);
                    vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active);
                    DpnsBuilder dpnsBuilder = new DpnsBuilder().setDpnId(dpnId);
                    dpnsBuilder.setVpnInterfaces(vpnInterfaces);

                    tx.put(LogicalDatastoreType.OPERATIONAL, id, vpnToDpnListBuilder.build(),
                        WriteTransaction.CREATE_MISSING_PARENTS);
                    tx.put(LogicalDatastoreType.OPERATIONAL, dpnOpElementId, dpnsBuilder.build(), true);

                    newDpnOnVpn.set(true);
                    LOG.debug("createOrUpdateVpnToDpnList: Creating vpn footprint for vpn {} vpnId {} interface {}"
                            + " on dpn {}", vpnName, vpnId, intfName, dpnId);
                }
            });
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("createOrUpdateVpnToDpnList: Error adding to dpnToVpnList for vpn {} vpnId {} interface {}"
                    + " dpn {}", vpnName, vpnId, intfName, dpnId, e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
        LOG.info("createOrUpdateVpnToDpnList: Created/Updated vpn footprint for vpn {} vpnId {} interfacName{}"
                + " on dpn {}", vpnName, vpnId, intfName, dpnId);
        /*
         * Informing the FIB only after writeTxn is submitted successfully.
         */
        if (newDpnOnVpn.get()) {
            if (vpnUtil.isVlan(intfName)) {
                if (!vpnUtil.shouldPopulateFibForVlan(vpnName, null, dpnId)) {
                    return;
                }
            }
            fibManager.populateFibOnNewDpn(dpnId, vpnId, primaryRd,
                    new DpnEnterExitVpnWorker(dpnId, vpnName, primaryRd, true /* entered */));
            LOG.info("createOrUpdateVpnToDpnList: Sent populateFib event for new dpn {} in VPN {} for interface {}",
                    dpnId, vpnName, intfName);
        }
    }

    private void createOrUpdateVpnToDpnListForIPAddress(long vpnId, String primaryRd, BigInteger dpnId,
            ImmutablePair<IpAddresses.IpAddressSource, String> ipAddressSourceValuePair, String vpnName) {
        AtomicBoolean newDpnOnVpn = new AtomicBoolean(false);
        final String rd = (VpnUtil.getVpnRd(dataBroker, vpnName) == null)
                ? vpnName : VpnUtil.getVpnRd(dataBroker, vpnName);

        /* Starts synchronized block. This ensures only one reader/writer get access to vpn-dpn-list
         * The future.get ensures that the write to the datastore is complete before leaving the synchronized block.
         */
        // FIXME: separate this out somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName);
        lock.lock();
        try {
            ListenableFuture<Void> future = txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                InstanceIdentifier<VpnToDpnList> id = VpnHelper.getVpnToDpnListIdentifier(primaryRd, dpnId);
                IpAddressesBuilder ipAddressesBldr = new IpAddressesBuilder()
                        .setIpAddressSource(ipAddressSourceValuePair.getKey());
                ipAddressesBldr.withKey(new IpAddressesKey(ipAddressSourceValuePair.getValue()));
                ipAddressesBldr.setIpAddress(ipAddressSourceValuePair.getValue());
                Optional<VpnToDpnList> dpnInVpn = tx.read(LogicalDatastoreType.OPERATIONAL, id).checkedGet();
                InstanceIdentifier<Dpns> dpnOpElementId = VpnUtil.getDpnListFromDpnOpElementsIdentifier(rd, dpnId);
                Optional<Dpns> dpnOpElements = VpnUtil.read(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, dpnOpElementId);
                if (dpnInVpn.isPresent()) {
                    VpnToDpnList vpnToDpnList = dpnInVpn.get();
                    Dpns dpnElement = dpnOpElements.get();
                    List<IpAddresses> ipAddresses = dpnElement.getIpAddresses();
                    if (ipAddresses == null) {
                        ipAddresses = new ArrayList<>();
                    }
                    ipAddresses.add(ipAddressesBldr.build());
                    VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder(vpnToDpnList);
                    vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active);
                    DpnsBuilder dpnsBuilder = new DpnsBuilder(dpnElement);
                    dpnsBuilder.setIpAddresses(ipAddresses);

                    tx.put(LogicalDatastoreType.OPERATIONAL, id, vpnToDpnListBuilder.build(), true);
                    tx.put(LogicalDatastoreType.OPERATIONAL, dpnOpElementId, dpnsBuilder.build(), true);
                    /*
                     * If earlier state was inactive, it is considered new DPN coming back to the
                     * same VPN
                     */
                    if (vpnToDpnList.getDpnState() == VpnToDpnList.DpnState.Inactive) {
                        newDpnOnVpn.set(true);
                    }
                } else {
                    List<IpAddresses> ipAddresses = new ArrayList<>();
                    ipAddresses.add(ipAddressesBldr.build());
                    VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder().setDpnId(dpnId);
                    vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active);
                    DpnsBuilder dpnsBuilder = new DpnsBuilder().setDpnId(dpnId);
                    dpnsBuilder.setIpAddresses(ipAddresses);
                    tx.put(LogicalDatastoreType.OPERATIONAL, id, vpnToDpnListBuilder.build(), true);
                    tx.put(LogicalDatastoreType.OPERATIONAL, dpnOpElementId, dpnsBuilder.build(), true);
                    newDpnOnVpn.set(true);
                }

            });
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("createOrUpdateVpnToDpnListForIPAddress: Error adding to dpnToVpnList for vpn {}"
                    + " ipAddresses {} dpn {}", vpnName, ipAddressSourceValuePair.getValue(), dpnId, e);
            throw new RuntimeException(e.getMessage(), e); //TODO: Avoid this
        } finally {
            lock.unlock();
        }
        /*
         * Informing the Fib only after writeTxn is submitted successfuly.
         */
        if (newDpnOnVpn.get()) {
            LOG.debug("Sending populateFib event for new dpn {} in VPN {}", dpnId, vpnName);
            fibManager.populateFibOnNewDpn(dpnId, vpnId, primaryRd,
                    new DpnEnterExitVpnWorker(dpnId, vpnName, primaryRd, true /* entered */));
        }
    }

    private void removeOrUpdateVpnToDpnListForInterfaceName(long vpnId, String rd, BigInteger dpnId, String intfName,
            String vpnName) {
        AtomicBoolean lastDpnOnVpn = new AtomicBoolean(false);
        /* Starts synchronized block. This ensures only one reader/writer get access to vpn-dpn-list
         * The future.get ensures that the write to the datastore is complete before leaving the synchronized block.
         */
        // FIXME: separate this out somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName);
        lock.lock();
        try {
            try {
                ListenableFuture<Void> future = txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                    InstanceIdentifier<VpnToDpnList> id = VpnHelper.getVpnToDpnListIdentifier(rd, dpnId);
                    Optional<VpnToDpnList> dpnInVpnOpt = tx.read(LogicalDatastoreType.OPERATIONAL, id)
                            .checkedGet();
                    if (!dpnInVpnOpt.isPresent()) {
                        LOG.error("removeOrUpdateVpnToDpnList: Could not find DpnToVpn map for VPN=[name={}"
                                + " rd={} id={}] and dpnId={}", vpnName, rd, id, dpnId);
                        return;
                    }
                    VpnToDpnList dpnInVpn = dpnInVpnOpt.get();
                    InstanceIdentifier<Dpns> dpnOpElementId = VpnUtil.getDpnListFromDpnOpElementsIdentifier(rd, dpnId);
                    Optional<Dpns> dpnOpElements = VpnUtil.read(dataBroker,
                            LogicalDatastoreType.OPERATIONAL, dpnOpElementId);
                    List<VpnInterfaces> vpnInterfaces = dpnOpElements.get().getVpnInterfaces();
                    if (vpnInterfaces == null) {
                        LOG.error("Could not find vpnInterfaces for DpnInVpn map for VPN=[name={} rd={} id={}] and "
                                + "dpnId={}", vpnName, rd, id, dpnId);
                        return;
                    }
                    VpnInterfaces currVpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();
                    if (vpnInterfaces.remove(currVpnInterface)) {
                        if (vpnInterfaces.isEmpty()) {
                            List<IpAddresses> ipAddresses = dpnOpElements.get().getIpAddresses();
                            VpnToDpnListBuilder dpnInVpnBuilder = new VpnToDpnListBuilder(dpnInVpn);
                            final DpnsBuilder dpnsBuilder = new DpnsBuilder(dpnOpElements.get()).setVpnInterfaces(null);
                            if (ipAddresses == null || ipAddresses.isEmpty()) {
                                dpnInVpnBuilder.setDpnState(VpnToDpnList.DpnState.Inactive);
                                lastDpnOnVpn.set(true);
                            } else {
                                LOG.error("removeOrUpdateVpnToDpnList: vpn interfaces are empty but ip addresses"
                                        + " are present for the vpn {} in dpn {} interface {}", vpnName, dpnId,
                                        intfName);
                            }
                            LOG.debug("removeOrUpdateVpnToDpnList: Removing vpn footprint for vpn {} vpnId {} "
                                    + "interface {}, on dpn {}", vpnName, vpnName, intfName, dpnId);
                            tx.put(LogicalDatastoreType.OPERATIONAL, id, dpnInVpnBuilder.build(),
                                    WriteTransaction.CREATE_MISSING_PARENTS);
                            tx.put(LogicalDatastoreType.OPERATIONAL, dpnOpElementId, dpnsBuilder.build(), true);


                        } else {
                            tx.delete(LogicalDatastoreType.OPERATIONAL,
                                    dpnOpElementId.child(VpnInterfaces.class, new VpnInterfacesKey(intfName)));
                            LOG.debug("removeOrUpdateVpnToDpnList: Updating vpn footprint for vpn {} vpnId {} "
                                    + "interface {}, on dpn {}", vpnName, vpnName, intfName, dpnId);
                        }
                    }
                });
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("removeOrUpdateVpnToDpnList: Error removing from dpnToVpnList for vpn {} vpnId {}"
                        + " interface {} dpn {}", vpnName, vpnId, intfName, dpnId, e);
                throw new RuntimeException(e.getMessage(), e);
            }
            // Ends synchronized block
            LOG.info("removeOrUpdateVpnToDpnList: Updated/Removed vpn footprint for vpn {} vpnId {} interface {},"
                    + " on dpn {}", vpnName, vpnName, intfName, dpnId);

            if (lastDpnOnVpn.get()) {
                fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd,
                        new DpnEnterExitVpnWorker(dpnId, vpnName, rd, false /* exited */));
                LOG.info("removeOrUpdateVpnToDpnList: Sent cleanup event for dpn {} in VPN {} vpnId {} interface {}",
                        dpnId, vpnName, vpnId, intfName);
            }
        } finally {
            lock.unlock();
        }
    }

    private void removeOrUpdateVpnToDpnListForIpAddress(long vpnId, String rd, BigInteger dpnId,
            ImmutablePair<IpAddresses.IpAddressSource, String> ipAddressSourceValuePair, String vpnName) {
        AtomicBoolean lastDpnOnVpn = new AtomicBoolean(false);
        /* Starts synchronized block. This ensures only one reader/writer get access to vpn-dpn-list
         * The future.get ensures that the write to the datastore is complete before leaving the synchronized block.
         */
        // FIXME: separate this out somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName);
        lock.lock();
        try {
            ListenableFuture<Void> future = txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                InstanceIdentifier<VpnToDpnList> id = VpnHelper.getVpnToDpnListIdentifier(rd, dpnId);

                Optional<VpnToDpnList> dpnInVpnOpt = tx.read(LogicalDatastoreType.OPERATIONAL, id)
                        .checkedGet();
                if (!dpnInVpnOpt.isPresent()) {
                    LOG.error("removeOrUpdateVpnToDpnList: Could not find DpnToVpn map for VPN=[name={} "
                            + "rd={} id={}] and dpnId={}", vpnName, rd, id, dpnId);
                    return;
                }
                VpnToDpnList dpnInVpn = dpnInVpnOpt.get();
                InstanceIdentifier<Dpns> dpnOpElementId = VpnUtil.getDpnListFromDpnOpElementsIdentifier(rd, dpnId);
                Dpns dpnOpElements = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, dpnOpElementId).get();
                List<IpAddresses> ipAddresses = dpnOpElements.getIpAddresses();
                if (ipAddresses == null) {
                    LOG.info("Could not find ipAddresses for DpnInVpn map for VPN=[name={} rd={} id={}] "
                            + "and dpnId={}", vpnName, rd, id, dpnId);
                    return;
                }

                IpAddresses currIpAddress = new IpAddressesBuilder()
                        .withKey(new IpAddressesKey(ipAddressSourceValuePair.getValue()))
                        .setIpAddressSource(ipAddressSourceValuePair.getKey()).build();
                if (ipAddresses.remove(currIpAddress)) {
                    if (ipAddresses.isEmpty()) {
                        List<VpnInterfaces> vpnInterfaces = dpnOpElements.getVpnInterfaces();
                        final DpnsBuilder dpnsBuilder = new DpnsBuilder(dpnOpElements).setIpAddresses(null);
                        VpnToDpnListBuilder dpnInVpnBuilder = new VpnToDpnListBuilder(dpnInVpn);
                        if (vpnInterfaces == null || vpnInterfaces.isEmpty()) {
                            dpnInVpnBuilder.setDpnState(VpnToDpnList.DpnState.Inactive);
                            lastDpnOnVpn.set(true);
                        } else {
                            LOG.warn("ip addresses are empty but vpn interfaces are present for the vpn {} in "
                                    + "dpn {}", vpnName, dpnId);
                        }
                        tx.put(LogicalDatastoreType.OPERATIONAL, id, dpnInVpnBuilder.build(), true);
                        tx.put(LogicalDatastoreType.OPERATIONAL, dpnOpElementId, dpnsBuilder.build(), true);


                    } else {
                        tx.delete(LogicalDatastoreType.OPERATIONAL, dpnOpElementId.child(IpAddresses.class,
                                new IpAddressesKey(ipAddressSourceValuePair.getValue())));
                    }
                }

            });
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error removing from dpnToVpnList for vpn {} Ipaddress {} dpn {}", vpnName,
                ipAddressSourceValuePair.getValue(), dpnId, e);
            throw new RuntimeException(e.getMessage(), e); //TODO: Avoid this
        } finally {
            lock.unlock();
        }

        if (lastDpnOnVpn.get()) {
            LOG.debug("Sending cleanup event for dpn {} in VPN {}", dpnId, vpnName);
            fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd,
                    new DpnEnterExitVpnWorker(dpnId, vpnName, rd, false /* exited */));
        }
    }

    private void publishAddNotification(final BigInteger dpnId, final String vpnName, final String rd) {
        LOG.debug("publishAddNotification: Sending notification for add dpn {} in vpn {} rd {} event ", dpnId, vpnName,
                rd);
        AddEventData data = new AddEventDataBuilder().setVpnName(vpnName).setRd(rd).setDpnId(dpnId).build();
        AddDpnEvent event = new AddDpnEventBuilder().setAddEventData(data).build();
        final ListenableFuture<?> eventFuture = notificationPublishService.offerNotification(event);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.error("publishAddNotification: Error in notifying listeners for add dpn {} in vpn {} rd {} event ",
                        dpnId, vpnName, rd, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.info("publishAddNotification: Successful in notifying listeners for add dpn {} in vpn {} rd {}"
                        + " event ", dpnId, vpnName, rd);
            }
        }, MoreExecutors.directExecutor());
    }

    private void publishRemoveNotification(final BigInteger dpnId, final String vpnName, final String rd) {
        LOG.debug("publishRemoveNotification: Sending notification for remove dpn {} in vpn {} rd {} event ", dpnId,
                vpnName, rd);
        RemoveEventData data = new RemoveEventDataBuilder().setVpnName(vpnName).setRd(rd).setDpnId(dpnId).build();
        RemoveDpnEvent event = new RemoveDpnEventBuilder().setRemoveEventData(data).build();
        final ListenableFuture<?> eventFuture = notificationPublishService.offerNotification(event);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.error("publishRemoveNotification: Error in notifying listeners for remove dpn {} in vpn {} rd {}"
                        + " event ", dpnId, vpnName, rd, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.info("publishRemoveNotification: Successful in notifying listeners for remove dpn {} in vpn {}"
                        + " rd {} event ", dpnId, vpnName, rd);
            }
        }, MoreExecutors.directExecutor());
    }

    private void publishInterfaceAddedToVpnNotification(String interfaceName, BigInteger dpnId, String vpnName,
            Long vpnId) {
        LOG.debug("publishInterfaceAddedToVpnNotification: Sending notification for addition of interface {} on dpn {}"
                + " for vpn {}", interfaceName, dpnId, vpnName);
        AddInterfaceEventData data = new AddInterfaceEventDataBuilder().setInterfaceName(interfaceName).setVpnId(vpnId)
                .setDpnId(dpnId).build();
        AddInterfaceToDpnOnVpnEvent event = new AddInterfaceToDpnOnVpnEventBuilder().setAddInterfaceEventData(data)
                .build();
        final ListenableFuture<?> eventFuture = notificationPublishService.offerNotification(event);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.warn("publishInterfaceAddedToVpnNotification: Error in notifying listeners for add interface {}"
                        + " on dpn {} in vpn {} event ", interfaceName, dpnId, vpnName, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.trace("publishInterfaceAddedToVpnNotification: Successful in notifying listeners for add"
                        + " interface {} on dpn {} in vpn {} event ", interfaceName, dpnId, vpnName);
            }
        }, MoreExecutors.directExecutor());
    }

    private void publishInterfaceRemovedFromVpnNotification(String interfaceName, BigInteger dpnId, String vpnName,
            Long vpnId) {
        LOG.debug("publishInterfaceAddedToVpnNotification: Sending notification for removal of interface {}"
                + " from dpn {} for vpn {}", interfaceName, dpnId, vpnName);
        RemoveInterfaceEventData data = new RemoveInterfaceEventDataBuilder().setInterfaceName(interfaceName)
                .setVpnId(vpnId).setDpnId(dpnId).build();
        RemoveInterfaceFromDpnOnVpnEvent event = new RemoveInterfaceFromDpnOnVpnEventBuilder()
                .setRemoveInterfaceEventData(data).build();
        final ListenableFuture<?> eventFuture = notificationPublishService.offerNotification(event);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.warn(
                        "publishInterfaceAddedToVpnNotification: Error in notifying listeners"
                                + " for removing interface {} from dpn {} in vpn {} event ",
                        interfaceName, dpnId, vpnName, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.trace("publishInterfaceAddedToVpnNotification: Successful in notifying listeners for removing"
                        + " interface {} from dpn {} in vpn {} event ", interfaceName, dpnId, vpnName);
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * JobCallback class is used as a future callback for main and rollback workers
     * to handle success and failure.
     */
    private class DpnEnterExitVpnWorker implements FutureCallback<List<Void>> {
        private final Logger log = LoggerFactory.getLogger(DpnEnterExitVpnWorker.class);
        BigInteger dpnId;
        String vpnName;
        String rd;
        boolean entered;

        DpnEnterExitVpnWorker(BigInteger dpnId, String vpnName, String rd, boolean entered) {
            this.entered = entered;
            this.dpnId = dpnId;
            this.vpnName = vpnName;
            this.rd = rd;
        }

        /**
         * This implies that all the future instances have returned success. -- TODO:
         * Confirm this
         */
        @Override
        public void onSuccess(List<Void> voids) {
            if (entered) {
                publishAddNotification(dpnId, vpnName, rd);
                log.info("onSuccess: FootPrint established for vpn {} rd {} on dpn {}", vpnName, rd, dpnId);
            } else {
                publishRemoveNotification(dpnId, vpnName, rd);
                log.info("onSuccess: FootPrint cleared for vpn {} rd {} on dpn {}", vpnName, rd, dpnId);
            }
        }

        /**
         * This method is used to handle failure callbacks. If more retry needed, the
         * retrycount is decremented and mainworker is executed again. After retries
         * completed, rollbackworker is executed. If rollbackworker fails, this is a
         * double-fault. Double fault is logged and ignored.
         */
        @Override
        public void onFailure(Throwable throwable) {
            log.info("onFailure: Failed to establish/clear footprint for vpn {} rd {} on dpn {} ", vpnName, rd, dpnId,
                    throwable);
        }
    }

    boolean isVpnFootPrintCleared(VpnInstanceOpDataEntry vpnInstanceOpData) {
        return vpnInstanceOpData.getVpnToDpnList() == null || vpnInstanceOpData.getVpnToDpnList().isEmpty();
    }
}

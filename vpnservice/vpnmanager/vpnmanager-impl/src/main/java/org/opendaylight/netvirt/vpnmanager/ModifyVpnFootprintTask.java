/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Adds or removes VpnInterfaces to/from a VPN's footprint.
 *
 * NOTE: This is class is designed to be executed by the DataStoreJobCoordinator
 *       in a sequenced way
 *
 */
public class ModifyVpnFootprintTask implements Callable<List<ListenableFuture<Void>>> {

    enum State {
        CREATED,
        RUNNING,
        SUCCESSFUL,
        FAILED
    }

    // Used Services
    DataBroker broker;
    IFibManager fibManager;
    NotificationPublishService notifService;
    VpnOpDataSyncer vpnOpDataSyncer;

    // Attributes
    String vpnName;
    BigInteger dpnId;
    String ifaceName;
    boolean isCreation;
    State state;
    Optional<Boolean> isFirstIfaceOnDpn = Optional.absent(); // Only present on interface additions
    Optional<Boolean> wasLastIfaceOnDpn = Optional.absent();  // Only present on interface removals

    // In case the invoker wants to wait till the task is finished
    CheckedFuture<Void, TransactionCommitFailedException> future;


    static final Logger LOG = LoggerFactory.getLogger(ModifyVpnFootprintTask.class);


    public ModifyVpnFootprintTask(DataBroker dataBroker, IFibManager fibManager,
                                  NotificationPublishService notificationsService, VpnOpDataSyncer vpnOpDataNotif,
                                  String vpnName, BigInteger dpnId, String ifaceName, boolean add) {
        LOG.debug("ModifyVpnFootPrint: vpnName={} dpnId={}  ifaceName={}  isCreation={}", vpnName, dpnId, ifaceName, add);
        this.broker = dataBroker;
        this.fibManager = fibManager;
        this.notifService = notificationsService;
        this.vpnOpDataSyncer = vpnOpDataNotif;
        this.vpnName = vpnName;
        this.dpnId = dpnId;
        this.ifaceName = ifaceName;
        this.isCreation = add;

        this.state = State.CREATED;
    }

    public boolean is1stVpnInterfaceOnDpn() {
        return isFirstIfaceOnDpn.or(Boolean.FALSE);
    }

    public boolean wasLastVpnInterfaceOnDpn() {
        return wasLastIfaceOnDpn.or(Boolean.FALSE);
    }

    public String getDsJobCoordinatorKey() {
        return "DpnToVpnMapUpdater." + this.vpnName + "." + this.dpnId;
    }

    public boolean isFinished() {
        return state == State.SUCCESSFUL || state == State.FAILED;
    }

    public boolean isSuccessful() {
        return state == State.SUCCESSFUL;
    }

    public boolean hasFailed() {
        return state == State.FAILED;
    }

    /**
     * Makes the current thread wait until the new Vpn footprint is persisted in Datastore
     *
     * @param maxTimeWait Maximum waiting time in milliseconds
     *
     * @throws InterruptedException @see java.util.concurrent.Future.get()
     * @throws ExecutionException @see java.util.concurrent.Future.get()
     * @throws TimeoutException if, after the maxTimeWait, the persistence has
     *                          not even been attempted
     */
    public void waitTillPersisted(long maxTimeWait) throws InterruptedException, ExecutionException, TimeoutException {
        LOG.debug("waitTillPersisted of vpn={} dpnId={}  ifaceName={}  isCreation={}",
                 vpnName, dpnId, ifaceName, isCreation);
        long t0 = System.currentTimeMillis();
        long t1 = t0;

        while (this.future == null && (t1 - t0) <  maxTimeWait ) {
            Thread.sleep(50);
            t1 = System.currentTimeMillis();
        }

        if ( this.future != null ) {
            this.future.get();
        } else {
            String msg = "ModifyVpnFootprintTask Future not ready after " + maxTimeWait + "ms. "
                         + "  vpn=" + vpnName + "  ifaceName=" + ifaceName;
            LOG.debug(msg);
            throw new TimeoutException(msg);
        }

    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {

        this.state = State.RUNNING;

        LOG.debug("Updating VpnToDpn Map for Vpn {} on Dpn {} with ifaceName {}", vpnName, dpnId, ifaceName);

        List<ListenableFuture<Void>> result = new ArrayList<>();

        String rd = VpnUtil.getVpnRd(broker, vpnName);
        if ( rd == null && isCreation ) {
            vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataSyncer.VpnOpDataType.vpnInstanceToId, vpnName,
                                                VpnConstants.PER_VPN_INSTANCE_MAX_WAIT_TIME_IN_MILLISECONDS);
            rd = VpnUtil.getVpnRd(broker, vpnName);
        }

        if ( rd == null ) {
            LOG.warn("Could not update Vpn {} footprint. Reason: could not find suitable RD. Stepping out", vpnName);
            this.state = State.FAILED;
            this.future = Futures.immediateFailedCheckedFuture(null);
            result.add(this.future);

        } else {

            InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
            synchronized (vpnName.intern()) {
                WriteTransaction writeTxn = broker.newWriteOnlyTransaction();

                if (isCreation) {
                    addIfaceToVpnFootprint(id, writeTxn);
                } else {
                    removeIfaceFromVpnFootprint(id, writeTxn);
                }
                this.future = writeTxn.submit();
                result.add(this.future);
                Futures.addCallback(Futures.allAsList(result),
                                    new FibOnDpnWorker(fibManager, notifService, vpnName, rd, dpnId, ifaceName) );
            }
        }
        return result;
    }

    private void removeIfaceFromVpnFootprint(InstanceIdentifier<VpnToDpnList> vpnFootprintIid,
                                             WriteTransaction writeTx) {
        Optional<VpnToDpnList> dpnInVpn = Optional.absent();
        try {
            dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnFootprintIid);
        } catch ( RuntimeException e ) { // It's possible that VpnOpDataEntry it's been already removed
            LOG.warn("Could not find Footprint for vpn {}. It could have been removed. Reason: {}", this.vpnName, e);
            return;
        }

        if (dpnInVpn.isPresent()) {
            List<VpnInterfaces> vpnInterfaces = dpnInVpn.get().getVpnInterfaces();
            VpnInterfaces vpnIfaceToRemove = new VpnInterfacesBuilder().setInterfaceName(this.ifaceName).build();

            if (vpnInterfaces.remove(vpnIfaceToRemove)) {
                if (vpnInterfaces.isEmpty()) {
                    List<IpAddresses> ipAddresses = dpnInVpn.get().getIpAddresses();
                    if (ipAddresses == null || ipAddresses.isEmpty()) {
                        VpnToDpnListBuilder dpnInVpnBuilder =
                            new VpnToDpnListBuilder(dpnInVpn.get()).setDpnState(VpnToDpnList.DpnState.Inactive)
                                                                   .setVpnInterfaces(null);
                        writeTx.put(LogicalDatastoreType.OPERATIONAL, vpnFootprintIid, dpnInVpnBuilder.build(), true);
                        this.wasLastIfaceOnDpn = Optional.of(Boolean.TRUE);
                    } else {
                        LOG.warn("vpn interfaces are empty but ip addresses are present for the vpn {} in dpn {}",
                                 vpnName, dpnId);
                        state = State.FAILED;
                    }
                } else {
                    writeTx.delete(LogicalDatastoreType.OPERATIONAL,
                                   vpnFootprintIid.child(VpnInterfaces.class, new VpnInterfacesKey(this.ifaceName)));
                }
            } else {
                LOG.info("Cannot remove Interface {} in DPN {} from VPN {} footprint cause it is not in footprint");
                state = State.FAILED;
            }
        } else {
            LOG.warn("Error while trying to remove iface {} in Dpn {} from VPN {} footprint: No footprint at all");
            state = State.FAILED;
        }
    }

    private void addIfaceToVpnFootprint(InstanceIdentifier<VpnToDpnList> vpnFootprintIid, WriteTransaction writeTx) {

        Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnFootprintIid);
        VpnInterfaces vpnIfaceToAdd = new VpnInterfacesBuilder().setInterfaceName(ifaceName).build();
        if (dpnInVpn.isPresent()) {
            VpnToDpnList vpnToDpnList = dpnInVpn.get();
            List<VpnInterfaces> vpnInterfaces = vpnToDpnList.getVpnInterfaces();
            if (vpnInterfaces == null) {
                vpnInterfaces = new ArrayList<>();
            }
            vpnInterfaces.add(vpnIfaceToAdd);
            VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder(vpnToDpnList);
            vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active).setVpnInterfaces(vpnInterfaces);

            writeTx.put(LogicalDatastoreType.OPERATIONAL, vpnFootprintIid, vpnToDpnListBuilder.build(), true);
            /* If earlier state was inactive, it is considered new DPN coming back to the
             * same VPN
             */
            if (vpnToDpnList.getDpnState() == VpnToDpnList.DpnState.Inactive) {
                this.isFirstIfaceOnDpn = Optional.of(Boolean.TRUE);
            }
        } else {
            List<VpnInterfaces> vpnInterfaces = new ArrayList<>();
            vpnInterfaces.add(vpnIfaceToAdd);
            VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder().setDpnId(dpnId);
            vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active).setVpnInterfaces(vpnInterfaces);

            writeTx.put(LogicalDatastoreType.OPERATIONAL, vpnFootprintIid, vpnToDpnListBuilder.build(), true);
            this.isFirstIfaceOnDpn = Optional.of(Boolean.TRUE);
        }
    }

    /**
     * This worker is in charge of populating Fib flows in a given DPN if it
     * is necessary (the first VpnInterface on a DPN) or remove them if they
     * are not necessary anymore (when the last VpnInterface of a VPN is
     * removed from a DPN)
     */
    private class FibOnDpnWorker implements FutureCallback<List<Void>> {

        String vpnName;
        String rd;
        BigInteger dpnId;
        String ifaceName;

        IFibManager fibManager;
        NotificationPublishService notifService;

        public FibOnDpnWorker(IFibManager fibManager, NotificationPublishService notifService,
                                 String vpnName, String rd, BigInteger dpnId, String ifaceName) {
            this.vpnName = vpnName;
            this.rd = rd;
            this.dpnId = dpnId;
            this.ifaceName = ifaceName;
            this.fibManager = fibManager;
            this.notifService = notifService;
        }

        @Override
        public void onFailure(Throwable t) {
            LOG.warn("Error while adding iface {} on DPN {} in VPN {} footprint. Reason: {}",
                     ifaceName, dpnId, vpnName, t);
            state = State.FAILED;
        }

        @Override
        public void onSuccess(List<Void> arg0) {
            LOG.debug("IfaceName {} was added correctly to VPN {} VpnToDpn Map on DPN {}",
                      this.ifaceName, this.vpnName, this.dpnId);
            state = State.SUCCESSFUL;
            if (is1stVpnInterfaceOnDpn()) {
                LOG.debug("IfaceName {} is the first VPN Interface on DPN {} for VPN {}. Sending populateFib event",
                          this.ifaceName, this.dpnId, this.vpnName);
                long vpnId =  VpnUtil.getVpnId(broker, this.vpnName);
                fibManager.populateFibOnNewDpn(dpnId, vpnId, rd,
                                               new DpnEnterExitVpnCallback(this.notifService, this.dpnId, this.vpnName,
                                                                         this.rd, true /* entered */));
            } else if ( wasLastVpnInterfaceOnDpn() ) {
                LOG.debug("Iface {} was the last VpnInterface on DPN {} for VPN {}. Sending cleanupFib event",
                          this.ifaceName, this.dpnId, this.vpnName);
                long vpnId = VpnUtil.getVpnId(broker, vpnName);
                fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd,
                                            new DpnEnterExitVpnCallback(this.notifService, this.dpnId, this.vpnName,
                                                                      this.rd, false /* exited */));
            }
        }

    }
}

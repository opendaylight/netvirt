/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

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
import org.opendaylight.fibmanager.api.IFibManager;
import org.opendaylight.vpnservice.intervpnlink.DpnEnterExitVpnWorker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Adds or removes VpnInterfaces to/from a VPN's footprint
 *
 */
public class ModifyVpnFootprintTask implements Callable<List<ListenableFuture<Void>>> {

    enum State {
        CREATED,
        RUNNING,
        SUCCESSFUL,
        FAILED
    }

    DataBroker broker;
    IFibManager fibManager;
    NotificationPublishService notifService;
    String vpnName;
    BigInteger dpnId;
    String ifaceName;
    boolean isCreation;
    State state;

    Optional<Boolean> isFirstIfaceOnDpn = Optional.absent(); // Only present on interface additions
    Optional<Boolean> isLastIfaceOnDpn = Optional.absent();  // Only present on interface removals

    // In case the invoker wants to wait till the task is finished
    CheckedFuture<Void, TransactionCommitFailedException> future;


    static final Logger LOG = LoggerFactory.getLogger(ModifyVpnFootprintTask.class);


    public ModifyVpnFootprintTask(DataBroker dataBroker, IFibManager fibManager,
                                  NotificationPublishService notificationsService, String vpnName, BigInteger dpnId,
                                  String ifaceName, boolean add) {
        LOG.debug("ModifyVpnFootPrint: vpnName={} dpnId={}  ifaceName={}  isCreation={}", vpnName, dpnId, ifaceName, add);
        this.broker = dataBroker;
        this.fibManager = fibManager;
        this.notifService = notificationsService;
        this.vpnName = vpnName;
        this.dpnId = dpnId;
        this.ifaceName = ifaceName;
        this.isCreation = add;

        this.state = State.CREATED;
    }

    public boolean is1stVpnInterfaceOnDpn() {
        LOG.debug("isFirstIfaceOnDpn = {}", isFirstIfaceOnDpn.or(Boolean.FALSE));
        return isFirstIfaceOnDpn.or(Boolean.FALSE);
    }

    public boolean isLastVpnInterfaceOnDpn() {
        LOG.debug("isLastIfaceOnDpn = {}", isLastIfaceOnDpn.or(Boolean.FALSE));
        return isLastIfaceOnDpn.or(Boolean.FALSE);
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
        if ( this.future != null ) {
            LOG.debug("waitTillPersisted: future is already not null. Waiting for it to finish. vpn={} iface={}",
                      this.vpnName, this.ifaceName);
            this.future.get();
        } else {
            synchronized (this) {
                try {
                    this.wait(maxTimeWait);
                } finally {
                    if ( this.future == null ) {  // maxTimeWait expired
                        String msg = "ModifyVpnFootprintTask Future not ready after " + maxTimeWait + "ms. "
                                     + "  vpn=" + vpnName + "  ifaceName=" + ifaceName;
                        LOG.debug(msg);
                        throw new TimeoutException(msg);
                    } else {
                        LOG.debug("waitwaitTillPersisted: future is now set. Waiting for it to finish. vpn={}  iface={}",
                                  vpnName, ifaceName);
                        this.future.get();
                    }
                }
            }
        }
    }


    @Override
    public List<ListenableFuture<Void>> call() throws Exception {

        this.state = State.RUNNING;

        LOG.debug("Updating VpnToDpn Map for Vpn {} on Dpn {} with ifaceName {}", vpnName, dpnId, ifaceName);

        List<ListenableFuture<Void>> result = new ArrayList<ListenableFuture<Void>>();

        String rd = VpnUtil.getVpnRd(broker, vpnName);
        InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
        synchronized (vpnName.intern()) {
            WriteTransaction writeTxn = broker.newWriteOnlyTransaction();

            if (isCreation) {
                addIfaceToVpnFootprint(id, writeTxn);
            } else {
                removeIfaceFromVpnFootprint(id, writeTxn);
            }
            this.future = writeTxn.submit();
            synchronized (this) {
                notifyAll();
            }

            result.add(this.future);
            Futures.addCallback(Futures.allAsList(result),
                                new FibOnNewDpnWorker(fibManager, notifService, vpnName, rd, dpnId, ifaceName) );
        }

        return result;
    }

    private void removeIfaceFromVpnFootprint(InstanceIdentifier<VpnToDpnList> vpnFootprintIid,
                                             WriteTransaction writeTx) {
        Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnFootprintIid);
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
                        this.isLastIfaceOnDpn = Optional.of(Boolean.TRUE);
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

    private class FibOnNewDpnWorker implements FutureCallback<List<Void>> {

        String vpnName;
        String rd;
        BigInteger dpnId;
        String ifaceName;

        IFibManager fibManager;
        NotificationPublishService notifService;

        public FibOnNewDpnWorker(IFibManager fibManager, NotificationPublishService notifService,
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
                                               new DpnEnterExitVpnWorker(this.notifService, this.dpnId, this.vpnName,
                                                                         this.rd, true /* entered */));
            }
        }

    }
}

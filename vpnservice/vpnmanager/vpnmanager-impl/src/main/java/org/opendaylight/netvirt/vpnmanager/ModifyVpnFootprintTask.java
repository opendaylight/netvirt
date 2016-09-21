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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
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
 */
public class ModifyVpnFootprintTask {

    private static final Logger LOG = LoggerFactory.getLogger(ModifyVpnFootprintTask.class);

    enum State {
        CREATED,
        RUNNING,
        SUCCESSFUL,
        FAILED
    }

    // Used Services
    private final DataBroker broker;
    private final IFibManager fibManager;
    private final NotificationPublishService notifService;
    private final VpnOpDataSyncer vpnOpDataSyncer;

    // Attributes
    private final String vpnName;
    private final BigInteger dpnId;
    private final String ifaceName;
    private final boolean isCreation;

    State state;
    String errMessage;
    Optional<Boolean> isFirstIfaceOnDpn = Optional.absent(); // Only present on interface additions
    Optional<Boolean> wasLastIfaceOnDpn = Optional.absent();  // Only present on interface removals


    public ModifyVpnFootprintTask(DataBroker dataBroker, IFibManager fibManager,
                                  NotificationPublishService notificationsService, VpnOpDataSyncer vpnOpDataNotif,
                                  String vpnName, BigInteger dpnId, String ifaceName, boolean add) {
        LOG.debug("ModifyVpnFootPrint: vpnName={} dpnId={}  ifaceName={}  isCreation={}",
                  vpnName, dpnId, ifaceName, add);

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

    public boolean isFinished() {
        return state == State.SUCCESSFUL || state == State.FAILED;
    }

    public boolean isSuccessful() {
        return state == State.SUCCESSFUL;
    }

    public boolean hasFailed() {
        return state == State.FAILED;
    }

    public String getErrMessage() {
        return this.errMessage;
    }


    public void run() {

        this.state = State.RUNNING;

        LOG.debug("Updating VpnToDpn Map for Vpn {} on Dpn {} with ifaceName {}", vpnName, dpnId, ifaceName);

        String rd = VpnUtil.getVpnRd(broker, vpnName);
        if ( rd == null && isCreation ) {
            vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataSyncer.VpnOpDataType.vpnInstanceToId, vpnName,
                                                VpnConstants.PER_VPN_INSTANCE_MAX_WAIT_TIME_IN_MILLISECONDS);
            rd = VpnUtil.getVpnRd(broker, vpnName);
        }

        if ( rd == null ) {
            this.errMessage =
                "Could not update Vpn " + vpnName + " footprint. Reason: could not find suitable RD. Stepping out";
            LOG.warn(this.errMessage);
            this.state = State.FAILED;

        } else {

            InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
            synchronized (vpnName.intern()) {
                WriteTransaction writeTxn = broker.newWriteOnlyTransaction();

                if (isCreation) {
                    addIfaceToVpnFootprint(id, writeTxn);
                } else {
                    removeIfaceFromVpnFootprint(id, writeTxn);
                }
                CheckedFuture<Void, TransactionCommitFailedException> persistFuture = writeTxn.submit();

                try {
                    persistFuture.get();
                    Executors.newSingleThreadExecutor().execute(new FibOnDpnWorker(fibManager, notifService, vpnName,
                                                                                   rd, dpnId, ifaceName) );
                } catch (InterruptedException | ExecutionException e) {
                    this.errMessage = "Error persisting VpnFootprint for VPN=" + this.vpnName + " DPN=" + this.dpnId;
                    LOG.error(errMessage, e);
                    this.state = State.FAILED;
                    throw new RuntimeException(e.getMessage());
                }
            }
        }
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
                        this.errMessage =
                            "Vpn interfaces are empty but ip addresses are present for the vpn " + this.vpnName
                            + " in " + this.dpnId;
                        LOG.warn(errMessage);
                        state = State.FAILED;
                    }
                } else {
                    writeTx.delete(LogicalDatastoreType.OPERATIONAL,
                                   vpnFootprintIid.child(VpnInterfaces.class, new VpnInterfacesKey(this.ifaceName)));
                }
            } else {
                this.errMessage =
                    "Cannot remove Interface " + this.ifaceName + " in DPN " + this.dpnId + " from VPN "
                    + this.vpnName + " footprint cause it is not in footprint";
                LOG.info(this.errMessage);
                state = State.FAILED;
            }
        } else {
            this.errMessage =
                "Error while trying to remove iface " + ifaceName + " in Dpn " + dpnId + " from VPN " + vpnName
                + " footprint: No footprint at all";
            LOG.warn(this.errMessage);
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
    private class FibOnDpnWorker implements Runnable {

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
        public void run() {
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

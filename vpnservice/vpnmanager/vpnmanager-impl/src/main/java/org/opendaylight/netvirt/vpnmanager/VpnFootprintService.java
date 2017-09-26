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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove._interface.from.dpn.on.vpn.event.RemoveInterfaceEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove._interface.from.dpn.on.vpn.event.RemoveInterfaceEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnFootprintService implements IVpnFootprintService {

    private static final Logger LOG = LoggerFactory.getLogger(VpnFootprintService.class);

    private final DataBroker dataBroker;
    private final IFibManager fibManager;
    private final VpnOpDataSyncer vpnOpDataSyncer;
    private final OdlInterfaceRpcService ifaceMgrRpcService;
    private final NotificationPublishService notificationPublishService;

    public VpnFootprintService(final DataBroker dataBroker, final IFibManager fibManager,
            final OdlInterfaceRpcService ifaceRpcService, final NotificationPublishService notificationPublishService,
            final VpnOpDataSyncer vpnOpDataSyncer) {
        this.dataBroker = dataBroker;
        this.fibManager = fibManager;
        this.vpnOpDataSyncer = vpnOpDataSyncer;
        this.ifaceMgrRpcService = ifaceRpcService;
        this.notificationPublishService = notificationPublishService;
    }

    @Override
    public void updateVpnToDpnMapping(BigInteger dpId, String vpnName, String primaryRd, String interfaceName,
            ImmutablePair<IpAddresses.IpAddressSource, String> ipAddressSourceValuePair, boolean add) {
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        if (dpId == null) {
            dpId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, interfaceName);
        }
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
                    vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
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
        Boolean newDpnOnVpn = Boolean.FALSE;

        synchronized (vpnName.intern()) {
            WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
            InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(primaryRd, dpnId);
            Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            VpnInterfaces vpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();

            if (dpnInVpn.isPresent()) {
                VpnToDpnList vpnToDpnList = dpnInVpn.get();
                List<VpnInterfaces> vpnInterfaces = vpnToDpnList.getVpnInterfaces();
                if (vpnInterfaces == null) {
                    vpnInterfaces = new ArrayList<>();
                }
                vpnInterfaces.add(vpnInterface);
                VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder(vpnToDpnList);
                vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active).setVpnInterfaces(vpnInterfaces);

                writeTxn.put(LogicalDatastoreType.OPERATIONAL, id, vpnToDpnListBuilder.build(),
                        WriteTransaction.CREATE_MISSING_PARENTS);
                /*
                 * If earlier state was inactive, it is considered new DPN coming back to the
                 * same VPN
                 */
                if (vpnToDpnList.getDpnState() == VpnToDpnList.DpnState.Inactive) {
                    newDpnOnVpn = Boolean.TRUE;
                }
                LOG.debug("createOrUpdateVpnToDpnList: Updating vpn footprint for vpn {} vpnId {} interface {}"
                        + " on dpn {}", vpnName, vpnId, intfName, dpnId);
            } else {
                List<VpnInterfaces> vpnInterfaces = new ArrayList<>();
                vpnInterfaces.add(vpnInterface);
                VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder().setDpnId(dpnId);
                vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active).setVpnInterfaces(vpnInterfaces);

                writeTxn.put(LogicalDatastoreType.OPERATIONAL, id, vpnToDpnListBuilder.build(),
                        WriteTransaction.CREATE_MISSING_PARENTS);
                newDpnOnVpn = Boolean.TRUE;
                LOG.debug("createOrUpdateVpnToDpnList: Creating vpn footprint for vpn {} vpnId {} interface {}"
                        + " on dpn {}", vpnName, vpnId, intfName, dpnId);
            }
            try {
                writeTxn.submit().get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("createOrUpdateVpnToDpnList: Error adding to dpnToVpnList for vpn {} vpnId {} interface {}"
                        + " dpn {}", vpnName, vpnId, intfName, dpnId, e);
                throw new RuntimeException(e.getMessage());
            }
        }
        LOG.info("createOrUpdateVpnToDpnList: Created/Updated vpn footprint for vpn {} vpnId {} interfacName{}"
                + " on dpn {}", vpnName, vpnId, intfName, dpnId);
        /*
         * Informing the FIB only after writeTxn is submitted successfully.
         */
        if (newDpnOnVpn) {
            fibManager.populateFibOnNewDpn(dpnId, vpnId, primaryRd,
                    new DpnEnterExitVpnWorker(dpnId, vpnName, primaryRd, true /* entered */));
            LOG.info("createOrUpdateVpnToDpnList: Sent populateFib event for new dpn {} in VPN {} for interface {}",
                    dpnId, vpnName, intfName);
        }
    }

    private void createOrUpdateVpnToDpnListForIPAddress(long vpnId, String primaryRd, BigInteger dpnId,
            ImmutablePair<IpAddresses.IpAddressSource, String> ipAddressSourceValuePair, String vpnName) {
        Boolean newDpnOnVpn = Boolean.FALSE;

        synchronized (vpnName.intern()) {
            WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
            InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(primaryRd, dpnId);
            Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            IpAddressesBuilder ipAddressesBldr = new IpAddressesBuilder()
                    .setIpAddressSource(ipAddressSourceValuePair.getKey());
            ipAddressesBldr.setKey(new IpAddressesKey(ipAddressSourceValuePair.getValue()));
            ipAddressesBldr.setIpAddress(ipAddressSourceValuePair.getValue());

            if (dpnInVpn.isPresent()) {
                VpnToDpnList vpnToDpnList = dpnInVpn.get();
                List<IpAddresses> ipAddresses = vpnToDpnList.getIpAddresses();
                if (ipAddresses == null) {
                    ipAddresses = new ArrayList<>();
                }
                ipAddresses.add(ipAddressesBldr.build());
                VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder(vpnToDpnList);
                vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active).setIpAddresses(ipAddresses);

                writeTxn.put(LogicalDatastoreType.OPERATIONAL, id, vpnToDpnListBuilder.build(), true);
                /*
                 * If earlier state was inactive, it is considered new DPN coming back to the
                 * same VPN
                 */
                if (vpnToDpnList.getDpnState() == VpnToDpnList.DpnState.Inactive) {
                    newDpnOnVpn = Boolean.TRUE;
                }
            } else {
                List<IpAddresses> ipAddresses = new ArrayList<>();
                ipAddresses.add(ipAddressesBldr.build());
                VpnToDpnListBuilder vpnToDpnListBuilder = new VpnToDpnListBuilder().setDpnId(dpnId);
                vpnToDpnListBuilder.setDpnState(VpnToDpnList.DpnState.Active).setIpAddresses(ipAddresses);

                writeTxn.put(LogicalDatastoreType.OPERATIONAL, id, vpnToDpnListBuilder.build(), true);
                newDpnOnVpn = Boolean.TRUE;
            }
            try {
                writeTxn.submit().get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error adding to dpnToVpnList for vpn {} ipAddresses {} dpn {}", vpnName,
                        ipAddressSourceValuePair.getValue(), dpnId, e);
                throw new RuntimeException(e.getMessage());
            }
        }
        /*
         * Informing the Fib only after writeTxn is submitted successfuly.
         */
        if (newDpnOnVpn) {
            LOG.debug("Sending populateFib event for new dpn {} in VPN {}", dpnId, vpnName);
            fibManager.populateFibOnNewDpn(dpnId, vpnId, primaryRd,
                    new DpnEnterExitVpnWorker(dpnId, vpnName, primaryRd, true /* entered */));
        }
    }

    private void removeOrUpdateVpnToDpnListForInterfaceName(long vpnId, String rd, BigInteger dpnId, String intfName,
            String vpnName) {
        Boolean lastDpnOnVpn = Boolean.FALSE;
        synchronized (vpnName.intern()) {
            InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
            VpnToDpnList dpnInVpn = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id).orNull();
            if (dpnInVpn == null) {
                LOG.error("removeOrUpdateVpnToDpnList: Could not find DpnToVpn map for VPN=[name={} rd={} id={}]"
                        + " and dpnId={}", vpnName, rd, id, dpnId);
                return;
            }
            List<VpnInterfaces> vpnInterfaces = dpnInVpn.getVpnInterfaces();
            if (vpnInterfaces == null) {
                LOG.error("Could not find vpnInterfaces for DpnInVpn map for VPN=[name={} rd={} id={}] and dpnId={}",
                        vpnName, rd, id, dpnId);
                return;
            }

            VpnInterfaces currVpnInterface = new VpnInterfacesBuilder().setInterfaceName(intfName).build();
            if (vpnInterfaces.remove(currVpnInterface)) {
                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                if (vpnInterfaces.isEmpty()) {
                    List<IpAddresses> ipAddresses = dpnInVpn.getIpAddresses();
                    VpnToDpnListBuilder dpnInVpnBuilder = new VpnToDpnListBuilder(dpnInVpn).setVpnInterfaces(null);
                    if (ipAddresses == null || ipAddresses.isEmpty()) {
                        dpnInVpnBuilder.setDpnState(VpnToDpnList.DpnState.Inactive);
                        lastDpnOnVpn = Boolean.TRUE;
                    } else {
                        LOG.error("removeOrUpdateVpnToDpnList: vpn interfaces are empty but ip addresses are present"
                                + " for the vpn {} in dpn {} interface {}", vpnName, dpnId, intfName);
                    }
                    LOG.debug("removeOrUpdateVpnToDpnList: Removing vpn footprint for vpn {} vpnId {} interface {},"
                            + " on dpn {}", vpnName, vpnName, intfName, dpnId);
                    writeTxn.put(LogicalDatastoreType.OPERATIONAL, id, dpnInVpnBuilder.build(),
                            WriteTransaction.CREATE_MISSING_PARENTS);

                } else {
                    writeTxn.delete(LogicalDatastoreType.OPERATIONAL,
                            id.child(VpnInterfaces.class, new VpnInterfacesKey(intfName)));
                    LOG.debug("removeOrUpdateVpnToDpnList: Updating vpn footprint for vpn {} vpnId {} interface {},"
                            + " on dpn {}", vpnName, vpnName, intfName, dpnId);
                }
                try {
                    writeTxn.submit().get();
                } catch (InterruptedException | ExecutionException e) {
                    LOG.error("removeOrUpdateVpnToDpnList: Error removing from dpnToVpnList for vpn {} vpnId {}"
                            + " interface {} dpn {}", vpnName, vpnId, intfName, dpnId, e);
                    throw new RuntimeException(e.getMessage());
                }
            }
        } // Ends synchronized block
        LOG.info("removeOrUpdateVpnToDpnList: Updated/Removed vpn footprint for vpn {} vpnId {} interface {},"
                + " on dpn {}", vpnName, vpnName, intfName, dpnId);

        if (lastDpnOnVpn) {
            fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd,
                    new DpnEnterExitVpnWorker(dpnId, vpnName, rd, false /* exited */));
            LOG.info("removeOrUpdateVpnToDpnList: Sent cleanup event for dpn {} in VPN {} vpnId {} interface {}", dpnId,
                    vpnName, vpnId, intfName);
        }
    }

    private void removeOrUpdateVpnToDpnListForIpAddress(long vpnId, String rd, BigInteger dpnId,
            ImmutablePair<IpAddresses.IpAddressSource, String> ipAddressSourceValuePair, String vpnName) {
        Boolean lastDpnOnVpn = Boolean.FALSE;
        synchronized (vpnName.intern()) {
            InstanceIdentifier<VpnToDpnList> id = VpnUtil.getVpnToDpnListIdentifier(rd, dpnId);
            VpnToDpnList dpnInVpn = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id).orNull();
            if (dpnInVpn == null) {
                LOG.error("removeOrUpdateVpnToDpnList: Could not find DpnToVpn map for VPN=[name={} rd={} id={}]"
                        + " and dpnId={}", vpnName, rd, id, dpnId);
                return;
            }
            List<IpAddresses> ipAddresses = dpnInVpn.getIpAddresses();
            if (ipAddresses == null) {
                LOG.info("Could not find ipAddresses for DpnInVpn map for VPN=[name={} rd={} id={}] and dpnId={}",
                        vpnName, rd, id, dpnId);
                return;
            }

            IpAddresses currIpAddress = new IpAddressesBuilder()
                    .setKey(new IpAddressesKey(ipAddressSourceValuePair.getValue()))
                    .setIpAddressSource(ipAddressSourceValuePair.getKey()).build();
            if (ipAddresses.remove(currIpAddress)) {
                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                if (ipAddresses.isEmpty()) {
                    List<VpnInterfaces> vpnInterfaces = dpnInVpn.getVpnInterfaces();
                    VpnToDpnListBuilder dpnInVpnBuilder = new VpnToDpnListBuilder(dpnInVpn).setIpAddresses(null);
                    if (vpnInterfaces == null || vpnInterfaces.isEmpty()) {
                        dpnInVpnBuilder.setDpnState(VpnToDpnList.DpnState.Inactive);
                        lastDpnOnVpn = Boolean.TRUE;
                    } else {
                        LOG.warn("ip addresses are empty but vpn interfaces are present for the vpn {} in dpn {}",
                                vpnName, dpnId);
                    }
                    writeTxn.put(LogicalDatastoreType.OPERATIONAL, id, dpnInVpnBuilder.build(), true);

                } else {
                    writeTxn.delete(LogicalDatastoreType.OPERATIONAL,
                            id.child(IpAddresses.class, new IpAddressesKey(ipAddressSourceValuePair.getValue())));
                }
                try {
                    writeTxn.submit().get();
                } catch (InterruptedException | ExecutionException e) {
                    LOG.error("Error removing from dpnToVpnList for vpn {} Ipaddress {} dpn {}", vpnName,
                            ipAddressSourceValuePair.getValue(), dpnId, e);
                    throw new RuntimeException(e.getMessage());
                }
            }
        } // Ends synchronized block

        if (lastDpnOnVpn) {
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
        });
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
        });
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
        });
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
        });
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

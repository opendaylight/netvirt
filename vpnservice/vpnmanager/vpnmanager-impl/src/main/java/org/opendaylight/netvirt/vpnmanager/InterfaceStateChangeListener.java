/*
 * Copyright (c) 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceStateChangeListener
    extends AsyncDataTreeChangeListenerBase<Interface, InterfaceStateChangeListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateChangeListener.class);
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;

    public InterfaceStateChangeListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager) {
        super(Interface.class, InterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }


    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected InterfaceStateChangeListener getDataTreeChangeListener() {
        return InterfaceStateChangeListener.this;
    }


    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                LOG.info("VPN Interface add event - intfName {} from InterfaceStateChangeListener",
                                intrf.getName());
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + intrf.getName(),
                    () -> {
                        WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction writeInvTxn = dataBroker.newWriteOnlyTransaction();
                        List<ListenableFuture<Void>> futures = new ArrayList<>();

                        final String interfaceName = intrf.getName();
                        LOG.info("Detected interface add event for interface {}", interfaceName);

                        final VpnInterface vpnInterface =
                                VpnUtil.getConfiguredVpnInterface(dataBroker, interfaceName);
                        if (vpnInterface != null) {
                            String primaryRd = VpnUtil.getPrimaryRd(dataBroker,
                                    vpnInterface.getVpnInstanceName());
                            if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                                LOG.debug("VPN Interface Name {}", vpnInterface);
                                BigInteger intfDpnId = BigInteger.ZERO;
                                try {
                                    intfDpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                                } catch (Exception e) {
                                    LOG.error("Unable to retrieve dpnId for interface {}. "
                                            + "Process vpn interface add failed",intrf.getName(), e);
                                    return futures;
                                }
                                final BigInteger dpnId = intfDpnId;
                                final int ifIndex = intrf.getIfIndex();
                                if (!vpnInterfaceManager.isVpnInstanceReady(vpnInterface.getVpnInstanceName())) {
                                    LOG.error("VPN Interface add event - intfName {} onto vpnName {} "
                                                    + "running oper-driven, VpnInstance not ready, holding on",
                                            vpnInterface.getName(), vpnInterface.getVpnInstanceName());
                                    return futures;

                                }
                                LOG.info("VPN Interface add event - intfName {} onto vpnName {} running oper-driven",
                                        vpnInterface.getName(), vpnInterface.getVpnInstanceName());
                                vpnInterfaceManager.processVpnInterfaceUp(dpnId, vpnInterface, primaryRd, ifIndex,
                                        false, writeConfigTxn, writeOperTxn, writeInvTxn, intrf);
                                ListenableFuture<Void> operFuture = writeOperTxn.submit();
                                try {
                                    operFuture.get();
                                } catch (ExecutionException e) {
                                    LOG.error("InterfaceStateChange - Exception encountered while submitting"
                                                    + " operational future for addVpnInterface {}",
                                            vpnInterface.getName(), e);
                                    return null;
                                }
                                futures.add(writeConfigTxn.submit());
                                futures.add(writeInvTxn.submit());
                            } else {
                                LOG.error("add: Ignoring addition of vpnInterface {}, as vpnInstance {}"
                                        + " with primaryRd {} is already marked for deletion", interfaceName,
                                        vpnInterface.getVpnInstanceName(), primaryRd);
                            }
                        }
                        return futures;
                    });
            }
        } catch (Exception e) {
            LOG.error("Exception caught in Interface {} Operational State Up event", intrf.getName(), e);
        }
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        final String interfaceName = intrf.getName();
        BigInteger dpId = BigInteger.ZERO;
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                LOG.info("VPN Interface remove event - intfName {} from InterfaceStateChangeListener",
                                intrf.getName());
                try {
                    dpId = InterfaceUtils.getDpIdFromInterface(intrf);
                } catch (Exception e) {
                    LOG.error("Unable to retrieve dpnId from interface operational data store for interface"
                            + " {}. Fetching from vpn interface op data store. ", interfaceName, e);
                }
                final BigInteger inputDpId = dpId;
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
                    () -> {
                        WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction writeInvTxn = dataBroker.newWriteOnlyTransaction();
                        List<ListenableFuture<Void>> futures = new ArrayList<>();

                        InstanceIdentifier<VpnInterface> id = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
                        Optional<VpnInterface> optVpnInterface =
                                VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
                        if (optVpnInterface.isPresent()) {
                            final VpnInterface vpnInterface = optVpnInterface.get();
                            BigInteger dpnId = inputDpId;
                            if (dpnId == null || dpnId.equals(BigInteger.ZERO)) {
                                dpnId = vpnInterface.getDpnId();
                            }
                            final int ifIndex = intrf.getIfIndex();
                            LOG.info("VPN Interface remove event - intfName {} onto vpnName {} running oper-driven",
                                    vpnInterface.getName(), vpnInterface.getVpnInstanceName());
                            vpnInterfaceManager.processVpnInterfaceDown(dpnId, interfaceName, ifIndex, intrf,
                                    vpnInterface, false, writeConfigTxn, writeOperTxn, writeInvTxn);
                            ListenableFuture<Void> operFuture = writeOperTxn.submit();
                            try {
                                operFuture.get();
                            } catch (ExecutionException e) {
                                LOG.error("InterfaceStateChange - Exception encountered while submitting operational"
                                        + " future for removeVpnInterface {}", vpnInterface.getName(), e);
                                return null;
                            }
                            futures.add(writeConfigTxn.submit());
                            futures.add(writeInvTxn.submit());
                        } else {
                            LOG.debug("Interface {} is not a vpninterface, ignoring.", interfaceName);
                        }

                        return futures;
                    });
            }
        } catch (Exception e) {
            LOG.error("Exception observed in handling deletion of VPN Interface {}. ", interfaceName, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
                    Interface original, Interface update) {
        final String interfaceName = update.getName();
        try {
            OperStatus originalOperStatus = original.getOperStatus();
            OperStatus updateOperStatus = update.getOperStatus();
            if (originalOperStatus.equals(Interface.OperStatus.Unknown)
                  || updateOperStatus.equals(Interface.OperStatus.Unknown)) {
                LOG.debug("Interface {} state change is from/to null/UNKNOWN. Ignoring the update event.",
                        interfaceName);
                return;
            }

            if (update.getIfIndex() == null) {
                return;
            }
            if (L2vlan.class.equals(update.getType())) {
                LOG.info("VPN Interface update event - intfName {} from InterfaceStateChangeListener",
                        update.getName());
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
                    () -> {
                        WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction writeInvTxn = dataBroker.newWriteOnlyTransaction();
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        final VpnInterface vpnInterface =
                                VpnUtil.getConfiguredVpnInterface(dataBroker, interfaceName);
                        if (vpnInterface != null) {
                            final int ifIndex = update.getIfIndex();
                            final BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(update);
                            if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                                String primaryRd = VpnUtil.getPrimaryRd(dataBroker,
                                        vpnInterface.getVpnInstanceName());
                                if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                                    LOG.info("VPN Interface update event - intfName {} onto vpnName {} running "
                                                    + " oper-driven UP", vpnInterface.getName(),
                                            vpnInterface.getVpnInstanceName());
                                    if (!vpnInterfaceManager.isVpnInstanceReady(vpnInterface.getVpnInstanceName())) {
                                        LOG.error("VPN Interface update event - intfName {} onto vpnName {} "
                                                        + "running oper-driven UP, VpnInstance not ready, holding on",
                                                vpnInterface.getName(), vpnInterface.getVpnInstanceName());
                                        return futures;
                                    }
                                    vpnInterfaceManager.processVpnInterfaceUp(dpnId, vpnInterface, primaryRd, ifIndex,
                                            true, writeConfigTxn, writeOperTxn, writeInvTxn, update);
                                } else {
                                    LOG.error("update: Ignoring UP event for vpnInterface {}, as vpnInstance {}"
                                            + " with primaryRd {} is already marked for deletion", interfaceName,
                                            vpnInterface.getVpnInstanceName(), primaryRd);
                                }
                            } else if (update.getOperStatus().equals(Interface.OperStatus.Down)) {
                                LOG.info("VPN Interface update event - intfName {} onto vpnName {} running oper-driven"
                                        + " DOWN", vpnInterface.getName(), vpnInterface.getVpnInstanceName());
                                InstanceIdentifier<VpnInterface> id = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
                                Optional<VpnInterface> optVpnInterface =
                                        VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
                                if (optVpnInterface.isPresent()) {
                                    VpnInterface vpnOpInterface = optVpnInterface.get();
                                    vpnInterfaceManager.processVpnInterfaceDown(dpnId, interfaceName, ifIndex, update,
                                            vpnOpInterface, true, writeConfigTxn, writeOperTxn,
                                            writeInvTxn);
                                } else {
                                    LOG.error("InterfaceStateChangeListener Update DOWN - vpnInterface {}"
                                            + " not available, ignoring event", vpnInterface.getName());
                                    return futures;
                                }
                            }
                            ListenableFuture<Void> operFuture = writeOperTxn.submit();
                            try {
                                operFuture.get();
                            } catch (ExecutionException e) {
                                LOG.error("InterfaceStateChange - Exception encountered while submitting operational"
                                        + " future for updateVpnInterface {}", vpnInterface.getName(), e);
                                return null;
                            }
                            futures.add(writeConfigTxn.submit());
                            futures.add(writeInvTxn.submit());
                        } else {
                            LOG.debug("Interface {} is not a vpninterface, ignoring.", interfaceName);
                        }

                        return futures;
                    });
            }
        } catch (Exception e) {
            LOG.error("Exception observed in handling updation of VPN Interface {}. ", update.getName(), e);
        }
    }
}

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
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InterfaceStateChangeListener
    extends AsyncDataTreeChangeListenerBase<Interface, InterfaceStateChangeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateChangeListener.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final JobCoordinator jobCoordinator;

    @Inject
    public InterfaceStateChangeListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager,
            final JobCoordinator jobCoordinator) {
        super(Interface.class, InterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
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
                jobCoordinator.enqueueJob("VPNINTERFACE-" + intrf.getName(), () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>(3);
                    futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeOperTxn -> {
                        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeConfigTxn -> {
                            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeInvTxn -> {
                                final String interfaceName = intrf.getName();
                                LOG.info("Detected interface add event for interface {}", interfaceName);

                                final VpnInterface vpnIf =
                                        VpnUtil.getConfiguredVpnInterface(dataBroker, interfaceName);
                                if (vpnIf != null) {
                                    for (VpnInstanceNames vpnInterfaceVpnInstance :
                                        vpnIf.getVpnInstanceNames()) {
                                        String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
                                        if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                                            LOG.debug("VPN Interface Name {}", vpnIf);
                                        } else {
                                            LOG.info("add: Ignoring addition of vpnInterface {}, as vpnInstance {}"
                                                + " with primaryRd {} is already marked for deletion", interfaceName,
                                                vpnName, primaryRd);
                                            return;
                                        }
                                    }
                                    BigInteger intfDpnId = BigInteger.ZERO;
                                    try {
                                        intfDpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                                    } catch (Exception e) {
                                        LOG.error("Unable to retrieve dpnId for interface {}. "
                                                + "Process vpn interface add failed",intrf.getName(), e);
                                        return;
                                    }
                                    final BigInteger dpnId = intfDpnId;
                                    final int ifIndex = intrf.getIfIndex();
                                    for (VpnInstanceNames vpnInterfaceVpnInstance : vpnIf.getVpnInstanceNames()) {
                                        String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
                                        if (!vpnInterfaceManager.isVpnInstanceReady(vpnName)) {
                                            LOG.error("VPN Interface add event - intfName {} onto vpnName {} "
                                                            + "running oper-driven, VpnInstance not ready, holding on",
                                                vpnIf.getName(), vpnName);
                                            continue;
                                        }
                                        LOG.info("VPN Interface add event - intfName {} onto vpnName {} running "
                                                + "oper-driven", vpnIf.getName(), vpnName);
                                        vpnInterfaceManager.processVpnInterfaceUp(dpnId, vpnIf, primaryRd, ifIndex,
                                                    false, writeConfigTxn, writeOperTxn, writeInvTxn, intrf, vpnName);
                                    }
                                }
                            }));
                        }));
                    }));
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
        final String ifName = intrf.getName();
        BigInteger dpId = BigInteger.ZERO;
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                LOG.info("VPN Interface remove event - intfName {} from InterfaceStateChangeListener",
                                intrf.getName());
                try {
                    dpId = InterfaceUtils.getDpIdFromInterface(intrf);
                } catch (Exception e) {
                    LOG.error("Unable to retrieve dpnId from interface operational data store for interface"
                            + " {}. Fetching from vpn interface op data store. ", ifName, e);
                }
                final BigInteger inputDpId = dpId;
                jobCoordinator.enqueueJob("VPNINTERFACE-" + ifName, () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>(3);
                    futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeOperTxn -> {
                        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeConfigTxn -> {
                            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeInvTxn -> {
                                VpnInterface cfgVpnInterface =
                                    VpnUtil.getConfiguredVpnInterface(dataBroker, ifName);
                                if (cfgVpnInterface == null) {
                                    LOG.debug("Interface {} is not a vpninterface, ignoring.", ifName);
                                    return;
                                }
                                for (VpnInstanceNames vpnInterfaceVpnInstance :
                                    cfgVpnInterface.getVpnInstanceNames()) {
                                    String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                    Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                                            VpnUtil.getVpnInterfaceOpDataEntry(dataBroker, ifName, vpnName);
                                    if (!optVpnInterface.isPresent()) {
                                        LOG.debug("Interface {} vpn {} is not a vpninterface, or deletion triggered"
                                               + " by northbound agent. ignoring.",
                                               ifName, vpnName);
                                        continue;
                                    }
                                    final VpnInterfaceOpDataEntry vpnInterface = optVpnInterface.get();
                                    BigInteger dpnId = inputDpId;
                                    if (dpnId == null || dpnId.equals(BigInteger.ZERO)) {
                                        dpnId = vpnInterface.getDpnId();
                                    }
                                    final int ifIndex = intrf.getIfIndex();
                                    LOG.info("VPN Interface remove event - intfName {} onto vpnName {}"
                                               + " running oper-driver", vpnInterface.getName(), vpnName);
                                    vpnInterfaceManager.processVpnInterfaceDown(dpnId, ifName, ifIndex, intrf,
                                            vpnInterface, false, writeConfigTxn, writeOperTxn, writeInvTxn);
                                }
                            }));
                        }));
                    }));
                    return futures;
                });
            }
        } catch (Exception e) {
            LOG.error("Exception observed in handling deletion of VPN Interface {}. ", ifName, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
                    Interface original, Interface update) {
        final String ifName = update.getName();
        try {
            OperStatus originalOperStatus = original.getOperStatus();
            OperStatus updateOperStatus = update.getOperStatus();
            if (originalOperStatus.equals(Interface.OperStatus.Unknown)
                  || updateOperStatus.equals(Interface.OperStatus.Unknown)) {
                LOG.debug("Interface {} state change is from/to null/UNKNOWN. Ignoring the update event.",
                        ifName);
                return;
            }

            if (update.getIfIndex() == null) {
                return;
            }
            if (L2vlan.class.equals(update.getType())) {
                LOG.info("VPN Interface update event - intfName {} from InterfaceStateChangeListener",
                        update.getName());
                jobCoordinator.enqueueJob("VPNINTERFACE-" + ifName, () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>(3);
                    futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeOperTxn -> {
                        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeConfigTxn -> {
                            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeInvTxn -> {
                                final VpnInterface vpnIf =
                                        VpnUtil.getConfiguredVpnInterface(dataBroker, ifName);
                                if (vpnIf != null) {
                                    final int ifIndex = update.getIfIndex();
                                    final BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(update);
                                    if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                                        for (VpnInstanceNames vpnInterfaceVpnInstance : vpnIf.getVpnInstanceNames()) {
                                            String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                            String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
                                            if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                                                LOG.info("VPN Interface update event - intfName {} onto vpnName {}"
                                                            + " running oper-driven UP", vpnIf.getName(), vpnName);
                                            } else {
                                                LOG.error("update: Ignoring UP event for vpnInterface {}, as "
                                                        + "vpnInstance {} with primaryRd {} is already marked for"
                                                        + " deletion", vpnIf.getName(), vpnName, primaryRd);
                                                return;
                                            }
                                        }
                                        for (VpnInstanceNames vpnInterfaceVpnInstance
                                                 : vpnIf.getVpnInstanceNames()) {
                                            String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                            String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
                                            if (!vpnInterfaceManager.isVpnInstanceReady(vpnName)) {
                                                LOG.error("VPN Interface update event - intfName {} onto vpnName {} "
                                                        + "running oper-driven UP, VpnInstance not ready, holding on",
                                                        vpnIf.getName(), vpnName);
                                                continue;
                                            }
                                            vpnInterfaceManager.processVpnInterfaceUp(dpnId, vpnIf, primaryRd, ifIndex,
                                                 true, writeConfigTxn, writeOperTxn, writeInvTxn, update, vpnName);
                                        }
                                    } else if (update.getOperStatus().equals(Interface.OperStatus.Down)) {
                                        for (VpnInstanceNames vpnInterfaceVpnInstance : vpnIf.getVpnInstanceNames()) {
                                            String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                            LOG.info("VPN Interface update event - intfName {} onto vpnName {}"
                                                   + " running oper-driven DOWN", vpnIf.getName(), vpnName);
                                            Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                                                 VpnUtil.getVpnInterfaceOpDataEntry(dataBroker,
                                                                     vpnIf.getName(), vpnName);
                                            if (optVpnInterface.isPresent()) {
                                                VpnInterfaceOpDataEntry vpnOpInterface = optVpnInterface.get();
                                                vpnInterfaceManager.processVpnInterfaceDown(dpnId, vpnIf.getName(),
                                                        ifIndex, update, vpnOpInterface, true,
                                                        writeConfigTxn, writeOperTxn, writeInvTxn);
                                            } else {
                                                LOG.error("InterfaceStateChangeListener Update DOWN - vpnInterface {}"
                                                        + " not available, ignoring event", vpnIf.getName());
                                                continue;
                                            }
                                        }
                                    }
                                } else {
                                    LOG.debug("Interface {} is not a vpninterface, ignoring.", ifName);
                                }
                            }));
                        }));
                    }));
                    return futures;
                });
            }
        } catch (Exception e) {
            LOG.error("Exception observed in handling updation of VPN Interface {}. ", update.getName(), e);
        }
    }
}

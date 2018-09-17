/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.mdsalutil.NWUtil.isIpv4Address;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.serviceutils.upgrade.UpgradeState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RouterInterfaceVrfEntryHandler extends BaseVrfEntryHandler implements IVrfEntryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RouterInterfaceVrfEntryHandler.class);
    private final IMdsalApiManager mdsalManager;
    private final IPv6Handler ipv6Handler;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public RouterInterfaceVrfEntryHandler(final DataBroker dataBroker, final NexthopManager nexthopManager,
            final IMdsalApiManager mdsalManager, final IPv6Handler ipv6Handler, final FibUtil fibUtil,
            final UpgradeState upgradeState, final DataTreeEventCallbackRegistrar eventCallbacks) {
        super(dataBroker, nexthopManager, mdsalManager, fibUtil, upgradeState, eventCallbacks);
        this.mdsalManager = mdsalManager;
        this.ipv6Handler = ipv6Handler;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    public void close() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    @Override
    public void createFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        RouterInterface routerInt = vrfEntry.augmentation(RouterInterface.class);
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
            confTx -> installRouterFibEntries(confTx, vrfEntry, rd, routerInt)), LOG,
            "Error adding flows for {} {} {}", identifier, vrfEntry, rd);
    }

    @Override
    public void updateFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update, String rd) {
        // Not used
    }

    @Override
    public void removeFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        RouterInterface routerInt = vrfEntry.augmentation(RouterInterface.class);
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
            confTx -> removeRouterFibEntries(confTx, vrfEntry, rd, routerInt)), LOG,
            "Error removing flows for {} {} {}", identifier, vrfEntry, rd);
    }

    private void installRouterFibEntries(TypedWriteTransaction<Configuration> confTx, VrfEntry vrfEntry, String rd,
            RouterInterface routerInterface) {
        final VpnInstanceOpDataEntry vpnInstance = getFibUtil().getVpnInstance(rd);
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + rd);
        Preconditions.checkNotNull(vpnInstance.getVpnId(),
                "Vpn Instance with rd " + vpnInstance.getVrfId() + " has null vpnId!");

        // FIXME: separate this out somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnInstance.getVpnInstanceName());
        lock.lock();
        try {
            final Collection<VpnToDpnList> vpnToDpnList;
            if (vrfEntry.getParentVpnRd() != null
                    && FibHelper.isControllerManagedNonSelfImportedRoute(RouteOrigin.value(vrfEntry.getOrigin()))) {
                VpnInstanceOpDataEntry parentVpnInstance =
                        getFibUtil().getVpnInstance(vrfEntry.getParentVpnRd());
                vpnToDpnList = parentVpnInstance != null ? parentVpnInstance.getVpnToDpnList()
                        : vpnInstance.getVpnToDpnList();
            } else {
                vpnToDpnList = vpnInstance.getVpnToDpnList();
            }
            final Long vpnId = vpnInstance.getVpnId();

            if (vpnToDpnList != null) {
                String routerId = routerInterface.getUuid();
                String macAddress = routerInterface.getMacAddress();
                String ipValue = routerInterface.getIpAddress();
                LOG.trace("createFibEntries - Router augmented vrfentry found for for router uuid:{}, ip:{}, mac:{}",
                        routerId, ipValue, macAddress);
                for (VpnToDpnList vpnDpn : vpnToDpnList) {
                    if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                        installRouterFibEntry(confTx, vrfEntry, vpnDpn.getDpnId(), vpnId, ipValue,
                            new MacAddress(macAddress));
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void removeRouterFibEntries(TypedReadWriteTransaction<Configuration> confTx, VrfEntry vrfEntry,
            String rd, RouterInterface routerInterface) throws ExecutionException, InterruptedException {
        final VpnInstanceOpDataEntry vpnInstance = getFibUtil().getVpnInstance(rd);
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + rd);
        Preconditions.checkNotNull(vpnInstance.getVpnId(),
            "Vpn Instance with rd " + vpnInstance.getVrfId() + " has null vpnId!");
        synchronized (vpnInstance.getVpnInstanceName().intern()) {
            final Collection<VpnToDpnList> vpnToDpnList;
            if (vrfEntry.getParentVpnRd() != null
                && FibHelper.isControllerManagedNonSelfImportedRoute(RouteOrigin.value(vrfEntry.getOrigin()))) {
                VpnInstanceOpDataEntry parentVpnInstance =
                    getFibUtil().getVpnInstance(vrfEntry.getParentVpnRd());
                vpnToDpnList = parentVpnInstance != null ? parentVpnInstance.getVpnToDpnList()
                    : vpnInstance.getVpnToDpnList();
            } else {
                vpnToDpnList = vpnInstance.getVpnToDpnList();
            }
            final Long vpnId = vpnInstance.getVpnId();

            if (vpnToDpnList != null) {
                String routerId = routerInterface.getUuid();
                String macAddress = routerInterface.getMacAddress();
                String ipValue = routerInterface.getIpAddress();
                LOG.trace("createFibEntries - Router augmented vrfentry found for for router uuid:{}, ip:{}, mac:{}",
                    routerId, ipValue, macAddress);
                for (VpnToDpnList vpnDpn : vpnToDpnList) {
                    if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                        removeRouterFibEntry(confTx, vrfEntry, vpnDpn.getDpnId(), vpnId, ipValue,
                            new MacAddress(macAddress));
                    }
                }
            }
        }
    }

    void installRouterFibEntry(TypedWriteTransaction<Configuration> confTx, final VrfEntry vrfEntry, BigInteger dpnId,
            long vpnId, String routerInternalIp, MacAddress routerMac) {

        // First install L3_GW_MAC_TABLE flows as it's common for both IPv4 and IPv6
        // address families
        FlowEntity l3GwMacFlowEntity = buildL3vpnGatewayFlow(dpnId, routerMac.getValue(), vpnId);
        mdsalManager.addFlow(confTx, l3GwMacFlowEntity);

        java.util.Optional<Long> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
        if (!optionalLabel.isPresent()) {
            LOG.warn("Routes paths not present. Exiting installRouterFibEntry");
            return;
        }

        String[] subSplit = routerInternalIp.split("/");
        LOG.trace("ADD_FLOW: Building Echo Flow entity for dpid:{}, router_ip:{}, vpnId:{}, subSplit:{} ",
                dpnId, routerInternalIp, vpnId, subSplit[0]);

        if (isIpv4Address(subSplit[0])) {
            installPingResponderFlowEntry(confTx, dpnId, vpnId, subSplit[0], routerMac, optionalLabel.get());
        } else {
            ipv6Handler.installPing6ResponderFlowEntry(confTx, dpnId, vpnId, routerInternalIp, routerMac,
                optionalLabel.get());
        }
    }

    void removeRouterFibEntry(TypedReadWriteTransaction<Configuration> confTx, final VrfEntry vrfEntry,
            BigInteger dpnId, long vpnId, String routerInternalIp, MacAddress routerMac)
            throws ExecutionException, InterruptedException {

        // First install L3_GW_MAC_TABLE flows as it's common for both IPv4 and IPv6
        // address families
        FlowEntity l3GwMacFlowEntity = buildL3vpnGatewayFlow(dpnId, routerMac.getValue(), vpnId);
        mdsalManager.removeFlow(confTx, l3GwMacFlowEntity);

        java.util.Optional<Long> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
        if (!optionalLabel.isPresent()) {
            LOG.warn("Routes paths not present. Exiting installRouterFibEntry");
            return;
        }

        String[] subSplit = routerInternalIp.split("/");
        LOG.trace("DELETE_FLOW: Building Echo Flow entity for dpid:{}, router_ip:{}, vpnId:{}, subSplit:{} ",
            dpnId, routerInternalIp, vpnId, subSplit[0]);

        if (isIpv4Address(subSplit[0])) {
            removePingResponderFlowEntry(confTx, dpnId, optionalLabel.get());
        } else {
            ipv6Handler.removePing6ResponderFlowEntry(confTx, dpnId, optionalLabel.get());
        }
    }
}

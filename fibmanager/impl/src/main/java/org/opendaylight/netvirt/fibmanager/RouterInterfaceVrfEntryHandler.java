/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static org.opendaylight.genius.mdsalutil.NWUtil.isIpv4Address;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.Collection;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.UpgradeState;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
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

    @Inject
    public RouterInterfaceVrfEntryHandler(final DataBroker dataBroker, final NexthopManager nexthopManager,
            final IMdsalApiManager mdsalManager, final IPv6Handler ipv6Handler, final FibUtil fibUtil,
            final UpgradeState upgradeState, final DataTreeEventCallbackRegistrar eventCallbacks) {
        super(dataBroker, nexthopManager, mdsalManager, fibUtil, upgradeState, eventCallbacks);
        this.mdsalManager = mdsalManager;
        this.ipv6Handler = ipv6Handler;
    }

    @Override
    public void close() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    @Override
    public void createFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        RouterInterface routerInt = vrfEntry.augmentation(RouterInterface.class);
        installRouterFibEntries(vrfEntry, rd, NwConstants.ADD_FLOW, routerInt);
    }

    @Override
    public void updateFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update, String rd) {
        // Not used
    }

    @Override
    public void removeFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        RouterInterface routerInt = vrfEntry.augmentation(RouterInterface.class);
        installRouterFibEntries(vrfEntry, rd, NwConstants.DEL_FLOW, routerInt);
    }

    private Boolean installRouterFibEntries(VrfEntry vrfEntry, String rd, int addOrRemove,
            RouterInterface routerInterface) {
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
                        installRouterFibEntry(vrfEntry, vpnDpn.getDpnId(), vpnId, ipValue, new MacAddress(macAddress),
                                addOrRemove);
                    }
                }
            }
        }
        return true;
    }

    public void installRouterFibEntry(final VrfEntry vrfEntry, BigInteger dpnId, long vpnId, String routerInternalIp,
            MacAddress routerMac, int addOrRemove) {

        // First install L3_GW_MAC_TABLE flows as it's common for both IPv4 and IPv6
        // address families
        FlowEntity l3GwMacFlowEntity = buildL3vpnGatewayFlow(dpnId, routerMac.getValue(), vpnId);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.syncInstallFlow(l3GwMacFlowEntity, 1);
        } else {
            mdsalManager.syncRemoveFlow(l3GwMacFlowEntity, 1);
        }

        java.util.Optional<Long> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
        if (!optionalLabel.isPresent()) {
            LOG.warn("Routes paths not present. Exiting installRouterFibEntry");
            return;
        }

        String[] subSplit = routerInternalIp.split("/");
        String addRemoveStr = addOrRemove == NwConstants.ADD_FLOW ? "ADD_FLOW" : "DELETE_FLOW";
        LOG.trace("{}: Building Echo Flow entity for dpid:{}, router_ip:{}, vpnId:{}, subSplit:{} ", addRemoveStr,
                dpnId, routerInternalIp, vpnId, subSplit[0]);

        if (isIpv4Address(subSplit[0])) {
            installPingResponderFlowEntry(dpnId, vpnId, subSplit[0], routerMac, optionalLabel.get(), addOrRemove);
        } else {
            ipv6Handler.installPing6ResponderFlowEntry(dpnId, vpnId, routerInternalIp, routerMac, optionalLabel.get(),
                    addOrRemove);
        }
        return;
    }
}

/*
 * Copyright (c) 2019 Alten Calsoft Labs India Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.netvirt.ipv6service.api.IVirtualNetwork;
import org.opendaylight.netvirt.ipv6service.utils.IpV6NAConfigHelper;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractClusteredSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.config.rev181010.Ipv6serviceConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class Ipv6ConfigChangeListener extends AbstractClusteredSyncDataTreeChangeListener<Ipv6serviceConfig> {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ConfigChangeListener.class);

    private final IfMgr ifMgr;
    private final DataBroker dataBroker;
    private final Ipv6ServiceInterfaceEventListener interfaceStateChangeListener;
    private final IpV6NAConfigHelper ipV6NAConfigHelper;
    private final Ipv6ServiceEosHandler ipv6ServiceEosHandler;

    @Inject
    public Ipv6ConfigChangeListener(final DataBroker dataBroker,
                                    Ipv6ServiceInterfaceEventListener interfaceStateChangeListener,
                                    Ipv6ServiceEosHandler ipv6ServiceEosHandler,
                                    IfMgr ifMgr, IpV6NAConfigHelper ipV6NAConfigHelper) {
        super(dataBroker, new DataTreeIdentifier<>(
                LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Ipv6serviceConfig.class)));
        this.dataBroker = dataBroker;
        this.ifMgr = ifMgr;
        this.interfaceStateChangeListener = interfaceStateChangeListener;
        this.ipV6NAConfigHelper = ipV6NAConfigHelper;
        this.ipv6ServiceEosHandler = ipv6ServiceEosHandler;
        LOG.info("Ipv6ConfigChangeListener () initialized");
    }

    @Override
    public void add(@NonNull Ipv6serviceConfig newDataObject) {
        /*if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }*/

        updateConfigAttr(newDataObject);
        if (Objects.equals(newDataObject.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Switch)) {
            convertControllerToSwitchMode(newDataObject);
        } else {
            convertSwitchToControllerMode(newDataObject);
        }
        printCurrentConfig();
    }

    @Override
    public void remove(@NonNull Ipv6serviceConfig removedDataObject) {
        LOG.info("REMOVE : Mode {}", removedDataObject.getNaResponderMode());
    }

    @Override
    public void update(@NonNull Ipv6serviceConfig original, Ipv6serviceConfig updated) {
        /*if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }*/

        updateConfigAttr(updated);
        if (Objects.equals(original.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Switch)
                && Objects.equals(updated.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Controller)) {
            convertSwitchToControllerMode(updated);
        } else {
            convertControllerToSwitchMode(updated);
        }
        printCurrentConfig();
    }


    private void convertControllerToSwitchMode(Ipv6serviceConfig updated) {
        List<IVirtualNetwork> networks = ifMgr.getNetworkCache();
        int lportTag;
        for (IVirtualNetwork network : networks) {
            VirtualNetwork vnet = ifMgr.getNetwork(network.getNetworkUuid());
            VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(network.getNetworkUuid());
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();

            LOG.info("convertControllerToSwitchMode:: Network : {} routerPort: {}", network, routerPort);
            final long networkElanTag = ifMgr.getNetworkElanTag(network.getNetworkUuid());
            //check if any VM is booted already before subnet is added to the router
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {

                ifMgr.getNDExecutorService().execute(() -> {
                    LOG.info("convertControllerToSwitchMode : Deleting Old flows on Switch Mode");

                    for (Ipv6Address ipv6Address : routerPort.getIpv6Addresses()) {
                        LOG.info("convertControllerToSwitchMode:: Deleting NS punt flow for : {}",
                                ipv6Address.getValue());
                        ifMgr.programIcmpv6NSPuntFlowForAddress(network.getNetworkUuid(), ipv6Address,
                                Ipv6ServiceConstants.DEL_FLOW);
                    }
                    ifMgr.checkVmBootBeforeSubnetAddRouter(dpnIfaceInfo, routerPort,
                            networkElanTag, Ipv6ServiceConstants.ADD_FLOW);
                    for (Ipv6Address ndTarget : routerPort.getIpv6Addresses()) {
                        LOG.info("convertControllerToSwitchM0ode:: Installing NS punt Flow for : {}",
                                ndTarget.getValue());
                        ifMgr.programIcmpv6NsDefaultPuntFlows(routerPort, ndTarget, Ipv6ServiceConstants.ADD_FLOW);
                    }
                });
            }
        }
    }


    private void convertSwitchToControllerMode(Ipv6serviceConfig updated) {
        List<IVirtualNetwork> networks = ifMgr.getNetworkCache();
        for (IVirtualNetwork network : networks) {
            VirtualNetwork vnet = ifMgr.getNetwork(network.getNetworkUuid());
            VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(network.getNetworkUuid());
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            LOG.info("convertSwitchToControllerMode::Network : {} routerPort: {}", network, routerPort);
            final long networkElanTag = ifMgr.getNetworkElanTag(network.getNetworkUuid());
            //check if any VM is booted already before subnet is added to the router
            if (routerPort == null) {
                LOG.warn("convertSwitchToControllerMode:: RouterPort not available for network:{}",
                        vnet.getNetworkUuid());
                continue;
            }
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                ifMgr.getNDExecutorService().execute(() -> {
                    LOG.info("convertSwitchToControllerMode : Deleting Old flows on Switch Mode");
                    ifMgr.checkVmBootBeforeSubnetAddRouter(dpnIfaceInfo, routerPort, networkElanTag,
                            Ipv6ServiceConstants.DEL_FLOW);
                    LOG.info("convertSwitchToControllerMode : Installing new flows on Controller Mode");
                    MacAddress ifaceMac = MacAddress.getDefaultInstance(routerPort.getMacAddress());
                    Ipv6Address llAddr = Ipv6Util.getIpv6LinkLocalAddressFromMac(ifaceMac);
                    for (Ipv6Address ndTarget : routerPort.getIpv6Addresses()) {
                        LOG.info("convertSwitchToControllerMode:: Deleting NS punt Flow for : {}", ndTarget.getValue());
                        //TODO del can be removed as key is same for add flows too
                        ifMgr.programIcmpv6NsDefaultPuntFlows(routerPort, ndTarget, Ipv6ServiceConstants.DEL_FLOW);
                        dpnIfaceInfo.clearNdTargetFlowInfo();
                        ifMgr.programIcmpv6NSPuntFlowForAddress(routerPort.getNetworkID(), ndTarget,
                                Ipv6ServiceConstants.ADD_FLOW);
                    }
                    ifMgr.programIcmpv6NSPuntFlowForAddress(routerPort.getNetworkID(), llAddr,
                            Ipv6ServiceConstants.ADD_FLOW);
                });
            }
        }
    }

    private void updateConfigAttr(Ipv6serviceConfig obj) {

        if (obj.getNaResponderMode() != null) {
            ipV6NAConfigHelper.setNaResponderMode(obj.getNaResponderMode());
        }
        if (obj.getIpv6RouterReachableTime() != null) {
            ipV6NAConfigHelper.setIpv6RouterReachableTimeinMS(obj.getIpv6RouterReachableTime().longValue());
        }
        if (obj.getNsSlowPathProtectionTimeout() != null) {
            ipV6NAConfigHelper.setNsSlowProtectionTimeOutinMs(obj.getNsSlowPathProtectionTimeout().longValue());
        }
    }

    private void printCurrentConfig() {
        LOG.info("printCurrentConfig: NAResponderMode {}, NS-Slow-path-Protection-TimeOut {}ms, RS ReachAbilityTime "
             + "{}ms", ipV6NAConfigHelper.getNaResponderMode(), ipV6NAConfigHelper.getNsSlowProtectionTimeOutinMs(),
             ipV6NAConfigHelper.getIpv6RouterReachableTimeinMS());
    }
}



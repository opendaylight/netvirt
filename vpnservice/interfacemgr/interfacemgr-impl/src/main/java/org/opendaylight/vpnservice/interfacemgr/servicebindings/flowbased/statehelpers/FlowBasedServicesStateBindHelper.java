/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.statehelpers;

import com.google.common.util.concurrent.ListenableFuture;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FlowBasedServicesStateBindHelper {
    private static final Logger LOG = LoggerFactory.getLogger(FlowBasedServicesStateBindHelper.class);

    public static List<ListenableFuture<Void>> bindServicesOnInterface(Interface ifaceState,
                                                             DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        ServicesInfo servicesInfo = FlowBasedServicesUtils.getServicesInfoForInterface(ifaceState.getName(), dataBroker);
        if (servicesInfo == null) {
            return futures;
        }

        List<BoundServices> allServices = servicesInfo.getBoundServices();
        if (allServices == null || allServices.isEmpty()) {
            return futures;
        }

        InterfaceKey interfaceKey = new InterfaceKey(ifaceState.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                    InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);

        if (iface.getType().isAssignableFrom(L2vlan.class)) {
            return bindServiceOnVlan(allServices, iface, ifaceState.getIfIndex(), dataBroker);
        } else if (iface.getType().isAssignableFrom(Tunnel.class)){
             return bindServiceOnTunnel(allServices, iface, ifaceState.getIfIndex(), dataBroker);
        }
        return futures;
    }

    private static List<ListenableFuture<Void>> bindServiceOnTunnel(
            List<BoundServices> allServices,
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface,
            Integer ifIndex, DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        NodeConnectorId nodeConnectorId = FlowBasedServicesUtils.getNodeConnectorIdFromInterface(iface, dataBroker);
        long portNo = Long.parseLong(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
        List<MatchInfo> matches = FlowBasedServicesUtils.getMatchInfoForTunnelPortAtIngressTable (dpId, portNo, iface);
        BoundServices highestPriorityBoundService = FlowBasedServicesUtils.getHighestPriorityService(allServices);
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        if (matches != null) {
            FlowBasedServicesUtils.installInterfaceIngressFlow(dpId, iface, highestPriorityBoundService,
                    t, matches, ifIndex, NwConstants.VLAN_INTERFACE_INGRESS_TABLE);
        }

        for (BoundServices boundService : allServices) {
            if (!boundService.equals(highestPriorityBoundService)) {
                FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, boundService, iface, t, ifIndex, boundService.getServicePriority(), (short) (boundService.getServicePriority()+1));
            }
        }

        futures.add(t.submit());
        return futures;
    }

    private static List<ListenableFuture<Void>> bindServiceOnVlan(
            List<BoundServices> allServices,
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface,
            Integer ifIndex, DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        NodeConnectorId nodeConnectorId = FlowBasedServicesUtils.getNodeConnectorIdFromInterface(iface, dataBroker);
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        Collections.sort(allServices, new Comparator<BoundServices>() {
            @Override
            public int compare(BoundServices serviceInfo1, BoundServices serviceInfo2) {
                return serviceInfo1.getServicePriority().compareTo(serviceInfo2.getServicePriority());
            }
        });
        BoundServices highestPriority = allServices.remove(0);
        short nextServiceIndex = (short) (allServices.size() > 0 ? allServices.get(0).getServicePriority() : highestPriority.getServicePriority() + 1);
        FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, highestPriority, iface, t, ifIndex, IfmConstants.DEFAULT_SERVICE_INDEX, nextServiceIndex);
        BoundServices prev = null;
        for (BoundServices boundService : allServices) {
            if (prev!=null) {
                FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, prev, iface, t, ifIndex, prev.getServicePriority(), boundService.getServicePriority());
            }
            prev = boundService;
        }
        if (prev!=null) {
            FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, prev, iface, t, ifIndex, prev.getServicePriority(), (short) (prev.getServicePriority()+1));
        }
        futures.add(t.submit());
        return futures;

    }

}
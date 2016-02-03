/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.confighelpers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class FlowBasedServicesConfigUnbindHelper {
    private static final Logger LOG = LoggerFactory.getLogger(FlowBasedServicesConfigUnbindHelper.class);

    public static List<ListenableFuture<Void>> unbindService(InstanceIdentifier<BoundServices> instanceIdentifier,
                                                             BoundServices boundServiceOld, DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        String interfaceName =
                InstanceIdentifier.keyOf(instanceIdentifier.firstIdentifierOf(ServicesInfo.class)).getInterfaceName();

        // Get the Parent ServiceInfo
        ServicesInfo servicesInfo = FlowBasedServicesUtils.getServicesInfoForInterface(interfaceName, dataBroker);
        if (servicesInfo == null) {
            LOG.error("Reached Impossible part in the code for bound service: {}", boundServiceOld);
            return futures;
        }

        InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
        Interface iface = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName, dataBroker);
        if (ifState == null || ifState.getOperStatus() == OperStatus.Down) {
            LOG.info("Not unbinding Service since operstatus is {} for Interface: {}",
                    ifState.getOperStatus(), interfaceName);
            return futures;
        }
        List<BoundServices> boundServices = servicesInfo.getBoundServices();

        // Split based on type of interface....
        if (iface.getType().isAssignableFrom(L2vlan.class)) {
            return unbindServiceOnVlan(boundServiceOld, boundServices, iface, ifState.getIfIndex(), dataBroker);
        } else if (iface.getType().isAssignableFrom(Tunnel.class)) {
           return unbindServiceOnTunnel(boundServiceOld, boundServices, iface, ifState.getIfIndex(), dataBroker);
        }
        return futures;
    }

    private static List<ListenableFuture<Void>> unbindServiceOnVlan(
            BoundServices boundServiceOld,
            List<BoundServices> boundServices, Interface iface, int ifIndex,
            DataBroker dataBroker) {

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        NodeConnectorId nodeConnectorId = FlowBasedServicesUtils.getNodeConnectorIdFromInterface(iface, dataBroker);
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));
        if (boundServices.isEmpty()) {
            // Remove default entry from Lport Dispatcher Table.
            FlowBasedServicesUtils.removeLPortDispatcherFlow(dpId, iface, boundServiceOld, t, IfmConstants.DEFAULT_SERVICE_INDEX);
            if (t != null) {
                futures.add(t.submit());
            }
            return futures;
        }
        BoundServices[] highLow = FlowBasedServicesUtils.getHighAndLowPriorityService(boundServices, boundServiceOld);
        BoundServices low = highLow[0];
        BoundServices high = highLow[1];
        // This means the one removed was the highest priority service
        if (high == null) {
            LOG.trace("Deleting table entry for service {}, match service index {}", boundServiceOld, IfmConstants.DEFAULT_SERVICE_INDEX);
            FlowBasedServicesUtils.removeLPortDispatcherFlow(dpId, iface, boundServiceOld, t, IfmConstants.DEFAULT_SERVICE_INDEX);
            if (low != null) {
                //delete the lower services flow entry.
                LOG.trace("Deleting table entry for lower service {}, match service index {}", low, low.getServicePriority());
                FlowBasedServicesUtils.removeLPortDispatcherFlow(dpId, iface, low, t, low.getServicePriority());
                BoundServices lower = FlowBasedServicesUtils.getHighAndLowPriorityService(boundServices, low)[0];
                short lowerServiceIndex = (short) ((lower!=null) ? lower.getServicePriority() : low.getServicePriority() + 1);
                LOG.trace("Installing new entry for lower service {}, match service index {}, update service index {}", low, IfmConstants.DEFAULT_SERVICE_INDEX, lowerServiceIndex);
                FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, low, iface, t, ifIndex, IfmConstants.DEFAULT_SERVICE_INDEX, lowerServiceIndex);
            }
        } else {
            LOG.trace("Deleting table entry for service {}, match service index {}", boundServiceOld, boundServiceOld.getServicePriority());
            FlowBasedServicesUtils.removeLPortDispatcherFlow(dpId, iface, boundServiceOld, t, boundServiceOld.getServicePriority());
            short lowerServiceIndex = (short) ((low!=null) ? low.getServicePriority() : boundServiceOld.getServicePriority() + 1);
            BoundServices highest = FlowBasedServicesUtils.getHighestPriorityService(boundServices);
            if (high.equals(highest)) {
                LOG.trace("Update the existing higher service {}, match service index {}, update service index {}", high, IfmConstants.DEFAULT_SERVICE_INDEX, lowerServiceIndex);
                FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, high, iface, t, ifIndex, IfmConstants.DEFAULT_SERVICE_INDEX, lowerServiceIndex);
            } else {
                LOG.trace("Update the existing higher service {}, match service index {}, update service index {}", high, high.getServicePriority(), lowerServiceIndex);
                FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, high, iface, t, ifIndex, high.getServicePriority(), lowerServiceIndex);
            }
        }
        futures.add(t.submit());
        return futures;
    }

    private static List<ListenableFuture<Void>> unbindServiceOnTunnel(
            BoundServices boundServiceOld,
            List<BoundServices> boundServices, Interface iface, int ifIndex,
            DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();

        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        NodeConnectorId nodeConnectorId = FlowBasedServicesUtils.getNodeConnectorIdFromInterface(iface, dataBroker);
        long portNo = Long.parseLong(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));

        if (boundServices.isEmpty()) {
            // Remove entry from Ingress Table.
            FlowBasedServicesUtils.removeIngressFlow(iface, boundServiceOld, dpId, t);
            if (t != null) {
                futures.add(t.submit());
            }
            return futures;
        }

        Map<Short, BoundServices> tmpServicesMap = new ConcurrentHashMap<>();
        short highestPriority = 0xFF;
        for (BoundServices boundService : boundServices) {
            tmpServicesMap.put(boundService.getServicePriority(), boundService);
            if (boundService.getServicePriority() < highestPriority) {
                highestPriority = boundService.getServicePriority();
            }
        }

        if (highestPriority < boundServiceOld.getServicePriority()) {
            FlowBasedServicesUtils.removeLPortDispatcherFlow(dpId, iface, boundServiceOld, t, boundServiceOld.getServicePriority());
            if (t != null) {
                futures.add(t.submit());
            }
            return futures;
        }

        List<MatchInfo> matches = null;
        matches = FlowBasedServicesUtils.getMatchInfoForTunnelPortAtIngressTable (dpId, portNo, iface);

        BoundServices toBeMoved = tmpServicesMap.get(highestPriority);
        FlowBasedServicesUtils.removeIngressFlow(iface, boundServiceOld, dpId, t);
        FlowBasedServicesUtils.installInterfaceIngressFlow(dpId, iface, toBeMoved, t,
                matches, ifIndex, NwConstants.VLAN_INTERFACE_INGRESS_TABLE);
        FlowBasedServicesUtils.removeLPortDispatcherFlow(dpId, iface, toBeMoved, t, toBeMoved.getServicePriority());

        if (t != null) {
            futures.add(t.submit());
        }
        return futures;
    }

}
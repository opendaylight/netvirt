/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.confighelpers;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FlowBasedServicesConfigUnbindHelper {
    private static final Logger LOG = LoggerFactory.getLogger(FlowBasedServicesConfigUnbindHelper.class);

    public static List<ListenableFuture<Void>> unbindService(InstanceIdentifier<BoundServices> instanceIdentifier,
                                                             BoundServices boundServiceOld, DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();

        String interfaceName =
                InstanceIdentifier.keyOf(instanceIdentifier.firstIdentifierOf(ServicesInfo.class)).getInterfaceName();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName, dataBroker);
        if (ifState == null || ifState.getOperStatus() == OperStatus.Down) {
            LOG.info("Not unbinding Service since operstatus is {} for Interface: {}",
                    ifState.getOperStatus(), interfaceName);
            return futures;
        }

        // Get the Parent ServiceInfo
        ServicesInfo servicesInfo = FlowBasedServicesUtils.getServicesInfoForInterface(interfaceName, dataBroker);
        if (servicesInfo == null) {
            LOG.error("Reached Impossible part in the code for bound service: {}", boundServiceOld);
            return futures;
        }

        InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
        Interface iface = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
        NodeConnectorId nodeConnectorId = FlowBasedServicesUtils.getNodeConnectorIdFromInterface(iface, dataBroker);
        long portNo = Long.parseLong(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));

        int vlanId = 0;
        if (iface.getType().isAssignableFrom(L2vlan.class)) {
            IfL2vlan l2vlan = iface.getAugmentation(IfL2vlan.class);
            if(l2vlan != null) {
                vlanId = iface.getAugmentation(IfL2vlan.class).getVlanId().getValue();
            }
        }
        List<BoundServices> boundServices = servicesInfo.getBoundServices();
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
            FlowBasedServicesUtils.removeLPortDispatcherFlow(dpId, iface, boundServiceOld, t);
            if (t != null) {
                futures.add(t.submit());
            }
            return futures;
        }

        List<MatchInfo> matches = null;
        if (iface.getType().isAssignableFrom(L2vlan.class)) {
            matches = FlowBasedServicesUtils.getMatchInfoForVlanPortAtIngressTable(dpId, portNo, vlanId);
        } else if (iface.getType().isAssignableFrom(Tunnel.class)){
            matches = FlowBasedServicesUtils.getMatchInfoForTunnelPortAtIngressTable (dpId, portNo, iface);
        }

        BoundServices toBeMoved = tmpServicesMap.get(highestPriority);
        FlowBasedServicesUtils.removeIngressFlow(iface, boundServiceOld, dpId, t);
        FlowBasedServicesUtils.installInterfaceIngressFlow(dpId, iface.getName(), vlanId, toBeMoved, dataBroker, t,
                matches, ifState.getIfIndex(), IfmConstants.VLAN_INTERFACE_INGRESS_TABLE);
        FlowBasedServicesUtils.removeLPortDispatcherFlow(dpId, iface, toBeMoved, t);

        if (t != null) {
            futures.add(t.submit());
        }
        return futures;
    }
}
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
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
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

public class FlowBasedServicesConfigBindHelper {
    private static final Logger LOG = LoggerFactory.getLogger(FlowBasedServicesConfigBindHelper.class);

    public static List<ListenableFuture<Void>> bindService(InstanceIdentifier<BoundServices> instanceIdentifier,
                                                           BoundServices boundServiceNew, DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        String interfaceName =
                InstanceIdentifier.keyOf(instanceIdentifier.firstIdentifierOf(ServicesInfo.class)).getInterfaceName();

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName, dataBroker);
        if (ifState == null || ifState.getOperStatus() == OperStatus.Down) {
            LOG.warn("Interface not up, not Binding Service for Interface: {}", interfaceName);
            return futures;
        }

        // Get the Parent ServiceInfo

        ServicesInfo servicesInfo = FlowBasedServicesUtils.getServicesInfoForInterface(interfaceName, dataBroker);
        if (servicesInfo == null) {
            LOG.error("Reached Impossible part 1 in the code during bind service for: {}", boundServiceNew);
            return futures;
        }

        InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
        Interface iface = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);

        List<BoundServices> allServices = servicesInfo.getBoundServices();
        if (allServices.isEmpty()) {
            LOG.error("Reached Impossible part 2 in the code during bind service for: {}", boundServiceNew);
            return futures;
        }

        NodeConnectorId nodeConnectorId = FlowBasedServicesUtils.getNodeConnectorIdFromInterface(iface, dataBroker);
        long portNo = Long.parseLong(IfmUtil.getPortNoFromNodeConnectorId(nodeConnectorId));
        BigInteger dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId));

        if (allServices.size() == 1) {
            // If only one service present, install instructions in table 0.
            int vlanId = 0;
            List<MatchInfo> matches = null;
            if (iface.getType().isAssignableFrom(L2vlan.class)) {
                matches = FlowBasedServicesUtils.getMatchInfoForVlanPortAtIngressTable(dpId, portNo, iface);
            } else if (iface.getType().isAssignableFrom(Tunnel.class)){
                matches = FlowBasedServicesUtils.getMatchInfoForTunnelPortAtIngressTable (dpId, portNo, iface);
            }

            if (matches != null) {
                FlowBasedServicesUtils.installInterfaceIngressFlow(dpId, iface, boundServiceNew,
                        transaction, matches, ifState.getIfIndex(), NwConstants.VLAN_INTERFACE_INGRESS_TABLE);
            }

            if (transaction != null) {
                futures.add(transaction.submit());
            }
            return futures;
        }

        boolean isCurrentServiceHighestPriority = true;
        Map<Short, BoundServices> tmpServicesMap = new ConcurrentHashMap<>();
        short highestPriority = 0xFF;
        for (BoundServices boundService : allServices) {
            if (boundService.getServicePriority() < boundServiceNew.getServicePriority()) {
                isCurrentServiceHighestPriority = false;
                break;
            }
            if (!boundService.equals(boundServiceNew)) {
                tmpServicesMap.put(boundService.getServicePriority(), boundService);
                if (boundService.getServicePriority() < highestPriority) {
                    highestPriority = boundService.getServicePriority();
                }
            }
        }

        if (!isCurrentServiceHighestPriority) {
            FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, boundServiceNew, iface, transaction,
                    ifState.getIfIndex());
        } else {
            BoundServices serviceToReplace = tmpServicesMap.get(highestPriority);
            FlowBasedServicesUtils.installLPortDispatcherFlow(dpId, serviceToReplace, iface, transaction,
                    ifState.getIfIndex());
            List<MatchInfo> matches = null;
            if (iface.getType().isAssignableFrom(L2vlan.class)) {
                matches = FlowBasedServicesUtils.getMatchInfoForVlanPortAtIngressTable(dpId, portNo, iface);
            } else if (iface.getType().isAssignableFrom(Tunnel.class)){
                matches = FlowBasedServicesUtils.getMatchInfoForTunnelPortAtIngressTable (dpId, portNo, iface);
            }

            if (matches != null) {

                WriteTransaction removeFlowTransaction = dataBroker.newWriteOnlyTransaction();
                FlowBasedServicesUtils.removeIngressFlow(iface, serviceToReplace, dpId, removeFlowTransaction);
                futures.add(removeFlowTransaction.submit());

                WriteTransaction installFlowTransaction = dataBroker.newWriteOnlyTransaction();
                FlowBasedServicesUtils.installInterfaceIngressFlow(dpId, iface, boundServiceNew, installFlowTransaction,
                        matches, ifState.getIfIndex(), NwConstants.VLAN_INTERFACE_INGRESS_TABLE);
                futures.add(installFlowTransaction.submit());
            }
        }

        if (transaction != null) {
            futures.add(transaction.submit());
        }
        return futures;
    }
}
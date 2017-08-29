/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterAssociatedToVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterDisassociatedFromVpn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RouterToVpnListener implements NeutronvpnListener {
    private static final Logger LOG = LoggerFactory.getLogger(RouterToVpnListener.class);
    private final DataBroker dataBroker;
    private final FloatingIPListener floatingIpListener;
    private final OdlInterfaceRpcService interfaceManager;
    private final ExternalRoutersListener externalRoutersListener;

    @Inject
    public RouterToVpnListener(final DataBroker dataBroker,
                               final FloatingIPListener floatingIpListener,
                               final OdlInterfaceRpcService interfaceManager,
                               final ExternalRoutersListener externalRoutersListener) {
        this.dataBroker = dataBroker;
        this.floatingIpListener = floatingIpListener;
        this.interfaceManager = interfaceManager;
        this.externalRoutersListener = externalRoutersListener;
    }

    /**
     * router association to vpn.
     */
    @Override
    public void onRouterAssociatedToVpn(RouterAssociatedToVpn notification) {
        String routerName = notification.getRouterId().getValue();
        String vpnName = notification.getVpnId().getValue();
        WriteTransaction writeFlowInvTx = dataBroker.newWriteOnlyTransaction();
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        //check router is associated to external network
        String extNetwork = NatUtil.getAssociatedExternalNetwork(dataBroker, routerName);
        if (extNetwork != null) {
            LOG.debug("onRouterAssociatedToVpn : Router {} is associated with ext nw {}", routerName, extNetwork);
            handleDNATConfigurationForRouterAssociation(routerName, vpnName, extNetwork);
            externalRoutersListener.changeLocalVpnIdToBgpVpnId(routerName, vpnName, writeFlowInvTx);
        } else {
            LOG.debug("onRouterAssociatedToVpn : Ignoring the Router {} association with VPN {} "
                    + "since it is not external router", routerName);
        }
        futures.add(NatUtil.waitForTransactionToComplete(writeFlowInvTx));
    }

    /**
     * router disassociation from vpn.
     */
    @Override
    public void onRouterDisassociatedFromVpn(RouterDisassociatedFromVpn notification) {
        String routerName = notification.getRouterId().getValue();
        String vpnName = notification.getVpnId().getValue();
        WriteTransaction writeFlowInvTx = dataBroker.newWriteOnlyTransaction();
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        //check router is associated to external network
        String extNetwork = NatUtil.getAssociatedExternalNetwork(dataBroker, routerName);
        if (extNetwork != null) {
            LOG.debug("onRouterDisassociatedFromVpn : Router {} is associated with ext nw {}", routerName, extNetwork);
            handleDNATConfigurationForRouterDisassociation(routerName, vpnName, extNetwork);
            externalRoutersListener.changeBgpVpnIdToLocalVpnId(routerName, vpnName, writeFlowInvTx);
        } else {
            LOG.debug("onRouterDisassociatedFromVpn : Ignoring the Router {} association with VPN {} "
                    + "since it is not external router", routerName);
        }
        futures.add(NatUtil.waitForTransactionToComplete(writeFlowInvTx));
    }

    void handleDNATConfigurationForRouterAssociation(String routerName, String vpnName, String externalNetwork) {
        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts =
            MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsId);
        if (!optRouterPorts.isPresent()) {
            LOG.debug("handleDNATConfigurationForRouterAssociation : Could not read Router Ports data "
                    + "object with id: {} to handle associate vpn {}", routerName, vpnName);
            return;
        }
        Uuid networkId = Uuid.getDefaultInstance(externalNetwork);
        RouterPorts routerPorts = optRouterPorts.get();
        List<Ports> interfaces = routerPorts.getPorts();
        Map<String, BigInteger> portToDpnMap = new HashMap<>();
        for (Ports port : interfaces) {
            String portName = port.getPortName();
            BigInteger dpnId = NatUtil.getDpnForInterface(interfaceManager, portName);
            if (dpnId.equals(BigInteger.ZERO)) {
                LOG.warn("handleDNATConfigurationForRouterAssociation : DPN not found for {}, "
                        + "skip handling of router {} association with vpn", portName, routerName, vpnName);
                continue;
            }
            portToDpnMap.put(portName, dpnId);
            List<InternalToExternalPortMap> intExtPortMapList = port.getInternalToExternalPortMap();
            for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
                //remove all NAT related entries with routerName
                //floatingIpListener.removeNATOnlyFlowEntries(dpnId, portName, routerName, null,
                // intExtPortMap.getInternalIp(), externalIp);
                //Create NAT entries with VPN Id
                LOG.debug("handleDNATConfigurationForRouterAssociation : Updating DNAT flows with VPN metadata {} ",
                        vpnName);
                floatingIpListener.createNATOnlyFlowEntries(dpnId, routerName, vpnName, networkId, intExtPortMap);
            }
        }
    }

    void handleDNATConfigurationForRouterDisassociation(String routerName, String vpnName, String externalNetwork) {
        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts =
            MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsId);
        if (!optRouterPorts.isPresent()) {
            LOG.error("handleDNATConfigurationForRouterDisassociation : Could not read Router Ports "
                    + "data object with id: {} to handle disassociate vpn {}", routerName, vpnName);
            return;
        }
        Uuid networkId = Uuid.getDefaultInstance(externalNetwork);
        RouterPorts routerPorts = optRouterPorts.get();
        List<Ports> interfaces = routerPorts.getPorts();
        for (Ports port : interfaces) {
            String portName = port.getPortName();
            BigInteger dpnId = NatUtil.getDpnForInterface(interfaceManager, portName);
            if (dpnId.equals(BigInteger.ZERO)) {
                LOG.debug("handleDNATConfigurationForRouterDisassociation : DPN not found for {}, "
                        + "skip handling of router {} association with vpn", portName, routerName, vpnName);
                continue;
            }
            List<InternalToExternalPortMap> intExtPortMapList = port.getInternalToExternalPortMap();
            for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
                //remove all NAT related entries with routerName
                //floatingIpListener.removeNATOnlyFlowEntries(dpnId, portName, routerName, vpnName,
                // intExtPortMap.getInternalIp(), externalIp);
                //Create NAT entries with VPN Id
                floatingIpListener.createNATOnlyFlowEntries(dpnId, routerName, null, networkId, intExtPortMap);
            }
        }
    }

}
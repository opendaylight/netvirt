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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortAddedToSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortRemovedFromSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterAssociatedToVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterDisassociatedFromVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetAddedToVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetDeletedFromVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetUpdatedInVpn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouterToVpnListener implements NeutronvpnListener {
    private static final Logger LOG = LoggerFactory.getLogger(RouterToVpnListener.class);
    private final DataBroker dataBroker;
    private final FloatingIPListener floatingIpListener;
    private final OdlInterfaceRpcService interfaceManager;
    private final ExternalRoutersListener externalRoutersListener;

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
     * router association to vpn
     *
     */
    @Override
    public void onRouterAssociatedToVpn(RouterAssociatedToVpn notification) {
        String routerName = notification.getRouterId().getValue();
        String vpnName = notification.getVpnId().getValue();
        //check router is associated to external network
        String extNetwork = NatUtil.getAssociatedExternalNetwork(dataBroker, routerName);
        if(extNetwork != null) {
            LOG.debug("Router {} is associated with ext nw {}", routerName, extNetwork);
            handleDNATConfigurationForRouterAssociation(routerName, vpnName, extNetwork);
            externalRoutersListener.changeLocalVpnIdToBgpVpnId(routerName, vpnName);
        } else {
            LOG.debug("Ignoring the Router {} association with VPN {} since it is not external router", routerName);
        }

    }

    /**
     * router disassociation from vpn
     *
     */
    @Override
    public void onRouterDisassociatedFromVpn(RouterDisassociatedFromVpn notification) {
        String routerName = notification.getRouterId().getValue();
        String vpnName = notification.getVpnId().getValue();
        //check router is associated to external network
        String extNetwork = NatUtil.getAssociatedExternalNetwork(dataBroker, routerName);
        if(extNetwork != null) {
            LOG.debug("Router {} is associated with ext nw {}", routerName, extNetwork);
            handleDNATConfigurationForRouterDisassociation(routerName, vpnName, extNetwork);
            externalRoutersListener.changeBgpVpnIdToLocalVpnId(routerName, vpnName);
        } else {
            LOG.debug("Ignoring the Router {} association with VPN {} since it is not external router", routerName);
        }
    }

    void handleDNATConfigurationForRouterAssociation(String routerName, String vpnName, String externalNetwork) {
        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsId);
        if(!optRouterPorts.isPresent()) {
            LOG.debug("Could not read Router Ports data object with id: {} to handle associate vpn {}", routerName, vpnName);
            return;
        }
        Uuid networkId = Uuid.getDefaultInstance(externalNetwork);
        RouterPorts routerPorts = optRouterPorts.get();
        List<Ports> interfaces = routerPorts.getPorts();
        Map<String, BigInteger> portToDpnMap = new HashMap<>();
        for(Ports port : interfaces) {
            String portName = port.getPortName();
            BigInteger dpnId = getDpnForInterface(interfaceManager, portName);
            if(dpnId.equals(BigInteger.ZERO)) {
                LOG.debug("DPN not found for {}, skip handling of router {} association with vpn", portName, routerName, vpnName);
                continue;
            }
            portToDpnMap.put(portName, dpnId);
            List<IpMapping> ipMapping = port.getIpMapping();
            for(IpMapping ipMap : ipMapping) {
                String externalIp = ipMap.getExternalIp();
                //remove all NAT related entries with routerName
                //floatingIpListener.removeNATOnlyFlowEntries(dpnId, portName, routerName, null, ipMap.getInternalIp(), externalIp);
                //Create NAT entries with VPN Id
                LOG.debug("Updating DNAT flows with VPN metadata {} ", vpnName);
                floatingIpListener.createNATOnlyFlowEntries(dpnId, portName, routerName, vpnName, networkId, ipMap.getInternalIp(), externalIp);
            }
        }
    }

    void handleDNATConfigurationForRouterDisassociation(String routerName, String vpnName, String externalNetwork) {
        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsId);
        if(!optRouterPorts.isPresent()) {
            LOG.debug("Could not read Router Ports data object with id: {} to handle disassociate vpn {}", routerName, vpnName);
            return;
        }
        Uuid networkId = Uuid.getDefaultInstance(externalNetwork);
        RouterPorts routerPorts = optRouterPorts.get();
        List<Ports> interfaces = routerPorts.getPorts();
        for(Ports port : interfaces) {
            String portName = port.getPortName();
            BigInteger dpnId = getDpnForInterface(interfaceManager, portName);
            if(dpnId.equals(BigInteger.ZERO)) {
                LOG.debug("DPN not found for {}, skip handling of router {} association with vpn", portName, routerName, vpnName);
                continue;
            }
            List<IpMapping> ipMapping = port.getIpMapping();
            for(IpMapping ipMap : ipMapping) {
                String externalIp = ipMap.getExternalIp();
                //remove all NAT related entries with routerName
                //floatingIpListener.removeNATOnlyFlowEntries(dpnId, portName, routerName, vpnName, ipMap.getInternalIp(), externalIp);
                //Create NAT entries with VPN Id
                floatingIpListener.createNATOnlyFlowEntries(dpnId, portName, routerName, null, networkId, ipMap.getInternalIp(), externalIp);
            }
        }
    }

    private BigInteger getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        BigInteger nodeId = BigInteger.ZERO;
        try {
            GetDpidFromInterfaceInput
                    dpIdInput =
                    new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>>
                    dpIdOutput =
                    interfaceManagerRpcService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if (dpIdResult.isSuccessful()) {
                nodeId = dpIdResult.getResult().getDpid();
            } else {
                LOG.error("Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting dpn for interface {}", ifName,  e);
        }
        return nodeId;
    }

    @Override
    public void onSubnetAddedToVpn(SubnetAddedToVpn notification) {
    }

    @Override
    public void onSubnetDeletedFromVpn(SubnetDeletedFromVpn notification) {
    }

    @Override
    public void onPortAddedToSubnet(PortAddedToSubnet notification) {
    }

    @Override
    public void onPortRemovedFromSubnet(PortRemovedFromSubnet notification) {
    }

    @Override
    public void onSubnetUpdatedInVpn(SubnetUpdatedInVpn notification) {
    }

}
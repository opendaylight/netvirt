/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice.jobs;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.dhcpservice.DhcpExternalTunnelManager;
import org.opendaylight.netvirt.dhcpservice.DhcpManager;
import org.opendaylight.netvirt.dhcpservice.DhcpServiceUtils;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput.ArpReponderInputBuilder;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.subnet.dhcp.port.data.SubnetToDhcpPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpInterfaceAddJob implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpInterfaceAddJob.class);

    private final DhcpManager dhcpManager;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final Interface interfaceAdd;
    private final BigInteger dpnId;
    private final IInterfaceManager interfaceManager;
    private final IElanService elanService;
    private final ItmRpcService itmRpcService;

    public DhcpInterfaceAddJob(DhcpManager dhcpManager, DhcpExternalTunnelManager dhcpExternalTunnelManager,
                               DataBroker dataBroker, Interface interfaceAdd, BigInteger dpnId,
                               IInterfaceManager interfaceManager, IElanService elanService,
                               ItmRpcService itmRpcService) {
        this.dhcpManager = dhcpManager;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interfaceAdd = interfaceAdd;
        this.dpnId = dpnId;
        this.interfaceManager = interfaceManager;
        this.elanService = elanService;
        this.itmRpcService = itmRpcService;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws ExecutionException, InterruptedException {
        String interfaceName = interfaceAdd.getName();
        LOG.trace("Received add DCN for interface {}, dpid {}", interfaceName, dpnId);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName);
        if (iface != null) {
            IfTunnel tunnelInterface = iface.augmentation(IfTunnel.class);
            if (tunnelInterface != null && !tunnelInterface.isInternal()) {
                IpAddress tunnelIp = tunnelInterface.getTunnelDestination();
                List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(dataBroker);
                if (dpns.contains(dpnId)) {
                    return dhcpExternalTunnelManager.handleTunnelStateUp(tunnelIp, dpnId);
                }
                return Collections.emptyList();
            }
        }
        if (!dpnId.equals(DhcpMConstants.INVALID_DPID)) {
            Port port = dhcpManager.getNeutronPort(interfaceName);
            Subnet subnet = dhcpManager.getNeutronSubnet(port);
            if (null == subnet || !subnet.isEnableDhcp()) {
                LOG.debug("DHCP is not enabled for port {}", port.getName());
                return Collections.emptyList();
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            // Support for VM migration use cases.
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> DhcpServiceUtils.bindDhcpService(interfaceName, NwConstants.DHCP_TABLE, tx)));
            LOG.info("DhcpInterfaceEventListener add isEnableDhcp:{}", subnet.isEnableDhcp());
            futures.addAll(installDhcpEntries(interfaceAdd.getName(), dpnId));
            LOG.trace("Checking ElanDpnInterface {} for dpn {} ", interfaceName, dpnId);
            String subnetId = subnet.getUuid().getValue();
            java.util.Optional<SubnetToDhcpPort> subnetToDhcp = DhcpServiceUtils
                    .getSubnetDhcpPortData(dataBroker, subnetId);
            if (!subnetToDhcp.isPresent()) {
                return Collections.emptyList();
            }
            LOG.trace("Installing the Arp responder for interface {} with DHCP MAC {} & IP {}.", interfaceName,
                    subnetToDhcp.get().getPortMacaddress(), subnetToDhcp.get().getPortFixedip());
            ArpReponderInputBuilder builder = new ArpReponderInputBuilder();
            builder.setDpId(dpnId).setInterfaceName(interfaceName).setSpa(subnetToDhcp.get().getPortFixedip())
                    .setSha(subnetToDhcp.get().getPortMacaddress()).setLportTag(interfaceAdd.getIfIndex());
            builder.setInstructions(ArpResponderUtil.getInterfaceInstructions(interfaceManager, interfaceName,
                    subnetToDhcp.get().getPortFixedip(), subnetToDhcp.get().getPortMacaddress(), itmRpcService));
            elanService.addArpResponderFlow(builder.buildForInstallFlow());
            return futures;
        }
        return Collections.emptyList();
    }

    private List<ListenableFuture<Void>> installDhcpEntries(String interfaceName, BigInteger dpId)
        throws ExecutionException, InterruptedException {
        String vmMacAddress = txRunner.applyWithNewReadWriteTransactionAndSubmit(OPERATIONAL,
            tx -> DhcpServiceUtils.getAndUpdateVmMacAddress(tx, interfaceName, dhcpManager)).get();
        return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
            tx -> dhcpManager.installDhcpEntries(dpId, vmMacAddress, tx)));
    }
}

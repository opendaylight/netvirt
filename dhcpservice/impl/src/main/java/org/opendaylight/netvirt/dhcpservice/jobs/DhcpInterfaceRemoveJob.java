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
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.dhcpservice.DhcpExternalTunnelManager;
import org.opendaylight.netvirt.dhcpservice.DhcpManager;
import org.opendaylight.netvirt.dhcpservice.DhcpServiceUtils;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.InterfaceNameMacAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710._interface.name.mac.addresses.InterfaceNameMacAddressKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.subnet.dhcp.port.data.SubnetToDhcpPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpInterfaceRemoveJob implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpInterfaceRemoveJob.class);

    private final DhcpManager dhcpManager;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final Interface interfaceDel;
    private final BigInteger dpnId;
    private final IInterfaceManager interfaceManager;
    private final IElanService elanService;
    private final Port port;

    public DhcpInterfaceRemoveJob(DhcpManager dhcpManager, DhcpExternalTunnelManager dhcpExternalTunnelManager,
                                  DataBroker dataBroker,
                                  Interface interfaceDel, BigInteger dpnId, IInterfaceManager interfaceManager,
                                  IElanService elanService, Port port) {
        this.dhcpManager = dhcpManager;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interfaceDel = interfaceDel;
        this.dpnId = dpnId;
        this.interfaceManager = interfaceManager;
        this.elanService = elanService;
        this.port = port;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws ExecutionException, InterruptedException {
        String interfaceName = interfaceDel.getName();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName);
        if (iface != null) {
            IfTunnel tunnelInterface = iface.augmentation(IfTunnel.class);
            if (tunnelInterface != null && !tunnelInterface.isInternal()) {
                IpAddress tunnelIp = tunnelInterface.getTunnelDestination();
                List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(dataBroker);
                if (dpns.contains(dpnId)) {
                    return dhcpExternalTunnelManager.handleTunnelStateDown(tunnelIp, dpnId);
                }
                return Collections.emptyList();
            }
        }
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        // Support for VM migration use cases.
        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
            tx -> DhcpServiceUtils.unbindDhcpService(interfaceName, tx)));
        java.util.Optional<String> subnetId = DhcpServiceUtils.getNeutronSubnetId(port);
        if (subnetId.isPresent()) {
            java.util.Optional<SubnetToDhcpPort> subnetToDhcp = DhcpServiceUtils.getSubnetDhcpPortData(dataBroker,
                    subnetId.get());
            if (subnetToDhcp.isPresent()) {
                LOG.trace("Removing ArpResponder flow for last interface {} on DPN {}", interfaceName, dpnId);
                ArpResponderInput arpInput = new ArpResponderInput.ArpReponderInputBuilder().setDpId(dpnId)
                        .setInterfaceName(interfaceName).setSpa(subnetToDhcp.get().getPortFixedip())
                        .setLportTag(interfaceDel.getIfIndex()).buildForRemoveFlow();
                elanService.removeArpResponderFlow(arpInput);
            }
        }
        futures.addAll(unInstallDhcpEntries(interfaceDel.getName(), dpnId));
        return futures;
    }

    private List<ListenableFuture<Void>> unInstallDhcpEntries(String interfaceName, BigInteger dpId)
        throws ExecutionException, InterruptedException {
        String vmMacAddress = txRunner.applyWithNewReadWriteTransactionAndSubmit(OPERATIONAL,
            tx -> getAndRemoveVmMacAddress(tx, interfaceName)).get();
        return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
            tx -> dhcpManager.unInstallDhcpEntries(dpId, vmMacAddress, tx)));
    }

    @Nullable
    private String getAndRemoveVmMacAddress(TypedReadWriteTransaction<Datastore.Operational> tx, String interfaceName)
        throws ExecutionException, InterruptedException {
        InstanceIdentifier<InterfaceNameMacAddress> instanceIdentifier =
                InstanceIdentifier.builder(InterfaceNameMacAddresses.class)
                        .child(InterfaceNameMacAddress.class, new InterfaceNameMacAddressKey(interfaceName)).build();
        return tx.read(instanceIdentifier).get().toJavaUtil().map(
            interfaceNameMacAddress -> {
                String vmMacAddress = interfaceNameMacAddress.getMacAddress();
                LOG.trace("Entry for interface found in InterfaceNameVmMacAddress map {}, {}", interfaceName,
                        vmMacAddress);
                tx.delete(instanceIdentifier);
                return vmMacAddress;
            }).orElseGet(() -> {
                LOG.trace("Entry for interface {} missing in InterfaceNameVmMacAddress map", interfaceName);
                return null;
            });
    }
}

/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice.jobs;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
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
    DhcpManager dhcpManager;
    DhcpExternalTunnelManager dhcpExternalTunnelManager;
    DataBroker dataBroker;
    Interface interfaceDel;
    BigInteger dpnId;
    IInterfaceManager interfaceManager;
    private final IElanService elanService;
    private static final FutureCallback<Void> DEFAULT_CALLBACK = new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
            LOG.debug("Success in Datastore write operation");
        }

        @Override
        public void onFailure(Throwable error) {
            LOG.error("Error in Datastore write operation", error);
        }
    };

    public DhcpInterfaceRemoveJob(DhcpManager dhcpManager, DhcpExternalTunnelManager dhcpExternalTunnelManager,
                                  DataBroker dataBroker,
                                  Interface interfaceDel, BigInteger dpnId, IInterfaceManager interfaceManager,
                                  IElanService elanService) {
        super();
        this.dhcpManager = dhcpManager;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.dataBroker = dataBroker;
        this.interfaceDel = interfaceDel;
        this.dpnId = dpnId;
        this.interfaceManager = interfaceManager;
        this.elanService = elanService;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        String interfaceName = interfaceDel.getName();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName);
        if (iface != null) {
            IfTunnel tunnelInterface = iface.getAugmentation(IfTunnel.class);
            if (tunnelInterface != null && !tunnelInterface.isInternal()) {
                IpAddress tunnelIp = tunnelInterface.getTunnelDestination();
                List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(dataBroker);
                if (dpns.contains(dpnId)) {
                    dhcpExternalTunnelManager.handleTunnelStateDown(tunnelIp, dpnId, futures);
                }
                return futures;
            }
        }
        Port port = dhcpManager.getNeutronPort(interfaceName);
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
        unInstallDhcpEntries(interfaceDel.getName(), dpnId, futures);
        return futures;
    }

    private void unInstallDhcpEntries(String interfaceName, BigInteger dpId, List<ListenableFuture<Void>> futures) {
        String vmMacAddress = getAndRemoveVmMacAddress(interfaceName);
        WriteTransaction flowTx = dataBroker.newWriteOnlyTransaction();
        dhcpManager.unInstallDhcpEntries(dpId, vmMacAddress, flowTx);
        futures.add(flowTx.submit());
    }

    private String getAndRemoveVmMacAddress(String interfaceName) {
        InstanceIdentifier<InterfaceNameMacAddress> instanceIdentifier =
                InstanceIdentifier.builder(InterfaceNameMacAddresses.class)
                        .child(InterfaceNameMacAddress.class, new InterfaceNameMacAddressKey(interfaceName)).build();
        Optional<InterfaceNameMacAddress> existingEntry =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, instanceIdentifier);
        if (existingEntry.isPresent()) {
            String vmMacAddress = existingEntry.get().getMacAddress();
            LOG.trace("Entry for interface found in InterfaceNameVmMacAddress map {}, {}", interfaceName, vmMacAddress);
            MDSALDataStoreUtils.asyncRemove(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    instanceIdentifier, DEFAULT_CALLBACK);
            return vmMacAddress;
        }
        LOG.trace("Entry for interface {} missing in InterfaceNameVmMacAddress map", interfaceName);
        return null;
    }
}
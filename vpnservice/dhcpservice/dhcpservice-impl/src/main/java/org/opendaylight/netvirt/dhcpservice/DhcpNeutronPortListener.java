/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput.ArpReponderInputBuilder;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpNeutronPortListener
        extends AsyncClusteredDataTreeChangeListenerBase<Port, DhcpNeutronPortListener> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpNeutronPortListener.class);
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final IElanService elanService;
    private final DataBroker broker;
    private final DhcpserviceConfig config;
    private final IInterfaceManager interfaceManager;
    private final JobCoordinator jobCoordinator;

    @Inject
    public DhcpNeutronPortListener(DataBroker db, DhcpExternalTunnelManager dhcpExternalTunnelManager,
            @Named("elanService") IElanService ielanService, IInterfaceManager interfaceManager,
            DhcpserviceConfig config, final JobCoordinator jobCoordinator) {
        super(Port.class, DhcpNeutronPortListener.class);
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.elanService = ielanService;
        this.interfaceManager = interfaceManager;
        this.broker = db;
        this.config = config;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void init() {
        if (config.isControllerDhcpEnabled()) {
            registerListener(LogicalDatastoreType.CONFIGURATION, broker);
        }
    }

    @Override
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        LOG.debug("DhcpNeutronPortListener Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port del) {
        LOG.trace("Port removed: {}", del);
        if (NeutronConstants.IS_ODL_DHCP_PORT.test(del)) {
            jobCoordinator.enqueueJob(getJobKey(del), () -> {
                WriteTransaction wrtConfigTxn = broker.newWriteOnlyTransaction();
                DhcpServiceUtils.removeSubnetDhcpPortData(del, subnetDhcpPortIdfr -> wrtConfigTxn
                        .delete(LogicalDatastoreType.CONFIGURATION, subnetDhcpPortIdfr));
                processArpResponderForElanDpns(del, arpInput -> {
                    LOG.trace("Removing ARP RESPONDER Flows  for dhcp port {} with ipaddress {} with mac {} on dpn {}",
                            arpInput.getInterfaceName(), arpInput.getSpa(), arpInput.getSha(), arpInput.getDpId());
                    elanService.removeArpResponderFlow(arpInput);
                });
                return Collections.singletonList(wrtConfigTxn.submit());
            });
        }
        if (isVnicTypeDirectOrMacVtap(del)) {
            removePort(del);
        }
    }

    private String getJobKey(Port port) {
        return "PORT- " + port.getUuid().getValue();
    }

    @Override
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        LOG.trace("Port changed to {}", update);
        if (!isVnicTypeDirectOrMacVtap(update)) {
            LOG.trace("Port updated is normal {}", update.getUuid());
            if (isVnicTypeDirectOrMacVtap(original)) {
                LOG.trace("Original Port was direct/macvtap {} so removing flows and cache entry if any",
                        update.getUuid());
                removePort(original);
            }
            return;
        }
        if (!isVnicTypeDirectOrMacVtap(original)) {
            LOG.trace("Original port was normal and updated is direct. Calling addPort()");
            addPort(update);
            return;
        }
        String macOriginal = getMacAddress(original);
        String macUpdated = getMacAddress(update);
        String segmentationIdOriginal = DhcpServiceUtils.getSegmentationId(original.getNetworkId(), broker);
        String segmentationIdUpdated = DhcpServiceUtils.getSegmentationId(update.getNetworkId(), broker);
        if (macOriginal != null && !macOriginal.equalsIgnoreCase(macUpdated) && segmentationIdOriginal != null
                && !segmentationIdOriginal.equalsIgnoreCase(segmentationIdUpdated)) {
            LOG.trace("Mac/segment id has changed");
            dhcpExternalTunnelManager.removeVniMacToPortCache(new BigInteger(segmentationIdOriginal), macOriginal);
            dhcpExternalTunnelManager.updateVniMacToPortCache(new BigInteger(segmentationIdUpdated),
                    macUpdated, update);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Port> identifier, Port add) {
        LOG.trace("Port added {}", add);
        if (NeutronConstants.IS_ODL_DHCP_PORT.test(add)) {
            jobCoordinator.enqueueJob(getJobKey(add), () -> {
                WriteTransaction wrtConfigTxn = broker.newWriteOnlyTransaction();
                DhcpServiceUtils.createSubnetDhcpPortData(add, (subnetDhcpPortIdfr, subnetToDhcpport) -> wrtConfigTxn
                        .put(LogicalDatastoreType.CONFIGURATION, subnetDhcpPortIdfr, subnetToDhcpport));
                processArpResponderForElanDpns(add, arpInput -> {
                    LOG.trace("Installing ARP RESPONDER Flows  for dhcp port {} ipaddress {} with mac {} on dpn {}",
                            arpInput.getInterfaceName(), arpInput.getSpa(), arpInput.getSha(), arpInput.getDpId());
                    ArpReponderInputBuilder builder = new ArpReponderInputBuilder(arpInput);
                    builder.setInstructions(ArpResponderUtil.getInterfaceInstructions(interfaceManager,
                            arpInput.getInterfaceName(), arpInput.getSpa(), arpInput.getSha()));
                    elanService.addArpResponderFlow(builder.buildForInstallFlow());
                });
                return Collections.singletonList(wrtConfigTxn.submit());
            });
        }
        if (!isVnicTypeDirectOrMacVtap(add)) {
            return;
        }
        addPort(add);
    }

    private void removePort(Port port) {
        String macAddress = getMacAddress(port);
        Uuid networkId = port.getNetworkId();
        String segmentationId = DhcpServiceUtils.getSegmentationId(networkId, broker);
        if (segmentationId == null) {
            return;
        }
        List<BigInteger> listOfDpns = DhcpServiceUtils.getListOfDpns(broker);
        dhcpExternalTunnelManager.unInstallDhcpFlowsForVms(networkId.getValue(), listOfDpns, macAddress);
        dhcpExternalTunnelManager.removeVniMacToPortCache(new BigInteger(segmentationId), macAddress);
    }

    private void addPort(Port port) {
        String macAddress = getMacAddress(port);
        Uuid networkId = port.getNetworkId();
        String segmentationId = DhcpServiceUtils.getSegmentationId(networkId, broker);
        if (segmentationId == null) {
            LOG.trace("segmentation id is null");
            return;
        }
        dhcpExternalTunnelManager.updateVniMacToPortCache(new BigInteger(segmentationId), macAddress, port);

    }

    private String getMacAddress(Port port) {
        return port.getMacAddress().getValue();
    }

    private boolean isVnicTypeDirectOrMacVtap(Port port) {
        PortBindingExtension portBinding = port.getAugmentation(PortBindingExtension.class);
        if (portBinding == null || portBinding.getVnicType() == null) {
            // By default, VNIC_TYPE is NORMAL
            return false;
        }
        String vnicType = portBinding.getVnicType().trim().toLowerCase();
        return vnicType.equals("direct") || vnicType.equals("macvtap");
    }

    @Override
    protected DhcpNeutronPortListener getDataTreeChangeListener() {
        return DhcpNeutronPortListener.this;
    }

    /**
     * Handle(Add/Remove) ARP Responder for DHCP IP on all the DPNs when DHCP is
     * enabled/disabled on subnet add or update or delete.
     *
     * @param port
     *            DHCP port for which ARP Responder flow to be added when dhcp
     *            flag is enabled on the subnet or DHCP port for which ARP
     *            Responder flow to be removed when dhcp flag is disabled on the
     *            Subnet
     * @param arpResponderAction
     *            ARP Responder Action to be performed i.e., add or remove flow
     */
    private void processArpResponderForElanDpns(Port port, Consumer<ArpResponderInput> arpResponderAction) {

        java.util.Optional<String> ip4Address = DhcpServiceUtils.getIpV4Address(port);
        if (!ip4Address.isPresent()) {
            LOG.warn("There is no IPv4Address for port {}, not performing ARP responder add/remove flow operation",
                    port.getName());
            return;
        }
        ElanHelper.getDpnInterfacesInElanInstance(broker, port.getNetworkId().getValue()).stream()
                .map(ifName -> DhcpServiceUtils.getInterfaceInfo(interfaceManager, ifName)).forEach(interfaceInfo -> {
                    ArpResponderInput arpResponderInput = new ArpResponderInput.ArpReponderInputBuilder()
                            .setDpId(interfaceInfo.getDpId()).setInterfaceName(interfaceInfo.getInterfaceName())
                            .setLportTag(interfaceInfo.getInterfaceTag()).setSha(port.getMacAddress().getValue())
                            .setSpa(ip4Address.get()).build();
                    arpResponderAction.accept(arpResponderInput);
                });

    }

}

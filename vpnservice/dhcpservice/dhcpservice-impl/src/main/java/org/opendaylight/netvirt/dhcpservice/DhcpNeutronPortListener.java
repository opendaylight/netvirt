/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
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
        extends AsyncClusteredDataTreeChangeListenerBase<Port, DhcpNeutronPortListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpNeutronPortListener.class);
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final IElanService elanService;
    private final DataBroker broker;
    private DhcpserviceConfig config;

    @Inject
    public DhcpNeutronPortListener(final DataBroker db, final DhcpExternalTunnelManager dhcpExternalTunnelManager,
            final @Named("elanService") IElanService ielanService, final DhcpserviceConfig config) {
        super(Port.class, DhcpNeutronPortListener.class);
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.elanService = ielanService;
        this.broker = db;
        this.config = config;
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
        if (DhcpServiceUtils.IS_ODL_DHCP_PORT.test(del)) {
            final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            portDataStoreCoordinator.enqueueJob("PORT- " + del.getUuid().getValue(), () -> {
                addOrRemoveArpResponder(del, NwConstants.DEL_FLOW);
                WriteTransaction wrtConfigTxn = broker.newWriteOnlyTransaction();
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                DhcpServiceUtils.removeNetworkDhcpPortData(broker,del, wrtConfigTxn);
                futures.add(wrtConfigTxn.submit());
                return futures;
            });
        }
        if (isVnicTypeDirectOrMacVtap(del)) {
            removePort(del);
        }
    }

    private void addOrRemoveArpResponder(Port port, int addOrRemove) {
        port.getFixedIps().stream().filter(v -> v.getIpAddress().getIpv4Address() != null).findFirst()
                .map(v -> v.getIpAddress().getIpv4Address()).ifPresent(ip -> {
                    if (addOrRemove == NwConstants.DEL_FLOW) {
                        delArpResponderForElanDpns(port.getNetworkId().getValue(), port.getMacAddress().getValue(),
                            ip.getValue());
                    }
                    if (addOrRemove == NwConstants.ADD_FLOW) {
                        addArpResponderForElanDpns(port.getNetworkId().getValue(), port.getMacAddress().getValue(),
                            ip.getValue());
                    }
                });
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
        if (!isVnicTypeDirectOrMacVtap((original))) {
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
        if (DhcpServiceUtils.IS_ODL_DHCP_PORT.test(add)) {
            final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            portDataStoreCoordinator.enqueueJob("PORT- " + add.getUuid().getValue(), () -> {
                WriteTransaction wrtConfigTxn = broker.newWriteOnlyTransaction();
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                DhcpServiceUtils.createNetworkDhcpPortData(broker,add, wrtConfigTxn);
                futures.add(wrtConfigTxn.submit());
                addOrRemoveArpResponder(add, NwConstants.ADD_FLOW);
                return futures;
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
        return (vnicType.equals("direct") || vnicType.equals("macvtap"));
    }

    @Override
    protected DhcpNeutronPortListener getDataTreeChangeListener() {
        return DhcpNeutronPortListener.this;
    }

    /**
     * Add ARP Responder for DHCP IP on all the DPNs when DHCP is enabled on
     * subnet add or update.
     *
     * @param networkId
     *            NetworkId on which subnet is enabled
     * @param macAddress
     *            Mac address of the DHCP Port
     * @param ipAddress
     *            IP address of the DHCP Port
     */
//    private void addArpResponderForElanDpns(String networkId, String macAddress, String ipAddress) {
//        List<BigInteger> dpnIds = DhcpServiceUtils.getDpnsForElan(networkId, broker);
//        for (BigInteger dpnId : dpnIds) {
//            LOG.trace("Installing DHCP ARPResponder Flows for DPN {}", dpnId);
//            DhcpServiceUtils.getAvailableDpnInterface(broker, dpnId, networkId).ifPresent(dpnInterface -> {
//                elanService.addArpResponderFlow(dpnId, dpnInterface, ipAddress, macAddress,
//                java.util.Optional.empty(),  false);
//            });
//        }
//    }

    private void addArpResponderForElanDpns(String networkId, String macAddress, String ipAddress) {
        for (String intrf : DhcpServiceUtils.getDpnInterfacesInElanInstance(broker, networkId)) {
            Interface ifState = DhcpServiceUtils.getInterfaceStateFromOperDS(broker, intrf);
            BigInteger dpnId = DhcpServiceUtils.getDpIdFromInterface(ifState);
            LOG.trace("Installing DHCP ARPResponder Flows for DPN {}", dpnId);
            //DhcpServiceUtils.getAvailableDpnInterface(broker, dpnId, networkId).ifPresent(dpnInterface -> {
            elanService.addArpResponderFlow(dpnId, intrf, ipAddress, macAddress,
                    java.util.Optional.of(ifState.getIfIndex()), false);

            //});
        }
    }

    /**
     * Remove ARP Responder for DHCP IP on all the DPNs when DHCP is disabled on
     * Subnet removed or updated.
     *
     * @param networkId
     *            NetworkId on which subnet is enabled
     * @param macAddress
     *            Mac address of the DHCP Port
     * @param ipAddress
     *            IP address of the DHCP Port
     */
//    private void delArpResponderForElanDpns(String networkId, String macAddress, String ipAddress) {
//        List<BigInteger> dpnIds = DhcpServiceUtils.getDpnsForElan(networkId, broker);
//        for (BigInteger dpnId : dpnIds) {
//            LOG.trace("Removing DHCP ArpResponder Flows for DPN {}", dpnId);
//            elanService.removeArpResponderFlow(dpnId, null, ipAddress, java.util.Optional.empty());
//        }
//
//    }

    private void delArpResponderForElanDpns(String networkId, String macAddress, String ipAddress) {
        for (String intrf : DhcpServiceUtils.getDpnInterfacesInElanInstance(broker, networkId)) {
            Interface ifState = DhcpServiceUtils.getInterfaceStateFromOperDS(broker, intrf);
            BigInteger dpnId = DhcpServiceUtils.getDpIdFromInterface(ifState);
            LOG.trace("Installing DHCP ARPResponder Flows for DPN {}", dpnId);
            //DhcpServiceUtils.getAvailableDpnInterface(broker, dpnId, networkId).ifPresent(dpnInterface -> {
            elanService.removeArpResponderFlow(dpnId, intrf, ipAddress,
                    java.util.Optional.of(ifState.getIfIndex()));

            //});
        }
    }

}
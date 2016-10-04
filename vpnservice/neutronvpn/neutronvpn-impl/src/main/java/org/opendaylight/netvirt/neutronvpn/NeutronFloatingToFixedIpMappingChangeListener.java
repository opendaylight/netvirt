/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.floatingips.attributes.Floatingips;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.floatingips.attributes.floatingips.Floatingip;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronFloatingToFixedIpMappingChangeListener extends AsyncDataTreeChangeListenerBase<Floatingip,
        NeutronFloatingToFixedIpMappingChangeListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronFloatingToFixedIpMappingChangeListener.class);
    private final DataBroker dataBroker;

    public NeutronFloatingToFixedIpMappingChangeListener(final DataBroker dataBroker) {
        super(Floatingip.class, NeutronFloatingToFixedIpMappingChangeListener.class);
        this.dataBroker = dataBroker;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Floatingip> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Floatingips.class).child(Floatingip.class);
    }

    @Override
    protected NeutronFloatingToFixedIpMappingChangeListener getDataTreeChangeListener() {
        return NeutronFloatingToFixedIpMappingChangeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<Floatingip> identifier, Floatingip input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Neutron Floating IP created: key: " + identifier + ", value=" + input);
        }
        IpAddress fixedIp = input.getFixedIpAddress();
        if (fixedIp != null) {
            addToFloatingIpInfo(input.getRouterId().getValue(), input.getFloatingNetworkId(), input.getPortId()
                    .getValue(), fixedIp.getIpv4Address().getValue(), input.getFloatingIpAddress().getIpv4Address()
                    .getValue());
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Floatingip> identifier, Floatingip input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Neutron Floating IP deleted : key: " + identifier + ", value=" + input);
        }
        IpAddress fixedIp = input.getFixedIpAddress();
        if (fixedIp != null) {
            clearFromFloatingIpInfo(input.getRouterId().getValue(), input.getPortId().getValue(), fixedIp
                    .getIpv4Address().getValue());
        }
    }

    // populate the floating to fixed ip map upon association/dissociation from fixed ip
    @Override
    protected void update(InstanceIdentifier<Floatingip> identifier, Floatingip original, Floatingip update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Handling FloatingIptoFixedIp mapping : key: " + identifier + ", original value=" + original +
                    ", update value=" + update);
        }
        IpAddress oldFixedIp = original.getFixedIpAddress();
        IpAddress newFixedIp = update.getFixedIpAddress();

        if (oldFixedIp != null && !oldFixedIp.equals(newFixedIp)) {
            clearFromFloatingIpInfo(original.getRouterId().getValue(), original.getPortId().getValue(), oldFixedIp
                    .getIpv4Address().getValue());
        }
        if (newFixedIp != null && !newFixedIp.equals(oldFixedIp)) {
            addToFloatingIpInfo(update.getRouterId().getValue(), update.getFloatingNetworkId(), update.getPortId()
                    .getValue(), newFixedIp.getIpv4Address().getValue(), update.getFloatingIpAddress
                    ().getIpv4Address().getValue());
        }
    }

    private void addToFloatingIpInfo(String routerName, Uuid extNetworkId, String fixedNeutronPortName, String
            fixedIpAddress, String floatingIpAddress) {
        RouterPortsBuilder routerPortsBuilder;
        boolean isLockAcquired = false;
        InstanceIdentifier<RouterPorts> routerPortsIdentifier = InstanceIdentifier.builder(FloatingIpInfo.class)
                .child(RouterPorts.class, new RouterPortsKey(routerName)).build();
        try {
            Optional<RouterPorts> optionalRouterPorts = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType
                    .CONFIGURATION, routerPortsIdentifier);
            if (optionalRouterPorts.isPresent()) {
                LOG.debug("Updating routerPorts node {} in floatingIpInfo DS for floating IP () on fixed " +
                        "neutron port {} : ", routerName, floatingIpAddress, fixedNeutronPortName);
                routerPortsBuilder = new RouterPortsBuilder(optionalRouterPorts.get());
            } else {
                LOG.debug("Creating new routerPorts node {} in floatingIpInfo DS for floating IP () on fixed " +
                        "neutron port {} : ", routerName, floatingIpAddress, fixedNeutronPortName);
                routerPortsBuilder = new RouterPortsBuilder().setKey(new RouterPortsKey(routerName)).setRouterId
                        (routerName);
            }
            if (extNetworkId != null) {
                routerPortsBuilder.setExternalNetworkId(extNetworkId);
            }
            if (fixedNeutronPortName != null) {
                List<Ports> portsList = routerPortsBuilder.getPorts();
                if (portsList == null) {
                    portsList = new ArrayList<>();
                }
                PortsBuilder fixedNeutronPortBuilder = null;
                for (Ports neutronPort : portsList) {
                    if (neutronPort.getPortName().equals(fixedNeutronPortName)) {
                        fixedNeutronPortBuilder = new PortsBuilder(neutronPort);
                        break;
                    }
                }
                if (fixedNeutronPortBuilder == null) {
                    fixedNeutronPortBuilder = new PortsBuilder().setKey(new PortsKey(fixedNeutronPortName))
                            .setPortName(fixedNeutronPortName);
                }
                if (fixedIpAddress != null) {
                    List<IpMapping> ipMappingList = fixedNeutronPortBuilder.getIpMapping();
                    if (ipMappingList == null) {
                        ipMappingList = new ArrayList<>();
                    }
                    IpMapping ipMapping = new IpMappingBuilder().setKey(new IpMappingKey(fixedIpAddress))
                            .setInternalIp(fixedIpAddress).setExternalIp(floatingIpAddress).setLabel(null).build();
                    ipMappingList.add(ipMapping);
                    fixedNeutronPortBuilder.setIpMapping(ipMappingList);
                }
                portsList.add(fixedNeutronPortBuilder.build());
                routerPortsBuilder.setPorts(portsList);
            }
            isLockAcquired = NeutronvpnUtils.lock(routerName);
            LOG.debug("Creating/Updating routerPorts node {} in floatingIpInfo DS for floating IP () on fixed " +
                    "neutron port {} : ", routerName, floatingIpAddress, fixedNeutronPortName);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsIdentifier, routerPortsBuilder
                    .build());
            LOG.debug("FloatingIpInfo DS updated for floating IP {} ", floatingIpAddress);
        } catch (Exception e) {
            LOG.error("addToFloatingIpInfo failed for floating IP: {} ", floatingIpAddress);
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(routerName);
            }
        }
    }

    private void clearFromFloatingIpInfo(String routerName, String fixedNeutronPortName, String fixedIpAddress) {
        boolean isLockAcquired = false;
        InstanceIdentifier.InstanceIdentifierBuilder<RouterPorts> routerPortsIdentifierBuilder = InstanceIdentifier
                .builder(FloatingIpInfo.class).child(RouterPorts.class, new RouterPortsKey(routerName));
        try {
            Optional<RouterPorts> optionalRouterPorts = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType
                    .CONFIGURATION, routerPortsIdentifierBuilder.build());
            if (optionalRouterPorts.isPresent()) {
                RouterPorts routerPorts = optionalRouterPorts.get();
                List<Ports> portsList = routerPorts.getPorts();
                List<IpMapping> ipMapping = new ArrayList<>();
                for (Ports ports : portsList) {
                    if (ports.getPortName().equals(fixedNeutronPortName)) {
                        ipMapping = ports.getIpMapping();
                    }
                }
                InstanceIdentifier.InstanceIdentifierBuilder<Ports> portsIdentifierBuilder = null;
                if (ipMapping.size() == 1) {
                    if (portsList.size() == 1) {
                        try {
                            // remove entire routerPorts node
                            isLockAcquired = NeutronvpnUtils.lock(routerName);
                            LOG.debug("removing routerPorts node: {} ", routerName);
                            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                    routerPortsIdentifierBuilder.build());
                        } catch (Exception e) {
                            LOG.error("Failure in deletion of routerPorts node {}", routerName);
                        } finally {
                            if (isLockAcquired) {
                                NeutronvpnUtils.unlock(routerName);
                            }
                        }
                    } else {
                        portsIdentifierBuilder = routerPortsIdentifierBuilder.child(Ports.class, new PortsKey
                                (fixedNeutronPortName));
                        try {
                            // remove entire ports node under this routerPorts node
                            isLockAcquired = NeutronvpnUtils.lock(fixedNeutronPortName);
                            LOG.debug("removing ports node {} under routerPorts node {}", fixedNeutronPortName,
                                    routerName);
                            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, portsIdentifierBuilder
                                    .build());
                        } catch (Exception e) {
                            LOG.error("Failure in deletion of routerPorts node {}", routerName);
                        } finally {
                            if (isLockAcquired) {
                                NeutronvpnUtils.unlock(routerName);
                            }
                        }
                    }
                } else {
                    InstanceIdentifier<IpMapping> ipMappingIdentifier =
                            portsIdentifierBuilder.child(IpMapping.class, new IpMappingKey(fixedIpAddress)).build();
                    try {
                        // remove particular ipMapping
                        isLockAcquired = NeutronvpnUtils.lock(fixedIpAddress);
                        LOG.debug("removing particular ipMapping {}", ipMapping);
                        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, ipMappingIdentifier);
                    } catch (Exception e) {
                        LOG.error("Failure in deletion of ipMapping {}", ipMapping);
                    } finally {
                        if (isLockAcquired) {
                            NeutronvpnUtils.unlock(fixedIpAddress);
                        }
                    }
                }
                LOG.debug("Deletion from FloatingIpInfo DS successful for fixedIp {} ", fixedIpAddress);
            } else {
                LOG.error("routerPorts for router {} not found", routerName);
            }
        } catch (Exception e) {
            LOG.error("Failed to delete ipMapping from FloatingIpInfo DS for fixed Ip {}", fixedIpAddress);
        }
    }

    protected void dissociatefixedIPFromFloatingIP(String fixedNeutronPortName) {
        boolean isLockAcquired = false;
        InstanceIdentifier.InstanceIdentifierBuilder<FloatingIpInfo> floatingIpInfoIdentifierBuilder =
                InstanceIdentifier.builder(FloatingIpInfo.class);
        try {
            Optional<FloatingIpInfo> optionalFloatingIPInfo = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType
                    .CONFIGURATION, floatingIpInfoIdentifierBuilder.build());
            if (optionalFloatingIPInfo.isPresent() && optionalFloatingIPInfo.get() != null) {
                List<RouterPorts> routerPortsList = optionalFloatingIPInfo.get().getRouterPorts();
                if (routerPortsList != null && !routerPortsList.isEmpty()) {
                    for (RouterPorts routerPorts : routerPortsList) {
                        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating
                                .ip.info.router.ports.Ports> portsList = routerPorts.getPorts();
                        if (portsList != null && !portsList.isEmpty()) {
                            String routerName = routerPorts.getRouterId();
                            InstanceIdentifier.InstanceIdentifierBuilder<RouterPorts> routerPortsIdentifierBuilder =
                                    floatingIpInfoIdentifierBuilder.child(RouterPorts.class, new RouterPortsKey
                                            (routerName));
                            if (portsList.size() == 1) {
                                try {
                                    // remove entire routerPorts node
                                    isLockAcquired = NeutronvpnUtils.lock(routerName);
                                    LOG.debug("removing routerPorts node: {} ", routerName);
                                    MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                            routerPortsIdentifierBuilder.build());

                                } catch (Exception e) {
                                    LOG.error("Failure in deletion of routerPorts node {}", routerName);
                                } finally {
                                    if (isLockAcquired) {
                                        NeutronvpnUtils.unlock(routerName);
                                    }
                                }
                            } else {
                                InstanceIdentifier.InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn
                                        .opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports
                                        .Ports> portsIdentifierBuilder = routerPortsIdentifierBuilder.child(org
                                        .opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
                                        .floating.ip.info.router.ports.Ports.class, new PortsKey(fixedNeutronPortName));
                                try {
                                    // remove entire ports node under this routerPorts node
                                    isLockAcquired = NeutronvpnUtils.lock(fixedNeutronPortName);
                                    LOG.debug("removing ports node {} under routerPorts node {}",
                                            fixedNeutronPortName, routerName);
                                    MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                            portsIdentifierBuilder.build());
                                } catch (Exception e) {
                                    LOG.error("Failure in deletion of routerPorts node {}", routerName);
                                } finally {
                                    if (isLockAcquired) {
                                        NeutronvpnUtils.unlock(routerName);
                                    }
                                }
                            }
                            LOG.debug("Deletion from FloatingIpInfo DS successful for fixedIP neutron port {} ",
                                    fixedNeutronPortName);
                        } else {
                            LOG.debug("Neutron port {} not associated to any floating IP", fixedNeutronPortName);
                        }
                    }
                } else {
                    LOG.debug("No router present containing fixed to floating IP association(s)");
                }
            } else {
                LOG.debug("FloatingIPInfo DS empty. Hence, no router present containing fixed to floating IP " +
                        "association(s)");
            }
        } catch (Exception e) {
            LOG.error("Failed to dissociate fixedIP from FloatingIpInfo DS for neutron port {}", fixedNeutronPortName);
        }
    }

}

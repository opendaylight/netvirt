/*
 * Copyright (c) 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetRouteInterfaceStateChangeListener extends AsyncDataTreeChangeListenerBase<Interface,
    SubnetRouteInterfaceStateChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetRouteInterfaceStateChangeListener.class);
    private static final String LOGGING_PREFIX = "SUBNETROUTE:";
    private final DataBroker dataBroker;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private final SubnetOpDpnManager subOpDpnManager;
    private final INeutronVpnManager neutronVpnManager;
    private final JobCoordinator jobCoordinator;

    @Inject
    public SubnetRouteInterfaceStateChangeListener(final DataBroker dataBroker,
            final VpnSubnetRouteHandler vpnSubnetRouteHandler, final SubnetOpDpnManager subnetOpDpnManager,
            final INeutronVpnManager neutronVpnService, final JobCoordinator jobCoordinator) {
        super(Interface.class, SubnetRouteInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
        this.subOpDpnManager = subnetOpDpnManager;
        this.neutronVpnManager = neutronVpnService;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected SubnetRouteInterfaceStateChangeListener getDataTreeChangeListener() {
        return SubnetRouteInterfaceStateChangeListener.this;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("{} add: Received interface {} up event", LOGGING_PREFIX, intrf);
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                LOG.trace("SubnetRouteInterfaceListener add: Received interface {} up event", intrf);
                if (intrf.getOperStatus().equals(Interface.OperStatus.Up)) {
                    List<Uuid> subnetIdList = getSubnetId(intrf);
                    if (subnetIdList.isEmpty()) {
                        LOG.trace("SubnetRouteInterfaceListener add: Port {} doesn't exist in configDS",
                                intrf.getName());
                        return;
                    }
                    for (Uuid subnetId : subnetIdList) {
                        jobCoordinator.enqueueJob("SUBNETROUTE-" + subnetId,
                            () -> {
                                String interfaceName = intrf.getName();
                                BigInteger dpnId = BigInteger.ZERO;
                                LOG.info("{} add: Received port UP event for interface {} subnetId {}",
                                        LOGGING_PREFIX, interfaceName, subnetId);
                                try {
                                    dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                                } catch (Exception e) {
                                    LOG.error("{} add: Unable to obtain dpnId for interface {} in subnet {},"
                                            + " subnetroute inclusion for this interface failed",
                                            LOGGING_PREFIX, interfaceName, subnetId, e);
                                }
                                InstanceIdentifier<VpnInterface> id = VpnUtil
                                        .getVpnInterfaceIdentifier(interfaceName);
                                Optional<VpnInterface> cfgVpnInterface = VpnUtil.read(dataBroker,
                                     LogicalDatastoreType.CONFIGURATION, id);
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                if (!cfgVpnInterface.isPresent()) {
                                    return futures;
                                }
                                vpnSubnetRouteHandler.onInterfaceUp(dpnId, intrf.getName(), subnetId);
                                return futures;
                            });
                    }
                }
            }
            LOG.info("{} add: Processed interface {} up event", LOGGING_PREFIX, intrf.getName());
        } catch (Exception e) {
            LOG.error("{} add: Exception observed in handling addition for VPN Interface {}.", LOGGING_PREFIX,
                intrf.getName(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        if (L2vlan.class.equals(intrf.getType())) {
            LOG.trace("SubnetRouteInterfaceListener remove: Received interface {} down event", intrf);
            List<Uuid> subnetIdList = getSubnetId(intrf);
            if (subnetIdList.isEmpty()) {
                LOG.trace("SubnetRouteInterfaceListener remove: Port {} doesn't exist in configDS",
                        intrf.getName());
                return;
            }
            LOG.trace("{} remove: Processing interface {} down event in ", LOGGING_PREFIX, intrf.getName());
            for (Uuid subnetId : subnetIdList) {
                jobCoordinator.enqueueJob("SUBNETROUTE-" + subnetId,
                    () -> {
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        try {
                            String interfaceName = intrf.getName();
                            BigInteger dpnId = BigInteger.ZERO;
                            LOG.info("{} remove: Received port DOWN event for interface {} in subnet {} ",
                                    LOGGING_PREFIX, interfaceName, subnetId);
                            try {
                                dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                            } catch (Exception e) {
                                LOG.error("{} remove: Unable to retrieve dpnId for interface {} in subnet {}. "
                                                + "Fetching from vpn interface itself",
                                        LOGGING_PREFIX, intrf.getName(), subnetId, e);
                            }
                            InstanceIdentifier<VpnInterface> id = VpnUtil
                                    .getVpnInterfaceIdentifier(interfaceName);
                            Optional<VpnInterface> cfgVpnInterface = SingleTransactionDataBroker.syncReadOptional(
                                    dataBroker, LogicalDatastoreType.CONFIGURATION, id);
                            if (!cfgVpnInterface.isPresent()) {
                                return futures;
                            }
                            boolean interfaceDownEligible = false;
                            for (VpnInstanceNames vpnInterfaceVpnInstance
                                    : cfgVpnInterface.get().getVpnInstanceNames()) {
                                String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                InstanceIdentifier<VpnInterfaceOpDataEntry> idOper = VpnUtil
                                        .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                                Optional<VpnInterfaceOpDataEntry> optVpnInterface = SingleTransactionDataBroker
                                        .syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, idOper);
                                if (optVpnInterface.isPresent()) {
                                    BigInteger dpnIdLocal = dpnId;
                                    if (dpnIdLocal.equals(BigInteger.ZERO)) {
                                        dpnIdLocal = optVpnInterface.get().getDpnId();
                                    }
                                    if (!dpnIdLocal.equals(BigInteger.ZERO)) {
                                        interfaceDownEligible = true;
                                        break;
                                    }
                                }
                            }
                            if (interfaceDownEligible) {
                                vpnSubnetRouteHandler.onInterfaceDown(dpnId, intrf.getName(), subnetId);
                            }
                            LOG.info("{} remove: Processed interface {} down event in ", LOGGING_PREFIX,
                                    intrf.getName());
                        } catch (ReadFailedException e) {
                            LOG.error("{} remove: Failed to read data store for {}", LOGGING_PREFIX, intrf.getName());
                        }
                        return futures;
                    });
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
        Interface original, Interface update) {
        try {
            String interfaceName = update.getName();
            if (L2vlan.class.equals(update.getType())) {
                LOG.trace("{} update: Operation Interface update event - Old: {}, New: {}", LOGGING_PREFIX,
                    original, update);
                List<Uuid> subnetIdList = getSubnetId(update);
                if (subnetIdList.isEmpty()) {
                    LOG.error("SubnetRouteInterfaceListener update: Port {} doesn't exist in configDS",
                            update.getName());
                    return;
                }
                for (Uuid subnetId : subnetIdList) {
                    jobCoordinator.enqueueJob("SUBNETROUTE-" + subnetId,
                        () -> {
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            BigInteger dpnId = BigInteger.ZERO;
                            try {
                                dpnId = InterfaceUtils.getDpIdFromInterface(update);
                            } catch (Exception e) {
                                LOG.error("{} remove: Unable to retrieve dpnId for interface {} in subnet  {}. "
                                                + "Fetching from vpn interface itself",
                                        LOGGING_PREFIX, update.getName(), subnetId, e);
                            }
                            InstanceIdentifier<VpnInterface> id = VpnUtil
                                    .getVpnInterfaceIdentifier(interfaceName);
                            Optional<VpnInterface> cfgVpnInterface = VpnUtil.read(dataBroker,
                                     LogicalDatastoreType.CONFIGURATION, id);
                            if (!cfgVpnInterface.isPresent()) {
                                return futures;
                            }
                            boolean interfaceChangeEligible = false;
                            for (VpnInstanceNames vpnInterfaceVpnInstance
                                  : cfgVpnInterface.get().getVpnInstanceNames()) {
                                String vpnName = vpnInterfaceVpnInstance.getVpnName();
                                InstanceIdentifier<VpnInterfaceOpDataEntry> idOper = VpnUtil
                                       .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                                Optional<VpnInterfaceOpDataEntry> optVpnInterface = VpnUtil.read(dataBroker,
                                       LogicalDatastoreType.OPERATIONAL, idOper);
                                if (optVpnInterface.isPresent()) {
                                    BigInteger dpnIdLocal = dpnId;
                                    if (dpnIdLocal.equals(BigInteger.ZERO)) {
                                        dpnIdLocal = optVpnInterface.get().getDpnId();
                                    }
                                    if (!dpnIdLocal.equals(BigInteger.ZERO)) {
                                        interfaceChangeEligible = true;
                                        break;
                                    }
                                }
                            }
                            if (interfaceChangeEligible) {
                                if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                                    LOG.info("{} update: Received port UP event for interface {} in subnet {}",
                                            LOGGING_PREFIX, update.getName(), subnetId);
                                    vpnSubnetRouteHandler.onInterfaceUp(dpnId, update.getName(), subnetId);
                                } else if (update.getOperStatus().equals(Interface.OperStatus.Down)
                                        || update.getOperStatus().equals(Interface.OperStatus.Unknown)) {
                                    /*
                                     * If the interface went down voluntarily (or) if the interface is not
                                     * reachable from control-path involuntarily, trigger subnetRoute election
                                     */
                                    LOG.info("{} update: Received port {} event for interface {} in subnet {} ",
                                            LOGGING_PREFIX, update.getOperStatus()
                                            .equals(Interface.OperStatus.Unknown)
                                            ? "UNKNOWN" : "DOWN", update.getName(), subnetId);
                                    vpnSubnetRouteHandler.onInterfaceDown(dpnId, update.getName(), subnetId);
                                }
                            }
                            return futures;
                        });
                }
            }
            LOG.info("{} update: Processed Interface {} update event", LOGGING_PREFIX, update.getName());
        } catch (Exception e) {
            LOG.error("{} update: Exception observed in handling deletion of VPNInterface {}", LOGGING_PREFIX,
                    update.getName(), e);
        }
    }

    @Nonnull
    protected List<Uuid> getSubnetId(Interface intrf) {
        List<Uuid> listSubnetIds = new ArrayList<>();
        if (!NeutronUtils.isUuid(intrf.getName())) {
            LOG.debug("SubnetRouteInterfaceListener: Interface {} doesn't have valid uuid pattern", intrf.getName());
            return listSubnetIds;
        }

        PortOpDataEntry portOpEntry = subOpDpnManager.getPortOpDataEntry(intrf.getName());
        if (portOpEntry != null) {
            List<Uuid> subnet = portOpEntry.getSubnetIds();
            if (subnet != null) {
                return subnet;
            }
            return listSubnetIds;
        }
        LOG.trace("SubnetRouteInterfaceListener : Received Port {} event for {} that is not part of subnetRoute",
                intrf.getOperStatus(), intrf.getName());
        Port port = neutronVpnManager.getNeutronPort(intrf.getName());
        if (port == null) {
            return listSubnetIds;
        }
        List<FixedIps> portIps = port.getFixedIps();
        if (port.getFixedIps() != null) {
            for (FixedIps portIp : portIps) {
                listSubnetIds.add(portIp.getSubnetId());
            }
        }
        return listSubnetIds;
    }
}

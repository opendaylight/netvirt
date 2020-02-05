/*
 * Copyright (c) 2016 - 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatInterfaceStateChangeListener
    extends AsyncDataTreeChangeListenerBase<Interface, NatInterfaceStateChangeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(NatInterfaceStateChangeListener.class);
    private final DataBroker dataBroker;
    private final NatSouthboundEventHandlers southboundEventHandlers;

    @Inject
    public NatInterfaceStateChangeListener(final DataBroker dataBroker,
                                           final NatSouthboundEventHandlers southboundEventHandlers) {
        super(Interface.class, NatInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.southboundEventHandlers = southboundEventHandlers;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected NatInterfaceStateChangeListener getDataTreeChangeListener() {
        return NatInterfaceStateChangeListener.this;
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("add : Interface {} up event received", intrf);
        if (!L2vlan.class.equals(intrf.getType())) {
            LOG.debug("add : Interface {} is not Vlan Interface.Ignoring", intrf.getName());
            return;
        }
        String interfaceName = intrf.getName();
        Uint64 intfDpnId;
        try {
            intfDpnId = NatUtil.getDpIdFromInterface(intrf);
        } catch (Exception e) {
            LOG.error("add : Exception occured while retriving dpnid for interface {}", intrf.getName(), e);
            return;
        }
        if (Uint64.ZERO.equals(intfDpnId)) {
            LOG.warn("add : Could not retrieve dp id for interface {} ", interfaceName);
            return;
        }
        // We service only VM interfaces. We do not service Tunnel Interfaces here.
        // Tunnel events are directly serviced by TunnelInterfacesStateListener present as part of
        // VpnInterfaceManager
        RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
        if (routerInterface != null) {
            this.southboundEventHandlers.handleAdd(interfaceName, intfDpnId, routerInterface);
        } else {
            LOG.info("add : Router-Interface Mapping not found for Interface : {}", interfaceName);
        }
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("remove : Interface {} removed event received", intrf);
        if (!L2vlan.class.equals(intrf.getType())) {
            LOG.debug("remove : Interface {} is not Vlan Interface.Ignoring", intrf.getName());
            return;
        }
        String interfaceName = intrf.getName();
        Uint64 intfDpnId = Uint64.ZERO;
        try {
            intfDpnId = NatUtil.getDpIdFromInterface(intrf);
        } catch (Exception e) {
            LOG.error("remove : Exception occured while retriving dpnid for interface {}",  intrf.getName(), e);
            InstanceIdentifier<VpnInterface> id = NatUtil.getVpnInterfaceIdentifier(interfaceName);
            Optional<VpnInterface> cfgVpnInterface =
                    SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                            dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (!cfgVpnInterface.isPresent()) {
                LOG.warn("remove : Interface {} is not a VPN Interface, ignoring.", interfaceName);
                return;
            }
            for (VpnInstanceNames vpnInterfaceVpnInstance : cfgVpnInterface.get().nonnullVpnInstanceNames()) {
                String vpnName  = vpnInterfaceVpnInstance.getVpnName();
                InstanceIdentifier<VpnInterfaceOpDataEntry> idOper = NatUtil
                      .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                      SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                            dataBroker, LogicalDatastoreType.OPERATIONAL, idOper);
                if (optVpnInterface.isPresent()) {
                    intfDpnId = optVpnInterface.get().getDpnId();
                    break;
                }
            }
        }
        if (Uint64.ZERO.equals(intfDpnId)) {
            LOG.warn("remove : Could not retrieve dpnid for interface {} ", interfaceName);
            return;
        }
        RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
        if (routerInterface != null) {
            this.southboundEventHandlers.handleRemove(intrf.getName(), intfDpnId, routerInterface);
        } else {
            LOG.info("remove : Router-Interface Mapping not found for Interface : {}", interfaceName);
        }
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        LOG.trace("update : Operation Interface update event - Old: {}, New: {}", original, update);
        if (!L2vlan.class.equals(update.getType())) {
            LOG.debug("update : Interface {} is not Vlan Interface.Ignoring", update.getName());
            return;
        }
        Uint64 intfDpnId = Uint64.ZERO;
        String interfaceName = update.getName();
        try {
            intfDpnId = NatUtil.getDpIdFromInterface(update);
        } catch (Exception e) {
            LOG.error("update : Exception occured while retriving dpnid for interface {}",  update.getName(), e);
            InstanceIdentifier<VpnInterface> id = NatUtil.getVpnInterfaceIdentifier(interfaceName);
            Optional<VpnInterface> cfgVpnInterface =
                    SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                            dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (!cfgVpnInterface.isPresent()) {
                LOG.warn("update : Interface {} is not a VPN Interface, ignoring.", interfaceName);
                return;
            }
            for (VpnInstanceNames vpnInterfaceVpnInstance : cfgVpnInterface.get().nonnullVpnInstanceNames()) {
                String vpnName  = vpnInterfaceVpnInstance.getVpnName();
                InstanceIdentifier<VpnInterfaceOpDataEntry> idOper = NatUtil
                      .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                      SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                            dataBroker, LogicalDatastoreType.OPERATIONAL, idOper);
                if (optVpnInterface.isPresent()) {
                    intfDpnId = optVpnInterface.get().getDpnId();
                    break;
                }
            }
        }
        if (Uint64.ZERO.equals(intfDpnId)) {
            LOG.warn("remove : Could not retrieve dpnid for interface {} ", interfaceName);
            return;
        }
        RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
        if (routerInterface != null) {
            this.southboundEventHandlers.handleUpdate(original, update, intfDpnId, routerInterface);
        } else {
            LOG.info("update : Router-Interface Mapping not found for Interface : {}", interfaceName);
        }
    }
}

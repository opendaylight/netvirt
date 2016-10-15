/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigInteger;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;

public class NatInterfaceStateChangeListener extends AsyncDataTreeChangeListenerBase<Interface, NatInterfaceStateChangeListener> implements
        AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NatInterfaceStateChangeListener.class);

    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService interfaceManager;

    public NatInterfaceStateChangeListener(final DataBroker db, final OdlInterfaceRpcService interfaceManager) {
        super(Interface.class, NatInterfaceStateChangeListener.class);
        this.dataBroker = db;
        this.interfaceManager = interfaceManager;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(dataBroker);
    }

    private void registerListener(final DataBroker db) {
        try {
            registerListener(LogicalDatastoreType.OPERATIONAL, db);
        } catch (final Exception e) {
            LOG.error("NAT Service : Interface DataChange listener registration failed", e);
            throw new IllegalStateException("NAT Service : Nexthop Manager registration Listener failed.", e);
        }
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
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        try {
            final String interfaceName = intrf.getName();
            LOG.trace("NAT Service : Received interface {} PORT UP OR ADD event ", interfaceName);
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
                configInterface = NatUtil.getInterface(dataBroker, interfaceName);
            if (configInterface != null) {
                if (!configInterface.getType().equals(Tunnel.class)) {
                    // We service only VM interfaces and Router interfaces here.
                    // We donot service Tunnel Interfaces here.
                    // Tunnel events are directly serviced
                    // by TunnelInterfacesStateListener present as part of VpnInterfaceManager
                    LOG.debug("NAT Service : Config Interface Name {}", configInterface.getName());
                    RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface
                            (dataBroker, interfaceName);
                    if (routerInterface != null) {
                        String routerName = routerInterface.getRouterName();
                        LOG.debug("NAT Service : Router Name {} ", routerInterface.getRouterName());
                        WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                        handleRouterInterfacesUpEvent(routerName, interfaceName, writeOperTxn);
                        writeOperTxn.submit();
                    } else {
                        LOG.info("NAT Service : Unable to process add for interface {}", interfaceName);
                    }
                }
            } else {
                LOG.error("Unable to process add for interface {} ," +
                        "since Interface ConfigDS entry absent for the same", interfaceName);
            }
        } catch (Exception e) {
          LOG.error("Exception caught in Interface Operational State Up event", e);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        try {
            final String interfaceName = intrf.getName();
            LOG.trace("NAT Service : Received interface {} PORT DOWN or REMOVE event", intrf.getName());
            if (intrf != null && intrf.getType() != null && !intrf.getType().equals(Tunnel.class)) {
                BigInteger dpId;
                  try {
                    dpId = NatUtil.getDpIdFromInterface(intrf);
                } catch (Exception e){
                    LOG.warn("NAT Service : Unable to retrieve DPNID from Interface operational data store for Interface {}. Fetching " +
                            "from VPN Interface op data store. ", intrf.getName(), e);
                    InstanceIdentifier<VpnInterface> id = NatUtil.getVpnInterfaceIdentifier(interfaceName);
                    Optional<VpnInterface> optVpnInterface = NatUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
                    if (!optVpnInterface.isPresent()) {
                        LOG.debug("NAT Service : Interface {} is not a VPN Interface, ignoring.", intrf.getName());
                        return;
                    }
                    final VpnInterface vpnInterface = optVpnInterface.get();
                    dpId = vpnInterface.getDpnId();
                }
                if(dpId == null || dpId.equals(BigInteger.ZERO)){
                    LOG.debug("NAT Service : Unable to get DPN ID for the Interface {}", interfaceName);
                    return;
                }
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
                if (routerInterface != null) {
                    handleRouterInterfacesDownEvent(routerInterface.getRouterName(), interfaceName, dpId, writeOperTxn);
                }
                writeOperTxn.submit();
            }
        } catch (Exception e) {
          LOG.error("NAT Service : Exception observed in handling deletion of VPN Interface {}. ", intrf.getName(), e);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
    }

    void handleRouterInterfacesUpEvent(String routerName, String interfaceName, WriteTransaction writeOperTxn) {
        LOG.debug("NAT Service : Handling UP event for router interface {} in Router {}", interfaceName, routerName);
        NatUtil.addToNeutronRouterDpnsMap(dataBroker, routerName, interfaceName, interfaceManager, writeOperTxn);
        NatUtil.addToDpnRoutersMap(dataBroker, routerName, interfaceName, interfaceManager, writeOperTxn);
    }

    void handleRouterInterfacesDownEvent(String routerName, String interfaceName, BigInteger dpnId,
                                         WriteTransaction writeOperTxn) {
        LOG.debug("NAT Service : Handling DOWN event for router Interface {} in Router {}", interfaceName, routerName);
        NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, interfaceName, dpnId, writeOperTxn);
    }
}

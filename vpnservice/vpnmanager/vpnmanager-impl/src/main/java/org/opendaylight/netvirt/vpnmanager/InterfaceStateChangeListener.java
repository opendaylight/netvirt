/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class InterfaceStateChangeListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private VpnInterfaceManager vpnInterfaceManager;
    private OdlInterfaceRpcService interfaceManager;


    public InterfaceStateChangeListener(final DataBroker db, VpnInterfaceManager vpnInterfaceManager) {
        super(Interface.class);
        broker = db;
        this.vpnInterfaceManager = vpnInterfaceManager;
        registerListener(db);
    }

    public void setInterfaceManager(OdlInterfaceRpcService interfaceManager) {
      this.interfaceManager = interfaceManager;
  }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("Interface listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                                                                 getWildCardPath(), InterfaceStateChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Interface DataChange listener registration failed", e);
            throw new IllegalStateException("Nexthop Manager registration Listener failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
      LOG.trace("Received interface {} up event", intrf);
      try {
        String interfaceName = intrf.getName();
        LOG.info("Received port UP event for interface {} ", interfaceName);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
            configInterface = InterfaceUtils.getInterface(broker, interfaceName);
        BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
        if (configInterface != null && configInterface.getType().equals(Tunnel.class)) {
          if(intrf.getOperStatus().equals(Interface.OperStatus.Up)) {
            //advertise all prefixes in all vpns for this dpn to bgp
            // FIXME: Blocked until tunnel event[vxlan/gre] support is available
            // vpnInterfaceManager.updatePrefixesForDPN(dpnId, VpnInterfaceManager.UpdateRouteAction.ADVERTISE_ROUTE);
          }
        } else {
          vpnInterfaceManager.processVpnInterfaceUp(dpnId, interfaceName, intrf.getIfIndex());
          vpnInterfaceManager.getVpnSubnetRouteHandler().onInterfaceUp(intrf);
          handleRouterInterfacesUpEvent(interfaceName);
        }
      } catch (Exception e) {
        LOG.error("Exception caught in Interface Operational State Up event", e);
      }
    }


    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
      LOG.trace("Received interface {} down event", intrf);
      try {
        String interfaceName = intrf.getName();
        LOG.info("Received port DOWN event for interface {} ", interfaceName);
        BigInteger dpId = InterfaceUtils.getDpIdFromInterface(intrf);
        if (intrf != null && intrf.getType() != null && intrf.getType().equals(Tunnel.class)) {
          //withdraw all prefixes in all vpns for this dpn from bgp
          // FIXME: Blocked until tunnel event[vxlan/gre] support is available
          // vpnInterfaceManager.updatePrefixesForDPN(dpId, VpnInterfaceManager.UpdateRouteAction.WITHDRAW_ROUTE);
        } else {
          if (VpnUtil.isVpnInterfaceConfigured(broker, interfaceName)) {
            vpnInterfaceManager.processVpnInterfaceDown(dpId, interfaceName, intrf.getIfIndex(), true);
            vpnInterfaceManager.getVpnSubnetRouteHandler().onInterfaceDown(intrf);
            handleRouterInterfacesDownEvent(interfaceName,dpId);
          }
        }
      } catch (Exception e) {
        LOG.error("Exception caught in onVlanInterfaceOperationalStateDown", e);
      }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
      LOG.trace("Operation Interface update event - Old: {}, New: {}", original, update);
      String interfaceName = update.getName();
      org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
          intf = InterfaceUtils.getInterface(broker, interfaceName);
      if (intf != null && intf.getType().equals(Tunnel.class)) {
        /*
        // FIXME: Blocked until tunnel event[vxlan/gre] support is available
        BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(update);
        if(update.getOperStatus().equals(Interface.OperStatus.Up)) {
          //advertise all prefixes in all vpns for this dpn to bgp
          // vpnInterfaceManager.updatePrefixesForDPN(dpnId, VpnInterfaceManager.UpdateRouteAction.ADVERTISE_ROUTE);
          vpnInterfaceManager.getVpnSubnetRouteHandler().onInterfaceUp(update);
        } else if(update.getOperStatus().equals(Interface.OperStatus.Down)) {
          //withdraw all prefixes in all vpns for this dpn from bgp
          // vpnInterfaceManager.updatePrefixesForDPN(dpnId, VpnInterfaceManager.UpdateRouteAction.WITHDRAW_ROUTE);
          vpnInterfaceManager.getVpnSubnetRouteHandler().onInterfaceDown(update);
        }*/
      }

    }

    void handleRouterInterfacesUpEvent(String interfaceName) {
        Optional<RouterInterface> optRouterInterface = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, VpnUtil.getRouterInterfaceId(interfaceName));
        if(optRouterInterface.isPresent()) {
            RouterInterface routerInterface = optRouterInterface.get();
            String routerName = routerInterface.getRouterName();
            LOG.debug("Handling UP event for router interface {} in Router {}", interfaceName, routerName);
            vpnInterfaceManager.addToNeutronRouterDpnsMap(routerName, interfaceName);
        } else {
            LOG.debug("No Router interface configured to handle UP event for {}", interfaceName);
        }
    }

    void handleRouterInterfacesDownEvent(String interfaceName,BigInteger dpnId) {
        Optional<RouterInterface> optRouterInterface = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, VpnUtil.getRouterInterfaceId(interfaceName));
        if(optRouterInterface.isPresent()) {
            RouterInterface routerInterface = optRouterInterface.get();
            String routerName = routerInterface.getRouterName();
            LOG.debug("Handling DOWN event for router interface {} in Router {}", interfaceName, routerName);
            vpnInterfaceManager.removeFromNeutronRouterDpnsMap(routerName, interfaceName,dpnId);
        } else {
            LOG.debug("No Router interface configured to handle  DOWN event for {}", interfaceName);
        }
    }

}

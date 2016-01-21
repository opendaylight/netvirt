/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
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
            vpnInterfaceManager.updatePrefixesForDPN(dpnId, VpnInterfaceManager.UpdateRouteAction.ADVERTISE_ROUTE);
          }
        } else {
          vpnInterfaceManager.processVpnInterfaceUp(dpnId, interfaceName, intrf.getIfIndex());
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
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
            intf = InterfaceUtils.getInterface(broker, interfaceName);
        BigInteger dpId = InterfaceUtils.getDpIdFromInterface(intrf);
        if (intf != null && intf.getType().equals(Tunnel.class)) {
          if(intrf.getOperStatus().equals(Interface.OperStatus.Down)) {
            //withdraw all prefixes in all vpns for this dpn from bgp
            vpnInterfaceManager.updatePrefixesForDPN(dpId, VpnInterfaceManager.UpdateRouteAction.WITHDRAW_ROUTE);
          }
        } else {
          if (VpnUtil.isVpnInterfaceConfigured(broker, interfaceName)) {
            vpnInterfaceManager.processVpnInterfaceDown(dpId, interfaceName, intrf.getIfIndex(), true);
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
        BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(update);
        if(update.getOperStatus().equals(Interface.OperStatus.Up)) {
          //advertise all prefixes in all vpns for this dpn to bgp
          vpnInterfaceManager.updatePrefixesForDPN(dpnId, VpnInterfaceManager.UpdateRouteAction.ADVERTISE_ROUTE);
        } else if(update.getOperStatus().equals(Interface.OperStatus.Down)) {
          //withdraw all prefixes in all vpns for this dpn from bgp
          vpnInterfaceManager.updatePrefixesForDPN(dpnId, VpnInterfaceManager.UpdateRouteAction.WITHDRAW_ROUTE);
        }
      }

    }

}

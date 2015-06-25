package org.opendaylight.vpnservice;

import java.math.BigInteger;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.L3tunnel;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceChangeListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private VpnInterfaceManager vpnInterfaceManager;
    private IInterfaceManager interfaceManager;


    public InterfaceChangeListener(final DataBroker db, VpnInterfaceManager vpnInterfaceManager) {
        super(Interface.class);
        broker = db;
        this.vpnInterfaceManager = vpnInterfaceManager;
        registerListener(db);
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
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
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), InterfaceChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Interface DataChange listener registration failed", e);
            throw new IllegalStateException("Nexthop Manager registration Listener failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("Adding Interface : key: " + identifier + ", value=" + intrf );

    }


    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("Remove interface event - key: {}, value: {}", identifier, intrf );
        if (intrf.getType().equals(L3tunnel.class)) {
          BigInteger dpnId =  interfaceManager.getDpnForInterface(intrf);
          String ifName = intrf.getName();
          LOG.debug("Removing tunnel interface associated with Interface {}", intrf.getName());
          vpnInterfaceManager.makeTunnelIngressFlow(dpnId, ifName, NwConstants.DEL_FLOW);
      }
        else {
        VpnInterface vpnInterface = vpnInterfaceManager.getVpnInterface(intrf.getName());
          if (vpnInterface !=null) {
            InstanceIdentifier<VpnInterface> id = VpnUtil.getVpnInterfaceIdentifier(intrf.getName());
            LOG.debug("Removing VPN Interface associated with Interface {}", intrf.getName());
            vpnInterfaceManager.remove(id, vpnInterface);
          }
          else {
            LOG.debug("No VPN Interface associated with Interface {}", intrf.getName());
          }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
        // TODO Auto-generated method stub

    }

}

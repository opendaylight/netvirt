/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import java.math.BigInteger;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6ServiceInterfaceEventListener extends AsyncDataTreeChangeListenerBase<Interface,
    Ipv6ServiceInterfaceEventListener>
    implements ClusteredDataTreeChangeListener<Interface>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceInterfaceEventListener.class);
    private final DataBroker broker;
    private IfMgr ifMgr;
    private Ipv6ServiceUtils ipv6ServiceUtils;
    private IMdsalApiManager mdsalUtil;

    /**
     * Intialize the member variables.
     * @param broker the data broker instance.
     */
    public Ipv6ServiceInterfaceEventListener(DataBroker broker, IMdsalApiManager mdsalUtil) {
        super(Interface.class, Ipv6ServiceInterfaceEventListener.class);
        this.broker = broker;
        this.mdsalUtil = mdsalUtil;
        ipv6ServiceUtils = new Ipv6ServiceUtils();
    }

    public void setIfMgrInstance(IfMgr instance) {
        this.ifMgr = instance;
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface del) {
        List<String> ofportIds = del.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty()) {
            return;
        }
        String interfaceName = del.getName();
        ImmutablePair<String, VirtualPort> pair = ifMgr.getInterfaceCache(interfaceName);
        if (pair != null && pair.getLeft() != null && pair.getRight() != null) {
            NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
            BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
            if (!dpId.equals(Ipv6Constants.INVALID_DPID)) {
                VirtualPort routerPort = pair.getRight();
                ipv6ServiceUtils.unbindIpv6Service(broker, interfaceName);
                ipv6ServiceUtils.installIcmpv6Flows(interfaceName, Ipv6Constants.IPV6_TABLE, dpId, pair.getLeft(),
                        mdsalUtil, Ipv6Constants.DEL_FLOW);
                ifMgr.removeInterfaceCache(interfaceName);
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface dataObjectModificationBefore,
                          Interface dataObjectModificationAfter) {
        // TODO Auto-generated method stub
        LOG.info("Port updated...");
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface add) {
        List<String> ofportIds = add.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty() || add.getName().contains(":")) {
            return;
        }
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface;
        iface = Ipv6ServiceUtils.getInterface(broker, add.getName());
        if (null != iface) {
            LOG.info("Port added {}", iface);
            NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
            BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));

            if (!dpId.equals(Ipv6Constants.INVALID_DPID)) {
                VirtualPort port = ifMgr.obtainV6Interface(new Uuid(iface.getName()));
                if (port == null) {
                    LOG.info("Port {} not found, skipping.", port);
                    return;
                }

                VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(port.getNetworkID());
                if (routerPort == null) {
                    LOG.info("Port {} is not associated to a Router, skipping.", routerPort);
                    return;
                }

                ipv6ServiceUtils.installIcmpv6Flows(iface.getName(), Ipv6Constants.IPV6_TABLE, dpId,
                        port.getMacAddress(), mdsalUtil, Ipv6Constants.ADD_FLOW);
                ipv6ServiceUtils.bindIpv6Service(broker, iface.getName(), Ipv6Constants.IPV6_TABLE);
                ifMgr.updateInterfaceCache(iface.getName(), new ImmutablePair<>(port.getMacAddress(), routerPort));
            }
        }
    }

    @Override
    protected Ipv6ServiceInterfaceEventListener getDataTreeChangeListener() {
        return Ipv6ServiceInterfaceEventListener.this;
    }
}

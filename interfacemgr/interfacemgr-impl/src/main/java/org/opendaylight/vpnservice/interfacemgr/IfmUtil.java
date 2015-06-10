/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr;

import java.math.BigInteger;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.Pools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.IdPoolKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

public class IfmUtil {

    public static String getDpnFromNodeConnectorId(NodeConnectorId portId) {
        /*
         * NodeConnectorId is of form 'openflow:dpnid:portnum'
         */
        String[] split = portId.getValue().split(IfmConstants.OF_URI_SEPARATOR);
        return split[1];
    }

    public static String getPortNoFromNodeConnectorId(NodeConnectorId portId) {
        /*
         * NodeConnectorId is of form 'openflow:dpnid:portnum'
         */
        String[] split = portId.getValue().split(IfmConstants.OF_URI_SEPARATOR);
        return split[2];
    }

    public static NodeId buildDpnNodeId(BigInteger dpnId) {
        return new NodeId(IfmConstants.OF_URI_PREFIX + dpnId);
    }

    public static InstanceIdentifier<Interface> buildId(String interfaceName) {
        //TODO Make this generic and move to AbstractDataChangeListener or Utils.
        InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        return id;
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.class,
                                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey(interfaceName));
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> id = idBuilder.build();
        return id;
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey getStateInterfaceKeyFromName(
                    String name) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey(name);
    }

    public static InstanceIdentifier<IdPool> getPoolId(String poolName){
        InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idBuilder =
                        InstanceIdentifier.builder(Pools.class).child(IdPool.class, new IdPoolKey(poolName));
        InstanceIdentifier<IdPool> id = idBuilder.build();
        return id;
    }
}

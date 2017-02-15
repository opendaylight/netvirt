package org.opendaylight.netvirt.neutronvpn;
/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;

import java.util.List;

public class NeutronQosManager {

    public static Network fetchNetworkForQos(DataBroker broker, Uuid networkId){
        return NeutronvpnUtils.getNeutronNetwork(broker, networkId);
    }

    public static List<Uuid> fetchSubnetIdsForQos(DataBroker broker, Uuid networkId){
        return NeutronvpnUtils.getSubnetIdsFromNetworkId(broker, networkId);
    }

    public static List<Uuid> fetchPortIdsForQos(DataBroker broker, Uuid subnetId){
        return NeutronvpnUtils.getPortIdsFromSubnetId(broker, subnetId);
    }

}

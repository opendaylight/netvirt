/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.utils.hwvtep;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeVxlanOverIpv4;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;

import com.google.common.collect.ImmutableBiMap;

// TODO (eperefr) Remove this class once it's been added to hwvtep-southbound
public class HwvtepSouthboundConstants {

    public static final TopologyId HWVTEP_TOPOLOGY_ID = new TopologyId("hwvtep:1");
    public static final String HWVTEP_ENTITY_TYPE = "hwvtep";
    public static final Object PSWITCH_URI_PREFIX = "physicalswitch";
    public static final ImmutableBiMap<Class<? extends EncapsulationTypeBase>, String> ENCAPS_TYPE_MAP = new ImmutableBiMap.Builder<Class<? extends EncapsulationTypeBase>, String>()
            .put(EncapsulationTypeVxlanOverIpv4.class, "vxlan_over_ipv4").build();

}

/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NetvirtProvider {

    private static final Logger LOG = LoggerFactory.getLogger(NetvirtProvider.class);
    private final DataBroker dataBroker;

    @Inject
    public NetvirtProvider(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public List<String> getLogicalInterfacesFromNeutronNetwork(NeutronNetwork nw) {
        InstanceIdentifier<NetworkMap> networkMapIdentifier =
                getNetworkMapIdentifier(new Uuid(nw.getNetworkUuid()));

        NetworkMap networkMap =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, networkMapIdentifier).orNull();
        if (networkMap == null) {
            LOG.warn("getLogicalInterfacesFromNeutronNetwork cant get NetworkMap for NW UUID [{}]",
                    nw.getNetworkUuid());
            return Collections.emptyList();
        }

        List<String> interfaces = new ArrayList<>();
        List<Uuid> subnetUuidList = networkMap.getSubnetIdList();
        if (subnetUuidList != null) {
            for (Uuid subnetUuid : subnetUuidList) {
                InstanceIdentifier<Subnetmap> subnetId = getSubnetMapIdentifier(subnetUuid);
                Subnetmap subnet = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetId).orNull();
                if (subnet == null) {
                    LOG.warn(
                            "getLogicalInterfacesFromNeutronNetwork cant get Subnetmap for NW UUID [{}] Subnet UUID "
                                    + "[{}]",
                            nw.getNetworkUuid(), subnetUuid.getValue());
                    continue;
                }

                if (subnet.getPortList() == null || subnet.getPortList().isEmpty()) {
                    LOG.warn("getLogicalInterfacesFromNeutronNetwork No ports on Subnet: NW UUID [{}] Subnet UUID [{}]",
                            nw.getNetworkUuid(), subnetUuid.getValue());
                    continue;
                }

                subnet.getPortList().forEach(portId -> interfaces.add(portId.getValue()));
            }
        }

        return interfaces;
    }

    //
    // Internal Util methods
    //

    private InstanceIdentifier<NetworkMap> getNetworkMapIdentifier(Uuid nwUuid) {
        return InstanceIdentifier.builder(NetworkMaps.class)
                .child(NetworkMap.class,new NetworkMapKey(nwUuid)).build();
    }

    private InstanceIdentifier<Subnetmap> getSubnetMapIdentifier(Uuid subnetUuid) {
        return InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetUuid)).build();
    }
}

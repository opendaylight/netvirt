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
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class NetvirtProvider {

    private final MdsalUtils mdsalUtils;

    @Inject
    public NetvirtProvider(final DataBroker dataBroker) {
        this.mdsalUtils = new MdsalUtils(dataBroker);
    }

    public List<String> getLogicalInterfacesFromNeutronNetwork(NeutronNetwork nw) {
        InstanceIdentifier<NetworkMap> networkMapIdentifier =
                getNetworkMapIdentifier(new Uuid(nw.getNetworkUuid()));

        NetworkMap networkMap = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, networkMapIdentifier);
        if (networkMap == null) {
            return Collections.emptyList();
        }

        List<String> interfaces = new ArrayList<>();
        List<Uuid> subnetUuidList = Optional.ofNullable(networkMap.getSubnetIdList()).orElse(Collections.emptyList());
        for (Uuid subnetUuid : subnetUuidList) {
            InstanceIdentifier<Subnetmap> subnetId = getSubnetMapIdentifier(subnetUuid);
            Subnetmap subnet = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, subnetId);
            if (subnet == null) {
                continue;
            }

            for (Uuid subnetPortUuid : subnet.getPortList()) {
                Optional<String> portId = getNeutronPort(subnetPortUuid);
                if (portId.isPresent()) {
                    interfaces.add(portId.get());
                }
            }
        }

        return interfaces;
    }

    //
    // Internal Util methods
    //

    private InstanceIdentifier<NetworkMap> getNetworkMapIdentifier(Uuid nwUuidStr) {
        return InstanceIdentifier.builder(NetworkMaps.class)
                .child(NetworkMap.class,new NetworkMapKey(nwUuidStr)).build();
    }

    private InstanceIdentifier<Subnetmap> getSubnetMapIdentifier(Uuid subnetUuidStr) {
        return InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetUuidStr)).build();
    }

    // Returns the Port UUID string, which is the same as a Genius Logical Interface name
    private Optional<String> getNeutronPort(Uuid portUuid) {
        InstanceIdentifier<Port> instId = InstanceIdentifier.create(Neutron.class).child(Ports.class)
                .child(Port.class, new PortKey(portUuid));
        Port port = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, instId);

        return port == null
                ? Optional.empty() : Optional.of(port.getUuid().getValue());
    }

}

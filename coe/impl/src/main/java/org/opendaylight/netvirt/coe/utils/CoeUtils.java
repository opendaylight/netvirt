/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.coe.utils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.NetworkAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CoeUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CoeUtils.class);

    private static final String SEPARATOR = ":";
    public static final ImmutableBiMap<NetworkAttributes.NetworkType, Class<? extends SegmentTypeBase>>
            NETWORK_MAP =
            new ImmutableBiMap.Builder<NetworkAttributes.NetworkType, Class<? extends SegmentTypeBase>>()
                    .put(NetworkAttributes.NetworkType.FLAT, SegmentTypeFlat.class)
                    .put(NetworkAttributes.NetworkType.GRE, SegmentTypeGre.class)
                    .put(NetworkAttributes.NetworkType.VLAN, SegmentTypeVlan.class)
                    .put(NetworkAttributes.NetworkType.VXLAN, SegmentTypeVxlan.class)
                    .build();

    private CoeUtils() { }

    public static InstanceIdentifier<Interface> buildVlanInterfaceIdentifier(String interfaceName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                .interfaces.Interface> id = InstanceIdentifier.builder(Interfaces.class).child(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                        .Interface.class, new InterfaceKey(interfaceName)).build();
        return id;
    }

    public static String buildInterfaceName(String networkNS, String podName) {
        return new StringBuilder().append(networkNS).append(SEPARATOR).append(podName).toString();
    }

    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .Interface buildInterface(String interfaceName) {
        IfL2vlan.L2vlanMode l2VlanMode = IfL2vlan.L2vlanMode.Trunk;
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder();
        IfL2vlanBuilder ifL2vlanBuilder = new IfL2vlanBuilder();
        ifL2vlanBuilder.setL2vlanMode(l2VlanMode);

        interfaceBuilder.setEnabled(true).setName(interfaceName).setType(L2vlan.class)
                .addAugmentation(IfL2vlan.class, ifL2vlanBuilder.build());

        return interfaceBuilder.build();
    }

    static ElanInstance buildElanInstance(String elanInstanceName, Class<? extends SegmentTypeBase> segmentType,
                                           String segmentationId,
                                           Boolean isExternal) {
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName);
        if (segmentType != null) {
            elanInstanceBuilder.setSegmentType(segmentType);
            if (segmentationId != null) {
                elanInstanceBuilder.setSegmentationId(Long.valueOf(segmentationId));
            }
        }

        elanInstanceBuilder.setExternal(isExternal);
        elanInstanceBuilder.setKey(new ElanInstanceKey(elanInstanceName));
        return elanInstanceBuilder.build();
    }

    public static void createElanInterface(org.opendaylight.yang.gen.v1.urn.opendaylight.coe
                                                   .northbound.pod.rev170611.pod_attributes.Interface podInterface,
                                           String elanInterfaceName, String elanInstanceName,
                                           WriteTransaction wrtConfigTxn) {
        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(elanInterfaceName)).build();
        // TODO set static mac entries based on pod interface mac
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(elanInterfaceName).setKey(new ElanInterfaceKey(elanInterfaceName)).build();
        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, id, elanInterface);
        LOG.debug("Creating new ELAN Interface {}", elanInterface);
    }

    public static void deleteElanInterface(String elanInterfaceName, WriteTransaction wrtConfigTxn) {
        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(elanInterfaceName)).build();
        wrtConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, id);
        LOG.debug("Deleting ELAN Interface {}", elanInterfaceName);
    }

    public static String createOfPortInterface(String interfaceName,
                                               org.opendaylight.yang.gen.v1.urn.opendaylight.coe
                                                       .northbound.pod.rev170611.pod_attributes.Interface podInterface,
                                               WriteTransaction wrtConfigTxn) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface inf =
                buildInterface(interfaceName);
        String infName = inf.getName();
        LOG.info("Creating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = CoeUtils.buildVlanInterfaceIdentifier(infName);
        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
        return infName;
    }

    public static void deleteOfPortInterface(String infName, WriteTransaction wrtConfigTxn) {
        LOG.debug("Deleting OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = CoeUtils.buildVlanInterfaceIdentifier(infName);
        wrtConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier);
    }

    static InstanceIdentifier<ElanInstance> createElanInstanceIdentifier(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class,
                new ElanInstanceKey(elanInstanceName)).build();
        return id;
    }

    public static Class<? extends SegmentTypeBase> getSegmentTypeFromNetwork(
            org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.pod_attributes.Interface
                    elanInterface) {
        return CoeUtils.NETWORK_MAP.get(elanInterface.getNetworkType());
    }

    public static String buildElanInstanceName(String nodeIp, String networkNS) {
        return new StringBuilder().append(nodeIp).append(SEPARATOR).append(networkNS).toString();
    }

    public static ElanInstance createElanInstanceForTheFirstPodInTheNetwork(String networkNS, String nodeIp,
            org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.pod_attributes.Interface
                    podInterface, ReadWriteTransaction tx) throws ReadFailedException {
        String elanInstanceName = buildElanInstanceName(nodeIp, networkNS);
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        Optional<ElanInstance> existingElanInstance = tx.read(LogicalDatastoreType.CONFIGURATION, id).checkedGet();
        if (existingElanInstance.isPresent()) {
            return existingElanInstance.get();
        }
        Class<? extends SegmentTypeBase> segmentType = getSegmentTypeFromNetwork(podInterface);
        String segmentationId = String.valueOf(podInterface.getSegmentationId());
        //FIXME String physicalNetworkName = ??
        // TODO external network support not added currently
        Boolean isExternal = false;
        ElanInstance elanInstance = CoeUtils.buildElanInstance(elanInstanceName, segmentType,
                segmentationId, isExternal);
        tx.put(LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        LOG.info("ELAN instance created for the first pod in the network {}", podInterface.getUid());
        return elanInstance;
    }
}

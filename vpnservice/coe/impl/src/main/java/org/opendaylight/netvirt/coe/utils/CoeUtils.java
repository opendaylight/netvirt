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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
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

public class CoeUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CoeUtils.class);
    public static final ImmutableBiMap<Class<? extends NetworkAttributes.NetworkType>, Class<? extends SegmentTypeBase>>
            NETWORK_MAP =
            new ImmutableBiMap.Builder<Class<? extends NetworkAttributes.NetworkType>,
                    Class<? extends SegmentTypeBase>>()
                    .put(NetworkAttributes.NetworkType.FLAT.getClass(), SegmentTypeFlat.class)
                    .put(NetworkAttributes.NetworkType.GRE.getClass(), SegmentTypeGre.class)
                    .put(NetworkAttributes.NetworkType.VLAN.getClass(), SegmentTypeVlan.class)
                    .put(NetworkAttributes.NetworkType.VXLAN.getClass(), SegmentTypeVxlan.class)
                    .build();

    public static InstanceIdentifier<Interface> buildVlanInterfaceIdentifier(String interfaceName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                .interfaces.Interface> id = InstanceIdentifier.builder(Interfaces.class).child(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                        .Interface.class, new InterfaceKey(interfaceName)).build();
        return id;
    }

    static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .Interface buildInterface(org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611
                                                         .pod_attributes.Interface podInterface) {
        String interfaceName = podInterface.getUid().getValue();
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
                                           String name, WriteTransaction wrtConfigTxn) {
        String elanInstanceName = podInterface.getNetworkId().getValue();
        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(name).setKey(new ElanInterfaceKey(name)).build();
        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, id, elanInterface);
        LOG.debug("Creating new ELAN Interface {}", elanInterface);
    }

    public static String createOfPortInterface(org.opendaylight.yang.gen.v1.urn.opendaylight.coe
                                                       .northbound.pod.rev170611.pod_attributes.Interface podInterface,
                                               WriteTransaction wrtConfigTxn, DataBroker dataBroker) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface inf =
                buildInterface(podInterface);
        String infName = inf.getName();
        LOG.debug("Creating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = CoeUtils.buildVlanInterfaceIdentifier(infName);
        Optional<Interface> optionalInf = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                interfaceIdentifier);
        if (!optionalInf.isPresent()) {
            wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
        } else {
            LOG.warn("Interface {} is already present", infName);
        }
        return infName;
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

    public static ElanInstance createElanInstanceForTheFirstPodInTheNetwork(
            org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.pod_attributes.Interface
                    podInterface, WriteTransaction wrtConfigTxn, DataBroker dataBroker) {
        String elanInstanceName = podInterface.getNetworkId().getValue();
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        Optional<ElanInstance> existingElanInstance = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                id);
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
        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, id, elanInstance);
        LOG.info("ELAN instance created for the first pod in the network {}", podInterface.getUid());
        return elanInstance;
    }
}

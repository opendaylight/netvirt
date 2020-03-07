/*
 * Copyright (c) 2017 - 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.utils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.networkutils.RDUtils;
import org.opendaylight.genius.networkutils.VniUtils;
import org.opendaylight.netvirt.coe.api.SouthboundInterfaceInfo;
import org.opendaylight.netvirt.coe.api.SouthboundInterfaceInfoBuilder;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4FamilyBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv6FamilyBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNamesBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNamesKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.NetworkAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.coe.Pods;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.service.rev170611.service.information.Services;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.coe.meta.rev180118.PodidentifierInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.coe.meta.rev180118.podidentifier.info.PodIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.coe.meta.rev180118.podidentifier.info.PodIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.coe.meta.rev180118.podidentifier.info.PodIdentifierKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.coe.service.meta.rev190123.ServiceGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.coe.service.meta.rev190123.service.gateway.info.ServiceGateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.coe.service.meta.rev190123.service.gateway.info.ServiceGatewayBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.coe.service.meta.rev190123.service.gateway.info.ServiceGatewayKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceExternalIds;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
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

    private final VniUtils vniUtils;
    private final RDUtils rdUtils;

    @Inject
    public CoeUtils(@Reference VniUtils vniUtils, @Reference RDUtils rdUtils) {
        this.vniUtils = vniUtils;
        this.rdUtils = rdUtils;
    }

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

    public static Class<? extends SegmentTypeBase> getSegmentTypeFromNetwork(
            org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.pod_attributes.Interface
                    elanInterface) {
        return CoeUtils.NETWORK_MAP.get(elanInterface.getNetworkType());
    }

    public static String buildElanInstanceName(String nodeIp, String networkNS) {
        return new StringBuilder().append(nodeIp).append(SEPARATOR).append(networkNS).toString();
    }

    public static InstanceIdentifier<PodIdentifier> getPodMetaInstanceId(String externalInterfaceId) {
        return InstanceIdentifier.builder(PodidentifierInfo.class)
                .child(PodIdentifier.class, new PodIdentifierKey(externalInterfaceId)).build();
    }

    public static InstanceIdentifier<BoundServices> buildKubeProxyServicesIId(String interfaceName) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(NwConstants.COE_KUBE_PROXY_SERVICE_INDEX)).build();
    }

    static InstanceIdentifier<VpnInstance> buildVpnInstance(String vpnName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName))
                .build();
        return id;
    }

    static InstanceIdentifier<VpnInterface> buildVpnInterfaceIdentifier(String ifName) {
        InstanceIdentifier<VpnInterface> id = InstanceIdentifier.builder(VpnInterfaces.class).child(VpnInterface
                .class, new VpnInterfaceKey(ifName)).build();
        return id;
    }

    static InstanceIdentifier<ElanInstance> createElanInstanceIdentifier(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class,
                new ElanInstanceKey(elanInstanceName)).build();
        return id;
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

    ElanInstance buildElanInstance(String elanInstanceName, Class<? extends SegmentTypeBase> segmentType,
                                   Boolean isExternal) throws ExecutionException, InterruptedException {
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName);
        if (segmentType != null) {
            elanInstanceBuilder.setSegmentType(segmentType);
            elanInstanceBuilder.setSegmentationId(vniUtils.getVNI(elanInstanceName).longValue());
        }

        elanInstanceBuilder.setExternal(isExternal);
        elanInstanceBuilder.withKey(new ElanInstanceKey(elanInstanceName));
        return elanInstanceBuilder.build();
    }

    public void createElanInterface(String elanInterfaceName, String elanInstanceName,
                                           TypedReadWriteTransaction<Datastore.Configuration> wrtConfigTxn) {
        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(elanInterfaceName)).build();
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(elanInterfaceName).withKey(new ElanInterfaceKey(elanInterfaceName)).build();
        wrtConfigTxn.put(id, elanInterface);
        LOG.debug("Creating new ELAN Interface {}", elanInterface);
    }

    public void updateElanInterfaceWithStaticMac(String macAddress, IpAddress ipAddress,
                                                 String elanInterfaceName, String elanInstanceName,
                                                 TypedReadWriteTransaction<Datastore.Configuration> wrtConfigTxn) {
        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(elanInterfaceName)).build();
        PhysAddress physAddress = PhysAddress.getDefaultInstance(macAddress);
        List<StaticMacEntries> staticMacEntriesList = new ArrayList<>();
        StaticMacEntries staticMacEntries = new StaticMacEntriesBuilder().withKey(new StaticMacEntriesKey(
                physAddress)).setMacAddress(physAddress).setIpPrefix(ipAddress).build();
        staticMacEntriesList.add(staticMacEntries);
        ElanInterface elanInterface = new ElanInterfaceBuilder().setName(elanInterfaceName)
                .setElanInstanceName(elanInstanceName).withKey(
                        new ElanInterfaceKey(elanInterfaceName)).setStaticMacEntries(staticMacEntriesList).build();
        wrtConfigTxn.merge(id, elanInterface);
        LOG.debug("Updating ELAN Interface with static mac {}", elanInterface);
    }

    public void createPodNameToPodUuidMap(String podName, InstanceIdentifier<Pods> pod,
                                                 TypedWriteTransaction<Datastore.Operational> writeTransaction) {
        InstanceIdentifier<PodIdentifier> id = InstanceIdentifier.builder(PodidentifierInfo.class)
                .child(PodIdentifier.class, new PodIdentifierKey(podName)).build();
        PodIdentifier podIdentifier = new PodIdentifierBuilder().withKey(new PodIdentifierKey(podName))
                .setPodName(podName).setPodUuid(pod).build();
        writeTransaction.put(id, podIdentifier);
        LOG.debug("Creating podnametouuid map {} to {}", podName, pod);
    }

    public void deletePodNameToPodUuidMap(String podName,
                                                 TypedWriteTransaction<Datastore.Operational> writeTransaction) {
        InstanceIdentifier<PodIdentifier> id = InstanceIdentifier.builder(PodidentifierInfo.class)
                .child(PodIdentifier.class, new PodIdentifierKey(podName)).build();
        writeTransaction.delete(id);
        LOG.debug("Deleting podnametouuid map for {}", podName);
    }

    public InstanceIdentifier<Pods> getPodUUIDforPodName(String podName, DataBroker dataBroker)
            throws ExecutionException, InterruptedException {
        ReadTransaction readTransaction = dataBroker.newReadOnlyTransaction();
        InstanceIdentifier<PodIdentifier> id = InstanceIdentifier.builder(PodidentifierInfo.class)
                .child(PodIdentifier.class, new PodIdentifierKey(podName)).build();
        InstanceIdentifier<?> instanceIdentifier = readTransaction.read(LogicalDatastoreType.OPERATIONAL, id)
                .get().toJavaUtil().map(PodIdentifier::getPodUuid).orElse(null);
        if (instanceIdentifier != null) {
            return (InstanceIdentifier<Pods>) instanceIdentifier;
        }
        return null;
    }

    public void deleteElanInterface(String elanInterfaceName,
                                           TypedWriteTransaction<Datastore.Configuration> wrtConfigTxn) {
        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(elanInterfaceName)).build();
        wrtConfigTxn.delete(id);
        LOG.debug("Deleting ELAN Interface {}", elanInterfaceName);
    }

    public String createOfPortInterface(String interfaceName,
                                               TypedReadWriteTransaction<Datastore.Configuration> wrtConfigTxn) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface inf =
                buildInterface(interfaceName);
        String infName = inf.getName();
        LOG.info("Creating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = CoeUtils.buildVlanInterfaceIdentifier(infName);
        wrtConfigTxn.put(interfaceIdentifier, inf);
        return infName;
    }

    public void deleteOfPortInterface(String infName,
                                             TypedWriteTransaction<Datastore.Configuration> wrtConfigTxn) {
        LOG.debug("Deleting OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = CoeUtils.buildVlanInterfaceIdentifier(infName);
        wrtConfigTxn.delete(interfaceIdentifier);
    }

    public ElanInstance
        createElanInstanceForTheFirstPodInTheNetwork(String clusterId, String nodeIp,
                                                 org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod
                                                         .rev170611.pod_attributes.Interface podInterface,
                                                 TypedReadWriteTransaction<Datastore.Configuration> wrtConfigTxn)
            throws ExecutionException, InterruptedException {
        String elanInstanceName = buildElanInstanceName(nodeIp, clusterId);
        InstanceIdentifier<ElanInstance> id = createElanInstanceIdentifier(elanInstanceName);
        ElanInstance existingElanInstance = wrtConfigTxn.read(id).get().orNull();
        if (existingElanInstance != null) {
            return existingElanInstance;
        }
        Class<? extends SegmentTypeBase> segmentType = getSegmentTypeFromNetwork(podInterface);
        //FIXME String physicalNetworkName = ??
        // TODO external network support not added currently
        Boolean isExternal = false;
        ElanInstance elanInstance = buildElanInstance(elanInstanceName, segmentType, isExternal);
        wrtConfigTxn.put(id, elanInstance);
        LOG.info("ELAN instance created for the first pod in the network {}", podInterface.getUid());
        return elanInstance;
    }

    public SouthboundInterfaceInfo getSouthboundInterfaceDetails(OvsdbTerminationPointAugmentation ovsdbTp) {
        SouthboundInterfaceInfoBuilder southboundInterfaceInfoBuilder = new SouthboundInterfaceInfoBuilder();
        if (ovsdbTp != null) {
            List<InterfaceExternalIds> ifaceExtIds = ovsdbTp.getInterfaceExternalIds();
            if (ifaceExtIds != null) {
                Iterator var2 = ifaceExtIds.iterator();
                while (var2.hasNext()) {
                    InterfaceExternalIds entry = (InterfaceExternalIds)var2.next();
                    if (entry.getExternalIdKey().equals("iface-id")) {
                        southboundInterfaceInfoBuilder.setInterfaceName(entry.getExternalIdValue());
                        continue;
                    }
                    if (entry.getExternalIdKey().equals("attached-mac")) {
                        southboundInterfaceInfoBuilder.setMacAddress(entry.getExternalIdValue());
                        continue;
                    }
                    if (entry.getExternalIdKey().equals("ip-address")) {
                        southboundInterfaceInfoBuilder.setNodeIp(entry.getExternalIdValue());
                        continue;
                    }
                    if (entry.getExternalIdKey().equals("is-service-gateway")) {
                        southboundInterfaceInfoBuilder.setIsServiceGateway(true);
                        continue;
                    }
                }
            }
        }

        return southboundInterfaceInfoBuilder.build();
    }

    public void createVpnInstance(String vpnName, List<String> rd, List<String> irt, List<String> ert,
                                         VpnInstance.Type type, long l3vni, IpVersionChoice ipVersion,
                                         TypedReadWriteTransaction<Datastore.Configuration> tx)
            throws ExecutionException, InterruptedException {
        List<VpnTarget> vpnTargetList = new ArrayList<>();
        LOG.debug("Creating/Updating a new vpn-instance node:{}", vpnName);

        VpnInstanceBuilder builder = new VpnInstanceBuilder().withKey(new VpnInstanceKey(vpnName))
                .setVpnInstanceName(vpnName)
                .setType(type).setL3vni(l3vni);
        if (rd == null) {
            String autoRd = rdUtils.getRD(vpnName);
            rd = Arrays.asList(autoRd);
            LOG.debug("Autogenerated RD {} for vpn {}", autoRd, vpnName);
        }
        if (irt != null && !irt.isEmpty()) {
            if (ert != null && !ert.isEmpty()) {
                List<String> commonRT = new ArrayList<>(irt);
                commonRT.retainAll(ert);

                for (String common : commonRT) {
                    irt.remove(common);
                    ert.remove(common);
                    VpnTarget vpnTarget =
                            new VpnTargetBuilder().withKey(new VpnTargetKey(common)).setVrfRTValue(common)
                                    .setVrfRTType(VpnTarget.VrfRTType.Both).build();
                    vpnTargetList.add(vpnTarget);
                }
            }
            for (String importRT : irt) {
                VpnTarget vpnTarget =
                        new VpnTargetBuilder().withKey(new VpnTargetKey(importRT)).setVrfRTValue(importRT)
                                .setVrfRTType(VpnTarget.VrfRTType.ImportExtcommunity).build();
                vpnTargetList.add(vpnTarget);
            }
        }

        if (ert != null && !ert.isEmpty()) {
            for (String exportRT : ert) {
                VpnTarget vpnTarget =
                        new VpnTargetBuilder().withKey(new VpnTargetKey(exportRT)).setVrfRTValue(exportRT)
                                .setVrfRTType(VpnTarget.VrfRTType.ExportExtcommunity).build();
                vpnTargetList.add(vpnTarget);
            }
        }

        VpnTargets vpnTargets = new VpnTargetsBuilder().setVpnTarget(vpnTargetList).build();

        Ipv4FamilyBuilder ipv4vpnBuilder = new Ipv4FamilyBuilder().setVpnTargets(vpnTargets);
        Ipv6FamilyBuilder ipv6vpnBuilder = new Ipv6FamilyBuilder().setVpnTargets(vpnTargets);

        if (rd != null && !rd.isEmpty()) {
            ipv4vpnBuilder.setRouteDistinguisher(rd);
            ipv6vpnBuilder.setRouteDistinguisher(rd);
        }

        if (ipVersion != null && ipVersion.isIpVersionChosen(IpVersionChoice.IPV4)) {
            builder.setIpv4Family(ipv4vpnBuilder.build());
        }
        if (ipVersion != null && ipVersion.isIpVersionChosen(IpVersionChoice.IPV6)) {
            builder.setIpv6Family(ipv6vpnBuilder.build());
        }
        if (ipVersion != null && ipVersion.isIpVersionChosen(IpVersionChoice.UNDEFINED)) {
            builder.setIpv4Family(ipv4vpnBuilder.build());
        }
        VpnInstance newVpn = builder.build();
        LOG.debug("Creating/Updating vpn-instance for {} ", vpnName);
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        tx.put(vpnIdentifier, newVpn);
    }

    public void deleteVpnInstance(String vpnName,
                                  TypedWriteTransaction<Datastore.Configuration> wrtConfigTxn) {
        LOG.trace("deleteVpnInstance  {}", vpnName);
        InstanceIdentifier<VpnInstance> vpnIfIdentifier = buildVpnInstance(vpnName);
        wrtConfigTxn.delete(vpnIfIdentifier);
    }


    public void createVpnInterface(String vpnName, Pods pod, String interfaceName, String macAddress,
                                          boolean isRouterInterface,
                                          TypedReadWriteTransaction<Datastore.Configuration> wrtConfigTxn) {
        LOG.trace("createVpnInterface for Port: {}, isRouterInterface: {}", interfaceName, isRouterInterface);
        List<VpnInstanceNames> listVpn = new ArrayList<>();
        listVpn.add(new VpnInstanceNamesBuilder().withKey(new VpnInstanceNamesKey(vpnName))
                .setVpnName(vpnName).setAssociatedSubnetType(VpnInstanceNames.AssociatedSubnetType
                        .V4Subnet).build());
        VpnInterfaceBuilder vpnb = new VpnInterfaceBuilder().withKey(new VpnInterfaceKey(interfaceName))
                .setName(interfaceName)
                .setVpnInstanceNames(listVpn)
                .setRouterInterface(isRouterInterface);
        Adjacencies adjs = createPortIpAdjacencies(pod, interfaceName, macAddress);
        if (adjs != null) {
            vpnb.addAugmentation(Adjacencies.class, adjs);
        }
        VpnInterface vpnIf = vpnb.build();
        LOG.info("Creating vpn interface {}", vpnIf);
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = buildVpnInterfaceIdentifier(interfaceName);
        wrtConfigTxn.put(vpnIfIdentifier, vpnIf);

    }

    public void deleteVpnInterface(String interfaceName,
                                          TypedWriteTransaction<Datastore.Configuration> wrtConfigTxn) {
        LOG.trace("deleteVpnInterface for Pod {}", interfaceName);
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = buildVpnInterfaceIdentifier(interfaceName);
        wrtConfigTxn.delete(vpnIfIdentifier);
    }

    Adjacencies createPortIpAdjacencies(Pods pod, String interfaceName, String macAddress) {
        List<Adjacency> adjList = new ArrayList<>();
        LOG.trace("create config adjacencies for Port: {}", interfaceName);
        IpAddress ip = pod.getInterface().get(0).getIpAddress();
        String ipValue = ip.getIpv4Address() != null ? ip.getIpv4Address().getValue() : ip.getIpv6Address().getValue();
        String ipPrefix = ip.getIpv4Address() != null ? ipValue + "/32" : ipValue + "/128";
        String hostIp = pod.getHostIpAddress().stringValue();
        UUID subnetId = UUID.nameUUIDFromBytes(hostIp.getBytes(StandardCharsets.UTF_8));
        String gatewayIP = ipValue.replaceFirst("\\d+$", "1");
        Adjacency vmAdj = new AdjacencyBuilder().withKey(new AdjacencyKey(ipPrefix)).setIpAddress(ipPrefix)
                .setMacAddress(macAddress).setAdjacencyType(Adjacency.AdjacencyType.PrimaryAdjacency)
                .setSubnetId(new Uuid(subnetId.toString())).setSubnetGatewayIp(gatewayIP).build();
        if (!adjList.contains(vmAdj)) {
            adjList.add(vmAdj);
        }
        return new AdjacenciesBuilder().setAdjacency(adjList).build();
    }

    public void unbindKubeProxyService(String interfaceName, TypedWriteTransaction<Datastore.Configuration> tx) {
        tx.delete(buildKubeProxyServicesIId(interfaceName));
    }

    public void updateServiceGatewayList(TypedWriteTransaction<Datastore.Configuration> tx, String serviceGatewayPod,
                                         String serviceGatewayIp,  String serviceGatewayMac) {
        InstanceIdentifier<ServiceGateway> serviceGatewayInstanceIdentifier =
                InstanceIdentifier.builder(ServiceGatewayInfo.class).child(
                        ServiceGateway.class, new ServiceGatewayKey(serviceGatewayPod)).build();
        ServiceGateway serviceGateway = new ServiceGatewayBuilder().setGatewayPodName(serviceGatewayPod)
                .setGatewayPodIpAddress(serviceGatewayIp).setGatewayPodMacAddress(serviceGatewayMac).build();
        tx.put(serviceGatewayInstanceIdentifier, serviceGateway);
    }

    private InstanceIdentifier<ServiceGatewayInfo> buildServiceGatewayInstanceIndentifier() {
        return InstanceIdentifier.builder(ServiceGatewayInfo.class).build();
    }

    public void updateVpnInterfaceWithExtraRouteAdjacency(TypedReadWriteTransaction<Datastore.Configuration> tx,
                                                          Services services) throws ExecutionException,
            InterruptedException {
        if (services.getClusterIpAddress() == null) {
            LOG.error("Incorrect input received for extra route. {}", services.getName());
        } else {
            LOG.info("update vpn-interface with extra route adjacencies for {}", services.getName());
            Optional<ServiceGatewayInfo> serviceGatewayList = tx.read(buildServiceGatewayInstanceIndentifier()).get();
            if (serviceGatewayList.isPresent() && serviceGatewayList.get() != null) {
                for (ServiceGateway serviceGateway : serviceGatewayList.get().nonnullServiceGateway()) {
                    String nextHop = serviceGateway.getGatewayPodIpAddress();
                    IpAddress destination = services.getClusterIpAddress();
                    String destinationIpValue = destination.getIpv4Address() != null
                            ? destination.getIpv4Address().getValue() :
                            destination.getIpv6Address().getValue();
                    String destinationIpPrefix = destination.getIpv4Address() != null ? destinationIpValue + "/32"
                            : destinationIpValue + "/128";
                    String infName = serviceGateway.getGatewayPodName();
                    if (infName != null) {
                        LOG.info("Updating extra route for destination {} onto vpn {} with nexthop {} and infName {}",
                                destination, services.getClusterId(), nextHop, infName);
                        InstanceIdentifier<Adjacency> path = InstanceIdentifier.builder(VpnInterfaces.class)
                                .child(VpnInterface.class, new VpnInterfaceKey(infName)).build()
                                .augmentation(Adjacencies.class).child(Adjacency.class,
                                        new AdjacencyKey(destinationIpPrefix));
                        Adjacency erAdj = new AdjacencyBuilder().setIpAddress(destinationIpPrefix)
                                .setNextHopIpList(Collections.singletonList(nextHop))
                                .setAdjacencyType(Adjacency.AdjacencyType.ExtraRoute).build();
                        tx.put(path, erAdj);

                    } else {
                        LOG.error("Unable to find VPN NextHop interface to apply extra-route destination {} on VPN {} "
                                + "with nexthop {}", destination, services.getClusterId(), nextHop);
                    }
                }
            }
        }
    }
}

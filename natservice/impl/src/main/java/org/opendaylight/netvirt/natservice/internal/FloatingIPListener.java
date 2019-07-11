/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetDestinationIp;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.actions.ActionSetSourceIp;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.netvirt.natservice.api.NatSwitchCache;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FloatingIPListener extends AsyncDataTreeChangeListenerBase<InternalToExternalPortMap, FloatingIPListener> {
    private static final Logger LOG = LoggerFactory.getLogger(FloatingIPListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final OdlInterfaceRpcService interfaceManager;
    private final FloatingIPHandler floatingIPHandler;
    private final SNATDefaultRouteProgrammer defaultRouteProgrammer;
    private final JobCoordinator coordinator;
    private final CentralizedSwitchScheduler centralizedSwitchScheduler;
    private final NatSwitchCache natSwitchCache;

    @Inject
    public FloatingIPListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                              final OdlInterfaceRpcService interfaceManager,
                              final FloatingIPHandler floatingIPHandler,
                              final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
                              final JobCoordinator coordinator,
                              final CentralizedSwitchScheduler centralizedSwitchScheduler,
                              final NatSwitchCache natSwitchCache) {
        super(InternalToExternalPortMap.class, FloatingIPListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.interfaceManager = interfaceManager;
        this.floatingIPHandler = floatingIPHandler;
        this.defaultRouteProgrammer = snatDefaultRouteProgrammer;
        this.coordinator = coordinator;
        this.centralizedSwitchScheduler = centralizedSwitchScheduler;
        this.natSwitchCache = natSwitchCache;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<InternalToExternalPortMap> getWildCardPath() {
        return InstanceIdentifier.create(FloatingIpInfo.class).child(RouterPorts.class).child(Ports.class)
                .child(InternalToExternalPortMap.class);
    }

    @Override
    protected FloatingIPListener getDataTreeChangeListener() {
        return FloatingIPListener.this;
    }

    @Override
    protected void add(final InstanceIdentifier<InternalToExternalPortMap> identifier,
                       final InternalToExternalPortMap mapping) {
        LOG.trace("FloatingIPListener add ip mapping method - key: {} value: {}",mapping.key(), mapping);
        processFloatingIPAdd(identifier, mapping);
    }

    @Override
    protected void remove(InstanceIdentifier<InternalToExternalPortMap> identifier, InternalToExternalPortMap mapping) {
        LOG.trace("FloatingIPListener remove ip mapping method - kkey: {} value: {}",mapping.key(), mapping);
        processFloatingIPDel(identifier, mapping);
    }

    @Override
    protected void update(InstanceIdentifier<InternalToExternalPortMap> identifier, InternalToExternalPortMap
            original, InternalToExternalPortMap update) {
        LOG.trace("FloatingIPListener update ip mapping method - key: {}, original: {}, update: {}",
                update.key(), original, update);
    }

    @Nullable
    private FlowEntity buildPreDNATFlowEntity(Uint64 dpId, InternalToExternalPortMap mapping, Uint32 routerId,
                                              Uint32 associatedVpn) {
        String externalIp = mapping.getExternalIp();
        Uuid floatingIpId = mapping.getExternalId();
        //Get the FIP MAC address for DNAT
        String floatingIpPortMacAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        if (floatingIpPortMacAddress == null) {
            LOG.error("buildPreDNATFlowEntity : Unable to retrieve floatingIpPortMacAddress from floating IP UUID {} "
                    + "for floating IP {}", floatingIpId, externalIp);
            return null;
        }
        LOG.debug("buildPreDNATFlowEntity : Bulding DNAT Flow entity for ip {} ", externalIp);
        Uint32 segmentId = associatedVpn == NatConstants.INVALID_ID ? routerId : associatedVpn;
        LOG.debug("buildPreDNATFlowEntity : Segment id {} in build preDNAT Flow", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        matches.add(new MatchIpv4Destination(externalIp, "32"));
        //Match Destination Floating IP MAC Address on table = 25 (PDNAT_TABLE)
        matches.add(new MatchEthernetDestination(new MacAddress(floatingIpPortMacAddress)));

//        matches.add(new MatchMetadata(
//                BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        String internalIp = mapping.getInternalIp();
        actionsInfos.add(new ActionSetDestinationIp(internalIp, "32"));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(segmentId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(NwConstants.DNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PDNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PDNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }

    private FlowEntity buildDNATFlowEntity(Uint64 dpId, InternalToExternalPortMap mapping, Uint32 routerId, Uint32
            associatedVpn) {
        String externalIp = mapping.getExternalIp();
        LOG.info("buildDNATFlowEntity : Bulding DNAT Flow entity for ip {} ", externalIp);

        Uint32 segmentId = associatedVpn == NatConstants.INVALID_ID ? routerId : associatedVpn;
        LOG.debug("buildDNATFlowEntity : Segment id {} in build DNAT", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(segmentId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        matches.add(MatchEthernetType.IPV4);
        String internalIp = mapping.getInternalIp();
        matches.add(new MatchIpv4Destination(internalIp, "32"));

        List<ActionInfo> actionsInfos = new ArrayList<>();
//        actionsInfos.add(new ActionSetDestinationIp(internalIp, "32"));

        List<InstructionInfo> instructions = new ArrayList<>();
//        instructions.add(new InstructionWriteMetadata(Uint64.valueOf
//                (routerId), MetaDataUtil.METADATA_MASK_VRFID));
        actionsInfos.add(new ActionNxLoadInPort(Uint64.valueOf(BigInteger.ZERO)));
        actionsInfos.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        //instructions.add(new InstructionGotoTable(NatConstants.L3_FIB_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.DNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;

    }

    private FlowEntity buildPreSNATFlowEntity(Uint64 dpId, String internalIp, String externalIp, Uint32 vpnId, Uint32
            routerId, Uint32 associatedVpn) {

        LOG.debug("buildPreSNATFlowEntity : Building PSNAT Flow entity for ip {} ", internalIp);

        Uint32 segmentId = associatedVpn == NatConstants.INVALID_ID ? routerId : associatedVpn;

        LOG.debug("buildPreSNATFlowEntity : Segment id {} in build preSNAT flow", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        matches.add(new MatchIpv4Source(internalIp, "32"));

        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(segmentId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionSetSourceIp(externalIp, "32"));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(
                new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
                        MetaDataUtil.METADATA_MASK_VRFID));
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(NwConstants.SNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PSNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }

    @Nullable
    private FlowEntity buildSNATFlowEntity(Uint64 dpId, InternalToExternalPortMap mapping, Uint32 vpnId, Uuid
            externalNetworkId) {
        String internalIp = mapping.getInternalIp();
        LOG.debug("buildSNATFlowEntity : Building SNAT Flow entity for ip {} ", internalIp);

        ProviderTypes provType = NatUtil.getProviderTypefromNetworkId(dataBroker, externalNetworkId);
        if (provType == null) {
            LOG.error("buildSNATFlowEntity : Unable to get Network Provider Type for network {}", externalNetworkId);
            return null;
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(MatchEthernetType.IPV4);
        String externalIp = mapping.getExternalIp();
        matches.add(new MatchIpv4Source(externalIp, "32"));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        actionsInfo.add(new ActionNxLoadInPort(Uint64.valueOf(BigInteger.ZERO)));
        Uuid floatingIpId = mapping.getExternalId();
        String macAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        if (macAddress != null) {
            actionsInfo.add(new ActionSetFieldEthernetSource(new MacAddress(macAddress)));
        } else {
            LOG.warn("buildSNATFlowEntity : No MAC address found for floating IP {}", externalIp);
        }

        LOG.trace("buildSNATFlowEntity : External Network Provider Type is {}, resubmit to FIB", provType.toString());
        actionsInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.SNAT_TABLE, vpnId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.SNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;

    }

    private void createDNATTblEntry(Uint64 dpnId, InternalToExternalPortMap mapping, Uint32 routerId,
                                    Uint32 associatedVpnId, TypedReadWriteTransaction<Configuration> confTx) {
        FlowEntity preFlowEntity = buildPreDNATFlowEntity(dpnId, mapping, routerId, associatedVpnId);
        if (preFlowEntity == null) {
            LOG.error("createDNATTblEntry : Flow entity received as NULL. "
                    + "Cannot proceed with installation of Pre-DNAT flow table {} --> table {} on DpnId {}",
                    NwConstants.PDNAT_TABLE, NwConstants.DNAT_TABLE, dpnId);
        } else {
            mdsalManager.addFlow(confTx, preFlowEntity);
            FlowEntity flowEntity = buildDNATFlowEntity(dpnId, mapping, routerId, associatedVpnId);
            if (flowEntity != null) {
                mdsalManager.addFlow(confTx, flowEntity);
            }
        }
    }

    private void removeDNATTblEntry(Uint64 dpnId, String internalIp, String externalIp, Uint32 routerId,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {
        FlowEntity preFlowEntity = buildPreDNATDeleteFlowEntity(dpnId, externalIp, routerId);
        mdsalManager.removeFlow(confTx, preFlowEntity);

        FlowEntity flowEntity = buildDNATDeleteFlowEntity(dpnId, internalIp, routerId);
        if (flowEntity != null) {
            mdsalManager.removeFlow(confTx, flowEntity);
        }
    }

    private void createSNATTblEntry(Uint64 dpnId, InternalToExternalPortMap mapping, Uint32 vpnId, Uint32 routerId,
                                    Uint32 associatedVpnId, Uuid externalNetworkId,
                                    TypedReadWriteTransaction<Configuration> confTx) {
        FlowEntity preFlowEntity = buildPreSNATFlowEntity(dpnId, mapping.getInternalIp(), mapping.getExternalIp(),
            vpnId, routerId, associatedVpnId);
        mdsalManager.addFlow(confTx, preFlowEntity);

        FlowEntity flowEntity = buildSNATFlowEntity(dpnId, mapping, vpnId, externalNetworkId);
        if (flowEntity != null) {
            mdsalManager.addFlow(confTx, flowEntity);
        }
    }

    private void removeSNATTblEntry(Uint64 dpnId, String internalIp, String externalIp, Uint32 routerId, Uint32 vpnId,
                                    TypedReadWriteTransaction<Configuration> removeFlowInvTx)
            throws ExecutionException, InterruptedException {
        FlowEntity preFlowEntity = buildPreSNATDeleteFlowEntity(dpnId, internalIp, routerId);
        mdsalManager.removeFlow(removeFlowInvTx, preFlowEntity);

        FlowEntity flowEntity = buildSNATDeleteFlowEntity(dpnId, externalIp, vpnId);
        if (flowEntity != null) {
            mdsalManager.removeFlow(removeFlowInvTx, flowEntity);
        }
    }

    @Nullable
    private Uuid getExtNetworkId(final InstanceIdentifier<RouterPorts> portIid,
                                 LogicalDatastoreType dataStoreType) {
        Optional<RouterPorts> rtrPort =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        dataStoreType, portIid);
        if (!rtrPort.isPresent()) {
            LOG.error("getExtNetworkId : Unable to read router port entry for {}", portIid);
            return null;
        }

        return rtrPort.get().getExternalNetworkId();
    }

    private Uint32 getVpnId(Uuid extNwId, Uuid floatingIpExternalId) {
        Uuid subnetId = NatUtil.getFloatingIpPortSubnetIdFromFloatingIpId(dataBroker, floatingIpExternalId);
        if (subnetId != null) {
            Uint32 vpnId = NatUtil.getVpnId(dataBroker, subnetId.getValue());
            if (vpnId != NatConstants.INVALID_ID) {
                LOG.debug("getVpnId : Got vpnId {} for floatingIpExternalId {}", vpnId, floatingIpExternalId);
                return vpnId;
            }
        }

        InstanceIdentifier<Networks> nwId = InstanceIdentifier.builder(ExternalNetworks.class).child(Networks.class,
                new NetworksKey(extNwId)).build();
        Optional<Networks> nw =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, nwId);
        if (!nw.isPresent()) {
            LOG.error("getVpnId : Unable to read external network for {}", extNwId);
            return NatConstants.INVALID_ID;
        }

        Uuid vpnUuid = nw.get().getVpnid();
        if (vpnUuid == null) {
            LOG.error("getVpnId : Unable to read vpn from External network: {}", extNwId);
            return NatConstants.INVALID_ID;
        }

        //Get the id using the VPN UUID (also vpn instance name)
        return NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
    }

    private void processFloatingIPAdd(final InstanceIdentifier<InternalToExternalPortMap> identifier,
                                      final InternalToExternalPortMap mapping) {
        LOG.trace("processFloatingIPAdd key: {}, value: {}", mapping.key(), mapping);

        final String routerId = identifier.firstKeyOf(RouterPorts.class).getRouterId();
        final PortsKey pKey = identifier.firstKeyOf(Ports.class);
        String interfaceName = pKey.getPortName();

        InstanceIdentifier<RouterPorts> portIid = identifier.firstIdentifierOf(RouterPorts.class);
        coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + mapping.key(), () -> Collections.singletonList(
                txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                    tx -> createNATFlowEntries(interfaceName, mapping, portIid, routerId, null, tx))),
                NatConstants.NAT_DJC_MAX_RETRIES);
    }

    private void processFloatingIPDel(final InstanceIdentifier<InternalToExternalPortMap> identifier,
                                      final InternalToExternalPortMap mapping) {
        LOG.trace("processFloatingIPDel : key: {}, value: {}", mapping.key(), mapping);

        final String routerId = identifier.firstKeyOf(RouterPorts.class).getRouterId();
        final PortsKey pKey = identifier.firstKeyOf(Ports.class);
        String interfaceName = pKey.getPortName();

        InstanceIdentifier<RouterPorts> portIid = identifier.firstIdentifierOf(RouterPorts.class);
        coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + mapping.key(), () -> Collections.singletonList(
                txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                    tx -> removeNATFlowEntries(interfaceName, mapping, portIid, routerId, null, tx))),
                NatConstants.NAT_DJC_MAX_RETRIES);
    }

    private InetAddress getInetAddress(String ipAddr) {
        InetAddress ipAddress = null;
        try {
            ipAddress = InetAddress.getByName(ipAddr);
        } catch (UnknownHostException e) {
            LOG.error("getInetAddress : UnknowHostException for ip {}", ipAddr, e);
        }
        return ipAddress;
    }

    private boolean validateIpMapping(InternalToExternalPortMap mapping) {
        return getInetAddress(mapping.getInternalIp()) != null && getInetAddress(mapping.getExternalIp()) != null;
    }

    private Uint64 getAssociatedDpnWithExternalInterface(final String routerName, Uuid extNwId, Uint64 dpnId,
            String interfaceName) {
        //Get the DPN on which this interface resides
        if (dpnId == null) {
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                .state.Interface interfaceState = NatUtil.getInterfaceStateFromOperDS(dataBroker, interfaceName);
            if (interfaceState != null) {
                dpnId = NatUtil.getDpIdFromInterface(interfaceState);
            }
        }
        Uint64 updatedDpnId = dpnId;
        if (updatedDpnId != null && updatedDpnId.equals(Uint64.ZERO)) {
            LOG.debug("getAssociatedDpnWithExternalInterface : The interface {} is not associated with any dpn",
                    interfaceName);
            return updatedDpnId;
        }
        ProviderTypes providerType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName, extNwId);
        if (providerType == null) {
            LOG.warn("getAssociatedDpnWithExternalInterface : Provider Network Type for router {} and"
                    + " externalNetwork {} is missing.", routerName, extNwId);
            return updatedDpnId;
        }

        // For FLAT and VLAN provider networks, we have to ensure that dpn hosting the VM has connectivity
        // to External Network via provider_mappings. In case the dpn does not have the provider mappings,
        // traffic from the VM has to be forwarded to the NAPT Switch (which is scheduled based on the provider
        // mappings) and then sent out on the external Network.
        if (providerType == ProviderTypes.FLAT || providerType == ProviderTypes.VLAN) {
            String providerNet = NatUtil.getElanInstancePhysicalNetwok(extNwId.getValue(), dataBroker);
            boolean isDpnConnected = natSwitchCache.isSwitchConnectedToExternal(updatedDpnId, providerNet);
            if (!isDpnConnected) {
                updatedDpnId = centralizedSwitchScheduler.getCentralizedSwitch(routerName);
            }
        }
        return updatedDpnId;
    }

    void createNATFlowEntries(String interfaceName, final InternalToExternalPortMap mapping,
            final InstanceIdentifier<RouterPorts> portIid, final String routerName, @Nullable Uint64 dpnId,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {
        if (!validateIpMapping(mapping)) {
            LOG.error("createNATFlowEntries : Not a valid ip addresses in the mapping {}", mapping);
            return;
        }

        Uuid extNwId = getExtNetworkId(portIid, LogicalDatastoreType.CONFIGURATION);
        if (extNwId == null) {
            LOG.error("createNATFlowEntries : External network associated with interface {} could not be retrieved",
                    interfaceName);
            return;
        }

        // For Overlay Networks, get the DPN on which this interface resides.
        // For FLAT/VLAN Networks, get the DPN with provider_mappings for external network.
        dpnId = getAssociatedDpnWithExternalInterface(routerName, extNwId, dpnId, interfaceName);
        if (dpnId == null || dpnId.equals(Uint64.ZERO)) {
            LOG.warn("createNATFlowEntries : No DPN for interface {}. NAT flow entries for ip mapping {} will "
                    + "not be installed", interfaceName, mapping);
            return;
        }

        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("createNATFlowEntries : Could not retrieve router id for {} to create NAT Flow entries",
                    routerName);
            return;
        }
        //Check if the router to vpn association is present
        //long associatedVpnId = NatUtil.getAssociatedVpn(dataBroker, routerName);
        Uuid associatedVpn = NatUtil.getVpnForRouter(dataBroker, routerName);
        Uint32 associatedVpnId = NatConstants.INVALID_ID;
        if (associatedVpn == null) {
            LOG.debug("createNATFlowEntries : Router {} is not assicated with any BGP VPN instance", routerName);
        } else {
            LOG.debug("createNATFlowEntries : Router {} is associated with VPN Instance with Id {}",
                    routerName, associatedVpn);
            associatedVpnId = NatUtil.getVpnId(dataBroker, associatedVpn.getValue());
            LOG.debug("createNATFlowEntries : vpninstance Id is {} for VPN {}", associatedVpnId, associatedVpn);
            //routerId = associatedVpnId;
        }

        String vpnUuid = NatUtil.getAssociatedVPN(dataBroker, extNwId);
        VpnInstance vpnInstance = NatUtil.getVpnIdToVpnInstance(dataBroker, vpnUuid);
        if (vpnInstance == null || vpnInstance.getVpnId() == null) {
            LOG.error("createNATFlowEntries : No VPN associated with Ext nw {}. Unable to create SNAT table entry "
                    + "for fixed ip {}", extNwId, mapping.getInternalIp());
            return;
        }
        //Install the DNAT default FIB flow L3_FIB_TABLE (21) -> PSNAT_TABLE (26) if SNAT is disabled
        boolean isSnatEnabled = NatUtil.isSnatEnabledForRouterId(dataBroker, routerName);
        if (!isSnatEnabled) {
            addOrDelDefaultFibRouteForDnat(dpnId, routerName, routerId, confTx, true);
        }
        //Create the DNAT and SNAT table entries
        Uint32 vpnId = vpnInstance.getVpnId();
        String vrfId = vpnInstance.getVrfId();
        createDNATTblEntry(dpnId, mapping, routerId, associatedVpnId, confTx);
        createSNATTblEntry(dpnId, mapping, vpnId, routerId, associatedVpnId, extNwId, confTx);
        floatingIPHandler.onAddFloatingIp(dpnId, routerName, routerId, extNwId, interfaceName, mapping,
            vrfId, confTx);
    }

    void createNATFlowEntries(Uint64 dpnId,  String interfaceName, String routerName, Uuid externalNetworkId,
                              InternalToExternalPortMap mapping, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("createNATFlowEntries : Could not retrieve router id for {} to create NAT Flow entries",
                    routerName);
            return;
        }
        //Check if the router to vpn association is present
        Uint32 associatedVpnId = NatUtil.getAssociatedVpn(dataBroker, routerName);
        if (associatedVpnId == NatConstants.INVALID_ID) {
            LOG.debug("createNATFlowEntries : Router {} is not assicated with any BGP VPN instance", routerName);
        } else {
            LOG.debug("createNATFlowEntries : Router {} is associated with VPN Instance with Id {}",
                routerName, associatedVpnId);
            //routerId = associatedVpnId;
        }

        String vpnUuid = NatUtil.getAssociatedVPN(dataBroker, externalNetworkId);
        VpnInstance vpnInstance = NatUtil.getVpnIdToVpnInstance(dataBroker, vpnUuid);
        if (vpnInstance == null || vpnInstance.getVpnId() == null) {
            LOG.error("createNATFlowEntries: No VPN associated with Ext nw {}. Unable to create SNAT table entry"
                    + " for fixed ip {}",externalNetworkId, mapping.getInternalIp());
            return;
        }
        //Install the DNAT default FIB flow L3_FIB_TABLE (21) -> PSNAT_TABLE (26) if SNAT is disabled
        boolean isSnatEnabled = NatUtil.isSnatEnabledForRouterId(dataBroker, routerName);
        if (!isSnatEnabled) {
            addOrDelDefaultFibRouteForDnat(dpnId, routerName, routerId, confTx, true);
        }
        //Create the DNAT and SNAT table entries
        Uint32 vpnId = vpnInstance.getVpnId();
        String vrfId = vpnInstance.getVrfId();
        createDNATTblEntry(dpnId, mapping, routerId, associatedVpnId, confTx);
        createSNATTblEntry(dpnId, mapping, vpnId, routerId, associatedVpnId, externalNetworkId, confTx);
        floatingIPHandler.onAddFloatingIp(dpnId, routerName, routerId, externalNetworkId, interfaceName, mapping,
            vrfId, confTx);
    }

    void createNATOnlyFlowEntries(Uint64 dpnId, String routerName, @Nullable String associatedVPN,
                                  Uuid externalNetworkId, InternalToExternalPortMap mapping)
            throws ExecutionException, InterruptedException {
        //String segmentId = associatedVPN == null ? routerName : associatedVPN;
        LOG.debug("createNATOnlyFlowEntries : Retrieving vpn id for VPN {} to proceed with create NAT Flows",
                routerName);
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("createNATOnlyFlowEntries : Could not retrieve vpn id for {} to create NAT Flow entries",
                    routerName);
            return;
        }
        Uint32 associatedVpnId = NatUtil.getVpnId(dataBroker, associatedVPN);
        LOG.debug("createNATOnlyFlowEntries : Associated VPN Id {} for router {}", associatedVpnId, routerName);
        Uint32 vpnId = getVpnId(externalNetworkId, mapping.getExternalId());
        if (vpnId.longValue() < 0) {
            LOG.error("createNATOnlyFlowEntries : Unable to create SNAT table entry for fixed ip {}",
                    mapping.getInternalIp());
            return;
        }
        //Install the DNAT default FIB flow L3_FIB_TABLE (21) -> PSNAT_TABLE (26) if SNAT is disabled
        boolean isSnatEnabled = NatUtil.isSnatEnabledForRouterId(dataBroker, routerName);
        if (!isSnatEnabled) {
            addOrDelDefaultFibRouteForDnat(dpnId, routerName, routerId, null, true);
        }
        //Create the DNAT and SNAT table entries
        FlowEntity preFlowEntity = buildPreDNATFlowEntity(dpnId, mapping, routerId, associatedVpnId);
        mdsalManager.installFlow(preFlowEntity);

        FlowEntity flowEntity = buildDNATFlowEntity(dpnId, mapping, routerId, associatedVpnId);
        mdsalManager.installFlow(flowEntity);

        String externalIp = mapping.getExternalIp();
        preFlowEntity = buildPreSNATFlowEntity(dpnId, mapping.getInternalIp(), externalIp, vpnId,
                routerId, associatedVpnId);
        mdsalManager.installFlow(preFlowEntity);

        flowEntity = buildSNATFlowEntity(dpnId, mapping, vpnId, externalNetworkId);
        if (flowEntity != null) {
            mdsalManager.installFlow(flowEntity);
        }

    }

    void removeNATFlowEntries(String interfaceName, final InternalToExternalPortMap mapping,
            InstanceIdentifier<RouterPorts> portIid, final String routerName, @Nullable Uint64 dpnId,
            TypedReadWriteTransaction<Configuration> removeFlowInvTx) throws ExecutionException, InterruptedException {
        Uuid extNwId = getExtNetworkId(portIid, LogicalDatastoreType.OPERATIONAL);
        if (extNwId == null) {
            LOG.error("removeNATFlowEntries : External network associated with interface {} could not be retrieved",
                    interfaceName);
            return;
        }

        // For Overlay Networks, get the DPN on which this interface resides.
        // For FLAT/VLAN Networks, get the DPN with provider_mappings for external network.
        if (dpnId == null) {
            dpnId = getAssociatedDpnWithExternalInterface(routerName, extNwId,
                    NatUtil.getDpnForInterface(interfaceManager, interfaceName), interfaceName);
            if (dpnId == null || dpnId.equals(Uint64.ZERO)) {
                LOG.warn("removeNATFlowEntries: Abort processing Floating ip configuration. No DPN for port: {}",
                        interfaceName);
                return;
            }
        }

        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("removeNATFlowEntries : Could not retrieve router id for {} to remove NAT Flow entries",
                    routerName);
            return;
        }

        String internalIp = mapping.getInternalIp();
        String externalIp = mapping.getExternalIp();

        //Delete the DNAT and SNAT table entries
        removeDNATTblEntry(dpnId, internalIp, externalIp, routerId, removeFlowInvTx);

        String vpnUuid = NatUtil.getAssociatedVPN(dataBroker, extNwId);
        VpnInstance vpnInstance = NatUtil.getVpnIdToVpnInstance(dataBroker, vpnUuid);
        if (vpnInstance == null || vpnInstance.getVpnId() == null) {
            LOG.error("removeNATFlowEntries: No VPN associated with Ext nw {}. Unable to create SNAT table entry "
                + "for fixed ip {}", extNwId, mapping.getInternalIp());
            return;
        }
        Uint32 vpnId = vpnInstance.getVpnId();
        String vrfId = vpnInstance.getVrfId();
        removeSNATTblEntry(dpnId, internalIp, externalIp, routerId, vpnId, removeFlowInvTx);
        //Remove the DNAT default FIB flow L3_FIB_TABLE (21) -> PSNAT_TABLE (26) if SNAT is disabled
        boolean isSnatEnabled = NatUtil.isSnatEnabledForRouterId(dataBroker, routerName);
        if (!isSnatEnabled) {
            addOrDelDefaultFibRouteForDnat(dpnId, routerName, routerId, removeFlowInvTx, false);
        }
        ProviderTypes provType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName, extNwId);
        if (provType == null) {
            LOG.error("removeNATFlowEntries : External Network Provider Type missing");
            return;
        }
        if (provType == ProviderTypes.VXLAN) {
            floatingIPHandler.onRemoveFloatingIp(dpnId, routerName, routerId, extNwId, mapping,
                    NatConstants.DEFAULT_L3VNI_VALUE, vrfId, removeFlowInvTx);
            removeOperationalDS(routerName, interfaceName, internalIp);
            return;
        }
        Uint32 label = getOperationalIpMapping(routerName, interfaceName, internalIp);
        if (label.longValue() < 0) {
            LOG.error("removeNATFlowEntries : Could not retrieve label for prefix {} in router {}",
                    internalIp, routerId);
            return;
        }
        floatingIPHandler.onRemoveFloatingIp(dpnId, routerName, routerId, extNwId, mapping, label, vrfId,
                removeFlowInvTx);
        removeOperationalDS(routerName, interfaceName, internalIp);
    }

    void removeNATFlowEntries(Uint64 dpnId, String interfaceName, String vpnName, String routerName,
                              InternalToExternalPortMap mapping, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        String internalIp = mapping.getInternalIp();
        String externalIp = mapping.getExternalIp();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("removeNATFlowEntries : Could not retrieve router id for {} to remove NAT Flow entries",
                    routerName);
            return;
        }

        VpnInstance vpnInstance = NatUtil.getVpnIdToVpnInstance(dataBroker, vpnName);
        if (vpnInstance == null || vpnInstance.getVpnId() == null) {
            LOG.warn("removeNATFlowEntries: VPN Id not found for {} to remove NAT flow entries {}",
                vpnName, internalIp);
            return;
        }
        Uint32 vpnId = vpnInstance.getVpnId();
        String vrfId = vpnInstance.getVrfId();

        //Delete the DNAT and SNAT table entries
        removeDNATTblEntry(dpnId, internalIp, externalIp, routerId, confTx);
        removeSNATTblEntry(dpnId, internalIp, externalIp, routerId, vpnId, confTx);
        //Remove the DNAT default FIB flow L3_FIB_TABLE (21) -> PSNAT_TABLE (26) if SNAT is disabled
        boolean isSnatEnabled = NatUtil.isSnatEnabledForRouterId(dataBroker, routerName);
        if (!isSnatEnabled) {
            addOrDelDefaultFibRouteForDnat(dpnId, routerName, routerId, confTx, false);
        }
        Uuid externalNetworkId = NatUtil.getNetworkIdFromRouterName(dataBroker,routerName);
        ProviderTypes provType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName, externalNetworkId);
        if (provType == null) {
            LOG.error("removeNATFlowEntries : External Network Provider Type Missing");
            return;
        }
        if (provType == ProviderTypes.VXLAN) {
            floatingIPHandler.cleanupFibEntries(dpnId, vpnName, externalIp, NatConstants.DEFAULT_L3VNI_VALUE, vrfId,
                    confTx, provType);
            removeOperationalDS(routerName, interfaceName, internalIp);
            return;
        }
        Uint32 label = getOperationalIpMapping(routerName, interfaceName, internalIp);
        if (label != null && label.longValue() < 0) {
            LOG.error("removeNATFlowEntries : Could not retrieve label for prefix {} in router {}",
                    internalIp, routerId);
            return;
        }
        if (provType == ProviderTypes.VXLAN) {
            floatingIPHandler.cleanupFibEntries(dpnId, vpnName, externalIp, NatConstants.DEFAULT_L3VNI_VALUE, vrfId,
                confTx, provType);
            removeOperationalDS(routerName, interfaceName, internalIp);
            return;
        }
        floatingIPHandler.cleanupFibEntries(dpnId, vpnName, externalIp, label, vrfId, confTx, provType);
        removeOperationalDS(routerName, interfaceName, internalIp);
    }

    protected Uint32 getOperationalIpMapping(String routerId, String interfaceName, String internalIp) {
        InstanceIdentifier<InternalToExternalPortMap> intExtPortMapIdentifier =
            NatUtil.getIntExtPortMapIdentifier(routerId, interfaceName, internalIp);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, intExtPortMapIdentifier).toJavaUtil().map(
                InternalToExternalPortMap::getLabel).orElse(NatConstants.INVALID_ID);
    }

    static void updateOperationalDS(DataBroker dataBroker, String routerId, String interfaceName, Uint32 label,
                                    String internalIp, String externalIp) {

        LOG.info("updateOperationalDS : Updating operational DS for floating ip config : {} with label {}",
                internalIp, label);
        InstanceIdentifier<Ports> portsId = NatUtil.getPortsIdentifier(routerId, interfaceName);
        Optional<Ports> optPorts =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, portsId);
        InternalToExternalPortMap intExtPortMap = new InternalToExternalPortMapBuilder().withKey(new
                InternalToExternalPortMapKey(internalIp)).setInternalIp(internalIp).setExternalIp(externalIp)
                .setLabel(label).build();
        if (optPorts.isPresent()) {
            LOG.debug("updateOperationalDS : Ports {} entry already present. Updating intExtPortMap for internal ip {}",
                    interfaceName, internalIp);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, portsId.child(InternalToExternalPortMap
                    .class, new InternalToExternalPortMapKey(internalIp)), intExtPortMap);
        } else {
            LOG.debug("updateOperationalDS : Adding Ports entry {} along with intExtPortMap {}",
                    interfaceName, internalIp);
            List<InternalToExternalPortMap> intExtPortMapList = new ArrayList<>();
            intExtPortMapList.add(intExtPortMap);
            Ports ports = new PortsBuilder().withKey(new PortsKey(interfaceName)).setPortName(interfaceName)
                    .setInternalToExternalPortMap(intExtPortMapList).build();
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, portsId, ports);
        }
    }

    void removeOperationalDS(String routerId, String interfaceName, String internalIp) {
        LOG.info("removeOperationalDS : Remove operational DS for floating ip config: {}", internalIp);
        InstanceIdentifier<InternalToExternalPortMap> intExtPortMapId = NatUtil.getIntExtPortMapIdentifier(routerId,
                interfaceName, internalIp);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, intExtPortMapId);
    }

    private FlowEntity buildPreDNATDeleteFlowEntity(Uint64 dpId, String externalIp, Uint32 routerId) {

        LOG.info("buildPreDNATDeleteFlowEntity : Bulding Delete DNAT Flow entity for ip {} ", externalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PDNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PDNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;
    }



    private FlowEntity buildDNATDeleteFlowEntity(Uint64 dpId, String internalIp, Uint32 routerId) {

        LOG.info("buildDNATDeleteFlowEntity : Bulding Delete DNAT Flow entity for ip {} ", internalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.DNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;

    }

    private FlowEntity buildPreSNATDeleteFlowEntity(Uint64 dpId, String internalIp, Uint32 routerId) {

        LOG.info("buildPreSNATDeleteFlowEntity : Building Delete PSNAT Flow entity for ip {} ", internalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PSNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);
        return flowEntity;
    }

    private FlowEntity buildSNATDeleteFlowEntity(Uint64 dpId, String externalIp, Uint32 routerId) {

        LOG.info("buildSNATDeleteFlowEntity : Building Delete SNAT Flow entity for ip {} ", externalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.SNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.SNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;
    }

    private void addOrDelDefaultFibRouteForDnat(Uint64 dpnId, String routerName, Uint32 routerId,
            @Nullable TypedReadWriteTransaction<Configuration> confTx, boolean create)
            throws ExecutionException, InterruptedException {
        if (confTx == null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                newTx -> addOrDelDefaultFibRouteForDnat(dpnId, routerName, routerId, newTx, create)), LOG,
                "Error handling default FIB route for DNAT");
            return;
        }
        //Check if the router to bgp-vpn association is present
        Uint32 associatedVpnId = NatConstants.INVALID_ID;
        Uuid associatedVpn = NatUtil.getVpnForRouter(dataBroker, routerName);
        if (associatedVpn != null) {
            associatedVpnId = NatUtil.getVpnId(dataBroker, associatedVpn.getValue());
        }
        if (create) {
            if (associatedVpnId != NatConstants.INVALID_ID) {
                LOG.debug("addOrDelDefaultFibRouteForDnat: Install NAT default route on DPN {} for the router {} with "
                        + "vpn-id {}", dpnId, routerName, associatedVpnId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, associatedVpnId, routerId, confTx);
            } else {
                LOG.debug("addOrDelDefaultFibRouteForDnat: Install NAT default route on DPN {} for the router {} with "
                        + "vpn-id {}", dpnId, routerName, routerId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, routerId, confTx);
            }
        } else {
            if (associatedVpnId != NatConstants.INVALID_ID) {
                LOG.debug("addOrDelDefaultFibRouteForDnat: Remove NAT default route on DPN {} for the router {} "
                        + "with vpn-id {}", dpnId, routerName, associatedVpnId);
                defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, associatedVpnId, routerId, confTx);
            } else {
                LOG.debug("addOrDelDefaultFibRouteForDnat: Remove NAT default route on DPN {} for the router {} "
                        + "with vpn-id {}", dpnId, routerName, routerId);
                defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, routerId, confTx);
            }
        }
    }
}

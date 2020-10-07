/*
 * Copyright © 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.ipv6util.api.Icmpv6Type;
import org.opendaylight.genius.ipv6util.api.Ipv6Constants;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NwConstants.NxmOfFieldType;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn.FlowMod;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationEth;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationIpv6;
import org.opendaylight.genius.mdsalutil.actions.ActionNdOptionType;
import org.opendaylight.genius.mdsalutil.actions.ActionNdReserved;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.actions.ActionSetIcmpv6Type;
import org.opendaylight.genius.mdsalutil.actions.ActionSetIpv6NdTarget;
import org.opendaylight.genius.mdsalutil.actions.ActionSetIpv6NdTll;
import org.opendaylight.genius.mdsalutil.actions.ActionSetSourceIpv6;
import org.opendaylight.genius.mdsalutil.ericmatches.MatchNdOptionType;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6NdTarget;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.ipv6service.VirtualSubnet;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.config.rev181010.Ipv6serviceConfig;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6ServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceUtils.class);
    public static final Ipv6Address ALL_NODES_MCAST_ADDR = newIpv6Address(Ipv6Constants.ALL_NODES_MCAST_ADDRESS);
    public static final Ipv6Address UNSPECIFIED_ADDR = newIpv6Address("0:0:0:0:0:0:0:0");

    @Nullable
    private static Ipv6Address newIpv6Address(String ip) {
        try {
            return Ipv6Address.getDefaultInstance(InetAddress.getByName(ip).getHostAddress());
        } catch (UnknownHostException e) {
            LOG.error("Ipv6ServiceUtils: Error instantiating ipv6 address", e);
            return null;
        }
    }

    private final DataBroker broker;
    private final IMdsalApiManager mdsalUtil;
    private final IpV6NAConfigHelper ipV6NAConfigHelper;
    private final ManagedNewTransactionRunner txRunner;
    private final Ipv6serviceConfig ipv6serviceConfig;

    @Inject
    public Ipv6ServiceUtils(DataBroker broker, IMdsalApiManager mdsalUtil,IpV6NAConfigHelper ipV6NAConfigHelper,
                            Ipv6serviceConfig ipv6ServiceConfig) {
        this.broker = broker;
        this.mdsalUtil = mdsalUtil;
        this.ipV6NAConfigHelper = ipV6NAConfigHelper;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.ipv6serviceConfig = ipv6ServiceConfig;
    }

    /**
     * Retrieves the object from the datastore.
     * @param datastoreType the data store type.
     * @param path the wild card path.
     * @return the required object.
     */
    public <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try (ReadTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the Interface from the datastore.
     * @param interfaceName the interface name
     * @return the interface.
     */
    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .@Nullable Interface getInterface(String interfaceName) {
        return read(LogicalDatastoreType.CONFIGURATION, getInterfaceIdentifier(interfaceName)).orElse(null);
    }

    /**
     * Builds the interface identifier.
     * @param interfaceName the interface name.
     * @return the interface identifier.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.Interface> getInterfaceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class)
                .child(
                        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                                .Interface.class, new InterfaceKey(interfaceName)).build();
    }

    /**
     * Build the interface state.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                        .state.Interface.class,
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
                        .rev140508.interfaces.state.InterfaceKey(interfaceName));
        return idBuilder.build();
    }

    /**
     * Retrieves the interface state.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .@Nullable Interface getInterfaceStateFromOperDS(String interfaceName) {
        return MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, buildStateInterfaceId(interfaceName), broker)
                .orElse(null);
    }

    private static List<MatchInfo> getIcmpv6RSMatch(Long elanTag) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Icmpv6Type.ROUTER_SOLICITATION.getValue(), (short) 0));
        matches.add(new MatchMetadata(MetaDataUtil.getElanTagMetadata(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        return matches;
    }

    private List<MatchInfo> getIcmpv6NSMatch(Long elanTag, Ipv6Address ipv6Address) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Icmpv6Type.NEIGHBOR_SOLICITATION.getValue(), (short) 0));
        matches.add(new MatchIpv6NdTarget(ipv6Address));
        matches.add(new MatchMetadata(MetaDataUtil.getElanTagMetadata(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        return matches;
    }

    private List<MatchInfo> getIcmpv6NAMatch(Long elanTag) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Icmpv6Type.NEIGHBOR_ADVERTISEMENT.getValue(), (short) 0));
        matches.add(new MatchMetadata(MetaDataUtil.getElanTagMetadata(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        return matches;
    }

    private static String getIPv6FlowRef(Uint64 dpId, Long elanTag, String flowType) {
        return new StringBuilder().append(Ipv6ServiceConstants.FLOWID_PREFIX)
                .append(dpId).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(elanTag).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(flowType).toString();
    }

    public void installIcmpv6NsPuntFlow(short tableId, Uint64 dpId, Long elanTag, Ipv6Address ipv6Address,
            int addOrRemove) {
        String flowId = getIPv6FlowRef(dpId, elanTag, Ipv6Util.getFormattedIpv6Address(ipv6Address));

        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            LOG.trace("Removing IPv6 Neighbor Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            LoggingFutures
                    .addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                        mdsalUtil.removeFlow(tx, dpId, flowId, tableId);
                    }), LOG, "Error while removing flow={}", flowId);
        } else {
            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionPuntToController());

            int ndPuntTimeout = ipv6serviceConfig.getNeighborDiscoveryPuntTimeout().toJava();
            if (isNdPuntProtectionEnabled(ndPuntTimeout)) {
                actionsInfos.add(getLearnActionForNsPuntProtection(ndPuntTimeout));
            }
            List<InstructionInfo> instructions = Arrays.asList(new InstructionApplyActions(actionsInfos));
            List<MatchInfo> nsMatch = getIcmpv6NSMatch(elanTag, ipv6Address);
            FlowEntity nsFlowEntity =
                    MDSALUtil.buildFlowEntity(dpId, tableId, flowId, Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY,
                            "IPv6NS", 0, 0, NwConstants.COOKIE_IPV6_TABLE, nsMatch, instructions);

            LOG.trace("Installing IPv6 Neighbor Solicitation Flow DpId={}, elanTag={} ipv6Address={}", dpId, elanTag,
                    ipv6Address.getValue());
            LoggingFutures
                    .addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                        mdsalUtil.addFlow(tx, nsFlowEntity);
                    }), LOG, "Error while adding flow={}", nsFlowEntity);
        }
    }

    private static String getIPv6OvsFlowRef(short tableId, Uint64 dpId, int lportTag, String ndTargetAddr) {
        return new StringBuilder().append(Ipv6ServiceConstants.FLOWID_PREFIX)
                .append(dpId).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(tableId).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(lportTag).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(ndTargetAddr).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(Ipv6ServiceConstants.FLOWID_NS_RESPONDER_SUFFIX).toString();
    }

    private static String getIPv6OvsFlowRef(short tableId, Uint64 dpId, int lportTag, String ndTargetAddr,
                                            String vmMacAddress) {
        return new StringBuilder().append(Ipv6ServiceConstants.FLOWID_PREFIX)
                .append(dpId).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(tableId).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(lportTag).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(vmMacAddress).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(ndTargetAddr).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(Ipv6ServiceConstants.FLOWID_NS_RESPONDER_SUFFIX).toString();
    }

    private ActionLearn getLearnActionForNsPuntProtection(int ndPuntTimeout) {
        List<FlowMod> flowMods = getFlowModsForIpv6PuntProtection(Icmpv6Type.NEIGHBOR_SOLICITATION);
        flowMods.add(new ActionLearn.MatchFromField(NxmOfFieldType.NXM_NX_ND_TARGET.getType(),
                NxmOfFieldType.NXM_NX_ND_TARGET.getType(), NxmOfFieldType.NXM_NX_ND_TARGET.getFlowModHeaderLenInt()));

        return new ActionLearn(0, ndPuntTimeout, Ipv6ServiceConstants.NS_PUNT_PROTECTION_FLOW_PRIORITY,
                NwConstants.COOKIE_IPV6_TABLE, 0, NwConstants.IPV6_TABLE, 0, 0, flowMods);
    }

    public void installIcmpv6RsPuntFlow(short tableId, Uint64 dpId, Long elanTag, int addOrRemove) {
        if (dpId == null || dpId.equals(Ipv6ServiceConstants.INVALID_DPID)) {
            return;
        }
        String flowId = getIPv6FlowRef(dpId, elanTag, "IPv6RS");
        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            LOG.trace("Removing IPv6 Router Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            LoggingFutures
                    .addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                        mdsalUtil.removeFlow(tx, dpId, flowId, tableId);
                    }), LOG, "Error while removing flow={}", flowId);
        } else {
            List<ActionInfo> actionsInfos = new ArrayList<>();
            // Punt to controller
            actionsInfos.add(new ActionPuntToController());

            int rdPuntTimeout = ipv6serviceConfig.getRouterDiscoveryPuntTimeout().toJava();
            if (isRdPuntProtectionEnabled(rdPuntTimeout)) {
                actionsInfos.add(getLearnActionForRsPuntProtection(rdPuntTimeout));
            }
            List<InstructionInfo> instructions = Arrays.asList(new InstructionApplyActions(actionsInfos));
            List<MatchInfo> routerSolicitationMatch = getIcmpv6RSMatch(elanTag);
            FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    flowId,Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY, "IPv6RS", 0, 0,
                    NwConstants.COOKIE_IPV6_TABLE, routerSolicitationMatch, instructions);

            LOG.trace("Installing IPv6 Router Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            LoggingFutures
                    .addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                        mdsalUtil.addFlow(tx, rsFlowEntity);
                    }), LOG, "Error while adding flow={}", rsFlowEntity);
        }
    }

    private ActionLearn getLearnActionForRsPuntProtection(int rdPuntTimeout) {
        return new ActionLearn(0, rdPuntTimeout, Ipv6ServiceConstants.RS_PUNT_PROTECTION_FLOW_PRIORITY,
                NwConstants.COOKIE_IPV6_TABLE, 0, NwConstants.IPV6_TABLE, 0, 0,
                getFlowModsForIpv6PuntProtection(Icmpv6Type.ROUTER_SOLICITATION));
    }

    private List<FlowMod> getFlowModsForIpv6PuntProtection(Icmpv6Type icmpv6Type) {
        return new ArrayList<>(Arrays.asList(
                new ActionLearn.MatchFromValue(NwConstants.ETHTYPE_IPV6, NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
                        NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromValue(IPProtocols.IPV6ICMP.shortValue(),
                        NxmOfFieldType.NXM_OF_IP_PROTO.getType(),
                        NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromValue(icmpv6Type.getValue(), NxmOfFieldType.NXM_OF_ICMPv6_TYPE.getType(),
                        NxmOfFieldType.NXM_OF_ICMPv6_TYPE.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromField(NxmOfFieldType.NXM_OF_ICMPv6_CODE.getType(),
                        NxmOfFieldType.NXM_OF_ICMPv6_CODE.getType(),
                        NxmOfFieldType.NXM_OF_ICMPv6_CODE.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromField(NxmOfFieldType.OXM_OF_METADATA.getType(),
                        MetaDataUtil.METADATA_LPORT_TAG_OFFSET, NxmOfFieldType.OXM_OF_METADATA.getType(),
                        MetaDataUtil.METADATA_LPORT_TAG_OFFSET, MetaDataUtil.METADATA_LPORT_TAG_BITLEN)));
    }

    private boolean isRdPuntProtectionEnabled(int rdPuntTimeout) {
        return rdPuntTimeout != 0;
    }

    private boolean isNdPuntProtectionEnabled(int ndPuntTimeout) {
        return ndPuntTimeout != 0;
    }

    public void installIcmpv6NaForwardFlow(short tableId, IVirtualPort vmPort, Uint64 dpId, Long elanTag,
            int addOrRemove) {
        List<MatchInfo> matches = getIcmpv6NAMatch(elanTag);
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));

        for (Ipv6Address ipv6Address : vmPort.getIpv6Addresses()) {
            matches.add(new MatchIpv6Source(ipv6Address.getValue() + NwConstants.IPV6PREFIX));
            String flowId = getIPv6FlowRef(dpId, elanTag,
                    vmPort.getIntfUUID().getValue() + Ipv6ServiceConstants.FLOWID_SEPARATOR + ipv6Address.getValue());
            FlowEntity rsFlowEntity =
                    MDSALUtil.buildFlowEntity(dpId, tableId, flowId, Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY,
                            "IPv6NA", 0, 0, NwConstants.COOKIE_IPV6_TABLE, matches, instructions);
            if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
                LOG.trace("Removing IPv6 Neighbor Advertisement Flow DpId {}, elanTag {}, ipv6Address {}", dpId,
                        elanTag, ipv6Address.getValue());
                mdsalUtil.removeFlow(rsFlowEntity);
            } else {
                LOG.trace("Installing IPv6 Neighbor Advertisement Flow DpId {}, elanTag {}, ipv6Address {}", dpId,
                        elanTag, ipv6Address.getValue());
                mdsalUtil.installFlow(rsFlowEntity);
            }
        }
    }

    public void installIcmpv6NaPuntFlow(short tableId, Ipv6Prefix ipv6Prefix, Uint64 dpId, Long elanTag,
            int addOrRemove) {
        List<MatchInfo> naMatch = getIcmpv6NAMatch(elanTag);
        naMatch.add(new MatchIpv6Source(ipv6Prefix));

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowId = getIPv6FlowRef(dpId, elanTag, "IPv6NA." + ipv6Prefix.getValue());
        FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                flowId, Ipv6ServiceConstants.PUNT_NA_FLOW_PRIORITY,
                "IPv6NA", 0, 0, NwConstants.COOKIE_IPV6_TABLE, naMatch, instructions);
        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            LOG.trace("Removing IPv6 Neighbor Advertisement Flow DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.removeFlow(rsFlowEntity);
        } else {
            LOG.trace("Installing IPv6 Neighbor Advertisement Flow DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.installFlow(rsFlowEntity);
        }
    }

    public BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                          Uint64 cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie)
                .setFlowPriority(flowPriority).setInstruction(instructions);
        return new BoundServicesBuilder().withKey(new BoundServicesKey(servicePriority))
                .setServiceName(serviceName).setServicePriority(servicePriority)
                .setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(augBuilder.build()).build();
    }

    private InstanceIdentifier<BoundServices> buildServiceId(String interfaceName,
                                              short priority) {
        return InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class,
                new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(priority)).build();
    }

    public void bindIpv6Service(String interfaceName, Long elanTag, short tableId) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getElanTagMetadata(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(tableId, ++instructionKey));
        short serviceIndex = ServiceIndex.getIndex(NwConstants.IPV6_SERVICE_NAME, NwConstants.IPV6_SERVICE_INDEX);
        BoundServices
                serviceInfo =
                getBoundServices(String.format("%s.%s", "ipv6", interfaceName),
                        serviceIndex, Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY,
                        NwConstants.COOKIE_IPV6_TABLE, instructions);
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, serviceIndex), serviceInfo);
    }

    public void unbindIpv6Service(String interfaceName) {
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, ServiceIndex.getIndex(NwConstants.IPV6_SERVICE_NAME,
                        NwConstants.IPV6_SERVICE_INDEX)));
    }

    @Nullable
    public Uint64 getDpIdFromInterfaceState(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
            .interfaces.rev140508.interfaces.state.Interface interfaceState) {
        Uint64 dpId = null;
        List<String> ofportIds = interfaceState.getLowerLayerIf();
        if (ofportIds != null && !ofportIds.isEmpty()) {
            NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
            dpId = getDpnIdFromNodeConnectorId(nodeConnectorId);
        }
        return dpId;
    }

    public static Uint64 getDpnIdFromNodeConnectorId(NodeConnectorId nodeConnectorId) {
        Long dpIdLong = MDSALUtil.getDpnIdFromPortName(nodeConnectorId);
        return dpIdLong < 0 ? Uint64.ZERO : Uint64.valueOf(dpIdLong);
    }

    public static long getRemoteBCGroup(long elanTag) {
        return Ipv6ServiceConstants.ELAN_GID_MIN + elanTag % Ipv6ServiceConstants.ELAN_GID_MIN * 2;
    }

    public static String buildIpv6MonitorJobKey(String ip) {
        return  "IPv6-"  + ip;
    }

    public static boolean isVmPort(String deviceOwner) {
        // FIXME: Currently for VM ports, Neutron is sending deviceOwner as empty instead of "compute:nova".
        // return Ipv6ServiceConstants.DEVICE_OWNER_COMPUTE_NOVA.equalsIgnoreCase(deviceOwner);
        return Ipv6ServiceConstants.DEVICE_OWNER_COMPUTE_NOVA.equalsIgnoreCase(deviceOwner)
                || StringUtils.isEmpty(deviceOwner);
    }

    public static boolean isIpv6Subnet(VirtualSubnet subnet) {
        if (subnet == null) {
            return false;
        }
        return subnet.getIpVersion().equals(Ipv6ServiceConstants.IP_VERSION_V6) ? true : false;
    }

    public ActionInfo getLearnActionForNsDrop(Long hardTimeoutinMs) {
        int hardTimeout = (int)(hardTimeoutinMs / 1000);
        hardTimeout = hardTimeout > 0 ? hardTimeout : 30;
        List<ActionLearn.FlowMod> flowMods = Arrays.asList(
                new ActionLearn.MatchFromValue(NwConstants.ETHTYPE_IPV6,
                        NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
                        NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromValue(IPProtocols.IPV6ICMP.intValue(),
                        NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getType(),
                        NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.OXM_OF_METADATA.getType(),
                        MetaDataUtil.METADATA_ELAN_TAG_OFFSET, NwConstants.NxmOfFieldType.OXM_OF_METADATA.getType(),
                        MetaDataUtil.METADATA_ELAN_TAG_OFFSET, Ipv6ServiceConstants.ELAN_TAG_LENGTH),
                new ActionLearn.MatchFromValue(Icmpv6Type.NEIGHBOR_SOLICITATION.getValue(),
                        NwConstants.NxmOfFieldType.OXM_OF_ICMPV6_TYPE.getType(),
                        NwConstants.NxmOfFieldType.OXM_OF_ICMPV6_TYPE.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.OXM_OF_IPV6_ND_TARGET.getType(),
                        NwConstants.NxmOfFieldType.OXM_OF_IPV6_ND_TARGET.getType(),
                        NwConstants.NxmOfFieldType.OXM_OF_IPV6_ND_TARGET.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.NXM_NX_IPV6_SRC.getType(),
                        NwConstants.NxmOfFieldType.NXM_NX_IPV6_SRC.getType(),
                        NwConstants.NxmOfFieldType.NXM_NX_IPV6_SRC.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.NXM_NX_IPV6_DST.getType(),
                        NwConstants.NxmOfFieldType.NXM_NX_IPV6_DST.getType(),
                        NwConstants.NxmOfFieldType.NXM_NX_IPV6_DST.getFlowModHeaderLenInt()));
        return new ActionLearn(0, hardTimeout, Ipv6ServiceConstants.SLOW_PATH_PROTECTION_PRIORITY,
                NwConstants.COOKIE_IPV6_TABLE, 0,
                NwConstants.IPV6_TABLE, 0, 0, flowMods);

    }

    public void instIcmpv6NsMatchFlow(short tableId, Uint64 dpId, Long elanTag, int lportTag, String vmMacAddress,
                                      Ipv6Address ndTargetAddr, int addOrRemove, TypedReadWriteTransaction tx,
                                      Boolean isSllOptionSet)
        throws ExecutionException, InterruptedException  {

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(getLearnActionForNsDrop(ipV6NAConfigHelper.getNsSlowProtectionTimeOutinMs()));
        actionsInfos.add(new ActionSetIcmpv6Type(Icmpv6Type.NEIGHBOR_ADVERTISEMENT.getValue()));
        short priority = Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY;
        if (isSllOptionSet) {
            actionsInfos.add(new ActionNdOptionType((short)2));
            priority = Ipv6ServiceConstants.SLLOPTION_SET_FLOW_PRIORITY;
        }
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(NwConstants.ARP_RESPONDER_TABLE));

        List<MatchInfo> neighborSolicitationMatch = getIcmpv6NsMatchFlow(elanTag, lportTag, vmMacAddress,
                ndTargetAddr, isSllOptionSet);

        FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                Boolean.TRUE.equals(isSllOptionSet)
                        ? getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue(), vmMacAddress) :
                        getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue()),priority,
                "IPv6NS", 0, 0, NwConstants.COOKIE_IPV6_TABLE,
                neighborSolicitationMatch, instructions);

        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            LOG.debug("installIcmpv6NsResponderFlow: Removing IPv6 Neighbor Solicitation Flow on "
                            + "DpId {} for NDTraget {}, elanTag {}, lportTag {}", dpId, ndTargetAddr.getValue(),
                    elanTag, lportTag);

            mdsalUtil.removeFlow(tx, rsFlowEntity);
        } else {
            LOG.debug("installIcmpv6NsResponderFlow: Installing IPv6 Neighbor Solicitation Flow on "
                            + "DpId {} for NDTraget {} elanTag {}, lportTag {}", dpId, ndTargetAddr.getValue(),
                    elanTag, lportTag);
            mdsalUtil.addFlow(tx, rsFlowEntity);
        }
    }

    public void installIcmpv6NaResponderFlow(short tableId, Uint64 dpId,
                                             Long elanTag, int lportTag, IVirtualPort intf, Ipv6Address ndTargetAddr,
                                             String rtrIntMacAddress, int addOrRemove,
                                             TypedReadWriteTransaction tx, Boolean isTllOptionSet)
        throws ExecutionException, InterruptedException {

        List<MatchInfo> neighborAdvertisementMatch = getIcmpv6NaResponderMatch(elanTag, lportTag, intf.getMacAddress(),
                ndTargetAddr, isTllOptionSet);
        short priority = isTllOptionSet ? Ipv6ServiceConstants.SLLOPTION_SET_FLOW_PRIORITY :
                Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY;
        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    Boolean.TRUE.equals(isTllOptionSet)
                            ? getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue(),
                            intf.getMacAddress()) : getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue()),
                    priority,"IPv6NA", 0, 0, NwConstants.COOKIE_IPV6_TABLE,
                    neighborAdvertisementMatch, null);
            LOG.debug("installIcmpv6NaResponderFlow: Removing IPv6 Neighbor Advertisement Flow on "
                            + "DpId {} for the NDTraget {}, elanTag {}, lportTag {}", dpId, ndTargetAddr.getValue(),
                    elanTag, lportTag);

            //mdsalUtil.removeFlowToTx(rsFlowEntity, tx);
            mdsalUtil.removeFlow(tx, rsFlowEntity);
        } else {
            List<ActionInfo> actionsInfos = new ArrayList<>();
            // Move Eth Src to Eth Dst
            actionsInfos.add(new ActionMoveSourceDestinationEth());
            actionsInfos.add(new ActionSetFieldEthernetSource(new MacAddress(rtrIntMacAddress)));

            // Move Ipv6 Src to Ipv6 Dst
            actionsInfos.add(new ActionMoveSourceDestinationIpv6());
            actionsInfos.add(new ActionSetSourceIpv6(ndTargetAddr.getValue()));

            actionsInfos.add(new ActionSetIpv6NdTarget(ndTargetAddr));
            if (Boolean.TRUE.equals(isTllOptionSet)) {
                actionsInfos.add(new ActionSetIpv6NdTll(new MacAddress(rtrIntMacAddress)));
                actionsInfos.add(new ActionNdReserved(Long.parseLong("3758096384")));
            } else {
                actionsInfos.add(new ActionNdReserved(Long.parseLong("3221225472")));
            }
            actionsInfos.add(new ActionNxLoadInPort(Uint64.ZERO));
            actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));

            List<InstructionInfo> instructions = new ArrayList<>();
            instructions.add(new InstructionApplyActions(actionsInfos));

            FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    Boolean.TRUE.equals(isTllOptionSet)
                            ? getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue(),
                            intf.getMacAddress()) : getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue()),
                    priority,"IPv6NA", 0, 0, NwConstants.COOKIE_IPV6_TABLE,
                    neighborAdvertisementMatch, instructions);
            LOG.debug("installIcmpv6NaResponderFlow: Installing IPv6 Neighbor Advertisement Flow on "
                            + "DpId {} for the NDTraget {},  elanTag {}, lportTag {}", dpId, ndTargetAddr.getValue(),
                    elanTag, lportTag);

            mdsalUtil.addFlow(tx, rsFlowEntity);
        }
    }

    public void installIcmpv6NsDefaultPuntFlow(short tableId, Uint64 dpId,  Long elanTag, Ipv6Address ipv6Address,
                                               int addOrRemove, TypedReadWriteTransaction tx)
        throws ExecutionException, InterruptedException {
        List<MatchInfo> neighborSolicitationMatch = getIcmpv6NSMatch(elanTag, ipv6Address);
        neighborSolicitationMatch.add(new MatchIpv6Source(UNSPECIFIED_ADDR.getValue() + "/128"));
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(getLearnActionForNsDrop(ipV6NAConfigHelper.getNsSlowProtectionTimeOutinMs()));
        actionsInfos.add(new ActionPuntToController());
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                getIPv6FlowRef(dpId, elanTag, ipv6Address + ".UNSPECIFIED.Switch.NS.Responder"),
                Ipv6ServiceConstants.FLOW_SUBNET_PRIORITY , "IPv6NS",
                0, 0, NwConstants.COOKIE_IPV6_TABLE, neighborSolicitationMatch, instructions);
        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            LOG.debug("installIcmpv6NsDefaultPuntFlow: Removing OVS based NA responder default subnet punt flow on "
                    + "DpId {}, elanTag {} for Unspecified Address", dpId, elanTag);
            mdsalUtil.removeFlow(tx, rsFlowEntity);

        } else {
            LOG.debug("installIcmpv6NsDefaultPuntFlow: Installing OVS based NA responder default subnet punt flow on "
                    + "DpId {}, elanTag {} for Unspecified Address", dpId, elanTag);
            mdsalUtil.addFlow(tx, rsFlowEntity);
        }
    }

    private List<MatchInfo> getIcmpv6NsMatchFlow(Long elanTag, int lportTag, String vmMacAddress,
                                                 Ipv6Address ndTargetAddr,
                                                 Boolean isSllOptionSet) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Icmpv6Type.NEIGHBOR_SOLICITATION.getValue(), (short) 0));
        matches.add(new MatchIpv6NdTarget(new Ipv6Address(ndTargetAddr)));
        if (Boolean.TRUE.equals(isSllOptionSet)) {
            matches.add(new MatchNdOptionType((short)1));
           /* matches.add(new MatchIpv6NdSll(new MacAddress(vmMacAddress))); */
        }
        matches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag, lportTag),
                ElanHelper.getElanMetadataMask()));
        return matches;
    }

    private List<MatchInfo> getIcmpv6NaResponderMatch(Long elanTag, int lportTag, String vmMacAddress,
                                                      Ipv6Address ndTarget, Boolean isNdOptionTypeSet) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Icmpv6Type.NEIGHBOR_ADVERTISEMENT.getValue(), (short) 0));
        matches.add(new MatchIpv6NdTarget(new Ipv6Address(ndTarget)));
        if (Boolean.TRUE.equals(isNdOptionTypeSet)) {
            matches.add(new MatchNdOptionType((short)2));
        }
        matches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag, lportTag),
                ElanHelper.getElanMetadataMask()));
        return matches;
    }
}

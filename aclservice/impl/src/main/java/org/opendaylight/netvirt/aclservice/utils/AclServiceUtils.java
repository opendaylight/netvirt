/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.globals.InterfaceServiceUtil;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack.NxCtAction;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchArpSpa;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.MatchCriteria;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.types.rev170711.NetvirtAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.AclPortsLookup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpVersionV6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.PortSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.acl.ports.lookup.AclPortsByIp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.acl.ports.lookup.AclPortsByIpKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.acl.ports.lookup.acl.ports.by.ip.AclIpPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.acl.ports.lookup.acl.ports.by.ip.AclIpPrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.acl.ports.lookup.acl.ports.by.ip.acl.ip.prefixes.PortIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.acl.ports.lookup.acl.ports.by.ip.acl.ip.prefixes.PortIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.acl.ports.lookup.acl.ports.by.ip.acl.ip.prefixes.PortIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.PortSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.PortSubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.port.subnet.SubnetInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class AclServiceUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceUtils.class);
    public static final AclserviceConfig.DefaultBehavior DEFAULT_DENY = AclserviceConfig.DefaultBehavior.Deny;
    public static final AclserviceConfig.DefaultBehavior DEFAULT_ALLOW = AclserviceConfig.DefaultBehavior.Allow;

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final AclDataUtil aclDataUtil;
    private final AclserviceConfig config;
    private final IdManagerService idManager;
    private final JobCoordinator jobCoordinator;

    @Inject
    public AclServiceUtils(DataBroker dataBroker, AclDataUtil aclDataUtil, AclserviceConfig config,
            IdManagerService idManager, JobCoordinator jobCoordinator) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.aclDataUtil = aclDataUtil;
        this.config = config;
        this.idManager = idManager;
        this.jobCoordinator = jobCoordinator;
    }

    /**
     * Retrieves the Interface from the datastore.
     * @param broker the data broker
     * @param interfaceName the interface name
     * @return the interface.
     */
    public static Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .Interface> getInterface(DataBroker broker, String interfaceName) {
        return read(broker, LogicalDatastoreType.CONFIGURATION, getInterfaceIdentifier(interfaceName));
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
     * Retrieves the object from the datastore.
     * @param broker the data broker.
     * @param datastoreType the data store type.
     * @param path the wild card path.
     * @param <T> type of DataObject
     * @return the required object.
     */
    public static <T extends DataObject> Optional<T> read(
            DataBroker broker, LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).checkedGet();
        } catch (ReadFailedException e) {
            LOG.error("Failed to read InstanceIdentifier {} from {}", path, datastoreType, e);
            return Optional.absent();
        }
    }

    /**
     * Retrieves the acl matching the key from the data store.
     *
     * @param broker the data broker
     * @param aclKey the acl key
     * @return the acl
     */
    public static Acl getAcl(DataBroker broker, String aclKey) {
        return read(broker, LogicalDatastoreType.CONFIGURATION, getAclInstanceIdentifier(aclKey)).orNull();
    }

    /** Creates the Acl instance identifier.
     *
     * @param aclKey the acl key
     * @return the instance identifier
     */
    public static InstanceIdentifier<Acl> getAclInstanceIdentifier(String aclKey) {
        return InstanceIdentifier.builder(AccessLists.class).child(Acl.class, new AclKey(aclKey, Ipv4Acl.class))
                .build();
    }

    /**
     * Get the data path number for the interface.
     * @param interfaceManagerRpcService interfaceManagerRpcService instance.
     * @param ifName the interface name.
     * @return the dpn.
     */
    public static BigInteger getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        BigInteger nodeId = BigInteger.ZERO;
        try {
            GetDpidFromInterfaceInput dpIdInput =
                    new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>> dpIdOutput =
                    interfaceManagerRpcService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if (dpIdResult.isSuccessful()) {
                nodeId = dpIdResult.getResult().getDpid();
            } else {
                LOG.error("Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting dpn for interface {}", ifName,  e);
        }
        return nodeId;
    }

    /**
     * Retrieves the interface state.
     * @param dataBroker the data broker.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
        .Interface getInterfaceStateFromOperDS(DataBroker dataBroker, String interfaceName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> ifStateId = buildStateInterfaceId(interfaceName);
        return MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, ifStateId, dataBroker).orNull();
    }

    /**
     * Build the interface state.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
        .interfaces.state.Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> idBuilder = InstanceIdentifier.builder(InterfacesState.class)
            .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .state.Interface.class, new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
            .rev140508.interfaces.state.InterfaceKey(interfaceName));
        return idBuilder.build();
    }

    /**
     * Checks whether port security is enabled for the port.
     * @param port the port.
     * @return the port security is enabled/not.
     */
    public static boolean isPortSecurityEnabled(AclInterface port) {
        return port.isPortSecurityEnabled();
    }

    /**
     * Checks whether port security is enabled for the port.
     * @param port the port.
     * @return the list of security groups.
     */
    public static List<Uuid> getInterfaceAcls(Interface port) {
        if (port == null) {
            LOG.error("Port is Null");
            return null;
        }
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort == null) {
            LOG.error("getSecurityGroupInPortList: no security group associated}",
                port.getName());
            return null;
        }
        return aclInPort.getSecurityGroups();
    }

    /**
     * Retrieves the security rule attribute augmentation from the access list.
     * @param ace the access list entry
     * @return the security rule attributes
     */
    public static SecurityRuleAttr  getAccesssListAttributes(Ace ace) {
        if (ace == null) {
            LOG.error("Ace is Null");
            return null;
        }
        SecurityRuleAttr aceAttributes = ace.getAugmentation(SecurityRuleAttr.class);
        if (aceAttributes == null) {
            LOG.error("Ace is null");
            return null;
        }
        return aceAttributes;
    }

    /**
     * Returns the DHCP match.
     *
     * @param srcPort the source port.
     * @param dstPort the destination port.
     * @param lportTag the lport tag
     * @param serviceMode ingress or egress service
     * @return list of matches.
     */
    public static List<MatchInfoBase> buildDhcpMatches(int srcPort, int dstPort, int lportTag,
            Class<? extends ServiceModeBase> serviceMode) {
        List<MatchInfoBase> matches = new ArrayList<>(5);
        matches.add(MatchEthernetType.IPV4);
        matches.add(MatchIpProtocol.UDP);
        matches.add(new MatchUdpDestinationPort(dstPort));
        matches.add(new MatchUdpSourcePort(srcPort));
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        return matches;
    }

    /**
     * Returns the DHCPv6 match.
     *
     * @param srcPort the source port.
     * @param dstPort the destination port.
     * @param lportTag the lport tag
     * @param serviceMode ingress or egress
     * @return list of matches.
     */
    public static List<MatchInfoBase> buildDhcpV6Matches(int srcPort, int dstPort, int lportTag,
            Class<? extends ServiceModeBase> serviceMode) {
        List<MatchInfoBase> matches = new ArrayList<>(6);
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.UDP);
        matches.add(new MatchUdpDestinationPort(dstPort));
        matches.add(new MatchUdpSourcePort(srcPort));
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        return matches;
    }

    /**
     * Returns the ICMPv6 match.
     *
     * @param icmpType the icmpv6-type.
     * @param icmpCode the icmpv6-code.
     * @param lportTag the lport tag
     * @param serviceMode ingress or egress
     * @return list of matches.
     */
    public static List<MatchInfoBase> buildIcmpV6Matches(int icmpType, int icmpCode, int lportTag,
            Class<? extends ServiceModeBase> serviceMode) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        if (icmpType != 0) {
            matches.add(new MatchIcmpv6((short) icmpType, (short) icmpCode));
        }
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        return matches;
    }

    public static List<MatchInfoBase> buildBroadcastIpV4Matches(String ipAddr) {
        List<MatchInfoBase> matches = new ArrayList<>(2);
        matches.add(new MatchEthernetDestination(new MacAddress(AclConstants.BROADCAST_MAC)));
        matches.addAll(AclServiceUtils.buildIpMatches(new IpPrefixOrAddress(ipAddr.toCharArray()),
                MatchCriteria.MATCH_DESTINATION));
        return matches;
    }

    public static List<MatchInfoBase> buildL2BroadcastMatches() {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new MatchEthernetDestination(new MacAddress(AclConstants.BROADCAST_MAC)));
        return matches;
    }

    /**
     * Builds the service id.
     *
     * @param interfaceName the interface name
     * @param serviceIndex the service index
     * @param serviceMode the service mode
     * @return the instance identifier
     */
    public static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName, short serviceIndex,
            Class<? extends ServiceModeBase> serviceMode) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, serviceMode))
                .child(BoundServices.class, new BoundServicesKey(serviceIndex)).build();
    }

    /**
     * Gets the bound services.
     *
     * @param serviceName the service name
     * @param servicePriority the service priority
     * @param flowPriority the flow priority
     * @param cookie the cookie
     * @param instructions the instructions
     * @return the bound services
     */
    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
            BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority)
                .setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority)).setServiceName(serviceName)
                .setServicePriority(servicePriority).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static List<Uuid> getUpdatedAclList(List<Uuid> updatedAclList, List<Uuid> currentAclList) {
        if (updatedAclList == null) {
            return Collections.emptyList();
        }
        List<Uuid> newAclList = new ArrayList<>(updatedAclList);
        if (currentAclList == null) {
            return newAclList;
        }
        List<Uuid> origAclList = new ArrayList<>(currentAclList);
        for (Iterator<Uuid> iterator = newAclList.iterator(); iterator.hasNext();) {
            Uuid updatedAclUuid = iterator.next();
            for (Uuid currentAclUuid :origAclList) {
                if (updatedAclUuid.getValue().equals(currentAclUuid.getValue())) {
                    iterator.remove();
                }
            }
        }
        return newAclList;
    }

    public static List<AllowedAddressPairs> getUpdatedAllowedAddressPairs(
            List<AllowedAddressPairs> updatedAllowedAddressPairs,
            List<AllowedAddressPairs> currentAllowedAddressPairs) {
        if (updatedAllowedAddressPairs == null) {
            return null;
        }
        List<AllowedAddressPairs> newAllowedAddressPairs = new ArrayList<>(updatedAllowedAddressPairs);
        if (currentAllowedAddressPairs == null) {
            return newAllowedAddressPairs;
        }
        List<AllowedAddressPairs> origAllowedAddressPairs = new ArrayList<>(currentAllowedAddressPairs);
        for (Iterator<AllowedAddressPairs> iterator = newAllowedAddressPairs.iterator(); iterator.hasNext();) {
            AllowedAddressPairs updatedAllowedAddressPair = iterator.next();
            for (AllowedAddressPairs currentAllowedAddressPair : origAllowedAddressPairs) {
                if (updatedAllowedAddressPair.getKey().equals(currentAllowedAddressPair.getKey())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return newAllowedAddressPairs;
    }

    public static List<AllowedAddressPairs> getPortAllowedAddresses(Interface port) {
        if (port == null) {
            LOG.error("Port is Null");
            return null;
        }
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort == null) {
            LOG.error("getSecurityGroupInPortList: no security group associated to Interface port: {}", port.getName());
            return null;
        }
        return aclInPort.getAllowedAddressPairs();
    }

    public static BigInteger getDpIdFromIterfaceState(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
            .interfaces.rev140508.interfaces.state.Interface interfaceState) {
        BigInteger dpId = null;
        List<String> ofportIds = interfaceState.getLowerLayerIf();
        if (ofportIds != null && !ofportIds.isEmpty()) {
            NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
            dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        }
        return dpId;
    }

    public static List<String> getIpBroadcastAddresses(List<SubnetInfo> subnetInfoList) {
        List<String> ipBroadcastAddresses = new ArrayList<>();
        for (SubnetInfo subnetInfo : subnetInfoList) {
            IpPrefix cidrIpPrefix = subnetInfo.getIpPrefix().getIpPrefix();
            if (cidrIpPrefix != null) {
                Ipv4Prefix cidrIpv4Prefix = cidrIpPrefix.getIpv4Prefix();
                if (cidrIpv4Prefix != null) {
                    ipBroadcastAddresses.add(getBroadcastAddressFromCidr(cidrIpv4Prefix.getValue()));
                }
            }
        }
        return ipBroadcastAddresses;
    }

    public static String getBroadcastAddressFromCidr(String cidr) {
        String[] ipaddressValues = cidr.split("/");
        int address = InetAddresses.coerceToInteger(InetAddresses.forString(ipaddressValues[0]));
        int cidrPart = Integer.parseInt(ipaddressValues[1]);
        int netmask = 0;
        for (int j = 0; j < cidrPart; ++j) {
            netmask |= 1 << 31 - j;
        }
        int network = address & netmask;
        int broadcast = network | ~netmask;
        return InetAddresses.toAddrString(InetAddresses.fromInteger(broadcast));
    }

    /**
     * Builds the ip matches.
     *
     * @param ipPrefixOrAddress the ip prefix or address
     * @param matchCriteria the source_ip or destination_ip used for the match
     * @return the list
     */
    public static List<MatchInfoBase> buildIpMatches(IpPrefixOrAddress ipPrefixOrAddress,
                                                     MatchCriteria matchCriteria) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        IpPrefix ipPrefix = ipPrefixOrAddress.getIpPrefix();
        if (ipPrefix != null) {
            Ipv4Prefix ipv4Prefix = ipPrefix.getIpv4Prefix();
            if (ipv4Prefix != null) {
                flowMatches.add(MatchEthernetType.IPV4);
                if (!ipv4Prefix.getValue().equals(AclConstants.IPV4_ALL_NETWORK)) {
                    flowMatches.add(matchCriteria == MatchCriteria.MATCH_SOURCE ? new MatchIpv4Source(ipv4Prefix)
                            : new MatchIpv4Destination(ipv4Prefix));
                }
            } else {
                flowMatches.add(MatchEthernetType.IPV6);
                flowMatches.add(matchCriteria == MatchCriteria.MATCH_SOURCE ? new MatchIpv6Source(
                        ipPrefix.getIpv6Prefix()) : new MatchIpv6Destination(ipPrefix.getIpv6Prefix()));
            }
        } else {
            IpAddress ipAddress = ipPrefixOrAddress.getIpAddress();
            if (ipAddress.getIpv4Address() != null) {
                flowMatches.add(MatchEthernetType.IPV4);
                flowMatches.add(matchCriteria == MatchCriteria.MATCH_SOURCE ? new MatchIpv4Source(
                        ipAddress.getIpv4Address().getValue(), "32") : new MatchIpv4Destination(
                        ipAddress.getIpv4Address().getValue(), "32"));
            } else {
                flowMatches.add(MatchEthernetType.IPV6);
                flowMatches.add(matchCriteria == MatchCriteria.MATCH_SOURCE ? new MatchIpv6Source(
                        ipAddress.getIpv6Address().getValue() + "/128") : new MatchIpv6Destination(
                        ipAddress.getIpv6Address().getValue() + "/128"));
            }
        }
        return flowMatches;
    }

    /**
     * Builds the arp ip matches.
     * @param ipPrefixOrAddress the ip prefix or address
     * @return the MatchInfoBase list
     */
    public static List<MatchInfoBase> buildArpIpMatches(IpPrefixOrAddress ipPrefixOrAddress) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        IpPrefix ipPrefix = ipPrefixOrAddress.getIpPrefix();
        if (ipPrefix != null) {
            Ipv4Prefix ipv4Prefix = ipPrefix.getIpv4Prefix();
            if (ipv4Prefix != null && !ipv4Prefix.getValue().equals(AclConstants.IPV4_ALL_NETWORK)) {
                flowMatches.add(new MatchArpSpa(ipv4Prefix));
            }
        } else {
            IpAddress ipAddress = ipPrefixOrAddress.getIpAddress();
            if (ipAddress != null && ipAddress.getIpv4Address() != null) {
                flowMatches.add(new MatchArpSpa(ipAddress.getIpv4Address().getValue(), "32"));
            }
        }
        return flowMatches;
    }

    public static MatchInfoBase buildRemoteAclTagMetadataMatch(Integer remoteAclTag) {
        return new MatchMetadata(getRemoteAclTagMetadata(BigInteger.valueOf(remoteAclTag)),
                MetaDataUtil.METADATA_MASK_REMOTE_ACL_TAG);
    }

    public static BigInteger getRemoteAclTagMetadata(BigInteger remoteAclTag) {
        return remoteAclTag.shiftLeft(4);
    }

    public static BigInteger getDropFlowCookie(int lport) {
        return MetaDataUtil.getLportTagMetaData(lport).or(AclConstants.COOKIE_ACL_DROP_FLOW);
    }

    /**
     * Does IPv4 address exists in the list of allowed address pair.
     *
     * @param aaps the allowed address pairs
     * @return true, if successful
     */
    public static boolean doesIpv4AddressExists(List<AllowedAddressPairs> aaps) {
        if (aaps == null) {
            return false;
        }
        for (AllowedAddressPairs aap : aaps) {
            IpPrefixOrAddress ipPrefixOrAddress = aap.getIpAddress();
            IpPrefix ipPrefix = ipPrefixOrAddress.getIpPrefix();
            if (ipPrefix != null) {
                if (ipPrefix.getIpv4Prefix() != null) {
                    return true;
                }
            } else {
                IpAddress ipAddress = ipPrefixOrAddress.getIpAddress();
                if (ipAddress != null && ipAddress.getIpv4Address() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Does IPv6 address exists in the list of allowed address pair.
     *
     * @param aaps the allowed address pairs
     * @return true, if successful
     */
    public static boolean doesIpv6AddressExists(List<AllowedAddressPairs> aaps) {
        if (aaps == null) {
            return false;
        }
        for (AllowedAddressPairs aap : aaps) {
            IpPrefixOrAddress ipPrefixOrAddress = aap.getIpAddress();
            IpPrefix ipPrefix = ipPrefixOrAddress.getIpPrefix();
            if (ipPrefix != null) {
                if (ipPrefix.getIpv6Prefix() != null) {
                    return true;
                }
            } else {
                IpAddress ipAddress = ipPrefixOrAddress.getIpAddress();
                if (ipAddress != null && ipAddress.getIpv6Address() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the lport tag match.
     * Ingress match is based on metadata and egress match is based on masked reg6
     *
     * @param lportTag the lport tag
     * @param serviceMode ingress or egress service mode
     * @return the lport tag match
     */
    public static MatchInfoBase buildLPortTagMatch(int lportTag, Class<? extends ServiceModeBase> serviceMode) {
        if (serviceMode != null && serviceMode.isAssignableFrom(ServiceModeEgress.class)) {
            return new NxMatchRegister(NxmNxReg6.class, MetaDataUtil.getLportTagForReg6(lportTag).longValue(),
                    MetaDataUtil.getLportTagMaskForReg6());
        } else {
            return new MatchMetadata(MetaDataUtil.getLportTagMetaData(lportTag), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        }
    }

    public static List<MatchInfoBase> buildMatchesForLPortTagAndRemoteAclTag(Integer lportTag, Integer remoteAclTag,
            Class<? extends ServiceModeBase> serviceMode) {
        List<MatchInfoBase> matches = new ArrayList<>();
        if (serviceMode != null && serviceMode.isAssignableFrom(ServiceModeEgress.class)) {
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
            matches.add(AclServiceUtils.buildRemoteAclTagMetadataMatch(remoteAclTag));
        } else {
            // In case of ingress service mode, only metadata is used for
            // matching both lportTag and aclTag. Hence performing "or"
            // operation on both lportTag and aclTag metadata.
            BigInteger metaData = MetaDataUtil.getLportTagMetaData(lportTag)
                    .or(getRemoteAclTagMetadata(BigInteger.valueOf(remoteAclTag)));
            BigInteger metaDataMask =
                    MetaDataUtil.METADATA_MASK_LPORT_TAG.or(MetaDataUtil.METADATA_MASK_REMOTE_ACL_TAG);
            matches.add(new MatchMetadata(metaData, metaDataMask));
        }
        return matches;
    }

    public static Collection<? extends MatchInfoBase> buildMatchesForLPortTagAndConntrackClassifierType(int lportTag,
            AclConntrackClassifierType conntrackClassifierType, Class<? extends ServiceModeBase> serviceMode) {
        List<MatchInfoBase> matches = new ArrayList<>();
        if (serviceMode != null && serviceMode.isAssignableFrom(ServiceModeEgress.class)) {
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
            matches.add(AclServiceUtils.buildAclConntrackClassifierTypeMatch(conntrackClassifierType));
        } else {
            // In case of ingress service mode, only metadata is used for
            // matching both lportTag and conntrackClassifierType. Hence performing "or"
            // operation on both lportTag and conntrackClassifierType metadata.
            BigInteger metaData = MetaDataUtil.getLportTagMetaData(lportTag)
                    .or(MetaDataUtil.getAclConntrackClassifierTypeFromMetaData(conntrackClassifierType.getValue()));
            BigInteger metaDataMask =
                    MetaDataUtil.METADATA_MASK_LPORT_TAG.or(MetaDataUtil.METADATA_MASK_ACL_CONNTRACK_CLASSIFIER_TYPE);
            matches.add(new MatchMetadata(metaData, metaDataMask));
        }
        return matches;
    }

    public static InstructionWriteMetadata getWriteMetadataForAclClassifierType(
            AclConntrackClassifierType conntrackClassifierType) {
        return new InstructionWriteMetadata(
                MetaDataUtil.getAclConntrackClassifierTypeFromMetaData(conntrackClassifierType.getValue()),
                MetaDataUtil.METADATA_MASK_ACL_CONNTRACK_CLASSIFIER_TYPE);
    }

    public static InstructionWriteMetadata getWriteMetadataForDropFlag() {
        return new InstructionWriteMetadata(MetaDataUtil.getAclDropMetaData(AclConstants.METADATA_DROP_FLAG),
                MetaDataUtil.METADATA_MASK_ACL_DROP);
    }

    public static InstructionWriteMetadata getWriteMetadataForRemoteAclTag(Integer remoteAclTag) {
        return new InstructionWriteMetadata(getRemoteAclTagMetadata(BigInteger.valueOf(remoteAclTag)),
                MetaDataUtil.METADATA_MASK_REMOTE_ACL_TAG);
    }

    public static MatchInfoBase buildAclConntrackClassifierTypeMatch(
            AclConntrackClassifierType conntrackSupportedType) {
        return new MatchMetadata(
                MetaDataUtil.getAclConntrackClassifierTypeFromMetaData(conntrackSupportedType.getValue()),
                MetaDataUtil.METADATA_MASK_ACL_CONNTRACK_CLASSIFIER_TYPE);
    }

    public AclserviceConfig getConfig() {
        return config;
    }

    public static boolean isIPv4Address(AllowedAddressPairs aap) {
        IpPrefixOrAddress ipPrefixOrAddress = aap.getIpAddress();
        IpPrefix ipPrefix = ipPrefixOrAddress.getIpPrefix();
        if (ipPrefix != null) {
            if (ipPrefix.getIpv4Prefix() != null) {
                return true;
            }
        } else {
            IpAddress ipAddress = ipPrefixOrAddress.getIpAddress();
            if (ipAddress != null && ipAddress.getIpv4Address() != null) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, List<MatchInfoBase>> getFlowForAllowedAddresses(
            List<AllowedAddressPairs> syncAllowedAddresses, Map<String, List<MatchInfoBase>> flowMatchesMap,
            boolean isSourceIpMacMatch) {
        if (flowMatchesMap == null) {
            return null;
        }
        Map<String, List<MatchInfoBase>> updatedFlowMatchesMap = new HashMap<>();
        MatchInfoBase ipv4Match = MatchEthernetType.IPV4;
        MatchInfoBase ipv6Match = MatchEthernetType.IPV6;
        for (Entry<String, List<MatchInfoBase>> entry : flowMatchesMap.entrySet()) {
            String flowName = entry.getKey();
            List<MatchInfoBase> flows = entry.getValue();
            // iterate over allow address pair and update match type
            for (AllowedAddressPairs aap : syncAllowedAddresses) {
                List<MatchInfoBase> matchInfoBaseList;
                String flowId;
                if (flows.contains(ipv4Match) && isIPv4Address(aap) && isNotIpv4AllNetwork(aap)) {
                    matchInfoBaseList = updateAAPMatches(isSourceIpMacMatch, flows, aap);
                    flowId = flowName + "_ipv4_remoteACL_interface_aap_" + getAapFlowId(aap);
                    updatedFlowMatchesMap.put(flowId, matchInfoBaseList);
                } else if (flows.contains(ipv6Match) && !isIPv4Address(aap) && isNotIpv6AllNetwork(aap)) {
                    matchInfoBaseList = updateAAPMatches(isSourceIpMacMatch, flows, aap);
                    flowId = flowName + "_ipv6_remoteACL_interface_aap_" + getAapFlowId(aap);
                    updatedFlowMatchesMap.put(flowId, matchInfoBaseList);
                }
            }
        }
        return updatedFlowMatchesMap;
    }

    public static boolean isNotIpv4AllNetwork(AllowedAddressPairs aap) {
        IpPrefix ipPrefix = aap.getIpAddress().getIpPrefix();
        if (ipPrefix != null && ipPrefix.getIpv4Prefix() != null
                && ipPrefix.getIpv4Prefix().getValue().equals(AclConstants.IPV4_ALL_NETWORK)) {
            return false;
        }
        return true;
    }

    protected static boolean isNotIpv6AllNetwork(AllowedAddressPairs aap) {
        IpPrefix ipPrefix = aap.getIpAddress().getIpPrefix();
        if (ipPrefix != null && ipPrefix.getIpv6Prefix() != null
                && ipPrefix.getIpv6Prefix().getValue().equals(AclConstants.IPV6_ALL_NETWORK)) {
            return false;
        }
        return true;
    }

    public static boolean isNotIpAllNetwork(AllowedAddressPairs aap) {
        return isNotIpv4AllNetwork(aap) && isNotIpv6AllNetwork(aap);
    }

    private static String getAapFlowId(AllowedAddressPairs aap) {
        return aap.getMacAddress().getValue() + "_" + String.valueOf(aap.getIpAddress().getValue());
    }

    public static Long getElanIdFromInterface(String elanInterfaceName,DataBroker broker) {
        ElanInterface elanInterface = getElanInterfaceByElanInterfaceName(elanInterfaceName, broker);
        if (null != elanInterface) {
            ElanInstance elanInfo = getElanInstanceByName(elanInterface.getElanInstanceName(), broker);
            return elanInfo.getElanTag();
        }
        return null;
    }

    public static ElanInterface getElanInterfaceByElanInterfaceName(String elanInterfaceName,DataBroker broker) {
        InstanceIdentifier<ElanInterface> elanInterfaceId = getElanInterfaceConfigurationDataPathId(elanInterfaceName);
        return read(broker, LogicalDatastoreType.CONFIGURATION, elanInterfaceId).orNull();
    }

    public static InstanceIdentifier<ElanInterface> getElanInterfaceConfigurationDataPathId(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).build();
    }

    // elan-instances config container
    public static ElanInstance getElanInstanceByName(String elanInstanceName, DataBroker broker) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        return read(broker, LogicalDatastoreType.CONFIGURATION, elanIdentifierId).orNull();
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
    }

    public List<SubnetInfo> getSubnetInfo(String portId) {
        InstanceIdentifier<PortSubnet> id = InstanceIdentifier.builder(PortSubnets.class)
                .child(PortSubnet.class, new PortSubnetKey(portId)).build();

        Optional<PortSubnet> portSubnet = read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (portSubnet.isPresent()) {
            return portSubnet.get().getSubnetInfo();
        }
        return null;
    }

    public void deleteSubnetInfo(String portId) {
        InstanceIdentifier<PortSubnet> id = InstanceIdentifier.builder(PortSubnets.class)
                .child(PortSubnet.class, new PortSubnetKey(portId)).build();
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to delete subnet info for port={}", portId, e);
        }
    }

    private static List<MatchInfoBase> updateAAPMatches(boolean isSourceIpMacMatch, List<MatchInfoBase> flows,
                                                        AllowedAddressPairs aap) {
        List<MatchInfoBase> matchInfoBaseList;
        if (isSourceIpMacMatch) {
            matchInfoBaseList = AclServiceUtils.buildIpMatches(aap.getIpAddress(), MatchCriteria.MATCH_SOURCE);
        } else {
            matchInfoBaseList = AclServiceUtils.buildIpMatches(aap.getIpAddress(), MatchCriteria.MATCH_DESTINATION);
        }
        matchInfoBaseList.addAll(flows);
        return matchInfoBaseList;
    }

    public static MatchInfoBase getMatchInfoByType(List<MatchInfoBase> flows, Class<? extends NxMatchInfo> type) {
        for (MatchInfoBase mib : flows) {
            if (type.isAssignableFrom(mib.getClass())) {
                return mib;
            }
        }
        return null;
    }

    public static boolean containsMatchFieldType(List<MatchInfoBase> flows, Class<? extends NxMatchInfo> type) {
        return getMatchInfoByType(flows, type) != null;
    }

    public static boolean containsTcpMatchField(List<MatchInfoBase> flows) {
        return flows.contains(MatchIpProtocol.TCP);
    }

    public static boolean containsUdpMatchField(List<MatchInfoBase> flows) {
        return flows.contains(MatchIpProtocol.UDP);
    }

    public static Integer allocateId(IdManagerService idManager, String poolName, String idKey, Integer defaultId) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                Integer allocatedId = rpcResult.getResult().getIdValue().intValue();
                LOG.debug("Allocated ACL ID: {} with key: {} into pool: {}", allocatedId, idKey, poolName);
                return allocatedId;
            } else {
                LOG.error("RPC Call to Get Unique Id for key {} from pool {} returned with Errors {}",
                        idKey, poolName, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting Unique Id for key {} from pool {} ", idKey, poolName, e);
        }
        return defaultId;
    }

    public static void releaseId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to release Id with Key {} from pool {} returned with Errors {}",
                        idKey, poolName, rpcResult.getErrors());
            } else {
                LOG.debug("Released ACL ID with key: {} from pool: {}", idKey, poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when releasing Id for key {} from pool {} ", idKey, poolName, e);
        }
    }

    /**
     * Gets the ACL tag from cache. If not found in cache, tries to allocate and
     * return the value.
     *
     * @param aclId the acl id
     * @return the acl tag
     */
    public Integer getAclTag(final Uuid aclId) {
        String aclName = aclId.getValue();
        Integer aclTag = this.aclDataUtil.getAclTag(aclName);
        if (aclTag == null) {
            LOG.debug("ACL tag not found in cache for ACL={}, trying to allocate again.", aclName);
            aclTag = allocateAclTag(aclName);
            if (aclTag != null && aclTag != AclConstants.INVALID_ACL_TAG) {
                this.aclDataUtil.addAclTag(aclName, aclTag);
            }
        }
        return aclTag;
    }

    /**
     * Allocate ACL tag.
     *
     * @param aclName the ACL name
     * @return the integer
     */
    public Integer allocateAclTag(String aclName) {
        Integer aclTag = AclServiceUtils.allocateId(this.idManager, AclConstants.ACL_TAG_POOL_NAME, aclName,
                AclConstants.INVALID_ACL_TAG);
        return aclTag;
    }

    /**
     * Release ACL tag.
     *
     * @param aclName the ACL name
     */
    public void releaseAclTag(String aclName) {
        AclServiceUtils.releaseId(this.idManager, AclConstants.ACL_TAG_POOL_NAME, aclName);
    }

    /**
     * Indicates whether the interface has port security enabled.
     *
     * @param aclInterface the interface.
     * @return true if port is security enabled.
     */
    public static boolean isOfInterest(AclInterface aclInterface) {
        return aclInterface != null && aclInterface.isPortSecurityEnabled();
    }

    /**
     * Creates the id pool for ACL tag.
     *
     * @param poolName the pool name
     */
    private void createIdPoolForAclTag(String poolName) {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
                .setPoolName(poolName).setLow(AclConstants.ACL_TAG_POOL_START)
                .setHigh(AclConstants.ACL_TAG_POOL_END).build();
        try {
            Future<RpcResult<Void>> result = this.idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("Created IdPool for {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create ID pool [{}] for remote ACL ids", poolName, e);
            throw new RuntimeException("Failed to create ID pool [{}] for remote ACL ids", e);
        }
    }

    /**
     * Delete id pool.
     *
     * @param poolName the pool name
     */
    public void deleteIdPool(String poolName) {
        DeleteIdPoolInput deletePool = new DeleteIdPoolInputBuilder().setPoolName(poolName).build();
        try {
            Future<RpcResult<Void>> result = this.idManager.deleteIdPool(deletePool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("Deleted IdPool for {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to delete ID pool [{}]", poolName, e);
            throw new RuntimeException("Failed to delete ID pool [" + poolName + "]", e);
        }
    }

    /**
     * Creates remote the acl id pools.
     */
    public void createRemoteAclIdPool() {
        createIdPoolForAclTag(AclConstants.ACL_TAG_POOL_NAME);
    }

    /**
     * Delete remote the acl id pools.
     */
    public void deleteRemoteAclIdPool() {
        deleteIdPool(AclConstants.ACL_TAG_POOL_NAME);
    }

    public static List<? extends MatchInfoBase> buildIpAndSrcServiceMatch(Integer aclTag, AllowedAddressPairs aap) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.add(buildRemoteAclTagMetadataMatch(aclTag));
        if (aap.getIpAddress().getIpAddress() != null) {
            if (aap.getIpAddress().getIpAddress().getIpv4Address() != null) {
                MatchEthernetType ipv4EthMatch = new MatchEthernetType(NwConstants.ETHTYPE_IPV4);
                flowMatches.add(ipv4EthMatch);
                MatchIpv4Source srcMatch = new MatchIpv4Source(
                        new Ipv4Prefix(aap.getIpAddress().getIpAddress().getIpv4Address().getValue() + "/32"));
                flowMatches.add(srcMatch);
            } else if (aap.getIpAddress().getIpAddress().getIpv6Address() != null) {
                MatchEthernetType ipv6EthMatch = new MatchEthernetType(NwConstants.ETHTYPE_IPV6);
                flowMatches.add(ipv6EthMatch);
                MatchIpv6Source srcMatch = new MatchIpv6Source(
                        new Ipv6Prefix(aap.getIpAddress().getIpAddress().getIpv6Address().getValue() + "/128"));
                flowMatches.add(srcMatch);
            }
        } else if (aap.getIpAddress().getIpPrefix() != null) {
            if (aap.getIpAddress().getIpPrefix().getIpv4Prefix() != null) {
                MatchEthernetType ipv4EthMatch = new MatchEthernetType(NwConstants.ETHTYPE_IPV4);
                flowMatches.add(ipv4EthMatch);
                MatchIpv4Source srcMatch = new MatchIpv4Source(aap.getIpAddress().getIpPrefix().getIpv4Prefix());
                flowMatches.add(srcMatch);
            } else if (aap.getIpAddress().getIpPrefix().getIpv6Prefix() != null) {
                MatchEthernetType ipv6EthMatch = new MatchEthernetType(NwConstants.ETHTYPE_IPV6);
                flowMatches.add(ipv6EthMatch);
                MatchIpv6Source srcMatch = new MatchIpv6Source(aap.getIpAddress().getIpPrefix().getIpv6Prefix());
                flowMatches.add(srcMatch);
            }
        }
        return flowMatches;
    }

    public static List<? extends MatchInfoBase> buildIpAndDstServiceMatch(Integer aclTag, AllowedAddressPairs aap) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.add(buildRemoteAclTagMetadataMatch(aclTag));

        if (aap.getIpAddress().getIpAddress() != null) {
            if (aap.getIpAddress().getIpAddress().getIpv4Address() != null) {
                MatchEthernetType ipv4EthMatch = new MatchEthernetType(NwConstants.ETHTYPE_IPV4);
                flowMatches.add(ipv4EthMatch);
                MatchIpv4Destination dstMatch = new MatchIpv4Destination(
                        new Ipv4Prefix(aap.getIpAddress().getIpAddress().getIpv4Address().getValue() + "/32"));
                flowMatches.add(dstMatch);
            } else if (aap.getIpAddress().getIpAddress().getIpv6Address() != null) {
                MatchEthernetType ipv6EthMatch = new MatchEthernetType(NwConstants.ETHTYPE_IPV6);
                flowMatches.add(ipv6EthMatch);
                MatchIpv6Destination dstMatch = new MatchIpv6Destination(
                        new Ipv6Prefix(aap.getIpAddress().getIpAddress().getIpv6Address().getValue() + "/128"));
                flowMatches.add(dstMatch);
            }
        } else if (aap.getIpAddress().getIpPrefix() != null) {
            if (aap.getIpAddress().getIpPrefix().getIpv4Prefix() != null) {
                MatchEthernetType ipv4EthMatch = new MatchEthernetType(NwConstants.ETHTYPE_IPV4);
                flowMatches.add(ipv4EthMatch);
                MatchIpv4Destination dstMatch =
                        new MatchIpv4Destination(aap.getIpAddress().getIpPrefix().getIpv4Prefix());
                flowMatches.add(dstMatch);
            } else if (aap.getIpAddress().getIpPrefix().getIpv6Prefix() != null) {
                MatchEthernetType ipv6EthMatch = new MatchEthernetType(NwConstants.ETHTYPE_IPV6);
                flowMatches.add(ipv6EthMatch);
                MatchIpv6Destination dstMatch =
                        new MatchIpv6Destination(aap.getIpAddress().getIpPrefix().getIpv6Prefix());
                flowMatches.add(dstMatch);
            }
        }
        return flowMatches;
    }

    public static boolean isOfAclInterest(Acl acl) {
        if (acl.getAccessListEntries() != null) {
            List<Ace> aceList = acl.getAccessListEntries().getAce();
            if (aceList != null && !aceList.isEmpty()) {
                return aceList.get(0).getAugmentation(SecurityRuleAttr.class) != null;
            }
        }
        return false;
    }

    public static void addLportTagMetadataMatch(int lportTag, List<MatchInfoBase> flowMatches,
            Class<? extends ServiceModeBase> serviceMode) {
        MatchInfoBase lportMatch = buildLPortTagMatch(lportTag, serviceMode);
        InterfaceServiceUtil.mergeMetadataMatchsOrAdd(flowMatches, lportMatch);
    }

    /**
     * Returns ACL specific key for synchronization.
     *
     * @param key the generic key
     * @return ACL key that can be used with synchronization
     */
    public static String getAclKeyForSynchronization(String key) {
        return key + AclConstants.ACL_SYNC_KEY_EXT;
    }

    /**
     * Builds the ip protocol matches.
     *
     * @param etherType the ether type
     * @param protocol the protocol
     * @return the list of matches.
     */
    public static List<MatchInfoBase> buildIpProtocolMatches(MatchEthernetType etherType, IPProtocols protocol) {
        return Lists.newArrayList(etherType, new MatchIpProtocol(protocol.shortValue()));
    }

    /**
     * Does ACE have remote group id.
     *
     * @param aceAttr the ace attr
     * @return true, if successful
     */
    public static boolean doesAceHaveRemoteGroupId(final SecurityRuleAttr aceAttr) {
        return aceAttr != null && aceAttr.getRemoteGroupId() != null;
    }

    public SortedSet<Integer> getRemoteAclTags(List<Uuid> aclIds, Class<? extends DirectionBase> direction) {
        SortedSet<Integer> remoteAclTags = new TreeSet<>();
        Set<Uuid> remoteAclIds = getRemoteAclIdsByDirection(aclIds, direction);
        for (Uuid remoteAclId : remoteAclIds) {
            Integer remoteAclTag = getAclTag(remoteAclId);
            if (remoteAclTag != null && remoteAclTag != AclConstants.INVALID_ACL_TAG) {
                remoteAclTags.add(remoteAclTag);
            }
        }
        return remoteAclTags;
    }

    public Set<Uuid> getRemoteAclIdsByDirection(List<Uuid> aclIds, Class<? extends DirectionBase> direction) {
        Set<Uuid> remoteAclIds = new HashSet<>();
        if (aclIds == null || aclIds.isEmpty()) {
            return remoteAclIds;
        }

        for (Uuid aclId : aclIds) {
            Acl acl = this.aclDataUtil.getAcl(aclId.getValue());
            if (null == acl) {
                LOG.warn("ACL {} not found in cache.", aclId.getValue());
                continue;
            }
            remoteAclIds.addAll(getRemoteAclIdsByDirection(acl, direction));
        }
        return remoteAclIds;
    }

    public static Set<Uuid> getRemoteAclIdsByDirection(Acl acl, Class<? extends DirectionBase> direction) {
        Set<Uuid> remoteAclIds = new HashSet<>();
        AccessListEntries accessListEntries = acl.getAccessListEntries();
        if (accessListEntries != null && accessListEntries.getAce() != null) {
            for (Ace ace : accessListEntries.getAce()) {
                SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
                if (aceAttr.getDirection().equals(direction) && doesAceHaveRemoteGroupId(aceAttr)) {
                    remoteAclIds.add(aceAttr.getRemoteGroupId());
                }
            }
        }
        return remoteAclIds;
    }

    /**
     * Skip delete in case of overlapping IP.
     *
     * <p>
     * When there are multiple ports (e.g., p1, p2, p3) having same AAP (e.g.,
     * 224.0.0.5) configured which are part of single SG, there would be single
     * flow in remote ACL table. When one of these ports (say p1) is deleted,
     * the single flow which is configured in remote ACL table shouldn't be
     * deleted. It should be deleted only when there are no more references to
     * it.
     *
     * @param portId the port id
     * @param remoteAclId the remote Acl Id
     * @param ipPrefix the ip prefix
     * @param addOrRemove the add or remove
     * @return true, if successful
     */
    public boolean skipDeleteInCaseOfOverlappingIP(String portId, Uuid remoteAclId, IpPrefixOrAddress ipPrefix,
            int addOrRemove) {
        boolean skipDelete = false;
        if (addOrRemove != NwConstants.DEL_FLOW) {
            return skipDelete;
        }
        AclIpPrefixes aclIpPrefixes = getAclIpPrefixesFromOperDs(remoteAclId.getValue(), ipPrefix);
        if (aclIpPrefixes != null && aclIpPrefixes.getPortIds() != null) {
            List<String> ignorePorts = Lists.newArrayList(portId);
            List<PortIds> portIds = new ArrayList<>(aclIpPrefixes.getPortIds());
            // Checking if there are any other ports excluding ignorePorts
            long noOfRemotePorts =
                    portIds.stream().map(x -> x.getPortId()).filter(y -> !ignorePorts.contains(y)).count();
            if (noOfRemotePorts > 0) {
                skipDelete = true;
            }
        }
        return skipDelete;
    }

    public static void deleteAclPortsLookupEntry(String aclName, DataBroker broker)
            throws TransactionCommitFailedException {
        SingleTransactionDataBroker.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, aclPortsByIpPath(aclName));
    }

    public static InstanceIdentifier<AclPortsByIp> aclPortsByIpPath(String aclName) {
        return InstanceIdentifier.builder(AclPortsLookup.class)
                .child(AclPortsByIp.class, new AclPortsByIpKey(aclName)).build();
    }

    public static InstanceIdentifier<AclIpPrefixes> getAclIpPrefixesPath(String aclName, IpPrefixOrAddress ipPrefix) {
        return InstanceIdentifier.builder(AclPortsLookup.class).child(AclPortsByIp.class, new AclPortsByIpKey(aclName))
                .child(AclIpPrefixes.class, new AclIpPrefixesKey(ipPrefix)).build();
    }

    public static InstanceIdentifier<PortIds> getPortIdsPathInAclPortsLookup(String ruleName,
            IpPrefixOrAddress ipPrefix, String portId) {
        return InstanceIdentifier.builder(AclPortsLookup.class).child(AclPortsByIp.class, new AclPortsByIpKey(ruleName))
                .child(AclIpPrefixes.class, new AclIpPrefixesKey(ipPrefix)).child(PortIds.class, new PortIdsKey(portId))
                .build();
    }

    public void addAclPortsLookupForInterfaceUpdate(AclInterface portBefore, AclInterface portAfter) {
        LOG.debug("Processing interface additions for port {}", portAfter.getInterfaceId());
        List<AllowedAddressPairs> addedAllowedAddressPairs = getUpdatedAllowedAddressPairs(
                portAfter.getAllowedAddressPairs(), portBefore.getAllowedAddressPairs());
        if (addedAllowedAddressPairs != null && !addedAllowedAddressPairs.isEmpty()) {
            addAclPortsLookup(portAfter, portAfter.getSecurityGroups(), addedAllowedAddressPairs);
        }

        List<Uuid> addedAcls = getUpdatedAclList(portAfter.getSecurityGroups(), portBefore.getSecurityGroups());
        if (addedAcls != null && !addedAcls.isEmpty()) {
            addAclPortsLookup(portAfter, addedAcls, portAfter.getAllowedAddressPairs());
        }
    }

    public void deleteAclPortsLookupForInterfaceUpdate(AclInterface portBefore, AclInterface portAfter) {
        LOG.debug("Processing interface removals for port {}", portAfter.getInterfaceId());
        List<AllowedAddressPairs> deletedAllowedAddressPairs = getUpdatedAllowedAddressPairs(
                portBefore.getAllowedAddressPairs(), portAfter.getAllowedAddressPairs());
        if (deletedAllowedAddressPairs != null && !deletedAllowedAddressPairs.isEmpty()) {
            deleteAclPortsLookup(portAfter, portAfter.getSecurityGroups(), deletedAllowedAddressPairs);
        }

        List<Uuid> deletedAcls = getUpdatedAclList(portBefore.getSecurityGroups(), portAfter.getSecurityGroups());
        if (deletedAcls != null && !deletedAcls.isEmpty()) {
            deleteAclPortsLookup(portAfter, deletedAcls, portAfter.getAllowedAddressPairs());
        }
    }

    public void addAclPortsLookup(AclInterface port, List<Uuid> aclList,
            List<AllowedAddressPairs> allowedAddresses) {
        String portId = port.getInterfaceId();
        LOG.trace("Adding AclPortsLookup for port={}, acls={}, AAPs={}", portId, aclList, allowedAddresses);

        if (aclList == null || allowedAddresses == null || allowedAddresses.isEmpty()) {
            LOG.warn("aclList or allowedAddresses is null. port={}, acls={}, AAPs={}", portId, aclList,
                    allowedAddresses);
            return;
        }

        for (Uuid aclId : aclList) {
            String aclName = aclId.getValue();
            jobCoordinator.enqueueJob(aclName.intern(), () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    for (AllowedAddressPairs aap : allowedAddresses) {
                        PortIds portIdObj =
                                new PortIdsBuilder().setKey(new PortIdsKey(portId)).setPortId(portId).build();
                        InstanceIdentifier<PortIds> path =
                                AclServiceUtils.getPortIdsPathInAclPortsLookup(aclName, aap.getIpAddress(), portId);
                        tx.put(LogicalDatastoreType.OPERATIONAL, path, portIdObj,
                                WriteTransaction.CREATE_MISSING_PARENTS);
                    }
                }));
                return futures;
            });
        }
    }

    public void deleteAclPortsLookup(AclInterface port, List<Uuid> aclList,
            List<AllowedAddressPairs> allowedAddresses) {
        String portId = port.getInterfaceId();
        LOG.trace("Deleting AclPortsLookup for port={}, acls={}, AAPs={}", portId, aclList, allowedAddresses);

        if (aclList == null || allowedAddresses == null || allowedAddresses.isEmpty()) {
            LOG.warn("aclList or allowedAddresses is null. port={}, acls={}, AAPs={}", portId, aclList,
                    allowedAddresses);
            return;
        }

        for (Uuid aclId : aclList) {
            String aclName = aclId.getValue();
            jobCoordinator.enqueueJob(aclName.intern(), () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    for (AllowedAddressPairs aap : allowedAddresses) {
                        InstanceIdentifier<PortIds> path =
                                AclServiceUtils.getPortIdsPathInAclPortsLookup(aclName, aap.getIpAddress(), portId);
                        tx.delete(LogicalDatastoreType.OPERATIONAL, path);
                    }

                    cleanUpStaleEntriesInAclPortsLookup(aclName, tx);
                }));
                return futures;
            });
        }
    }

    private void cleanUpStaleEntriesInAclPortsLookup(String aclName, WriteTransaction tx) {
        AclPortsByIp aclPortsByIp = getAclPortsByIpFromOperDs(aclName);
        if (aclPortsByIp == null) {
            return;
        }
        boolean deleteEntireAcl;
        List<AclIpPrefixes> ipPrefixes = aclPortsByIp.getAclIpPrefixes();
        if (ipPrefixes == null || ipPrefixes.isEmpty()) {
            deleteEntireAcl = true;
        } else {
            boolean deleteMap = true;
            for (AclIpPrefixes ipPrefix : ipPrefixes) {
                if (ipPrefix.getPortIds() != null && !ipPrefix.getPortIds().isEmpty()) {
                    deleteMap = false;
                    break;
                }
            }
            deleteEntireAcl = deleteMap;
        }
        if (deleteEntireAcl) {
            tx.delete(LogicalDatastoreType.OPERATIONAL, AclServiceUtils.aclPortsByIpPath(aclName));
        } else {
            for (AclIpPrefixes ipPrefix : ipPrefixes) {
                if (ipPrefix.getPortIds() == null || ipPrefix.getPortIds().isEmpty()) {
                    InstanceIdentifier<AclIpPrefixes> delPath =
                            AclServiceUtils.getAclIpPrefixesPath(aclName, ipPrefix.getIpPrefix());
                    tx.delete(LogicalDatastoreType.OPERATIONAL, delPath);
                }
            }
        }
    }

    @Nullable
    private AclPortsByIp getAclPortsByIpFromOperDs(String aclName) {
        InstanceIdentifier<AclPortsByIp> path = aclPortsByIpPath(aclName);
        try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(LogicalDatastoreType.OPERATIONAL, path).checkedGet().orNull();
        } catch (ReadFailedException e) {
            LOG.error("Failed to read ACL ports {}", path, e);
            return null;
        }
    }

    @Nullable
    private AclIpPrefixes getAclIpPrefixesFromOperDs(String aclName, IpPrefixOrAddress ipPrefix) {
        InstanceIdentifier<AclIpPrefixes> path = getAclIpPrefixesPath(aclName, ipPrefix);
        try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(LogicalDatastoreType.OPERATIONAL, path).checkedGet().orNull();
        } catch (ReadFailedException e) {
            LOG.error("Failed to read ACL IP prefixes {}", path, e);
            return null;
        }
    }

    /**
     * Gets the ace flow priority.
     *
     * @param aclName the acl name
     * @return the ace flow priority
     */
    public Integer getAceFlowPriority(String aclName) {
        Integer priority = AclConstants.ACE_DEFAULT_PRIORITY;
        Integer aclTag = getAclTag(new Uuid(aclName));
        if (aclTag != null && aclTag != AclConstants.INVALID_ACL_TAG) {
            // To handle overlapping rules, aclTag is added to priority
            priority += aclTag;
        } else {
            LOG.warn("aclTag=[] is null or invalid for aclName={}", aclTag, aclName);
        }
        return priority;
    }

    /**
     * Returns the hard timeout based on the protocol when a ACL rule removed from the instance.
     * It will returns the timeout configured in the {@link AclserviceConfig} class.
     *
     * @param ace the ace
     * @param aclServiceUtils acl service utils
     * @return the hard time out
     */
    public static Integer getHardTimoutForApplyStatefulChangeOnExistingTraffic(Ace ace,
            AclServiceUtils aclServiceUtils) {
        int hardTimeout = AclConstants.SECURITY_GROUP_ICMP_IDLE_TIME_OUT;
        Matches matches = ace.getMatches();
        AceIp acl = (AceIp) matches.getAceType();
        Short protocol = acl.getProtocol();
        if (protocol == null) {
            return hardTimeout;
        } else if (protocol == NwConstants.IP_PROT_TCP) {
            hardTimeout = aclServiceUtils.getConfig().getSecurityGroupTcpIdleTimeout();
        } else if (protocol == NwConstants.IP_PROT_UDP) {
            hardTimeout = aclServiceUtils.getConfig().getSecurityGroupUdpIdleTimeout();
        }
        return hardTimeout;
    }

    /**
     * This method creates and returns the ct_mark instruction when a ACL rule removed from the
     * instance. This instruction will reset the ct_mark value and stops the existing traffics.
     *
     * @param filterTable the filterTable
     * @param elanId the Elan id
     * @return list of instruction
     */
    public static List<InstructionInfo> createCtMarkInstructionForNewState(Short filterTable, Long elanId) {

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<NxCtAction> ctActionsList = new ArrayList<>();
        NxCtAction nxCtMarkClearAction = new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_NEW_STATE);
        ctActionsList.add(nxCtMarkClearAction);

        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(2, 1, 0, elanId.intValue(),
            (short) 255, ctActionsList);
        actionsInfos.add(actionNxConntrack);
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(filterTable));

        return instructions;
    }

    public static List<AllowedAddressPairs> excludeMulticastAAPs(List<AllowedAddressPairs> allowedAddresses) {
        List<AllowedAddressPairs> filteredAAPs = new ArrayList<>();
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            InetAddress inetAddr = getInetAddress(allowedAddress.getIpAddress());
            if (inetAddr != null && !inetAddr.isMulticastAddress()) {
                filteredAAPs.add(allowedAddress);
            }
        }
        return filteredAAPs;
    }

    public static String getRecoverServiceRegistryKey() {
        return NetvirtAcl.class.toString();
    }

    private static InetAddress getInetAddress(IpPrefixOrAddress ipPrefixOrAddress) {
        InetAddress inetAddress = null;
        String addr = null;

        IpPrefix ipPrefix = ipPrefixOrAddress.getIpPrefix();
        if (ipPrefix != null) {
            addr = String.valueOf(ipPrefix.getValue()).split("/")[0];
        } else {
            IpAddress ipAddress = ipPrefixOrAddress.getIpAddress();
            if (ipAddress == null) {
                LOG.error("Invalid address : {}", ipPrefixOrAddress);
                return null;
            } else {
                addr = String.valueOf(ipAddress.getValue());
            }
        }
        try {
            inetAddress = InetAddress.getByName(addr);
        } catch (UnknownHostException e) {
            LOG.error("Invalid address : {}", addr, e.getMessage());
            return null;
        }
        return inetAddress;
    }

    public static Boolean isIpv6Subnet(List<SubnetInfo> subnetInfoList) {
        if (subnetInfoList != null && !subnetInfoList.isEmpty()) {
            for (SubnetInfo subnetInfo : subnetInfoList) {
                if (subnetInfo != null && IpVersionV6.class.equals(subnetInfo.getIpVersion())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the subnet difference by performing (subnetInfo1 - subnetInfo2).
     *
     * @param subnetInfo1 the subnet info 1
     * @param subnetInfo2 the subnet info 2
     * @return the subnet diff
     */
    public static List<SubnetInfo> getSubnetDiff(List<SubnetInfo> subnetInfo1, List<SubnetInfo> subnetInfo2) {
        if (subnetInfo1 == null) {
            return Collections.emptyList();
        }
        List<SubnetInfo> newSubnetList = new ArrayList<>(subnetInfo1);
        if (subnetInfo2 == null) {
            return newSubnetList;
        }
        newSubnetList.removeAll(subnetInfo2);
        return newSubnetList;
    }
}

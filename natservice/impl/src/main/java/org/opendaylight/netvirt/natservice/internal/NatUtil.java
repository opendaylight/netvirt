/*
 * Copyright © 2016, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static java.util.Collections.emptyList;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.apache.commons.net.util.SubnetUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.sal.common.util.Arguments;
import org.opendaylight.genius.datastoreutils.ExpectedDataObjectNotFoundException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.TypedReadTransaction;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionOutput;
import org.opendaylight.genius.mdsalutil.actions.ActionPushVlan;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldVlanVid;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.natservice.ha.NatDataUtil;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.serviceutils.upgrade.UpgradeState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefixBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.DpnRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PrefixToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnIdToVpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInterfaceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.DpnRoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.DpnRoutersListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.DpnRoutersListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.dpn.routers.list.RoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.dpn.routers.list.RoutersListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.dpn.routers.list.RoutersListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalIpsCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpPortInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.IntextIpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.IntextIpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProtocolTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.RouterIdName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.RouterToVpnMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.SnatintIpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.ExternalCounters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.ExternalCountersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.external.counters.ExternalIpCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolTypeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.id.name.RouterIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.id.name.RouterIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.id.name.RouterIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.to.vpn.mapping.Routermapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.to.vpn.mapping.RoutermappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.IntipPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.IntipPortMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.IpPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.IpPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.ip.port.IntIpProtoType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.ip.port.IntIpProtoTypeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.InterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.add.group.input.buckets.bucket.action.action.NxActionResubmitRpcAddGroupCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NatUtil {

    private static String OF_URI_SEPARATOR = ":";
    private static final Logger LOG = LoggerFactory.getLogger(NatUtil.class);
    private static final String OTHER_CONFIG_PARAMETERS_DELIMITER = ",";
    private static final String OTHER_CONFIG_KEY_VALUE_DELIMITER = ":";
    private static final String PROVIDER_MAPPINGS = "provider_mappings";
    private static final String FIB_EVENT_SOURCE_NAT_STATE = "natEvent";

    private NatUtil() {

    }

    /*
     getCookieSnatFlow() computes and returns a unique cookie value for the NAT flows using the router ID as the
      reference value.
     */
    public static Uint64 getCookieSnatFlow(long routerId) {
        return Uint64.valueOf(NatConstants.COOKIE_NAPT_BASE.toJava().add(new BigInteger("0110000", 16)).add(
            BigInteger.valueOf(routerId)));
    }

    /*
      getCookieNaptFlow() computes and returns a unique cookie value for the NAPT flows using the router ID as the
       reference value.
    */
    public static Uint64 getCookieNaptFlow(Uint32 routerId) {
        return Uint64.valueOf(NatConstants.COOKIE_NAPT_BASE.toJava().add(new BigInteger("0111000", 16)).add(
            BigInteger.valueOf(routerId.longValue())));
    }

    /*
        getVpnId() returns the VPN ID from the VPN name
     */
    public static Uint32 getVpnId(DataBroker broker, @Nullable String vpnName) {
        if (vpnName == null) {
            return NatConstants.INVALID_ID;
        }

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
            .instance.to.vpn.id.VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                .instance.to.vpn.id.VpnInstance> vpnInstance =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, id);

        Uint32 vpnId = NatConstants.INVALID_ID;
        if (vpnInstance.isPresent()) {
            Uint32 vpnIdAsLong = vpnInstance.get().getVpnId();
            if (vpnIdAsLong != null) {
                vpnId = vpnIdAsLong;
            }
        }
        return vpnId;
    }

    public static Uint32 getVpnId(TypedReadTransaction<Configuration> confTx, String vpnName) {
        if (vpnName == null) {
            return NatConstants.INVALID_ID;
        }

        try {
            return confTx.read(getVpnInstanceToVpnIdIdentifier(vpnName)).get().map(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                    .VpnInstance::getVpnId).orElse(NatConstants.INVALID_ID);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error retrieving VPN id for {}", vpnName, e);
        }

        return NatConstants.INVALID_ID;
    }

    public static Uint32 getNetworkVpnIdFromRouterId(DataBroker broker, Uint32 routerId) {
        //Get the external network ID from the ExternalRouter model
        Uuid networkId = NatUtil.getNetworkIdFromRouterId(broker, routerId);
        if (networkId == null) {
            LOG.error("getNetworkVpnIdFromRouterId : networkId is null");
            return NatConstants.INVALID_ID;
        }

        //Get the VPN ID from the ExternalNetworks model
        Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(broker, networkId);
        if (vpnUuid == null) {
            LOG.error("getNetworkVpnIdFromRouterId : vpnUuid is null");
            return NatConstants.INVALID_ID;
        }
        Uint32 vpnId = NatUtil.getVpnId(broker, vpnUuid.getValue());
        return vpnId;
    }

    public static Boolean validateIsIntefacePartofRouter(DataBroker broker, String routerName, String interfaceName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces
            .map.router.interfaces.Interfaces> vmInterfaceIdentifier = getRoutersInterfacesIdentifier(routerName,
            interfaceName);

        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces
            .map.router.interfaces.Interfaces> routerInterfacesData;
        try {
            routerInterfacesData = SingleTransactionDataBroker.syncReadOptional(broker,
                LogicalDatastoreType.CONFIGURATION, vmInterfaceIdentifier);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Read Failed Exception While read RouterInterface data for router {}", routerName, e);
            routerInterfacesData = Optional.empty();
        }
        if (routerInterfacesData.isPresent()) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    private static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn
        .rev150602.router.interfaces
        .map.router.interfaces.Interfaces> getRoutersInterfacesIdentifier(String routerName, String interfaceName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn
            .rev150602.RouterInterfacesMap.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router
                    .interfaces.map.RouterInterfaces.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router
                    .interfaces.map.RouterInterfacesKey(new Uuid(routerName)))
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces
                    .map.router.interfaces.Interfaces.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces
                    .map.router.interfaces.InterfacesKey(interfaceName)).build();
    }

    private static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn
        .rev150602.router.interfaces.map.RouterInterfaces> getRoutersInterfacesIdentifier(String routerName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn
            .rev150602.RouterInterfacesMap.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router
                    .interfaces.map.RouterInterfaces.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router
                    .interfaces.map.RouterInterfacesKey(new Uuid(routerName))).build();
    }

    static InstanceIdentifier<RouterPorts> getRouterPortsId(String routerId) {
        return InstanceIdentifier.builder(FloatingIpInfo.class)
            .child(RouterPorts.class, new RouterPortsKey(routerId)).build();
    }

    static InstanceIdentifier<Routermapping> getRouterVpnMappingId(String routerId) {
        return InstanceIdentifier.builder(RouterToVpnMapping.class)
            .child(Routermapping.class, new RoutermappingKey(routerId)).build();
    }

    static InstanceIdentifier<Ports> getPortsIdentifier(String routerId, String portName) {
        return InstanceIdentifier.builder(FloatingIpInfo.class).child(RouterPorts.class, new RouterPortsKey(routerId))
            .child(Ports.class, new PortsKey(portName)).build();
    }

    static InstanceIdentifier<InternalToExternalPortMap> getIntExtPortMapIdentifier(String routerId, String portName,
                                                                                    String internalIp) {
        return InstanceIdentifier.builder(FloatingIpInfo.class).child(RouterPorts.class, new RouterPortsKey(routerId))
            .child(Ports.class, new PortsKey(portName))
            .child(InternalToExternalPortMap.class, new InternalToExternalPortMapKey(internalIp)).build();
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
        .instance.to.vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                    .instance.to.vpn.id.VpnInstance.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                    .instance.to.vpn.id.VpnInstanceKey(vpnName)).build();
    }

    @Nullable
    static String getVpnInstanceFromVpnIdentifier(DataBroker broker, Uint32 vpnId) {
        InstanceIdentifier<VpnIds> id = InstanceIdentifier.builder(VpnIdToVpnInstance.class)
            .child(VpnIds.class, new VpnIdsKey(vpnId)).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(VpnIds::getVpnInstanceName).orElse(null);
    }

    /*
       getFlowRef() returns a string identfier for the SNAT flows using the router ID as the reference.
    */
    public static String getFlowRef(Uint64 dpnId, short tableId, Uint32 routerID, String ip) {
        return new StringBuilder().append(NatConstants.NAPT_FLOWID_PREFIX).append(dpnId)
            .append(NatConstants.FLOWID_SEPARATOR).append(tableId).append(NatConstants.FLOWID_SEPARATOR)
            .append(routerID).append(NatConstants.FLOWID_SEPARATOR).append(ip).toString();
    }

    public static String getFlowRef(Uint64 dpnId, short tableId, InetAddress destPrefix, Uint32 vpnId) {
        return new StringBuilder().append(NatConstants.NAPT_FLOWID_PREFIX).append(dpnId)
            .append(NatConstants.FLOWID_SEPARATOR).append(tableId).append(NatConstants.FLOWID_SEPARATOR)
            .append(destPrefix.getHostAddress()).append(NatConstants.FLOWID_SEPARATOR).append(vpnId).toString();
    }

    public static String getNaptFlowRef(Uint64 dpnId, short tableId, String routerID, String ip,
        int port, String protocol) {
        return new StringBuilder().append(NatConstants.NAPT_FLOWID_PREFIX).append(dpnId)
            .append(NatConstants.FLOWID_SEPARATOR).append(tableId).append(NatConstants.FLOWID_SEPARATOR)
            .append(routerID).append(NatConstants.FLOWID_SEPARATOR).append(ip).append(NatConstants.FLOWID_SEPARATOR)
            .append(port).append(NatConstants.FLOWID_SEPARATOR).append(protocol).toString();
    }

    @Nullable
    static Uuid getNetworkIdFromRouterId(DataBroker broker, Uint32 routerId) {
        String routerName = getRouterName(broker, routerId);
        if (routerName == null) {
            LOG.error("getNetworkIdFromRouterId - empty routerName received");
            return null;
        }
        return getNetworkIdFromRouterName(broker, routerName);
    }

    @Nullable
    static Uuid getNetworkIdFromRouterName(DataBroker broker, String routerName) {
        if (routerName == null) {
            LOG.error("getNetworkIdFromRouterName - empty routerName received");
            return null;
        }
        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(Routers::getNetworkId).orElse(null);
    }

    static InstanceIdentifier<Routers> buildRouterIdentifier(String routerId) {
        InstanceIdentifier<Routers> routerInstanceIndentifier = InstanceIdentifier.builder(ExtRouters.class)
            .child(Routers.class, new RoutersKey(routerId)).build();
        return routerInstanceIndentifier;
    }

    private static InstanceIdentifier<RouterIds> buildRouterIdentifier(Uint32 routerId) {
        InstanceIdentifier<RouterIds> routerIds = InstanceIdentifier.builder(RouterIdName.class)
            .child(RouterIds.class, new RouterIdsKey(routerId)).build();
        return routerIds;
    }

    /**
     * Return if SNAT is enabled for the given router.
     *
     * @param broker   The DataBroker
     * @param routerId The router
     * @return boolean true if enabled, otherwise false
     */
    static boolean isSnatEnabledForRouterId(DataBroker broker, String routerId) {
        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(Routers::isEnableSnat).orElse(false);
    }

    @Nullable
    public static Uuid getVpnIdfromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(Networks::getVpnid).orElse(null);
    }

    @Nullable
    public static Uuid getVpnIdfromNetworkId(TypedReadTransaction<Configuration> tx, Uuid networkId) {
        try {
            return tx.read(buildNetworkIdentifier(networkId)).get().map(Networks::getVpnid).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading network VPN id for {}", networkId, e);
            return null;
        }
    }

    @Nullable
    public static ProviderTypes getProviderTypefromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(Networks::getProviderNetworkType).orElse(null);
    }

    @Nullable
    public static ProviderTypes getProviderTypefromNetworkId(TypedReadTransaction<Configuration> tx, Uuid networkId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        try {
            return tx.read(id).get().map(Networks::getProviderNetworkType).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error retrieving provider type for {}", networkId, e);
            return null;
        }
    }

    @Nullable
    static String getAssociatedExternalNetwork(DataBroker dataBroker, String routerId) {
        InstanceIdentifier<Routers> id = NatUtil.buildRouterIdentifier(routerId);
        Optional<Routers> routerData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, id);
        if (routerData.isPresent()) {
            Uuid networkId = routerData.get().getNetworkId();
            if (networkId != null) {
                return networkId.getValue();
            }
        }
        LOG.info("getAssociatedExternalNetwork : External Network missing for routerid : {}", routerId);
        return null;
    }

    private static InstanceIdentifier<Networks> buildNetworkIdentifier(Uuid networkId) {
        return InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(networkId)).build();
    }

    @Nullable
    public static Uint64 getPrimaryNaptfromRouterId(DataBroker broker, Uint32 routerId) {
        // convert routerId to Name
        String routerName = getRouterName(broker, routerId);
        if (routerName == null) {
            LOG.error("getPrimaryNaptfromRouterId - empty routerName received");
            return null;
        }
        return getPrimaryNaptfromRouterName(broker, routerName);
    }

    @Nullable
    public static Uint64 getPrimaryNaptfromRouterName(DataBroker broker, String routerName) {
        if (routerName == null) {
            LOG.error("getPrimaryNaptfromRouterName - empty routerName received");
            return null;
        }
        InstanceIdentifier<RouterToNaptSwitch> id = buildNaptSwitchIdentifier(routerName);
        return (SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(RouterToNaptSwitch::getPrimarySwitchId).orElse(
                Uint64.valueOf(0L)));
    }

    public static InstanceIdentifier<RouterToNaptSwitch> buildNaptSwitchIdentifier(String routerId) {
        return InstanceIdentifier.builder(NaptSwitches.class).child(RouterToNaptSwitch.class,
            new RouterToNaptSwitchKey(routerId)).build();
    }

    public static Optional<NaptSwitches> getAllPrimaryNaptSwitches(DataBroker broker) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, getNaptSwitchesIdentifier());
    }

    @Nullable
    public static String getRouterName(DataBroker broker, Uint32 routerId) {
        return getVpnInstanceFromVpnIdentifier(broker, routerId);
    }

    static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String vrfId) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(vrfId)).build();
    }

    public static FlowEntity buildFlowEntity(Uint64 dpnId, short tableId, Uint64 cookie, String flowId) {
        return new FlowEntityBuilder()
                .setDpnId(dpnId)
                .setTableId(tableId)
                .setCookie(cookie)
                .setFlowId(flowId)
                .build();
    }

    public static FlowEntity buildFlowEntity(Uint64 dpnId, short tableId, String flowId) {
        return new FlowEntityBuilder()
                .setDpnId(dpnId)
                .setTableId(tableId)
                .setFlowId(flowId)
                .build();
    }

    @Nullable
    public static String getEndpointIpAddressForDPN(DataBroker broker, Uint64 dpnId) {
        String nextHopIp = null;
        InstanceIdentifier<DPNTEPsInfo> tunnelInfoId =
            InstanceIdentifier.builder(DpnEndpoints.class)
                .child(DPNTEPsInfo.class, new DPNTEPsInfoKey(dpnId)).build();
        Optional<DPNTEPsInfo> tunnelInfo =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, tunnelInfoId);
        if (tunnelInfo.isPresent()) {
            List<TunnelEndPoints> nexthopIpList = tunnelInfo.get().getTunnelEndPoints();
            if (nexthopIpList != null && !nexthopIpList.isEmpty()) {
                nextHopIp = nexthopIpList.get(0).getIpAddress().stringValue();
            }
        }
        return nextHopIp;
    }

    @Nullable
    public static String getVpnRd(DataBroker broker, String vpnName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
            .instance.to.vpn.id.VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                        .VpnInstance::getVrfId).orElse(null);
    }

    @Nullable
    public static String getVpnRd(TypedReadTransaction<Configuration> tx, String vpnName) {
        try {
            return tx.read(getVpnInstanceToVpnIdIdentifier(vpnName)).get().map(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                    .VpnInstance::getVrfId).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading the VPN VRF id for {}", vpnName, e);
            return null;
        }
    }

    @Nullable
    public static IpPortExternal getExternalIpPortMap(DataBroker broker, Uint32 routerId, String internalIpAddress,
                                                      String internalPort, NAPTEntryEvent.Protocol protocol) {
        ProtocolTypes protocolType = NatUtil.getProtocolType(protocol);
        InstanceIdentifier<IpPortMap> ipPortMapId =
            buildIpToPortMapIdentifier(routerId, internalIpAddress, internalPort, protocolType);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, ipPortMapId).map(IpPortMap::getIpPortExternal).orElse(
                null);
    }

    private static InstanceIdentifier<IpPortMap> buildIpToPortMapIdentifier(Uint32 routerId, String internalIpAddress,
                                                                            String internalPort,
                                                                            ProtocolTypes protocolType) {
        return InstanceIdentifier.builder(IntextIpPortMap.class)
            .child(IpPortMapping.class, new IpPortMappingKey(routerId))
            .child(IntextIpProtocolType.class, new IntextIpProtocolTypeKey(protocolType))
            .child(IpPortMap.class, new IpPortMapKey(internalIpAddress + ":" + internalPort)).build();
    }

    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
            .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).build();
    }

    @Nullable
    public static String getDpnFromNodeConnectorId(NodeConnectorId portId) {
        /*
         * NodeConnectorId is of form 'openflow:dpnid:portnum'
         */
        String[] split = portId.getValue().split(OF_URI_SEPARATOR);
        if (split.length != 3) {
            LOG.error("getDpnFromNodeConnectorId : invalid portid : {}", portId.getValue());
            return null;
        }
        return split[1];
    }

    public static Uint64 getDpIdFromInterface(
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .state.Interface ifState) {
        String lowerLayerIf = ifState.getLowerLayerIf().get(0);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
        return Uint64.valueOf(getDpnFromNodeConnectorId(nodeConnectorId));
    }


    @Nullable
    public static String getRouterIdfromVpnInstance(DataBroker broker, String vpnName, String ipAddress) {
        // returns only router, attached to IPv4 networks
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
            .child(VpnMap.class, new VpnMapKey(new Uuid(vpnName))).build();
        Optional<VpnMap> optionalVpnMap =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (!optionalVpnMap.isPresent()) {
            LOG.error("getRouterIdfromVpnInstance : Router not found for vpn : {}", vpnName);
            return null;
        }
        List<Uuid> routerIdsList = NeutronUtils.getVpnMapRouterIdsListUuid(
                new ArrayList<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602
                        .vpnmaps.vpnmap.RouterIds>(optionalVpnMap.get().nonnullRouterIds().values()));
        if (routerIdsList != null && !routerIdsList.isEmpty()) {
            for (Uuid routerUuid : routerIdsList) {
                InstanceIdentifier<Routers> id = buildRouterIdentifier(routerUuid.getValue());
                Optional<Routers> routerData =
                        SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                                LogicalDatastoreType.CONFIGURATION, id);
                if (routerData.isPresent()) {
                    List<Uuid> subnetIdsList = routerData.get().getSubnetIds();
                    for (Uuid subnetUuid : subnetIdsList) {
                        String subnetIp = getSubnetIp(broker, subnetUuid);
                        SubnetUtils subnet = new SubnetUtils(subnetIp);
                        if (subnet.getInfo().isInRange(ipAddress)) {
                            return routerUuid.getValue();
                        }
                    }
                }
            }
        }
        LOG.info("getRouterIdfromVpnInstance : Router not found for vpn : {}", vpnName);
        return null;
    }

    @Nullable
    static Uuid getVpnForRouter(DataBroker broker, String routerId) {
        Preconditions.checkNotNull(routerId, "getVpnForRouter: routerId not found!");
        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optionalVpnMaps =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, vpnMapsIdentifier);
        if (optionalVpnMaps.isPresent() && optionalVpnMaps.get().getVpnMap() != null) {
            for (VpnMap vpnMap : optionalVpnMaps.get().nonnullVpnMap().values()) {
                if (routerId.equals(vpnMap.getVpnId().getValue())) {
                    continue;
                }
                List<Uuid> routerIdsList = NeutronUtils.getVpnMapRouterIdsListUuid(
                        new ArrayList<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602
                                .vpnmaps.vpnmap.RouterIds>(vpnMap.nonnullRouterIds().values()));
                if (routerIdsList.isEmpty()) {
                    continue;
                }
                // Skip router vpnId fetching from internet BGP-VPN
                if (vpnMap.getNetworkIds() != null && !vpnMap.getNetworkIds().isEmpty()) {
                    // We only need to check the first network; if it’s not an external network there’s no
                    // need to check the rest of the VPN’s network list
                    if (isExternalNetwork(broker, vpnMap.getNetworkIds().iterator().next())) {
                        continue;
                    }
                }
                if (routerIdsList.contains(new Uuid(routerId))) {
                    return vpnMap.getVpnId();
                }
            }
        }
        LOG.debug("getVpnForRouter : VPN not found for routerID:{}", routerId);
        return null;
    }

    static Uint32 getAssociatedVpn(DataBroker broker, String routerName) {
        InstanceIdentifier<Routermapping> routerMappingId = NatUtil.getRouterVpnMappingId(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.OPERATIONAL, routerMappingId).map(Routermapping::getVpnId).orElse(
                NatConstants.INVALID_ID);
    }

    @Nullable
    public static String getAssociatedVPN(DataBroker dataBroker, Uuid networkId) {
        Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
        if (vpnUuid == null) {
            LOG.error("getAssociatedVPN : No VPN instance associated with ext network {}", networkId);
            return null;
        }
        return vpnUuid.getValue();
    }

    @Nullable
    public static String getAssociatedVPN(TypedReadTransaction<Configuration> tx, Uuid networkId) {
        Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(tx, networkId);
        if (vpnUuid == null) {
            LOG.error("getAssociatedVPN : No VPN instance associated with ext network {}", networkId);
            return null;
        }
        return vpnUuid.getValue();
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void addPrefixToBGP(DataBroker broker,
                                      IBgpManager bgpManager,
                                      IFibManager fibManager,
                                      String vpnName,
                                      String rd,
                                      String prefix,
                                      String nextHopIp,
                                      @Nullable String parentVpnRd,
                                      @Nullable String macAddress,
                                      Uint32 label,
                                      Uint32 l3vni,
                                      RouteOrigin origin,
                                      Uint64 dpId) {
        try {
            LOG.info("addPrefixToBGP : Adding Fib entry rd {} prefix {} nextHop {} label {}", rd,
                    prefix, nextHopIp, label);
            if (nextHopIp == null) {
                LOG.error("addPrefixToBGP : prefix {} rd {} failed since nextHopIp cannot be null.",
                        prefix, rd);
                return;
            }

            addPrefixToInterface(broker, getVpnId(broker, vpnName), null /*interfaceName*/,prefix, parentVpnRd,
                dpId, Prefixes.PrefixCue.Nat);

            fibManager.addOrUpdateFibEntry(rd, macAddress, prefix,
                    Collections.singletonList(nextHopIp), VrfEntry.EncapType.Mplsgre, label, l3vni /*l3vni*/,
                    null /*gatewayMacAddress*/, parentVpnRd, origin, null,
                    FIB_EVENT_SOURCE_NAT_STATE, null /*writeTxn*/);
            if (rd != null && !rd.equalsIgnoreCase(vpnName)) {
            /* Publish to Bgp only if its an INTERNET VPN */
                bgpManager.advertisePrefix(rd, null /*macAddress*/, prefix, Collections.singletonList(nextHopIp),
                        VrfEntry.EncapType.Mplsgre, label, Uint32.ZERO /*l3vni*/, Uint32.ZERO  /*l2vni*/,
                        null /*gatewayMac*/);
            }
            LOG.info("addPrefixToBGP : Added Fib entry rd {} prefix {} nextHop {} label {}", rd,
                    prefix, nextHopIp, label);
        } catch (Exception e) {
            LOG.error("addPrefixToBGP : Add prefix rd {} prefix {} nextHop {} label {} failed", rd,
                    prefix, nextHopIp, label, e);
        }
    }

    static void addPrefixToInterface(DataBroker broker, Uint32 vpnId, @Nullable String interfaceName, String ipPrefix,
                                     String networkId, Uint64 dpId, Prefixes.PrefixCue prefixCue) {
        InstanceIdentifier<Prefixes> prefixId = InstanceIdentifier.builder(PrefixToInterface.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                        .VpnIds.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix
                        .to._interface.VpnIdsKey(vpnId))
                .child(Prefixes.class, new PrefixesKey(ipPrefix)).build();
        PrefixesBuilder prefixBuilder = new PrefixesBuilder().setDpnId(dpId).setIpAddress(ipPrefix);
        prefixBuilder.setVpnInterfaceName(interfaceName).setPrefixCue(prefixCue);
        prefixBuilder.setNetworkId(new Uuid(networkId));
        try {
            SingleTransactionDataBroker.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, prefixId,
                    prefixBuilder.build());
        } catch (TransactionCommitFailedException e) {
            LOG.error("addPrefixToInterface : Failed to write prefxi-to-interface for {} vpn-id {} DPN {}",
                    ipPrefix, vpnId, dpId, e);
        }
    }

    public static void deletePrefixToInterface(DataBroker broker, Uint32 vpnId, String ipPrefix) {
        try {
            SingleTransactionDataBroker.syncDelete(broker, LogicalDatastoreType.OPERATIONAL,
                    InstanceIdentifier.builder(PrefixToInterface.class)
                    .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                            .VpnIds.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911
                            .prefix.to._interface.VpnIdsKey(vpnId)).child(Prefixes.class, new PrefixesKey(ipPrefix))
                    .build());
        } catch (TransactionCommitFailedException e) {
            LOG.error("deletePrefixToInterface : Failed to delete prefxi-to-interface for vpn-id {}",
                    vpnId, e);
        }
    }

    static InstanceIdentifier<Ports> buildPortToIpMapIdentifier(String routerId, String portName) {
        InstanceIdentifier<Ports> ipPortMapId = InstanceIdentifier.builder(FloatingIpInfo.class)
            .child(RouterPorts.class, new RouterPortsKey(routerId)).child(Ports.class, new PortsKey(portName)).build();
        return ipPortMapId;
    }

    static InstanceIdentifier<RouterPorts> buildRouterPortsIdentifier(String routerId) {
        InstanceIdentifier<RouterPorts> routerInstanceIndentifier = InstanceIdentifier.builder(FloatingIpInfo.class)
            .child(RouterPorts.class, new RouterPortsKey(routerId)).build();
        return routerInstanceIndentifier;
    }

    @NonNull
    public static List<Uint16> getInternalIpPortListInfo(DataBroker dataBroker, Uint32 routerId,
                                                          String internalIpAddress, ProtocolTypes protocolType) {
        List<Uint16> portList = SingleTransactionDataBroker
                .syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION,
                        buildSnatIntIpPortIdentifier(routerId, internalIpAddress, protocolType)).map(
                IntIpProtoType::getPorts).orElse(emptyList());

        if (!portList.isEmpty()) {
            portList = new ArrayList<>(portList);
        }
        return portList;
    }

    public static InstanceIdentifier<IntIpProtoType> buildSnatIntIpPortIdentifier(Uint32 routerId,
                                                                                  String internalIpAddress,
                                                                                  ProtocolTypes protocolType) {
        InstanceIdentifier<IntIpProtoType> intIpProtocolTypeId =
            InstanceIdentifier.builder(SnatintIpPortMap.class)
                .child(IntipPortMap.class, new IntipPortMapKey(routerId))
                .child(IpPort.class, new IpPortKey(internalIpAddress))
                .child(IntIpProtoType.class, new IntIpProtoTypeKey(protocolType)).build();
        return intIpProtocolTypeId;
    }

    public static InstanceIdentifier<IpPort> buildSnatIntIpPortIdentifier(Long routerId,
            String internalIpAddress) {
        InstanceIdentifier<IpPort> intIpProtocolTypeId =
            InstanceIdentifier.builder(SnatintIpPortMap.class)
                .child(IntipPortMap.class, new IntipPortMapKey(routerId))
                .child(IpPort.class, new IpPortKey(internalIpAddress)).build();
        return intIpProtocolTypeId;
    }

    @Nullable
    public static IpPort getInternalIpPortInfo(DataBroker dataBroker, Long routerId,
                                                          String internalIpAddress) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION,
                buildSnatIntIpPortIdentifier(routerId, internalIpAddress)).orElse(null);
    }

    public static ProtocolTypes getProtocolType(NAPTEntryEvent.Protocol protocol) {
        ProtocolTypes protocolType = ProtocolTypes.TCP.toString().equals(protocol.toString())
            ? ProtocolTypes.TCP : ProtocolTypes.UDP;
        return protocolType;
    }

    public static InstanceIdentifier<NaptSwitches> getNaptSwitchesIdentifier() {
        return InstanceIdentifier.create(NaptSwitches.class);
    }

    public static InstanceIdentifier<RouterToNaptSwitch> buildNaptSwitchRouterIdentifier(String routerId) {
        return InstanceIdentifier.create(NaptSwitches.class)
            .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerId));
    }

    public static String getGroupIdKey(String routerName) {
        return "snatmiss." + routerName;
    }

    public static Uint32 getUniqueId(IdManagerService idManager, String poolName, String idKey) {

        AllocateIdInput getIdInput = (new AllocateIdInputBuilder()).setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = (RpcResult)result.get();
            return rpcResult.isSuccessful() ? rpcResult.getResult().getIdValue()
                    : NatConstants.INVALID_ID;
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("releaseId: Exception when releasing Id for key {} from pool {}", idKey, poolName, e);
        }
        return NatConstants.INVALID_ID;
    }

    public static Uint32 releaseId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<ReleaseIdOutput>> result = idManager.releaseId(idInput);
            if (result == null || result.get() == null || !result.get().isSuccessful()) {
                LOG.error("releaseId: RPC Call to release Id from pool {} with key {} returned with Errors {}",
                    poolName, idKey,
                    (result != null && result.get() != null) ? result.get().getErrors() : "RpcResult is null");
            } else {
                return Uint32.ONE;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("releaseId: Exception when releasing Id for key {} from pool {}", idKey, poolName, e);
        }
        return NatConstants.INVALID_ID;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void removePrefixFromBGP(IBgpManager bgpManager, IFibManager fibManager,
                                           String rd, String prefix, String vpnName) {
        try {
            LOG.debug("removePrefixFromBGP: Removing Fib entry rd {} prefix {}", rd, prefix);
            fibManager.removeFibEntry(rd, prefix, null, null, null);
            if (rd != null && !rd.equalsIgnoreCase(vpnName)) {
                bgpManager.withdrawPrefix(rd, prefix);
            }
            LOG.info("removePrefixFromBGP: Removed Fib entry rd {} prefix {}", rd, prefix);
        } catch (Exception e) {
            LOG.error("removePrefixFromBGP : Delete prefix for rd {} prefix {} vpnName {} failed",
                    rd, prefix, vpnName, e);
        }
    }

    @Nullable
    public static IpPortMapping getIportMapping(DataBroker broker, Uint32 routerId) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, getIportMappingIdentifier(routerId)).orElse(null);
    }

    public static InstanceIdentifier<IpPortMapping> getIportMappingIdentifier(Uint32 routerId) {
        return InstanceIdentifier.builder(IntextIpPortMap.class)
            .child(IpPortMapping.class, new IpPortMappingKey(routerId)).build();
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt
        .natservice.rev160111.intext.ip.map.IpMapping> getIpMappingBuilder(Uint32 routerId) {
        return InstanceIdentifier.builder(IntextIpMap.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map
                .IpMapping.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
                .intext.ip.map.IpMappingKey(routerId))
            .build();
    }

    @NonNull
    public static Collection<String> getExternalIpsForRouter(DataBroker dataBroker, Uint32 routerId) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext
            .ip.map.IpMapping> ipMappingOptional =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, getIpMappingBuilder(routerId));
        // Ensure there are no duplicates
        Collection<String> externalIps = new HashSet<>();
        if (ipMappingOptional.isPresent()) {
            for (IpMap ipMap : ipMappingOptional.get().nonnullIpMap().values()) {
                externalIps.add(ipMap.getExternalIp());
            }
        }
        return externalIps;
    }

    @NonNull
    public static List<String> getExternalIpsForRouter(DataBroker dataBroker, String routerName) {
        Routers routerData = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
        if (routerData != null) {
            return NatUtil.getIpsListFromExternalIps(
                    new ArrayList<ExternalIps>(routerData.nonnullExternalIps().values()));
        }

        return emptyList();
    }

    @NonNull
    public static Map<String, Uint32> getExternalIpsLabelForRouter(DataBroker dataBroker, Uint32 routerId) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext
            .ip.map.IpMapping> ipMappingOptional =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, getIpMappingBuilder(routerId));
        Map<String, Uint32> externalIpsLabel = new HashMap<>();
        if (ipMappingOptional.isPresent()) {
            for (IpMap ipMap : ipMappingOptional.get().nonnullIpMap().values()) {
                externalIpsLabel.put(ipMap.getExternalIp(), ipMap.getLabel());
            }
        }
        return externalIpsLabel;
    }

    @Nullable
    public static String getLeastLoadedExternalIp(DataBroker dataBroker, Uint32 segmentId) {
        String leastLoadedExternalIp = null;
        InstanceIdentifier<ExternalCounters> id =
            InstanceIdentifier.builder(ExternalIpsCounter.class)
                .child(ExternalCounters.class, new ExternalCountersKey(segmentId)).build();
        Optional<ExternalCounters> externalCountersData;
        try {
            externalCountersData = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getLeastLoadedExternalIp: Exception while reading ExternalCounters DS for the segmentId {}",
                    segmentId, e);
            return leastLoadedExternalIp;
        }
        if (externalCountersData.isPresent()) {
            ExternalCounters externalCounter = externalCountersData.get();
            short countOfLstLoadExtIp = 32767;
            for (ExternalIpCounter externalIpCounter : externalCounter.nonnullExternalIpCounter().values()) {
                String curExternalIp = externalIpCounter.getExternalIp();
                short countOfCurExtIp = externalIpCounter.getCounter().toJava();
                if (countOfCurExtIp < countOfLstLoadExtIp) {
                    countOfLstLoadExtIp = countOfCurExtIp;
                    leastLoadedExternalIp = curExternalIp;
                }
            }
        }
        return leastLoadedExternalIp;
    }

    @SuppressFBWarnings("PZLA_PREFER_ZERO_LENGTH_ARRAYS")
    @Nullable
    public static String[] getSubnetIpAndPrefix(DataBroker dataBroker, Uuid subnetId) {
        String subnetIP = getSubnetIp(dataBroker, subnetId);
        if (subnetIP != null) {
            return getSubnetIpAndPrefix(subnetIP);
        }
        LOG.error("getSubnetIpAndPrefix : SubnetIP and Prefix missing for subnet : {}", subnetId);
        return null;
    }

    @NonNull
    public static String[] getSubnetIpAndPrefix(String subnetString) {
        String[] subnetSplit = subnetString.split("/");
        String subnetIp = subnetSplit[0];
        String subnetPrefix = "0";
        if (subnetSplit.length == 2) {
            subnetPrefix = subnetSplit[1];
        }
        return new String[] {subnetIp, subnetPrefix};
    }

    @Nullable
    public static String getSubnetIp(DataBroker dataBroker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapId = InstanceIdentifier
            .builder(Subnetmaps.class)
            .child(Subnetmap.class, new SubnetmapKey(subnetId))
            .build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, subnetmapId).map(Subnetmap::getSubnetIp).orElse(null);
    }

    public static String[] getExternalIpAndPrefix(String leastLoadedExtIpAddr) {
        String[] leastLoadedExtIpAddrSplit = leastLoadedExtIpAddr.split("/");
        String leastLoadedExtIp = leastLoadedExtIpAddrSplit[0];
        String leastLoadedExtIpPrefix = String.valueOf(NatConstants.DEFAULT_PREFIX);
        if (leastLoadedExtIpAddrSplit.length == 2) {
            leastLoadedExtIpPrefix = leastLoadedExtIpAddrSplit[1];
        }
        return new String[] {leastLoadedExtIp, leastLoadedExtIpPrefix};
    }

    @NonNull
    public static List<Uint64> getDpnsForRouter(DataBroker dataBroker, String routerUuid) {
        InstanceIdentifier id = InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerUuid)).build();
        Optional<RouterDpnList> routerDpnListData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, id);
        List<Uint64> dpns = new ArrayList<>();
        if (routerDpnListData.isPresent()) {
            for (DpnVpninterfacesList dpnVpnInterface
                    : routerDpnListData.get().nonnullDpnVpninterfacesList().values()) {
                dpns.add(dpnVpnInterface.getDpnId());
            }
        }
        return dpns;
    }

    public static Uint32 getBgpVpnId(DataBroker dataBroker, String routerName) {
        Uint32 bgpVpnId = NatConstants.INVALID_ID;
        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
        if (bgpVpnUuid != null) {
            bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
        }
        return bgpVpnId;
    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces
            .@Nullable RouterInterface getConfiguredRouterInterface(DataBroker broker, String interfaceName) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, NatUtil.getRouterInterfaceId(interfaceName)).orElse(null);
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911
        .router.interfaces.RouterInterface> getRouterInterfaceId(String interfaceName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight
            .netvirt.l3vpn.rev130911.RouterInterfaces.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces
                    .RouterInterface.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces
                    .RouterInterfaceKey(interfaceName)).build();
    }

    public static void addToNeutronRouterDpnsMap(String routerName, String interfaceName, Uint64 dpId,
        TypedReadWriteTransaction<Operational> operTx) throws ExecutionException, InterruptedException {

        if (dpId.equals(Uint64.ZERO)) {
            LOG.warn("addToNeutronRouterDpnsMap : Could not retrieve dp id for interface {} "
                    + "to handle router {} association model", interfaceName, routerName);
            return;
        }

        LOG.debug("addToNeutronRouterDpnsMap : Adding the Router {} and DPN {} for the Interface {} in the "
                + "ODL-L3VPN : NeutronRouterDpn map", routerName, dpId, interfaceName);
        InstanceIdentifier<DpnVpninterfacesList> dpnVpnInterfacesListIdentifier = getRouterDpnId(routerName, dpId);

        Optional<DpnVpninterfacesList> optionalDpnVpninterfacesList = operTx.read(dpnVpnInterfacesListIdentifier).get();
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns
            .router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces routerInterface =
            new RouterInterfacesBuilder().withKey(new RouterInterfacesKey(interfaceName))
            .setInterface(interfaceName).build();
        if (optionalDpnVpninterfacesList.isPresent()) {
            LOG.debug("addToNeutronRouterDpnsMap : RouterDpnList already present for the Router {} and DPN {} for the "
                    + "Interface {} in the ODL-L3VPN : NeutronRouterDpn map", routerName, dpId, interfaceName);
            operTx.mergeParentStructureMerge(dpnVpnInterfacesListIdentifier
                    .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router
                            .dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces.class,
                            new RouterInterfacesKey(interfaceName)), routerInterface);
        } else {
            LOG.debug("addToNeutronRouterDpnsMap : Building new RouterDpnList for the Router {} and DPN {} for the "
                    + "Interface {} in the ODL-L3VPN : NeutronRouterDpn map", routerName, dpId, interfaceName);
            RouterDpnListBuilder routerDpnListBuilder = new RouterDpnListBuilder();
            routerDpnListBuilder.setRouterId(routerName);
            DpnVpninterfacesListBuilder dpnVpnList = new DpnVpninterfacesListBuilder().setDpnId(dpId);
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router
                .dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces> routerInterfaces = new ArrayList<>();
            routerInterfaces.add(routerInterface);
            dpnVpnList.setRouterInterfaces(routerInterfaces);
            routerDpnListBuilder.setDpnVpninterfacesList(Collections.singletonList(dpnVpnList.build()));
            operTx.mergeParentStructureMerge(getRouterId(routerName), routerDpnListBuilder.build());
        }
    }

    public static void addToDpnRoutersMap(String routerName, String interfaceName, Uint64 dpId,
        TypedReadWriteTransaction<Operational> operTx) throws ExecutionException, InterruptedException {
        if (dpId.equals(Uint64.ZERO)) {
            LOG.error("addToDpnRoutersMap : Could not retrieve dp id for interface {} to handle router {} "
                    + "association model", interfaceName, routerName);
            return;
        }

        LOG.debug("addToDpnRoutersMap : Adding the DPN {} and router {} for the Interface {} in the ODL-L3VPN : "
                + "DPNRouters map", dpId, routerName, interfaceName);
        InstanceIdentifier<DpnRoutersList> dpnRoutersListIdentifier = getDpnRoutersId(dpId);

        Optional<DpnRoutersList> optionalDpnRoutersList = operTx.read(dpnRoutersListIdentifier).get();

        if (optionalDpnRoutersList.isPresent()) {
            RoutersList routersList = new RoutersListBuilder().withKey(new RoutersListKey(routerName))
                    .setRouter(routerName).build();
            Map<RoutersListKey, RoutersList> keyroutersMapFromDs = optionalDpnRoutersList.get().nonnullRoutersList();
            if (!keyroutersMapFromDs.values().contains(routersList)) {
                LOG.debug("addToDpnRoutersMap : Router {} not present for the DPN {}"
                        + " in the ODL-L3VPN : DPNRouters map", routerName, dpId);
                operTx.mergeParentStructureMerge(dpnRoutersListIdentifier
                        .child(RoutersList.class, new RoutersListKey(routerName)), routersList);
            } else {
                LOG.debug("addToDpnRoutersMap : Router {} already mapped to the DPN {} in the ODL-L3VPN : "
                        + "DPNRouters map", routerName, dpId);
            }
        } else {
            LOG.debug("addToDpnRoutersMap : Building new DPNRoutersList for the Router {} present in the DPN {} "
                    + "ODL-L3VPN : DPNRouters map", routerName, dpId);
            DpnRoutersListBuilder dpnRoutersListBuilder = new DpnRoutersListBuilder();
            dpnRoutersListBuilder.setDpnId(dpId);
            RoutersListBuilder routersListBuilder = new RoutersListBuilder();
            routersListBuilder.setRouter(routerName);
            dpnRoutersListBuilder.setRoutersList(Collections.singletonList(routersListBuilder.build()));
            operTx.mergeParentStructureMerge(getDpnRoutersId(dpId), dpnRoutersListBuilder.build());
        }
    }

    public static void removeFromNeutronRouterDpnsMap(String routerName, Uint64 dpId,
        TypedReadWriteTransaction<Operational> operTx) throws ExecutionException, InterruptedException {
        if (dpId.equals(Uint64.ZERO)) {
            LOG.warn("removeFromNeutronRouterDpnsMap : DPN ID is invalid for the router {} ", routerName);
            return;
        }

        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList = operTx.read(routerDpnListIdentifier).get();
        if (optionalRouterDpnList.isPresent()) {
            LOG.debug("removeFromNeutronRouterDpnsMap : Removing the dpn-vpninterfaces-list from the "
                    + "odl-l3vpn:neutron-router-dpns model for the router {}", routerName);
            operTx.delete(routerDpnListIdentifier);
        } else {
            LOG.debug("removeFromNeutronRouterDpnsMap : dpn-vpninterfaces-list does not exist in the "
                    + "odl-l3vpn:neutron-router-dpns model for the router {}", routerName);
        }
    }

    public static void removeFromNeutronRouterDpnsMap(String routerName, String vpnInterfaceName,
         Uint64 dpId, @NonNull TypedReadWriteTransaction<Operational> operTx) {
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList;
        try {
            optionalRouterDpnList = operTx.read(routerDpnListIdentifier).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading the router DPN list for {}", routerDpnListIdentifier, e);
            optionalRouterDpnList = Optional.empty();
        }
        if (optionalRouterDpnList.isPresent()) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns
                    .router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces> routerInterfaces =
                    new ArrayList<>(optionalRouterDpnList.get().nonnullRouterInterfaces().values());
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn
                    .list.dpn.vpninterfaces.list.RouterInterfaces routerInterface =
                    new RouterInterfacesBuilder().withKey(new RouterInterfacesKey(vpnInterfaceName))
                            .setInterface(vpnInterfaceName).build();

            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    operTx.delete(routerDpnListIdentifier);
                } else {
                    operTx.delete(routerDpnListIdentifier.child(
                            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router
                                    .dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces.class,
                            new RouterInterfacesKey(vpnInterfaceName)));
                }
            }
        }
    }

    public static void removeFromDpnRoutersMap(DataBroker broker, String routerName, String vpnInterfaceName,
        Uint64 curDpnId, OdlInterfaceRpcService ifaceMgrRpcService, TypedReadWriteTransaction<Operational> operTx)
        throws ExecutionException, InterruptedException {
        /*
            1) Get the DpnRoutersList for the DPN.
            2) Get the RoutersList identifier for the DPN and router.
            3) Get the VPN interfaces for the router (routerList) through which it is connected to the DPN.
            4) If the removed VPN interface is the only interface through which the router is connected to the DPN,
             then remove RouterList.
         */

        LOG.debug("removeFromDpnRoutersMap() : Removing the DPN {} and router {} for the Interface {}"
            + " in the ODL-L3VPN : DPNRouters map", curDpnId, routerName, vpnInterfaceName);

        //Get the dpn-routers-list instance for the current DPN.
        InstanceIdentifier<DpnRoutersList> dpnRoutersListIdentifier = getDpnRoutersId(curDpnId);
        Optional<DpnRoutersList> dpnRoutersListData = operTx.read(dpnRoutersListIdentifier).get();

        if (dpnRoutersListData == null || !dpnRoutersListData.isPresent()) {
            LOG.error("removeFromDpnRoutersMap : dpn-routers-list is not present for DPN {} "
                    + "in the ODL-L3VPN:dpn-routers model", curDpnId);
            return;
        }

        //Get the routers-list instance for the router on the current DPN only
        InstanceIdentifier<RoutersList> routersListIdentifier = getRoutersList(curDpnId, routerName);
        Optional<RoutersList> routersListData = operTx.read(routersListIdentifier).get();

        if (routersListData == null || !routersListData.isPresent()) {
            LOG.error("removeFromDpnRoutersMap : routers-list is not present for the DPN {} "
                    + "in the ODL-L3VPN:dpn-routers model",
                curDpnId);
            return;
        }

        LOG.debug("removeFromDpnRoutersMap : Get the interfaces for the router {} "
                + "from the NeutronVPN - router-interfaces-map", routerName);
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router
            .interfaces.map.RouterInterfaces> routerInterfacesId = getRoutersInterfacesIdentifier(routerName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map
                .RouterInterfaces> routerInterfacesData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, routerInterfacesId);

        if (routerInterfacesData == null || !routerInterfacesData.isPresent()) {
            LOG.debug("removeFromDpnRoutersMap : Unable to get the routers list for the DPN {}. Possibly all subnets "
                    + "removed from router {} OR Router {} has been deleted. Hence DPN router model WILL be cleared ",
                curDpnId, routerName, routerName);
            operTx.delete(routersListIdentifier);
            return;
        }

        //Get the VM interfaces for the router on the current DPN only.
        Map<InterfacesKey, Interfaces> vmInterfacesMap
                = routerInterfacesData.get().nonnullInterfaces();
        if (vmInterfacesMap == null) {
            LOG.debug("removeFromDpnRoutersMap : VM interfaces are not present for the router {} in the "
                + "NeutronVPN - router-interfaces-map", routerName);
            return;
        }

        // If the removed VPN interface is the only interface through which the router is connected to the DPN,
        // then remove RouterList.
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map
                 .router.interfaces.Interfaces vmInterface : vmInterfacesMap.values()) {
            String vmInterfaceName = vmInterface.getInterfaceId();
            Uint64 vmDpnId = getDpnForInterface(ifaceMgrRpcService, vmInterfaceName);
            if (vmDpnId.equals(Uint64.ZERO) || !vmDpnId.equals(curDpnId)) {
                LOG.debug("removeFromDpnRoutersMap : DPN ID {} for the removed interface {} is not the same as that of "
                        + "the DPN ID {} for the checked interface {}",
                    curDpnId, vpnInterfaceName, vmDpnId, vmInterfaceName);
                continue;
            }
            if (!vmInterfaceName.equalsIgnoreCase(vpnInterfaceName)) {
                LOG.info("removeFromDpnRoutersMap : Router {} is present in the DPN {} through the other interface {} "
                    + "Hence DPN router model WOULD NOT be cleared", routerName, curDpnId, vmInterfaceName);
                return;
            }
        }
        LOG.debug("removeFromDpnRoutersMap : Router {} is present in the DPN {} only through the interface {} "
            + "Hence DPN router model WILL be cleared. Possibly last VM for the router "
            + "deleted in the DPN", routerName, curDpnId, vpnInterfaceName);
        operTx.delete(routersListIdentifier);
    }

    private static InstanceIdentifier<RoutersList> getRoutersList(Uint64 dpnId, String routerName) {
        return InstanceIdentifier.builder(DpnRouters.class)
            .child(DpnRoutersList.class, new DpnRoutersListKey(dpnId))
            .child(RoutersList.class, new RoutersListKey(routerName)).build();
    }

    public static Uint64 getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        Uint64 nodeId = Uint64.ZERO;
        try {
            GetDpidFromInterfaceInput
                dpIdInput =
                new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>>
                dpIdOutput =
                interfaceManagerRpcService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if (dpIdResult.isSuccessful()) {
                nodeId = dpIdResult.getResult().getDpid();
            } else {
                LOG.debug("getDpnForInterface : Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("getDpnForInterface : Exception when getting dpn for interface {}", ifName, e);
        }
        return nodeId;
    }

    @NonNull
    public static List<ActionInfo> getEgressActionsForInterface(OdlInterfaceRpcService odlInterfaceRpcService,
                                                                ItmRpcService itmRpcService,
                                                                IInterfaceManager interfaceManager, String ifName,
                                                                Uint32 tunnelKey, boolean internalTunnelInterface) {
        return getEgressActionsForInterface(odlInterfaceRpcService, itmRpcService, interfaceManager,
                ifName, tunnelKey, 0, internalTunnelInterface);
    }

    @NonNull
    public static List<ActionInfo> getEgressActionsForInterface(OdlInterfaceRpcService odlInterfaceRpcService,
                                                                ItmRpcService itmRpcService,
                                                                IInterfaceManager interfaceManager,
                                                                String ifName, @Nullable Uint32 tunnelKey, int pos,
                                                                boolean internalTunnelInterface) {
        LOG.debug("getEgressActionsForInterface : called for interface {}", ifName);
        GetEgressActionsForInterfaceInputBuilder egressActionsIfmBuilder =
                new GetEgressActionsForInterfaceInputBuilder().setIntfName(ifName);
        GetEgressActionsForTunnelInputBuilder egressActionsItmBuilder =
                new GetEgressActionsForTunnelInputBuilder().setIntfName(ifName);
        if (tunnelKey != null) {
            egressActionsIfmBuilder.setTunnelKey(tunnelKey);
            egressActionsItmBuilder.setTunnelKey(tunnelKey);
        } //init builders, ITM/IFM rpc can be called based on type of interface

        try {
            List<Action> actions = emptyList();
            if (interfaceManager.isItmDirectTunnelsEnabled() && internalTunnelInterface) {
                RpcResult<GetEgressActionsForTunnelOutput> rpcResult =
                        itmRpcService.getEgressActionsForTunnel(egressActionsItmBuilder.build()).get();
                if (!rpcResult.isSuccessful()) {
                    LOG.error("getEgressActionsForTunnels : RPC Call to Get egress actions for Tunnels {} "
                            + "returned with Errors {}", ifName, rpcResult.getErrors());
                } else {
                    actions = new ArrayList<Action>(rpcResult.getResult().nonnullAction().values());
                }
            } else {
                RpcResult<GetEgressActionsForInterfaceOutput> rpcResult =
                        odlInterfaceRpcService.getEgressActionsForInterface(egressActionsIfmBuilder.build()).get();
                if (!rpcResult.isSuccessful()) {
                    LOG.error("getEgressActionsForInterface : RPC Call to Get egress actions for interface {} "
                            + "returned with Errors {}", ifName, rpcResult.getErrors());
                } else {
                    actions = new ArrayList<Action>(rpcResult.getResult().nonnullAction().values());
                }
            }
            List<ActionInfo> listActionInfo = new ArrayList<>();
            for (Action action : actions) {
                org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action
                    actionClass = action.getAction();
                if (actionClass instanceof OutputActionCase) {
                    listActionInfo.add(new ActionOutput(pos++,
                        ((OutputActionCase) actionClass).getOutputAction().getOutputNodeConnector()));
                } else if (actionClass instanceof PushVlanActionCase) {
                    listActionInfo.add(new ActionPushVlan(pos++));
                } else if (actionClass instanceof SetFieldCase) {
                    if (((SetFieldCase) actionClass).getSetField().getVlanMatch() != null) {
                        int vlanVid = ((SetFieldCase) actionClass).getSetField().getVlanMatch().getVlanId()
                            .getVlanId().getValue().toJava();
                        listActionInfo.add(new ActionSetFieldVlanVid(pos++, vlanVid));
                    }
                } else if (actionClass instanceof NxActionResubmitRpcAddGroupCase) {
                    Short tableId = ((NxActionResubmitRpcAddGroupCase) actionClass).getNxResubmit().getTable().toJava();
                    listActionInfo.add(new ActionNxResubmit(pos++, tableId));
                } else if (actionClass instanceof NxActionRegLoadNodesNodeTableFlowApplyActionsCase) {
                    NxRegLoad nxRegLoad =
                        ((NxActionRegLoadNodesNodeTableFlowApplyActionsCase) actionClass).getNxRegLoad();
                    listActionInfo.add(new ActionRegLoad(pos++, NxmNxReg6.class, nxRegLoad.getDst().getStart().toJava(),
                        nxRegLoad.getDst().getEnd().toJava(), nxRegLoad.getValue().longValue()));
                }
            }
            return listActionInfo;
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when egress actions for interface {}", ifName, e);
        }
        LOG.error("Error when getting egress actions for interface {}", ifName);
        return emptyList();
    }

    @Nullable
    public static Port getNeutronPortForRouterGetewayIp(DataBroker broker, IpAddress targetIP) {
        return getNeutronPortForIp(broker, targetIP, NeutronConstants.DEVICE_OWNER_GATEWAY_INF);
    }

    @NonNull
    public static List<Port> getNeutronPorts(DataBroker broker) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports>
            portsIdentifier = InstanceIdentifier.create(Neutron.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports.class);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports>
                portsOptional =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, portsIdentifier);

        if (!portsOptional.isPresent() || portsOptional.get().getPort() == null) {
            LOG.error("getNeutronPorts : No neutron ports found");
            return emptyList();
        }

        return new ArrayList<Port>(portsOptional.get().nonnullPort().values());
    }

    @Nullable
    public static Port getNeutronPortForIp(DataBroker broker, IpAddress targetIP, String deviceType) {
        List<Port> ports = getNeutronPorts(
            broker);

        for (Port port : ports) {
            if (deviceType.equals(port.getDeviceOwner()) && port.getFixedIps() != null) {
                for (FixedIps ip : port.nonnullFixedIps().values()) {
                    if (Objects.equals(ip.getIpAddress(), targetIP)) {
                        return port;
                    }
                }
            }
        }
        LOG.error("getNeutronPortForIp : Neutron Port missing for IP:{} DeviceType:{}", targetIP, deviceType);
        return null;
    }

    @Nullable
    public static Uuid getSubnetIdForFloatingIp(Port port, IpAddress targetIP) {
        if (port == null) {
            LOG.error("getSubnetIdForFloatingIp : port is null");
            return null;
        }
        for (FixedIps ip : port.nonnullFixedIps().values()) {
            if (Objects.equals(ip.getIpAddress(), targetIP)) {
                return ip.getSubnetId();
            }
        }
        LOG.error("getSubnetIdForFloatingIp : No Fixed IP configured for targetIP:{}", targetIP);
        return null;
    }

    @Nullable
    public static Subnetmap getSubnetMap(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapId = InstanceIdentifier.builder(Subnetmaps.class)
            .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, subnetmapId).orElse(null);
    }

    @NonNull
    public static List<Uuid> getSubnetIdsFromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<NetworkMap> id = InstanceIdentifier.builder(NetworkMaps.class)
            .child(NetworkMap.class, new NetworkMapKey(networkId)).build();
        List<Uuid> subnetIdList = SingleTransactionDataBroker
                .syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, id).map(NetworkMap::getSubnetIdList).orElse(
                emptyList());
        if (!subnetIdList.isEmpty()) {
            subnetIdList = new ArrayList<>(subnetIdList);
        }

        return subnetIdList;
    }

    @Nullable
    public static String getSubnetGwMac(DataBroker broker, Uuid subnetId, String vpnName) {
        if (subnetId == null) {
            LOG.error("getSubnetGwMac : subnetID is null");
            return null;
        }

        InstanceIdentifier<Subnet> subnetInst = InstanceIdentifier.create(Neutron.class).child(Subnets.class)
            .child(Subnet.class, new SubnetKey(subnetId));
        Optional<Subnet> subnetOpt =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, subnetInst);
        if (!subnetOpt.isPresent()) {
            LOG.error("getSubnetGwMac : unable to obtain Subnet for id : {}", subnetId);
            return null;
        }

        IpAddress gatewayIp = subnetOpt.get().getGatewayIp();
        if (gatewayIp == null) {
            LOG.warn("getSubnetGwMac : No GW ip found for subnet {}", subnetId.getValue());
            return null;
        }

        if (null != gatewayIp.getIpv6Address()) {
            return null;
        }

        InstanceIdentifier<VpnPortipToPort> portIpInst = InstanceIdentifier.builder(NeutronVpnPortipPortData.class)
            .child(VpnPortipToPort.class, new VpnPortipToPortKey(gatewayIp.getIpv4Address().getValue(), vpnName))
            .build();
        Optional<VpnPortipToPort> portIpToPortOpt =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, portIpInst);
        if (portIpToPortOpt.isPresent()) {
            return portIpToPortOpt.get().getMacAddress();
        }

        InstanceIdentifier<LearntVpnVipToPort> learntIpInst = InstanceIdentifier.builder(LearntVpnVipToPortData.class)
            .child(LearntVpnVipToPort.class, new LearntVpnVipToPortKey(gatewayIp.getIpv4Address().getValue(), vpnName))
            .build();
        Optional<LearntVpnVipToPort> learntIpToPortOpt =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, learntIpInst);
        if (learntIpToPortOpt.isPresent()) {
            return learntIpToPortOpt.get().getMacAddress();
        }

        LOG.info("getSubnetGwMac : No resolution was found to GW ip {} in subnet {}", gatewayIp, subnetId.getValue());
        return null;
    }

    public static boolean isIPv6Subnet(String prefix) {
        return IpPrefixBuilder.getDefaultInstance(prefix).getIpv6Prefix() != null;
    }

    static InstanceIdentifier<DpnRoutersList> getDpnRoutersId(Uint64 dpnId) {
        return InstanceIdentifier.builder(DpnRouters.class)
            .child(DpnRoutersList.class, new DpnRoutersListKey(dpnId)).build();
    }

    static InstanceIdentifier<DpnVpninterfacesList> getRouterDpnId(String routerName, Uint64 dpnId) {
        return InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerName))
            .child(DpnVpninterfacesList.class, new DpnVpninterfacesListKey(dpnId)).build();
    }

    static InstanceIdentifier<RouterDpnList> getRouterId(String routerName) {
        return InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerName)).build();
    }

    @Nullable
    protected static String getFloatingIpPortMacFromFloatingIpId(DataBroker broker, Uuid floatingIpId) {
        InstanceIdentifier<FloatingIpIdToPortMapping> id = buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(
                FloatingIpIdToPortMapping::getFloatingIpPortMacAddress).orElse(null);
    }

    @Nullable
    protected static String getFloatingIpPortMacFromFloatingIpId(TypedReadTransaction<Configuration> confTx,
        Uuid floatingIpId) {
        try {
            return confTx.read(buildfloatingIpIdToPortMappingIdentifier(floatingIpId)).get().map(
                FloatingIpIdToPortMapping::getFloatingIpPortMacAddress).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading the floating IP port MAC for {}", floatingIpId, e);
            return null;
        }
    }

    @Nullable
    protected static Uuid getFloatingIpPortSubnetIdFromFloatingIpId(DataBroker broker, Uuid floatingIpId) {
        InstanceIdentifier<FloatingIpIdToPortMapping> id = buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(
                FloatingIpIdToPortMapping::getFloatingIpPortSubnetId).orElse(null);
    }

    @Nullable
    protected static Uuid getFloatingIpPortSubnetIdFromFloatingIpId(TypedReadTransaction<Configuration> confTx,
        Uuid floatingIpId) {
        try {
            return confTx.read(buildfloatingIpIdToPortMappingIdentifier(floatingIpId)).get().map(
                FloatingIpIdToPortMapping::getFloatingIpPortSubnetId).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading the floating IP port subnet for {}", floatingIpId, e);
            return null;
        }
    }

    static InstanceIdentifier<FloatingIpIdToPortMapping> buildfloatingIpIdToPortMappingIdentifier(Uuid floatingIpId) {
        return InstanceIdentifier.builder(FloatingIpPortInfo.class).child(FloatingIpIdToPortMapping.class, new
            FloatingIpIdToPortMappingKey(floatingIpId)).build();
    }

    @Nullable
    static Interface getInterfaceStateFromOperDS(DataBroker dataBroker, String interfaceName) {
        InstanceIdentifier<Interface> ifStateId =
            buildStateInterfaceId(interfaceName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, ifStateId).orElse(null);
    }

    static InstanceIdentifier<Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder =
            InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                .interfaces.rev140508.InterfacesState.class)
                .child(Interface.class,
                    new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                        .interfaces.state.InterfaceKey(interfaceName));
        return idBuilder.build();
    }

    @Nullable
    public static Routers getRoutersFromConfigDS(DataBroker dataBroker, String routerName) {
        InstanceIdentifier<Routers> routerIdentifier = NatUtil.buildRouterIdentifier(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, routerIdentifier).orElse(null);
    }

    @Nullable
    public static Routers getRoutersFromConfigDS(TypedReadTransaction<Configuration> confTx, String routerName) {
        try {
            return confTx.read(NatUtil.buildRouterIdentifier(routerName)).get().orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading router {}", routerName, e);
            return null;
        }
    }

    static void createRouterIdsConfigDS(DataBroker dataBroker, Uint32 routerId, String routerName) {
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("createRouterIdsConfigDS : invalid routerId for routerName {}", routerName);
            return;
        }
        RouterIds rtrs = new RouterIdsBuilder().withKey(new RouterIdsKey(routerId))
            .setRouterId(routerId).setRouterName(routerName).build();
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, buildRouterIdentifier(routerId), rtrs);
    }

    @Nullable
    static FlowEntity buildDefaultNATFlowEntityForExternalSubnet(Uint64 dpId, Uint32 vpnId, String subnetId,
            IdManagerService idManager) {
        InetAddress defaultIP = null;
        try {
            defaultIP = InetAddress.getByName("0.0.0.0");
        } catch (UnknownHostException e) {
            LOG.error("buildDefaultNATFlowEntityForExternalSubnet : Failed to build FIB Table Flow for "
                    + "Default Route to NAT.", e);
            return null;
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        //add match for vrfid
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfo = new ArrayList<>();
        Uint32 groupId = getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME, NatUtil.getGroupIdKey(subnetId));
        if (groupId == NatConstants.INVALID_ID) {
            LOG.error("Unable to get groupId for subnet {} while building defauly flow entity", subnetId);
            return null;
        }
        actionsInfo.add(new ActionGroup(groupId.longValue()));
        String flowRef = getFlowRef(dpId, NwConstants.L3_FIB_TABLE, defaultIP, vpnId);
        instructions.add(new InstructionApplyActions(actionsInfo));
        return MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);
    }

    @Nullable
    static String getExtGwMacAddFromRouterId(DataBroker broker, Uint32 routerId) {
        String routerName = getRouterName(broker, routerId);
        if (routerName == null) {
            LOG.error("getExtGwMacAddFromRouterId : empty routerName received");
            return null;
        }
        return getExtGwMacAddFromRouterName(broker, routerName);
    }

    @Nullable
    static String getExtGwMacAddFromRouterName(DataBroker broker, String routerName) {
        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).map(Routers::getExtGwMacAddress).orElse(null);
    }

    @Nullable
    static String getExtGwMacAddFromRouterName(TypedReadTransaction<Configuration> tx, String routerName) {
        try {
            return tx.read(buildRouterIdentifier(routerName)).get().map(
                Routers::getExtGwMacAddress).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error retrieving external gateway MAC address for router {}", routerName, e);
            return null;
        }
    }

    static InstanceIdentifier<Router> buildNeutronRouterIdentifier(Uuid routerUuid) {
        InstanceIdentifier<Router> routerInstanceIdentifier = InstanceIdentifier.create(Neutron.class)
             .child(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers.class)
             .child(Router.class, new RouterKey(routerUuid));
        return routerInstanceIdentifier;
    }

    @Nullable
    public static String getNeutronRouterNamebyUuid(DataBroker broker, Uuid routerUuid) {
        InstanceIdentifier<Router> neutronRouterIdentifier = NatUtil.buildNeutronRouterIdentifier(routerUuid);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, neutronRouterIdentifier).map(Router::getName).orElse(
                null);
    }

    @NonNull
    public static List<Ports> getFloatingIpPortsForRouter(DataBroker broker, Uuid routerUuid) {
        InstanceIdentifier<RouterPorts> routerPortsIdentifier = getRouterPortsId(routerUuid.getValue());
        List<Ports> portsList = new ArrayList<Ports>(SingleTransactionDataBroker
                .syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker, LogicalDatastoreType.CONFIGURATION,
                        routerPortsIdentifier).map(RouterPorts::nonnullPorts).orElse(Collections.emptyMap()).values());

        if (!portsList.isEmpty()) {
            portsList = new ArrayList<>(portsList);
        }
        return portsList;
    }

    @NonNull
    public static List<Uuid> getRouterUuIdsForVpn(DataBroker broker, Uuid vpnUuid) {
        InstanceIdentifier<ExternalNetworks> externalNwIdentifier = InstanceIdentifier.create(ExternalNetworks.class);
        Optional<ExternalNetworks> externalNwData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, externalNwIdentifier);
        if (externalNwData.isPresent()) {
            for (Networks externalNw : externalNwData.get().nonnullNetworks().values()) {
                if (externalNw.getVpnid() != null && externalNw.getVpnid().equals(vpnUuid)) {
                    @Nullable List<Uuid> routerIds = externalNw.getRouterIds();
                    return routerIds != null ? new ArrayList<>(routerIds) : emptyList();
                }
            }
        }
        return emptyList();
    }

    public static boolean isIpInSubnet(String ipAddress, String start, String end) {

        try {
            long ipLo = ipToLong(InetAddress.getByName(start));
            long ipHi = ipToLong(InetAddress.getByName(end));
            long ipToTest = ipToLong(InetAddress.getByName(ipAddress));
            return ipToTest >= ipLo && ipToTest <= ipHi;
        } catch (UnknownHostException e) {
            LOG.error("isIpInSubnet : failed for IP {}", ipAddress, e);
            return false;
        }
    }

    @NonNull
    public static Collection<Uuid> getExternalSubnetIdsFromExternalIps(@Nullable List<ExternalIps> externalIps) {
        if (externalIps == null) {
            return Collections.emptySet();
        }

        return externalIps.stream().map(ExternalIps::getSubnetId).collect(Collectors.toSet());
    }

    @NonNull
    public static Collection<Uuid> getExternalSubnetIdsForRouter(DataBroker dataBroker, @Nullable String routerName) {
        if (routerName == null) {
            LOG.error("getExternalSubnetIdsForRouter : empty routerName received");
            return Collections.emptySet();
        }

        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerName);
        Optional<Routers> routerData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, id);
        if (routerData.isPresent()) {
            return NatUtil.getExternalSubnetIdsFromExternalIps(
                    new ArrayList<ExternalIps>(routerData.get().nonnullExternalIps().values()));
        } else {
            LOG.warn("getExternalSubnetIdsForRouter : No external router data for router {}", routerName);
            return Collections.emptySet();
        }
    }

    @NonNull
    protected static Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
        .subnets.Subnets> getOptionalExternalSubnets(DataBroker dataBroker, Uuid subnetId) {
        if (subnetId == null) {
            LOG.warn("getOptionalExternalSubnets : subnetId is null");
            return Optional.empty();
        }

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
            .rev160111.external.subnets.Subnets> subnetsIdentifier =
                InstanceIdentifier.builder(ExternalSubnets.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
                        .rev160111.external.subnets.Subnets.class, new SubnetsKey(subnetId)).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, subnetsIdentifier);
    }

    @NonNull
    protected static Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
        .subnets.Subnets> getOptionalExternalSubnets(TypedReadTransaction<Configuration> tx, Uuid subnetId) {
        if (subnetId == null) {
            LOG.warn("getOptionalExternalSubnets : subnetId is null");
            return Optional.empty();
        }

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
            .rev160111.external.subnets.Subnets> subnetsIdentifier =
            InstanceIdentifier.builder(ExternalSubnets.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
                    .rev160111.external.subnets.Subnets.class, new SubnetsKey(subnetId)).build();
        try {
            return tx.read(subnetsIdentifier).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error retrieving external subnets on {}", subnetId, e);
            return Optional.empty();
        }
    }

    protected static Uint32 getExternalSubnetVpnId(DataBroker dataBroker, Uuid subnetId) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
            .subnets.Subnets> optionalExternalSubnets = NatUtil.getOptionalExternalSubnets(dataBroker,
                   subnetId);
        if (optionalExternalSubnets.isPresent()) {
            return NatUtil.getVpnId(dataBroker, subnetId.getValue());
        }

        return NatConstants.INVALID_ID;
    }

    protected static Uint32 getExternalSubnetVpnId(TypedReadTransaction<Configuration> tx, Uuid subnetId) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
            .subnets.Subnets> optionalExternalSubnets = NatUtil.getOptionalExternalSubnets(tx,
            subnetId);
        if (optionalExternalSubnets.isPresent()) {
            return NatUtil.getVpnId(tx, subnetId.getValue());
        }

        return NatConstants.INVALID_ID;
    }

    protected static Uint32 getExternalSubnetVpnIdForRouterExternalIp(DataBroker dataBroker, String externalIpAddress,
            Routers router) {
        Uuid externalSubnetId = NatUtil.getExternalSubnetForRouterExternalIp(externalIpAddress, router);
        if (externalSubnetId != null) {
            return NatUtil.getExternalSubnetVpnId(dataBroker,externalSubnetId);
        }

        return NatConstants.INVALID_ID;
    }

    @Nullable
    protected static Uuid getExternalSubnetForRouterExternalIp(String externalIpAddress, Routers router) {
        externalIpAddress = validateAndAddNetworkMask(externalIpAddress);
        for (ExternalIps extIp : router.nonnullExternalIps().values()) {
            String extIpString = validateAndAddNetworkMask(extIp.getIpAddress());
            if (extIpString.equals(externalIpAddress)) {
                return extIp.getSubnetId();
            }
        }
        LOG.warn("getExternalSubnetForRouterExternalIp : Missing External Subnet for Ip:{}", externalIpAddress);
        return null;
    }

    private static long ipToLong(InetAddress ip) {
        byte[] octets = ip.getAddress();
        long result = 0;
        for (byte octet : octets) {
            result <<= 8;
            result |= octet & 0xff;
        }
        return result;
    }

    @NonNull
    static List<String> getIpsListFromExternalIps(@Nullable List<ExternalIps> externalIps) {
        if (externalIps == null) {
            return emptyList();
        }

        return externalIps.stream().map(ExternalIps::getIpAddress).collect(Collectors.toList());
    }

    // elan-instances config container
    @Nullable
    public static ElanInstance getElanInstanceByName(String elanInstanceName, DataBroker broker) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, elanIdentifierId).orElse(null);
    }

    @Nullable
    public static ElanInstance getElanInstanceByName(TypedReadTransaction<Configuration> tx, String elanInstanceName) {
        try {
            return tx.read(getElanInstanceConfigurationDataPath(elanInstanceName)).get().orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error retrieving ELAN instance by name {}", elanInstanceName, e);
            return null;
        }
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
    }

    public static Uint64 getTunnelIdForNonNaptToNaptFlow(DataBroker dataBroker, NatOverVxlanUtil natOverVxlanUtil,
                                                       IElanService elanManager, IdManagerService idManager,
                                                       Uint32 routerId, String routerName) {
        if (elanManager.isOpenStackVniSemanticsEnforced()) {
            // Router VNI will be set as tun_id if OpenStackSemantics is enabled
            return natOverVxlanUtil.getRouterVni(routerName, routerId);
        } else {
            return NatEvpnUtil.getTunnelIdForRouter(idManager, dataBroker, routerName, routerId);
        }
    }

    public static void makePreDnatToSnatTableEntry(IMdsalApiManager mdsalManager, Uint64 naptDpnId,
            short tableId, TypedWriteTransaction<Configuration> confTx) {
        LOG.debug("makePreDnatToSnatTableEntry : Create Pre-DNAT table {} --> table {} flow on NAPT DpnId {} ",
                NwConstants.PDNAT_TABLE, tableId, naptDpnId);

        Map<InstructionKey, Instruction> preDnatToSnatInstructionsMap = new HashMap<InstructionKey, Instruction>();
        preDnatToSnatInstructionsMap.put(new InstructionKey(0),
                new InstructionGotoTable(tableId).buildInstruction(0));
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        String flowRef = getFlowRefPreDnatToSnat(naptDpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        Flow preDnatToSnatTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.PDNAT_TABLE,flowRef,
                5, flowRef, 0, 0,  NwConstants.COOKIE_DNAT_TABLE,
                matches, preDnatToSnatInstructionsMap);

        mdsalManager.addFlow(confTx, naptDpnId, preDnatToSnatTableFlowEntity);
        LOG.debug("makePreDnatToSnatTableEntry : Successfully installed Pre-DNAT flow {} on NAPT DpnId {} ",
                preDnatToSnatTableFlowEntity,  naptDpnId);
    }

    public static void removePreDnatToSnatTableEntry(TypedReadWriteTransaction<Configuration> confTx,
            IMdsalApiManager mdsalManager, Uint64 naptDpnId) throws ExecutionException, InterruptedException {
        LOG.debug("removePreDnatToSnatTableEntry : Remove Pre-DNAT table {} --> table {} flow on NAPT DpnId {} ",
                NwConstants.PDNAT_TABLE, NwConstants.INBOUND_NAPT_TABLE, naptDpnId);
        String flowRef = getFlowRefPreDnatToSnat(naptDpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        mdsalManager.removeFlow(confTx, naptDpnId, flowRef, NwConstants.PDNAT_TABLE);
        LOG.debug("removePreDnatToSnatTableEntry: Successfully removed Pre-DNAT flow {} on NAPT DpnId = {}",
                flowRef, naptDpnId);
    }

    private static String getFlowRefPreDnatToSnat(Uint64 dpnId, short tableId, String uniqueId) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + uniqueId;
    }

    public static boolean isFloatingIpPresentForDpn(DataBroker dataBroker, Uint64 dpnId, String rd,
                                                    String vpnName, String externalIp,
                                                    Boolean isMoreThanOneFipCheckOnDpn) {
        InstanceIdentifier<VpnToDpnList> id = getVpnToDpnListIdentifier(rd, dpnId);
        Optional<VpnToDpnList> dpnInVpn;
        try {
            dpnInVpn = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("isFloatingIpPresentForDpn: Exception while reading VpnToDpnList DS for the rd {} dpnId {}",
                    rd, dpnId, e);
            return false;
        }
        if (dpnInVpn.isPresent()) {
            LOG.debug("isFloatingIpPresentForDpn : vpn-to-dpn-list is not empty for vpnName {}, dpn id {}, "
                    + "rd {} and floatingIp {}", vpnName, dpnId, rd, externalIp);
            try {
                Map<IpAddressesKey, IpAddresses> keyIpAddressesMap = dpnInVpn.get().getIpAddresses();
                if (keyIpAddressesMap != null && !keyIpAddressesMap.isEmpty()) {
                    int floatingIpPresentCount = 0;
                    for (IpAddresses ipAddress: keyIpAddressesMap.values()) {
                        if (!Objects.equals(ipAddress.getIpAddress(), externalIp)
                                && IpAddresses.IpAddressSource.FloatingIP.equals(ipAddress.getIpAddressSource())) {
                            floatingIpPresentCount++;
                            //Add tunnel table check
                            if (isMoreThanOneFipCheckOnDpn && floatingIpPresentCount > 1) {
                                return true;
                            }
                            //Remove tunnel table check
                            if (!isMoreThanOneFipCheckOnDpn) {
                                return true;
                            }
                        }
                    }
                } else {
                    LOG.debug("isFloatingIpPresentForDpn : vpn-to-dpn-list does not contain any floating IP for DPN {}",
                           dpnId);
                    return false;
                }
            } catch (NullPointerException e) {
                LOG.error("isFloatingIpPresentForDpn: Exception occurred on getting external IP address from "
                        + "vpn-to-dpn-list on Dpn {}", dpnId, e);
                return false;
            }
        }
        return false;
    }

    private static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, Uint64 dpnId) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd))
                .child(VpnToDpnList.class, new VpnToDpnListKey(dpnId)).build();
    }

    @Nullable
    public static String getPrimaryRd(String vpnName, TypedReadTransaction<Configuration> tx)
        throws ExecutionException, InterruptedException {
        return tx.read(getVpnInstanceIdentifier(vpnName)).get().map(NatUtil::getPrimaryRd).orElse(null);
    }

    @Nullable
    public static String getPrimaryRd(@Nullable VpnInstance vpnInstance) {
        if (vpnInstance == null) {
            return null;
        }
        List<String> rds = getListOfRdsFromVpnInstance(vpnInstance);
        return rds.isEmpty() ? vpnInstance.getVpnInstanceName() : rds.get(0);
    }

    public static InstanceIdentifier<VpnInstance> getVpnInstanceIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstances.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }

    @NonNull
    public static List<String> getListOfRdsFromVpnInstance(VpnInstance vpnInstance) {
        return vpnInstance.getRouteDistinguisher() != null ? new ArrayList<>(
                vpnInstance.getRouteDistinguisher()) : new ArrayList<>();
    }

    public static String validateAndAddNetworkMask(String ipAddress) {
        return ipAddress.contains("/32") ? ipAddress : ipAddress + "/32";
    }

    public static InstanceIdentifier<VpnInterfaceOpDataEntry> getVpnInterfaceOpDataEntryIdentifier(
            String vpnInterfaceName, String vpnName) {
        return InstanceIdentifier.builder(VpnInterfaceOpData.class).child(VpnInterfaceOpDataEntry.class,
        new VpnInterfaceOpDataEntryKey(vpnInterfaceName, vpnName)).build();
    }

    public static boolean checkForRoutersWithSameExtNetAndNaptSwitch(DataBroker broker, Uuid networkId,
                                                                     String routerName, Uint64 dpnId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        Optional<Networks> networkData = null;
        try {
            networkData = SingleTransactionDataBroker.syncReadOptional(broker,
                    LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("checkForRoutersWithSameExtNetAndNaptSwitch: Exception while reading Networks DS for the "
                            + "network {} router {} dpnId {}", networkId.getValue(), routerName, dpnId, e);
            return false;
        }
        if (networkData != null && networkData.isPresent()) {
            List<Uuid> routerUuidList = networkData.get().getRouterIds();
            if (routerUuidList != null && !routerUuidList.isEmpty()) {
                for (Uuid routerUuid : routerUuidList) {
                    String sharedRouterName = routerUuid.getValue();
                    if (!routerName.equals(sharedRouterName)) {
                        Uint64 switchDpnId = NatUtil.getPrimaryNaptfromRouterName(broker, sharedRouterName);
                        if (switchDpnId != null && switchDpnId.equals(dpnId)) {
                            LOG.debug("checkForRoutersWithSameExtNetAndNaptSwitch: external-network {} is "
                                    + "associated with other active router {} on NAPT switch {}", networkId,
                                    sharedRouterName, switchDpnId);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean checkForRoutersWithSameExtSubnetAndNaptSwitch(DataBroker broker, Uuid externalSubnetId,
                                                                        String routerName, Uint64 dpnId) {
        List<Uuid> routerUuidList = getOptionalExternalSubnets(broker, externalSubnetId)
                .map(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
                .subnets.Subnets::getRouterIds).orElse(emptyList());
        if (!routerUuidList.isEmpty()) {
            for (Uuid routerUuid : routerUuidList) {
                String sharedRouterName = routerUuid.getValue();
                if (!routerName.equals(sharedRouterName)) {
                    Uint64 switchDpnId = NatUtil.getPrimaryNaptfromRouterName(broker, sharedRouterName);
                    if (switchDpnId != null && switchDpnId.equals(dpnId)) {
                        LOG.debug("checkForRoutersWithSameExtSubnetAndNaptSwitch: external-subnetwork {} is "
                                  + "associated with other active router {} on NAPT switch {}", externalSubnetId,
                            sharedRouterName, switchDpnId);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void installRouterGwFlows(ManagedNewTransactionRunner txRunner, IVpnManager vpnManager,
            Routers router, Uint64 primarySwitchId, int addOrRemove) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            Map<ExternalIpsKey, ExternalIps> keyExternalIpsMap = router.getExternalIps();
            List<String> externalIpsSting = new ArrayList<>();

            if (keyExternalIpsMap == null || keyExternalIpsMap.isEmpty()) {
                LOG.error("installRouterGwFlows: setupRouterGwFlows no externalIP present");
                return;
            }
            for (ExternalIps externalIp : keyExternalIpsMap.values()) {
                externalIpsSting.add(externalIp.getIpAddress());
            }
            Uuid subnetVpnName = keyExternalIpsMap.get(0).getSubnetId();
            if (addOrRemove == NwConstants.ADD_FLOW) {
                vpnManager.addRouterGwMacFlow(router.getRouterName(), router.getExtGwMacAddress(), primarySwitchId,
                        router.getNetworkId(), subnetVpnName.getValue(), tx);
                vpnManager.addArpResponderFlowsToExternalNetworkIps(router.getRouterName(), externalIpsSting,
                        router.getExtGwMacAddress(), primarySwitchId,
                        router.getNetworkId());
            } else {
                vpnManager.removeRouterGwMacFlow(router.getRouterName(), router.getExtGwMacAddress(), primarySwitchId,
                        router.getNetworkId(), subnetVpnName.getValue(), tx);
                vpnManager.removeArpResponderFlowsToExternalNetworkIps(router.getRouterName(), externalIpsSting,
                        router.getExtGwMacAddress(), primarySwitchId,
                        router.getNetworkId());
            }
        }), LOG, "Error installing router gateway flows");
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void handleSNATForDPN(DataBroker dataBroker, IMdsalApiManager mdsalManager,
        IdManagerService idManager, NaptSwitchHA naptSwitchHA,
        Uint64 dpnId, Routers extRouters, Uint32 routerId, Uint32 routerVpnId,
        TypedReadWriteTransaction<Configuration> confTx,
        ProviderTypes extNwProvType, UpgradeState upgradeState) {
        //Check if primary and secondary switch are selected, If not select the role
        //Install select group to NAPT switch
        //Install default miss entry to NAPT switch
        Uint64 naptSwitch;
        String routerName = extRouters.getRouterName();
        Boolean upgradeInProgress = false;
        if (upgradeState != null) {
            upgradeInProgress = upgradeState.isUpgradeInProgress();
        }
        Uint64 naptId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (naptId == null || naptId.equals(Uint64.ZERO)
            || (!NatUtil.getSwitchStatus(dataBroker, naptId) && (upgradeInProgress == false))) {
            LOG.debug("handleSNATForDPN : NaptSwitch is down or not selected for router {},naptId {}",
                routerName, naptId);
            naptSwitch = dpnId;
            boolean naptstatus = naptSwitchHA.updateNaptSwitch(routerName, naptSwitch);
            if (!naptstatus) {
                LOG.error("handleSNATForDPN : Failed to update newNaptSwitch {} for routername {}",
                    naptSwitch, routerName);
                return;
            }
            LOG.debug("handleSNATForDPN : Switch {} is elected as NaptSwitch for router {}", dpnId, routerName);

            String externalVpnName = null;
            NatUtil.createRouterIdsConfigDS(dataBroker, routerId, routerName);
            naptSwitchHA.subnetRegisterMapping(extRouters, routerId);
            Uuid extNwUuid = extRouters.getNetworkId();
            externalVpnName = NatUtil.getAssociatedVPN(dataBroker, extNwUuid);
            if (externalVpnName != null) {
                naptSwitchHA.installSnatFlows(routerName, routerId, naptSwitch, routerVpnId, extNwUuid,
                    externalVpnName, confTx);
            }
            // Install miss entry (table 26) pointing to table 46
            FlowEntity flowEntity = naptSwitchHA.buildSnatFlowEntityForNaptSwitch(dpnId, routerName,
                routerVpnId, NatConstants.ADD_FLOW);
            if (flowEntity == null) {
                LOG.error("handleSNATForDPN : Failed to populate flowentity for router {} with dpnId {}",
                    routerName, dpnId);
                return;
            }
            LOG.debug("handleSNATForDPN : Successfully installed flow for dpnId {} router {}", dpnId, routerName);
            mdsalManager.addFlow(confTx, flowEntity);
            //Removing primary flows from old napt switch
            if (naptId != null && !naptId.equals(Uint64.ZERO)) {
                LOG.debug("handleSNATForDPN : Removing primary flows from old napt switch {} for router {}",
                    naptId, routerName);
                try {
                    naptSwitchHA.removeSnatFlowsInOldNaptSwitch(extRouters, routerId, naptId, null,
                        externalVpnName, confTx);
                } catch (Exception e) {
                    LOG.error("Exception while removing SnatFlows form OldNaptSwitch {}", naptId, e);
                }
            }
            naptSwitchHA.updateNaptSwitchBucketStatus(routerName, routerId, naptSwitch);
        } else if (naptId.equals(dpnId)) {
            LOG.error("handleSNATForDPN : NaptSwitch {} gone down during cluster reboot came alive", naptId);
        } else {
            naptSwitch = naptId;
            LOG.debug("handleSNATForDPN : Napt switch with Id {} is already elected for router {}",
                naptId, routerName);

            //installing group
            List<BucketInfo> bucketInfo = naptSwitchHA.handleGroupInNeighborSwitches(dpnId,
                routerName, routerId, naptSwitch);
            naptSwitchHA.installSnatGroupEntry(dpnId, bucketInfo, routerName);

            // Install miss entry (table 26) pointing to group
            Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME,
                NatUtil.getGroupIdKey(routerName));
            if (groupId != NatConstants.INVALID_ID) {
                FlowEntity flowEntity =
                    naptSwitchHA.buildSnatFlowEntity(dpnId, routerName, groupId.longValue(),
                        routerVpnId, NatConstants.ADD_FLOW);
                if (flowEntity == null) {
                    LOG.error("handleSNATForDPN : Failed to populate flowentity for router {} with dpnId {}"
                        + " groupId {}", routerName, dpnId, groupId);
                    return;
                }
                LOG.debug("handleSNATForDPN : Successfully installed flow for dpnId {} router {} group {}",
                    dpnId, routerName, groupId);
                mdsalManager.addFlow(confTx, flowEntity);
            } else {
                LOG.error("handleSNATForDPN: Unable to get groupId for router:{}", routerName);
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void removeSNATFromDPN(DataBroker dataBroker, IMdsalApiManager mdsalManager,
            IdManagerService idManager, NaptSwitchHA naptSwitchHA, Uint64 dpnId,
            Routers extRouter, Uint32 routerId, Uint32 routerVpnId, String externalVpnName,
            ProviderTypes extNwProvType, TypedReadWriteTransaction<Configuration> confTx)
                    throws ExecutionException, InterruptedException {
        //irrespective of naptswitch or non-naptswitch, SNAT default miss entry need to be removed
        //remove miss entry to NAPT switch
        //if naptswitch elect new switch and install Snat flows and remove those flows in oldnaptswitch
        if (extNwProvType == null) {
            return;
        }
        String routerName = extRouter.getRouterName();
        //Get the external IP labels other than VXLAN provider type. Since label is not applicable for VXLAN
        Map<String, Uint32> externalIpLabel;
        if (extNwProvType == ProviderTypes.VXLAN) {
            externalIpLabel = null;
        } else {
            externalIpLabel = NatUtil.getExternalIpsLabelForRouter(dataBroker, routerId);
        }
        Uint64 naptSwitch = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (naptSwitch == null || naptSwitch.equals(Uint64.ZERO)) {
            LOG.error("removeSNATFromDPN : No naptSwitch is selected for router {}", routerName);
            return;
        }
        Collection<String> externalIpCache = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
        boolean naptStatus =
            naptSwitchHA.isNaptSwitchDown(extRouter, routerId, dpnId, naptSwitch, routerVpnId,
                    externalIpCache, confTx);
        if (!naptStatus) {
            LOG.debug("removeSNATFromDPN: Switch with DpnId {} is not naptSwitch for router {}",
                dpnId, routerName);
            Uint32 groupId = getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME, NatUtil.getGroupIdKey(routerName));
            FlowEntity flowEntity = null;
            try {
                if (groupId != NatConstants.INVALID_ID) {
                    flowEntity = naptSwitchHA
                        .buildSnatFlowEntity(dpnId, routerName, groupId.longValue(), routerVpnId,
                            NatConstants.DEL_FLOW);
                    if (flowEntity == null) {
                        LOG.error("removeSNATFromDPN : Failed to populate flowentity for router:{} "
                            + "with dpnId:{} groupId:{}", routerName, dpnId, groupId);
                        return;
                    }
                    LOG.debug("removeSNATFromDPN : Removing default SNAT miss entry flow entity {}",
                        flowEntity);
                    mdsalManager.removeFlow(confTx, flowEntity);
                } else {
                    LOG.error("removeSNATFromDPN: Unable to get groupId for router:{}", routerName);
                }

            } catch (Exception ex) {
                LOG.error("removeSNATFromDPN : Failed to remove default SNAT miss entry flow entity {}",
                    flowEntity, ex);
                return;
            }
            LOG.debug("removeSNATFromDPN : Removed default SNAT miss entry flow for dpnID {} with routername {}",
                dpnId, routerName);

            //remove group
            GroupEntity groupEntity = null;
            try {
                if (groupId != NatConstants.INVALID_ID) {
                    groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId.longValue(), routerName,
                        GroupTypes.GroupAll, emptyList() /*listBucketInfo*/);
                    LOG.info("removeSNATFromDPN : Removing NAPT GroupEntity:{}", groupEntity);
                    mdsalManager.removeGroup(groupEntity);
                } else {
                    LOG.error("removeSNATFromDPN: Unable to get groupId for router:{}", routerName);
                }
            } catch (Exception ex) {
                LOG.error("removeSNATFromDPN : Failed to remove group entity {}", groupEntity, ex);
                return;
            }
            LOG.debug("removeSNATFromDPN : Removed default SNAT miss entry flow for dpnID {} with routerName {}",
                dpnId, routerName);
        } else {
            naptSwitchHA.removeSnatFlowsInOldNaptSwitch(extRouter, routerId, naptSwitch,
                    externalIpLabel, externalVpnName, confTx);
            //remove table 26 flow ppointing to table46
            FlowEntity flowEntity = null;
            try {
                flowEntity = naptSwitchHA.buildSnatFlowEntityForNaptSwitch(dpnId, routerName, routerVpnId,
                    NatConstants.DEL_FLOW);
                if (flowEntity == null) {
                    LOG.error("removeSNATFromDPN : Failed to populate flowentity for router {} with dpnId {}",
                            routerName, dpnId);
                    return;
                }
                LOG.debug("removeSNATFromDPN : Removing default SNAT miss entry flow entity for router {} with "
                    + "dpnId {} in napt switch {}", routerName, dpnId, naptSwitch);
                mdsalManager.removeFlow(confTx, flowEntity);

            } catch (Exception ex) {
                LOG.error("removeSNATFromDPN : Failed to remove default SNAT miss entry flow entity {}",
                    flowEntity, ex);
                return;
            }
            LOG.debug("removeSNATFromDPN : Removed default SNAT miss entry flow for dpnID {} with routername {}",
                dpnId, routerName);

            //best effort to check IntExt model
            naptSwitchHA.bestEffortDeletion(routerId, routerName, externalIpLabel, confTx);
        }
    }

    public static Boolean isOpenStackVniSemanticsEnforcedForGreAndVxlan(IElanService elanManager,
                                                                        ProviderTypes extNwProvType) {
        if (elanManager.isOpenStackVniSemanticsEnforced() && (extNwProvType == ProviderTypes.GRE
                || extNwProvType == ProviderTypes.VXLAN)) {
            return true;
        }
        return false;
    }

    public static void addPseudoPortToElanDpn(String elanInstanceName, String pseudoPortId,
            Uint64 dpnId, DataBroker dataBroker) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfaceId = getElanDpnInterfaceOperationalDataPath(
                elanInstanceName, dpnId);
        // FIXME: separate this out?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(elanInstanceName);
        lock.lock();
        try {
            Optional<DpnInterfaces> dpnInElanInterfaces = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
            List<String> elanInterfaceList = new ArrayList<>();
            DpnInterfaces dpnInterface;
            if (dpnInElanInterfaces.isPresent()) {
                dpnInterface = dpnInElanInterfaces.get();

                elanInterfaceList = (dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty())
                        ? new ArrayList<>(dpnInterface.getInterfaces()) : elanInterfaceList;
            }
            if (!elanInterfaceList.contains(pseudoPortId)) {
                elanInterfaceList.add(pseudoPortId);
                dpnInterface = new DpnInterfacesBuilder().setDpId(dpnId).setInterfaces(elanInterfaceList)
                        .withKey(new DpnInterfacesKey(dpnId)).build();
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    elanDpnInterfaceId, dpnInterface);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Failed to read elanDpnInterface with error {}", e.getMessage());
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to add elanDpnInterface with error {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public static void removePseudoPortFromElanDpn(String elanInstanceName, String pseudoPortId,
            Uint64 dpnId, DataBroker dataBroker) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfaceId = getElanDpnInterfaceOperationalDataPath(
                elanInstanceName, dpnId);
        // FIXME: separate this out?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(elanInstanceName);
        lock.lock();
        try {
            Optional<DpnInterfaces> dpnInElanInterfaces = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
            List<String> elanInterfaceList = new ArrayList<>();
            DpnInterfaces dpnInterface;
            if (!dpnInElanInterfaces.isPresent()) {
                LOG.info("No interface in any dpn for {}", elanInstanceName);
                return;
            }

            dpnInterface = dpnInElanInterfaces.get();
            elanInterfaceList = (dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty())
                    ? new ArrayList<>(dpnInterface.getInterfaces()) : elanInterfaceList;
            if (!elanInterfaceList.contains(pseudoPortId)) {
                LOG.info("Router port not present in DPN {} for VPN {}", dpnId, elanInstanceName);
                return;
            }
            elanInterfaceList.remove(pseudoPortId);
            dpnInterface = new DpnInterfacesBuilder().setDpId(dpnId).setInterfaces(elanInterfaceList)
                    .withKey(new DpnInterfacesKey(dpnId)).build();
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    elanDpnInterfaceId, dpnInterface);
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Failed to read elanDpnInterface with error {}", e.getMessage());
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to remove elanDpnInterface with error {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public static boolean isLastExternalRouter(String networkid, String routerName, NatDataUtil natDataUtil) {
        Set<Map.Entry<String,Routers>> extRouter = natDataUtil.getAllRouters();
        for (Map.Entry<String,Routers> router : extRouter) {
            if (!router.getKey().equals(routerName) && router.getValue().getNetworkId().getValue()
                    .equals(networkid)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static LearntVpnVipToPortData getLearntVpnVipToPortData(DataBroker dataBroker) {
        try {
            return SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, getLearntVpnVipToPortDataId());
        }
        catch (ExpectedDataObjectNotFoundException e) {
            LOG.warn("Failed to read LearntVpnVipToPortData with error {}", e.getMessage());
            return null;
        }
    }

    public static InstanceIdentifier<LearntVpnVipToPortData> getLearntVpnVipToPortDataId() {
        InstanceIdentifier<LearntVpnVipToPortData> learntVpnVipToPortDataId = InstanceIdentifier
                .builder(LearntVpnVipToPortData.class).build();
        return learntVpnVipToPortDataId;
    }

    public static InstanceIdentifier<DpnInterfaces> getElanDpnInterfaceOperationalDataPath(String elanInstanceName,
            Uint64 dpId) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName))
                .child(DpnInterfaces.class, new DpnInterfacesKey(dpId)).build();
    }

    public static InstanceIdentifier<Group> getGroupInstanceId(Uint64 dpnId, Uint32 groupId) {
        return InstanceIdentifier.builder(Nodes.class).child(org.opendaylight.yang.gen.v1.urn.opendaylight
                .inventory.rev130819.nodes.Node.class, new NodeKey(new NodeId("openflow:" + dpnId)))
                .augmentation(FlowCapableNode.class).child(Group.class, new GroupKey(new GroupId(groupId))).build();
    }

    public static void createGroupIdPool(IdManagerService idManager) {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
                .setPoolName(NatConstants.SNAT_IDPOOL_NAME)
                .setLow(NatConstants.SNAT_ID_LOW_VALUE)
                .setHigh(NatConstants.SNAT_ID_HIGH_VALUE)
                .build();
        try {
            Future<RpcResult<CreateIdPoolOutput>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("createGroupIdPool : GroupIdPool created successfully");
            } else {
                LOG.error("createGroupIdPool : Unable to create GroupIdPool");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("createGroupIdPool : Failed to create PortPool for NAPT Service", e);
        }
    }

    public static boolean getSwitchStatus(DataBroker broker, Uint64 switchId) {
        NodeId nodeId = new NodeId("openflow:" + switchId);
        LOG.debug("getSwitchStatus : Querying switch with dpnId {} is up/down", nodeId);
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> nodeInstanceId
            = InstanceIdentifier.builder(Nodes.class).child(org.opendaylight.yang.gen.v1.urn.opendaylight
                    .inventory.rev130819.nodes.Node.class, new NodeKey(nodeId)).build();
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> nodeOptional =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, nodeInstanceId);
        if (nodeOptional.isPresent()) {
            LOG.debug("getSwitchStatus : Switch {} is up", nodeId);
            return true;
        }
        LOG.debug("getSwitchStatus : Switch {} is down", nodeId);
        return false;
    }

    public static boolean isExternalNetwork(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        Optional<Networks> networkData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                        broker, LogicalDatastoreType.CONFIGURATION, id);
        return networkData.isPresent();
    }

    @Nullable
    public static String getElanInstancePhysicalNetwok(String elanInstanceName, DataBroker broker) {
        ElanInstance elanInstance =  getElanInstanceByName(elanInstanceName, broker);
        if (null != elanInstance) {
            return elanInstance.getPhysicalNetworkName();
        }
        return null;

    }

    public static Map<String, String> getOpenvswitchOtherConfigMap(Uint64 dpnId, DataBroker dataBroker) {
        String otherConfigVal = getProviderMappings(dpnId, dataBroker);
        return getMultiValueMap(otherConfigVal);
    }

    public static Map<String, String> getMultiValueMap(String multiKeyValueStr) {
        if (Strings.isNullOrEmpty(multiKeyValueStr)) {
            return Collections.emptyMap();
        }

        Map<String, String> valueMap = new HashMap<>();
        Splitter splitter = Splitter.on(OTHER_CONFIG_PARAMETERS_DELIMITER);
        for (String keyValue : splitter.split(multiKeyValueStr)) {
            String[] split = keyValue.split(OTHER_CONFIG_KEY_VALUE_DELIMITER, 2);
            if (split.length == 2) {
                valueMap.put(split[0], split[1]);
            }
        }

        return valueMap;
    }

    public static Optional<Node> getBridgeRefInfo(Uint64 dpnId, DataBroker dataBroker) {
        InstanceIdentifier<BridgeRefEntry> bridgeRefInfoPath = InstanceIdentifier.create(BridgeRefInfo.class)
                .child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId));

        Optional<BridgeRefEntry> bridgeRefEntry =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, bridgeRefInfoPath);
        if (!bridgeRefEntry.isPresent()) {
            LOG.info("getBridgeRefInfo : bridgeRefEntry is not present for {}", dpnId);
            return Optional.empty();
        }

        InstanceIdentifier<Node> nodeId =
                bridgeRefEntry.get().getBridgeReference().getValue().firstIdentifierOf(Node.class);

        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, nodeId);
    }

    @Nullable
    public static String getProviderMappings(Uint64 dpId, DataBroker dataBroker) {
        return getBridgeRefInfo(dpId, dataBroker).map(node -> getOpenvswitchOtherConfigs(node,
                PROVIDER_MAPPINGS, dataBroker)).orElse(null);
    }

    @Nullable
    public static String getOpenvswitchOtherConfigs(Node node, String key, DataBroker dataBroker) {
        OvsdbNodeAugmentation ovsdbNode = node.augmentation(OvsdbNodeAugmentation.class);
        if (ovsdbNode == null) {
            Optional<Node> nodeFromReadOvsdbNode = readOvsdbNode(node, dataBroker);
            if (nodeFromReadOvsdbNode.isPresent()) {
                ovsdbNode = nodeFromReadOvsdbNode.get().augmentation(OvsdbNodeAugmentation.class);
            }
        }

        if (ovsdbNode != null && ovsdbNode.getOpenvswitchOtherConfigs() != null) {
            for (OpenvswitchOtherConfigs openvswitchOtherConfigs
                    : ovsdbNode.nonnullOpenvswitchOtherConfigs().values()) {
                if (Objects.equals(openvswitchOtherConfigs.getOtherConfigKey(), key)) {
                    return openvswitchOtherConfigs.getOtherConfigValue();
                }
            }
        }
        LOG.info("getOpenvswitchOtherConfigs : OtherConfigs is not present for ovsdbNode {}", node.getNodeId());
        return null;
    }

    @NonNull
    public static Optional<Node> readOvsdbNode(Node bridgeNode, DataBroker dataBroker) {
        OvsdbBridgeAugmentation bridgeAugmentation = extractBridgeAugmentation(bridgeNode);
        if (bridgeAugmentation != null) {
            InstanceIdentifier<Node> ovsdbNodeIid =
                    (InstanceIdentifier<Node>) bridgeAugmentation.getManagedBy().getValue();
            return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, ovsdbNodeIid);
        }
        return Optional.empty();

    }

    @Nullable
    public static OvsdbBridgeAugmentation extractBridgeAugmentation(Node node) {
        if (node == null) {
            return null;
        }
        return node.augmentation(OvsdbBridgeAugmentation.class);
    }

    public static String getDefaultFibRouteToSNATForSubnetJobKey(String subnetName, Uint64 dpnId) {
        return NatConstants.NAT_DJC_PREFIX + subnetName + dpnId;
    }

    public static ExternalSubnets getExternalSubnets(DataBroker dataBroker) {
        InstanceIdentifier<ExternalSubnets> subnetsIdentifier =
                InstanceIdentifier.builder(ExternalSubnets.class)
                .build();
        try {
            Optional<ExternalSubnets> optionalExternalSubnets  = SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetsIdentifier);
            if (optionalExternalSubnets.isPresent()) {
                return optionalExternalSubnets.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read the subnets from the datastore.");
        }
        return null;

    }

    public static void addFlow(TypedWriteTransaction<Configuration> confTx, IMdsalApiManager mdsalManager,
            Uint64 dpId, short tableId, String flowId, int priority, String flowName, Uint64 cookie,
            List<? extends MatchInfoBase> matches, List<InstructionInfo> instructions) {
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName,
                NatConstants.DEFAULT_IDLE_TIMEOUT, NatConstants.DEFAULT_IDLE_TIMEOUT, cookie, matches,
                instructions);
        LOG.trace("syncFlow : Installing DpnId {}, flowId {}", dpId, flowId);
        mdsalManager.addFlow(confTx, flowEntity);
    }

    public static void removeFlow(TypedReadWriteTransaction<Configuration> confTx, IMdsalApiManager mdsalManager,
            Uint64 dpId, short tableId, String flowId) throws ExecutionException, InterruptedException {
        LOG.trace("syncFlow : Removing Acl Flow DpnId {}, flowId {}", dpId, flowId);
        mdsalManager.removeFlow(confTx, dpId, flowId, tableId);
    }

    public static String getIpv6FlowRef(Uint64 dpnId, short tableId, Uint32 routerID) {
        return new StringBuilder().append(NatConstants.IPV6_FLOWID_PREFIX).append(dpnId).append(NatConstants
                .FLOWID_SEPARATOR).append(tableId).append(NatConstants.FLOWID_SEPARATOR).append(routerID).toString();
    }

    public static String getTunnelInterfaceName(Uint64 srcDpId, Uint64 dstDpId,
                                                ItmRpcService itmManager) {
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        RpcResult<GetTunnelInterfaceNameOutput> rpcResult;
        try {
            Future<RpcResult<GetTunnelInterfaceNameOutput>> result = itmManager
                    .getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder().setSourceDpid(srcDpId)
                            .setDestinationDpid(dstDpId).setTunnelType(tunType).build());
            rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                tunType = TunnelTypeGre.class ;
                result = itmManager.getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder()
                        .setSourceDpid(srcDpId)
                        .setDestinationDpid(dstDpId)
                        .setTunnelType(tunType)
                        .build());
                rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.warn("getTunnelInterfaceName : RPC Call to getTunnelInterfaceId returned with Errors {}",
                            rpcResult.getErrors());
                } else {
                    return rpcResult.getResult().getInterfaceName();
                }
                LOG.warn("getTunnelInterfaceName : RPC Call to getTunnelInterfaceId returned with Errors {}",
                        rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException | NullPointerException e) {
            LOG.error("getTunnelInterfaceName : Exception when getting tunnel interface Id for tunnel "
                    + "between {} and {}", srcDpId, dstDpId);
        }
        return null;
    }

    public static Boolean isRouterInterfacePort(DataBroker broker, String ifaceName) {
        Port neutronPort = getNeutronPort(broker, ifaceName);
        if (neutronPort == null) {
            return Boolean.TRUE;
        } else {
            return (NatConstants.NETWORK_ROUTER_INTERFACE.equalsIgnoreCase(neutronPort.getDeviceOwner()) ? Boolean.TRUE
                : Boolean.FALSE);
        }
    }

    private static Port getNeutronPort(DataBroker broker, String ifaceName) {
        InstanceIdentifier<Port>
            portsIdentifier = InstanceIdentifier.create(Neutron.class)
            .child(
                org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports.class)
            .child(Port.class, new PortKey(new Uuid(ifaceName)));
        Optional<Port> portsOptional;
        try {
            portsOptional = SingleTransactionDataBroker
                .syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION, portsIdentifier);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Read Failed Exception While Reading Neutron Port for {}", ifaceName, e);
            portsOptional = Optional.empty();
        }
        if (!portsOptional.isPresent()) {
            LOG.error("getNeutronPort : No neutron ports found for interface {}", ifaceName);
            return null;
        }
        return portsOptional.get();
    }

    public static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
        .instance.to.vpn.id.VpnInstance getVpnIdToVpnInstance(DataBroker broker, String vpnName) {
        if (vpnName == null) {
            return null;
        }

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911
            .vpn.instance.to.vpn.id.VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911
            .vpn.instance.to.vpn.id.VpnInstance> vpnInstance = Optional.empty();
        try {
            vpnInstance = SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION, id);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read VpnInstance {}", vpnInstance, e);
        }
        if (vpnInstance.isPresent()) {
            return vpnInstance.get();
        } else {
            return null;
        }
    }

    public static Uint32 getExternalVpnIdForExtNetwork(DataBroker broker, Uuid externalNwUuid) {
        //Get the VPN ID from the ExternalNetworks model
        if (externalNwUuid == null) {
            LOG.error("getExternalVpnIdForExtNetwork : externalNwUuid is null");
            return null;
        }
        Uuid vpnUuid = getVpnIdfromNetworkId(broker, externalNwUuid);
        if (vpnUuid == null) {
            LOG.error("NAT Service : vpnUuid is null");
            return null;
        }
        Uint32 vpnId = getVpnId(broker, vpnUuid.getValue());
        return vpnId;
    }

    static ReentrantLock lockForNat(final Uint64 dataPath) {
        // FIXME: wrap this in an Identifier
        return JvmGlobalLocks.getLockForString(NatConstants.NAT_DJC_PREFIX + dataPath);
    }

    public static void removeSnatEntriesForPort(DataBroker dataBroker, NaptManager naptManager,
        IMdsalApiManager mdsalManager, NeutronvpnService neutronVpnService,
        String interfaceName, String routerName) {
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("removeSnatEntriesForPort: routerId not found for routername {}", routerName);
            return;
        }
        Uint64 naptSwitch = getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (naptSwitch == null || naptSwitch.equals(Uint64.ZERO)) {
            LOG.error("removeSnatEntriesForPort: NaptSwitch is not elected for router {}"
                + "with Id {}", routerName, routerId);
            return;
        }
        //getInternalIp for port
        List<String> fixedIps = getFixedIpsForPort(neutronVpnService, interfaceName);
        if (fixedIps == null) {
            LOG.error("removeSnatEntriesForPort: Internal Ips not found for InterfaceName {} in router {} with id {}",
                interfaceName, routerName, routerId);
            return;
        }
        List<ProtocolTypes> protocolTypesList = getPortocolList();
        for (String internalIp : fixedIps) {
            LOG.debug("removeSnatEntriesForPort: Internal Ip retrieved for interface {} is {} in router with Id {}",
                interfaceName, internalIp, routerId);
            for (ProtocolTypes protocol : protocolTypesList) {
                List<Uint16> portList = NatUtil.getInternalIpPortListInfo(dataBroker, routerId, internalIp, protocol);
                if (portList != null) {
                    for (Uint16 portnum : portList) {
                        //build and remove the flow in outbound table
                        removeNatFlow(mdsalManager, naptSwitch, NwConstants.OUTBOUND_NAPT_TABLE,
                            routerId, internalIp, portnum.toJava(), protocol.getName());

                        //build and remove the flow in inboundtable

                        removeNatFlow(mdsalManager, naptSwitch, NwConstants.INBOUND_NAPT_TABLE, routerId,
                            internalIp, portnum.toJava(), protocol.getName());

                        //Get the external IP address and the port from the model

                        NAPTEntryEvent.Protocol proto = protocol.toString().equals(ProtocolTypes.TCP.toString())
                            ? NAPTEntryEvent.Protocol.TCP : NAPTEntryEvent.Protocol.UDP;
                        IpPortExternal ipPortExternal = NatUtil.getExternalIpPortMap(dataBroker, routerId,
                            internalIp, String.valueOf(portnum.toJava()), proto);
                        if (ipPortExternal == null) {
                            LOG.error("removeSnatEntriesForPort: Mapping for internalIp {} "
                                + "with port {} is not found in "
                                + "router with Id {}", internalIp, portnum, routerId);
                            return;
                        }
                        String externalIpAddress = ipPortExternal.getIpAddress();
                        String internalIpPort = internalIp + ":" + portnum.toJava();
                        // delete the entry from IntExtIpPortMap DS

                        naptManager.removeFromIpPortMapDS(routerId, internalIpPort, proto);
                        naptManager.removePortFromPool(internalIpPort, externalIpAddress);

                    }
                } else {
                    LOG.debug("removeSnatEntriesForPort: No {} session for interface {} with internalIP {} "
                            + "in router with id {}",
                        protocol, interfaceName, internalIp, routerId);
                }
            }
            // delete the entry from SnatIntIpPortMap DS
            LOG.debug("removeSnatEntriesForPort: Removing InternalIp :{} of router {} from snatint-ip-port-map",
                internalIp, routerId);
            naptManager.removeFromSnatIpPortDS(routerId, internalIp);
        }
    }

    private static List<String> getFixedIpsForPort(NeutronvpnService neutronVpnService, String interfname) {
        LOG.debug("getFixedIpsForPort: getFixedIpsForPort method is called for interface {}", interfname);
        try {
            Future<RpcResult<GetFixedIPsForNeutronPortOutput>> result =
                neutronVpnService.getFixedIPsForNeutronPort(new GetFixedIPsForNeutronPortInputBuilder()
                    .setPortId(new Uuid(interfname)).build());

            RpcResult<GetFixedIPsForNeutronPortOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("getFixedIpsForPort: RPC Call to GetFixedIPsForNeutronPortOutput returned with Errors {}",
                    rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getFixedIPs();
            }
        } catch (InterruptedException | ExecutionException | NullPointerException ex) {
            LOG.error("getFixedIpsForPort: Exception while receiving fixedIps for port {}", interfname, ex);
        }
        return null;
    }

    private static List<ProtocolTypes> getPortocolList() {
        List<ProtocolTypes> protocollist = new ArrayList<>();
        protocollist.add(ProtocolTypes.TCP);
        protocollist.add(ProtocolTypes.UDP);
        return protocollist;
    }

    private static void removeNatFlow(IMdsalApiManager mdsalManager, Uint64 dpnId, short tableId, Uint32 routerId,
        String ipAddress, int ipPort, String protocol) {

        String switchFlowRef = NatUtil.getNaptFlowRef(dpnId, tableId, String.valueOf(routerId), ipAddress, ipPort,
            protocol);
        FlowEntity snatFlowEntity = NatUtil.buildFlowEntity(dpnId, tableId, switchFlowRef);

        mdsalManager.removeFlow(snatFlowEntity);
        LOG.debug("removeNatFlow: Removed the flow in table {} for the switch with the DPN ID {} for "
            + "router {} ip {} port {}", tableId, dpnId, routerId, ipAddress, ipPort);
    }

    public static String getDpnFromNodeRef(NodeRef node) {
        PathArgument pathArgument = Iterables.get(node.getValue().getPathArguments(), 1);
        InstanceIdentifier.IdentifiableItem<?, ?> item = Arguments.checkInstanceOf(pathArgument,
            InstanceIdentifier.IdentifiableItem.class);
        NodeKey key = Arguments.checkInstanceOf(item.getKey(), NodeKey.class);
        String dpnKey = key.getId().getValue();
        String dpnID = null;
        if (dpnKey.contains(NatConstants.COLON_SEPARATOR)) {
            dpnID = Uint64.valueOf(dpnKey.split(NatConstants.COLON_SEPARATOR)[1]).toString();
        }
        return dpnID;
    }
}
